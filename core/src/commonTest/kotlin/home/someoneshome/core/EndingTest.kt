package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Outcome
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **How a round ends, as things that can fail** (D-131, D-153, D-157).
 *
 * Six seats, seats 1 and 4 the Insiders — two rather than one, because half of what is being
 * asserted is about *counting* them: parity reads living Insiders on one side, and the reveal has
 * to name both and only both.
 *
 * ### Every assertion here is about a boundary or an absence
 *
 * The four routes are each one comparison, and a comparison is exactly the kind of rule that ships
 * wrong by one and looks right for a whole evening. So parity is asserted at the seat before it as
 * well as at the seat it fires on; the meter is asserted at one remaining as well as at zero; and
 * the two rules written as absences — *a running Egress outlives its Insiders*, *the house never
 * uses a Resident's line* — are asserted rather than trusted, because nothing about either of them
 * is visible when it breaks.
 */
private val SEATS = (0 until 6).map { Seat(it) }
private val INSIDERS = listOf(Seat(1), Seat(4))

/**
 * A line per seat, each one identifiable on sight.
 *
 * The text names its own seat so that a leak is legible in a failure message rather than being a
 * string that has to be traced back to an owner — `line-3` appearing in the ending effects of a
 * round where seat 3 is a Resident is the whole of the report.
 */
private val LINES = SEATS.map { OneLineHeld(it, "line-${it.index}") }

/**
 * What each seat asked to be called (D-115).
 *
 * Distinguishable from the lines on sight — `NAME-1` against `line-1` — so a reveal that put the
 * wrong text in the wrong field fails on the value rather than on a shape that happens to match.
 */
private val NAMES = SEATS.map { SeatNamed(it, "NAME-${it.index}") }

/** Eight ordinary markers, which is D-127's floor and enough for the house to draw work from. */
private val ENDING_MARKERS = (0 until 8).map { MarkerId("e$it") }

private fun armed(
    seats: List<Seat> = SEATS,
    insiders: List<Seat> = INSIDERS,
    lines: List<OneLineHeld> = LINES,
): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(
        Tick(0), seed = 20260822L, seats = seats, insiders = insiders,
        markers = placed(ENDING_MARKERS), names = NAMES, oneLines = lines,
    ),
).state

/**
 * Every field of an effect as text, for a sweep that must not be able to miss one.
 *
 * **`toString()` on purpose, which is the one place in this project it is the right tool.** The
 * recording refuses a generated `toString` because it has to outlive the build that wrote it; this
 * is a search, run in the same process that produced the value, and what it needs is exactly what
 * a generated `toString` gives — **every** field, including the one somebody adds tomorrow. A
 * hand-written renderer here would go stale the moment an effect grew a member, and going stale is
 * the failure mode a completeness check exists to prevent.
 */
private fun Effect.textOf(): String = toString()

/** Drive events through the **admission gate**, never through [reduce] — the gate is half of it. */
private class EndingWalk(start: GameState) {
    var state: GameState = start
        private set
    val emitted = mutableListOf<Effect>()
    val refusals = mutableListOf<RefusalReason>()

    fun feed(vararg events: Event): EndingWalk {
        for (event in events) {
            when (val admission = admit(state, event)) {
                is Admission.Admitted -> {
                    state = admission.reduction.state
                    emitted += admission.reduction.effects
                }
                is Admission.Refused -> refusals += admission.reason
            }
        }
        return this
    }

    fun drain(): List<Effect> = emitted.toList().also { emitted.clear() }
}

/**
 * Restrain [seat] the way the room does it: a meeting, a unanimous ballot, and the halfway mark.
 *
 * Driven through the real lifecycle rather than by writing into `restrained`, because the whole
 * question is whether the win check is consulted **on the transition the room actually makes** —
 * a test that set the list by hand would pass against a build in which nothing ever looked.
 */
private fun EndingWalk.restrain(seat: Seat, from: Long): EndingWalk {
    val voters = state.livingSeats.filterNot { it.index == seat.index }
    feed(Event.MeetingCalled(Tick(from), voters.first(), MeetingTrigger.MeetingCard))
    // Every seat, including the ones already out: D-104's gate is ONE gate and does not close a
    // player short. The last check-in is what opens the talk, so nothing closes the CheckIn phase
    // by hand.
    state.seats.forEach { feed(Event.MeetingCheckedIn(Tick(from + 1), it)) }
    feed(Event.DiscussionClosed(Tick(from + 3)))
    state.livingSeats.forEach {
        feed(Event.VoteSelected(Tick(from + 4), it, seat), Event.VoteLocked(Tick(from + 5), it))
    }
    feed(Event.VoteWindowClosed(Tick(from + 6)), Event.TallyHalfwayReached(Tick(from + 7)))
    feed(Event.MeetingClosed(Tick(from + 8)))
    return this
}

class EndingTest {

    // ---- (a) the meter -------------------------------------------------------------------------

    /**
     * **The meter reaching nothing ends the round on the spot, between meetings** (`gdd.md:203`).
     *
     * *Residents win immediately — no meeting required.* This is the one exception to the meter
     * being batched and frozen (`gdd.md:1002`), and it is asserted at both ends of the boundary:
     * with one still standing the round is **running**, and the return that takes it to zero ends
     * it in the same reduction that banks it. A build that waited for a meeting passes the second
     * half of this and fails the first.
     */
    @Test
    fun `the meter reaching zero ends the round where the player is standing`() {
        val nearly = armed().copy(systemIntegrity = 1, meeting = null)
        assertNull(outcomeOf(nearly, Event.MeetingClosed(Tick(1))), "one left and the round was over")

        val cleared = nearly.copy(systemIntegrity = 0)
        assertEquals(
            Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared),
            outcomeOf(cleared, Event.MeetingClosed(Tick(1))),
            "the meter reached nothing and the round carried on",
        )
    }

    /**
     * The same thing through the rules rather than through the predicate: a plain Resident hands
     * over the last accepted entry and the round is over before their phone has finished buzzing.
     *
     * The state is built one entry short and the last one is banked for real, so what is being
     * exercised is `returned`'s own arithmetic reaching zero — not a hand-written zero.
     */
    @Test
    fun `the last accepted entry ends the round in the reduction that banks it`() {
        val start = armed().copy(systemIntegrity = 1)
        val worker = Seat(0)
        assertEquals(Role.Resident, start.roleOf(worker), "the fixture is stale")

        val open = start.workOrderFor(worker)?.entries?.first()
        assertTrue(open != null, "the fixture armed no work")

        val walk = EndingWalk(start).feed(Event.MarkerScanned(Tick(10), worker, open.anchor!!))
        // The ANSWER, read off authority state. The client-facing instance the scan came back with
        // carries the question and is physically incapable of carrying this — which is the whole
        // Subroutine treatment, and is why a test has to reach past it to hand over a right one.
        val answer = walk.state.openSubroutineFor(worker)?.expected
        assertTrue(!answer.isNullOrEmpty(), "the scan opened nothing; the rest of this test proves nothing")

        walk.feed(Event.SubroutineReturned(Tick(11), worker, open.anchor!!, answer))
        assertTrue(
            walk.emitted.any { it is Effect.SubroutineGraded && it.accepted },
            "the entry was rejected; nothing was banked and the rest of this test proves nothing",
        )
        assertEquals(0, walk.state.systemIntegrity)
        assertEquals(
            Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared), walk.state.outcome,
            "the meter cleared and the round did not end",
        )
    }

    // ---- (c) parity ----------------------------------------------------------------------------

    /**
     * **Parity fires exactly at the boundary, and not one seat early** (D-131).
     *
     * Six seats, four plain Residents, two Insiders. Losing one plain Resident leaves `3 > 2` and
     * the round runs; losing the second leaves `2 <= 2` and the Insiders take it. The first
     * assertion is the one that matters — a `<` written where `<=` belongs passes the second and
     * a `<=` written where `<` belongs fails the first, so neither alone says anything.
     *
     * **Driven through real meetings**, so what is exercised is the win check being consulted at
     * the moment the room actually removes somebody.
     */
    @Test
    fun `parity fires at the boundary and not one seat early`() {
        val walk = EndingWalk(armed())
        assertEquals(4, walk.state.livingPlainResidents.size, "the fixture is stale")

        walk.restrain(Seat(0), from = 100)
        assertEquals(3, walk.state.livingPlainResidents.size)
        assertNull(
            walk.state.outcome,
            "three plain Residents against two Insiders is not parity, and the round ended",
        )

        walk.restrain(Seat(2), from = 200)
        assertEquals(2, walk.state.livingPlainResidents.size)
        assertEquals(
            Outcome(Winner.Insiders, WinRoute.Parity), walk.state.outcome,
            "living plain Residents fell to the number of living Insiders and the round ran on",
        )
    }

    /**
     * **The win check counts LIVING Insiders, so an Insider revoking an Insider moves the
     * goalposts against themselves** (`gdd.md:213`).
     *
     * The same six seats, one Insider gone — and now parity is `2 <= 1`, which the state above
     * reached at three plain Residents and this one does not. *Do not tell them* is a property of
     * the effect stream and is asserted elsewhere; this asserts that the arithmetic really did
     * move.
     */
    @Test
    fun `parity counts living Insiders and not the draw`() {
        val short = armed().copy(restrained = listOf(Seat(4)))
        assertEquals(1, short.livingInsiders.size)

        val threeLeft = short.copy(restrained = listOf(Seat(4), Seat(0)))
        assertEquals(3, threeLeft.livingPlainResidents.size)
        assertNull(
            outcomeOf(threeLeft, Event.MeetingClosed(Tick(1))),
            "three plain Residents against ONE Insider is not parity",
        )

        val twoLeft = short.copy(restrained = listOf(Seat(4), Seat(0), Seat(2)))
        assertNull(
            outcomeOf(twoLeft, Event.MeetingClosed(Tick(1))),
            "two plain Residents against one Insider is still not parity — the goalposts moved",
        )
    }

    // ---- (b) the Insiders run out --------------------------------------------------------------

    /**
     * **Restraining every Insider ends the round for the Residents** (D-131).
     *
     * With both Insiders held, nothing is left for the room to do — and the check fires on the
     * transition that removes the second, not at the next meeting.
     */
    @Test
    fun `the round ends when the room runs out of Insiders`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100)
        assertNull(walk.state.outcome, "one Insider left and the round was already over")

        walk.restrain(Seat(4), from = 200)
        assertEquals(
            Outcome(Winner.Residents, WinRoute.InsidersRestrained), walk.state.outcome,
            "no Insider was left in the round and it carried on",
        )
    }

    /**
     * **A round drawn with no Insider at all is not a round the Residents have won.**
     *
     * The clause is a guard against arithmetic rather than against a game: `InsiderBand` makes an
     * Insider-less round unreachable in play, and without the guard every hand-built state that
     * arms `insiders = emptyList()` to exercise something else would end itself before the rule
     * under test ran.
     */
    @Test
    fun `a round with no Insiders drawn does not end itself`() {
        val nobody = armed(insiders = emptyList())
        assertNull(
            outcomeOf(nobody, Event.MeetingClosed(Tick(1))),
            "a round nobody armed properly reported itself as won",
        )
    }

    // ---- The ending effects --------------------------------------------------------------------

    /**
     * **The ending reaches every seat, once each, with one Short haptic** (D-135, D-156).
     *
     * Per seat rather than broadcast, exactly as the opening message and the Egress alert are; and
     * Short, because D-135's long haptic is closed at five and the round ending is not one of
     * them. The buzz count is asserted as *one per seat over the whole ending*, which is D-156
     * read the way F-003 found it broken: the house has three things to say here and the room
     * feels one of them.
     */
    @Test
    fun `the ending reaches every seat once and buzzes once`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        val ended = walk.emitted.filterIsInstance<Effect.RoundEnded>()

        assertEquals(
            SEATS.map {
                Effect.RoundEnded(
                    it, Winner.Residents, WinRoute.InsidersRestrained, Haptic.Short,
                    integrity = Balance.integrityPercent(walk.state.systemIntegrity, SEATS.size),
                )
            },
            ended,
            "the ending did not reach every seat once, in seat order, identically",
        )
        // The push first, because it is what moves every screen; the reveal and the sign-off are
        // content landing on the screen it moved them to. Asserted as an ordering rather than as a
        // set, because an ending whose reveal arrived before the ending would be a phone drawing
        // the answer over the round it was still playing.
        val ending = walk.emitted.filter {
            it is Effect.RoundEnded || it is Effect.InsidersRevealed || it is Effect.HouseSignedOff
        }
        assertTrue(
            ending.takeWhile { it is Effect.RoundEnded }.size == SEATS.size,
            "the reveal or the sign-off was emitted before the ending push: $ending",
        )
    }

    /**
     * **THE COMPLETENESS CHECK: a name crosses the boundary at the ending and nowhere before it.**
     *
     * D-115 put names on the wire in the **lobby**, where six people standing in a hall can already
     * see each other. What the reveal does is pair a name with an **alignment**, and that pairing is
     * the one disclosure this app makes all evening — so the guarantee worth holding is not *no
     * effect carries a name* but *nothing carries one until the round is over*.
     *
     * Swept over a whole round rather than asserted about one effect: every effect the rules emit
     * from arming to the ending is rendered and searched for the fixture's own name text. A build
     * that put a name on a work order, a verdict, a walk-in or a meeting result fails here, and it
     * fails on the string it leaked.
     */
    @Test
    fun `no effect carries a name until the round ends`() {
        val walk = EndingWalk(armed())
        // A shift's worth of ordinary traffic first: scans, a hand-over, and a whole meeting.
        val seat = Seat(0)
        val order = walk.state.workOrderFor(seat)!!
        val entry = order.entries.first { order.isActionable(it) }
        walk.feed(
            Event.MarkerScanned(Tick(10), seat, entry.anchor!!),
            Event.SubroutineReturned(Tick(11), seat, entry.anchor!!, listOf(9, 9, 9)),
        )
        walk.restrain(Seat(1), from = 100)

        val before = walk.drain().map { it.textOf() }
        assertTrue(before.isNotEmpty(), "nothing was emitted, so this sweep looked at nothing")
        for (named in NAMES) {
            // The offending line is found FIRST rather than inside the message: `assertTrue`
            // evaluates its message eagerly, so a `first {}` in there throws on the passing path
            // and the test reports a NoSuchElementException instead of the leak it is watching for.
            val leaked = before.firstOrNull { named.name in it }
            assertTrue(
                leaked == null,
                "'${named.name}' reached a client before the round ended, on: $leaked",
            )
        }

        // And then the ending, where exactly one effect is allowed to carry them.
        walk.restrain(Seat(4), from = 200)
        val after = walk.drain()
        val reveal = after.filterIsInstance<Effect.InsidersRevealed>().single()
        assertEquals(
            listOf("NAME-1", "NAME-4"), reveal.insiders.map { it.name },
            "the reveal did not name the Insiders it revealed",
        )
        assertTrue(
            after.filterNot { it is Effect.InsidersRevealed }
                .none { effect -> NAMES.any { it.name in effect.textOf() } },
            "a second effect at the ending carries a name; the reveal is the one that may",
        )
    }

    /**
     * **The desk is empty of names the instant the round ends** (D-115), exactly as it is of lines.
     *
     * Both are round-scoped text and both are dropped by the transition that publishes them, so a
     * state in which the round is over is physically incapable of saying who anybody was.
     */
    @Test
    fun `the round ending drops every name the house was holding`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        assertTrue(walk.state.ended, "the fixture did not end a round, so nothing was wiped")
        assertEquals(
            emptyList(), walk.state.names,
            "the round is over and the house is still holding what to call everybody",
        )
        assertEquals(emptyList(), walk.state.oneLines)
    }

    /**
     * **The reveal names both Insiders and publishes what was held over them** (`gdd.md:1063`).
     *
     * Every Insider is named, **living or not** — the reveal is about who was working for the
     * house, and somebody Restrained at the first meeting was working for it just as much as
     * somebody still walking around at the end. Here both were Restrained, which is the sharpest
     * form of that: a build that named the survivors would name nobody at all.
     */
    @Test
    fun `the reveal names every Insider living or not with their line`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        val revealed = walk.emitted.filterIsInstance<Effect.InsidersRevealed>()

        assertEquals(1, revealed.size, "the reveal is one disclosure and must be emitted once")
        assertEquals(
            listOf(
                InsiderNamed(Seat(1), "NAME-1", "line-1"),
                InsiderNamed(Seat(4), "NAME-4", "line-4"),
            ),
            revealed.single().insiders,
            "the reveal did not name both Insiders with their own lines, in seat order",
        )
    }

    /**
     * **THE INJECTION: the blackmail publish carries only Insiders' lines.**
     *
     * *The house never uses a Resident's* (D-116). This walks every line the ending emitted and
     * asserts that not one of them belongs to a seat that was only ever a Resident — the lines are
     * seat-labelled precisely so that a leak names its own owner in the failure message.
     *
     * The rules build the list by mapping `insiderSeats`, so a Resident's line is not *filtered
     * out* of it; it was never in it. That is the distinction this test exists to keep: a filter
     * is a thing somebody removes while tidying, and this fails the moment the list is built from
     * anything wider.
     */
    @Test
    fun `the publish carries no Resident's line`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        val published = walk.emitted.filterIsInstance<Effect.InsidersRevealed>().flatMap { it.insiders }
        assertTrue(published.isNotEmpty(), "nothing was published; the rest of this test proves nothing")

        val residents = SEATS.filter { walk.state.roleOf(it) == Role.Resident }
        for (seat in residents) {
            assertTrue(
                published.none { it.line == "line-${seat.index}" },
                "seat ${seat.index} is a Resident and the house published their line: $published",
            )
            assertTrue(
                published.none { it.seat.index == seat.index },
                "seat ${seat.index} is a Resident and the reveal named them: $published",
            )
        }
    }

    /**
     * **The house speaks last, only to the Insiders it owned, in both endings** (`gdd.md:1051`).
     *
     * Two sign-offs on a Resident win and the words are *Unfortunate.* — flat, incurious, and
     * exactly as bothered as it was when it won. Addressed to the Insiders and to nobody else;
     * that it is *permitted* to nobody else is the allowlist's, and asserted there.
     */
    @Test
    fun `the house signs off to the Insiders and to nobody else`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        val signed = walk.emitted.filterIsInstance<Effect.HouseSignedOff>()

        assertEquals(INSIDERS, signed.map { it.seat }, "the sign-off did not go to exactly the Insiders")
        assertEquals(listOf("Unfortunate.", "Unfortunate."), signed.map { it.body })
    }

    /**
     * **THE INJECTION: the lines are wiped from the desk, and the publish is what wipes them.**
     *
     * D-116's promise is *deleted when the round ends*, and the publish **is** the round ending. So
     * two things are asserted together: the reveal really did carry the text, and the state it left
     * behind is physically incapable of quoting anybody. A build that wiped first passes the second
     * and publishes blanks; a build that never wiped passes the first and keeps six confessions in
     * authority state for the rest of the evening.
     */
    @Test
    fun `the desk is empty the instant the round ends`() {
        val walk = EndingWalk(armed())
        assertEquals(6, walk.state.oneLines.size, "the fixture handed nothing over")

        walk.restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        val published = walk.emitted.filterIsInstance<Effect.InsidersRevealed>().single()

        assertEquals(
            listOf("line-1", "line-4"), published.insiders.map { it.line },
            "the wipe ran before the publish and the house quoted nobody",
        )
        assertEquals(
            emptyList(), walk.state.oneLines,
            "the round is over and the house is still holding everybody's line",
        )
        assertNull(walk.state.lineOf(Seat(1)), "a line survived the round it was handed over for")
    }

    // ---- Once, and then nothing ----------------------------------------------------------------

    /**
     * **A round cannot end twice, and nothing gets in afterwards.**
     *
     * The gate's `RoundAlreadyEnded` is what refuses the events; the predicate's own first clause
     * is what stops a second ending being appended to a reduction that already had one. Both are
     * checked, because they fail differently: without the gate the couch would go on receiving a
     * meeting, and without the clause every subsequent transition would re-publish the reveal.
     */
    @Test
    fun `nothing reaches a round that is already over`() {
        val walk = EndingWalk(armed()).restrain(Seat(1), from = 100).restrain(Seat(4), from = 200)
        walk.drain()
        // The helper's own last event -- LIGHTS OUT, on a meeting whose halfway mark ended the
        // round -- is already one of these. Counted from here, so what is asserted is what THIS
        // event met rather than everything the fixture happened to walk through.
        val before = walk.refusals.size

        walk.feed(Event.MeetingCalled(Tick(300), Seat(0), MeetingTrigger.MeetingCard))
        assertEquals(
            listOf(RefusalReason.RoundAlreadyEnded), walk.refusals.drop(before),
            "a meeting was called in a round that is over",
        )
        assertEquals(emptyList(), walk.drain(), "an event was reduced after the round ended")

        assertNull(
            outcomeOf(walk.state, Event.MeetingClosed(Tick(301))),
            "the predicate would end an already-ended round a second time",
        )
    }

    /**
     * **A round that never started cannot end** — the empty state satisfies the meter clause by
     * arithmetic and must not answer with it.
     */
    @Test
    fun `an unarmed state is not a won round`() {
        assertEquals(0, GameState.EMPTY.systemIntegrity, "the premise of this test has moved")
        assertNull(
            outcomeOf(GameState.EMPTY, Event.MeetingClosed(Tick(1))),
            "a state with no round in it reported a winner",
        )
        assertFalse(GameState.EMPTY.ended)
    }

    /**
     * **GUARD (decision pack 9b option a, D-284): the ending always carries the final System
     * Integrity, one value to every seat.** Three seats, one Insider, and the first Revoke ends the
     * round by parity before any meeting has told anybody the meter, which is the round that drew
     * SYSTEM INTEGRITY "–" on every ending.
     */
    @Test
    fun `the ending carries the final System Integrity to every seat with no meeting behind it`() {
        val three = (0 until 3).map { Seat(it) }
        val armed = reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 3L, seats = three, insiders = listOf(Seat(1)))).state
        val half = Balance.meterTotal(three.size) / 2
        val start = armed.copy(systemIntegrity = half)
        val expected = Balance.integrityPercent(half, three.size)
        assertTrue(expected in 1..99, "the fixture's meter is at an end, so a constant could pass: $expected")

        val ready = Balance.REVOKE_COOLDOWN / 2
        val walk = EndingWalk(start).feed(
            Event.RevokeArmed(Tick(ready), Seat(1)),
            Event.ContactMade(Tick(ready + 1), Seat(1), Seat(2)),
        )
        assertEquals(Outcome(Winner.Insiders, WinRoute.Parity), walk.state.outcome, "the fixture did not end by parity")
        val ended = walk.emitted.filterIsInstance<Effect.RoundEnded>()
        assertEquals(three, ended.map { it.seat }, "the ending did not reach every seat")
        assertEquals(
            List(three.size) { expected }, ended.map { it.integrity },
            "the ending did not carry the final System Integrity, the same to every seat",
        )
    }
}
