package com.example.anthropic.sessions

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-level settings for Session Browser.
 */
@State(
    name = "ClaudiaSessionSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
@Service(Service.Level.PROJECT)
class SessionSettings : PersistentStateComponent<SessionSettings.State> {

    private var myState = State()

    data class State(
        // Custom directory path, null means use default (current project).
        var customSessionsDirectory: String? = null,
        // Recently used directories for quick switching.
        var recentDirectories: MutableList<String> = mutableListOf()
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var customSessionsDirectory: String?
        get() = myState.customSessionsDirectory
        set(value) {
            myState.customSessionsDirectory = value
        }

    val recentDirectories: MutableList<String>
        get() = myState.recentDirectories

    /**
     * Add a directory to recent list (max 5).
     */
    fun addRecentDirectory(path: String) {
        myState.recentDirectories.remove(path)
        myState.recentDirectories.add(0, path)
        if (myState.recentDirectories.size > 5) {
            myState.recentDirectories.removeAt(myState.recentDirectories.lastIndex)
        }
    }

    /**
     * Check if using custom directory.
     */
    fun isUsingCustomDirectory(): Boolean {
        return customSessionsDirectory != null
    }

    /**
     * Reset to default (current project).
     */
    fun resetToDefault() {
        customSessionsDirectory = null
    }

    companion object {
        fun getInstance(project: Project): SessionSettings =
            project.getService(SessionSettings::class.java)
    }
}
