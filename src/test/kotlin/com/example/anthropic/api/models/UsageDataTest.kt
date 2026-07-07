package com.example.anthropic.api.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.Duration

class UsageDataTest {

    @Test
    fun `should calculate time progress correctly for 5-hour window`() {
        val now = Instant.parse("2026-07-07T10:00:00Z")
        val resetsAt = Instant.parse("2026-07-07T12:00:00Z") // 2 hours remaining, 3 hours passed
        
        // For a 5h window, lastReset = resetsAt - 5h = 07:00:00Z
        // Progress = (10:00 - 07:00) / 5h = 3/5 = 0.6 (60%)
        
        val usageData = createUsageData(resetsAt)
        
        assertEquals(60.0, usageData.calculateTimeProgress(now, isFiveHour = true), 0.1)
    }

    @Test
    fun `should calculate time progress correctly for 7-day window`() {
        val now = Instant.parse("2026-07-07T10:00:00Z")
        val resetsAt = Instant.parse("2026-07-10T10:00:00Z") // 3 days remaining, 4 days passed
        
        // For a 7d window, lastReset = resetsAt - 7d = 2026-07-03T10:00:00Z
        // Progress = 4 days / 7 days = 4/7 ≈ 57.14%
        
        val usageData = UsageData(
            fiveHourUtilization = 50.0,
            sevenDayUtilization = 10.0,
            fiveHourResetsAt = null,
            sevenDayResetsAt = resetsAt,
            lastUpdated = Instant.now(),
            breakdown = emptyMap()
        )
        
        assertEquals(57.14, usageData.calculateTimeProgress(now, isFiveHour = false), 0.5)
    }

    private fun createUsageData(fiveHourResetsAt: Instant): UsageData {
        return UsageData(
            fiveHourUtilization = 50.0,
            sevenDayUtilization = 10.0,
            fiveHourResetsAt = fiveHourResetsAt,
            sevenDayResetsAt = null,
            lastUpdated = Instant.now(),
            breakdown = emptyMap()
        )
    }
}
