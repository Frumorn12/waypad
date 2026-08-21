package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.waypad.android.ui.components.SectionHeader
import dev.waypad.android.ui.components.ShortcutGrid
import dev.waypad.android.ui.components.WaypadCard
import dev.waypad.android.ui.state.KeyboardActions
import dev.waypad.android.ui.theme.WaypadPreviewSurface
import dev.waypad.android.ui.theme.WaypadTheme

private const val LIVE_INPUT_MIN_LINES = 4

/** Live text forwarding plus the shortcut palette. */
@Composable
fun KeyboardScreen(
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WaypadTheme.spacing.xl),
    ) {
        item {
            SectionHeader(
                "Keyboard",
                "Send text and common shortcuts through the daemon.",
            )
            WaypadCard {
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        actions.onKeyboardEdit(text, next)
                        text = next
                    },
                    label = { Text("Live keyboard input") },
                    minLines = LIVE_INPUT_MIN_LINES,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                Text(
                    "Typing here is forwarded immediately to the focused PC window. Windows types " +
                        "any character directly; on Hyprland without the RemoteDesktop portal, ASCII " +
                        "uses IPC key events and the rest falls back to a clipboard paste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(WaypadTheme.spacing.lg))
                Button(onClick = { text = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear local buffer")
                }
            }
        }
        item {
            ShortcutGrid(
                listOf(
                    "Copy" to { actions.onShortcut(listOf("ctrl", "c")) },
                    "Paste" to { actions.onShortcut(listOf("ctrl", "v")) },
                    "Undo" to { actions.onShortcut(listOf("ctrl", "z")) },
                    "Redo" to { actions.onShortcut(listOf("ctrl", "shift", "z")) },
                    "Save" to { actions.onShortcut(listOf("ctrl", "s")) },
                    "Close" to { actions.onShortcut(listOf("ctrl", "w")) },
                    "Terminal" to { actions.onShortcut(listOf("super", "enter")) },
                    "Launcher" to { actions.onShortcut(listOf("super", "space")) },
                    "Esc" to { actions.onShortcut(listOf("esc")) },
                    "Tab" to { actions.onShortcut(listOf("tab")) },
                    "Enter" to { actions.onShortcut(listOf("enter")) },
                )
            )
        }
    }
}

@Preview(name = "Keyboard - dark", heightDp = 900)
@Composable
private fun KeyboardScreenPreviewDark() = WaypadPreviewSurface(darkTheme = true) {
    KeyboardScreen(KeyboardActions())
}

@Preview(name = "Keyboard - light", heightDp = 900)
@Composable
private fun KeyboardScreenPreviewLight() = WaypadPreviewSurface(darkTheme = false) {
    KeyboardScreen(KeyboardActions())
}
