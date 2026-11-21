package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-213: the house's contact resolver, case by case**, on distance (D-301; every round since
 * D-308). Every test feeds arrivals through [resolveContacts] the way the wiring does, one tick at
 * a time, carrying the memory forward. What the report's hold does is [UwbContactTest]'s; this is
 * the touch, the duplicate guard, the grace between a pair, the token book the pairing ranks by,
 * and the resolver's determinism.
 */
class ContactResolverTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val ready = Balance.REVOKE_COOLDOWN / 2

    /** Seats 1 and 5 are the Insiders; seat 1 has Revoked seat 6, so 6 is out and 1 is not armed. */
    private val state: GameState = listOf(
        Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(Seat(1), Seat(5))),
        Event.RevokeArmed(Tick(ready + 1), Seat(1)),
        Event.ContactMade(Tick(ready + 2), Seat(1), Seat(6)),
    ).fold(GameState.EMPTY) { s, e -> reduce(s, e).state }

    private val w = ContactWindow(5_000L)
    private val t0 = ready + 100

    private var uploads = 0L

    /**
     * **A real upload: packed and unpacked**, so it carries the keyed filler every phone sends and
     * no two are alike unless they are one upload delivered twice — which is the property the
     * duplicate guard relies on, and a hand-built slot list would not have it. [read] is each peer
     * this phone measured, at 4 cm unless given (`seat to cm`).
     */
    private fun up(seat: Int, vararg read: Int, cm: Int = 4, window: ContactWindow = w) = ContactUpload(
        Seat(seat),
        SightingUpload.unpack(SightingUpload.pack(contactSeedFor(state.seed, Seat(seat)), window, uploads++, emptyList()))!!,
        read.map { ContactDistance(Seat(it), cm) },
    )

    private fun contacts(a: Int, b: Int) =
        listOf(Event.ContactMade(Tick(0), Seat(a), Seat(b)), Event.ContactMade(Tick(0), Seat(b), Seat(a)))

    /** The resolver, driven one arrival at a time. */
    private inner class Feed(var memory: ContactMemory = ContactMemory.EMPTY, val against: GameState = state) {
        val log = mutableListOf<ContactResolution>()
        fun at(tick: Long, vararg uploads: ContactUpload, window: ContactWindow = w): List<Event> {
            val r = resolveContacts(memory, Tick(tick), window, uploads.toList(), against)
            memory = r.memory
            log += r
            r.events.forEach { assertEquals(Tick(tick), it.at, "minted at the wrong tick: $it") }
            return r.events
        }
    }

    /** Events compared without their tick, which [Feed.at] already checks is the arrival's. */
    private fun shape(events: List<Event>) = events.map {
        when (it) {
            is Event.ContactMade -> it.copy(at = Tick(0))
            is Event.MeetingCalled -> it.copy(at = Tick(0))
            else -> it
        }
    }

    // ----------------------------------------------------------------------------------------------
    // The cadence.

    @Test
    fun `an upload interval is the upload cadence`() {
        assertEquals(SightingUpload.CADENCE_MILLIS * Balance.TICKS_PER_SECOND / 1_000L, ContactGate.UPLOAD_TICKS, "the report's slack and gap are counted in uploads")
    }

    // ----------------------------------------------------------------------------------------------
    // Mutual against one-sided.

    @Test
    fun `one side reading the other is not a contact`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, 3)))
        assertEquals(emptyList(), f.at(t0 + 10, up(3)))
        assertEquals(emptyList(), f.at(t0 + 20, up(2, 3)))
    }

    @Test
    fun `the two halves must arrive within the mutual window`() {
        val limit = ContactGate.MUTUAL_TICKS
        val inside = Feed()
        inside.at(t0, up(2, 3))
        assertEquals(contacts(2, 3), shape(inside.at(t0 + limit, up(3, 2))))

        val outside = Feed()
        outside.at(t0, up(2, 3))
        assertEquals(emptyList(), outside.at(t0 + limit + 1, up(3, 2)), "halves ${limit + 1} ticks apart")
    }

    @Test
    fun `both halves in one arrival are one contact`() {
        assertEquals(contacts(2, 3), shape(Feed().at(t0, up(3, 2), up(2, 3))))
    }

    /**
     * **The Bluetooth slots are no half of a touch** (D-308). Every one of the 24 slots of seat 2's
     * upload rewritten to −20 dBm, and seat 3's upload hearing seat 2's real token at −20: nothing is
     * remembered and nothing touches. Only a distance is read.
     */
    @Test
    fun `a phone's Bluetooth slots however loud are no half of a touch`() {
        val packed = SightingUpload.unpack(SightingUpload.pack(contactSeedFor(state.seed, Seat(2)), w, 7, emptyList()))!!
        val loud = packed.map { it.copy(rssi = -20) }
        assertEquals(SightingUpload.SLOTS, loud.size)
        val hearsTwo = Sighting(ContactTokens.of(contactSeedFor(state.seed, Seat(2)), w), -20, w)
        val three = ContactUpload(
            Seat(3),
            SightingUpload.unpack(SightingUpload.pack(contactSeedFor(state.seed, Seat(3)), w, 8, listOf(hearsTwo)))!!,
        )
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, ContactUpload(Seat(2), loud), three))
        assertEquals(emptyList(), f.memory.heard, "a Bluetooth slot was read as half of a touch")
    }

    // ----------------------------------------------------------------------------------------------
    // The book: kept for the house's UWB pairing, which ranks peers by Bluetooth (D-300).

    @Test
    fun `the book answers exactly what seatOf answers`() {
        val seeds = seats.map { it to contactSeedFor(state.seed, it) }
        val book = ContactBook.of(ContactHouse.of(state), w)
        for (back in -3L..3L) {
            val window = ContactWindow(w.index - back)
            for (seat in seats) {
                val t = ContactTokens.of(seeds[seat.index].second, window)
                val expected = ContactTokens.seatOf(t, w, seeds)
                    ?: ContactTokens.seatOf(t, ContactWindow(w.index - 1), seeds)
                    ?: ContactTokens.seatOf(t, ContactWindow(w.index + 1), seeds)
                assertEquals(expected, book.seatOf[t], "seat ${seat.index}'s token ${back} windows back")
                assertEquals(back in -1L..1L, expected != null, "seat ${seat.index}'s token ${back} windows back")
            }
        }
        assertEquals(3 * seats.size, book.seatOf.size, "the book holds three windows of every seat")
    }

    @Test
    fun `across an edge the half read before it still pairs with the half read after and the book is rebuilt`() {
        val next = ContactWindow(w.index + 1)
        val f = Feed()
        f.at(t0, up(2, 3), window = w)
        assertEquals(contacts(2, 3), shape(f.at(t0 + 10, up(3, 2, window = next), window = next)))
        assertEquals(next, f.memory.book?.window, "the book was not rebuilt for the new window")
    }

    // ----------------------------------------------------------------------------------------------
    // The grace and duplicates.

    @Test
    fun `one contact per pair per five seconds - in either direction`() {
        val gap = ContactGate.PAIR_GRACE_TICKS
        assertEquals(100L, gap)
        val f = Feed()
        assertEquals(contacts(2, 3), shape(f.at(t0, up(2, 3), up(3, 2))))
        assertEquals(emptyList(), f.at(t0 + gap - 1, up(3, 2), up(2, 3)), "re-minted inside five seconds")
        assertEquals(contacts(2, 3), shape(f.at(t0 + gap, up(2, 3), up(3, 2))))
    }

    @Test
    fun `one pair's grace does not hold another pair`() {
        val f = Feed()
        f.at(t0, up(2, 3), up(3, 2))
        assertEquals(contacts(2, 4), shape(f.at(t0 + 10, up(2, 3, 4), up(4, 2))))
    }

    @Test
    fun `a second delivery of an upload does not refresh what it read`() {
        val first = up(2, 3)
        val f = Feed()
        f.at(t0, first)
        assertEquals(emptyList(), f.at(t0 + 60, first), "the duplicate minted something")
        assertEquals(emptyList(), f.at(t0 + 70, up(3, 2)), "a duplicate stood in for a fresh half")
        assertTrue(f.memory.heard.none { it.observer == Seat(2) }, "seat 2's half is still fresh: ${f.memory.heard}")
    }

    @Test
    fun `a second delivery is recognised for as long as a token in it can resolve`() {
        val start = 0L
        val first = up(2, 3)
        val f = Feed()
        f.at(start, first, window = w)
        val late = start + 3L * ContactWindow.MILLIS * Balance.TICKS_PER_SECOND / 1_000L - 1
        assertEquals(emptyList(), f.at(late, first, window = ContactWindow(w.index + 2)), "the duplicate counted")
        assertTrue(f.memory.heard.none { it.at.step >= late }, "a duplicate was taken as a fresh reading: ${f.memory.heard}")
        assertEquals(late + 1, ContactGate.DUPLICATE_TICKS, "the duplicate memory is as long as a token resolves: three windows")
    }

    // ----------------------------------------------------------------------------------------------
    // Distances that are nobody.

    @Test
    fun `a distance to the phone itself or to a seat not in the round is nobody`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, 2, 30)))
        assertEquals(emptyList(), f.memory.heard, "a phone's own seat or a stranger was read")
    }

    @Test
    fun `an upload from a seat not in the round is not read`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(30, 3), up(3, 30)), "a seat outside the round touched")
        assertEquals(emptyList(), f.memory.heard)
    }

    // ----------------------------------------------------------------------------------------------
    // Several at once.

    @Test
    fun `three phones in a huddle are three contacts - six events - in seat order`() {
        val f = Feed()
        val events = f.at(t0, up(4, 2, 3), up(2, 3, 4), up(3, 4, 2))
        assertEquals(
            listOf(2 to 3, 2 to 4, 3 to 2, 3 to 4, 4 to 2, 4 to 3).map { (a, b) -> Event.ContactMade(Tick(t0), Seat(a), Seat(b)) },
            events,
        )
    }

    @Test
    fun `in a huddle the rules decide - and an armed Insider reaches the lowest seat touched`() {
        val armed = reduce(state, Event.RevokeArmed(Tick(ready + 200), Seat(5))).state
        val f = Feed(against = armed)
        val events = f.at(t0 + 200, up(5, 3, 4), up(3, 5, 4), up(4, 5, 3))
        assertEquals(6, events.size)
        val after = events.fold(armed) { s, e -> (admit(s, e) as Admission.Admitted).reduction.state }
        assertEquals(listOf(3, 6), after.revoked.map { it.index }, "the rules did not see the huddle in seat order")
    }

    // ----------------------------------------------------------------------------------------------
    // Replay.

    private fun script(f: Feed) {
        uploads = 0L // the same uploads, filler and all, on every run
        f.at(t0, up(2, 3, cm = 6), up(5, 6))
        f.at(t0 + 5, up(3, 2, cm = 9), up(4, 3, cm = 60))
        f.at(t0 + 20, up(4, 3, cm = 2), up(3, 4, cm = 3), up(6, 3))
        f.at(t0 + 80, up(2, 3), up(3, 2), up(7, 6, 5))
        f.at(t0 + 300, up(1, 2), up(2, 1), window = ContactWindow(w.index + 1))
    }

    @Test
    fun `the same arrivals replay to the same events and the same memory`() {
        val a = Feed().also { script(it) }
        val b = Feed().also { script(it) }
        assertTrue(a.log.flatMap { it.events }.isNotEmpty(), "the script mints nothing; this compares nothing")
        assertEquals(a.log, b.log)
    }

    @Test
    fun `the order of uploads inside one arrival changes nothing`() {
        val batch = listOf(up(4, 2), up(2, 4, 3), up(3, 2), up(7, 6))
        val baseline = resolveContacts(ContactMemory.EMPTY, Tick(t0), w, batch, state)
        assertTrue(baseline.events.isNotEmpty())
        for (order in listOf(batch.reversed(), listOf(batch[2], batch[0], batch[3], batch[1]))) {
            assertEquals(baseline, resolveContacts(ContactMemory.EMPTY, Tick(t0), w, order, state))
        }
    }
}
