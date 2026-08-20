package dev.waypad.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.core.model.ScreenStreamStats
import dev.waypad.android.core.model.formatFps
import dev.waypad.android.ui.components.OVERLAY_ALPHA
import dev.waypad.android.ui.components.TelemetryOverlay
import dev.waypad.android.ui.theme.WaypadOnVideoLetterboxMuted
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** Live status HUD floated over the video output. */
@Composable
fun StreamStatusOverlay(
    screenStatus: String,
    screenStreaming: Boolean,
    stats: ScreenStreamStats,
    fallbackBackend: String?,
    modifier: Modifier = Modifier,
) {
    val backend = stats.backend.takeIf { it.isNotBlank() } ?: fallbackBackend ?: "unknown"
    TelemetryOverlay(modifier) {
        Text(
            screenStatus,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (screenStreaming && (stats.deliveredFps > 0 || stats.actualFps > 0)) {
            val fpsText = if (stats.actualFps > 0 && stats.actualFps != stats.targetFps) {
                "${stats.deliveredFps.formatFps()}/${stats.actualFps} fps (asked ${stats.targetFps})"
            } else {
                "${stats.deliveredFps.formatFps()}/${stats.targetFps} fps"
            }
            Text(
                "$backend · $fpsText · ${stats.averageKib} KiB/f",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Floating control bar shown while the remote display is fullscreen.
 *
 * Material 3 Expressive's `HorizontalFloatingToolbar` is not available in material3 1.4.0, so this
 * is a plain elevated `Surface` with the expressive corner scale.
 */
@Composable
fun RemoteScreenFullscreenBar(
    status: String,
    onExit: () -> Unit,
    onReconnect: () -> Unit,
    onKeyboard: () -> Unit,
    gameMode: Boolean,
    onGameMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = OVERLAY_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = WaypadTheme.shapes.overlay,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(
                horizontal = WaypadTheme.spacing.md,
                vertical = WaypadTheme.spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit fullscreen")
            }
            Text(
                status,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = onReconnect) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reconnect")
            }
            IconButton(onClick = onKeyboard) {
                Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard")
            }
            TextButton(onClick = onGameMode) {
                Text(
                    if (gameMode) "Game: ON" else "Game: OFF",
                    color = if (gameMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen")
            }
        }
    }
}

/** Invisible strip at the top of game mode that brings the fullscreen controls back. */
@Composable
fun ControlsRevealHandle(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth(REVEAL_STRIP_WIDTH_FRACTION)
            .height(WaypadTheme.spacing.revealStripHeight)
            .clickable(onClick = onReveal),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = WaypadTheme.spacing.sm)
                .width(WaypadTheme.spacing.handleWidth)
                .height(WaypadTheme.spacing.handleHeight)
                .clip(WaypadTheme.shapes.handle)
                .background(WaypadOnVideoLetterboxMuted),
        )
    }
}

private const val REVEAL_STRIP_WIDTH_FRACTION = 0.36f

@Preview(name = "Fullscreen bar - dark")
@Composable
private fun RemoteScreenFullscreenBarPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    RemoteScreenFullscreenBar(
        status = "Live 2560x1440 · wayland-screencast-portal",
        onExit = {},
        onReconnect = {},
        onKeyboard = {},
        gameMode = true,
        onGameMode = {},
    )
}

@Preview(name = "Stream status overlay - light")
@Composable
private fun StreamStatusOverlayPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    StreamStatusOverlay(
        screenStatus = "Live 2560x1440 | wayland-screencast-portal",
        screenStreaming = true,
        stats = ScreenStreamStats(
            deliveredFps = 29.4,
            targetFps = 30,
            actualFps = 30,
            averageKib = 128,
            backend = "wayland-screencast-portal",
        ),
        fallbackBackend = null,
    )
}
