package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.core.model.ScreenSource
import dev.waypad.android.ui.components.SelectableTile
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.RemoteDisplayActions
import dev.waypad.android.ui.state.RemoteDisplayUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** Colour role each capture backend is rendered with, so the user can tell them apart at a glance. */
@Composable
internal fun backendAccent(backend: String): Color = when (backend) {
    "x11-ffmpeg" -> MaterialTheme.colorScheme.primary
    "wayland-screencast-portal" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/** Capture source picker, stream start/stop, and the entry points into fullscreen and game mode. */
@Composable
fun StreamSetupCard(
    state: RemoteDisplayUiState,
    actions: RemoteDisplayActions,
    modifier: Modifier = Modifier,
) {
    WaypadCard(modifier) {
        Text("Stream Setup", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(WaypadTheme.spacing.md))
        if (state.screenSources.isNotEmpty()) {
            Text("Source", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(WaypadTheme.spacing.xs))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.screenSources) { source ->
                    SelectableTile(
                        title = source.label.ifBlank { source.id },
                        supporting = source.backend,
                        selected = source.id == state.selectedScreenSourceId,
                        onClick = { actions.onSelectSource(source.id) },
                        accent = backendAccent(source.backend),
                    )
                }
            }
            Spacer(Modifier.height(WaypadTheme.spacing.lg))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
        ) {
            Button(
                onClick = actions.onStartStream,
                modifier = Modifier.weight(1f),
                enabled = !state.screenStreaming,
            ) {
                Icon(Icons.Rounded.Computer, contentDescription = null)
                Spacer(Modifier.width(WaypadTheme.spacing.sm))
                Text("Start Stream")
            }
            OutlinedButton(
                onClick = actions.onStopStream,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
        ) {
            OutlinedButton(
                onClick = actions.onRefreshSources,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(WaypadTheme.spacing.sm))
                Text("Refresh sources")
            }
            OutlinedButton(
                onClick = { actions.onSetFullscreen(true) },
                modifier = Modifier.width(WaypadTheme.spacing.compactActionWidth),
            ) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen")
                Spacer(Modifier.width(WaypadTheme.spacing.sm))
                Text("Full")
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Game Mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    "60 fps, hidden controls, controller-forwarding.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { actions.onSetGameMode(true) },
                enabled = !state.gameMode,
            ) {
                Text("Enter")
            }
        }
    }
}

@Preview(name = "Stream setup - dark")
@Composable
private fun StreamSetupCardPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    StreamSetupCard(previewStreamSetupState, RemoteDisplayActions())
}

@Preview(name = "Stream setup - light")
@Composable
private fun StreamSetupCardPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    StreamSetupCard(previewStreamSetupState, RemoteDisplayActions())
}

internal val previewStreamSetupState = RemoteDisplayUiState(
    captureSupported = true,
    inputSupported = true,
    screenSources = listOf(
        ScreenSource(
            id = "DP-1",
            label = "DP-1 2560x1440",
            kind = "monitor",
            backend = "wayland-screencast-portal",
            width = 2560,
            height = 1440,
            x = 0,
            y = 0,
            scale = 1.0,
            focused = true,
        ),
        ScreenSource(
            id = "HDMI-A-1",
            label = "HDMI-A-1 1920x1080",
            kind = "monitor",
            backend = "x11-ffmpeg",
            width = 1920,
            height = 1080,
            x = 2560,
            y = 0,
            scale = 1.0,
            focused = false,
        ),
    ),
    selectedScreenSourceId = "DP-1",
)
