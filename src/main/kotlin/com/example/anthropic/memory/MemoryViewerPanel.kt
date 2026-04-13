package com.example.anthropic.memory

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class MemoryViewerPanel(
    private val project: Project,
    private val parentDisposable: Disposable
) {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private val service = MemoryService.getInstance(project)
    private val rootNode = DefaultMutableTreeNode("Claude Memory")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val cardLayout = java.awt.CardLayout()
    private val centerPanel = JPanel(cardLayout)
    private val emptyLabel = JLabel("No memory files found", SwingConstants.CENTER)
    private val statusLabel = JLabel()
    private var statusTimer: Timer? = null

    init {
        setupUI()
        loadFiles()
        subscribeToChanges()
    }

    private fun setupUI() {
        // Toolbar.
        val toolbar = createToolbar()
        mainPanel.add(toolbar.component, BorderLayout.NORTH)

        // Tree setup.
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = MemoryTreeCellRenderer()

        // Double-click to open file.
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    val userObj = node.userObject
                    if (userObj is MemoryFile) {
                        openOrCreateFile(userObj)
                    } else if (userObj is NewRuleAction) {
                        createNewRule(userObj.category, userObj.baseDir)
                    }
                }
            }
        })

        // Right-click context menu.
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val path = tree.getPathForLocation(x, y) ?: return
                tree.selectionPath = path
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val userObj = node.userObject
                if (userObj is MemoryFile) {
                    showContextMenu(userObj, comp, x, y)
                }
            }
        })

        // Empty state.
        emptyLabel.foreground = JBColor.GRAY
        emptyLabel.border = JBUI.Borders.empty(20)

        // Card layout.
        centerPanel.add(JScrollPane(tree), "tree")
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
            add(object : AnAction("Refresh", "Reload memory files", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    service.refresh()
                    showStatus("Refreshed")
                }
            })
            add(object : AnAction("New User Rule", "Create a new user rule file", AllIcons.General.Add) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val home = System.getProperty("user.home")
                    val rulesDir = Path.of(home, ".claude", "rules")
                    createNewRule(MemoryCategory.USER_RULES, rulesDir)
                }
            })
            add(object : AnAction("New Project Rule", "Create a new project rule file", AllIcons.Nodes.Module) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val projectPath = project.basePath ?: return
                    val rulesDir = Path.of(projectPath, ".claude", "rules")
                    createNewRule(MemoryCategory.PROJECT_RULES, rulesDir)
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MemoryViewerToolbar", group, true)
        toolbar.targetComponent = mainPanel
        return toolbar
    }

    private fun showContextMenu(memoryFile: MemoryFile, comp: Component, x: Int, y: Int) {
        val group = DefaultActionGroup().apply {
            if (memoryFile.exists) {
                add(object : AnAction("Open in Editor", "Open this file in the editor", AllIcons.Actions.MenuOpen) {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                    override fun actionPerformed(e: AnActionEvent) {
                        openFile(memoryFile.path)
                    }
                })
                addSeparator()
                add(object : AnAction("Reveal in Finder", "Show in file manager", AllIcons.Actions.ShowAsTree) {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                    override fun actionPerformed(e: AnActionEvent) {
                        revealInFinder(memoryFile.path)
                    }
                })
                // Allow deletion for rule files only.
                if (memoryFile.category == MemoryCategory.USER_RULES ||
                    memoryFile.category == MemoryCategory.PROJECT_RULES
                ) {
                    addSeparator()
                    add(object : AnAction("Delete", "Delete this rule file", AllIcons.General.Remove) {
                        override fun getActionUpdateThread() = ActionUpdateThread.EDT
                        override fun actionPerformed(e: AnActionEvent) {
                            val confirm = Messages.showYesNoDialog(
                                project,
                                "Delete '${memoryFile.displayName}'?",
                                "Delete Rule File",
                                Messages.getWarningIcon()
                            )
                            if (confirm == Messages.YES) {
                                service.deleteFile(memoryFile.path)
                                showStatus("Deleted ${memoryFile.displayName}")
                            }
                        }
                    })
                }
            } else {
                add(object : AnAction("Create File", "Create this file", AllIcons.General.Add) {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                    override fun actionPerformed(e: AnActionEvent) {
                        openOrCreateFile(memoryFile)
                    }
                })
            }
        }

        val popupMenu = ActionManager.getInstance()
            .createActionPopupMenu("MemoryViewerContext", group)
        popupMenu.component.show(comp, x, y)
    }

    private fun openOrCreateFile(memoryFile: MemoryFile) {
        if (memoryFile.exists) {
            openFile(memoryFile.path)
        } else {
            service.createFile(memoryFile.path)
            // Wait briefly for file creation, then open.
            Timer(300) {
                ApplicationManager.getApplication().invokeLater {
                    openFile(memoryFile.path)
                }
            }.apply {
                isRepeats = false
                start()
            }
            showStatus("Created ${memoryFile.displayName}")
        }
    }

    private fun openFile(path: Path) {
        val vfs = LocalFileSystem.getInstance()
        vfs.refreshAndFindFileByNioFile(path)?.let { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }

    private fun revealInFinder(path: Path) {
        val file = path.toFile()
        if (file.exists()) {
            com.intellij.ide.actions.RevealFileAction.openFile(file)
        }
    }

    private fun createNewRule(category: MemoryCategory, baseDir: Path) {
        val name = Messages.showInputDialog(
            project,
            "Enter rule file name (without .md extension):",
            "New Rule File",
            AllIcons.General.Add
        ) ?: return

        if (name.isBlank()) return

        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "-")
        val filePath = baseDir.resolve("$sanitized.md")

        service.createFile(filePath, "# $name\n\n")
        Timer(300) {
            ApplicationManager.getApplication().invokeLater {
                openFile(filePath)
            }
        }.apply {
            isRepeats = false
            start()
        }
        showStatus("Created $sanitized.md")
    }

    private fun loadFiles() {
        val files = service.getMemoryFiles()
        rebuildTree(files)
    }

    private fun rebuildTree(files: List<MemoryFile>) {
        // Save expanded state.
        val expandedCategories = mutableSetOf<String>()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val userObj = child.userObject
            if (userObj is String && tree.isExpanded(TreePath(arrayOf(rootNode, child)))) {
                expandedCategories.add(userObj)
            }
        }

        rootNode.removeAllChildren()

        if (files.isEmpty()) {
            treeModel.reload()
            cardLayout.show(centerPanel, "empty")
            return
        }

        // Group by category.
        val grouped = files.groupBy { it.category }

        for (category in MemoryCategory.entries) {
            val categoryFiles = grouped[category] ?: emptyList()

            // Always show categories that have files or are expected (User Memory, Project Memory).
            if (categoryFiles.isEmpty() &&
                category != MemoryCategory.USER_MEMORY &&
                category != MemoryCategory.PROJECT_MEMORY
            ) continue

            val categoryNode = DefaultMutableTreeNode(category.label)

            for (file in categoryFiles) {
                categoryNode.add(DefaultMutableTreeNode(file))
            }

            // Add "New rule..." action node for rule categories.
            if (category == MemoryCategory.USER_RULES) {
                val home = System.getProperty("user.home")
                val rulesDir = Path.of(home, ".claude", "rules")
                categoryNode.add(DefaultMutableTreeNode(NewRuleAction(category, rulesDir)))
            } else if (category == MemoryCategory.PROJECT_RULES) {
                val projectPath = project.basePath
                if (projectPath != null) {
                    val rulesDir = Path.of(projectPath, ".claude", "rules")
                    categoryNode.add(DefaultMutableTreeNode(NewRuleAction(category, rulesDir)))
                }
            }

            rootNode.add(categoryNode)
        }

        treeModel.reload()

        // Restore expanded state, or expand all on first load.
        if (expandedCategories.isEmpty()) {
            // First load: expand all categories.
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
                tree.expandPath(TreePath(arrayOf(rootNode, child)))
            }
        } else {
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
                if (child.userObject as? String in expandedCategories) {
                    tree.expandPath(TreePath(arrayOf(rootNode, child)))
                }
            }
        }

        cardLayout.show(centerPanel, "tree")
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

    private fun subscribeToChanges() {
        project.messageBus.connect(parentDisposable)
            .subscribe(MemoryService.MEMORY_CHANGED_TOPIC,
                object : MemoryChangedListener {
                    override fun onMemoryChanged() {
                        ApplicationManager.getApplication().invokeLater {
                            loadFiles()
                        }
                    }
                })
    }

    /**
     * Marker object for "New rule..." action nodes in the tree.
     */
    private data class NewRuleAction(val category: MemoryCategory, val baseDir: Path)

    /**
     * Custom cell renderer for the memory tree.
     */
    private class MemoryTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

            val node = value as? DefaultMutableTreeNode ?: return component
            val userObj = node.userObject

            when (userObj) {
                is String -> {
                    // Category header.
                    text = userObj
                    icon = AllIcons.Nodes.Folder
                    font = font.deriveFont(Font.BOLD)
                }
                is MemoryFile -> {
                    if (userObj.exists) {
                        text = userObj.displayName
                        icon = AllIcons.FileTypes.Text
                        font = font.deriveFont(Font.PLAIN)
                        foreground = if (sel) textSelectionColor else textNonSelectionColor
                    } else {
                        text = "${userObj.displayName}  (create)"
                        icon = AllIcons.FileTypes.Text
                        font = font.deriveFont(Font.ITALIC)
                        foreground = if (sel) textSelectionColor else JBColor.GRAY
                    }
                }
                is NewRuleAction -> {
                    text = "+ New rule..."
                    icon = AllIcons.General.Add
                    font = font.deriveFont(Font.ITALIC)
                    foreground = if (sel) textSelectionColor else JBColor.GRAY
                }
            }

            return component
        }
    }
}
