package com.example.anthropic.sessions

import com.example.anthropic.sessions.model.SessionEntry
import com.example.anthropic.sessions.parser.SessionIndexParser
import com.intellij.openapi.Disposable
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
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.deleteIfExists

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val log = logger<SessionService>()
    private var cachedSessions: List<SessionEntry> = emptyList()
    @Volatile
    private var watcherRunning = true
    private var currentWatchedPath: Path? = null

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
    }

    /**
     * Watch the sessions directory for changes using NIO WatchService.
     * This detects external file modifications that IntelliJ VFS misses.
     */
    private fun startFileWatcher() {
        val indexPath = getSessionsIndexPath()
        val dir = indexPath.parent ?: return
        if (!dir.exists()) {
            try {
                Files.createDirectories(dir)
            } catch (e: Exception) {
                log.warn("Cannot create sessions directory: ${e.message}")
                return
            }
        }

        thread(isDaemon = true, name = "claude-sessions-watcher") {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                dir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )

                while (watcherRunning) {
                    val key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS)
                    if (key != null) {
                        val relevant = key.pollEvents().any { event ->
                            val context = event.context()
                            context?.toString() == "sessions-index.json"
                        }
                        key.reset()
                        if (relevant) {
                            // Small delay to let the file finish writing.
                            Thread.sleep(200)
                            SwingUtilities.invokeLater { refresh() }
                        }
                    }
                }
                watchService.close()
            } catch (e: InterruptedException) {
                // Service disposed.
            } catch (e: Exception) {
                log.warn("File watcher error: ${e.message}", e)
            }
        }
    }

    /**
     * Encode project path to directory name as Claude Code does.
     * /Users/smidl/Desktop/ccup -> -Users-smidl-Desktop-ccup
     */
    private fun encodeProjectPath(projectPath: String): String {
        return projectPath.replace("/", "-")
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
        restartFileWatcher()
        refresh()
    }

    /**
     * List available Claude project directories.
     */
    fun listAvailableProjects(): List<ClaudeProjectInfo> {
        val projectsDir = getClaudeProjectsDir()
        if (!projectsDir.exists()) return emptyList()

        return try {
            Files.list(projectsDir)
                .filter { Files.isDirectory(it) }
                .filter { Files.exists(it.resolve("sessions-index.json")) }
                .map { dir ->
                    val name = dir.fileName.toString()
                    val decodedPath = name.replace("-", "/")
                    ClaudeProjectInfo(
                        directoryPath = dir.toString(),
                        encodedName = name,
                        decodedPath = decodedPath
                    )
                }
                .toList()
                .sortedBy { it.decodedPath }
        } catch (e: Exception) {
            log.warn("Failed to list Claude projects: ${e.message}")
            emptyList()
        }
    }

    /**
     * Restart file watcher for new directory.
     */
    private fun restartFileWatcher() {
        // The watcher will pick up the new path on next iteration.
        // For immediate effect, we could stop and restart the watcher thread.
        // For now, just refresh and the watcher will adapt.
    }

    /**
     * Load sessions from disk.
     */
    fun loadSessions(): List<SessionEntry> {
        val indexPath = getSessionsIndexPath()
        cachedSessions = SessionIndexParser.parse(indexPath)
        return cachedSessions
    }

    /**
     * Get cached sessions or load from disk.
     */
    fun getSessions(): List<SessionEntry> {
        if (cachedSessions.isEmpty()) {
            cachedSessions = loadSessions()
        }
        return cachedSessions
    }

    /**
     * Refresh sessions from disk and notify listeners.
     */
    fun refresh() {
        cachedSessions = loadSessions()
        project.messageBus.syncPublisher(SESSIONS_CHANGED_TOPIC).onSessionsChanged()
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
    fun resumeSession(sessionId: String) {
        executeInTerminal("claude --resume $sessionId")
    }

    /**
     * Fork a session in the IDE terminal.
     */
    fun forkSession(sessionId: String) {
        executeInTerminal("claude --resume $sessionId --fork-session")
    }

    /**
     * Delete a session: remove the .jsonl file, update sessions-index.json, and refresh.
     */
    fun deleteSession(session: SessionEntry): Boolean {
        return try {
            // Delete the .jsonl conversation file.
            val fullPath = session.fullPath
            if (fullPath != null) {
                Path.of(fullPath).deleteIfExists()
            }

            // Remove entry from sessions-index.json.
            removeFromIndex(session.sessionId)

            refresh()
            true
        } catch (e: Exception) {
            log.warn("Failed to delete session ${session.sessionId}: ${e.message}", e)
            false
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

    private fun executeInTerminal(command: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow(
            TerminalToolWindowFactory.TOOL_WINDOW_ID
        ) ?: return

        terminalWindow.activate({
            SwingUtilities.invokeLater {
                try {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)
                    val widget = terminalManager.createLocalShellWidget(
                        project.basePath, "Claude Session"
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
            starter.sendString("$command\n", false)
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
        watcherRunning = false
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
