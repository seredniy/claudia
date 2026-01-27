package com.example.anthropic.sessions.ui

import com.example.anthropic.sessions.SessionService
import com.example.anthropic.sessions.SessionsChangedListener
import com.example.anthropic.sessions.model.SessionEntry
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Main panel for the Session Browser tool window.
 */
class SessionBrowserPanel(private val project: Project) {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private val service = SessionService.getInstance(project)
    private val listModel = DefaultListModel<SessionEntry>()
    private val sessionList = JBList(listModel)
    private val emptyLabel = JLabel("No sessions found", SwingConstants.CENTER)

    init {
        setupUI()
        loadSessions()
        subscribeToChanges()
    }

    private fun setupUI() {
        // Toolbar.
        val toolbar = createToolbar()
        mainPanel.add(toolbar.component, BorderLayout.NORTH)

        // Session list.
        sessionList.cellRenderer = SessionCellRenderer()
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionList.fixedCellHeight = -1

        // Double-click to resume.
        sessionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val session = sessionList.selectedValue ?: return
                    service.resumeSession(session.sessionId)
                }
            }
        })

        // Right-click context menu.
        sessionList.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: java.awt.Component, x: Int, y: Int) {
                val index = sessionList.locationToIndex(java.awt.Point(x, y))
                if (index >= 0) {
                    sessionList.selectedIndex = index
                    createContextMenu().show(comp, x, y)
                }
            }
        })

        // Empty state.
        emptyLabel.foreground = JBColor.GRAY
        emptyLabel.border = JBUI.Borders.empty(20)

        val scrollPane = JBScrollPane(sessionList)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Reload sessions", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    service.refresh()
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("SessionBrowserToolbar", group, true)
        toolbar.targetComponent = mainPanel
        return toolbar
    }

    private fun createContextMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val session = sessionList.selectedValue ?: return menu

        // Resume.
        menu.add(JMenuItem("Resume Session", AllIcons.Actions.Execute).apply {
            addActionListener { service.resumeSession(session.sessionId) }
        })

        menu.addSeparator()

        // Fork.
        menu.add(JMenuItem("Fork Session", AllIcons.Vcs.Branch).apply {
            addActionListener { service.forkSession(session.sessionId) }
        })

        menu.addSeparator()

        // Copy Session ID.
        menu.add(JMenuItem("Copy Session ID", AllIcons.Actions.Copy).apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(session.sessionId), null)
            }
        })

        menu.addSeparator()

        // Delete.
        menu.add(JMenuItem("Delete Session", AllIcons.General.Remove).apply {
            addActionListener {
                val confirm = Messages.showYesNoDialog(
                    project,
                    "Delete session '${session.displayTitle}'?\n\nSession ID: ${session.sessionId}",
                    "Delete Session",
                    Messages.getWarningIcon()
                )
                if (confirm == Messages.YES) {
                    service.deleteSession(session)
                }
            }
        })

        return menu
    }

    private fun loadSessions() {
        val sessions = service.getSessions()
        listModel.clear()
        if (sessions.isEmpty()) {
            mainPanel.remove(mainPanel.getComponent(1))
            mainPanel.add(emptyLabel, BorderLayout.CENTER)
        } else {
            sessions.forEach { listModel.addElement(it) }
            // Make sure the list is showing, not the empty label.
            val centerComponent = mainPanel.getComponent(1)
            if (centerComponent == emptyLabel) {
                mainPanel.remove(emptyLabel)
                mainPanel.add(JBScrollPane(sessionList), BorderLayout.CENTER)
            }
        }
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun subscribeToChanges() {
        project.messageBus.connect()
            .subscribe(SessionService.SESSIONS_CHANGED_TOPIC,
                object : SessionsChangedListener {
                    override fun onSessionsChanged() {
                        ApplicationManager.getApplication().invokeLater {
                            loadSessions()
                        }
                    }
                })
    }
}
