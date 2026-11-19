package home.someoneshome.model.protocol

import home.someoneshome.model.RadioCause
import kotlin.math.roundToInt

/**
 * **How a phone's Nearby Interaction session ended**, as much as the pool needs to know (D-303).
 * The iOS radio maps `NIError` codes onto these four; nothing here names an iOS type.
 */
enum class SessionEnd {
    /** iOS has no room for another session (`activeSessionsLimitExceeded`): one fewer from now. */
    LimitReached,

    /** The person refused Nearby Interaction (`userDidNotAllow`): their act (D-187, D-223). */
    NotAllowed,

    /** This phone cannot range at all (`unsupportedPlatform`). */
    Unsupported,

    /** Anything else: a resource timeout, a failed session, an invalidation. The phone's own. */
    Failed,
}

/**
 * **What the pool asks of the phone's Nearby Interaction**: one session per slot, `0` until
 * [RangingWire.MAX_PEERS]. The iOS radio is `NISession`s; a test holds a fake. Every call is made
 * on one thread, the one the session callbacks arrive on.
 *
 * A session is named by its slot and the [generation] it was opened with, so a callback from a
 * session the pool has already closed or replaced carries an old generation and is nothing (the
 * spike's `==` fix, cff978e, solved the same staleness by comparing sessions).
 */
interface RangingSessions {
    /** Open a fresh idle session in [slot]: its archived discovery token, or null if it gave none. */
    fun open(slot: Int, generation: Int): ByteArray?

    /** Run [slot]'s idle session against [peer]. False when the peer's token does not read. */
    fun run(slot: Int, peer: RangingToken): Boolean

    /** Run [slot]'s session again against the peer it last ran with: a suspension ended, or a timeout. */
    fun rerun(slot: Int)

    /** End [slot]'s session. Whatever it says after this is nothing. */
    fun close(slot: Int)
}

/**
 * **The phone's UWB pool, with nothing iOS in it** (D-303, the U6 plan's unit 4): the capacity, the
 * idle sessions offered as spares, the plan applied to them, the nearest reading per pair, and the
 * faults. The Nearby Interaction radio is this and a [RangingSessions] of `NISession`s; it lives in
 * `model` beside [RangingCodec] so that it runs, and is measured, on the JVM.
 *
 * ### The capacity
 *
 * [RangingWire.STARTING_CAPACITY] at every [open], one less each time iOS answers
 * [SessionEnd.LimitReached], never below 0. A refusal ([SessionEnd.NotAllowed]) or a phone that
 * cannot range ([SessionEnd.Unsupported]) holds nothing and says 0 until the next round.
 *
 * ### The pool
 *
 * It holds capacity's worth of sessions. One that is not ranging is *idle*, offered under a key this
 * phone drew ([spares]). [start] runs the idle session the house named against the peer's token;
 * [stop] ends the pair and opens a fresh idle session, with a new key and token, in its place. **A
 * session is run against one peer, ever**: a stopped or failed one is retired and replaced, never
 * run again with another. A spare the house deals twice (D-302's hand-back) is one this phone never
 * started, so its one run is the second peer's.
 *
 * ### Readings (rule 7)
 *
 * [read] writes the nearest centimetres and a count into two fixed arrays, per slot, and allocates
 * nothing; [drain] turns them into the second's list once a beat and starts the second again. A pair
 * with no reading this second is listed with none, as the house's reconciliation needs (D-300).
 *
 * ### Faults (rule 6; T2, D-222; D-223)
 *
 * [SessionEnd.NotAllowed] is the person's; [SessionEnd.LimitReached], [SessionEnd.Unsupported], a
 * token that cannot be offered, and the failure of a session that was ranging are the device's. An
 * idle spare that iOS ends is replaced and reported to nobody: it was measuring nothing. A
 * suspension is not a fault here; leaving the foreground is already reported (D-229). Faults
 * within [SETTLE_DRAINS] beats of the last are one onset, which [faultBegan] hands over once; a
 * person's refusal is never folded into a device's onset.
 *
 * Not thread-safe, on purpose: every call is made on the main thread on iOS.
 */
class RangingPool(
    private val sessions: RangingSessions,
    /** A fresh random key for an idle session: `secureRandomBytes` on a phone. */
    private val nextKey: () -> Long,
    /** The device log, and only the log (rule 6). */
    private val log: (String) -> Unit = {},
) {
    private val slots = RangingWire.MAX_PEERS
    private val state = IntArray(slots)
    private val key = LongArray(slots)
    private val handle = LongArray(slots)
    private val generation = IntArray(slots)
    private val spare = arrayOfNulls<RangingSpare>(slots)
    private val nearest = IntArray(slots)
    private val count = IntArray(slots)
    private val openedAt = LongArray(slots)
    private val fastFails = IntArray(slots)

    private var capacity = RangingWire.STARTING_CAPACITY
    private var refused = false
    private var unsupported = false

    /** Beats drained: the pool's only clock. */
    private var drains = 0L

    private var pending: RadioCause? = null
    private var onset: RadioCause? = null
    private var quietFrom = 0L

    /** How many peers this phone can range at once, now. */
    fun capacity(): Int = if (refused || unsupported) 0 else capacity

    /** Every idle session's key and token. */
    fun spares(): List<RangingSpare> {
        val out = ArrayList<RangingSpare>(slots)
        for (i in 0 until slots) if (state[i] == IDLE) spare[i]?.let(out::add)
        return out
    }

    /** Every pair being ranged, with the nearest reading this second and how many; then a new second. */
    fun drain(): List<RangingReading> {
        drains++
        val out = ArrayList<RangingReading>(slots)
        for (i in 0 until slots) {
            if (state[i] != RUNNING) continue
            out += if (count[i] == 0) RangingReading(RangingHandle(handle[i]), null, 0) else RangingReading(RangingHandle(handle[i]), nearest[i], count[i])
            count[i] = 0
        }
        return out
    }

    /** Range [pair] on the idle session [own] against [peer]. A pair already held, or a key not idle, is nothing. */
    fun start(pair: RangingHandle, own: SpareKey, peer: RangingToken) {
        for (i in 0 until slots) if (state[i] == RUNNING && handle[i] == pair.bits) return
        var at = -1
        for (i in 0 until slots) if (state[i] == IDLE && key[i] == own.bits) at = i
        if (at < 0) return
        if (!sessions.run(at, peer)) {
            log("A PEER'S TOKEN DID NOT READ — THE SPARE STAYS IDLE")
            return
        }
        state[at] = RUNNING
        handle[at] = pair.bits
        count[at] = 0
    }

    /** Stop ranging [pair], and open a fresh idle session in its place. A pair not held is nothing. */
    fun stop(pair: RangingHandle) {
        for (i in 0 until slots) {
            if (state[i] == RUNNING && handle[i] == pair.bits) {
                retire(i)
                fill()
                return
            }
        }
    }

    /** A round began: start the capacity again and open the pool. */
    fun open() {
        retireAll()
        capacity = RangingWire.STARTING_CAPACITY
        refused = false
        unsupported = false
        resetFaults()
        fastFails.fill(0)
        fill()
    }

    /** The round is over: close every session. */
    fun silence() {
        retireAll()
        resetFaults()
    }

    /** **A fault began since the last call, and whose**; null when none did. Once per onset. */
    fun faultBegan(): RadioCause? = pending.also { pending = null }

    // ---- What the sessions say ---------------------------------------------------------------

    /** One distance from [slot]'s session, in metres. Allocates nothing (rule 7). */
    fun read(slot: Int, generation: Int, metres: Float) {
        if (!current(slot, generation) || state[slot] != RUNNING || !metres.isFinite()) return
        val cm = (metres * 100f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        if (count[slot] == 0 || cm < nearest[slot]) nearest[slot] = cm
        if (count[slot] < RangingWire.MOST_READINGS) count[slot]++
    }

    /** [slot]'s session was suspended (a lock, the background): it reads nothing until it is run again. */
    fun suspended(slot: Int, generation: Int) {
        if (!current(slot, generation)) return
        log("SESSION ${slot + 1} SUSPENDED")
    }

    /** [slot]'s suspension ended: run it again, against the same peer, with the same token. */
    fun resumed(slot: Int, generation: Int) {
        if (!current(slot, generation) || state[slot] != RUNNING) return
        log("SESSION ${slot + 1} BACK FROM A SUSPENSION — RUNNING AGAIN")
        sessions.rerun(slot)
    }

    /** [slot]'s peer went away: run again after a timeout; a peer that ended its own session waits for the house's stop. */
    fun lost(slot: Int, generation: Int, timedOut: Boolean) {
        if (!current(slot, generation) || state[slot] != RUNNING || !timedOut) return
        sessions.rerun(slot)
    }

    /** [slot]'s session ended, and why. */
    fun ended(slot: Int, generation: Int, why: SessionEnd) {
        if (!current(slot, generation)) return
        val wasRunning = state[slot] == RUNNING
        val young = drains - openedAt[slot] < FAST_DRAINS
        retire(slot)
        log("SESSION ${slot + 1} ENDED: $why${if (wasRunning) " WHILE RANGING" else " WHILE IDLE"}")
        when (why) {
            SessionEnd.LimitReached -> {
                if (capacity > 0) capacity--
                fault(RadioCause.Device)
                trim()
            }
            SessionEnd.NotAllowed -> {
                refused = true
                retireAll()
                fault(RadioCause.Person)
            }
            SessionEnd.Unsupported -> {
                unsupported = true
                retireAll()
                fault(RadioCause.Device)
            }
            SessionEnd.Failed -> {
                if (wasRunning) fault(RadioCause.Device)
                fastFails[slot] = if (young) fastFails[slot] + 1 else 0
                fill()
            }
        }
    }

    // ---- Inside ------------------------------------------------------------------------------

    private fun current(slot: Int, generation: Int): Boolean =
        slot in 0 until slots && state[slot] != EMPTY && this.generation[slot] == generation

    private fun held(): Int {
        var n = 0
        for (i in 0 until slots) if (state[i] != EMPTY) n++
        return n
    }

    /** Open idle sessions until capacity's worth are held, skipping a slot that keeps failing young. */
    private fun fill() {
        if (refused || unsupported) return
        var held = held()
        for (i in 0 until slots) {
            if (held >= capacity) return
            if (state[i] != EMPTY || fastFails[i] >= MOST_FAST_FAILS) continue
            if (openAt(i)) held++
        }
    }

    private fun openAt(i: Int): Boolean {
        generation[i]++
        val bytes = sessions.open(i, generation[i])
        if (bytes == null || bytes.size !in 1..RangingWire.TOKEN_BYTES) {
            sessions.close(i)
            fastFails[i]++
            log("SESSION ${i + 1} OFFERED NO TOKEN THAT FITS (${bytes?.size ?: 0} BYTES)")
            fault(RadioCause.Device)
            return false
        }
        var k = nextKey()
        while (keyHeld(k)) k = nextKey()
        state[i] = IDLE
        key[i] = k
        spare[i] = RangingSpare(SpareKey(k), RangingToken.of(bytes))
        openedAt[i] = drains
        count[i] = 0
        return true
    }

    private fun keyHeld(k: Long): Boolean {
        for (i in 0 until slots) if (state[i] != EMPTY && key[i] == k) return true
        return false
    }

    /** Fewer sessions than are held may be kept now: the idle ones go first, the last slot first. */
    private fun trim() {
        var over = held() - capacity
        for (i in slots - 1 downTo 0) if (over > 0 && state[i] == IDLE) { retire(i); over-- }
        for (i in slots - 1 downTo 0) if (over > 0 && state[i] == RUNNING) { retire(i); over-- }
    }

    private fun retire(i: Int) {
        if (state[i] == EMPTY) return
        sessions.close(i)
        state[i] = EMPTY
        spare[i] = null
        count[i] = 0
    }

    private fun retireAll() {
        for (i in 0 until slots) retire(i)
    }

    private fun fault(cause: RadioCause) {
        val quiet = drains >= quietFrom
        if (quiet || (cause == RadioCause.Person && onset == RadioCause.Device)) {
            onset = cause
            pending = cause
        } else if (cause == RadioCause.Person && pending != null) {
            onset = cause
            pending = cause
        }
        quietFrom = drains + SETTLE_DRAINS
    }

    private fun resetFaults() {
        pending = null
        onset = null
        quietFrom = 0L
    }

    companion object {
        private const val EMPTY = 0
        private const val IDLE = 1
        private const val RUNNING = 2

        /** Faults within this many beats of the last are one onset (Bluetooth's two-second settle, D-222). */
        const val SETTLE_DRAINS: Long = 2L

        /** A session that ends inside this many beats of opening ended young. */
        const val FAST_DRAINS: Long = 2L

        /** A slot whose sessions end young this many times running is left empty until the next round. */
        const val MOST_FAST_FAILS: Int = 3
    }
}
