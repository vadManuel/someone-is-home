package home.someoneshome.core

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Outcome
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** D-066. The gate above the rules, and the three events that demonstrated the bug. */
class AdmissionTest {

    private val seats = (0 until 8).map { Seat(it) }

    /** The exact three events that showed `armed` had two writers and no readers. */
    private fun theDemonstration(): List<Event> = listOf(
        Event.ContactMade(Tick(0), Seat(1), Seat(2)),
        Event.SubroutineReturned(Tick(1), Seat(3), MarkerId("m0"), listOf(0, 0)),
        Event.MeetingCalled(Tick(2), Seat(4), MeetingTrigger.MeetingCard),
    )

    private fun armed(): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1))),
    ).state

    @Test
    fun `the three events that emitted effects before a round existed are refused`() {
        for (event in theDemonstration()) {
            val admission = admit(GameState.EMPTY, event)
            assertIs<Admission.Refused>(admission, "${event::class.simpleName} was admitted pre-arm")
            assertEquals(RefusalReason.RoundNotArmed, admission.reason)
        }
    }

    /**
     * **The gate leaves no state and no effects behind.**
     *
     * A refusal that returned `Reduction(state, emptyList())` would be rule 1's forbidden shape
     * wearing a different name — the absent effect is the signal. Refusing has to be a different
     * kind of answer, not a quieter one.
     */
    @Test
    fun `a refusal carries no reduction at all`() {
        val admission = admit(GameState.EMPTY, theDemonstration().first())
        assertIs<Admission.Refused>(admission)
    }

    /** `RoundArmed` is the event that creates the round, so it is always admitted. */
    @Test
    fun `arming is admitted from an empty state`() {
        val admission = admit(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1))),
        )
        assertIs<Admission.Admitted>(admission)
        assertTrue(admission.reduction.state.armed)
    }

    /** Inside a live round the gate is transparent — the rules see exactly what they saw before. */
    @Test
    fun `inside a live round the gate changes nothing`() {
        val state = armed()
        for (event in theDemonstration()) {
            val admission = admit(state, event)
            assertIs<Admission.Admitted>(admission, "${event::class.simpleName} refused mid-round")
            assertEquals(reduce(state, event).effects, admission.reduction.effects)
        }
    }

    /** The other publicly observable round-state. D-068's second safe reason. */
    @Test
    fun `events arriving after the round has ended are refused`() {
        val finished = armed().endRound(Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared))
        for (event in theDemonstration()) {
            val admission = admit(finished, event)
            assertIs<Admission.Refused>(admission, "${event::class.simpleName} admitted after the end")
            assertEquals(RefusalReason.RoundAlreadyEnded, admission.reason)
        }
    }

    /**
     * **`reduce` never learned the flag exists**, which is what makes the branch provably outside
     * every client-visible path rather than merely reviewed as being outside one.
     */
    @Test
    fun `the rules still process a pre-arm event identically when called directly`() {
        assertTrue(
            reduce(GameState.EMPTY, theDemonstration()[2]).effects.isNotEmpty(),
            "reduce has grown an armed check; the gate is no longer the only refusal and rule 1's " +
                "forbidden shape may have been reintroduced inside the rules",
        )
    }
}
