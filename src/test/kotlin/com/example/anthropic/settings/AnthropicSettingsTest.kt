package com.example.anthropic.settings

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AnthropicSettingsTest {

    @Test
    fun `should have timeBasedColoring property in settings state`() {
        val state = AnthropicSettingsState.State()
        assertNotNull(state.timeBasedColoring)
    }
}
