package dev.waypad.android.core.video

/** Rolling measurements of the live stream, refreshed on every received and rendered frame. */
data class VideoStreamMetrics(
    val receivedFps: Double = 0.0,
    val renderedFps: Double = 0.0,
    val bytesPerSecond: Long = 0L,
    val averageKib: Int = 0,
    val lastFrameAgeMs: Long = 0L,
    val receivedFrames: Long = 0L,
    val renderedFrames: Long = 0L,
    val droppedFrames: Long = 0L,
)

/**
 * Sliding-window statistics over the last [windowMs].
 *
 * A sliding window rather than the periodically reset counters of the old client: those made the
 * reported fps collapse to zero right after every reset.
 */
class VideoStreamStatsTracker(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private class Sample(val atMs: Long, val bytes: Int)

    private val received = ArrayDeque<Sample>()
    private val rendered = ArrayDeque<Long>()

    private var receivedFrames = 0L
    private var renderedFrames = 0L
    private var droppedFrames = 0L
    private var lastFrameAgeMs = 0L

    @Synchronized
    fun onFrameReceived(bytes: Int, ageMs: Long, nowMs: Long) {
        receivedFrames++
        lastFrameAgeMs = ageMs.coerceAtLeast(0L)
        received.addLast(Sample(nowMs, bytes))
        trim(nowMs)
    }

    @Synchronized
    fun onFrameRendered(nowMs: Long) {
        renderedFrames++
        rendered.addLast(nowMs)
        trim(nowMs)
    }

    @Synchronized
    fun onFramesDropped(count: Long) {
        if (count > 0) droppedFrames += count
    }

    /** Replaces the running drop counter with an absolute value owned by another component. */
    @Synchronized
    fun setDroppedFrames(total: Long) {
        droppedFrames = total
    }

    @Synchronized
    fun snapshot(nowMs: Long): VideoStreamMetrics {
        trim(nowMs)
        val windowSeconds = windowMs / 1000.0
        val windowBytes = received.sumOf { it.bytes.toLong() }
        return VideoStreamMetrics(
            receivedFps = received.size / windowSeconds,
            renderedFps = rendered.size / windowSeconds,
            bytesPerSecond = (windowBytes / windowSeconds).toLong(),
            averageKib = if (received.isEmpty()) 0 else (windowBytes / received.size / 1024L).toInt(),
            lastFrameAgeMs = lastFrameAgeMs,
            receivedFrames = receivedFrames,
            renderedFrames = renderedFrames,
            droppedFrames = droppedFrames,
        )
    }

    @Synchronized
    fun reset() {
        received.clear()
        rendered.clear()
        receivedFrames = 0L
        renderedFrames = 0L
        droppedFrames = 0L
        lastFrameAgeMs = 0L
    }

    private fun trim(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (received.isNotEmpty() && received.first().atMs <= cutoff) received.removeFirst()
        while (rendered.isNotEmpty() && rendered.first() <= cutoff) rendered.removeFirst()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
    }
}
