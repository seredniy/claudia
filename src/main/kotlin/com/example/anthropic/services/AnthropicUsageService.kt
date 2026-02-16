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
    private var isInErrorState = false
    private var consecutiveErrors = 0

    companion object {
        val USAGE_TOPIC = Topic.create(
            "Anthropic Usage Updates",
            UsageUpdateListener::class.java
        )

        // Retry intervals in seconds: 30s, 1m, 2m, 5m (then stays at 5m).
        private val RETRY_INTERVALS = listOf(30L, 60L, 120L, 300L)
    }

    init {
        log.info("AnthropicUsageService initialized")
    }

    fun startTracking() {
        log.info("Starting usage tracking")
        stopTracking()

        val initialized = initializeApiService()
        if (!initialized) {
            log.info("API service not initialized, will retry periodically")
            isInErrorState = true
            consecutiveErrors = 1
        }

        refreshJob = scope.launch {
            // Initial fetch.
            fetchAndPublishUsage()

            // Periodic refresh with adaptive interval based on error state.
            while (isActive) {
                val delayMs = if (isInErrorState) {
                    // Use exponential backoff for retries.
                    val retryIndex = (consecutiveErrors - 1).coerceIn(0, RETRY_INTERVALS.lastIndex)
                    RETRY_INTERVALS[retryIndex] * 1000L
                } else {
                    settings.refreshIntervalMinutes * 60 * 1000L
                }
                delay(delayMs)

                // Re-initialize credentials on each retry when in error state.
                if (isInErrorState) {
                    try {
                        initializeApiService()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Failed to re-initialize API service", e)
                    }
                }
                fetchAndPublishUsage()
            }
        }

        log.info("Usage tracking started with ${settings.refreshIntervalMinutes} minute interval")
    }

    /**
     * Initialize the API service.
     * Returns true if successfully initialized.
     */
    private fun initializeApiService(): Boolean {
        return if (settings.useManualToken) {
            val accessToken = settings.getSecureAccessToken()
            if (accessToken.isNullOrBlank()) {
                log.info("No manual access token configured")
                return false
            }
            apiService.initializeWithToken(accessToken)
            true
        } else {
            val success = apiService.initializeWithAutoDiscovery()
            if (!success) {
                log.info("OAuth auto-discovery failed, no credentials found")
                publishError("No Claude Code credentials found. Please install Claude Code and sign in.")
            }
            success
        }
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
        log.info("Fetching usage data")

        try {
            val result = apiService.fetchCurrentUsage()

            result.onSuccess { usageData ->
                log.info("Successfully fetched usage data: 5h=${usageData.fiveHourUtilization}% 7d=${usageData.sevenDayUtilization}%")

                // Clear error state on success.
                if (isInErrorState) {
                    log.info("Recovered from error state")
                    isInErrorState = false
                    consecutiveErrors = 0
                }

                cache.update(usageData)
                publishUpdate(usageData)

                // Check if notification needed.
                if (settings.showNotifications && shouldShowNotification(usageData)) {
                    showUsageWarning(usageData)
                }
            }

            result.onFailure { error ->
                handleFetchError(error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFetchError(e)
        }
    }

    private fun handleFetchError(error: Throwable) {
        val errorMessage = when (error) {
            is ClaudeApiException -> error.getUserMessage()
            else -> error.message ?: "Unknown error"
        }
        log.warn("Failed to fetch usage data: $errorMessage", error)

        // Track error state for retry logic.
        isInErrorState = true
        consecutiveErrors++
        val retryIndex = (consecutiveErrors - 1).coerceIn(0, RETRY_INTERVALS.lastIndex)
        val nextRetrySeconds = RETRY_INTERVALS[retryIndex]
        log.info("Error state: will retry in ${nextRetrySeconds}s (attempt $consecutiveErrors)")

        publishError(errorMessage)
    }

    private fun shouldShowNotification(data: UsageData): Boolean {
        val currentPercentage = data.fiveHourUtilization.toInt()
        val threshold = settings.notifyAtPercentage

        // Only show notification if we've crossed the threshold and haven't notified at this level yet.
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
                    "Claude Usage Warning",
                    "5-hour limit: ${String.format("%.1f", data.fiveHourUtilization)}%",
                    NotificationType.WARNING
                )

            notification.addAction(object : AnAction("View Settings") {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(e.project, "Claudia Settings")
                    notification.expire()
                }
            })

            notification.notify(null)
        }
    }

    fun getCurrentUsage(): UsageData? = cache.get()

    fun forceRefresh() {
        scope.launch {
            // Re-initialize credentials in case they've changed.
            initializeApiService()
            fetchAndPublishUsage()
        }
    }

    /**
     * Check if OAuth credentials can be auto-discovered.
     */
    fun canAutoDiscoverCredentials(): Boolean {
        return apiService.canAutoDiscoverCredentials()
    }

    override fun dispose() {
        log.info("Disposing AnthropicUsageService")
        scope.cancel()
        apiService.dispose()
    }

    /**
     * Project listener to start tracking when first project opens.
     */
    class ProjectListener : ProjectManagerListener {
        @Suppress("DEPRECATION")
        override fun projectOpened(project: Project) {
            service<AnthropicUsageService>().startTracking()
        }
    }
}

interface UsageUpdateListener {
    fun onUsageUpdated(data: UsageData)
    fun onError(error: String)
}
