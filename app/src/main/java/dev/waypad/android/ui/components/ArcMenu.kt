package dev.waypad.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.waypad.android.ui.theme.WaypadTheme
import kotlin.math.roundToInt

/** One entry of the arc menu. */
@Immutable
data class ArcMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

/** Pixel sizes the arc is drawn at, resolved once from the current density. */
class ArcMenuMetrics(
    val geometry: ArcMenuGeometry,
    val itemSizePx: Float,
    val labelWidthPx: Float,
)

/** Builds the geometry and the pixel sizes that go with it. */
@Composable
fun rememberArcMenuMetrics(itemCount: Int): ArcMenuMetrics {
    val density = LocalDensity.current
    return remember(itemCount, density) {
        with(density) {
            ArcMenuMetrics(
                geometry = ArcMenuGeometry(
                    itemCount = itemCount,
                    radiusPx = ARC_RADIUS.toPx(),
                    minInsetPx = ARC_MIN_INSET.toPx(),
                    maxInsetPx = ARC_MAX_INSET.toPx(),
                ),
                itemSizePx = ITEM_SIZE.toPx(),
                labelWidthPx = LABEL_MAX_WIDTH.toPx(),
            )
        }
    }
}

/**
 * The arc menu: a curve of entries drawn around wherever the thumb is.
 *
 * Purely presentational. It draws the state the gesture host hands it and reports nothing back,
 * because the whole interaction is one continuous drag owned by that host — tapping an entry
 * directly is never how this is used.
 */
@Composable
fun ArcMenu(
    items: List<ArcMenuItem>,
    metrics: ArcMenuMetrics,
    anchorY: Float,
    selectedIndex: Int,
    viewportWidth: Float,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty() || viewportWidth <= 0f) return
    val placements = metrics.geometry.placements(anchorY, selectedIndex, viewportWidth)
    val guideColour = MaterialTheme.colorScheme.primary

    Box(modifier.fillMaxSize()) {
        // The guide is what makes the entries read as one track instead of loose dots.
        Canvas(Modifier.fillMaxSize()) {
            if (placements.size < 2) return@Canvas
            val path = Path().apply {
                moveTo(placements.first().x, placements.first().y)
                placements.zipWithNext { from, to ->
                    quadraticTo(
                        (from.x + to.x) / 2f - GUIDE_BOW,
                        (from.y + to.y) / 2f,
                        to.x,
                        to.y,
                    )
                }
            }
            drawPath(path, guideColour.copy(alpha = GUIDE_ALPHA), style = Stroke(GUIDE_WIDTH))
        }

        placements.forEach { placement ->
            val item = items[placement.index]
            val selected = placement.index == selectedIndex
            val scale by animateFloatAsState(
                targetValue = placement.scale,
                animationSpec = WaypadTheme.motion.fastSpatial(),
                label = "arc_item_scale",
            )
            Box(
                Modifier
                    .arcOffset(placement.x, placement.y, metrics.itemSizePx)
                    .size(ITEM_SIZE)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(placement.alpha)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = IDLE_ALPHA)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }

        // Only the selection is named: labelling every entry would crowd the picture and defeat
        // the point of a menu you can run through without reading.
        placements.getOrNull(selectedIndex)?.let { selected ->
            Box(
                Modifier
                    .arcLabelOffset(selected.x, selected.y, metrics.itemSizePx, metrics.labelWidthPx)
                    .size(width = LABEL_MAX_WIDTH, height = ITEM_SIZE),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = WaypadTheme.shapes.overlay,
                        )
                        .padding(
                            horizontal = WaypadTheme.spacing.xxl,
                            vertical = WaypadTheme.spacing.md,
                        )
                ) {
                    Text(
                        text = items[selectedIndex].label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Slim strip on the trailing edge, so the gesture is discoverable without being in the way. */
@Composable
fun ArcMenuHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = HANDLE_ALPHA),
                shape = CircleShape,
            )
    )
}

/** Places a circle by its centre rather than its top-left corner. */
private fun Modifier.arcOffset(centreX: Float, centreY: Float, sizePx: Float): Modifier =
    offset {
        IntOffset(
            x = (centreX - sizePx / 2f).roundToInt(),
            y = (centreY - sizePx / 2f).roundToInt(),
        )
    }

/** Parks the label immediately to the left of the selected circle. */
private fun Modifier.arcLabelOffset(
    centreX: Float,
    centreY: Float,
    sizePx: Float,
    labelWidthPx: Float,
): Modifier = offset {
    IntOffset(
        x = (centreX - sizePx / 2f - LABEL_GAP_PX - labelWidthPx).roundToInt(),
        y = (centreY - sizePx / 2f).roundToInt(),
    )
}

internal val ARC_EDGE_WIDTH: Dp = 28.dp

private val ARC_RADIUS = 150.dp
private val ARC_MIN_INSET = 44.dp
private val ARC_MAX_INSET = 104.dp
private val ITEM_SIZE = 52.dp
private val ICON_SIZE = 24.dp
private val LABEL_MAX_WIDTH = 180.dp
private val HANDLE_WIDTH = 4.dp
private val HANDLE_HEIGHT = 72.dp

private const val LABEL_GAP_PX = 12f
private const val GUIDE_ALPHA = 0.35f
private const val GUIDE_WIDTH = 2f
private const val GUIDE_BOW = 10f
private const val IDLE_ALPHA = 0.86f
private const val HANDLE_ALPHA = 0.55f
