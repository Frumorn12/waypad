package dev.waypad.android.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioArrivalDriftTest {

    /**
     * The daemon clock and the phone clock differ by an arbitrary constant — 332 ms on the test
     * device. Cancelling it is the whole point: the naive `now - header.timestamp_ms` read as about
     * -350 ms on every packet, and an impossible number sends the next reader chasing a phantom.
     */
    @Test
    fun cancelsTheOffsetBetweenTheTwoClocksAndNeverGoesNegative() {
        val drift = AudioArrivalDrift()
        val hostClockOffset = 332_000L

        drift.onPacket(sourceTimestampMs = hostClockOffset, localElapsedMs = 1_000)
        assertEquals(0L, drift.driftMs())

        // Both clocks advanced by the same 400 ms: the stream is keeping pace.
        drift.onPacket(sourceTimestampMs = hostClockOffset + 400, localElapsedMs = 1_400)
        assertEquals(0L, drift.driftMs())

        // A packet that somehow beats the anchor re-anchors instead of reporting a negative delay.
        drift.onPacket(sourceTimestampMs = hostClockOffset + 800, localElapsedMs = 1_750)
        assertTrue(drift.driftMs() >= 0)
        assertEquals(0L, drift.driftMs())
    }

    @Test
    fun reportsDelayAboveTheFastestPacketOfTheSession() {
        val drift = AudioArrivalDrift()
        drift.onPacket(sourceTimestampMs = 5_000, localElapsedMs = 100)

        // Stamped 1000 ms after the first packet, seen 1090 ms after: 90 ms of extra transit.
        drift.onPacket(sourceTimestampMs = 6_000, localElapsedMs = 1_190)
        assertEquals(90L, drift.driftMs())

        // Recovering shows as the drift falling back towards zero.
        drift.onPacket(sourceTimestampMs = 7_000, localElapsedMs = 2_110)
        assertEquals(10L, drift.driftMs())
    }

    @Test
    fun `the first packet of a burst does not bias the whole session`() {
        val drift = AudioArrivalDrift()
        // The session opens with a burst the portal approval had queued up: that packet is 200 ms
        // late, and anchoring on it would make every healthy packet afterwards read as -200.
        drift.onPacket(sourceTimestampMs = 1_000, localElapsedMs = 1_200)
        drift.onPacket(sourceTimestampMs = 1_020, localElapsedMs = 1_205)

        drift.onPacket(sourceTimestampMs = 1_040, localElapsedMs = 1_040)
        assertEquals(0L, drift.driftMs())
        drift.onPacket(sourceTimestampMs = 1_060, localElapsedMs = 1_075)
        assertEquals(15L, drift.driftMs())
    }

    @Test
    fun reAnchorsOnEveryStreamAndIgnoresUnstampedPackets() {
        val drift = AudioArrivalDrift()
        drift.onPacket(sourceTimestampMs = 5_000, localElapsedMs = 100)
        drift.onPacket(sourceTimestampMs = 6_000, localElapsedMs = 1_500)
        assertEquals(400L, drift.driftMs())

        drift.reset()
        assertEquals(0L, drift.driftMs())

        // A header with no timestamp must not take part in the estimate.
        drift.onPacket(sourceTimestampMs = 0, localElapsedMs = 9_000)
        drift.onPacket(sourceTimestampMs = 8_000, localElapsedMs = 10_000)
        drift.onPacket(sourceTimestampMs = 8_500, localElapsedMs = 10_500)
        assertEquals(0L, drift.driftMs())
    }
}
