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
    val endpoints: List<WaypadInviteEndpoint> = listOf(WaypadInviteEndpoint(address, port, route)),
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

    fun toCandidateHosts(): List<DiscoveredHost> =
        endpoints.ifEmpty { listOf(WaypadInviteEndpoint(address, port, route)) }
            .map { endpoint ->
                DiscoveredHost(
                    hostName = hostName.ifBlank { endpoint.address },
                    address = endpoint.address,
                    port = endpoint.port,
                    fingerprint = fingerprint,
                    inputSupported = false,
                    inputBackend = "invite/${endpoint.route}",
                    captureSupported = false,
                    captureBackend = "invite/${endpoint.route}",
                )
            }

    companion object {
        fun parse(raw: String): WaypadInvite {
            val uri = URI(raw.trim())
            require(uri.scheme == "waypad" && uri.host == "invite") {
                "Unsupported Waypad invite format"
            }
            val query = parseQuery(uri.rawQuery.orEmpty())
            val port = query["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 47771
            val endpoints = buildList {
                query["remote_address"]?.takeIf(String::isNotBlank)?.let {
                    add(WaypadInviteEndpoint(it, port, "direct-public"))
                }
                query["address"]?.takeIf(String::isNotBlank)?.let {
                    val route = if (isPrivateLanAddress(it)) "direct-lan" else "direct-public"
                    add(WaypadInviteEndpoint(it, port, route))
                }
                query["lan_address"]?.takeIf(String::isNotBlank)?.let {
                    add(WaypadInviteEndpoint(it, port, "direct-lan"))
                }
            }.distinctBy { "${it.address}:${it.port}" }
            val primary = endpoints.firstOrNull() ?: throw IllegalArgumentException("Invite is missing host address")
            val code = query["code"].orEmpty().filter(Char::isDigit).take(6)
            require(code.length == 6) { "Invite is missing a 6 digit pairing code" }
            return WaypadInvite(
                hostName = query["host"].orEmpty(),
                address = primary.address,
                port = primary.port,
                fingerprint = query["fingerprint"].orEmpty(),
                pairingCode = code,
                expiresAt = query["expires"]?.toLongOrNull() ?: 0L,
                route = query["route"].orEmpty().ifBlank { primary.route },
                endpoints = endpoints,
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

data class WaypadInviteEndpoint(
    val address: String,
    val port: Int,
    val route: String,
)

private fun isPrivateLanAddress(address: String): Boolean =
    address.startsWith("10.") ||
        address.startsWith("192.168.") ||
        Regex("""^172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(address) ||
        address == "localhost" ||
        address.startsWith("127.")
