package com.example.anthropic.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import javax.swing.SwingUtilities

class SendToClaudeAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(SendToClaudeAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFiles = getSelectedFiles(e, project)

        if (virtualFiles.isEmpty()) return

        val pathsText = virtualFiles.joinToString(" ") { "@${it.path}" } + " "
        sendToTerminal(project, pathsText)
    }

    private fun getSelectedFiles(e: AnActionEvent, project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()

        // From action context
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.let { files.addAll(it) }

        if (files.isEmpty()) {
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { files.add(it) }
        }

        // Directly from ProjectView (works even when focus is elsewhere)
        if (files.isEmpty()) {
            try {
                val projectView = ProjectView.getInstance(project)
                val selectedElements = projectView.currentProjectViewPane?.selectedElements ?: emptyArray()

                for (element in selectedElements) {
                    when (element) {
                        is PsiFile -> element.virtualFile?.let { files.add(it) }
                        is PsiDirectory -> element.virtualFile?.let { files.add(it) }
                        is VirtualFile -> files.add(element)
                    }
                }
            } catch (ex: Exception) {
                LOG.warn("Error getting files from ProjectView: ${ex.message}")
            }
        }

        return files
    }

    private fun sendToTerminal(project: Project, text: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        terminalWindow.activate({
            SwingUtilities.invokeLater {
                try {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)
                    val widgets = terminalManager.terminalWidgets

                    for (widget in widgets) {
                        val shellWidget = findShellTerminalWidget(widget.component)
                        if (shellWidget != null) {
                            shellWidget.terminalStarter?.sendString(text, false)
                            return@invokeLater
                        }
                    }

                    // Fallback to clipboard paste
                    fallbackPaste(project, text)
                } catch (ex: Exception) {
                    LOG.warn("Error sending to terminal: ${ex.message}")
                    fallbackPaste(project, text)
                }
            }
        }, true, true)
    }

    private fun findShellTerminalWidget(component: java.awt.Component): ShellTerminalWidget? {
        if (component is ShellTerminalWidget) return component
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findShellTerminalWidget(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun fallbackPaste(project: Project, text: String) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)

            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val widgets = terminalManager.terminalWidgets
            if (widgets.isNotEmpty()) {
                widgets.first().component.requestFocusInWindow()
                SwingUtilities.invokeLater {
                    Thread.sleep(200)
                    try {
                        val robot = java.awt.Robot()
                        robot.keyPress(java.awt.event.KeyEvent.VK_META)
                        robot.keyPress(java.awt.event.KeyEvent.VK_V)
                        robot.keyRelease(java.awt.event.KeyEvent.VK_V)
                        robot.keyRelease(java.awt.event.KeyEvent.VK_META)
                    } catch (ex: Exception) {
                        LOG.warn("Robot paste failed: ${ex.message}")
                    }
                }
            }
        } catch (ex: Exception) {
            LOG.warn("Fallback paste error: ${ex.message}")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}