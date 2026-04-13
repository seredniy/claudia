package com.example.anthropic.memory

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class MemoryService(private val project: Project) : Disposable {
    private val log = logger<MemoryService>()
    @Volatile
    private var cachedFiles: List<MemoryFile> = emptyList()
    private var watcherThread: Thread? = null

    companion object {
        val MEMORY_CHANGED_TOPIC = Topic.create(
            "Claude Memory Changed",
            MemoryChangedListener::class.java
        )

        fun getInstance(project: Project): MemoryService =
            project.getService(MemoryService::class.java)

        private fun getClaudeHomeDir(): Path {
            val home = System.getProperty("user.home")
            return Path.of(home, ".claude")
        }
    }

    init {
        refresh()
        startFileWatcher()
    }

    /**
     * Encode project path to directory name as Claude Code does.
     * /Users/smidl/Desktop/ccup -> -Users-smidl-Desktop-ccup
     */
    private fun encodeProjectPath(projectPath: String): String {
        return projectPath
            .replace("\\", "-")
            .replace("/", "-")
            .replace(":", "")
    }

    /**
     * Discover all memory files across all locations.
     */
    private fun discoverMemoryFiles(): List<MemoryFile> {
        val files = mutableListOf<MemoryFile>()
        val claudeHome = getClaudeHomeDir()
        val projectPath = project.basePath ?: return files

        // User Memory: ~/.claude/CLAUDE.md
        val userMemory = claudeHome.resolve("CLAUDE.md")
        files.add(MemoryFile(
            category = MemoryCategory.USER_MEMORY,
            path = userMemory,
            displayName = "CLAUDE.md",
            exists = userMemory.exists()
        ))

        // User Rules: ~/.claude/rules/*.md
        val userRulesDir = claudeHome.resolve("rules")
        if (userRulesDir.exists() && userRulesDir.isDirectory()) {
            try {
                Files.list(userRulesDir).use { stream ->
                    stream.filter { it.extension == "md" }
                        .sorted()
                        .forEach { file ->
                            files.add(MemoryFile(
                                category = MemoryCategory.USER_RULES,
                                path = file,
                                displayName = file.name,
                                exists = true
                            ))
                        }
                }
            } catch (e: Exception) {
                log.warn("Failed to list user rules: ${e.message}")
            }
        }

        // Project Memory: {project}/CLAUDE.md
        val projectRoot = Path.of(projectPath)
        val projectMemory = projectRoot.resolve("CLAUDE.md")
        files.add(MemoryFile(
            category = MemoryCategory.PROJECT_MEMORY,
            path = projectMemory,
            displayName = "CLAUDE.md",
            exists = projectMemory.exists()
        ))

        // Project Alt: {project}/.claude/CLAUDE.md
        val projectAltMemory = projectRoot.resolve(".claude").resolve("CLAUDE.md")
        files.add(MemoryFile(
            category = MemoryCategory.PROJECT_MEMORY,
            path = projectAltMemory,
            displayName = ".claude/CLAUDE.md",
            exists = projectAltMemory.exists()
        ))

        // Project Local: {project}/CLAUDE.local.md
        val projectLocal = projectRoot.resolve("CLAUDE.local.md")
        files.add(MemoryFile(
            category = MemoryCategory.PROJECT_MEMORY,
            path = projectLocal,
            displayName = "CLAUDE.local.md",
            exists = projectLocal.exists()
        ))

        // Project Rules: {project}/.claude/rules/*.md
        val projectRulesDir = projectRoot.resolve(".claude").resolve("rules")
        if (projectRulesDir.exists() && projectRulesDir.isDirectory()) {
            try {
                Files.list(projectRulesDir).use { stream ->
                    stream.filter { it.extension == "md" }
                        .sorted()
                        .forEach { file ->
                            files.add(MemoryFile(
                                category = MemoryCategory.PROJECT_RULES,
                                path = file,
                                displayName = file.name,
                                exists = true
                            ))
                        }
                }
            } catch (e: Exception) {
                log.warn("Failed to list project rules: ${e.message}")
            }
        }

        // Auto Memory: ~/.claude/projects/{encoded}/memory/*.md
        val encoded = encodeProjectPath(projectPath)
        val autoMemoryDir = claudeHome.resolve("projects").resolve(encoded).resolve("memory")
        if (autoMemoryDir.exists() && autoMemoryDir.isDirectory()) {
            try {
                Files.list(autoMemoryDir).use { stream ->
                    stream.filter { it.extension == "md" }
                        .sorted()
                        .forEach { file ->
                            files.add(MemoryFile(
                                category = MemoryCategory.AUTO_MEMORY,
                                path = file,
                                displayName = file.name,
                                exists = true
                            ))
                        }
                }
            } catch (e: Exception) {
                log.warn("Failed to list auto memory: ${e.message}")
            }
        }

        return files
    }

    /**
     * Get cached memory files.
     */
    fun getMemoryFiles(): List<MemoryFile> = cachedFiles

    /**
     * Refresh memory files from disk and notify listeners.
     */
    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val files = discoverMemoryFiles()
            ApplicationManager.getApplication().invokeLater {
                cachedFiles = files
                project.messageBus.syncPublisher(MEMORY_CHANGED_TOPIC).onMemoryChanged()
            }
        }
    }

    /**
     * Create a file at the given path with optional initial content.
     */
    fun createFile(path: Path, content: String = "") {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Files.createDirectories(path.parent)
                Files.writeString(path, content)
                refresh()
            } catch (e: Exception) {
                log.warn("Failed to create file $path: ${e.message}", e)
            }
        }
    }

    /**
     * Delete a file at the given path.
     */
    fun deleteFile(path: Path) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Files.deleteIfExists(path)
                refresh()
            } catch (e: Exception) {
                log.warn("Failed to delete file $path: ${e.message}", e)
            }
        }
    }

    /**
     * Watch relevant directories for file changes.
     */
    private fun startFileWatcher() {
        watcherThread?.interrupt()

        watcherThread = thread(isDaemon = true, name = "claude-memory-watcher") {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                val claudeHome = getClaudeHomeDir()
                val projectPath = project.basePath ?: return@thread

                // Register directories that exist.
                val dirsToWatch = listOfNotNull(
                    claudeHome.takeIf { it.exists() },
                    claudeHome.resolve("rules").takeIf { it.exists() },
                    Path.of(projectPath).takeIf { it.exists() },
                    Path.of(projectPath, ".claude").takeIf { it.exists() },
                    Path.of(projectPath, ".claude", "rules").takeIf { it.exists() },
                    claudeHome.resolve("projects")
                        .resolve(encodeProjectPath(projectPath))
                        .resolve("memory").takeIf { it.exists() }
                )

                for (dir in dirsToWatch) {
                    try {
                        dir.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE
                        )
                    } catch (e: Exception) {
                        log.warn("Failed to watch directory $dir: ${e.message}")
                    }
                }

                while (!Thread.currentThread().isInterrupted) {
                    val key = watchService.poll(2, TimeUnit.SECONDS)
                    if (key != null) {
                        val relevant = key.pollEvents().any { event ->
                            val context = event.context()?.toString() ?: return@any false
                            context.endsWith(".md")
                        }
                        key.reset()
                        if (relevant) {
                            Thread.sleep(200)
                            refresh()
                        }
                    }
                }
                watchService.close()
            } catch (e: InterruptedException) {
                // Watcher stopped.
            } catch (e: Exception) {
                log.warn("Memory file watcher error: ${e.message}", e)
            }
        }
    }

    override fun dispose() {
        watcherThread?.interrupt()
        watcherThread = null
        log.info("Disposing MemoryService")
    }
}

interface MemoryChangedListener {
    fun onMemoryChanged()
}
