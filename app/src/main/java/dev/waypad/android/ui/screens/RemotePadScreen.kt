package dev.waypad.android.ui.screens

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.waypad.android.BuildConfig
import dev.waypad.android.core.input.RemoteGestureAction
import dev.waypad.android.core.input.RemoteGestureMode
import dev.waypad.android.core.input.RemotePointer
import dev.waypad.android.core.input.RemoteTouchpadGestureMachine
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.ui.components.CapabilityPill
import dev.waypad.android.ui.components.ClickButton
import dev.waypad.android.ui.components.StatusPill
import dev.waypad.android.ui.components.TelemetryOverlay
import dev.waypad.android.ui.state.RemotePadActions
import dev.waypad.android.ui.state.RemotePadUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme
import kotlinx.coroutines.CancellationException

/**
 * Relative touchpad: taps, double taps, drag lock and two-finger scroll.
 *
 * The gesture pipeline is a verbatim move of the previous implementation - the pointer bookkeeping,
 * logging tags, consume() calls, cancellation paths and the `rememberUpdatedState` captures are
 * unchanged, only `viewModel.x(...)` became `actions.onX(...)`.
 */
@Composable
fun RemotePadScreen(
    state: RemotePadUiState,
    actions: RemotePadActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val tapSlop = with(LocalDensity.current) { WaypadTheme.spacing.tapSlop.toPx() }
    var dragLocked by remember { mutableStateOf(false) }
    val currentDragLocked by rememberUpdatedState(dragLocked)
    val currentHapticsEnabled by rememberUpdatedState(state.haptics)
    val padActive = state.sessionActive
    val scrollMode = state.gestureMode == RemoteGestureMode.TwoFingerScroll

    val targetBorder = when {
        scrollMode -> MaterialTheme.colorScheme.tertiary
        padActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val targetContainer = when {
        scrollMode -> MaterialTheme.colorScheme.tertiaryContainer
        padActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val padBorder by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = WaypadTheme.motion.defaultEffects<Color>(),
        label = "pad-border",
    )
    val padContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = WaypadTheme.motion.defaultEffects<Color>(),
        label = "pad-container",
    )
    val padGradient = listOf(padContainer, MaterialTheme.colorScheme.surfaceContainerLow)
    val padAccent = when {
        scrollMode -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    fun setDragLocked(enabled: Boolean) {
        if (dragLocked == enabled) return
        if (enabled) {
            if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            actions.onPointerButton(PointerButton.Left, ButtonState.Pressed)
        } else {
            actions.onPointerButton(PointerButton.Left, ButtonState.Released)
        }
        dragLocked = enabled
    }

    fun handleGestureAction(action: RemoteGestureAction) {
        when (action) {
            is RemoteGestureAction.Move -> actions.onPointerMove(action.dx, action.dy)
            is RemoteGestureAction.Scroll -> actions.onScroll(action.dx, action.dy, false)
            RemoteGestureAction.FinishScroll -> actions.onScroll(0f, 0f, true)
            RemoteGestureAction.Click -> {
                if (!currentDragLocked) {
                    if (currentHapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.onPointerButton(PointerButton.Left, ButtonState.Pressed)
                    actions.onPointerButton(PointerButton.Left, ButtonState.Released)
                }
            }
            RemoteGestureAction.DragStart -> {
                if (!currentDragLocked) {
                    if (currentHapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onPointerButton(PointerButton.Left, ButtonState.Pressed)
                }
            }
            RemoteGestureAction.DragEnd -> {
                if (!currentDragLocked) {
                    actions.onPointerButton(PointerButton.Left, ButtonState.Released)
                }
            }
            is RemoteGestureAction.ModeChanged -> {
                if (action.from != action.to) {
                    Log.d("WaypadTouchpad", "gesture_transition from=${action.from.label} to=${action.to.label} reason=${action.reason}")
                    if (action.to == RemoteGestureMode.TwoFingerScroll && currentHapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        setDragLocked(false)
        actions.onReleasePointerButtons()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        setDragLocked(false)
        actions.onReleasePointerButtons()
    }

    DisposableEffect(Unit) {
        onDispose {
            actions.onReleasePointerButtons()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapabilityPill(
                label = if (state.inputSupported) "Input ready" else "Input blocked",
                available = state.inputSupported,
            )
            TextButton(onClick = actions.onPrepareInput) {
                Text(if (state.inputBackend == "wayland-portal") "Approve portal" else "Refresh input")
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.sm))
        if (state.controllerConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill("Controller connected")
                Text(
                    "Open fullscreen or Game Mode to forward inputs to PC.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = WaypadTheme.spacing.md),
                )
            }
            Spacer(Modifier.height(WaypadTheme.spacing.sm))
        }
        Spacer(Modifier.height(WaypadTheme.spacing.sm))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(WaypadTheme.shapes.slab)
                .background(Brush.verticalGradient(padGradient))
                .border(WaypadTheme.spacing.borderWidth, padBorder, WaypadTheme.shapes.slab)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val gesture = RemoteTouchpadGestureMachine(
                            tapSlopPx = tapSlop,
                            longPressDragEnabled = !currentDragLocked,
                        )
                        var sessionId = 0L
                        var sessionStarted = false
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            sessionId = actions.onBeginInteraction()
                            sessionStarted = true
                            Log.d("WaypadTouchpad", "pointer_down id=${down.id.value} x=${down.position.x} y=${down.position.y}")
                            gesture.begin(
                                RemotePointer(down.id.value, down.position.x, down.position.y),
                                down.uptimeMillis,
                            ).forEach(::handleGestureAction)
                            actions.onGestureStateChanged(gesture.mode, 1)

                            var moveLogCounter = 0
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    when {
                                        change.changedToDown() -> Log.d(
                                            "WaypadTouchpad",
                                            "pointer_down id=${change.id.value} x=${change.position.x} y=${change.position.y}",
                                        )
                                        change.changedToUp() -> Log.d("WaypadTouchpad", "pointer_up id=${change.id.value}")
                                        change.positionChanged() && change.pressed -> {
                                            moveLogCounter += 1
                                            if (moveLogCounter % 24 == 0) {
                                                Log.v(
                                                    "WaypadTouchpad",
                                                    "pointer_move sample_count=$moveLogCounter pointers=${event.changes.count { it.pressed }}",
                                                )
                                            }
                                        }
                                    }
                                }

                                val pressed = event.changes
                                    .filter { it.pressed }
                                    .map { RemotePointer(it.id.value, it.position.x, it.position.y) }
                                gesture.update(
                                    activePointers = pressed,
                                    timeMillis = event.changes.maxOfOrNull { it.uptimeMillis } ?: down.uptimeMillis,
                                ).forEach(::handleGestureAction)
                                actions.onGestureStateChanged(gesture.mode, pressed.size)

                                event.changes.forEach { change ->
                                    if (change.changedToDown() || change.changedToUp() || change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                                if (pressed.isEmpty()) break
                            }
                        } catch (cancelled: CancellationException) {
                            actions.onPointerCancelled("pointer_input_cancelled")
                            gesture.cancel("pointer_input_cancelled").forEach(::handleGestureAction)
                            throw cancelled
                        } catch (throwable: Throwable) {
                            actions.onPointerFailed(throwable)
                            gesture.cancel("pointer_input_error").forEach(::handleGestureAction)
                        } finally {
                            gesture.cancel("pointer_input_finally").forEach(::handleGestureAction)
                            actions.onGestureStateChanged(RemoteGestureMode.Idle, 0)
                            if (sessionStarted) actions.onEndInteraction(sessionId)
                            if (!currentDragLocked) actions.onReleasePointerButtons()
                        }
                    }
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(WaypadTheme.spacing.hero),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Mouse,
                    contentDescription = null,
                    tint = padAccent,
                    modifier = Modifier.size(WaypadTheme.spacing.emptyStateIconSize),
                )
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                Text("Touchpad", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap, double tap, drag lock, two-finger scroll",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (BuildConfig.DEBUG) {
                RemoteInputDebugOverlay(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(WaypadTheme.spacing.xxl),
                )
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.xl))
        if (dragLocked) {
            Button(onClick = { setDragLocked(false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Release drag")
            }
        } else {
            OutlinedButton(onClick = { setDragLocked(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Drag lock")
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.xl))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.lg),
        ) {
            ClickButton("Left", Modifier.weight(1f)) {
                actions.onPointerButton(PointerButton.Left, ButtonState.Pressed)
                actions.onPointerButton(PointerButton.Left, ButtonState.Released)
            }
            ClickButton("Right", Modifier.weight(1f)) {
                actions.onPointerButton(PointerButton.Right, ButtonState.Pressed)
                actions.onPointerButton(PointerButton.Right, ButtonState.Released)
            }
            ClickButton("Middle", Modifier.weight(1f)) {
                actions.onPointerButton(PointerButton.Middle, ButtonState.Pressed)
                actions.onPointerButton(PointerButton.Middle, ButtonState.Released)
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.xl))
    }
}

/** Debug-build-only read-out of the gesture machine and the input queue. */
@Composable
private fun RemoteInputDebugOverlay(state: RemotePadUiState, modifier: Modifier = Modifier) {
    TelemetryOverlay(modifier) {
        Text(
            state.gestureMode.label,
            style = WaypadTheme.accentTypography.telemetry,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "pointers ${state.pointerCount}",
            style = WaypadTheme.accentTypography.telemetry,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "queue ${state.inputBacklog}",
            style = WaypadTheme.accentTypography.telemetry,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            state.connectionLabel,
            style = WaypadTheme.accentTypography.telemetry,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Touchpad - idle - dark", heightDp = 760)
@Composable
private fun RemotePadScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    RemotePadScreen(
        state = RemotePadUiState(
            inputSupported = true,
            inputBackend = "wayland-portal",
            connectionLabel = "Connected",
        ),
        actions = RemotePadActions(),
    )
}

@Preview(name = "Touchpad - scrolling - light", heightDp = 760)
@Composable
private fun RemotePadScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    RemotePadScreen(
        state = RemotePadUiState(
            inputSupported = true,
            inputBackend = "wayland-portal",
            sessionActive = true,
            gestureMode = RemoteGestureMode.TwoFingerScroll,
            pointerCount = 2,
            connectionLabel = "Connected",
            controllerConnected = true,
        ),
        actions = RemotePadActions(),
    )
}
