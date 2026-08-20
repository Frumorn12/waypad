@file:OptIn(ExperimentalMaterial3Api::class)

package dev.waypad.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.R
import dev.waypad.android.ui.state.ShellUiState
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

/** Screen title + one-line explanation, repeated at the top of every content screen. */
@Composable
fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = WaypadTheme.spacing.md)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(WaypadTheme.spacing.xs))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The Waypad logotype cut-out. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(WaypadTheme.spacing.brandMarkSize)
            .clip(MaterialTheme.shapes.small),
    ) {
        Image(
            painter = painterResource(R.drawable.waypad_brand_cutout),
            contentDescription = "Waypad",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * App bar showing the brand, the connected host (or the current status line) and the settings entry
 * point, with the global error strip docked underneath.
 *
 * The Material 3 Expressive `TopAppBar` overload with a dedicated `subtitle` slot exists in
 * material3 1.4.0 but is `internal`, so the second line is rendered inside the `title` slot.
 */
@Composable
fun WaypadTopBar(
    state: ShellUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "WAYPAD",
                        style = WaypadTheme.accentTypography.brand,
                    )
                    Text(
                        state.hostName ?: state.status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = { BrandMark(Modifier.padding(start = WaypadTheme.spacing.xl)) },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        AnimatedErrorBanner(
            message = state.error,
            modifier = Modifier.padding(horizontal = WaypadTheme.spacing.gutter),
        )
    }
}

@Preview(name = "Top bar - dark")
@Composable
private fun WaypadTopBarPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    Column {
        WaypadTopBar(ShellUiState(hostName = "frumorn-desktop", status = "Connected"), onOpenSettings = {})
        SectionHeader("Host Discovery", "UDP LAN discovery with manual IP and QR invite fallback.")
    }
}

@Preview(name = "Top bar - error - light")
@Composable
private fun WaypadTopBarPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    WaypadTopBar(
        state = ShellUiState(status = "Pairing failed", error = "Connection refused"),
        onOpenSettings = {},
    )
}
