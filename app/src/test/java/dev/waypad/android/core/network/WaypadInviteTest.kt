package dev.waypad.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WaypadInviteTest {
    @Test
    fun parsesDirectInviteUri() {
        val invite = WaypadInvite.parse(
            "waypad://invite?v=1&host=pc&address=192.0.2.10&port=47771&code=123456&fingerprint=abcd&expires=42",
        )

        assertEquals("pc", invite.hostName)
        assertEquals("192.0.2.10", invite.address)
        assertEquals(47771, invite.port)
        assertEquals("123456", invite.pairingCode)
        assertEquals("abcd", invite.fingerprint)
        assertEquals(42L, invite.expiresAt)
        assertEquals(listOf("direct-public"), invite.endpoints.map { it.route })
    }

    @Test
    fun prefersRemoteAddressForMobileDataInvites() {
        val invite = WaypadInvite.parse(
            "waypad://invite?lan_address=192.168.1.20&remote_address=203.0.113.44&port=47771&code=654321",
        )

        assertEquals("203.0.113.44", invite.address)
    }

    @Test
    fun keepsRemoteAndLanCandidatesForFallback() {
        val invite = WaypadInvite.parse(
            "waypad://invite?lan_address=192.168.1.20&remote_address=pc.example.test&port=47771&code=654321",
        )

        assertEquals(listOf("pc.example.test", "192.168.1.20"), invite.endpoints.map { it.address })
        assertEquals(listOf("direct-public", "direct-lan"), invite.endpoints.map { it.route })
    }
}
