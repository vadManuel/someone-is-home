package home.someoneshome.app

import home.someoneshome.model.ContactToken
import home.someoneshome.model.Seat
import home.someoneshome.platform.ContactRadio
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
 * falling 20 dB a decade, floored at −100. The resolver's −35 dBm floor (D-213, a placeholder until
 * unit 5) is then about nine centimetres, which is a touch, and a phone across a room is about
 * −70. The curve is a stand-in and says so; unit 5 measures the real one.
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

        private const val REFERENCE_METRES = 0.05
        private const val REFERENCE_DBM = -30.0
        private const val FLOOR_DBM = -100

        /** −30 dBm at 5 cm, −20 dB a decade, never below −100. */
        fun rssiAt(metres: Double): Int {
            val d = if (metres < REFERENCE_METRES) REFERENCE_METRES else metres
            return (REFERENCE_DBM - 20.0 * log10(d / REFERENCE_METRES)).roundToInt().coerceAtLeast(FLOOR_DBM)
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
}
