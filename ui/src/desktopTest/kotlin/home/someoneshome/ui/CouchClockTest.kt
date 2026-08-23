package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.MeetingPhase

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **The couch's clock, which is the couch's whole meeting** (D-134).
 *
 * *During a meeting, the discussion and vote timers plus the live vote.* Two timers, and the out
 * have **one screen** to show them on: the living walk Notice → Discussion → Vote → Tally and are
 * told which phase they are in by which screen they are standing on, while a ghost sits still and
 * watches all four go past underneath the same view.
 *
 * So `GhostMeeting` is the one surface in the game whose clock changes meaning without the screen
 * changing, and before this it did not know: it said **VOTING ENDS IN** through every phase,
 * counting down a window that had not opened while the room was still talking. That is a small
 * thing to look at and a bad thing to sit behind — the couch is the audience with nothing to do but
 * read the screen, and a clock that is wrong for ninety seconds teaches them to stop trusting it
 * for the forty-five that matter.
 */
@OptIn(ExperimentalTestApi::class)
class CouchClockTest {

    /**
     * **Each phase names its own clock, and the check-in has none to name.**
     *
     * D-104's gate closes when the last player walks in, not when a clock runs out, so there is no
     * window for a bar to be a fraction of — and a bar drawn against no window would be a phone
     * predicting a moment only the house can see.
     */
    @Test
    fun theCouchsClockIsThePhaseTheHouseOpened() {
        val expected = mapOf(
            MeetingPhase.CheckIn to "WAITING FOR THE HOUSE",
            MeetingPhase.Discussion to "DISCUSSION ENDS IN",
            MeetingPhase.Vote to "VOTING ENDS IN",
            MeetingPhase.Tally to "LIGHTS OUT IN",
        )
        assertEquals(
            MeetingPhase.entries.toSet(), expected.keys,
            "a phase was added and the couch has no word for it — the out would sit under a clock " +
                "labelled with somebody else's phase",
        )
        for ((phase, label) in expected) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(
                                screen = ScreenId.GhostMeeting,
                                outBy = OutBy.Revoked,
                                meetingPhase = phase,
                            )
                        )
                    }
                }
                onAllNodesWithText(label).assertCountEquals(1)
                // And no other phase's label is on the screen at the same time.
                for (other in expected.values.filter { it != label }) {
                    onAllNodesWithText(other).assertCountEquals(0)
                }
            }
        }
    }

    /**
     * The clock behind the label is the phase's window, and the check-in genuinely has none.
     *
     * `Countdown.NONE` is what a screen with no clock resolves to, so the assertion is on
     * [Countdowns.onGhostMeeting] rather than on what the screen made of a null.
     */
    @Test
    fun theCheckInHasNoWindowAndTheOtherThreeDo() {
        assertNull(
            Countdowns.onGhostMeeting(MeetingPhase.CheckIn),
            "the couch was given a countdown for a gate that closes on the last player walking in",
        )
        for (phase in listOf(MeetingPhase.Discussion, MeetingPhase.Vote, MeetingPhase.Tally)) {
            assertNotNull(
                Countdowns.onGhostMeeting(phase),
                "$phase runs on a house clock and the couch is not shown it (D-134)",
            )
        }
    }

    /**
     * **A phase with no window draws no readout and no bar** — caught by rendering, not reasoning.
     *
     * The first version of this wiring drew the block unconditionally, and the check-in frame came
     * out with a lone `0` above a bar with nothing lit in it. Nothing failed; it renders, it is
     * plausible, and it says the exact opposite of what is true — a clock that has **run out**
     * rather than a gate waiting on a person still walking. On a screen whose reader has nothing to
     * do but read it, that is a lie with an audience.
     *
     * The three phases that do have a window are asserted beside it, so the fix cannot be *draw no
     * clock ever*.
     */
    @Test
    fun aPhaseWithNoWindowDrawsNoClock() {
        for (phase in MeetingPhase.entries) {
            val vals = PanelVals(
                PanelState(
                    screen = ScreenId.GhostMeeting,
                    outBy = OutBy.Revoked,
                    meetingPhase = phase,
                )
            )
            assertEquals(
                phase != MeetingPhase.CheckIn, vals.hasMeetingClock,
                "$phase disagrees with its own window about whether the couch has a clock",
            )
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(
                                screen = ScreenId.GhostMeeting,
                                outBy = OutBy.Revoked,
                                meetingPhase = phase,
                            )
                        )
                    }
                }
                onAllNodesWithText(vals.countdown.text)
                    .assertCountEquals(if (phase == MeetingPhase.CheckIn) 0 else 1)
            }
        }
    }

    /**
     * **With no house attached the couch resolves to the vote, and draws the design's own hand at
     * the ruled window** (D-163).
     *
     * `VOTING ENDS IN 0:24` is the design's drawn moment and is kept. The bar under it is not a
     * second number: it is that moment as a fraction of the window, and the window is now forty-
     * five, so twelve lit segments of thirty became **sixteen**. The picture was redrawn rather
     * than preserved, which is the ruling — re-cutting `0:24` to `0:18` to hold the old bar would
     * have moved the artifact into the field beside it.
     */
    @Test
    fun withNoPhasePushedTheCouchDrawsTheDesignsOwnVoteClock() {
        val drawn = assertNotNull(Countdowns.onGhostMeeting(MeetingPhase.Vote))
        assertEquals("0:24", drawn.text)
        assertEquals(16, drawn.litOf(PanelVals.VOTE_SEGMENTS), "the ghost meeting bar moved")
        assertEquals(
            drawn.text,
            PanelVals(PanelState(screen = ScreenId.GhostMeeting, outBy = OutBy.Revoked))
                .countdown.text,
            "a couch with no phase pushed resolves somewhere other than the vote",
        )
    }

    /**
     * **One clock, one number** — the couch's ballot and the living's are the same window, at rest
     * and in play alike (D-163, D-145).
     *
     * The couch and the living room are twelve feet apart and can see each other, so two bars
     * draining the same ballot at different rates is a defect somebody reads off another player's
     * phone. Asserted in both directions — a bench with no house, and a house that sent a window —
     * because the fixture is where the two numbers drifted apart the first time.
     *
     * The living's row is `Balance.VOTE_WINDOW_SECONDS`, so this fails if either row is ever given
     * a literal of its own again, whichever of the two it is.
     */
    @Test
    fun theCouchAndTheLivingCountTheSameBallot() {
        assertEquals(
            Countdowns.windowOf(ScreenId.Vote),
            Countdowns.windowOf(ScreenId.GhostMeeting),
            "the couch's ballot window and the living's disagree with no house attached",
        )
        assertEquals(
            Balance.VOTE_WINDOW_SECONDS,
            Countdowns.windowOf(ScreenId.GhostMeeting),
            "the couch's ballot is not the window the rules arm a round against",
        )
        for (sent in listOf(30, 45, 60)) {
            assertEquals(
                Countdowns.windowOf(ScreenId.Vote, sent),
                Countdowns.windowOf(ScreenId.GhostMeeting, sent),
                "a host who moved the ballot to ${sent}s moved one of the two bars and not the other",
            )
        }
    }
}
