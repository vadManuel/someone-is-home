package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.ReportSlot

/*
 * **The house's contact resolver: what the radios heard, turned into contacts** (D-213, D-212).
 *
 * Above the rules, beside `routeScan` and `armingFor`, and for their reason: something has to
 * decide what kind of fact an arrival is, and that decision needs knowledge `reduce` has never
 * held. Here it is the token book, every seat's seed run forward to the current window. Nothing
 * here reads a clock: the tick and the window are arguments, and which clock they are cut from
 * is the wiring's (unit 3).
 *
 * ### What it mints, and what it never asks
 *
 * The contact primitive is one gesture with two outcomes, *decided by the state of the two
 * phones* (`gdd.md`, the contact primitive; D-121 corrected). This file mints the gesture and
 * leaves the outcome to the rules:
 *
 * - **A mutual touch mints [Event.ContactMade] in both directions**, for every pair, whoever they
 *   are: both phones read each other at [ContactGate.REVOKE_REACH_CM] or nearer within
 *   [ContactGate.MUTUAL_TICKS]. Whether either side holds an armed Revoke is `Rules.contact`'s
 *   question, and the emit schema decides who hears the answer. **No role and no armed state is
 *   read here**, and the guarantee is [ContactHouse]'s field list: it carries the round's seed, its
 *   seats, who is out, who is Revoked and who was reported, and nothing else.
 * - **A living phone holding a Revoked seat mints the report**, `MeetingCalled` with
 *   [MeetingTrigger.RevokeReported]: within [ContactGate.REPORT_REACH_CM] for
 *   [ContactGate.REPORT_HOLD_TICKS], read from the reader's side alone, with a pending hold the
 *   house tells that phone about every second ([ReportSlot]) so its circle can fill. Who is Revoked
 *   is the one piece of state read, because the report *is* that fact meeting a phone.
 *
 * ### Distance, in every round (D-308)
 *
 * Both questions read the nearest UWB distance each phone measured of each peer it is ranging
 * ([ContactUpload.distances]), and nothing else: **no Bluetooth reading decides a touch or a
 * report** (the owner's 2026-11-20 ruling: every round is the hybrid). Bluetooth only ranks who is
 * near whom, for the house's UWB pairing (`Ranging.kt`, U6, D-306), which is why the token book is
 * still built here and kept in [ContactMemory.book]. A phone that is out keeps ranging (D-300), and
 * the house reads none of its distances (D-008's *a phone that is out does not scan*, kept). A
 * phone that cannot range (capacity 0) is paired with nobody, so it reads no distance and nobody
 * reads one of it: it can neither touch nor be touched, report nor be reported.
 *
 * Where several apply at once, every one is minted, in seat order ([inSeatOrder]), and the rules
 * decide. A huddle of three is six contacts; a pair that also hears a Revoked seat is two contacts
 * and two reports, and the admission gate refuses the second meeting as it refuses any.
 *
 * ### Why there is no early return here and why that is not rule 1 bending
 *
 * Nothing this file declines is a client-visible path: an arrival that resolves to nothing is the
 * physical case *anything else → nothing at all* (`gdd.md`), and nothing is constructed for it at
 * any level. What rule 1 forbids is a branch on *success*, and every branch below is on what the
 * radios heard, never on what the contact would do.
 */

/**
 * **One phone's upload, as the house took it off the wire**: the seat its socket belongs to and
 * every slot it carried, filler included.
 *
 * Filler is passed in rather than stripped by the caller because it is what makes two uploads
 * from one phone distinct without a sequence number (`SightingUpload`), and [resolveContacts]
 * recognises a duplicate by the whole slot list. It resolves to no seat, so it mints nothing.
 */
data class ContactUpload(
    val seat: Seat,
    val sightings: List<Sighting>,
    /**
     * **The nearest UWB distance this phone read this second of each peer it is ranging** (D-301),
     * resolved by the house's pairing from the pair's handle to the seat at its other end. Only a
     * pair the house keeps resolves, so only a pair being ranged can touch or report (U6).
     */
    val distances: List<ContactDistance> = emptyList(),
    /**
     * **Whether the phone said it is on the main view** (D-307): on springboard page 1 or 2 (a
     * banner over the page included) or the report screen, in no Subroutine, scan, briefing, Egress
     * widget, meeting or lantern.
     * Read off the upload's one-character field (`MainViewField`), and read only by the UWB report's
     * hold ([heldReports]): a reader off the main view starts no hold, and one running is cancelled.
     * False by default, so an upload built without saying starts nothing (it fails closed).
     */
    val onMainView: Boolean = false,
)

/** **[target] read at [cm] centimetres**, the nearest of a second's readings (D-301). A touch can read at or below zero. */
data class ContactDistance(val target: Seat, val cm: Int)

/**
 * What a resolution produced: the memory to carry to the next one, and the events to admit.
 *
 * [taken] is every seat whose upload this arrival took as new, in seat order: not a second
 * delivery and from a seat of the round. The spectator map senses from those uploads alone (O4),
 * so a transport's repeat is one capture and not two, and its error is rolled once.
 */
data class ContactResolution(
    val memory: ContactMemory,
    val events: List<Event>,
    val taken: List<Seat> = emptyList(),
    /**
     * **Each uploading seat's report slot for its answer** (D-301), one per upload of a seat of the
     * round, in seat order: whether its phone is holding a report, and for how long. The house seals
     * it into the plan it answers every upload with, so every phone is sent one, at one width.
     */
    val slots: List<ContactSlot> = emptyList(),
) {
    /** [seat]'s slot from this arrival, or idle for a seat that did not upload in it. */
    fun slotOf(seat: Seat): ReportSlot = slots.firstOrNull { it.seat.index == seat.index }?.slot ?: ReportSlot.Idle
}

/** [seat]'s report slot for this answer (D-301). */
data class ContactSlot(val seat: Seat, val slot: ReportSlot)

/**
 * **[reader] has held [target], a Revoked seat, within report reach since [since]** (D-301), and
 * last read it on the upload taken at [seen]. The pending half of a UWB report: the house's, carried
 * between arrivals, and told to the reader's phone as [ReportSlot.Pending].
 */
data class ReportHeld(val reader: Seat, val target: Seat, val since: Tick, val seen: Tick)

/**
 * **What the resolver remembers between arrivals**, as values, so the resolver is a pure function
 * of `(memory, arrival)` and a replay that feeds the same arrivals rebuilds the same memory.
 *
 * Every list is built in an order fixed by the arrivals alone (seat order within an arrival), so two
 * memories reached by the same arrivals are equal.
 */
data class ContactMemory(
    /**
     * Every peer a living phone read at touch reach, one per (observer, target) per upload, younger
     * than [ContactGate.MUTUAL_TICKS]: the halves a touch is made of.
     */
    val heard: List<ContactHeard>,
    /** The last contact or report minted per unordered pair, younger than [ContactGate.PAIR_GRACE_TICKS]. */
    val minted: List<ContactMinted>,
    /** Uploads already taken, younger than [ContactGate.DUPLICATE_TICKS], so a second delivery is recognised. */
    val received: List<ContactReceived>,
    /**
     * The token book for the window last resolved in, or null before the first arrival. Read by the
     * house's UWB pairing, which ranks peers by Bluetooth (D-300), and by no rule (D-308).
     */
    val book: ContactBook?,
    /** **Every report being held, by its reader, in seat order** (D-301). */
    val held: List<ReportHeld> = emptyList(),
) {
    companion object {
        val EMPTY = ContactMemory(emptyList(), emptyList(), emptyList(), null)
    }
}

/** [observer]'s phone read [target] at touch reach, in the upload that arrived at [at]. */
data class ContactHeard(val observer: Seat, val target: Seat, val at: Tick)

/** A contact between [low] and [high] (low < high) was minted at [at]. */
data class ContactMinted(val low: Seat, val high: Seat, val at: Tick)

/** [seat]'s upload of exactly [sightings] was taken at [at]. */
data class ContactReceived(val seat: Seat, val at: Tick, val sightings: List<Sighting>)

/**
 * **Every token the round's seats advertise in [window], the window before it and the window after
 * it, and whose each is** (D-236). [ContactTokens.seatOf]'s inversion, computed once per window
 * rather than once per slot.
 *
 * `seatOf` answers one token by deriving every seat's token, which is two HMACs per seat per slot:
 * 24 slots × 18 seats × 3 windows per upload, from every phone once a second, on a host that is a
 * phone with a whole-app allocation budget (rule 7). The book derives each seat's three tokens once
 * per fifteen seconds and answers by lookup; between window edges it is reused as it stands and
 * allocates nothing. Built by [ContactTokens.of], the function `seatOf` inverts, in seat order with
 * the current window first, then the one behind, then the one ahead, and the first match kept, so it
 * answers exactly what `seatOf` answers asked in that order; `ContactResolverTest` pins the two
 * together.
 *
 * The map is read by key and never iterated, so its order decides nothing (rule 4).
 */
data class ContactBook(
    val roundSeed: Long,
    val seats: List<Seat>,
    val window: ContactWindow,
    val seatOf: Map<ContactToken, Seat>,
) {
    internal fun fits(house: ContactHouse, at: ContactWindow): Boolean =
        roundSeed == house.roundSeed && window == at && seats == house.seats

    internal companion object {
        fun of(house: ContactHouse, window: ContactWindow): ContactBook {
            val seeds = house.seats.map { it to contactSeedFor(house.roundSeed, it) }
            val book = LinkedHashMap<ContactToken, Seat>()
            for (offset in ContactGate.windowOffsets) {
                val w = ContactWindow(window.index + offset)
                for ((seat, seed) in seeds) {
                    val token = ContactTokens.of(seed, w)
                    if (token !in book) book[token] = seat
                }
            }
            return ContactBook(house.roundSeed, house.seats, window, book)
        }
    }
}

/**
 * **The tolerances, named, in one place** (D-213).
 *
 * The mutual window is a placeholder and says so; the reaches are the owner's rulings (U1, Q5). A
 * number here is a gameplay rule wearing a constant's clothes: a reach decides whether a Revoke
 * lands at all, and a mis-set one fails silently in both directions (architecture risk 2).
 */
object ContactGate {

    /**
     * **Windows behind the house's that a token may come from: one.** A sighting made just before
     * an edge arrives just after it, so the previous window's tokens are honest for a whole window
     * longer, and an advertiser whose clock runs behind the house's is still in it. Anything older is
     * stale and resolves to no seat.
     */
    const val WINDOWS_BACK: Long = 1L

    /**
     * **Windows ahead of the house's that a token may come from: one** (D-236; D-213 refused it). An
     * advertiser whose clock runs ahead of the host's moves to the next window's token that much
     * before the house does, at every edge; refused, every touch inside that lead was lost, four
     * times a minute. Two ahead is refused: no honest clock is a whole window fast, and a token from
     * further ahead than the next window can only come from a seed, never from the air. The bound is
     * the same on both sides, so a token resolves for three windows and then never again.
     */
    const val WINDOWS_AHEAD: Long = 1L

    /** The house's window first, then behind it, then ahead: the order [ContactBook] keeps first matches in. */
    internal val windowOffsets: List<Long> =
        listOf(0L) + (1L..WINDOWS_BACK).map { -it } + (1L..WINDOWS_AHEAD).toList()

    /**
     * ⚠️ **PLACEHOLDER: two seconds.** The two halves of a mutual sighting must arrive within this
     * of each other. Measured on arrival ticks, so it holds one upload interval on each side
     * (`SightingUpload.CADENCE_MILLIS`, itself provisional) plus delivery jitter. Unit 7's
     * contact-to-dark measurement is what sets it, with the cadence.
     */
    const val MUTUAL_TICKS: Long = 2L * Balance.TICKS_PER_SECOND

    /**
     * **The pair's grace: five seconds after two phones mint anything, they mint nothing more
     * between them** (D-212's scoping, ruling 4; named a rule by the owner's 2026-11-18 ruling,
     * option a, D-291).
     *
     * It began as a debounce, one contact per pair per five seconds, so that one held touch is one
     * touch. It is also, and deliberately, **the grace after a Revoke**: for five seconds after the
     * touch that Revokes a phone, neither phone of that pair can report the other. Without it the
     * Insider's own phone, still within reach of the player it just Revoked, would report them on its
     * next two uploads and ring the house at the very touch. **After the grace anyone can report,
     * the Insider who did the Revoke included**: the owner ruled that the Insider may report the
     * player they Revoked, and that the reporter's own circle warns them first.
     *
     * It is a fact about the pair and never about who is in it: no role, no arm, no Revoke is read
     * ([ContactHouse] carries none). **A third phone is not in the pair**, so a neighbour already
     * within reach of the Revoked player can report at once; the owner accepted that under U3.
     *
     * Ruled at five seconds; the report's own distance and hold move with UWB, not this.
     */
    const val PAIR_GRACE_TICKS: Long = 5L * Balance.TICKS_PER_SECOND

    /**
     * **One upload interval, in ticks: one second**, the same value as `SightingUpload.CADENCE_MILLIS`
     * (provisional there, and `ContactResolverTest` pins the two together). Held here so the report's
     * slack and gap below are counted in uploads and move with the cadence.
     */
    const val UPLOAD_TICKS: Long = 1L * Balance.TICKS_PER_SECOND

    /**
     * **How long an upload is remembered to recognise a second delivery: as long as a token in it
     * can resolve**, the window behind the house's, the house's, and the one ahead (D-236, was two
     * windows). Derived, not tuned: after that, every token in it is stale and it can resolve
     * nothing anyway.
     */
    const val DUPLICATE_TICKS: Long =
        (WINDOWS_BACK + 1 + WINDOWS_AHEAD) * ContactWindow.MILLIS * Balance.TICKS_PER_SECOND / 1_000L

    // ---- UWB distance (D-301, D-308) -----------------------------------------------------------

    /**
     * **A touch, by UWB: 10 cm** (the owner's U1; the U6 plan, unit 3). Each phone of a pair must
     * read the other at or nearer than this, within [MUTUAL_TICKS] of each other: one modified
     * phone cannot claim a touch alone, and both phones read the same two-way exchange, so a real
     * flyby shows on both. The
     * phone keeps the nearest reading of each second (D-300), so a pass inside 10 cm caught by one
     * or two readings at 150 ms is not averaged away.
     */
    const val REVOKE_REACH_CM: Int = 10

    /**
     * **A report, by UWB: 30 cm, from the reader's side alone** (the owner's ruling on Q5,
     * 2026-11-18). A living phone reading a Revoked seat this near starts a hold; the house reads
     * only the reader's measurement of the pair, never the Revoked phone's (D-300).
     */
    const val REPORT_REACH_CM: Int = 30

    /**
     * **...held for two seconds** (Q5). The reader's phone fills a circle over them, told by the
     * house in every answer ([ReportSlot.Pending]); still in reach on an upload two seconds after
     * the hold began, the meeting is called; out of reach first, the hold is cancelled. The two
     * seconds are [Balance.REPORT_HOLD_MILLIS], the same number the circle fills over (D-307).
     */
    const val REPORT_HOLD_TICKS: Long = Balance.REPORT_HOLD_MILLIS * Balance.TICKS_PER_SECOND / 1_000L

    /**
     * ⚠️ **Provisional (overnight): half an upload of slack on the hold** (D-301). The hold is judged
     * on arrival ticks, once a second: the upload two intervals after the first arrives two seconds
     * later give or take the Wi-Fi's jitter, and a strict two seconds would push a third of honest
     * holds a whole second longer. Half an interval is D-236's allowance for the same jitter.
     */
    const val REPORT_HOLD_SLACK_TICKS: Long = UPLOAD_TICKS / 2

    /**
     * ⚠️ **Provisional (overnight): a hold breaks when its reader misses an upload** (D-301). Its
     * next upload more than an interval and a half after the last one that read the Revoked seat
     * starts the hold again: the house cannot say the phone stayed in reach across a second it did
     * not hear about.
     */
    const val REPORT_GAP_TICKS: Long = UPLOAD_TICKS + UPLOAD_TICKS / 2

    /** Whether [cm] is a touch's distance. */
    fun atRevokeReach(cm: Int): Boolean = cm <= REVOKE_REACH_CM

    /** Whether [cm] is a report's distance. */
    fun atReportReach(cm: Int): Boolean = cm <= REPORT_REACH_CM
}

/**
 * **The facts the distance rules ask of the round**, and nothing else: no role, no armed state.
 * [ContactHouse] answers them for the resolver; the house's UWB pairing answers them from the round
 * as it stands (D-306), so that the pairing asks the resolver's own questions ([readsDistancesOf],
 * [holdMayRun], [holdMayTarget]) and never a copy of them.
 */
internal interface ContactFacts {
    /** Whether a meeting is running: no hold starts or runs at one, where everybody stands together. */
    val inMeeting: Boolean

    fun isRevoked(seat: Seat): Boolean

    /** Out is Revoked or Restrained: its phone ranges, and the house reads none of it (D-301). */
    fun isOut(seat: Seat): Boolean

    /** Revoked seats a meeting was already called to report (S4, D-219): no hold starts on one. */
    fun wasReported(seat: Seat): Boolean

    /**
     * **Whether [seat]'s phone said it is on the main view** (D-307): springboard page 1 or 2 (a
     * banner over the page included) or the report screen, in its upload's one-character field. The
     * resolver answers it from this arrival's uploads; the pairing from each seat's last upload.
     */
    fun onMainView(seat: Seat): Boolean
}

/**
 * **Whether the house reads [seat]'s distances at all** (D-301): only a living phone's. A phone that
 * is out keeps ranging so that it stays measurable (D-300); what it measures is not read, as an out
 * phone's Bluetooth never was (D-008). So a touch by distance needs both phones living.
 */
internal fun ContactFacts.readsDistancesOf(seat: Seat): Boolean = !isOut(seat)

/**
 * **Whether [reader] may hold a report at all on this upload** (D-301): a living reader (R3), no
 * meeting running (D-301 decision 5), and the reader's phone on the main view
 * ([reportsFromWhereItIs], D-307). The pairing asks this same question (D-306), so a report pair is
 * ranged only while the hold could run.
 */
internal fun ContactFacts.holdMayRun(reader: Seat): Boolean =
    readsDistancesOf(reader) && !inMeeting && reportsFromWhereItIs(reader)

/**
 * **Whether [reader] may report from where its phone is** (D-307, the owner's 2026-11-20 ruling:
 * *in order to report a player you must not be in a minigame; you must just be in the main view,
 * that being first or second page*). The phone says it in every upload ([ContactUpload.onMainView]);
 * off the main view, [holdMayRun] is false, so nothing in reach is read, no hold begins, and a hold
 * already running ends [ReportSlot.Cancelled] on that very upload, as leaving reach ends one.
 *
 * Asked of the reader alone: no role, no arm, never the Revoked phone. The house's own facts already
 * cover the meeting ([ContactFacts.inMeeting]); this covers what only the phone knows, the screen
 * it is on. One named question, so a ruling that moves the line moves this.
 */
internal fun ContactFacts.reportsFromWhereItIs(reader: Seat): Boolean = onMainView(reader)

/**
 * **Whether [target] is a seat [reader] could hold a report on**, distance and grace aside: another
 * seat, Revoked, and not already reported (S4).
 */
internal fun ContactFacts.holdMayTarget(reader: Seat, target: Seat): Boolean =
    target.index != reader.index && isRevoked(target) && !wasReported(target)

/**
 * **The [ContactFacts] of the round as it stands**, for the house's UWB pairing (D-306): every
 * answer read straight off [state], the same lists [ContactHouse.of] copies. Held and re-pointed per
 * upload (rule 7): it allocates nothing.
 */
internal class RoundContactFacts(var state: GameState) : ContactFacts {
    override val inMeeting: Boolean get() = state.meeting != null
    override fun isRevoked(seat: Seat): Boolean = state.isRevoked(seat)
    override fun isOut(seat: Seat): Boolean = state.isOut(seat)
    override fun wasReported(seat: Seat): Boolean = state.wasReported(seat)

    /**
     * **Each seat's last word on the main view** (D-307), by index: true where its last upload said
     * it was off. A seat not yet heard from is taken as on, so the pairing never holds back a pair the
     * hold might need: the hold itself reads every upload's own field ([ContactUpload.onMainView]) and
     * fails closed there. Grown only when a seat index is first seen past its end.
     */
    private var off = BooleanArray(0)

    /** [seat]'s upload just said whether it is on the main view. */
    fun sawMainView(seat: Seat, on: Boolean) {
        if (seat.index < 0) return
        if (seat.index >= off.size) off = off.copyOf(seat.index + 1)
        off[seat.index] = !on
    }

    override fun onMainView(seat: Seat): Boolean = !(seat.index in off.indices && off[seat.index])
}

/**
 * **The whole of what the resolver may know about the round**, and the field list is the
 * guarantee: no role, no armed state, no cooldown. A role swap or an arm cannot reach a function
 * that only ever sees this (D-213).
 */
internal class ContactHouse private constructor(
    val roundSeed: Long,
    val seats: List<Seat>,
    val revoked: List<Seat>,
    val out: List<Seat>,
    val reported: List<Seat>,
    override val inMeeting: Boolean,
    /** This arrival's uploads, read for the main view alone (D-307). */
    private val uploads: List<ContactUpload>,
) : ContactFacts {
    fun holds(seat: Seat): Boolean = seats.any { it.index == seat.index }

    /** Whether an upload of [seat]'s in this arrival said the main view (D-307). Allocates nothing. */
    override fun onMainView(seat: Seat): Boolean {
        for (i in uploads.indices) {
            val upload = uploads[i]
            if (upload.seat.index == seat.index && upload.onMainView) return true
        }
        return false
    }
    override fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }
    override fun isOut(seat: Seat): Boolean = out.any { it.index == seat.index }
    override fun wasReported(seat: Seat): Boolean = reported.any { it.index == seat.index }

    companion object {
        fun of(state: GameState, uploads: List<ContactUpload> = emptyList()) = ContactHouse(
            roundSeed = state.seed,
            seats = state.seats.sortedBy { it.index },
            revoked = state.revoked.sortedBy { it.index },
            out = (state.revoked + state.restrained).distinctBy { it.index }.sortedBy { it.index },
            reported = state.reported.sortedBy { it.index },
            inMeeting = state.meeting != null,
            uploads = uploads,
        )
    }
}

/**
 * **One arrival, resolved: [uploads] taken off the wire at [at], in the house's [window].**
 *
 * [uploads] may hold several phones' uploads that arrived on one tick; the result does not depend
 * on their order in the list. In five steps:
 *
 * 1. **A second delivery of an upload is ignored.** Same seat, same slots, filler included, within
 *    [ContactGate.DUPLICATE_TICKS]. Filler is keyed off the upload's own counter, so two honest
 *    uploads never match; a repeat is the transport's, and it must not refresh a reading's time.
 *    An upload from a seat not in the round is ignored too: nothing it read can be anybody's.
 * 2. **Each distance is gated** ([ContactGate.atRevokeReach]): a peer of this round, other than the
 *    uploader, read at [ContactGate.REVOKE_REACH_CM] or nearer. An out phone's distances are not
 *    read at all (D-301). The Bluetooth slots decide nothing here (D-308).
 * 3. **The heard list gains** one entry per seat each upload read at touch reach, and forgets what
 *    is older than [ContactGate.MUTUAL_TICKS].
 * 4. **Each pair read this arrival is judged:** a contact if each side has read the other within
 *    [ContactGate.MUTUAL_TICKS].
 * 5. **A pair inside its grace mints nothing** ([inPairGrace], [ContactGate.PAIR_GRACE_TICKS],
 *    D-291); otherwise the contact is minted at [at] and the pair is stamped once.
 *
 * Then the report, [heldReports]: one living reader holding a Revoked seat within
 * [ContactGate.REPORT_REACH_CM] for [ContactGate.REPORT_HOLD_TICKS], told to the reader's phone in
 * every answer as its [ContactResolution.slots]. The grace of step 5 covers it.
 */
fun resolveContacts(
    memory: ContactMemory,
    at: Tick,
    window: ContactWindow,
    uploads: List<ContactUpload>,
    state: GameState,
): ContactResolution {
    val house = ContactHouse.of(state, uploads)
    val book = memory.book?.takeIf { it.fits(house, window) } ?: ContactBook.of(house, window)

    // One list built per arrival, filtered into and then appended to, rather than a filter and a
    // copy per upload: it holds every upload of the last three windows, so on an eighteen-seat host
    // it is the resolver's largest allocation (rule 7, D-236).
    val received = ArrayList<ContactReceived>(memory.received.size + uploads.size)
    memory.received.filterTo(received) { at.step - it.at.step <= ContactGate.DUPLICATE_TICKS }
    val fresh = mutableListOf<ContactHeard>()
    val taken = ArrayList<Seat>(uploads.size)
    for (upload in uploads.sortedWith(compareBy<ContactUpload> { it.seat.index }.thenBy { slotsKey(it.sightings) })) {
        val repeat = received.any { it.seat.index == upload.seat.index && it.sightings == upload.sightings }
        if (repeat || !house.holds(upload.seat)) continue
        received += ContactReceived(upload.seat, at, upload.sightings)
        taken += upload.seat
        // **Distance, from a living phone only** (D-301). A phone that is out keeps ranging so that
        // it stays measurable (D-300); what it measures is not read, as an out phone's Bluetooth
        // never was (D-008).
        if (house.readsDistancesOf(upload.seat)) {
            upload.distances
                .filter { ContactGate.atRevokeReach(it.cm) && house.holds(it.target) && it.target.index != upload.seat.index }
                .distinctBy { it.target.index }
                .sortedBy { it.target.index }
                .forEach { fresh += ContactHeard(upload.seat, it.target, at) }
        }
    }

    val heard = (memory.heard + fresh)
        .filter { at.step - it.at.step <= ContactGate.MUTUAL_TICKS }
        .sortedWith(compareBy<ContactHeard> { it.observer.index }.thenBy { it.target.index }.thenBy { it.at.step })

    /** How many of [observer]'s uploads heard [target] within [ticks] of this arrival. */
    fun heardBy(observer: Seat, target: Seat, ticks: Long): Int = heard.count {
        it.observer.index == observer.index && it.target.index == target.index && at.step - it.at.step <= ticks
    }

    var minted = memory.minted.filter { inPairGrace(it, at) }
    val events = mutableListOf<Event>()
    val pairs = fresh
        .map { if (it.observer.index < it.target.index) it.observer to it.target else it.target to it.observer }
        .distinctBy { (low, high) -> low.index to high.index }
        .sortedWith(compareBy<Pair<Seat, Seat>> { it.first.index }.thenBy { it.second.index })
    for ((low, high) in pairs) {
        val applies = buildList {
            // Mutual is both halves within the window. Every pair judged here has a half from this
            // arrival, so the window is measured back from now.
            if (heardBy(low, high, ContactGate.MUTUAL_TICKS) > 0 && heardBy(high, low, ContactGate.MUTUAL_TICKS) > 0) {
                add(Event.ContactMade(at, low, high))
                add(Event.ContactMade(at, high, low))
            }
        }
        val graced = minted.any { it.low.index == low.index && it.high.index == high.index }
        if (applies.isEmpty() || graced) continue
        events += applies
        minted = minted + ContactMinted(low, high, at)
    }

    // **The report's hold** (D-301): after the contacts, so a touch minted on this arrival puts
    // its pair in the grace before any hold between the two is judged.
    val holds = heldReports(memory.held, at, uploads, taken, house, minted)
    events += holds.events
    minted = minted + holds.minted

    return ContactResolution(
        memory = ContactMemory(
            heard = heard,
            minted = minted.sortedWith(compareBy<ContactMinted> { it.low.index }.thenBy { it.high.index }),
            received = received,
            book = book,
            held = holds.held,
        ),
        events = inSeatOrder(events),
        taken = taken,
        slots = holds.slots,
    )
}

/** What [heldReports] decided on one arrival. */
private class Holds(
    val held: List<ReportHeld>,
    val events: List<Event>,
    val minted: List<ContactMinted>,
    val slots: List<ContactSlot>,
)

/**
 * **The UWB report, held** (D-301; the owner's ruling on Q5: *30 cm held 2 s, with a filling
 * circle*). One reader at a time, in seat order, for every upload this arrival [taken]:
 *
 * 1. **What it reads in reach**: every Revoked seat its upload measured at
 *    [ContactGate.REPORT_REACH_CM] or nearer, not already reported (S4), its pair with the reader
 *    not inside its grace ([inPairGrace], D-291), nearest first, then by seat. Nothing when the
 *    reader is out (R3: only a living phone reports), when a meeting is running, or when the
 *    reader's phone is off the main view (D-307): [holdMayRun].
 * 2. **A hold already running on a seat still in reach**: two seconds since it began
 *    ([ContactGate.REPORT_HOLD_TICKS], less [ContactGate.REPORT_HOLD_SLACK_TICKS]), the report is
 *    minted, `MeetingCalled` with [MeetingTrigger.RevokeReported], and the
 *    pair is stamped; sooner, the slot says [ReportSlot.Pending] with the time held.
 * 3. **A hold whose seat left reach**, or whose reader missed an upload
 *    ([ContactGate.REPORT_GAP_TICKS]): it ends, and the slot says [ReportSlot.Cancelled], unless
 *    another seat is in reach, on which a new hold begins.
 * 4. **No hold, and a seat in reach**: one begins, [ReportSlot.Pending] at nothing held.
 *
 * **The reader's side alone.** The Revoked phone ranges too (D-300) and its measurement of the same
 * pair is never read here: what calls the house is a living phone held at a Revoked one. A role is
 * never read: the question is the same for every reader, and [ContactHouse] carries none.
 *
 * An upload the resolver did not take (a second delivery) changes nothing and is answered with the
 * hold as it stands.
 */
private fun heldReports(
    before: List<ReportHeld>,
    at: Tick,
    uploads: List<ContactUpload>,
    taken: List<Seat>,
    house: ContactHouse,
    graced: List<ContactMinted>,
): Holds {
    // A hold whose reader has gone quiet is over, whoever uploads next (rule 4: by arrival ticks alone).
    val held = before.filterTo(ArrayList(before.size + 1)) { at.step - it.seen.step <= ContactGate.REPORT_GAP_TICKS }
    val events = ArrayList<Event>(0)
    val minted = ArrayList<ContactMinted>(0)
    val slots = ArrayList<ContactSlot>(uploads.size)

    fun inGrace(a: Seat, b: Seat): Boolean {
        val low = minOf(a.index, b.index)
        val high = maxOf(a.index, b.index)
        return (graced + minted).any { it.low.index == low && it.high.index == high && inPairGrace(it, at) }
    }

    for (upload in uploads.sortedBy { it.seat.index }.distinctBy { it.seat.index }) {
        val reader = upload.seat
        if (!house.holds(reader)) continue
        val running = held.firstOrNull { it.reader.index == reader.index }
        if (taken.none { it.index == reader.index }) {
            slots += ContactSlot(reader, running?.let { pending(at.step - it.since.step) } ?: ReportSlot.Idle)
            continue
        }
        val reads = house.holdMayRun(reader)
        val inReach = if (!reads) emptyList() else upload.distances
            .filter { ContactGate.atReportReach(it.cm) && house.holdMayTarget(reader, it.target) && !inGrace(reader, it.target) }
            .sortedWith(compareBy<ContactDistance> { it.cm }.thenBy { it.target.index })
        val still = running?.takeIf { r -> inReach.any { it.target.index == r.target.index } }
        val slot: ReportSlot = when {
            still != null && at.step - still.since.step >= ContactGate.REPORT_HOLD_TICKS - ContactGate.REPORT_HOLD_SLACK_TICKS -> {
                held.remove(still)
                events += Event.MeetingCalled(at, reader, MeetingTrigger.RevokeReported(still.target))
                minted += ContactMinted(Seat(minOf(reader.index, still.target.index)), Seat(maxOf(reader.index, still.target.index)), at)
                ReportSlot.Idle
            }
            still != null -> {
                held[held.indexOf(still)] = still.copy(seen = at)
                pending(at.step - still.since.step)
            }
            else -> {
                if (running != null) held.remove(running)
                val next = inReach.firstOrNull()
                when {
                    next != null -> {
                        held += ReportHeld(reader, next.target, at, at)
                        ReportSlot.Pending(0)
                    }
                    running != null -> ReportSlot.Cancelled
                    else -> ReportSlot.Idle
                }
            }
        }
        slots += ContactSlot(reader, slot)
    }
    held.sortBy { it.reader.index }
    return Holds(held, events, minted, slots)
}

/** A hold of [ticks], as the slot carries it: milliseconds on the house's clock. */
private fun pending(ticks: Long): ReportSlot =
    ReportSlot.Pending((ticks * 1_000L / Balance.TICKS_PER_SECOND).coerceIn(0L, MOST_HELD_MILLIS).toInt())

private const val MOST_HELD_MILLIS: Long = home.someoneshome.model.protocol.RangingWire.MOST_REPORT_MILLIS.toLong()

/**
 * **Whether a pair minted at [minted] is still inside its grace at [at]** (D-291, the owner's
 * 2026-11-18 ruling, option a). The one question [ContactGate.PAIR_GRACE_TICKS] answers: under five
 * seconds since the pair last minted, the pair mints nothing, neither a contact nor a report either
 * way, and at five seconds it may again. Asked of the pair alone, never of a role, an arm or a
 * Revoke, so the grace after a Revoke is the same grace as after any touch.
 */
internal fun inPairGrace(minted: ContactMinted, at: Tick): Boolean =
    at.step - minted.at.step < ContactGate.PAIR_GRACE_TICKS

/**
 * **Seat order, across every pair minted on one arrival**: by the seat that acts or calls, then by
 * the seat it names, and a contact before a report between the same two. So in a huddle an armed
 * Insider's first contact reaches the lowest seat they touched, and that is the order the rules see.
 */
private fun inSeatOrder(events: List<Event>): List<Event> = events.sortedWith(
    compareBy<Event> { first(it) }.thenBy { second(it) }.thenBy { if (it is Event.ContactMade) 0 else 1 },
)

private fun first(event: Event): Int = when (event) {
    is Event.ContactMade -> event.actor.index
    is Event.MeetingCalled -> event.caller.index
    else -> Int.MAX_VALUE
}

private fun second(event: Event): Int = when (event) {
    is Event.ContactMade -> event.target.index
    is Event.MeetingCalled -> (event.trigger as? MeetingTrigger.RevokeReported)?.reported?.index ?: Int.MAX_VALUE
    else -> Int.MAX_VALUE
}

/** A total order on slot lists, so two uploads from one seat in one arrival sort the same way every time. */
private fun slotsKey(sightings: List<Sighting>): String =
    sightings.joinToString(",") { "${it.token.hex}:${it.rssi}:${it.window.index}" }
