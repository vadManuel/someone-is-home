package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The meeting lifecycle, as things that can fail.**
 *
 * Six seats, because the design's own meeting screens are drawn with six and because five is the
 * minimum party (D-128). Seat 1 is the Insider throughout, so the seat index under test is never
 * also the role under test.
 *
 * Everything the house does at a meeting depends on **every** phone in the house — the gate closes
 * when the last player walks in, the talk ends on a unanimous ready, the ballot is read when the
 * window shuts. A phone cannot count phones, so each of those is asserted here as a push the rules
 * make, and `ui`'s side of the same line is asserted in `FlowTest` against the same table.
 */
private val SEATS = (0 until 6).map { Seat(it) }
private val INSIDERS = listOf(Seat(1))

private fun armed(): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed = 20260821L, seats = SEATS, insiders = INSIDERS),
).state

/**
 * Drive a list of events through the **admission gate**, not through [reduce].
 *
 * Through the gate, because half the meeting's rules are refusals and a helper that called the
 * rules directly would exercise a path no client can reach — the same reason the harness's driver
 * goes through [admit].
 */
private class Walk(start: GameState) {
    var state: GameState = start
        private set
    val emitted = mutableListOf<Effect>()
    val refusals = mutableListOf<RefusalReason>()

    fun feed(vararg events: Event): Walk {
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

    /** Everything emitted since this was last called. The way each step is read on its own. */
    fun drain(): List<Effect> = emitted.toList().also { emitted.clear() }
}

private var tick = 0L
private fun t() = Tick(tick++)

/** A meeting called from the card, with everybody walked in: the state the talk starts from. */
private fun talking(from: GameState = armed(), caller: Seat = Seat(0)): Walk {
    val walk = Walk(from)
    walk.feed(Event.MeetingCalled(t(), caller, MeetingTrigger.MeetingCard))
    // The caller's scan IS their check-in (D-121), so they are not fed one.
    from.seats.filterNot { it.index == caller.index }
        .forEach { walk.feed(Event.MeetingCheckedIn(t(), it)) }
    walk.drain()
    return walk
}

/** A meeting with the ballot open. */
private fun voting(from: GameState = armed(), caller: Seat = Seat(0)): Walk {
    val walk = talking(from, caller)
    walk.feed(Event.DiscussionClosed(t()))
    walk.drain()
    return walk
}

class MeetingTest {

    // ---- Calling ---------------------------------------------------------------------------

    /**
     * **D-121: the caller's scan is their check-in**, and the gate needs nothing new for them.
     *
     * If it did not count them, the gate would be waiting on a player who is standing at the card
     * it was called from — a meeting that can never start, called by the person it is waiting for.
     */
    @Test
    fun `the caller is counted the instant the meeting exists`() {
        val walk = Walk(armed()).feed(Event.MeetingCalled(t(), Seat(4), MeetingTrigger.MeetingCard))
        val counted = walk.drain().filterIsInstance<Effect.CheckInProgressed>().single()
        assertEquals(1, counted.present)
        assertEquals(6, counted.expected)
        assertTrue(walk.state.meeting!!.hasCheckedIn(Seat(4)))
    }

    /**
     * **D-134: the ring is for the living; a player who is out is told to stand and walk in.**
     *
     * And D-135's long haptic is on the round-state axis: reserved for a **newly** Revoked player,
     * ordinary for the couch. Both halves are asserted, because a build that sent every out player
     * the long buzz would look right on one phone and wrong in a room.
     */
    @Test
    fun `the living are rung and the out are told to stand up`() {
        // Seat 3 revoked at an earlier meeting, seat 5 revoked since it closed.
        val earlier = Walk(armed()).feed(
            Event.RevokeArmed(t(), Seat(1)),
            Event.ContactMade(t(), Seat(1), Seat(3)),
            Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard),
        )
        SEATS.drop(1).forEach { earlier.feed(Event.MeetingCheckedIn(t(), it)) }
        earlier.feed(
            Event.DiscussionClosed(t()),
            Event.VoteWindowClosed(t()),
            Event.TallyHalfwayReached(t()),
            Event.MeetingClosed(t()),
            Event.RevokeArmed(t(), Seat(1)),
            Event.ContactMade(t(), Seat(1), Seat(5)),
        )
        earlier.drain()

        earlier.feed(Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard))
        val effects = earlier.drain()

        val ring = effects.filterIsInstance<Effect.MeetingOpened>().single()
        assertEquals(Haptic.Long, ring.haptic, "an incoming call is one of D-135's five")

        val walkIns = effects.filterIsInstance<Effect.StandAndWalkIn>()
        assertEquals(listOf(3, 5), walkIns.map { it.seat.index }, "the out, in seat order")
        assertEquals(
            Haptic.Short, walkIns.single { it.seat.index == 3 }.haptic,
            "the couch is called in with an ordinary buzz",
        )
        assertEquals(
            Haptic.Long, walkIns.single { it.seat.index == 5 }.haptic,
            "a NEWLY Revoked player's STAND AND WALK IN is D-135's third long haptic",
        )
    }

    /**
     * **D-133: the meeting card is inert during an Egress, and the Revoke report is not.**
     *
     * Both directions, because either one alone passes on a build that refuses everything or on a
     * build that refuses nothing.
     */
    @Test
    fun `an Egress makes the card inert and leaves the report alone`() {
        val burning = armed().withEgress(true)

        val card = Walk(burning).feed(Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard))
        assertEquals(listOf(RefusalReason.EgressRunning), card.refusals)
        assertNull(card.state.meeting, "a meeting started while the house was on fire")

        val report = Walk(burning).feed(
            Event.MeetingCalled(t(), Seat(0), MeetingTrigger.RevokeReported(Seat(3))),
        )
        assertEquals(emptyList(), report.refusals, "D-121's one exception was refused")
        assertTrue(report.state.meeting != null)
    }

    /** A second call would reset the meeting everybody is already standing at. */
    @Test
    fun `a meeting cannot be called inside a meeting`() {
        val walk = talking()
        walk.feed(Event.MeetingCalled(t(), Seat(2), MeetingTrigger.MeetingCard))
        assertEquals(listOf(RefusalReason.MeetingAlreadyRunning), walk.refusals)
        assertEquals(MeetingPhase.Discussion, walk.state.meeting!!.phase)
    }

    // ---- The check-in gate (D-104) -----------------------------------------------------------

    /**
     * **THE GATE DOES NOT CLOSE A PLAYER SHORT.**
     *
     * *The talk does not start until every living player and every out player has checked in.*
     * Presence over proceed-without, with the stall accepted and named. Five of six is five of
     * six, and the sixth is still walking.
     */
    @Test
    fun `the gate will not close a player short`() {
        val walk = Walk(armed())
        walk.feed(Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard))
        SEATS.drop(1).dropLast(1).forEach { walk.feed(Event.MeetingCheckedIn(t(), it)) }

        assertEquals(MeetingPhase.CheckIn, walk.state.meeting!!.phase)
        assertEquals(
            emptyList(), walk.drain().filterIsInstance<Effect.MeetingPhaseOpened>(),
            "the talk started with somebody still walking",
        )

        walk.feed(Event.MeetingCheckedIn(t(), SEATS.last()))
        assertEquals(
            MeetingPhase.Discussion,
            walk.state.meeting!!.phase,
            "the last player walked in and the gate stayed shut",
        )
    }

    /**
     * **The out are half of D-104's gate**, which is the whole reason it is one gate and not two.
     *
     * A Revoked player who has not walked in yet holds the meeting exactly as a living one does.
     */
    @Test
    fun `a player who is out holds the gate open like anybody else`() {
        val start = Walk(armed())
            .feed(Event.RevokeArmed(t(), Seat(1)), Event.ContactMade(t(), Seat(1), Seat(4)))
            .state

        val walk = Walk(start)
        walk.feed(Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard))
        SEATS.drop(1).filterNot { it.index == 4 }.forEach {
            walk.feed(Event.MeetingCheckedIn(t(), it))
        }
        assertEquals(
            MeetingPhase.CheckIn, walk.state.meeting!!.phase,
            "the talk started without the player who is out",
        )

        walk.feed(Event.MeetingCheckedIn(t(), Seat(4)))
        assertEquals(MeetingPhase.Discussion, walk.state.meeting!!.phase)
    }

    /** A second I'M HERE from a seat already standing there answers with the same numbers. */
    @Test
    fun `checking in twice is answered rather than swallowed`() {
        val walk = Walk(armed())
        walk.feed(
            Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard),
            Event.MeetingCheckedIn(t(), Seat(2)),
        )
        walk.drain()
        walk.feed(Event.MeetingCheckedIn(t(), Seat(2)))
        assertEquals(
            listOf(Effect.CheckInProgressed(2, 6)), walk.drain(),
            "a repeat check-in produced no answer at all",
        )
    }

    // ---- Discussion ---------------------------------------------------------------------------

    /** **A unanimous READY TO VOTE ends the talk early**, and one short of unanimous does not. */
    @Test
    fun `unanimous readiness opens the ballot and one hand short does not`() {
        val walk = talking()
        SEATS.dropLast(1).forEach { walk.feed(Event.ReadyToVoteDeclared(t(), it)) }
        assertEquals(
            MeetingPhase.Discussion, walk.state.meeting!!.phase,
            "the ballot opened with a hand still down",
        )
        assertEquals(5, walk.drain().filterIsInstance<Effect.ReadyProgressed>().last().ready)

        walk.feed(Event.ReadyToVoteDeclared(t(), SEATS.last()))
        assertEquals(MeetingPhase.Vote, walk.state.meeting!!.phase)
        assertEquals(6, walk.state.meeting!!.ballots.size, "one ballot per living seat")
    }

    /**
     * The ready count is against the living, who are the only players holding the control.
     *
     * With a seat out, unanimity is five hands and not six — otherwise the talk could never end
     * early once anybody had been Revoked, because the sixth hand belongs to a screen with no
     * READY TO VOTE on it (D-134).
     */
    @Test
    fun `readiness is unanimous among the living`() {
        val start = Walk(armed())
            .feed(Event.RevokeArmed(t(), Seat(1)), Event.ContactMade(t(), Seat(1), Seat(4)))
            .state
        val walk = talking(start)

        start.livingSeats.forEach { walk.feed(Event.ReadyToVoteDeclared(t(), it)) }
        assertEquals(MeetingPhase.Vote, walk.state.meeting!!.phase)
        assertEquals(
            listOf(0, 1, 2, 3, 5), walk.state.meeting!!.ballots.map { it.voter.index },
            "ghosts cast nothing (gdd.md:417), so they are given no ballot to cast",
        )
    }

    // ---- The vote (D-117) ---------------------------------------------------------------------

    /** Every tap transmits live, which is what the couch watches happen. */
    @Test
    fun `every selection tap goes out live`() {
        val walk = voting()
        walk.feed(Event.VoteSelected(t(), Seat(2), Seat(5)))
        val shown = walk.drain().filterIsInstance<Effect.VoteSelectionShown>().single()
        assertEquals(2, shown.voter.index)
        assertEquals(5, shown.selection?.index)
    }

    /**
     * **THE INJECTION THIS UNIT IS ABOUT: a tap after READY is REFUSED, and the house re-asserts.**
     *
     * D-117 makes READY irrevocable. Rule 1 makes the refusal *an answer of the same shape*, never
     * a silence — so a later tap comes back carrying the locked selection rather than the one the
     * finger just landed on, and the player is never left looking at a lit row the house has never
     * heard of.
     *
     * Three things are asserted and all three matter: the stored vote did not move, the effects
     * were emitted at all, and what they carry is the **old** selection.
     */
    @Test
    fun `a tap after READY is refused with the house re-asserting`() {
        val walk = voting()
        walk.feed(
            Event.VoteSelected(t(), Seat(2), Seat(5)),
            Event.VoteLocked(t(), Seat(2)),
        )
        walk.drain()

        walk.feed(Event.VoteSelected(t(), Seat(2), Seat(3)))
        val after = walk.drain()

        assertEquals(
            5, walk.state.meeting!!.ballotFor(Seat(2))!!.selection?.index,
            "READY was not irrevocable",
        )
        val held = after.filterIsInstance<Effect.VoteHeld>().singleOrNull()
            ?: throw AssertionError("the refused tap was answered with silence: $after")
        assertEquals(5, held.selection?.index, "the house re-asserted the wrong selection")
        assertTrue(held.locked, "the seat was not told its ballot is locked")
        assertEquals(
            5,
            after.filterIsInstance<Effect.VoteSelectionShown>().single().selection?.index,
            "the couch was shown a selection the house does not hold",
        )
    }

    /**
     * The same shape either way — the property the assertion above rests on.
     *
     * An accepted tap and a refused one produce the same two kinds in the same order. If a refusal
     * ever emitted fewer effects than an acceptance, the absence would be the message and every
     * living phone in the room could count it.
     */
    @Test
    fun `an accepted tap and a refused tap have the same shape`() {
        val open = voting()
        open.feed(Event.VoteSelected(t(), Seat(2), Seat(5)))
        val accepted = open.drain()

        val locked = voting()
        locked.feed(Event.VoteSelected(t(), Seat(2), Seat(4)), Event.VoteLocked(t(), Seat(2)))
        locked.drain()
        locked.feed(Event.VoteSelected(t(), Seat(2), Seat(5)))
        val refused = locked.drain()

        assertEquals(
            accepted.map { it::class.simpleName }, refused.map { it::class.simpleName },
            "a refused tap is distinguishable from an accepted one by shape alone",
        )
    }

    /** `N OF 6 VOTED` counts locked seats, not selections (E8-1's first question). */
    @Test
    fun `the count the living see is of locked seats and not of selections`() {
        val walk = voting()
        walk.feed(
            Event.VoteSelected(t(), Seat(0), Seat(3)),
            Event.VoteSelected(t(), Seat(2), Seat(3)),
            Event.VoteSelected(t(), Seat(4), null),
        )
        assertEquals(
            emptyList(), walk.drain().filterIsInstance<Effect.VoteProgressed>(),
            "three selections moved the voted count",
        )

        walk.feed(Event.VoteLocked(t(), Seat(0)))
        assertEquals(
            Effect.VoteProgressed(1, 6),
            walk.drain().filterIsInstance<Effect.VoteProgressed>().single(),
        )
    }

    /** **Unanimous locks close the window early**, symmetric with the discussion's readiness. */
    @Test
    fun `unanimous locks close the window early`() {
        val walk = voting()
        SEATS.dropLast(1).forEach {
            walk.feed(Event.VoteSelected(t(), it, Seat(3)), Event.VoteLocked(t(), it))
        }
        assertEquals(
            MeetingPhase.Vote, walk.state.meeting!!.phase,
            "the window closed with one ballot still open",
        )

        walk.feed(Event.VoteLocked(t(), SEATS.last()))
        assertEquals(MeetingPhase.Tally, walk.state.meeting!!.phase)
        assertEquals(3, walk.state.meeting!!.restrainPending?.index)
    }

    /**
     * **Auto-lock at the buzzer banks the LATEST selection** (D-117), and an empty one is a Skip
     * (D-075, narrowed).
     *
     * Seat 0 changes its mind twice and never presses anything; seat 4 never chooses at all.
     */
    @Test
    fun `the buzzer banks the latest selection and silence is a Skip`() {
        val walk = voting()
        walk.feed(
            Event.VoteSelected(t(), Seat(0), Seat(2)),
            Event.VoteSelected(t(), Seat(0), Seat(3)),
            Event.VoteSelected(t(), Seat(0), Seat(5)),
        )
        walk.feed(Event.VoteWindowClosed(t()))

        val ballots = walk.state.meeting!!.ballots
        assertTrue(ballots.all { it.locked }, "the buzzer left a ballot unlocked")
        assertEquals(5, ballots.single { it.voter.index == 0 }.selection?.index)
        assertNull(
            ballots.single { it.voter.index == 4 }.selection,
            "a seat that never chose was given a vote",
        )
    }

    // ---- The tally --------------------------------------------------------------------------

    /** Most votes is Restrained (`gdd.md:413`), and the result goes to everybody. */
    @Test
    fun `the room restrains whoever leads the ballot`() {
        val walk = voting()
        listOf(Seat(0), Seat(2), Seat(4)).forEach {
            walk.feed(Event.VoteSelected(t(), it, Seat(3)))
        }
        walk.feed(Event.VoteSelected(t(), Seat(1), Seat(5)))
        walk.drain()
        walk.feed(Event.VoteWindowClosed(t()))

        val effects = walk.drain()
        assertEquals(3, effects.filterIsInstance<Effect.MeetingResult>().single().restrained?.index)
        val resolved = effects.filterIsInstance<Effect.MeetingResolved>().single()
        assertEquals(3, resolved.restrained?.index)
        assertEquals(
            listOf(0 to 3, 1 to 5, 2 to 3, 3 to null, 4 to 3, 5 to null),
            resolved.attribution.map { it.first.index to it.second?.index },
            "attribution is the ballot in seat order",
        )
    }

    /**
     * **Ties resolve to Skip, and so does a Skip that leads** (`gdd.md:413`, `:1007`, D-075).
     *
     * *The whole weight of inaction sits behind restraining nobody.* Both ways of getting there
     * are asserted, because a build that only handled the named tie would still restrain somebody
     * in a room that mostly voted to restrain nobody.
     */
    @Test
    fun `a tie and a leading Skip both restrain nobody`() {
        val tied = voting()
        tied.feed(
            Event.VoteSelected(t(), Seat(0), Seat(3)),
            Event.VoteSelected(t(), Seat(1), Seat(3)),
            Event.VoteSelected(t(), Seat(2), Seat(5)),
            Event.VoteSelected(t(), Seat(4), Seat(5)),
            Event.VoteWindowClosed(t()),
        )
        assertNull(tied.state.meeting!!.restrainPending, "a tied ballot restrained somebody")

        val skipped = voting()
        skipped.feed(
            Event.VoteSelected(t(), Seat(0), Seat(3)),
            Event.VoteSelected(t(), Seat(1), Seat(3)),
            Event.VoteWindowClosed(t()),
        )
        assertNull(
            skipped.state.meeting!!.restrainPending,
            "two votes beat four silences; Skip is a candidate and not an abstention",
        )
    }

    /**
     * **THE TAKEOVER GOES TO EXACTLY THE LOSING SEAT AT EXACTLY T-HALF** (D-102, D-134's E1-1).
     *
     * Three separate claims, and each of them is a way this can be built wrong: nothing arrives at
     * the buzzer, the seat is still **Live** until the halfway mark, and when it does arrive it
     * names one seat.
     */
    @Test
    fun `the takeover reaches the losing seat and only at the halfway mark`() {
        val walk = voting()
        listOf(Seat(0), Seat(2), Seat(4), Seat(5)).forEach {
            walk.feed(Event.VoteSelected(t(), it, Seat(3)))
        }
        walk.feed(Event.VoteWindowClosed(t()))

        assertEquals(
            emptyList(), walk.drain().filterIsInstance<Effect.RestrainedTakeover>(),
            "the takeover arrived at the buzzer, and the player will walk away",
        )
        assertTrue(
            walk.state.restrained.isEmpty(),
            "the seat left the round at the buzzer; the house deauthorises them moments LATER",
        )

        walk.feed(Event.TallyHalfwayReached(t()))
        val takeover = walk.drain().filterIsInstance<Effect.RestrainedTakeover>().single()
        assertEquals(3, takeover.seat.index)
        assertEquals(Haptic.Long, takeover.haptic, "D-135's fourth long haptic")
        assertEquals(listOf(3), walk.state.restrained.map { it.index })
        assertTrue(walk.state.revoked.isEmpty(), "a Restrain was recorded as a Revoke (rule 9)")
    }

    /** Nobody restrained means nobody taken over. The branch is on a fact already broadcast. */
    @Test
    fun `a meeting that restrained nobody takes nobody over`() {
        val walk = voting()
        walk.feed(Event.VoteWindowClosed(t()), Event.TallyHalfwayReached(t()))
        assertEquals(emptyList(), walk.drain().filterIsInstance<Effect.RestrainedTakeover>())
        assertTrue(walk.state.restrained.isEmpty())
    }

    /** Lights out ends the meeting, and everyone who walked in is couch from here (D-134). */
    @Test
    fun `lights out closes the meeting and ages the newly Revoked into the couch`() {
        val start = Walk(armed())
            .feed(Event.RevokeArmed(t(), Seat(1)), Event.ContactMade(t(), Seat(1), Seat(4)))
            .state
        assertEquals(listOf(4), start.newlyRevoked.map { it.index })

        val walk = talking(start)
        walk.feed(Event.DiscussionClosed(t()), Event.VoteWindowClosed(t()))
        walk.drain()
        walk.feed(Event.TallyHalfwayReached(t()), Event.MeetingClosed(t()))

        val ended = walk.drain().filterIsInstance<Effect.MeetingEnded>().single()
        assertEquals(Haptic.Long, ended.haptic, "D-135's fifth long haptic")
        assertNull(walk.state.meeting)
        assertEquals(
            emptyList(), walk.state.newlyRevoked,
            "the next meeting would ring this phone with STAND AND WALK IN a second time",
        )
        assertEquals(listOf(4), walk.state.revoked.map { it.index }, "they are still out")
    }

    // ---- Out of phase --------------------------------------------------------------------------

    /**
     * Every meeting event belongs to a phase, and arriving at the wrong one is a **refusal**.
     *
     * Refused above the rules rather than dropped inside them: an early return in [reduce] for
     * each of these is rule 1's forbidden shape sitting five times over in the one file rule 1 is
     * about, and a refusal is recorded where a drop is invisible.
     */
    @Test
    fun `a meeting event at the wrong phase is refused and recorded`() {
        val walk = talking()
        walk.feed(
            Event.MeetingCheckedIn(t(), Seat(2)),
            Event.VoteLocked(t(), Seat(2)),
            Event.TallyHalfwayReached(t()),
            Event.MeetingClosed(t()),
        )
        assertEquals(4, walk.refusals.size, "an out-of-phase event reached the rules")
        assertTrue(walk.refusals.all { it == RefusalReason.WrongMeetingPhase })
        assertEquals(emptyList(), walk.drain(), "a refusal emitted effects")
        assertEquals(MeetingPhase.Discussion, walk.state.meeting!!.phase)
    }

    /** With no meeting at all there is nothing to be in phase with, and the answer is the same. */
    @Test
    fun `a vote with no meeting running is refused`() {
        val walk = Walk(armed()).feed(Event.VoteSelected(t(), Seat(2), Seat(3)))
        assertEquals(listOf(RefusalReason.WrongMeetingPhase), walk.refusals)
        assertEquals(emptyList(), walk.emitted)
    }

    /** The phase gate is above the rules, which is where the whole of D-066 lives. */
    @Test
    fun `the gate refuses rather than the rules returning quietly`() {
        val admission = admit(armed(), Event.VoteLocked(Tick(9), Seat(0)))
        assertIs<Admission.Refused>(admission)
    }
}
