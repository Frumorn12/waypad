package dev.waypad.android.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedFrameQueueTest {

    @Test
    fun deliversFramesInOrderWhileTheDecoderKeepsUp() {
        val queue = EncodedFrameQueue(softCapacity = 4, hardCapacity = 8)
        queue.offer(encodedFrame(1, keyFrame = true))
        queue.offer(encodedFrame(2))

        assertEquals(1L, queue.poll()?.header?.seq)
        assertEquals(2L, queue.poll()?.header?.seq)
        assertNull(queue.poll())
        assertEquals(0L, queue.droppedFrames)
    }

    @Test
    fun prunesToTheLatestKeyFrameOnceTheBacklogGrows() {
        val queue = EncodedFrameQueue(softCapacity = 3, hardCapacity = 8)
        queue.offer(encodedFrame(1, keyFrame = true))
        queue.offer(encodedFrame(2))
        queue.offer(encodedFrame(3))
        queue.offer(encodedFrame(4, keyFrame = true))

        assertEquals(4L, queue.poll()?.header?.seq)
        assertNull(queue.poll())
        assertEquals(3L, queue.droppedFrames)
        assertFalse(queue.consumeContinuityBreak())
    }

    @Test
    fun neverDropsTheParameterSetsWhilePruning() {
        val queue = EncodedFrameQueue(softCapacity = 3, hardCapacity = 8)
        queue.offer(encodedFrame(1, config = true))
        queue.offer(encodedFrame(2, keyFrame = true))
        queue.offer(encodedFrame(3))
        queue.offer(encodedFrame(4))
        queue.offer(encodedFrame(5, keyFrame = true))

        val drained = generateSequence { queue.poll() }.map { it.header.seq }.toList()
        assertEquals(listOf(1L, 5L), drained)
        assertTrue(queue.peek() == null)
    }

    @Test
    fun cutsTheWholeBacklogWhenNoKeyFrameEverArrives() {
        val queue = EncodedFrameQueue(softCapacity = 2, hardCapacity = 4)
        queue.offer(encodedFrame(0, config = true))
        repeat(6) { index -> queue.offer(encodedFrame(index + 1L)) }

        assertTrue(queue.consumeContinuityBreak())
        assertFalse("the flag is consumed once", queue.consumeContinuityBreak())
        assertTrue(queue.droppedFrames >= 4)
        assertTrue("the backlog stays bounded", queue.size() <= 4)
        val drained = generateSequence { queue.poll() }.toList()
        assertTrue("the parameter sets survive the cut", drained.any { it.header.config })
    }

    @Test
    fun clearResetsTheBacklogAndTheContinuityFlag() {
        val queue = EncodedFrameQueue(softCapacity = 1, hardCapacity = 2)
        repeat(5) { index -> queue.offer(encodedFrame(index.toLong())) }
        queue.clear()

        assertEquals(0, queue.size())
        assertFalse(queue.consumeContinuityBreak())
        assertNull(queue.poll())
    }

    @Test
    fun defaultCapacitiesBoundTheBacklog() {
        val queue = EncodedFrameQueue()
        repeat(200) { index -> queue.offer(encodedFrame(index.toLong(), keyFrame = index % 30 == 0)) }

        assertTrue("backlog stays bounded: ${queue.size()}", queue.size() <= EncodedFrameQueue.DEFAULT_HARD_CAPACITY)
        assertTrue(queue.droppedFrames > 0)
    }
}
