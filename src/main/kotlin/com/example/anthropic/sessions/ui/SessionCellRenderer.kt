package com.example.anthropic.sessions.ui

import com.example.anthropic.sessions.model.SessionEntry
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.*

/**
 * Custom cell renderer for session list items.
 * Renders a card-style cell with summary title and metadata subtitle.
 */
class SessionCellRenderer(
    private val hoveredIndexProvider: () -> Int = { -1 }
) : ListCellRenderer<SessionEntry> {

    private val titleLabel = JLabel()
    private val subtitleLabel = JLabel()
    private val panel = JPanel(BorderLayout())

    init {
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 13f)
        subtitleLabel.font = subtitleLabel.font.deriveFont(Font.PLAIN, 11f)
        subtitleLabel.foreground = JBColor.GRAY

        val textPanel = JPanel()
        textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
        textPanel.isOpaque = false
        textPanel.add(titleLabel)
        textPanel.add(Box.createVerticalStrut(2))
        textPanel.add(subtitleLabel)

        panel.add(textPanel, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(6, 10, 6, 10)
    }

    override fun getListCellRendererComponent(
        list: JList<out SessionEntry>,
        value: SessionEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
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
            panel.background = list.selectionBackground
            titleLabel.foreground = list.selectionForeground
            subtitleLabel.foreground = list.selectionForeground
        } else if (isHovered) {
            panel.background = JBColor(
                UIUtil.getListBackground().brighter(),
                UIUtil.getListBackground().brighter()
            )
            titleLabel.foreground = list.foreground
            subtitleLabel.foreground = JBColor.GRAY
        } else {
            panel.background = list.background
            titleLabel.foreground = list.foreground
            subtitleLabel.foreground = JBColor.GRAY
        }
        panel.isOpaque = true

        return panel
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
