package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What one phone can say at a meeting, and everything it cannot.**
 *
 * The meeting is the part of this game with the most buttons on it and the least for them to do.
 * I AM HERE, READY TO VOTE and LOCK IN all look like commits and none of them is one: what follows
 * each depends on every phone in the house — the check-in gate closes when every living player and
 * every out player is standing there (D-104), the talk skips ahead only on a *unanimous* READY,
 * the ballot is read when the window closes — and a phone cannot count phones.
 *
 * The failure this guards against is not a crash. It is a phone that walks itself forward on its
 * own button press, arrives at a result screen the house has not sent, and shows one player a
 * meeting the other five are not at.
 */
class MeetingTest {

    // ---- Echo, and its limits -----------------------------------------------------------------

    /**
     * **The three readiness controls report one phone and move nothing.**
     *
     * Read off the graph rather than off a screen, because this is the property that has to hold
     * for all of them at once: three buttons on three screens, each of which would look perfectly
     * reasonable wired to the next screen along.
     */
    @Test
    fun theReadinessControlsWalkNowhere() {
        val reporting = mapOf(
            ScreenId.Assemble to "I AM HERE — the check-in gate is every phone's (D-104)",
            ScreenId.Ghost2 to "I AM HERE, from outside the system — the same gate",
            ScreenId.Discussion to "READY TO VOTE — unanimous skips ahead, and unanimous is a count",
            ScreenId.Vote to "LOCK IN — the ballot is read when the window closes",
        )
        for ((screen, why) in reporting) {
            assertEquals(
                emptySet(), ScreenGraph.exitsOf(screen),
                "$screen publishes a way onward. $why",
            )
            assertNull(
                Flow.viaActions[screen],
                "$screen declares an actions-layer edge; these controls walk no edge at all",
            )
            // ...which makes the house's own transition the only way off, so it had better exist.
            assertTrue(
                Flow.autoAdvance.containsKey(screen),
                "$screen has no control and no fall-through: the meeting stops there for good",
            )
        }
    }

    /** A tap on a name lights that name, and moving it moves it. The vote stays changeable. */
    @Test
    fun theVoteIsThePlayersToChangeUntilTheClockEnds() {
        val meeting = MeetingModel.sample()

        meeting.choose(VoteChoice.Named("DANI"))
        assertTrue(meeting.holds("DANI"))
        assertFalse(meeting.holds("MARCUS"), "two rows are lit at once")

        meeting.choose(VoteChoice.Named("ROSE"))
        assertTrue(meeting.holds("ROSE"))
        assertFalse(meeting.holds("DANI"))

        // Restraining nobody is a choice, not the absence of one.
        meeting.choose(VoteChoice.Skip)
        assertTrue(meeting.skipping)
        assertFalse(meeting.holds("ROSE"))
    }

    /**
     * **LOCK IN hands over what is on the screen, and changing it afterwards unlocks it.**
     *
     * Not a flag, because a flag would keep saying LOCKED IN over a row the house has never heard
     * of. The vote is changeable until the clock ends and the button has to tell the truth about
     * which version of it the house is holding.
     */
    @Test
    fun lockingInIsAboutWhatWasSentNotAboutBeingFinished() {
        val meeting = MeetingModel.sample()
        assertFalse(meeting.locked, "the fixture is chosen and not yet handed over")

        meeting.lockIn()
        assertTrue(meeting.locked)

        meeting.choose(VoteChoice.Named("ROSE"))
        assertFalse(meeting.locked, "the button still claimed the house held the old vote")

        meeting.lockIn()
        assertTrue(meeting.locked)
    }

    /**
     * Locking in nothing does nothing.
     *
     * Not voting is already a Skip and SKIP is already a row (D-075). A button that handed over an
     * empty vote would be a third way to say the same thing and the only one with nothing on
     * screen to show for it.
     */
    @Test
    fun anEmptyVoteIsNotHandedOver() {
        val meeting = MeetingModel(names = listOf("PRIYA"))
        meeting.lockIn()
        assertNull(meeting.handedOver)
        assertFalse(meeting.locked, "an empty vote read as locked in")
    }

    /**
     * **Nothing this phone does moves the house's counts.**
     *
     * The numbers on these screens — *4 OF 6 CHECKED IN*, *3 OF 6 READY*, *4 OF 6 VOTED* — are
     * the authority's, and the temptation to increment one on your own press is exactly the
     * temptation to predict an outcome. It would even look right, for as long as the network took
     * to disagree.
     */
    @Test
    fun aPhoneSayingItIsReadyDoesNotMoveTheRoomsNumbers() {
        val meeting = MeetingModel.sample()
        val before = meeting.counts

        meeting.checkIn()
        meeting.sayReady()
        meeting.choose(VoteChoice.Named("DANI"))
        meeting.lockIn()

        assertEquals(before, meeting.counts, "one phone's press moved the house's counts")
        assertEquals("4 OF 6", meeting.counts.ofSeats(meeting.counts.present))
    }

    // ---- A new meeting -------------------------------------------------------------------------

    /**
     * **A new meeting starts with nothing said.**
     *
     * A round holds several meetings and the model outlives all of them. Without this, a check-in
     * made at the first is still lit at the second — a phone telling a player they are already
     * standing at the meeting area they have this second been called to, which is precisely the
     * moment they most need to be told to move.
     */
    @Test
    fun everyWayIntoAMeetingClearsWhatWasSaidAtTheLastOne() {
        for (start in MeetingModel.STARTS) {
            val model = FlowModel(PanelState(screen = ScreenId.Home))
            model.checkIn()
            model.sayReady()
            model.chooseVote(VoteChoice.Named("DANI"))
            model.lockInVote()

            model.push(start)

            assertFalse(model.meeting.checkedIn, "$start kept the last meeting's check-in")
            assertFalse(model.meeting.ready, "$start kept the last meeting's READY")
            assertNull(model.meeting.choice, "$start kept the last meeting's vote")
            assertNull(model.meeting.handedOver, "$start kept the last meeting's handed-over vote")
        }
    }

    /**
     * ...and walking anywhere else does not clear it. The check-in belongs to the meeting, and the
     * meeting is five screens long — a player who checks in and is then moved to the notices has
     * not un-checked-in.
     */
    @Test
    fun walkingOnThroughTheMeetingKeepsWhatWasSaid() {
        val model = FlowModel(PanelState(screen = ScreenId.Call))
        model.checkIn()
        for (next in listOf(ScreenId.Assemble, ScreenId.Notice, ScreenId.Discussion, ScreenId.Vote)) {
            model.push(next)
            assertTrue(model.meeting.checkedIn, "arriving at $next threw the check-in away")
        }
    }

    /** Being pushed onto the screen you are already on is not a new meeting. */
    @Test
    fun theHouseReassertingAScreenIsNotANewMeeting() {
        val model = FlowModel(PanelState(screen = ScreenId.Call))
        model.checkIn()
        model.push(ScreenId.Call)
        assertTrue(
            model.meeting.checkedIn,
            "a re-asserted view cleared the check-in; D-097 makes that an ordinary delivery",
        )
    }

    // ---- The two sides of the ballot ----------------------------------------------------------

    /**
     * **A living phone's meeting state cannot carry attribution, structurally.**
     *
     * The living see a count; only a player outside the system sees who cast what (D-075). That is
     * held by [OutsideView] being a different type rather than a field somebody decided not to
     * read — a nulled or unused field is one refactor away from being drawn, and the failure mode
     * is a screen quietly showing six people who voted for whom.
     *
     * The rendering half of this claim is `MeetingDisclosureTest`.
     */
    @Test
    fun theGhostsBallotIsCountedRatherThanWrittenDown() {
        assertEquals(5, OutsideView.ballots.size)
        assertEquals(3, OutsideView.cast, "VOTES CAST is counted off the rows, not typed beside them")
        assertEquals(
            2, OutsideView.ballots.count { it.forWhom == null },
            "still deciding is a missing target, not the words STILL DECIDING in a fixture",
        )
    }
}
