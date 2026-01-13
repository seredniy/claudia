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
 * API client for personal claude.ai accounts (unofficial API)
 *
 * WARNING: This uses unofficial/undocumented API endpoints that may change at any time.
 * Use at your own risk. This may violate Anthropic's Terms of Service.
 */
class ClaudePersonalApiClient(
    private val sessionKey: String,
    private val organizationId: String
) {
    private val client: OkHttpClient
    private val gson = Gson()

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getPersonalUsage(): Result<PersonalUsageResponse> {
        return retryWithExponentialBackoff {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://claude.ai/api/organizations/$organizationId/usage"

                    val httpRequest = Request.Builder()
                        .url(url)
                        .header("Cookie", "sessionKey=$sessionKey")
                        .header("User-Agent", "AnthropicUsagePlugin/1.0")
                        .header("Accept", "application/json")
                        .get()
                        .build()

                    val response = client.newCall(httpRequest).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val usageResponse = gson.fromJson(body, PersonalUsageResponse::class.java)
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

            // Return immediately on success
            if (result.isSuccess) {
                return result
            }

            // Don't retry on auth errors
            val exception = result.exceptionOrNull()
            if (exception is ClaudeApiException && exception.isUnauthorized) {
                return result
            }

            // Last attempt, return the failure
            if (attempt == maxRetries - 1) {
                return result
            }

            // Wait before retrying
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }

        return Result.failure(IllegalStateException("Max retries exceeded"))
    }
}

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
            isUnauthorized -> "Invalid session key or organization ID. Please check your credentials in settings."
            isRateLimited -> "Rate limit exceeded. Will retry later."
            isServerError -> "Claude.ai is currently unavailable. Will retry later."
            else -> "Failed to fetch usage data: $message"
        }
    }
}
