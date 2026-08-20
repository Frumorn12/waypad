package dev.waypad.android.core.video

import dev.waypad.android.core.network.ScreenStreamProtocol
import dev.waypad.android.core.network.StreamFrameHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds the header of a v2 frame; [seq] doubles as the identity in the assertions. */
internal fun frameHeader(
    seq: Long,
    keyFrame: Boolean = false,
    config: Boolean = false,
    width: Int = 1920,
    height: Int = 1080,
    codec: String = ScreenStreamProtocol.CODEC_H264,
    timestampMs: Long = seq * 33,
) = StreamFrameHeader(
    seq = seq,
    timestampMs = timestampMs,
    width = width,
    height = height,
    codec = codec,
    keyFrame = keyFrame,
    config = config,
)

internal fun encodedFrame(
    seq: Long,
    keyFrame: Boolean = false,
    config: Boolean = false,
    bytes: Int = 1_024,
    width: Int = 1920,
    height: Int = 1080,
) = EncodedVideoFrame(
    header = frameHeader(seq, keyFrame, config, width, height),
    payload = ByteArray(bytes) { seq.toByte() },
)

class FrameDropPolicyTest {

    @Test
    fun keepsEverythingWhenNoKeyFrameCanAnchorTheDrop() {
        val backlog = listOf(frameHeader(1), frameHeader(2), frameHeader(3))

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(backlog) { it }

        assertEquals(0, outcome.droppedCount)
        assertEquals(backlog, outcome.kept)
        assertTrue(outcome.continuityPreserved)
    }

    @Test
    fun dropsEverythingBeforeTheMostRecentKeyFrame() {
        val backlog = listOf(
            frameHeader(1),
            frameHeader(2, keyFrame = true),
            frameHeader(3),
            frameHeader(4, keyFrame = true),
            frameHeader(5),
        )

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(backlog) { it }

        assertEquals(listOf(4L, 5L), outcome.kept.map { it.seq })
        assertEquals(3, outcome.droppedCount)
        assertTrue(outcome.continuityPreserved)
    }

    @Test
    fun neverDropsTheParameterSetsNeededByTheSurvivingKeyFrame() {
        val backlog = listOf(
            frameHeader(1, config = true),
            frameHeader(2),
            frameHeader(3, config = true),
            frameHeader(4),
            frameHeader(5, keyFrame = true),
            frameHeader(6),
        )

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(backlog) { it }

        assertEquals(listOf(3L, 5L, 6L), outcome.kept.map { it.seq })
        assertTrue(outcome.kept.first().config)
        assertEquals(3, outcome.droppedCount)
    }

    @Test
    fun keepsConfigEnvelopesThatFollowTheKeyFrame() {
        val backlog = listOf(
            frameHeader(1),
            frameHeader(2, keyFrame = true),
            frameHeader(3, config = true),
            frameHeader(4, keyFrame = true),
        )

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(backlog) { it }

        assertEquals(listOf(3L, 4L), outcome.kept.map { it.seq })
    }

    @Test
    fun leavesASingleFrameAlone() {
        val single = listOf(frameHeader(9, keyFrame = true))

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(single) { it }

        assertEquals(single, outcome.kept)
        assertEquals(0, outcome.droppedCount)
    }

    @Test
    fun keepsABacklogThatAlreadyStartsOnTheKeyFrame() {
        val backlog = listOf(frameHeader(1, keyFrame = true), frameHeader(2), frameHeader(3))

        val outcome = FrameDropPolicy.pruneToLatestKeyFrame(backlog) { it }

        assertEquals(0, outcome.droppedCount)
        assertEquals(backlog, outcome.kept)
    }

    @Test
    fun hardDropKeepsOnlyTheNewestConfigAndBreaksContinuity() {
        val backlog = listOf(
            frameHeader(1, config = true),
            frameHeader(2),
            frameHeader(3, config = true),
            frameHeader(4),
        )

        val outcome = FrameDropPolicy.dropAllButConfig(backlog) { it }

        assertEquals(listOf(3L), outcome.kept.map { it.seq })
        assertEquals(3, outcome.droppedCount)
        assertFalse(outcome.continuityPreserved)
    }

    @Test
    fun hardDropOnAnEmptyBacklogIsANoOp() {
        val outcome = FrameDropPolicy.dropAllButConfig(emptyList<StreamFrameHeader>()) { it }

        assertTrue(outcome.kept.isEmpty())
        assertEquals(0, outcome.droppedCount)
        assertTrue(outcome.continuityPreserved)
    }
}
