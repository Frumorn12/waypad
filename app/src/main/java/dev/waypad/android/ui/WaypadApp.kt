package dev.waypad.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.waypad.android.Screen
import dev.waypad.android.WaypadUiState
import dev.waypad.android.WaypadViewModel
import dev.waypad.android.core.model.ConnectionState
import dev.waypad.android.ui.components.AtmosphericBackground
import dev.waypad.android.ui.components.WaypadNavigationBar
import dev.waypad.android.ui.components.WaypadTopBar
import dev.waypad.android.ui.navigation.ScreensCapturingExternalPointer
import dev.waypad.android.ui.navigation.ScreensReturningToDiscovery
import dev.waypad.android.ui.navigation.WaypadNavItems
import dev.waypad.android.ui.screens.ControlsScreen
import dev.waypad.android.ui.screens.DiscoveryScreen
import dev.waypad.android.ui.screens.KeyboardScreen
import dev.waypad.android.ui.screens.OnboardingScreen
import dev.waypad.android.ui.screens.PairingScreen
import dev.waypad.android.ui.screens.RemoteDisplayScreen
import dev.waypad.android.ui.screens.RemoteDisplayVideoSurface
import dev.waypad.android.ui.screens.RemotePadScreen
import dev.waypad.android.ui.screens.SettingsScreen
import dev.waypad.android.ui.screens.TroubleshootingScreen
import dev.waypad.android.ui.screens.TrustedHostsScreen
import dev.waypad.android.ui.system.ExternalPointerCaptureEffect
import dev.waypad.android.ui.system.FullscreenSystemUiEffect
import dev.waypad.android.ui.system.RemoteScreenOrientationEffect
import dev.waypad.android.ui.theme.WaypadTheme

/**
 * Root of the Waypad UI: the only composable that knows about [WaypadViewModel].
 *
 * It collects the ViewModel state, builds the callback tree once, and hands both down as plain
 * values. Every screen underneath is a pure function of its own immutable state plus lambdas, which
 * is what makes the `@Preview`s under `ui.screens` and `ui.components` possible.
 */
@Composable
fun WaypadApp(viewModel: WaypadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberWaypadActions(viewModel)
    WaypadTheme {
        WaypadAppShell(
            state = state,
            actions = actions,
            // The ViewModel stops here: the shell receives a slot, not the session.
            videoSurface = { RemoteDisplayVideoSurface(viewModel.videoSession.renderer) },
        )
    }
}

/**
 * App chrome and screen switcher.
 *
 * Navigation is a state machine owned by the ViewModel rather than a `NavHost` - see
 * [dev.waypad.android.ui.navigation.WaypadNavItems] for the rationale.
 */
@Composable
internal fun WaypadAppShell(
    state: WaypadUiState,
    actions: WaypadActions,
    modifier: Modifier = Modifier,
    videoSurface: @Composable () -> Unit = {},
) {
    val remoteFullscreen = state.screen == Screen.RemoteDisplay && state.remoteScreenFullscreen
    val externalPointerCapture = state.connectionState == ConnectionState.Connected &&
        state.screen in ScreensCapturingExternalPointer
    val connected = state.connectionState == ConnectionState.Connected

    RemoteScreenOrientationEffect(state.screen == Screen.RemoteDisplay)
    FullscreenSystemUiEffect(remoteFullscreen)
    ExternalPointerCaptureEffect(externalPointerCapture)
    BackHandler(remoteFullscreen) { actions.onExitFullscreen() }
    BackHandler(!remoteFullscreen && state.screen in ScreensReturningToDiscovery) {
        actions.onNavigate(Screen.Discovery)
    }

    val gutter = WaypadTheme.spacing.gutter
    val none = WaypadTheme.spacing.none
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        if (!remoteFullscreen) AtmosphericBackground()
        Scaffold(
            // The container is transparent so the gradient and the atmospheric wash show through;
            // contentColorFor(Transparent) is Unspecified, which would leave unstyled Text black,
            // so the content colour has to be provided explicitly.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                if (!remoteFullscreen) {
                    WaypadTopBar(state.toShellState(), actions.onOpenSettings)
                }
            },
            bottomBar = {
                if (connected && !remoteFullscreen) {
                    WaypadNavigationBar(
                        items = WaypadNavItems,
                        selectedKey = state.screen,
                        onSelect = { key -> actions.onNavigate(key as Screen) },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(if (remoteFullscreen) PaddingValues(none) else padding)
                    .padding(horizontal = if (remoteFullscreen) none else gutter)
                    .fillMaxSize()
            ) {
                when (state.screen) {
                    Screen.Onboarding -> OnboardingScreen(actions.onboarding)
                    Screen.Discovery -> DiscoveryScreen(state.toDiscoveryState(), actions.discovery)
                    Screen.Pairing -> PairingScreen(state.toPairingState(), actions.pairing)
                    Screen.Remote -> RemotePadScreen(state.toRemotePadState(), actions.remotePad)
                    Screen.RemoteDisplay -> RemoteDisplayScreen(
                        state = state.toRemoteDisplayState(),
                        actions = actions.remoteDisplay,
                        videoSurface = videoSurface,
                    )
                    Screen.Keyboard -> KeyboardScreen(actions.keyboard)
                    Screen.Controls -> ControlsScreen(state.toControlsState(), actions.controls)
                    Screen.Settings -> SettingsScreen(state.toSettingsState(), actions.settings)
                    Screen.TrustedHosts -> TrustedHostsScreen(
                        state = state.toTrustedHostsState(),
                        actions = actions.trustedHosts,
                    )
                    Screen.Troubleshooting -> TroubleshootingScreen(
                        state = state.toDiagnosticsState(),
                        actions = actions.diagnostics,
                    )
                }
            }
        }
    }
}

@Preview(name = "Shell - onboarding - dark", widthDp = 412, heightDp = 900)
@Composable
private fun WaypadAppShellPreviewDark() {
    WaypadTheme(darkTheme = true, dynamicColor = false) {
        WaypadAppShell(state = WaypadUiState(), actions = WaypadActions())
    }
}

@Preview(name = "Shell - connected - light", widthDp = 412, heightDp = 900)
@Composable
private fun WaypadAppShellPreviewLight() {
    WaypadTheme(darkTheme = false, dynamicColor = false) {
        WaypadAppShell(
            state = WaypadUiState(
                screen = Screen.Controls,
                connectionState = ConnectionState.Connected,
                status = "Connected to frumorn-desktop",
            ),
            actions = WaypadActions(),
        )
    }
}
