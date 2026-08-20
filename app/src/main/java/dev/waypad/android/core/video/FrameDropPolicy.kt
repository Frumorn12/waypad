package dev.waypad.android.core.video

import dev.waypad.android.core.network.StreamFrameHeader

/**
 * Result of pruning a backlog of encoded frames.
 *
 * [continuityPreserved] is false when frames were removed without leaving a key frame at the head
 * of the survivors: the decoder then holds a broken reference chain and must resynchronise on the
 * next key frame instead of rendering garbage.
 */
data class PruneOutcome<T>(
    val kept: List<T>,
    val droppedCount: Int,
    val continuityPreserved: Boolean,
)

/**
 * Frame dropping rules shared by the socket reader and the decoder queue.
 *
 * h264 frames are not independently decodable, so a backlog can only be skipped up to a key frame;
 * dropping arbitrary P frames would corrupt everything until the next IDR. Codec-config envelopes
 * (SPS/PPS) are never dropped, only superseded by a newer one.
 */
object FrameDropPolicy {

    /**
     * Drops everything that precedes the most recent key frame, keeping the last codec-config that
     * came before it so the decoder can still be (re)configured.
     */
    fun <T> pruneToLatestKeyFrame(frames: List<T>, header: (T) -> StreamFrameHeader): PruneOutcome<T> {
        if (frames.size < 2) return PruneOutcome(frames, 0, true)
        val lastKeyFrameIndex = frames.indexOfLast { header(it).keyFrame && !header(it).config }
        if (lastKeyFrameIndex <= 0) return PruneOutcome(frames, 0, true)

        val kept = ArrayList<T>(frames.size - lastKeyFrameIndex + 1)
        frames.subList(0, lastKeyFrameIndex).lastOrNull { header(it).config }?.let(kept::add)
        kept.addAll(frames.subList(lastKeyFrameIndex, frames.size))
        return PruneOutcome(kept, frames.size - kept.size, true)
    }

    /**
     * Last resort for a backlog with no key frame in sight: throw away every payload but the most
     * recent codec-config and let the decoder wait for the next key frame.
     */
    fun <T> dropAllButConfig(frames: List<T>, header: (T) -> StreamFrameHeader): PruneOutcome<T> {
        val lastConfig = frames.lastOrNull { header(it).config }
        val kept = if (lastConfig != null) listOf(lastConfig) else emptyList()
        val dropped = frames.size - kept.size
        return PruneOutcome(kept, dropped, dropped == 0)
    }
}
