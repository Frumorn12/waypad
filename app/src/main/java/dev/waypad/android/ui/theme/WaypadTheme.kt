package dev.waypad.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Waypad's Material 3 Expressive theme.
 *
 * * Colour comes from the wallpaper on Android 12+ ([dynamicLightColorScheme] /
 *   [dynamicDarkColorScheme]); older devices fall back to [WaypadLightColorScheme] /
 *   [WaypadDarkColorScheme].
 * * Light and dark both follow the system setting by default.
 * * `MaterialExpressiveTheme` and `MotionScheme` are `internal` in material3 1.4.0 (the version the
 *   Compose BOM 2026.02.01 resolves), so the expressive layer is applied through the public
 *   `MaterialTheme` entry point plus Waypad's own expressive tokens: [WaypadShapes] for the rounder
 *   shape scale, [WaypadTypography] for the weight contrast and [WaypadMotion] for the expressive
 *   spring scheme.
 */
@Composable
fun WaypadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = remember(darkTheme, dynamicColor, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> WaypadDarkColorScheme
            else -> WaypadLightColorScheme
        }
    }
    CompositionLocalProvider(
        LocalWaypadSpacing provides WaypadSpacing(),
        LocalWaypadExtraShapes provides WaypadExtraShapes(),
        LocalWaypadAccentTypography provides WaypadAccentTypography(),
        LocalWaypadMotion provides WaypadMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = WaypadShapes,
            typography = WaypadTypography,
            content = content,
        )
    }
}

/** Accessors for the Waypad design tokens that Material 3 does not model publicly. */
object WaypadTheme {
    val spacing: WaypadSpacing
        @Composable @ReadOnlyComposable get() = LocalWaypadSpacing.current

    val shapes: WaypadExtraShapes
        @Composable @ReadOnlyComposable get() = LocalWaypadExtraShapes.current

    val accentTypography: WaypadAccentTypography
        @Composable @ReadOnlyComposable get() = LocalWaypadAccentTypography.current

    val motion: WaypadMotion
        @Composable @ReadOnlyComposable get() = LocalWaypadMotion.current
}
