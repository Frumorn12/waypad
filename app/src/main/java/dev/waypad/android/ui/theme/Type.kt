package dev.waypad.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Material 3 type scale with the weight contrast the expressive guidance asks for: display and
 * headline roles go heavy, titles go semi-bold, body stays regular and labels go medium.
 *
 * The `*Emphasized` roles of the expressive type scale are not part of material3 1.4.0, so the
 * emphasis is expressed through weight on the standard roles instead.
 */
val WaypadTypography: Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge,
    bodyMedium = Default.bodyMedium,
    bodySmall = Default.bodySmall,
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontWeight = FontWeight.Medium),
)

/** Type roles that do not exist in the Material 3 scale but that Waypad needs. */
@Immutable
data class WaypadAccentTypography(
    /** Wordmark shown in the top app bar. */
    val brand: TextStyle = Default.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp,
    ),
    /** Host key fingerprints: must be monospaced so digits line up. */
    val fingerprint: TextStyle = Default.labelMedium.copy(fontFamily = FontFamily.Monospace),
    /** Read-outs in the diagnostics HUDs. */
    val telemetry: TextStyle = Default.labelSmall.copy(fontFamily = FontFamily.Monospace),
)

val LocalWaypadAccentTypography = staticCompositionLocalOf { WaypadAccentTypography() }
