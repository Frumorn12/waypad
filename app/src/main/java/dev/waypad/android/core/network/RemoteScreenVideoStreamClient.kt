package dev.waypad.android.core.network

import android.util.Log
import dev.waypad.android.core.video.EncodedVideoFrame
import dev.waypad.android.core.video.FrameDropPolicy
import dev.waypad.android.core.video.VideoFrameSink
import dev.waypad.android.core.video.VideoStreamMetrics
import dev.waypad.android.core.video.VideoStreamStatsTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "WaypadVideoStream"

/** Everything needed to attach to one screen stream socket. */
data class ScreenStreamEndpoint(
    val host: String,
    val port: Int,
    val token: String,
    val transport: String,
)

/**
 * Reads the Waypad screen stream socket and hands the encoded frames to a [VideoFrameSink].
 *
 * Differences that matter compared to the old JPEG client:
 * - nothing is decoded here, so the socket is never blocked by rendering;
 * - the sink is a plain callback, not a `suspend` one, so it cannot apply backpressure;
 * - whatever the sender already pushed into the receive buffer is drained in one batch and pruned
 *   with [FrameDropPolicy], so a congested link costs freshness instead of latency.
 */
class RemoteScreenVideoStreamClient(
    val stats: VideoStreamStatsTracker = VideoStreamStatsTracker(),
) {
    /** Invoked after every batch with a fresh snapshot; runs on the IO dispatcher. */
    var onMetrics: ((VideoStreamMetrics) -> Unit)? = null

    /**
     * Streams until the socket closes, the coroutine is cancelled, or the transport fails.
     *
     * @throws RemoteScreenTransportException on any transport level failure.
     */
    suspend fun collect(
        host: String,
        port: Int,
        token: String,
        transport: String,
        sink: VideoFrameSink,
    ) = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        val onIdle: () -> Unit = { job?.ensureActive() }

        Log.i(TAG, "stream_connect_start host=$host port=$port transport=$transport")
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.receiveBufferSize = RECEIVE_BUFFER_BYTES
            socket.sendBufferSize = SEND_BUFFER_BYTES
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = SOCKET_POLL_MS

            var failure: Throwable? = null
            var started = false
            try {
                val attach = ScreenStreamProtocol.attachLine(token, transport)
                socket.getOutputStream().apply {
                    write(attach.toByteArray(Charsets.UTF_8))
                    flush()
                }

                val reader = StreamEnvelopeReader(socket.getInputStream())
                val magic = reader.readHandshakeLine()
                val version = StreamProtocolVersion.fromMagic(magic)
                    ?: throw RemoteScreenTransportException("Unexpected screen stream header: $magic")
                Log.i(TAG, "stream_connect_success host=$host port=$port protocol=${version.magic}")
                started = true
                sink.onStreamStarted(version)
                readFrames(reader, version, sink, onIdle)
                Log.i(TAG, "stream_close reason=eof")
            } catch (throwable: Throwable) {
                failure = throwable
                if (throwable is CancellationException) {
                    Log.i(TAG, "stream_close reason=cancelled")
                } else {
                    Log.w(TAG, "stream_close reason=exception message=${throwable.message}", throwable)
                }
            } finally {
                if (started) sink.onStreamEnded(failure)
            }

            when (val error = failure) {
                null -> Unit
                is CancellationException -> throw error
                is RemoteScreenTransportException -> throw error
                else -> throw RemoteScreenTransportException(
                    error.message ?: error::class.java.simpleName,
                    error,
                )
            }
        }
    }

    private fun readFrames(
        reader: StreamEnvelopeReader,
        version: StreamProtocolVersion,
        sink: VideoFrameSink,
        onIdle: () -> Unit,
    ) {
        val batch = ArrayList<EncodedVideoFrame>(MAX_BATCH_FRAMES)
        var firstFrame = true
        while (true) {
            onIdle()
            batch.clear()
            val timeoutMs = if (firstFrame) FIRST_FRAME_TIMEOUT_MS else FRAME_PROGRESS_TIMEOUT_MS
            var batchBytes = 0
            batch += readFrame(reader, version, timeoutMs, onIdle).also { batchBytes += it.sizeBytes }
            firstFrame = false

            // Anything already sitting in the receive buffer is late by definition: pull it now so
            // the drop policy can choose what is still worth decoding.
            while (batch.size < MAX_BATCH_FRAMES &&
                batchBytes < MAX_BATCH_BYTES &&
                reader.bufferedBytes() >= MIN_ENVELOPE_BYTES
            ) {
                onIdle()
                val extra = readFrame(reader, version, FRAME_PROGRESS_TIMEOUT_MS, onIdle)
                batch += extra
                batchBytes += extra.sizeBytes
            }

            val nowMs = monotonicMs()
            val wallClockMs = System.currentTimeMillis()
            for (frame in batch) {
                stats.onFrameReceived(
                    bytes = frame.sizeBytes,
                    ageMs = wallClockMs - frame.header.timestampMs,
                    nowMs = nowMs,
                )
            }

            val outcome = FrameDropPolicy.pruneToLatestKeyFrame(batch) { it.header }
            if (outcome.droppedCount > 0) {
                stats.onFramesDropped(outcome.droppedCount.toLong())
                Log.d(TAG, "batch_pruned read=${batch.size} kept=${outcome.kept.size} dropped=${outcome.droppedCount}")
            }
            for (frame in outcome.kept) {
                Log.v(TAG, "frame seq=${frame.header.seq} bytes=${frame.sizeBytes} key=${frame.header.keyFrame} config=${frame.header.config}")
                sink.onFrame(frame)
            }
            onMetrics?.invoke(stats.snapshot(nowMs))
        }
    }

    private fun readFrame(
        reader: StreamEnvelopeReader,
        version: StreamProtocolVersion,
        timeoutMs: Long,
        onIdle: () -> Unit,
    ): EncodedVideoFrame {
        val envelope = reader.readEnvelope(timeoutMs, onIdle)
        val header = try {
            ScreenStreamProtocol.parseHeader(envelope.headerBytes, version.defaultCodec)
        } catch (error: JsonFormatException) {
            throw RemoteScreenTransportException("Malformed screen frame header: ${error.message}", error)
        }
        return EncodedVideoFrame(header, envelope.payload)
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val SOCKET_POLL_MS = 1_000
        const val RECEIVE_BUFFER_BYTES = 512 * 1024
        const val SEND_BUFFER_BYTES = 64 * 1024
        const val FIRST_FRAME_TIMEOUT_MS = 130_000L
        const val FRAME_PROGRESS_TIMEOUT_MS = 12_000L

        /** Two u32 length prefixes: the smallest hint that another envelope already landed. */
        const val MIN_ENVELOPE_BYTES = 8
        const val MAX_BATCH_FRAMES = 16
        const val MAX_BATCH_BYTES = 8 * 1024 * 1024
    }
}
