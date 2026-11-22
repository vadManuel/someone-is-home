package home.someoneshome.core

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.RangingCodec
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPlan
import home.someoneshome.model.protocol.RangingPlanSource
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingReportView
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.SpareKey

/*
 * **The house pairs phones for UWB: Bluetooth picks who is near, UWB measures them** (U6, D-300).
 *
 * Beside `resolveContacts` and for its reason: something above the rules has to turn what the
 * radios said into what happens next, and that needs knowledge `reduce` never holds (here, which
 * phone offered which token). It mints no `Event` and changes no state the rules hold. What it
 * decides is which phones are told to range which.
 *
 * **The rules read its distances, in every round** (D-301, D-308: there are no Bluetooth rounds): the desk
 * opens each upload's report first ([RangingDesk.read]) and resolves every distance to the seat at
 * the other end of a pair the house keeps, for `resolveContacts` and `resolvePlacements`; then the
 * pairing is told and the plan sealed, with the reader's report slot in it ([RangingDesk.answer]).
 *
 * ### Only close pairs the game needs (D-306)
 *
 * The owner's ruling (2026-11-20): *UWB only comes in play during precise measurement situations and
 * Bluetooth is the one running most of the game.* So the house pairs two phones only while **both**
 * hold: Bluetooth says they are close (the pair's strength at or above
 * [RangingGate.NEAR_RSSI_FLOOR]), and **the game needs the pair** ([RangingNeed], asked of every pair
 * heard on every upload): one of the rules that read a UWB distance could fire for it. The three, in
 * [rangingNeeded], each asked through the rule's own predicate and never a copy:
 *
 * - **Revoke**: one seat holds an armed Revoke that a touch would land on the other
 *   ([revokeLands], `Rules.contact`'s question), both phones living (a touch by distance reads only
 *   living phones, [readsDistancesOf]).
 * - **Report**: one seat may hold a report ([holdMayRun]: a round that reads distance, living, no
 *   meeting) on the other ([holdMayTarget]: Revoked, not yet reported).
 * - **Map**: one seat is a sensor ([sensingRoom]) and the other a seat it could place ([couldPlace]).
 *
 * Every other pair is ranked as if it were not heard: it is never formed, never ranks ahead of a
 * needed one, and never displaces. A kept pair that stops being needed or close is kept
 * [RangingGate.NEED_GRACE_TICKS] longer, so one noisy reading or one upload's gap does not end it, and
 * then goes the way any pair goes: the house stops keeping it and each phone is told Stop. A phone
 * that says it can range nobody (capacity 0: NEARBY DEVICES refused, no support, the session limit)
 * is paired with nobody, so no rule reads a distance of it or from it (D-308).
 *
 * ### What it does, once per upload ([RangingPairing.arrive])
 *
 * 1. **Ranks each seat's peers by Bluetooth**, from the 24 RSSI slots every upload already carries,
 *    resolved through the contact resolver's own token book. A pair's strength is its strongest
 *    reading, either way round, among its last [RangingGate.HEARD_KEPT] inside
 *    [RangingGate.HEARD_TICKS]: one side is enough, so a phone that is out, which advertises and
 *    never scans (D-008), is still ranked by the phones that hear it. **Only a pair that is close and
 *    needed, or was within [RangingGate.NEED_GRACE_TICKS], is ranked at all** (D-306).
 * 2. **Keeps the pairs that are still candidates** (hysteresis): a pair stays while each phone is
 *    among the other's strongest *capacity + [RangingGate.KEEP_MARGIN]* peers, both phones keep
 *    saying they hold it ([RangingGate.HOLD_GRACE_TICKS]), and neither phone's degree passes its
 *    capacity.
 * 3. **Lets two phones that rank each other first displace a kept pair** (D-302): when the pair
 *    both phones rank strongest is not kept and a phone of it is full, that phone's weakest kept
 *    pair goes, if it sits past the phone's entry rank and is at least
 *    [RangingGate.DISPLACE_MARGIN_DB] weaker. Without it a kept pair went only once it fell past
 *    *capacity + 2*, which in a small round never happens, and a held touch with a peer the house
 *    never picked could never land.
 * 4. **Forms new pairs greedily, strongest first**, each only between two phones that are close and
 *    needed on this upload (D-306), each among the other's strongest *capacity* peers, both under
 *    their capacity and both with a spare token the house has not dealt. Entry is stricter than
 *    staying, so a pair does not flap at the edge.
 *    A pair both phones rank first that waits only on a spare holds both phones' room for itself.
 *    A spare dealt into a pair that was let go before its phone ever listed it is handed back, and
 *    dealt again once the phone's next report still offers it idle (D-302).
 * 5. **Deals each new pair a handle drawn from the round's seed** under its own label, indexed by
 *    how many pairs the round has formed ([SeedPurpose.RangingHandle]). Never from a seat: no
 *    phone can tell from a handle who it is ranging, and a pair formed again gets a new one.
 * 6. **Plans the uploading phone's second**: stop every pair it holds that the house no longer
 *    keeps, start every kept pair it does not yet hold (on its own spare, against the peer's; at most
 *    [RangingWire.TOKEN_SLOTS] a second), and keep the rest.
 *
 * ### Symmetric by construction
 *
 * A pair is one record naming both seats and both spares; each phone's plan is read off the same
 * records, so if one phone of a pair is told to start it, the other is told on its own next upload,
 * with the other's token.
 *
 * ### It reads the round's state, by the owner's ruling (D-306)
 *
 * Until D-306 the pairing took nothing but the round's seed and seats, *never a role, an armed state
 * or an out list*, so that the plan a phone is dealt could say nothing about anybody to whoever held
 * that phone's seed. **The owner ruled on 2026-11-20 that the pairing may read roles and game
 * state**: a fact that lives only inside a player's own phone (its memory, its logs, a modified
 * build) is not a leak and is not designed against. What still holds, and is not this file's to
 * move: nothing a person can see, hear or feel changes, and the wire is one width and one cadence
 * for every phone. Every upload is still answered with one sealed plan of
 * [RangingWire.PLAN_CHARS] characters, a phone with no pairs included (D-217, D-300), and the
 * uploads keep their width. So an armed Insider's phone is paired with the close phones its Revoke
 * could land on, and a Revoked phone with the close living phones that could still report it, and a
 * phone nobody needs is paired with nobody: all of it inside plans of one width.
 *
 * [RangingPairing] itself still takes no state: the need is asked through [RangingNeed], which the
 * desk answers from the round as it stands ([rangingNeeded]).
 *
 * ### Deterministic (rule 4), and held rather than rebuilt (rule 7)
 *
 * Every step walks seats in seat order and pairs in forming order, so a replay feeding the same
 * uploads against the same states rebuilds the same pairs, handles and plans: the need is a function
 * of the state, and the state of the recording. Its clock is the arrival ticks with every
 * stretch in which no upload arrived at all closed up to one upload ([RangingGate.SILENCE_TICKS],
 * D-302): a house off the air, or a clock that jumps, is not time the phones spent apart, so the
 * pairs they still hold are not let go for it. It is not recorded: its inputs (the uploads)
 * are not, exactly as the contact resolver's are not, and nothing it produces reaches the rules.
 * Unlike the resolver its memory is held in arrays sized at arming and written in place: it runs
 * for every seat every second, and rebuilding its lists each time cost the host more than the
 * whole-app budget allows (D-300). A token is kept once per offered key, so a phone offering the
 * same idle sessions second after second costs the house nothing new. `RangingAllocationTest`
 * guards it.
 */

/**
 * **The pairing's tolerances, named, in one place.** ⚠️ All provisional (D-300): the plan fixes the
 * shape, and phones will set the numbers.
 */
object RangingGate {

    /** How long a Bluetooth reading ranks a pair: three uploads. */
    const val HEARD_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /**
     * **A Bluetooth reading of zero or more is not a reading.** CoreBluetooth reports 127 when it has
     * no RSSI, and the upload carries it faithfully; ungated, *no reading* would rank as the nearest
     * peer possible from anywhere in the house.
     */
    const val RSSI_CEILING: Int = -1

    /**
     * **Readings kept per pair: 8.** Both phones of a pair hear each other once a second, so three
     * seconds is six; past eight the oldest goes first.
     */
    const val HEARD_KEPT: Int = 8

    /**
     * **How far down a phone's ranking a pair may slip and still be kept: two places past its
     * capacity.** Entry asks for the top *capacity*; staying asks for the top *capacity + 2*.
     */
    const val KEEP_MARGIN: Int = 2

    /**
     * **How long a pair may go without one of its phones saying it holds it: three uploads.** A new
     * pair starts the count at its forming, so a Start lost on the way is sent again for three
     * seconds before the pair is let go and formed afresh.
     */
    const val HOLD_GRACE_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /** How long a phone's last report stands for it: three uploads. Older, it can hold nothing. */
    const val REPORT_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /** Spare keys remembered as dealt per seat, so a stale upload's offer is not dealt twice. */
    const val DEALT_KEPT: Int = 4 * RangingWire.MAX_PEERS

    /**
     * **How much stronger, in dB, a pair both phones rank first must be than the kept pair it
     * displaces: 10** (D-302). **Provisional (overnight).**
     *
     * Measured phone to phone (D-233): touching firmly −27, loosely −37, 30 cm −43 (−41 to −45),
     * 1 m −57. Ten dB is more than twice the 4 dB spread at one distance, so jitter alone never
     * displaces a pair, and taking it back needs a 20 dB swing the other way, a tenfold change of
     * distance, so two nearly tied candidates cannot trade places beat by beat. It is small enough
     * that a touch at the contact gate (−40) displaces anything at about half a metre or farther, and
     * a firm touch anything not itself touching. And it leaves D-300's *a peer just ahead of the
     * kept one*, twice as near (6 dB), unable to displace it.
     */
    const val DISPLACE_MARGIN_DB: Int = 10

    /**
     * **A stretch longer than this in which no upload arrived from any phone is the house off the
     * air, not time passing between the phones: two uploads** (D-302). **Provisional (overnight).**
     * Every phone uploads every second, so two seconds with nothing from any of them is no network
     * or a jump of the house's clock (D-225), never a quiet room. The pairing closes such a stretch
     * up to one upload: the phones still hold their sessions, and a pair is let go only once its
     * phones stop listing it, by the grace above.
     */
    const val SILENCE_TICKS: Long = 2L * ContactGate.UPLOAD_TICKS

    /**
     * ⚠️ **Provisional: −68 dBm, *Bluetooth says they are close*, about two and a half metres**
     * (D-306). A pair is formed for UWB only at or above this, and only if the game needs it.
     *
     * The owner asked for roughly 2 to 3 m. Measured phone to phone (D-233): 30 cm −43, 1 m −57, which
     * is about 27 dB a decade; carried on, 2 m reads about −65 and 3 m about −70, so −68 is about
     * 2.6 m on that slope (and 4 m on the bench's 20 dB curve, so the bench's spread-out phones, 5 m
     * apart at −70, are not close). It sits 28 dB under D-233's loose touch (−40); the map's 1.5 m
     * reach reads about −62 on D-233's slope, 6 dB inside it, more than the 4 dB a reading jitters at
     * one distance. A
     * pair's strength is its strongest reading in the last three seconds, so a pair walking in is
     * judged by its nearest moment, not its average.
     *
     * **The pace it allows, said plainly**: at a walk (about 1.4 m/s) 2.6 m to a touch is about two
     * seconds, and from the first close reading the pair needs one to two uploads to be started on
     * both phones and a beat for the radio to read. A deliberate approach is ranged well before it
     * touches; a brisk pass from out of range is at the edge. Phones set the number.
     */
    const val NEAR_RSSI_FLOOR: Int = -68

    /**
     * ⚠️ **Provisional: two uploads, the grace on a pair the game stopped needing** (D-306). A kept
     * pair whose need has ended (the Revoke spent or lapsed, the seat reported, the sensor no longer
     * performing) or whose strength fell under [NEAR_RSSI_FLOOR] is kept this much longer before it
     * goes the way any pair goes (its phones told Stop). Entry asks for the need and the floor on
     * the upload itself, so this is the same entry-stricter-than-staying hysteresis as
     * [KEEP_MARGIN]: one reading under the floor, or a state that flickers for one upload, does not
     * stop a session the next second would start again under a new handle.
     */
    const val NEED_GRACE_TICKS: Long = 2L * ContactGate.UPLOAD_TICKS
}

/**
 * **Whether the game needs [a] and [b] ranged at [at]** (D-306): asked by [RangingPairing] of every
 * pair Bluetooth says is close, on every upload. [at] is the house's tick, as the rules read it. The
 * house's answer is [rangingNeeded], from the round as it stands; a test of the pairing's mechanics
 * alone may answer yes to everything.
 */
fun interface RangingNeed {
    fun needs(a: Seat, b: Seat, at: Tick): Boolean
}

/**
 * **Whether the round [state] needs [a] and [b] ranged at [at]** (D-306): at least one rule that
 * reads a UWB distance could fire for the pair, either way round. Never before arming or after the
 * end. Each of the three is the rule's own predicate:
 *
 * 1. **A Revoke**: [revokeLands] (`Rules.contact`), both phones living ([readsDistancesOf]: a touch
 *    by distance reads both phones, and none of an out phone's).
 * 2. **A report**: [holdMayRun] and [holdMayTarget] (the resolver's hold), grace aside: the grace
 *    after a touch (D-291) is five seconds and the pair is wanted at its end.
 * 3. **The map**: [sensingRoom] (`resolvePlacements`'s sensor) and [couldPlace].
 *
 * [facts] are [state]'s ([RoundContactFacts]), passed in so that asking allocates nothing.
 */
internal fun rangingNeeded(state: GameState, facts: ContactFacts, a: Seat, b: Seat, at: Tick): Boolean {
    if (!state.armed || state.ended) return false
    return revokeNeeds(state, facts, a, b, at) || revokeNeeds(state, facts, b, a, at) ||
        reportNeeds(facts, a, b) || reportNeeds(facts, b, a) ||
        mapNeeds(state, a, b, at) || mapNeeds(state, b, a, at)
}

private fun revokeNeeds(state: GameState, facts: ContactFacts, actor: Seat, target: Seat, at: Tick): Boolean =
    facts.readsDistancesOf(actor) && facts.readsDistancesOf(target) && state.revokeLands(actor, target, at)

private fun reportNeeds(facts: ContactFacts, reader: Seat, target: Seat): Boolean =
    facts.holdMayRun(reader) && facts.holdMayTarget(reader, target)

private fun mapNeeds(state: GameState, sensor: Seat, seat: Seat, at: Tick): Boolean =
    couldPlace(state, sensor, seat) && sensingRoom(state, sensor, at) != null

/**
 * **The house's [RangingNeed]: [rangingNeeded] for the round as it stands** (D-306). Held by the desk
 * and pointed at the current state before each upload is paired, so asking allocates nothing.
 */
internal class RoundRangingNeed : RangingNeed {
    private val facts = RoundContactFacts(GameState.EMPTY)

    var state: GameState
        get() = facts.state
        set(value) {
            facts.state = value
        }

    override fun needs(a: Seat, b: Seat, at: Tick): Boolean = rangingNeeded(facts.state, facts, a, b, at)

    /** [seat]'s upload said whether its phone is on the main view (D-307), for [holdMayRun]. */
    fun sawMainView(seat: Seat, on: Boolean) = facts.sawMainView(seat, on)
}

/**
 * **One pair the house keeps, as a value**: [low] < [high], the [handle] both phones know it by, the
 * spare each phone offered for it, the pair's [ordinal] in the round's forming order, and when each
 * phone last said it holds it (its forming, until then). What [RangingPairing.pairs] reads out, for
 * the bench and the tests; the house holds the same facts in arrays.
 */
data class RangingPair(
    val low: Seat,
    val high: Seat,
    val handle: RangingHandle,
    val lowSpare: RangingSpare,
    val highSpare: RangingSpare,
    val ordinal: Long,
    val lowHeldAt: Tick,
    val highHeldAt: Tick,
) {
    fun has(seat: Seat): Boolean = low.index == seat.index || high.index == seat.index
}

/**
 * **The house's UWB pairing for one round** (U6, D-300): built at arming from the round's seed and
 * its seats, and handed every upload in arrival order.
 *
 * For each upload: [hear] each Bluetooth reading it carried (already resolved to a seat), then
 * [arrive] with its report and the game's [RangingNeed] (D-306); the uploading phone's plan for this
 * second is then [plan], a [RangingPlanSource] the codec seals from directly. **Not thread-safe**:
 * the house calls it under its one lock.
 */
class RangingPairing(private val roundSeed: Long, seats: List<Seat>) {

    /** The round's seats, in seat order: slot `i` is the `i`th. */
    private val seatsInOrder: List<Seat> = seats.sortedBy { it.index }
    private val n = seatsInOrder.size
    private val slotOfIndex = IntArray((seatsInOrder.maxOfOrNull { it.index } ?: -1) + 1) { -1 }.also { slots ->
        seatsInOrder.forEachIndexed { slot, seat -> slots[seat.index] = slot }
    }

    private fun slotOf(seat: Int): Int = if (seat in slotOfIndex.indices) slotOfIndex[seat] else -1

    /** Whether [seat] is one of the round's. */
    fun holds(seat: Seat): Boolean = slotOf(seat.index) >= 0

    private val maxPeers = RangingWire.MAX_PEERS
    private val tokenSlots = RangingWire.TOKEN_SLOTS

    // ---- Bluetooth: the last readings of each unordered pair --------------------------------------
    private val heardRssi = IntArray(n * n * RangingGate.HEARD_KEPT)
    private val heardAt = LongArray(n * n * RangingGate.HEARD_KEPT) { NEVER }
    private val heardNext = IntArray(n * n)

    /** This upload's readings, strongest per peer, until [arrive] takes them. */
    private val hearing = IntArray(n) { NONE }

    // ---- The game's need, per unordered pair (D-306) -----------------------------------------------
    /** Whether each pair is close and needed on this upload: entry asks for this. */
    private val useful = BooleanArray(n * n)

    /** The pairing's tick each pair was last close and needed, or [NEVER]: staying asks for its grace. */
    private val usefulAt = LongArray(n * n) { NEVER }

    // ---- Each seat's last report -----------------------------------------------------------------
    private val capacity = IntArray(n)
    private val reportAt = LongArray(n) { NEVER }
    private val spareKey = LongArray(n * tokenSlots)
    private val spareToken = arrayOfNulls<RangingToken>(n * tokenSlots)
    private val spareCount = IntArray(n)
    private val heldHandle = LongArray(n * maxPeers)
    private val heldCm = IntArray(n * maxPeers)
    private val heldReadings = IntArray(n * maxPeers)
    private val heldCount = IntArray(n)
    private val newKey = LongArray(tokenSlots)
    private val newToken = arrayOfNulls<RangingToken>(tokenSlots)

    // ---- Spare keys already dealt, per seat: a ring ----------------------------------------------
    private val dealt = LongArray(n * RangingGate.DEALT_KEPT)
    private val dealtCount = IntArray(n)
    private val dealtNext = IntArray(n)

    /** Whether each remembered key is still dealt: false once handed back ([undeal], D-302). */
    private val dealtLive = BooleanArray(n * RangingGate.DEALT_KEPT)

    /** The arrival each key was handed back at: it is dealable again only on a report taken after. */
    private val dealtBack = LongArray(n * RangingGate.DEALT_KEPT)

    /** Arrivals counted, and the arrival each seat's last report was taken at. */
    private var arrivals = 0L
    private val reportSeq = LongArray(n) { -1L }

    // ---- The pairs, in forming order -------------------------------------------------------------
    private val most = n * maxPeers / 2 + 1
    private val pLow = IntArray(most)
    private val pHigh = IntArray(most)
    private val pHandle = LongArray(most)
    private val pLowKey = LongArray(most)
    private val pHighKey = LongArray(most)
    private val pLowToken = arrayOfNulls<RangingToken>(most)
    private val pHighToken = arrayOfNulls<RangingToken>(most)
    private val pOrdinal = LongArray(most)
    private val pLowHeld = LongArray(most)
    private val pHighHeld = LongArray(most)

    /** Whether each phone has ever listed the pair: until it does, its spare is still idle on it. */
    private val pLowListed = BooleanArray(most)
    private val pHighListed = BooleanArray(most)
    private var pairCount = 0
    private var formed = 0L

    // ---- The pairing's clock: arrival ticks with the house's silences closed up (D-302) -------------
    private var lastArrival = NEVER
    private var silent = 0L

    // ---- Scratch, held -----------------------------------------------------------------------------
    private val strength = IntArray(n * n)
    private val degree = IntArray(n)
    private val order = IntArray(most)
    private val keep = BooleanArray(most)
    private val candidates = IntArray(n * n)
    private val stopScratch = LongArray(maxPeers)
    private val first = IntArray(n)
    private val reserved = BooleanArray(n)

    // ---- The plan for the seat that just uploaded --------------------------------------------------
    private val stepKind = IntArray(maxPeers)
    private val stepHandle = LongArray(maxPeers)
    private val stepOwn = LongArray(maxPeers)
    private val stepPeer = arrayOfNulls<RangingToken>(maxPeers)
    private var stepCount = 0

    /** **The plan for the seat that last [arrive]d**: read it, or seal it, before the next arrival. */
    val plan: RangingPlanSource = object : RangingPlanSource {
        override val stepCount: Int get() = this@RangingPairing.stepCount
        override fun stepKind(i: Int): Int = this@RangingPairing.stepKind[i]
        override fun stepHandle(i: Int): Long = this@RangingPairing.stepHandle[i]
        override fun stepOwn(i: Int): Long = this@RangingPairing.stepOwn[i]
        override fun stepPeer(i: Int): RangingToken? = this@RangingPairing.stepPeer[i]
    }

    /** **This upload heard [seat] at [rssi]**: one Bluetooth slot, resolved. Before [arrive]. */
    fun hear(seat: Int, rssi: Int) {
        val p = slotOf(seat)
        if (p < 0 || rssi > RangingGate.RSSI_CEILING) return
        if (hearing[p] == NONE || rssi > hearing[p]) hearing[p] = rssi
    }

    /**
     * **[seat]'s upload, arriving at [at]**: the readings [hear]d for it, and its ranging [report]
     * (null when the field did not read). [taken] is false for a transport's second delivery or a
     * seat not of the round: nothing it carried is taken, and the plan is read off what the house
     * holds. [need] is asked of every close pair, at [at] (D-306). Afterwards [plan] is this seat's.
     */
    fun arrive(at: Tick, seat: Seat, report: RangingReportView?, taken: Boolean, need: RangingNeed) {
        val now = clock(at.step)
        arrivals++
        val s = slotOf(seat.index)
        if (taken && s >= 0) {
            for (p in 0 until n) {
                val rssi = hearing[p]
                if (rssi == NONE || p == s) continue
                val pair = if (s < p) s * n + p else p * n + s
                val k = pair * RangingGate.HEARD_KEPT + heardNext[pair]
                heardRssi[k] = rssi
                heardAt[k] = now
                heardNext[pair] = (heardNext[pair] + 1) % RangingGate.HEARD_KEPT
            }
            if (report != null) take(s, now, report)
        }
        hearing.fill(NONE)

        strengths(now, at, need)
        rankFirst()
        keepPairs(now)
        displacePairs(now)
        formPairs(now)
        planFor(s)
    }

    /**
     * **The pairing's own tick for an arrival at [step]** (D-302): the house's, less every stretch in
     * which no upload arrived for longer than [RangingGate.SILENCE_TICKS], each closed up to one
     * upload. Every arrival counts, a second delivery or a seat not of the round included: any of
     * them is the house on the air.
     */
    private fun clock(step: Long): Long {
        if (lastArrival != NEVER && step - lastArrival > RangingGate.SILENCE_TICKS) {
            silent += step - lastArrival - ContactGate.UPLOAD_TICKS
        }
        if (lastArrival == NEVER || step > lastArrival) lastArrival = step
        return step - silent
    }

    /** [report] becomes seat [s]'s, and every pair it lists is held at [now]. */
    private fun take(s: Int, now: Long, report: RangingReportView) {
        val spares = minOf(report.spareCount, tokenSlots)
        for (i in 0 until spares) {
            val key = report.spareKey(i)
            var token: RangingToken? = null
            // A key offered before keeps the token taken then, so an idle session costs one copy.
            for (j in 0 until spareCount[s]) {
                val old = spareToken[s * tokenSlots + j]
                if (spareKey[s * tokenSlots + j] == key && old != null && report.spareTokenIs(i, old)) {
                    token = old
                    break
                }
            }
            newKey[i] = key
            newToken[i] = token ?: report.spareToken(i)
        }
        for (i in 0 until tokenSlots) {
            spareKey[s * tokenSlots + i] = if (i < spares) newKey[i] else 0L
            spareToken[s * tokenSlots + i] = if (i < spares) newToken[i] else null
            newToken[i] = null
        }
        spareCount[s] = spares
        val readings = minOf(report.readingCount, maxPeers)
        for (i in 0 until readings) {
            heldHandle[s * maxPeers + i] = report.readingHandle(i)
            heldReadings[s * maxPeers + i] = report.readingReadings(i)
            heldCm[s * maxPeers + i] = report.readingCm(i)
        }
        heldCount[s] = readings
        capacity[s] = report.capacity.coerceIn(0, maxPeers)
        reportAt[s] = now
        reportSeq[s] = arrivals
        for (q in 0 until pairCount) {
            if (!holdsHandle(s, pHandle[q])) continue
            if (pLow[q] == s) {
                pLowHeld[q] = now
                pLowListed[q] = true
            }
            if (pHigh[q] == s) {
                pHighHeld[q] = now
                pHighListed[q] = true
            }
        }
    }

    private fun holdsHandle(s: Int, handle: Long): Boolean {
        for (i in 0 until heldCount[s]) if (heldHandle[s * maxPeers + i] == handle) return true
        return false
    }

    /**
     * Every pair's strength now: its strongest reading inside the window, or [NONE]; and **[NONE] for a
     * pair the game does not want ranged** (D-306): one that is not close and needed now
     * ([useful]) and has not been within [RangingGate.NEED_GRACE_TICKS]. Everything after reads
     * strength alone, so an unwanted pair is ranked, kept, displaced and formed as one not heard.
     */
    private fun strengths(now: Long, at: Tick, need: RangingNeed) {
        for (a in 0 until n) {
            strength[a * n + a] = NONE
            for (b in a + 1 until n) {
                val pair = a * n + b
                var best = NONE
                for (k in pair * RangingGate.HEARD_KEPT until (pair + 1) * RangingGate.HEARD_KEPT) {
                    val t = heardAt[k]
                    if (t == NEVER || now - t > RangingGate.HEARD_TICKS) continue
                    if (best == NONE || heardRssi[k] > best) best = heardRssi[k]
                }
                val close = best != NONE && best >= RangingGate.NEAR_RSSI_FLOOR
                val wantedNow = close && need.needs(seatsInOrder[a], seatsInOrder[b], at)
                useful[pair] = wantedNow
                if (wantedNow) usefulAt[pair] = now
                val wanted = best != NONE && usefulAt[pair] != NEVER && now - usefulAt[pair] <= RangingGate.NEED_GRACE_TICKS
                val s = if (wanted) best else NONE
                strength[pair] = s
                strength[b * n + a] = s
            }
        }
    }

    /** Each seat's strongest peer, as [within] ranks them (stronger, then the lower seat), or -1. */
    private fun rankFirst() {
        for (a in 0 until n) {
            var best = -1
            var bestStrength = NONE
            for (c in 0 until n) {
                if (c == a) continue
                val sac = strength[a * n + c]
                if (sac == NONE) continue
                if (best < 0 || sac > bestStrength) {
                    best = c
                    bestStrength = sac
                }
            }
            first[a] = best
        }
    }

    /** Whether [a] and [b] each rank the other first. */
    private fun mutuallyFirst(a: Int, b: Int): Boolean = first[a] == b && first[b] == a

    private fun capacityOf(s: Int, now: Long): Int =
        if (reportAt[s] != NEVER && now - reportAt[s] <= RangingGate.REPORT_TICKS) capacity[s] else 0

    /** Whether [b] is among [a]'s strongest [depth] peers: ranked by strength, then by seat. */
    private fun within(a: Int, b: Int, depth: Int): Boolean {
        val sab = strength[a * n + b]
        if (sab == NONE || depth <= 0) return false
        var ahead = 0
        for (c in 0 until n) {
            if (c == a || c == b) continue
            val sac = strength[a * n + c]
            if (sac == NONE) continue
            if (sac > sab || (sac == sab && c < b)) {
                ahead++
                if (ahead >= depth) return false
            }
        }
        return true
    }

    private fun pairStrength(q: Int): Int = strength[pLow[q] * n + pHigh[q]]

    /** Keep: strongest first, then oldest; the kept stay in forming order. */
    private fun keepPairs(now: Long) {
        for (q in 0 until pairCount) order[q] = q
        for (i in 1 until pairCount) {
            val moving = order[i]
            var j = i - 1
            while (j >= 0 && keptBefore(moving, order[j])) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = moving
        }
        degree.fill(0)
        for (i in 0 until pairCount) {
            val q = order[i]
            keep[q] = false
            val low = pLow[q]
            val high = pHigh[q]
            if (now - pLowHeld[q] > RangingGate.HOLD_GRACE_TICKS || now - pHighHeld[q] > RangingGate.HOLD_GRACE_TICKS) continue
            if (!within(low, high, capacityOf(low, now) + RangingGate.KEEP_MARGIN)) continue
            if (!within(high, low, capacityOf(high, now) + RangingGate.KEEP_MARGIN)) continue
            if (degree[low] >= capacityOf(low, now) || degree[high] >= capacityOf(high, now)) continue
            keep[q] = true
            degree[low]++
            degree[high]++
        }
        compact()
    }

    /** Drops every pair [keep] does not mark, keeping the rest in forming order. */
    private fun compact() {
        var w = 0
        for (q in 0 until pairCount) {
            if (!keep[q]) {
                // A phone that never listed the pair never took up its spare: hand the key back.
                if (!pLowListed[q]) undeal(pLow[q], pLowKey[q])
                if (!pHighListed[q]) undeal(pHigh[q], pHighKey[q])
                continue
            }
            if (w != q) movePair(q, w)
            w++
        }
        for (q in w until pairCount) {
            pLowToken[q] = null
            pHighToken[q] = null
        }
        pairCount = w
    }

    /**
     * **Displace: a pair both phones rank first takes the room it needs** (D-302). For each such
     * pair the house does not keep, both phones ranging at all: a phone of it that is full gives up
     * its weakest kept pair, provided that pair is past the phone's entry rank and at least
     * [RangingGate.DISPLACE_MARGIN_DB] weaker than the pair taking its place. Both phones' sides are
     * judged before either is dropped, so a phone never loses a pair for a pair that cannot form.
     * The new pair forms below, at once if both phones have a spare, or as soon as the dropped
     * pairs' stops free one. Each seat ranks one peer first, so a seat gives up at most one pair an
     * upload; and a pair both rank first is never the weakest of either, so it is never displaced.
     */
    private fun displacePairs(now: Long) {
        for (q in 0 until pairCount) keep[q] = true
        var dropped = false
        for (a in 0 until n) {
            val b = first[a]
            // Only a pair that could form on this upload displaces (D-306): one in its need's grace could not.
            if (b <= a || !mutuallyFirst(a, b) || paired(a, b) || !useful[a * n + b]) continue
            val capA = capacityOf(a, now)
            val capB = capacityOf(b, now)
            if (capA <= 0 || capB <= 0) continue
            val s = strength[a * n + b]
            val fromA = if (degree[a] < capA) ROOM else yielding(a, s, capA)
            if (fromA == NO_PAIR) continue
            val fromB = if (degree[b] < capB) ROOM else yielding(b, s, capB)
            if (fromB == NO_PAIR) continue
            if (fromA >= 0) drop(fromA)
            if (fromB >= 0) drop(fromB)
            if (fromA >= 0 || fromB >= 0) dropped = true
        }
        if (dropped) compact()
    }

    /**
     * The kept pair [x] would give up for a pair of strength [s]: its weakest (the lowest in its
     * ranking), if that is past its entry rank [cap] and [RangingGate.DISPLACE_MARGIN_DB] weaker
     * than [s]; otherwise [NO_PAIR].
     */
    private fun yielding(x: Int, s: Int, cap: Int): Int {
        var weakest = NO_PAIR
        var weakestPeer = -1
        var weakestStrength = NONE
        for (q in 0 until pairCount) {
            if (!keep[q]) continue
            val peer = when (x) {
                pLow[q] -> pHigh[q]
                pHigh[q] -> pLow[q]
                else -> continue
            }
            val sp = strength[x * n + peer]
            if (weakest == NO_PAIR || sp < weakestStrength || (sp == weakestStrength && peer > weakestPeer)) {
                weakest = q
                weakestPeer = peer
                weakestStrength = sp
            }
        }
        if (weakest == NO_PAIR || weakestStrength == NONE) return NO_PAIR
        if (s.toLong() - weakestStrength.toLong() < RangingGate.DISPLACE_MARGIN_DB) return NO_PAIR
        if (within(x, weakestPeer, cap)) return NO_PAIR
        return weakest
    }

    private fun drop(q: Int) {
        keep[q] = false
        degree[pLow[q]]--
        degree[pHigh[q]]--
    }

    /** Whether pair [a] is judged before pair [b]: stronger, or as strong and formed earlier. */
    private fun keptBefore(a: Int, b: Int): Boolean {
        val sa = pairStrength(a)
        val sb = pairStrength(b)
        if (sa != sb) return sa > sb
        return pOrdinal[a] < pOrdinal[b]
    }

    private fun movePair(from: Int, to: Int) {
        pLow[to] = pLow[from]; pHigh[to] = pHigh[from]; pHandle[to] = pHandle[from]
        pLowKey[to] = pLowKey[from]; pHighKey[to] = pHighKey[from]
        pLowToken[to] = pLowToken[from]; pHighToken[to] = pHighToken[from]
        pOrdinal[to] = pOrdinal[from]; pLowHeld[to] = pLowHeld[from]; pHighHeld[to] = pHighHeld[from]
        pLowListed[to] = pLowListed[from]; pHighListed[to] = pHighListed[from]
    }

    private fun paired(a: Int, b: Int): Boolean {
        for (q in 0 until pairCount) if (pLow[q] == a && pHigh[q] == b) return true
        return false
    }

    /**
     * Form: strongest first, then by seat; each spare dealt once; only a pair close and needed on
     * this upload ([useful], D-306), never one only inside its need's grace. A pair both phones rank
     * first, with room on both but a spare missing on one (its stop not yet through, D-302), holds
     * both phones' room for itself this upload, so a weaker pair cannot take the place it displaced
     * for.
     */
    private fun formPairs(now: Long) {
        var count = 0
        for (a in 0 until n) for (b in a + 1 until n) {
            if (useful[a * n + b] && strength[a * n + b] != NONE && !paired(a, b)) candidates[count++] = a * n + b
        }
        for (i in 1 until count) {
            val moving = candidates[i]
            var j = i - 1
            while (j >= 0 && formedBefore(moving, candidates[j])) {
                candidates[j + 1] = candidates[j]
                j--
            }
            candidates[j + 1] = moving
        }
        reserved.fill(false)
        for (i in 0 until count) {
            val a = candidates[i] / n
            val b = candidates[i] % n
            if (reserved[a] || reserved[b]) continue
            val capA = capacityOf(a, now)
            val capB = capacityOf(b, now)
            if (degree[a] >= capA || degree[b] >= capB) continue
            if (!within(a, b, capA) || !within(b, a, capB)) continue
            val sa = spareOf(a, now)
            val sb = spareOf(b, now)
            if (sa < 0 || sb < 0) {
                if (mutuallyFirst(a, b)) {
                    reserved[a] = true
                    reserved[b] = true
                }
                continue
            }
            if (pairCount >= most) break
            var handle: Long
            do {
                formed++
                handle = rangingHandleFor(roundSeed, formed).bits
            } while (hasHandle(handle))
            deal(a, spareKey[a * tokenSlots + sa])
            deal(b, spareKey[b * tokenSlots + sb])
            val q = pairCount++
            pLow[q] = a; pHigh[q] = b; pHandle[q] = handle
            pLowKey[q] = spareKey[a * tokenSlots + sa]; pHighKey[q] = spareKey[b * tokenSlots + sb]
            pLowToken[q] = spareToken[a * tokenSlots + sa]; pHighToken[q] = spareToken[b * tokenSlots + sb]
            pOrdinal[q] = formed; pLowHeld[q] = now; pHighHeld[q] = now
            pLowListed[q] = false; pHighListed[q] = false
            degree[a]++
            degree[b]++
        }
    }

    private fun formedBefore(x: Int, y: Int): Boolean {
        val sx = strength[x]
        val sy = strength[y]
        if (sx != sy) return sx > sy
        return x < y
    }

    private fun hasHandle(handle: Long): Boolean {
        for (q in 0 until pairCount) if (pHandle[q] == handle) return true
        return false
    }

    /** The slot of [s]'s undealt spare with the lowest key (unsigned), or -1. */
    private fun spareOf(s: Int, now: Long): Int {
        if (reportAt[s] == NEVER || now - reportAt[s] > RangingGate.REPORT_TICKS) return -1
        var best = -1
        for (i in 0 until spareCount[s]) {
            val key = spareKey[s * tokenSlots + i]
            if (wasDealt(s, key)) continue
            if (best < 0 || key.toULong() < spareKey[s * tokenSlots + best].toULong()) best = i
        }
        return best
    }

    /**
     * Whether [s]'s [key] is dealt: dealt and not handed back, or handed back but not yet offered
     * again in a report taken after (the phone may have taken up the start late, and only its own
     * next report, offering the key idle, says it did not).
     */
    private fun wasDealt(s: Int, key: Long): Boolean {
        for (i in 0 until dealtCount[s]) {
            val k = s * RangingGate.DEALT_KEPT + i
            if (dealt[k] == key && (dealtLive[k] || reportSeq[s] <= dealtBack[k])) return true
        }
        return false
    }

    /**
     * **[s]'s spare [key] is handed back** (D-302): the pair it was dealt into was let go before [s]
     * ever listed it, so the session is still idle on the phone. Kept dealt, the phone would offer
     * it every second and the house refuse it every second, and a phone whose every spare went
     * that way could never be paired again in the round. It is dealable again once a report taken
     * after this arrival still offers it.
     */
    private fun undeal(s: Int, key: Long) {
        for (i in 0 until dealtCount[s]) {
            val k = s * RangingGate.DEALT_KEPT + i
            if (dealt[k] == key && dealtLive[k]) {
                dealtLive[k] = false
                dealtBack[k] = arrivals
            }
        }
    }

    private fun deal(s: Int, key: Long) {
        dealtLive[s * RangingGate.DEALT_KEPT + dealtNext[s]] = true
        dealt[s * RangingGate.DEALT_KEPT + dealtNext[s]] = key
        dealtNext[s] = (dealtNext[s] + 1) % RangingGate.DEALT_KEPT
        if (dealtCount[s] < RangingGate.DEALT_KEPT) dealtCount[s]++
    }

    /**
     * **Seat [s]'s steps this second**: stop what it holds that the house no longer keeps, start what
     * the house keeps that it does not hold, keep the rest; in that order, at most
     * [RangingWire.MAX_PEERS], and at most [RangingWire.TOKEN_SLOTS] starts. A phone stops before it
     * starts, so a full phone frees a session first; a start cut off goes out on the next answer.
     */
    private fun planFor(s: Int) {
        stepCount = 0
        for (i in 0 until maxPeers) stepPeer[i] = null
        if (s < 0) return
        // Stops: held handles no pair of this seat keeps, in handle order (unsigned).
        var stops = 0
        for (i in 0 until heldCount[s]) {
            val h = heldHandle[s * maxPeers + i]
            if (!keeps(s, h)) stopScratch[stops++] = h
        }
        for (i in 1 until stops) {
            val moving = stopScratch[i]
            var j = i - 1
            while (j >= 0 && stopScratch[j].toULong() > moving.toULong()) {
                stopScratch[j + 1] = stopScratch[j]
                j--
            }
            stopScratch[j + 1] = moving
        }
        for (i in 0 until stops) add(RangingWire.STEP_STOP, stopScratch[i], 0L, null)
        var starts = 0
        for (q in 0 until pairCount) {
            if (pLow[q] != s && pHigh[q] != s) continue
            if (holdsHandle(s, pHandle[q]) || starts >= tokenSlots) continue
            val low = pLow[q] == s
            add(RangingWire.STEP_START, pHandle[q], if (low) pLowKey[q] else pHighKey[q], if (low) pHighToken[q] else pLowToken[q])
            starts++
        }
        for (q in 0 until pairCount) {
            if (pLow[q] != s && pHigh[q] != s) continue
            if (holdsHandle(s, pHandle[q])) add(RangingWire.STEP_KEEP, pHandle[q], 0L, null)
        }
    }

    private fun keeps(s: Int, handle: Long): Boolean {
        for (q in 0 until pairCount) if (pHandle[q] == handle && (pLow[q] == s || pHigh[q] == s)) return true
        return false
    }

    private fun add(kind: Int, handle: Long, own: Long, peer: RangingToken?) {
        if (stepCount >= maxPeers) return
        stepKind[stepCount] = kind
        stepHandle[stepCount] = handle
        stepOwn[stepCount] = own
        stepPeer[stepCount] = peer
        stepCount++
    }

    // ---- Read out as values, for the bench and the tests (allocates) ------------------------------

    /** Every pair the house keeps, in forming order. */
    fun pairs(): List<RangingPair> = List(pairCount) { q ->
        RangingPair(
            low = seatsInOrder[pLow[q]],
            high = seatsInOrder[pHigh[q]],
            handle = RangingHandle(pHandle[q]),
            lowSpare = RangingSpare(SpareKey(pLowKey[q]), pLowToken[q]!!),
            highSpare = RangingSpare(SpareKey(pHighKey[q]), pHighToken[q]!!),
            ordinal = pOrdinal[q],
            lowHeldAt = Tick(pLowHeld[q] + silent),
            highHeldAt = Tick(pHighHeld[q] + silent),
        )
    }

    /** The plan for the seat that last arrived, as a value. */
    fun planned(): RangingPlan = RangingPlan(
        List(stepCount) { i ->
            val handle = RangingHandle(stepHandle[i])
            when (stepKind[i]) {
                RangingWire.STEP_START -> RangingStep.Start(handle, SpareKey(stepOwn[i]), stepPeer[i]!!)
                RangingWire.STEP_KEEP -> RangingStep.Keep(handle)
                else -> RangingStep.Stop(handle)
            }
        },
    )

    /**
     * **The seat at the other end of the pair [handle] that [seat] is in**, or null when the house
     * keeps no such pair (D-301). Allocates nothing: the seat is the round's own value. A handle a
     * phone lists that names no kept pair is not a pair being ranged, so it can touch nobody.
     */
    fun peerOf(seat: Seat, handle: Long): Seat? {
        val s = slotOf(seat.index)
        if (s < 0) return null
        for (q in 0 until pairCount) {
            if (pHandle[q] != handle) continue
            if (pLow[q] == s) return seatsInOrder[pHigh[q]]
            if (pHigh[q] == s) return seatsInOrder[pLow[q]]
        }
        return null
    }

    /** What [seat] last said it ranges, with the distances, or nothing for a seat not of the round. */
    fun readingsOf(seat: Seat): List<RangingReading> {
        val s = slotOf(seat.index)
        if (s < 0) return emptyList()
        return List(heldCount[s]) { i ->
            val k = s * maxPeers + i
            RangingReading(RangingHandle(heldHandle[k]), if (heldReadings[k] == 0) null else heldCm[k], heldReadings[k])
        }
    }

    /** How many pairs the round has formed. */
    val pairsFormed: Long get() = formed

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val NEVER = Long.MIN_VALUE

        /** [yielding] was not asked: the phone has room, so gives up nothing. */
        const val ROOM = -2

        /** [yielding]: the phone has no pair it may give up. */
        const val NO_PAIR = -1
    }
}

/**
 * **The [formed]th pair's handle in the round of [roundSeed]**: [oneWaySeed] under
 * [SeedPurpose.RangingHandle], as eight bytes. No seat is an input.
 */
internal fun rangingHandleFor(roundSeed: Long, formed: Long): RangingHandle =
    RangingHandle(oneWaySeed(roundSeed, SeedPurpose.RangingHandle, formed))

/**
 * **The ranging desk for the round [state] arms**: built from its seed and its seats. The one place
 * a desk is made from a round. The round's state as it stands reaches the pairing on each upload
 * ([RangingDesk.answer]), for the need (D-306), and nowhere else.
 */
fun rangingDeskFor(state: GameState): RangingDesk = RangingDesk(state.seed, state.seats)

/**
 * **The house's whole ranging path for one round, per upload** (U6, D-300): the upload's Bluetooth
 * slots resolved to seats, its report opened under the seat's seed, the pairing told, and the seat's
 * plan sealed under the same seed. Built at arming; one per house, called under its lock.
 *
 * It holds everything it reuses (rule 7): the [RangingPairing], one [RangingCodec], each seat's seed
 * (derived once here), and the contact book's tokens keyed by their text, rebuilt only when the
 * book is (once a window), so resolving a slot allocates nothing. On a steady second the one thing
 * an upload costs is its plan's string. `RangingAllocationTest` measures it.
 */
class RangingDesk(roundSeed: Long, seats: List<Seat>) {
    val pairing = RangingPairing(roundSeed, seats)
    private val codec = RangingCodec()
    private val seeds = arrayOfNulls<ContactSeed>((seats.maxOfOrNull { it.index } ?: -1) + 1).also { seeds ->
        for (seat in seats) seeds[seat.index] = contactSeedFor(roundSeed, seat)
    }
    private var book: ContactBook? = null
    private val tokenSeats = HashMap<String, Int>()

    /** The seat whose report [codec] holds open, from [read] until [answer]; null when none does. */
    private var opened: Seat? = null

    /** The game's need (D-306), pointed at the round as it stands on each [answer]. */
    private val need = RoundRangingNeed()

    /** [read]'s answer, held and refilled (rule 7): nothing keeps it past the upload it was read for. */
    private val distances = ArrayList<ContactDistance>(RangingWire.MAX_PEERS)

    /**
     * **The handles that have read a distance this round, the latest [FIRST_READS_KEPT]**, a ring
     * written in place (rule 7): so [read] can say a pair just read its first distance, for the
     * house's log (D-308: how long ranging takes to start is the phones' to measure).
     */
    private val readHandles = LongArray(FIRST_READS_KEPT)
    private var readCount = 0

    /** **How many pairs [read] saw read their first distance**, and the nearest of those, in cm. */
    var firstReads: Int = 0
        private set
    var firstCm: Int = 0
        private set

    /** The plan [answer] seals: the pairing's steps for the seat that just arrived, and its report slot. */
    private val sealing = object : RangingPlanSource {
        var kind = RangingWire.REPORT_IDLE
        var millis = 0
        override val stepCount: Int get() = pairing.plan.stepCount
        override fun stepKind(i: Int): Int = pairing.plan.stepKind(i)
        override fun stepHandle(i: Int): Long = pairing.plan.stepHandle(i)
        override fun stepOwn(i: Int): Long = pairing.plan.stepOwn(i)
        override fun stepPeer(i: Int): RangingToken? = pairing.plan.stepPeer(i)
        override val reportKind: Int get() = kind
        override val reportMillis: Int get() = millis
    }

    /**
     * **[seat]'s ranging report, opened, and its distances resolved to seats** (D-301): one
     * [ContactDistance] for every pair it lists with a reading this second whose handle names a pair
     * the house keeps with [seat] in it, in the order the phone listed them. Nothing for a field that
     * does not read, a seat with no seed, or a handle no kept pair has. **Read it before the next
     * call**: the list is held and refilled. Call [answer] for the same seat next.
     */
    fun read(seat: Seat, field: String): List<ContactDistance> {
        distances.clear()
        opened = null
        firstReads = 0
        val seed = seeds.getOrNull(seat.index) ?: return distances
        if (!codec.openReport(seed, field)) return distances
        opened = seat
        for (i in 0 until codec.readingCount) {
            if (codec.readingReadings(i) == 0) continue
            val handle = codec.readingHandle(i)
            val peer = pairing.peerOf(seat, handle) ?: continue
            distances += ContactDistance(peer, codec.readingCm(i))
            if (!hasRead(handle)) {
                readHandles[readCount % FIRST_READS_KEPT] = handle
                readCount++
                firstCm = if (firstReads == 0) codec.readingCm(i) else minOf(firstCm, codec.readingCm(i))
                firstReads++
            }
        }
        return distances
    }

    private fun hasRead(handle: Long): Boolean {
        for (i in 0 until minOf(readCount, FIRST_READS_KEPT)) if (readHandles[i] == handle) return true
        return false
    }

    /**
     * **[seat]'s upload, through the pairing, and its plan sealed with [nonce]**: [sightings] are
     * every Bluetooth slot it carried, resolved through [book] (the contact resolver's, for this
     * window); the report is the one [read] just opened for [seat], if it read; [report] is the
     * seat's report slot from the resolver (D-301), sealed into the plan. [state] is the round as it
     * stands after the rules took this upload, and the pairing asks it which close pairs the game
     * needs ([rangingNeeded], D-306). [onMainView] is the upload's main view field (D-307), kept as
     * the seat's last word so the pairing's report need asks [holdMayRun] as the hold does; on by
     * default for a caller with no field. Null for a seat that is not the round's, which has no seed
     * here.
     */
    fun answer(state: GameState, at: Tick, seat: Seat, sightings: List<Sighting>, book: ContactBook?, taken: Boolean, report: ReportSlot, nonce: ByteArray, onMainView: Boolean = true): String? {
        val seed = seeds.getOrNull(seat.index) ?: return null
        if (book !== this.book) {
            this.book = book
            tokenSeats.clear()
            book?.seatOf?.forEach { (token, owner) -> tokenSeats[token.hex] = owner.index }
        }
        for (i in sightings.indices) {
            val s = sightings[i]
            val owner = tokenSeats[s.token.hex] ?: continue
            pairing.hear(owner, s.rssi)
        }
        val view: RangingReportView? = if (opened?.index == seat.index) codec else null
        opened = null
        need.state = state
        need.sawMainView(seat, onMainView)
        pairing.arrive(at, seat, view, taken, need)
        sealing.kind = report.kind
        sealing.millis = report.millis
        return codec.packPlan(seed, nonce, sealing)
    }

    /**
     * **[beat], sealed under [seat]'s own seed with [nonce]**, for the upload's answer (D-316): one
     * width whatever it holds. Null for a seat that is not the round's, which has no seed here. On
     * the desk's codec and its kept keys, so a beat costs its string (rule 7); never between a
     * [read] and its [answer], which the authority's one lock already orders.
     */
    fun sealMap(seat: Seat, beat: OccupancyBeat, nonce: ByteArray): String? {
        val seed = seeds.getOrNull(seat.index) ?: return null
        return codec.packMap(seed, nonce, beat)
    }

    private companion object {
        /** The handles [read] remembers having read: far more than a round's live pairs. */
        const val FIRST_READS_KEPT = 64
    }

    /** **[read] then [answer], with an idle report slot**: the whole path for a caller with no resolver between (D-300's). */
    fun answer(state: GameState, at: Tick, seat: Seat, sightings: List<Sighting>, book: ContactBook?, field: String, taken: Boolean, nonce: ByteArray): String? {
        read(seat, field)
        return answer(state, at, seat, sightings, book, taken, ReportSlot.Idle, nonce)
    }
}
