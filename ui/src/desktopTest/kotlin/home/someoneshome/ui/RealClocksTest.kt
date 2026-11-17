package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A housed phone shows real times and real countdowns; a phone with no house shows the design's**
 * (D-243; the owner on two iPhones, 2026-11-16: *is the clock fake?? can we stop having fake things
 * in the app*).
 *
 * Every time on the glass was the mockup's: the strip's `21:07`, the lantern's `21:04` and
 * `SATURDAY 16 AUGUST`, the house thread's `20:58`-`21:02`, the endings' `21:38`, and every meeting
 * clock and the Egress's stood still at the drawn moment. Now the phone's own wall clock (injected
 * here) and the house's deadlines draw them on a housed phone, and the unhoused render harness keeps
 * the drawn values so its pictures still compare against the mockup ([HouseNoticeTest]'s pattern).
 */
@OptIn(ExperimentalTestApi::class)
class RealClocksTest {

    /** A clock the test owns. Monday 16 November, 22:13, until moved. */
    private var now = WallTime(hour = 22, minute = 13, weekday = 1, day = 16, month = 11)

    private fun housed(screen: ScreenId = ScreenId.Home): FlowModel =
        FlowModel(PanelState(screen = screen), wallClock = { now }).also { it.houseAttached(true) }

    /** Every piece of text [state] draws, with [notifications] standing on the lock screen. */
    private fun drawn(state: PanelState, notifications: NotificationsModel = NotificationsModel()): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(state, notifications = notifications) } }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }

    // ---- 1 · The wall clock ---------------------------------------------------------------------

    /** **GUARD: the strip draws the phone's own time on a housed phone, and moves with it.** */
    @Test
    fun aHousedStripDrawsThePhonesOwnTimeAndMovesWithIt() {
        val phone = housed()
        assertEquals("22:13", PanelVals(phone.state).clock)
        val texts = drawn(phone.state)
        assertTrue("22:13" in texts, "the strip did not draw the phone's time: $texts")
        assertTrue("21:07" !in texts, "a housed strip drew the design's time: $texts")

        now = now.copy(minute = 14)
        phone.wallClockRead()
        assertEquals("22:14", PanelVals(phone.state).clock, "the strip did not follow the phone's clock")
    }

    /** **GUARD: the lantern draws the phone's own time and date on a housed phone.** */
    @Test
    fun aHousedLanternDrawsThePhonesOwnTimeAndDate() {
        val phone = housed(ScreenId.Lock)
        val texts = drawn(phone.state, phone.notifications)
        assertTrue("22:13" in texts, "the lantern did not draw the phone's time: $texts")
        assertTrue("MONDAY 16 NOVEMBER" in texts, "the lantern did not draw the phone's date: $texts")
        assertTrue("21:04" !in texts && "SATURDAY 16 AUGUST" !in texts, "a housed lantern drew the design's: $texts")
        assertTrue(texts.none { "21:01" in it || "21:03" in it }, "a housed lantern held the design's standing texts: $texts")
    }

    /** And the render harness still draws the design's times, so it still compares to the mockup. */
    @Test
    fun aPhoneWithNoHouseStillDrawsTheDesignsTimes() {
        assertTrue("21:07" in drawn(PanelState(screen = ScreenId.Home)))
        val lock = drawn(PanelState(screen = ScreenId.Lock))
        assertTrue("21:04" in lock && "SATURDAY 16 AUGUST" in lock, "the harness lost the lantern's drawn time: $lock")
        assertTrue(lock.any { "21:01" in it }, "the harness lost the lantern's standing texts: $lock")
        // A clock handed down changes nothing without a house.
        val unhoused = FlowModel(PanelState(screen = ScreenId.Home), wallClock = { now })
        unhoused.wallClockRead()
        assertEquals("21:07", PanelVals(unhoused.state).clock)
    }

    /** A housed phone handed no clock prints the dash, never a drawn time. */
    @Test
    fun aHousedPhoneWithNoClockPrintsTheDash() {
        val vals = PanelVals(PanelState(screen = ScreenId.Lock, houseAttached = true))
        assertEquals(PanelVals.NO_READING, vals.clock)
        assertEquals(PanelVals.NO_READING, vals.lockClock)
        assertEquals(PanelVals.NO_READING, vals.lockDate)
    }

    // ---- 2 · Arrival times --------------------------------------------------------------------------

    /**
     * **GUARD: every house text carries the time it landed** — the opening rows and the role's line
     * at LIGHTS OUT, a later text at its own arrival, on the thread, the inbox row and the lock screen.
     */
    @Test
    fun aHousedThreadCarriesEachTextsArrivalTime() {
        now = now.copy(hour = 21, minute = 50)
        val phone = housed()
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Long))
        now = now.copy(hour = 22, minute = 5)
        phone.houseSaid(RoundMessage.MessageDelivered("Do not run."))

        val vals = PanelVals(phone.state)
        assertEquals(
            listOf("21:50", "21:50", "21:50", "22:05"), vals.houseThread.map { it.first },
            "the thread's times are not the arrivals: ${vals.houseThread}",
        )
        assertEquals("Do not run.", vals.houseThread.last().second)
        assertTrue(vals.houseThread.none { it.second == Notifications.text.body }, "a housed thread held a text no house sent")
        assertEquals("22:05", vals.inbox.first().at, "the house's row is not its newest arrival")
        assertEquals("22:05", phone.notifications.standing.first().at, "the lock screen's text was not stamped")

        val thread = drawn(phone.state.copy(screen = ScreenId.RevealThread))
        assertTrue("21:50" in thread && "22:05" in thread, "the thread screen did not draw the arrivals: $thread")
        assertTrue(listOf("20:58", "20:59", "21:01", "21:02").none { it in thread }, "a housed thread drew a design time: $thread")
    }

    /** The unhoused thread is the design's, times and the 21:01 line included. */
    @Test
    fun anUnhousedThreadIsTheDesigns() {
        val thread = PanelVals(PanelState()).houseThread
        assertEquals(listOf("20:58", "20:59", "21:01", "21:02"), thread.map { it.first })
    }

    /** **GUARD: the endings print the time the ending landed and the Egress the house ran.** */
    @Test
    fun aHousedEndingPrintsWhenItLanded() {
        val phone = housed()
        // The sign-off is the house's word to the people it owned, so it is drawn on an Insider's.
        phone.houseSaid(RoundMessage.RoleRevealed(insider = true))
        phone.houseSaid(RoundMessage.EgressOpened(EgressType.Tether, NODES, remaining = 2_400, haptic = Haptic.Long))
        now = now.copy(hour = 22, minute = 30)
        phone.houseSaid(RoundMessage.RoundEnded(Winner.Insiders, WinRoute.EgressUncontained, Haptic.Long))
        phone.houseSaid(RoundMessage.HouseSignedOff("Unfortunate."))
        assertEquals(ScreenId.WinInsiders, phone.state.screen)
        val vals = PanelVals(phone.state)
        assertEquals("22:30", vals.endedAt)
        assertEquals("22:30 . TETHER", vals.egressEndedLine)
        val texts = drawn(phone.state)
        assertTrue("22:30 . TETHER" in texts, "the ending did not print when it landed: $texts")
        assertTrue(texts.count { it == "22:30" } >= 2, "the strip and the sign-off did not both carry the ending's time: $texts")
        assertTrue(texts.none { "21:38" in it }, "a housed ending drew the design's time: $texts")

        val port = PanelVals(PanelState(screen = ScreenId.WinInsiders))
        assertEquals("21:38 . BEACON", port.egressEndedLine, "the harness lost the ending's drawn line")
    }

    // ---- 3 · The house's deadlines --------------------------------------------------------------------

    /**
     * **GUARD: each meeting phase's clock starts at the house's window and counts down from the push**,
     * and the gate and the shift between meetings have none.
     */
    @Test
    fun meetingClocksFollowTheHousesDeadlines() {
        val phone = housed(ScreenId.Notice)
        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.CheckIn, null, Haptic.Short))
        assertNull(phone.state.secondsLeft, "the gate ran a clock")

        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Discussion, null, Haptic.Short))
        assertEquals(90, phone.state.secondsLeft)
        repeat(10) { phone.secondPassed() }
        assertEquals("1:20", PanelVals(phone.state.copy(screen = ScreenId.Discussion)).countdown.text)

        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Vote, 30, Haptic.Short))
        assertEquals(30, phone.state.secondsLeft, "the ballot did not start at the window the house sent")
        repeat(5) { phone.secondPassed() }
        assertEquals("0:25", PanelVals(phone.state.copy(screen = ScreenId.Vote)).countdown.text)
        assertEquals(
            "0:25", PanelVals(phone.state.copy(screen = ScreenId.GhostMeeting, outBy = OutBy.Revoked)).countdown.text,
            "the couch and the hallway count different ballots",
        )

        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Tally, null, Haptic.Short))
        repeat(6) { phone.secondPassed() }
        assertEquals("9", PanelVals(phone.state.copy(screen = ScreenId.Tally)).countdown.text)
        repeat(60) { phone.secondPassed() }
        assertEquals(0, phone.state.secondsLeft, "the clock ran past zero")

        phone.houseSaid(RoundMessage.MeetingEnded(Haptic.Long))
        assertNull(phone.state.secondsLeft, "the meeting's clock outlived the meeting")
    }

    /** **GUARD: the Egress countdown is the house's `remaining`, and HELD stops it.** */
    @Test
    fun theEgressCountdownFollowsTheHousesRemaining() {
        val phone = housed()
        phone.houseSaid(RoundMessage.EgressOpened(EgressType.Beacon, NODES, remaining = 2_400, haptic = Haptic.Long))
        assertEquals(Countdown(120, 120), phone.state.egressLeft)
        repeat(30) { phone.secondPassed() }
        val running = PanelVals(phone.state)
        assertEquals("1:30", running.egressLeftText)
        assertEquals(24, running.egressLit, "the bar is not the clock's fraction")
        assertEquals("75%", running.egressPercent)

        phone.houseSaid(RoundMessage.EgressHeld(remaining = 1_000, running = false, haptic = Haptic.Short))
        repeat(10) { phone.secondPassed() }
        assertEquals(Countdown(50, 120), phone.state.egressLeft, "a held Egress went on counting")

        phone.houseSaid(RoundMessage.EgressHeld(remaining = 999, running = true, haptic = Haptic.Short))
        assertEquals(50, phone.state.egressLeft?.secondsLeft, "the restart rounded the house's ticks down")
        phone.secondPassed()
        assertEquals(49, phone.state.egressLeft?.secondsLeft)

        phone.houseSaid(RoundMessage.EgressContained(Haptic.Short))
        assertNull(phone.state.egressLeft)
    }

    /** The harness keeps the drawn clocks, and a housed phone with none draws no drawn moment. */
    @Test
    fun unhousedClocksAreTheDesignsAndHousedOnesAreNever() {
        val port = PanelVals(PanelState(screen = ScreenId.EgressWidget))
        assertEquals("1:42", port.egressLeftText)
        assertEquals(22, port.egressLit)
        assertEquals("1:04", PanelVals(PanelState(screen = ScreenId.Discussion)).countdown.text)

        val housed = PanelVals(PanelState(screen = ScreenId.EgressWidget, houseAttached = true))
        assertEquals(PanelVals.NO_READING, housed.egressLeftText)
        assertEquals(0, housed.egressLit)
        val talk = PanelVals(PanelState(screen = ScreenId.Discussion, houseAttached = true)).countdown
        assertEquals(0, talk.secondsLeft, "a housed discussion with no clock drew the design's moment")
    }

    /**
     * **GUARD: `FlowHost` is the second hand** — a housed phone's discussion clock moves with real
     * (test) time, from the push, and stops at zero; nothing else moves it.
     */
    @Test
    fun flowHostCountsTheHousesClockDown() {
        val phone = housed(ScreenId.Notice)
        runDesktopComposeUiTest(width = 600, height = 1300) {
            mainClock.autoAdvance = false
            setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(phone) } }
            mainClock.advanceTimeByFrame()
            phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Discussion, null, Haptic.Short))
            mainClock.advanceTimeByFrame()
            val standing = phone.state.screen
            repeat(3) { mainClock.advanceTimeBy(1_000L) }
            mainClock.advanceTimeByFrame()
            val left = phone.state.secondsLeft
            assertTrue(left != null && left in 86..87, "three seconds of FlowHost left the talk at $left")
            assertEquals(standing, phone.state.screen, "the clock moved the screen")
            repeat(100) { mainClock.advanceTimeBy(1_000L) }
            mainClock.advanceTimeByFrame()
            assertEquals(0, phone.state.secondsLeft, "the clock ran past zero or stopped short")
            assertEquals(standing, phone.state.screen, "the clock running out moved the screen")
        }
    }

    private companion object {
        val NODES = listOf(
            Destination(MarkerId("card-a"), "diamond", "PANTRY"),
            Destination(MarkerId("card-b"), "cross", "ATTIC"),
        )
    }
}
