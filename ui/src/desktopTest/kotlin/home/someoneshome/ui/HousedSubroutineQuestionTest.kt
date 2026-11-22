package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.JamRings
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.ReplayPattern
import home.someoneshome.model.ShortHold
import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A housed phone draws the question the house dealt, and the bench's fixture only where there is
 * no house** (D-238; `HouseNoticeTest`'s pattern, one family over).
 *
 * The bug: every Subroutine screen drew the companion's fixture — one parity grid, one wiring, one
 * set of columns, four Replay dots and no flash — while `SubroutineInstance.parameters` arrived on
 * every scan and nothing in `ui` read it. So every phone in a round drew the same sample puzzle and
 * the house graded each entry against a question the player had never been shown. Measured against
 * the real rules before the fix: of 25 Replays answered from the screen none was graded right, and
 * none of 12 Parity Checks, 11 Deallocates or 17 Signal Traces (the commit carries the tally).
 *
 * `ui` cannot see `core`, so what is asserted here is the client half: the house's parameters, as
 * they arrive in `RoundMessage.ScanAnswered`, are what the model holds and what the pixels show.
 * `SubroutineQuestionWiringTest` in `app` is the other half — a house-drawn instance over a real
 * socket, answered from what the phone holds, graded by the real rules.
 */
@OptIn(ExperimentalTestApi::class)
class HousedSubroutineQuestionTest {

    private val card = MarkerId("card-study")

    /**
     * **One question of each kind, every one of them different from the bench's** — each drawn by
     * the house's own draw in a seeded round (copied from that round's log), so none is a shape the
     * house could not deal. Different in what the screen draws: a Replay of five dots against the
     * bench's four, Handshake's four beats against five, another odd cell, other columns, another
     * lattice, another band, another lane, another ask, the ring opening on the other side.
     */
    private val dealt: Map<SubroutineKind, List<Int>> = mapOf(
        SubroutineKind.Replay to listOf(3, 0, 4, 2, 1),
        SubroutineKind.Interrupt to listOf(32, 7, 16, 119),
        SubroutineKind.ParityCheck to listOf(7),
        SubroutineKind.Sniff to listOf(3, 6, 10),
        SubroutineKind.Deallocate to listOf(7, 1, 7, 1, 5),
        SubroutineKind.Drift to listOf(345, 11, 9, 6),
        SubroutineKind.Short to listOf(4),
        SubroutineKind.SignalTrace to listOf(267),
        SubroutineKind.Jam to listOf(9),
        SubroutineKind.Handshake to listOf(5, 5, 6),
    )

    /** A housed phone whose house has issued one line of [kind] at [card] and answered its scan. */
    private fun housedAndDealt(kind: SubroutineKind, parameters: List<Int>): FlowModel {
        val phone = FlowModel(
            PanelState(screen = ScreenId.Scan, houseAttached = true),
            subroutines = SubroutineModel(),
        )
        phone.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    OrderLine.Known(
                        index = 0,
                        work = AssignedWork.Subroutine(kind),
                        done = false,
                        at = listOf(Destination(card, shape = "diamond", room = "STUDY")),
                    ),
                ),
            ),
        )
        phone.scanning(card)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, parameters)))
        phone.actions().beginSubroutine()
        return phone
    }

    /**
     * **What a screen of [kind] draws its question from**, as plain values a test can compare. The
     * same projection read off the model and off the reader, so equality is *the model holds the
     * house's question*. Sniff has nothing drawn: its question is the motor's (`HouseBuzzer`).
     */
    private fun drawnFrom(model: SubroutineModel, kind: SubroutineKind): Any = when (kind) {
        SubroutineKind.Replay -> listOf(model.pattern.order, model.replay.length)
        SubroutineKind.Interrupt -> model.sweep.let { listOf(it.bandAt, it.bandHalf, it.speed, it.phase) }
        SubroutineKind.ParityCheck -> model.grid
        SubroutineKind.Sniff -> Unit
        SubroutineKind.Deallocate -> listOf(model.columns, model.deallocate.columns)
        SubroutineKind.Drift -> model.path.let { listOf(it.lane, it.cover, it.speed, it.askAtMillis, it.hitRadius) }
        SubroutineKind.Short -> model.hold.fingers
        SubroutineKind.SignalTrace -> model.wiring.let { listOf(it.nodes, it.edges, it.source, it.sink) }
        SubroutineKind.Jam -> model.rings.opening
        SubroutineKind.Handshake -> model.handshake.length
        // No screen draws a Memory Dump: its two screens are STOPPED for the owner (D-322).
        SubroutineKind.MemoryDump -> Unit
    }

    /** The same projection, read straight off the reader the house drew against. */
    private fun asked(kind: SubroutineKind, p: List<Int>): Any = when (kind) {
        SubroutineKind.Replay -> ReplayPattern.of(p).order.let { listOf(it, it.size) }
        SubroutineKind.Interrupt -> InterruptSweep.of(p).let { listOf(it.bandAt, it.bandHalf, it.speed, it.phase) }
        SubroutineKind.ParityCheck -> ParityGrid.of(p)
        SubroutineKind.Sniff -> Unit
        SubroutineKind.Deallocate -> DotColumns.of(p).let { listOf(it, it.size) }
        SubroutineKind.Drift -> DriftPath.of(p).let { listOf(it.lane, it.cover, it.speed, it.askAtMillis, it.hitRadius) }
        SubroutineKind.Short -> ShortHold.of(p).fingers
        SubroutineKind.SignalTrace -> SignalGraph.of(p).let { listOf(it.nodes, it.edges, it.source, it.sink) }
        SubroutineKind.Jam -> JamRings.of(p).opening
        SubroutineKind.Handshake -> HandshakeRhythm.of(p).beats
        SubroutineKind.MemoryDump -> Unit
    }

    private fun render(state: PanelState, model: SubroutineModel, atMillis: Long = 0): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 300, height = 650) {
            mainClock.autoAdvance = false
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(state, subroutines = model) } }
            mainClock.advanceTimeByFrame()
            if (atMillis > 0) mainClock.advanceTimeBy(atMillis)
            image = onRoot().captureToImage().toAwtImage()
        }
        return image!!
    }

    private fun differing(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) if (a.getRGB(x, y) != b.getRGB(x, y)) count++
        }
        return count
    }

    /** **GUARD: on a housed phone, BEGIN deals the house's parameters into the model the screen reads.** */
    @Test
    fun aHousedPhoneHoldsTheQuestionTheHouseDealt() {
        // ⚠️ Every kind but Memory Dump, whose two screens are STOPPED for the owner (D-322): it has
        // no roster row, so a phone opens nothing over it, and that is asserted rather than skipped.
        assertEquals(SubroutineKind.entries.toSet() - SubroutineKind.MemoryDump, dealt.keys, "a kind has no dealt question here")
        assertEquals(null, Subroutine.ofOrNull(SubroutineKind.MemoryDump), "Memory Dump grew a screen: give it a dealt question here")
        for ((kind, parameters) in dealt) {
            val phone = housedAndDealt(kind, parameters)
            val subroutine = Subroutine.of(kind)
            assertEquals(subroutine.screen, phone.state.screen, "$kind: BEGIN did not open the dealt work")
            assertEquals(
                asked(kind, parameters), drawnFrom(phone.subroutines, kind),
                "$kind: the housed phone holds a question the house did not deal",
            )
            if (kind != SubroutineKind.Sniff) {
                assertNotEquals(
                    drawnFrom(SubroutineModel(), kind), drawnFrom(phone.subroutines, kind),
                    "$kind: the dealt question is the bench's fixture, so this test proves nothing",
                )
            }
        }
    }

    /**
     * **GUARD: the PIXELS are the house's question** — a housed phone's screen differs from the
     * bench's fixture screen for every kind that draws one, and the Insider's is the Resident's.
     *
     * This is the half the model check above cannot see: a screen that went back to reading
     * `SubroutineModel.PARITY` would leave the model right and the glass wrong. Sniff draws nothing
     * from its question (it is buzzed), so for Sniff only the role parity is asserted.
     */
    @Test
    fun theScreenDrawsTheHousesQuestionAndTheFakeDrawsTheSame() {
        // Every kind is checked before anything fails, so one broken screen cannot hide another.
        val faults = mutableListOf<String>()
        for ((kind, parameters) in dealt) {
            val phone = housedAndDealt(kind, parameters)
            val resident = render(phone.state.copy(role = PanelRole.Resident), phone.subroutines)
            val insider = render(phone.state.copy(role = PanelRole.Insider), phone.subroutines)
            if (differing(resident, insider) != 0) {
                faults += "$kind: the Insider's fake draws the house's question differently from the Resident's"
            }
            if (kind == SubroutineKind.Sniff) continue
            val bench = render(PanelState(screen = phone.state.screen), SubroutineModel())
            if (differing(resident, bench) == 0) {
                faults += "$kind: a housed phone dealt $parameters drew exactly the bench's fixture"
            }
        }
        assertEquals(emptyList(), faults)
    }

    /** **GUARD: a housed phone the house dealt nothing opens no Subroutine, however BEGIN is pressed.** */
    @Test
    fun aHousedPhoneWithNoDealtQuestionOpensNothing() {
        for (subroutine in Subroutine.built) {
            val housed = FlowModel(PanelState(screen = ScreenId.ScanCaught, houseAttached = true))
            housed.beginSubroutine(subroutine)
            assertEquals(
                ScreenId.ScanCaught, housed.state.screen,
                "${subroutine.label} opened on a housed phone with no question dealt — the bench's fixture, in a round",
            )
        }
        // And the bench still opens its own, which is what the render harness draws.
        val bench = FlowModel(PanelState(screen = ScreenId.ScanCaught, houseAttached = false))
        bench.beginSubroutine(Subroutine.ParityCheck)
        assertEquals(ScreenId.SubParity, bench.state.screen, "the unhoused bench can no longer open its fixture")
        assertEquals(SubroutineModel.PARITY, bench.subroutines.grid, "the bench stopped drawing its own grid")
    }

    /** A question this client cannot draw opens nothing: the phone stays on the scan. */
    @Test
    fun aQuestionTheClientCannotDrawOpensNothing() {
        val phone = housedAndDealt(SubroutineKind.Replay, listOf(0, 0, 1))
        assertEquals(ScreenId.Scan, phone.state.screen, "a malformed question moved the phone")
        assertNull(phone.state.current, "a malformed question became a caught scan")
    }

    /**
     * **GUARD: Replay flashes the house's pattern, in order, once** (D-238).
     *
     * The schedule's pure function is pinned dot by dot, and the pixels are checked to light during
     * each flash, to light differently for different dots, and to go back to the opening frame once
     * the playback has run.
     */
    @Test
    fun replayFlashesTheHousesPatternInOrder() {
        val order = listOf(3, 0, 4, 2, 1)
        assertNull(replayFlashing(order, 0), "something flashed before the lead-in was over")
        order.forEachIndexed { k, dot ->
            val lit = REPLAY_LEAD_MILLIS + k * REPLAY_STEP_MILLIS + REPLAY_LIT_MILLIS / 2
            assertEquals(dot, replayFlashing(order, lit), "flash $k is not the pattern's dot $dot")
            val dark = REPLAY_LEAD_MILLIS + k * REPLAY_STEP_MILLIS + (REPLAY_LIT_MILLIS + REPLAY_STEP_MILLIS) / 2
            assertNull(replayFlashing(order, dark), "flash $k never went out")
        }
        assertNull(replayFlashing(order, replayPlayedMillis(order) + 1), "the playback ran past its end")

        val phone = housedAndDealt(SubroutineKind.Replay, order)
        val opening = render(phone.state, phone.subroutines)
        val frames = order.indices.map { k ->
            render(phone.state, phone.subroutines, REPLAY_LEAD_MILLIS + k * REPLAY_STEP_MILLIS + REPLAY_LIT_MILLIS / 2)
        }
        frames.forEachIndexed { k, frame ->
            assertTrue(differing(opening, frame) > 0, "flash $k lit nothing on the glass")
        }
        assertTrue(differing(frames[0], frames[1]) > 0, "two different dots flashed as the same picture")
        val after = render(phone.state, phone.subroutines, replayPlayedMillis(order) + 500)
        assertEquals(0, differing(opening, after), "a dot is still lit after the playback")
    }
}
