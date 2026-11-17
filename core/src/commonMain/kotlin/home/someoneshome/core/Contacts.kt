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
 * - **A mutual sighting mints [Event.ContactMade] in both directions**, for every pair, whoever
 *   they are. Whether either side holds an armed Revoke is `Rules.contact`'s question, and the
 *   emit schema decides who hears the answer. **No role and no armed state is read here**, and
 *   the guarantee is [ContactHouse]'s field list: it carries the round's seed, its seats and who
 *   is Revoked, and nothing else.
 * - **A one-sided sighting of a Revoked seat mints the report**, `MeetingCalled` with
 *   [MeetingTrigger.RevokeReported], exactly the event the bench's stand-in mints. One side
 *   suffices because a Revoked phone advertises and never scans (D-008). Who is Revoked is the
 *   one piece of state read, because the report *is* that fact meeting a phone.
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
data class ContactUpload(val seat: Seat, val sightings: List<Sighting>)

/** What a resolution produced: the memory to carry to the next one, and the events to admit. */
data class ContactResolution(val memory: ContactMemory, val events: List<Event>)

/**
 * **What the resolver remembers between arrivals**, as values, so the resolver is a pure function
 * of `(memory, arrival)` and a replay that feeds the same arrivals rebuilds the same memory.
 *
 * Every list is built in an order fixed by the arrivals alone (seat order within an arrival), so two
 * memories reached by the same arrivals are equal.
 */
data class ContactMemory(
    /** The latest gated, resolved sighting per (observer, target), younger than [ContactGate.MUTUAL_TICKS]. */
    val heard: List<ContactHeard>,
    /** The last contact minted per unordered pair, younger than [ContactGate.DEBOUNCE_TICKS]. */
    val minted: List<ContactMinted>,
    /** Uploads already taken, younger than [ContactGate.DUPLICATE_TICKS], so a second delivery is recognised. */
    val received: List<ContactReceived>,
    /** The token book for the window last resolved in, or null before the first arrival. */
    val book: ContactBook?,
) {
    companion object {
        val EMPTY = ContactMemory(emptyList(), emptyList(), emptyList(), null)
    }
}

/** [observer]'s radio heard [target] at contact range, in the upload that arrived at [at]. */
data class ContactHeard(val observer: Seat, val target: Seat, val at: Tick)

/** A contact between [low] and [high] (low < high) was minted at [at]. */
data class ContactMinted(val low: Seat, val high: Seat, val at: Tick)

/** [seat]'s upload of exactly [sightings] was taken at [at]. */
data class ContactReceived(val seat: Seat, val at: Tick, val sightings: List<Sighting>)

/**
 * **Every token the round's seats advertise in [window] and the window before it, and whose each
 * is.** [ContactTokens.seatOf]'s inversion, computed once per window rather than once per slot.
 *
 * `seatOf` answers one token by deriving every seat's token, which is two HMACs per seat per slot:
 * 24 slots × 18 seats × 2 windows per upload, from every phone once a second, on a host that is a
 * phone with a whole-app allocation budget (rule 7). The book derives each seat's two tokens once
 * per fifteen seconds and answers by lookup. Built by [ContactTokens.of], the function `seatOf`
 * inverts, in seat order with the current window first and the first match kept, so it answers
 * exactly what `seatOf` answers; `ContactResolverTest` pins the two together.
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
            for (back in 0L..ContactGate.WINDOWS_BACK) {
                val w = ContactWindow(window.index - back)
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
 * The mutual window is a placeholder and says so; the floor is the owner's ruling (D-233). A number
 * here is a gameplay rule wearing a constant's clothes: the floor decides whether a Revoke lands at
 * all, and a mis-set one fails silently in both directions (architecture risk 2).
 */
object ContactGate {

    /**
     * **Ruled by the owner on two phones: −40 dBm** (D-233). A reading at or above this is contact;
     * below it is not.
     *
     * Measured phone to phone (2026-11-16): touching firmly −27, touching loosely −37, 30 cm −43
     * (−41 to −45), 1 m −57. At −35 about a third to a half of quick realistic touches registered;
     * at −40 about 65 to 80 per cent. So −40 opens on a loose touch and stays shut at 30 cm, by one
     * dB at that distance's strongest reading.
     *
     * **The radius is a hard constraint, not a tuning value** (D-008, `gdd.md`). At *nearby* the
     * report is a through-wall detector for Revoked players and *you can walk past a Revoked player
     * and never notice* stops being true; too tight, and a Revoke silently never lands, which nobody
     * can tell from an Insider who never tried. D-008 said it may only ever tighten; **D-233 loosens
     * it from −35 by the owner's ruling, for this value only**, because at −35 half or more of real
     * touches never landed. Any further move needs its own ruling and a two-phone 30 cm pass-by
     * check.
     */
    const val RSSI_FLOOR: Int = -40

    /**
     * **A reading of zero or more is not a reading.** CoreBluetooth reports 127 when it has no
     * RSSI, and the upload carries it faithfully; ungated, *no reading* would pass as the strongest
     * contact possible from anywhere in the house.
     */
    const val RSSI_CEILING: Int = -1

    /**
     * **Windows behind the house's that a token may come from: one.** A sighting made just before
     * an edge arrives just after it, so the previous window's tokens are honest for a whole window
     * longer. Anything older is stale and resolves to no seat. A token from the window *ahead* of the
     * house's is not accepted: an advertiser whose clock runs ahead past an edge is unresolvable for
     * its skew and is heard again by the next upload.
     */
    const val WINDOWS_BACK: Long = 1L

    /**
     * ⚠️ **PLACEHOLDER: two seconds.** The two halves of a mutual sighting must arrive within this
     * of each other. Measured on arrival ticks, so it holds one upload interval on each side
     * (`SightingUpload.CADENCE_MILLIS`, itself provisional) plus delivery jitter. Unit 7's
     * contact-to-dark measurement is what sets it, with the cadence.
     */
    const val MUTUAL_TICKS: Long = 2L * Balance.TICKS_PER_SECOND

    /** **Ruled: one contact per pair per five seconds** (D-212's scoping, ruling 4). */
    const val DEBOUNCE_TICKS: Long = 5L * Balance.TICKS_PER_SECOND

    /**
     * **How long an upload is remembered to recognise a second delivery: the window and the one
     * behind it.** Derived, not tuned: after that, every token in it is stale and it can resolve
     * nothing anyway.
     */
    const val DUPLICATE_TICKS: Long =
        (WINDOWS_BACK + 1) * ContactWindow.MILLIS * Balance.TICKS_PER_SECOND / 1_000L

    /** Whether [rssi] is a contact-range reading. */
    fun atContact(rssi: Int): Boolean = rssi in RSSI_FLOOR..RSSI_CEILING
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
) {
    fun holds(seat: Seat): Boolean = seats.any { it.index == seat.index }
    fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }

    companion object {
        fun of(state: GameState) = ContactHouse(
            roundSeed = state.seed,
            seats = state.seats.sortedBy { it.index },
            revoked = state.revoked.sortedBy { it.index },
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
 *    uploads never match; a repeat is the transport's, and it must not refresh a sighting's time.
 *    An upload from a seat not in the round is ignored too: nothing it heard can be anybody's.
 * 2. **Each slot is gated, then inverted.** [ContactGate.atContact] first, then the book: a token
 *    from the house's window or the one before it, from a seat of this round, that is not the
 *    uploader's own. Filler, strangers and stale windows resolve to nobody.
 * 3. **The heard list is refreshed** with what this arrival heard.
 * 4. **Each pair heard this arrival is judged:** a contact if each side has heard the other within
 *    [ContactGate.MUTUAL_TICKS]; a report for each side that heard a Revoked seat.
 * 5. **A pair minted within [ContactGate.DEBOUNCE_TICKS] mints nothing**; otherwise everything that
 *    applies to it is minted at [at] and the pair is stamped once.
 */
fun resolveContacts(
    memory: ContactMemory,
    at: Tick,
    window: ContactWindow,
    uploads: List<ContactUpload>,
    state: GameState,
): ContactResolution {
    val house = ContactHouse.of(state)
    val book = memory.book?.takeIf { it.fits(house, window) } ?: ContactBook.of(house, window)

    var received = memory.received.filter { at.step - it.at.step <= ContactGate.DUPLICATE_TICKS }
    val fresh = mutableListOf<ContactHeard>()
    for (upload in uploads.sortedWith(compareBy<ContactUpload> { it.seat.index }.thenBy { slotsKey(it.sightings) })) {
        val repeat = received.any { it.seat.index == upload.seat.index && it.sightings == upload.sightings }
        if (repeat || !house.holds(upload.seat)) continue
        received = received + ContactReceived(upload.seat, at, upload.sightings)
        upload.sightings
            .filter { ContactGate.atContact(it.rssi) }
            .mapNotNull { book.seatOf[it.token] }
            .filter { it.index != upload.seat.index }
            .distinctBy { it.index }
            .forEach { fresh += ContactHeard(upload.seat, it, at) }
    }

    val heard = (
        memory.heard.filter { old -> fresh.none { it.observer.index == old.observer.index && it.target.index == old.target.index } } +
            fresh
        )
        .filter { at.step - it.at.step <= ContactGate.MUTUAL_TICKS }
        .sortedWith(compareBy<ContactHeard> { it.observer.index }.thenBy { it.target.index })

    fun heardBy(observer: Seat, target: Seat): ContactHeard? =
        heard.firstOrNull { it.observer.index == observer.index && it.target.index == target.index }

    var minted = memory.minted.filter { at.step - it.at.step < ContactGate.DEBOUNCE_TICKS }
    val events = mutableListOf<Event>()
    val pairs = fresh
        .map { if (it.observer.index < it.target.index) it.observer to it.target else it.target to it.observer }
        .distinctBy { (low, high) -> low.index to high.index }
        .sortedWith(compareBy<Pair<Seat, Seat>> { it.first.index }.thenBy { it.second.index })
    for ((low, high) in pairs) {
        val there = heardBy(low, high)
        val back = heardBy(high, low)
        val applies = buildList {
            // Mutual is both halves present. [heard] holds only halves younger than the window and
            // every pair judged here has a half from this arrival, so present is within it.
            if (there != null && back != null) {
                add(Event.ContactMade(at, low, high))
                add(Event.ContactMade(at, high, low))
            }
            if (there != null && house.isRevoked(high)) {
                add(Event.MeetingCalled(at, low, MeetingTrigger.RevokeReported(high)))
            }
            if (back != null && house.isRevoked(low)) {
                add(Event.MeetingCalled(at, high, MeetingTrigger.RevokeReported(low)))
            }
        }
        val debounced = minted.any { it.low.index == low.index && it.high.index == high.index }
        if (applies.isEmpty() || debounced) continue
        events += applies
        minted = minted + ContactMinted(low, high, at)
    }

    return ContactResolution(
        memory = ContactMemory(
            heard = heard,
            minted = minted.sortedWith(compareBy<ContactMinted> { it.low.index }.thenBy { it.high.index }),
            received = received,
            book = book,
        ),
        events = inSeatOrder(events),
    )
}

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
