package com.example.anthropic.statusbar

import com.example.anthropic.api.ClaudeCredentialDiscovery
import com.example.anthropic.settings.AnthropicSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class AnthropicUsageWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "com.example.anthropic.statusbar.widget"

    override fun getDisplayName() = "Claude Usage"

    override fun isAvailable(project: Project): Boolean {
        val settings = service<AnthropicSettingsState>()

        return if (settings.useManualToken) {
            settings.hasAccessToken()
        } else {
            ClaudeCredentialDiscovery().hasCredentials()
        }
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return AnthropicUsageWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar) = true
}
