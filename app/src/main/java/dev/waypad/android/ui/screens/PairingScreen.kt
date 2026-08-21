package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.components.SectionHeader
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.PairingActions
import dev.waypad.android.ui.state.PairingUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

private const val MANUAL_HOST_FINGERPRINT_HINT =
    "Manual host: compare fingerprint printed by the daemon."

/** Six-digit pairing exchange with the fingerprint the user has to compare. */
@Composable
fun PairingScreen(
    state: PairingUiState,
    actions: PairingActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        SectionHeader(
            "Pair ${state.hostName ?: "host"}",
            "Open the Waypad panel on your PC, or run `waypad-daemon pair-code`, then enter the code here.",
        )
        WaypadCard {
            Text(
                "Host fingerprint",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                state.fingerprint?.ifBlank { MANUAL_HOST_FINGERPRINT_HINT } ?: "",
                style = WaypadTheme.accentTypography.fingerprint,
            )
            Spacer(Modifier.height(WaypadTheme.spacing.xxl))
            OutlinedTextField(
                value = state.pairingCode,
                onValueChange = actions.onPairingCodeChange,
                label = { Text("6 digit pairing code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WaypadTheme.spacing.xxl))
            Button(onClick = actions.onPair, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Shield, contentDescription = null)
                Spacer(Modifier.width(WaypadTheme.spacing.md))
                Text("Pair securely")
            }
        }
    }
}

@Preview(name = "Pairing - dark", heightDp = 640)
@Composable
private fun PairingScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    PairingScreen(previewPairingState, PairingActions())
}

@Preview(name = "Pairing - light", heightDp = 640)
@Composable
private fun PairingScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    PairingScreen(previewPairingState, PairingActions())
}

private val previewPairingState = PairingUiState(
    hostName = "frumorn-desktop",
    fingerprint = "SHA256:7f1a3c9d20e4b8a6f0c1d2e3f4a5b6c7d8e9f0a1",
    pairingCode = "042",
)
