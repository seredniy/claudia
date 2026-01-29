package com.example.anthropic.api

import com.example.anthropic.api.models.UsageData
import com.example.anthropic.api.models.toUsageData
import com.intellij.openapi.diagnostic.logger

/**
 * Service for fetching usage data from Claude OAuth API.
 * Uses api.anthropic.com/api/oauth/usage endpoint.
 */
class AnthropicApiService {
    private val log = logger<AnthropicApiService>()
    private val credentialDiscovery = ClaudeCredentialDiscovery()

    private var oauthClient: ClaudeOAuthApiClient? = null
    private var isAutoDiscovered: Boolean = false

    /**
     * Initialize with manual OAuth token.
     */
    fun initializeWithToken(accessToken: String) {
        log.info("Initializing with manual OAuth token")
        this.oauthClient = ClaudeOAuthApiClient(accessToken)
        this.isAutoDiscovered = false
    }

    /**
     * Initialize with auto-discovered credentials.
     * Returns true if credentials were found, false otherwise.
     */
    fun initializeWithAutoDiscovery(): Boolean {
        log.info("Attempting to auto-discover OAuth credentials")
        val credentials = credentialDiscovery.discoverAccessToken()

        if (credentials == null) {
            log.info("No OAuth credentials found")
            return false
        }

        if (credentials.isExpired()) {
            log.warn("Discovered credentials are expired, but will try anyway")
        }

        log.info("Found OAuth credentials from ${credentials.source}")
        this.oauthClient = ClaudeOAuthApiClient(credentials.accessToken)
        this.isAutoDiscovered = true
        return true
    }

    fun isInitialized(): Boolean = oauthClient != null

    fun isUsingAutoDiscovery(): Boolean = isAutoDiscovered

    /**
     * Check if OAuth credentials can be auto-discovered.
     */
    fun canAutoDiscoverCredentials(): Boolean {
        return credentialDiscovery.hasCredentials()
    }

    /**
     * Get information about discovered credentials (for UI display).
     */
    fun getDiscoveredCredentialsInfo(): String? {
        val credentials = credentialDiscovery.discoverAccessToken() ?: return null
        val sourceStr = when (credentials.source) {
            CredentialSource.FILE -> "~/.claude/.credentials.json"
            CredentialSource.KEYCHAIN -> "macOS Keychain"
            CredentialSource.MANUAL -> "manual"
        }
        val expiredStr = if (credentials.isExpired()) " (expired)" else ""
        return "Found in $sourceStr$expiredStr"
    }

    suspend fun fetchCurrentUsage(): Result<UsageData> {
        val client = oauthClient
            ?: return Result.failure(IllegalStateException(
                "Client not initialized. Please configure your credentials in settings."
            ))

        return client.getUsage()
            .map { response -> response.toUsageData() }
    }

    suspend fun testConnection(): Result<Boolean> {
        return fetchCurrentUsage().map { true }
    }

    fun dispose() {
        oauthClient = null
    }
}
