package home.someoneshome.platform

import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.SpareKey

/**
 * **The UWB radio, as the app sees it** (U6, D-300). A phone will hold the Nearby Interaction one
 * (a later unit; there is no `NISession` code yet), the bench a simulated one, and the tests
 * [NoRanging].
 *
 * **A pool of sessions, and nothing that could carry a seat or a role.** The radio keeps up to
 * [capacity] sessions. A session that is not ranging is *idle*, and its discovery token is offered
 * to the house ([spares]) under a key the phone drew. When the house pairs this phone with another,
 * the plan says [start]: run the idle session named by its key against the peer's token, under a
 * handle the house dealt. [stop] ends a pair and opens a fresh idle session in its place, with a new
 * token. The radio never learns whom a handle is, and neither does the app.
 *
 * Out phones range like any (the UWB plan: *a Revoked phone keeps ranging*), so the uploader never
 * asks the radio to stop because the house put this phone out.
 *
 * ### Allocation (rule 7)
 *
 * [spares] and [drain] are read once per upload, so they are a few small lists a second. A real
 * radio keeps a fixed table of the nearest reading per handle (the UWB plan, unit 4), not a list of
 * every reading.
 */
interface RangingRadio {

    /**
     * **How many peers this phone can range at once, now**: `RangingWire.STARTING_CAPACITY` at the
     * start of a round, one less each time iOS refuses a session, never more than
     * `RangingWire.MAX_PEERS`.
     */
    fun capacity(): Int

    /** Every idle session's key and token, at most [capacity] less the pairs being ranged. */
    fun spares(): List<RangingSpare>

    /**
     * **Every pair being ranged, with the nearest distance read since the last call and how many
     * readings**; a pair with no reading since is listed with none. Then the second starts again.
     */
    fun drain(): List<RangingReading>

    /** Range [handle] on the idle session [own], against the peer's [peer]. A handle already held is left as it is. */
    fun start(handle: RangingHandle, own: SpareKey, peer: RangingToken)

    /** Stop ranging [handle], and open a fresh idle session in its place. A handle not held is nothing. */
    fun stop(handle: RangingHandle)

    /** A round began: open the pool of idle sessions. */
    fun open()

    /** The round is over: close every session. */
    fun silence()
}

/**
 * **No UWB at all**: no capacity, nothing offered, nothing ranged (D-300). What every phone holds
 * until the Nearby Interaction radio exists, and what a test holds when ranging is not the
 * question. The uploader still sends a full-width report every second, and the house pairs it with
 * nobody.
 */
object NoRanging : RangingRadio {
    override fun capacity(): Int = 0
    override fun spares(): List<RangingSpare> = emptyList()
    override fun drain(): List<RangingReading> = emptyList()
    override fun start(handle: RangingHandle, own: SpareKey, peer: RangingToken) = Unit
    override fun stop(handle: RangingHandle) = Unit
    override fun open() = Unit
    override fun silence() = Unit
}
