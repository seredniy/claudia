package com.example.anthropic.api

import com.example.anthropic.api.models.PersonalUsageResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * API client for Claude OAuth API.
 * Uses the official OAuth endpoint: api.anthropic.com/api/oauth/usage
 *
 * This is based on the claude-hud implementation:
 * https://github.com/jarrodwatts/claude-hud/blob/main/src/usage-api.ts
 */
class ClaudeOAuthApiClient(
    private val accessToken: String
) {
    private val client: OkHttpClient
    private val gson = Gson()

    companion object {
        private const val OAUTH_API_HOST = "https://api.anthropic.com"
        private const val USAGE_ENDPOINT = "/api/oauth/usage"
        private const val ANTHROPIC_BETA_HEADER = "oauth-2025-04-20"
        private const val USER_AGENT = "claude-usage-plugin/1.0"
        private const val TIMEOUT_MS = 5000L
    }

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Fetches usage data from the OAuth API endpoint.
     */
    suspend fun getUsage(): Result<PersonalUsageResponse> {
        return retryWithExponentialBackoff {
            withContext(Dispatchers.IO) {
                try {
                    val url = "$OAUTH_API_HOST$USAGE_ENDPOINT"

                    val httpRequest = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .header("anthropic-beta", ANTHROPIC_BETA_HEADER)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .get()
                        .build()

                    val response = client.newCall(httpRequest).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val usageResponse = parseUsageResponse(body)
                            Result.success(usageResponse)
                        } else {
                            Result.failure(ClaudeApiException(response.code, "Empty response body"))
                        }
                    } else {
                        val errorBody = response.body?.string() ?: response.message
                        Result.failure(ClaudeApiException(response.code, errorBody))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Parses the OAuth usage response.
     * The response format is similar to the web API but may have slight differences.
     */
    private fun parseUsageResponse(json: String): PersonalUsageResponse {
        return gson.fromJson(json, PersonalUsageResponse::class.java)
    }

    private suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 1000,
        maxDelayMillis: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            val result = block()

            // Return immediately on success.
            if (result.isSuccess) {
                return result
            }

            // Don't retry on auth errors.
            val exception = result.exceptionOrNull()
            if (exception is ClaudeApiException && exception.isUnauthorized) {
                return result
            }

            // Last attempt, return the failure.
            if (attempt == maxRetries - 1) {
                return result
            }

            // Wait before retrying.
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }

        return Result.failure(IllegalStateException("Max retries exceeded"))
    }
}

/**
 * Exception for Claude API errors.
 */
class ClaudeApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message) {

    val isRateLimited: Boolean
        get() = statusCode == 429

    val isUnauthorized: Boolean
        get() = statusCode == 401 || statusCode == 403

    val isServerError: Boolean
        get() = statusCode >= 500

    fun getUserMessage(): String {
        return when {
            isUnauthorized -> "Invalid or expired access token. Please check your credentials."
            isRateLimited -> "Rate limit exceeded. Will retry later."
            isServerError -> "Claude API is currently unavailable. Will retry later."
            else -> "Failed to fetch usage data: $message"
        }
    }
}