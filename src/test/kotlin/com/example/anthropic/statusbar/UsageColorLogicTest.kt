package com.example.anthropic.statusbar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UsageColorLogicTest {

    @Test
    fun `should use standard logic when time-based coloring is disabled`() {
        assertEquals(UsageColorLogic.GREEN, UsageColorLogic.getColor(70.0, 50.0, false))
        assertEquals(UsageColorLogic.YELLOW, UsageColorLogic.getColor(75.0, 50.0, false))
        assertEquals(UsageColorLogic.YELLOW, UsageColorLogic.getColor(89.0, 50.0, false))
        assertEquals(UsageColorLogic.RED, UsageColorLogic.getColor(90.0, 50.0, false))
    }

    @Test
    fun `should use time-based logic when enabled`() {
        // timeProgress = 50%
        
        // utilization <= 50% -> GREEN
        assertEquals(UsageColorLogic.GREEN, UsageColorLogic.getColor(40.0, 50.0, true))
        assertEquals(UsageColorLogic.GREEN, UsageColorLogic.getColor(50.0, 50.0, true))
        
        // utilization > 50% but <= 55% -> GREEN
        assertEquals(UsageColorLogic.GREEN, UsageColorLogic.getColor(52.0, 50.0, true))
        
        // utilization > 55% -> YELLOW
        assertEquals(UsageColorLogic.YELLOW, UsageColorLogic.getColor(56.0, 50.0, true))
        assertEquals(UsageColorLogic.YELLOW, UsageColorLogic.getColor(60.0, 50.0, true))
        
        // utilization > 60% -> RED
        assertEquals(UsageColorLogic.RED, UsageColorLogic.getColor(61.0, 50.0, true))
    }
}
