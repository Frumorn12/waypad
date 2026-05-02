package dev.waypad.android

import dev.waypad.android.core.model.CapabilitySummary
import dev.waypad.android.core.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelTest {
    @Test
    fun defaultCapabilitySummaryIsSafe() {
        val summary = CapabilitySummary()
        assertFalse(summary.inputSupported)
        assertEquals("unknown", summary.inputBackend)
        assertFalse(summary.captureSupported)
        assertEquals("unknown", summary.captureBackend)
    }

    @Test
    fun initialUiStateIsDisconnected() {
        val state = WaypadUiState()
        assertEquals(ConnectionState.Disconnected, state.connectionState)
        assertEquals(Screen.Onboarding, state.screen)
    }
}
