package com.example.anthropic.sessions.ui

import com.example.anthropic.sessions.model.SessionEntry
import com.example.anthropic.sessions.model.SessionListItem
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.*

/**
 * Custom cell renderer for session list items.
 * Renders headers as date group separators and sessions as card-style cells.
 */
class SessionCellRenderer(
    private val hoveredIndexProvider: () -> Int = { -1 }
) : ListCellRenderer<SessionListItem> {

    // Session card components.
    private val titleLabel = JLabel()
    private val subtitleLabel = JLabel()
    private val sessionPanel = JPanel(BorderLayout())

    // Header components.
    private val headerPanel = JPanel(BorderLayout())
    private val headerLabel = JLabel()
    private val leftSeparator = JSeparator()
    private val rightSeparator = JSeparator()

    init {
        // Session card setup.
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 13f)
        subtitleLabel.font = subtitleLabel.font.deriveFont(Font.PLAIN, 11f)
        subtitleLabel.foreground = JBColor.GRAY

        val textPanel = JPanel()
        textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
        textPanel.isOpaque = false
        textPanel.add(titleLabel)
        textPanel.add(Box.createVerticalStrut(2))
        textPanel.add(subtitleLabel)

        sessionPanel.add(textPanel, BorderLayout.CENTER)
        sessionPanel.border = JBUI.Borders.empty(6, 10, 6, 10)

        // Header setup.
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 12f)
        headerLabel.foreground = JBColor(Color(80, 80, 80), Color(180, 180, 180))

        val gbl = java.awt.GridBagLayout()
        val headerContent = JPanel(gbl)
        headerContent.isOpaque = false

        val gbc = java.awt.GridBagConstraints()

        // Left separator.
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.gridy = 0
        gbc.gridx = 0
        headerContent.add(leftSeparator, gbc)

        // Label.
        gbc.fill = java.awt.GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.gridx = 1
        headerLabel.border = JBUI.Borders.empty(0, 8, 0, 8)
        headerContent.add(headerLabel, gbc)

        // Right separator.
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.gridx = 2
        headerContent.add(rightSeparator, gbc)

        headerPanel.add(headerContent, BorderLayout.CENTER)
        headerPanel.border = JBUI.Borders.empty(10, 10, 4, 10)
    }

    override fun getListCellRendererComponent(
        list: JList<out SessionListItem>,
        value: SessionListItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return when (value) {
            is SessionListItem.Header -> renderHeader(value, list)
            is SessionListItem.Session -> renderSession(value.entry, list, index, isSelected)
        }
    }

    private fun renderHeader(header: SessionListItem.Header, list: JList<*>): Component {
        headerLabel.text = header.title.uppercase()
        headerPanel.background = list.background
        headerPanel.isOpaque = true
        return headerPanel
    }

    private fun renderSession(
        value: SessionEntry,
        list: JList<*>,
        index: Int,
        isSelected: Boolean
    ): Component {
        // Title.
        titleLabel.text = value.displayTitle
        titleLabel.icon = AllIcons.Actions.Run_anything

        // Subtitle: branch · msgs · date.
        val parts = mutableListOf<String>()
        value.gitBranch?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        parts.add("${value.messageCount} msgs")
        parts.add(formatRelativeDate(value.modified))
        subtitleLabel.text = parts.joinToString("  ·  ")

        // Selection and hover colors.
        val isHovered = index == hoveredIndexProvider()
        if (isSelected) {
            sessionPanel.background = list.selectionBackground
            titleLabel.foreground = list.selectionForeground
            subtitleLabel.foreground = list.selectionForeground
        } else if (isHovered) {
            sessionPanel.background = JBColor(
                UIUtil.getListBackground().brighter(),
                UIUtil.getListBackground().brighter()
            )
            titleLabel.foreground = list.foreground
            subtitleLabel.foreground = JBColor.GRAY
        } else {
            sessionPanel.background = list.background
            titleLabel.foreground = list.foreground
            subtitleLabel.foreground = JBColor.GRAY
        }
        sessionPanel.isOpaque = true

        return sessionPanel
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")
            .withZone(ZoneId.systemDefault())

        fun formatRelativeDate(isoDate: String): String {
            return try {
                val instant = Instant.parse(isoDate)
                val now = Instant.now()
                val minutesAgo = ChronoUnit.MINUTES.between(instant, now)

                when {
                    minutesAgo < 1 -> "just now"
                    minutesAgo < 60 -> "${minutesAgo}m ago"
                    minutesAgo < 1440 -> "${minutesAgo / 60}h ago"
                    minutesAgo < 2880 -> "yesterday"
                    else -> dateFormatter.format(instant)
                }
            } catch (e: Exception) {
                isoDate.take(10)
            }
        }
    }
}
