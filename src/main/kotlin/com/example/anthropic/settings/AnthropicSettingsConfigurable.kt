package com.example.anthropic.settings

import com.example.anthropic.services.AnthropicUsageService
import com.example.anthropic.statusbar.AnthropicUsageWidgetFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.openapi.wm.ToolWindowManager
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

        return c.enableSessionBrowser != settings.enableSessionBrowser ||
               c.enableUsageBar != settings.enableUsageBar ||
               c.enableSendToClaude != settings.enableSendToClaude ||
               c.useManualToken != settings.useManualToken ||
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
        val oldEnableUsageBar = settings.enableUsageBar
        val oldEnableSessionBrowser = settings.enableSessionBrowser

        // Save feature toggles.
        settings.enableSessionBrowser = c.enableSessionBrowser
        settings.enableUsageBar = c.enableUsageBar
        settings.enableSendToClaude = c.enableSendToClaude

        // Save credential settings.
        settings.useManualToken = c.useManualToken
        settings.setSecureAccessToken(c.accessToken)

        // Save other settings.
        settings.refreshIntervalMinutes = c.refreshInterval
        settings.showNotifications = c.showNotifications
        settings.notifyAtPercentage = c.notifyAtPercentage

        // Handle Usage Bar toggle change.
        val usageBarToggleChanged = oldEnableUsageBar != c.enableUsageBar
        val credentialsChanged = oldUseManualToken != c.useManualToken ||
                oldAccessToken != c.accessToken

        if (usageBarToggleChanged || credentialsChanged) {
            val usageService = service<AnthropicUsageService>()
            if (c.enableUsageBar) {
                usageService.restartTracking()
            } else {
                usageService.stopTracking()
            }
        }

        // Update status bar widget visibility across all open projects.
        if (usageBarToggleChanged) {
            ApplicationManager.getApplication().invokeLater {
                for (project in ProjectManager.getInstance().openProjects) {
                    if (!project.isDisposed) {
                        project.getService(StatusBarWidgetsManager::class.java)
                            .updateWidget(AnthropicUsageWidgetFactory::class.java)
                    }
                }
            }
        }

        // Update Session Browser tool window availability across all open projects.
        if (oldEnableSessionBrowser != c.enableSessionBrowser) {
            for (project in ProjectManager.getInstance().openProjects) {
                val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow("Claude Sessions")
                toolWindow?.isAvailable = c.enableSessionBrowser
            }
        }
    }

    override fun reset() {
        val c = component ?: return

        c.enableSessionBrowser = settings.enableSessionBrowser
        c.enableUsageBar = settings.enableUsageBar
        c.enableSendToClaude = settings.enableSendToClaude
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
