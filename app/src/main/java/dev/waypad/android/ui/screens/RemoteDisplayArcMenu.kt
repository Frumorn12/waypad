package dev.waypad.android.ui.screens

import android.graphics.Rect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.waypad.android.ui.components.ARC_EDGE_WIDTH
import dev.waypad.android.ui.components.ArcMenu
import dev.waypad.android.ui.components.ArcMenuItem
import dev.waypad.android.ui.components.rememberArcMenuMetrics

/**
 * Hosts the arc menu and owns the swipe that opens it.
 *
 * Sits above the video as a full-size overlay so that once a swipe has started it keeps receiving
 * events wherever the thumb goes, but it only *claims* a touch that begins inside the trailing
 * edge strip. Anything starting elsewhere is left untouched and falls through to the remote
 * desktop gestures underneath.
 *
 * That fall-through is not enough on its own: [remoteDisplayGestures] deliberately takes pointers
 * with `requireUnconsumed = false`, so it would also act on a touch consumed here. The viewport
 * passes the same [ARC_EDGE_WIDTH] to it as an edge guard, and the two together are what stop a
 * swipe from opening the menu and dragging the remote pointer at the same time.
 */
@Composable
fun ArcMenuOverlay(
    items: List<ArcMenuItem>,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    haptics: HapticFeedback,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val metrics = rememberArcMenuMetrics(items.size)
    val density = LocalDensity.current
    val edgePx = with(density) { ARC_EDGE_WIDTH.toPx() }
    val marginPx = with(density) { ARC_MARGIN.toPx() }

    var open by remember { mutableStateOf(false) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableFloatStateOf(0f) }

    // Android reserves the vertical edges for its own Back gesture. Claiming the strip back keeps
    // the swipe from being eaten, and the system caps how much can be claimed, which is one more
    // reason the strip stays narrow.
    val view = LocalView.current
    var edgeBounds by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(view, edgeBounds, enabled) {
        val bounds = edgeBounds
        if (enabled && bounds != null) {
            view.systemGestureExclusionRects = listOf(bounds)
        }
        onDispose {
            if (view.systemGestureExclusionRects.isNotEmpty()) {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewportWidth = coordinates.size.width.toFloat()
                val width = coordinates.size.width
                val height = coordinates.size.height
                edgeBounds = Rect(
                    (width - edgePx).toInt().coerceAtLeast(0),
                    0,
                    width,
                    height,
                )
            }
            .pointerInput(enabled, items.size, edgePx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Not our strip: leave the event unconsumed for the desktop gestures below.
                    if (down.position.x < size.width - edgePx) return@awaitEachGesture
                    down.consume()

                    val height = size.height.toFloat()
                    anchorY = metrics.geometry.anchorFor(down.position.y, height, marginPx)
                    selected = metrics.geometry.selectedIndex(anchorY, down.position.y)
                    open = true
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            // Sliding back onto the edge is how the gesture is called off, which
                            // keeps every swipe reversible without a separate cancel target.
                            cancelled = change.position.x >= size.width - edgePx
                            break
                        }
                        val next = metrics.geometry.selectedIndex(anchorY, change.position.y)
                        if (next != selected) {
                            selected = next
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }

                    val chosen = items.getOrNull(selected)
                    open = false
                    if (!cancelled && chosen != null) {
                        onActivate(chosen.id)
                    }
                }
            }
    ) {
        if (open) {
            ArcMenu(
                items = items,
                metrics = metrics,
                anchorY = anchorY,
                selectedIndex = selected,
                viewportWidth = viewportWidth,
            )
        }
    }
}

private val ARC_MARGIN = 40.dp
