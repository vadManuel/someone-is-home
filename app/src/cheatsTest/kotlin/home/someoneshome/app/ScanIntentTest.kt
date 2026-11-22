package home.someoneshome.app

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **What a card read inside a round becomes** — [inRoundScan], swept rather than sampled.
 *
 * The callback this function was pulled out of was **empty until the payoff run**, and the comment
 * above it said why: it was written when no house was attached (E-D3-3), so a read had nowhere to
 * go. These are the guards on the link the finger is.
 *
 * ### Since D-318 the phone decides nothing about the card
 *
 * It used to split the read into `CallMeeting` or `BeginSubroutine` by the shape printed on the
 * paper, which trusted the paper over the map: a card the home never registered reached the rules
 * as work, and a spare meeting card called a meeting. Now every card this game printed goes up as
 * its id and the house routes it (`routeScan`). **The sweep is the point**: *no shape is decided on
 * the phone* is a claim about all forty-four, and `MarkerShapes.all` is the anchor.
 */
class ScanIntentTest {

    private fun payloadOf(shape: home.someoneshome.model.MarkerShape, id: String): String =
        CardPayload.encode(MarkerCard(CardPayload.VERSION, shape, MarkerId(id)))

    /**
     * **Every shape of the forty-four goes up as its id and nothing else** (D-318), the meeting
     * card's and the Terminal's included. What the card is, is the house's answer from the map.
     */
    @Test
    fun everyShapeInTheWholeRosterGoesUpAsItsIdAndNothingIsDecidedOnThePhone() {
        MarkerShapes.all.forEachIndexed { index, shape ->
            // Seven characters, all inside the alphabet, distinct per shape.
            val id = "CARD" + index.toString().padStart(3, '0')
            assertEquals(
                MarkerId(id), inRoundScan(payloadOf(shape, id)),
                "the shape '${shape.id}' did not go up as its own id",
            )
        }
    }

    /**
     * **Paper this game did not print goes nowhere at all** (D-071). Not a refusal sent to the
     * house and answered — nothing leaves the phone, because an unreadable card is a fact about a
     * piece of paper rather than a statement about a player.
     */
    @Test
    fun paperThisGameDidNotPrintNeverReachesTheHouse() {
        for (bad in listOf("", "TOO SHORT", "9ZBENCH-A", "1!BENCH-A", "wrong length entirely")) {
            assertNull(inRoundScan(bad), "'$bad' produced a request and should have produced none")
        }
    }

    /**
     * **The bench deck really is readable by this function**, which is the positive control the
     * payoff run needs: a round on this build is armed with `BenchHome.destinations`, so those are
     * the only markers a scan can open, and the cards shown to a player have to be those.
     *
     * Without this the scan wiring could be perfect and every card in the room still open nothing —
     * and *the wiring is broken* and *the deck is the wrong deck* look identical from a phone.
     */
    @Test
    fun everyCardTheBenchHomeArmsIsReachableByAScan() {
        for (destination in BenchHome.destinations) {
            val shape = requireNotNull(MarkerShapes.all.firstOrNull { it.id == destination.shape }) {
                "the bench home holds a shape the roster does not: ${destination.shape}"
            }
            assertEquals(
                destination.marker, inRoundScan(payloadOf(shape, destination.marker.value)),
                "the bench marker ${destination.marker.value} is not reachable by a scan",
            )
        }
        assertEquals(
            BenchHome.meetingCard.id, inRoundScan(CardPayload.encode(BenchHome.meetingCard)),
            "the bench home's meeting card is not read as its own id",
        )
    }

    /**
     * **The nine characters that have to be on the screen the owner points a phone at.**
     *
     * ⚠️ **This is the one test here that pins LITERALS, and it is deliberate.** The bench deck is
     * generated outside this build — a QR sheet drawn by a script that works `CardPayload.encode`
     * out by hand, because `:cards:sheet` draws ids fresh every printing (`QK7M003`) and the round
     * is armed with `BENCH-*`. Every other assertion in this file would pass just as happily if the
     * encoding changed, because both sides of them come out of the same encoder. **These do not.**
     * If the payload format ever moves, the symbols on that sheet stop matching the build and every
     * scan in the house opens nothing — which looks exactly like the scan wiring being broken.
     *
     * Version 1 + shape index into `MarkerShapes.ALPHABET` + the seven-character id. The meeting
     * card's `*` is index 38 (`u_shape`) and the Terminal's `+` is 39 (`t_shape`).
     */
    @Test
    fun theBenchDecksPrintedPayloadsAreExactlyWhatThisBuildEncodes() {
        assertEquals(
            "10BENCH-A", CardPayload.encode(
                MarkerCard(CardPayload.VERSION, MarkerShapes.registrable.first(), MarkerId("BENCH-A"))
            ),
            "the first ordinary bench card's payload moved; the generated sheet is now wrong",
        )
        assertEquals(
            "1*BENCH-U", CardPayload.encode(BenchHome.meetingCard),
            "the bench meeting card's payload moved; the generated sheet is now wrong",
        )
        assertEquals(
            "1+BENCH-T", CardPayload.encode(
                MarkerCard(CardPayload.VERSION, MarkerShapes.TERMINAL, MarkerId("BENCH-T"))
            ),
            "the bench Terminal card's payload moved; the generated sheet is now wrong",
        )
    }
}
