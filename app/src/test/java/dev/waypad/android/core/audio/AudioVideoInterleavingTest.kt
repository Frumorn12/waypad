package dev.waypad.android.core.audio

import dev.waypad.android.core.network.ScreenStreamProtocol
import dev.waypad.android.core.network.StreamFrameHeader
import dev.waypad.android.core.video.FrameDropPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audio and video share one socket, so the video backlog pruner sees audio envelopes too.
 *
 * [FrameDropPolicy] does not look at the codec: it keeps everything from the last `key_frame`
 * onwards plus the last `config` before it. That is why the daemon pins both flags to `false` on
 * audio — the tests below are what stops a future header change from silently letting sound evict
 * the H.264 parameter sets, which would leave the picture black.
 */
class AudioVideoInterleavingTest {

    @Test
    fun `interleaved audio never becomes a prune anchor`() {
        val batch = listOf(
            video(seq = 0, config = true),
            video(seq = 1, keyFrame = true),
            audio(seq = 0),
            video(seq = 2),
            audio(seq = 1),
        )

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(batch) { it }

        // The key frame is at index 1 and nothing after it may be cut, so the batch survives whole.
        assertEquals(0, outcome.droppedCount)
        assertEquals(batch, outcome.kept)
    }

    @Test
    fun `pruning a congested batch keeps the video config, not an audio envelope`() {
        val batch = listOf(
            video(seq = 0),
            audio(seq = 0),
            video(seq = 1, config = true),
            audio(seq = 1),
            video(seq = 2, keyFrame = true),
        )

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(batch) { it }

        assertTrue(outcome.continuityPreserved)
        // Exactly the SPS/PPS envelope plus the IDR: the audio packets in between are expendable,
        // the config is not.
        assertEquals(listOf(1L, 2L), outcome.kept.map { it.seq })
        assertTrue(outcome.kept.first().config)
        assertTrue(outcome.kept.none { it.isOpus })
    }

    private fun video(seq: Long, keyFrame: Boolean = false, config: Boolean = false) =
        StreamFrameHeader(
            seq = seq,
            timestampMs = seq * 16,
            width = 1920,
            height = 1080,
            sourceWidth = 1920,
            sourceHeight = 1080,
            codec = ScreenStreamProtocol.CODEC_H264,
            keyFrame = keyFrame,
            config = config,
        )

    private fun audio(seq: Long) = ScreenStreamProtocol.parseHeader(
        """{"seq":$seq,"timestamp_ms":${seq * 20},"codec":"opus","sample_rate":48000,
           "channels":2,"frame_ms":20,"pre_skip":312,"key_frame":false,"config":false}""",
    )
}
