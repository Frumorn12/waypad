package dev.waypad.android.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "WaypadAudioPlayer"

/**
 * Plays the desktop audio that rides along on the screen stream socket.
 *
 * Opus packets are decoded by a hardware/framework `MediaCodec` in asynchronous mode and written to
 * an [AudioTrack] in low-latency streaming mode. Every codec and track operation — creation,
 * feeding, the `MediaCodec.Callback`, teardown — runs on this player's own [HandlerThread], the same
 * discipline [dev.waypad.android.core.video.H264SurfaceDecoder] uses: serialising on one thread is
 * what keeps `stop()` from deadlocking against a callback.
 *
 * Latency, not throughput, is the thing being defended. Audio that arrives faster than it plays is
 * dropped by [AudioDropPolicy] rather than queued, because a speaker cannot catch up and every
 * buffered packet would be permanent drift away from the picture.
 *
 * Wiring it to a UI needs three things and nothing else:
 * - [attachAudioFocus] with any `Context`, so a phone call silences the stream;
 * - [setMuted] for a mute switch, with [onMuteChanged] to forward the same state to the daemon;
 * - [pausePlayback]/[resumePlayback] on the foreground/background transitions.
 */
class RemoteAudioPlayer(
    private val listener: AudioPlaybackListener = AudioPlaybackListener.NoOp,
) {
    private val queue = EncodedAudioQueue()
    private val lock = Any()

    private val _stats = MutableStateFlow(AudioPlaybackStats())

    /** Rolling playback statistics; safe to collect from the UI. */
    val stats: StateFlow<AudioPlaybackStats> = _stats.asStateFlow()

    private val _muted = MutableStateFlow(false)

    /** Mute switch state, for a UI toggle to bind to. */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /**
     * Raised whenever [setMuted] or [toggleMute] changes the state, on the caller's thread.
     *
     * Local muting only silences the speaker; forward the value to the daemon with
     * `set_desktop_audio_mute` so the packets stop being sent at all and the mute costs no bandwidth.
     */
    var onMuteChanged: ((muted: Boolean) -> Unit)? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var focus: AudioFocusController? = null

    @Volatile
    private var released = false

    @Volatile
    private var paused = false

    @Volatile
    private var focusState = AudioFocusState.Granted

    // Handler-thread confined state.
    private val inputBuffers = ArrayDeque<Int>()
    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var format = AudioFormatSpec()
    private var configuredFormat: AudioFormatSpec? = null
    private var generation = 0
    private var framesSubmitted = 0L
    private var framesWritten = 0L
    private var trackFramesAtFlush = 0L
    private var errorBurst = 0
    private var errorBurstStartedAt = 0L
    private var codecFatal = false
    private var packetsReceived = 0L
    private var lastStatsPublishedAt = 0L
    private var bytesSinceReport = 0L
    private var packetsSinceReport = 0L
    private var lastReportAt = 0L
    private val drift = AudioArrivalDrift()

    /**
     * Installs audio-focus handling. Optional — playback works without it — but until it is called
     * the desktop stream will happily play over a phone call, so the UI should always attach it.
     * Idempotent; the application context is used, so any `Context` is safe to pass.
     */
    fun attachAudioFocus(context: Context) {
        if (released) return
        synchronized(lock) {
            if (focus != null) return
            focus = AudioFocusController(context) { state -> onFocusStateChanged(state) }
        }
        Log.i(TAG, "audio_focus_attached")
    }

    /** Called by the renderer when a stream socket opens. */
    fun onStreamStarted() {
        if (released) return
        paused = false
        queue.clear()
        packetsReceived = 0
        drift.reset()
        ensureThread().post {
            teardown("stream_started")
            codecFatal = false
            errorBurst = 0
        }
        if (focus?.request() == false) {
            Log.w(TAG, "audio_focus_denied playing_anyway=false")
        }
        publishStats()
    }

    /** Hands a packet over without blocking; the socket reader must never wait on the decoder. */
    fun submit(packet: EncodedAudioPacket) {
        if (released) return
        packetsReceived++
        bytesSinceReport += packet.sizeBytes
        packetsSinceReport++
        drift.onPacket(packet.header.timestampMs, SystemClock.elapsedRealtime())
        if (!isAudible()) {
            // Muted, paused or focus-less: nothing may be buffered, or unmuting would replay stale
            // audio that is by then seconds behind the picture.
            queue.clear()
            return
        }
        queue.offer(packet)
        ensureThread().post(drainRunnable)
    }

    fun onStreamEnded() {
        if (released) return
        queue.clear()
        handler?.post { teardown("stream_ended") }
        focus?.abandon()
        _stats.value = _stats.value.copy(active = false, bufferedMs = 0)
    }

    /** Mutes locally and reports the new state through [onMuteChanged]. */
    fun setMuted(value: Boolean) {
        if (released || _muted.value == value) return
        _muted.value = value
        Log.i(TAG, "mute_changed muted=$value")
        if (value) {
            queue.clear()
            handler?.post { flushOutput("muted") }
        }
        publishStats()
        onMuteChanged?.invoke(value)
    }

    fun toggleMute(): Boolean {
        val next = !_muted.value
        setMuted(next)
        return next
    }

    /** Call from `ON_STOP`: releases the codec and the track so the system can reclaim them. */
    fun pausePlayback() {
        if (released || paused) return
        paused = true
        queue.clear()
        handler?.post { teardown("paused") }
        focus?.abandon()
        Log.i(TAG, "playback_paused")
        publishStats()
    }

    /** Call from `ON_START`: the codec is rebuilt lazily from the next packet's header. */
    fun resumePlayback() {
        if (released || !paused) return
        paused = false
        focus?.request()
        Log.i(TAG, "playback_resumed")
        publishStats()
    }

    /** Releases the codec, the track and the worker thread. The player is unusable afterwards. */
    fun release() {
        if (released) return
        released = true
        queue.clear()
        onMuteChanged = null
        focus?.abandon()
        val worker = synchronized(lock) { thread }
        if (worker != null) {
            runBlockingOnPlayer("release") { teardown("release") }
            worker.quitSafely()
            runCatching { worker.join(RELEASE_JOIN_TIMEOUT_MS) }
        }
        synchronized(lock) {
            thread = null
            handler = null
            focus = null
        }
    }

    // --- handler thread ----------------------------------------------------------------------

    private val drainRunnable = Runnable { drain() }

    private fun drain() {
        if (released) return
        if (!isAudible()) {
            queue.clear()
            return
        }
        trimBacklog()
        while (true) {
            val head = queue.peek() ?: break
            if (configuredFormat == null || configuredFormat != head.format) {
                if (!ensureCodec(head.format)) {
                    queue.poll()
                    countDrop(1)
                    continue
                }
            }
            if (inputBuffers.isEmpty()) break
            val packet = queue.poll() ?: break
            feed(packet)
        }
        publishStats()
    }

    /**
     * Cuts the backlog when the speaker is further behind than [AudioDropPolicy] tolerates.
     *
     * The measurement is deliberately the *playout* buffer alone and not the encoded queue. The
     * socket reader delivers whatever the link buffered as one batch, so eight packets can land
     * together while the stream is perfectly on time; counting them as 160 ms of latency made the
     * guard drop audio that was about to be decoded within a millisecond, and the phone then ran
     * the track dry — dropping and underrunning at the same time. What is genuinely late is only
     * what has already been written to the track and not yet played. A pathological encoded
     * backlog is still bounded, by [EncodedAudioQueue]'s own capacity.
     */
    private fun trimBacklog() {
        val bufferedMs = playoutBufferedMs()
        val toDrop = AudioDropPolicy.packetsToDrop(bufferedMs, format.frameMs.toLong())
        if (toDrop <= 0) return
        // Not writing for `toDrop` packets lets the track drain back to the target on its own.
        val dropped = queue.dropOldest(toDrop)
        if (dropped > 0) {
            Log.d(TAG, "backlog_trimmed dropped=$dropped bufferedMs=$bufferedMs")
            listener.onPacketsDropped(dropped)
        }
    }

    private fun playoutBufferedMs(): Long {
        val active = track ?: return 0
        val played = (active.playbackHeadPosition.toLong() and 0xFFFF_FFFFL) + trackFramesAtFlush
        return AudioDropPolicy.bufferedMs(framesWritten, played, format.sampleRate)
    }

    private fun ensureCodec(spec: AudioFormatSpec): Boolean {
        if (codec != null && configuredFormat == spec) return true
        if (codecFatal) return false
        teardown("format_change")
        format = spec

        var created: MediaCodec? = null
        return try {
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                spec.sampleRate,
                spec.channels,
            )
            // All three buffers are mandatory: MediaCodec rejects an Opus decoder that is missing
            // any of them, and the failure names none of them.
            spec.codecSpecificData().forEachIndexed { index, csd ->
                mediaFormat.setByteBuffer("csd-$index", ByteBuffer.wrap(csd))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            created = decoder
            generation += 1
            decoder.setCallback(callbackFor(generation), handler)
            decoder.configure(mediaFormat, null, null, 0)
            decoder.start()
            codec = decoder
            configuredFormat = spec
            inputBuffers.clear()
            framesSubmitted = 0
            Log.i(TAG, "codec_started ${spec.sampleRate}Hz x${spec.channels} frameMs=${spec.frameMs} preSkip=${spec.preSkipSamples}")
            listener.onPlaybackStarted(spec)
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "codec_start_failed", throwable)
            created?.let { runCatching { it.release() } }
            generation += 1
            configuredFormat = null
            handleFailure(throwable)
            false
        }
    }

    private fun feed(packet: EncodedAudioPacket) {
        val active = codec ?: return
        val index = inputBuffers.removeFirstOrNull() ?: return
        val buffer = runCatching { active.getInputBuffer(index) }.getOrNull()
        if (buffer == null || buffer.capacity() < packet.sizeBytes) {
            countDrop(1)
            return
        }
        // Opus packets are self-contained, so a synthetic timeline is enough and immune to the
        // daemon clock jumping.
        val ptsUs = framesSubmitted * 1_000_000L / format.sampleRate
        try {
            buffer.clear()
            buffer.put(packet.payload)
            active.queueInputBuffer(index, 0, packet.sizeBytes, ptsUs, 0)
            framesSubmitted += format.framesPerPacket
        } catch (throwable: Throwable) {
            Log.w(TAG, "queue_input_failed seq=${packet.header.seq}", throwable)
            handleFailure(throwable)
        }
    }

    private fun callbackFor(codecGeneration: Int) = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (released || codecGeneration != generation) return
            inputBuffers.addLast(index)
            drain()
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (released || codecGeneration != generation) return
            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!isConfig && info.size > 0 && isAudible()) {
                runCatching { codec.getOutputBuffer(index) }.getOrNull()?.let { buffer ->
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    writeToTrack(buffer, info.size)
                }
            }
            runCatching { codec.releaseOutputBuffer(index, false) }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (released || codecGeneration != generation) return
            val rate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                .getOrDefault(this@RemoteAudioPlayer.format.sampleRate)
            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                .getOrDefault(this@RemoteAudioPlayer.format.channels)
            Log.i(TAG, "output_format ${rate}Hz x$channels")
            ensureTrack(rate, channels)
        }

        override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
            if (released || codecGeneration != generation) return
            Log.e(TAG, "codec_error recoverable=${error.isRecoverable} transient=${error.isTransient}", error)
            handler?.post { handleFailure(error) }
        }
    }

    private fun writeToTrack(buffer: ByteBuffer, size: Int) {
        val active = ensureTrack(format.sampleRate, format.channels) ?: return
        val written = try {
            active.write(buffer, size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (throwable: IllegalStateException) {
            Log.w(TAG, "track_write_failed", throwable)
            return
        }
        if (written > 0) {
            framesWritten += written / bytesPerFrame()
        }
        if (written in 0 until size) {
            // The track is full: the rest of this packet is dropped rather than blocking the codec
            // callback, which is exactly the backpressure the latency guard is there to avoid.
            countDrop(1)
        }
    }

    private fun ensureTrack(sampleRate: Int, channels: Int): AudioTrack? {
        track?.let { existing ->
            if (existing.sampleRate == sampleRate && existing.channelCount == channels) return existing
            releaseTrack()
        }
        val channelMask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            Log.e(TAG, "track_unsupported rate=$sampleRate channels=$channels")
            return null
        }
        val wanted = sampleRate * channels * 2 * TARGET_TRACK_BUFFER_MS / 1000
        return try {
            val created = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, wanted))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            created.setVolume(volumeForFocus())
            created.play()
            track = created
            framesWritten = 0
            trackFramesAtFlush = 0
            format = format.copy(sampleRate = sampleRate, channels = channels)
            Log.i(TAG, "track_started ${sampleRate}Hz x$channels buffer=${maxOf(minBuffer, wanted)}B")
            _stats.value = _stats.value.copy(active = true, sampleRate = sampleRate, channels = channels)
            created
        } catch (throwable: Throwable) {
            Log.e(TAG, "track_start_failed", throwable)
            handleFailure(throwable)
            null
        }
    }

    private fun flushOutput(reason: String) {
        val active = track ?: return
        // playbackHeadPosition restarts from zero after a flush, so the played counter carries the
        // pre-flush total or the latency guard would see a huge negative backlog.
        trackFramesAtFlush += active.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
        runCatching {
            active.pause()
            active.flush()
            active.play()
        }
        framesWritten = trackFramesAtFlush
        Log.d(TAG, "track_flushed reason=$reason")
    }

    private fun releaseTrack() {
        val active = track ?: return
        track = null
        runCatching { active.pause() }
        runCatching { active.flush() }
        runCatching { active.release() }
        framesWritten = 0
        trackFramesAtFlush = 0
    }

    private fun teardown(reason: String) {
        releaseTrack()
        val current = codec ?: return
        generation += 1
        codec = null
        configuredFormat = null
        inputBuffers.clear()
        Log.i(TAG, "codec_teardown reason=$reason")
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun handleFailure(error: Throwable) {
        if (released) return
        val now = SystemClock.elapsedRealtime()
        if (now - errorBurstStartedAt > ERROR_BURST_WINDOW_MS) {
            errorBurstStartedAt = now
            errorBurst = 0
        }
        errorBurst += 1
        teardown("codec_error")
        queue.clear()
        if (errorBurst > MAX_ERRORS_PER_WINDOW) {
            Log.e(TAG, "codec_error_burst count=$errorBurst giving_up")
            // Audio is optional: give up quietly on the sound rather than fight a codec that keeps
            // failing, and leave the video stream completely untouched.
            codecFatal = true
            _stats.value = _stats.value.copy(active = false, error = error.message ?: error::class.java.simpleName)
            listener.onPlaybackError(error)
        }
    }

    private fun onFocusStateChanged(state: AudioFocusState) {
        focusState = state
        when (state) {
            AudioFocusState.Granted -> track?.let { runCatching { it.setVolume(volumeForFocus()) } }
            AudioFocusState.Ducked -> track?.let { runCatching { it.setVolume(volumeForFocus()) } }
            AudioFocusState.Lost -> {
                queue.clear()
                handler?.post { flushOutput("focus_lost") }
            }
        }
        listener.onFocusChanged(state != AudioFocusState.Lost)
        publishStats()
    }

    private fun volumeForFocus(): Float = when (focusState) {
        AudioFocusState.Granted -> 1.0f
        AudioFocusState.Ducked -> DUCK_VOLUME
        AudioFocusState.Lost -> 0.0f
    }

    private fun isAudible(): Boolean =
        !released && !paused && !_muted.value && focusState != AudioFocusState.Lost

    private fun countDrop(count: Int) {
        if (count <= 0) return
        listener.onPacketsDropped(count)
    }

    private fun publishStats() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStatsPublishedAt < STATS_INTERVAL_MS) return
        lastStatsPublishedAt = now
        val bufferedMs = playoutBufferedMs()
        _stats.value = _stats.value.copy(
            muted = _muted.value,
            hasFocus = focusState != AudioFocusState.Lost,
            packetsReceived = packetsReceived,
            packetsDropped = queue.droppedPackets,
            bufferedMs = bufferedMs,
        )
        // Periodic and cheap: bufferedMs is the only place the audio delay is actually visible,
        // and packetAgeMs next to it separates a slow link from a long playout buffer.
        val sinceReport = now - lastReportAt
        if (sinceReport >= REPORT_INTERVAL_MS) {
            lastReportAt = now
            Log.d(
                TAG,
                "playback packets=$packetsSinceReport kbps=${bytesSinceReport * 8 / sinceReport} " +
                    "bufferedMs=$bufferedMs driftMs=${drift.driftMs()} dropped=${queue.droppedPackets} " +
                    "underruns=${track?.underrunCount ?: 0}",
            )
            bytesSinceReport = 0
            packetsSinceReport = 0
        }
    }

    private fun ensureThread(): Handler = synchronized(lock) {
        handler?.let { return it }
        val worker = HandlerThread("waypad-opus").apply { start() }
        val created = Handler(worker.looper)
        thread = worker
        handler = created
        created
    }

    private fun runBlockingOnPlayer(label: String, block: () -> Unit) {
        val target = handler ?: return
        if (Thread.currentThread() === thread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        if (!target.post { try { block() } finally { latch.countDown() } }) return
        if (!latch.await(BLOCKING_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "player_op_timeout op=$label")
        }
    }

    private fun bytesPerFrame(): Int = 2 * format.channels.coerceAtLeast(1)

    private companion object {
        /**
         * Short enough to stay near the picture, long enough to survive Wi-Fi jitter. An 80 ms
         * track ran dry repeatedly on the test link, and `PERFORMANCE_MODE_LOW_LATENCY` shrinks
         * the HAL buffer behind it further, so the headroom has to come from here.
         */
        const val TARGET_TRACK_BUFFER_MS = 120
        const val DUCK_VOLUME = 0.2f
        const val ERROR_BURST_WINDOW_MS = 5_000L
        const val MAX_ERRORS_PER_WINDOW = 3
        const val BLOCKING_OP_TIMEOUT_MS = 1_500L
        const val RELEASE_JOIN_TIMEOUT_MS = 1_000L
        const val STATS_INTERVAL_MS = 500L
        const val REPORT_INTERVAL_MS = 5_000L
    }
}
