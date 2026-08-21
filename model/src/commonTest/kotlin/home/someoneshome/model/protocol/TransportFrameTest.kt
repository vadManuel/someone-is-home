package home.someoneshome.model.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wire format is versioned (D8), so the JSON shape is a contract, not an implementation
 * detail: every frame round-trips, and every discriminator string is pinned. Renaming one in
 * place is a silent protocol break between a host and a phone built a week apart — the failing
 * assertion here is the reminder that the honest rename is a new [TRANSPORT_PROTOCOL].
 */
class TransportFrameTest {

    private val json = Json

    private val everyFrame: Map<String, TransportFrame> = mapOf(
        "hello" to TransportFrame.Hello(TRANSPORT_PROTOCOL),
        "resume" to TransportFrame.Resume(SeatToken("tk-7")),
        "seated" to TransportFrame.Seated(SeatToken("tk-7")),
        "refused" to TransportFrame.Refused(TransportRefusal.RoundLocked),
        "proposed" to TransportFrame.Proposed(proposal = 3, body = "opaque"),
        "ack" to TransportFrame.Ack(proposal = 3),
        "commit" to TransportFrame.Commit(proposal = 3),
        "probe" to TransportFrame.TimeProbe(probe = 4),
        "mark" to TransportFrame.TimeMark(probe = 4, hostMillis = 12_345),
        "carry" to TransportFrame.Carry(body = "opaque"),
    )

    @Test
    fun everyFrameRoundTripsThroughJson() {
        for ((_, frame) in everyFrame) {
            val text = json.encodeToString(TransportFrame.serializer(), frame)
            val back = json.decodeFromString(TransportFrame.serializer(), text)
            assertEquals(frame, back, "round trip changed the frame: $text")
        }
    }

    @Test
    fun theDiscriminatorsAreFrozen() {
        for ((wireName, frame) in everyFrame) {
            val text = json.encodeToString(TransportFrame.serializer(), frame)
            assertTrue(
                "\"type\":\"$wireName\"" in text,
                "frame ${frame::class.simpleName} no longer says '$wireName' on the wire: $text",
            )
        }
    }

    @Test
    fun theFrameListIsExhaustive() {
        // The `when` is exhaustive over the sealed type, so a frame added without a row in this
        // test's table is a COMPILE error here, not a frame that ships with an unpinned
        // discriminator. Same discipline as Screen.kt's screen switch.
        fun wireNameOf(frame: TransportFrame): String = when (frame) {
            is TransportFrame.Hello -> "hello"
            is TransportFrame.Resume -> "resume"
            is TransportFrame.Seated -> "seated"
            is TransportFrame.Refused -> "refused"
            is TransportFrame.Proposed -> "proposed"
            is TransportFrame.Ack -> "ack"
            is TransportFrame.Commit -> "commit"
            is TransportFrame.TimeProbe -> "probe"
            is TransportFrame.TimeMark -> "mark"
            is TransportFrame.Carry -> "carry"
        }
        for ((wireName, frame) in everyFrame) {
            assertEquals(wireName, wireNameOf(frame), "table key disagrees with the frame's wire name")
        }
    }
}
