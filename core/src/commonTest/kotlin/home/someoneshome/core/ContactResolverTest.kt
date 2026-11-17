package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-213: the house's contact resolver, case by case.** Every test feeds arrivals through
 * [resolveContacts] the way the wiring will, one tick at a time, carrying the memory forward.
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

    private fun token(seat: Int, window: ContactWindow = w) =
        ContactTokens.of(contactSeedFor(state.seed, Seat(seat)), window)

    private fun hears(seat: Int, rssi: Int = -20, window: ContactWindow = w) = Sighting(token(seat, window), rssi, window)

    private var uploads = 0L

    /**
     * **A real upload: packed and unpacked**, so it carries the keyed filler every phone sends and
     * no two are alike unless they are one upload delivered twice — which is the property the
     * duplicate guard relies on, and a hand-built slot list would not have it.
     */
    private fun up(seat: Int, vararg heard: Sighting) = ContactUpload(
        Seat(seat),
        SightingUpload.unpack(SightingUpload.pack(contactSeedFor(state.seed, Seat(seat)), w, uploads++, heard.toList()))!!,
    )

    private fun contacts(a: Int, b: Int) =
        listOf(Event.ContactMade(Tick(0), Seat(a), Seat(b)), Event.ContactMade(Tick(0), Seat(b), Seat(a)))

    private fun report(caller: Int, reported: Int) =
        Event.MeetingCalled(Tick(0), Seat(caller), MeetingTrigger.RevokeReported(Seat(reported)))

    /**
     * **[caller] hears [reported] in two uploads one second apart**, the least a report takes
     * (D-236), and returns what the second minted. The first must mint nothing.
     */
    private fun Feed.twice(caller: Int, reported: Int, rssi: Int = -20, window: ContactWindow = w, from: Long = t0): List<Event> {
        assertEquals(emptyList(), at(from, up(caller, hears(reported, rssi, window))), "one reading of seat $reported minted something")
        return at(from + ContactGate.UPLOAD_TICKS, up(caller, hears(reported, rssi, window)))
    }

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
    // The RSSI gate.

    @Test
    fun `a mutual sighting at the floor is a contact and one dBm under it is not`() {
        val floor = ContactGate.RSSI_FLOOR
        assertEquals(-40, floor, "the floor moved without a ruling: the owner set it at -40 (D-233)")
        val at = Feed()
        assertEquals(emptyList(), at.at(t0, up(2, hears(3, floor))))
        assertEquals(contacts(2, 3), shape(at.at(t0 + 10, up(3, hears(2, floor)))))

        val under = Feed()
        under.at(t0, up(2, hears(3, floor)))
        assertEquals(emptyList(), under.at(t0 + 10, up(3, hears(2, floor - 1))), "one side under the floor")
        assertEquals(emptyList(), under.memory.heard.filter { it.observer == Seat(3) }, "an under-floor reading was remembered")
    }

    /**
     * **The owner's ruling, pinned in the readings it was made from** (D-233, which loosens D-008's
     * "only tighten" for this value). Literal dBm, not [ContactGate.RSSI_FLOOR], so moving the
     * constant in either direction fails here: a loose touch (−37) and the floor itself (−40) are
     * contact; one dB under, 30 cm's strongest (−41), 30 cm's median (−43) and 1 m (−57) are not.
     */
    @Test
    fun `the gate is the owner's -40 ruling - a loose touch lands and 30 cm does not`() {
        for (rssi in listOf(-27, -37, -40)) {
            val f = Feed()
            f.at(t0, up(2, hears(3, rssi)))
            assertEquals(contacts(2, 3), shape(f.at(t0 + 10, up(3, hears(2, rssi)))), "$rssi dBm is a touch (D-233) and did not land")
        }
        for (rssi in listOf(-41, -43, -45, -57)) {
            val f = Feed()
            f.at(t0, up(2, hears(3, rssi)))
            assertEquals(emptyList(), f.at(t0 + 10, up(3, hears(2, rssi))), "$rssi dBm is 30 cm or more (D-233) and landed")
            assertEquals(emptyList(), Feed().twice(2, 6, rssi), "$rssi dBm, twice, reported a Revoked seat")
        }
    }

    @Test
    fun `a reading of zero or more is no reading and never passes the gate`() {
        for (junk in listOf(127, 0)) {
            val f = Feed()
            f.at(t0, up(2, hears(3, -20)))
            assertEquals(emptyList(), f.at(t0 + 10, up(3, hears(2, junk))), "RSSI $junk passed as contact")
            assertEquals(emptyList(), Feed().twice(2, 6, junk), "RSSI $junk, twice, reported a Revoked seat")
        }
        val f = Feed()
        f.at(t0, up(2, hears(3, -20)))
        assertEquals(contacts(2, 3), shape(f.at(t0 + 10, up(3, hears(2, -1)))), "-1 dBm is a reading")
    }

    @Test
    fun `a Revoked seat heard under the floor is not reported`() {
        assertEquals(emptyList(), Feed().twice(2, 6, ContactGate.RSSI_FLOOR - 1))
        assertEquals(listOf(report(2, 6)), shape(Feed().twice(2, 6, ContactGate.RSSI_FLOOR)))
    }

    // ----------------------------------------------------------------------------------------------
    // Mutual against one-sided.

    @Test
    fun `one side hearing the other is not a contact`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, hears(3))))
        assertEquals(emptyList(), f.at(t0 + 10, up(3)))
        assertEquals(emptyList(), f.at(t0 + 20, up(2, hears(3))))
    }

    @Test
    fun `the two halves must arrive within the mutual window`() {
        val limit = ContactGate.MUTUAL_TICKS
        val inside = Feed()
        inside.at(t0, up(2, hears(3)))
        assertEquals(contacts(2, 3), shape(inside.at(t0 + limit, up(3, hears(2)))))

        val outside = Feed()
        outside.at(t0, up(2, hears(3)))
        assertEquals(emptyList(), outside.at(t0 + limit + 1, up(3, hears(2))), "halves ${limit + 1} ticks apart")
    }

    @Test
    fun `both halves in one arrival are one contact`() {
        assertEquals(contacts(2, 3), shape(Feed().at(t0, up(3, hears(2)), up(2, hears(3)))))
    }

    @Test
    fun `a Revoked seat needs one side only - because it never scans`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, hears(6)), up(6)))
        assertEquals(listOf(report(2, 6)), shape(f.at(t0 + ContactGate.UPLOAD_TICKS, up(2, hears(6)), up(6))))
    }

    // ----------------------------------------------------------------------------------------------
    // A report is heard twice (D-236).

    @Test
    fun `one reading of a Revoked seat reports nothing and a second within the span reports it`() {
        assertEquals(2, ContactGate.REPORT_READINGS, "a report's readings moved without a D-entry (D-236)")
        assertEquals(SightingUpload.CADENCE_MILLIS * Balance.TICKS_PER_SECOND / 1_000L, ContactGate.UPLOAD_TICKS, "the span is counted in uploads")
        assertEquals(50L, ContactGate.REPORT_SPAN_TICKS, "two of three uploads, plus half of one, is 2.5 s (D-236)")

        val once = Feed()
        assertEquals(emptyList(), once.at(t0, up(2, hears(6))), "one reading reported a Revoked seat")
        for (tick in (t0 + 1)..(t0 + ContactGate.DEBOUNCE_TICKS * 2) step 10) {
            assertEquals(emptyList(), once.at(tick, up(2)), "one reading reported a Revoked seat later, at $tick")
        }

        val span = ContactGate.REPORT_SPAN_TICKS
        val inside = Feed()
        inside.at(t0, up(2, hears(6)))
        assertEquals(listOf(report(2, 6)), shape(inside.at(t0 + span, up(2, hears(6)))), "two readings $span ticks apart")

        val outside = Feed()
        outside.at(t0, up(2, hears(6)))
        assertEquals(emptyList(), outside.at(t0 + span + 1, up(2, hears(6))), "two readings ${span + 1} ticks apart reported")

        // The two of the three latest: hear, miss, hear.
        val gap = Feed()
        gap.at(t0, up(2, hears(6)))
        gap.at(t0 + ContactGate.UPLOAD_TICKS, up(2))
        assertEquals(listOf(report(2, 6)), shape(gap.at(t0 + 2 * ContactGate.UPLOAD_TICKS, up(2, hears(6)))), "hear, miss, hear")
    }

    /**
     * **The 30 cm pass-by and the lingering Insider** (R5 of the 2026-11-16 review): what D-233's
     * one-dB margin lets through is one reading at a time. Stray readings at the floor three uploads
     * apart are not a find; two in a row are.
     */
    @Test
    fun `a stray reading over the floor among 30 cm readings reports nothing`() {
        val f = Feed()
        // 30 cm's measured spread (D-233, −41 to −45), with a stray −40 every third upload.
        val pass = listOf(-43, -41, -40, -44, -45, -40, -42, -45, -40)
        for ((i, rssi) in pass.withIndex()) {
            assertEquals(emptyList(), f.at(t0 + i * ContactGate.UPLOAD_TICKS, up(2, hears(6, rssi))), "a 30 cm pass reported at reading $i ($rssi dBm)")
        }
        assertEquals(listOf(report(2, 6)), shape(f.at(t0 + pass.size * ContactGate.UPLOAD_TICKS, up(2, hears(6, -40)))), "two readings at the floor, one upload apart")
    }

    @Test
    fun `readings from different phones do not add up to one report`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, hears(6))))
        assertEquals(emptyList(), f.at(t0 + ContactGate.UPLOAD_TICKS, up(3, hears(6))), "two phones' single readings made a report")
    }

    @Test
    fun `a second delivery of one upload is not a second reading`() {
        val f = Feed()
        val first = up(2, hears(6))
        f.at(t0, first)
        assertEquals(emptyList(), f.at(t0 + ContactGate.UPLOAD_TICKS, first), "a duplicate counted as the second reading")
    }

    @Test
    fun `a contact still needs only one reading from each phone`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(2, hears(3, -37))))
        assertEquals(contacts(2, 3), shape(f.at(t0 + ContactGate.UPLOAD_TICKS, up(3, hears(2, -38)))), "a quick touch, one reading a side, did not land")
    }

    // ----------------------------------------------------------------------------------------------
    // Windows.

    /**
     * **R8 of the 2026-11-16 review: a phone whose clock runs ahead of the host's** moves to its next
     * token before the house's window does. Its sightings of others resolve either way (it reports
     * the tokens it heard); what a fast clock changes is the token *it* advertises.
     */
    @Test
    fun `a phone whose clock runs one window ahead is still mapped at the edge - and two ahead is not`() {
        val ahead = ContactWindow(w.index + 1)
        val f = Feed()
        f.at(t0, up(2, hears(3, window = w)))
        assertEquals(contacts(2, 3), shape(f.at(t0 + 10, up(3, hears(2, window = w)))), "the baseline touch")

        val edge = Feed()
        edge.at(t0, up(2, hears(3, window = ahead)), window = w)
        assertEquals(contacts(2, 3), shape(edge.at(t0 + 10, up(3, hears(2, window = w)), window = w)), "seat 3 is a window ahead and its touch was lost")
        assertEquals(listOf(Seat(3)), edge.memory.heard.filter { it.observer == Seat(2) }.map { it.target }, "seat 3's next-window token mapped to nobody")

        val far = Feed()
        far.at(t0, up(2, hears(3, window = ContactWindow(w.index + 2))), window = w)
        assertEquals(emptyList(), far.at(t0 + 10, up(3, hears(2, window = w)), window = w), "a token two windows ahead was mapped")
        assertEquals(emptyList(), far.memory.heard.filter { it.observer == Seat(2) }, "a token two windows ahead was remembered")
    }

    @Test
    fun `a token one window ahead is remembered against a second delivery for as long as it resolves`() {
        // Taken at the start of the house's window, carrying the next window's token: that token
        // resolves until the end of the window after next, which is just under three windows on.
        val start = 0L
        val first = up(2, hears(6, window = ContactWindow(w.index + 1)))
        val f = Feed()
        f.at(start, first, window = w)
        f.at(start + 1, up(2, hears(6, window = ContactWindow(w.index + 1))), window = w) // the report
        val late = start + 3L * ContactWindow.MILLIS * Balance.TICKS_PER_SECOND / 1_000L - 1
        assertEquals(emptyList(), f.at(late, first, window = ContactWindow(w.index + 2)), "the duplicate counted")
        assertTrue(f.memory.heard.none { it.at.step >= late }, "a duplicate's token was taken as a fresh reading: ${f.memory.heard}")
        assertEquals(late + 1, ContactGate.DUPLICATE_TICKS, "the duplicate memory is as long as a token resolves: three windows")
    }

    @Test
    fun `the house's window and one either side of it are accepted and nothing else`() {
        for ((label, window, accepted) in listOf(
            Triple("current", w, true),
            Triple("previous", ContactWindow(w.index - 1), true),
            Triple("two back", ContactWindow(w.index - 2), false),
            Triple("ahead", ContactWindow(w.index + 1), true),
            Triple("two ahead", ContactWindow(w.index + 2), false),
        )) {
            val f = Feed()
            f.at(t0, up(2, hears(3, window = window)))
            val got = f.at(t0 + 10, up(3, hears(2, window = window)))
            assertEquals(if (accepted) contacts(2, 3) else emptyList(), shape(got), "a token from the $label window")
            assertEquals(
                if (accepted) listOf(report(2, 6)) else emptyList(),
                shape(Feed().twice(2, 6, window = window)),
                "a report from the $label window",
            )
        }
    }

    @Test
    fun `across an edge the half heard before it still pairs with the half heard after`() {
        val next = ContactWindow(w.index + 1)
        val f = Feed()
        f.at(t0, up(2, hears(3, window = w)), window = w)
        assertEquals(contacts(2, 3), shape(f.at(t0 + 10, up(3, hears(2, window = w)), window = next)))
        assertEquals(next, f.memory.book?.window, "the book was not rebuilt for the new window")
    }

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

    // ----------------------------------------------------------------------------------------------
    // Debounce and duplicates.

    @Test
    fun `one contact per pair per five seconds - in either direction and whatever it was`() {
        val gap = ContactGate.DEBOUNCE_TICKS
        assertEquals(100L, gap)
        val f = Feed()
        assertEquals(contacts(2, 3), shape(f.at(t0, up(2, hears(3)), up(3, hears(2)))))
        assertEquals(emptyList(), f.at(t0 + gap - 1, up(3, hears(2)), up(2, hears(3))), "re-minted inside five seconds")
        assertEquals(contacts(2, 3), shape(f.at(t0 + gap, up(2, hears(3)), up(3, hears(2)))))

        val r = Feed()
        assertEquals(listOf(report(2, 6)), shape(r.twice(2, 6)))
        assertEquals(emptyList(), r.at(t0 + 40, up(2, hears(6))), "a second report inside five seconds")
        val stamped = t0 + ContactGate.UPLOAD_TICKS
        // Still held there, an upload a second, so every arrival below has two readings to count.
        for (tick in listOf(t0 + 60, t0 + 80, t0 + 100)) assertEquals(emptyList(), r.at(tick, up(2, hears(6))), "reported at $tick")
        assertEquals(emptyList(), r.at(stamped + gap - 1, up(2, hears(6))))
        assertEquals(listOf(report(2, 6)), shape(r.at(stamped + gap, up(2, hears(6)))))
    }

    @Test
    fun `one pair's debounce does not hold another pair`() {
        val f = Feed()
        f.at(t0, up(2, hears(3)), up(3, hears(2)))
        assertEquals(contacts(2, 4), shape(f.at(t0 + 10, up(2, hears(3), hears(4)), up(4, hears(2)))))
    }

    @Test
    fun `a second delivery of an upload does not refresh what it heard`() {
        val first = up(2, hears(3))
        val f = Feed()
        f.at(t0, first)
        assertEquals(emptyList(), f.at(t0 + 60, first), "the duplicate minted something")
        assertEquals(emptyList(), f.at(t0 + 70, up(3, hears(2))), "a duplicate stood in for a fresh half")
        assertTrue(f.memory.heard.none { it.observer == Seat(2) }, "seat 2's half is still fresh: ${f.memory.heard}")
    }

    @Test
    fun `a second delivery of a report's upload does not report twice`() {
        val f = Feed()
        f.at(t0, up(2, hears(6)))
        val second = up(2, hears(6))
        assertEquals(listOf(report(2, 6)), shape(f.at(t0 + ContactGate.UPLOAD_TICKS, second)))
        assertEquals(emptyList(), f.at(t0 + ContactGate.DEBOUNCE_TICKS + 20, second), "the duplicate reported again")
        assertEquals(emptyList(), f.at(t0 + ContactGate.DEBOUNCE_TICKS + 30, second), "two duplicates counted as two readings")
    }

    // ----------------------------------------------------------------------------------------------
    // Slots that are nobody.

    @Test
    fun `filler - strangers and a phone's own token resolve to nobody`() {
        val packed = SightingUpload.unpack(SightingUpload.pack(contactSeedFor(state.seed, Seat(2)), w, 7, emptyList()))!!
        val loudFiller = packed.map { it.copy(rssi = -20) }
        assertEquals(SightingUpload.SLOTS, loudFiller.size)
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, ContactUpload(Seat(2), loudFiller)))
        assertEquals(emptyList(), f.memory.heard, "filler resolved to a seat")

        val stranger = Sighting(ContactTokens.of(contactSeedFor(state.seed + 1, Seat(3)), w), -20, w)
        assertEquals(emptyList(), f.at(t0 + 10, up(2, stranger, hears(2))))
        assertEquals(emptyList(), f.memory.heard, "a stranger or the phone's own token resolved")
    }

    @Test
    fun `an upload from a seat not in the round is not heard from`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0, up(30, hears(6))), "a seat outside the round reported")
        f.at(t0, up(3, hears(30)))
        assertEquals(emptyList(), f.memory.heard)
    }

    // ----------------------------------------------------------------------------------------------
    // Several at once.

    @Test
    fun `three phones in a huddle are three contacts - six events - in seat order`() {
        val f = Feed()
        val events = f.at(
            t0,
            up(4, hears(2), hears(3)),
            up(2, hears(3), hears(4)),
            up(3, hears(4), hears(2)),
        )
        assertEquals(
            listOf(2 to 3, 2 to 4, 3 to 2, 3 to 4, 4 to 2, 4 to 3).map { (a, b) -> Event.ContactMade(Tick(t0), Seat(a), Seat(b)) },
            events,
        )
    }

    @Test
    fun `in a huddle the rules decide - and an armed Insider reaches the lowest seat touched`() {
        val armed = reduce(state, Event.RevokeArmed(Tick(ready + 200), Seat(5))).state
        val f = Feed(against = armed)
        val events = f.at(t0 + 200, up(5, hears(3), hears(4)), up(3, hears(5), hears(4)), up(4, hears(5), hears(3)))
        assertEquals(6, events.size)
        val after = events.fold(armed) { s, e -> (admit(s, e) as Admission.Admitted).reduction.state }
        assertEquals(listOf(3, 6), after.revoked.map { it.index }, "the rules did not see the huddle in seat order")
    }

    @Test
    fun `a pair that both hear a Revoked seat mints every event that applies - in seat order`() {
        val f = Feed()
        assertEquals(emptyList(), f.at(t0 - ContactGate.UPLOAD_TICKS, up(4, hears(6)), up(2, hears(6))), "one reading reported")
        val events = f.at(t0, up(4, hears(2), hears(6)), up(2, hears(4), hears(6)))
        assertEquals(
            listOf(
                Event.ContactMade(Tick(t0), Seat(2), Seat(4)),
                Event.MeetingCalled(Tick(t0), Seat(2), MeetingTrigger.RevokeReported(Seat(6))),
                Event.ContactMade(Tick(t0), Seat(4), Seat(2)),
                Event.MeetingCalled(Tick(t0), Seat(4), MeetingTrigger.RevokeReported(Seat(6))),
            ),
            events,
        )
        // The first report calls a meeting, and from then on the gate refuses both what follows: the
        // touch's other half, because contacts are ignored at a meeting (R5, D-217), and the second
        // report, because seat 6 has been reported already (S4, D-219, which sits above the meeting
        // checks so a repeat is recorded as one). Both are recorded rather than dropped.
        var s = state
        val refusals = events.mapNotNull { e ->
            when (val a = admit(s, e)) {
                is Admission.Admitted -> { s = a.reduction.state; null }
                is Admission.Refused -> e::class.simpleName to a.reason
            }
        }
        assertEquals(
            listOf(
                "ContactMade" to RefusalReason.MeetingAlreadyRunning,
                "MeetingCalled" to RefusalReason.AlreadyReported,
            ),
            refusals,
        )
    }

    // ----------------------------------------------------------------------------------------------
    // Replay.

    private fun script(f: Feed) {
        uploads = 0L // the same uploads, filler and all, on every run
        f.at(t0, up(2, hears(3, -30)), up(5, hears(6)))
        f.at(t0 + 5, up(3, hears(2, -28), hears(4, -60)))
        f.at(t0 + 20, up(4, hears(3, -25)), up(3, hears(4, -22)), up(6))
        f.at(t0 + 80, up(2, hears(3)), up(3, hears(2)), up(7, hears(6), hears(5)))
        f.at(t0 + 100, up(7, hears(6, -38)))
        f.at(t0 + 300, up(1, hears(2, window = ContactWindow(w.index))), up(2, hears(1)), window = ContactWindow(w.index + 1))
        f.at(t0 + 301, up(1, hears(2, window = ContactWindow(w.index))), window = ContactWindow(w.index + 1))
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
        val batch = listOf(up(4, hears(2), hears(6)), up(2, hears(4), hears(6)), up(3, hears(2)), up(2, hears(3)))
        val baseline = resolveContacts(ContactMemory.EMPTY, Tick(t0), w, batch, state)
        assertTrue(baseline.events.isNotEmpty())
        for (order in listOf(batch.reversed(), listOf(batch[2], batch[0], batch[3], batch[1]))) {
            assertEquals(baseline, resolveContacts(ContactMemory.EMPTY, Tick(t0), w, order, state))
        }
    }
}
