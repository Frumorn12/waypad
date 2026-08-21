package dev.waypad.android.core.video

import android.util.Log
import android.view.Surface
import dev.waypad.android.core.audio.EncodedAudioPacket
import dev.waypad.android.core.audio.RemoteAudioPlayer
import dev.waypad.android.core.network.StreamProtocolVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WaypadVideoRenderer"

/**
 * Routes the frames of a screen stream to the backend that can draw them, and owns the [Surface]
 * handed over by [WaypadVideoView].
 *
 * `WAYPAD_STREAM_V2` goes to the hardware [H264SurfaceDecoder], `WAYPAD_STREAM_V1` to the software
 * [JpegSurfaceRenderer]; both paint onto the same surface, so the view never has to know which
 * protocol the daemon picked. Only one backend holds the surface at a time.
 *
 * Desktop audio arrives interleaved on that same socket, distinguished only by the `codec` field of
 * its header, and is forwarded to [audio]. Routing per envelope rather than per socket is what lets
 * sound share the connection with no second port and no extra handshake.
 */
class RemoteVideoRenderer(
    private val listener: VideoRenderListener = VideoRenderListener.NoOp,
    /**
     * Desktop audio playback. Exposed so the UI can bind a mute switch and the lifecycle without
     * having to know how the stream is routed; see [RemoteAudioPlayer].
     */
    val audio: RemoteAudioPlayer = RemoteAudioPlayer(),
) : VideoFrameSink {

    private val lock = Any()

    private val backendListener = object : VideoRenderListener {
        override fun onVideoSizeChanged(size: VideoSize) {
            if (!size.isValid || _videoSize.value == size) return
            _videoSize.value = size
            onVideoSize?.invoke(size)
            listener.onVideoSizeChanged(size)
        }

        override fun onFrameRendered(presentationTimeUs: Long) = listener.onFrameRendered(presentationTimeUs)

        override fun onFramesDropped(count: Int) = listener.onFramesDropped(count)

        override fun onKeyFrameNeeded() = listener.onKeyFrameNeeded()

        override fun onRenderError(error: Throwable) = listener.onRenderError(error)
    }

    private val h264 = H264SurfaceDecoder(backendListener)
    private val jpeg = JpegSurfaceRenderer(backendListener)
    private val _videoSize = MutableStateFlow(VideoSize.Unknown)
    private val _sourceSize = MutableStateFlow(VideoSize.Unknown)

    /** Size of the picture currently being decoded, for aspect-ratio aware layout. */
    val videoSize: StateFlow<VideoSize> = _videoSize.asStateFlow()

    /**
     * Size of the remote desktop, which the stream may be downscaled from. This is what touch
     * coordinates have to be mapped against; [videoSize] describes the pixels on the wire.
     */
    val sourceSize: StateFlow<VideoSize> = _sourceSize.asStateFlow()

    /** Convenience hook for views that cannot collect a flow. */
    var onVideoSize: ((VideoSize) -> Unit)? = null

    private var surface: Surface? = null
    private var jpegActive = false

    @Volatile
    private var released = false

    /** Frames discarded anywhere in the pipeline since the process started. */
    val droppedFrames: Long
        get() = h264.droppedFrames + jpeg.droppedFrames

    /**
     * Called by [WaypadVideoView] on `surfaceCreated`/`surfaceDestroyed`. Passing `null` blocks
     * until the backends released the surface, which is mandatory before Android tears it down.
     */
    fun attachSurface(newSurface: Surface?) {
        if (released) return
        val useJpeg = synchronized(lock) {
            surface = newSurface
            jpegActive
        }
        handOverSurface(newSurface, useJpeg)
    }

    override fun onStreamStarted(version: StreamProtocolVersion) {
        if (released) return
        val useJpeg = version == StreamProtocolVersion.V1
        val current = synchronized(lock) {
            jpegActive = useJpeg
            surface
        }
        Log.i(TAG, "stream_started version=${version.magic} codec=${version.defaultCodec}")
        _videoSize.value = VideoSize.Unknown
        _sourceSize.value = VideoSize.Unknown
        if (useJpeg) jpeg.reset() else h264.reset()
        handOverSurface(current, useJpeg)
        audio.onStreamStarted()
    }

    override fun onFrame(frame: EncodedVideoFrame) {
        if (released) return
        // Route on the envelope itself: a daemon may still emit JPEG on a v2 socket, and audio
        // packets are interleaved with the video ones on the very same connection.
        if (frame.header.isOpus) {
            audio.submit(EncodedAudioPacket(frame.header, frame.payload))
            return
        }
        val source = VideoSize(frame.header.sourceWidth, frame.header.sourceHeight)
        if (source.isValid && _sourceSize.value != source) {
            _sourceSize.value = source
        }
        when {
            frame.header.isH264 -> h264.submit(frame)
            frame.header.isJpeg -> jpeg.submit(frame)
            else -> Log.w(TAG, "frame_unknown_codec codec=${frame.header.codec}")
        }
    }

    override fun onStreamEnded(error: Throwable?) {
        if (released) return
        Log.i(TAG, "stream_ended error=${error?.message}")
        h264.reset()
        jpeg.reset()
        audio.onStreamEnded()
    }

    fun release() {
        if (released) return
        released = true
        synchronized(lock) { surface = null }
        onVideoSize = null
        h264.release()
        jpeg.release()
        audio.release()
    }

    private fun handOverSurface(target: Surface?, useJpeg: Boolean) {
        if (useJpeg) {
            h264.setSurface(null)
            jpeg.setSurface(target)
        } else {
            jpeg.setSurface(null)
            h264.setSurface(target)
        }
    }
}
