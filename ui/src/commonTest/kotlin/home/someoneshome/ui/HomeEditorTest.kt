package home.someoneshome.ui

import home.someoneshome.model.Cell
import home.someoneshome.model.RoomKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The home editor, against the plan it is editing.
 *
 * Everything here is host-side setup — a plan drawn in the light, weeks before anybody plays — so
 * none of it is a leak surface and all of it is a *losable evening*. Fifteen minutes of walking a
 * house is the thing being protected, and the ways to lose it are quiet: a drag that silently
 * overwrote a room, a rename that dropped the cards registered in it, a type change that took a
 * terminal with it and said nothing.
 *
 * The refusals are the model's — [home.someoneshome.model.HousePlan.paint] decides what is legal
 * and this layer only translates. What is tested here is that the translation loses nothing:
 * every `no` reaches the host in words, and every `yes` leaves the plan exactly as intended.
 */
class HomeEditorTest {

    private fun bungalow() = HomeEditorModel.bungalow()

    /** A cell no room on the open storey covers. There are several; this takes the first. */
    private fun HomeEditorModel.firstBareCell(): Cell =
        (0 until HomeEditorModel.COLS * HomeEditorModel.ROWS)
            .map { Cell(x = it % HomeEditorModel.COLS, y = it / HomeEditorModel.COLS) }
            .first { roomAt(it) == null }

    private fun HomeEditorModel.drag(from: Cell, to: Cell): String? {
        dragFrom(from)
        dragTo(to)
        return dropDrag()
    }

    // ---- Drag two corners ----------------------------------------------------------------------

    @Test
    fun aDragOverBareGridPaintsARoomCoveringExactlyThoseCells() {
        val editor = bungalow()
        val a = Cell(0, 11)
        val b = Cell(2, 11)
        assertNull(editor.roomAt(a), "the fixture already has a room here; pick another corner")

        val name = assertNotNull(editor.drag(a, b), "the drag painted nothing: ${editor.refusal}")
        val room = assertNotNull(editor.plan.roomNamed(name))
        assertEquals(listOf(Cell(0, 11), Cell(1, 11), Cell(2, 11)), room.cells)
    }

    /**
     * A drag runs whichever way the finger went, and the room is the same either way.
     *
     * Dragging up-and-left is exactly as ordinary as dragging down-and-right, and a rectangle
     * computed from the corners in order rather than from their extents comes out with a negative
     * width — which the model refuses, so the host's drag would simply do nothing.
     */
    @Test
    fun aDragBackwardsPaintsTheSameRoomAsADragForwards() {
        val forwards = bungalow().apply { drag(Cell(0, 11), Cell(2, 11)) }
        val backwards = bungalow().apply { drag(Cell(2, 11), Cell(0, 11)) }
        assertEquals(
            forwards.plan.roomNamed("ROOM 1")?.cells,
            backwards.plan.roomNamed("ROOM 1")?.cells,
            "the same two corners in the other order drew a different room",
        )
    }

    @Test
    fun aPaintedRoomIsHeldSoTheRoomPanelOpensOnIt() {
        val editor = bungalow()
        val name = assertNotNull(editor.drag(Cell(0, 11), Cell(1, 11)))
        assertEquals(name, editor.heldName)
    }

    /**
     * A drag across a room that is already there is **refused, and the host is told which room**.
     *
     * A cell belonging to two rooms is a house that cannot say where a player is standing. The
     * model refuses it; what matters here is that the refusal arrives as a sentence rather than
     * as nothing happening, because nothing happening is indistinguishable from a missed touch.
     */
    @Test
    fun aDragAcrossAnExistingRoomIsRefusedAndNamesIt() {
        val editor = bungalow()
        val before = editor.plan.rooms.size

        // Start on bare grid and run INTO the kitchen, so this is a new room rather than the
        // kitchen growing.
        val painted = editor.drag(Cell(0, 3), Cell(0, 0))
        assertNull(painted, "a room was painted over the kitchen")
        assertEquals("THAT CROSSES KITCHEN", editor.refusal)
        assertEquals(before, editor.plan.rooms.size, "a refused drag changed the plan anyway")
    }

    @Test
    fun eachNewRoomGetsItsOwnProvisionalName() {
        val editor = bungalow()
        assertEquals("ROOM 1", editor.drag(Cell(0, 11), Cell(0, 11)))
        assertEquals("ROOM 2", editor.drag(Cell(2, 11), Cell(2, 11)))
    }

    /**
     * **An L-shaped room is expressible** — E4's acceptance criterion, through the gesture.
     *
     * A room is a list of strokes and not one rect, and the whole reason the grid was chosen over
     * free rectangles is that real houses have L-shaped rooms and rectangles cannot express them.
     * The model has always allowed it; this is the editor being able to *produce* one, by
     * starting the second drag inside the room the first one made.
     */
    @Test
    fun anLShapedRoomIsDrawnByStartingTheSecondDragInsideTheFirst() {
        val editor = bungalow()
        // Column 4 is bare down to row 3, and row 3 is bare all the way across. The two strokes
        // meet at (4,3), which is the corner of the L.
        val name = assertNotNull(editor.drag(Cell(4, 0), Cell(4, 3)), editor.refusal.orEmpty())
        assertEquals(
            name, editor.drag(Cell(4, 3), Cell(6, 3)),
            "the second drag made a new room instead of growing the first",
        )

        val room = assertNotNull(editor.plan.roomNamed(name))
        assertEquals(
            setOf(Cell(4, 0), Cell(4, 1), Cell(4, 2), Cell(4, 3), Cell(5, 3), Cell(6, 3)),
            room.cells.toSet(),
        )
        assertEquals(1, editor.plan.rooms.count { it.name == name }, "the room was duplicated")
    }

    /** Growing a room across a different room is still refused — the exemption is for itself only. */
    @Test
    fun growingARoomStillCannotCrossAnotherOne() {
        val editor = bungalow()
        val garage = assertNotNull(editor.plan.roomNamed("GARAGE"))
        val cells = garage.cells.size

        // GARAGE is rows 7..9, columns 5..9. STAIRS is rows 4..5, columns 6..9. A drag from
        // inside the garage up into the stairwell crosses a room that is not the garage.
        assertNull(editor.drag(Cell(6, 7), Cell(6, 4)), "the garage swallowed the stairs")
        assertEquals("THAT CROSSES STAIRS", editor.refusal)
        assertEquals(cells, assertNotNull(editor.plan.roomNamed("GARAGE")).cells.size)
    }

    @Test
    fun aDragThatIsCancelledPaintsNothing() {
        val editor = bungalow()
        val before = editor.plan.rooms.size
        editor.dragFrom(Cell(0, 11))
        editor.dragTo(Cell(2, 11))
        editor.cancelDrag()
        assertNull(editor.drag)
        assertEquals(before, editor.plan.rooms.size)
    }

    /** The live answer the preview is coloured with, and it must exempt the room being grown. */
    @Test
    fun theLivePreviewCallsACrossingBlockedAndItsOwnRoomNot() {
        val editor = bungalow()
        editor.dragFrom(Cell(0, 3))
        editor.dragTo(Cell(0, 0))
        assertTrue(editor.dragBlocked, "a drag into the kitchen was not shown as blocked")

        editor.cancelDrag()
        editor.dragFrom(Cell(0, 0))
        editor.dragTo(Cell(1, 1))
        assertFalse(editor.dragBlocked, "the kitchen was shown as blocking itself")
    }

    // ---- Naming --------------------------------------------------------------------------------

    /**
     * Naming the held room — the field and the preset chips are the same call, so this is both.
     *
     * The cards come across because a card is registered to a *name*: a rename that dropped them
     * would unregister a room's contents for a host who thought they were correcting a typo.
     */
    @Test
    fun namingTheHeldRoomCarriesItsCards() {
        val editor = bungalow()
        editor.open("GARAGE")
        val cards = editor.heldMarkers
        assertTrue(cards.isNotEmpty(), "the fixture room holds no cards; the test proves nothing")

        editor.renameHeld("WORKSHOP")
        assertNull(editor.plan.roomNamed("GARAGE"), "the old name survived the rename")
        assertEquals("WORKSHOP", editor.heldName)
        assertEquals(cards, editor.heldMarkers, "the room's cards were dropped by a rename")
    }

    /** The chips are named here so the screen cannot offer a different five than the model has. */
    @Test
    fun thePresetsAreTheFiveRoomsEveryHouseHas() {
        assertEquals(
            listOf("KITCHEN", "LIVING", "GARAGE", "BATH 1", "BED 1"), HomeEditorModel.PRESETS,
        )
    }

    /** A room's name is what a card is registered to, so two rooms cannot share one. */
    @Test
    fun renamingToANameAnotherRoomHoldsIsRefused() {
        val editor = bungalow()
        editor.open("GARAGE")
        editor.renameHeld("KITCHEN")
        assertEquals("A ROOM IS ALREADY CALLED KITCHEN", editor.refusal)
        assertEquals("GARAGE", editor.heldName, "the room was renamed anyway")
        assertNotNull(editor.plan.roomNamed("KITCHEN"))
    }

    @Test
    fun renamingToNothingIsRefused() {
        val editor = bungalow()
        editor.open("GARAGE")
        editor.renameHeld("   ")
        assertEquals("A ROOM NEEDS A NAME", editor.refusal)
        assertEquals("GARAGE", editor.heldName)
    }

    /**
     * The T card follows the room it is in.
     *
     * A rename that quietly left the terminal pointing at a name nothing answers to would give
     * the home no terminal, and the host would find out at REVIEW HOME with no idea why.
     */
    @Test
    fun renamingTheTerminalsRoomTakesTheTerminalWithIt() {
        val editor = bungalow()
        editor.open("HALL")
        editor.renameHeld("LANDING 2")
        assertEquals("LANDING 2", editor.terminal)
        assertTrue(editor.hasTerminal)
    }

    // ---- Type ----------------------------------------------------------------------------------

    /**
     * **Stairs hold nothing** (D-099), and the editor's half of that rule is that the cards stop
     * being registered *as part of* the type change rather than in a step that follows it.
     */
    @Test
    fun aRoomBecomingStairsGivesUpItsCards() {
        val editor = bungalow()
        editor.open("GARAGE")
        assertEquals(2, editor.heldMarkers.size)

        editor.setKind(RoomKind.Stairs)
        assertEquals(RoomKind.Stairs, editor.heldKind)
        assertEquals(emptyList(), editor.heldMarkers)
        assertFalse(editor.holdsAnything("GARAGE"))
    }

    @Test
    fun theTerminalsRoomBecomingStairsLeavesTheHomeWithoutOne() {
        val editor = bungalow()
        assertTrue(editor.hasTerminal)
        editor.open("HALL")
        editor.setKind(RoomKind.Stairs)
        assertFalse(editor.hasTerminal, "the terminal survived its room becoming a walk-through")
    }

    /** Changing the type back does not undo it. The cards were unregistered, not hidden. */
    @Test
    fun changingBackDoesNotReturnTheCards() {
        val editor = bungalow()
        editor.open("GARAGE")
        editor.setKind(RoomKind.Stairs)
        editor.setKind(RoomKind.Room)
        assertEquals(RoomKind.Room, editor.heldKind)
        assertEquals(emptyList(), editor.heldMarkers)
    }

    @Test
    fun aRoomKeepsItsCellsWhenItChangesType() {
        val editor = bungalow()
        editor.open("GARAGE")
        val cells = assertNotNull(editor.plan.roomNamed("GARAGE")).cells
        editor.setKind(RoomKind.Stairs)
        assertEquals(cells, assertNotNull(editor.plan.roomNamed("GARAGE")).cells)
    }

    // ---- Deleting ------------------------------------------------------------------------------

    @Test
    fun deletingARoomTakesItsCardsAndHoldsWhateverIsLeft() {
        val editor = bungalow()
        editor.open("GARAGE")
        editor.deleteHeld()
        assertNull(editor.plan.roomNamed("GARAGE"))
        assertEquals(emptyList(), editor.markersIn("GARAGE"))
        assertNotNull(editor.held, "the panel is left holding nothing while rooms remain")
        assertNotNull(editor.plan.roomNamed(editor.heldName))
    }

    @Test
    fun deletingTheTerminalsRoomLeavesTheHomeWithoutOne() {
        val editor = bungalow()
        editor.open("HALL")
        editor.deleteHeld()
        assertFalse(editor.hasTerminal)
        assertNull(editor.terminal)
    }

    // ---- Floors --------------------------------------------------------------------------------

    /**
     * Floors are **additive and unordered**, and the new one is opened rather than merely added.
     *
     * A host who adds a storey and is left looking at the list has no way to tell it worked.
     */
    @Test
    fun addingAFloorOpensItEmpty() {
        val editor = bungalow()
        val before = editor.floorCount
        val name = editor.addFloor()

        assertEquals(before + 1, editor.floorCount)
        assertEquals(name, editor.floorName)
        assertEquals(emptyList(), editor.rooms)
        assertNull(editor.held, "a storey with no rooms is holding one")
    }

    @Test
    fun aNewFloorsNameIsNotOneAlreadyInTheHouse() {
        val editor = bungalow()
        val names = editor.plan.floors.map { it.name }.toMutableList()
        repeat(3) { names += editor.addFloor() }
        assertEquals(names.size, names.toSet().size, "two storeys ended up with one name")
    }

    /** A room belongs to a storey, so the panel's subject changes with the storey. */
    @Test
    fun openingAnotherFloorHoldsARoomOnThatFloor()  {
        val editor = bungalow()
        editor.open("GARAGE")
        editor.openFloor(HomeEditorModel.UPPER)
        assertEquals(HomeEditorModel.UPPER, editor.floorName)
        assertNotNull(
            editor.floor?.roomNamed(editor.heldName),
            "the panel is still holding a room on the storey the host left",
        )
    }

    @Test
    fun paintingLandsOnTheOpenStoreyAndNotTheOneBeforeIt() {
        val editor = bungalow()
        val ground = editor.roomsOn(HomeEditorModel.GROUND)
        editor.openFloor(HomeEditorModel.UPPER)
        val upper = editor.roomsOn(HomeEditorModel.UPPER)

        editor.drag(editor.firstBareCell(), editor.firstBareCell())

        assertEquals(ground, editor.roomsOn(HomeEditorModel.GROUND), "the ground floor grew a room")
        assertEquals(upper + 1, editor.roomsOn(HomeEditorModel.UPPER))
    }

    // ---- What the screens count ------------------------------------------------------------------

    /**
     * The counts the save screen shows are the plan's, so they cannot describe a different house.
     *
     * The port had them written down — 2 floors, 11 rooms, 9 markers — which is a claim that was
     * true of one bungalow and of nothing a host would ever paint.
     */
    @Test
    fun theCountsAreCounted() {
        val editor = bungalow()
        assertEquals(2, editor.floorCount)
        assertEquals(11, editor.roomCount)
        assertEquals(9, editor.markerCount)

        editor.open("GARAGE")
        editor.deleteHeld()
        assertEquals(10, editor.roomCount)
        assertEquals(7, editor.markerCount, "the deleted room's two cards are still being counted")
    }

    /**
     * The editor's sample is **converted from the live map's rooms, not typed out again**.
     *
     * [Plan]'s own KDoc makes the promise: one source of truth for every screen that draws a
     * plan, because a room sitting in one place in the editor and another on the map is a bug
     * nobody can see from inside the game. Two copies agree on the day they are written.
     */
    @Test
    fun theSampleGroundFloorIsTheLiveMapsRooms() {
        val editor = bungalow()
        val ground = assertNotNull(editor.plan.floorNamed(HomeEditorModel.GROUND))
        assertEquals(Plan.rooms.map { it.name }, ground.rooms.map { it.name })

        for (rect in Plan.rooms) {
            val painted = assertNotNull(ground.roomNamed(rect.name))
            val expected = buildSet {
                for (row in rect.r0..rect.r1) for (col in rect.c0..rect.c1) add(Cell(col, row))
            }
            assertEquals(expected, painted.cells.toSet(), rect.name)
            assertEquals(
                if (rect.transit) RoomKind.Stairs else RoomKind.Room, painted.kind, rect.name,
            )
        }
    }
}
