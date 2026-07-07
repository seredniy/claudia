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
import javax.swing.PopupFactory
import javax.swing.*

class AnthropicUsageWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget.Multiframe {
    private val panel = CustomProgressPanel()
    private val usageService = service<AnthropicUsageService>()
    private val settings = service<AnthropicSettingsState>()
    private var currentData: UsageData? = null
    private var tooltipPopup: javax.swing.Popup? = null

    init {
        setupUI()
        subscribeToUsageUpdates()

        // Load initial data if available.
        usageService.getCurrentUsage()?.let { data ->
            currentData = data
            updateUI(data)
        }
    }

    override fun ID() = "com.sercraft.claudia.statusbar.widget"

    override fun getComponent(): JComponent = panel

    override fun copy(): StatusBarWidget {
        return AnthropicUsageWidget(project)
    }

    private fun setupUI() {
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                hideTooltipPopup()
                if (SwingUtilities.isLeftMouseButton(e)) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, AnthropicSettingsConfigurable::class.java)
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e)
                }
            }

            override fun mouseEntered(e: MouseEvent?) {
                panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                showTooltipPopup()
            }

            override fun mouseExited(e: MouseEvent?) {
                panel.cursor = Cursor.getDefaultCursor()
                hideTooltipPopup()
            }
        })
    }

    private fun showTooltipPopup() {
        hideTooltipPopup()
        val data = currentData ?: return
        try {
            val content = UsageTooltipContent(data)
            val loc = panel.locationOnScreen
            val popupX = loc.x
            val popupY = loc.y - content.preferredSize.height - 4
            tooltipPopup = PopupFactory.getSharedInstance().getPopup(panel, content, popupX, popupY)
            tooltipPopup?.show()
        } catch (e: Exception) {
            // Panel may not be showing yet — ignore.
        }
    }

    private fun hideTooltipPopup() {
        tooltipPopup?.hide()
        tooltipPopup = null
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

        // Calculate time progress for the line.
        val isFiveHour = settings.displayMode == AnthropicSettingsState.UsageDisplayMode.FIVE_HOUR
        val timeProgress = data.calculateTimeProgress(java.time.Instant.now(), isFiveHour)

        // Color based on usage percentage and time progress.
        // Use raw utilization for precise color threshold evaluation.
        val utilization = if (isFiveHour) data.fiveHourUtilization else data.sevenDayUtilization
        val color = UsageColorLogic.getColor(utilization, timeProgress, settings.timeBasedColoring)

        panel.updateUsage(percentage, text, color, timeProgress)
        panel.isVisible = true
    }

    private fun showError(error: String) {
        panel.updateUsage(0, "Error", JBColor.RED)
        panel.toolTipText = "Failed to fetch usage data: $error\nClick to open settings"
        panel.isVisible = true
    }

    override fun install(statusBar: StatusBar) {
        // Widget installed in status bar.
    }

    override fun dispose() {
        Disposer.dispose(this)
    }

    /**
     * Shared bar painting logic used by both the status bar widget and the tooltip.
     * Draws: background, filled progress, border, time-progress line, centered text.
     */
    private fun paintBar(
        g2: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        percentage: Int,
        timeProgress: Double,
        text: String,
        barColor: Color,
        isDark: Boolean
    ) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Draw background.
        g2.color = if (isDark) Gray._85 else Gray._200
        g2.fillRoundRect(x, y, width, height, 3, 3)

        // Draw filled progress.
        if (percentage > 0) {
            val filledWidth = (width * percentage / 100).coerceAtLeast(0)
            g2.color = barColor
            g2.fillRoundRect(x, y, filledWidth, height, 3, 3)
        }

        // Draw border.
        g2.color = if (isDark) Gray._70 else Gray._180
        g2.drawRoundRect(x, y, width - 1, height - 1, 3, 3)

        // Draw time progress line (purple).
        if (settings.timeBasedColoring && timeProgress > 0) {
            val lineX = x + (width * timeProgress / 100).toInt().coerceIn(0, width - 1)
            g2.color = if (isDark) Color(180, 80, 255) else Color(128, 0, 128)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(lineX, y, lineX, y + height - 1)
        }

        // Draw text centered over the full bar width.
        g2.color = if (isDark) Gray._220 else Gray._50
        g2.font = JBUI.Fonts.toolbarSmallComboBoxFont()

        val fontMetrics = g2.fontMetrics
        val textWidth = fontMetrics.stringWidth(text)
        val textHeight = fontMetrics.ascent
        val textX = x + (width - textWidth) / 2
        val textY = y + (height + textHeight) / 2 - 1

        g2.drawString(text, textX, textY)
    }

    /**
     * Computes the bar color based on usage percentage and time progress.
     */
    private fun barColor(pct: Double, timeProgress: Double, isDark: Boolean): Color {
        if (settings.timeBasedColoring) {
            return UsageColorLogic.getColor(pct, timeProgress, true)
        }
        return when {
            pct >= 90 -> if (isDark) Color(180, 40, 40) else Color(200, 50, 50)
            pct >= 75 -> if (isDark) Color(200, 130, 0) else Color(255, 165, 0)
            else -> if (isDark) Color(80, 170, 80) else Color(50, 150, 50)
        }
    }

    /**
     * Custom panel with progress bar painting (like Memory Indicator).
     */
    private inner class CustomProgressPanel : JPanel() {
        private var percentage: Int = 0
        private var timeProgress: Double = 0.0
        private var text: String = "Loading..."
        private var barColor: Color = JBColor.GREEN

        init {
            preferredSize = Dimension(130, 20)
            border = JBUI.Borders.empty(0, 2)
            isOpaque = false
        }

        fun updateUsage(percentage: Int, text: String, color: Color, timeProgress: Double = 0.0) {
            this.percentage = percentage
            this.text = text
            this.barColor = color
            this.timeProgress = timeProgress
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            val insets = insets
            val width = getWidth() - insets.left - insets.right
            val height = getHeight() - insets.top - insets.bottom

            paintBar(g2, 0, 0, width, height, percentage, timeProgress, text, barColor, UIUtil.isUnderDarcula())

            g2.dispose()
        }
    }

    /**
     * Custom JPanel that renders usage tooltip content using Java2D.
     * Embedded inside a standard JToolTip for correct sizing.
     */
    private inner class UsageTooltipContent(private val data: UsageData) : JPanel() {
        private val BAR_WIDTH = 250
        private val BAR_HEIGHT = 16
        private val PAD = 10
        private val LINE_GAP = 4
        private val SECTION_GAP = 8

        init {
            isOpaque = true
            preferredSize = calculateSize()
        }

        private fun calculateSize(): Dimension {
            val g2 = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration
                .createCompatibleImage(1, 1).createGraphics()

            val titleFont = JBUI.Fonts.label().deriveFont(Font.BOLD)
            val labelFont = JBUI.Fonts.label().deriveFont(Font.BOLD)
            val smallFont = JBUI.Fonts.smallFont()

            g2.font = titleFont
            val titleH = g2.fontMetrics.height
            g2.font = labelFont
            val labelH = g2.fontMetrics.height
            g2.font = smallFont
            val smallH = g2.fontMetrics.height
            g2.dispose()

            var h = PAD + titleH + SECTION_GAP
            // 5-hour
            h += labelH + LINE_GAP + BAR_HEIGHT + SECTION_GAP
            // 7-day
            h += labelH + LINE_GAP + BAR_HEIGHT + LINE_GAP
            // reset info
            if (data.formattedSevenDayResetsAt != null) {
                h += smallH + LINE_GAP
            }
            // breakdown
            if (data.breakdown.isNotEmpty()) {
                h += SECTION_GAP
                for (entry in data.breakdown) {
                    h += labelH + LINE_GAP + BAR_HEIGHT + LINE_GAP
                }
            }
            // updated
            h += SECTION_GAP + smallH + PAD

            return Dimension(BAR_WIDTH + PAD * 2, h)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val isDark = UIUtil.isUnderDarcula()
            val w = width
            val h = height

            // Background.
            val bg = if (isDark) Color(60, 63, 65) else Color(245, 245, 245)
            g2.color = bg
            g2.fillRoundRect(0, 0, w, h, 6, 6)

            // Border.
            g2.color = if (isDark) Color(80, 83, 85) else Color(200, 200, 200)
            g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6)

            val textColor = if (isDark) Gray._220 else Gray._50
            val dimColor = if (isDark) Gray._140 else Gray._120

            val titleFont = JBUI.Fonts.label().deriveFont(Font.BOLD)
            val labelFont = JBUI.Fonts.label().deriveFont(Font.BOLD)
            val smallFont = JBUI.Fonts.smallFont()

            var curY = PAD
            val leftX = PAD

            val now = java.time.Instant.now()
            val fiveHourTP = data.calculateTimeProgress(now, isFiveHour = true)
            val sevenDayTP = data.calculateTimeProgress(now, isFiveHour = false)

            // ── Title ──
            g2.color = textColor
            g2.font = titleFont
            g2.drawString("Claude Usage", leftX, curY + g2.fontMetrics.ascent)
            curY += g2.fontMetrics.height + SECTION_GAP

            // ── 5-Hour Limit ──
            g2.color = textColor
            g2.font = labelFont
            g2.drawString("5-Hour Limit", leftX, curY + g2.fontMetrics.ascent)
            curY += g2.fontMetrics.height + LINE_GAP

            val fivePct = data.fiveHourUtilization.coerceIn(0.0, 100.0)
            val fiveLabel = String.format("%.1f%%", fivePct) +
                    (data.fiveHourTimeRemaining?.let { " · $it" } ?: "")
            val fiveColor = barColor(fivePct, fiveHourTP, isDark)
            paintBar(g2, leftX, curY, BAR_WIDTH, BAR_HEIGHT,
                fivePct.toInt(), fiveHourTP, fiveLabel, fiveColor, isDark)
            curY += BAR_HEIGHT + SECTION_GAP

            // ── 7-Day Limit ──
            g2.color = textColor
            g2.font = labelFont
            g2.drawString("7-Day Limit", leftX, curY + g2.fontMetrics.ascent)
            curY += g2.fontMetrics.height + LINE_GAP

            val sevenPct = data.sevenDayUtilization.coerceIn(0.0, 100.0)
            val sevenLabel = String.format("%.1f%%", sevenPct)
            val sevenColor = barColor(sevenPct, sevenDayTP, isDark)
            paintBar(g2, leftX, curY, BAR_WIDTH, BAR_HEIGHT,
                sevenPct.toInt(), sevenDayTP, sevenLabel, sevenColor, isDark)
            curY += BAR_HEIGHT + LINE_GAP

            // Reset info.
            data.formattedSevenDayResetsAt?.let { resetText ->
                g2.color = dimColor
                g2.font = smallFont
                g2.drawString("Resets $resetText", leftX, curY + g2.fontMetrics.ascent)
                curY += g2.fontMetrics.height + LINE_GAP
            }

            // ── Breakdown ──
            if (data.breakdown.isNotEmpty()) {
                curY += SECTION_GAP
                for ((model, usage) in data.breakdown) {
                    g2.color = textColor
                    g2.font = labelFont
                    g2.drawString(model, leftX, curY + g2.fontMetrics.ascent)
                    curY += g2.fontMetrics.height + LINE_GAP

                    val mPct = usage.utilization.coerceIn(0.0, 100.0)
                    val mLabel = String.format("%.1f%%", mPct)
                    val mColor = barColor(mPct, 0.0, isDark)
                    paintBar(g2, leftX, curY, BAR_WIDTH, BAR_HEIGHT,
                        mPct.toInt(), 0.0, mLabel, mColor, isDark)
                    curY += BAR_HEIGHT + LINE_GAP
                }
            }

            // ── Updated ──
            curY += SECTION_GAP
            val minutesAgo = java.time.Duration.between(data.lastUpdated, java.time.Instant.now()).toMinutes()
            val updatedText = when {
                minutesAgo > 60 -> "Data may be stale"
                minutesAgo < 2 -> "Updated just now"
                else -> "Updated ${minutesAgo}m ago"
            }
            g2.color = dimColor
            g2.font = smallFont
            g2.drawString(updatedText, leftX, curY + g2.fontMetrics.ascent)

            g2.dispose()
        }
    }
}

