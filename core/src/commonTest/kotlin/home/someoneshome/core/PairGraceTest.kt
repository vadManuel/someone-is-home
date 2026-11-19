package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-291: the five-second grace after a Revoke is a rule** (the owner's 2026-11-18 ruling, option a).
 *
 * The resolver and the rules together, one arrival at a time, the way the house runs them: the
 * Insider's phone touches seat 3 armed, the Revoke lands, and the Insider's phone stays within
 * reach of seat 3, uploading every second. For [ContactGate.PAIR_GRACE_TICKS] after the touch the
 * pair mints nothing, so the Insider's own phone does not ring the house at the touch; at five
 * seconds it reports like anyone (the owner: *the Insider who did the Revoke may report*). A phone
 * that was not in the touch is not in the pair, and reports as soon as its own two readings count.
 */
class PairGraceTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val ready = Balance.REVOKE_COOLDOWN / 2
    private val insider = 1
    private val target = 3
    private val neighbour = 2
    private val w = ContactWindow(5_000L)
    private val hit = ready + 100
    private val second = ContactGate.UPLOAD_TICKS

    private val armed: GameState = listOf(
        Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(Seat(1), Seat(5))),
        Event.RevokeArmed(Tick(ready + 1), Seat(insider)),
    ).fold(GameState.EMPTY) { s, e -> reduce(s, e).state }

    private var uploads = 0L

    private fun hears(seat: Int) =
        Sighting(ContactTokens.of(contactSeedFor(armed.seed, Seat(seat)), w), -20, w)

    private fun up(seat: Int, vararg heard: Int) = ContactUpload(
        Seat(seat),
        SightingUpload.unpack(
            SightingUpload.pack(contactSeedFor(armed.seed, Seat(seat)), w, uploads++, heard.map { hears(it) }),
        )!!,
    )

    /** The house: the resolver's memory and the rules' state, every minted event put through `admit`. */
    private inner class House {
        var memory = ContactMemory.EMPTY
        var state = armed

        fun at(tick: Long, vararg uploads: ContactUpload): List<Event> {
            val r = resolveContacts(memory, Tick(tick), w, uploads.toList(), state)
            memory = r.memory
            for (event in r.events) {
                val admission = admit(state, event)
                check(admission is Admission.Admitted) { "the house refused $event: $admission" }
                state = admission.reduction.state
            }
            return r.events
        }

        /** The touch: both phones hear each other, the arm lands, and seat 3 is out. */
        fun touch(vararg alsoUploading: ContactUpload) {
            val events = at(hit, up(insider, target), up(target, insider), *alsoUploading)
            assertEquals(2, events.count { it is Event.ContactMade }, "the touch minted no contact: $events")
            assertTrue(state.isRevoked(Seat(target)), "the armed touch revoked nobody, so the grace protected nothing")
        }
    }

    private fun reportBy(caller: Int) = { e: Event ->
        e is Event.MeetingCalled && e.caller == Seat(caller) && e.trigger == MeetingTrigger.RevokeReported(Seat(target))
    }

    @Test
    fun `the grace is five seconds`() {
        assertEquals(5L * Balance.TICKS_PER_SECOND, ContactGate.PAIR_GRACE_TICKS, "the grace moved without a ruling (D-291)")
    }

    /**
     * The Insider's phone hears seat 3 at every upload after the hit, which is two readings in the
     * span from the first second on: without the grace it would report one second after the touch.
     */
    @Test
    fun `the Revoking Insider's phone does not report inside the grace and does at its end`() {
        val house = House()
        house.touch()
        var tick = hit + second
        while (tick < hit + ContactGate.PAIR_GRACE_TICKS) {
            assertEquals(emptyList(), house.at(tick, up(insider, target)), "the Insider's phone reported at ${tick - hit} ticks, inside the grace")
            tick += second
        }
        assertEquals(hit + ContactGate.PAIR_GRACE_TICKS, tick, "the walk did not reach the grace's end")
        val atEnd = house.at(tick, up(insider, target))
        assertEquals(1, atEnd.count(reportBy(insider)), "the Insider's phone did not report at the grace's end: $atEnd")
        assertTrue(house.state.meeting != null, "the house refused the Insider's report: the owner ruled it may report")
    }

    /** The grace counts from the touch, not from the last reading: one tick short still mints nothing. */
    @Test
    fun `one tick before the grace ends is still inside it`() {
        val house = House()
        house.touch()
        house.at(hit + ContactGate.PAIR_GRACE_TICKS - 2 * second, up(insider, target))
        assertEquals(
            emptyList(),
            house.at(hit + ContactGate.PAIR_GRACE_TICKS - 1, up(insider, target)),
            "the Insider's phone reported a tick before the grace ended",
        )
    }

    /** Accepted under U3: a neighbour within reach of seat 3 is not in the pair and reports at once. */
    @Test
    fun `a neighbour who was not in the touch reports as soon as its own readings count`() {
        val house = House()
        house.touch(up(neighbour, target))
        val next = house.at(hit + second, up(insider, target), up(neighbour, target))
        assertEquals(1, next.count(reportBy(neighbour)), "the neighbour was held by a grace that is the pair's: $next")
        assertEquals(0, next.count(reportBy(insider)), "the Insider's phone reported inside its grace")
    }
}
