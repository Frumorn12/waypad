package dev.waypad.android.core.network

/**
 * Raised when the screen stream socket misbehaves: a bad handshake, a malformed envelope, or a
 * peer that stops sending. Distinguishes transport trouble, which is worth retrying, from decoder
 * failures, which are not.
 */
class RemoteScreenTransportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
