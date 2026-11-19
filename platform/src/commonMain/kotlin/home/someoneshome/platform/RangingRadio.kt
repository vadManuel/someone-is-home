package home.someoneshome.platform

import home.someoneshome.model.RadioCause
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPool
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.SpareKey

/**
 * **The UWB radio, as the app sees it** (U6, D-300). A phone holds the Nearby Interaction one
 * ([nearbyRangingRadio], D-303) when the developer switch turns it on and [NoRanging] otherwise, the
 * bench a simulated one, and the tests [NoRanging].
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

    /**
     * **Whether the radio went down since the last call, and on which side of D-187's line** (D-303,
     * the plan's *Outages*): [RadioCause.Person] for Nearby Interaction refused, [RadioCause.Device]
     * for the session limit, an unsupported phone or a ranging session that failed. Once per onset,
     * as `ContactRadio.faultBegan`; the uploader sends it as the same fixed `RoundBody.RadioFaulted`.
     * A radio that cannot fault says null, which is every one but the phone's.
     */
    fun faultBegan(): RadioCause? = null
}

/**
 * **A [RangingPool] as the app's radio** (D-303): the pool is the whole of the radio's bookkeeping,
 * and lives in `model` so that it runs on the JVM; this hands the app's calls to it.
 */
class PooledRanging(private val pool: RangingPool) : RangingRadio {
    override fun capacity(): Int = pool.capacity()
    override fun spares(): List<RangingSpare> = pool.spares()
    override fun drain(): List<RangingReading> = pool.drain()
    override fun start(handle: RangingHandle, own: SpareKey, peer: RangingToken) = pool.start(handle, own, peer)
    override fun stop(handle: RangingHandle) = pool.stop(handle)
    override fun open() = pool.open()
    override fun silence() = pool.silence()
    override fun faultBegan(): RadioCause? = pool.faultBegan()
}

/**
 * **This phone's Nearby Interaction radio** (D-303): a [RangingPool] of `NISession`s. It opens no
 * session until [RangingRadio.open], which is the round's seed (D-067: built at arming, not before),
 * and every callback lands on the main thread, where the uploader runs. [log] is the device log.
 */
expect fun nearbyRangingRadio(log: (String) -> Unit): RangingRadio

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
