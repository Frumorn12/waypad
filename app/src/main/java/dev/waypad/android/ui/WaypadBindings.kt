package dev.waypad.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.waypad.android.Screen
import dev.waypad.android.WaypadUiState
import dev.waypad.android.WaypadViewModel
import dev.waypad.android.core.externalinput.ExternalInputDeviceClass
import dev.waypad.android.ui.state.ControlsActions
import dev.waypad.android.ui.state.ControlsUiState
import dev.waypad.android.ui.state.DiagnosticsActions
import dev.waypad.android.ui.state.DiagnosticsUiState
import dev.waypad.android.ui.state.DiscoveryActions
import dev.waypad.android.ui.state.DiscoveryUiState
import dev.waypad.android.ui.state.KeyboardActions
import dev.waypad.android.ui.state.OnboardingActions
import dev.waypad.android.ui.state.PairingActions
import dev.waypad.android.ui.state.PairingUiState
import dev.waypad.android.ui.state.RemoteDisplayActions
import dev.waypad.android.ui.state.RemoteDisplayUiState
import dev.waypad.android.ui.state.RemotePadActions
import dev.waypad.android.ui.state.RemotePadUiState
import dev.waypad.android.ui.state.SettingsActions
import dev.waypad.android.ui.state.SettingsUiState
import dev.waypad.android.ui.state.ShellUiState
import dev.waypad.android.ui.state.TrustedHostsActions
import dev.waypad.android.ui.state.TrustedHostsUiState

/**
 * The single seam between `WaypadViewModel` and the UI tree.
 *
 * Everything below [WaypadApp] receives plain state objects and lambdas; this file is the only place
 * in `ui/` that is allowed to mention the ViewModel or `WaypadUiState`.
 */
@Immutable
class WaypadActions(
    val onNavigate: (Screen) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onExitFullscreen: () -> Unit = {},
    val onboarding: OnboardingActions = OnboardingActions(),
    val discovery: DiscoveryActions = DiscoveryActions(),
    val pairing: PairingActions = PairingActions(),
    val remotePad: RemotePadActions = RemotePadActions(),
    val remoteDisplay: RemoteDisplayActions = RemoteDisplayActions(),
    val keyboard: KeyboardActions = KeyboardActions(),
    val controls: ControlsActions = ControlsActions(),
    val settings: SettingsActions = SettingsActions(),
    val trustedHosts: TrustedHostsActions = TrustedHostsActions(),
    val diagnostics: DiagnosticsActions = DiagnosticsActions(),
)

/** Builds the callback tree once per ViewModel instance. */
@Composable
fun rememberWaypadActions(viewModel: WaypadViewModel): WaypadActions = remember(viewModel) {
    WaypadActions(
        onNavigate = viewModel::go,
        onOpenSettings = { viewModel.go(Screen.Settings) },
        onExitFullscreen = { viewModel.setRemoteScreenFullscreen(false) },
        onboarding = OnboardingActions(
            onDiscoverHosts = viewModel::startDiscovery,
            onOpenTrustedHosts = { viewModel.go(Screen.TrustedHosts) },
        ),
        discovery = DiscoveryActions(
            onScanLan = viewModel::startDiscovery,
            onSelectHost = viewModel::selectHost,
            onApplyInvite = viewModel::applyInvite,
            onManualAddressChange = viewModel::setManualAddress,
            onManualPortChange = viewModel::setManualPort,
            onUseManualHost = viewModel::useManualHost,
        ),
        pairing = PairingActions(
            onPairingCodeChange = viewModel::setPairingCode,
            onPair = viewModel::pairSelectedHost,
        ),
        remotePad = RemotePadActions(
            onPrepareInput = viewModel::prepareInput,
            onPointerMove = viewModel::pointerMove,
            onScroll = { dx, dy, finish -> viewModel.scroll(dx, dy, finish) },
            onPointerButton = viewModel::pointerButton,
            onReleasePointerButtons = viewModel::releasePointerButtons,
            onBeginInteraction = viewModel::beginRemoteInteraction,
            onEndInteraction = viewModel::endRemoteInteraction,
            onGestureStateChanged = viewModel::updateRemoteGesture,
            onPointerCancelled = viewModel::notePointerCancellation,
            onPointerFailed = viewModel::notePointerFailure,
        ),
        remoteDisplay = RemoteDisplayActions(
            onSelectSource = viewModel::selectScreenSource,
            onStartStream = viewModel::startScreenStream,
            onStopStream = viewModel::stopScreenStream,
            onRefreshSources = viewModel::loadScreenSources,
            onSetFullscreen = viewModel::setRemoteScreenFullscreen,
            onSetGameMode = viewModel::setRemoteScreenGameMode,
            onSetControlsVisible = { visible, reason ->
                viewModel.setRemoteScreenControlsVisible(visible, reason)
            },
            onRevealControls = viewModel::revealRemoteScreenControls,
            onKeyboardEdit = viewModel::sendLiveKeyboardEdit,
            onPointerButton = viewModel::pointerButton,
            onDesktopPointerMove = viewModel::remoteScreenPointerMove,
            onDesktopClick = { x, y -> viewModel.remoteScreenClick(x, y) },
            onScroll = { dx, dy, finish -> viewModel.scroll(dx, dy, finish) },
        ),
        keyboard = KeyboardActions(
            onKeyboardEdit = viewModel::sendLiveKeyboardEdit,
            onShortcut = { keys -> viewModel.shortcut(*keys.toTypedArray()) },
        ),
        controls = ControlsActions(
            onMedia = viewModel::media,
            onVolume = viewModel::volume,
            onBrightness = viewModel::brightness,
            onSystem = viewModel::system,
        ),
        settings = SettingsActions(
            onSelectProfile = viewModel::setStreamProfile,
            onSetMaxFps = viewModel::setStreamMaxFps,
            onSetMaxDimension = viewModel::setStreamMaxDimension,
            onSetJpegQuality = viewModel::setStreamJpegQuality,
            onToggleStats = viewModel::toggleStreamStats,
            onToggleHaptics = viewModel::toggleHaptics,
            onSetGameMode = viewModel::setRemoteScreenGameMode,
            onOpenTrustedHosts = { viewModel.go(Screen.TrustedHosts) },
            onDisconnect = viewModel::disconnect,
        ),
        trustedHosts = TrustedHostsActions(
            onDiscoverNewHost = viewModel::startDiscovery,
            onConnect = viewModel::connect,
            onRemove = viewModel::removeTrustedHost,
        ),
        diagnostics = DiagnosticsActions(
            onRefreshCapabilities = viewModel::refreshCapabilities,
        ),
    )
}

internal fun WaypadUiState.toShellState(): ShellUiState = ShellUiState(
    hostName = connectedHost?.hostName,
    status = status,
    error = error,
)

internal fun WaypadUiState.toDiscoveryState(): DiscoveryUiState = DiscoveryUiState(
    discoveredHosts = discoveredHosts,
    manualAddress = manualAddress,
    manualPort = manualPort,
)

internal fun WaypadUiState.toPairingState(): PairingUiState = PairingUiState(
    hostName = selectedHost?.hostName,
    fingerprint = selectedHost?.fingerprint,
    pairingCode = pairingCode,
)

internal fun WaypadUiState.toRemotePadState(): RemotePadUiState = RemotePadUiState(
    inputSupported = capabilities.inputSupported,
    inputBackend = capabilities.inputBackend,
    haptics = haptics,
    sessionActive = remoteInputSessionActive,
    gestureMode = remoteGestureMode,
    pointerCount = remotePointerCount,
    inputBacklog = remoteInputBacklog,
    connectionLabel = connectionState.name,
    controllerConnected = externalInputDevices.any { device ->
        device.classes.contains(ExternalInputDeviceClass.Gamepad) ||
            device.classes.contains(ExternalInputDeviceClass.Joystick)
    },
)

internal fun WaypadUiState.toRemoteDisplayState(): RemoteDisplayUiState = RemoteDisplayUiState(
    frameWidth = videoWidth,
    frameHeight = videoHeight,
    captureSupported = capabilities.captureSupported,
    inputSupported = capabilities.inputSupported,
    screenSources = screenSources,
    selectedScreenSourceId = selectedScreenSourceId,
    streamingSource = screenStreamInfo?.source,
    screenStreaming = screenStreaming,
    screenStatus = screenStatus,
    screenError = screenError,
    stats = screenStreamStats,
    fullscreen = remoteScreenFullscreen,
    gameMode = remoteScreenGameMode,
    controlsVisible = remoteScreenControlsVisible,
    haptics = haptics,
)

internal fun WaypadUiState.toControlsState(): ControlsUiState = ControlsUiState(
    mediaSupported = capabilities.media,
    volumeSupported = capabilities.volume,
    brightnessSupported = capabilities.brightness,
    systemSupported = capabilities.lock || capabilities.suspend,
)

internal fun WaypadUiState.toSettingsState(): SettingsUiState = SettingsUiState(
    streamSettings = streamSettings,
    haptics = haptics,
    gameMode = remoteScreenGameMode,
)

internal fun WaypadUiState.toTrustedHostsState(): TrustedHostsUiState = TrustedHostsUiState(
    trustedHosts = trustedHosts,
)

internal fun WaypadUiState.toDiagnosticsState(): DiagnosticsUiState = DiagnosticsUiState(
    connectionLabel = connectionState.name,
    capabilities = capabilities,
    externalInputStatus = externalInputStatus,
    externalInputDevices = externalInputDevices,
    screenStreaming = screenStreaming,
    stats = screenStreamStats,
    screenStatus = screenStatus,
    screenError = screenError,
)
