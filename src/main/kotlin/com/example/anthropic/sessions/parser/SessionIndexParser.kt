package com.example.anthropic.sessions.parser

import com.example.anthropic.sessions.model.SessionEntry
import com.example.anthropic.sessions.model.SessionsIndex
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Parses sessions-index.json files from Claude Code project directories.
 */
object SessionIndexParser {
    private val log = logger<SessionIndexParser>()
    private val gson = Gson()

    /**
     * Parse sessions-index.json and return sessions sorted by modified desc.
     * Filters out sidechain (sub-agent) sessions.
     */
    fun parse(indexPath: Path): List<SessionEntry> {
        if (!indexPath.exists()) {
            return emptyList()
        }

        return try {
            val json = indexPath.readText(Charsets.UTF_8)
            val index = gson.fromJson(json, SessionsIndex::class.java)
                ?: return emptyList()

            index.entries
                .filter { !it.isSidechain }
                .sortedByDescending { it.modified }
        } catch (e: Exception) {
            log.warn("Failed to parse sessions index at $indexPath: ${e.message}", e)
            emptyList()
        }
    }
}
