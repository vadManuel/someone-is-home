package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * **An Egress opening over the caught scan or the scan keeps the screen, with the alert over it**
 * (the owner's 2026-11-19 ruling on D-297's questions, D-299).
 *
 * *The alert must never take the camera away from someone standing at a card.* Before this both
 * screens took the push: the phone went to `Banner`, the springboard, so a person at a card lost the
 * scan's window (and the camera with it) or the briefing before BEGIN, and had to scan again. Every
 * case is walked for a Resident and an Insider, and the two phones are compared field for field,
 * because the screen is the only thing the alert may ask about.
 */
class EgressOverScanTest {

    private val study = MarkerId("card-study")
    private val west = MarkerId("west-card")

    private fun egressOpened() = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(west, "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    private fun answered(kind: SubroutineKind = SubroutineKind.Jam) =
        RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind)))

    private class Phone(role: PanelRole) {
        val sent = mutableListOf<IntentMessage>()
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            send = { sent += it },
            subroutines = SubroutineModel(send = { sent += it }),
        )
    }

    /** A housed phone of [role] with one Jam dealt at [study], on the springboard. */
    private fun dealt(role: PanelRole): Phone {
        val phone = Phone(role)
        phone.model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    OrderLine.Known(
                        index = 0,
                        work = AssignedWork.Subroutine(SubroutineKind.Jam),
                        done = false,
                        at = listOf(Destination(study, shape = "diamond", room = "STUDY")),
                    ),
                ),
            ),
        )
        return phone
    }

    /** SCAN tapped: the lit screen is up and its window is running. */
    private fun scanning(role: PanelRole): Phone = dealt(role).also {
        it.model.go(ScreenId.Scan)
        assertEquals(ScreenId.Scan, it.model.state.screen)
    }

    /** The card read and the house's answer in: the briefing, before BEGIN. */
    private fun caught(role: PanelRole): Phone = scanning(role).also {
        it.model.scanning(study)
        it.model.houseSaid(answered())
        assertEquals(ScreenId.ScanCaught, it.model.state.screen, "$role: the scan caught nothing")
    }

    private fun Phone.panel(): PanelState = model.state.copy(role = PanelRole.Resident)

    private val screens: List<Pair<ScreenId, (PanelRole) -> Phone>> = listOf(
        ScreenId.Scan to ::scanning,
        ScreenId.ScanCaught to ::caught,
    )

    @Test
    fun `the alert drops over the scan and the caught scan and the screen stays for either role`() {
        for ((screen, at) in screens) {
            val panels = PanelRole.entries.map { role ->
                val phone = at(role)
                val before = phone.model.state
                phone.model.houseSaid(egressOpened())

                val state = phone.model.state
                assertEquals(screen, state.screen, "$role: the Egress took the phone off the $screen")
                assertTrue(state.egressAlertUp, "$role: no alert over the $screen")
                assertEquals(before.current, state.current, "$role: the $screen's row was dropped")
                assertEquals(before.scannedAt, state.scannedAt, "$role: the card read on the $screen was forgotten")
                val arrival = assertNotNull(PanelVals(state).arrival, "$role: no alert drawn over the $screen")
                assertEquals(NotificationKind.Egress, arrival.notification.kind)
                assertEquals("CONTAIN AT UTILITY AND LANDING", arrival.notification.detail)
                assertEquals(screen, arrival.under, "$role: a swipe would leave the $screen")
                assertEquals(Presentation.Banner, arrival.presentation)
                assertTrue(Notifications.dims(arrival), "$role: the alert over the $screen does not dim as D-118's Egress does")
                assertTrue(state.egressRunning, "$role: the Egress is not running on this phone")
                phone.panel()
            }
            assertEquals(1, panels.toSet().size, "$screen: the alert differs by role")
        }
    }

    @Test
    fun `a swipe and the ten seconds take the alert down and leave the screen`() {
        for ((screen, at) in screens) {
            for (role in PanelRole.entries) {
                val swiped = at(role)
                swiped.model.houseSaid(egressOpened())
                swiped.model.actions().dismissNotification()
                assertEquals(screen, swiped.model.state.screen, "$role: the swipe left the $screen")
                assertFalse(swiped.model.state.egressAlertUp, "$role: the alert outlived its swipe on the $screen")
                assertNull(PanelVals(swiped.model.state).arrival, "$role: something is still drawn over the $screen")
                assertTrue(swiped.model.state.egressRunning, "$role: the swipe ended the Egress on this phone")

                val lapsed = at(role)
                lapsed.model.houseSaid(egressOpened())
                lapsed.model.egressAlertLapsed()
                assertEquals(screen, lapsed.model.state.screen, "$role: the hold walked the phone off the $screen")
                assertNull(PanelVals(lapsed.model.state).arrival, "$role: the alert outlived its ten seconds on the $screen")
                assertEquals(swiped.panel(), lapsed.panel(), "$role: the swipe and the hold leave the $screen differently")
            }
        }
    }

    @Test
    fun `a tap on the alert opens the Egress widget for either role`() {
        for ((screen, at) in screens) {
            val panels = PanelRole.entries.map { role ->
                val phone = at(role)
                phone.model.houseSaid(egressOpened())
                val opens = assertNotNull(PanelVals(phone.model.state).arrival?.notification?.opens, "$role: the alert over the $screen is not a door")
                assertEquals(ScreenId.EgressWidget, opens)
                // What the banner's tap does ([NotificationBanner]).
                phone.model.actions().nav(opens)
                assertEquals(ScreenId.EgressWidget, phone.model.state.screen, "$role: the tap did not open the widget from the $screen")
                assertFalse(phone.model.state.egressAlertUp, "$role: the alert followed the phone to the widget")
                phone.panel()
            }
            assertEquals(1, panels.toSet().size, "$screen: the tap lands differently by role")
        }
    }

    @Test
    fun `a card read under the alert is answered as any other and the briefing opens the work`() {
        for (role in PanelRole.entries) {
            // The read lands: the caught scan, with the house's row, and the alert down with the scan.
            val phone = scanning(role)
            phone.model.houseSaid(egressOpened())
            phone.model.scanning(study)
            phone.model.houseSaid(answered())
            val state = phone.model.state
            assertEquals(ScreenId.ScanCaught, state.screen, "$role: the read under the alert reached no briefing")
            assertFalse(state.egressAlertUp, "$role: the alert followed the phone onto the briefing")
            assertEquals(Subroutine.Jam, state.current?.subroutine, "$role: the briefing has no row")
            assertEquals(study, state.scannedAt)

            // An empty card under the alert is refused as any empty card is.
            val empty = scanning(role)
            empty.model.houseSaid(egressOpened())
            empty.model.scanning(MarkerId("card-hall"))
            empty.model.houseSaid(RoundMessage.ScanAnswered(null))
            assertEquals(ScreenId.ScanBad, empty.model.state.screen, "$role: the empty card under the alert was not refused")
            assertFalse(empty.model.state.egressAlertUp)

            // A node read under the alert is the tap control (D-295, E2), as it is without one.
            val node = scanning(role)
            node.model.houseSaid(egressOpened())
            node.model.scanning(west)
            node.model.houseSaid(RoundMessage.ScanAnswered(null))
            assertEquals(ScreenId.EgressWidget, node.model.state.screen, "$role: the node read under the alert missed the tap control")
            assertTrue(PanelVals(node.model.state).pulseLive, "$role: the tap control is not live")

            // BEGIN under the alert on the briefing opens the work the card dealt, and the entry files.
            val briefed = caught(role)
            briefed.model.houseSaid(egressOpened())
            briefed.model.actions().beginSubroutine()
            assertEquals(ScreenId.SubJam, briefed.model.state.screen, "$role: BEGIN under the alert opened nothing")
            assertFalse(briefed.model.state.egressAlertUp, "$role: the alert followed the phone into the work")
            val entry = briefed.model.subroutines.entry(Subroutine.Jam)
            briefed.model.actions().tapSubroutine(Subroutine.Jam, 1)
            assertSame(entry, briefed.model.subroutines.entry(Subroutine.Jam))
            briefed.model.actions().handOverSubroutine(Subroutine.Jam)
            val filed = briefed.sent.filterIsInstance<IntentMessage.HandOverEntry>()
            assertEquals(1, filed.size, "$role: the work opened under the alert never filed")
            assertEquals(study, filed.single().marker, "$role: the entry was filed against another card")
        }
    }

    /**
     * What the ruling does not name is left as it was: the springboard takes the banner, and a
     * refusal, which is a house-driving screen ([Flow.houseDriving]), is left alone by [FlowModel]'s
     * `arrived` and draws nothing. Pinned so a change to either is a decision and not a drift; the
     * refusal's case is raised in D-299.
     */
    @Test
    fun `the springboard still takes the banner and a refusal is left as it was`() {
        for (role in PanelRole.entries) {
            val refused = scanning(role)
            refused.model.scanning(MarkerId("card-hall"))
            refused.model.houseSaid(RoundMessage.ScanAnswered(null))
            assertEquals(ScreenId.ScanBad, refused.model.state.screen)
            refused.model.houseSaid(egressOpened())
            assertEquals(ScreenId.ScanBad, refused.model.state.screen, "$role: NOTHING FOR YOU HERE moved")
            assertFalse(refused.model.state.egressAlertUp)
            assertNull(PanelVals(refused.model.state).arrival, "$role: NOTHING FOR YOU HERE drew an alert")

            val home = dealt(role)
            home.model.houseSaid(egressOpened())
            assertEquals(ScreenId.Banner, home.model.state.screen, "$role: the springboard got no banner")
        }
    }

    @Test
    fun `the screens the alert keeps are the ten Subroutines the caught scan the scan and the report`() {
        val kept = ScreenId.entries.filter { Flow.keepsItsScreenUnderTheEgress(it) }.toSet()
        // And the report screen (D-307): the alert drops over the circle, which keeps filling.
        val expected = Subroutine.entries.mapNotNull { it.screen }.toSet() + ScreenId.ScanCaught + ScreenId.Scan + ScreenId.Report
        assertEquals(expected, kept)
    }
}
