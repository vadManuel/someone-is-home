package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.HandshakeTaps
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A Handshake tapped on the screen goes to the house as the gaps between the taps, timed on this
 * phone's own monotonic clock, at one length for every role** (the owner's Q7 ruling, D-296).
 *
 * Driven through [PanelActions], the finger's own layer, on a Resident's phone and an Insider's,
 * with the clock the app root hands down standing in for the phone's. What each put on the wire is
 * read back: the gaps it tapped, and the bytes the request takes in the upload's slot.
 */
class HandshakeTimingTest {

    private val hall = MarkerId("card-hall")

    private val order: List<OrderLine> = listOf(
        OrderLine.Known(
            index = 0,
            work = AssignedWork.Subroutine(SubroutineKind.Handshake),
            done = false,
            at = listOf(Destination(hall, shape = "diamond", room = "HALL")),
        ),
    )

    private class Phone(role: PanelRole) {
        val sent = mutableListOf<IntentMessage>()
        var now = 81_442_017L
        val flow = FlowModel(
            PanelState(screen = ScreenId.Scan, role = role),
            subroutines = SubroutineModel(send = { sent += it }, monotonicMillis = { now }),
        )
        val handOvers get() = sent.filterIsInstance<IntentMessage.HandOverEntry>()
    }

    /** A phone of [role] scans the hall, opens the bench's Handshake, and taps it [after] apart. */
    private fun tapped(role: PanelRole, after: List<Long>): Phone {
        val phone = Phone(role)
        phone.flow.houseSaid(RoundMessage.WorkOrderIssued(order))
        phone.flow.scanning(hall)
        phone.flow.houseSaid(
            RoundMessage.ScanAnswered(
                SubroutineInstance(
                    entry = 0,
                    subroutine = SubroutineKind.Handshake,
                    parameters = SubroutineModel.benchParameters(SubroutineKind.Handshake),
                ),
            ),
        )
        val actions = phone.flow.actions()
        actions.beginSubroutine()
        assertEquals(ScreenId.SubHandshake, phone.flow.state.screen, "BEGIN did not open the Handshake")
        val beats = phone.flow.subroutines.handshake.length
        repeat(beats) { at ->
            if (at > 0) phone.now += after[at - 1]
            actions.tapSubroutine(Subroutine.Handshake, at)
        }
        return phone
    }

    private val dealt: List<Long> = SubroutineModel.HANDSHAKE.gapsMillis.map { it.toLong() }

    /** Timings a person could tap: on the beat, a little off, all at once, far too slow. */
    private val timings: Map<String, List<Long>> = mapOf(
        "on the beat" to dealt,
        "a little off" to dealt.map { it + it / 5 },
        "all at once" to dealt.map { 0L },
        "far too slow" to dealt.map { 4_000L },
        "ragged" to listOf(13L, 1_207L, 488L, 9L),
    )

    @Test
    fun `the gaps tapped are the gaps sent and stamped on this phone's clock`() {
        for (role in PanelRole.entries) {
            for ((label, after) in timings) {
                val phone = tapped(role, after)
                val sent = phone.handOvers.single()
                assertEquals(hall, sent.marker)
                assertEquals(
                    after.map { it.toInt() } + null,
                    HandshakeTaps.gapsIn(sent.entered),
                    "a $role phone tapping $label sent other gaps than it tapped",
                )
            }
        }
    }

    /**
     * **The same bytes on the wire for every role and every timing**, and for the same timing the
     * same entry: an Insider's fake is this screen, this entry and this encoding.
     */
    @Test
    fun `every role and every timing sends a hand-over of one length`() {
        val bytes = mutableMapOf<String, Int>()
        for ((label, after) in timings) {
            val resident = tapped(PanelRole.Resident, after).handOvers.single()
            val insider = tapped(PanelRole.Insider, after).handOvers.single()
            assertEquals(resident, insider, "$label: the Insider's fake sent a different entry")
            bytes["Resident $label"] = RequestSlot.bytesOf(Asked.Act(resident))
            bytes["Insider $label"] = RequestSlot.bytesOf(Asked.Act(insider))
        }
        assertEquals(1, bytes.values.toSet().size, "Handshake hand-overs differ in length: $bytes")
    }
}
