package dev.waypad.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** One entry of a [ChoiceChipRow]. */
@Immutable
data class ChoiceOption<T>(val value: T, val label: String)

/**
 * Labelled, horizontally scrollable single-choice row.
 *
 * Material 3 Expressive's `ButtonGroup` / `ConnectedButtonGroup` are not in material3 1.4.0 (only
 * their design tokens ship), so this uses the standard `FilterChip` set instead.
 */
@Composable
fun <T> ChoiceChipRow(
    label: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = WaypadTheme.spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(WaypadTheme.spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md)) {
            items(options) { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    shape = WaypadTheme.shapes.pill,
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}

/**
 * Two-line selectable tile: a title plus a supporting line.
 *
 * Used for the stream quality profiles and for the capture source picker, where a plain chip cannot
 * carry the second line of metadata.
 */
@Composable
fun SelectableTile(
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            accent.copy(alpha = SELECTED_TILE_ALPHA)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(WaypadTheme.spacing.lg)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SELECTED_TILE_ALPHA = 0.18f

@Preview(name = "Choices - dark")
@Composable
private fun ChoicesPreviewDark() = WaypadPreviewSurface(darkTheme = true) { ChoicesPreviewBody() }

@Preview(name = "Choices - light")
@Composable
private fun ChoicesPreviewLight() = WaypadPreviewSurface(darkTheme = false) { ChoicesPreviewBody() }

@Composable
private fun ChoicesPreviewBody() {
    Column(verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xl)) {
        ChoiceChipRow(
            label = "Target FPS",
            options = listOf(
                ChoiceOption(30, "30"),
                ChoiceOption(45, "45"),
                ChoiceOption(60, "60"),
            ),
            selected = 30,
            onSelect = {},
        )
        SelectableTile(
            title = "Balanced",
            supporting = "30 fps - 1600p - Q70",
            selected = true,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        SelectableTile(
            title = "Game Mode",
            supporting = "60 fps - 1280p - Q52",
            selected = false,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
