package dev.waypad.android.core.audio

/**
 * How much *later* than expected packets are arriving, relative to the start of the session.
 *
 * The obvious statistic — `System.currentTimeMillis() - header.timestamp_ms` — is not computable:
 * the timestamp comes from the host's wall clock and the subtraction from the phone's, and the two
 * are independent. Measured on a normally NTP-synced pair, that offset was 332 ms, which made the
 * "age" of every packet read as roughly -350 ms. A statistic that prints an impossible number is
 * worse than no statistic at all, because the next person to debug this spends their time on it.
 *
 * What *is* computable is the change in that difference. Both clocks advance at the same rate, so
 * `local - source` is the true transit time plus an unknown constant, and subtracting the smallest
 * such value seen in the session cancels the constant. What is left is the relative transit time:
 * 0 for the fastest packet of the session, positive for everything that queued somewhere on the
 * way, and never negative — so a reading can no longer be an impossible number. A value that keeps
 * climbing is delay accumulating between the encoder and this queue.
 *
 * The local side uses [android.os.SystemClock.elapsedRealtime], which cannot jump when the phone
 * re-syncs its clock. Anchoring on the minimum rather than on the first packet matters because the
 * first packet of a session usually arrives at the head of a burst, which would bias every later
 * reading by that burst's own delay.
 */
class AudioArrivalDrift {

    private var minOffsetMs = Long.MAX_VALUE
    private var driftMs = 0L

    fun reset() {
        minOffsetMs = Long.MAX_VALUE
        driftMs = 0L
    }

    fun onPacket(sourceTimestampMs: Long, localElapsedMs: Long) {
        if (sourceTimestampMs <= 0L) return
        val offset = localElapsedMs - sourceTimestampMs
        if (offset < minOffsetMs) minOffsetMs = offset
        driftMs = offset - minOffsetMs
    }

    /** Milliseconds of transit delay above the best the session has seen; never negative. */
    fun driftMs(): Long = driftMs
}
