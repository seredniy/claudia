package com.example.anthropic.statusbar

import com.example.anthropic.api.models.UsageData
import com.example.anthropic.services.AnthropicUsageService
import com.example.anthropic.services.UsageUpdateListener
import com.example.anthropic.settings.AnthropicSettingsConfigurable
import com.example.anthropic.settings.AnthropicSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class AnthropicUsageWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget.Multiframe {
    private val panel = CustomProgressPanel()
    private val usageService = service<AnthropicUsageService>()
    private val settings = service<AnthropicSettingsState>()
    private var currentData: UsageData? = null

    init {
        setupUI()
        subscribeToUsageUpdates()

        // Load initial data if available.
        usageService.getCurrentUsage()?.let { data ->
            currentData = data
            updateUI(data)
        }
    }

    override fun ID() = "com.example.anthropic.statusbar.widget"

    override fun getComponent(): JComponent = panel

    override fun copy(): StatusBarWidget {
        return AnthropicUsageWidget(project)
    }

    private fun setupUI() {
        panel.toolTipText = "Claude Usage - Click for details"

        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // Left click - open settings.
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, AnthropicSettingsConfigurable::class.java)
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    // Right click - show context menu.
                    showContextMenu(e)
                }
            }

            override fun mouseEntered(e: MouseEvent?) {
                panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }

            override fun mouseExited(e: MouseEvent?) {
                panel.cursor = Cursor.getDefaultCursor()
            }
        })
    }

    private fun showContextMenu(e: MouseEvent) {
        val popup = JPopupMenu()

        // Display mode options.
        val fiveHourItem = JCheckBoxMenuItem("Show 5-hour limit")
        fiveHourItem.isSelected = settings.displayMode == AnthropicSettingsState.UsageDisplayMode.FIVE_HOUR
        fiveHourItem.addActionListener {
            settings.displayMode = AnthropicSettingsState.UsageDisplayMode.FIVE_HOUR
            currentData?.let { updateUI(it) }
        }

        val sevenDayItem = JCheckBoxMenuItem("Show 7-day limit")
        sevenDayItem.isSelected = settings.displayMode == AnthropicSettingsState.UsageDisplayMode.SEVEN_DAY
        sevenDayItem.addActionListener {
            settings.displayMode = AnthropicSettingsState.UsageDisplayMode.SEVEN_DAY
            currentData?.let { updateUI(it) }
        }

        popup.add(fiveHourItem)
        popup.add(sevenDayItem)
        popup.addSeparator()

        // Refresh action.
        val refreshItem = JMenuItem("Refresh")
        refreshItem.addActionListener {
            usageService.forceRefresh()
        }
        popup.add(refreshItem)

        // Settings action.
        val settingsItem = JMenuItem("Settings...")
        settingsItem.addActionListener {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, AnthropicSettingsConfigurable::class.java)
        }
        popup.add(settingsItem)

        popup.show(e.component, e.x, e.y)
    }

    private fun subscribeToUsageUpdates() {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AnthropicUsageService.USAGE_TOPIC, object : UsageUpdateListener {
                override fun onUsageUpdated(data: UsageData) {
                    ApplicationManager.getApplication().invokeLater {
                        currentData = data
                        updateUI(data)
                    }
                }

                override fun onError(error: String) {
                    ApplicationManager.getApplication().invokeLater {
                        showError(error)
                    }
                }
            })
    }

    private fun updateUI(data: UsageData) {
        // Get percentage based on display mode.
        val (percentage, timeRemaining, label) = when (settings.displayMode) {
            AnthropicSettingsState.UsageDisplayMode.FIVE_HOUR -> {
                Triple(
                    data.fiveHourUtilization.toInt().coerceIn(0, 100),
                    data.fiveHourTimeRemaining,
                    "5h"
                )
            }
            AnthropicSettingsState.UsageDisplayMode.SEVEN_DAY -> {
                Triple(
                    data.sevenDayUtilization.toInt().coerceIn(0, 100),
                    null,  // No time remaining for 7-day.
                    "7d"
                )
            }
        }

        // Format: "5h: 35% • 2h 15m" or "7d: 12%".
        val text = if (timeRemaining != null) {
            "$label: $percentage% • $timeRemaining"
        } else {
            "$label: $percentage%"
        }

        // Color based on usage percentage.
        val color = when {
            percentage >= 90 -> JBColor(Color(200, 50, 50), Color(180, 40, 40))      // Red.
            percentage >= 75 -> JBColor(Color(255, 165, 0), Color(200, 130, 0))      // Orange.
            else -> JBColor(Color(50, 150, 50), Color(80, 170, 80))                  // Green.
        }

        panel.updateUsage(percentage, text, color)
        panel.toolTipText = buildTooltip(data)
        panel.isVisible = true
    }

    private fun showError(error: String) {
        panel.updateUsage(0, "Error", JBColor.RED)
        panel.toolTipText = "Failed to fetch usage data: $error\nClick to open settings"
        panel.isVisible = true
    }

    private fun buildTooltip(data: UsageData): String {
        val breakdown = if (data.breakdown.isNotEmpty()) {
            val modelBreakdown = data.breakdown.entries.joinToString("<br/>") { (model, usage) ->
                "&nbsp;&nbsp;- $model: ${String.format("%.1f", usage.utilization)}%"
            }
            "<br/><b>By Model:</b><br/>$modelBreakdown"
        } else {
            ""
        }

        return """
            <html>
            <b>Claude Usage</b><br/>
            <br/>
            <b>5-Hour Limit:</b> ${String.format("%.1f", data.fiveHourUtilization)}%<br/>
            ${data.formattedFiveHourResetsAt?.let { "&nbsp;&nbsp;Resets at: $it<br/>" } ?: ""}
            ${data.fiveHourTimeRemaining?.let { "&nbsp;&nbsp;Time remaining: $it<br/>" } ?: ""}
            <br/>
            <b>7-Day Limit:</b> ${String.format("%.1f", data.sevenDayUtilization)}%<br/>
            ${data.formattedSevenDayResetsAt?.let { "&nbsp;&nbsp;Resets at: $it<br/>" } ?: ""}
            <br/>
            <i>Last Updated: ${data.formattedLastUpdate}</i>
            $breakdown
            <br/>
            <br/>
            <i>Left-click: Settings | Right-click: Options</i>
            </html>
        """.trimIndent()
    }

    override fun install(statusBar: StatusBar) {
        // Widget installed in status bar.
    }

    override fun dispose() {
        Disposer.dispose(this)
    }

    /**
     * Custom panel with progress bar painting (like Memory Indicator).
     */
    private class CustomProgressPanel : JPanel() {
        private var percentage: Int = 0
        private var text: String = "Loading..."
        private var barColor: Color = JBColor.GREEN

        init {
            preferredSize = Dimension(130, 20)
            border = JBUI.Borders.empty(0, 2)
            isOpaque = false
        }

        fun updateUsage(percentage: Int, text: String, color: Color) {
            this.percentage = percentage
            this.text = text
            this.barColor = color
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val insets = insets
            val width = getWidth() - insets.left - insets.right
            val height = getHeight() - insets.top - insets.bottom
            val x = insets.left
            val y = insets.top

            // Draw background.
            g2.color = if (UIUtil.isUnderDarcula()) Gray._85 else Gray._200
            g2.fillRoundRect(x, y, width, height, 3, 3)

            // Draw filled progress.
            if (percentage > 0) {
                val filledWidth = (width * percentage / 100).coerceAtLeast(0)
                g2.color = barColor
                g2.fillRoundRect(x, y, filledWidth, height, 3, 3)
            }

            // Draw border.
            g2.color = if (UIUtil.isUnderDarcula()) Gray._70 else Gray._180
            g2.drawRoundRect(x, y, width - 1, height - 1, 3, 3)

            // Draw text centered.
            g2.color = if (UIUtil.isUnderDarcula()) Gray._220 else Gray._50
            g2.font = JBUI.Fonts.toolbarSmallComboBoxFont()

            val fontMetrics = g2.fontMetrics
            val textWidth = fontMetrics.stringWidth(text)
            val textHeight = fontMetrics.ascent
            val textX = x + (width - textWidth) / 2
            val textY = y + (height + textHeight) / 2 - 1

            g2.drawString(text, textX, textY)

            g2.dispose()
        }
    }
}
