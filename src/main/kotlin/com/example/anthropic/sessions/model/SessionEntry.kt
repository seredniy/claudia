package com.example.anthropic.sessions.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single Claude Code session from sessions-index.json.
 */
data class SessionEntry(
    @SerializedName("sessionId")
    val sessionId: String,

    @SerializedName("summary")
    val summary: String?,

    @SerializedName("firstPrompt")
    val firstPrompt: String?,

    @SerializedName("messageCount")
    val messageCount: Int,

    @SerializedName("created")
    val created: String,

    @SerializedName("modified")
    val modified: String,

    @SerializedName("gitBranch")
    val gitBranch: String?,

    @SerializedName("projectPath")
    val projectPath: String?,

    @SerializedName("isSidechain")
    val isSidechain: Boolean,

    @SerializedName("fullPath")
    val fullPath: String?
) {
    /**
     * Display title: summary or truncated first prompt or session ID.
     */
    val displayTitle: String
        get() = summary?.takeIf { it.isNotBlank() }
            ?: firstPrompt?.take(60)?.takeIf { it.isNotBlank() }
            ?: sessionId
}

/**
 * Root object of sessions-index.json.
 */
data class SessionsIndex(
    @SerializedName("version")
    val version: Int,

    @SerializedName("entries")
    val entries: List<SessionEntry>,

    @SerializedName("originalPath")
    val originalPath: String?
)
