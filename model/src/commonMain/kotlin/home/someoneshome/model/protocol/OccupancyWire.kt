package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ObservationView

/*
 * **The map's half of the beat: what this phone may draw of the home's counts, this second** (D-316).
 *
 * The owner's 2026-11-21 ruling: *the Terminal and the couch must show the home's map with room
 * counts.* The plan reaches every phone once, at arming (`Effect.PlanIssued`). The counts change
 * every second, and they reach two audiences that must not be readable off the Wi-Fi: the one phone
 * reading the Terminal, and the phones on the couch. So they ride the one frame every phone in a
 * round is already sent once a beat, the answer to its upload (`RoundBody.UploadHeard.map`), in one
 * field of one width, sealed under the seat's own contact seed exactly as the ranging plan is
 * (D-300). Without the seed a reading, true occupancy and nothing are the same uniform bytes, so no
 * frame is added, none changes length, and none changes cadence, whoever reads and whoever is out
 * (D-217, D-306).
 *
 * ### What fills it is the emit schema's to permit
 *
 * `core`'s `occupancyBeat` builds an [OccupancyBeat] for a seat at a moment, and asks
 * `EmitSchema.permits` for `TERMINAL_READING` or `TRUE_OCCUPANCY` against that seat's class before
 * it builds anything but [OccupancyBeat.Nothing]. A kind with no row is nothing to everybody: the
 * fail-closed direction (rule 2).
 *
 * ### The layout, before sealing ([PLAIN_BYTES])
 *
 * - byte 0: the kind, [KIND_NOTHING], [KIND_TERMINAL], [KIND_OUTSIDE] or [KIND_LAPSE];
 * - byte 1: the Terminal's own room, the reader's outline, as its index in the plan plus one, or 0;
 * - byte 2: how many rooms each set of counts holds, at most [MAX_ROOMS];
 * - bytes 3 to 7 (D-319), the timelapse's: on a reading, byte 3 the seconds until a playback may
 *   begin and byte 4 the frames the history holds; on a playback, byte 4 the frames it plays, byte 5
 *   the first frame this beat carries, byte 6 how many it carries (one to [LAPSE_FRAMES]) and byte 7
 *   which of them were jammed, one bit each; zero everywhere else;
 * - then, from [HEADER_BYTES], one byte per room in plan order: the count in the low six bits, the
 *   band (fresh 0, stale 1, cold 2) in the top two. True occupancy and a playback's frames have no
 *   band, and a playback carries its frames one after another.
 *
 * Every byte past the rooms is zero, and a field that is nothing is all zeros, so a field holding
 * nothing and one holding a reading differ only under the seal.
 *
 * ### One width, the playback's (D-319)
 *
 * A playback shows a frame every third of a second (2 s at 6×) and the field arrives once a beat, so
 * a beat carries the next [LAPSE_FRAMES] frames. The field is as wide as that on every phone, every
 * beat, whatever it holds: [LAPSE_FRAMES] times [MAX_ROOMS] room bytes. *Every phone's frames must
 * stay one size whatever happens* (the owner's ruling, D-217).
 */

/**
 * **One beat's map, as a phone may hold it** (D-316). Three shapes, and the type is the field list
 * (rule 3): [Terminal] is built only from [ObservationView]s, which cannot carry a true count, and
 * [Outside] is the couch's privilege, built by the house only for a seat on the couch.
 */
@ClientFacing
sealed class OccupancyBeat {

    /** **Nothing to draw**: every living phone but the reader, a newly Revoked phone, any phone at a meeting. */
    data object Nothing : OccupancyBeat()

    /**
     * **The Terminal's reading, to the reader alone** (M11): per room in plan order, the count the
     * house reported (its error rolled once at capture, D-272) and the band it stood in when this
     * beat was built (`Observation.stalenessAt`, its jitter stored). [here] is the station's own
     * room by plan index, for the outline, or -1.
     *
     * **And the station's timelapse** (D-319): [readyIn], the whole seconds until a playback may
     * begin, 0 when it may now (the station's cooldown, or the wait for the history's first frame);
     * [history], the frames the history holds, one every two seconds.
     */
    class Terminal internal constructor(
        val here: Int,
        val counts: List<Int>,
        val bands: List<Int>,
        val readyIn: Int = 0,
        val history: Int = 0,
    ) : OccupancyBeat() {
        override fun equals(other: Any?): Boolean =
            other is Terminal && here == other.here && counts == other.counts && bands == other.bands &&
                readyIn == other.readyIn && history == other.history
        override fun hashCode(): Int = ((here * 31 + counts.hashCode()) * 31 + bands.hashCode()) * 31 + readyIn * 61 + history
        override fun toString(): String = "Terminal(here=$here, counts=$counts, bands=$bands, readyIn=$readyIn, history=$history)"
    }

    /**
     * **The timelapse playing, to the reader alone** (D-319): the station's history, oldest to
     * newest, [LAPSE_FRAMES][OccupancyWire.LAPSE_FRAMES] frames a beat. [frames] is how many the
     * playback plays and [first] the index among them of the first of [counts]; each of [counts] is
     * one frame's bare counts per room in plan order, and [jammed] says which of them the house was
     * jammed for (D-186), whose counts are all 0. [here] is the outline, as on a reading. **While it
     * plays the live view is not sent**: watching the past blinds you to the present (the GDD).
     */
    class Lapse internal constructor(
        val here: Int,
        val frames: Int,
        val first: Int,
        val counts: List<List<Int>>,
        val jammed: List<Boolean>,
    ) : OccupancyBeat() {
        override fun equals(other: Any?): Boolean =
            other is Lapse && here == other.here && frames == other.frames && first == other.first &&
                counts == other.counts && jammed == other.jammed
        override fun hashCode(): Int = (((here * 31 + frames) * 31 + first) * 31 + counts.hashCode()) * 31 + jammed.hashCode()
        override fun toString(): String = "Lapse(here=$here, frames=$frames, first=$first, counts=$counts, jammed=$jammed)"
    }

    /** **True occupancy, to the couch** (M12): per room in plan order, the living seats the house places there. */
    class Outside internal constructor(val counts: List<Int>) : OccupancyBeat() {
        override fun equals(other: Any?): Boolean = other is Outside && counts == other.counts
        override fun hashCode(): Int = counts.hashCode()
        override fun toString(): String = "Outside(counts=$counts)"
    }

    companion object {
        /**
         * **The reader's beat, from the house's own views** — one per room in plan order, or null
         * for a room nobody has sensed in this shift, which is drawn as 0 in the cold band (a hole
         * and an empty room are the same fact, `gdd.md:524`). [here] is the station's room, or -1.
         */
        fun terminal(here: Int, views: List<ObservationView?>, readyIn: Int = 0, history: Int = 0): Terminal = Terminal(
            here,
            views.map { it?.reportedCount?.coerceIn(0, MOST_COUNT) ?: 0 },
            views.map { it?.staleness?.coerceIn(0, OccupancyWire.COLD) ?: OccupancyWire.COLD },
            readyIn.coerceIn(0, OccupancyWire.MOST_READY_IN),
            history.coerceIn(0, OccupancyWire.MOST_FRAMES),
        )

        /**
         * **A playback's beat** (D-319): frames [first] onwards of a playback of [frames], each one
         * count per room in plan order, with [jammed] beside each. One to
         * [OccupancyWire.LAPSE_FRAMES] of them; a jammed frame's counts are sent as 0 whatever it holds.
         */
        fun lapse(here: Int, frames: Int, first: Int, counts: List<List<Int>>, jammed: List<Boolean>): Lapse {
            require(counts.size == jammed.size && counts.size in 1..OccupancyWire.LAPSE_FRAMES) { "a playback's beat carries one to ${OccupancyWire.LAPSE_FRAMES} frames" }
            require(frames in 1..OccupancyWire.MOST_FRAMES && first >= 0 && first + counts.size <= frames) { "the frames are not inside the playback" }
            return Lapse(
                here, frames, first,
                counts.mapIndexed { f, frame -> frame.map { if (jammed[f]) 0 else it.coerceIn(0, MOST_COUNT) } },
                jammed,
            )
        }

        /** **The couch's beat**: one true count per room in plan order. */
        fun outside(counts: List<Int>): Outside = Outside(counts.map { it.coerceIn(0, MOST_COUNT) })

        /** The most a room can say: six bits. A round seats at most eighteen. */
        const val MOST_COUNT: Int = 63
    }
}

object OccupancyWire {

    /** **The most rooms a field carries** (D-316). A home past it draws its later rooms empty. */
    const val MAX_ROOMS: Int = 128

    const val KIND_NOTHING: Int = 0
    const val KIND_TERMINAL: Int = 1
    const val KIND_OUTSIDE: Int = 2

    /** **A playback's frames** (D-319). */
    const val KIND_LAPSE: Int = 3

    /** **The frames a playback's beat carries**: a beat's worth at 6×, a frame every third of a second. */
    const val LAPSE_FRAMES: Int = 3

    /** The most frames a history holds: 2:00 at one every two seconds (`Timelapse.FRAMES`). */
    const val MOST_FRAMES: Int = 60

    /** The longest READY IN a reading can say, in seconds: a byte. The station's cooldown is at most 2:00. */
    const val MOST_READY_IN: Int = 255

    /** The bytes before the rooms: the kind, the outline, the rooms, and the timelapse's five. */
    const val HEADER_BYTES: Int = 8

    /** The three bands, as `Observation.stalenessAt` numbers them. */
    const val FRESH: Int = 0
    const val STALE: Int = 1
    const val COLD: Int = 2

    /** The field before sealing. */
    const val PLAIN_BYTES: Int = HEADER_BYTES + LAPSE_FRAMES * MAX_ROOMS

    /** The field on the wire, sealed: a nonce, the plain bytes, a tag. */
    const val FIELD_BYTES: Int = RangingWire.NONCE_BYTES + PLAIN_BYTES + RangingWire.TAG_BYTES

    /** Its width in URL-safe base64. */
    val CHARS: Int = RangingWire.charsFor(FIELD_BYTES)

    internal const val DOMAIN_MAP: Byte = 'M'.code.toByte()
    internal const val DOMAIN_MAP_TAG: Byte = 'm'.code.toByte()

    /** Whether [text] is an occupancy field's shape: the width, and URL-safe base64. */
    fun isField(text: String): Boolean = text.length == CHARS && RangingWire.isBase64Text(text)

    /** **A field of [random] bytes**: the answer for a seat the house holds no seed for. Opens on no phone. */
    fun blank(random: ByteArray): String {
        require(random.size == FIELD_BYTES) { "a blank occupancy field is $FIELD_BYTES random bytes" }
        return Base64Url.encode(random, random.size)
    }

    /** **[beat] written into [into]**, the plain layout above, in place. */
    fun write(beat: OccupancyBeat, into: ByteArray) {
        require(into.size >= PLAIN_BYTES) { "an occupancy field is $PLAIN_BYTES bytes before sealing" }
        into.fill(0, 0, PLAIN_BYTES)
        when (beat) {
            OccupancyBeat.Nothing -> Unit
            is OccupancyBeat.Terminal -> {
                into[0] = KIND_TERMINAL.toByte()
                into[1] = (if (beat.here in 0 until MAX_ROOMS) beat.here + 1 else 0).toByte()
                val n = minOf(beat.counts.size, MAX_ROOMS)
                into[2] = n.toByte()
                into[3] = beat.readyIn.toByte()
                into[4] = beat.history.toByte()
                for (i in 0 until n) into[HEADER_BYTES + i] = (beat.counts[i] or (beat.bands[i] shl 6)).toByte()
            }
            is OccupancyBeat.Outside -> {
                into[0] = KIND_OUTSIDE.toByte()
                val n = minOf(beat.counts.size, MAX_ROOMS)
                into[2] = n.toByte()
                for (i in 0 until n) into[HEADER_BYTES + i] = beat.counts[i].toByte()
            }
            is OccupancyBeat.Lapse -> {
                into[0] = KIND_LAPSE.toByte()
                into[1] = (if (beat.here in 0 until MAX_ROOMS) beat.here + 1 else 0).toByte()
                var n = 0
                for (frame in beat.counts) n = maxOf(n, frame.size)
                n = minOf(n, MAX_ROOMS)
                into[2] = n.toByte()
                into[4] = beat.frames.toByte()
                into[5] = beat.first.toByte()
                into[6] = beat.counts.size.toByte()
                var jams = 0
                for (f in beat.counts.indices) {
                    if (beat.jammed[f]) jams = jams or (1 shl f)
                    val frame = beat.counts[f]
                    for (i in 0 until minOf(frame.size, n)) into[HEADER_BYTES + f * n + i] = frame[i].toByte()
                }
                into[7] = jams.toByte()
            }
        }
    }

    /**
     * **[plain] read back**, or null for anything that is not exactly one: a kind out of range, a
     * room count past [MAX_ROOMS], a band past cold, a band on true occupancy, an outline on
     * anything but a reading, or a byte past the rooms that is not zero. Fail closed: a field that
     * does not read is nothing drawn, never a partial map.
     */
    fun read(plain: ByteArray): OccupancyBeat? {
        if (plain.size < PLAIN_BYTES) return null
        val kind = plain[0].toInt() and 0xff
        val here = plain[1].toInt() and 0xff
        val n = plain[2].toInt() and 0xff
        if (n > MAX_ROOMS) return null
        val b3 = plain[3].toInt() and 0xff
        val b4 = plain[4].toInt() and 0xff
        val b5 = plain[5].toInt() and 0xff
        val b6 = plain[6].toInt() and 0xff
        val b7 = plain[7].toInt() and 0xff
        // The room bytes a kind may use: a playback's frames one after another, any other kind one set.
        val used = if (kind == KIND_LAPSE) b6 * n else n
        if (used > LAPSE_FRAMES * MAX_ROOMS) return null
        for (i in HEADER_BYTES + used until PLAIN_BYTES) if (plain[i].toInt() != 0) return null
        return when (kind) {
            KIND_NOTHING ->
                if (here == 0 && n == 0 && b3 == 0 && b4 == 0 && b5 == 0 && b6 == 0 && b7 == 0) OccupancyBeat.Nothing else null
            KIND_TERMINAL -> {
                if (here > n || b4 > MOST_FRAMES || b5 != 0 || b6 != 0 || b7 != 0) return null
                val counts = ArrayList<Int>(n)
                val bands = ArrayList<Int>(n)
                for (i in 0 until n) {
                    val b = plain[HEADER_BYTES + i].toInt() and 0xff
                    val band = b ushr 6
                    if (band > COLD) return null
                    counts += b and 0x3f
                    bands += band
                }
                OccupancyBeat.Terminal(here - 1, counts, bands, b3, b4)
            }
            KIND_OUTSIDE -> {
                if (here != 0 || b3 != 0 || b4 != 0 || b5 != 0 || b6 != 0 || b7 != 0) return null
                val counts = ArrayList<Int>(n)
                for (i in 0 until n) {
                    val b = plain[HEADER_BYTES + i].toInt() and 0xff
                    if (b > OccupancyBeat.MOST_COUNT) return null
                    counts += b
                }
                OccupancyBeat.Outside(counts)
            }
            KIND_LAPSE -> {
                if (here > n || b3 != 0 || b4 !in 1..MOST_FRAMES || b6 !in 1..LAPSE_FRAMES || b5 + b6 > b4) return null
                if (b7 ushr b6 != 0) return null
                val frames = ArrayList<List<Int>>(b6)
                val jammed = ArrayList<Boolean>(b6)
                for (f in 0 until b6) {
                    val jam = (b7 ushr f) and 1 == 1
                    val counts = ArrayList<Int>(n)
                    for (i in 0 until n) {
                        val b = plain[HEADER_BYTES + f * n + i].toInt() and 0xff
                        if (b > OccupancyBeat.MOST_COUNT || (jam && b != 0)) return null
                        counts += b
                    }
                    frames += counts
                    jammed += jam
                }
                OccupancyBeat.Lapse(here - 1, b4, b5, frames, jammed)
            }
            else -> null
        }
    }

    /** **[beat], sealed under the receiving seat's [seed] with [nonce]**: always [CHARS] characters. Allocates; the house uses [RangingCodec]. */
    fun pack(seed: ContactSeed, nonce: ByteArray, beat: OccupancyBeat): String =
        RangingCodec().packMap(seed, nonce, beat)

    /** **A field, opened with [seed]**, or null for anything that is not exactly one. */
    fun unpack(seed: ContactSeed, text: String): OccupancyBeat? = RangingCodec().openMap(seed, text)
}
