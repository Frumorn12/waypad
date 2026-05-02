package dev.waypad.android.core.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCoordinateMapperTest {
    @Test
    fun mapsContainedLandscapeFrameToDesktopCoordinates() {
        val viewport = ScreenViewport(
            viewWidth = 1000f,
            viewHeight = 800f,
            sourceWidth = 1920f,
            sourceHeight = 1080f,
        )

        val point = viewport.map(500f, 400f)

        assertEquals(960f, point?.x ?: -1f, 0.1f)
        assertEquals(540f, point?.y ?: -1f, 0.1f)
    }

    @Test
    fun rejectsTouchesInLetterboxArea() {
        val viewport = ScreenViewport(
            viewWidth = 1000f,
            viewHeight = 800f,
            sourceWidth = 1920f,
            sourceHeight = 1080f,
        )

        assertNull(viewport.map(500f, 20f))
    }

    @Test
    fun mapsPortraitViewWithPillarboxing() {
        val viewport = ScreenViewport(
            viewWidth = 500f,
            viewHeight = 1000f,
            sourceWidth = 1000f,
            sourceHeight = 1000f,
        )

        assertEquals(0f, viewport.map(0f, 500f)?.x ?: -1f, 0.1f)
        assertNull(viewport.map(250f, 100f))
    }
}
