package dev.waypad.android.core.audio

import dev.waypad.android.core.network.StreamFrameHeader

/** One audio envelope: the decoded metadata plus the still-encoded Opus packet. */
class EncodedAudioPacket(
    val header: StreamFrameHeader,
    val payload: ByteArray,
) {
    val sizeBytes: Int get() = payload.size

    val format: AudioFormatSpec get() = AudioFormatSpec.of(header)

    override fun toString(): String =
        "EncodedAudioPacket(seq=${header.seq}, ${format.sampleRate}Hz x${format.channels}, bytes=$sizeBytes)"
}

/**
 * Everything needed to configure the decoder, taken from the envelope header.
 *
 * Defaults cover a daemon that omits a field: Opus decodes at 48 kHz regardless of the capture
 * rate, and stereo is what the desktop monitor produces.
 */
data class AudioFormatSpec(
    val sampleRate: Int = OpusCodecSpecificData.OPUS_SAMPLE_RATE,
    val channels: Int = 2,
    val frameMs: Int = 20,
    val preSkipSamples: Int = OpusCodecSpecificData.DEFAULT_PRE_SKIP_SAMPLES,
) {
    /** Samples per channel in one packet, i.e. what one decoded packet adds to the playout buffer. */
    val framesPerPacket: Int get() = sampleRate * frameMs / 1000

    fun codecSpecificData(): List<ByteArray> =
        OpusCodecSpecificData.build(channels, preSkipSamples, sampleRate)

    companion object {
        fun of(header: StreamFrameHeader): AudioFormatSpec {
            val default = AudioFormatSpec()
            return AudioFormatSpec(
                sampleRate = header.sampleRate.takeIf { it > 0 } ?: default.sampleRate,
                channels = header.channels.takeIf { it in 1..2 } ?: default.channels,
                frameMs = header.frameMs.takeIf { it in 1..120 } ?: default.frameMs,
                // 0 reads as "field absent": the encoder lookahead is never actually zero, and a
                // missing pre-skip must fall back to the libopus value rather than to none.
                preSkipSamples = header.preSkipSamples.takeIf { it in 1 until 65_536 }
                    ?: default.preSkipSamples,
            )
        }
    }
}

/** Rolling playback statistics, ready to be shown next to the video ones. */
data class AudioPlaybackStats(
    val active: Boolean = false,
    val muted: Boolean = false,
    val hasFocus: Boolean = true,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val packetsReceived: Long = 0,
    val packetsDropped: Long = 0,
    /** How much decoded audio is still queued for the speaker; the latency to keep small. */
    val bufferedMs: Long = 0,
    val error: String? = null,
)

/** Callbacks raised on the player's own worker thread. */
interface AudioPlaybackListener {
    fun onPlaybackStarted(format: AudioFormatSpec) {}

    fun onPacketsDropped(count: Int) {}

    /** Fired when another app takes or returns the audio focus. */
    fun onFocusChanged(hasFocus: Boolean) {}

    /** Fatal: the decoder or the output track could not be brought back up. */
    fun onPlaybackError(error: Throwable) {}

    companion object {
        val NoOp: AudioPlaybackListener = object : AudioPlaybackListener {}
    }
}
