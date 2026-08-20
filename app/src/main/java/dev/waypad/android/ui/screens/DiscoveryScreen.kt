package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.ui.WaypadQrScannerOverlay
import dev.waypad.android.ui.components.DiscoveredHostCard
import dev.waypad.android.ui.components.SectionHeader
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.DiscoveryActions
import dev.waypad.android.ui.state.DiscoveryUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

private const val INVITE_PREFIX = "waypad://invite"

/** LAN scan results, QR invite entry and the manual IP fallback. */
@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    actions: DiscoveryActions,
    modifier: Modifier = Modifier,
) {
    var inviteText by remember { mutableStateOf("") }
    var scannerOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xxl)) {
            item {
                SectionHeader(
                    "Host Discovery",
                    "UDP LAN discovery with manual IP and QR invite fallback.",
                )
                Button(onClick = actions.onScanLan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(WaypadTheme.spacing.md))
                    Text("Scan LAN")
                }
            }
            items(state.discoveredHosts) { host ->
                DiscoveredHostCard(host = host, onClick = { actions.onSelectHost(host) })
            }
            item {
                WaypadCard {
                    Text("QR invite", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan a terminal QR in-app. Invites can carry LAN and remote-direct endpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(WaypadTheme.spacing.lg))
                    Button(
                        onClick = { scannerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(WaypadTheme.spacing.md))
                        Text("Scan QR invite")
                    }
                    Spacer(Modifier.height(WaypadTheme.spacing.lg))
                    OutlinedTextField(
                        value = inviteText,
                        onValueChange = { inviteText = it.trim() },
                        label = { Text("waypad://invite...") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(WaypadTheme.spacing.md))
                    Button(
                        onClick = { actions.onApplyInvite(inviteText) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = inviteText.startsWith(INVITE_PREFIX),
                    ) {
                        Text("Use pasted invite")
                    }
                }
            }
            item {
                WaypadCard {
                    Text("Manual connect", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(WaypadTheme.spacing.lg))
                    OutlinedTextField(
                        value = state.manualAddress,
                        onValueChange = actions.onManualAddressChange,
                        label = { Text("Host IP address") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(WaypadTheme.spacing.md))
                    OutlinedTextField(
                        value = state.manualPort,
                        onValueChange = actions.onManualPortChange,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(WaypadTheme.spacing.xl))
                    Button(onClick = actions.onUseManualHost, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                }
            }
        }
        if (scannerOpen) {
            WaypadQrScannerOverlay(
                onInviteScanned = { raw ->
                    scannerOpen = false
                    inviteText = raw
                    actions.onApplyInvite(raw)
                },
                onDismiss = { scannerOpen = false },
            )
        }
    }
}

@Preview(name = "Discovery - dark", heightDp = 900)
@Composable
private fun DiscoveryScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    DiscoveryScreen(previewDiscoveryState, DiscoveryActions())
}

@Preview(name = "Discovery - light", heightDp = 900)
@Composable
private fun DiscoveryScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    DiscoveryScreen(previewDiscoveryState, DiscoveryActions())
}

private val previewDiscoveryState = DiscoveryUiState(
    discoveredHosts = listOf(
        DiscoveredHost(
            hostName = "frumorn-desktop",
            address = "192.168.1.42",
            port = 47771,
            fingerprint = "SHA256:7f1a3c9d20e4b8a6f0c1d2e3f4a5b6c7d8e9f0a1",
            inputSupported = true,
            inputBackend = "wayland-portal",
            captureSupported = true,
            captureBackend = "wayland-screencast-portal",
        ),
    ),
    manualAddress = "192.168.1.50",
    manualPort = "47771",
)
