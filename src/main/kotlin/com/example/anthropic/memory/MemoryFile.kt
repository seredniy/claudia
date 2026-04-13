package com.example.anthropic.memory

import java.nio.file.Path

data class MemoryFile(
    val category: MemoryCategory,
    val path: Path,
    val displayName: String,
    val exists: Boolean
)

enum class MemoryCategory(val label: String) {
    USER_MEMORY("User Memory"),
    USER_RULES("User Rules"),
    PROJECT_MEMORY("Project Memory"),
    PROJECT_RULES("Project Rules"),
    AUTO_MEMORY("Auto Memory")
}
