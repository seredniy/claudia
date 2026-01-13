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
        var refreshIntervalMinutes: Int = 5,
        var showNotifications: Boolean = true,
        var notifyAtPercentage: Int = 90,
        var organizationId: String = ""  // claude.ai organization ID
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // Convenience accessors
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

    var organizationId: String
        get() = myState.organizationId
        set(value) {
            myState.organizationId = value
        }

    // Secure sessionKey storage using PasswordSafe
    fun getSecureSessionKey(): String? {
        val passwordSafe = PasswordSafe.instance
        val attributes = createCredentialAttributes()
        return passwordSafe.getPassword(attributes)
    }

    fun setSecureSessionKey(key: String?) {
        val passwordSafe = PasswordSafe.instance
        val attributes = createCredentialAttributes()

        if (key != null && key.isNotBlank()) {
            val credentials = Credentials(CREDENTIAL_USER, key)
            passwordSafe.set(attributes, credentials)
        } else {
            passwordSafe.set(attributes, null)
        }
    }

    fun hasSessionKey(): Boolean {
        return !getSecureSessionKey().isNullOrBlank()
    }

    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            serviceName = "ClaudeAiSessionKey",
            userName = CREDENTIAL_USER
        )
    }

    companion object {
        private const val CREDENTIAL_USER = "claude-ai-session-key"
    }
}
