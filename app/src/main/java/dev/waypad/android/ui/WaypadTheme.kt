package dev.waypad.android.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Acid = Color(0xFFB8FF5C)
val Ink = Color(0xFF070807)
val Graphite = Color(0xFF111411)
val Panel = Color(0xFF171B17)
val Mist = Color(0xFFEAF0E7)
val Muted = Color(0xFF9DAA9D)
val Line = Color(0xFF2A312B)
val Warning = Color(0xFFFFC857)

private val WaypadDark: ColorScheme = darkColorScheme(
    primary = Acid,
    onPrimary = Ink,
    secondary = Color(0xFF9ED8FF),
    onSecondary = Ink,
    tertiary = Warning,
    background = Ink,
    onBackground = Mist,
    surface = Graphite,
    onSurface = Mist,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Color(0xFFFF6B6B),
    onError = Ink,
)

@Composable
fun WaypadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaypadDark,
        typography = MaterialTheme.typography,
        content = content,
    )
}
