package dev.waypad.android.core.network

import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.CapabilitySummary
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.model.TrustedHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WaypadClient {
    private companion object {
        const val MAX_ONE_WAY_POINTER_RESPONSES = 4
    }

    private val transportMutex = Mutex()
    private val oneWayResponseIds = ArrayDeque<String>()
    private var channel: SecureChannel? = null

    suspend fun pair(
        address: String,
        port: Int,
        deviceName: String,
        pairingCode: String,
        expectedFingerprint: String? = null,
    ): Pair<TrustedHost, CapabilitySummary> = withContext(Dispatchers.IO) { transportMutex.withLock {
        channel?.close()
        oneWayResponseIds.clear()
        val ch = SecureChannel.connect(address, port, expectedFingerprint)
        val id = requestId()
        ch.send(
            JSONObject()
                .put("type", "pair_request")
                .put("request_id", id)
                .put("device_name", deviceName)
                .put("pairing_code", pairingCode)
                .put("app_version", "0.1.0")
        )
        val response = ch.receiveResponse(id)
        val data = response.getJSONObject("data")
        val host = TrustedHost(
            id = "${address}:${port}",
            hostName = data.optString("host_name", address),
            address = address,
            port = port,
            fingerprint = data.getString("host_fingerprint"),
            deviceId = data.getString("device_id"),
            sessionToken = data.getString("session_token"),
            lastConnectedAt = System.currentTimeMillis(),
        )
        channel = ch
        host to data.optJSONObject("capabilities").toCapabilitySummary()
    } }

    suspend fun connect(host: TrustedHost): CapabilitySummary = withContext(Dispatchers.IO) { transportMutex.withLock {
        channel?.close()
        oneWayResponseIds.clear()
        val ch = SecureChannel.connect(host.address, host.port, host.fingerprint)
        val id = requestId()
        ch.send(
            JSONObject()
                .put("type", "auth_request")
                .put("request_id", id)
                .put("device_id", host.deviceId)
                .put("session_token", host.sessionToken)
                .put("app_version", "0.1.0")
        )
        val response = ch.receiveResponse(id)
        channel = ch
        response.getJSONObject("data").optJSONObject("capabilities").toCapabilitySummary()
    } }

    suspend fun prepareInput() {
        command("prepare_input")
    }

    suspend fun ping() {
        withContext(Dispatchers.IO) { transportMutex.withLock {
            val ch = channel ?: error("Not connected")
            val id = requestId()
            ch.send(JSONObject().put("type", "ping").put("request_id", id))
            ch.receiveResponse(id)
        } }
    }

    suspend fun pointerMove(dx: Float, dy: Float) {
        commandOneWay("pointer_move", JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble()))
    }

    suspend fun pointerButton(button: PointerButton, state: ButtonState) {
        command(
            "pointer_button",
            JSONObject().put("button", button.wireName).put("state", state.wireName),
        )
    }

    suspend fun scroll(dx: Float, dy: Float, finish: Boolean = false) {
        val body = JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble()).put("finish", finish)
        if (finish) {
            command("scroll", body)
        } else {
            commandOneWay("scroll", body)
        }
    }

    suspend fun text(text: String) {
        command("text", JSONObject().put("text", text))
    }

    suspend fun shortcut(vararg keys: String) {
        command("shortcut", JSONObject().put("keys", JSONArray(keys.toList())))
    }

    suspend fun media(action: String) {
        command("media", JSONObject().put("action", action))
    }

    suspend fun volume(action: String) {
        command("volume", JSONObject().put("action", action))
    }

    suspend fun brightness(action: String) {
        command("brightness", JSONObject().put("action", action))
    }

    suspend fun system(action: String) {
        command("system", JSONObject().put("action", action))
    }

    suspend fun clipboard(text: String) {
        command("clipboard_set", JSONObject().put("text", text))
    }

    suspend fun capabilities(): CapabilitySummary = withContext(Dispatchers.IO) {
        command("get_capabilities")?.toCapabilitySummary() ?: CapabilitySummary()
    }

    fun close() {
        channel?.close()
        channel = null
        oneWayResponseIds.clear()
    }

    private suspend fun command(name: String, body: JSONObject = JSONObject()): JSONObject? = withContext(Dispatchers.IO) { transportMutex.withLock {
        val ch = channel ?: error("Not connected")
        val id = requestId()
        val command = JSONObject(body.toString()).put("name", name)
        ch.send(JSONObject().put("type", "command").put("request_id", id).put("command", command))
        ch.receiveResponse(id).optJSONObject("data")
    } }

    private suspend fun commandOneWay(name: String, body: JSONObject = JSONObject()) = withContext(Dispatchers.IO) { transportMutex.withLock {
        val ch = channel ?: error("Not connected")
        val id = requestId()
        val command = JSONObject(body.toString()).put("name", name)
        ch.send(JSONObject().put("type", "command").put("request_id", id).put("command", command))
        oneWayResponseIds.addLast(id)
        while (oneWayResponseIds.size > MAX_ONE_WAY_POINTER_RESPONSES) {
            ch.receiveResponse(oneWayResponseIds.removeFirst())
        }
    } }

    private fun SecureChannel.receiveResponse(id: String): JSONObject {
        while (true) {
            val response = receive()
            check(response.getString("type") == "response") { "Unexpected server message" }
            val responseId = response.getString("request_id")
            if (responseId != id) {
                if (oneWayResponseIds.remove(responseId)) {
                    if (!response.getBoolean("ok")) {
                        val error = response.getJSONObject("error")
                        throw IllegalStateException(error.getString("message"))
                    }
                    continue
                }
                error("Response ID mismatch")
            }
            if (!response.getBoolean("ok")) {
                val error = response.getJSONObject("error")
                throw IllegalStateException(error.getString("message"))
            }
            oneWayResponseIds.remove(responseId)
            return response
        }
    }
}

private fun JSONObject?.toCapabilitySummary(): CapabilitySummary {
    if (this == null) return CapabilitySummary()
    val input = optJSONObject("input")
    val system = optJSONObject("system")
    return CapabilitySummary(
        inputSupported = input?.optBoolean("supported") ?: false,
        inputReason = input?.optString("reason", "No input capability data") ?: "No input capability data",
        inputBackend = input?.optString("backend", "unknown") ?: "unknown",
        volume = system?.optBoolean("volume") ?: false,
        media = system?.optBoolean("media") ?: false,
        brightness = system?.optBoolean("brightness") ?: false,
        clipboard = system?.optBoolean("clipboard") ?: false,
        lock = system?.optBoolean("lock") ?: false,
        suspend = system?.optBoolean("suspend") ?: false,
    )
}
