package dev.waypad.android.core.network

import dev.waypad.android.core.model.DiscoveredHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WaypadDiscovery {
    suspend fun discover(timeoutMillis: Int = 1800): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, DiscoveredHost>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 350
            val payload = "WAYPAD_DISCOVER_V1".toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 47770)
            socket.send(packet)

            val deadline = System.currentTimeMillis() + timeoutMillis
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val reply = DatagramPacket(buffer, buffer.size)
                    socket.receive(reply)
                    val json = JSONObject(String(reply.data, 0, reply.length, Charsets.UTF_8))
                    if (json.optString("service") == "dev.waypad.daemon") {
                        val address = reply.address.hostAddress ?: continue
                        val host = DiscoveredHost(
                            hostName = json.optString("host_name", address),
                            address = address,
                            port = json.optInt("control_port", 47771),
                            fingerprint = json.optString("host_fingerprint"),
                            inputSupported = json.optBoolean("input_supported"),
                            inputBackend = json.optString("input_backend", "unknown"),
                        )
                        results["${host.address}:${host.port}"] = host
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Continue until the overall discovery window closes.
                }
            }
        }
        results.values.toList()
    }
}
