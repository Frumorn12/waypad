package dev.waypad.android

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.waypad.android.core.externalinput.ExternalInputDeviceClass
import dev.waypad.android.core.externalinput.ExternalInputDeviceSummary
import dev.waypad.android.core.externalinput.ExternalInputEvent
import dev.waypad.android.core.input.RemoteGestureMode
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.CapabilitySummary
import dev.waypad.android.core.model.ConnectionState
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.model.RemoteScreenConnectionState
import dev.waypad.android.core.model.ScreenSource
import dev.waypad.android.core.model.ScreenStreamInfo
import dev.waypad.android.core.model.TrustedHost
import dev.waypad.android.core.network.RemoteScreenFrame
import dev.waypad.android.core.network.RemoteScreenStreamClient
import dev.waypad.android.core.network.WaypadClient
import dev.waypad.android.core.network.WaypadDiscovery
import dev.waypad.android.core.screen.RemoteScreenSessionEvent
import dev.waypad.android.core.screen.RemoteScreenSessionMachine
import dev.waypad.android.core.storage.TrustedHostStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class Screen {
    Onboarding,
    Discovery,
    Pairing,
    Remote,
    RemoteDisplay,
    Keyboard,
    Controls,
    Settings,
    TrustedHosts,
    Troubleshooting,
}

data class WaypadUiState(
    val screen: Screen = Screen.Onboarding,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val trustedHosts: List<TrustedHost> = emptyList(),
    val selectedHost: DiscoveredHost? = null,
    val connectedHost: TrustedHost? = null,
    val capabilities: CapabilitySummary = CapabilitySummary(),
    val pairingCode: String = "",
    val manualAddress: String = "",
    val manualPort: String = "47771",
    val status: String = "Ready",
    val error: String? = null,
    val haptics: Boolean = true,
    val remoteInputSessionActive: Boolean = false,
    val remoteGestureMode: RemoteGestureMode = RemoteGestureMode.Idle,
    val remotePointerCount: Int = 0,
    val remoteInputBacklog: Int = 0,
    val externalInputDevices: List<ExternalInputDeviceSummary> = emptyList(),
    val externalInputStatus: String = "No external input devices detected",
    val screenSources: List<ScreenSource> = emptyList(),
    val selectedScreenSourceId: String? = null,
    val screenStreamInfo: ScreenStreamInfo? = null,
    val screenFrame: RemoteScreenFrame? = null,
    val screenStreaming: Boolean = false,
    val screenConnectionState: RemoteScreenConnectionState = RemoteScreenConnectionState.Idle,
    val remoteScreenFullscreen: Boolean = false,
    val screenStatus: String = "Screen stream idle",
    val screenError: String? = null,
)

class WaypadViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "WaypadViewModel"
        const val INPUT_QUEUE_CAPACITY = 256
        const val INTERACTION_GRACE_MS = 180L
        const val INTERACTION_KEEPALIVE_INITIAL_MS = 2_000L
        const val INTERACTION_KEEPALIVE_INTERVAL_MS = 5_000L
        const val SCREEN_STREAM_MAX_RETRIES = 3
        const val EXTERNAL_UNSUPPORTED_NOTICE_MS = 2_000L
    }

    private val discovery = WaypadDiscovery(application)
    private val client = WaypadClient()
    private val screenStreamClient = RemoteScreenStreamClient()
    private val store = TrustedHostStore(application)
    private val inputCommands = Channel<RemoteInputCommand>(capacity = INPUT_QUEUE_CAPACITY)
    private val queuedInputCommands = AtomicInteger(0)
    private var interactionKeepAliveJob: Job? = null
    private var interactionEndJob: Job? = null
    private var screenStreamJob: Job? = null
    private var screenStreamDesired = false
    private val screenSessionMachine = RemoteScreenSessionMachine()
    private var activeInteractionSessionId = 0L
    private var nextInteractionSessionId = 0L
    private var lastExternalUnsupportedNoticeAt = 0L
    private val _state = MutableStateFlow(
        WaypadUiState(trustedHosts = store.load())
    )
    val state: StateFlow<WaypadUiState> = _state

    init {
        viewModelScope.launch { drainInputCommands() }
    }

    fun go(screen: Screen) {
        val wasCapturingExternalInput = shouldCaptureExternalInput(_state.value)
        val previous = _state.value.screen
        if (previous == Screen.RemoteDisplay && screen != Screen.RemoteDisplay) {
            stopScreenStream()
        }
        _state.update {
            it.copy(
                screen = screen,
                error = null,
                remoteScreenFullscreen = if (screen == Screen.RemoteDisplay) {
                    it.remoteScreenFullscreen
                } else {
                    false
                },
            )
        }
        if (!wasCapturingExternalInput && shouldCaptureExternalInput(_state.value)) {
            publishCurrentExternalDevices()
        }
        if (screen == Screen.RemoteDisplay && _state.value.screenSources.isEmpty()) {
            loadScreenSources()
        }
    }

    fun updateExternalInputDevices(devices: List<ExternalInputDeviceSummary>) {
        val previous = _state.value.externalInputDevices.associateBy { it.id }
        val current = devices.associateBy { it.id }
        val added = devices.filter { it.id !in previous }
        val removed = previous.values.filter { it.id !in current }
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            Log.i(
                TAG,
                "external_devices_changed added=${added.map { it.name }} removed=${removed.map { it.name }} count=${devices.size}",
            )
        }
        _state.update {
            it.copy(
                externalInputDevices = devices,
                externalInputStatus = externalInputSummary(devices),
            )
        }
        if (shouldCaptureExternalInput(_state.value)) {
            added.forEach { device ->
                enqueueExternalDeviceConnected(device)
            }
            removed.forEach { device ->
                enqueueInput(
                    RemoteInputCommand.External(
                        ExternalInputEvent.DeviceDisconnected(
                            deviceId = device.id,
                            deviceType = device.classes.primaryTypeForStatus(),
                        ),
                    ),
                )
            }
        }
    }

    private fun publishCurrentExternalDevices() {
        val current = _state.value
        if (!shouldCaptureExternalInput(current)) return
        current.externalInputDevices.forEach(::enqueueExternalDeviceConnected)
    }

    private fun enqueueExternalDeviceConnected(device: ExternalInputDeviceSummary) {
        enqueueInput(
            RemoteInputCommand.External(
                ExternalInputEvent.DeviceConnected(
                    deviceId = device.id,
                    deviceType = device.classes.primaryTypeForStatus(),
                    name = device.name,
                    classes = device.classes,
                ),
            ),
        )
    }

    fun handleExternalInputEvent(event: ExternalInputEvent): Boolean {
        val current = _state.value
        if (!shouldCaptureExternalInput(current)) return false
        if (!isExternalInputSupported(current, event)) {
            noteExternalUnsupported(event)
            return true
        }
        enqueueInput(RemoteInputCommand.External(event))
        return true
    }

    private fun noteExternalUnsupported(event: ExternalInputEvent) {
        val now = System.currentTimeMillis()
        if (now - lastExternalUnsupportedNoticeAt < EXTERNAL_UNSUPPORTED_NOTICE_MS) return
        lastExternalUnsupportedNoticeAt = now
        val label = event.deviceType.unsupportedLabel()
        val reason = _state.value.capabilities.externalInputReason
        Log.w(TAG, "external_input_unsupported type=${event.deviceType.wireName} reason=$reason")
        _state.update {
            it.copy(
                externalInputStatus = "$label forwarding unsupported: $reason",
                status = "$label forwarding unsupported",
            )
        }
    }

    fun setPairingCode(code: String) {
        _state.update { it.copy(pairingCode = code.filter(Char::isDigit).take(6)) }
    }

    fun setManualAddress(value: String) {
        _state.update { it.copy(manualAddress = value.trim()) }
    }

    fun setManualPort(value: String) {
        _state.update { it.copy(manualPort = value.filter(Char::isDigit).take(5)) }
    }

    fun toggleHaptics() {
        _state.update { it.copy(haptics = !it.haptics) }
    }

    fun startDiscovery() {
        _state.update {
            it.copy(screen = Screen.Discovery, connectionState = ConnectionState.Discovering, status = "Scanning LAN...", error = null)
        }
        viewModelScope.launch {
            runCatching { discovery.discover() }
                .onSuccess { hosts ->
                    _state.update {
                        it.copy(
                            discoveredHosts = hosts,
                            connectionState = ConnectionState.Disconnected,
                            status = if (hosts.isEmpty()) "No hosts discovered. Use manual connect." else "Found ${hosts.size} host(s).",
                        )
                    }
                }
                .onFailure { fail("Discovery failed", it) }
        }
    }

    fun selectHost(host: DiscoveredHost) {
        _state.update { it.copy(selectedHost = host, screen = Screen.Pairing, pairingCode = "", error = null) }
    }

    fun useManualHost() {
        val current = _state.value
        val port = current.manualPort.toIntOrNull() ?: 47771
        if (current.manualAddress.isBlank()) {
            _state.update { it.copy(error = "Enter a host IP address first.") }
            return
        }
        selectHost(
            DiscoveredHost(
                hostName = current.manualAddress,
                address = current.manualAddress,
                port = port,
                fingerprint = "",
                inputSupported = false,
                inputBackend = "manual",
                captureSupported = false,
                captureBackend = "manual",
            )
        )
    }

    fun pairSelectedHost() {
        val current = _state.value
        val host = current.selectedHost ?: return
        if (current.pairingCode.length != 6) {
            _state.update { it.copy(error = "Enter the 6 digit pairing code shown on the Linux host.") }
            return
        }
        Log.i(TAG, "connection_state=pairing host=${host.hostName} address=${host.address}:${host.port}")
        _state.update { it.copy(connectionState = ConnectionState.Pairing, status = "Pairing with ${host.hostName}...", error = null) }
        viewModelScope.launch {
            val deviceName = "Waypad Android ${Build.MODEL}".take(80)
            runCatching {
                client.pair(
                    address = host.address,
                    port = host.port,
                    deviceName = deviceName,
                    pairingCode = current.pairingCode,
                    expectedFingerprint = host.fingerprint.ifBlank { null },
                )
            }.onSuccess { (trusted, capabilities) ->
                Log.i(TAG, "connection_state=connected paired_host=${trusted.hostName} input_backend=${capabilities.inputBackend}")
                store.upsert(trusted)
                _state.update {
                    it.copy(
                        trustedHosts = store.load(),
                        connectedHost = trusted,
                        capabilities = capabilities,
                        connectionState = ConnectionState.Connected,
                        screen = Screen.Remote,
                        status = "Connected to ${trusted.hostName}",
                        error = null,
                    )
                }
                publishCurrentExternalDevices()
            }.onFailure { fail("Pairing failed", it) }
        }
    }

    fun connect(host: TrustedHost) {
        Log.i(TAG, "connection_state=connecting host=${host.hostName} address=${host.address}:${host.port}")
        _state.update { it.copy(connectionState = ConnectionState.Connecting, status = "Connecting to ${host.hostName}...", error = null) }
        viewModelScope.launch {
            runCatching { client.connect(host) }
                .onSuccess { capabilities ->
                    Log.i(TAG, "connection_state=connected host=${host.hostName} input_backend=${capabilities.inputBackend}")
                    val updated = host.copy(lastConnectedAt = System.currentTimeMillis())
                    store.upsert(updated)
                    _state.update {
                        it.copy(
                            trustedHosts = store.load(),
                            connectedHost = updated,
                            capabilities = capabilities,
                            connectionState = ConnectionState.Connected,
                            screen = Screen.Remote,
                            status = "Connected to ${host.hostName}",
                        )
                    }
                    publishCurrentExternalDevices()
                }
                .onFailure { fail("Connection failed", it) }
        }
    }

    fun disconnect() {
        Log.i(TAG, "connection_state=disconnected user_requested=true")
        stopScreenStream()
        interactionKeepAliveJob?.cancel()
        interactionEndJob?.cancel()
        activeInteractionSessionId = 0L
        _state.update {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                connectedHost = null,
                screen = Screen.Discovery,
                status = "Disconnected",
                remoteInputSessionActive = false,
                remoteGestureMode = RemoteGestureMode.Idle,
                remotePointerCount = 0,
                remoteInputBacklog = 0,
                externalInputStatus = externalInputSummary(it.externalInputDevices),
                screenSources = emptyList(),
                selectedScreenSourceId = null,
                screenStreamInfo = null,
                screenFrame = null,
                screenStreaming = false,
                screenConnectionState = RemoteScreenConnectionState.Idle,
                remoteScreenFullscreen = false,
                screenStatus = "Screen stream idle",
                screenError = null,
            )
        }
        clearQueuedInput()
        client.close()
    }

    fun removeTrustedHost(id: String) {
        store.remove(id)
        _state.update { it.copy(trustedHosts = store.load()) }
    }

    fun prepareInput() = launchCommand("Requesting portal approval...") { client.prepareInput() }

    fun beginRemoteInteraction(): Long {
        val sessionId = ++nextInteractionSessionId
        activeInteractionSessionId = sessionId
        interactionEndJob?.cancel()
        if (!_state.value.remoteInputSessionActive) {
            Log.d(TAG, "remote_interaction_start session=$sessionId")
        }
        _state.update {
            it.copy(
                remoteInputSessionActive = true,
                remoteGestureMode = RemoteGestureMode.SinglePointerDown,
                remotePointerCount = maxOf(1, it.remotePointerCount),
            )
        }
        startInteractionKeepAlive()
        return sessionId
    }

    fun updateRemoteGesture(mode: RemoteGestureMode, pointerCount: Int) {
        val current = _state.value
        if (current.remoteGestureMode == mode && current.remotePointerCount == pointerCount) return
        Log.d(TAG, "gesture_state mode=${mode.label} pointer_count=$pointerCount")
        _state.update { it.copy(remoteGestureMode = mode, remotePointerCount = pointerCount) }
    }

    fun endRemoteInteraction(sessionId: Long) {
        if (activeInteractionSessionId != sessionId) return
        interactionEndJob?.cancel()
        interactionEndJob = viewModelScope.launch {
            delay(INTERACTION_GRACE_MS)
            if (activeInteractionSessionId != sessionId) return@launch
            Log.d(TAG, "remote_interaction_end session=$sessionId")
            activeInteractionSessionId = 0L
            interactionKeepAliveJob?.cancel()
            _state.update {
                it.copy(
                    remoteInputSessionActive = false,
                    remoteGestureMode = RemoteGestureMode.Idle,
                    remotePointerCount = 0,
                )
            }
        }
    }

    fun notePointerCancellation(reason: String) {
        Log.w(TAG, "pointer_cancel reason=$reason")
    }

    fun notePointerFailure(throwable: Throwable) {
        Log.e(TAG, "pointer_failure", throwable)
    }

    fun pointerMove(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        enqueueInput(RemoteInputCommand.PointerMove(dx, dy))
    }

    fun pointerButton(button: PointerButton, state: ButtonState) {
        if (_state.value.capabilities.inputBackend == "hyprland-hyprctl") {
            if (state == ButtonState.Pressed) {
                _state.update { it.copy(status = "Legacy Hyprland fallback moves only the pointer; update waypad-daemon for IPC clicks.") }
            }
            return
        }
        enqueueInput(RemoteInputCommand.PointerButton(button, state))
    }

    fun releasePointerButtons() {
        enqueueInput(RemoteInputCommand.ReleasePointerButtons)
    }

    fun scroll(dx: Float, dy: Float, finish: Boolean = false) {
        if (_state.value.capabilities.inputBackend == "hyprland-hyprctl") {
            if (!finish) {
                _state.update { it.copy(status = "Legacy Hyprland fallback cannot scroll; update waypad-daemon for IPC scroll.") }
            }
            return
        }
        if (finish) {
            enqueueInput(RemoteInputCommand.ScrollFinish)
        } else if (dx.isFinite() && dy.isFinite()) {
            enqueueInput(RemoteInputCommand.Scroll(dx, dy))
        }
    }

    fun sendText(text: String) = launchCommand("Sending text...") { client.text(text) }

    fun sendLiveKeyboardEdit(previous: String, next: String) {
        when {
            next.startsWith(previous) -> {
                val appended = next.removePrefix(previous)
                if (appended.isNotEmpty()) launchQuiet { client.text(appended) }
            }
            previous.startsWith(next) -> {
                val removed = (previous.length - next.length).coerceAtMost(32)
                if (removed > 0) launchQuiet {
                    repeat(removed) { client.shortcut("backspace") }
                }
            }
            next != previous -> {
                launchQuiet { client.text(next) }
            }
        }
    }

    fun shortcut(vararg keys: String) = launchCommand(null) { client.shortcut(*keys) }

    fun media(action: String) = launchCommand(null) { client.media(action) }

    fun volume(action: String) = launchCommand(null) { client.volume(action) }

    fun brightness(action: String) = launchCommand(null) { client.brightness(action) }

    fun system(action: String) = launchCommand(null) { client.system(action) }

    fun clipboard(text: String) = launchCommand("Sending clipboard text...") { client.clipboard(text) }

    fun refreshCapabilities() {
        launchCommand("Refreshing capabilities...") {
            val capabilities = client.capabilities()
            _state.update { it.copy(capabilities = capabilities, status = "Capabilities refreshed") }
        }
    }

    fun loadScreenSources() {
        launchCommand("Loading screen sources...") {
            val sources = client.listScreenSources()
            _state.update { current ->
                val selected = current.selectedScreenSourceId
                    ?.takeIf { id -> sources.any { it.id == id } }
                    ?: sources.firstOrNull { it.focused }?.id
                    ?: sources.firstOrNull()?.id
                current.copy(
                    screenSources = sources,
                    selectedScreenSourceId = selected,
                    screenStatus = if (sources.isEmpty()) "No screen sources available" else "Found ${sources.size} screen source(s)",
                    screenError = null,
                )
            }
        }
    }

    fun selectScreenSource(sourceId: String) {
        _state.update {
            it.copy(
                selectedScreenSourceId = sourceId,
                screenStatus = "Selected ${it.screenSources.firstOrNull { source -> source.id == sourceId }?.label ?: sourceId}",
            )
        }
    }

    fun startScreenStream() {
        val host = _state.value.connectedHost ?: run {
            _state.update { it.copy(screenError = "Connect to a host before starting a screen stream.") }
            return
        }
        val existingSessionId = _state.value.screenStreamInfo?.sessionId
        screenStreamDesired = true
        screenStreamJob?.cancel()
        screenStreamJob = viewModelScope.launch {
            if (existingSessionId != null) {
                stopScreenStreamOnHost(existingSessionId, "before_restart")
            }
            _state.update {
                it.copy(
                    screenStreaming = true,
                    screenFrame = null,
                    screenStatus = "Starting screen stream...",
                    screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Start),
                    screenError = null,
                )
            }
            val selected = _state.value.selectedScreenSourceId
            var attempt = 0
            while (isActive && screenStreamDesired && attempt < SCREEN_STREAM_MAX_RETRIES) {
                attempt += 1
                var info: ScreenStreamInfo? = null
                val result = runCatching {
                    if (attempt > 1) {
                        _state.update {
                            it.copy(
                                screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Retry),
                                screenStatus = "Reconnecting screen stream ($attempt/$SCREEN_STREAM_MAX_RETRIES)...",
                            )
                        }
                        delay(500L * attempt)
                    }
                    val streamInfo = client.startScreenStream(selected, maxFps = 12, jpegQuality = 70)
                    info = streamInfo
                    Log.i(TAG, "screen_stream_start attempt=$attempt session=${streamInfo.sessionId} source=${streamInfo.source.id} port=${streamInfo.streamPort}")
                    _state.update {
                        it.copy(
                            screenStreamInfo = streamInfo,
                            selectedScreenSourceId = streamInfo.source.id,
                            screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Negotiated),
                            screenStatus = "Connecting stream...",
                            screenError = null,
                        )
                    }
                    var sawFrame = false
                    screenStreamClient.collect(
                        host = host.address,
                        port = streamInfo.streamPort,
                        token = streamInfo.token,
                        transport = streamInfo.transport,
                    ) { frame ->
                        if (!sawFrame) {
                            sawFrame = true
                            transitionScreenSession(RemoteScreenSessionEvent.FirstFrame)
                        }
                        _state.update {
                            it.copy(
                                screenFrame = frame,
                                screenStreaming = true,
                                screenConnectionState = RemoteScreenConnectionState.Streaming,
                                screenStatus = "Live ${frame.width}x${frame.height} ${frame.byteCount / 1024} KiB",
                                screenError = null,
                            )
                        }
                    }
                }
                info?.sessionId?.let { sessionId ->
                    stopScreenStreamOnHost(sessionId, "cleanup")
                }
                result.onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (!screenStreamDesired || !isActive) {
                        Log.i(TAG, "screen_stream_failure_ignored_after_stop message=${throwable.message}")
                        return@onFailure
                    }
                    Log.w(TAG, "screen_stream_failed attempt=$attempt desired=$screenStreamDesired", throwable)
                    val message = throwable.message ?: throwable::class.java.simpleName
                    val failedState = transitionScreenSession(RemoteScreenSessionEvent.Fail)
                    val willRetry = attempt < SCREEN_STREAM_MAX_RETRIES && screenStreamDesired
                    _state.update {
                        it.copy(
                            screenStreaming = willRetry,
                            screenStreamInfo = null,
                            screenConnectionState = failedState,
                            screenStatus = if (willRetry) {
                                "Screen stream dropped; retrying..."
                            } else {
                                "Screen stream stopped"
                            },
                            screenError = message,
                        )
                    }
                }.onSuccess {
                    Log.i(TAG, "screen_stream_closed attempt=$attempt desired=$screenStreamDesired")
                    if (screenStreamDesired && isActive) {
                        _state.update {
                            it.copy(
                                screenStreaming = false,
                                screenStreamInfo = null,
                                screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Retry),
                                screenStatus = "Screen stream closed; reconnecting...",
                            )
                        }
                    }
                }
            }
            if (!screenStreamDesired) {
                _state.update {
                    it.copy(
                        screenStreaming = false,
                        screenStreamInfo = null,
                        screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Close),
                        screenStatus = "Screen stream idle",
                    )
                }
            } else if (attempt >= SCREEN_STREAM_MAX_RETRIES) {
                _state.update {
                    it.copy(
                        screenStreaming = false,
                        screenStreamInfo = null,
                        screenConnectionState = RemoteScreenConnectionState.Failed,
                        screenStatus = "Screen stream failed after retries",
                    )
                }
            }
        }
    }

    fun stopScreenStream() {
        screenStreamDesired = false
        val sessionId = _state.value.screenStreamInfo?.sessionId
        screenStreamJob?.cancel()
        screenStreamJob = null
        if (sessionId != null) {
            viewModelScope.launch {
                stopScreenStreamOnHost(sessionId, "user_stop")
            }
        }
        _state.update {
            it.copy(
                screenStreaming = false,
                screenStreamInfo = null,
                screenConnectionState = transitionScreenSession(RemoteScreenSessionEvent.Close),
                screenStatus = "Screen stream idle",
            )
        }
    }

    fun setRemoteScreenFullscreen(enabled: Boolean) {
        val current = _state.value.remoteScreenFullscreen
        if (current == enabled) return
        Log.i(TAG, "remote_screen_fullscreen enabled=$enabled")
        _state.update { it.copy(remoteScreenFullscreen = enabled) }
    }

    private suspend fun stopScreenStreamOnHost(sessionId: String, reason: String) {
        withContext(NonCancellable) {
            runCatching { client.stopScreenStream(sessionId) }
                .onSuccess { Log.i(TAG, "screen_stream_stop_sent reason=$reason session=$sessionId") }
                .onFailure { Log.w(TAG, "screen_stream_stop_failed reason=$reason session=$sessionId", it) }
        }
    }

    private fun transitionScreenSession(event: RemoteScreenSessionEvent): RemoteScreenConnectionState {
        val next = screenSessionMachine.transition(event)
        Log.d(TAG, "screen_session_state event=$event state=$next")
        return next
    }

    fun remoteScreenPointerMove(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        enqueueInput(RemoteInputCommand.PointerMoveAbsolute(activeScreenSourceId(), x, y))
    }

    fun remoteScreenClick(x: Float, y: Float, button: PointerButton = PointerButton.Left) {
        remoteScreenPointerMove(x, y)
        pointerButton(button, ButtonState.Pressed)
        pointerButton(button, ButtonState.Released)
    }

    private fun activeScreenSourceId(): String? =
        _state.value.screenStreamInfo?.source?.id ?: _state.value.selectedScreenSourceId

    private fun launchCommand(status: String?, block: suspend () -> Unit) {
        if (status != null) _state.update { it.copy(status = status, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { if (status != null) _state.update { it.copy(status = "Ready") } }
                .onFailure { throwable ->
                    if (throwable.isTransportFailure() && reconnectCurrentHost()) {
                        runCatching { block() }
                            .onSuccess { _state.update { it.copy(status = "Connection restored", error = null) } }
                            .onFailure { fail("Command failed", it) }
                    } else {
                        fail("Command failed", throwable)
                    }
                }
        }
    }

    private fun launchQuiet(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { throwable ->
                    if (throwable.isTransportFailure()) {
                        Log.w(TAG, "transport_failure quiet_command reconnecting=true", throwable)
                        reconnectCurrentHost()
                    } else {
                        fail("Input failed", throwable)
                    }
                }
        }
    }

    private suspend fun reconnectCurrentHost(): Boolean {
        val host = _state.value.connectedHost ?: return false
        Log.i(TAG, "connection_state=reconnecting host=${host.hostName}")
        return runCatching {
            val capabilities = client.connect(host)
            Log.i(TAG, "connection_state=reconnected host=${host.hostName} input_backend=${capabilities.inputBackend}")
            _state.update {
                it.copy(
                    capabilities = capabilities,
                    connectionState = ConnectionState.Connected,
                    status = "Reconnected to ${host.hostName}",
                    error = null,
                )
            }
            publishCurrentExternalDevices()
        }.isSuccess
    }

    private fun fail(prefix: String, throwable: Throwable) {
        Log.e(TAG, "connection_state=error prefix=$prefix", throwable)
        _state.update {
            it.copy(
                connectionState = ConnectionState.Error,
                status = prefix,
                error = throwable.message ?: throwable::class.java.simpleName,
            )
        }
    }

    private fun enqueueInput(command: RemoteInputCommand) {
        val current = _state.value
        if (current.connectedHost == null || current.connectionState == ConnectionState.Disconnected) {
            Log.d(TAG, "input_queue_drop_disconnected command=$command")
            return
        }
        val result = inputCommands.trySend(command)
        if (result.isSuccess) {
            val depth = queuedInputCommands.incrementAndGet()
            updateInputBacklog(depth)
        } else if (command.isTerminal) {
            Log.w(TAG, "input_queue_backpressure command=$command preserving_terminal=true")
            viewModelScope.launch {
                runCatching { inputCommands.send(command) }
                    .onSuccess {
                        val depth = queuedInputCommands.incrementAndGet()
                        updateInputBacklog(depth)
                    }
                    .onFailure {
                        Log.w(TAG, "input_queue_rejected command=$command cause=${it.message}")
                    }
            }
        } else {
            Log.w(TAG, "input_queue_drop_backpressure command=$command cause=${result.exceptionOrNull()?.message}")
        }
    }

    private fun clearQueuedInput() {
        while (inputCommands.tryReceive().isSuccess) {
            // Drain stale gesture commands after explicit disconnect so they cannot reconnect or
            // mutate UI state after the user has left the remote session.
        }
        queuedInputCommands.set(0)
        updateInputBacklog(0)
    }

    private fun startInteractionKeepAlive() {
        if (interactionKeepAliveJob?.isActive == true) return
        interactionKeepAliveJob = viewModelScope.launch {
            delay(INTERACTION_KEEPALIVE_INITIAL_MS)
            while (isActive && _state.value.remoteInputSessionActive) {
                enqueueInput(RemoteInputCommand.Ping)
                delay(INTERACTION_KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private suspend fun drainInputCommands() {
        val pending = ArrayDeque<RemoteInputCommand>()
        while (true) {
            val command = if (pending.isNotEmpty()) {
                pending.removeFirst()
            } else {
                inputCommands.receiveCatching().getOrNull()?.also { markInputDequeued() } ?: break
            }
            if (_state.value.connectedHost == null || _state.value.connectionState == ConnectionState.Disconnected) {
                Log.d(TAG, "input_queue_skip_disconnected command=$command")
                continue
            }
            handleInputCommand(command, pending)
        }
    }

    private suspend fun handleInputCommand(
        command: RemoteInputCommand,
        pending: ArrayDeque<RemoteInputCommand>,
    ) {
        when (command) {
            is RemoteInputCommand.PointerMove -> {
                var dx = command.dx
                var dy = command.dy
                while (true) {
                    val next = pollInputCommand() ?: break
                    if (next is RemoteInputCommand.PointerMove) {
                        dx += next.dx
                        dy += next.dy
                    } else {
                        pending.addFirst(next)
                        break
                    }
                }
                if (abs(dx) + abs(dy) > 0.01f) {
                    Log.v(TAG, "transport_send type=pointer_move dx=$dx dy=$dy")
                    sendInput("pointer_move") { client.pointerMove(dx, dy) }
                }
            }
            is RemoteInputCommand.PointerMoveAbsolute -> {
                var latest: RemoteInputCommand.PointerMoveAbsolute = command
                while (true) {
                    val next = pollInputCommand() ?: break
                    if (next is RemoteInputCommand.PointerMoveAbsolute && next.sourceId == latest.sourceId) {
                        latest = next
                    } else {
                        pending.addFirst(next)
                        break
                    }
                }
                Log.v(TAG, "transport_send type=pointer_move_absolute source=${latest.sourceId} x=${latest.x} y=${latest.y}")
                sendInput("pointer_move_absolute") {
                    client.pointerMoveAbsolute(latest.sourceId, latest.x, latest.y)
                }
            }
            is RemoteInputCommand.Scroll -> {
                var dx = command.dx
                var dy = command.dy
                while (true) {
                    val next = pollInputCommand() ?: break
                    if (next is RemoteInputCommand.Scroll) {
                        dx += next.dx
                        dy += next.dy
                    } else {
                        pending.addFirst(next)
                        break
                    }
                }
                if (abs(dx) + abs(dy) > 0.01f) {
                    Log.v(TAG, "transport_send type=scroll dx=$dx dy=$dy")
                    sendInput("scroll") { client.scroll(dx, dy, finish = false) }
                }
            }
            RemoteInputCommand.ScrollFinish -> {
                Log.d(TAG, "transport_send type=scroll_finish")
                sendInput("scroll_finish") { client.scroll(0f, 0f, finish = true) }
            }
            is RemoteInputCommand.PointerButton -> {
                Log.d(TAG, "transport_send type=pointer_button button=${command.button.wireName} state=${command.state.wireName}")
                sendInput("pointer_button") { client.pointerButton(command.button, command.state) }
            }
            RemoteInputCommand.ReleasePointerButtons -> {
                Log.d(TAG, "transport_send type=release_pointer_buttons")
                sendInput("release_pointer_buttons") {
                    client.pointerButton(PointerButton.Left, ButtonState.Released)
                    client.pointerButton(PointerButton.Right, ButtonState.Released)
                    client.pointerButton(PointerButton.Middle, ButtonState.Released)
                }
            }
            RemoteInputCommand.Ping -> {
                Log.v(TAG, "transport_send type=ping")
                sendInput("ping") { client.ping() }
            }
            is RemoteInputCommand.External -> {
                when (val external = command.event) {
                    is ExternalInputEvent.PointerMove -> {
                        var dx = external.dx
                        var dy = external.dy
                        while (true) {
                            val next = pollInputCommand() ?: break
                            val nextEvent = (next as? RemoteInputCommand.External)?.event
                            if (nextEvent is ExternalInputEvent.PointerMove &&
                                nextEvent.deviceId == external.deviceId
                            ) {
                                dx += nextEvent.dx
                                dy += nextEvent.dy
                            } else {
                                pending.addFirst(next)
                                break
                            }
                        }
                        val coalesced = external.copy(dx = dx, dy = dy)
                        Log.v(TAG, "transport_send type=external_pointer_move device=${coalesced.deviceId} dx=$dx dy=$dy")
                        sendInput("external_pointer_move") { client.externalInput(coalesced) }
                    }
                    is ExternalInputEvent.PointerScroll -> {
                        var dx = external.dx
                        var dy = external.dy
                        var finish = external.finish
                        while (true) {
                            val next = pollInputCommand() ?: break
                            val nextEvent = (next as? RemoteInputCommand.External)?.event
                            if (nextEvent is ExternalInputEvent.PointerScroll &&
                                nextEvent.deviceId == external.deviceId
                            ) {
                                dx += nextEvent.dx
                                dy += nextEvent.dy
                                finish = finish || nextEvent.finish
                            } else {
                                pending.addFirst(next)
                                break
                            }
                        }
                        val coalesced = external.copy(dx = dx, dy = dy, finish = finish)
                        Log.v(TAG, "transport_send type=external_pointer_scroll device=${coalesced.deviceId} dx=$dx dy=$dy finish=$finish")
                        sendInput("external_pointer_scroll") { client.externalInput(coalesced) }
                    }
                    else -> {
                        Log.d(TAG, "transport_send type=external_input event=${external::class.simpleName} device=${external.deviceId}")
                        sendInput("external_input") { client.externalInput(external) }
                    }
                }
            }
        }
    }

    private fun pollInputCommand(): RemoteInputCommand? {
        val command = inputCommands.tryReceive().getOrNull() ?: return null
        markInputDequeued()
        return command
    }

    private fun markInputDequeued() {
        val depth = queuedInputCommands.updateAndGet { (it - 1).coerceAtLeast(0) }
        updateInputBacklog(depth)
    }

    private fun updateInputBacklog(depth: Int) {
        val current = _state.value.remoteInputBacklog
        if (depth == 0 || abs(depth - current) >= 8) {
            _state.update { it.copy(remoteInputBacklog = depth) }
        }
    }

    private suspend fun sendInput(label: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onFailure { throwable ->
                Log.w(TAG, "transport_send_failed type=$label", throwable)
                if (throwable.isTransportFailure() && reconnectCurrentHost()) {
                    runCatching { block() }
                        .onFailure { fail("Input failed", it) }
                } else {
                    fail("Input failed", throwable)
                }
            }
    }

    override fun onCleared() {
        inputCommands.close()
        interactionKeepAliveJob?.cancel()
        interactionEndJob?.cancel()
        screenStreamJob?.cancel()
        client.close()
        super.onCleared()
    }
}

private fun shouldCaptureExternalInput(state: WaypadUiState): Boolean =
    state.connectionState == ConnectionState.Connected &&
        (state.screen == Screen.Remote || state.screen == Screen.RemoteDisplay)

private fun isExternalInputSupported(state: WaypadUiState, event: ExternalInputEvent): Boolean =
    when (event) {
        is ExternalInputEvent.DeviceConnected,
        is ExternalInputEvent.DeviceDisconnected -> true
        is ExternalInputEvent.PointerMove,
        is ExternalInputEvent.PointerButton,
        is ExternalInputEvent.PointerScroll -> state.capabilities.externalPointerSupported
        is ExternalInputEvent.KeyboardKey -> state.capabilities.externalKeyboardSupported
        is ExternalInputEvent.ControllerButton,
        is ExternalInputEvent.ControllerAxis -> state.capabilities.externalControllerSupported
    }

private fun ExternalInputDeviceClass.unsupportedLabel(): String = when (this) {
    ExternalInputDeviceClass.Mouse,
    ExternalInputDeviceClass.Touchpad -> "external pointer"
    ExternalInputDeviceClass.Keyboard -> "external keyboard"
    ExternalInputDeviceClass.Gamepad,
    ExternalInputDeviceClass.Joystick -> "controller"
    ExternalInputDeviceClass.Unknown -> "external input"
}

private fun Set<ExternalInputDeviceClass>.primaryTypeForStatus(): ExternalInputDeviceClass =
    when {
        ExternalInputDeviceClass.Mouse in this -> ExternalInputDeviceClass.Mouse
        ExternalInputDeviceClass.Touchpad in this -> ExternalInputDeviceClass.Touchpad
        ExternalInputDeviceClass.Gamepad in this -> ExternalInputDeviceClass.Gamepad
        ExternalInputDeviceClass.Joystick in this -> ExternalInputDeviceClass.Joystick
        ExternalInputDeviceClass.Keyboard in this -> ExternalInputDeviceClass.Keyboard
        else -> ExternalInputDeviceClass.Unknown
    }

private fun externalInputSummary(devices: List<ExternalInputDeviceSummary>): String =
    if (devices.isEmpty()) {
        "No external input devices detected"
    } else {
        "External devices: " + devices.joinToString { "${it.name} (${it.displayClasses})" }
    }

private sealed interface RemoteInputCommand {
    val isTerminal: Boolean
        get() = this !is RemoteInputCommand.PointerMove &&
            this !is RemoteInputCommand.PointerMoveAbsolute &&
            this !is RemoteInputCommand.Scroll &&
            (this !is RemoteInputCommand.External || !this.event.highFrequency) &&
            this !is RemoteInputCommand.Ping

    data class PointerMove(val dx: Float, val dy: Float) : RemoteInputCommand
    data class PointerMoveAbsolute(val sourceId: String?, val x: Float, val y: Float) : RemoteInputCommand
    data class Scroll(val dx: Float, val dy: Float) : RemoteInputCommand
    data class PointerButton(val button: dev.waypad.android.core.model.PointerButton, val state: ButtonState) : RemoteInputCommand
    data class External(val event: ExternalInputEvent) : RemoteInputCommand
    data object ScrollFinish : RemoteInputCommand
    data object ReleasePointerButtons : RemoteInputCommand
    data object Ping : RemoteInputCommand
}

private fun Throwable.isTransportFailure(): Boolean {
    val text = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()
    return "broken pipe" in text ||
        "connection reset" in text ||
        "connection closed" in text ||
        "socket closed" in text
}
