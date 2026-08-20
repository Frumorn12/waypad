package dev.waypad.android.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared scaffold for `@Preview` functions.
 *
 * Dynamic colour is switched off so previews render the deterministic fallback palette instead of
 * whatever wallpaper the preview device reports.
 */
@Composable
fun WaypadPreviewSurface(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    WaypadTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(WaypadTheme.spacing.gutter),
            ) {
                content()
            }
        }
    }
}
