package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.RoomKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Registering a card, and every answer the house map can give back.**
 *
 * This is the second half of the setup walk: the host has painted the rooms and is now going round
 * the real house with a stack of printed cards, scanning one in each. Fifteen minutes, once, and
 * what it produces is the thing every round is played on.
 *
 * The failure this file is written against is **the scan that appears to have worked**. A card
 * that was turned away and said nothing about it leaves a host walking out of a room believing
 * something is registered there, and the first anybody hears of it is a player standing at a wall
 * in the dark being told nothing opens. So every branch is asserted to produce a readout, not just
 * the ones that changed the map.
 */
class RegistrationTest {

    private fun card(shape: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shape), MarkerId(id))

    private fun terminalCard(id: String = "SEEDT01") =
        MarkerCard(CardPayload.VERSION, MarkerShapes.TERMINAL, MarkerId(id))

    private fun meetingCard(id: String = "SEEDU01") =
        MarkerCard(CardPayload.VERSION, MarkerShapes.MEETING, MarkerId(id))

    /** The host, in a room, with the phone open on the viewfinder. */
    private fun scanning(room: String = "KITCHEN"): FlowModel {
        val model = FlowModel(PanelState(screen = ScreenId.ScanMarker))
        model.editor.open(room)
        return model
    }

    private fun FlowModel.scan(card: MarkerCard) = cardScanned(CardPayload.encode(card))

    // ---- The two that land -------------------------------------------------------------------

    @Test
    fun aScannedCardIsRegisteredInTheRoomTheHostHasOpen() {
        val model = scanning()
        val before = model.editor.markersIn("KITCHEN").size

        model.scan(card("bowtie", "CARD-01"))

        assertEquals(before + 1, model.editor.markersIn("KITCHEN").size)
        assertEquals(
            MarkerId("CARD-01"),
            model.editor.cardsIn("KITCHEN").last().card.id,
            "the card was registered without its printed id",
        )
        val landed = assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertEquals("KITCHEN", landed.room)
        assertNull(landed.from)
        assertEquals(
            ScreenId.ScanMarker, model.state.screen,
            "the phone walked away between cards, and the host has a stack of them",
        )
    }

    /** A host correcting themselves mid-walk. The card moves; it is never registered twice. */
    @Test
    fun theSameCardInAnotherRoomMovesItAndSaysWhereFrom() {
        val model = scanning()
        model.scan(card("bowtie", "CARD-01"))
        model.editor.open("STUDY")
        model.scan(card("bowtie", "CARD-01"))

        assertEquals(emptyList(), model.editor.markersIn("KITCHEN").filter { it.id == "bowtie" })
        assertEquals(MarkerId("CARD-01"), model.editor.cardsIn("STUDY").last().card.id)
        val landed = assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertEquals("KITCHEN", landed.from)
    }

    // ---- The refusals ------------------------------------------------------------------------

    /**
     * **D-086, on the screen.** Two live cards may never share a shape: the shape is the marker's
     * whole name, so a player told to go to the ring would have two places to stand, and the
     * wrong-room reports that follow are indistinguishable from the error the Terminal injects on
     * purpose. The refusal names the room the shape is already in, because the host has to go and
     * look at it.
     */
    @Test
    fun aSecondCardCarryingARegisteredShapeIsRefusedAndNamesTheRoom() {
        val model = scanning()
        val before = model.editor.markerCount

        model.scan(card("ring", "CARD-99"))

        assertEquals(before, model.editor.markerCount, "the shape was registered twice")
        val refused = assertIs<ScanOutcome.Refused>(model.editor.lastScan)
        assertTrue("GARAGE" in refused.why, refused.why)
        assertEquals(MarkerShapes.require("ring"), refused.shape, "a refusal that names no card")
    }

    /**
     * Unreachable through the screens — the marker sheet is only offered for a room — and answered
     * anyway, because **the absent refusal is the leak**. A room can become stairs from the room
     * panel while this is the room the scan screen is holding.
     */
    @Test
    fun aCardOfferedToStairsIsRefusedRatherThanSilentlyDropped() {
        val model = FlowModel(PanelState(screen = ScreenId.ScanMarker))
        model.editor.openFloor("UPPER")
        model.editor.open("TOP OF STAIRS")
        assertEquals(RoomKind.Stairs, model.editor.heldKind)

        model.scan(card("bowtie", "CARD-01"))

        assertEquals(emptyList(), model.editor.cardsIn("TOP OF STAIRS"))
        val refused = assertIs<ScanOutcome.Refused>(model.editor.lastScan)
        assertTrue("STAIRS HOLD NOTHING" in refused.why, refused.why)
    }

    /** D-071: a card this build cannot read is a fact about a piece of paper and may be said. */
    @Test
    fun aPayloadThisBuildCannotReadSaysSoAndRegistersNothing() {
        val model = scanning()
        val before = model.editor.markerCount

        model.cardScanned("NOTACARD")

        assertEquals(before, model.editor.markerCount)
        assertIs<ScanOutcome.Unreadable>(model.editor.lastScan)
        assertEquals(ScreenId.ScanMarker, model.state.screen)
    }

    /** Nothing on any screen reaches this. It is answered because silence is the failure mode. */
    @Test
    fun aScanWithNoRoomOpenRefusesRatherThanRegisteringNowhere() {
        val model = FlowModel(PanelState(screen = ScreenId.ScanMarker))
        model.mapNewHome()
        assertNull(model.editor.held, "the empty plan came up holding a room")

        model.scan(card("bowtie", "CARD-01"))

        assertEquals(0, model.editor.markerCount)
        assertNotNull(model.editor.lastScan, "the scan produced nothing at all, which is the leak")
    }

    // ---- The terminal ------------------------------------------------------------------------

    @Test
    fun theCardMarkedTPlacesTheTerminalAndIsNotAMarker() {
        val model = scanning()
        model.removeTerminal()
        model.editor.open("KITCHEN")
        val before = model.editor.markerCount

        model.scan(terminalCard())

        assertEquals("KITCHEN", model.editor.terminal)
        assertTrue(model.editor.hasTerminal)
        assertEquals(before, model.editor.markerCount, "the T card was filed as an ordinary marker")
        assertTrue(assertIs<ScanOutcome.Landed>(model.editor.lastScan).isTerminal)
    }

    /**
     * **One home, one terminal**, and the host is asked rather than overruled.
     *
     * A second would give the house two places to be found, and standing at the terminal alone in
     * the dark is the trade the whole map is built on.
     */
    @Test
    fun aSecondTerminalCardAsksInsteadOfMovingIt() {
        val model = scanning()
        assertEquals("HALL", model.editor.terminal)

        model.scan(terminalCard())

        assertEquals(ScreenId.TermTaken, model.state.screen)
        assertEquals("HALL", model.editor.terminal, "the terminal moved before the host answered")
    }

    /** KEEP IT IN HALL: the card in the host's hand is simply not the one that places it. */
    @Test
    fun keepingTheTerminalWhereItIsChangesNothing() {
        val model = scanning()
        model.scan(terminalCard())
        model.go(ScreenId.ScanMarker)

        assertEquals("HALL", model.editor.terminal)
    }

    @Test
    fun movingTheTerminalRebindsItToTheOpenRoomAndLeavesTheOldOneWithout() {
        val model = scanning()
        model.scan(terminalCard())
        model.moveTerminal()

        assertEquals("KITCHEN", model.editor.terminal)
        assertEquals(ScreenId.ScanMarker, model.state.screen)
        val landed = assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertEquals("HALL", landed.from, "the host was not told which room lost it")
    }

    /** REMOVE IT, and **no terminal, no playable home** — the gate on REVIEW HOME is a fact. */
    @Test
    fun removingTheTerminalLeavesTheHomeUnsaveable() {
        val model = scanning()
        model.removeTerminal()

        assertNull(model.editor.terminal)
        assertTrue(!model.editor.hasTerminal)
        assertEquals(ScreenId.MarkerSheet, model.state.screen)
    }

    // ---- The meeting card ----------------------------------------------------------------------

    @Test
    fun theMeetingCardPlacesTheMeetingAreaAndIsNotAMarker() {
        val model = scanning()
        model.removeMeeting()
        model.editor.open("KITCHEN")
        val before = model.editor.markerCount

        model.scan(meetingCard())

        assertEquals("KITCHEN", model.editor.meeting)
        assertTrue(model.editor.hasMeeting)
        assertEquals(before, model.editor.markerCount, "the U card was filed as an ordinary marker")
        val landed = assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertTrue(landed.isMeeting)
        assertTrue(!landed.isTerminal, "the meeting card was read as the terminal")
    }

    /**
     * **One home, one meeting card**, and the host is asked rather than overruled.
     *
     * A second is a second place the house would take a meeting from, and half the party would
     * walk to the wrong one in the dark.
     */
    @Test
    fun aSecondMeetingCardAsksInsteadOfMovingIt() {
        val model = scanning()
        assertEquals("LIVING", model.editor.meeting)

        model.scan(meetingCard())

        assertEquals(ScreenId.MeetTaken, model.state.screen)
        assertEquals("LIVING", model.editor.meeting, "it moved before the host answered")
    }

    /** KEEP IT IN LIVING: the card in the host's hand is simply not the one that places it. */
    @Test
    fun keepingTheMeetingCardWhereItIsChangesNothing() {
        val model = scanning()
        model.scan(meetingCard())
        model.go(ScreenId.ScanMarker)

        assertEquals("LIVING", model.editor.meeting)
    }

    @Test
    fun movingTheMeetingCardRebindsItAndLeavesTheOldRoomWithout() {
        val model = scanning()
        model.scan(meetingCard())
        model.moveMeeting()

        assertEquals("KITCHEN", model.editor.meeting)
        assertEquals(ScreenId.ScanMarker, model.state.screen)
        val landed = assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertEquals("LIVING", landed.from, "the host was not told which room lost it")
    }

    @Test
    fun removingTheMeetingCardLeavesTheHomeUnsaveable() {
        val model = scanning()
        model.removeMeeting()

        assertNull(model.editor.meeting)
        assertTrue(!model.editor.hasMeeting)
        assertTrue(!model.editor.review.passes)
        assertEquals(ScreenId.MarkerSheet, model.state.screen)
    }

    /**
     * **A shape is reserved, a room is not** (D-121).
     *
     * An ordinary card registered in the meeting card's room is the expected case — the meeting
     * area is a room people already gather in — and the refusal that would come instead is one a
     * host would meet in the busiest room of the house.
     */
    @Test
    fun anOrdinaryCardMayBeRegisteredInTheMeetingCardsRoom() {
        val model = scanning("LIVING")
        assertEquals("LIVING", model.editor.meeting)
        val before = model.editor.markersIn("LIVING").size

        model.scan(card("bowtie", "CARD-09"))

        assertIs<ScanOutcome.Landed>(model.editor.lastScan)
        assertEquals(before + 1, model.editor.markersIn("LIVING").size)
        assertEquals("LIVING", model.editor.meeting, "the marker displaced the meeting card")
    }

    // ---- Taking one back off ------------------------------------------------------------------

    @Test
    fun tappingAMarkerOnTheSheetUnregistersThatCard() {
        val model = scanning("GARAGE")
        val ring = model.editor.cardsIn("GARAGE").first { it.card.shape.id == "ring" }

        model.editor.forgetMarker(ring.card.id)

        assertEquals(listOf("star"), model.editor.markersIn("GARAGE").map { it.id })
    }

    // ---- What the readout is about -------------------------------------------------------------

    /**
     * A readout is about a card **in a room**. Carrying it into the next room would tell the host
     * a card had just been read somewhere they have only walked into.
     */
    @Test
    fun theReadoutIsClearedWhenTheHostOpensAnotherRoom() {
        val model = scanning()
        model.scan(card("bowtie", "CARD-01"))
        assertNotNull(model.editor.lastScan)

        model.editor.open("GARAGE")
        assertNull(model.editor.lastScan)
    }

    /** Stepping out to the marker sheet and back is looking, not scanning. It keeps the readout. */
    @Test
    fun steppingOutToTheSheetAndBackKeepsTheReadout() {
        val model = scanning()
        model.scan(card("bowtie", "CARD-01"))

        model.go(ScreenId.MarkerSheet)
        model.editor.open("KITCHEN")
        model.go(ScreenId.ScanMarker)

        assertNotNull(model.editor.lastScan, "the confirmation the host went to look at is gone")
    }

    // ---- The whole walk, kept -------------------------------------------------------------------

    /**
     * **The point of the unit: a registered card survives being saved and read back.**
     *
     * Not the shape — the shape would round-trip through a fixture. The printed id is what makes a
     * scanned card the same card next June, and it is the thing a format that only stored shapes
     * could never carry.
     */
    @Test
    fun aRegisteredCardSurvivesTheHomeBeingSavedAndReopened() {
        val store = MemoryHomeStore()
        val model = FlowModel(PanelState(screen = ScreenId.ScanMarker), homes = SavedHomesModel(store))
        model.editor.open("KITCHEN")
        model.scan(card("bowtie", "CARD-01"))
        model.editor.nameHome("THE ANNEXE")
        model.saveHome()

        // A second model over the same store: the file, parsed by something that never held the
        // editor that wrote it.
        val home = SavedHomesModel(store).homes.single { it.name == "THE ANNEXE" }
        assertEquals(MarkerId("CARD-01"), home.map.inRoomNamed("KITCHEN").last().card.id)
        assertEquals("HALL", home.terminal)
        assertTrue(
            home.map.terminal!!.card.isTerminal,
            "the terminal came back as a card that is not the one marked T",
        )
    }
}
