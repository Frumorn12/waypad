package dev.waypad.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.theme.WaypadPreviewSurface

/**
 * Soft tonal wash painted behind the whole app.
 *
 * Now driven entirely by the active colour scheme, so it follows the wallpaper palette in both
 * light and dark.
 */
@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val sliver = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(primary.copy(alpha = PRIMARY_GLOW_ALPHA), radius = w * 0.42f, center = Offset(w * 0.82f, h * 0.12f))
        drawCircle(tertiary.copy(alpha = TERTIARY_GLOW_ALPHA), radius = w * 0.34f, center = Offset(w * 0.1f, h * 0.85f))
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.18f)
            lineTo(w * 0.9f, h * 0.12f)
            lineTo(w * 0.76f, h * 0.17f)
            lineTo(w * 0.18f, h * 0.24f)
            close()
        }
        drawPath(path, sliver.copy(alpha = SLIVER_ALPHA))
    }
}

private const val PRIMARY_GLOW_ALPHA = 0.10f
private const val TERTIARY_GLOW_ALPHA = 0.08f
private const val SLIVER_ALPHA = 0.025f

@Preview(name = "Atmospheric background - dark")
@Composable
private fun AtmosphericBackgroundPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    AtmosphericBackground(Modifier.fillMaxSize())
}
