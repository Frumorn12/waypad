package dev.waypad.android.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds the three codec-specific data buffers Android's Opus decoder demands.
 *
 * `MediaCodec` refuses to configure an `audio/opus` decoder unless **all three** are present, and
 * the failure is a bare `IllegalArgumentException` from `configure()` that says nothing about which
 * one is missing:
 *
 * - `csd-0` is the 19 byte *OpusHead* identification header of RFC 7845;
 * - `csd-1` is the encoder pre-skip expressed **in nanoseconds**, as an 8 byte long;
 * - `csd-2` is the seek pre-roll, also in nanoseconds and 8 bytes.
 *
 * Both longs are written in the machine's native byte order, not big-endian: the framework reads
 * them straight back into a host `int64_t`.
 *
 * Nothing here comes off the wire as a payload. The daemon never sends an init envelope — an audio
 * envelope claiming `config: true` would compete with the H.264 parameter sets inside the video
 * batch pruner — so every field needed to synthesise OpusHead travels as a plain header value on
 * every packet, and the header is rebuilt locally.
 */
object OpusCodecSpecificData {

    /** Opus always decodes at 48 kHz whatever the capture rate was. */
    const val OPUS_SAMPLE_RATE = 48_000

    /** libopus reports a 6.5 ms lookahead at 48 kHz; used when the daemon sends no `pre_skip`. */
    const val DEFAULT_PRE_SKIP_SAMPLES = 312

    /** 80 ms, the value RFC 7845 recommends and every Android muxer writes. */
    const val DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3_840

    const val OPUS_HEAD_SIZE = 19

    private val MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)

    /**
     * The RFC 7845 identification header:
     * ```
     * "OpusHead" | version:1 | channels:1 | preSkip:2 | inputRate:4 | outputGain:2 | mapping:1
     * ```
     * Every multi-byte field is little-endian, unlike the nanosecond buffers above which follow the
     * host order. Channel mapping family 0 covers mono and stereo, which is all this stream carries.
     */
    fun opusHead(
        channels: Int,
        preSkipSamples: Int = DEFAULT_PRE_SKIP_SAMPLES,
        inputSampleRate: Int = OPUS_SAMPLE_RATE,
        outputGain: Int = 0,
    ): ByteArray {
        val safeChannels = channels.coerceIn(1, 2)
        val safePreSkip = preSkipSamples.coerceIn(0, UShort.MAX_VALUE.toInt())
        return ByteBuffer.allocate(OPUS_HEAD_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MAGIC)
            .put(1)
            .put(safeChannels.toByte())
            .putShort(safePreSkip.toShort())
            .putInt(inputSampleRate)
            .putShort(outputGain.toShort())
            .put(0)
            .array()
    }

    /** Duration of [samples] at the Opus decode rate, in nanoseconds. */
    fun nanosOfSamples(samples: Int): Long = samples.toLong() * 1_000_000_000L / OPUS_SAMPLE_RATE

    /** An 8 byte long in the host's byte order, which is what the framework parses. */
    fun nativeOrderLong(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array()

    /**
     * The complete `csd-0`/`csd-1`/`csd-2` triple, in the order `MediaFormat` expects them.
     */
    fun build(
        channels: Int,
        preSkipSamples: Int = DEFAULT_PRE_SKIP_SAMPLES,
        inputSampleRate: Int = OPUS_SAMPLE_RATE,
        seekPreRollSamples: Int = DEFAULT_SEEK_PRE_ROLL_SAMPLES,
    ): List<ByteArray> = listOf(
        opusHead(channels, preSkipSamples, inputSampleRate),
        nativeOrderLong(nanosOfSamples(preSkipSamples.coerceAtLeast(0))),
        nativeOrderLong(nanosOfSamples(seekPreRollSamples.coerceAtLeast(0))),
    )

    /** Reads the pre-skip back out of a header, mostly so the tests can assert round trips. */
    fun preSkipOf(opusHead: ByteArray): Int {
        require(opusHead.size >= OPUS_HEAD_SIZE) { "OpusHead is ${opusHead.size} bytes" }
        return ByteBuffer.wrap(opusHead).order(ByteOrder.LITTLE_ENDIAN).getShort(10).toInt() and 0xFFFF
    }
}
