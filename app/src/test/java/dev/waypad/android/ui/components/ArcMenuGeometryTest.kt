package dev.waypad.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcMenuGeometryTest {

    private fun geometry(itemCount: Int = 5) = ArcMenuGeometry(
        itemCount = itemCount,
        radiusPx = 400f,
        minInsetPx = 120f,
        maxInsetPx = 280f,
    )

    @Test
    fun `entries run top to bottom in order`() {
        val placements = geometry().placements(anchorY = 500f, selected = 2, trailingEdgeX = 1000f)
        val ys = placements.map { it.y }
        assertEquals(ys.sorted(), ys)
    }

    @Test
    fun `the middle of the arc reaches furthest from the edge`() {
        val placements = geometry().placements(anchorY = 500f, selected = 2, trailingEdgeX = 1000f)
        val insets = placements.map { 1000f - it.x }
        val middle = insets[2]
        assertTrue("middle should be the deepest inset", insets.all { it <= middle + 0.01f })
        assertTrue("ends should sit closest to the edge", insets.first() < middle)
        assertTrue("ends should sit closest to the edge", insets.last() < middle)
    }

    @Test
    fun `the arc is centred on where the thumb entered`() {
        val placements = geometry().placements(anchorY = 500f, selected = 2, trailingEdgeX = 1000f)
        assertEquals(500f, placements[2].y, 0.01f)
    }

    @Test
    fun `the entry nearest the thumb is the selected one`() {
        val arc = geometry()
        val anchor = 500f
        val placements = arc.placements(anchor, selected = 2, trailingEdgeX = 1000f)
        placements.forEach { placement ->
            assertEquals(placement.index, arc.selectedIndex(anchor, placement.y))
        }
    }

    @Test
    fun `dragging past the last entry stays on the last entry`() {
        val arc = geometry()
        assertEquals(4, arc.selectedIndex(anchorY = 500f, dragY = 9_000f))
        assertEquals(0, arc.selectedIndex(anchorY = 500f, dragY = -9_000f))
    }

    @Test
    fun `a swipe near the top corner is pulled down so every entry fits`() {
        val arc = geometry()
        val anchor = arc.anchorFor(entryY = 0f, viewportHeight = 1000f, marginPx = 40f)
        val placements = arc.placements(anchor, selected = 0, trailingEdgeX = 1000f)
        assertTrue("first entry must stay on screen", placements.first().y >= 40f)
        assertTrue("last entry must stay on screen", placements.last().y <= 960f)
    }

    @Test
    fun `a swipe near the bottom corner is pulled up so every entry fits`() {
        val arc = geometry()
        val anchor = arc.anchorFor(entryY = 1000f, viewportHeight = 1000f, marginPx = 40f)
        val placements = arc.placements(anchor, selected = 4, trailingEdgeX = 1000f)
        assertTrue("first entry must stay on screen", placements.first().y >= 40f)
        assertTrue("last entry must stay on screen", placements.last().y <= 960f)
    }

    @Test
    fun `a swipe in open space keeps the arc exactly where the thumb landed`() {
        val arc = geometry()
        assertEquals(500f, arc.anchorFor(entryY = 500f, viewportHeight = 1000f, marginPx = 40f), 0.01f)
    }

    @Test
    fun `a viewport too short to hold the arc centres it rather than clipping one end`() {
        val arc = geometry()
        val anchor = arc.anchorFor(entryY = 10f, viewportHeight = 100f, marginPx = 40f)
        assertEquals(50f, anchor, 0.01f)
    }

    @Test
    fun `only the selected entry is drawn at full size`() {
        val placements = geometry().placements(anchorY = 500f, selected = 1, trailingEdgeX = 1000f)
        assertEquals(ArcMenuGeometry.SELECTED_SCALE, placements[1].scale, 0.001f)
        placements.filter { it.index != 1 }.forEach {
            assertEquals(ArcMenuGeometry.UNSELECTED_SCALE, it.scale, 0.001f)
        }
    }

    @Test
    fun `entries fade with distance but never disappear`() {
        val placements = geometry(itemCount = 7)
            .placements(anchorY = 500f, selected = 0, trailingEdgeX = 1000f)
        assertTrue(placements.all { it.alpha > 0f })
        assertTrue("nearer entries stay more visible", placements[1].alpha > placements[6].alpha)
    }

    @Test
    fun `a single entry still lays out`() {
        val arc = ArcMenuGeometry(itemCount = 1, radiusPx = 400f, minInsetPx = 120f, maxInsetPx = 280f)
        val placements = arc.placements(anchorY = 500f, selected = 0, trailingEdgeX = 1000f)
        assertEquals(1, placements.size)
        assertEquals(500f, placements[0].y, 0.01f)
        assertEquals(0, arc.selectedIndex(500f, 123f))
        assertEquals(0f, arc.spanPx, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty arc is rejected`() {
        ArcMenuGeometry(itemCount = 0, radiusPx = 400f, minInsetPx = 120f, maxInsetPx = 280f)
    }
}
