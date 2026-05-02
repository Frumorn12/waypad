package dev.waypad.android.core.network

import android.util.Base64
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object WaypadCrypto {
    fun generateEcDhKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun publicKeyToUncompressedPoint(publicKey: PublicKey): ByteArray {
        val ec = publicKey as ECPublicKey
        return encodePoint(ec.w, ec.params)
    }

    fun parseUncompressedPoint(bytes: ByteArray): PublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "Invalid P-256 public key" }
        val params = p256Params()
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        val spec = ECPublicKeySpec(ECPoint(x, y), params)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    fun ecdh(privateKey: java.security.PrivateKey, peerPublicPoint: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(parseUncompressedPoint(peerPublicPoint), true)
        return agreement.generateSecret()
    }

    fun verifyHandshake(
        hostPublicPoint: ByteArray,
        clientEphemeral: ByteArray,
        serverEphemeral: ByteArray,
        sessionNonce: ByteArray,
        signatureBytes: ByteArray,
    ): Boolean {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(parseUncompressedPoint(hostPublicPoint))
        signature.update("WAYPAD-HANDSHAKE-v1".toByteArray(Charsets.UTF_8))
        signature.update(clientEphemeral)
        signature.update(serverEphemeral)
        signature.update(sessionNonce)
        return signature.verify(signatureBytes)
    }

    fun deriveKeys(sharedSecret: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmacSha256(salt, sharedSecret)
        val c2s = hkdfExpand(prk, "waypad v1 c2s".toByteArray(Charsets.UTF_8), 32)
        val s2c = hkdfExpand(prk, "waypad v1 s2c".toByteArray(Charsets.UTF_8), 32)
        return c2s to s2c
    }

    fun encrypt(key: ByteArray, prefix: ByteArray, seq: Long, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce(prefix, seq)))
        cipher.updateAAD(seq.toBytes())
        return cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, prefix: ByteArray, seq: Long, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce(prefix, seq)))
        cipher.updateAAD(seq.toBytes())
        return cipher.doFinal(ciphertext)
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64Decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    fun fingerprint(publicKey: ByteArray): String {
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(publicKey)
            .joinToString("") { "%02x".format(it) }
        return hex.chunked(4).joinToString(":")
    }

    private fun p256Params(): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun encodePoint(point: ECPoint, params: ECParameterSpec): ByteArray {
        val fieldSize = (params.curve.field.fieldSize + 7) / 8
        return byteArrayOf(0x04) + point.affineX.toFixed(fieldSize) + point.affineY.toFixed(fieldSize)
    }

    private fun BigInteger.toFixed(size: Int): ByteArray {
        val raw = toByteArray()
        val unsigned = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        return ByteArray(size - unsigned.size) + unsigned
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ArrayList<Byte>()
        var previous = ByteArray(0)
        var counter = 1
        while (output.size < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            previous.forEach { output.add(it) }
            counter++
        }
        return output.take(length).toByteArray()
    }

    private fun nonce(prefix: ByteArray, seq: Long): ByteArray {
        require(prefix.size == 4)
        return prefix + seq.toBytes()
    }

    private fun Long.toBytes(): ByteArray =
        ByteArray(8) { index -> ((this ushr ((7 - index) * 8)) and 0xff).toByte() }
}
