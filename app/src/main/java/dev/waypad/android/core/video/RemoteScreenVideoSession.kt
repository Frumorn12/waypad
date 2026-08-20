package dev.waypad.android.core.video

import android.util.Log
import dev.waypad.android.core.model.ScreenStreamStats
import dev.waypad.android.core.network.RemoteScreenVideoStreamClient
import dev.waypad.android.core.network.ScreenStreamEndpoint
import dev.waypad.android.core.network.StreamProtocolVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WaypadVideoSession"

/**
 * Everything the UI needs to show a remote screen: a socket reader, a hardware decoder and the
 * statistics, wired together.
 *
 * Typical use:
 * ```
 * val session = RemoteScreenVideoSession()          // once, e.g. in the ViewModel
 * videoView.attachRenderer(session.renderer)        // from the Compose AndroidView factory
 * session.stream { attempt -> negotiateEndpoint() } // suspends until stopped or exhausted
 * ```
 */
class RemoteScreenVideoSession {

    private val statsTracker = VideoStreamStatsTracker()
    private val client = RemoteScreenVideoStreamClient(statsTracker)

    private val _stats = MutableStateFlow(ScreenStreamStats())
    private val _renderError = MutableStateFlow<String?>(null)

    /** Rolling stream statistics, ready to be shown by the UI. */
    val stats: StateFlow<ScreenStreamStats> = _stats.asStateFlow()

    /** Last unrecoverable decoder failure, or `null`. */
    val renderError: StateFlow<String?> = _renderError.asStateFlow()

    /** Owns the surface; hand it to [WaypadVideoView.attachRenderer]. */
    val renderer: RemoteVideoRenderer = RemoteVideoRenderer(
        object : VideoRenderListener {
            override fun onFrameRendered(presentationTimeUs: Long) {
                statsTracker.onFrameRendered(monotonicMs())
            }

            override fun onFramesDropped(count: Int) {
                statsTracker.onFramesDropped(count.toLong())
            }

            override fun onKeyFrameNeeded() {
                onKeyFrameNeeded?.invoke()
            }

            override fun onRenderError(error: Throwable) {
                Log.e(TAG, "render_error", error)
                _renderError.value = error.message ?: error::class.java.simpleName
            }
        },
    )

    /** Decoded picture size, for aspect-ratio aware layout outside [WaypadVideoView]. */
    val videoSize: StateFlow<VideoSize> get() = renderer.videoSize

    /** Fired once per stream, the first time a frame is received. */
    var onFirstFrame: (() -> Unit)? = null

    /**
     * Fired when the decoder is stuck waiting for an IDR — surface recreated after the app came
     * back from background, hot resolution change, or a backlog cut. Ask the daemon for a key
     * frame here if it supports it; otherwise the picture stays frozen until the next periodic one.
     */
    var onKeyFrameNeeded: (() -> Unit)? = null

    private var firstFrameSeen = false

    init {
        client.onMetrics = ::publishMetrics
    }

    /** Session values the socket cannot know; call after the daemon accepted the stream request. */
    fun updateSessionInfo(targetFps: Int, actualFps: Int, backend: String) {
        _stats.value = _stats.value.copy(targetFps = targetFps, actualFps = actualFps, backend = backend)
    }

    /**
     * Runs a single connection. Suspends until the stream ends and throws
     * [dev.waypad.android.core.network.RemoteScreenTransportException] on transport failures.
     */
    suspend fun collect(host: String, port: Int, token: String, transport: String) {
        resetForNewStream()
        client.collect(host, port, token, transport, sink)
    }

    /**
     * Reconnecting variant: [endpoint] is re-evaluated before every attempt so the caller can
     * renegotiate a fresh session with the daemon, and failures are retried with a linear backoff.
     *
     * @return normally when the stream ended without error; rethrows the last failure otherwise.
     */
    suspend fun stream(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        backoffMs: (attempt: Int) -> Long = { attempt -> RETRY_BASE_DELAY_MS * attempt },
        onAttempt: (attempt: Int) -> Unit = {},
        endpoint: suspend (attempt: Int) -> ScreenStreamEndpoint,
    ) {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            currentCoroutineContext().ensureActive()
            if (attempt > 1) delay(backoffMs(attempt - 1))
            onAttempt(attempt)
            try {
                val target = endpoint(attempt)
                collect(target.host, target.port, target.token, target.transport)
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                lastError = throwable
                Log.w(TAG, "stream_attempt_failed attempt=$attempt/$maxAttempts message=${throwable.message}")
            }
        }
        lastError?.let { throw it }
    }

    /** Releases the decoder and its threads. The session is unusable afterwards. */
    fun release() {
        client.onMetrics = null
        onFirstFrame = null
        onKeyFrameNeeded = null
        renderer.release()
    }

    private val sink = object : VideoFrameSink {
        override fun onStreamStarted(version: StreamProtocolVersion) {
            _stats.value = _stats.value.copy(codec = version.defaultCodec)
            renderer.onStreamStarted(version)
        }

        override fun onFrame(frame: EncodedVideoFrame) {
            renderer.onFrame(frame)
            if (!firstFrameSeen && !frame.header.config) {
                firstFrameSeen = true
                onFirstFrame?.invoke()
            }
        }

        override fun onStreamEnded(error: Throwable?) {
            renderer.onStreamEnded(error)
        }
    }

    private fun resetForNewStream() {
        firstFrameSeen = false
        statsTracker.reset()
        _renderError.value = null
        _stats.value = _stats.value.copy(
            estimatedFps = 0.0,
            deliveredFps = 0.0,
            averageKib = 0,
            bytesPerSecond = 0,
            lastFrameAgeMs = 0,
            droppedStaleFrames = 0,
            receivedFrames = 0,
        )
    }

    private fun publishMetrics(metrics: VideoStreamMetrics) {
        _stats.value = _stats.value.copy(
            estimatedFps = metrics.receivedFps,
            deliveredFps = metrics.renderedFps,
            averageKib = metrics.averageKib,
            bytesPerSecond = metrics.bytesPerSecond,
            lastFrameAgeMs = metrics.lastFrameAgeMs,
            droppedStaleFrames = metrics.droppedFrames,
            receivedFrames = metrics.receivedFrames,
        )
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 500L
    }
}
