package com.example.anthropic.settings

import com.example.anthropic.api.AnthropicApiService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import javax.swing.*

class AnthropicSettingsComponent {
    val panel: JPanel
    private val sessionKeyField = JBPasswordField()
    private val organizationIdField = JBTextField()
    private val refreshIntervalField = JBTextField()
    private val showNotificationsCheckbox = JCheckBox("Show usage notifications")
    private val notifyAtPercentageField = JBTextField()
    private val testConnectionButton = JButton("Test Connection")
    private val statusLabel = JBLabel()

    init {
        // Set default values
        refreshIntervalField.text = "5"
        notifyAtPercentageField.text = "90"

        // Create help labels
        val sessionKeyHelpLabel = JBLabel("<html><i>Get your sessionKey cookie from " +
                "<a href='https://claude.ai'>claude.ai</a>" +
                " (see instructions below)</i></html>")
        sessionKeyHelpLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val orgIdHelpLabel = JBLabel("<html><i>Find your organization ID in the Network tab " +
                "(format: 14038684-39d3-451d-99ca-0db1367c3edd)</i></html>")

        // Build the form
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Session Key:", sessionKeyField, 1, false)
            .addComponentToRightColumn(sessionKeyHelpLabel, 0)
            .addVerticalGap(10)
            .addLabeledComponent("Organization ID:", organizationIdField, 1, false)
            .addComponentToRightColumn(orgIdHelpLabel, 0)
            .addVerticalGap(15)
            .addSeparator()
            .addVerticalGap(10)
            .addLabeledComponent("Refresh interval (minutes):", refreshIntervalField, 1, false)
            .addVerticalGap(10)
            .addComponent(showNotificationsCheckbox)
            .addLabeledComponent("Notify at percentage:", notifyAtPercentageField, 1, false)
            .addVerticalGap(15)
            .addComponent(createTestConnectionPanel())
            .addVerticalGap(15)
            .addSeparator()
            .addVerticalGap(10)
            .addComponent(createInstructionsPanel())
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Add test connection button listener
        testConnectionButton.addActionListener {
            testConnection()
        }
    }

    private fun createTestConnectionPanel(): JPanel {
        val testPanel = JPanel(BorderLayout())
        testPanel.add(testConnectionButton, BorderLayout.WEST)
        testPanel.add(statusLabel, BorderLayout.CENTER)
        testPanel.border = JBUI.Borders.emptyTop(10)
        return testPanel
    }

    private fun createInstructionsPanel(): JPanel {
        val instructionsText = """
            <html>
            <h3>How to get your Session Key:</h3>
            <ol>
              <li>Open <a href='https://claude.ai'>claude.ai</a> in your browser and log in</li>
              <li>Press <b>F12</b> to open DevTools</li>
              <li>Go to <b>Application</b> tab → <b>Cookies</b> → https://claude.ai</li>
              <li>Find the <b>sessionKey</b> cookie and copy its value</li>
              <li>Paste it in the "Session Key" field above</li>
            </ol>
            <h3>How to get your Organization ID:</h3>
            <ol>
              <li>While on <a href='https://claude.ai/settings/usage'>claude.ai/settings/usage</a></li>
              <li>Open DevTools (<b>F12</b>) → <b>Network</b> tab</li>
              <li>Refresh the page (<b>F5</b>)</li>
              <li>Look for a request to <b>/api/organizations/{id}/usage</b></li>
              <li>Copy the UUID from the URL (format: 14038684-39d3-451d-99ca-0db1367c3edd)</li>
              <li>Paste it in the "Organization ID" field above</li>
            </ol>
            <p><b>⚠️ Warning:</b> This uses unofficial API. Use at your own risk.</p>
            </html>
        """.trimIndent()

        val instructionsLabel = JBLabel(instructionsText)
        val instructionsPanel = JPanel(BorderLayout())
        instructionsPanel.add(instructionsLabel, BorderLayout.CENTER)
        instructionsPanel.border = JBUI.Borders.empty(10)
        return instructionsPanel
    }

    var sessionKey: String?
        get() = String(sessionKeyField.password).takeIf { it.isNotBlank() }
        set(value) {
            sessionKeyField.text = value ?: ""
        }

    var organizationId: String
        get() = organizationIdField.text
        set(value) {
            organizationIdField.text = value
        }

    var refreshInterval: Int
        get() = refreshIntervalField.text.toIntOrNull() ?: 5
        set(value) {
            refreshIntervalField.text = value.toString()
        }

    var showNotifications: Boolean
        get() = showNotificationsCheckbox.isSelected
        set(value) {
            showNotificationsCheckbox.isSelected = value
        }

    var notifyAtPercentage: Int
        get() = notifyAtPercentageField.text.toIntOrNull() ?: 90
        set(value) {
            notifyAtPercentageField.text = value.toString()
        }

    private fun testConnection() {
        val key = sessionKey
        val orgId = organizationId

        if (key.isNullOrBlank()) {
            setStatus("Please enter a session key", StatusType.ERROR)
            return
        }

        if (orgId.isBlank()) {
            setStatus("Please enter an organization ID", StatusType.ERROR)
            return
        }

        testConnectionButton.isEnabled = false
        setStatus("Testing connection...", StatusType.INFO)

        // Test in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            null, "Testing Claude.ai Connection", true
        ) {
            var success = false
            var errorMessage = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Connecting to claude.ai..."

                val apiService = AnthropicApiService()
                apiService.initialize(key, orgId)

                val result = runBlocking {
                    apiService.testConnection()
                }

                success = result.isSuccess
                if (result.isFailure) {
                    errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                }
            }

            override fun onSuccess() {
                testConnectionButton.isEnabled = true
                if (success) {
                    setStatus("✓ Connection successful", StatusType.SUCCESS)
                } else {
                    setStatus("✗ Connection failed: $errorMessage", StatusType.ERROR)
                }
            }

            override fun onThrowable(error: Throwable) {
                testConnectionButton.isEnabled = true
                setStatus("✗ Connection failed: ${error.message}", StatusType.ERROR)
            }

            override fun onCancel() {
                testConnectionButton.isEnabled = true
                setStatus("Connection test cancelled", StatusType.INFO)
            }
        })
    }

    private fun setStatus(message: String, type: StatusType) {
        ApplicationManager.getApplication().invokeLater {
            statusLabel.text = message
            statusLabel.foreground = when (type) {
                StatusType.SUCCESS -> Color(0, 128, 0)  // Green
                StatusType.ERROR -> Color(255, 0, 0)     // Red
                StatusType.INFO -> Color(0, 0, 0)        // Black
            }
        }
    }

    private enum class StatusType {
        SUCCESS, ERROR, INFO
    }
}
