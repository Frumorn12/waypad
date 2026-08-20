package dev.waypad.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/**
 * Oversized tonal button used for the mouse-button row under the touchpad.
 *
 * Material 3 Expressive's shape-morphing button API (`ButtonShapes` / `MaterialShapes`) is not part
 * of material3 1.4.0, so the press morph is reproduced by animating the corner radius with the
 * expressive spring scheme in [WaypadTheme.motion].
 */
@Composable
fun ClickButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) WaypadTheme.spacing.xl else WaypadTheme.spacing.section,
        animationSpec = WaypadTheme.motion.fastSpatial<Dp>(),
        label = "click-button-corner",
    )
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(WaypadTheme.spacing.clickButtonHeight),
        shape = RoundedCornerShape(corner),
        interactionSource = interactionSource,
    ) {
        Text(label)
    }
}

/** Equal-width outlined action inside a [ControlGroup] row. */
@Composable
fun RowScope.ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
}

/** Titled card grouping related host actions, badged with host support for that group. */
@Composable
fun ControlGroup(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    WaypadCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            CapabilityPill(capabilityLabel(enabled), available = enabled)
        }
        Spacer(Modifier.height(WaypadTheme.spacing.xl))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
        ) {
            content()
        }
    }
}

/** Two-column grid of keyboard shortcut buttons. */
@Composable
fun ShortcutGrid(items: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    WaypadCard(modifier) {
        items.chunked(SHORTCUT_COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md),
            ) {
                row.forEach { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(WaypadTheme.spacing.md))
        }
    }
}

private const val SHORTCUT_COLUMNS = 2

@Preview(name = "Buttons - dark")
@Composable
private fun ButtonsPreviewDark() = WaypadPreviewSurface(darkTheme = true) { ButtonsPreviewBody() }

@Preview(name = "Buttons - light")
@Composable
private fun ButtonsPreviewLight() = WaypadPreviewSurface(darkTheme = false) { ButtonsPreviewBody() }

@Composable
private fun ButtonsPreviewBody() {
    Column(verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xl)) {
        Row(horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.lg)) {
            ClickButton("Left", Modifier.weight(1f)) {}
            ClickButton("Right", Modifier.weight(1f)) {}
            ClickButton("Middle", Modifier.weight(1f)) {}
        }
        ControlGroup("Media", enabled = true) {
            ActionButton("Play/Pause") {}
            ActionButton("Previous") {}
            ActionButton("Next") {}
        }
        ShortcutGrid(listOf("Copy" to {}, "Paste" to {}, "Undo" to {}))
    }
}
