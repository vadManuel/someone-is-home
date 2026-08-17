package home.spike

/**
 * Fixed-capacity, column-oriented trial storage.
 *
 * Primitive arrays allocated once at run start. A `List<Trial>` of data classes would allocate
 * an object per trial on the path that resolves the trial — which is the path this spike is
 * measuring. The instrument must not be the thing that causes the pause it reports.
 */
class TrialBuffer(capacity: Int) {
    val triggerNanos = LongArray(capacity)
    val drawNanos = LongArray(capacity)
    val presentNanos = LongArray(capacity)
    val vsyncAtTriggerNanos = LongArray(capacity)
    val vsyncAtDrawNanos = LongArray(capacity)
    val tickAtTrigger = LongArray(capacity)
    val tickAtDraw = LongArray(capacity)
    val gcEpoch = LongArray(capacity)
    val flags = IntArray(capacity)

    var count = 0
        private set

    fun add(
        triggerNanos: Long,
        drawNanos: Long,
        presentNanos: Long,
        vsyncAtTriggerNanos: Long,
        vsyncAtDrawNanos: Long,
        tickAtTrigger: Long,
        tickAtDraw: Long,
        gcEpoch: Long,
        flags: Int,
    ) {
        val i = count
        if (i >= this.triggerNanos.size) return
        this.triggerNanos[i] = triggerNanos
        this.drawNanos[i] = drawNanos
        this.presentNanos[i] = presentNanos
        this.vsyncAtTriggerNanos[i] = vsyncAtTriggerNanos
        this.vsyncAtDrawNanos[i] = vsyncAtDrawNanos
        this.tickAtTrigger[i] = tickAtTrigger
        this.tickAtDraw[i] = tickAtDraw
        this.gcEpoch[i] = gcEpoch
        this.flags[i] = flags
        count = i + 1
    }

    /** trigger -> the frame that drew black. */
    fun drawLatencyNanos(i: Int): Long = drawNanos[i] - triggerNanos[i]

    /** trigger -> first vsync after that frame. Inferred; see the caveat in the README. */
    fun presentLatencyNanos(i: Int): Long = presentNanos[i] - triggerNanos[i]

    /**
     * Vsync boundaries crossed between the trigger and the frame that drew black.
     *
     * THE HEADLINE METRIC. 1 is the passing shape: the trigger landed inside a frame interval
     * and the very next frame drew black, which is the earliest the hardware allows. 2 or more
     * means the renderer missed a frame.
     *
     * Derived from vsync timestamps rather than tick counts, because our CADisplayLink and
     * Compose's own may fire in either order within a single vsync, which would put +/-1 of
     * pure noise on a tick-count difference.
     */
    fun span(i: Int, intervalNanos: Long): Int {
        if (intervalNanos <= 0L) return -1
        val delta = vsyncAtDrawNanos[i] - vsyncAtTriggerNanos[i]
        return ((delta + intervalNanos / 2) / intervalNanos).toInt()
    }

    fun isPrewarm(i: Int): Boolean = flags[i] and FLAG_PREWARM != 0
}
