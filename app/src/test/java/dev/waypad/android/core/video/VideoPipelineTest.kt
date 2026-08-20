package dev.waypad.android.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the queue and the gate together, i.e. the exact composition the decoder drain loop
 * uses: prune the backlog, then let the gate decide what can actually be fed to `MediaCodec`.
 */
class VideoPipelineTest {

    private val queue = EncodedFrameQueue(softCapacity = 3, hardCapacity = 8)
    private val gate = H264SyncGate()

    @Test
    fun feedsNothingBeforeTheParameterSetsAndTheFirstKeyFrame() {
        offerAll(encodedFrame(1), encodedFrame(2), encodedFrame(3))

        val fed = drain()

        assertTrue(fed.isEmpty())
        assertTrue(gate.droppedAwaitingSync >= 1)
    }

    @Test
    fun startsDecodingOnConfigThenKeyFrame() {
        offerAll(
            encodedFrame(0, config = true),
            encodedFrame(1),
            encodedFrame(2, keyFrame = true),
            encodedFrame(3),
        )

        val fed = drain()

        // seq 1 arrives before the key frame and cannot be decoded.
        assertEquals(listOf(0L, 2L, 3L), fed)
    }

    @Test
    fun aBacklogBurstResumesFromTheLatestKeyFrameWithoutLosingTheConfig() {
        offerAll(encodedFrame(0, config = true), encodedFrame(1, keyFrame = true))
        assertEquals(listOf(0L, 1L), drain())

        // Network stall: six frames land at once, the newest key frame is seq 6.
        offerAll(
            encodedFrame(2),
            encodedFrame(3),
            encodedFrame(4),
            encodedFrame(5),
            encodedFrame(6, keyFrame = true),
            encodedFrame(7),
        )

        assertEquals(listOf(6L, 7L), drain())
        assertTrue(queue.droppedFrames >= 4)
    }

    @Test
    fun aBacklogWithoutAnyKeyFrameIsCutAndWaitsForTheNextIdr() {
        offerAll(encodedFrame(0, config = true), encodedFrame(1, keyFrame = true))
        drain()

        offerAll(*Array(20) { encodedFrame(it + 2L) })
        val duringStall = drain()

        // The hard cut broke the reference chain, so nothing is fed until a key frame shows up.
        assertTrue("fed=$duringStall", duringStall.size < 20)
        offerAll(encodedFrame(100, keyFrame = true), encodedFrame(101))
        assertEquals(listOf(100L, 101L), drain())
    }

    private fun offerAll(vararg frames: EncodedVideoFrame) = frames.forEach(queue::offer)

    /** Mirrors `H264SurfaceDecoder.drain`, minus the codec. */
    private fun drain(): List<Long> {
        val fed = ArrayList<Long>()
        if (queue.consumeContinuityBreak()) gate.requestResync()
        while (true) {
            val frame = queue.poll() ?: return fed
            when (gate.admit(frame.header)) {
                GateDecision.Feed, GateDecision.FeedConfig -> fed += frame.header.seq
                else -> Unit
            }
        }
    }
}
