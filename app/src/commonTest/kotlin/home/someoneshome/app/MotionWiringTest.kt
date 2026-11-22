package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.DrivenMotion
import home.someoneshome.platform.NoPermissions
import home.someoneshome.platform.PermittedMotion
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.SubroutineModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The motion budget, wired as the app root wires it** (M2, D-321): the panel's `onMeasuring`
 * through [measuringWith] to a sensor behind the MOTION & FITNESS grant, the sensor driven by hand.
 *
 * Granted, the sensor runs while a Subroutine or the station is open, and walking away closes the
 * Subroutine as STOP NOW does and frees the station. **Refused, nothing is sampled**: the budget
 * holds however far the phone is carried, and the phone says nothing it would not have said.
 */
class MotionWiringTest {

    private val study = MarkerId("card-study")

    private class Phone(grant: Grant) {
        val sent = mutableListOf<IntentMessage>()
        val permissions = NoPermissions().apply { says(DevicePermission.Motion, grant) }
        val sensor = DrivenMotion()
        var model: FlowModel? = null
        init {
            model = FlowModel(
                PanelState(screen = ScreenId.Home, houseAttached = true),
                send = { sent += it },
                subroutines = SubroutineModel(send = { sent += it }),
                onMeasuring = measuringWith(PermittedMotion.of(sensor, permissions)) { model },
            )
        }
        val flow: FlowModel get() = model!!

        fun walk(samples: Int) = repeat(samples) { sensor.feed(0.12, 0.15, 0.05) }
    }

    private fun Phone.openJam() {
        flow.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Jam), false, listOf(Destination(study, "diamond", "STUDY")))),
            ),
        )
        flow.scanning(study)
        flow.houseSaid(
            RoundMessage.ScanAnswered(SubroutineInstance(0, SubroutineKind.Jam, SubroutineModel.JAM_PARAMETERS)),
        )
        flow.actions().beginSubroutine()
        assertEquals(ScreenId.SubJam, flow.state.screen)
    }

    @Test
    fun `granted walking away closes the Subroutine and the sensor stops`() {
        val phone = Phone(Grant.Granted)
        assertFalse(phone.sensor.running, "the sensor ran on the springboard")
        phone.openJam()
        assertTrue(phone.sensor.running, "the sensor did not start on the Subroutine")
        phone.sent.clear()
        phone.walk(2_000)
        assertEquals(ScreenId.Work, phone.flow.state.screen, "walking away did not close the Subroutine")
        assertEquals(listOf<IntentMessage>(IntentMessage.StopSubroutine), phone.sent)
        assertFalse(phone.sensor.running, "the sensor outlived the Subroutine")
    }

    @Test
    fun `granted walking away from the station frees it`() {
        val phone = Phone(Grant.Granted)
        phone.flow.houseSaid(RoundMessage.TerminalAnswered(reading = true))
        assertTrue(phone.sensor.running)
        phone.walk(2_000)
        assertEquals(ScreenId.Home, phone.flow.state.screen)
        assertEquals(listOf<IntentMessage>(IntentMessage.LeaveTerminal), phone.sent)
        assertFalse(phone.sensor.running)
    }

    @Test
    fun `refused and nothing is sampled and the budget holds`() {
        for (grant in listOf(Grant.Refused, Grant.Unasked, Grant.Asked, Grant.Unavailable)) {
            val phone = Phone(grant)
            phone.openJam()
            assertFalse(phone.sensor.running, "$grant: the sensor started")
            phone.sent.clear()
            phone.walk(10_000)
            assertEquals(ScreenId.SubJam, phone.flow.state.screen, "$grant: the phone was walked off without a sensor")
            assertEquals(1.0, phone.flow.motion.left, "$grant: the budget moved")
            assertEquals(emptyList(), phone.sent, "$grant: the refusal said something")

            phone.flow.actions().stopSubroutine()
            phone.flow.houseSaid(RoundMessage.TerminalAnswered(reading = true))
            phone.walk(10_000)
            assertEquals(ScreenId.TermLive, phone.flow.state.screen, "$grant: the reader was walked off without a sensor")
        }
    }
}
