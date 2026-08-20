package dev.waypad.android.core.video

import dev.waypad.android.core.network.StreamFrameHeader

/** What the decoder should do with an incoming h264 envelope. */
enum class GateDecision {
    /** SPS/PPS: configures the codec, never dropped. */
    FeedConfig,

    /** Decodable frame, the reference chain is intact. */
    Feed,

    /** No SPS/PPS seen yet, the codec cannot even be configured. */
    DropAwaitingConfig,

    /** Codec is (re)configured but the reference chain is broken until the next key frame. */
    DropAwaitingKeyFrame,

    /** Older than something already decoded. */
    DropStale,
}

/**
 * Enforces the `config -> key frame -> frames` ordering an h264 decoder needs.
 *
 * Feeding P frames before SPS/PPS and an IDR produces either a decoder error or a garbage picture,
 * so everything that arrives before the stream is synchronised is discarded on purpose.
 */
class H264SyncGate {
    var hasConfig: Boolean = false
        private set

    /** True once SPS/PPS and a key frame have been fed to the codec. */
    var synced: Boolean = false
        private set

    var droppedAwaitingSync: Long = 0L
        private set

    private var lastFedSeq = Long.MIN_VALUE

    /**
     * @param configChanged whether a `config` envelope carries parameter sets different from the
     * ones the codec is already running with. Daemons repeat SPS/PPS periodically; only a real
     * change invalidates the reference chain, a verbatim repeat must not stall the stream.
     */
    fun admit(header: StreamFrameHeader, configChanged: Boolean = true): GateDecision {
        if (header.config) {
            hasConfig = true
            if (configChanged) {
                synced = false
                lastFedSeq = Long.MIN_VALUE
            }
            return GateDecision.FeedConfig
        }
        if (!hasConfig) {
            droppedAwaitingSync++
            return GateDecision.DropAwaitingConfig
        }
        if (!synced) {
            if (!header.keyFrame) {
                droppedAwaitingSync++
                return GateDecision.DropAwaitingKeyFrame
            }
            synced = true
            lastFedSeq = header.seq
            return GateDecision.Feed
        }
        if (header.seq <= lastFedSeq) return GateDecision.DropStale
        lastFedSeq = header.seq
        return GateDecision.Feed
    }

    /** The codec was recreated or the backlog was cut: wait for the next key frame. */
    fun requestResync() {
        synced = false
        lastFedSeq = Long.MIN_VALUE
    }

    /** Forgets the cached parameter sets too, e.g. when the session restarts. */
    fun reset() {
        hasConfig = false
        synced = false
        droppedAwaitingSync = 0L
        lastFedSeq = Long.MIN_VALUE
    }
}
