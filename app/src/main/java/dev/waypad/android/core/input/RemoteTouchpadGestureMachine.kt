package dev.waypad.android.core.input

import kotlin.math.abs
import kotlin.math.hypot

data class RemotePointer(
    val id: Long,
    val x: Float,
    val y: Float,
) {
    val position: PointerPosition
        get() = PointerPosition(x, y)
}

data class PointerPosition(
    val x: Float,
    val y: Float,
) {
    operator fun minus(other: PointerPosition): PointerDelta = PointerDelta(x - other.x, y - other.y)

    fun distanceTo(other: PointerPosition): Float = hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()
}

data class PointerDelta(
    val dx: Float,
    val dy: Float,
) {
    fun isSignificant(threshold: Float): Boolean = abs(dx) + abs(dy) > threshold
}

enum class RemoteGestureMode(val label: String) {
    Idle("Idle"),
    SinglePointerDown("SinglePointerDown"),
    MovingPointer("MovingPointer"),
    TwoFingerScroll("TwoFingerScroll"),
    Dragging("Dragging"),
    GestureCancelled("GestureCancelled"),
    Recovering("Recovering"),
}

sealed interface RemoteGestureAction {
    data class Move(val dx: Float, val dy: Float) : RemoteGestureAction
    data class Scroll(val dx: Float, val dy: Float) : RemoteGestureAction
    data object FinishScroll : RemoteGestureAction
    data object Click : RemoteGestureAction
    data object DragStart : RemoteGestureAction
    data object DragEnd : RemoteGestureAction
    data class ModeChanged(
        val from: RemoteGestureMode,
        val to: RemoteGestureMode,
        val reason: String,
    ) : RemoteGestureAction
}

/**
 * Pure state machine for the remote touchpad. Compose supplies pointer snapshots; this class owns
 * the gesture transitions so multi-touch changes cannot implicitly reset cursor/scroll state.
 */
class RemoteTouchpadGestureMachine(
    private val tapSlopPx: Float,
    private val moveThresholdPx: Float = 0.05f,
    private val longPressTimeoutMillis: Long = 420L,
    private val longPressDragEnabled: Boolean = true,
) {
    private var state: GestureState = GestureState.Idle
    private var maxPointerCount = 0
    private var movedBeyondTap = false
    private var sawScroll = false
    private var dragActive = false
    private var downTimeMillis = 0L
    private var downPosition = PointerPosition(0f, 0f)

    val mode: RemoteGestureMode
        get() = state.mode

    fun begin(pointer: RemotePointer, timeMillis: Long): List<RemoteGestureAction> {
        val actions = mutableListOf<RemoteGestureAction>()
        if (state !is GestureState.Idle) {
            actions += cancel("new_pointer_down_before_idle")
        }
        maxPointerCount = 1
        movedBeyondTap = false
        sawScroll = false
        dragActive = false
        downTimeMillis = timeMillis
        downPosition = pointer.position
        actions += transitionTo(GestureState.SinglePointerDown(pointer.id, pointer.position), "pointer_down")
        return actions
    }

    fun update(activePointers: List<RemotePointer>, timeMillis: Long): List<RemoteGestureAction> {
        val pointers = activePointers.filter { it.x.isFinite() && it.y.isFinite() }
        maxPointerCount = maxOf(maxPointerCount, pointers.size)
        if (pointers.isEmpty()) return end("all_pointers_up")

        return when (val current = state) {
            GestureState.Idle -> begin(pointers.first(), timeMillis)
            is GestureState.SinglePointerDown -> updateSinglePointer(current.pointerId, current.lastPosition, pointers, timeMillis)
            is GestureState.MovingPointer -> updateSinglePointer(current.pointerId, current.lastPosition, pointers, timeMillis)
            is GestureState.Dragging -> updateDragging(current.pointerId, current.lastPosition, pointers)
            is GestureState.TwoFingerScroll -> updateTwoFingerScroll(current.lastCentroid, pointers)
            is GestureState.Recovering -> updateRecovering(pointers)
            GestureState.Cancelled -> cancel("cancelled_state_update")
        }
    }

    fun end(reason: String = "gesture_end"): List<RemoteGestureAction> {
        if (state is GestureState.Idle) return emptyList()
        val actions = mutableListOf<RemoteGestureAction>()
        if (state is GestureState.TwoFingerScroll && sawScroll) {
            actions += RemoteGestureAction.FinishScroll
        }
        if (dragActive) {
            actions += RemoteGestureAction.DragEnd
        }
        if ((state is GestureState.SinglePointerDown || state is GestureState.MovingPointer) && !movedBeyondTap && maxPointerCount == 1) {
            actions += RemoteGestureAction.Click
        }
        actions += transitionTo(GestureState.Idle, reason)
        resetGestureBookkeeping()
        return actions
    }

    fun cancel(reason: String = "gesture_cancelled"): List<RemoteGestureAction> {
        if (state is GestureState.Idle) return emptyList()
        val actions = mutableListOf<RemoteGestureAction>()
        if (state is GestureState.TwoFingerScroll && sawScroll) {
            actions += RemoteGestureAction.FinishScroll
        }
        if (dragActive) {
            actions += RemoteGestureAction.DragEnd
        }
        actions += transitionTo(GestureState.Cancelled, reason)
        actions += transitionTo(GestureState.Idle, "cancel_reset")
        resetGestureBookkeeping()
        return actions
    }

    private fun updateSinglePointer(
        pointerId: Long,
        lastPosition: PointerPosition,
        pointers: List<RemotePointer>,
        timeMillis: Long,
    ): List<RemoteGestureAction> {
        if (pointers.size >= 2) return enterScroll(pointers, "second_pointer_down")
        val pointer = pointers.firstOrNull { it.id == pointerId } ?: return recover(pointers, "primary_pointer_missing")
        val delta = pointer.position - lastPosition
        val canStartDrag = shouldStartDrag(pointer.position, delta, timeMillis)
        if (pointer.position.distanceTo(downPosition) > tapSlopPx) {
            movedBeyondTap = true
        }

        val actions = mutableListOf<RemoteGestureAction>()
        when {
            canStartDrag -> {
                movedBeyondTap = true
                dragActive = true
                actions += transitionTo(GestureState.Dragging(pointer.id, pointer.position), "long_press_drag")
                actions += RemoteGestureAction.DragStart
                if (delta.isSignificant(moveThresholdPx)) actions += RemoteGestureAction.Move(delta.dx, delta.dy)
            }
            delta.isSignificant(moveThresholdPx) -> {
                val next = if (movedBeyondTap) {
                    GestureState.MovingPointer(pointer.id, pointer.position)
                } else {
                    GestureState.SinglePointerDown(pointer.id, pointer.position)
                }
                actions += transitionTo(next, if (movedBeyondTap) "pointer_move" else "pointer_jitter")
                actions += RemoteGestureAction.Move(delta.dx, delta.dy)
            }
            else -> {
                state = if (movedBeyondTap) {
                    GestureState.MovingPointer(pointer.id, pointer.position)
                } else {
                    GestureState.SinglePointerDown(pointer.id, pointer.position)
                }
            }
        }
        return actions
    }

    private fun updateDragging(
        pointerId: Long,
        lastPosition: PointerPosition,
        pointers: List<RemotePointer>,
    ): List<RemoteGestureAction> {
        if (pointers.size >= 2) {
            dragActive = false
            return listOf(RemoteGestureAction.DragEnd) + enterScroll(pointers, "second_pointer_down_while_dragging")
        }
        val pointer = pointers.firstOrNull { it.id == pointerId }
            ?: return listOf(RemoteGestureAction.DragEnd).also { dragActive = false } + recover(pointers, "drag_pointer_missing")
        val delta = pointer.position - lastPosition
        state = GestureState.Dragging(pointer.id, pointer.position)
        return if (delta.isSignificant(moveThresholdPx)) listOf(RemoteGestureAction.Move(delta.dx, delta.dy)) else emptyList()
    }

    private fun updateTwoFingerScroll(
        lastCentroid: PointerPosition,
        pointers: List<RemotePointer>,
    ): List<RemoteGestureAction> {
        if (pointers.size < 2) {
            val actions = mutableListOf<RemoteGestureAction>()
            if (sawScroll) actions += RemoteGestureAction.FinishScroll
            actions += transitionTo(recoveringState(pointers), "scroll_pointer_lifted")
            return actions
        }
        val centroid = centroidOf(pointers)
        val delta = centroid - lastCentroid
        state = GestureState.TwoFingerScroll(centroid)
        return if (delta.isSignificant(moveThresholdPx)) listOf(RemoteGestureAction.Scroll(delta.dx, delta.dy)) else emptyList()
    }

    private fun updateRecovering(pointers: List<RemotePointer>): List<RemoteGestureAction> {
        if (pointers.size >= 2) return enterScroll(pointers, "second_pointer_returned")
        val pointer = pointers.first()
        val current = state as? GestureState.Recovering
        val lastPosition = current?.lastPosition
        if (current?.pointerId == pointer.id && lastPosition != null) {
            val delta = pointer.position - lastPosition
            val actions = mutableListOf<RemoteGestureAction>()
            actions += transitionTo(GestureState.MovingPointer(pointer.id, pointer.position), "recovered_single_pointer")
            if (delta.isSignificant(moveThresholdPx)) {
                actions += RemoteGestureAction.Move(delta.dx, delta.dy)
            }
            return actions
        }
        state = GestureState.Recovering(pointer.id, pointer.position)
        return emptyList()
    }

    private fun enterScroll(pointers: List<RemotePointer>, reason: String): List<RemoteGestureAction> {
        sawScroll = true
        movedBeyondTap = true
        return listOf(transitionTo(GestureState.TwoFingerScroll(centroidOf(pointers)), reason))
    }

    private fun recover(pointers: List<RemotePointer>, reason: String): List<RemoteGestureAction> {
        val actions = mutableListOf<RemoteGestureAction>()
        if (dragActive) {
            dragActive = false
            actions += RemoteGestureAction.DragEnd
        }
        actions += transitionTo(recoveringState(pointers), reason)
        return actions
    }

    private fun recoveringState(pointers: List<RemotePointer>): GestureState {
        val pointer = pointers.firstOrNull()
        return GestureState.Recovering(pointer?.id, pointer?.position)
    }

    private fun shouldStartDrag(position: PointerPosition, delta: PointerDelta, timeMillis: Long): Boolean {
        return longPressDragEnabled &&
            !dragActive &&
            maxPointerCount == 1 &&
            !movedBeyondTap &&
            timeMillis - downTimeMillis >= longPressTimeoutMillis &&
            position.distanceTo(downPosition) <= tapSlopPx * 1.5f &&
            delta.isSignificant(moveThresholdPx)
    }

    private fun transitionTo(next: GestureState, reason: String): RemoteGestureAction.ModeChanged {
        val previousMode = state.mode
        state = next
        return RemoteGestureAction.ModeChanged(previousMode, next.mode, reason)
    }

    private fun resetGestureBookkeeping() {
        maxPointerCount = 0
        movedBeyondTap = false
        sawScroll = false
        dragActive = false
        downTimeMillis = 0L
        downPosition = PointerPosition(0f, 0f)
    }

    private fun centroidOf(pointers: List<RemotePointer>): PointerPosition {
        val x = pointers.sumOf { it.x.toDouble() }.toFloat() / pointers.size
        val y = pointers.sumOf { it.y.toDouble() }.toFloat() / pointers.size
        return PointerPosition(x, y)
    }

    private sealed interface GestureState {
        val mode: RemoteGestureMode

        data object Idle : GestureState {
            override val mode = RemoteGestureMode.Idle
        }

        data class SinglePointerDown(
            val pointerId: Long,
            val lastPosition: PointerPosition,
        ) : GestureState {
            override val mode = RemoteGestureMode.SinglePointerDown
        }

        data class MovingPointer(
            val pointerId: Long,
            val lastPosition: PointerPosition,
        ) : GestureState {
            override val mode = RemoteGestureMode.MovingPointer
        }

        data class TwoFingerScroll(
            val lastCentroid: PointerPosition,
        ) : GestureState {
            override val mode = RemoteGestureMode.TwoFingerScroll
        }

        data class Dragging(
            val pointerId: Long,
            val lastPosition: PointerPosition,
        ) : GestureState {
            override val mode = RemoteGestureMode.Dragging
        }

        data class Recovering(
            val pointerId: Long?,
            val lastPosition: PointerPosition?,
        ) : GestureState {
            override val mode = RemoteGestureMode.Recovering
        }

        data object Cancelled : GestureState {
            override val mode = RemoteGestureMode.GestureCancelled
        }
    }
}
