package dev.waypad.android.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

private const val TAG = "WaypadAudioFocus"

/** What the player should do while another app owns the focus. */
enum class AudioFocusState {
    /** Free to play at full volume. */
    Granted,

    /** Somebody else is talking over us: keep decoding but at a low volume. */
    Ducked,

    /** A call or another exclusive player took over: stop feeding the speaker. */
    Lost,
}

/**
 * Wraps [AudioManager.requestAudioFocus] so a phone call, a navigation prompt or another media app
 * silences the desktop stream instead of playing on top of it.
 *
 * A transient loss pauses; a duck request drops the volume rather than pausing, because desktop
 * audio is usually background and a two second gap is more jarring than a quiet moment.
 */
class AudioFocusController(
    context: Context,
    private val onStateChanged: (AudioFocusState) -> Unit,
) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val state = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusState.Granted
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusState.Ducked
            else -> AudioFocusState.Lost
        }
        Log.i(TAG, "focus_change change=$change state=$state")
        onStateChanged(state)
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(listener)
        .build()

    private var held = false

    /** @return true when playback may start; a denied request is not an error, just silence. */
    @Synchronized
    fun request(): Boolean {
        if (held) return true
        val granted = runCatching { audioManager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        held = granted
        Log.i(TAG, "focus_request granted=$granted")
        return granted
    }

    @Synchronized
    fun abandon() {
        if (!held) return
        held = false
        runCatching { audioManager.abandonAudioFocusRequest(request) }
        Log.i(TAG, "focus_abandoned")
    }
}
