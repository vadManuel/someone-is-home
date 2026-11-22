package home.someoneshome.core

import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The request becoming the fact, and the one gate it is put to.**
 *
 * Two things are checked here and they are different in kind. The first is mechanical: every
 * [Intent] becomes exactly the [Event] it names. The second is the judgement, and it is the one
 * worth reading — **which client-originated events have no request at all**, and why each of those
 * is a decision rather than a case somebody failed to reach.
 */
class IntentAdmissionTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val marker = MarkerId("CARD-11")
    private val at = Tick(9)

    private fun armed(): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1))),
    ).state

    /**
     * Every request there is, once. Closed against [Intent] by [factOf]'s own exhaustiveness.
     *
     * ⚠️ **This list and [everyEvent] are hand-written, and both had drifted.** The three Tier-2
     * fires were in neither of them, so the completeness check below — the one that exists to
     * catch a request that names a seat the authority was supposed to resolve — had silently
     * stopped covering three abilities. It did not fail while it was blind, because a fact absent
     * from *both* lists is a fact the loop never reaches from either direction.
     *
     * They are written by hand on purpose and that stays right: a list derived from [factOf] would
     * be [factOf] agreeing with itself (L2's injection-6 finding). What the drift shows is that
     * hand-written needs the same treatment the emit schema's completeness list gets — checked
     * against the type, not against memory.
     */
    private val everyIntent: List<Intent> = listOf(
        Intent.ArmRevoke(Seat(1)),
        Intent.FireSurge(Seat(1), Seat(2)),
        Intent.FireIsolate(Seat(1), marker),
        Intent.FireStatic(Seat(1)),
        Intent.StopSubroutine(Seat(2)),
        Intent.LeaveTerminal(Seat(2)),
        Intent.ReturnSyncPulse(Seat(2), listOf(1L, 2L)),
        Intent.CheckIn(Seat(4)),
        Intent.DeclareReadyToVote(Seat(4)),
        Intent.SelectVote(Seat(4), Seat(0)),
        Intent.LockVote(Seat(4)),
        Intent.HandOverEntry(Seat(2), marker, listOf(3, 1, 4)),
    )

    // ---- 1 · The translation -----------------------------------------------------------------------

    /**
     * **Every request is the fact it names, with the actor it was attributed and the tick it
     * arrived on.**
     *
     * Written out rather than derived, because a table derived from [factOf] would be [factOf]
     * agreeing with itself — L2's injection-6 finding, and the reason the emit schema's own
     * completeness list is written twice.
     */
    @Test
    fun `every request becomes the fact it names`() {
        assertEquals(Event.RevokeArmed(at, Seat(1)), factOf(Intent.ArmRevoke(Seat(1)), at))
        assertEquals(Event.MeetingCheckedIn(at, Seat(4)), factOf(Intent.CheckIn(Seat(4)), at))
        assertEquals(
            Event.ReadyToVoteDeclared(at, Seat(4)),
            factOf(Intent.DeclareReadyToVote(Seat(4)), at),
        )
        assertEquals(
            Event.VoteSelected(at, Seat(4), Seat(0)),
            factOf(Intent.SelectVote(Seat(4), Seat(0)), at),
        )
        assertEquals(Event.VoteLocked(at, Seat(4)), factOf(Intent.LockVote(Seat(4)), at))
        assertEquals(
            Event.SubroutineReturned(at, Seat(2), marker, listOf(3, 1, 4)),
            factOf(Intent.HandOverEntry(Seat(2), marker, listOf(3, 1, 4)), at),
        )
    }

    /**
     * **The actor survives the translation, and it survives it alone.**
     *
     * The wire strips the seat off and the desk stamps it back on; this is the last leg of that,
     * and a translation that read a seat from anywhere but [Intent.actor] would break the chain at
     * its last link. Two different actors have to produce two different facts.
     */
    @Test
    fun `the attributed seat is the one the fact carries`() {
        for (intent in everyIntent) {
            val theirs = factOf(intent, at)
            val mine = factOf(reattributed(intent, Seat(5)), at)
            assertTrue(theirs != mine, "$intent produced the same fact for two different actors")
        }
    }

    // ---- 2 · One gate, two sources -----------------------------------------------------------------

    /**
     * **A request reaches the same gate an event does, by the same route.**
     *
     * There is not a second admission path for requests off a socket, and this is what says so:
     * `admit(state, intent, at)` is `admit(state, factOf(intent, at))` and nothing else. A future
     * shortcut — a check "just for the wire", a refusal "the client can handle" — fails here.
     */
    @Test
    fun `the request path and the fact path are one gate`() {
        for (state in listOf(GameState.EMPTY, armed())) {
            for (intent in everyIntent) {
                assertEquals(
                    describe(admit(state, factOf(intent, at))),
                    describe(admit(state, intent, at)),
                    "$intent was judged differently depending on which door it came in by",
                )
            }
        }
    }

    /**
     * **A request outside a round is refused, and the refusal carries nothing back.**
     *
     * D-066 unchanged, reached through the new door. The refusal is the authority's own record; the
     * phone that sent it is answered by re-assertion and never by an explanation (D-097), which is
     * only possible because [Admission.Refused] has no reduction and therefore no effects that
     * could differ from a success's.
     */
    @Test
    fun `a request outside a round is refused and leaves nothing behind`() {
        for (intent in everyIntent) {
            val admission = admit(GameState.EMPTY, intent, at)
            assertIs<Admission.Refused>(admission, "$intent was admitted with no round to change")
            assertEquals(RefusalReason.RoundNotArmed, admission.reason)
        }
    }

    // ---- 3 · What has no request at all, and why ---------------------------------------------------

    /**
     * Where an [Event] comes from. **Exhaustive over [Event] on purpose**: a new event does not
     * compile until somebody decides whether a phone may ask for it, which is the prompt this file
     * exists to keep in place.
     */
    private enum class Origin {

        /** A phone did it, and the fact needs nothing but the phone, the connection and the clock. */
        Requestable,

        /**
         * A phone did it, but the fact names a seat only the authority can resolve.
         *
         * Contact is phone-to-phone and every phone's advertised identity is a token that rotates
         * every fifteen seconds, resolvable by the house alone (D7). A client that could send one
         * of these would be naming the seat it touched — the forge the whole upward channel is
         * built to make unrepresentable.
         */
        ContactResolved,

        /**
         * A phone did it, but the fact carries a draw the **house** makes, or needs the house's
         * **map** to exist at all.
         *
         * `EgressFired` needs a type and two nodes chosen above the rules against a home's shape
         * the phone has never held. **A card read is the second** (D-318): whether paper is work,
         * the meeting card or NOT A MARKER is the map's answer (`routeScan`), so `MarkerScanned` and
         * the card's `MeetingCalled` are routed by the house from `Asked.Read`, and unregistered
         * paper makes no fact at all. Carrying either would mean composing a fact from more than
         * the phone gave, which is the loop's join rather than this translation's.
         */
        HouseComposed,

        /** Nobody pressed anything. The house's own clock, sampled at the edge. */
        HouseClock,

        /**
         * **Nobody pressed anything: a phone's radio told it so** (T2, D-222). Reported up its own
         * fixed body (`RoundBody.RadioFaulted`), never requested, so no [Intent] may carry it.
         */
        RadioReported,
    }

    private fun originOf(event: Event): Origin = when (event) {
        // **A card read, routed by the house against the map** (D-318). It was requestable, and a
        // phone chose the event by the shape printed on the paper: a card the home never
        // registered reached the rules as work, and a spare meeting card called a meeting.
        is Event.MarkerScanned -> Origin.HouseComposed
        is Event.SubroutineReturned, is Event.RevokeArmed,
        is Event.MeetingCheckedIn, is Event.ReadyToVoteDeclared, is Event.VoteSelected,
        is Event.VoteLocked,
        // **The two Tier-2 fires, and they are requestable where `EgressFired` is not.** The
        // contrast is the whole reason they sit on this row: the house draws nothing for either.
        // A Surge names a seat off a roster and an Isolate names a card off a list the house
        // itself published to that phone, so the fact is settled by what the phone did, which
        // connection did it, and when. **Static is the third and is here now**, and it is the
        // clearest case on the row: it names nothing at all, so the fact is the actor and the tick
        // and there is nothing anywhere for the house to compose from a second source.
        is Event.SurgeFired, is Event.IsolateFired, is Event.StaticFired,
        -> Origin.Requestable

        // Two ways to call a meeting and neither is a request. `MeetingCard` is a card read the house
        // routed against the map (D-318): only the card this home registered calls one. `RevokeReport`
        // names the seat that was reported, arrived at by touching a phone to another phone. The
        // trigger still splits the row, because the two are refused for different reasons.
        is Event.MeetingCalled -> when (event.trigger) {
            is MeetingTrigger.MeetingCard -> Origin.HouseComposed
            is MeetingTrigger.RevokeReported -> Origin.ContactResolved
        }

        is Event.ContactMade -> Origin.ContactResolved
        // The spectator map's input (O4): resolved from the radio by the house, as a contact is,
        // and naming a room the house knows rather than one any phone said.
        is Event.Sensed -> Origin.ContactResolved
        is Event.EgressFired -> Origin.HouseComposed
        is Event.RadioFaulted -> Origin.RadioReported
        // **The close the house concludes from a radio report** (D-310): minted by the house when
        // the rules say a permission switched off after the warning closes it. Never requested.
        is Event.HouseClosedOnPermission -> Origin.RadioReported

        // **STOP NOW and the taps of a Sync Pulse, carried now** (E-P2-5 / E-P3-7, closed). Both
        // were real controls with no member on the wire, and both belong on this row rather than
        // beside `EgressFired`: the first composes a fact out of the actor alone, the second out of
        // the actor and the taps the finger made, and neither needs a draw or a token resolved. The
        // gap was a missing member, never a missing argument — which is why moving them here costs
        // nothing but the members themselves.
        is Event.PerformanceEnded, is Event.SyncPulseReturned -> Origin.Requestable

        // **The live view closing, and it is requestable for STOP NOW's reason** (D-203): the
        // actor and nothing else. The host also composes it for a dropped connection, and that is
        // the same request made on the phone's behalf -- not a fact the phone could not have stated.
        is Event.TerminalLeft -> Origin.Requestable

        is Event.RoundArmed, is Event.DiscussionClosed, is Event.VoteWindowClosed,
        is Event.TallyHalfwayReached, is Event.MeetingClosed, is Event.EgressExpired,
        // The house's return to the air, off its own clock (the owner's 2026-11-18 ruling, D-281).
        is Event.OffAirEnded,
        -> Origin.HouseClock
    }

    /** One of every [Event] there is, so the classification above is applied rather than declared. */
    private fun everyEvent(): List<Event> = listOf(
        Event.RoundArmed(at, seed = 1L, seats = seats, insiders = listOf(Seat(1))),
        Event.MarkerScanned(at, Seat(2), marker),
        Event.SubroutineReturned(at, Seat(2), marker, listOf(1)),
        Event.PerformanceEnded(at, Seat(2)),
        Event.TerminalLeft(at, Seat(2)),
        Event.RevokeArmed(at, Seat(1)),
        // ⚠️ **The three Tier-2 fires were missing from this list**, which is why the assertion
        // below could classify them as requestable while no request produced them and nothing
        // failed. See [everyIntent]'s note: a fact absent from both hand-written lists is a fact
        // the check never reaches from either direction, and an instrument with nothing to look at
        // reports green forever.
        Event.SurgeFired(at, Seat(1), Seat(2)),
        Event.IsolateFired(at, Seat(1), marker),
        Event.StaticFired(at, Seat(1)),
        Event.ContactMade(at, Seat(1), Seat(2)),
        Event.RadioFaulted(at, Seat(2), RadioCause.Person),
        Event.RadioFaulted(at, Seat(2), RadioCause.Device),
        Event.MeetingCalled(at, Seat(3), MeetingTrigger.MeetingCard),
        Event.MeetingCalled(at, Seat(3), MeetingTrigger.RevokeReported(Seat(2))),
        Event.MeetingCheckedIn(at, Seat(4)),
        Event.ReadyToVoteDeclared(at, Seat(4)),
        Event.VoteSelected(at, Seat(4), Seat(0)),
        Event.VoteLocked(at, Seat(4)),
        Event.DiscussionClosed(at),
        Event.VoteWindowClosed(at),
        Event.TallyHalfwayReached(at),
        Event.MeetingClosed(at),
        Event.EgressFired(at, Seat(1), EgressType.Beacon, listOf(marker), pulseFrom = 4_000_000L),
        Event.SyncPulseReturned(at, Seat(2), listOf(1L)),
        Event.EgressExpired(at),
        Event.OffAirEnded(at, since = at),
    )

    /**
     * **Every fact a phone can ask for is one a phone can ask for, and no other is.**
     *
     * Both directions, because each catches a different mistake. A requestable event with no
     * request is a control that goes nowhere; a request for an event classified otherwise is the
     * thing this unit must never ship — a client naming a seat the authority was supposed to
     * resolve, or making a draw the house was supposed to make.
     *
     * The classification is written by hand in [originOf] and the coverage is computed from
     * [factOf], so the two are independent and neither can agree with itself.
     */
    @Test
    fun `every client-originated fact is either requestable or named as unreachable`() {
        val reachable = everyIntent.map { shapeOf(factOf(it, at)) }.toSet()

        for (event in everyEvent()) {
            val asked = shapeOf(event) in reachable
            when (originOf(event)) {
                Origin.Requestable -> assertTrue(
                    asked,
                    "${event::class.simpleName} is a phone's to ask for and no request produces it",
                )
                Origin.ContactResolved, Origin.HouseComposed, Origin.HouseClock, Origin.RadioReported -> assertTrue(
                    !asked,
                    "${event::class.simpleName} can be built from a frame, and it must not be — " +
                        "see Origin for which of the three reasons applies",
                )
            }
        }

        // Not vacuous in either direction: both halves of the table are populated.
        val origins = everyEvent().map(::originOf).toSet()
        assertEquals(Origin.entries.toSet(), origins, "a whole class of origin went unexercised")
        assertEquals(everyIntent.size, reachable.size, "two requests make the same fact")
    }

    /**
     * **What kind of fact this is**, ignoring who did it, when, and what they entered.
     *
     * The comparison has to be by shape rather than by value: a request and the event a fixture
     * lists are the same *kind* of fact carrying different taps, and comparing them whole would
     * make this check pass or fail on whether two lists of numbers happened to match.
     *
     * [Event.MeetingCalled] is split on its trigger because it is the one event with two origins —
     * the card is a phone's to ask for and the Revoked report is not — and a shape that ignored
     * the trigger would let the report ride in on the card's row.
     */
    private fun shapeOf(event: Event): String = when (event) {
        is Event.MeetingCalled -> "MeetingCalled/${event.trigger::class.simpleName}"
        else -> event::class.simpleName.orEmpty()
    }

    /** The reason a refusal carries, or the effects an admission did — never the reduction itself. */
    private fun describe(admission: Admission): String = when (admission) {
        is Admission.Refused -> "refused ${admission.reason}"
        is Admission.Admitted -> "admitted ${admission.reduction.effects}"
    }

    /** The same request from somebody else. The test's own, never the wire's. */
    private fun reattributed(intent: Intent, actor: Seat): Intent = when (intent) {
        is Intent.ArmRevoke -> intent.copy(actor = actor)
        is Intent.FireSurge -> intent.copy(actor = actor)
        is Intent.FireIsolate -> intent.copy(actor = actor)
        is Intent.FireStatic -> intent.copy(actor = actor)
        is Intent.StopSubroutine -> intent.copy(actor = actor)
        is Intent.LeaveTerminal -> intent.copy(actor = actor)
        is Intent.ReturnSyncPulse -> intent.copy(actor = actor)
        is Intent.CheckIn -> intent.copy(actor = actor)
        is Intent.DeclareReadyToVote -> intent.copy(actor = actor)
        is Intent.SelectVote -> intent.copy(actor = actor)
        is Intent.LockVote -> intent.copy(actor = actor)
        is Intent.HandOverEntry -> intent.copy(actor = actor)
    }
}
