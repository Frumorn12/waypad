package dev.waypad.android.core.video

/**
 * Bounded backlog between the socket reader and the decoder.
 *
 * The reader never blocks on the decoder: when frames pile up the queue prunes itself with
 * [FrameDropPolicy] instead of growing, so the phone always renders the freshest picture the link
 * managed to deliver rather than a seconds-old one.
 */
class EncodedFrameQueue(
    private val softCapacity: Int = DEFAULT_SOFT_CAPACITY,
    private val hardCapacity: Int = DEFAULT_HARD_CAPACITY,
) {
    private val frames = ArrayList<EncodedVideoFrame>(hardCapacity + 1)

    @Volatile
    var droppedFrames: Long = 0L
        private set

    private var continuityBroken = false

    @Synchronized
    fun offer(frame: EncodedVideoFrame) {
        frames.add(frame)
        if (frames.size <= softCapacity) return

        val pruned = FrameDropPolicy.pruneToLatestKeyFrame(frames) { it.header }
        applyPrune(pruned)
        if (frames.size > hardCapacity) {
            // No key frame anywhere in the backlog: keeping the chain intact would only add delay,
            // so cut everything loose and resynchronise on the next IDR.
            applyPrune(FrameDropPolicy.dropAllButConfig(frames) { it.header })
        }
    }

    private fun applyPrune(outcome: PruneOutcome<EncodedVideoFrame>) {
        if (outcome.droppedCount == 0) return
        frames.clear()
        frames.addAll(outcome.kept)
        droppedFrames += outcome.droppedCount
        if (!outcome.continuityPreserved) continuityBroken = true
    }

    @Synchronized
    fun poll(): EncodedVideoFrame? = if (frames.isEmpty()) null else frames.removeAt(0)

    @Synchronized
    fun peek(): EncodedVideoFrame? = frames.firstOrNull()

    @Synchronized
    fun size(): Int = frames.size

    @Synchronized
    fun isEmpty(): Boolean = frames.isEmpty()

    /** Returns true once, when a prune broke the decoder reference chain. */
    @Synchronized
    fun consumeContinuityBreak(): Boolean = continuityBroken.also { continuityBroken = false }

    @Synchronized
    fun clear() {
        frames.clear()
        continuityBroken = false
    }

    companion object {
        const val DEFAULT_SOFT_CAPACITY = 3
        const val DEFAULT_HARD_CAPACITY = 12
    }
}
