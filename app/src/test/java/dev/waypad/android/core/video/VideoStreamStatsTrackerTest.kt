package dev.waypad.android.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStreamStatsTrackerTest {

    @Test
    fun measuresFpsAndThroughputOverTheWindow() {
        val tracker = VideoStreamStatsTracker(windowMs = 1_000L)
        repeat(30) { index ->
            tracker.onFrameReceived(bytes = 10_240, ageMs = 40, nowMs = index * 33L)
            tracker.onFrameRendered(index * 33L)
        }

        val metrics = tracker.snapshot(nowMs = 990L)

        assertEquals(30.0, metrics.receivedFps, 0.001)
        assertEquals(30.0, metrics.renderedFps, 0.001)
        assertEquals(30L * 10_240L, metrics.bytesPerSecond)
        assertEquals(10, metrics.averageKib)
        assertEquals(40L, metrics.lastFrameAgeMs)
        assertEquals(30L, metrics.receivedFrames)
        assertEquals(30L, metrics.renderedFrames)
    }

    @Test
    fun forgetsSamplesOlderThanTheWindow() {
        val tracker = VideoStreamStatsTracker(windowMs = 1_000L)
        repeat(10) { index -> tracker.onFrameReceived(1_024, ageMs = 0, nowMs = index * 10L) }

        val metrics = tracker.snapshot(nowMs = 5_000L)

        assertEquals(0.0, metrics.receivedFps, 0.001)
        assertEquals(0L, metrics.bytesPerSecond)
        assertEquals(0, metrics.averageKib)
        assertEquals("totals are cumulative, not windowed", 10L, metrics.receivedFrames)
    }

    @Test
    fun countsRenderedFramesSeparatelyFromReceivedOnes() {
        val tracker = VideoStreamStatsTracker(windowMs = 1_000L)
        repeat(60) { index -> tracker.onFrameReceived(2_048, ageMs = 12, nowMs = index * 16L) }
        repeat(30) { index -> tracker.onFrameRendered(index * 32L) }
        tracker.onFramesDropped(30)

        val metrics = tracker.snapshot(nowMs = 960L)

        assertTrue(metrics.receivedFps > metrics.renderedFps)
        assertEquals(30L, metrics.droppedFrames)
    }

    @Test
    fun neverReportsANegativeAge() {
        val tracker = VideoStreamStatsTracker(windowMs = 1_000L)
        tracker.onFrameReceived(512, ageMs = -250, nowMs = 0L)

        assertEquals(0L, tracker.snapshot(0L).lastFrameAgeMs)
    }

    @Test
    fun resetClearsEverything() {
        val tracker = VideoStreamStatsTracker(windowMs = 1_000L)
        repeat(5) { tracker.onFrameReceived(1_024, ageMs = 10, nowMs = it.toLong()) }
        tracker.onFramesDropped(3)

        tracker.reset()
        val metrics = tracker.snapshot(10L)

        assertEquals(0L, metrics.receivedFrames)
        assertEquals(0L, metrics.droppedFrames)
        assertEquals(0.0, metrics.receivedFps, 0.001)
        assertEquals(0L, metrics.lastFrameAgeMs)
    }

    @Test
    fun setDroppedFramesOverridesTheRunningCounter() {
        val tracker = VideoStreamStatsTracker()
        tracker.onFramesDropped(5)
        tracker.setDroppedFrames(42)

        assertEquals(42L, tracker.snapshot(0L).droppedFrames)
    }
}
