package home.someoneshome.app

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.protocol.IntentMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What a card read inside a round becomes** — [inRoundScan], swept rather than sampled.
 *
 * The callback this function was pulled out of was **empty until the payoff run**, and the comment
 * above it said why: it was written when no house was attached (E-D3-3), so a read had nowhere to
 * go. Everything downstream had been built since — C3 routed `ScanAnswered` to `ScanCaught` and
 * `ScanBad`, R1 minted the wire members — and the missing link was the finger. These are the guards
 * on that link.
 *
 * **The sweep is the point, not the three examples.** The rule *exactly one shape in the roster is
 * a meeting* is a claim about all forty-four, and a test naming `u_shape` and `t_shape` by hand
 * would agree with itself. `MarkerShapes.all` is the anchor, in both directions.
 */
class ScanIntentTest {

    private fun payloadOf(shape: home.someoneshome.model.MarkerShape, id: String): String =
        CardPayload.encode(MarkerCard(CardPayload.VERSION, shape, MarkerId(id)))

    /**
     * **D-121, and the whole reason a meeting exists as a separate member**: a meeting is called by
     * physically scanning the meeting card and by nothing else.
     */
    @Test
    fun theMeetingCardCallsAMeeting() {
        assertEquals(
            IntentMessage.CallMeeting,
            inRoundScan(payloadOf(MarkerShapes.MEETING, "BENCH-U")),
            "the meeting card did not call a meeting",
        )
    }

    /**
     * **D-124: the id travels and the verdict comes back.** Whether *this* seat has work at *this*
     * marker is the rules' answer, so what a scan produces here is the question and never a guess
     * at the answer.
     */
    @Test
    fun anOrdinaryCardAsksTheHouseAboutThatMarkerAndNothingElse() {
        val intent = inRoundScan(payloadOf(MarkerShapes.registrable.first(), "BENCH-A"))
        assertEquals(
            IntentMessage.BeginSubroutine(MarkerId("BENCH-A")), intent,
            "an ordinary card did not ask the house about its own marker",
        )
    }

    /**
     * **The Terminal is work, not a place with a member of its own.** `routeScan` puts
     * `map.terminal` on the same branch as every registration — it is somewhere you scan to work —
     * so a build that gave it a third case would be inventing a rule the house does not have.
     */
    @Test
    fun theTerminalTravelsAsAnOrdinaryMarker() {
        assertEquals(
            IntentMessage.BeginSubroutine(MarkerId("BENCH-T")),
            inRoundScan(payloadOf(MarkerShapes.TERMINAL, "BENCH-T")),
            "the Terminal did not travel as an ordinary marker",
        )
    }

    /**
     * **Exactly one shape of the forty-four is a meeting** — swept, so the day somebody adds a
     * second reserved shape this fails rather than quietly routing it to work.
     *
     * Anchored on `MarkerShapes.all` rather than on a hand-written list, which is `RoundWireTest`'s
     * discipline: E1 found `HouseBuzzTest.everyKindIsHere` comparing two hand-written lists in one
     * file, and they had agreed with each other while missing three kinds for months.
     */
    @Test
    fun exactlyOneShapeInTheWholeRosterIsAMeetingAndEveryOtherIsWork() {
        val meetings = mutableListOf<String>()
        val work = mutableListOf<String>()
        MarkerShapes.all.forEachIndexed { index, shape ->
            // Seven characters, all inside the alphabet, distinct per shape.
            val id = "CARD" + index.toString().padStart(3, '0')
            when (val intent = inRoundScan(payloadOf(shape, id))) {
                IntentMessage.CallMeeting -> meetings += shape.id
                is IntentMessage.BeginSubroutine -> {
                    assertEquals(
                        MarkerId(id), intent.marker,
                        "the shape '${shape.id}' travelled under somebody else's marker",
                    )
                    work += shape.id
                }
                else -> throw AssertionError("'${shape.id}' produced $intent, which is neither")
            }
        }
        assertEquals(
            listOf(MarkerShapes.MEETING.id), meetings,
            "the set of shapes that call a meeting is not exactly the meeting card",
        )
        assertEquals(
            MarkerShapes.all.size - 1, work.size,
            "every shape but the meeting card should be work",
        )
        assertTrue(
            MarkerShapes.TERMINAL.id in work,
            "the Terminal left the work branch — routeScan puts it on it",
        )
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
    fun everyMarkerTheBenchHomeArmsIsReachableByAScan() {
        for (destination in BenchHome.destinations) {
            val shape = requireNotNull(MarkerShapes.all.firstOrNull { it.id == destination.shape }) {
                "the bench home holds a shape the roster does not: ${destination.shape}"
            }
            assertEquals(
                IntentMessage.BeginSubroutine(destination.marker),
                inRoundScan(payloadOf(shape, destination.marker.value)),
                "the bench marker ${destination.marker.value} is not reachable by a scan",
            )
        }
        assertEquals(
            IntentMessage.CallMeeting,
            inRoundScan(CardPayload.encode(BenchHome.meetingCard)),
            "the bench home's meeting card does not call a meeting",
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
