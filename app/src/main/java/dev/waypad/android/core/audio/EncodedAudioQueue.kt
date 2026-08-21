package dev.waypad.android.core.audio

/**
 * Bounded backlog between the socket reader and the Opus decoder.
 *
 * The reader never blocks on the decoder. When packets pile up the queue drops from the **head**,
 * because in audio the oldest packet is the one that is already too late to matter — the opposite of
 * a video queue, which has to keep the oldest key frame to stay decodable.
 */
class EncodedAudioQueue(private val capacity: Int = DEFAULT_CAPACITY) {

    private val packets = ArrayDeque<EncodedAudioPacket>(capacity + 1)

    @Volatile
    var droppedPackets: Long = 0L
        private set

    @Synchronized
    fun offer(packet: EncodedAudioPacket) {
        packets.addLast(packet)
        var dropped = 0
        while (packets.size > capacity) {
            packets.removeFirst()
            dropped++
        }
        if (dropped > 0) droppedPackets += dropped
    }

    /** Discards the [count] oldest packets, e.g. when the playout buffer ran long. */
    @Synchronized
    fun dropOldest(count: Int): Int {
        var dropped = 0
        while (dropped < count && packets.isNotEmpty()) {
            packets.removeFirst()
            dropped++
        }
        if (dropped > 0) droppedPackets += dropped
        return dropped
    }

    @Synchronized
    fun poll(): EncodedAudioPacket? = packets.removeFirstOrNull()

    @Synchronized
    fun peek(): EncodedAudioPacket? = packets.firstOrNull()

    @Synchronized
    fun size(): Int = packets.size

    @Synchronized
    fun clear() {
        packets.clear()
    }

    companion object {
        /** Roughly half a second of 20 ms packets: enough for a Wi-Fi hiccup, not enough to drift. */
        const val DEFAULT_CAPACITY = 24
    }
}
