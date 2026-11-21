package home.someoneshome.ui

import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-307: a report runs only from the main view, and takes it over with the circle** (the owner's
 * 2026-11-20 rulings and clarification).
 *
 * - **Where the phone says it is** ([MainView]): page 1 or 2 (a banner over the page included), or
 *   the report screen, and nowhere else: no Subroutine, scan, briefing, Egress widget, meeting or
 *   lantern. This is the character every upload carries, and the house's hold starts and runs only
 *   on it.
 * - **The report screen**: shown only from page 1 or 2, on the house's pending slot; back to the
 *   page it came from on a cancel or an idle slot; ended by the meeting's call.
 * - **The circle** is read off the slot's held time on this phone's own clock (D-243, D-269).
 * - **Both roles take one path**: an Insider's panel and a Resident's are equal at every step.
 */
class ReportScreenTest {

    /** A housed phone of [role] on [screen], whose monotonic clock is [now]. */
    private class Phone(role: PanelRole = PanelRole.Resident, screen: ScreenId = ScreenId.Home) {
        var now = 50_000L
        val model = FlowModel(
            PanelState(screen = screen, role = role, houseAttached = true),
            monotonicMillis = { now },
        )
        val fill: Float get() = model.report.fill.floatValue
    }

    /** Every screen that is an activity, or a meeting: off the main view, each of them. */
    private val activities: List<ScreenId> =
        Subroutine.built.mapNotNull { it.screen } +
            listOf(
                ScreenId.Scan, ScreenId.ScanCaught, ScreenId.EgressWidget,
                ScreenId.Calling, ScreenId.Call, ScreenId.Found, ScreenId.Assemble, ScreenId.Notice,
                ScreenId.Discussion, ScreenId.Vote, ScreenId.Tally,
                ScreenId.Work, ScreenId.Files, ScreenId.Lock, ScreenId.LockNotify, ScreenId.SurgeTargets,
                ScreenId.IsolateTargets, ScreenId.TermLive,
            )

    // ---- Where the phone says it is ----------------------------------------------------------

    @Test
    fun `the main view is page 1 or 2 or the report screen and nothing else`() {
        val on = ScreenId.entries.filter { MainView.isOn(PanelState(screen = it)) }.toSet()
        assertEquals(
            setOf(ScreenId.Home, ScreenId.Page2, ScreenId.Report, ScreenId.Banner, ScreenId.Notify, ScreenId.Quiet),
            on,
            "the main view is not the two pages, the report screen and a banner over page 1",
        )
        for (screen in activities) {
            assertFalse(MainView.isOn(PanelState(screen = screen)), "$screen counted as the main view")
        }
    }

    @Test
    fun `the lantern is not the main view and a report waits for SLIDE TO OPEN`() {
        for (lantern in listOf(ScreenId.Lock, ScreenId.LockNotify)) {
            val phone = Phone(screen = lantern)
            assertFalse(phone.model.onMainView, "$lantern counted as the main view")
            phone.model.reportSaid(ReportSlot.Pending(0))
            assertEquals(lantern, phone.model.state.screen, "$lantern: the report screen took over the lantern")
        }
        val phone = Phone(screen = ScreenId.Lock)
        phone.model.go(ScreenId.Home)
        assertTrue(phone.model.onMainView, "SLIDE TO OPEN did not reach the main view")
        phone.model.reportSaid(ReportSlot.Pending(0))
        assertEquals(ScreenId.Report, phone.model.state.screen)
    }

    @Test
    fun `the Egress alert over page 1 or over the report screen is the main view`() {
        assertTrue(MainView.isOn(PanelState(screen = ScreenId.Banner)))
        assertTrue(MainView.isOn(PanelState(screen = ScreenId.Report, egressAlertUp = true)))
    }

    @Test
    fun `the main view is the same answer for an Insider and a Resident on every screen`() {
        for (screen in ScreenId.entries) {
            assertEquals(
                MainView.isOn(PanelState(screen = screen, role = PanelRole.Resident)),
                MainView.isOn(PanelState(screen = screen, role = PanelRole.Insider)),
                "$screen: the main view differs by role",
            )
        }
    }

    // ---- The report screen, from page 1 or 2 only -----------------------------------------------

    @Test
    fun `a pending slot on page 1 or 2 takes the screen over and a cancel goes back to that page`() {
        for (page in listOf(ScreenId.Home, ScreenId.Page2)) {
            for (end in listOf(ReportSlot.Cancelled, ReportSlot.Idle)) {
                val phone = Phone(screen = page)
                phone.model.reportSaid(ReportSlot.Pending(0))
                assertEquals(ScreenId.Report, phone.model.state.screen, "$page: a pending slot did not take the screen over")
                assertEquals(page, phone.model.state.reportFrom)
                assertTrue(phone.model.onMainView, "$page: the report screen is not the main view")
                phone.model.reportSaid(ReportSlot.Pending(1_000))
                assertEquals(ScreenId.Report, phone.model.state.screen)
                phone.model.reportSaid(end)
                assertEquals(page, phone.model.state.screen, "$page: $end did not go back to the page it came from")
                assertNull(phone.model.state.reportFrom)
                assertEquals(0f, phone.fill, "$page: the circle outlived the screen")
            }
        }
    }

    @Test
    fun `a banner over page 1 is page 1 and the report leaves it behind`() {
        for (banner in listOf(ScreenId.Banner, ScreenId.Notify, ScreenId.Quiet)) {
            val phone = Phone(screen = banner)
            phone.model.reportSaid(ReportSlot.Pending(0))
            assertEquals(ScreenId.Report, phone.model.state.screen, "$banner: a banner over page 1 refused the report")
            phone.model.reportSaid(ReportSlot.Cancelled)
            assertEquals(ScreenId.Home, phone.model.state.screen, "$banner: the cancel did not land on page 1")
        }
    }

    @Test
    fun `the phone never shows the report screen from anywhere but page 1 or 2`() {
        for (screen in activities + ScreenId.entries.filter { !MainView.isOn(PanelState(screen = it)) }) {
            val phone = Phone(screen = screen)
            phone.model.reportSaid(ReportSlot.Pending(0))
            phone.model.reportSaid(ReportSlot.Pending(1_500))
            assertEquals(screen, phone.model.state.screen, "$screen: a pending slot took the phone to the report screen")
            phone.model.reportSaid(ReportSlot.Cancelled)
            assertEquals(screen, phone.model.state.screen, "$screen: a cancel moved a phone that was never reporting")
        }
    }

    @Test
    fun `an idle slot on page 1 moves nothing`() {
        val phone = Phone()
        repeat(3) { phone.model.reportSaid(ReportSlot.Idle) }
        assertEquals(ScreenId.Home, phone.model.state.screen)
    }

    // ---- The circle, on the phone's own clock ---------------------------------------------------

    @Test
    fun `the fill is the slot's held time on the phone's clock and runs smoothly between answers`() {
        val phone = Phone()
        phone.model.reportSaid(ReportSlot.Pending(500))
        assertEquals(0.25f, phone.fill, "the circle did not start at the time the house says was held")
        phone.now += 250
        phone.model.reportRead()
        assertEquals(0.375f, phone.fill, "the circle did not run on the phone's clock between answers")
        // The next answer, late by its travel, keeps the stamp: no step backwards.
        phone.now += 750
        phone.model.reportSaid(ReportSlot.Pending(1_400))
        assertEquals(0.75f, phone.fill, "an answer of the same hold restamped the circle")
        phone.now += 600
        phone.model.reportRead()
        assertEquals(1f, phone.fill)
        phone.now += 5_000
        phone.model.reportRead()
        assertEquals(1f, phone.fill, "the circle ran past full")
        assertEquals(ScreenId.Report, phone.model.state.screen, "a full circle moved the phone: the call is the house's")
    }

    @Test
    fun `a shorter held time is a new hold and the circle starts again from it`() {
        val phone = Phone()
        phone.model.reportSaid(ReportSlot.Pending(1_000))
        assertEquals(0.5f, phone.fill)
        phone.now += 1_000
        phone.model.reportSaid(ReportSlot.Pending(0))
        assertEquals(0f, phone.fill, "a hold begun again did not restart the circle")
    }

    @Test
    fun `a model with no clock of its own draws the held time and stands`() {
        val model = FlowModel(PanelState(screen = ScreenId.Page2, houseAttached = true))
        model.reportSaid(ReportSlot.Pending(1_000))
        assertEquals(0.5f, model.report.fill.floatValue)
        model.reportRead()
        assertEquals(0.5f, model.report.fill.floatValue)
    }

    // ---- What else can reach the report screen --------------------------------------------------

    private fun egressOpened() = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(MarkerId("west-card"), "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    @Test
    fun `an Egress drops its alert over the report screen and the circle keeps filling`() {
        val phone = Phone()
        phone.model.reportSaid(ReportSlot.Pending(0))
        phone.now += 500
        phone.model.houseSaid(egressOpened())
        assertEquals(ScreenId.Report, phone.model.state.screen, "the Egress took the report screen away")
        assertTrue(phone.model.state.egressAlertUp, "the Egress alert is not over the report screen")
        assertTrue(Notifications.dims(PanelVals(phone.model.state).arrival), "the alert over the report screen does not dim")
        phone.now += 500
        phone.model.reportSaid(ReportSlot.Pending(1_000))
        assertEquals(0.5f, phone.fill, "the circle stopped under the alert")
        assertTrue(phone.model.onMainView, "the alert over the report screen took the phone off the main view")
        // The swipe takes the alert down and leaves the circle.
        phone.model.dismissNotification()
        assertEquals(ScreenId.Report, phone.model.state.screen)
        assertFalse(phone.model.state.egressAlertUp)
    }

    @Test
    fun `the meeting's call ends the report screen and a slot after it moves nothing`() {
        for (trigger in listOf(MeetingTrigger.RevokeReported(Seat(3)), MeetingTrigger.MeetingCard)) {
            val phone = Phone(screen = ScreenId.Page2)
            phone.model.reportSaid(ReportSlot.Pending(1_000))
            phone.model.houseSaid(RoundMessage.MeetingOpened(Seat(2), trigger, Haptic.Long))
            val ring = if (trigger is MeetingTrigger.RevokeReported) ScreenId.Found else ScreenId.Call
            assertEquals(ring, phone.model.state.screen, "the call did not end the report screen")
            phone.model.reportSaid(ReportSlot.Idle)
            assertEquals(ring, phone.model.state.screen, "the idle slot after the call took the phone off the ring")
            assertFalse(phone.model.onMainView, "a ringing phone is on the main view")
        }
    }

    @Test
    fun `a text arriving during the report waits where its kind waits and does not take the circle`() {
        val phone = Phone()
        phone.model.reportSaid(ReportSlot.Pending(0))
        phone.model.houseSaid(RoundMessage.MessageDelivered("THE SOUTH DOOR IS UNLATCHED"))
        assertEquals(ScreenId.Report, phone.model.state.screen, "a text took the report screen away")
        assertEquals("THE SOUTH DOOR IS UNLATCHED", phone.model.notifications.standing.first().body, "the text was lost")
    }

    @Test
    fun `the report screen is house-driven and has no way back by a finger`() {
        assertTrue(ScreenId.Report in Flow.houseDriving)
        assertTrue(ScreenId.Report in Flow.housePushed)
        assertFalse(ScreenId.Report in Flow.backable)
        assertEquals(emptySet(), ScreenGraph.exitsOf(ScreenId.Report))
        val phone = Phone()
        phone.model.reportSaid(ReportSlot.Pending(0))
        assertFalse(phone.model.back(), "a finger stepped back off the report screen")
    }

    // ---- Role parity ----------------------------------------------------------------------------

    @Test
    fun `an Insider and a Resident take one path through the report`() {
        val resident = Phone(PanelRole.Resident, ScreenId.Page2)
        val insider = Phone(PanelRole.Insider, ScreenId.Page2)
        val steps: List<(Phone) -> Unit> = listOf(
            { it.model.reportSaid(ReportSlot.Pending(0)) },
            { it.now += 700; it.model.reportRead() },
            { it.model.reportSaid(ReportSlot.Pending(1_000)) },
            { it.model.houseSaid(egressOpened()) },
            { it.now += 300; it.model.reportSaid(ReportSlot.Cancelled) },
        )
        for ((i, step) in steps.withIndex()) {
            step(resident)
            step(insider)
            assertEquals(resident.model.state.copy(role = PanelRole.Insider), insider.model.state, "step $i: the panels differ by role")
            assertEquals(resident.fill, insider.fill, "step $i: the circles differ by role")
            assertEquals(resident.model.onMainView, insider.model.onMainView, "step $i: the main view differs by role")
        }
    }
}
