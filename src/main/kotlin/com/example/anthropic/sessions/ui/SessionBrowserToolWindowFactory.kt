package com.example.anthropic.sessions.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Factory for the Claude Sessions tool window.
 * Appears as an icon on the right IDE sidebar stripe.
 */
class SessionBrowserToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SessionBrowserPanel(project, toolWindow.disposable)
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(panel.mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
