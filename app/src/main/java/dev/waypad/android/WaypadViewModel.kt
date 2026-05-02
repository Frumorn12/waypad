package dev.waypad.android

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.CapabilitySummary
import dev.waypad.android.core.model.ConnectionState
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.model.TrustedHost
import dev.waypad.android.core.network.WaypadClient
import dev.waypad.android.core.network.WaypadDiscovery
import dev.waypad.android.core.storage.TrustedHostStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen {
    Onboarding,
    Discovery,
    Pairing,
    Remote,
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
)

class WaypadViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = WaypadDiscovery(application)
    private val client = WaypadClient()
    private val store = TrustedHostStore(application)
    private val _state = MutableStateFlow(
        WaypadUiState(trustedHosts = store.load())
    )
    val state: StateFlow<WaypadUiState> = _state

    fun go(screen: Screen) {
        _state.update { it.copy(screen = screen, error = null) }
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
            }.onFailure { fail("Pairing failed", it) }
        }
    }

    fun connect(host: TrustedHost) {
        _state.update { it.copy(connectionState = ConnectionState.Connecting, status = "Connecting to ${host.hostName}...", error = null) }
        viewModelScope.launch {
            runCatching { client.connect(host) }
                .onSuccess { capabilities ->
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
                }
                .onFailure { fail("Connection failed", it) }
        }
    }

    fun disconnect() {
        client.close()
        _state.update {
            it.copy(connectionState = ConnectionState.Disconnected, connectedHost = null, screen = Screen.Discovery, status = "Disconnected")
        }
    }

    fun removeTrustedHost(id: String) {
        store.remove(id)
        _state.update { it.copy(trustedHosts = store.load()) }
    }

    fun prepareInput() = launchCommand("Requesting portal approval...") { client.prepareInput() }

    fun pointerMove(dx: Float, dy: Float) = launchQuiet { client.pointerMove(dx, dy) }

    fun pointerButton(button: PointerButton, state: ButtonState) {
        if (_state.value.capabilities.inputBackend == "hyprland-hyprctl") {
            if (state == ButtonState.Pressed) {
                _state.update { it.copy(status = "Hyprland fallback moves the pointer only; clicks need RemoteDesktop portal.") }
            }
            return
        }
        launchCommand(null) { client.pointerButton(button, state) }
    }

    fun scroll(dx: Float, dy: Float, finish: Boolean = false) {
        if (_state.value.capabilities.inputBackend == "hyprland-hyprctl") {
            if (!finish) {
                _state.update { it.copy(status = "Scroll needs RemoteDesktop portal on Hyprland.") }
            }
            return
        }
        launchQuiet { client.scroll(dx, dy, finish) }
    }

    fun sendText(text: String) = launchCommand("Sending text...") { client.text(text) }

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
                        reconnectCurrentHost()
                    } else {
                        fail("Input failed", throwable)
                    }
                }
        }
    }

    private suspend fun reconnectCurrentHost(): Boolean {
        val host = _state.value.connectedHost ?: return false
        return runCatching {
            val capabilities = client.connect(host)
            _state.update {
                it.copy(
                    capabilities = capabilities,
                    connectionState = ConnectionState.Connected,
                    status = "Reconnected to ${host.hostName}",
                    error = null,
                )
            }
        }.isSuccess
    }

    private fun fail(prefix: String, throwable: Throwable) {
        _state.update {
            it.copy(
                connectionState = ConnectionState.Error,
                status = prefix,
                error = throwable.message ?: throwable::class.java.simpleName,
            )
        }
    }
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
