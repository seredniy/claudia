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
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Main panel for the Session Browser tool window.
 */
class SessionBrowserPanel(private val project: Project) {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private val service = SessionService.getInstance(project)
    private val listModel = DefaultListModel<SessionEntry>()
    private val sessionList = object : JBList<SessionEntry>(listModel) {
        override fun getToolTipText(event: MouseEvent): String? {
            val index = locationToIndex(event.point)
            if (index < 0) return null
            val cellBounds = getCellBounds(index, index) ?: return null
            if (!cellBounds.contains(event.point)) return null
            val session = model.getElementAt(index) ?: return null
            return buildSessionTooltip(session)
        }
    }
    private val searchField = SearchTextField(false)
    private val emptyLabel = JLabel("No sessions found", SwingConstants.CENTER)
    private var allSessions: List<SessionEntry> = emptyList()
    private var hoveredIndex: Int = -1
    private val statusLabel = JLabel()
    private var statusTimer: Timer? = null

    init {
        setupUI()
        loadSessions()
        subscribeToChanges()
    }

    private fun setupUI() {
        // Top panel: search + buttons in one row.
        val toolbar = createToolbar()
        val topPanel = JPanel(BorderLayout())

        searchField.textEditor.emptyText.text = "Search sessions..."
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filterSessions()
            override fun removeUpdate(e: DocumentEvent) = filterSessions()
            override fun changedUpdate(e: DocumentEvent) = filterSessions()
        })
        topPanel.add(searchField, BorderLayout.CENTER)
        topPanel.add(toolbar.component, BorderLayout.EAST)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Session list.
        val cellRenderer = SessionCellRenderer { hoveredIndex }
        sessionList.cellRenderer = cellRenderer
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionList.fixedCellHeight = -1

        // Tooltip: fast appearance, wider dismiss.
        val ttm = ToolTipManager.sharedInstance()
        ttm.registerComponent(sessionList)
        ttm.initialDelay = 300
        ttm.dismissDelay = 15000

        // Hover highlight.
        sessionList.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = sessionList.locationToIndex(e.point)
                val bounds = sessionList.getCellBounds(index, index)
                val newHovered = if (index >= 0 && bounds != null && bounds.contains(e.point)) index else -1
                if (newHovered != hoveredIndex) {
                    hoveredIndex = newHovered
                    sessionList.repaint()
                }
            }
        })
        sessionList.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1
                    sessionList.repaint()
                }
            }
        })

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
                    val group = createContextMenuGroup()
                    val popupMenu = ActionManager.getInstance()
                        .createActionPopupMenu("SessionBrowserContext", group)
                    popupMenu.component.show(comp, x, y)
                }
            }
        })

        // Keyboard shortcuts.
        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "resumeSession")
        sessionList.actionMap.put("resumeSession", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val session = sessionList.selectedValue ?: return
                service.resumeSession(session.sessionId)
            }
        })

        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSession")
        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteSession")
        sessionList.actionMap.put("deleteSession", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val session = sessionList.selectedValue ?: return
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

        // Empty state.
        emptyLabel.foreground = JBColor.GRAY
        emptyLabel.border = JBUI.Borders.empty(20)

        val scrollPane = JBScrollPane(sessionList)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // Status bar at bottom.
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(Font.PLAIN, 11f)
        statusLabel.border = JBUI.Borders.empty(4, 8)
        statusLabel.isVisible = false
        mainPanel.add(statusLabel, BorderLayout.SOUTH)
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("New Session", "Start a new Claude Code session", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    service.newSession()
                }
            })
            add(object : AnAction("Refresh", "Reload sessions", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    service.refresh()
                    showStatus("Refreshed — ${listModel.size()} sessions")
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("SessionBrowserToolbar", group, true)
        toolbar.targetComponent = mainPanel
        return toolbar
    }

    private fun createContextMenuGroup(): ActionGroup {
        return DefaultActionGroup().apply {
            add(object : AnAction("Resume Session", "Resume this session in terminal", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = sessionList.selectedValue ?: return
                    service.resumeSession(session.sessionId)
                }
            })

            addSeparator()

            add(object : AnAction("Fork Session", "Fork this session into a new one", AllIcons.Vcs.Branch) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = sessionList.selectedValue ?: return
                    service.forkSession(session.sessionId)
                }
            })

            addSeparator()

            add(object : AnAction("Copy Session ID", "Copy session ID to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = sessionList.selectedValue ?: return
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(session.sessionId), null)
                }
            })

            addSeparator()

            add(object : AnAction("Delete Session", "Delete this session", AllIcons.General.Remove) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = sessionList.selectedValue ?: return
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
        }
    }

    private fun loadSessions() {
        allSessions = service.getSessions()
        filterSessions()
    }

    private fun filterSessions() {
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { session ->
                session.displayTitle.lowercase().contains(query) ||
                    session.gitBranch?.lowercase()?.contains(query) == true ||
                    session.firstPrompt?.lowercase()?.contains(query) == true
            }
        }

        listModel.clear()
        if (filtered.isEmpty()) {
            val centerComponent = mainPanel.getComponent(1)
            if (centerComponent != emptyLabel) {
                mainPanel.remove(centerComponent)
                mainPanel.add(emptyLabel, BorderLayout.CENTER)
            }
        } else {
            filtered.forEach { listModel.addElement(it) }
            val centerComponent = mainPanel.getComponent(1)
            if (centerComponent == emptyLabel) {
                mainPanel.remove(emptyLabel)
                mainPanel.add(JBScrollPane(sessionList), BorderLayout.CENTER)
            }
        }
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun showStatus(text: String) {
        statusTimer?.stop()
        statusLabel.text = text
        statusLabel.isVisible = true
        statusTimer = Timer(2000) {
            statusLabel.isVisible = false
            mainPanel.revalidate()
        }
        statusTimer?.isRepeats = false
        statusTimer?.start()
        mainPanel.revalidate()
    }

    private fun buildSessionTooltip(session: SessionEntry): String {
        val sb = StringBuilder("<html><body><div style='width:320px;padding:4px'>")

        // First prompt.
        val prompt = session.firstPrompt?.takeIf { it.isNotBlank() }
        if (prompt != null) {
            val escaped = prompt.take(150)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.append("<div style='margin-bottom:6px'>")
            sb.append("<b>First prompt</b><br>")
            sb.append("<span style='color:gray'>$escaped")
            if (prompt.length > 150) sb.append("...")
            sb.append("</span></div>")
        }

        // Metadata table.
        sb.append("<table cellpadding='1' cellspacing='0'>")

        session.gitBranch?.takeIf { it.isNotBlank() }?.let {
            sb.append("<tr><td><b>Branch</b>&nbsp;&nbsp;</td><td>$it</td></tr>")
        }

        sb.append("<tr><td><b>Messages</b>&nbsp;&nbsp;</td><td>${session.messageCount}</td></tr>")

        val created = formatExactDate(session.created)
        sb.append("<tr><td><b>Created</b>&nbsp;&nbsp;</td><td>$created</td></tr>")

        val modified = formatExactDate(session.modified)
        sb.append("<tr><td><b>Modified</b>&nbsp;&nbsp;</td><td>$modified</td></tr>")

        sb.append("<tr><td><b>ID</b>&nbsp;&nbsp;</td><td><code>${session.sessionId}</code></td></tr>")

        sb.append("</table></div></body></html>")
        return sb.toString()
    }

    private fun formatExactDate(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            TOOLTIP_DATE_FORMATTER.format(instant)
        } catch (e: Exception) {
            isoDate
        }
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

    companion object {
        private val TOOLTIP_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}
