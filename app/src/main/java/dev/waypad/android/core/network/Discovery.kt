package dev.waypad.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import dev.waypad.android.core.model.DiscoveredHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

class WaypadDiscovery(context: Context) {
    private val appContext = context.applicationContext

    suspend fun discover(timeoutMillis: Int = 1800): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, DiscoveredHost>()
        val wifiLock = multicastLock()
        wifiLock?.setReferenceCounted(false)
        wifiLock?.acquire()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.reuseAddress = true
                socket.soTimeout = 350
                val payload = "WAYPAD_DISCOVER_V1".toByteArray(Charsets.UTF_8)
                val targets = discoveryTargets()
                repeat(3) {
                    targets.forEach { target ->
                        val packet = DatagramPacket(payload, payload.size, target, 47770)
                        socket.send(packet)
                    }
                    Thread.sleep(120)
                }

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
                                captureSupported = json.optBoolean("capture_supported"),
                                captureBackend = json.optString("capture_backend", "unknown"),
                            )
                            results["${host.address}:${host.port}"] = host
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Continue until the overall discovery window closes.
                    }
                }
            }
        } finally {
            if (wifiLock?.isHeld == true) wifiLock.release()
        }
        results.values.toList()
    }

    private fun multicastLock(): WifiManager.MulticastLock? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return wifi.createMulticastLock("waypad-discovery")
    }

    private fun discoveryTargets(): Set<InetAddress> {
        val targets = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return targets
        connectivity.allNetworks
            .mapNotNull { network -> connectivity.getLinkProperties(network) }
            .flatMap { properties -> properties.linkAddresses }
            .mapNotNullTo(targets) { address -> address.broadcastAddress() }
        return targets
    }

    private fun LinkAddress.broadcastAddress(): InetAddress? {
        val ipv4 = address as? Inet4Address ?: return null
        val raw = ipv4.address
        val ip = ((raw[0].toInt() and 0xff) shl 24) or
            ((raw[1].toInt() and 0xff) shl 16) or
            ((raw[2].toInt() and 0xff) shl 8) or
            (raw[3].toInt() and 0xff)
        val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
        val broadcast = ip or mask.inv()
        val bytes = byteArrayOf(
            ((broadcast ushr 24) and 0xff).toByte(),
            ((broadcast ushr 16) and 0xff).toByte(),
            ((broadcast ushr 8) and 0xff).toByte(),
            (broadcast and 0xff).toByte(),
        )
        return InetAddress.getByAddress(bytes)
    }
}
