package com.example.anthropic.settings

import com.example.anthropic.api.AnthropicApiService
import com.example.anthropic.api.ClaudeCredentialDiscovery
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

class AnthropicSettingsComponent {
    val panel: JPanel

    // Feature toggles.
    private val enableSessionBrowserCheckbox = JBCheckBox("Session Browser")
    private val enableUsageBarCheckbox = JBCheckBox("Usage Bar")
    private val enableSendToClaudeCheckbox = JBCheckBox("Send to Claude Code")

    // Credentials.
    private val useManualTokenCheckbox = JBCheckBox("Use manual access token")
    private val accessTokenField = JBPasswordField()
    private val autoDiscoveryStatusLabel = JBLabel()
    private val checkCredentialsButton = JButton("Check Credentials")

    // Common fields.
    private val refreshIntervalField = JBTextField()
    private val showNotificationsCheckbox = JCheckBox("Show usage notifications")
    private val notifyAtPercentageField = JBTextField()
    private val testConnectionButton = JButton("Test Connection")
    private val statusLabel = JBLabel()

    // Reset time display mode.
    private val showTimeRemainingRadio = JRadioButton("Show time remaining (e.g. 2h 15m)")
    private val showResetTimeRadio = JRadioButton("Show reset time (e.g. 3:15 PM)")

    // All controls in the "Usage Bar Settings" section that should be disabled when the toggle is off.
    private val usageBarSettingsControls = mutableListOf<JComponent>()

    private val credentialDiscovery = ClaudeCredentialDiscovery()

    init {
        // Set default values.
        refreshIntervalField.text = "5"
        notifyAtPercentageField.text = "90"

        // Feature toggles default to checked.
        enableSessionBrowserCheckbox.isSelected = true
        enableUsageBarCheckbox.isSelected = true
        enableSendToClaudeCheckbox.isSelected = true

        // Group reset time radio buttons.
        val resetTimeDisplayGroup = ButtonGroup()
        resetTimeDisplayGroup.add(showTimeRemainingRadio)
        resetTimeDisplayGroup.add(showResetTimeRadio)
        showTimeRemainingRadio.isSelected = true

        // Access token field enabled state depends on checkbox.
        accessTokenField.isEnabled = false
        useManualTokenCheckbox.addActionListener {
            accessTokenField.isEnabled = useManualTokenCheckbox.isSelected
            checkCredentialsButton.isEnabled = !useManualTokenCheckbox.isSelected
            if (!useManualTokenCheckbox.isSelected) {
                checkOAuthCredentials()
            }
        }

        checkCredentialsButton.addActionListener {
            checkOAuthCredentials()
        }

        // Usage Bar toggle controls the enabled state of the settings section.
        enableUsageBarCheckbox.addActionListener {
            updateUsageBarSettingsState()
        }

        // Collect all controls that belong to the Usage Bar Settings section.
        usageBarSettingsControls.addAll(listOf(
            useManualTokenCheckbox, accessTokenField, autoDiscoveryStatusLabel,
            checkCredentialsButton, refreshIntervalField, showNotificationsCheckbox,
            notifyAtPercentageField, testConnectionButton,
            showTimeRemainingRadio, showResetTimeRadio
        ))

        // Build the form.
        panel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Features</b></html>"))
            .addVerticalGap(5)
            .addComponent(enableSessionBrowserCheckbox)
            .addComponent(enableUsageBarCheckbox)
            .addComponent(enableSendToClaudeCheckbox)
            .addVerticalGap(15)
            .addSeparator()
            .addVerticalGap(10)
            .addComponent(JBLabel("<html><b>Usage Bar Settings</b></html>"))
            .addVerticalGap(5)
            .addComponent(JBLabel("<html><b>Credentials</b></html>"))
            .addVerticalGap(5)
            .addComponent(createAutoDiscoveryPanel())
            .addVerticalGap(10)
            .addComponent(useManualTokenCheckbox)
            .addLabeledComponent("Access Token:", accessTokenField, 1, false)
            .addVerticalGap(15)
            .addSeparator()
            .addVerticalGap(10)
            .addComponent(JBLabel("<html><b>Settings</b></html>"))
            .addVerticalGap(5)
            .addLabeledComponent("Refresh interval (minutes):", refreshIntervalField, 1, false)
            .addVerticalGap(10)
            .addComponent(showNotificationsCheckbox)
            .addLabeledComponent("Notify at percentage:", notifyAtPercentageField, 1, false)
            .addVerticalGap(10)
            .addComponent(JBLabel("Reset time display:"))
            .addComponent(showTimeRemainingRadio)
            .addComponent(showResetTimeRadio)
            .addVerticalGap(15)
            .addComponent(createTestConnectionPanel())
            .addVerticalGap(15)
            .addSeparator()
            .addVerticalGap(10)
            .addComponent(createInstructionsPanel())
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Test connection button listener.
        testConnectionButton.addActionListener {
            testConnection()
        }

        // Check credentials on init.
        checkOAuthCredentials()
    }

    private fun updateUsageBarSettingsState() {
        val enabled = enableUsageBarCheckbox.isSelected
        for (control in usageBarSettingsControls) {
            control.isEnabled = enabled
        }
        // Respect the manual-token sub-toggle when re-enabling.
        if (enabled) {
            accessTokenField.isEnabled = useManualTokenCheckbox.isSelected
            checkCredentialsButton.isEnabled = !useManualTokenCheckbox.isSelected
        }
    }

    private fun createAutoDiscoveryPanel(): JPanel {
        val statusPanel = JPanel(BorderLayout(10, 0))
        statusPanel.add(JBLabel("Auto-discovery:"), BorderLayout.WEST)
        statusPanel.add(autoDiscoveryStatusLabel, BorderLayout.CENTER)
        statusPanel.add(checkCredentialsButton, BorderLayout.EAST)
        return statusPanel
    }

    private fun checkOAuthCredentials() {
        val credentials = credentialDiscovery.discoverAccessToken()
        if (credentials != null) {
            val sourceStr = when (credentials.source) {
                com.example.anthropic.api.CredentialSource.FILE -> "~/.claude/.credentials.json"
                com.example.anthropic.api.CredentialSource.KEYCHAIN -> "macOS Keychain"
                com.example.anthropic.api.CredentialSource.MANUAL -> "manual"
            }
            val expiredStr = if (credentials.isExpired()) " (EXPIRED)" else ""
            autoDiscoveryStatusLabel.text = "✓ Found in $sourceStr$expiredStr"
            autoDiscoveryStatusLabel.foreground = if (credentials.isExpired()) Color(255, 165, 0) else Color(0, 128, 0)
        } else {
            autoDiscoveryStatusLabel.text = "✗ No credentials found"
            autoDiscoveryStatusLabel.foreground = Color(255, 0, 0)
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
            <h3>Auto-discovery (Recommended)</h3>
            <p>If you have Claude Code installed and signed in, credentials will be automatically discovered from macOS Keychain.</p>
            <ol>
              <li>Install <a href='https://claude.ai/download'>Claude Code</a></li>
              <li>Sign in with your Claude account</li>
              <li>Credentials will be auto-discovered</li>
            </ol>

            <h3>Manual Token</h3>
            <p>If auto-discovery doesn't work, you can enter your access token manually:</p>
            <ol>
              <li>Find your token in <code>~/.claude/.credentials.json</code></li>
              <li>Or extract it from macOS Keychain (service: "Claude Code-credentials")</li>
              <li>Check "Use manual access token" and paste the token</li>
            </ol>
            </html>
        """.trimIndent()

        val instructionsLabel = JBLabel(instructionsText)
        val instructionsPanel = JPanel(BorderLayout())
        instructionsPanel.add(instructionsLabel, BorderLayout.CENTER)
        instructionsPanel.border = JBUI.Borders.empty(10)
        return instructionsPanel
    }

    // Feature toggle accessors.
    var enableSessionBrowser: Boolean
        get() = enableSessionBrowserCheckbox.isSelected
        set(value) {
            enableSessionBrowserCheckbox.isSelected = value
        }

    var enableUsageBar: Boolean
        get() = enableUsageBarCheckbox.isSelected
        set(value) {
            enableUsageBarCheckbox.isSelected = value
            updateUsageBarSettingsState()
        }

    var enableSendToClaude: Boolean
        get() = enableSendToClaudeCheckbox.isSelected
        set(value) {
            enableSendToClaudeCheckbox.isSelected = value
        }

    // Property accessors.
    var useManualToken: Boolean
        get() = useManualTokenCheckbox.isSelected
        set(value) {
            useManualTokenCheckbox.isSelected = value
            accessTokenField.isEnabled = value
            checkCredentialsButton.isEnabled = !value
        }

    var accessToken: String?
        get() = String(accessTokenField.password).takeIf { it.isNotBlank() }
        set(value) {
            accessTokenField.text = value ?: ""
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

    var resetTimeDisplayMode: AnthropicSettingsState.ResetTimeDisplayMode
        get() = if (showResetTimeRadio.isSelected)
                    AnthropicSettingsState.ResetTimeDisplayMode.RESET_TIME
                else
                    AnthropicSettingsState.ResetTimeDisplayMode.TIME_REMAINING
        set(value) {
            if (value == AnthropicSettingsState.ResetTimeDisplayMode.RESET_TIME)
                showResetTimeRadio.isSelected = true
            else
                showTimeRemainingRadio.isSelected = true
        }

    private fun testConnection() {
        // Validate inputs.
        if (useManualToken && accessToken.isNullOrBlank()) {
            setStatus("Please enter an access token", StatusType.ERROR)
            return
        }

        if (!useManualToken && credentialDiscovery.discoverAccessToken() == null) {
            setStatus("No credentials found. Install Claude Code and sign in.", StatusType.ERROR)
            return
        }

        testConnectionButton.isEnabled = false
        setStatus("Testing connection...", StatusType.INFO)

        // Test in background.
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            null, "Testing Claude API Connection", true
        ) {
            var success = false
            var errorMessage = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Connecting to Claude API..."

                val apiService = AnthropicApiService()

                if (useManualToken) {
                    apiService.initializeWithToken(accessToken!!)
                } else {
                    apiService.initializeWithAutoDiscovery()
                }

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
                StatusType.SUCCESS -> Color(0, 128, 0)  // Green.
                StatusType.ERROR -> Color(255, 0, 0)     // Red.
                StatusType.INFO -> Color(0, 0, 0)        // Black.
            }
        }
    }

    private enum class StatusType {
        SUCCESS, ERROR, INFO
    }
}
