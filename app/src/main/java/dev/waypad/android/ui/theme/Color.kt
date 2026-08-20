package dev.waypad.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Letterbox behind the remote desktop video.
 *
 * Deliberately theme independent: a video viewport is black in light mode too, so the overlays drawn
 * on top of it need fixed content colours rather than the scheme's `onSurface` roles.
 */
val WaypadVideoLetterbox: Color = Color(0xFF000000)
val WaypadOnVideoLetterbox: Color = Color(0xFFE8E8E4)
val WaypadOnVideoLetterboxMuted: Color = Color(0xFFA9ADA5)

/**
 * Static fallback palette used when dynamic color is unavailable (API < 31) or explicitly disabled.
 *
 * It is a complete Material 3 tonal mapping seeded on the historical Waypad lime accent, so the app
 * keeps a recognisable identity on devices that cannot extract a wallpaper palette, while still
 * satisfying M3 contrast pairings for every role.
 */
internal val WaypadLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F6900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F35E),
    onPrimaryContainer = Color(0xFF2E4E00),
    inversePrimary = Color(0xFF9CD650),
    secondary = Color(0xFF57624A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE7C8),
    onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBECE7),
    onTertiaryContainer = Color(0xFF1F4E4B),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5),
    onSurfaceVariant = Color(0xFF44483D),
    surfaceTint = Color(0xFF3F6900),
    inverseSurface = Color(0xFF2F312A),
    inverseOnSurface = Color(0xFFF1F2E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9FAEF),
    surfaceDim = Color(0xFFDADBD0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5E9),
    surfaceContainer = Color(0xFFEDEEE3),
    surfaceContainerHigh = Color(0xFFE7E9DD),
    surfaceContainerHighest = Color(0xFFE1E4D8),
)

internal val WaypadDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF9CD650),
    onPrimary = Color(0xFF1F3700),
    primaryContainer = Color(0xFF2F4F00),
    onPrimaryContainer = Color(0xFFB7F35E),
    inversePrimary = Color(0xFF3F6900),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331F),
    secondaryContainer = Color(0xFF404A33),
    onSecondaryContainer = Color(0xFFDBE7C8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBBECE7),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    surfaceTint = Color(0xFF9CD650),
    inverseSurface = Color(0xFFE2E3D8),
    inverseOnSurface = Color(0xFF2F312A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A32),
    surfaceDim = Color(0xFF12140E),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E),
)
