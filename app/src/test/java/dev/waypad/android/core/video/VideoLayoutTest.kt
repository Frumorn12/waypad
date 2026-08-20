package dev.waypad.android.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VideoLayoutTest {

    @Test
    fun letterboxesAWideVideoInATallBox() {
        val fitted = VideoLayout.fitAspectRatio(800, 1000, 1600, 900)

        assertEquals(VideoSize(800, 450), fitted)
        assertRatioPreserved(fitted, 1600.0 / 900.0)
    }

    @Test
    fun pillarboxesATallVideoInAWideBox() {
        val fitted = VideoLayout.fitAspectRatio(1000, 800, 900, 1600)

        assertEquals(VideoSize(450, 800), fitted)
        assertRatioPreserved(fitted, 900.0 / 1600.0)
    }

    @Test
    fun fillsTheBoxWhenTheRatiosMatch() {
        assertEquals(VideoSize(1600, 900), VideoLayout.fitAspectRatio(1600, 900, 1920, 1080))
    }

    @Test
    fun fallsBackToTheBoxWhenTheVideoSizeIsUnknown() {
        assertEquals(VideoSize(800, 600), VideoLayout.fitAspectRatio(800, 600, 0, 0))
        assertEquals(VideoSize(0, 0), VideoLayout.fitAspectRatio(0, 0, 1920, 1080))
    }

    @Test
    fun neverCollapsesToZero() {
        val fitted = VideoLayout.fitAspectRatio(1, 1000, 1920, 1080)

        assertTrue(fitted.width >= 1)
        assertTrue(fitted.height >= 1)
    }

    @Test
    fun centresTheFittedRectangle() {
        val rect = VideoLayout.fitCentre(1000, 1000, 1000, 500)

        assertEquals(VideoRect(0, 250, 1000, 750), rect)
        assertEquals(1000, rect.width)
        assertEquals(500, rect.height)
    }

    @Test
    fun centringIsANoOpWhenThePictureFillsTheBox() {
        assertEquals(VideoRect(0, 0, 1600, 900), VideoLayout.fitCentre(1600, 900, 1920, 1080))
    }

    private fun assertRatioPreserved(fitted: VideoSize, expectedRatio: Double) {
        val ratio = fitted.width.toDouble() / fitted.height
        assertTrue("ratio $ratio != $expectedRatio", abs(ratio - expectedRatio) < 0.01)
    }
}
