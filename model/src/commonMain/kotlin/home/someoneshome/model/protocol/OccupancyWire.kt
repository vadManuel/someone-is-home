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
 * - byte 0: the kind, [KIND_NOTHING], [KIND_TERMINAL] or [KIND_OUTSIDE];
 * - byte 1: the Terminal's own room, the reader's outline, as its index in the plan plus one, or 0;
 * - byte 2: how many rooms follow, at most [MAX_ROOMS];
 * - then one byte per room in plan order: the count in the low six bits, the band (fresh 0, stale
 *   1, cold 2) in the top two. True occupancy has no band.
 *
 * Every byte past the rooms is zero, and a field that is nothing is all zeros, so a field holding
 * nothing and one holding a reading differ only under the seal.
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
     */
    class Terminal internal constructor(val here: Int, val counts: List<Int>, val bands: List<Int>) : OccupancyBeat() {
        override fun equals(other: Any?): Boolean =
            other is Terminal && here == other.here && counts == other.counts && bands == other.bands
        override fun hashCode(): Int = (here * 31 + counts.hashCode()) * 31 + bands.hashCode()
        override fun toString(): String = "Terminal(here=$here, counts=$counts, bands=$bands)"
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
        fun terminal(here: Int, views: List<ObservationView?>): Terminal = Terminal(
            here,
            views.map { it?.reportedCount?.coerceIn(0, MOST_COUNT) ?: 0 },
            views.map { it?.staleness?.coerceIn(0, OccupancyWire.COLD) ?: OccupancyWire.COLD },
        )

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

    /** The three bands, as `Observation.stalenessAt` numbers them. */
    const val FRESH: Int = 0
    const val STALE: Int = 1
    const val COLD: Int = 2

    /** The field before sealing. */
    const val PLAIN_BYTES: Int = 3 + MAX_ROOMS

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
                for (i in 0 until n) into[3 + i] = (beat.counts[i] or (beat.bands[i] shl 6)).toByte()
            }
            is OccupancyBeat.Outside -> {
                into[0] = KIND_OUTSIDE.toByte()
                val n = minOf(beat.counts.size, MAX_ROOMS)
                into[2] = n.toByte()
                for (i in 0 until n) into[3 + i] = beat.counts[i].toByte()
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
        for (i in 3 + n until PLAIN_BYTES) if (plain[i].toInt() != 0) return null
        return when (kind) {
            KIND_NOTHING -> if (here == 0 && n == 0) OccupancyBeat.Nothing else null
            KIND_TERMINAL -> {
                if (here > n) return null
                val counts = ArrayList<Int>(n)
                val bands = ArrayList<Int>(n)
                for (i in 0 until n) {
                    val b = plain[3 + i].toInt() and 0xff
                    val band = b ushr 6
                    if (band > COLD) return null
                    counts += b and 0x3f
                    bands += band
                }
                OccupancyBeat.Terminal(here - 1, counts, bands)
            }
            KIND_OUTSIDE -> {
                if (here != 0) return null
                val counts = ArrayList<Int>(n)
                for (i in 0 until n) {
                    val b = plain[3 + i].toInt() and 0xff
                    if (b > OccupancyBeat.MOST_COUNT) return null
                    counts += b
                }
                OccupancyBeat.Outside(counts)
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
