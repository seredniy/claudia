package com.example.anthropic.sessions

import com.example.anthropic.sessions.model.SessionEntry
import com.example.anthropic.sessions.parser.SessionIndexParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.io.path.deleteIfExists

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val log = logger<SessionService>()
    private var cachedSessions: List<SessionEntry> = emptyList()

    companion object {
        val SESSIONS_CHANGED_TOPIC = Topic.create(
            "Claude Sessions Changed",
            SessionsChangedListener::class.java
        )

        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)
    }

    init {
        project.messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val relevant = events.any { event ->
                        val path = event.path ?: return@any false
                        path.contains(".claude/projects") && path.endsWith("sessions-index.json")
                    }
                    if (relevant) {
                        refresh()
                    }
                }
            })
    }

    /**
     * Encode project path to directory name as Claude Code does.
     * /Users/smidl/Desktop/ccup -> -Users-smidl-Desktop-ccup
     */
    private fun encodeProjectPath(projectPath: String): String {
        return projectPath.replace("/", "-")
    }

    /**
     * Get path to sessions-index.json for the current project.
     */
    fun getSessionsIndexPath(): Path {
        val home = System.getProperty("user.home")
        val projectPath = project.basePath ?: return Path.of(home, ".claude", "projects")
        val encoded = encodeProjectPath(projectPath)
        return Path.of(home, ".claude", "projects", encoded, "sessions-index.json")
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
        log.info("Disposing SessionService")
    }
}

interface SessionsChangedListener {
    fun onSessionsChanged()
}
