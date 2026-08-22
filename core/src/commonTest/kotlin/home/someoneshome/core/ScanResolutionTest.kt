package home.someoneshome.core

import home.someoneshome.model.CardPayload
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HouseMap
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RegisterResult
import home.someoneshome.model.Role
import home.someoneshome.model.Room
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What a scan means in a round** (D-123, D-124, D-110, D-139, D-140).
 *
 * Everything here is written as a *differential* between two cases that must be indistinguishable,
 * or as a property that a build which quietly did nothing would fail. An assertion that merely
 * said *"a scan opens work"* would pass on every build in this file's history, including the one
 * where a scan opened whatever that seat had next wherever they happened to be standing.
 */
private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1))
/**
 * **Twenty markers, not eight.** D-127's floor is eight and it is the honest home to arm in for
 * most of what this file's neighbours assert — but at eight markers and eight seats the round's
 * active set is the whole home and every seat is anchored at every card, so *a registered card
 * that holds nothing for you* does not exist to scan. A home with room in it is what makes
 * D-124's case reachable at all.
 */
private val MARKERS = (0 until 20).map { MarkerId("m$it") }

private fun armed(): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed = 20260821L, seats = SEATS, insiders = INSIDERS, markers = MARKERS),
).state

/** This seat's own first anchor, read off the draw rather than typed in. */
private fun cardFor(state: GameState, seat: Seat): MarkerId =
    state.workOrderFor(seat)!!.entries.first().marker

/** A card in the round that holds nothing for this seat. Registered, and not theirs. */
private fun emptyCardFor(state: GameState, seat: Seat): MarkerId {
    val mine = state.workOrderFor(seat)!!.entries.map { it.marker.value }.toSet()
    return state.activeMarkers.first { it.value !in mine }
}

private fun scan(state: GameState, seat: Seat, marker: MarkerId, at: Long = 1L) =
    reduce(state, Event.MarkerScanned(Tick(at), seat, marker))

private fun answerOf(effects: List<Effect>): Effect.ScanAnswered =
    effects.filterIsInstance<Effect.ScanAnswered>().single()

/**
 * **THE ABSENCE-IS-THE-LEAK TEST, for the scan** (rule 1, D-124).
 *
 * NOTHING FOR YOU HERE and a real opening must be the same message with different contents, never
 * a message and a silence.
 */
class NothingForYouHereTest {

    /**
     * **One answer per scan, whatever the card held.**
     *
     * This is the test the old behaviour fails: a scan used to emit nothing at all, so *found
     * work* and *found nothing* differed by the existence of a message. A player whose phone heard
     * nothing could not tell an empty card from a dead radio, and anything downstream counting
     * messages could separate the two without seeing a payload.
     */
    @Test
    fun `a scan that finds nothing answers in the same shape as one that finds work`() {
        val state = armed()
        val seat = Seat(0)
        val found = scan(state, seat, cardFor(state, seat)).effects
        val empty = scan(state, seat, emptyCardFor(state, seat)).effects

        assertEquals(
            found.map { it::class.simpleName }, empty.map { it::class.simpleName },
            "a scan that found nothing came back a different shape from one that found work",
        )
        assertNotNull(answerOf(found).opened, "the seat's own anchor opened nothing")
        assertNull(answerOf(empty).opened, "a card holding nothing for this seat opened work")
    }

    /**
     * **Both answers reach exactly one phone, and it is the same phone.**
     *
     * The shape test above passes on a build that broadcasts both. What would be published is who
     * is standing at which marker finding work, live, to the whole house — the per-player read the
     * percentage meter (D-103) exists to keep out of aggregate.
     */
    @Test
    fun `both answers are addressed to the scanning seat and to nobody else`() {
        val state = armed()
        val seat = Seat(3)
        for (card in listOf(cardFor(state, seat), emptyCardFor(state, seat))) {
            val answer = answerOf(scan(state, seat, card).effects)
            assertEquals(
                listOf(seat),
                home.someoneshome.model.EmitSchema.deliveries(answer, state).map { it.seat },
                "the answer to ${card.value} reached more phones than the one that scanned it",
            )
        }
    }

    /**
     * **An Insider's scan is answered exactly as a Resident's is** (D-109, D-129).
     *
     * The fake is drawn by the same rule at the same length, so the scan that opens it must come
     * back in the same shape. A build that answered an Insider's scan with a null would be a role
     * oracle after one card, delivered by the rules rather than by a screen.
     */
    @Test
    fun `an Insider's scan opens work exactly as a Resident's does`() {
        val state = armed()
        assertEquals(Role.Resident, state.roleOf(Seat(0)))
        assertEquals(Role.Insider, state.roleOf(Seat(1)))

        val resident = answerOf(scan(state, Seat(0), cardFor(state, Seat(0))).effects)
        val insider = answerOf(scan(state, Seat(1), cardFor(state, Seat(1))).effects)
        assertNotNull(resident.opened)
        assertNotNull(insider.opened, "an Insider's scan found no work at their own anchor")
        assertEquals(
            resident.opened!!.parameters.size, insider.opened!!.parameters.size,
            "the two roles were handed different amounts of question",
        )
    }

    /**
     * **The instance on the wire cannot carry the answer** (rule 3).
     *
     * Structural rather than a value check: [home.someoneshome.model.SubroutineInstance] is a
     * different type from `OpenSubroutine`, so there is no field here a later change could
     * populate with the answer key. The house holds `expected`; the phone is told the question.
     */
    @Test
    fun `the opened instance carries the question and not the answer`() {
        val state = armed()
        val seat = Seat(0)
        val after = scan(state, seat, cardFor(state, seat))
        val shown = assertNotNull(answerOf(after.effects).opened)
        val held = assertNotNull(after.state.openSubroutineFor(seat))

        assertEquals(held.parameters, shown.parameters)
        assertEquals(held.entry, shown.entry)
        assertFalse(
            shown.toString().contains("expected", ignoreCase = true),
            "the client-facing instance grew a field that could hold the answer: $shown",
        )
    }
}

/** **D-139 and D-140 — the instance rides the scan, and every re-scan re-draws it.** */
class InstanceRidesTheScanTest {

    /**
     * **A re-scan asks a different question**, which is the whole of the re-draw ruling: *a retry
     * is a fresh judgment rather than a second run at a picture the player has already memorised.*
     *
     * Both halves are asserted. A build whose parameters never moved would fail the first; a build
     * that re-drew the parameters and kept the answer key would fail the second, and would be
     * worse than not re-drawing at all — the player would be shown a new picture and graded
     * against the old one.
     */
    @Test
    fun `a re-scan re-draws the parameters and the answer with them`() {
        val state = armed()
        val seat = Seat(0)
        val card = cardFor(state, seat)

        val first = scan(state, seat, card).state
        val one = assertNotNull(first.openSubroutineFor(seat))
        // Spend it the way a round does, then walk back to the marker.
        val spent = reduce(first, Event.SubroutineReturned(Tick(2), seat, card, listOf(9, 9))).state
        val two = assertNotNull(scan(spent, seat, card, at = 3L).state.openSubroutineFor(seat))

        assertEquals(one.entry, two.entry, "the re-scan opened a different line of the order")
        assertNotEquals(
            one.parameters, two.parameters,
            "the re-scan re-used the first instance's parameters, so a retry is a second run at " +
                "a picture the player has already memorised",
        )
        assertNotEquals(
            one.expected, two.expected,
            "the parameters moved and the answer did not — the player is shown a new question " +
                "and graded against the old one",
        )
    }

    /**
     * **The same scan, replayed, draws the same instance.** The complement of the test above, and
     * the reason the draw hangs off the minted id rather than off a clock or a counter of nothing.
     *
     * A build that re-drew from a fresh random source would pass every re-draw assertion and fail
     * this one — and would take the only debugging instrument eight phones in a dark house have
     * and make it lie.
     */
    @Test
    fun `the same scan replayed draws the same instance`() {
        val state = armed()
        val seat = Seat(0)
        val card = cardFor(state, seat)
        val a = assertNotNull(scan(state, seat, card).state.openSubroutineFor(seat))
        val b = assertNotNull(scan(state, seat, card).state.openSubroutineFor(seat))
        assertEquals(a.parameters, b.parameters)
        assertEquals(a.expected, b.expected)
    }

    /**
     * **The question does not read `Role`** (D-109).
     *
     * The differential harness's premise stated locally. Two rounds, identical but for who was
     * drawn, and the same seat scanning the same card is asked the same thing — if it were not,
     * the game's central secret would be sitting in the answer key.
     */
    @Test
    fun `the question the scan draws does not move with the role`() {
        fun round(insiders: List<Seat>) = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), 20260821L, SEATS, insiders, markers = MARKERS),
        ).state

        val none = round(emptyList())
        val some = round(listOf(Seat(0), Seat(3)))
        for (seat in SEATS) {
            val a = scan(none, seat, cardFor(none, seat)).state.openSubroutineFor(seat)
            val b = scan(some, seat, cardFor(some, seat)).state.openSubroutineFor(seat)
            assertEquals(a?.parameters, b?.parameters, "seat ${seat.index}'s question moved")
            assertEquals(a?.expected, b?.expected, "seat ${seat.index}'s answer moved")
        }
    }

    /**
     * **D-110's re-arm path, end to end: REJECTED, walk back, scan, and it is a live question
     * again** — answered correctly this time, and it banks.
     *
     * The answer has to be read *after* the re-scan, because it did not exist before it. That is
     * the fixture stating the ruling: there is no RETRY button and there could not be one, since
     * the phone has nothing to retry against until the house has asked again.
     */
    @Test
    fun `a rejected entry re-arms on a re-scan and the second attempt banks`() {
        val state = armed()
        val seat = Seat(0)
        val card = cardFor(state, seat)

        val opened = scan(state, seat, card).state
        val rejected = reduce(opened, Event.SubroutineReturned(Tick(2), seat, card, listOf(9, 9)))
        assertFalse(
            rejected.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted,
        )
        assertFalse(rejected.state.openSubroutineFor(seat)!!.armed, "a returned entry is spent")
        assertEquals(state.systemIntegrity, rejected.state.systemIntegrity)

        val rearmed = scan(rejected.state, seat, card, at = 3L).state
        assertTrue(rearmed.openSubroutineFor(seat)!!.armed, "the re-scan did not re-arm")
        val accepted = reduce(
            rearmed,
            Event.SubroutineReturned(Tick(4), seat, card, rearmed.openSubroutineFor(seat)!!.expected),
        )
        assertTrue(accepted.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted)
        assertEquals(
            state.systemIntegrity - 1, accepted.state.systemIntegrity,
            "the second, correct attempt did not bank",
        )
    }
}

/**
 * **A seat outside the system scans nothing open** — the hygiene gate, and the two independent
 * denials behind it.
 */
class RevokedSeatScanTest {

    /**
     * The state gate. A Revoked seat's scan of its own anchor opens no work and arms nothing.
     *
     * **The shape does not move**, which is what keeps this out of rule 1's forbidden territory:
     * the same two effects come back with the same fields, and the difference is the null that
     * NOTHING FOR YOU HERE already uses. Round-state is publicly observable (D-068) — everybody in
     * the room knows who is out.
     */
    @Test
    fun `a Revoked seat's scan opens nothing`() {
        val state = armed()
        val seat = Seat(0)
        val card = cardFor(state, seat)
        val out = state.copy(revoked = listOf(seat))

        val living = scan(state, seat, card)
        val revoked = scan(out, seat, card)
        assertNotNull(answerOf(living.effects).opened, "the fixture's live seat found no work")
        assertNull(answerOf(revoked.effects).opened, "a Revoked seat's scan opened work")
        assertNull(revoked.state.openSubroutineFor(seat), "a Revoked seat armed a Subroutine")
        assertEquals(
            living.effects.map { it::class.simpleName }, revoked.effects.map { it::class.simpleName },
            "a Revoked seat's scan came back a different shape",
        )
    }

    /** A Restrained seat is out by its own list, and the gate does not care which list. */
    @Test
    fun `a Restrained seat's scan opens nothing either`() {
        val state = armed()
        val seat = Seat(0)
        val held = state.copy(restrained = listOf(seat))
        assertNull(answerOf(scan(held, seat, cardFor(state, seat)).effects).opened)
        assertTrue(held.revoked.isEmpty(), "a Restrain was stored as a Revoke")
    }

    /**
     * The second denial, independent of the first: the allowlist declines the kind to an out class
     * whatever the rules did. Written out so that removing the state gate leaves the leak closed,
     * and removing the row leaves it closed too.
     */
    @Test
    fun `the answer to an out seat is declined at the boundary as well`() {
        val out = armed().copy(revoked = listOf(Seat(0)))
        assertEquals(
            emptyList(),
            home.someoneshome.model.EmitSchema.deliveries(
                Effect.ScanAnswered(Seat(0), opened = null), out,
            ),
        )
    }
}

/** **What a piece of paper turns into** (D-121, D-124, D-071, D-072). */
class ScanRoutingTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    private fun home(): HouseMap {
        var map = HouseMap.EMPTY
        for ((shape, id) in listOf("diamond" to "AAAAAAA", "ring" to "BBBBBBB")) {
            map = (map.register(card(shape, id), Room("HALL")) as RegisterResult.Registered).map
        }
        map = (map.register(card(MarkerShapes.TERMINAL.id, "TTTTTTT"), Room("STUDY")) as RegisterResult.Registered).map
        return (map.register(card(MarkerShapes.MEETING.id, "MMMMMMM"), Room("KITCHEN")) as RegisterResult.Registered).map
    }

    /**
     * **The meeting card is never work** (D-121). *A meeting is called by standing at this card.*
     *
     * Routed through the work plane it would resolve against a work order that cannot contain it —
     * the reserved shapes are not among the round's markers — and answer NOTHING FOR YOU HERE. The
     * party would never be called, and the failure would present as *the meeting card is broken*.
     */
    @Test
    fun `the meeting card calls a meeting and never opens work`() {
        val routing = routeScan(Tick(5), Seat(2), card(MarkerShapes.MEETING.id, "MMMMMMM"), home())
        val called = assertIs<ScanRouting.Meeting>(routing)
        assertEquals(Seat(2), called.event.caller)
        assertEquals(MeetingTrigger.MeetingCard, called.event.trigger)
        assertEquals(Tick(5), called.event.at)
    }

    /** An ordinary registered card is work, and the rules decide whether there is any. */
    @Test
    fun `an ordinary registered card routes to the work plane`() {
        val routing = routeScan(Tick(1), Seat(0), card("diamond", "AAAAAAA"), home())
        val work = assertIs<ScanRouting.Work>(routing)
        assertEquals(MarkerId("AAAAAAA"), work.event.marker)
    }

    /**
     * **The Terminal routes to work too, and answers NOTHING FOR YOU HERE until it is built.**
     *
     * It is a registered card, so D-124's registered vocabulary is the honest one for it. Routing
     * it to the unregistered alert would tell a player that the T card in their hand is not part
     * of the home, which is false and is the one thing that refusal is allowed to be specific
     * about.
     */
    @Test
    fun `the terminal is a registered card and is answered as one`() {
        val routing = routeScan(Tick(1), Seat(0), card(MarkerShapes.TERMINAL.id, "TTTTTTT"), home())
        assertIs<ScanRouting.Work>(routing)

        val state = armed()
        assertNull(
            answerOf(scan(state, Seat(0), MarkerId("TTTTTTT")).effects).opened,
            "the Terminal opened work",
        )
    }

    /**
     * **Unregistered paper never reaches the rules, and is reported to nobody** (D-071, D-072).
     *
     * The absence of an event is the decision here rather than an omission: a count at the end of
     * the round or a notice at the next meeting would turn the app into an arbiter of a player's
     * claim, letting the room verify testimony.
     */
    @Test
    fun `unregistered paper produces no event at all`() {
        val routing = routeScan(Tick(1), Seat(0), card("cross", "ZZZZZZZ"), home())
        assertEquals(ScanRouting.Unregistered, routing)
    }

    /**
     * A card registered to one home is unregistered in another, and the routing says so.
     *
     * The complement, so this cannot pass on a build that calls everything unregistered.
     */
    @Test
    fun `an empty home registers nothing and routes everything to the alert`() {
        assertEquals(
            ScanRouting.Unregistered,
            routeScan(Tick(1), Seat(0), card("diamond", "AAAAAAA"), HouseMap.EMPTY),
        )
        assertEquals(
            ScanRouting.Unregistered,
            routeScan(Tick(1), Seat(0), card(MarkerShapes.MEETING.id, "MMMMMMM"), HouseMap.EMPTY),
            "a meeting card the host never registered called a meeting in a house with no " +
                "meeting place",
        )
    }
}
