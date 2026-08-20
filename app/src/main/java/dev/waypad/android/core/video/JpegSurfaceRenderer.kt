package dev.waypad.android.core.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "WaypadJpegRenderer"

/**
 * Legacy `WAYPAD_STREAM_V1` path: software JPEG decode drawn onto the very same [Surface] the
 * hardware decoder uses, so the view layer is identical for both protocol versions.
 *
 * Two things keep it from behaving like the old client:
 * - only the newest frame is kept (JPEG is all-intra, so dropping is always safe);
 * - the decode target bitmap is recycled through `inBitmap` instead of allocating ~120 MB/s.
 */
class JpegSurfaceRenderer(
    private val listener: VideoRenderListener = VideoRenderListener.NoOp,
) {
    private val pending = AtomicReference<EncodedVideoFrame?>(null)
    private val thread = HandlerThread("waypad-jpeg").apply { start() }
    private val handler = Handler(thread.looper)
    private val drainRunnable = Runnable { drain() }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val destination = Rect()
    private val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
        inMutable = true
    }

    @Volatile
    private var released = false

    @Volatile
    private var droppedCount = 0L

    private var surface: Surface? = null
    private var reusable: Bitmap? = null
    private var lastSize = VideoSize.Unknown

    val droppedFrames: Long get() = droppedCount

    fun setSurface(newSurface: Surface?) {
        if (released) return
        if (newSurface == null) {
            runBlockingOnRenderer("detach_surface") { surface = null }
        } else {
            handler.post { surface = newSurface }
        }
    }

    fun submit(frame: EncodedVideoFrame) {
        if (released) return
        val previous = pending.getAndSet(frame)
        if (previous != null) countDrop()
        handler.post(drainRunnable)
    }

    fun reset() {
        pending.set(null)
        lastSize = VideoSize.Unknown
    }

    fun release() {
        if (released) return
        released = true
        pending.set(null)
        runBlockingOnRenderer("release") {
            surface = null
            reusable?.recycle()
            reusable = null
        }
        thread.quitSafely()
        runCatching { thread.join(RELEASE_JOIN_TIMEOUT_MS) }
    }

    // --- renderer thread -----------------------------------------------------------------------

    private fun drain() {
        if (released) return
        val frame = pending.getAndSet(null) ?: return
        val target = surface?.takeIf { it.isValid }
        if (target == null) {
            countDrop()
            return
        }
        val bitmap = decode(frame) ?: return
        val size = VideoSize(bitmap.width, bitmap.height)
        if (size.isValid && size != lastSize) {
            lastSize = size
            listener.onVideoSizeChanged(size)
        }
        if (paint(target, bitmap)) {
            listener.onFrameRendered(frame.header.timestampMs * 1_000L)
        } else {
            countDrop()
        }
    }

    private fun decode(frame: EncodedVideoFrame): Bitmap? {
        options.inBitmap = reusable
        val decoded = try {
            BitmapFactory.decodeByteArray(frame.payload, 0, frame.sizeBytes, options)
        } catch (_: IllegalArgumentException) {
            // The cached bitmap could not be reused (size or config changed): retry from scratch.
            options.inBitmap = null
            reusable?.recycle()
            reusable = null
            runCatching { BitmapFactory.decodeByteArray(frame.payload, 0, frame.sizeBytes, options) }.getOrNull()
        }
        if (decoded == null) {
            Log.w(TAG, "jpeg_decode_failed seq=${frame.header.seq} bytes=${frame.sizeBytes}")
            countDrop()
            return null
        }
        if (decoded !== reusable) {
            reusable?.takeIf { it !== decoded }?.recycle()
            reusable = decoded
        }
        return decoded
    }

    private fun paint(target: Surface, bitmap: Bitmap): Boolean {
        val canvas = lockCanvas(target) ?: return false
        try {
            canvas.drawColor(Color.BLACK)
            val fitted = VideoLayout.fitCentre(canvas.width, canvas.height, bitmap.width, bitmap.height)
            destination.set(fitted.left, fitted.top, fitted.right, fitted.bottom)
            canvas.drawBitmap(bitmap, null, destination, paint)
        } finally {
            runCatching { target.unlockCanvasAndPost(canvas) }
        }
        return true
    }

    private fun lockCanvas(target: Surface): Canvas? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            target.lockHardwareCanvas()
        } else {
            target.lockCanvas(null)
        }
    }.getOrElse {
        Log.w(TAG, "lock_canvas_failed", it)
        null
    }

    private fun countDrop() {
        droppedCount += 1
        listener.onFramesDropped(1)
    }

    private fun runBlockingOnRenderer(label: String, block: () -> Unit) {
        if (Thread.currentThread() === thread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        if (!handler.post {
                try {
                    block()
                } finally {
                    latch.countDown()
                }
            }
        ) {
            return
        }
        if (!latch.await(BLOCKING_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "renderer_op_timeout op=$label")
        }
    }

    private companion object {
        const val BLOCKING_OP_TIMEOUT_MS = 1_500L
        const val RELEASE_JOIN_TIMEOUT_MS = 1_000L
    }
}
