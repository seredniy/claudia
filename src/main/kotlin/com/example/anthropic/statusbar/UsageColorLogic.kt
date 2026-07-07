package com.example.anthropic.statusbar

import com.intellij.ui.JBColor
import java.awt.Color

object UsageColorLogic {
    val GREEN = JBColor(Color(50, 150, 50), Color(80, 170, 80))
    val YELLOW = JBColor(Color(255, 165, 0), Color(200, 130, 0))
    val RED = JBColor(Color(200, 50, 50), Color(180, 40, 40))

    fun getColor(utilization: Double, timeProgress: Double, timeBasedColoring: Boolean): Color {
        return if (timeBasedColoring) {
            when {
                utilization >= timeProgress + 5.0 -> RED
                utilization >= timeProgress -> YELLOW
                else -> GREEN
            }
        } else {
            when {
                utilization >= 90.0 -> RED
                utilization >= 75.0 -> YELLOW
                else -> GREEN
            }
        }
    }
}
