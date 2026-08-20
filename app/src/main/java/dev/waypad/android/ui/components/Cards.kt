package dev.waypad.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.core.model.TrustedHost
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** The one card style used across the app: tonal container, hairline outline, extra-large corner. */
@Composable
fun WaypadCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(WaypadTheme.spacing.borderWidth, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(WaypadTheme.spacing.gutter), content = content)
    }
    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = inner,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = inner,
        )
    }
}

/** A host found on the LAN (or built from an invite), tappable to start pairing. */
@Composable
fun DiscoveredHostCard(
    host: DiscoveredHost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaypadCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(WaypadTheme.spacing.xl))
            Column(Modifier.weight(1f)) {
                Text(host.hostName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${host.address}:${host.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    shortFingerprint(host.fingerprint),
                    style = WaypadTheme.accentTypography.fingerprint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                when {
                    host.inputSupported && host.captureSupported -> "Control + screen"
                    host.inputSupported -> host.inputBackend
                    host.captureSupported -> host.captureBackend
                    else -> "Limited"
                }
            )
        }
    }
}

/** A pinned host stored in the trusted-host store. */
@Composable
fun TrustedHostCard(
    host: TrustedHost,
    onConnect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaypadCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(WaypadTheme.spacing.xl))
            Column(Modifier.weight(1f)) {
                Text(host.hostName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${host.address}:${host.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    shortFingerprint(host.fingerprint),
                    style = WaypadTheme.accentTypography.fingerprint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(WaypadTheme.spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.md)) {
            FilledTonalButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
            OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remove") }
        }
    }
}

/** Collapses a long SHA fingerprint so it stays on one line. */
fun shortFingerprint(value: String): String =
    if (value.length > 31) value.take(19) + "..." + value.takeLast(9) else value

@Preview(name = "Cards - dark")
@Composable
private fun WaypadCardsPreviewDark() = WaypadPreviewSurface(darkTheme = true) { CardsPreviewBody() }

@Preview(name = "Cards - light")
@Composable
private fun WaypadCardsPreviewLight() = WaypadPreviewSurface(darkTheme = false) { CardsPreviewBody() }

@Composable
private fun CardsPreviewBody() {
    Column(verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xl)) {
        DiscoveredHostCard(
            host = DiscoveredHost(
                hostName = "frumorn-desktop",
                address = "192.168.1.42",
                port = 47771,
                fingerprint = "SHA256:7f1a3c9d20e4b8a6f0c1d2e3f4a5b6c7d8e9f0a1",
                inputSupported = true,
                inputBackend = "wayland-portal",
                captureSupported = true,
                captureBackend = "wayland-screencast-portal",
            ),
            onClick = {},
        )
        TrustedHostCard(
            host = TrustedHost(
                id = "host-1",
                hostName = "frumorn-laptop",
                address = "192.168.1.77",
                port = 47771,
                fingerprint = "SHA256:aa11bb22cc33dd44ee55ff66aa77bb88cc99dd00",
                deviceId = "device",
                sessionToken = "token",
                lastConnectedAt = 0L,
            ),
            onConnect = {},
            onRemove = {},
        )
    }
}
