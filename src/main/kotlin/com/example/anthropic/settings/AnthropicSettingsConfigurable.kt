package com.example.anthropic.settings

import com.example.anthropic.services.AnthropicUsageService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AnthropicSettingsConfigurable : Configurable {
    private var component: AnthropicSettingsComponent? = null
    private val settings = service<AnthropicSettingsState>()

    override fun getDisplayName() = "Claude.ai Usage Settings"

    override fun createComponent(): JComponent {
        component = AnthropicSettingsComponent()
        reset()  // Load current settings into UI
        return component!!.panel
    }

    override fun isModified(): Boolean {
        val c = component ?: return false

        // Check if any setting has changed
        return c.sessionKey != settings.getSecureSessionKey() ||
               c.organizationId != settings.organizationId ||
               c.refreshInterval != settings.refreshIntervalMinutes ||
               c.showNotifications != settings.showNotifications ||
               c.notifyAtPercentage != settings.notifyAtPercentage
    }

    override fun apply() {
        val c = component ?: return

        // Save session key securely
        val oldSessionKey = settings.getSecureSessionKey()
        val oldOrgId = settings.organizationId
        val newSessionKey = c.sessionKey
        val newOrgId = c.organizationId

        settings.setSecureSessionKey(newSessionKey)
        settings.organizationId = newOrgId

        // Save other settings
        settings.refreshIntervalMinutes = c.refreshInterval
        settings.showNotifications = c.showNotifications
        settings.notifyAtPercentage = c.notifyAtPercentage

        // Restart usage tracking if credentials changed or refresh interval changed
        if (oldSessionKey != newSessionKey || oldOrgId != newOrgId || isModified) {
            val usageService = service<AnthropicUsageService>()
            usageService.restartTracking()
        }
    }

    override fun reset() {
        val c = component ?: return

        // Load current settings into UI
        c.sessionKey = settings.getSecureSessionKey()
        c.organizationId = settings.organizationId
        c.refreshInterval = settings.refreshIntervalMinutes
        c.showNotifications = settings.showNotifications
        c.notifyAtPercentage = settings.notifyAtPercentage
    }

    override fun disposeUIResources() {
        component = null
    }
}
