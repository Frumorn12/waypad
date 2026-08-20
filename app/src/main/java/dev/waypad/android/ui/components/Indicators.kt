package dev.waypad.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** Small capability/state badge. */
@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = WaypadTheme.shapes.pill,
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = WaypadTheme.spacing.lg,
                vertical = WaypadTheme.spacing.sm,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Positive/negative variant of [StatusPill] driven by a capability flag. */
@Composable
fun CapabilityPill(label: String, available: Boolean, modifier: Modifier = Modifier) {
    StatusPill(
        label = label,
        modifier = modifier,
        container = if (available) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        content = if (available) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** Inline error strip shown under the top bar and under the remote viewport. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(WaypadTheme.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(WaypadTheme.spacing.lg))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** [ErrorBanner] that fades itself in and out when [message] appears/disappears. */
@Composable
fun AnimatedErrorBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        ErrorBanner(message ?: "")
    }
}

/** `label ......... value` row used by the diagnostics screen. */
@Composable
fun DiagnosticRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = WaypadTheme.spacing.xs),
    ) {
        Text(
            label,
            modifier = Modifier.weight(DIAGNOSTIC_LABEL_WEIGHT),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(DIAGNOSTIC_VALUE_WEIGHT),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val DIAGNOSTIC_LABEL_WEIGHT = 0.38f
private const val DIAGNOSTIC_VALUE_WEIGHT = 0.62f

/** Translucent read-out floated on top of the remote video output. */
@Composable
fun TelemetryOverlay(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = OVERLAY_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = WaypadTheme.shapes.overlayCompact,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(
                horizontal = WaypadTheme.spacing.lg,
                vertical = WaypadTheme.spacing.sm,
            ),
            content = content,
        )
    }
}

internal const val OVERLAY_ALPHA = 0.86f

/** Shared "available"/"unsupported" wording for capability read-outs. */
fun capabilityLabel(value: Boolean): String = if (value) "available" else "unsupported"

@Preview(name = "Indicators - dark")
@Composable
private fun IndicatorsPreviewDark() = WaypadPreviewSurface(darkTheme = true) { IndicatorsPreviewBody() }

@Preview(name = "Indicators - light")
@Composable
private fun IndicatorsPreviewLight() = WaypadPreviewSurface(darkTheme = false) { IndicatorsPreviewBody() }

@Composable
private fun IndicatorsPreviewBody() {
    Column(verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xl)) {
        Row(horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md)) {
            CapabilityPill("Input ready", available = true)
            CapabilityPill("Capture blocked", available = false)
        }
        StatusPill("Controller connected")
        ErrorBanner("Screen stream dropped; retrying...")
        DiagnosticRow("Input backend", "wayland-portal")
        DiagnosticRow("Controller forwarding", capabilityLabel(false))
        TelemetryOverlay {
            Text("Live 2560x1440", style = MaterialTheme.typography.labelSmall)
            Text("30/30 fps", style = MaterialTheme.typography.labelSmall)
        }
    }
}
