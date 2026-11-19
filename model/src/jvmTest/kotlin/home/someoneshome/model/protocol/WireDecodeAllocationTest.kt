package home.someoneshome.model.protocol

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **GUARD (rule 7, D-276): turning away the other family's body costs nothing.**
 *
 * Every phone offers every round frame (each push, each upload's answer) to the lobby's decoder
 * before the round's, and the host offers every lobby body to the round's decoder first. That
 * decode used to fail by throwing, and a thrown exception captures a stack and formats a message:
 * kilobytes, several times a second, on every phone, for an answer that was always null. JVM
 * per-thread allocation, exact; see `UploadAllocationTest` for why the JVM and what the ceilings
 * mean.
 */
class WireDecodeAllocationTest {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Volatile
    private var sink: Any? = null

    private fun perCall(times: Int = 2_000, block: () -> Any?): Long {
        repeat(times) { sink = block() }
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        repeat(times) { sink = block() }
        return (threads.getThreadAllocatedBytes(id) - before) / times
    }

    private fun assertUnder(ceiling: Long, measured: Long, what: String) =
        assertTrue(measured <= ceiling, "$what allocates $measured bytes, over its ceiling of $ceiling (rule 7, D-276)")

    @Test
    fun `a round body offered to the lobby's decoder is turned away without allocating`() {
        val answer = RoundWire.encode(RoundBody.UploadHeard.of("0123456789abcdef", UploadProbe.NONE, 0L, 0L, RangingWire.NO_PLAN))
        val push = RoundWire.encode(RoundBody.Push(9, RoundMessage.MeetingCalling, round = 1))
        assertNull(LobbyWire.decodeOrNull(answer))
        assertUnder(64, perCall { LobbyWire.decodeOrNull(answer) }, "the lobby's decode of an upload's answer")
        assertUnder(64, perCall { LobbyWire.decodeOrNull(push) }, "the lobby's decode of a push")
    }

    /**
     * The host reads one of these per seat per beat. The body is an upload's JSON with one character
     * past Latin-1 on the end, so the JVM holds its strings at two bytes a character, as Kotlin/Native
     * always does; with plain ASCII the JVM halves its strings and the two ways of reading come within
     * a few hundred bytes of each other. 12.9 KB a call through the general decoder (before D-276),
     * 9.0 KB now: the body once as characters and once as its string. On Kotlin/Native, measured
     * with the std allocator, the plain upload frame went from 11.1 KB to 7.2 KB.
     *
     * **D-300 grew the frame by its ranging report**, 1 699 characters of every upload from every
     * phone (three 384-byte token slots and six distance slots, sealed). The read is still the body
     * twice and little else, so the ceiling moved with the body: 17.7 KB measured, 20 KB allowed. Six
     * token slots, D-300's first draft, measured 25.6 KB.
     */
    @Test
    fun `reading an upload's frame allocates its body and little else`() {
        val body = RoundWire.encode(RoundBody.Sighted("0".repeat(SightingUpload.BODY_CHARS), SightingUpload.EMPTY_ASK, UploadProbe.NONE, RangingWire.NO_REPORT)) + "李"
        val frame = TransportWire.encode(TransportFrame.Carry(body))
        val read = perCall { TransportWire.decodeOrNull(frame) }
        println("D-300 READING AN UPLOAD FRAME — $read B")
        assertUnder(20_480, read, "reading an upload's frame")
    }

    /**
     * **D-300: the house writes one of these per seat per beat**, an upload's answer carrying the
     * seat's sealed plan, [RangingWire.PLAN_CHARS] characters. The body and the frame are each built
     * and each the plan's width again: 4.4 KB measured, against 0.5 KB for an answer with no plan
     * (a `FaultHeard`), and 7.3 KB for D-300's first draft's six token slots.
     */
    @Test
    fun `writing an upload's answer costs the frame and little else`() {
        val plan = RangingWire.NO_PLAN
        val written = perCall {
            TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.UploadHeard.of("0123456789abcdef", UploadProbe.NONE, 5L, 6L, plan))))
        }
        println("D-300 WRITING AN UPLOAD'S ANSWER — $written B")
        assertUnder(5_120, written, "writing an upload's answer")
    }

    @Test
    fun `a lobby body offered to the round's decoder is turned away without allocating`() {
        val standing = LobbyWire.encode(LobbyBody.Standing(joined = 6, linesIn = 4, names = listOf("ROSE", "TOMAS"), insiders = 2))
        assertNull(RoundWire.decodeOrNull(standing))
        assertUnder(64, perCall { RoundWire.decodeOrNull(standing) }, "the round's decode of a standing")
    }
}
