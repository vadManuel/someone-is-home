package home.someoneshome.model.protocol

import home.someoneshome.model.ClientClass
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **An Insider's Isolate offer and a Resident's blank are one length on the wire, and an ability's
 * answer is one length whether or not it started a cooldown** (D-258).
 *
 * Measured as [ContactAnswerSizeTest] measures a touch: pushed, encoded, carried, encoded again,
 * through the real `RoundWire` and `TransportWire`, at every sequence number a round reaches. The
 * socket-level half is `IsolateFrameSizeTest` in `app`.
 */
class IsolateWireSizeTest {

    private fun frame(seq: Long, message: RoundMessage): String =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))

    private val seqs = listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)

    private fun card(i: Int, room: String) = Destination(MarkerId("card-$i"), "shape-$i", room)

    /** Lists whose JSON is a different width from their length: escapes, multibyte, empty. */
    private val lists = mapOf(
        "no cards" to emptyList(),
        "plain rooms" to listOf(card(0, "KITCHEN"), card(1, "HALL")),
        "rooms JSON escapes" to listOf(card(0, "KID'S \"DEN\""), card(1, "BATH\\ROOM\n\t\u0001")),
        "multibyte rooms" to listOf(card(0, "ÉTAGE ü"), card(1, "鍵鍵鍵"), card(2, "🏠")),
        "a long home" to (0 until 40).map { card(it, "ROOM \"$it\" ü") },
    )

    @Test
    fun `an offer and its blank are one length on the wire`() {
        val lengths = mutableMapOf<String, Pair<Int, Int>>()
        for ((label, targets) in lists) {
            val offered = RoundWire.project(Effect.IsolateTargetsOffered(Seat(1), targets))
            val blanked = RoundWire.project(Effect.IsolateTargetsBlanked(Seat(2), targets))
            for (seq in seqs) {
                val a = frame(seq, offered)
                val b = frame(seq, blanked)
                assertEquals(a.length, b.length, "$label at seq $seq: offer ${a.length} bytes, blank ${b.length}:\n$a\n$b")
                if (seq == 1L) lengths[label] = a.length to b.length
            }
        }
        println("D-258 ISOLATE OFFER/BLANK BYTES AT SEQ 1 — $lengths")
    }

    @Test
    fun `the offer's list survives the wire and the blank carries none`() {
        for ((label, targets) in lists) {
            val offered = RoundWire.decodeOrNull(frameBody(RoundWire.project(Effect.IsolateTargetsOffered(Seat(1), targets))))
            assertEquals(targets, ((offered as RoundBody.Push).message as RoundMessage.IsolateTargetsOffered).targets, label)
            val blanked = (RoundWire.decodeOrNull(frameBody(RoundWire.project(Effect.IsolateTargetsBlanked(Seat(2), targets))))
                as RoundBody.Push).message
            assertTrue(blanked is RoundMessage.IsolateTargetsBlanked, "$label: the blank came back as $blanked")
            assertTrue(blanked.paddingHex.all { it == '0' }, "$label: the blank carried something")
            assertEquals(EmitSchema.ISOLATE_TARGETS_BLANKED, blanked.kind)
        }
    }

    /** A blank with anything in it, or an offer that is not a list, is not a message. */
    @Test
    fun `a blank that carries anything does not decode`() {
        assertFailsWith<IllegalArgumentException> { RoundMessage.IsolateTargetsBlanked("5b5d") }
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"IsolateTargetsBlanked","paddingHex":"5b5d"}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"IsolateTargetsOffered","targetsHex":"zz"}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"IsolateTargetsOffered","targetsHex":"7b7d"}}"""))
    }

    @Test
    fun `an answer that started a cooldown and one that did not are one length on the wire`() {
        for (seq in seqs) {
            val started = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(1), cooldownStarted = true)))
            val running = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(1), cooldownStarted = false)))
            assertEquals(started.length, running.length, "at seq $seq the answer's size says whether it took:\n$started\n$running")
        }
        val back = (RoundWire.decodeOrNull(frameBody(RoundMessage.AbilityFired(cooldownStarted = false))) as RoundBody.Push).message
        assertEquals(false, (back as RoundMessage.AbilityFired).cooldownStarted)
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"AbilityFired","cooldownStarted":true}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"AbilityFired","cooldownStarted":10}}"""))
    }

    /** One class each, and the two classes are the two living roles: one frame per living seat. */
    @Test
    fun `the offer's row and the blank's row are the two living roles one each`() {
        assertEquals(listOf(ClientClass(Role.Insider, RoundState.Live)), EmitSchema.classesFor(EmitSchema.ISOLATE_TARGETS_OFFERED))
        assertEquals(listOf(ClientClass(Role.Resident, RoundState.Live)), EmitSchema.classesFor(EmitSchema.ISOLATE_TARGETS_BLANKED))
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("IsolateTargetsBlank")), "a misspelt kind reached somebody")
    }

    /** Built for every seat, each living seat is sent exactly one of the pair, and the out none. */
    @Test
    fun `each living seat is sent exactly one of the pair`() {
        val seats = (0..5).map { Seat(it) }
        val targets = lists.getValue("rooms JSON escapes")
        val state = GameState.armedRound(seed = 7L, seats = seats, insiders = listOf(Seat(1), Seat(4)), systemIntegrity = 42)
            .copy(revoked = listOf(Seat(3)))
        val sent = seats.flatMap { seat ->
            listOf(Effect.IsolateTargetsOffered(seat, targets), Effect.IsolateTargetsBlanked(seat, targets))
                .flatMap { EmitSchema.deliveries(it, state) }
        }.groupBy({ it.seat.index }, { it.kind })
        assertEquals(
            mapOf(
                0 to listOf(EmitSchema.ISOLATE_TARGETS_BLANKED),
                1 to listOf(EmitSchema.ISOLATE_TARGETS_OFFERED),
                2 to listOf(EmitSchema.ISOLATE_TARGETS_BLANKED),
                4 to listOf(EmitSchema.ISOLATE_TARGETS_OFFERED),
                5 to listOf(EmitSchema.ISOLATE_TARGETS_BLANKED),
            ),
            sent,
            "the Isolate frames per seat follow the role in count, or reach a seat that is out",
        )
    }

    private fun frameBody(message: RoundMessage): String = RoundWire.encode(RoundBody.Push(1, message))
}
