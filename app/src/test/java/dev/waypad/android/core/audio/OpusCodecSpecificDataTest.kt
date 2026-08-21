package dev.waypad.android.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusCodecSpecificDataTest {

    @Test
    fun buildsTheThreeBuffersMediaCodecRequires() {
        // Missing any one of them makes configure() throw with no indication of which, so the
        // count and the order are part of the contract.
        val csd = OpusCodecSpecificData.build(channels = 2, preSkipSamples = 312)

        assertEquals(3, csd.size)
        assertEquals(OpusCodecSpecificData.OPUS_HEAD_SIZE, csd[0].size)
        assertEquals(8, csd[1].size)
        assertEquals(8, csd[2].size)
    }

    @Test
    fun opusHeadFollowsRfc7845Layout() {
        val head = OpusCodecSpecificData.opusHead(channels = 2, preSkipSamples = 312)

        assertEquals("OpusHead", String(head, 0, 8, Charsets.US_ASCII))
        assertEquals(1, head[8].toInt())
        assertEquals(2, head[9].toInt())
        // Pre-skip and input rate are little-endian, unlike the nanosecond buffers.
        assertEquals(312, OpusCodecSpecificData.preSkipOf(head))
        val rate = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        assertEquals(48_000, rate)
        // Output gain 0 and channel mapping family 0, the only family valid for mono/stereo.
        assertEquals(0, ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getShort(16).toInt())
        assertEquals(0, head[18].toInt())
    }

    @Test
    fun preSkipAndPreRollAreNanosecondsInNativeOrder() {
        val csd = OpusCodecSpecificData.build(channels = 2, preSkipSamples = 312)

        // 312 samples at 48 kHz is 6.5 ms.
        val preSkipNs = ByteBuffer.wrap(csd[1]).order(ByteOrder.nativeOrder()).long
        assertEquals(6_500_000L, preSkipNs)
        // The RFC recommends an 80 ms seek pre-roll, which is 3840 samples.
        val preRollNs = ByteBuffer.wrap(csd[2]).order(ByteOrder.nativeOrder()).long
        assertEquals(80_000_000L, preRollNs)
        assertArrayEquals(OpusCodecSpecificData.nativeOrderLong(6_500_000L), csd[1])
    }

    @Test
    fun clampsValuesThatWouldOverflowTheHeaderFields() {
        // Pre-skip is a u16 in OpusHead; a daemon sending nonsense must not corrupt the header.
        val head = OpusCodecSpecificData.opusHead(channels = 7, preSkipSamples = 999_999)

        assertEquals(2, head[9].toInt())
        assertTrue(OpusCodecSpecificData.preSkipOf(head) <= 65_535)
        assertEquals(1, OpusCodecSpecificData.opusHead(channels = 0, preSkipSamples = 0)[9].toInt())
    }

    @Test
    fun mapsAudioHeaderFieldsOntoTheDecoderFormat() {
        val spec = AudioFormatSpec.of(
            dev.waypad.android.core.network.ScreenStreamProtocol.parseHeader(
                """{"seq":3,"timestamp_ms":17,"codec":"opus","sample_rate":48000,"channels":2,
                   "frame_ms":10,"pre_skip":312,"key_frame":false,"config":false}""",
            ),
        )

        assertEquals(48_000, spec.sampleRate)
        assertEquals(2, spec.channels)
        assertEquals(10, spec.frameMs)
        assertEquals(312, spec.preSkipSamples)
        assertEquals(480, spec.framesPerPacket)
    }

    @Test
    fun fallsBackToOpusDefaultsWhenTheDaemonOmitsTheFormat() {
        // Every audio envelope repeats the format, but a daemon that trims the header must still
        // yield a configurable decoder rather than none.
        val spec = AudioFormatSpec.of(
            dev.waypad.android.core.network.ScreenStreamProtocol.parseHeader(
                """{"seq":1,"timestamp_ms":0,"codec":"opus"}""",
            ),
        )

        assertEquals(OpusCodecSpecificData.OPUS_SAMPLE_RATE, spec.sampleRate)
        assertEquals(2, spec.channels)
        assertEquals(20, spec.frameMs)
        assertEquals(OpusCodecSpecificData.DEFAULT_PRE_SKIP_SAMPLES, spec.preSkipSamples)
    }
}
