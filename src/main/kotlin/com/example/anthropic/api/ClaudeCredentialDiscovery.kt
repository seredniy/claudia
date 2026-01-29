package com.example.anthropic.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.logger
import java.io.File

/**
 * Discovers OAuth credentials from Claude Code installations.
 *
 * Credential sources (in priority order):
 * 1. File-based credentials (~/.claude/.credentials.json)
 * 2. macOS Keychain (Claude Code 2.x) - future implementation
 */
class ClaudeCredentialDiscovery {
    private val log = logger<ClaudeCredentialDiscovery>()
    private val gson = Gson()

    /**
     * Attempts to discover OAuth access token from Claude Code credential stores.
     * Returns null if no valid credentials found.
     *
     * Discovery order (same as claude-hud):
     * 1. macOS Keychain (Claude Code 2.x) - primary source
     * 2. File-based (~/.claude/.credentials.json) - fallback
     */
    fun discoverAccessToken(): DiscoveredCredentials? {
        // Try macOS Keychain first (only on macOS) - this is the primary source
        if (isMacOS()) {
            val keychainCredentials = tryMacOSKeychain()
            if (keychainCredentials != null) {
                log.info("Found credentials in macOS Keychain")
                return keychainCredentials
            }
        }

        // Fall back to file-based credentials
        val fileCredentials = tryFileBasedCredentials()
        if (fileCredentials != null) {
            log.info("Found credentials in ~/.claude/.credentials.json")
            return fileCredentials
        }

        log.info("No Claude Code credentials found")
        return null
    }

    /**
     * Try to read credentials from ~/.claude/.credentials.json
     */
    private fun tryFileBasedCredentials(): DiscoveredCredentials? {
        return try {
            val homeDir = System.getProperty("user.home")
            val credentialsFile = File(homeDir, ".claude/.credentials.json")

            if (!credentialsFile.exists()) {
                log.debug("Credentials file not found: ${credentialsFile.absolutePath}")
                return null
            }

            val content = credentialsFile.readText()
            val credentials = gson.fromJson(content, ClaudeCredentialsFile::class.java)

            // Check for OAuth token
            val accessToken = credentials.claudeAiOauth?.accessToken
            if (accessToken.isNullOrBlank()) {
                log.debug("No access token found in credentials file")
                return null
            }

            // Check if token is expired
            val expiresAt = credentials.claudeAiOauth.expiresAt
            if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
                log.warn("Access token is expired (expired at: $expiresAt)")
                // Still return it - the API will handle refresh or error
            }

            DiscoveredCredentials(
                accessToken = accessToken,
                source = CredentialSource.FILE,
                expiresAt = expiresAt
            )
        } catch (e: Exception) {
            log.warn("Failed to read credentials file: ${e.message}")
            null
        }
    }

    /**
     * Try to read credentials from macOS Keychain.
     * Uses security command to access Claude Code's keychain entry.
     * Service name: 'Claude Code-credentials'
     */
    private fun tryMacOSKeychain(): DiscoveredCredentials? {
        return try {
            val process = ProcessBuilder(
                "/usr/bin/security",
                "find-generic-password",
                "-s", "Claude Code-credentials",
                "-w"
            )
                .redirectErrorStream(false)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                log.debug("Keychain entry not found or access denied")
                return null
            }

            val password = process.inputStream.bufferedReader().readText().trim()
            if (password.isBlank()) {
                return null
            }

            // Parse the keychain entry (JSON format with claudeAiOauth wrapper)
            val keychainData = gson.fromJson(password, ClaudeCredentialsFile::class.java)
            val oauthData = keychainData.claudeAiOauth

            if (oauthData?.accessToken.isNullOrBlank()) {
                log.debug("No access token in keychain entry")
                return null
            }

            DiscoveredCredentials(
                accessToken = oauthData!!.accessToken!!,
                source = CredentialSource.KEYCHAIN,
                expiresAt = oauthData.expiresAt
            )
        } catch (e: Exception) {
            log.debug("Failed to read from macOS Keychain: ${e.message}")
            null
        }
    }

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    /**
     * Checks if Claude Code credentials are available.
     */
    fun hasCredentials(): Boolean {
        return discoverAccessToken() != null
    }
}

/**
 * Discovered OAuth credentials.
 */
data class DiscoveredCredentials(
    val accessToken: String,
    val source: CredentialSource,
    val expiresAt: Long? = null
) {
    fun isExpired(): Boolean {
        return expiresAt != null && expiresAt < System.currentTimeMillis()
    }
}

enum class CredentialSource {
    FILE,
    KEYCHAIN,
    MANUAL
}

/**
 * Structure of ~/.claude/.credentials.json
 */
data class ClaudeCredentialsFile(
    @SerializedName("claudeAiOauth")
    val claudeAiOauth: OAuthTokenData?
)

data class OAuthTokenData(
    @SerializedName("accessToken")
    val accessToken: String?,
    @SerializedName("refreshToken")
    val refreshToken: String?,
    @SerializedName("expiresAt")
    val expiresAt: Long?,
    @SerializedName("scopes")
    val scopes: List<String>?
)

