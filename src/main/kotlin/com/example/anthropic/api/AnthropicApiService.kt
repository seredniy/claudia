package com.example.anthropic.api

import com.example.anthropic.api.models.UsageData
import com.example.anthropic.api.models.toUsageData

/**
 * Service for fetching usage data from personal claude.ai accounts
 */
class AnthropicApiService {
    private var client: ClaudePersonalApiClient? = null

    fun initialize(sessionKey: String, organizationId: String) {
        this.client = ClaudePersonalApiClient(sessionKey, organizationId)
    }

    fun isInitialized(): Boolean = client != null

    suspend fun fetchCurrentUsage(): Result<UsageData> {
        val currentClient = client
            ?: return Result.failure(IllegalStateException(
                "Client not initialized. Please configure your sessionKey and organization ID in settings."
            ))

        return currentClient.getPersonalUsage()
            .map { response -> response.toUsageData() }
    }

    suspend fun testConnection(): Result<Boolean> {
        val currentClient = client
            ?: return Result.failure(IllegalStateException("Client not initialized"))

        return currentClient.getPersonalUsage()
            .map { true }
    }

    fun dispose() {
        client = null
    }
}
