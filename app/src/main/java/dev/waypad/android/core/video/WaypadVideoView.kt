package dev.waypad.android.core.video

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

private const val TAG = "WaypadVideoView"

/**
 * `SurfaceView` that owns the surface the remote screen is decoded onto.
 *
 * The surface of a `SurfaceView` is destroyed whenever the window goes away (app backgrounded,
 * screen off, configuration change) and created again on the way back. `surfaceDestroyed` must not
 * return before the decoder let go of it, otherwise the next `MediaCodec.configure()` runs against
 * a dead surface and throws, so the detach is deliberately synchronous.
 *
 * The view measures itself to the video aspect ratio (letterbox). Place it inside a centering
 * container, e.g. a Compose `Box(contentAlignment = Alignment.Center)`.
 */
class WaypadVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var renderer: RemoteVideoRenderer? = null
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        holder.addCallback(this)
    }

    /**
     * Binds the view to a renderer. Safe to call repeatedly; the previous renderer is detached
     * first. Call from the main thread.
     */
    fun attachRenderer(target: RemoteVideoRenderer) {
        if (renderer === target) return
        detachRenderer()
        renderer = target
        target.onVideoSize = { size -> post { setVideoSize(size.width, size.height) } }
        target.videoSize.value.takeIf { it.isValid }?.let { setVideoSize(it.width, it.height) }
        if (holder.surface?.isValid == true) target.attachSurface(holder.surface)
    }

    fun detachRenderer() {
        val current = renderer ?: return
        renderer = null
        current.onVideoSize = null
        current.attachSurface(null)
    }

    /** Drives the letterboxing; safe to call from any thread. */
    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == videoWidth && height == videoHeight) return
        if (!isMainThread()) {
            post { setVideoSize(width, height) }
            return
        }
        Log.i(TAG, "video_size ${width}x$height")
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surface_created")
        renderer?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "surface_changed ${width}x$height")
        renderer?.attachSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "surface_destroyed")
        // Blocks until the decoder detached; returning early would leave it drawing on a dead
        // surface.
        renderer?.attachSurface(null)
    }

    override fun onDetachedFromWindow() {
        detachRenderer()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (videoWidth <= 0 || videoHeight <= 0 ||
            widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED
        ) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val fitted = VideoLayout.fitAspectRatio(availableWidth, availableHeight, videoWidth, videoHeight)
        setMeasuredDimension(fitted.width, fitted.height)
    }

    private fun isMainThread(): Boolean = Looper.myLooper() === Looper.getMainLooper()
}
