package dev.waypad.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.waypad.android.core.model.ScreenSource
import dev.waypad.android.ui.components.StatusPill
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.RemoteDisplayActions
import dev.waypad.android.ui.state.RemoteDisplayUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** Colour role each capture backend is rendered with, so the user can tell them apart at a glance. */
@Composable
internal fun backendAccent(backend: String): Color = when (backend) {
    "wayland-screencast-portal" -> MaterialTheme.colorScheme.primary
    "x11-ffmpeg" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/**
 * Starting a stream, and the two doors into it.
 *
 * The card leads with what people came to do rather than with configuration: one button starts
 * sharing, a second one goes straight into game mode, and the source picker stays folded away
 * because the right answer is already selected. Opening it is for the rare case where the
 * recommended source does not work.
 */
@Composable
fun StreamSetupCard(
    state: RemoteDisplayUiState,
    actions: RemoteDisplayActions,
    modifier: Modifier = Modifier,
) {
    var sourcesExpanded by remember { mutableStateOf(false) }
    val groups = remember(state.screenSources) { groupSources(state.screenSources) }
    val selectedId = state.selectedScreenSourceId ?: defaultSourceId(state.screenSources)
    val selected = state.screenSources.firstOrNull { it.id == selectedId }

    WaypadCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Screen sharing", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.screenStreaming) "Streaming now" else "Your desktop, on this phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.screenStreaming) StatusPill("Live")
        }

        Spacer(Modifier.height(WaypadTheme.spacing.gutter))

        Button(
            onClick = if (state.screenStreaming) actions.onStopStream else actions.onStartStream,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.captureSupported,
        ) {
            Icon(
                if (state.screenStreaming) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(WaypadTheme.spacing.md))
            Text(if (state.screenStreaming) "Stop sharing" else "Start sharing")
        }

        Spacer(Modifier.height(WaypadTheme.spacing.md))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
        ) {
            FilledTonalButton(
                // One press is the whole intent: share, at full rate, already in fullscreen.
                onClick = {
                    if (!state.screenStreaming) actions.onStartStream()
                    actions.onSetGameMode(true)
                    actions.onSetFullscreen(true)
                },
                enabled = state.captureSupported,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.SportsEsports, contentDescription = null)
                Spacer(Modifier.width(WaypadTheme.spacing.sm))
                Text("Game mode")
            }
            OutlinedButton(
                onClick = { actions.onSetFullscreen(true) },
                enabled = state.screenStreaming,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = null)
                Spacer(Modifier.width(WaypadTheme.spacing.sm))
                Text("Fullscreen")
            }
        }

        if (state.screenSources.isNotEmpty()) {
            Spacer(Modifier.height(WaypadTheme.spacing.gutter))
            SourceSummary(
                summary = selected?.let(::presentSource),
                expanded = sourcesExpanded,
                onToggle = { sourcesExpanded = !sourcesExpanded },
            )
            AnimatedVisibility(sourcesExpanded) {
                // Bounded and scrollable: the card sits above the preview in a column that does
                // not scroll, so an unbounded list would push the last sources off the screen.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = EXPANDED_SOURCES_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                ) {
                    groups.forEach { (group, items) ->
                        Spacer(Modifier.height(WaypadTheme.spacing.lg))
                        Text(
                            group.title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            group.caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(WaypadTheme.spacing.sm))
                        items.forEach { item ->
                            SourceRow(
                                item = item,
                                selected = item.id == selectedId,
                                onClick = { actions.onSelectSource(item.id) },
                            )
                            Spacer(Modifier.height(WaypadTheme.spacing.xs))
                        }
                    }
                    TextButton(onClick = actions.onRefreshSources) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(WaypadTheme.spacing.sm))
                        Text("Look for sources again")
                    }
                }
            }
        }
    }
}

/** The folded source picker: what is selected, and a way in. */
@Composable
private fun SourceSummary(
    summary: SourcePresentation?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(WaypadTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (summary?.fast == false) Icons.Rounded.WarningAmber else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = if (summary?.fast == false) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(WaypadTheme.spacing.section),
            )
            Spacer(Modifier.width(WaypadTheme.spacing.gutter))
            Column(Modifier.weight(1f)) {
                Text(
                    summary?.title ?: "Choose a source",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    summary?.detail ?: "Nothing selected yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Hide sources" else "Show sources",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One source in the expanded picker. */
@Composable
private fun SourceRow(
    item: SourcePresentation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(WaypadTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val EXPANDED_SOURCES_MAX_HEIGHT = 260.dp

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

@Preview(name = "Stream setup - streaming")
@Composable
private fun StreamSetupCardPreviewStreaming() = WaypadPreviewSurface(darkTheme = true) {
    StreamSetupCard(
        previewStreamSetupState.copy(screenStreaming = true),
        RemoteDisplayActions(),
    )
}

internal val previewStreamSetupState = RemoteDisplayUiState(
    captureSupported = true,
    inputSupported = true,
    screenSources = listOf(
        ScreenSource(
            id = "portal:chooser",
            label = "Portal picker (PipeWire screencast — 30–60 FPS)",
            kind = "portal",
            backend = "wayland-screencast-portal",
            width = 0,
            height = 0,
            x = 0,
            y = 0,
            scale = 1.0,
            focused = true,
        ),
        ScreenSource(
            id = "hyprland:monitor:eDP-1",
            label = "eDP-1 (BOE 0x0BCA)",
            kind = "monitor",
            backend = "hyprland-grim",
            width = 1920,
            height = 1080,
            x = 1920,
            y = 0,
            scale = 1.0,
            focused = false,
        ),
        ScreenSource(
            id = "x11:HDMI-A-1",
            label = "HDMI-A-1 (X11 – 60 FPS, no approval)",
            kind = "monitor",
            backend = "x11-ffmpeg",
            width = 1920,
            height = 1080,
            x = 0,
            y = 0,
            scale = 1.0,
            focused = false,
        ),
    ),
    selectedScreenSourceId = "portal:chooser",
)
