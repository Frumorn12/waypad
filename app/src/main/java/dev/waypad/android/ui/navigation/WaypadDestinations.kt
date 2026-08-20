package dev.waypad.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import dev.waypad.android.Screen
import dev.waypad.android.ui.components.NavItem

/**
 * Declarative description of Waypad's navigation graph.
 *
 * Waypad deliberately does **not** use `androidx.navigation:navigation-compose`: `WaypadViewModel`
 * already owns the current [Screen] inside its UI state, persists it through `SavedStateHandle`, and
 * attaches side effects to every transition (`go()` stops the screen stream, resets fullscreen and
 * lazily loads capture sources). A `NavHost` would introduce a second, competing source of truth for
 * the same value; reconciling the two would require changing the ViewModel. Keeping the state
 * machine and describing it here gives the same readability without the duplication.
 */
val WaypadNavItems: List<NavItem> = listOf(
    NavItem(Screen.Remote, "Pad", Icons.Rounded.Mouse),
    NavItem(Screen.RemoteDisplay, "Screen", Icons.Rounded.Computer),
    NavItem(Screen.Keyboard, "Keys", Icons.Rounded.Keyboard),
    NavItem(Screen.Controls, "Control", Icons.Rounded.Tune),
    NavItem(Screen.Troubleshooting, "Diag", Icons.Rounded.Shield),
)

/**
 * Screens where the system back gesture returns to [Screen.Discovery] instead of leaving the app.
 */
val ScreensReturningToDiscovery: Set<Screen> = setOf(
    Screen.Remote,
    Screen.RemoteDisplay,
    Screen.Keyboard,
    Screen.Controls,
    Screen.Troubleshooting,
    Screen.Settings,
)

/** Screens that forward external pointer devices to the host while they are on top. */
val ScreensCapturingExternalPointer: Set<Screen> = setOf(Screen.Remote, Screen.RemoteDisplay)
