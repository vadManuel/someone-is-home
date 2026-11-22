package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **M2's motion budget, and the two things it closes** (D-043, D-110, D-203, D-321).
 *
 * The accumulator drains on movement and holds when still; it calibrates on each opening against
 * the hand holding the phone; spent, it closes a Subroutine exactly as STOP NOW does and the
 * station exactly as BACK does, which frees the station; and every one of those is the same for an
 * Insider's phone as for a Resident's. Samples are fed by hand, as the desktop's sensor is.
 */
class MotionBudgetTest {

    private val study = MarkerId("card-study")

    /** A hand holding the phone still: tremor and breath, well under any limit. */
    private fun FlowModel.still(samples: Int) = repeat(samples) { motionSampled(0.004, 0.006, 0.003) }

    /** Walking away: a tenth of a g and more, every sample. */
    private fun FlowModel.walk(samples: Int) = repeat(samples) { motionSampled(0.12, 0.15, 0.05) }

    /** Walks until the phone leaves [screen], and says how many samples it took (or -1). */
    private fun FlowModel.walkOff(screen: ScreenId, cap: Int = 10_000): Int {
        var n = 0
        while (state.screen == screen && n < cap) {
            motionSampled(0.12, 0.15, 0.05)
            n++
        }
        return if (state.screen == screen) -1 else n
    }

    private class Phone(role: PanelRole, housed: Boolean = true) {
        val sent = mutableListOf<IntentMessage>()
        val measuring = mutableListOf<Boolean>()
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = housed),
            send = { sent += it },
            subroutines = SubroutineModel(send = { sent += it }),
            onMeasuring = { measuring += it },
        )
    }

    /** A housed phone of [role] on the [kind] the house dealt at [study], BEGIN pressed. */
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

    /** A housed phone of [role] the house has just let read the station. */
    private fun atTheStation(role: PanelRole): Phone {
        val phone = Phone(role)
        phone.model.houseSaid(RoundMessage.TerminalAnswered(reading = true))
        assertEquals(ScreenId.TermLive, phone.model.state.screen)
        return phone
    }

    // ---- The accumulator ----------------------------------------------------------------------

    @Test
    fun `the budget drains on movement and holds when still`() {
        val budget = MotionBudget()
        budget.begin(MotionAllowance.MEDIUM)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { budget.sample(0.004, 0.006, 0.003) }
        assertEquals(1.0, budget.left, "calibrating spent something")

        repeat(3_000) { budget.sample(0.004, 0.006, 0.003) }
        assertEquals(1.0, budget.left, "thirty seconds of a still hand spent the budget")

        repeat(20) { budget.sample(0.12, 0.15, 0.05) }
        val afterAStep = budget.left
        assertTrue(afterAStep < 1.0, "a fifth of a second of walking spent nothing")

        repeat(3_000) { budget.sample(0.004, 0.006, 0.003) }
        assertEquals(afterAStep, budget.left, "standing still again refilled it, or spent it")
        assertFalse(budget.gone)
    }

    @Test
    fun `calibration is the first half second and measures this hand`() {
        // Moving hard during the first half second spends nothing: it is the measurement.
        val budget = MotionBudget()
        budget.begin(MotionAllowance.SHORT)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { assertFalse(budget.sample(0.3, 0.3, 0.3)) }
        assertEquals(1.0, budget.left)

        // A shaky hand and a steady one at the same movement: the steady one is held to more.
        fun spentBy(tremor: Double, move: Double): Double {
            val b = MotionBudget()
            b.begin(MotionAllowance.CIRCUIT)
            repeat(MotionBudget.CALIBRATION_SAMPLES) { b.sample(tremor, 0.0, 0.0) }
            repeat(100) { b.sample(move, 0.0, 0.0) }
            return 1.0 - b.left
        }
        val steady = spentBy(tremor = 0.005, move = 0.07)
        val shaky = spentBy(tremor = 0.025, move = 0.07)
        assertTrue(steady > shaky, "a steadier hand was not held to a stricter limit: $steady against $shaky")

        // Scanning and walking straight off is caught: the limit has a ceiling.
        val walkedOff = MotionBudget()
        walkedOff.begin(MotionAllowance.SHORT)
        var spentAt = -1
        for (n in 0 until 1_000) {
            if (walkedOff.sample(0.2, 0.2, 0.1)) { spentAt = n; break }
        }
        assertTrue(spentAt > MotionBudget.CALIBRATION_SAMPLES, "walking from the first sample was never caught")
    }

    @Test
    fun `the allowance scales with the tier and a short one is the strictest`() {
        val tiers = SubroutineTier.entries.map { MotionAllowance.of(it) }
        assertEquals(tiers.sorted(), tiers, "a later tier is allowed less than an earlier one")
        assertTrue(MotionAllowance.of(SubroutineTier.Short) < MotionAllowance.of(SubroutineTier.Circuit))
        for (s in Subroutine.entries) assertEquals(MotionAllowance.of(s.tier), MotionAllowance.on(s.screen!!))
        assertEquals(MotionAllowance.STATION, MotionAllowance.on(ScreenId.TermLive))
        assertEquals(0.0, MotionAllowance.on(ScreenId.Home))
    }

    // ---- What it closes -----------------------------------------------------------------------

    @Test
    fun `passing the budget cancels the open Subroutine as STOP NOW does`() {
        for (kind in SubroutineKind.entries - SubroutineKind.MemoryDump) {
            val phone = atWork(PanelRole.Resident, kind)
            val screen = phone.model.state.screen
            phone.sent.clear()
            phone.model.still(MotionBudget.CALIBRATION_SAMPLES + 500)
            assertEquals(screen, phone.model.state.screen, "$kind: a still hand was walked off")
            assertTrue(phone.walkOffOk(screen), "$kind: walking away never closed it")
            assertEquals(ScreenId.Work, phone.model.state.screen, "$kind: the budget landed somewhere STOP NOW does not")
            assertEquals(listOf<IntentMessage>(IntentMessage.StopSubroutine), phone.sent, "$kind: the cancel said something STOP NOW does not")
            assertEquals(listOf(true, false), phone.measuring, "$kind: the sensor was not started and stopped once")
        }
    }

    private fun Phone.walkOffOk(screen: ScreenId): Boolean = model.walkOff(screen) > 0

    @Test
    fun `the cancel and STOP NOW leave the same phone`() {
        val walked = atWork(PanelRole.Resident).also { it.sent.clear() }
        walked.model.walkOff(ScreenId.SubJam)
        val pressed = atWork(PanelRole.Resident).also { it.sent.clear() }
        pressed.model.actions().stopSubroutine()
        assertEquals(pressed.sent, walked.sent)
        assertEquals(pressed.model.state, walked.model.state, "the motion cancel left a phone STOP NOW would not")
    }

    @Test
    fun `passing the budget at the station closes the view and frees the station`() {
        val live = atTheStation(PanelRole.Resident)
        live.model.still(MotionBudget.CALIBRATION_SAMPLES + 500)
        assertEquals(ScreenId.TermLive, live.model.state.screen, "a still reader was walked off the station")
        assertTrue(live.model.walkOff(ScreenId.TermLive) > 0, "walking away never closed the live view")
        assertEquals(ScreenId.Home, live.model.state.screen)
        assertEquals(listOf<IntentMessage>(IntentMessage.LeaveTerminal), live.sent, "the station was not freed once, actor only")

        // The playback is the same reading: one budget across the two, not one each.
        val playing = atTheStation(PanelRole.Resident)
        playing.model.still(MotionBudget.CALIBRATION_SAMPLES)
        playing.model.walk(40)
        val leftOnTheLiveView = playing.model.motion.left
        playing.model.go(ScreenId.Timelapse)
        assertEquals(leftOnTheLiveView, playing.model.motion.left, "the playback recalibrated a reading already under way")
        assertTrue(playing.model.walkOff(ScreenId.Timelapse) > 0, "walking away never closed the playback")
        assertEquals(ScreenId.Home, playing.model.state.screen)
        assertEquals(1, playing.sent.count { it == IntentMessage.LeaveTerminal }, "the playback's close did not free the station once")
        assertEquals(listOf(true, false), playing.measuring)
    }

    @Test
    fun `both roles are cancelled by the same samples into the same phone`() {
        val subroutine = PanelRole.entries.map { role ->
            val phone = atWork(role).also { it.sent.clear() }
            val took = phone.model.walkOff(ScreenId.SubJam)
            Triple(took, phone.sent.toList(), phone.model.state.copy(role = PanelRole.Resident))
        }
        assertEquals(1, subroutine.toSet().size, "the Subroutine's cancel differs by role: $subroutine")

        val station = PanelRole.entries.map { role ->
            val phone = atTheStation(role)
            val took = phone.model.walkOff(ScreenId.TermLive)
            Triple(took, phone.sent.toList(), phone.model.state.copy(role = PanelRole.Resident))
        }
        assertEquals(1, station.toSet().size, "the station's close differs by role: $station")
    }

    @Test
    fun `the bar steps down as it drains and is full on every opening`() {
        val phone = atWork(PanelRole.Resident)
        assertEquals(1f, phone.model.motionBar.left.floatValue)
        phone.model.still(MotionBudget.CALIBRATION_SAMPLES)
        phone.model.walk(60)
        val partway = phone.model.motionBar.left.floatValue
        assertTrue(partway < 1f && partway > 0f, "the bar did not drain partway: $partway")
        phone.model.walkOff(ScreenId.SubJam)
        assertEquals(1f, phone.model.motionBar.left.floatValue, "the bar outlived the opening it drew")
    }

    @Test
    fun `nothing samples where the budget does not apply or on a phone with no house`() {
        val home = Phone(PanelRole.Resident)
        home.model.walk(5_000)
        assertEquals(ScreenId.Home, home.model.state.screen)
        assertFalse(home.model.motion.running)
        assertEquals(emptyList(), home.measuring)

        val unhoused = Phone(PanelRole.Resident, housed = false)
        unhoused.model.go(ScreenId.SubJam)
        unhoused.model.walk(5_000)
        assertEquals(ScreenId.SubJam, unhoused.model.state.screen, "a phone with no house was walked off a fixture")
        assertEquals(emptyList(), unhoused.measuring)

        // And samples that arrive after the phone left are nothing.
        val left = atWork(PanelRole.Resident)
        left.model.actions().stopSubroutine()
        left.sent.clear()
        left.model.walk(5_000)
        assertEquals(ScreenId.Work, left.model.state.screen)
        assertEquals(emptyList(), left.sent, "a late sample reported something")
    }
}
