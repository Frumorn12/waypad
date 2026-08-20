package dev.waypad.android.core.network

import java.io.InputStream
import java.net.SocketTimeoutException

/** A framed message read off the stream socket, still undecoded. */
class StreamEnvelope(val headerBytes: ByteArray, val payload: ByteArray)

/**
 * Reads the `[u32 headerLen][u32 payloadLen][header][payload]` framing off an [InputStream].
 *
 * Deliberately free of Android and coroutine types so the framing can be exercised by JVM unit
 * tests. Blocking reads are expected to be interrupted by the socket `soTimeout`; [onIdle] is
 * invoked on every such wakeup so the caller can honour coroutine cancellation, and the read only
 * fails once no byte at all made progress for `timeoutMs`.
 */
class StreamEnvelopeReader(
    private val input: InputStream,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    /** Bytes already buffered locally; used to detect that the sender is ahead of the renderer. */
    fun bufferedBytes(): Int = runCatching { input.available() }.getOrDefault(0)

    fun readHandshakeLine(): String {
        val bytes = ArrayList<Byte>(32)
        while (true) {
            val value = input.read()
            if (value < 0) throw RemoteScreenTransportException("Stream closed before handshake line")
            if (value == '\n'.code) break
            bytes.add(value.toByte())
            if (bytes.size >= ScreenStreamProtocol.MAX_HANDSHAKE_BYTES) {
                throw RemoteScreenTransportException("Stream handshake line is too long")
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
    }

    fun readEnvelope(timeoutMs: Long, onIdle: () -> Unit = {}): StreamEnvelope {
        val headerLength = readInt(timeoutMs, onIdle, "frame header length")
        val payloadLength = readInt(timeoutMs, onIdle, "frame payload length")
        ScreenStreamProtocol.requireValidHeaderLength(headerLength)
        ScreenStreamProtocol.requireValidPayloadLength(payloadLength)

        val headerBytes = ByteArray(headerLength)
        readFully(headerBytes, timeoutMs, onIdle, "frame header")
        val payload = ByteArray(payloadLength)
        readFully(payload, timeoutMs, onIdle, "frame payload")
        return StreamEnvelope(headerBytes, payload)
    }

    private fun readInt(timeoutMs: Long, onIdle: () -> Unit, label: String): Int {
        val buffer = ByteArray(4)
        readFully(buffer, timeoutMs, onIdle, label)
        return ((buffer[0].toInt() and 0xFF) shl 24) or
            ((buffer[1].toInt() and 0xFF) shl 16) or
            ((buffer[2].toInt() and 0xFF) shl 8) or
            (buffer[3].toInt() and 0xFF)
    }

    private fun readFully(buffer: ByteArray, timeoutMs: Long, onIdle: () -> Unit, label: String) {
        var offset = 0
        var lastProgress = nowMs()
        while (offset < buffer.size) {
            onIdle()
            try {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) throw RemoteScreenTransportException("Screen stream closed while reading $label")
                offset += read
                lastProgress = nowMs()
            } catch (_: SocketTimeoutException) {
                if (nowMs() - lastProgress > timeoutMs) {
                    throw RemoteScreenTransportException("Screen stream heartbeat timeout while reading $label")
                }
            }
        }
    }
}
