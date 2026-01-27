package com.example.anthropic.sessions.model

/**
 * Represents an item in the session list — either a date group header or a session entry.
 */
sealed class SessionListItem {
    data class Header(val title: String) : SessionListItem()
    data class Session(val entry: SessionEntry) : SessionListItem()
}
