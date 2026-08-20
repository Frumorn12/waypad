package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.core.model.StreamProfile
import dev.waypad.android.ui.components.ChoiceChipRow
import dev.waypad.android.ui.components.ChoiceOption
import dev.waypad.android.ui.components.SectionHeader
import dev.waypad.android.ui.components.SelectableTile
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.SettingsActions
import dev.waypad.android.ui.state.SettingsUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

private const val PROFILE_COLUMNS = 2

private val FpsOptions = listOf(
    ChoiceOption(30, "30"),
    ChoiceOption(45, "45"),
    ChoiceOption(60, "60"),
)

private val ResolutionOptions = listOf(
    ChoiceOption(1280, "720p-1280"),
    ChoiceOption(1600, "1080p-1600"),
    ChoiceOption(2400, "1440p-2400"),
    ChoiceOption(3840, "4K-3840"),
)

private val QualityOptions = listOf(
    ChoiceOption(35, "Low 35"),
    ChoiceOption(52, "Balanced 52"),
    ChoiceOption(70, "Good 70"),
    ChoiceOption(86, "High 86"),
    ChoiceOption(92, "Best 92"),
)

/** Stream quality, input feel and connection management. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xxl),
    ) {
        item { SectionHeader("Settings", "Stream quality, input feel, and app preferences.") }

        item {
            WaypadCard {
                Text("Stream Performance", style = MaterialTheme.typography.titleMedium)
                Text(
                    "These apply the next time you start a stream. Changing them does not affect a " +
                        "live stream until you restart it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(WaypadTheme.spacing.xl))

                Text("Profile", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(WaypadTheme.spacing.sm))
                StreamProfile.entries.chunked(PROFILE_COLUMNS).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
                    ) {
                        row.forEach { profile ->
                            SelectableTile(
                                title = profile.label,
                                supporting = "${profile.defaultFps} fps · ${profile.defaultMaxDimension}p · Q${profile.defaultQuality}",
                                selected = state.streamSettings.profile == profile,
                                onClick = { actions.onSelectProfile(profile) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(WaypadTheme.spacing.md))
                }

                ChoiceChipRow(
                    label = "Target FPS",
                    options = FpsOptions,
                    selected = state.streamSettings.maxFps,
                    onSelect = actions.onSetMaxFps,
                )
                ChoiceChipRow(
                    label = "Max Resolution",
                    options = ResolutionOptions,
                    selected = state.streamSettings.maxDimension,
                    onSelect = actions.onSetMaxDimension,
                )
                ChoiceChipRow(
                    label = "JPEG Quality",
                    options = QualityOptions,
                    selected = state.streamSettings.jpegQuality,
                    onSelect = actions.onSetJpegQuality,
                )

                Spacer(Modifier.height(WaypadTheme.spacing.md))
                SettingSwitchRow(
                    title = "Show live stats overlay",
                    supporting = "FPS, backend, and bitrate on the stream screen.",
                    checked = state.streamSettings.showStats,
                    onCheckedChange = { actions.onToggleStats() },
                )
            }
        }

        item {
            WaypadCard {
                Text("Input Feel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                SettingSwitchRow(
                    title = "Haptic feedback",
                    supporting = "Vibration on taps and drag lock.",
                    checked = state.haptics,
                    onCheckedChange = { actions.onToggleHaptics() },
                )
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                SettingSwitchRow(
                    title = "Game Mode",
                    supporting = "Fullscreen, 60 fps, hidden UI, controller-forwarding active.",
                    checked = state.gameMode,
                    onCheckedChange = actions.onSetGameMode,
                )
            }
        }

        item {
            WaypadCard {
                Text("Connection", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                Button(onClick = actions.onOpenTrustedHosts, modifier = Modifier.fillMaxWidth()) {
                    Text("Trusted hosts")
                }
                Spacer(Modifier.height(WaypadTheme.spacing.md))
                OutlinedButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(name = "Settings - dark", heightDp = 1400)
@Composable
private fun SettingsScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    SettingsScreen(SettingsUiState(), SettingsActions())
}

@Preview(name = "Settings - light", heightDp = 1400)
@Composable
private fun SettingsScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    SettingsScreen(SettingsUiState(gameMode = true), SettingsActions())
}
