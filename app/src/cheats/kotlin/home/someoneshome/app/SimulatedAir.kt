package home.someoneshome.app

import home.someoneshome.model.ContactToken
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.SpareKey
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RangingRadio
import home.someoneshome.platform.RadioReading
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * **A house's air, simulated: where every phone is standing, and what each radio would hear**
 * (D-217).
 *
 * The fake half of `platform`'s [ContactRadio]: what the bench plays through until unit 4's iOS radio
 * exists, and what every test of the contact path plays through after. It knows positions and
 * nothing else. It never knows a role, an armed state or a seat's token by any route but the radio
 * being handed one to advertise, so a bench walking two phones together exercises exactly what a
 * person walking two phones together does: the uploader, the socket, the resolver, the gate.
 *
 * **Signal strength is a function of distance and of nothing else**: −30 dBm at five centimetres,
 * falling 20 dB a decade, floored at −100. The pairing's −68 dBm near floor (D-306) is then about
 * four metres, and a phone across a room is about −70. The curve is a stand-in and says so: real
 * phones read −43 at 30 cm and −57 at 1 m (D-233), which this curve does not follow. Since D-308
 * Bluetooth decides nothing in the rules; on the bench it only ranks who the house pairs for UWB,
 * and the distance that decides comes from [rangingFor].
 *
 * In the cheats source set, absent from a release build, like every other stand-in.
 */
class SimulatedAir {

    /** A place in the house, in metres. */
    data class Spot(val x: Double, val y: Double)

    private val radios = mutableListOf<SimulatedRadio>()
    private val spots = mutableMapOf<Int, Spot>()

    /** This seat's radio. One per seat; asking twice returns the same one. */
    fun radioFor(seat: Seat): SimulatedRadio =
        radios.firstOrNull { it.seat.index == seat.index }
            ?: SimulatedRadio(seat, this).also { radios += it }

    /** Stand [seat] at [spot]. */
    fun place(seat: Seat, spot: Spot) {
        spots[seat.index] = spot
    }

    /** Stand every seat in [seats] far apart, a room each, so nothing is in contact. */
    fun spreadOut(seats: List<Seat>) {
        seats.forEachIndexed { i, seat -> place(seat, Spot(x = i * APART_METRES, y = 0.0)) }
    }

    /** **Phone to phone**: put [mover] a few centimetres from [still], well inside contact. */
    fun touch(mover: Seat, still: Seat) {
        val at = spots[still.index] ?: Spot(0.0, 0.0)
        place(mover, Spot(at.x + TOUCH_METRES, at.y))
    }

    /** Where [seat] is standing, or null for a seat nobody placed. */
    fun spotOf(seat: Seat): Spot? = spots[seat.index]

    // ---- UWB (U6, D-300) ------------------------------------------------------------------------

    private val rangings = mutableListOf<SimulatedRanging>()
    private val bodies = mutableSetOf<Pair<Int, Int>>()

    /**
     * **The noise the UWB model draws from**, one stream for the whole air, seeded, so a bench run is
     * the same run every time (the bench is not the rules, but a flaky bench proves nothing).
     */
    internal val noise = BenchNoise(NOISE_SEED)

    /** This seat's UWB radio, its pool at [capacity]. One per seat; asking twice returns the same one. */
    fun rangingFor(seat: Seat, capacity: Int = RangingWire.STARTING_CAPACITY): SimulatedRanging =
        rangings.firstOrNull { it.seat.index == seat.index }
            ?: SimulatedRanging(seat, this, capacity).also { rangings += it }

    /** **A body stands between [a] and [b]** ([on]), or no longer does: their readings come out long. */
    fun bodyBetween(a: Seat, b: Seat, on: Boolean = true) {
        val key = minOf(a.index, b.index) to maxOf(a.index, b.index)
        if (on) bodies += key else bodies -= key
    }

    internal fun blocked(a: Seat, b: Seat): Boolean = (minOf(a.index, b.index) to maxOf(a.index, b.index)) in bodies

    /** The session, on any radio in this air, whose own token is [token], and the radio it is on. */
    internal fun sessionWith(token: RangingToken): Pair<SimulatedRanging, SimulatedRanging.Session>? {
        for (r in rangings) r.sessionWith(token)?.let { return r to it }
        return null
    }

    /** The distance between two seats, in metres, or null if either is nowhere. */
    internal fun metresBetween(a: Seat, b: Seat): Double? {
        val here = spots[a.index] ?: return null
        val there = spots[b.index] ?: return null
        return distance(here, there)
    }

    /** What [listener] hears right now: every other advertising radio, at its distance's strength. */
    internal fun heardBy(listener: SimulatedRadio): List<RadioReading> {
        val here = spots[listener.seat.index] ?: return emptyList()
        return radios
            .filter { it !== listener }
            .sortedBy { it.seat.index }
            .mapNotNull { other ->
                val token = other.advertised ?: return@mapNotNull null
                val there = spots[other.seat.index] ?: return@mapNotNull null
                RadioReading(token, rssiAt(distance(here, there)))
            }
    }

    companion object {
        /** Five metres between neighbours when spread out: a room apart, far outside contact. */
        const val APART_METRES = 5.0

        /** Four centimetres: two phones held together. */
        const val TOUCH_METRES = 0.04

        /**
         * **The benches' phones range three peers each, the phones' own start** (U6, D-302): every
         * bench that plays through this air plays on distance, as every round does (D-308). D-301 set four, so that five phones ranged every pair, because at three D-300's
         * pairing could leave a pair of five unranged for good; since D-302 a pair both phones rank
         * first displaces a kept pair, and the benches play at the phones' number.
         */
        const val BENCH_CAPACITY = RangingWire.STARTING_CAPACITY

        private const val REFERENCE_METRES = 0.05
        private const val REFERENCE_DBM = -30.0
        private const val FLOOR_DBM = -100

        /** −30 dBm at 5 cm, −20 dB a decade, never below −100. */
        fun rssiAt(metres: Double): Int {
            val d = if (metres < REFERENCE_METRES) REFERENCE_METRES else metres
            return (REFERENCE_DBM - 20.0 * log10(d / REFERENCE_METRES)).roundToInt().coerceAtLeast(FLOOR_DBM)
        }

        /**
         * **UWB readings arrive every 150 ms: 6.7 a second** (the spike, step 5). The model counts a
         * second's readings as the whole number of 150 ms ticks that fell in it: 6, 7, 7, 6, 7, 7, …
         */
        const val UWB_READING_MILLIS: Long = 150L

        /** **±3 cm on a clear line** (the spike, step 2: held 15 cm read 12–13, touching 2 cm). */
        const val UWB_CLEAR_NOISE_CM: Int = 3

        /**
         * **A body in the way reads 3 to 5 m long, and never short** (the spike, step 3: 4.6 m at 1 m
         * with a back between). Added to the true distance, so it can only lengthen a reading.
         */
        const val UWB_BODY_LONG_MIN_CM: Int = 300
        const val UWB_BODY_LONG_MAX_CM: Int = 500

        private const val NOISE_SEED = 20261118L

        /**
         * **A lock or a trip to the background pauses UWB, and it recovers within half a second**
         * (the spike, step 6; D-301). Recovery is drawn per resume from 100 ms to this, and the
         * readings of the second it falls in start only after it: at worst three of that second's
         * 150 ms ticks are left. No new token is needed, so the pair is still listed throughout.
         */
        const val UWB_RECOVER_MAX_MILLIS: Int = 500
        const val UWB_RECOVER_MIN_MILLIS: Int = 100

        /**
         * **One UWB reading of [metres], in centimetres**: the true distance, ±3 cm of noise on a clear
         * line, or 3 to 5 m long through a body. The pause on a lock and its recovery within 0.5 s
         * are [SimulatedRanging.paused]'s (D-301); dropouts mid-touch are still a later unit's.
         */
        fun uwbReadingCm(metres: Double, blocked: Boolean, noise: BenchNoise): Int {
            val trueCm = (metres * 100.0).roundToInt()
            return if (blocked) {
                trueCm + noise.nextInt(UWB_BODY_LONG_MIN_CM, UWB_BODY_LONG_MAX_CM + 1)
            } else {
                trueCm + noise.nextInt(-UWB_CLEAR_NOISE_CM, UWB_CLEAR_NOISE_CM + 1)
            }
        }

        private fun distance(a: Spot, b: Spot): Double {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}

/**
 * **One seat's simulated radio** (D-217): advertises what it is told to, and when scanning hears
 * what [SimulatedAir] says is in the air. It holds no buffer: a scan is read at the moment of the
 * upload, which is the radio's whole job compressed to one instant.
 */
class SimulatedRadio internal constructor(val seat: Seat, private val air: SimulatedAir) : ContactRadio {

    /** The token on the air now, or null once the round has gone quiet. */
    var advertised: ContactToken? = null
        private set

    /** Whether this radio is listening. A phone that is out stops (D-008). */
    var scanning: Boolean = false
        private set

    override fun advertise(token: ContactToken) {
        advertised = token
    }

    override fun scan(on: Boolean) {
        scanning = on
    }

    override fun drain(): List<RadioReading> = if (scanning) air.heardBy(this) else emptyList()

    override fun silence() {
        advertised = null
        scanning = false
    }

    private var down: RadioCause? = null

    /**
     * **The bench's hand on the Bluetooth switch, or the OS resetting it** (T2, D-222; D-223). Down,
     * this radio reports one fault onset with [cause] at the next beat; it hears and is heard as
     * before, because what a fault does to the air is the hardware's and not the bench's question.
     */
    fun fail(cause: RadioCause = RadioCause.Person) {
        down = cause
    }

    override fun faultBegan(): RadioCause? = down.also { down = null }
}

/**
 * **One seat's simulated UWB radio** (U6, D-300): a pool of sessions in [SimulatedAir], kept the way
 * the Nearby Interaction radio will keep them, and read with the spike's distance model.
 *
 * The pool holds up to [capacity] sessions, each with a key and a token of its own (random, 312 to
 * 345 bytes, the measured sizes). An idle session is offered as a spare. [start] runs the idle
 * session the house named against the peer's token; [stop] ends it and opens a fresh idle one with a
 * new key and token. **A pair reads only when both phones run it**, each against the other's token,
 * as UWB's two-way exchange needs; a phone that started alone reads nothing. Then [drain] gives,
 * for each pair, the nearest of that second's readings ([SimulatedAir.uwbReadingCm]) and how many.
 *
 * Like [SimulatedRadio] it holds no buffer: a second's readings are drawn at the moment of the
 * upload. It never learns a seat from a handle; only the air, which knows where everybody stands,
 * does.
 */
class SimulatedRanging internal constructor(
    val seat: Seat,
    private val air: SimulatedAir,
    private var limit: Int,
) : RangingRadio {

    /** One session: its own key and token, and, once ranging, the pair's handle and the peer's token. */
    class Session(val key: SpareKey, val token: RangingToken) {
        var handle: RangingHandle? = null
        var peer: RangingToken? = null
    }

    private val sessions = mutableListOf<Session>()
    private var drains = 0L

    /** Locked or in the background now (D-301): no session reads, on this phone or against it. */
    var paused: Boolean = false
        private set

    /** The milliseconds the next second loses to a recovery, until this radio's next drain. */
    private var recovering = 0

    /**
     * **The phone locked or left the foreground** ([on]), **or came back** (D-301; the spike, step 6).
     * While paused, every pair it is in is still listed, on both phones, with no reading: iOS
     * suspends the session and keeps it. On the way back the first second loses a recovery of
     * [SimulatedAir.UWB_RECOVER_MIN_MILLIS] to [SimulatedAir.UWB_RECOVER_MAX_MILLIS] before its
     * readings start; the second after is whole.
     */
    fun paused(on: Boolean) {
        if (paused && !on) recovering = air.noise.nextInt(SimulatedAir.UWB_RECOVER_MIN_MILLIS, SimulatedAir.UWB_RECOVER_MAX_MILLIS + 1)
        paused = on
    }

    /** What the next drain loses to a recovery, for a peer's drain in the same second. */
    internal val recoveringMillis: Int get() = recovering

    /** The pairs this radio is ranging, by handle. For the bench. */
    val ranging: List<RangingHandle> get() = sessions.mapNotNull { it.handle }

    /** The seat at the other end of [handle], as only the air could say: for the bench's assertions. */
    fun peerOf(handle: RangingHandle): Seat? {
        val peer = sessions.firstOrNull { it.handle == handle }?.peer ?: return null
        return air.sessionWith(peer)?.first?.seat
    }

    internal fun sessionWith(token: RangingToken): Session? = sessions.firstOrNull { it.token == token }

    /** **iOS refused a session** (`activeSessionsLimitExceeded`): the capacity drops by one. */
    fun refused() {
        if (limit > 0) limit--
        while (sessions.size > limit) {
            val idle = sessions.lastOrNull { it.handle == null } ?: sessions.last()
            sessions.remove(idle)
        }
    }

    override fun capacity(): Int = limit

    override fun spares(): List<RangingSpare> =
        sessions.filter { it.handle == null }.map { RangingSpare(it.key, it.token) }

    override fun drain(): List<RangingReading> {
        // 6.7 a second: the 150 ms ticks that fell in this second, 6 or 7.
        val secondStarts = drains * 1_000L
        val from = secondStarts / SimulatedAir.UWB_READING_MILLIS
        drains++
        val to = drains * 1_000L / SimulatedAir.UWB_READING_MILLIS
        val own = recovering
        recovering = 0
        return sessions.mapNotNull { s ->
            val handle = s.handle ?: return@mapNotNull null
            val other = s.peer?.let(air::sessionWith)
            val peerSeat = if (other != null && other.second.peer == s.token && other.second.handle != null) other.first.seat else null
            val metres = peerSeat?.let { air.metresBetween(seat, it) }
            // A pair reads only while both phones run it (D-301): a paused phone on either end, none.
            val asleep = paused || other?.first?.paused == true
            // A recovery on either end loses the start of the second: only the ticks after it read.
            val lost = maxOf(own, other?.first?.recoveringMillis ?: 0)
            val count = (from + 1..to).count { it * SimulatedAir.UWB_READING_MILLIS - secondStarts >= lost }
            if (peerSeat == null || metres == null || asleep || count == 0) {
                RangingReading(handle, null, 0)
            } else {
                val blocked = air.blocked(seat, peerSeat)
                val nearest = (0 until count).minOf { SimulatedAir.uwbReadingCm(metres, blocked, air.noise) }
                RangingReading(handle, nearest, count)
            }
        }
    }

    override fun start(handle: RangingHandle, own: SpareKey, peer: RangingToken) {
        if (sessions.any { it.handle == handle }) return
        val idle = sessions.firstOrNull { it.key == own && it.handle == null } ?: return
        idle.handle = handle
        idle.peer = peer
    }

    override fun stop(handle: RangingHandle) {
        val s = sessions.firstOrNull { it.handle == handle } ?: return
        sessions.remove(s)
        fill()
    }

    override fun open() {
        sessions.clear()
        fill()
    }

    override fun silence() {
        sessions.clear()
    }

    private fun fill() {
        while (sessions.size < limit) sessions += fresh()
    }

    private fun fresh(): Session {
        val keyBytes = air.noise.nextBytes(RangingWire.KEY_BYTES)
        var bits = 0L
        for (b in keyBytes) bits = (bits shl 8) or (b.toLong() and 0xff)
        val token = RangingToken.of(air.noise.nextBytes(air.noise.nextInt(312, 346)))
        val key = SpareKey(bits)
        return Session(key, token)
    }
}

/**
 * **The bench's noise: a seeded SplitMix64 stream** (D-300). Written out rather than borrowed, for
 * `seedSourceCordon`'s reason: nothing in the app draws from `kotlin.random`, so a seed or a seat
 * token can never be one. This draws only a fake radio's jitter and its fake session tokens, never
 * a round's secret, and is seeded so that a bench run is the same run every time.
 */
class BenchNoise(seed: Long) {
    private var state = seed

    private fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** A value in [from] until [until]. */
    fun nextInt(from: Int, until: Int): Int {
        require(until > from) { "an empty range" }
        val span = (until - from).toLong()
        return from + ((next() ushr 1) % span).toInt()
    }

    /** [count] bytes. */
    fun nextBytes(count: Int): ByteArray = ByteArray(count) { next().toByte() }
}
