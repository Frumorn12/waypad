package dev.waypad.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import dev.waypad.android.ui.components.AnimatedErrorBanner
import dev.waypad.android.ui.components.CapabilityPill
import dev.waypad.android.ui.components.OVERLAY_ALPHA
import dev.waypad.android.ui.state.RemoteDisplayActions
import dev.waypad.android.ui.state.RemoteDisplayUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme
import dev.waypad.android.ui.theme.WaypadVideoLetterbox
import kotlinx.coroutines.delay
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.PointerButton

/** How long the game-mode controls stay on screen before hiding themselves again. */
private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L

/** Opacity of the streaming outline around the windowed viewport. */
private const val STREAMING_BORDER_ALPHA = 0.65f

/**
 * Remote desktop viewport: capture setup, the video output and the absolute-pointer gestures.
 *
 * The video output itself is a slot ([videoSurface]) so the rendering backend can be swapped -
 * see [RemoteDisplayVideoSurface] for the contract it has to honour. Everything around it (layout,
 * overlays, gesture mapping) stays put.
 */
@Composable
fun RemoteDisplayScreen(
    state: RemoteDisplayUiState,
    actions: RemoteDisplayActions,
    modifier: Modifier = Modifier,
    // Default is empty so previews render without a decoder; the real surface is supplied by
    // the app shell, which is the only place that can reach the session's renderer.
    videoSurface: @Composable () -> Unit = {},
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var keyboardText by remember { mutableStateOf("") }
    var quickKeyboardVisible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val tapSlop = with(LocalDensity.current) { WaypadTheme.spacing.tapSlop.toPx() }
    val fullscreen = state.fullscreen
    val gameMode = state.gameMode
    val showFullscreenControls = fullscreen && (!gameMode || state.controlsVisible)
    val activeSource = state.activeSource
    val gestureCallbacks = remember(actions) {
        RemoteDisplayGestureCallbacks(
            onDesktopPointerMove = actions.onDesktopPointerMove,
            onDesktopClick = actions.onDesktopClick,
            onPointerButton = actions.onPointerButton,
            onScroll = actions.onScroll,
        )
    }

    LaunchedEffect(fullscreen, gameMode, state.controlsVisible) {
        if (fullscreen && gameMode && state.controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            actions.onSetControlsVisible(false, "auto_hide")
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(if (fullscreen) WaypadVideoLetterbox else Color.Transparent)
    ) {
        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
            ) {
                CapabilityPill(
                    label = if (state.captureSupported) "Capture ready" else "Capture blocked",
                    available = state.captureSupported,
                )
                CapabilityPill(
                    label = if (state.inputSupported) "Input ready" else "Input blocked",
                    available = state.inputSupported,
                )
            }
            Spacer(Modifier.height(WaypadTheme.spacing.lg))
            StreamSetupCard(state = state, actions = actions)
            Spacer(Modifier.height(WaypadTheme.spacing.lg))
        }
        val viewportShape = if (fullscreen) {
            WaypadTheme.shapes.viewportFullscreen
        } else {
            WaypadTheme.shapes.viewport
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(viewportShape)
                .background(WaypadVideoLetterbox)
                .border(
                    width = if (fullscreen) {
                        WaypadTheme.spacing.none
                    } else {
                        WaypadTheme.spacing.borderWidth
                    },
                    color = if (state.screenStreaming) {
                        MaterialTheme.colorScheme.primary.copy(alpha = STREAMING_BORDER_ALPHA)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = viewportShape,
                )
                .onSizeChanged { viewSize = it }
                .remoteDisplayGestures(
                    frameWidth = state.frameWidth,
                    frameHeight = state.frameHeight,
                    viewSize = viewSize,
                    sourceKey = activeSource?.id,
                    tapSlopPx = tapSlop,
                    hapticsEnabled = state.haptics,
                    haptics = haptics,
                    callbacks = gestureCallbacks,
                ),
            // Children without an explicit `align` are centred, so a self-measuring
            // `AndroidView { WaypadVideoView(...) }` can be dropped into `videoSurface`
            // without any sizing modifier. Every overlay below aligns itself explicitly.
            contentAlignment = Alignment.Center,
        ) {
            videoSurface()
            if (state.frameWidth == null) {
                RemoteDisplayPlaceholder(
                    statusText = state.screenStatus,
                    sourceLabel = activeSource?.label,
                )
            }
            if (!fullscreen || !gameMode || state.controlsVisible) {
                StreamStatusOverlay(
                    screenStatus = state.screenStatus,
                    screenStreaming = state.screenStreaming,
                    stats = state.stats,
                    fallbackBackend = state.streamingSource?.backend,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(WaypadTheme.spacing.lg),
                )
            }
            if (showFullscreenControls) {
                RemoteScreenFullscreenBar(
                    status = state.screenStatus,
                    onExit = { actions.onSetFullscreen(false) },
                    onReconnect = actions.onStartStream,
                    onKeyboard = { quickKeyboardVisible = !quickKeyboardVisible },
                    gameMode = gameMode,
                    onGameMode = { actions.onSetGameMode(!gameMode) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = WaypadTheme.spacing.lg,
                            start = WaypadTheme.spacing.lg,
                            end = WaypadTheme.spacing.lg,
                        ),
                )
            } else if (fullscreen && gameMode) {
                ControlsRevealHandle(
                    onReveal = { actions.onRevealControls("touch_top_handle") },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (fullscreen && state.controlsVisible && quickKeyboardVisible) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(WaypadTheme.spacing.xxl),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = OVERLAY_ALPHA),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = WaypadTheme.shapes.overlay,
                    ) {
                        OutlinedTextField(
                            value = keyboardText,
                            onValueChange = { next ->
                                actions.onKeyboardEdit(keyboardText, next)
                                keyboardText = next
                            },
                            label = { Text("Quick text") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(WaypadTheme.spacing.lg),
                        )
                    }
                }
            }
        }
        if (!fullscreen) {
            AnimatedErrorBanner(state.screenError)
            Spacer(Modifier.height(WaypadTheme.spacing.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
            ) {
                OutlinedButton(
                    onClick = {
                        actions.onPointerButton(PointerButton.Right, ButtonState.Pressed)
                        actions.onPointerButton(PointerButton.Right, ButtonState.Released)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Right click")
                }
                OutlinedButton(
                    onClick = { quickKeyboardVisible = !quickKeyboardVisible },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Quick keys")
                }
            }
            AnimatedVisibility(quickKeyboardVisible) {
                OutlinedTextField(
                    value = keyboardText,
                    onValueChange = { next ->
                        actions.onKeyboardEdit(keyboardText, next)
                        keyboardText = next
                    },
                    label = { Text("Quick text input") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(WaypadTheme.spacing.xl))
        }
    }
}

@Preview(name = "Remote display - windowed - dark", heightDp = 900)
@Composable
private fun RemoteDisplayScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    RemoteDisplayScreen(
        state = previewStreamSetupState.copy(screenStatus = "Screen stream idle"),
        actions = RemoteDisplayActions(),
    )
}

@Preview(name = "Remote display - windowed - light", heightDp = 900)
@Composable
private fun RemoteDisplayScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    RemoteDisplayScreen(
        state = previewStreamSetupState.copy(
            screenStreaming = true,
            screenStatus = "Live 2560x1440 · wayland-screencast-portal",
            screenError = "Screen stream dropped; retrying...",
        ),
        actions = RemoteDisplayActions(),
    )
}
