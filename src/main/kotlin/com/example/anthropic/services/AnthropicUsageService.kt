package com.example.anthropic.services

import com.example.anthropic.api.AnthropicApiService
import com.example.anthropic.api.ClaudeApiException
import com.example.anthropic.api.models.UsageData
import com.example.anthropic.settings.AnthropicSettingsState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.Topic
import kotlinx.coroutines.*

@Service(Service.Level.APP)
class AnthropicUsageService : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private val settings = service<AnthropicSettingsState>()
    private val apiService = AnthropicApiService()
    private val cache = UsageDataCache()
    private val log = logger<AnthropicUsageService>()

    private var lastNotificationPercentage = 0

    companion object {
        val USAGE_TOPIC = Topic.create(
            "Anthropic Usage Updates",
            UsageUpdateListener::class.java
        )
    }

    init {
        log.info("AnthropicUsageService initialized")
    }

    fun startTracking() {
        log.info("Starting usage tracking")
        stopTracking()

        val sessionKey = settings.getSecureSessionKey()
        val organizationId = settings.organizationId

        if (sessionKey.isNullOrBlank() || organizationId.isBlank()) {
            log.info("No session key or organization ID configured, skipping usage tracking")
            return
        }

        apiService.initialize(sessionKey, organizationId)

        refreshJob = scope.launch {
            // Initial fetch
            fetchAndPublishUsage()

            // Periodic refresh
            while (isActive) {
                delay(settings.refreshIntervalMinutes * 60 * 1000L)
                fetchAndPublishUsage()
            }
        }

        log.info("Usage tracking started with ${settings.refreshIntervalMinutes} minute interval")
    }

    fun stopTracking() {
        log.info("Stopping usage tracking")
        refreshJob?.cancel()
        refreshJob = null
    }

    fun restartTracking() {
        log.info("Restarting usage tracking")
        stopTracking()
        startTracking()
    }

    private suspend fun fetchAndPublishUsage() {
        log.info("Fetching usage data from Anthropic API")

        val result = apiService.fetchCurrentUsage()

        result.onSuccess { usageData ->
            log.info("Successfully fetched usage data: 5h=${usageData.fiveHourUtilization}% 7d=${usageData.sevenDayUtilization}% (max=${usageData.percentage}%)")
            cache.update(usageData)
            publishUpdate(usageData)

            // Check if notification needed
            if (settings.showNotifications && shouldShowNotification(usageData)) {
                showUsageWarning(usageData)
            }
        }

        result.onFailure { error ->
            val errorMessage = when (error) {
                is ClaudeApiException -> error.getUserMessage()
                else -> error.message ?: "Unknown error"
            }
            log.warn("Failed to fetch usage data: $errorMessage", error)
            publishError(errorMessage)
        }
    }

    private fun shouldShowNotification(data: UsageData): Boolean {
        val currentPercentage = data.percentage.toInt()
        val threshold = settings.notifyAtPercentage

        // Only show notification if we've crossed the threshold and haven't notified at this level yet
        if (currentPercentage >= threshold && lastNotificationPercentage < threshold) {
            lastNotificationPercentage = currentPercentage
            return true
        }

        return false
    }

    private fun publishUpdate(data: UsageData) {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(USAGE_TOPIC)
            .onUsageUpdated(data)
    }

    private fun publishError(error: String) {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(USAGE_TOPIC)
            .onError(error)
    }

    private fun showUsageWarning(data: UsageData) {
        ApplicationManager.getApplication().invokeLater {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Anthropic Usage Alerts")
                .createNotification(
                    "Claude.ai Usage Warning",
                    "You have used ${String.format("%.1f", data.percentage)}% of your usage limit",
                    NotificationType.WARNING
                )

            notification.addAction(object : AnAction("View Settings") {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(e.project, "Anthropic API Settings")
                    notification.expire()
                }
            })

            notification.notify(null)
        }
    }

    fun getCurrentUsage(): UsageData? = cache.get()

    fun forceRefresh() {
        scope.launch {
            fetchAndPublishUsage()
        }
    }

    override fun dispose() {
        log.info("Disposing AnthropicUsageService")
        scope.cancel()
        apiService.dispose()
    }

    /**
     * Project listener to start tracking when first project opens
     */
    class ProjectListener : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            service<AnthropicUsageService>().startTracking()
        }
    }
}

interface UsageUpdateListener {
    fun onUsageUpdated(data: UsageData)
    fun onError(error: String)
}
