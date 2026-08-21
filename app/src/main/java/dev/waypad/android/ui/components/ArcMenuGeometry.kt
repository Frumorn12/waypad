package dev.waypad.android.ui.components

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Where one entry sits while the arc is open. Coordinates are viewport pixels. */
data class ArcItemPlacement(
    val index: Int,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
)

/**
 * Layout and selection maths for the arc menu, kept free of Compose so it can be exercised on the
 * JVM. Everything is in pixels; the caller converts from dp.
 *
 * Entries sit on a circular arc that bulges away from the trailing edge, with the middle of the
 * arc reaching furthest inward. Selection is positional: the entry nearest the thumb wins, so
 * sliding up and down runs through the list without the thumb ever leaving the edge, and letting
 * go activates whatever is under it.
 *
 * The arc is centred on wherever the thumb entered rather than on a fixed point, which is what
 * keeps the reach constant no matter where along the edge the swipe starts. [anchorFor] then pulls
 * that centre back inside the viewport when the swipe starts near a corner, because an entry drawn
 * off-screen cannot be selected.
 */
class ArcMenuGeometry(
    val itemCount: Int,
    private val radiusPx: Float,
    private val minInsetPx: Float,
    private val maxInsetPx: Float,
    private val stepRadians: Float = DEFAULT_STEP_RADIANS,
) {
    init {
        require(itemCount > 0) { "an arc menu needs at least one entry" }
    }

    /** Index of the middle entry, as a fraction, so an even count still balances around the thumb. */
    private val centreIndex: Float = (itemCount - 1) / 2f

    /** Vertical distance the arc spans from the first entry to the last. */
    val spanPx: Float
        get() = if (itemCount == 1) 0f else offsetY(itemCount - 1) - offsetY(0)

    private fun angle(index: Int): Float = (index - centreIndex) * stepRadians

    private fun offsetY(index: Int): Float = radiusPx * sin(angle(index))

    private fun inset(index: Int): Float =
        minInsetPx + (maxInsetPx - minInsetPx) * cos(angle(index))

    /**
     * Clamps the arc's centre so every entry stays on screen, keeping it as close as possible to
     * where the thumb actually entered.
     */
    fun anchorFor(entryY: Float, viewportHeight: Float, marginPx: Float): Float {
        val top = offsetY(0)
        val bottom = offsetY(itemCount - 1)
        val lowest = marginPx - top
        val highest = viewportHeight - marginPx - bottom
        // A viewport too short for the whole arc has no valid range; centring is the least bad
        // answer, and every entry stays equally reachable.
        if (highest < lowest) return viewportHeight / 2f
        return entryY.coerceIn(lowest, highest)
    }

    /** The entry currently under the thumb. */
    fun selectedIndex(anchorY: Float, dragY: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (index in 0 until itemCount) {
            val distance = abs(dragY - (anchorY + offsetY(index)))
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /**
     * Positions for every entry. [trailingEdgeX] is the right edge of the viewport; entries are
     * inset to its left.
     */
    fun placements(anchorY: Float, selected: Int, trailingEdgeX: Float): List<ArcItemPlacement> =
        (0 until itemCount).map { index ->
            val distance = abs(index - selected)
            ArcItemPlacement(
                index = index,
                x = trailingEdgeX - inset(index),
                y = anchorY + offsetY(index),
                scale = if (index == selected) SELECTED_SCALE else UNSELECTED_SCALE,
                // Entries far from the selection fade rather than vanish, so the list stays
                // readable as a whole while the thumb moves through it.
                alpha = (1f - distance * ALPHA_FALLOFF).coerceAtLeast(MIN_ALPHA),
            )
        }

    companion object {
        /** Angular gap between neighbouring entries; ~22° reads as an arc without crowding. */
        const val DEFAULT_STEP_RADIANS = 0.384f
        const val SELECTED_SCALE = 1f
        const val UNSELECTED_SCALE = 0.72f
        private const val ALPHA_FALLOFF = 0.22f
        private const val MIN_ALPHA = 0.35f
    }
}
