package com.example.anthropic.sessions

import com.example.anthropic.sessions.model.SessionEntry
import com.example.anthropic.sessions.parser.SessionIndexParser
import com.example.anthropic.sessions.parser.SessionJsonlScanner
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.deleteIfExists

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val log = logger<SessionService>()
    @Volatile
    private var cachedSessions: List<SessionEntry> = emptyList()
    private var watcherThread: Thread? = null
    private val sessionFileCache = ConcurrentHashMap<String, Pair<FileTime, SessionEntry>>()

    companion object {
        val SESSIONS_CHANGED_TOPIC = Topic.create(
            "Claude Sessions Changed",
            SessionsChangedListener::class.java
        )

        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)

        /**
         * Get the default Claude projects directory.
         */
        fun getClaudeProjectsDir(): Path {
            val home = System.getProperty("user.home")
            return Path.of(home, ".claude", "projects")
        }
    }

    private val settings: SessionSettings
        get() = SessionSettings.getInstance(project)

    init {
        startFileWatcher()
        refresh()
    }

    /**
     * Watch the sessions directory for changes using NIO WatchService.
     * Detects new/modified/deleted .jsonl files and sessions-index.json updates.
     */
    private fun startFileWatcher() {
        watcherThread?.interrupt()

        val dir = getSessionsDirectory()
        if (!dir.exists()) {
            try {
                Files.createDirectories(dir)
            } catch (e: Exception) {
                log.warn("Cannot create sessions directory: ${e.message}")
                return
            }
        }

        watcherThread = thread(isDaemon = true, name = "claude-sessions-watcher") {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                dir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )

                while (!Thread.currentThread().isInterrupted) {
                    val key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS)
                    if (key != null) {
                        val relevant = key.pollEvents().any { event ->
                            val context = event.context()?.toString() ?: return@any false
                            context == "sessions-index.json" || context.endsWith(".jsonl")
                        }
                        key.reset()
                        if (relevant) {
                            // Small delay to let the file finish writing.
                            Thread.sleep(200)
                            refresh()
                        }
                    }
                }
                watchService.close()
            } catch (e: InterruptedException) {
                // Watcher stopped or restarted.
            } catch (e: Exception) {
                log.warn("File watcher error: ${e.message}", e)
            }
        }
    }

    /**
     * Encode project path to directory name as Claude Code does.
     * /Users/smidl/Desktop/ccup -> -Users-smidl-Desktop-ccup
     * E:\laragon\www\wpforms -> E-laragon-www-wpforms (Windows)
     */
    private fun encodeProjectPath(projectPath: String): String {
        return projectPath
            .replace("\\", "-")  // Windows backslashes
            .replace("/", "-")   // Unix forward slashes
            .replace(":", "")    // Remove Windows drive letter colon
    }

    /**
     * Decode encoded project path back to display format.
     * -Users-smidl-Desktop-ccup -> /Users/smidl/Desktop/ccup
     * E-laragon-www-wpforms -> E:\laragon\www\wpforms (Windows)
     */
    private fun decodeProjectPath(encodedName: String): String {
        val parts = encodedName.split("-").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return encodedName

        // Check if first part looks like a Windows drive letter (single letter).
        val firstPart = parts.first()
        return if (firstPart.length == 1 && firstPart[0].isLetter()) {
            // Windows path: E-laragon-www -> E:\laragon\www
            "${firstPart}:\\${parts.drop(1).joinToString("\\")}"
        } else {
            // Unix path: -Users-smidl -> /Users/smidl
            "/${parts.joinToString("/")}"
        }
    }

    /**
     * Get path to sessions-index.json.
     * Uses custom directory if set, otherwise uses current project path.
     */
    fun getSessionsIndexPath(): Path {
        val customDir = settings.customSessionsDirectory
        if (customDir != null) {
            return Path.of(customDir, "sessions-index.json")
        }

        val home = System.getProperty("user.home")
        val projectPath = project.basePath ?: return Path.of(home, ".claude", "projects")
        val encoded = encodeProjectPath(projectPath)
        return Path.of(home, ".claude", "projects", encoded, "sessions-index.json")
    }

    /**
     * Get the current sessions directory (without sessions-index.json).
     */
    fun getSessionsDirectory(): Path {
        return getSessionsIndexPath().parent
    }

    /**
     * Get display name for the current sessions directory.
     */
    fun getSessionsDirectoryDisplayName(): String {
        val customDir = settings.customSessionsDirectory
        if (customDir != null) {
            // Extract the last component (encoded project path).
            val path = Path.of(customDir)
            return path.fileName?.toString() ?: customDir
        }
        return "Current Project"
    }

    /**
     * Set custom sessions directory and refresh.
     */
    fun setSessionsDirectory(path: String?) {
        settings.customSessionsDirectory = path
        if (path != null) {
            settings.addRecentDirectory(path)
        }
        sessionFileCache.clear()
        restartFileWatcher()
        refresh()
    }

    /**
     * List available Claude project directories.
     * Includes directories with sessions-index.json or any .jsonl session files.
     */
    fun listAvailableProjects(): List<ClaudeProjectInfo> {
        val projectsDir = getClaudeProjectsDir()
        if (!projectsDir.exists()) return emptyList()

        return try {
            Files.list(projectsDir).use { stream ->
                stream
                    .filter { Files.isDirectory(it) }
                    .filter { dir ->
                        try {
                            Files.exists(dir.resolve("sessions-index.json")) ||
                                Files.list(dir).use { files ->
                                    files.anyMatch { it.toString().endsWith(".jsonl") }
                                }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    .map { dir ->
                        val name = dir.fileName.toString()
                        val decodedPath = decodeProjectPath(name)
                        ClaudeProjectInfo(
                            directoryPath = dir.toString(),
                            encodedName = name,
                            decodedPath = decodedPath
                        )
                    }
                    .toList()
                    .sortedBy { it.decodedPath }
            }
        } catch (e: Exception) {
            log.warn("Failed to list Claude projects: ${e.message}")
            emptyList()
        }
    }

    /**
     * Restart file watcher for new directory.
     */
    private fun restartFileWatcher() {
        startFileWatcher()
    }

    /**
     * Load sessions from disk with incremental caching.
     * Only re-parses .jsonl files whose modification time has changed.
     * Must be called from a background thread.
     */
    private fun loadSessions(): List<SessionEntry> {
        val sessionsDir = getSessionsDirectory()
        if (!sessionsDir.exists()) return emptyList()

        // List current .jsonl files.
        val currentFiles = try {
            Files.list(sessionsDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension == "jsonl" }
                    .toList()
            }
        } catch (e: Exception) {
            log.warn("Failed to list sessions directory: ${e.message}")
            return emptyList()
        }

        // Incremental cache: remove deleted, only re-parse changed files.
        val currentIds = currentFiles.associateBy { it.nameWithoutExtension }
        sessionFileCache.keys.retainAll(currentIds.keys)

        for ((id, file) in currentIds) {
            try {
                val mtime = Files.getLastModifiedTime(file)
                val cached = sessionFileCache[id]
                if (cached == null || cached.first != mtime) {
                    val entry = SessionJsonlScanner.parseSessionFile(file)
                    if (entry != null) {
                        sessionFileCache[id] = mtime to entry
                    } else {
                        sessionFileCache.remove(id)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to check session file $file: ${e.message}")
            }
        }

        val scannedSessions = sessionFileCache.values
            .map { it.second }
            .filter { !it.isSidechain && it.messageCount > 0 && it.firstPrompt != null }
            .sortedByDescending { it.modified }

        // Enrich with summaries from sessions-index.json.
        val indexPath = getSessionsIndexPath()
        val indexMap = try {
            SessionIndexParser.parse(indexPath).associateBy { it.sessionId }
        } catch (e: Exception) {
            emptyMap()
        }

        return scannedSessions.map { scanned ->
            val indexed = indexMap[scanned.sessionId]
            if (indexed?.summary != null) {
                scanned.copy(summary = indexed.summary)
            } else {
                scanned
            }
        }
    }

    /**
     * Get cached sessions (never triggers I/O on the calling thread).
     */
    fun getSessions(): List<SessionEntry> = cachedSessions

    /**
     * Refresh sessions from disk and notify listeners.
     * Runs I/O on a background thread, then updates UI on EDT.
     */
    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val sessions = loadSessions()
            ApplicationManager.getApplication().invokeLater {
                cachedSessions = sessions
                project.messageBus.syncPublisher(SESSIONS_CHANGED_TOPIC).onSessionsChanged()
            }
        }
    }

    /**
     * Start a new Claude Code session in the IDE terminal.
     */
    fun newSession() {
        executeInTerminal("claude")
    }

    /**
     * Resume a session in the IDE terminal.
     */
    fun resumeSession(sessionId: String, projectPath: String? = null) {
        executeInTerminal("claude --resume $sessionId", projectPath)
    }

    /**
     * Fork a session in the IDE terminal.
     */
    fun forkSession(sessionId: String, projectPath: String? = null) {
        executeInTerminal("claude --resume $sessionId --fork-session", projectPath)
    }

    /**
     * Delete a session: remove the .jsonl file, update sessions-index.json, and refresh.
     * Runs all I/O on a background thread.
     */
    fun deleteSession(session: SessionEntry) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val fullPath = session.fullPath
                if (fullPath != null) {
                    Path.of(fullPath).deleteIfExists()
                }
                removeFromIndex(session.sessionId)
                sessionFileCache.remove(session.sessionId)
                val sessions = loadSessions()
                ApplicationManager.getApplication().invokeLater {
                    cachedSessions = sessions
                    project.messageBus.syncPublisher(SESSIONS_CHANGED_TOPIC).onSessionsChanged()
                }
            } catch (e: Exception) {
                log.warn("Failed to delete session ${session.sessionId}: ${e.message}", e)
            }
        }
    }

    /**
     * Remove a session entry from sessions-index.json by sessionId.
     */
    private fun removeFromIndex(sessionId: String) {
        val indexPath = getSessionsIndexPath()
        if (!indexPath.exists()) return

        try {
            val json = indexPath.toFile().readText(Charsets.UTF_8)
            val index = com.google.gson.Gson().fromJson(
                json,
                com.example.anthropic.sessions.model.SessionsIndex::class.java
            ) ?: return

            val filtered = index.entries.filter { it.sessionId != sessionId }
            val updated = index.copy(entries = filtered)

            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            indexPath.toFile().writeText(gson.toJson(updated), Charsets.UTF_8)
        } catch (e: Exception) {
            log.warn("Failed to update sessions-index.json: ${e.message}", e)
        }
    }

    private fun executeInTerminal(command: String, workingDirectory: String? = null) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow(
            TerminalToolWindowFactory.TOOL_WINDOW_ID
        ) ?: return

        terminalWindow.activate({
            SwingUtilities.invokeLater {
                try {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)
                    val cwd = workingDirectory ?: project.basePath
                    val widget = terminalManager.createLocalShellWidget(
                        cwd, "Claude Session"
                    )
                    if (widget != null) {
                        waitAndSendCommand(widget, command, 0)
                    }
                } catch (ex: Exception) {
                    log.warn("Failed to execute in terminal: ${ex.message}", ex)
                }
            }
        }, true, true)
    }

    /**
     * Wait for the shell to initialize, then send the command.
     * Retries up to 20 times with 250ms delay (5 seconds total).
     */
    private fun waitAndSendCommand(widget: ShellTerminalWidget, command: String, attempt: Int) {
        if (attempt >= 20) {
            log.warn("Terminal did not initialize after ${attempt} attempts")
            return
        }

        val starter = widget.terminalStarter
        if (starter != null) {
            starter.sendString("env -u CLAUDECODE $command\n", false)
        } else {
            // Shell not ready yet, retry after a short delay.
            val timer = javax.swing.Timer(250) {
                waitAndSendCommand(widget, command, attempt + 1)
            }
            timer.isRepeats = false
            timer.start()
        }
    }

    override fun dispose() {
        watcherThread?.interrupt()
        watcherThread = null
        sessionFileCache.clear()
        log.info("Disposing SessionService")
    }
}

interface SessionsChangedListener {
    fun onSessionsChanged()
}

/**
 * Info about a Claude project directory.
 */
data class ClaudeProjectInfo(
    val directoryPath: String,
    val encodedName: String,
    val decodedPath: String
)
