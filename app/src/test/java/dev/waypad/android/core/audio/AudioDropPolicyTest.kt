package dev.waypad.android.core.audio

import dev.waypad.android.core.network.ScreenStreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDropPolicyTest {

    @Test
    fun measuresTheBacklogFromTheTrackPlaybackHead() {
        // 4800 frames still unplayed at 48 kHz is 100 ms of latency.
        assertEquals(100L, AudioDropPolicy.bufferedMs(framesWritten = 14_400, framesPlayed = 9_600, sampleRate = 48_000))
        assertEquals(0L, AudioDropPolicy.bufferedMs(framesWritten = 100, framesPlayed = 500, sampleRate = 48_000))
        assertEquals(0L, AudioDropPolicy.bufferedMs(framesWritten = 4_800, framesPlayed = 0, sampleRate = 0))
    }

    @Test
    fun toleratesJitterButCutsARealBacklog() {
        // Below the ceiling nothing is dropped: a small buffer is what absorbs Wi-Fi jitter.
        assertEquals(0, AudioDropPolicy.packetsToDrop(bufferedMs = 100, packetMs = 20, maxMs = 180, targetMs = 90))
        assertEquals(0, AudioDropPolicy.packetsToDrop(bufferedMs = 180, packetMs = 20, maxMs = 180, targetMs = 90))

        // 300 ms buffered has to come back to the 90 ms target: 210 ms is ten and a half packets.
        assertEquals(11, AudioDropPolicy.packetsToDrop(bufferedMs = 300, packetMs = 20, maxMs = 180, targetMs = 90))
    }

    @Test
    fun roundsUpSoTheGuardCannotStayArmed() {
        // 200 - 90 = 110 ms, which is 5.5 packets: keeping the fraction would leave the backlog
        // above target and re-trigger on every packet.
        assertEquals(6, AudioDropPolicy.packetsToDrop(bufferedMs = 200, packetMs = 20, maxMs = 180, targetMs = 90))
        assertEquals(0, AudioDropPolicy.packetsToDrop(bufferedMs = 500, packetMs = 0))
    }

    @Test
    fun `the shipped thresholds leave room for a burst of eight packets`() {
        // The socket reader hands over whatever the link buffered as one batch — eight 20 ms
        // packets were observed on Wi-Fi. A ceiling below that would fire on ordinary jitter and
        // starve the track between cuts, which is exactly the drop-and-underrun pattern this guard
        // is supposed to prevent.
        assertTrue(AudioDropPolicy.DEFAULT_MAX_BUFFERED_MS >= 8 * 20)
        assertTrue(AudioDropPolicy.DEFAULT_TARGET_BUFFERED_MS < AudioDropPolicy.DEFAULT_MAX_BUFFERED_MS)
        assertEquals(0, AudioDropPolicy.packetsToDrop(bufferedMs = 8 * 20, packetMs = 20))
    }

    @Test
    fun queueDropsTheOldestPacketsBecauseTheyAreTheLateOnes() {
        val queue = EncodedAudioQueue(capacity = 3)
        repeat(6) { queue.offer(audioPacket(seq = it.toLong())) }

        // Audio has no reference chain, so the newest packets are simply the right ones to keep.
        assertEquals(3, queue.size())
        assertEquals(3L, queue.poll()?.header?.seq)
        assertEquals(4L, queue.poll()?.header?.seq)
        assertEquals(5L, queue.poll()?.header?.seq)
        assertEquals(3L, queue.droppedPackets)
    }

    @Test
    fun queueTrimsTheRequestedNumberOfPacketsAndCountsThem() {
        val queue = EncodedAudioQueue(capacity = 8)
        repeat(5) { queue.offer(audioPacket(seq = it.toLong())) }

        assertEquals(2, queue.dropOldest(2))
        assertEquals(2L, queue.header())
        assertEquals(2L, queue.droppedPackets)
        // Asking for more than is queued drains it without going negative.
        assertEquals(3, queue.dropOldest(99))
        assertTrue(queue.size() == 0)
        assertEquals(null, queue.poll())
    }

    private fun EncodedAudioQueue.header(): Long? = peek()?.header?.seq

    private fun audioPacket(seq: Long, bytes: Int = 240) = EncodedAudioPacket(
        header = ScreenStreamProtocol.parseHeader(
            """{"seq":$seq,"timestamp_ms":${seq * 20},"codec":"opus","sample_rate":48000,
               "channels":2,"frame_ms":20,"pre_skip":312,"key_frame":false,"config":false}""",
        ),
        payload = ByteArray(bytes),
    )
}
