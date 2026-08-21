package dev.waypad.android.ui.screens

import dev.waypad.android.core.model.ScreenSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourcePresentationTest {

    private fun source(
        id: String,
        label: String,
        backend: String,
        width: Int = 0,
        height: Int = 0,
    ) = ScreenSource(
        id = id,
        label = label,
        kind = "monitor",
        backend = backend,
        width = width,
        height = height,
        x = 0,
        y = 0,
        scale = 1.0,
        focused = false,
    )

    private val portal = source(
        "portal:chooser",
        "Portal picker (PipeWire screencast — 30–60 FPS)",
        "wayland-screencast-portal",
    )
    private val grim = source("hyprland:monitor:eDP-1", "eDP-1 (BOE 0x0BCA)", "hyprland-grim", 1920, 1080)
    private val x11 = source("x11:HDMI-A-1", "HDMI-A-1 (X11 – 60 FPS, no approval)", "x11-ffmpeg", 1920, 1080)

    @Test
    fun `the hardware path is the recommended one`() {
        val presented = presentSource(portal)
        assertEquals(SourceGroup.Recommended, presented.group)
        assertTrue(presented.fast)
    }

    @Test
    fun `the slow backends are grouped as fallbacks`() {
        assertEquals(SourceGroup.Fallback, presentSource(grim).group)
        assertEquals(SourceGroup.Fallback, presentSource(x11).group)
        assertFalse(presentSource(grim).fast)
        assertFalse(presentSource(x11).fast)
    }

    @Test
    fun `the detail says what it costs, not which library it uses`() {
        assertTrue(presentSource(grim).detail.contains("6 fps"))
        assertTrue(presentSource(portal).detail.contains("60 fps"))
    }

    @Test
    fun `the resolution is shown when the daemon knows it`() {
        assertTrue(presentSource(grim).detail.contains("1920 × 1080"))
        assertFalse(presentSource(portal).detail.contains("×"))
    }

    @Test
    fun `the title drops the parenthetical the daemon appends`() {
        assertEquals("Portal picker", presentSource(portal).title)
        // Fallbacks carry the method, because the same monitor appears once per backend.
        assertEquals("eDP-1 · Screenshot", presentSource(grim).title)
        assertEquals("HDMI-A-1 · X11", presentSource(x11).title)
    }

    @Test
    fun `a source with no label falls back to its id`() {
        val nameless = source("only-id", "", "hyprland-grim")
        assertEquals("only-id · Screenshot", presentSource(nameless).title)
    }

    @Test
    fun `a label that is entirely parenthetical keeps something to show`() {
        val odd = source("weird", " (all suffix)", "hyprland-grim")
        assertEquals(" (all suffix) · Screenshot", presentSource(odd).title)
    }

    @Test
    fun `an unknown backend is treated as a fallback rather than recommended`() {
        val future = source("new", "Something new", "some-future-backend")
        val presented = presentSource(future)
        assertEquals(SourceGroup.Fallback, presented.group)
        assertFalse(presented.fast)
        assertTrue(presented.detail.contains("some-future-backend"))
    }

    @Test
    fun `groups come out recommended first and skip the empty ones`() {
        val grouped = groupSources(listOf(x11, portal, grim))
        assertEquals(listOf(SourceGroup.Recommended, SourceGroup.Fallback), grouped.map { it.first })
        assertEquals(1, grouped[0].second.size)
        assertEquals(2, grouped[1].second.size)
    }

    @Test
    fun `a list with only fallbacks shows just that group`() {
        val grouped = groupSources(listOf(grim, x11))
        assertEquals(listOf(SourceGroup.Fallback), grouped.map { it.first })
    }

    @Test
    fun `the default is the fastest source regardless of the order it arrives in`() {
        assertEquals("portal:chooser", defaultSourceId(listOf(x11, grim, portal)))
    }

    @Test
    fun `with no fast source the first one is used rather than none`() {
        assertEquals("hyprland:monitor:eDP-1", defaultSourceId(listOf(grim, x11)))
    }

    @Test
    fun `an empty list has no default`() {
        assertNull(defaultSourceId(emptyList()))
    }
    @Test
    fun `the same monitor on two backends does not produce two identical rows`() {
        val viaGrim = source("hyprland:monitor:eDP-1", "eDP-1 (BOE 0x0BCA)", "hyprland-grim")
        val viaX11 = source("x11:eDP-1", "eDP-1 (X11 – 60 FPS, no approval)", "x11-ffmpeg")
        assertTrue(presentSource(viaGrim).title != presentSource(viaX11).title)
    }

}
