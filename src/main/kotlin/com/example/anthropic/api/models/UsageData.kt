package com.example.anthropic.api.models

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Usage data for personal claude.ai account
 */
data class UsageData(
    val fiveHourUtilization: Double,      // Percentage 0-100
    val sevenDayUtilization: Double,      // Percentage 0-100
    val fiveHourResetsAt: Instant?,
    val sevenDayResetsAt: Instant?,
    val lastUpdated: Instant,
    val breakdown: Map<String, ModelUsage> = emptyMap()
) {
    // Maximum utilization across all periods
    val percentage: Double
        get() = maxOf(fiveHourUtilization, sevenDayUtilization)

    val formattedLastUpdate: String
        get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(lastUpdated)

    val formattedFiveHourResetsAt: String?
        get() = fiveHourResetsAt?.let {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(it)
        }

    val formattedSevenDayResetsAt: String?
        get() = sevenDayResetsAt?.let {
            val day = DateTimeFormatter.ofPattern("EEE").withZone(ZoneId.systemDefault()).format(it)
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(it)
            "$day $time"
        }

    // Compact time remaining format: "2h 15m" or "45m" or "5m"
    val fiveHourTimeRemaining: String?
        get() = fiveHourResetsAt?.let {
            val now = Instant.now()
            val duration = java.time.Duration.between(now, it)

            if (duration.isNegative) {
                "resetting..."
            } else {
                val hours = duration.toHours()
                val minutes = (duration.toMinutes() % 60)

                when {
                    hours > 0 -> "${hours}h ${minutes}m"
                    minutes > 0 -> "${minutes}m"
                    else -> "<1m"
                }
            }
        }
}

data class ModelUsage(
    val model: String,
    val utilization: Double  // Percentage 0-100
)
