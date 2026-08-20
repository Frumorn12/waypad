package dev.waypad.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale, pushed towards the rounder end of the expressive range.
 *
 * `Shapes.largeIncreased` / `extraLargeIncreased` / `extraExtraLarge` exist in material3 1.4.0 but
 * are `internal`, so the extra expressive steps are provided by [WaypadExtraShapes] instead.
 */
val WaypadShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Shape tokens that have no slot in the standard Material 3 [Shapes] scale. */
@Immutable
data class WaypadExtraShapes(
    /** Fully rounded, for status pills and badges. */
    val pill: Shape = RoundedCornerShape(percent = 50),
    /** The touchpad slab and the windowed remote display. */
    val slab: Shape = RoundedCornerShape(34.dp),
    /** Remote display when windowed. */
    val viewport: Shape = RoundedCornerShape(18.dp),
    /** Remote display when fullscreen: edge to edge, no corner. */
    val viewportFullscreen: Shape = RoundedCornerShape(0.dp),
    /** Floating overlays drawn on top of the video output. */
    val overlay: Shape = RoundedCornerShape(14.dp),
    /** Compact overlays (stats HUD, debug HUD). */
    val overlayCompact: Shape = RoundedCornerShape(10.dp),
    /** The drag handle revealed in game mode. */
    val handle: Shape = RoundedCornerShape(2.dp),
)

val LocalWaypadExtraShapes = staticCompositionLocalOf { WaypadExtraShapes() }
