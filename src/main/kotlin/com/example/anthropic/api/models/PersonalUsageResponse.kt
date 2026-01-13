package com.example.anthropic.api.models

import com.google.gson.annotations.SerializedName
import java.time.Instant

/**
 * Response from claude.ai personal account usage endpoint
 * GET https://claude.ai/api/organizations/{organizationId}/usage
 */
data class PersonalUsageResponse(
    @SerializedName("five_hour")
    val fiveHour: UsagePeriod?,
    @SerializedName("seven_day")
    val sevenDay: UsagePeriod?,
    @SerializedName("seven_day_oauth_apps")
    val sevenDayOauthApps: UsagePeriod?,
    @SerializedName("seven_day_opus")
    val sevenDayOpus: UsagePeriod?,
    @SerializedName("seven_day_sonnet")
    val sevenDaySonnet: UsagePeriod?,
    @SerializedName("iguana_necktie")
    val iguanaNecktie: UsagePeriod?,
    @SerializedName("extra_usage")
    val extraUsage: UsagePeriod?
)

data class UsagePeriod(
    @SerializedName("utilization")
    val utilization: Double,  // Percentage 0-100
    @SerializedName("resets_at")
    val resetsAt: String      // ISO 8601 timestamp
)

/**
 * Extension function to convert Personal Account response to UsageData
 */
fun PersonalUsageResponse.toUsageData(): UsageData {
    val fiveHourUtil = fiveHour?.utilization ?: 0.0
    val sevenDayUtil = sevenDay?.utilization ?: 0.0

    val fiveHourResets = fiveHour?.resetsAt?.let { parseInstant(it) }
    val sevenDayResets = sevenDay?.resetsAt?.let { parseInstant(it) }

    return UsageData(
        fiveHourUtilization = fiveHourUtil,
        sevenDayUtilization = sevenDayUtil,
        fiveHourResetsAt = fiveHourResets,
        sevenDayResetsAt = sevenDayResets,
        lastUpdated = Instant.now(),
        breakdown = buildBreakdown()
    )
}

private fun PersonalUsageResponse.buildBreakdown(): Map<String, ModelUsage> {
    val breakdown = mutableMapOf<String, ModelUsage>()

    sevenDayOpus?.let {
        breakdown["Opus (7-day)"] = ModelUsage(
            model = "opus",
            utilization = it.utilization
        )
    }

    sevenDaySonnet?.let {
        breakdown["Sonnet (7-day)"] = ModelUsage(
            model = "sonnet",
            utilization = it.utilization
        )
    }

    return breakdown
}

private fun parseInstant(isoString: String): Instant? {
    return try {
        Instant.parse(isoString)
    } catch (e: Exception) {
        null
    }
}
