package dev.waypad.android.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H264SyncGateTest {

    @Test
    fun dropsEverythingUntilTheParameterSetsArrive() {
        val gate = H264SyncGate()

        assertEquals(GateDecision.DropAwaitingConfig, gate.admit(frameHeader(1)))
        assertEquals(GateDecision.DropAwaitingConfig, gate.admit(frameHeader(2, keyFrame = true)))
        assertFalse(gate.hasConfig)
        assertEquals(2L, gate.droppedAwaitingSync)
    }

    @Test
    fun followsTheConfigKeyFrameFrameSequence() {
        val gate = H264SyncGate()

        assertEquals(GateDecision.FeedConfig, gate.admit(frameHeader(0, config = true)))
        assertTrue(gate.hasConfig)
        assertFalse(gate.synced)

        assertEquals(GateDecision.DropAwaitingKeyFrame, gate.admit(frameHeader(1)))
        assertFalse(gate.synced)

        assertEquals(GateDecision.Feed, gate.admit(frameHeader(2, keyFrame = true)))
        assertTrue(gate.synced)

        assertEquals(GateDecision.Feed, gate.admit(frameHeader(3)))
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(4)))
    }

    @Test
    fun dropsFramesThatAreOlderThanWhatWasAlreadyDecoded() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(1, keyFrame = true))
        gate.admit(frameHeader(2))

        assertEquals(GateDecision.DropStale, gate.admit(frameHeader(2)))
        assertEquals(GateDecision.DropStale, gate.admit(frameHeader(1)))
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(3)))
    }

    @Test
    fun aChangedParameterSetForcesAResync() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(1, keyFrame = true))
        assertTrue(gate.synced)

        // Resolution change: new SPS/PPS, everything before the next IDR is unusable.
        assertEquals(GateDecision.FeedConfig, gate.admit(frameHeader(2, config = true), configChanged = true))
        assertFalse(gate.synced)
        assertEquals(GateDecision.DropAwaitingKeyFrame, gate.admit(frameHeader(3)))
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(4, keyFrame = true)))
    }

    @Test
    fun aRepeatedIdenticalParameterSetDoesNotStallTheStream() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(1, keyFrame = true))

        assertEquals(GateDecision.FeedConfig, gate.admit(frameHeader(2, config = true), configChanged = false))
        assertTrue(gate.synced)
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(3)))
    }

    @Test
    fun resyncKeepsTheParameterSetsButWaitsForAKeyFrame() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(1, keyFrame = true))

        gate.requestResync()

        assertTrue("the codec can still be configured", gate.hasConfig)
        assertFalse(gate.synced)
        assertEquals(GateDecision.DropAwaitingKeyFrame, gate.admit(frameHeader(2)))
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(3, keyFrame = true)))
    }

    @Test
    fun resetForgetsEverything() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(1, keyFrame = true))

        gate.reset()

        assertFalse(gate.hasConfig)
        assertFalse(gate.synced)
        assertEquals(0L, gate.droppedAwaitingSync)
        assertEquals(GateDecision.DropAwaitingConfig, gate.admit(frameHeader(2, keyFrame = true)))
    }

    @Test
    fun acceptsAKeyFrameWhoseSequenceRestartsAfterAResync() {
        val gate = H264SyncGate()
        gate.admit(frameHeader(0, config = true))
        gate.admit(frameHeader(500, keyFrame = true))
        gate.requestResync()

        // A reconnected daemon starts numbering from scratch.
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(0, keyFrame = true)))
        assertEquals(GateDecision.Feed, gate.admit(frameHeader(1)))
    }
}
