package dev.waypad.android.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException

class StreamEnvelopeReaderTest {

    @Test
    fun readsTheHandshakeLineWithoutConsumingTheFirstEnvelope() {
        val stream = ByteArrayOutputStream().apply {
            write("WAYPAD_STREAM_V2\r\n".toByteArray())
            writeEnvelope(HEADER, byteArrayOf(1, 2, 3))
        }
        val reader = StreamEnvelopeReader(ByteArrayInputStream(stream.toByteArray()))

        assertEquals("WAYPAD_STREAM_V2", reader.readHandshakeLine(1_000L))
        val envelope = reader.readEnvelope(1_000L)
        assertEquals(HEADER, String(envelope.headerBytes))
        assertArrayEquals(byteArrayOf(1, 2, 3), envelope.payload)
    }

    @Test
    fun readsConsecutiveEnvelopes() {
        val stream = ByteArrayOutputStream().apply {
            writeEnvelope("""{"seq":1}""", ByteArray(4) { 7 })
            writeEnvelope("""{"seq":2}""", ByteArray(9) { 8 })
        }
        val reader = StreamEnvelopeReader(ByteArrayInputStream(stream.toByteArray()))

        assertEquals("""{"seq":1}""", String(reader.readEnvelope(1_000L).headerBytes))
        val second = reader.readEnvelope(1_000L)
        assertEquals("""{"seq":2}""", String(second.headerBytes))
        assertEquals(9, second.payload.size)
    }

    @Test
    fun reassemblesAnEnvelopeSplitAcrossReads() {
        val bytes = ByteArrayOutputStream().apply {
            writeEnvelope(HEADER, ByteArray(64) { it.toByte() })
        }.toByteArray()
        val reader = StreamEnvelopeReader(ChunkedInputStream(bytes, chunkSize = 3))

        val envelope = reader.readEnvelope(1_000L)
        assertEquals(HEADER, String(envelope.headerBytes))
        assertEquals(64, envelope.payload.size)
    }

    @Test
    fun failsOnlyWhenNoByteMadeProgressForTheWholeTimeout() {
        val bytes = ByteArrayOutputStream().apply { writeEnvelope(HEADER, byteArrayOf(9)) }.toByteArray()
        var clock = 0L
        // Times out on every other read, but keeps making progress in between.
        val stream = TimingOutInputStream(bytes, timeoutEvery = 2) { clock += 100L }
        val reader = StreamEnvelopeReader(stream) { clock }

        val envelope = reader.readEnvelope(1_000L)
        assertEquals(HEADER, String(envelope.headerBytes))
    }

    @Test
    fun reportsAHeartbeatTimeoutWhenNothingArrives() {
        var clock = 0L
        val stalled = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                clock += 500L
                throw SocketTimeoutException()
            }
        }
        val reader = StreamEnvelopeReader(stalled) { clock }

        val error = runCatching { reader.readEnvelope(1_000L) }.exceptionOrNull()
        assertTrue(error is RemoteScreenTransportException)
        assertTrue(error!!.message!!.contains("heartbeat timeout"))
    }

    @Test
    fun rejectsAbsurdLengthPrefixes() {
        val bytes = ByteArrayOutputStream().apply {
            writeInt(ScreenStreamProtocol.MAX_HEADER_BYTES + 1)
            writeInt(16)
        }.toByteArray()
        val reader = StreamEnvelopeReader(ByteArrayInputStream(bytes))

        val error = runCatching { reader.readEnvelope(1_000L) }.exceptionOrNull()
        assertTrue(error is RemoteScreenTransportException)
    }

    @Test
    fun surfacesTheEndOfStream() {
        val reader = StreamEnvelopeReader(ByteArrayInputStream(ByteArray(2)))

        val error = runCatching { reader.readEnvelope(1_000L) }.exceptionOrNull()
        assertTrue(error is RemoteScreenTransportException)
        assertTrue(error!!.message!!.contains("closed"))
    }

    @Test
    fun invokesTheIdleHookSoTheCallerCanCancel() {
        val bytes = ByteArrayOutputStream().apply { writeEnvelope(HEADER, byteArrayOf(1)) }.toByteArray()
        val reader = StreamEnvelopeReader(ByteArrayInputStream(bytes))
        var idleCalls = 0

        reader.readEnvelope(1_000L) { idleCalls++ }

        assertTrue("idle hook must run at least once per read", idleCalls >= 4)
    }

    @Test
    fun exposesTheLocallyBufferedByteCount() {
        val bytes = ByteArrayOutputStream().apply { writeEnvelope(HEADER, byteArrayOf(1, 2)) }.toByteArray()
        val reader = StreamEnvelopeReader(ByteArrayInputStream(bytes))

        assertEquals(bytes.size, reader.bufferedBytes())
        reader.readEnvelope(1_000L)
        assertEquals(0, reader.bufferedBytes())
    }

    private class ChunkedInputStream(private val bytes: ByteArray, private val chunkSize: Int) : InputStream() {
        private var index = 0
        override fun read(): Int = if (index >= bytes.size) -1 else bytes[index++].toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (index >= bytes.size) return -1
            val count = minOf(chunkSize, len, bytes.size - index)
            System.arraycopy(bytes, index, b, off, count)
            index += count
            return count
        }
    }

    private class TimingOutInputStream(
        private val bytes: ByteArray,
        private val timeoutEvery: Int,
        private val onTimeout: () -> Unit,
    ) : InputStream() {
        private var index = 0
        private var calls = 0
        override fun read(): Int = if (index >= bytes.size) -1 else bytes[index++].toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            calls++
            if (calls % timeoutEvery == 0) {
                onTimeout()
                throw SocketTimeoutException()
            }
            if (index >= bytes.size) return -1
            val count = minOf(len, bytes.size - index)
            System.arraycopy(bytes, index, b, off, count)
            index += count
            return count
        }
    }

    private companion object {
        const val HEADER = """{"seq":0,"codec":"h264"}"""

        fun ByteArrayOutputStream.writeInt(value: Int) {
            write((value ushr 24) and 0xFF)
            write((value ushr 16) and 0xFF)
            write((value ushr 8) and 0xFF)
            write(value and 0xFF)
        }

        fun ByteArrayOutputStream.writeEnvelope(header: String, payload: ByteArray) {
            val headerBytes = header.toByteArray()
            writeInt(headerBytes.size)
            writeInt(payload.size)
            write(headerBytes)
            write(payload)
        }
    }
    @Test
    fun waitsThroughPollTimeoutsWhileTheDaemonBringsTheEncoderUp() {
        // The daemon only sends the handshake once it knows which codec it can deliver, and
        // portal approval plus pipeline startup sit in between. Poll timeouts here are normal.
        val payload = "WAYPAD_STREAM_V2\n".toByteArray()
        val stream = object : InputStream() {
            private var stalls = 5
            private var index = 0
            override fun read(): Int {
                if (stalls > 0) {
                    stalls--
                    throw SocketTimeoutException("Read timed out")
                }
                return if (index < payload.size) payload[index++].toInt() else -1
            }
        }
        var clock = 0L
        val reader = StreamEnvelopeReader(stream, nowMs = { clock += 100L; clock })

        assertEquals("WAYPAD_STREAM_V2", reader.readHandshakeLine(30_000L))
    }

    @Test
    fun givesUpOnTheHandshakeOnlyAfterTheDeadline() {
        val stream = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException("Read timed out")
        }
        var clock = 0L
        val reader = StreamEnvelopeReader(stream, nowMs = { clock += 1_000L; clock })

        val error = runCatching { reader.readHandshakeLine(5_000L) }.exceptionOrNull()
        assertTrue(error is RemoteScreenTransportException)
        assertTrue(error!!.message!!.contains("handshake"))
    }

}
