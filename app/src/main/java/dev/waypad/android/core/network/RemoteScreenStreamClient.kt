package dev.waypad.android.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

private const val TAG = "WaypadScreenStream"
private const val CONNECT_TIMEOUT_MS = 5_000
private const val FIRST_FRAME_TIMEOUT_MS = 130_000L
private const val FRAME_PROGRESS_TIMEOUT_MS = 12_000L

private val JPEG_DECODE_DISPATCHER = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

data class RemoteScreenFrame(
    val seq: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val byteCount: Int,
    val bitmap: Bitmap,
)

class RemoteScreenTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RemoteScreenStreamClient {
    suspend fun collect(
        host: String,
        port: Int,
        token: String,
        transport: String,
        onFrame: suspend (RemoteScreenFrame) -> Unit,
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "stream_connect_start host=$host port=$port transport=$transport")
        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var lastSeenSeq = -1L
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.receiveBufferSize = 256 * 1024
            socket.sendBufferSize = 64 * 1024
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 1_000
            try {
                val attach = if (transport == "waypad-control-port-stream-v2") {
                    JSONObject()
                        .put("type", "stream_connect")
                        .put("token", token)
                        .toString()
                } else {
                    token
                }
                socket.getOutputStream().write("$attach\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()

                val input = DataInputStream(socket.getInputStream())
                val magic = input.readLineUtf8()
                if (magic != "WAYPAD_STREAM_V1") {
                    throw RemoteScreenTransportException("Unexpected screen stream header: $magic")
                }
                Log.i(TAG, "stream_connect_success host=$host port=$port")

                var firstFrame = true
                var staleSkipCount = 0L
                while (currentCoroutineContext().isActive) {
                    val timeoutMs = if (firstFrame) FIRST_FRAME_TIMEOUT_MS else FRAME_PROGRESS_TIMEOUT_MS
                    val headerLength = input.readIntCancellable(timeoutMs, "frame header length")
                    val payloadLength = input.readIntCancellable(timeoutMs, "frame payload length")
                    if (headerLength !in 1..8192) {
                        throw RemoteScreenTransportException("Invalid screen frame header length: $headerLength")
                    }
                    if (payloadLength !in 1..20_971_520) {
                        throw RemoteScreenTransportException("Invalid screen frame payload length: $payloadLength")
                    }

                    val headerBytes = ByteArray(headerLength)
                    input.readFullyCancellable(headerBytes, timeoutMs, "frame header")
                    val payload = ByteArray(payloadLength)
                    input.readFullyCancellable(payload, timeoutMs, "frame payload")

                    val header = JSONObject(String(headerBytes, Charsets.UTF_8))
                    val seq = header.optLong("seq")

                    if (seq < lastSeenSeq) {
                        staleSkipCount++
                        Log.v(TAG, "frame_skip_stale seq=$seq last=$lastSeenSeq total_skipped=$staleSkipCount")
                        continue
                    }
                    lastSeenSeq = seq

                    val deferred = CompletableDeferred<Bitmap>()
                    withContext(JPEG_DECODE_DISPATCHER) {
                        val bitmap = BitmapFactory.decodeByteArray(payload, 0, payload.size, decodeOpts)
                            ?: throw RemoteScreenTransportException("Could not decode JPEG frame")
                        deferred.complete(bitmap)
                    }
                    val bitmap = deferred.await()
                    val frame = RemoteScreenFrame(
                        seq = seq,
                        timestampMs = header.optLong("timestamp_ms"),
                        width = header.optInt("width", bitmap.width),
                        height = header.optInt("height", bitmap.height),
                        byteCount = payload.size,
                        bitmap = bitmap,
                    )
                    firstFrame = false
                    Log.v(TAG, "frame seq=${frame.seq} ${frame.width}x${frame.height} bytes=${frame.byteCount}")
                    onFrame(frame)
                }
                Log.i(TAG, "stream_close reason=coroutine_inactive")
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    Log.i(TAG, "stream_close reason=cancelled")
                    throw throwable
                }
                Log.w(TAG, "stream_close reason=exception message=${throwable.message}", throwable)
                throw RemoteScreenTransportException(
                    throwable.message ?: throwable::class.java.simpleName,
                    throwable,
                )
            }
        }
    }

    private fun DataInputStream.readLineUtf8(): String {
        val bytes = ArrayList<Byte>(32)
        while (true) {
            val value = read()
            if (value < 0) throw RemoteScreenTransportException("Stream closed before header")
            if (value == '\n'.code) break
            bytes.add(value.toByte())
            if (bytes.size >= 128) throw RemoteScreenTransportException("Stream header is too long")
        }
        return bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
    }

    private suspend fun DataInputStream.readIntCancellable(timeoutMs: Long, label: String): Int {
        val buffer = ByteArray(4)
        readFullyCancellable(buffer, timeoutMs, label)
        return ByteBuffer.wrap(buffer).int
    }

    private suspend fun DataInputStream.readFullyCancellable(
        buffer: ByteArray,
        timeoutMs: Long,
        label: String,
    ) {
        var offset = 0
        var lastProgress = SystemClock.elapsedRealtime()
        while (offset < buffer.size) {
            currentCoroutineContext().ensureActive()
            try {
                val read = read(buffer, offset, buffer.size - offset)
                if (read < 0) throw RemoteScreenTransportException("Screen stream closed while reading $label")
                offset += read
                lastProgress = SystemClock.elapsedRealtime()
            } catch (_: SocketTimeoutException) {
                if (SystemClock.elapsedRealtime() - lastProgress > timeoutMs) {
                    throw RemoteScreenTransportException("Screen stream heartbeat timeout while reading $label")
                }
            }
        }
    }
}
