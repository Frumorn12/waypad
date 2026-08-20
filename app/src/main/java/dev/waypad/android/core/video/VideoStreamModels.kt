package dev.waypad.android.core.video

import dev.waypad.android.core.network.StreamFrameHeader
import dev.waypad.android.core.network.StreamProtocolVersion

/** One envelope of the screen stream: decoded metadata plus the still-encoded payload. */
class EncodedVideoFrame(
    val header: StreamFrameHeader,
    val payload: ByteArray,
) {
    val sizeBytes: Int get() = payload.size

    override fun toString(): String =
        "EncodedVideoFrame(seq=${header.seq}, ${header.width}x${header.height}, " +
            "codec=${header.codec}, key=${header.keyFrame}, config=${header.config}, bytes=$sizeBytes)"
}

/** Size of the decoded video, as announced by the stream header. */
data class VideoSize(val width: Int, val height: Int) {
    val isValid: Boolean get() = width > 0 && height > 0

    companion object {
        val Unknown = VideoSize(0, 0)
    }
}

/**
 * Destination of the frames read off the socket.
 *
 * Every method must return promptly: the socket reader calls them inline and any blocking here
 * turns into backpressure on the network read, which is exactly what the old JPEG client got
 * wrong.
 */
interface VideoFrameSink {
    fun onStreamStarted(version: StreamProtocolVersion)

    fun onFrame(frame: EncodedVideoFrame)

    fun onStreamEnded(error: Throwable?)
}

/** Callbacks raised by the render backends; they fire on the backend's own worker thread. */
interface VideoRenderListener {
    /** Real decoded size, i.e. what the view has to letterbox against. */
    fun onVideoSizeChanged(size: VideoSize) {}

    fun onFrameRendered(presentationTimeUs: Long) {}

    fun onFramesDropped(count: Int) {}

    /**
     * The decoder cannot produce a picture until the next IDR: the surface was recreated, the
     * parameter sets changed, or a backlog cut broke the reference chain. Raised once per resync,
     * so the caller can ask the daemon for a key frame instead of waiting for the next periodic
     * one.
     */
    fun onKeyFrameNeeded() {}

    /** Fatal: the backend could not be brought back up. */
    fun onRenderError(error: Throwable) {}

    companion object {
        val NoOp: VideoRenderListener = object : VideoRenderListener {}
    }
}
