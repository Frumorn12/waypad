package dev.waypad.android.core.video

import kotlin.math.roundToInt

/** Pixel rectangle, kept free of `android.graphics` so the layout maths stays unit testable. */
data class VideoRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Letterboxing maths shared by [WaypadVideoView] and [JpegSurfaceRenderer]. */
object VideoLayout {

    /** Largest size with the video aspect ratio that still fits the available box. */
    fun fitAspectRatio(
        availableWidth: Int,
        availableHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): VideoSize {
        if (availableWidth <= 0 || availableHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return VideoSize(availableWidth.coerceAtLeast(0), availableHeight.coerceAtLeast(0))
        }
        val videoRatio = videoWidth.toDouble() / videoHeight
        val boxRatio = availableWidth.toDouble() / availableHeight
        return if (videoRatio > boxRatio) {
            VideoSize(availableWidth, (availableWidth / videoRatio).roundToInt().coerceAtLeast(1))
        } else {
            VideoSize((availableHeight * videoRatio).roundToInt().coerceAtLeast(1), availableHeight)
        }
    }

    /** [fitAspectRatio] centred inside the box. */
    fun fitCentre(
        availableWidth: Int,
        availableHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): VideoRect {
        val fitted = fitAspectRatio(availableWidth, availableHeight, videoWidth, videoHeight)
        val left = (availableWidth - fitted.width) / 2
        val top = (availableHeight - fitted.height) / 2
        return VideoRect(left, top, left + fitted.width, top + fitted.height)
    }
}
