package dev.waypad.android.core.model

data class DiscoveredHost(
    val hostName: String,
    val address: String,
    val port: Int,
    val fingerprint: String,
    val inputSupported: Boolean,
    val inputBackend: String,
)

data class TrustedHost(
    val id: String,
    val hostName: String,
    val address: String,
    val port: Int,
    val fingerprint: String,
    val deviceId: String,
    val sessionToken: String,
    val lastConnectedAt: Long,
)

data class CapabilitySummary(
    val inputSupported: Boolean = false,
    val inputReason: String = "Not connected",
    val inputBackend: String = "unknown",
    val volume: Boolean = false,
    val media: Boolean = false,
    val brightness: Boolean = false,
    val clipboard: Boolean = false,
    val lock: Boolean = false,
    val suspend: Boolean = false,
)

enum class ConnectionState {
    Disconnected,
    Discovering,
    Pairing,
    Connecting,
    Connected,
    Error,
}

enum class PointerButton(val wireName: String) {
    Left("left"),
    Middle("middle"),
    Right("right"),
}

enum class ButtonState(val wireName: String) {
    Pressed("pressed"),
    Released("released"),
}
