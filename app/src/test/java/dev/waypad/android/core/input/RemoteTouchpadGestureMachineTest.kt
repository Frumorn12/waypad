package dev.waypad.android.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTouchpadGestureMachineTest {
    @Test
    fun singlePointerMoveEmitsMoveEvent() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f, longPressDragEnabled = false)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        val actions = gesture.update(listOf(pointer(1, 20f, 12f)), timeMillis = 16L)

        val move = actions.filterIsInstance<RemoteGestureAction.Move>().single()
        assertEquals(10f, move.dx)
        assertEquals(2f, move.dy)
        assertEquals(RemoteGestureMode.MovingPointer, gesture.mode)
    }

    @Test
    fun secondPointerDownEntersScrollWithoutSyntheticDelta() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        val enterActions = gesture.update(
            listOf(pointer(1, 10f, 10f), pointer(2, 30f, 10f)),
            timeMillis = 20L,
        )
        val scrollActions = gesture.update(
            listOf(pointer(1, 10f, 22f), pointer(2, 30f, 22f)),
            timeMillis = 36L,
        )

        assertTrue(enterActions.none { it is RemoteGestureAction.Scroll })
        assertTrue(enterActions.any { it is RemoteGestureAction.ModeChanged && it.to == RemoteGestureMode.TwoFingerScroll })
        val scroll = scrollActions.filterIsInstance<RemoteGestureAction.Scroll>().single()
        assertEquals(0f, scroll.dx)
        assertEquals(12f, scroll.dy)
    }

    @Test
    fun liftingOnePointerDuringScrollFinishesAndRecovers() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        gesture.update(listOf(pointer(1, 10f, 10f), pointer(2, 30f, 10f)), timeMillis = 10L)
        gesture.update(listOf(pointer(1, 10f, 25f), pointer(2, 30f, 25f)), timeMillis = 20L)
        val actions = gesture.update(listOf(pointer(1, 10f, 25f)), timeMillis = 30L)

        assertTrue(actions.any { it == RemoteGestureAction.FinishScroll })
        assertTrue(actions.any { it is RemoteGestureAction.ModeChanged && it.to == RemoteGestureMode.Recovering })
        assertEquals(RemoteGestureMode.Recovering, gesture.mode)
    }

    @Test
    fun remainingPointerCanMoveAfterScrollRecoveryWithoutJump() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        gesture.update(listOf(pointer(1, 10f, 10f), pointer(2, 30f, 10f)), timeMillis = 10L)
        gesture.update(listOf(pointer(1, 10f, 25f), pointer(2, 30f, 25f)), timeMillis = 20L)
        gesture.update(listOf(pointer(1, 10f, 25f)), timeMillis = 30L)
        val actions = gesture.update(listOf(pointer(1, 14f, 27f)), timeMillis = 46L)

        val move = actions.filterIsInstance<RemoteGestureAction.Move>().single()
        assertEquals(4f, move.dx)
        assertEquals(2f, move.dy)
        assertEquals(RemoteGestureMode.MovingPointer, gesture.mode)
    }

    @Test
    fun fullGestureEndReturnsToIdleCleanly() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        gesture.update(listOf(pointer(1, 16f, 10f)), timeMillis = 16L)
        val actions = gesture.update(emptyList(), timeMillis = 32L)

        assertTrue(actions.any { it is RemoteGestureAction.ModeChanged && it.to == RemoteGestureMode.Idle })
        assertEquals(RemoteGestureMode.Idle, gesture.mode)
    }

    @Test
    fun canceledScrollFinishesAndReturnsToIdle() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        gesture.update(listOf(pointer(1, 10f, 10f), pointer(2, 30f, 10f)), timeMillis = 10L)
        val actions = gesture.cancel("test_cancel")

        assertTrue(actions.any { it == RemoteGestureAction.FinishScroll })
        assertTrue(actions.any { it is RemoteGestureAction.ModeChanged && it.to == RemoteGestureMode.GestureCancelled })
        assertEquals(RemoteGestureMode.Idle, gesture.mode)
    }

    @Test
    fun noDeadStateAfterScrollEnds() {
        val gesture = RemoteTouchpadGestureMachine(tapSlopPx = 8f, longPressDragEnabled = false)

        gesture.begin(pointer(1, 10f, 10f), timeMillis = 0L)
        gesture.update(listOf(pointer(1, 10f, 10f), pointer(2, 30f, 10f)), timeMillis = 10L)
        gesture.update(listOf(pointer(1, 10f, 25f), pointer(2, 30f, 25f)), timeMillis = 20L)
        gesture.update(emptyList(), timeMillis = 30L)

        gesture.begin(pointer(3, 5f, 5f), timeMillis = 60L)
        val actions = gesture.update(listOf(pointer(3, 9f, 6f)), timeMillis = 76L)

        assertTrue(actions.any { it is RemoteGestureAction.Move })
        assertEquals(RemoteGestureMode.SinglePointerDown, gesture.mode)
    }

    private fun pointer(id: Long, x: Float, y: Float) = RemotePointer(id, x, y)
}
