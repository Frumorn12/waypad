package dev.waypad.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.theme.WaypadPreviewSurface

/**
 * One entry of the bottom bar.
 *
 * [key] is deliberately untyped so this component does not depend on the app's navigation enum and
 * stays previewable on its own.
 */
@Immutable
data class NavItem(val key: Any, val label: String, val icon: ImageVector)

/**
 * Bottom navigation for the connected session.
 *
 * Uses `ShortNavigationBar`, the Material 3 Expressive replacement for `NavigationBar`.
 */
@Composable
fun WaypadNavigationBar(
    items: List<NavItem>,
    selectedKey: Any?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        items.forEach { item ->
            ShortNavigationBarItem(
                selected = item.key == selectedKey,
                onClick = { onSelect(item.key) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Preview(name = "Navigation bar - dark")
@Composable
private fun WaypadNavigationBarPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    WaypadNavigationBar(items = previewItems, selectedKey = "screen", onSelect = {})
}

@Preview(name = "Navigation bar - light")
@Composable
private fun WaypadNavigationBarPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    WaypadNavigationBar(items = previewItems, selectedKey = "pad", onSelect = {})
}

private val previewItems = listOf(
    NavItem("pad", "Pad", Icons.Rounded.Mouse),
    NavItem("screen", "Screen", Icons.Rounded.Computer),
    NavItem("keys", "Keys", Icons.Rounded.Keyboard),
    NavItem("control", "Control", Icons.Rounded.Tune),
    NavItem("diag", "Diag", Icons.Rounded.Shield),
)
