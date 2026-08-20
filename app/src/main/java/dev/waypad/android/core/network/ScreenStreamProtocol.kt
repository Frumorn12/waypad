package dev.waypad.android.core.network

/**
 * Wire protocol of the Waypad screen stream socket.
 *
 * Framing is identical for every version:
 * ```
 * [u32 big-endian headerLen][u32 big-endian payloadLen][header JSON UTF-8][payload]
 * ```
 * The handshake line advertised by the daemon selects the payload codec.
 */
object ScreenStreamProtocol {
    const val CODEC_H264 = "h264"
    const val CODEC_JPEG = "jpeg"

    /** Transport that multiplexes the stream on the control port and expects a JSON attach line. */
    const val TRANSPORT_CONTROL_PORT_V2 = "waypad-control-port-stream-v2"

    const val MAX_HANDSHAKE_BYTES = 128
    const val MAX_HEADER_BYTES = 8192
    const val MAX_PAYLOAD_BYTES = 20_971_520

    /** Annex-B start code that prefixes every NAL unit of an h264 payload. */
    val ANNEX_B_START_CODE = byteArrayOf(0, 0, 0, 1)

    /**
     * The line written right after connecting so the daemon can bind the socket to a stream
     * session. The v2 control-port transport expects a JSON envelope, every other transport a bare
     * token. The trailing newline is part of the contract.
     */
    fun attachLine(token: String, transport: String): String =
        if (transport == TRANSPORT_CONTROL_PORT_V2) {
            "{\"type\":\"stream_connect\",\"token\":\"${FlatJson.escape(token)}\"}\n"
        } else {
            "$token\n"
        }

    fun parseHeader(bytes: ByteArray, defaultCodec: String = CODEC_JPEG): StreamFrameHeader =
        parseHeader(String(bytes, Charsets.UTF_8), defaultCodec)

    fun parseHeader(json: String, defaultCodec: String = CODEC_JPEG): StreamFrameHeader {
        val fields = FlatJson.parseObject(json)
        return StreamFrameHeader(
            seq = fields.longValue("seq", 0L),
            timestampMs = fields.longValue("timestamp_ms", 0L),
            width = fields.intValue("width", 0),
            height = fields.intValue("height", 0),
            codec = fields.stringValue("codec", defaultCodec).lowercase(),
            keyFrame = fields.booleanValue("key_frame", false),
            config = fields.booleanValue("config", false),
        )
    }

    fun requireValidHeaderLength(length: Int) {
        if (length !in 1..MAX_HEADER_BYTES) {
            throw RemoteScreenTransportException("Invalid screen frame header length: $length")
        }
    }

    fun requireValidPayloadLength(length: Int) {
        if (length !in 1..MAX_PAYLOAD_BYTES) {
            throw RemoteScreenTransportException("Invalid screen frame payload length: $length")
        }
    }
}

/** Handshake line advertised by the daemon on the first line of the stream socket. */
enum class StreamProtocolVersion(val magic: String, val defaultCodec: String) {
    /** Software JPEG frames, one full image per envelope. */
    V1("WAYPAD_STREAM_V1", ScreenStreamProtocol.CODEC_JPEG),

    /** Annex-B h264 NAL units decoded by [android.media.MediaCodec]. */
    V2("WAYPAD_STREAM_V2", ScreenStreamProtocol.CODEC_H264);

    companion object {
        fun fromMagic(magic: String): StreamProtocolVersion? {
            val trimmed = magic.trim()
            return entries.firstOrNull { it.magic == trimmed }
        }
    }
}

/** Metadata of a single stream envelope, decoded from the header JSON. */
data class StreamFrameHeader(
    val seq: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val codec: String,
    val keyFrame: Boolean,
    val config: Boolean,
) {
    val isH264: Boolean
        get() = codec == ScreenStreamProtocol.CODEC_H264 || codec == "avc" || codec == "avc1"

    val isJpeg: Boolean
        get() = codec.isBlank() || codec == ScreenStreamProtocol.CODEC_JPEG || codec == "jpg" || codec == "mjpeg"

    /** A codec-config envelope carries SPS/PPS and must never be dropped. */
    val isDroppable: Boolean
        get() = !config
}
