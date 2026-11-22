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
 * **An Egress opening over a Subroutine drops its alert over the work and leaves the work where it
 * is** (the owner's E1 ruling, D-297).
 *
 * Before this the alert was a screen (`Banner`, the springboard with the banner over it), so a
 * person mid-Subroutine was pushed off the work, the trail was cleared and the banner fell through
 * to Home: the entry they had made was unreachable and a rescan dealt a new question. Every case is
 * walked for a Resident's work and an Insider's fake, and the two phones are compared field for
 * field, because the screen is the only thing the alert may ask about.
 */
class EgressOverSubroutineTest {

    private val study = MarkerId("card-study")

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

    private class Phone(role: PanelRole) {
        val sent = mutableListOf<IntentMessage>()
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            send = { sent += it },
            subroutines = SubroutineModel(send = { sent += it }),
        )
    }

    /** A housed phone of [role], on the Subroutine the house dealt at [study], with an entry begun. */
    private fun atWork(role: PanelRole, kind: SubroutineKind = SubroutineKind.Jam): Phone {
        val phone = Phone(role)
        phone.model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    OrderLine.Known(
                        index = 0,
                        work = AssignedWork.Subroutine(kind),
                        done = false,
                        at = listOf(Destination(study, shape = "diamond", room = "STUDY")),
                    ),
                ),
            ),
        )
        phone.model.scanning(study)
        phone.model.houseSaid(
            RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))),
        )
        phone.model.actions().beginSubroutine()
        assertEquals(Subroutine.of(kind).screen, phone.model.state.screen, "$role: BEGIN did not open the $kind")
        return phone
    }

    /** Everything on the panel but the role, so two phones can be compared at one moment. */
    private fun Phone.panel(): PanelState = model.state.copy(role = PanelRole.Resident)

    @Test
    fun `the alert drops over every Subroutine and the screen and the work stay for either role`() {
        // Memory Dump has no screen yet (D-322, STOPPED).
        for (kind in SubroutineKind.entries - SubroutineKind.MemoryDump) {
            val panels = PanelRole.entries.map { role ->
                val phone = atWork(role, kind)
                val subroutine = Subroutine.of(kind)
                val entry = phone.model.subroutines.entry(subroutine)
                val current = phone.model.state.current
                phone.model.houseSaid(egressOpened())

                val state = phone.model.state
                assertEquals(subroutine.screen, state.screen, "$role: the Egress took the phone off the $kind")
                assertSame(entry, phone.model.subroutines.entry(subroutine), "$role: the $kind's entry was replaced")
                assertEquals(current, state.current, "$role: the $kind's row was dropped")
                assertEquals(study, state.scannedAt, "$role: the card the work was opened at was forgotten")
                val arrival = assertNotNull(PanelVals(state).arrival, "$role: no alert over the $kind")
                assertEquals(NotificationKind.Egress, arrival.notification.kind)
                assertEquals("CONTAIN AT UTILITY AND LANDING", arrival.notification.detail, "$role: the alert names other rooms")
                assertEquals(subroutine.screen, arrival.under, "$role: a swipe would leave the $kind")
                assertEquals(Presentation.Banner, arrival.presentation)
                assertTrue(Notifications.dims(arrival), "$role: the alert over the $kind does not dim as D-118's Egress does")
                phone.panel()
            }
            assertEquals(1, panels.toSet().size, "$kind: the alert over the work differs by role")
        }
    }

    @Test
    fun `the swipe takes the alert down and leaves the work and the entry is handed over and filed`() {
        val panels = PanelRole.entries.map { role ->
            val phone = atWork(role)
            val actions = phone.model.actions()
            actions.tapSubroutine(Subroutine.Jam, 1)
            val entry = phone.model.subroutines.entry(Subroutine.Jam)
            phone.model.houseSaid(egressOpened())
            actions.dismissNotification()

            assertEquals(ScreenId.SubJam, phone.model.state.screen, "$role: the swipe left the Jam")
            assertNull(PanelVals(phone.model.state).arrival, "$role: the alert outlived its swipe")
            assertFalse(phone.model.state.egressAlertUp)
            assertSame(entry, phone.model.subroutines.entry(Subroutine.Jam))
            assertTrue(entry!!.touched, "$role: the tap made before the Egress was lost")
            assertTrue(phone.model.state.egressRunning, "$role: the swipe ended the Egress on this phone")

            actions.handOverSubroutine(Subroutine.Jam)
            val filed = phone.sent.filterIsInstance<IntentMessage.HandOverEntry>()
            assertEquals(1, filed.size, "$role: the entry never went up after the Egress opened")
            assertEquals(study, filed.single().marker, "$role: the entry was filed against another card")

            // The house's answer lands on the screen that sent it.
            phone.model.houseSaid(RoundMessage.SubroutineGraded(accepted = true))
            assertEquals(ScreenId.SubJam, phone.model.state.screen)
            assertNotNull(phone.model.state.verdict, "$role: the verdict did not land on the Jam")
            phone.panel()
        }
        assertEquals(1, panels.toSet().size, "the hand-over after the alert differs by role")
    }

    @Test
    fun `the ten seconds take it down and leaving the work by any route takes it down`() {
        for (role in PanelRole.entries) {
            val lapsed = atWork(role)
            lapsed.model.houseSaid(egressOpened())
            lapsed.model.egressAlertLapsed()
            assertEquals(ScreenId.SubJam, lapsed.model.state.screen, "$role: the hold walked the phone off the Jam")
            assertNull(PanelVals(lapsed.model.state).arrival, "$role: the alert outlived its ten seconds")

            val stopped = atWork(role)
            stopped.model.houseSaid(egressOpened())
            stopped.model.actions().stopSubroutine()
            assertEquals(ScreenId.Work, stopped.model.state.screen, "$role: STOP landed somewhere else")
            assertFalse(stopped.model.state.egressAlertUp, "$role: the alert followed the phone off the Jam")
            assertNull(PanelVals(stopped.model.state).arrival)
            assertTrue(IntentMessage.StopSubroutine in stopped.sent, "$role: STOP did not tell the house")

            // From the work order the widget is one tap away, as it is for everyone.
            stopped.model.go(ScreenId.Home)
            assertTrue(PanelVals(stopped.model.state).egressRunning, "$role: the springboard has no Egress widget")
        }
    }

    @Test
    fun `off a Subroutine the alert is still the banner over the springboard`() {
        for (role in PanelRole.entries) {
            val phone = Phone(role)
            phone.model.houseSaid(egressOpened())
            assertEquals(ScreenId.Banner, phone.model.state.screen, "$role: the springboard got no banner")
            assertFalse(phone.model.state.egressAlertUp, "$role: the springboard took the Subroutine's alert")

            val atTheList = Phone(role)
            atTheList.model.go(ScreenId.Work)
            atTheList.model.houseSaid(egressOpened())
            assertEquals(ScreenId.Banner, atTheList.model.state.screen, "$role: the work order got no banner")
        }
    }
}
