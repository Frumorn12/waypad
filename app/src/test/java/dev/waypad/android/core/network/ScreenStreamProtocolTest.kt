package dev.waypad.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStreamProtocolTest {

    @Test
    fun parsesH264FrameHeader() {
        val header = ScreenStreamProtocol.parseHeader(
            """{"seq": 42, "timestamp_ms": 1700000000123, "width": 1920, "height": 1080,
               "codec": "h264", "key_frame": true, "config": false}""",
            defaultCodec = ScreenStreamProtocol.CODEC_H264,
        )

        assertEquals(42L, header.seq)
        assertEquals(1_700_000_000_123L, header.timestampMs)
        assertEquals(1920, header.width)
        assertEquals(1080, header.height)
        assertEquals("h264", header.codec)
        assertTrue(header.keyFrame)
        assertFalse(header.config)
        assertTrue(header.isH264)
        assertFalse(header.isJpeg)
    }

    @Test
    fun parsesConfigHeaderAndMarksItUndroppable() {
        val header = ScreenStreamProtocol.parseHeader(
            """{"seq":0,"timestamp_ms":0,"width":1920,"height":1080,"codec":"h264","key_frame":true,"config":true}""",
            defaultCodec = ScreenStreamProtocol.CODEC_H264,
        )

        assertTrue(header.config)
        assertFalse(header.isDroppable)
    }

    @Test
    fun fallsBackToTheNegotiatedCodecWhenTheHeaderOmitsIt() {
        val v1 = ScreenStreamProtocol.parseHeader(
            """{"seq":7,"timestamp_ms":5,"width":800,"height":600}""",
            defaultCodec = StreamProtocolVersion.V1.defaultCodec,
        )

        assertEquals("jpeg", v1.codec)
        assertTrue(v1.isJpeg)
        assertFalse(v1.keyFrame)
        assertFalse(v1.config)
    }

    @Test
    fun ignoresUnknownAndNestedFields() {
        val header = ScreenStreamProtocol.parseHeader(
            """{"seq":1,"extra":{"a":[1,2,{"b":"}"}],"c":"x"},"codec":"H264","width":10,"height":20,
               "flags":null,"key_frame":false,"config":false,"quality":0.75}""",
            defaultCodec = ScreenStreamProtocol.CODEC_JPEG,
        )

        assertEquals(1L, header.seq)
        assertEquals(10, header.width)
        assertEquals(20, header.height)
        assertEquals("h264", header.codec)
    }

    @Test
    fun parsesEscapedStrings() {
        val header = ScreenStreamProtocol.parseHeader(
            """{"seq":1,"codec":"h2\/6\"4","width":2,"height":2}""",
            defaultCodec = ScreenStreamProtocol.CODEC_JPEG,
        )

        assertEquals("h2/6\"4", header.codec)
    }

    @Test(expected = JsonFormatException::class)
    fun rejectsTruncatedJson() {
        ScreenStreamProtocol.parseHeader("""{"seq":1,"width":""")
    }

    @Test
    fun buildsTheJsonAttachLineForTheV2ControlPortTransport() {
        val line = ScreenStreamProtocol.attachLine(
            token = "abc\"123",
            transport = ScreenStreamProtocol.TRANSPORT_CONTROL_PORT_V2,
        )

        assertEquals("{\"type\":\"stream_connect\",\"token\":\"abc\\\"123\"}\n", line)
    }

    @Test
    fun buildsABareTokenLineForEveryOtherTransport() {
        assertEquals("tok\n", ScreenStreamProtocol.attachLine("tok", "waypad-stream-port"))
    }

    @Test
    fun mapsHandshakeMagicToProtocolVersion() {
        assertEquals(StreamProtocolVersion.V1, StreamProtocolVersion.fromMagic("WAYPAD_STREAM_V1"))
        assertEquals(StreamProtocolVersion.V2, StreamProtocolVersion.fromMagic("WAYPAD_STREAM_V2 "))
        assertNull(StreamProtocolVersion.fromMagic("WAYPAD_STREAM_V9"))
        assertEquals(ScreenStreamProtocol.CODEC_H264, StreamProtocolVersion.V2.defaultCodec)
        assertEquals(ScreenStreamProtocol.CODEC_JPEG, StreamProtocolVersion.V1.defaultCodec)
    }

    @Test
    fun rejectsOutOfRangeLengths() {
        assertThrowsTransport { ScreenStreamProtocol.requireValidHeaderLength(0) }
        assertThrowsTransport { ScreenStreamProtocol.requireValidHeaderLength(ScreenStreamProtocol.MAX_HEADER_BYTES + 1) }
        assertThrowsTransport { ScreenStreamProtocol.requireValidPayloadLength(-1) }
        assertThrowsTransport { ScreenStreamProtocol.requireValidPayloadLength(ScreenStreamProtocol.MAX_PAYLOAD_BYTES + 1) }
        ScreenStreamProtocol.requireValidHeaderLength(1)
        ScreenStreamProtocol.requireValidPayloadLength(ScreenStreamProtocol.MAX_PAYLOAD_BYTES)
    }

    private fun assertThrowsTransport(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected RemoteScreenTransportException")
        } catch (_: RemoteScreenTransportException) {
            // expected
        }
    }

    @Test
    fun `keeps the desktop size separate from the encoded size`() {
        val header = ScreenStreamProtocol.parseHeader(
            """{"seq":4,"width":1600,"height":900,"source_width":1920,"source_height":1080,"codec":"h264"}""",
        )
        assertEquals(1600, header.width)
        assertEquals(900, header.height)
        assertEquals(1920, header.sourceWidth)
        assertEquals(1080, header.sourceHeight)
    }

    @Test
    fun `falls back to the frame size when the daemon predates the geometry split`() {
        val header = ScreenStreamProtocol.parseHeader("""{"seq":1,"width":1280,"height":720}""")
        assertEquals(1280, header.sourceWidth)
        assertEquals(720, header.sourceHeight)
    }
}
