package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactSource
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **D-301: the rules read UWB distance** (the U6 plan, unit 3), in a round whose contact source is
 * [ContactSource.Uwb], and Bluetooth exactly as before in one whose source is [ContactSource.Rssi].
 *
 * The resolver and the rules together, one arrival a second, the way the house runs them. Every
 * upload carries real packed Bluetooth slots (so the duplicate guard sees two uploads as two) and the
 * distances the house's pairing resolved for it ([ContactUpload.distances]).
 *
 * - **A touch** is both phones at [ContactGate.REVOKE_REACH_CM] or nearer, within the mutual window.
 * - **A report** is one living reader holding a Revoked seat within [ContactGate.REPORT_REACH_CM]
 *   for [ContactGate.REPORT_HOLD_TICKS], read from the reader's side alone, told to the reader's
 *   phone as a pending slot, cancelled when it leaves reach first, and covered by the pair's grace.
 */
class UwbContactTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val ready = Balance.REVOKE_COOLDOWN / 2
    private val insider = 1
    private val target = 3
    private val neighbour = 2
    private val w = ContactWindow(5_000L)
    private val second = ContactGate.UPLOAD_TICKS
    private val hit = ready + 100

    private fun armedRound(source: ContactSource, arm: Boolean = true): GameState = buildList {
        add(Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(Seat(1), Seat(5)), contactSource = source))
        if (arm) add(Event.RevokeArmed(Tick(ready + 1), Seat(insider)))
    }.fold(GameState.EMPTY) { s, e -> reduce(s, e).state }

    private var uploads = 0L

    /**
     * [seat]'s upload: Bluetooth hearing [heard] at −20 dBm, and [distances] (seat to centimetres),
     * from a phone on the main view unless [main] says otherwise (D-307).
     */
    private fun up(seat: Int, vararg distances: Pair<Int, Int>, heard: List<Int> = emptyList(), seed: Long = 42L, main: Boolean = true) = ContactUpload(
        Seat(seat),
        SightingUpload.unpack(
            SightingUpload.pack(
                contactSeedFor(seed, Seat(seat)), w, uploads++,
                heard.map { Sighting(ContactTokens.of(contactSeedFor(seed, Seat(it)), w), -20, w) },
            ),
        )!!,
        distances.map { (peer, cm) -> ContactDistance(Seat(peer), cm) },
        onMainView = main,
    )

    /** The house: the resolver's memory and the rules' state, every minted event put through `admit`. */
    private inner class House(var state: GameState) {
        var memory = ContactMemory.EMPTY
        var last: ContactResolution? = null

        fun at(tick: Long, vararg uploads: ContactUpload): List<Event> {
            val r = resolveContacts(memory, Tick(tick), w, uploads.toList(), state)
            memory = r.memory
            last = r
            for (event in r.events) {
                val admission = admit(state, event)
                check(admission is Admission.Admitted) { "the house refused $event: $admission" }
                state = admission.reduction.state
            }
            return r.events
        }

        fun slot(seat: Int): ReportSlot = last!!.slotOf(Seat(seat))

        /** The touch: both phones at 4 cm, the arm lands, and seat 3 is out. */
        fun touch(vararg alsoUploading: ContactUpload) {
            val events = at(hit, up(insider, target to 4), up(target, insider to 5), *alsoUploading)
            assertEquals(2, events.count { it is Event.ContactMade }, "the touch minted no contact: $events")
            assertTrue(state.isRevoked(Seat(target)), "the armed touch revoked nobody")
        }
    }

    private fun reportBy(caller: Int, reported: Int = target) = { e: Event ->
        e is Event.MeetingCalled && e.caller == Seat(caller) && e.trigger == MeetingTrigger.RevokeReported(Seat(reported))
    }

    private fun contacts(events: List<Event>) = events.filterIsInstance<Event.ContactMade>().map { it.actor.index to it.target.index }

    // ---- The numbers, pinned ----------------------------------------------------------------------

    @Test
    fun `the reaches and the hold are the owner's numbers`() {
        assertEquals(10, ContactGate.REVOKE_REACH_CM, "the touch's reach moved without a ruling (U1)")
        assertEquals(30, ContactGate.REPORT_REACH_CM, "the report's reach moved without a ruling (Q5)")
        assertEquals(2L * Balance.TICKS_PER_SECOND, ContactGate.REPORT_HOLD_TICKS, "the report's hold moved without a ruling (Q5)")
        assertEquals(150, SensingGate.ROOM_REACH_CM, "the map's reach moved (the plan's provisional start)")
    }

    // ---- The touch -------------------------------------------------------------------------------

    @Test
    fun `a touch is both phones at ten centimetres or nearer within the mutual window`() {
        val house = House(armedRound(ContactSource.Uwb, arm = false))
        assertEquals(emptyList(), house.at(hit, up(4, 6 to 10)), "one phone's reading alone minted something")
        val second = house.at(hit + ContactGate.MUTUAL_TICKS, up(6, 4 to 9))
        assertEquals(listOf(4 to 6, 6 to 4), contacts(second), "both phones at ten centimetres or nearer did not touch: $second")
    }

    @Test
    fun `eleven centimetres on either side is not a touch`() {
        for ((a, b) in listOf(11 to 3, 3 to 11)) {
            val house = House(armedRound(ContactSource.Uwb, arm = false))
            val events = house.at(hit, up(4, 6 to a), up(6, 4 to b))
            assertEquals(emptyList(), contacts(events), "a pair read at $a and $b cm touched: $events")
        }
    }

    @Test
    fun `the halves of a touch must fall in the mutual window`() {
        val house = House(armedRound(ContactSource.Uwb, arm = false))
        house.at(hit, up(4, 6 to 2))
        val late = house.at(hit + ContactGate.MUTUAL_TICKS + 1, up(6, 4 to 2))
        assertEquals(emptyList(), contacts(late), "two halves further apart than the mutual window touched: $late")
    }

    @Test
    fun `an armed Insider's touch by distance Revokes the other phone`() {
        val house = House(armedRound(ContactSource.Uwb))
        house.touch()
        assertTrue(house.state.isRevoked(Seat(target)))
    }

    @Test
    fun `an out phone's distances are not read so it touches nobody`() {
        val house = House(armedRound(ContactSource.Uwb))
        house.touch()
        // Seat 1's arm is spent; seat 5 (the other Insider) arms and touches the Revoked seat 3, both ways.
        house.state = reduce(house.state, Event.RevokeArmed(Tick(hit + 200), Seat(5))).state
        val events = house.at(hit + 210, up(5, target to 3), up(target, 5 to 3))
        assertEquals(emptyList(), contacts(events), "an out phone's own distance made half of a touch: $events")
    }

    // ---- The report ------------------------------------------------------------------------------

    /** A Revoked seat 3 and a reader at seat 2, nobody touching: the plain hold. */
    private fun reportRound(): House {
        val house = House(armedRound(ContactSource.Uwb))
        house.touch()
        return house
    }

    @Test
    fun `a reader holding a Revoked seat at thirty centimetres for two seconds calls the meeting`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        assertEquals(emptyList(), house.at(t0, up(neighbour, target to 30)))
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour), "the first upload in reach did not start the hold")
        assertEquals(emptyList(), house.at(t0 + second, up(neighbour, target to 22)))
        assertEquals(ReportSlot.Pending(1_000), house.slot(neighbour), "a second in, the slot did not say a second held")
        val called = house.at(t0 + 2 * second, up(neighbour, target to 28))
        assertEquals(1, called.count(reportBy(neighbour)), "two seconds held did not call the meeting: $called")
        assertEquals(ReportSlot.Idle, house.slot(neighbour), "the slot did not go back to idle at the call")
        assertNotNull(house.state.meeting, "the rules refused the report")
    }

    @Test
    fun `thirty-one centimetres starts no hold`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        for (k in 0..4) {
            assertEquals(emptyList(), house.at(t0 + k * second, up(neighbour, target to 31)))
            assertEquals(ReportSlot.Idle, house.slot(neighbour), "a reader at 31 cm was told it is holding a report")
        }
    }

    @Test
    fun `the hold is two seconds less half an upload of jitter and no less`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        house.at(t0, up(neighbour, target to 10))
        house.at(t0 + second, up(neighbour, target to 10))
        val early = house.at(t0 + ContactGate.REPORT_HOLD_TICKS - ContactGate.REPORT_HOLD_SLACK_TICKS - 1, up(neighbour, target to 10))
        assertEquals(emptyList(), early, "the meeting was called before the hold was done")
        val onTime = house.at(t0 + ContactGate.REPORT_HOLD_TICKS - ContactGate.REPORT_HOLD_SLACK_TICKS, up(neighbour, target to 10))
        assertEquals(1, onTime.count(reportBy(neighbour)), "an upload two seconds in, less the slack, did not call: $onTime")
    }

    @Test
    fun `leaving reach before two seconds cancels the hold and calls nothing`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        house.at(t0, up(neighbour, target to 20))
        house.at(t0 + second, up(neighbour, target to 40))
        assertEquals(ReportSlot.Cancelled, house.slot(neighbour), "leaving reach did not cancel the hold")
        val after = house.at(t0 + 2 * second, up(neighbour, target to 80))
        assertEquals(emptyList(), after, "a cancelled hold called the house")
        assertEquals(ReportSlot.Idle, house.slot(neighbour), "a cancelled hold did not go back to idle")
        // In reach again: a fresh hold, from nothing.
        house.at(t0 + 3 * second, up(neighbour, target to 20))
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour), "back in reach did not start a fresh hold")
    }

    // ---- The main view (D-307) ---------------------------------------------------------------------

    @Test
    fun `a reader off the main view starts no hold however long it is in reach`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        for (k in 0..4) {
            assertEquals(emptyList(), house.at(t0 + k * second, up(neighbour, target to 10, main = false)), "a reader off the main view called the house")
            assertEquals(ReportSlot.Idle, house.slot(neighbour), "a reader off the main view was told it is holding a report")
        }
        // Back on the main view, in reach: a hold from nothing, and the call two seconds after.
        val t1 = t0 + 5 * second
        house.at(t1, up(neighbour, target to 10))
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour), "back on the main view did not start the hold")
        house.at(t1 + second, up(neighbour, target to 10))
        val called = house.at(t1 + 2 * second, up(neighbour, target to 10))
        assertEquals(1, called.count(reportBy(neighbour)), "a hold on the main view did not call: $called")
    }

    @Test
    fun `leaving the main view cancels a running hold and calls nothing`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        house.at(t0, up(neighbour, target to 20))
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour))
        val left = house.at(t0 + second, up(neighbour, target to 20, main = false))
        assertEquals(emptyList(), left, "a reader that left the main view called the house")
        assertEquals(ReportSlot.Cancelled, house.slot(neighbour), "leaving the main view did not cancel the hold")
        val still = house.at(t0 + 2 * second, up(neighbour, target to 20, main = false))
        assertEquals(emptyList(), still, "a cancelled hold called the house off the main view")
        assertEquals(ReportSlot.Idle, house.slot(neighbour))
        assertTrue(house.memory.held.none { it.reader == Seat(neighbour) }, "the house kept a hold for a reader off the main view")
    }

    @Test
    fun `the main view is asked of the reader alone and never of the Revoked phone`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        // The Revoked seat's own phone off the main view (it is on the couch) changes nothing.
        for (k in 0..1) {
            house.at(t0 + k * second, up(neighbour, target to 20), up(target, neighbour to 20, main = false))
            assertEquals(ReportSlot.Pending((k * 1_000)), house.slot(neighbour), "the Revoked phone's screen moved the reader's hold")
        }
        val called = house.at(t0 + 2 * second, up(neighbour, target to 20), up(target, neighbour to 20, main = false))
        assertEquals(1, called.count(reportBy(neighbour)), "the Revoked phone's screen stopped the report: $called")
    }

    @Test
    fun `the pairing asks the hold's own main view gate so a report pair is ranged only while the hold could run`() {
        val house = reportRound()
        val at = Tick(hit + ContactGate.PAIR_GRACE_TICKS)
        val need = RoundRangingNeed()
        need.state = house.state
        assertTrue(need.needs(Seat(neighbour), Seat(target), at), "a reader on the main view beside a Revoked seat was not needed")
        need.sawMainView(Seat(neighbour), false)
        assertEquals(false, need.needs(Seat(neighbour), Seat(target), at), "the pairing ranged a report pair for a reader off the main view (D-306, D-307)")
        need.sawMainView(Seat(target), true)
        assertEquals(false, need.needs(Seat(target), Seat(neighbour), at), "the Revoked phone's main view made a report pair")
        need.sawMainView(Seat(neighbour), true)
        assertTrue(need.needs(Seat(neighbour), Seat(target), at), "back on the main view, the pair was not needed again")
    }

    @Test
    fun `a missed upload breaks the hold`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        house.at(t0, up(neighbour, target to 20))
        val gap = house.at(t0 + ContactGate.REPORT_GAP_TICKS + 1, up(neighbour, target to 20))
        assertEquals(emptyList(), gap, "a hold across a missed upload called the house")
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour), "a hold across a missed upload was not started again")
    }

    @Test
    fun `the house reads only the reader's side`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        // The Revoked phone measures the neighbour at 5 cm every second; the neighbour's phone
        // lists no distance for the pair (no reading this second).
        for (k in 0..4) {
            val events = house.at(t0 + k * second, up(target, neighbour to 5), up(neighbour))
            assertEquals(emptyList(), events, "the Revoked phone's own measurement reported: $events")
            assertEquals(ReportSlot.Idle, house.slot(neighbour), "the reader was told it holds a report it never measured")
            assertEquals(ReportSlot.Idle, house.slot(target), "the Revoked phone was told it holds a report")
        }
    }

    @Test
    fun `the Revoking Insider's phone holds nothing inside the grace then holds and reports after it`() {
        val house = reportRound()
        var tick = hit + second
        while (tick < hit + ContactGate.PAIR_GRACE_TICKS) {
            assertEquals(emptyList(), house.at(tick, up(insider, target to 6)))
            assertEquals(ReportSlot.Idle, house.slot(insider), "the Insider's phone started a hold ${tick - hit} ticks into the grace")
            tick += second
        }
        house.at(tick, up(insider, target to 6))
        assertEquals(ReportSlot.Pending(0), house.slot(insider), "the Insider's circle did not start at the grace's end")
        house.at(tick + second, up(insider, target to 6))
        val called = house.at(tick + 2 * second, up(insider, target to 6))
        assertEquals(1, called.count(reportBy(insider)), "the Insider's phone did not report after the grace: $called")
    }

    @Test
    fun `a neighbour who was not in the touch starts holding at once`() {
        val house = House(armedRound(ContactSource.Uwb))
        house.touch(up(neighbour, target to 25))
        house.at(hit + second, up(neighbour, target to 25))
        assertEquals(ReportSlot.Pending(0), house.slot(neighbour), "the neighbour was held by a grace that is the pair's")
        house.at(hit + 2 * second, up(neighbour, target to 25))
        val called = house.at(hit + 3 * second, up(neighbour, target to 25))
        assertEquals(1, called.count(reportBy(neighbour)), "the neighbour did not report: $called")
    }

    @Test
    fun `a seat already reported or a meeting running or an out reader holds nothing`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        house.at(t0, up(neighbour, target to 10))
        house.at(t0 + second, up(neighbour, target to 10))
        assertEquals(1, house.at(t0 + 2 * second, up(neighbour, target to 10)).count(reportBy(neighbour)))
        // A meeting is running: nobody holds.
        house.at(t0 + 3 * second, up(4, target to 10))
        assertEquals(ReportSlot.Idle, house.slot(4), "a hold started at a meeting")
        // After the meeting, seat 3 has been reported: nobody holds it again (S4).
        house.state = house.state.copy(meeting = null)
        house.at(t0 + 4 * second, up(4, target to 10))
        assertEquals(ReportSlot.Idle, house.slot(4), "a hold started on a seat already reported")
        // The out reader: seat 3 itself, reading another Revoked seat, is never a reader.
        house.state = house.state.copy(revoked = house.state.revoked + Seat(6), reported = emptyList())
        house.at(t0 + 5 * second, up(target, 6 to 10))
        assertEquals(ReportSlot.Idle, house.slot(target), "an out phone was told it holds a report")
    }

    @Test
    fun `a second delivery is answered with the hold as it stands and changes nothing`() {
        val house = reportRound()
        val t0 = hit + ContactGate.PAIR_GRACE_TICKS
        val first = up(neighbour, target to 10)
        house.at(t0, first)
        house.at(t0 + 10, first)
        assertEquals(ReportSlot.Pending(500), house.slot(neighbour), "a repeat did not answer the hold as it stands")
        assertEquals(1, house.memory.held.size)
        assertEquals(Tick(t0), house.memory.held.single().seen, "a repeat refreshed the hold")
    }

    // ---- The source, per round -------------------------------------------------------------------

    @Test
    fun `a round that reads Bluetooth ignores distances and one that reads distance ignores Bluetooth`() {
        val rssi = House(armedRound(ContactSource.Rssi, arm = false))
        val byDistance = rssi.at(hit, up(4, 6 to 2), up(6, 4 to 2))
        assertEquals(emptyList(), byDistance, "a Bluetooth round touched on distance: $byDistance")
        val byRssi = rssi.at(hit + 200, up(4, heard = listOf(6)), up(6, heard = listOf(4)))
        assertEquals(listOf(4 to 6, 6 to 4), contacts(byRssi), "a Bluetooth round did not touch on RSSI")

        val uwb = House(armedRound(ContactSource.Uwb, arm = false))
        val uwbByRssi = uwb.at(hit, up(4, heard = listOf(6)), up(6, heard = listOf(4)))
        assertEquals(emptyList(), uwbByRssi, "a distance round touched on RSSI: $uwbByRssi")
        assertEquals(ReportSlot.Idle, uwb.slot(4))
    }

    @Test
    fun `a Bluetooth round's report is D-236's and its slots are always idle`() {
        val house = House(armedRound(ContactSource.Rssi))
        val events = house.at(hit, up(insider, heard = listOf(target)), up(target, heard = listOf(insider)))
        assertEquals(2, contacts(events).size)
        house.at(hit + second, up(neighbour, target to 5, heard = listOf(target)))
        assertEquals(ReportSlot.Idle, house.slot(neighbour), "a Bluetooth round told a phone it holds a report")
        val called = house.at(hit + 2 * second, up(neighbour, target to 5, heard = listOf(target)))
        assertEquals(1, called.count(reportBy(neighbour)), "D-236's two readings did not report in a Bluetooth round")
        assertEquals(ReportSlot.Idle, house.slot(neighbour))
    }

    @Test
    fun `the source is UWB only when every armed phone can range`() {
        val five = (0 until 5).map { Seat(it) }
        assertEquals(ContactSource.Uwb, contactSourceFor(five) { 3 })
        assertEquals(ContactSource.Uwb, contactSourceFor(five) { 1 })
        assertEquals(ContactSource.Rssi, contactSourceFor(five) { if (it.index == 2) 0 else 3 }, "one phone that cannot range")
        assertEquals(ContactSource.Rssi, contactSourceFor(five) { if (it.index == 4) null else 3 }, "one phone that never said")
        assertEquals(ContactSource.Rssi, contactSourceFor(five) { null }, "no phone said")
        assertEquals(ContactSource.Rssi, contactSourceFor(emptyList()) { 3 }, "no seats")
    }

    @Test
    fun `the arming records the source the house chose`() {
        val five = (0 until 5).map { Seat(it) }
        val uwb = armingFor(Tick(0), 7L, five, null, emptyList(), contactSource = contactSourceFor(five) { 2 })
        assertEquals(ContactSource.Uwb, uwb.contactSource)
        assertEquals(ContactSource.Uwb, reduce(GameState.EMPTY, uwb).state.contactSource, "the state did not hold the arming's source")
        val rssi = armingFor(Tick(0), 7L, five, null, emptyList())
        assertEquals(ContactSource.Rssi, rssi.contactSource, "an arming that chose nothing does not read Bluetooth")
    }
}
