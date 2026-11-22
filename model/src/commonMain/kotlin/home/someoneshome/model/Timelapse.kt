package home.someoneshome.model

/*
 * **The Terminal's timelapse: the station's history, the playback running on it, and the station's
 * cooldown** (D-319; the owner's 2026-11-21 rulings; `gdd.md`'s timelapse table).
 *
 * The table rules the shape: a **2:00** history, played at **6×**, once, **oldest to newest**; the
 * floor is the round's start or the end of the last meeting, whichever is later; the cooldown
 * starts **at playback end** and equals the history played (`cooldown == history duration`); an
 * early cancel cuts it **linearly by the fraction unwatched**. The owner settled the rest:
 *
 * - *The cooldown belongs to the station. The next reader inherits it.* So it is one value here,
 *   [Timelapse.readyAt], and no seat is on it.
 * - *Frames are bare counts (no staleness shading), snapshotted every 2 s of real time.* Sixty
 *   frames for a full 2:00, each shown for a third of a second at 6×.
 * - *A meeting clears it*: it ends a playback, is not an early cancel, and after it the cooldown
 *   equals the new, tiny history.
 * - *Static crossing the history renders those seconds as noise* (D-186): a frame taken while the
 *   house was jammed holds nothing but the fact of the jam.
 *
 * ### Authority state, and it never reaches a client as itself
 *
 * What the reader is shown of it is `core`'s beat, sealed into the field every upload's answer
 * already carries (`OccupancyWire`, D-316), so nothing here is `@Serializable`. A frame holds the
 * **reported** counts the reader would have been shown at that mark, read through
 * `Observation.toView`, the one bridge that cannot carry a true count (rule 3).
 */

/**
 * **One frame of the station's history: the counts at one two-second mark, or the jam** (D-319).
 *
 * [counts] is one per room in plan order: the room's latest capture this shift as the reader would
 * have been shown it at [at], its error rolled once and stored, and 0 for stairs and for a room
 * nobody had sensed (`gdd.md:524`). **No band**: the owner ruled frames bare. When [jammed], the
 * house's monitoring was jammed at [at] (D-186) and [counts] are all 0, because *a player who watches
 * it back learns the window and nothing inside it*.
 */
class TimelapseFrame(val at: Tick, val counts: List<Int>, val jammed: Boolean) {
    override fun equals(other: Any?): Boolean =
        other is TimelapseFrame && at == other.at && counts == other.counts && jammed == other.jammed

    override fun hashCode(): Int = (at.hashCode() * 31 + counts.hashCode()) * 31 + jammed.hashCode()

    override fun toString(): String = "TimelapseFrame(at=${at.step}, counts=$counts, jammed=$jammed)"
}

/**
 * **A playback running at the station: who started it, when, and the frames it plays** (D-319).
 *
 * [frames] is the history as it stood when the playback began, oldest first, and it is what plays
 * whatever is captured after: a playback shows the past it was asked for. It runs for
 * [Timelapse.playedFor] of its frames and ends at [endsAt].
 */
class Playback(val reader: Seat, val startedAt: Tick, val frames: List<TimelapseFrame>) {
    /** When it plays its last frame out. */
    val endsAt: Tick get() = startedAt + Timelapse.playedFor(frames.size)

    /** **The frame showing at [at]**, oldest first, or [frames]'s size once it has played out. */
    fun frameAt(at: Tick): Int = Timelapse.frameAt(at.step - startedAt.step).coerceIn(0, frames.size)

    override fun equals(other: Any?): Boolean =
        other is Playback && reader.index == other.reader.index && startedAt == other.startedAt && frames == other.frames

    override fun hashCode(): Int = (reader.index * 31 + startedAt.hashCode()) * 31 + frames.hashCode()

    override fun toString(): String = "Playback(reader=${reader.index}, startedAt=${startedAt.step}, frames=${frames.size})"
}

/**
 * **The station's timelapse, house-wide, one per round** (D-319).
 *
 * - [history]: the frames this shift, oldest first, the last [FRAMES] of them. Taken lazily, at
 *   the next event past each mark, from the state as it stood at the mark (the rules' `sampled`),
 *   so a replay takes the same frames from the same events.
 * - [nextFrameAt]: the next mark, on a grid anchored at the shift's start ([FRAME_EVERY] after it,
 *   then every [FRAME_EVERY]). Reset with the history at every meeting's end.
 * - [playback]: the playback running, or null.
 * - [readyAt]: **the station's cooldown**, the tick a playback may next begin. No seat is on it,
 *   because the owner ruled it the station's: the next reader inherits it.
 */
class Timelapse(
    val history: List<TimelapseFrame>,
    val nextFrameAt: Tick,
    val playback: Playback?,
    val readyAt: Tick,
) {
    fun copy(
        history: List<TimelapseFrame> = this.history,
        nextFrameAt: Tick = this.nextFrameAt,
        playback: Playback? = this.playback,
        readyAt: Tick = this.readyAt,
    ): Timelapse = Timelapse(history, nextFrameAt, playback, readyAt)

    /** **The playback still playing at [at]**, or null: none, or one that has played out. */
    fun playingAt(at: Tick): Playback? = playback?.takeIf { at < it.endsAt }

    override fun equals(other: Any?): Boolean =
        other is Timelapse && history == other.history && nextFrameAt == other.nextFrameAt &&
            playback == other.playback && readyAt == other.readyAt

    override fun hashCode(): Int =
        ((history.hashCode() * 31 + nextFrameAt.hashCode()) * 31 + playback.hashCode()) * 31 + readyAt.hashCode()

    override fun toString(): String =
        "Timelapse(frames=${history.size}, nextFrameAt=${nextFrameAt.step}, playback=$playback, readyAt=${readyAt.step})"

    companion object {
        /** **A frame every two seconds of real time** (the owner's ruling). */
        const val FRAME_EVERY: Long = 2L * Balance.TICKS_PER_SECOND

        /** **The history window, 2:00** (`gdd.md`'s table). */
        const val HISTORY: Long = 120L * Balance.TICKS_PER_SECOND

        /** **Sixty frames** for a full history: [HISTORY] over [FRAME_EVERY]. */
        const val FRAMES: Int = (HISTORY / FRAME_EVERY).toInt()

        /** **6×** (`gdd.md`'s table): a frame is shown for [FRAME_EVERY] / 6, a third of a second. */
        const val SPEED: Long = 6L

        /** **A shift's timelapse at its start**: no history, no playback, and the station ready (D-319). */
        fun startingAt(shiftStartedAt: Tick): Timelapse =
            Timelapse(emptyList(), shiftStartedAt + FRAME_EVERY, null, shiftStartedAt)

        /** How long [frames] frames take to play at [SPEED], in ticks, rounded up to the tick. */
        fun playedFor(frames: Int): Long = (frames * FRAME_EVERY + SPEED - 1) / SPEED

        /** The frame showing [elapsed] ticks into a playback: elapsed × [SPEED] / [FRAME_EVERY]. */
        fun frameAt(elapsed: Long): Int = if (elapsed < 0) 0 else (elapsed * SPEED / FRAME_EVERY).toInt()

        /**
         * **How many frames the history holds at [at], in a shift that began at [shiftStartedAt]**:
         * the marks strictly before [at], at most [FRAMES]. Arithmetic on the grid, so the beat can
         * say it between events, and it is the count `sampled` will have taken by then.
         */
        fun framesHeld(shiftStartedAt: Tick, at: Tick): Int {
            val since = at.step - shiftStartedAt.step
            if (since <= FRAME_EVERY) return 0
            return ((since - 1) / FRAME_EVERY).toInt().coerceAtMost(FRAMES)
        }
    }
}
