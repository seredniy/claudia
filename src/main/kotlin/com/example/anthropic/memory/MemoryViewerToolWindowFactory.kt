package com.example.anthropic.memory

import com.example.anthropic.settings.AnthropicSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class MemoryViewerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project): Boolean {
        return service<AnthropicSettingsState>().enableMemoryViewer
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MemoryViewerPanel(project, toolWindow.disposable)
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(panel.mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
