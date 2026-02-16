package com.example.anthropic.sessions.parser

import com.example.anthropic.sessions.model.SessionEntry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * Scans .jsonl session files directly to build session entries.
 * This is the primary session discovery mechanism, independent of sessions-index.json.
 */
object SessionJsonlScanner {
    private val log = logger<SessionJsonlScanner>()
    private val gson = Gson()
    private val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
    private val timestampRegex = """"timestamp"\s*:\s*"([^"]+)"""".toRegex()

    /**
     * Scan all .jsonl files in the given directory and build session entries.
     * Only scans top-level files (skips subdirectories like subagents/).
     * Filters out sidechain sessions and sorts by modified desc.
     */
    fun scan(sessionsDir: Path): List<SessionEntry> {
        if (!sessionsDir.exists()) return emptyList()

        return try {
            Files.list(sessionsDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension == "jsonl" }
                    .toList()
                    .mapNotNull { parseSessionFile(it) }
                    .filter { !it.isSidechain && it.messageCount > 0 && it.firstPrompt != null }
                    .sortedByDescending { it.modified }
            }
        } catch (e: Exception) {
            log.warn("Failed to scan sessions directory $sessionsDir: ${e.message}", e)
            emptyList()
        }
    }

    internal fun parseSessionFile(file: Path): SessionEntry? {
        val sessionId = file.nameWithoutExtension

        try {
            val mtime = Files.getLastModifiedTime(file).toInstant()
            var firstUserMessage: JsonObject? = null
            var firstPrompt: String? = null
            var createdTimestamp: String? = null
            var messageCount = 0

            BufferedReader(InputStreamReader(Files.newInputStream(file), Charsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if ("\"type\"" !in line) return@forEach
                    val typeMatch = typeRegex.find(line) ?: return@forEach
                    val type = typeMatch.groupValues[1]

                    when (type) {
                        "user" -> {
                            messageCount++
                            // Only parse JSON while we still need metadata from user messages.
                            if (firstUserMessage == null || firstPrompt == null) {
                                try {
                                    val parsed = gson.fromJson(line, JsonObject::class.java)
                                    if (firstUserMessage == null) {
                                        firstUserMessage = parsed
                                    }
                                    if (firstPrompt == null) {
                                        val text = extractPromptText(parsed.getAsJsonObject("message"))
                                        if (!text.isNullOrBlank()) {
                                            firstPrompt = text
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        "assistant", "message" -> messageCount++
                        "file-history-snapshot" -> {
                            if (createdTimestamp == null) {
                                createdTimestamp = timestampRegex.find(line)?.groupValues?.getOrNull(1)
                            }
                        }
                    }
                }
            }

            val isSidechain = firstUserMessage?.get("isSidechain")?.asBoolean ?: false
            val gitBranch = firstUserMessage?.get("gitBranch")?.asString
            val cwd = firstUserMessage?.get("cwd")?.asString
            val userTimestamp = firstUserMessage?.get("timestamp")?.asString

            return SessionEntry(
                sessionId = sessionId,
                summary = null,
                firstPrompt = firstPrompt,
                messageCount = messageCount,
                created = userTimestamp ?: createdTimestamp ?: mtime.toString(),
                modified = mtime.toString(),
                gitBranch = gitBranch,
                projectPath = cwd,
                isSidechain = isSidechain,
                fullPath = file.toString()
            )
        } catch (e: Exception) {
            log.warn("Failed to parse session file $file: ${e.message}")
            return null
        }
    }

    /**
     * Internal Claude Code tags that appear as user messages but aren't real prompts.
     */
    private val internalPrefixes = listOf(
        "<local-command",
        "<command-name>",
        "<command-message>",
        "<task-notification>",
        "[Request interrupted",
        "Base directory for this skill"
    )

    private fun isInternalMessage(text: String): Boolean {
        val trimmed = text.trimStart()
        return internalPrefixes.any { trimmed.startsWith(it) }
    }

    private fun extractPromptText(message: JsonObject?): String? {
        if (message == null) return null
        val content = message.get("content") ?: return null

        val text = when {
            content.isJsonPrimitive -> content.asString
            content.isJsonArray -> {
                var found: String? = null
                for (element in content.asJsonArray) {
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        if (obj.get("type")?.asString == "text") {
                            found = obj.get("text")?.asString
                            break
                        }
                    }
                }
                found
            }
            else -> null
        }

        if (text.isNullOrBlank() || isInternalMessage(text)) return null
        return text
    }
}