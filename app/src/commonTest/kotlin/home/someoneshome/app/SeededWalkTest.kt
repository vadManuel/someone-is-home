package home.someoneshome.app

import home.someoneshome.model.CardPayload
import home.someoneshome.platform.SeededCardScanner
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScanOutcome
import home.someoneshome.ui.ScreenId

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The seeded deck, walked against a real home — the one test that can see both halves.**
 *
 * `ui` cannot see `platform` and `platform` cannot see `ui`, which is what keeps the screens off
 * the filesystem and the filesystem off the screens. `app` is the module where they are introduced
 * to each other, so it is the only place that can ask whether the deck a build hands the scanner
 * actually demonstrates anything against the home a build opens on.
 *
 * That question is not academic. The deck's shapes originally collided with the sample home's, so
 * **every ordinary card in it was turned away by D-086 before it could register anything** — a
 * playtest walk that could only ever show the refusal, on a build whose whole purpose is to walk
 * the flow on a phone. Nothing in either module could have noticed: each was correct on its own.
 */
class SeededWalkTest {

    private fun walking(room: String = "KITCHEN"): FlowModel {
        val model = FlowModel(PanelState(screen = ScreenId.ScanMarker))
        model.editor.open(room)
        return model
    }

    /** The deck, dealt into the flow exactly the way the shutter deals it. */
    private fun deal(model: FlowModel, cards: Int): SeededCardScanner {
        val scanner = SeededCardScanner()
        scanner.start(model::cardScanned)
        repeat(cards) { scanner.present() }
        return scanner
    }

    /**
     * The first two cards land. Not "do not crash" — **land**, in the room the host has open, with
     * the printed ids on them.
     */
    @Test
    fun theFirstCardsOfTheDeckRegisterInTheOpenRoom() {
        val model = walking()
        val before = model.editor.markerCount
        deal(model, 2)

        assertEquals(before + 2, model.editor.markerCount, "the deck cannot register anything")
        assertEquals(
            listOf("SEED001", "SEED002"),
            model.editor.cardsIn("KITCHEN").takeLast(2).map { it.card.id.value },
        )
        assertEquals(ScreenId.ScanMarker, model.state.screen)
    }

    /**
     * No card in the deck may collide with the sample home, or the walk starts on a refusal.
     *
     * Only the ordinary cards: the two reserved shapes are what is printed on the paper (D-120,
     * D-121), so a deck that avoided them to dodge a collision would be demonstrating an ordinary
     * marker where the flow expects a terminal or a meeting card.
     */
    @Test
    fun noOrdinaryCardInTheDeckClashesWithTheSampleHome() {
        val held = FlowModel().editor.map.registrations.map { it.card.shape.id }.toSet()
        val clashing = SeededCardScanner.DECK.filter { it.isOrdinary }.filter { it.shape.id in held }
        assertEquals(
            emptyList(), clashing.map { it.id.value },
            "these cards are refused before they can demonstrate anything, against $held",
        )
    }

    /**
     * **The deck carries both reserved cards**, so a playtest walk demonstrates both refusals.
     *
     * The lesson this file exists for, applied to the new card: a deck that had gained the meeting
     * card and not its duplicate would walk the placement and never the refusal, and the refusal
     * is the half a host actually meets.
     */
    @Test
    fun theDeckCarriesBothReservedCardsAndASecondOfEach() {
        assertEquals(2, SeededCardScanner.DECK.count { it.isTerminal })
        assertEquals(2, SeededCardScanner.DECK.count { it.isMeeting })
    }

    /**
     * The third card is the deck's own duplicate shape, and it is refused **because of the walk**
     * rather than because of the fixture — which is the difference between a demonstration and an
     * accident.
     */
    @Test
    fun theThirdCardIsRefusedForCarryingTheFirstsShape() {
        val model = walking()
        deal(model, 3)

        val refused = assertIs<ScanOutcome.Refused>(model.editor.lastScan)
        assertEquals("SEED003", refused.card.id.value)
        assertTrue("ALREADY IN" in refused.why, refused.why)
        assertEquals(2, model.editor.cardsIn("KITCHEN").size - 1, "the duplicate registered anyway")
    }

    /** The fourth is the card marked T, and the home already has its terminal in the hall. */
    @Test
    fun theTerminalCardWalksOntoTheRefusalScreen() {
        val model = walking()
        deal(model, 4)

        assertEquals(ScreenId.TermTaken, model.state.screen)
        assertEquals("HALL", model.editor.terminal)
    }

    /**
     * The sixth is the meeting card, and the home already has one in LIVING.
     *
     * Dealt as the sixth rather than reached by its own route, because what this file is about is
     * the deck as a phone deals it: a card that only demonstrates something when a test hands it
     * over directly is a card no playtest walk would ever reach.
     */
    @Test
    fun theMeetingCardWalksOntoItsOwnRefusalScreen() {
        val model = walking()
        deal(model, 6)

        assertEquals(ScreenId.MeetTaken, model.state.screen)
        assertEquals("LIVING", model.editor.meeting)
    }

    /** Whole laps, so the deck can be walked on a phone without it quietly running out. */
    @Test
    fun theDeckKeepsDealingPastItsOwnLength() {
        val model = walking()
        val scanner = deal(model, SeededCardScanner.DECK.size + 1)
        assertEquals(
            CardPayload.encode(SeededCardScanner.DECK[1]),
            CardPayload.encode(scanner.peek!!),
            "the deck stopped instead of coming round again",
        )
    }
}
