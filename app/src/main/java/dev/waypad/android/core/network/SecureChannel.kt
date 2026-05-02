package dev.waypad.android.core.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import java.util.UUID

class SecureChannel private constructor(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    private val c2sKey: ByteArray,
    private val s2cKey: ByteArray,
    val hostFingerprint: String,
    val hostPublicKey: String,
) : AutoCloseable {
    private var sendSeq = 0L
    private var recvSeq = 0L

    fun send(message: JSONObject) {
        val plaintext = message.toString().toByteArray(Charsets.UTF_8)
        val ciphertext = WaypadCrypto.encrypt(c2sKey, byteArrayOf(0x43, 0x32, 0x53, 0x00), sendSeq, plaintext)
        val frame = JSONObject()
            .put("seq", sendSeq)
            .put("ciphertext", WaypadCrypto.b64(ciphertext))
        sendSeq++
        writer.write(frame.toString())
        writer.write("\n")
        writer.flush()
    }

    fun receive(): JSONObject {
        val line = reader.readLine() ?: error("Connection closed")
        val frame = JSONObject(line)
        val seq = frame.getLong("seq")
        check(seq == recvSeq) { "Replay or out-of-order frame: got $seq expected $recvSeq" }
        recvSeq++
        val ciphertext = WaypadCrypto.b64Decode(frame.getString("ciphertext"))
        val plaintext = WaypadCrypto.decrypt(s2cKey, byteArrayOf(0x53, 0x32, 0x43, 0x00), seq, ciphertext)
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    override fun close() {
        socket.close()
    }

    companion object {
        fun connect(host: String, port: Int, expectedFingerprint: String? = null): SecureChannel {
            val socket = Socket(host, port)
            socket.tcpNoDelay = true
            socket.soTimeout = 30_000
            val reader = socket.getInputStream().bufferedReader()
            val writer = socket.getOutputStream().bufferedWriter()

            val keyPair = WaypadCrypto.generateEcDhKeyPair()
            val clientPublic = WaypadCrypto.publicKeyToUncompressedPoint(keyPair.public)
            val hello = JSONObject()
                .put("type", "client_hello")
                .put("protocol", 1)
                .put("client_ephemeral_pub", WaypadCrypto.b64(clientPublic))
                .put("device_id", JSONObject.NULL)
            writer.write(hello.toString())
            writer.write("\n")
            writer.flush()

            val responseLine = reader.readLine() ?: error("No server hello received")
            val response = JSONObject(responseLine)
            if (response.getString("type") == "error") {
                error(response.getString("message"))
            }
            check(response.getString("type") == "server_hello") { "Unexpected handshake response" }
            check(response.getInt("protocol") == 1) { "Unsupported protocol version" }

            val hostPublic = WaypadCrypto.b64Decode(response.getString("host_public_key"))
            val fingerprint = WaypadCrypto.fingerprint(hostPublic)
            val advertisedFingerprint = response.getString("host_fingerprint")
            check(fingerprint == advertisedFingerprint) { "Host fingerprint does not match host key" }
            if (expectedFingerprint != null) {
                check(fingerprint == expectedFingerprint) { "Host fingerprint changed. Refusing to connect." }
            }

            val serverEphemeral = WaypadCrypto.b64Decode(response.getString("server_ephemeral_pub"))
            val sessionNonce = WaypadCrypto.b64Decode(response.getString("session_nonce"))
            val signature = WaypadCrypto.b64Decode(response.getString("signature"))
            check(
                WaypadCrypto.verifyHandshake(
                    hostPublic,
                    clientPublic,
                    serverEphemeral,
                    sessionNonce,
                    signature,
                )
            ) { "Server handshake signature is invalid" }

            val shared = WaypadCrypto.ecdh(keyPair.private, serverEphemeral)
            val (c2s, s2c) = WaypadCrypto.deriveKeys(shared, sessionNonce)
            return SecureChannel(socket, reader, writer, c2s, s2c, fingerprint, response.getString("host_public_key"))
        }
    }
}

fun requestId(): String = UUID.randomUUID().toString()
