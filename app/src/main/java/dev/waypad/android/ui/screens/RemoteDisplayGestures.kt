package dev.waypad.android.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.screen.ScreenViewport
import kotlin.math.hypot

/** Host-side effects the remote-display gesture recogniser can trigger. */
@Immutable
data class RemoteDisplayGestureCallbacks(
    val onDesktopPointerMove: (x: Float, y: Float) -> Unit,
    val onDesktopClick: (x: Float, y: Float) -> Unit,
    val onPointerButton: (PointerButton, ButtonState) -> Unit,
    val onScroll: (dx: Float, dy: Float, finish: Boolean) -> Unit,
)

/** Long-press duration after which a moving finger becomes a left-button drag. */
private const val DRAG_HOLD_MILLIS = 320L

/** Damping applied to two-finger scroll deltas before they are sent to the host. */
private const val SCROLL_DAMPING = 0.75f

/**
 * Absolute pointer mapping for the remote display.
 *
 * Behaviour is a verbatim move of the previous inline `pointerInput` block, including the
 * `pointerInput` key set, the `consume()` calls, the drag/scroll arbitration and the tap detection.
 * It maps view coordinates onto desktop coordinates through [ScreenViewport], which assumes the
 * video output is letterboxed with `ContentScale.Fit` semantics inside the same box - whatever
 * renders the frames must keep that contract.
 *
 * @param frameWidth width in pixels of the last decoded frame, `null` when nothing has arrived yet
 * @param frameHeight height in pixels of the last decoded frame
 * @param viewSize measured size of the viewport box
 * @param sourceKey id of the capture source; a change restarts the recogniser
 * @param hapticsEnabled snapshot of the user's haptics preference
 */
fun Modifier.remoteDisplayGestures(
    frameWidth: Int?,
    frameHeight: Int?,
    viewSize: IntSize,
    sourceKey: String?,
    tapSlopPx: Float,
    hapticsEnabled: Boolean,
    haptics: HapticFeedback,
    callbacks: RemoteDisplayGestureCallbacks,
): Modifier = pointerInput(frameWidth, frameHeight, viewSize, sourceKey) {
    if (frameWidth == null || frameHeight == null) return@pointerInput
    if (viewSize.width <= 0 || viewSize.height <= 0) return@pointerInput
    val viewport = ScreenViewport(
        viewWidth = viewSize.width.toFloat(),
        viewHeight = viewSize.height.toFloat(),
        sourceWidth = frameWidth.toFloat(),
        sourceHeight = frameHeight.toFloat(),
    )
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val start = viewport.map(down.position.x, down.position.y)
        var lastPoint = start
        var moved = false
        var dragActive = false
        var scrollActive = false
        var lastCentroid: Offset? = null
        if (start != null) callbacks.onDesktopPointerMove(start.x, start.y)

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                val now = event.changes.maxOfOrNull { it.uptimeMillis } ?: down.uptimeMillis
                if (pressed.size >= 2) {
                    if (!scrollActive) {
                        if (dragActive) {
                            callbacks.onPointerButton(PointerButton.Left, ButtonState.Released)
                            dragActive = false
                        }
                        scrollActive = true
                        lastCentroid = pressed.centroid()
                    } else {
                        val centroid = pressed.centroid()
                        lastCentroid?.let { previous ->
                            callbacks.onScroll(
                                (previous.x - centroid.x) * SCROLL_DAMPING,
                                (previous.y - centroid.y) * SCROLL_DAMPING,
                                false,
                            )
                        }
                        lastCentroid = centroid
                    }
                } else {
                    if (scrollActive) {
                        callbacks.onScroll(0f, 0f, true)
                        scrollActive = false
                        lastCentroid = null
                    }
                    val change = pressed.firstOrNull()
                    if (change != null) {
                        val distance = hypot(
                            change.position.x - down.position.x,
                            change.position.y - down.position.y,
                        )
                        if (distance > tapSlopPx) moved = true
                        val mapped = viewport.map(change.position.x, change.position.y)
                        if (mapped != null) {
                            if (moved && !dragActive && now - down.uptimeMillis > DRAG_HOLD_MILLIS) {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                callbacks.onPointerButton(PointerButton.Left, ButtonState.Pressed)
                                dragActive = true
                            }
                            callbacks.onDesktopPointerMove(mapped.x, mapped.y)
                            lastPoint = mapped
                        }
                    }
                }
                event.changes.forEach { change ->
                    if (change.changedToDown() || change.changedToUp() || change.positionChanged()) {
                        change.consume()
                    }
                }
                if (pressed.isEmpty()) break
            }
        } finally {
            if (scrollActive) callbacks.onScroll(0f, 0f, true)
            if (dragActive) callbacks.onPointerButton(PointerButton.Left, ButtonState.Released)
        }
        if (!moved && start != null && lastPoint != null) {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            callbacks.onDesktopClick(start.x, start.y)
        }
    }
}

internal fun List<PointerInputChange>.centroid(): Offset {
    val pressed = filter { it.pressed }
    if (pressed.isEmpty()) return Offset.Zero
    val x = pressed.sumOf { it.position.x.toDouble() }.toFloat() / pressed.size
    val y = pressed.sumOf { it.position.y.toDouble() }.toFloat() / pressed.size
    return Offset(x, y)
}
