package com.example.anthropic.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "AnthropicSettings",
    storages = [Storage("anthropic-usage.xml")]
)
@Service(Service.Level.APP)
class AnthropicSettingsState : PersistentStateComponent<AnthropicSettingsState.State> {

    private var myState = State()

    data class State(
        var enableSessionBrowser: Boolean = true,
        var enableUsageBar: Boolean = true,
        var enableSendToClaude: Boolean = true,
        var enableMemoryViewer: Boolean = true,
        var refreshIntervalMinutes: Int = 5,
        var showNotifications: Boolean = true,
        var notifyAtPercentage: Int = 90,
        var useManualToken: Boolean = false,  // false = auto-discovery, true = manual token
        var displayMode: UsageDisplayMode = UsageDisplayMode.FIVE_HOUR  // Which limit to show in status bar
    )

    /**
     * Which usage limit to display in the status bar.
     */
    enum class UsageDisplayMode {
        FIVE_HOUR,
        SEVEN_DAY
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // Convenience accessors — feature toggles.
    var enableSessionBrowser: Boolean
        get() = myState.enableSessionBrowser
        set(value) {
            myState.enableSessionBrowser = value
        }

    var enableUsageBar: Boolean
        get() = myState.enableUsageBar
        set(value) {
            myState.enableUsageBar = value
        }

    var enableSendToClaude: Boolean
        get() = myState.enableSendToClaude
        set(value) {
            myState.enableSendToClaude = value
        }

    var enableMemoryViewer: Boolean
        get() = myState.enableMemoryViewer
        set(value) {
            myState.enableMemoryViewer = value
        }

    // Convenience accessors.
    var refreshIntervalMinutes: Int
        get() = myState.refreshIntervalMinutes
        set(value) {
            myState.refreshIntervalMinutes = value
        }

    var showNotifications: Boolean
        get() = myState.showNotifications
        set(value) {
            myState.showNotifications = value
        }

    var notifyAtPercentage: Int
        get() = myState.notifyAtPercentage
        set(value) {
            myState.notifyAtPercentage = value
        }

    var useManualToken: Boolean
        get() = myState.useManualToken
        set(value) {
            myState.useManualToken = value
        }

    var displayMode: UsageDisplayMode
        get() = myState.displayMode
        set(value) {
            myState.displayMode = value
        }

    // Secure OAuth access token storage (for manual mode).
    fun getSecureAccessToken(): String? {
        val passwordSafe = PasswordSafe.instance
        val attributes = createCredentialAttributes(CREDENTIAL_ACCESS_TOKEN)
        return passwordSafe.getPassword(attributes)
    }

    fun setSecureAccessToken(token: String?) {
        val passwordSafe = PasswordSafe.instance
        val attributes = createCredentialAttributes(CREDENTIAL_ACCESS_TOKEN)

        if (token != null && token.isNotBlank()) {
            val credentials = Credentials(CREDENTIAL_ACCESS_TOKEN, token)
            passwordSafe.set(attributes, credentials)
        } else {
            passwordSafe.set(attributes, null)
        }
    }

    fun hasAccessToken(): Boolean {
        return !getSecureAccessToken().isNullOrBlank()
    }

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            serviceName = "ClaudeUsagePlugin",
            userName = key
        )
    }

    companion object {
        private const val CREDENTIAL_ACCESS_TOKEN = "claude-oauth-access-token"
    }
}
