package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.ui.FlowModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The instrument the second rehearsal is built on, and the two things it must never write.**
 *
 * [ScreenLedger] is what turns *the round is visible now* from a claim about routing into a claim
 * about five phones: it says what this seat's screen was at the instant the bench announced a beat.
 * That makes it load-bearing evidence, and load-bearing evidence read off a device console is the
 * same new hiding place for the game's two secrets that [RoundClientTest] guards one wire down.
 *
 * The reveal is again where they meet. `PanelState.revealed` is the one field on the whole class
 * that carries an alignment, and it carries a **one line** with it — so a ledger that printed its
 * fields would write every Insider's confession into a file on a Mac, at the one moment of the
 * round when doing so looks like thoroughness.
 */
class ScreenLedgerTest {

    /** Deliberately distinctive, so a sweep for them cannot pass by matching something ordinary. */
    private val name = "PRIYA"
    private val line = "i still have marcus spare key and i used it"

    private class Bench {
        val flow = FlowModel()
        val said = mutableListOf<String>()
        val ledger = ScreenLedger(flow) { said += it }
    }

    /**
     * **The reading is the screen, and it moves when the house moves it.**
     *
     * Asserted in both directions on purpose. A ledger that always printed the same line would pass
     * a test that only looked for the screen's name in it, and would have been useless in exactly
     * the way P3's logs were useless — they *did* contain the word LOBBY, and the finding was that
     * they never contained anything else.
     */
    @Test
    fun theReadingIsTheScreenAndItMovesWhenTheHouseMovesIt() {
        val b = Bench()
        val before = b.ledger.reading()
        assertTrue(before.startsWith("ON "), "the reading does not lead with the screen: $before")

        b.flow.houseSaid(RoundMessage.MeetingOpened(Seat(2), trigger(), Haptic.Short))
        val after = b.ledger.reading()
        assertFalse(before == after, "the house rang and the ledger read the same line: $after")
    }

    /**
     * **The reveal is counted and never quoted** — [RoundClientTest]'s rule, on the field that
     * actually holds it.
     */
    @Test
    fun theRevealIsCountedAndNeitherTheNameNorTheLineIsPrinted() {
        val b = Bench()
        b.flow.houseSaid(RoundMessage.InsidersRevealed(listOf(InsiderNamed(Seat(3), name, line))))
        val reading = b.ledger.reading()

        assertTrue(reading.contains("REVEALED 1"), "the reveal was not counted: $reading")
        assertFalse(reading.contains(name), "the ledger printed the name on the reveal")
        assertFalse(reading.contains(line), "the ledger printed a one line: it is now a file on a Mac")
    }

    /**
     * **The house's sign-off is a length and never its words.**
     *
     * Not a secret, and that is the point of guarding it anyway: the reason to print a length rather
     * than a body is that the two failures worth telling apart are *nothing arrived* and *something
     * arrived*, and a whole paragraph per seat per sample would bury every other field in the line.
     */
    @Test
    fun theHousesLastWordIsALengthAndNeverItsWords() {
        val b = Bench()
        b.flow.houseSaid(RoundMessage.HouseSignedOff("THE HOUSE THANKS ITS RESIDENTS"))
        val reading = b.ledger.reading()

        assertTrue(reading.contains("SIGNOFF 30"), "the sign-off's length is not on the line: $reading")
        assertFalse(reading.contains("THANKS"), "the ledger printed the house's body text")
    }

    /**
     * **A beat prints even when nothing moved, and that is the entry that matters most.**
     *
     * The seats a beat is *about* are usually the ones that did something. The seats a rehearsal is
     * checked by are the ones that did not: *the couch stayed on Ghost3 while the room voted* is a
     * claim with no evidence behind it unless something wrote a line at that instant.
     */
    @Test
    fun aBeatPrintsEvenWhenTheScreenHasNotMoved() {
        val b = Bench()
        b.ledger.sample()
        val quiet = b.said.size
        b.ledger.sample()
        assertEquals(quiet, b.said.size, "an unchanged screen printed twice")

        b.ledger.beat("VOTE")
        assertEquals(quiet + 1, b.said.size, "a beat on an unchanged screen printed nothing")
        assertTrue(b.said.last().contains("BEAT VOTE"), "the beat is not named: ${b.said.last()}")
    }

    /**
     * **Every line carries a monotonic stamp, because the table is a join across five processes.**
     *
     * Without it there is no way to ask what seat 3 was showing when seat 0 announced a beat, which
     * is the only question this class exists to answer.
     */
    @Test
    fun everyLineCarriesAStampAndTheStampsDoNotGoBackwards() {
        val b = Bench()
        b.ledger.beat("ARMED")
        b.flow.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Vote, 45, null, Haptic.Short))
        b.ledger.sample()
        b.ledger.beat("VOTE")

        assertTrue(b.said.size >= 3, "the ledger printed too little to check: ${b.said}")
        val stamps = b.said.map { line ->
            assertTrue(line.startsWith("@"), "a ledger line carries no stamp: $line")
            line.substring(1).substringBefore(' ').toLong()
        }
        assertEquals(stamps.sorted(), stamps, "the stamps went backwards: $stamps")
    }

    /**
     * **A field the house has not filled prints as `-`, and never as a fixture's value.**
     *
     * The ledger is read by a script that compares five seats' lines to each other, so the shape of
     * a line may not change with its content. And a ledger that quietly printed the port's drawn
     * fixture where the house had said nothing would report a phone as up to date about something
     * it had never been told — which is the exact failure C3's nullable `PanelState.seats` exists to
     * prevent one layer down.
     */
    @Test
    fun aFieldTheHouseHasNotFilledReadsAsAbsence() {
        val reading = Bench().ledger.reading()
        for (field in listOf("OUT", "VERDICT", "WORK", "ORDER", "METER", "SEATS", "PHASE", "JAM")) {
            assertTrue(
                reading.contains("· $field -"),
                "$field is not absent on a phone no house has spoken to: $reading",
            )
        }
    }

    private fun trigger() = home.someoneshome.model.MeetingTrigger.RevokeReported(Seat(4))

    /**
     * **A count no house sent prints as `-`** (D-314). Every seat's line on JOIN read
     * `GATE 4/6 · READY 3/6 · VOTED 4/6`, the design's fixture, before any house existed. Once a house
     * is attached the counts are its own, and the first it sends is what the line prints.
     */
    @Test
    fun aCountNoHouseSentPrintsAsUnsaid() {
        val b = Bench()
        val before = b.ledger.reading()
        assertTrue(
            before.contains("GATE -/- · READY -/- · VOTED -/-"),
            "an unhoused seat printed meeting counts nobody sent: $before",
        )

        b.flow.houseAttached(true)
        assertTrue(
            b.ledger.reading().contains("GATE -/- · READY -/- · VOTED -/-"),
            "a housed seat with no count yet printed one: ${b.ledger.reading()}",
        )
        b.flow.meeting.houseCounted(present = 2, seats = 5)
        assertTrue(
            b.ledger.reading().contains("GATE 2/5"),
            "the house counted and the line did not print it: ${b.ledger.reading()}",
        )
    }
}
