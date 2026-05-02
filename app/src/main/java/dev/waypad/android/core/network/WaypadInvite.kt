package dev.waypad.android.core.network

import dev.waypad.android.core.model.DiscoveredHost
import java.net.URI
import java.net.URLDecoder

data class WaypadInvite(
    val hostName: String,
    val address: String,
    val port: Int,
    val fingerprint: String,
    val pairingCode: String,
    val expiresAt: Long,
    val route: String,
) {
    fun toDiscoveredHost(): DiscoveredHost = DiscoveredHost(
        hostName = hostName.ifBlank { address },
        address = address,
        port = port,
        fingerprint = fingerprint,
        inputSupported = false,
        inputBackend = "invite",
        captureSupported = false,
        captureBackend = "invite",
    )

    companion object {
        fun parse(raw: String): WaypadInvite {
            val uri = URI(raw.trim())
            require(uri.scheme == "waypad" && uri.host == "invite") {
                "Unsupported Waypad invite format"
            }
            val query = parseQuery(uri.rawQuery.orEmpty())
            val address = query["remote_address"]
                ?.takeIf(String::isNotBlank)
                ?: query["address"]
                ?: query["lan_address"]
                ?: throw IllegalArgumentException("Invite is missing host address")
            val code = query["code"].orEmpty().filter(Char::isDigit).take(6)
            require(code.length == 6) { "Invite is missing a 6 digit pairing code" }
            return WaypadInvite(
                hostName = query["host"].orEmpty(),
                address = address,
                port = query["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 47771,
                fingerprint = query["fingerprint"].orEmpty(),
                pairingCode = code,
                expiresAt = query["expires"]?.toLongOrNull() ?: 0L,
                route = query["route"].orEmpty().ifBlank { "direct" },
            )
        }

        private fun parseQuery(raw: String): Map<String, String> =
            raw.split('&')
                .asSequence()
                .filter { it.isNotBlank() }
                .map { part ->
                    val key = part.substringBefore('=')
                    val value = part.substringAfter('=', "")
                    decode(key) to decode(value)
                }
                .toMap()

        private fun decode(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
