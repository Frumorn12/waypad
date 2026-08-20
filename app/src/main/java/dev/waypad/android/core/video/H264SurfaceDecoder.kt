package dev.waypad.android.core.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "WaypadH264Decoder"

/**
 * Hardware h264 decoder rendering straight onto a [Surface].
 *
 * `MediaCodec` runs in asynchronous mode and the output buffers are released with `render = true`,
 * so decoded pixels never leave the GPU: no `ImageReader`, no `Bitmap`, no copy.
 *
 * Every codec operation — creation, feeding, teardown and the `MediaCodec.Callback` itself — runs
 * on the decoder's own [HandlerThread]. Serialising on a single thread instead of guarding a
 * shared codec with a lock is what keeps `stop()` from deadlocking against a callback that is
 * waiting for that same lock.
 */
class H264SurfaceDecoder(
    private val listener: VideoRenderListener = VideoRenderListener.NoOp,
) {
    private val queue = EncodedFrameQueue()
    private val gate = H264SyncGate()
    private val thread = HandlerThread("waypad-h264").apply { start() }
    private val handler = Handler(thread.looper)
    private val drainRunnable = Runnable { drain() }

    @Volatile
    private var released = false

    // Handler-thread confined state.
    private val inputBuffers = ArrayDeque<Int>()
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var codecConfig: ByteArray? = null
    private var announcedSize = VideoSize.Unknown
    private var configuredSize = VideoSize.Unknown
    private var generation = 0
    private var pendingOutputs = 0
    private var lastQueuedPtsUs = Long.MIN_VALUE
    private var lastRenderedPtsUs = Long.MIN_VALUE
    private var errorBurst = 0
    private var errorBurstStartedAt = 0L
    private var codecFatal = false
    private var keyFrameRequested = false

    @Volatile
    private var decoderDrops: Long = 0L

    /** Frames thrown away either by the backlog pruning or by the decoder itself. */
    val droppedFrames: Long
        get() = decoderDrops + queue.droppedFrames

    /**
     * Binds the decoder to a surface. Pass `null` from `surfaceDestroyed`: the call blocks until
     * the codec has actually let go of the surface, because configuring or rendering onto a
     * destroyed surface throws.
     */
    fun setSurface(newSurface: Surface?) {
        if (released) return
        if (newSurface == null) {
            runBlockingOnDecoder("detach_surface") { applySurface(null) }
        } else {
            handler.post { applySurface(newSurface) }
        }
    }

    /** Hands a frame over without blocking; the socket reader must never wait on the decoder. */
    fun submit(frame: EncodedVideoFrame) {
        if (released) return
        queue.offer(frame)
        handler.post(drainRunnable)
    }

    /** Drops the backlog and waits for a fresh key frame, e.g. after a reconnect. */
    fun reset() {
        if (released) return
        queue.clear()
        handler.post {
            gate.reset()
            codecConfig = null
            announcedSize = VideoSize.Unknown
            codecFatal = false
            errorBurst = 0
            keyFrameRequested = false
            teardownCodec("reset")
        }
    }

    fun release() {
        if (released) return
        released = true
        queue.clear()
        runBlockingOnDecoder("release") {
            teardownCodec("release")
            surface = null
        }
        thread.quitSafely()
        runCatching { thread.join(RELEASE_JOIN_TIMEOUT_MS) }
    }

    // --- handler thread ------------------------------------------------------------------------

    private fun applySurface(newSurface: Surface?) {
        if (surface === newSurface) return
        Log.i(TAG, "surface_change valid=${newSurface?.isValid == true}")
        surface = newSurface
        codecFatal = false
        teardownCodec("surface_change")
        // The codec is gone but the parameter sets survive, so only a key frame is missing.
        requestResync("surface_change")
        if (newSurface != null) ensureCodec()
        drain()
    }

    private fun drain() {
        if (released) return
        if (queue.consumeContinuityBreak()) {
            Log.d(TAG, "backlog_pruned resync_required dropped=${queue.droppedFrames}")
            requestResync("backlog_pruned")
        }
        while (true) {
            val head = queue.peek() ?: return
            if (head.header.config) {
                queue.poll()
                applyCodecConfig(head)
                continue
            }
            if (surface == null) {
                // Nothing to render onto: burn the backlog instead of letting it rot.
                queue.poll()
                countDrop(1)
                continue
            }
            if (!announcedSize.isValid && head.header.width > 0 && head.header.height > 0) {
                // Some daemons omit the size on the config envelope; take it from the first frame.
                announcedSize = VideoSize(head.header.width, head.header.height)
            }
            if (codec == null && !ensureCodec()) {
                queue.poll()
                countDrop(1)
                continue
            }
            if (inputBuffers.isEmpty()) return
            val frame = queue.poll() ?: return
            when (val decision = gate.admit(frame.header)) {
                GateDecision.Feed -> feed(frame)
                GateDecision.FeedConfig -> applyCodecConfig(frame)
                else -> {
                    countDrop(1)
                    if (decision == GateDecision.DropAwaitingKeyFrame) {
                        Log.v(TAG, "frame_drop reason=awaiting_key_frame seq=${frame.header.seq}")
                    }
                }
            }
        }
    }

    private fun applyCodecConfig(frame: EncodedVideoFrame) {
        val payload = frame.payload
        val headerSize = VideoSize(frame.header.width, frame.header.height)
        if (headerSize.isValid) announcedSize = headerSize
        val changed = !payload.contentEquals(codecConfig) ||
            (announcedSize.isValid && configuredSize.isValid && announcedSize != configuredSize)
        codecConfig = payload
        gate.admit(frame.header, configChanged = changed)
        if (!changed && codec != null) return

        Log.i(TAG, "codec_config bytes=${payload.size} size=$announcedSize changed=$changed")
        // Fresh parameter sets are a fresh chance for a codec that had given up.
        if (changed) codecFatal = false
        teardownCodec("codec_config")
        ensureCodec()
    }

    private fun ensureCodec(): Boolean {
        if (codec != null) return true
        if (codecFatal) return false
        val activeSurface = surface ?: return false
        if (!activeSurface.isValid) return false
        val csd = codecConfig ?: return false
        val size = announcedSize.takeIf { it.isValid } ?: return false

        var created: MediaCodec? = null
        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height)
            format.setByteBuffer(CSD_0, ByteBuffer.wrap(csd))
            format.setInteger(MediaFormat.KEY_PRIORITY, PRIORITY_REALTIME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            created = decoder
            generation += 1
            decoder.setCallback(callbackFor(generation), handler)
            decoder.configure(format, activeSurface, null, 0)
            decoder.start()
            codec = decoder
            configuredSize = size
            inputBuffers.clear()
            pendingOutputs = 0
            lastQueuedPtsUs = Long.MIN_VALUE
            lastRenderedPtsUs = Long.MIN_VALUE
            requestResync("codec_started")
            Log.i(TAG, "codec_started ${size.width}x${size.height} lowLatency=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.R}")
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "codec_start_failed", throwable)
            // The instance never reached `codec`, so teardownCodec would not free it.
            created?.let { runCatching { it.release() } }
            generation += 1
            listener.onRenderError(throwable)
            false
        }
    }

    private fun feed(frame: EncodedVideoFrame) {
        val active = codec ?: return
        val index = inputBuffers.removeFirstOrNull() ?: return
        val buffer = try {
            active.getInputBuffer(index)
        } catch (throwable: IllegalStateException) {
            Log.w(TAG, "input_buffer_unavailable", throwable)
            null
        }
        if (buffer == null) {
            countDrop(1)
            return
        }
        if (buffer.capacity() < frame.sizeBytes) {
            Log.w(TAG, "frame_too_large bytes=${frame.sizeBytes} capacity=${buffer.capacity()}")
            countDrop(1)
            requestResync("frame_too_large")
            return
        }
        val ptsUs = presentationTimeUs(frame)
        try {
            buffer.clear()
            buffer.put(frame.payload)
            active.queueInputBuffer(index, 0, frame.sizeBytes, ptsUs, 0)
            lastQueuedPtsUs = ptsUs
            pendingOutputs += 1
            if (frame.header.keyFrame) keyFrameRequested = false
        } catch (throwable: Throwable) {
            Log.w(TAG, "queue_input_failed seq=${frame.header.seq}", throwable)
            handleCodecFailure(throwable)
        }
    }

    private fun presentationTimeUs(frame: EncodedVideoFrame): Long {
        val header = frame.header
        val raw = if (header.timestampMs > 0L) header.timestampMs * 1_000L else header.seq * 1_000L
        // MediaCodec requires monotonic timestamps; daemon clock hiccups must not stall rendering.
        return if (raw <= lastQueuedPtsUs) lastQueuedPtsUs + 1_000L else raw
    }

    private fun callbackFor(codecGeneration: Int) = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (released || codecGeneration != generation) return
            inputBuffers.addLast(index)
            drain()
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (released || codecGeneration != generation) return
            pendingOutputs = (pendingOutputs - 1).coerceAtLeast(0)
            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            // A newer picture is already waiting: showing this one would only add latency.
            val superseded = queue.size() > 0 || pendingOutputs > RENDER_PIPELINE_TOLERANCE
            val render = info.size > 0 &&
                !isConfig &&
                !superseded &&
                info.presentationTimeUs >= lastRenderedPtsUs
            try {
                codec.releaseOutputBuffer(index, render)
            } catch (throwable: IllegalStateException) {
                Log.w(TAG, "release_output_failed", throwable)
                return
            }
            if (render) {
                lastRenderedPtsUs = info.presentationTimeUs
                listener.onFrameRendered(info.presentationTimeUs)
            } else if (!isConfig && info.size > 0) {
                countDrop(1)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (released || codecGeneration != generation) return
            // Reported only: announcedSize stays the header size, which is what configure() used.
            val size = decodedSizeOf(format)
            Log.i(TAG, "output_format size=$size")
            if (size.isValid) listener.onVideoSizeChanged(size)
        }

        override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
            if (released || codecGeneration != generation) return
            Log.e(TAG, "codec_error recoverable=${error.isRecoverable} transient=${error.isTransient}", error)
            handler.post { handleCodecFailure(error) }
        }
    }

    private fun handleCodecFailure(error: Throwable) {
        if (released) return
        val now = SystemClock.elapsedRealtime()
        if (now - errorBurstStartedAt > ERROR_BURST_WINDOW_MS) {
            errorBurstStartedAt = now
            errorBurst = 0
        }
        errorBurst += 1
        teardownCodec("codec_error")
        queue.clear()
        requestResync("codec_error")
        if (errorBurst > MAX_ERRORS_PER_WINDOW) {
            Log.e(TAG, "codec_error_burst count=$errorBurst giving_up")
            // Stop the recreate loop until a new surface or a new parameter set shows up.
            codecFatal = true
            listener.onRenderError(error)
            return
        }
        ensureCodec()
    }

    private fun teardownCodec(reason: String) {
        val current = codec ?: return
        generation += 1
        codec = null
        inputBuffers.clear()
        pendingOutputs = 0
        configuredSize = VideoSize.Unknown
        Log.i(TAG, "codec_teardown reason=$reason")
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    /** Waits for the next IDR and asks the caller to solicit one, at most once per resync. */
    private fun requestResync(reason: String) {
        gate.requestResync()
        if (keyFrameRequested) return
        keyFrameRequested = true
        Log.i(TAG, "key_frame_needed reason=$reason")
        listener.onKeyFrameNeeded()
    }

    private fun countDrop(count: Int) {
        if (count <= 0) return
        decoderDrops += count
        listener.onFramesDropped(count)
    }

    private fun runBlockingOnDecoder(label: String, block: () -> Unit) {
        if (Thread.currentThread() === thread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        val posted = handler.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        if (!posted) return
        if (!latch.await(BLOCKING_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "decoder_op_timeout op=$label")
        }
    }

    private companion object {
        const val CSD_0 = "csd-0"
        const val PRIORITY_REALTIME = 0
        const val RENDER_PIPELINE_TOLERANCE = 2
        const val ERROR_BURST_WINDOW_MS = 5_000L
        const val MAX_ERRORS_PER_WINDOW = 3
        const val BLOCKING_OP_TIMEOUT_MS = 1_500L
        const val RELEASE_JOIN_TIMEOUT_MS = 1_000L

        /**
         * `MediaFormat.containsKey` only exists from API 29, so optional keys are probed by
         * catching instead.
         */
        fun optInt(format: MediaFormat, key: String): Int? =
            runCatching { format.getInteger(key) }.getOrNull()

        fun decodedSizeOf(format: MediaFormat): VideoSize {
            val cropLeft = optInt(format, "crop-left")
            val cropRight = optInt(format, "crop-right")
            val cropTop = optInt(format, "crop-top")
            val cropBottom = optInt(format, "crop-bottom")
            val width = if (cropLeft != null && cropRight != null) {
                cropRight - cropLeft + 1
            } else {
                optInt(format, MediaFormat.KEY_WIDTH) ?: 0
            }
            val height = if (cropTop != null && cropBottom != null) {
                cropBottom - cropTop + 1
            } else {
                optInt(format, MediaFormat.KEY_HEIGHT) ?: 0
            }
            return VideoSize(width, height)
        }
    }
}
