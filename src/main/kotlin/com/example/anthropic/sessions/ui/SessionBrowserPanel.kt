package com.example.anthropic.sessions.ui

import com.example.anthropic.sessions.ClaudeProjectInfo
import com.example.anthropic.sessions.SessionService
import com.example.anthropic.sessions.SessionSettings
import com.example.anthropic.sessions.SessionsChangedListener
import com.example.anthropic.sessions.model.SessionEntry
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.example.anthropic.sessions.model.SessionListItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
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
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Main panel for the Session Browser tool window.
 */
class SessionBrowserPanel(private val project: Project, private val parentDisposable: Disposable) {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private val service = SessionService.getInstance(project)
    private var listModel = DefaultListModel<SessionListItem>()
    private val sessionList = object : JBList<SessionListItem>(listModel) {
        override fun getToolTipText(event: MouseEvent): String? {
            val index = locationToIndex(event.point)
            if (index < 0) return null
            val cellBounds = getCellBounds(index, index) ?: return null
            if (!cellBounds.contains(event.point)) return null
            val item = model.getElementAt(index)
            if (item is SessionListItem.Session) {
                return buildSessionTooltip(item.entry)
            }
            return null
        }
    }
    private val searchField = SearchTextField(false)
    private val emptyLabel = JLabel("No sessions found", SwingConstants.CENTER)
    private var allSessions: List<SessionEntry> = emptyList()
    private var hoveredIndex: Int = -1
    private var selectedBranch: String? = null
    private val cardLayout = java.awt.CardLayout()
    private val centerPanel = JPanel(cardLayout)
    private val statusLabel = JLabel()
    private var statusTimer: Timer? = null

    init {
        setupUI()
        loadSessions()
        subscribeToChanges()
    }

    /**
     * Get the selected session entry, skipping headers.
     */
    private fun getSelectedSession(): SessionEntry? {
        val item = sessionList.selectedValue ?: return null
        return (item as? SessionListItem.Session)?.entry
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
                val newHovered = if (index >= 0 && bounds != null && bounds.contains(e.point)) {
                    val item = listModel.getElementAt(index)
                    if (item is SessionListItem.Session) index else -1
                } else -1
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
                    val session = getSelectedSession() ?: return
                    service.resumeSession(session.sessionId)
                }
            }
        })

        // Right-click context menu.
        sessionList.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: java.awt.Component, x: Int, y: Int) {
                val index = sessionList.locationToIndex(java.awt.Point(x, y))
                if (index >= 0) {
                    val item = listModel.getElementAt(index)
                    if (item is SessionListItem.Session) {
                        sessionList.selectedIndex = index
                        val group = createContextMenuGroup()
                        val popupMenu = ActionManager.getInstance()
                            .createActionPopupMenu("SessionBrowserContext", group)
                        popupMenu.component.show(comp, x, y)
                    }
                }
            }
        })

        // Keyboard shortcuts.
        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "resumeSession")
        sessionList.actionMap.put("resumeSession", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val session = getSelectedSession() ?: return
                service.resumeSession(session.sessionId)
            }
        })

        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSession")
        sessionList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteSession")
        sessionList.actionMap.put("deleteSession", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val session = getSelectedSession() ?: return
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

        // Card layout for switching between list and empty state.
        centerPanel.add(JBScrollPane(sessionList), "list")
        centerPanel.add(emptyLabel, "empty")
        mainPanel.add(centerPanel, BorderLayout.CENTER)

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
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    service.newSession()
                }
            })
            add(createBranchFilterAction())
            add(createDirectoryPickerAction())
            add(object : AnAction("Refresh", "Reload sessions", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    service.refresh()
                    val sessionCount = listModel.elements().toList()
                        .count { it is SessionListItem.Session }
                    showStatus("Refreshed — $sessionCount sessions")
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("SessionBrowserToolbar", group, true)
        toolbar.targetComponent = mainPanel
        return toolbar
    }

    private fun createDirectoryPickerAction(): AnAction {
        return object : AnAction("Select Directory", "Choose sessions directory", AllIcons.Nodes.Folder) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) {
                val availableProjects = service.listAvailableProjects()
                val settings = SessionSettings.getInstance(project)

                val popupGroup = DefaultActionGroup().apply {
                    // Current project option.
                    val currentProjectLabel = if (!settings.isUsingCustomDirectory()) {
                        "Current Project  ✓"
                    } else {
                        "Current Project"
                    }
                    add(object : AnAction(currentProjectLabel, "Use current project's sessions", null) {
                        override fun getActionUpdateThread() = ActionUpdateThread.EDT
                        override fun actionPerformed(e: AnActionEvent) {
                            selectedBranch = null
                            service.setSessionsDirectory(null)
                            showStatus("Switched to current project")
                        }
                    })

                    if (availableProjects.isNotEmpty()) {
                        addSeparator("Available Projects")

                        for (projectInfo in availableProjects) {
                            val isSelected = settings.customSessionsDirectory == projectInfo.directoryPath
                            val label = if (isSelected) {
                                "${projectInfo.decodedPath}  ✓"
                            } else {
                                projectInfo.decodedPath
                            }
                            add(object : AnAction(label, projectInfo.directoryPath, null) {
                                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                                override fun actionPerformed(e: AnActionEvent) {
                                    selectedBranch = null
                                    service.setSessionsDirectory(projectInfo.directoryPath)
                                    showStatus("Switched to ${projectInfo.decodedPath}")
                                }
                            })
                        }
                    }

                    // Recent directories.
                    val recentDirs = settings.recentDirectories.filter { recent ->
                        availableProjects.none { it.directoryPath == recent }
                    }
                    if (recentDirs.isNotEmpty()) {
                        addSeparator("Recent")
                        for (dir in recentDirs) {
                            val path = java.nio.file.Path.of(dir)
                            val name = path.fileName?.toString() ?: dir
                            val isSelected = settings.customSessionsDirectory == dir
                            val label = if (isSelected) "$name  ✓" else name
                            add(object : AnAction(label, dir, null) {
                                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                                override fun actionPerformed(e: AnActionEvent) {
                                    selectedBranch = null
                                    service.setSessionsDirectory(dir)
                                    showStatus("Switched to $name")
                                }
                            })
                        }
                    }

                    addSeparator()

                    // Browse action.
                    add(object : AnAction("Browse...", "Select custom directory", AllIcons.Actions.MenuOpen) {
                        override fun getActionUpdateThread() = ActionUpdateThread.EDT
                        override fun actionPerformed(e: AnActionEvent) {
                            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                            descriptor.title = "Select Claude Sessions Directory"
                            descriptor.description = "Select a directory containing sessions-index.json"

                            val initialDir = SessionService.getClaudeProjectsDir()
                            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                                .findFileByNioFile(initialDir)

                            FileChooser.chooseFile(descriptor, project, virtualFile) { selected ->
                                val selectedPath = selected.path
                                selectedBranch = null
                                service.setSessionsDirectory(selectedPath)
                                showStatus("Switched to ${selected.name}")
                            }
                        }
                    })
                }

                val popup = ActionManager.getInstance()
                    .createActionPopupMenu("DirectoryPickerPopup", popupGroup)

                val component = e.inputEvent?.component ?: return
                popup.component.show(component, 0, component.height)
            }

            override fun update(e: AnActionEvent) {
                val settings = SessionSettings.getInstance(project)
                e.presentation.text = if (settings.isUsingCustomDirectory()) {
                    service.getSessionsDirectoryDisplayName()
                } else {
                    "Select Directory"
                }
            }
        }
    }

    private fun createBranchFilterAction(): AnAction {
        return object : AnAction("Filter by Branch", "Filter sessions by git branch", AllIcons.General.Filter) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) {
                val branches = allSessions
                    .mapNotNull { it.gitBranch?.takeIf { b -> b.isNotBlank() } }
                    .distinct()
                    .sorted()

                if (branches.isEmpty()) {
                    showStatus("No branches found")
                    return
                }

                val popupGroup = DefaultActionGroup().apply {
                    // "All branches" option.
                    add(object : AnAction(if (selectedBranch == null) "All branches  ✓" else "All branches") {
                        override fun getActionUpdateThread() = ActionUpdateThread.EDT
                        override fun actionPerformed(e: AnActionEvent) {
                            selectedBranch = null
                            filterSessions()
                        }
                    })

                    addSeparator()

                    // Branch options.
                    for (branch in branches) {
                        val label = if (branch == selectedBranch) "$branch  ✓" else branch
                        add(object : AnAction(label) {
                            override fun getActionUpdateThread() = ActionUpdateThread.EDT
                            override fun actionPerformed(e: AnActionEvent) {
                                selectedBranch = branch
                                filterSessions()
                            }
                        })
                    }
                }

                val popup = ActionManager.getInstance()
                    .createActionPopupMenu("BranchFilterPopup", popupGroup)

                val component = e.inputEvent?.component ?: return
                popup.component.show(component, 0, component.height)
            }

            override fun update(e: AnActionEvent) {
                // Highlight icon when filter is active.
                if (selectedBranch != null) {
                    e.presentation.text = "Branch: $selectedBranch"
                    e.presentation.icon = com.intellij.execution.runners.ExecutionUtil
                        .getLiveIndicator(AllIcons.General.Filter)
                } else {
                    e.presentation.text = "Filter by Branch"
                    e.presentation.icon = AllIcons.General.Filter
                }
            }
        }
    }

    private fun createContextMenuGroup(): ActionGroup {
        return DefaultActionGroup().apply {
            add(object : AnAction("Resume Session", "Resume this session in terminal", AllIcons.Actions.Execute) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val session = getSelectedSession() ?: return
                    service.resumeSession(session.sessionId)
                }
            })

            addSeparator()

            add(object : AnAction("Fork Session", "Fork this session into a new one", AllIcons.Vcs.Branch) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val session = getSelectedSession() ?: return
                    service.forkSession(session.sessionId)
                }
            })

            addSeparator()

            add(object : AnAction("Copy Session ID", "Copy session ID to clipboard", AllIcons.Actions.Copy) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val session = getSelectedSession() ?: return
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(session.sessionId), null)
                }
            })

            addSeparator()

            add(object : AnAction("Delete Session", "Delete this session", AllIcons.General.Remove) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val session = getSelectedSession() ?: return
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

        var filtered = allSessions

        // Branch filter.
        if (selectedBranch != null) {
            filtered = filtered.filter { it.gitBranch == selectedBranch }
        }

        // Text search.
        if (query.isNotEmpty()) {
            filtered = filtered.filter { session ->
                session.displayTitle.lowercase().contains(query) ||
                    session.gitBranch?.lowercase()?.contains(query) == true ||
                    session.firstPrompt?.lowercase()?.contains(query) == true
            }
        }

        // Batch update: build new model and swap in one operation to avoid per-element repaint.
        val newModel = DefaultListModel<SessionListItem>()
        if (filtered.isEmpty()) {
            cardLayout.show(centerPanel, "empty")
        } else {
            val grouped = groupByDate(filtered)
            grouped.forEach { newModel.addElement(it) }
            cardLayout.show(centerPanel, "list")
        }
        listModel = newModel
        sessionList.model = newModel
    }

    /**
     * Group sessions by date into list items with headers.
     */
    private fun groupByDate(sessions: List<SessionEntry>): List<SessionListItem> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val weekStart = today.with(ChronoField.DAY_OF_WEEK, 1)

        val groups = linkedMapOf<String, MutableList<SessionEntry>>()

        for (session in sessions) {
            val date = try {
                Instant.parse(session.modified).atZone(zone).toLocalDate()
            } catch (e: Exception) {
                LocalDate.MIN
            }

            val group = when {
                date == today -> "Today"
                date == yesterday -> "Yesterday"
                date >= weekStart && date < today -> "This Week"
                else -> "Older"
            }

            groups.getOrPut(group) { mutableListOf() }.add(session)
        }

        val result = mutableListOf<SessionListItem>()
        for ((title, entries) in groups) {
            result.add(SessionListItem.Header(title))
            entries.forEach { result.add(SessionListItem.Session(it)) }
        }
        return result
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
        project.messageBus.connect(parentDisposable)
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
