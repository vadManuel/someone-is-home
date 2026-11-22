package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN: set in the lobby, locked at arming, and read by
 * the rules off the round and never off `Balance`** (the owner's 2026-11-21 ruling, D-317; D-037).
 *
 * [VoteWindowTest]'s arrangement for the other three timed rows. Every value armed here is one the
 * defaults are not — a 60 s talk, a 60 s Egress, a 30 s Revoke cooldown — so a rule that read the
 * default instead of the locked value fails by name rather than passing on a coincidence. The
 * lobby offers one choice per row today (no ruling gives a range); the rules take any number the
 * arming carries, which is what lets the owner's range be a list in `ui` and nothing else.
 */
class LobbySettingsTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insider = Seat(1)
    private val home = (0 until 8).map { MarkerId("m$it") }
    private val second = Balance.TICKS_PER_SECOND

    private fun armed(
        discussion: Int = Balance.DISCUSSION_SECONDS,
        egressTimer: Int = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldown: Int = Balance.REVOKE_COOLDOWN_SECONDS,
    ): Pair<GameState, List<Effect>> = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 1L, seats = seats, insiders = listOf(insider), markers = placed(home),
            discussionSeconds = discussion, egressTimerSeconds = egressTimer, revokeCooldownSeconds = revokeCooldown,
        ),
    ).let { it.state to it.effects }

    /** Feed [events] through the admission gate, collecting what the house says. */
    private fun walk(start: GameState, vararg events: Event): Pair<GameState, List<Effect>> {
        var state = start
        val emitted = mutableListOf<Effect>()
        for (event in events) {
            val admission = admit(state, event)
            assertTrue(admission is Admission.Admitted, "$event was refused: $admission")
            state = admission.reduction.state
            emitted += admission.reduction.effects
        }
        return state to emitted
    }

    // ---- Locked at arming --------------------------------------------------------------------

    /** **The arming event's three numbers are the round's**, and a round armed without them runs the defaults. */
    @Test
    fun `the arming locks all three settings onto the round`() {
        val (state, _) = armed(discussion = 60, egressTimer = 60, revokeCooldown = 30)
        assertEquals(60, state.discussionSeconds, "the talk's length was not locked at arming")
        assertEquals(60, state.egressTimerSeconds, "the Egress timer was not locked at arming")
        assertEquals(30, state.revokeCooldownSeconds, "the Revoke cooldown was not locked at arming")

        val (plain, _) = armed()
        assertEquals(Balance.DISCUSSION_SECONDS, plain.discussionSeconds)
        assertEquals(Balance.EGRESS_TIMER_SECONDS, plain.egressTimerSeconds)
        assertEquals(Balance.REVOKE_COOLDOWN_SECONDS, plain.revokeCooldownSeconds)
    }

    // ---- EGRESS TIMER --------------------------------------------------------------------------

    /**
     * **THE INJECTION: a 60 s EGRESS TIMER gives the Egress 60 s** (D-014, D-317).
     *
     * The deadline the house holds and the `remaining` every phone is sent are both the locked
     * sixty, not the default hundred and twenty.
     */
    @Test
    fun `a sixty second Egress timer gives the Egress sixty seconds`() {
        val (start, _) = armed(egressTimer = 60)
        val at = start.egressReadyAt
        val nodes = start.activeMarkers.take(2)
        val (fired, effects) = walk(start, Event.EgressFired(at, insider, EgressType.Beacon, nodes, pulseFrom = 7_000_000L))

        assertEquals(
            Tick(at.step + 60 * second), fired.egress!!.deadline,
            "the house armed a 60 s Egress timer and ran an Egress to ${fired.egress!!.deadline}",
        )
        val opened = effects.filterIsInstance<Effect.EgressOpened>()
        assertEquals(seats, opened.map { it.seat }, "the Egress did not reach every phone")
        assertEquals(
            listOf(60 * second), opened.map { it.remaining }.distinct(),
            "the house armed a 60 s Egress timer and told the phones ${opened.map { it.remaining }.distinct()} ticks",
        )
    }

    // ---- DISCUSSION ----------------------------------------------------------------------------

    private fun openTheTalk(start: GameState): List<Effect> =
        walk(
            start,
            Event.MeetingCalled(Tick(100), Seat(0), MeetingTrigger.MeetingCard),
            *seats.map { Event.MeetingCheckedIn(Tick(101), it) }.toTypedArray(),
        ).second

    /**
     * **THE INJECTION: a 60 s DISCUSSION is published as 60 s on the push that opens the talk**
     * (D-317). The house's clock closes it on the same number (`HouseClockTest` in `app`).
     */
    @Test
    fun `the talk opens with the length the round was armed with`() {
        val (start, _) = armed(discussion = 60)
        val talk = openTheTalk(start).filterIsInstance<Effect.MeetingPhaseOpened>().single { it.phase == MeetingPhase.Discussion }
        assertEquals(60, talk.discussionSeconds, "the house armed a 60 s talk and opened a ${talk.discussionSeconds} s one")
        assertNull(talk.voteWindowSeconds, "the talk's push carried a ballot window")
    }

    /** The field is the talk's and no other phase's, as `voteWindowSeconds` is the ballot's. */
    @Test
    fun `no phase but the talk publishes a discussion length`() {
        val (start, _) = armed(discussion = 60)
        val (met, opened) = walk(
            start,
            Event.MeetingCalled(Tick(100), Seat(0), MeetingTrigger.MeetingCard),
            *seats.map { Event.MeetingCheckedIn(Tick(101), it) }.toTypedArray(),
            Event.DiscussionClosed(Tick(103)),
            Event.VoteWindowClosed(Tick(104)),
        )
        check(met.meeting != null)
        for (push in opened.filterIsInstance<Effect.MeetingPhaseOpened>().filterNot { it.phase == MeetingPhase.Discussion }) {
            assertNull(push.discussionSeconds, "${push.phase} published a discussion length")
        }
    }

    // ---- REVOKE COOLDOWN -----------------------------------------------------------------------

    /**
     * **THE INJECTION: a 30 s REVOKE COOLDOWN opens every seat's Revoke at 15 s and an arm spends
     * 30 s** (D-037, D-132, D-317).
     */
    @Test
    fun `a thirty second Revoke cooldown opens at half and an arm spends all of it`() {
        val (start, _) = armed(revokeCooldown = 30)
        for (seat in seats) {
            assertEquals(
                Tick(15 * second), start.cooldownFor(seat, InsiderAbility.Revoke)!!.readyAt,
                "seat ${seat.index}'s Revoke opened at ${start.cooldownFor(seat, InsiderAbility.Revoke)!!.readyAt}, " +
                    "not at half of the 30 s the round was armed with",
            )
        }
        val ready = Tick(15 * second)
        val (armedAt, _) = walk(start, Event.RevokeArmed(ready, insider))
        assertEquals(
            Tick(ready.step + 30 * second), armedAt.cooldownFor(insider, InsiderAbility.Revoke)!!.readyAt,
            "an arm under a 30 s Revoke cooldown spent ${armedAt.cooldownFor(insider, InsiderAbility.Revoke)!!.readyAt}",
        )
    }

    /** **Every phone is pushed the locked cooldown at LIGHTS OUT**, so page 2 draws the host's number. */
    @Test
    fun `LIGHTS OUT tells every phone the Revoke cooldown the round was armed with`() {
        val (_, effects) = armed(revokeCooldown = 30)
        val opening = effects.filterIsInstance<Effect.OpeningMessage>()
        assertEquals(seats, opening.map { it.seat }, "the opening message missed a phone")
        assertEquals(
            listOf(30 * second), opening.map { it.revokeCooldown }.distinct(),
            "the phones were told ${opening.map { it.revokeCooldown }.distinct()} ticks for a 30 s cooldown",
        )
    }

    /** **A meeting's end puts every Revoke back at half of the locked cooldown, and says so** (D-184). */
    @Test
    fun `a meeting's end reopens the Revoke at half of the locked cooldown`() {
        val (start, _) = armed(revokeCooldown = 30)
        val closedAt = Tick(200)
        val (after, effects) = walk(
            start,
            Event.MeetingCalled(Tick(100), Seat(0), MeetingTrigger.MeetingCard),
            *seats.map { Event.MeetingCheckedIn(Tick(101), it) }.toTypedArray(),
            Event.DiscussionClosed(Tick(103)),
            Event.VoteWindowClosed(Tick(104)),
            Event.TallyHalfwayReached(Tick(105)),
            Event.MeetingClosed(closedAt),
        )
        for (seat in seats) {
            assertEquals(
                Tick(closedAt.step + 15 * second), after.cooldownFor(seat, InsiderAbility.Revoke)!!.readyAt,
                "seat ${seat.index}'s Revoke reopened at the default's half, not the locked cooldown's",
            )
        }
        assertEquals(
            listOf(30 * second), effects.filterIsInstance<Effect.MeetingEnded>().map { it.revokeCooldown },
            "the meeting's end did not carry the locked cooldown",
        )
    }
}
