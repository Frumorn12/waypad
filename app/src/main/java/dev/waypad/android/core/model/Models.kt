package dev.waypad.android.core.model

data class DiscoveredHost(
    val hostName: String,
    val address: String,
    val port: Int,
    val fingerprint: String,
    val inputSupported: Boolean,
    val inputBackend: String,
    val captureSupported: Boolean = false,
    val captureBackend: String = "unknown",
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
    val captureSupported: Boolean = false,
    val captureReason: String = "Not connected",
    val captureBackend: String = "unknown",
    val captureRequiresApproval: Boolean = false,
    val volume: Boolean = false,
    val media: Boolean = false,
    val brightness: Boolean = false,
    val clipboard: Boolean = false,
    val lock: Boolean = false,
    val suspend: Boolean = false,
)

data class ScreenSource(
    val id: String,
    val label: String,
    val kind: String,
    val backend: String,
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val scale: Double,
    val focused: Boolean,
)

data class ScreenStreamInfo(
    val sessionId: String,
    val streamPort: Int,
    val token: String,
    val codec: String,
    val transport: String,
    val source: ScreenSource,
)

enum class RemoteScreenConnectionState {
    Idle,
    Connecting,
    Negotiating,
    Streaming,
    Reconnecting,
    Failed,
    Closed,
}

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
