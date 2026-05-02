package dev.waypad.android.core.screen

import kotlin.math.min

data class DesktopPoint(val x: Float, val y: Float)

data class ScreenViewport(
    val viewWidth: Float,
    val viewHeight: Float,
    val sourceWidth: Float,
    val sourceHeight: Float,
) {
    fun map(viewX: Float, viewY: Float): DesktopPoint? {
        if (viewWidth <= 0f || viewHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
            return null
        }
        val scale = min(viewWidth / sourceWidth, viewHeight / sourceHeight)
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val offsetX = (viewWidth - drawnWidth) / 2f
        val offsetY = (viewHeight - drawnHeight) / 2f
        val localX = viewX - offsetX
        val localY = viewY - offsetY
        if (localX < 0f || localY < 0f || localX > drawnWidth || localY > drawnHeight) {
            return null
        }
        return DesktopPoint(
            x = (localX / scale).coerceIn(0f, sourceWidth),
            y = (localY / scale).coerceIn(0f, sourceHeight),
        )
    }
}
