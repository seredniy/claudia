package com.example.anthropic.settings

import com.example.anthropic.services.AnthropicUsageService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AnthropicSettingsConfigurable : Configurable {
    private var component: AnthropicSettingsComponent? = null
    private val settings = service<AnthropicSettingsState>()

    override fun getDisplayName() = "Claudia Settings"

    override fun createComponent(): JComponent {
        component = AnthropicSettingsComponent()
        reset()  // Load current settings into UI.
        return component!!.panel
    }

    override fun isModified(): Boolean {
        val c = component ?: return false

        return c.useManualToken != settings.useManualToken ||
               c.accessToken != settings.getSecureAccessToken() ||
               c.refreshInterval != settings.refreshIntervalMinutes ||
               c.showNotifications != settings.showNotifications ||
               c.notifyAtPercentage != settings.notifyAtPercentage
    }

    override fun apply() {
        val c = component ?: return

        // Save old values to detect changes.
        val oldUseManualToken = settings.useManualToken
        val oldAccessToken = settings.getSecureAccessToken()

        // Save new values.
        settings.useManualToken = c.useManualToken
        settings.setSecureAccessToken(c.accessToken)

        // Save other settings.
        settings.refreshIntervalMinutes = c.refreshInterval
        settings.showNotifications = c.showNotifications
        settings.notifyAtPercentage = c.notifyAtPercentage

        // Restart usage tracking if credentials changed.
        val credentialsChanged = oldUseManualToken != c.useManualToken ||
                oldAccessToken != c.accessToken

        if (credentialsChanged) {
            val usageService = service<AnthropicUsageService>()
            usageService.restartTracking()
        }
    }

    override fun reset() {
        val c = component ?: return

        c.useManualToken = settings.useManualToken
        c.accessToken = settings.getSecureAccessToken()
        c.refreshInterval = settings.refreshIntervalMinutes
        c.showNotifications = settings.showNotifications
        c.notifyAtPercentage = settings.notifyAtPercentage
    }

    override fun disposeUIResources() {
        component = null
    }
}
