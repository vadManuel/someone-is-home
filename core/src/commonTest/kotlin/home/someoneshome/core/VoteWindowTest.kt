package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The ballot's length: set in the lobby, locked at arming, published when the ballot opens**
 * (D-117, `gdd.md:412`, `:1006`).
 *
 * The host's VOTING row has drawn a control since U6 and moved a number nothing read: `LobbyLink`
 * had no arm, `LobbyBody` had no arming case, and `Event.RoundArmed` carried no settings — so the
 * design's *45 seconds, host-changeable* was a sentence with no mechanism under it, and every phone
 * counted the ballot down over whatever its own table said.
 *
 * This is the mechanism, held at both ends: **the round remembers what it was armed with**, and
 * **the push that opens the ballot is the one place that number is read**.
 */
class VoteWindowTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val home = (0 until 8).map { MarkerId("m$it") }

    private fun armed(window: Int): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1)),
            markers = placed(home), voteWindowSeconds = window,
        ),
    ).state

    /** Everybody walks in and the talk closes, which is what opens the ballot. */
    private fun openTheBallot(start: GameState): List<Effect> {
        var state = start
        val emitted = mutableListOf<Effect>()
        fun feed(event: Event) {
            val admission = admit(state, event)
            if (admission is Admission.Admitted) {
                state = admission.reduction.state
                emitted += admission.reduction.effects
            }
        }
        feed(Event.MeetingCalled(Tick(100), Seat(0), MeetingTrigger.MeetingCard))
        seats.forEach { feed(Event.MeetingCheckedIn(Tick(101), it)) }
        feed(Event.DiscussionClosed(Tick(103)))
        return emitted
    }

    /**
     * **THE INJECTION: change the setting, and the clock the house publishes moves with it.**
     *
     * Every window the lobby's control offers, so a build that honoured one number and ignored the
     * rest fails rather than passing on the default — which is what a hard-coded 45 would do.
     */
    @Test
    fun `the ballot opens with the window the round was armed with`() {
        for (window in listOf(30, 45, 60, 90)) {
            val opened = openTheBallot(armed(window))
                .filterIsInstance<Effect.MeetingPhaseOpened>()
                .single { it.phase == MeetingPhase.Vote }
            assertEquals(
                window, opened.voteWindowSeconds,
                "the house armed a ${window}s ballot and opened a ${opened.voteWindowSeconds}s one",
            )
        }
    }

    /**
     * **The other three phases carry no window, and that is what stops the field being read as
     * *this phase's clock*.**
     *
     * The discussion's ninety seconds and the tally's fifteen are the design's own constants and
     * nothing gave them a sender in this unit; the check-in has no window at all, because D-104's
     * gate closes when the last player walks in. A field that were null on two phases that *do*
     * have clocks would be read as *no clock* by the first client to try, and the discussion's
     * countdown would come off the screen.
     */
    @Test
    fun `no phase but the vote publishes a window`() {
        val opened = openTheBallot(armed(45)).filterIsInstance<Effect.MeetingPhaseOpened>()
        for (push in opened.filterNot { it.phase == MeetingPhase.Vote }) {
            assertNull(
                push.voteWindowSeconds,
                "${push.phase} published a ballot window; the field is the ballot's and no other " +
                    "phase's",
            )
        }
    }

    /**
     * **A round armed without a setting runs the design's own number.**
     *
     * Empty is not the fail-closed direction here: a nought-second ballot is not a cautious round,
     * it is a meeting nobody can vote at, arriving in a dark house with the whole party standing in
     * one room.
     */
    @Test
    fun `a round armed with no setting opens the design's ballot`() {
        val state = reduce(
            GameState.EMPTY,
            Event.RoundArmed(
                Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1)),
                markers = placed(home),
            ),
        ).state
        assertEquals(Balance.VOTE_WINDOW_SECONDS, state.voteWindowSeconds)
        assertEquals(
            Balance.VOTE_WINDOW_SECONDS,
            openTheBallot(state)
                .filterIsInstance<Effect.MeetingPhaseOpened>()
                .single { it.phase == MeetingPhase.Vote }
                .voteWindowSeconds,
        )
    }

    /**
     * **It survives the whole round, because balance values lock at arming.**
     *
     * A second meeting opens the same ballot as the first. Nothing mid-round may move it — the
     * setting is a fact about the evening the host configured, stamped into the recording, and a
     * round whose ballot got longer between meetings would replay as a different round.
     */
    @Test
    fun `a second meeting opens the same ballot as the first`() {
        var state = armed(30)
        fun meet(from: Long): Int {
            val emitted = mutableListOf<Effect>()
            fun feed(event: Event) {
                val admission = admit(state, event)
                if (admission is Admission.Admitted) {
                    state = admission.reduction.state
                    emitted += admission.reduction.effects
                }
            }
            feed(Event.MeetingCalled(Tick(from), Seat(0), MeetingTrigger.MeetingCard))
            seats.forEach { feed(Event.MeetingCheckedIn(Tick(from + 1), it)) }
            feed(Event.DiscussionClosed(Tick(from + 3)))
            val window = emitted.filterIsInstance<Effect.MeetingPhaseOpened>()
                .single { it.phase == MeetingPhase.Vote }.voteWindowSeconds
            // Close it, so the next call is not refused as a second meeting.
            feed(Event.VoteWindowClosed(Tick(from + 4)))
            feed(Event.TallyHalfwayReached(Tick(from + 5)))
            feed(Event.MeetingClosed(Tick(from + 6)))
            return window!!
        }
        assertEquals(30, meet(100))
        assertEquals(30, meet(500))
    }
}
