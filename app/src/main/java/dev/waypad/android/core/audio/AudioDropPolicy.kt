package dev.waypad.android.core.audio

/**
 * Backlog rules for the audio path, the counterpart of
 * [dev.waypad.android.core.video.FrameDropPolicy].
 *
 * Same philosophy, simpler mechanics. Video may only skip forward to a key frame because its frames
 * reference each other; every Opus packet decodes on its own, so a backlog can be cut anywhere. What
 * does *not* change is the rule: audio that arrives faster than it plays must be thrown away, never
 * queued. A speaker cannot catch up by playing faster, so every buffered packet is permanent added
 * latency — a stream that never drops drifts further behind the picture for as long as it runs.
 */
object AudioDropPolicy {

    /**
     * Above this much audio queued for the speaker the stream is late enough to be worth cutting.
     *
     * Measured on Wi-Fi, packets arrive in bursts of up to eight, so a ceiling near one burst makes
     * the guard fire on ordinary jitter and leaves the track running dry between cuts.
     */
    const val DEFAULT_MAX_BUFFERED_MS = 180L

    /** Where a cut brings the backlog back to: enough to absorb jitter, short enough to stay in sync. */
    const val DEFAULT_TARGET_BUFFERED_MS = 90L

    /** Milliseconds of audio still queued for the speaker. */
    fun bufferedMs(framesWritten: Long, framesPlayed: Long, sampleRate: Int): Long {
        if (sampleRate <= 0) return 0
        val pending = (framesWritten - framesPlayed).coerceAtLeast(0)
        return pending * 1000L / sampleRate
    }

    /**
     * How many packets to discard so the backlog falls back to [targetMs].
     *
     * Returns 0 while the backlog is still within [maxMs]: dropping earlier would trade a harmless
     * amount of jitter tolerance for audible gaps.
     */
    fun packetsToDrop(
        bufferedMs: Long,
        packetMs: Long,
        maxMs: Long = DEFAULT_MAX_BUFFERED_MS,
        targetMs: Long = DEFAULT_TARGET_BUFFERED_MS,
    ): Int {
        if (packetMs <= 0 || bufferedMs <= maxMs) return 0
        val excess = bufferedMs - targetMs.coerceAtMost(maxMs)
        if (excess <= 0) return 0
        // Round up: leaving a fraction of a packet behind would keep the guard armed forever.
        return ((excess + packetMs - 1) / packetMs).toInt()
    }
}
