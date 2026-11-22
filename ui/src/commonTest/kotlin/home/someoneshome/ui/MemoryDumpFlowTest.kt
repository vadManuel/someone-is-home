package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.CircuitStation
import home.someoneshome.model.Destination
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-325 on the phone: what BEGIN and NOT THIS ONE send, where each lands, and what the order and
 * Files say** — a housed [FlowModel] fed only the house's messages, both roles.
 */
class MemoryDumpFlowTest {

    private val hall = Destination(MarkerId("card-hall"), "diamond", "HALL")
    private val bed = Destination(MarkerId("card-bed2"), "cross", "BED 2")
    private val study = Destination(MarkerId("card-study"), "square_frame", "STUDY")
    private val pattern = listOf(2, 0, 3, 1)

    private fun order(held: Boolean) = listOf(
        OrderLine.Known(0, AssignedWork.ArrayWipe, done = false, at = listOf(study, bed, hall)),
        OrderLine.Known(1, AssignedWork.MemoryDump, done = false, at = listOf(bed, hall), pattern = if (held) 1 else 0),
        OrderLine.Blocked(2),
    )

    private class Phone(role: PanelRole) {
        val sent = mutableListOf<IntentMessage>()
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(send = { sent += it }),
            send = { sent += it },
        )
    }

    private fun bothRoles(check: (Phone) -> Unit) = PanelRole.entries.forEach { check(Phone(it)) }

    private fun Phone.atTheSource() {
        model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        model.go(ScreenId.Scan)
        model.scanning(bed.marker)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(1, SubroutineKind.MemoryDump, listOf(MemoryPattern.SOURCE) + pattern)))
    }

    private fun Phone.atTheTarget() {
        model.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        model.go(ScreenId.Scan)
        model.scanning(hall.marker)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(1, SubroutineKind.MemoryDump, listOf(MemoryPattern.TARGET, 4))))
    }

    @Test
    fun beginAtTheSourceAsksTheHouseToHoldThePatternAndPlaysIt() = bothRoles { phone ->
        phone.atTheSource()
        assertEquals(ScreenId.ScanCaught, phone.model.state.screen)
        assertEquals(MEMORY_DUMP_SOURCE_BRIEFING, PanelVals(phone.model.state).current?.briefing)
        phone.model.beginSubroutine()
        assertEquals(ScreenId.SubMemory, phone.model.state.screen, "BEGIN did not start the playback")
        assertEquals(listOf<IntentMessage>(IntentMessage.BeginCarry), phone.sent, "BEGIN at the source did not ask the house to hold it")
    }

    @Test
    fun notThisOneAtTheSourceSendsNothing() = bothRoles { phone ->
        phone.atTheSource()
        phone.model.go(ScreenId.Work)
        assertEquals(ScreenId.Work, phone.model.state.screen)
        assertTrue(phone.sent.isEmpty(), "NOT THIS ONE told the house something: ${phone.sent}")
    }

    @Test
    fun theTargetIsBriefedAndTheLastTapHandsTheOrderOver() = bothRoles { phone ->
        phone.atTheTarget()
        val caught = PanelVals(phone.model.state).current!!
        assertEquals(MEMORY_DUMP_TARGET_BRIEFING, caught.briefing)
        assertEquals("HALL", caught.room, "the target's caught scan names the wrong room")
        phone.model.beginSubroutine()
        assertEquals(ScreenId.SubMemory, phone.model.state.screen)
        assertTrue(phone.sent.isEmpty(), "BEGIN at the target asked for a carry")
        for (t in pattern) phone.model.tapSubroutine(Subroutine.MemoryDump, t)
        assertEquals(listOf<IntentMessage>(IntentMessage.HandOverEntry(hall.marker, pattern)), phone.sent)
    }

    @Test
    fun theRowReadsToTheTargetWhileThePatternIsHeld() = bothRoles { phone ->
        phone.model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        val before = PanelVals(phone.model.state).order[1] as OrderRow.Named
        assertEquals("BED 2", before.room)
        phone.model.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        val held = PanelVals(phone.model.state).order[1] as OrderRow.Named
        assertEquals(memoryDumpTo("HALL"), held.room, "the held row does not read TO <ROOM>")
        assertEquals(LightSignature.Medium, held.light, "Memory Dump is not rated MEDIUM")
    }

    @Test
    fun filesNeverShowsThePattern() = bothRoles { phone ->
        phone.atTheSource()
        phone.model.beginSubroutine()
        phone.model.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        assertNull(PanelVals(phone.model.state).filed, "Files holds something while a pattern is carried")
        assertNull(phone.model.state.carrying, "the pattern rode the carry Files draws")
    }

    @Test
    fun theFirstSparesIsBriefedAndBeginAsksForTheDisk() = bothRoles { phone ->
        phone.model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        phone.model.go(ScreenId.Scan)
        phone.model.scanning(study.marker)
        phone.model.houseSaid(RoundMessage.ScanAnswered(null, circuit = true))
        assertEquals(ScreenId.ScanCaught, phone.model.state.screen, "the first Spares was not caught")
        assertEquals(1, PanelVals(phone.model.state).circuit?.index)
        phone.model.beginSubroutine()
        assertEquals(listOf<IntentMessage>(IntentMessage.BeginCarry), phone.sent)
        // The house's answer to BEGIN moves the phone, as every station step does (D-322).
        phone.model.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, CircuitStation.Rack)))
        assertEquals(ScreenId.Home, phone.model.state.screen)
        assertEquals(Carried("DISK 01", "RACK"), phone.model.state.carrying)
    }

    @Test
    fun notThisOneAtTheFirstSparesTakesNothing() = bothRoles { phone ->
        phone.model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        phone.model.go(ScreenId.Scan)
        phone.model.scanning(study.marker)
        phone.model.houseSaid(RoundMessage.ScanAnswered(null, circuit = true))
        phone.model.go(ScreenId.Work)
        assertTrue(phone.sent.isEmpty(), "NOT THIS ONE asked for the disk")
        assertNull(phone.model.state.carrying)
    }
}
