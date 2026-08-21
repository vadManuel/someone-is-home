package home.someoneshome.ui

import home.someoneshome.model.Cell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The gesture, against a real touch stack.**
 *
 * [HomeEditorTest] proves what the editor does when it is told a finger went from one cell to
 * another. This proves the other half — that a finger really does produce those cells — and it is
 * the half nothing else can reach. `ScreenGraphTest` fires click *semantics actions*, which a
 * `pointerInput` does not publish; `DeviceLayoutTest` renders and measures pixels. Between them
 * the entire drag-two-corners gesture could be wired to nothing and every test would stay green.
 *
 * That is not hypothetical. The two detectors here sit in separate `pointerInput` blocks because
 * one cannot wait for touch slop and for a finger-up-without-movement at the same time, and
 * getting that wrong turns a slow, careful drag into a tap on its first corner — which on this
 * screen means the host opens the room panel instead of painting the room they were drawing.
 *
 * ### The window is the grid
 *
 * Rendered at exactly [HomeEditorModel.COLS] × [HomeEditorModel.ROWS] times [CELL] pixels, so a
 * cell's centre is arithmetic rather than a guess, and the mapping from pixels back to cells is
 * being tested rather than assumed.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSurfaceTest {

    private companion object {
        const val CELL = 20
        const val WIDTH = HomeEditorModel.COLS * CELL
        const val HEIGHT = HomeEditorModel.ROWS * CELL
    }

    private fun centreOf(cell: Cell) =
        Offset(cell.x * CELL + CELL / 2f, cell.y * CELL + CELL / 2f)

    private fun surface(
        editor: HomeEditorModel,
        onOpenRoom: (Cell) -> Unit = {},
        gesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit,
    ) = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
        setContent { EditorSurface(editor, onOpenRoom, Modifier.fillMaxSize()) }
        onRoot().performTouchInput(gesture)
        waitForIdle()
    }

    /** A drag across bare grid paints a room covering exactly the cells it crossed. */
    @Test
    fun aFingerDraggedAcrossBareGridPaintsTheRoomItCrossed() {
        val editor = HomeEditorModel.bungalow()
        val from = Cell(4, 0)
        val to = Cell(4, 3)
        assertNull(editor.roomAt(from), "the fixture has a room at the corner this test drags from")

        surface(editor) {
            down(centreOf(from))
            moveTo(centreOf(Cell(4, 1)))
            moveTo(centreOf(Cell(4, 2)))
            moveTo(centreOf(to))
            up()
        }

        val room = assertNotNull(
            editor.plan.roomNamed("ROOM 1"),
            "the drag painted nothing: ${editor.refusal}",
        )
        assertEquals(
            listOf(Cell(4, 0), Cell(4, 1), Cell(4, 2), Cell(4, 3)), room.cells,
        )
    }

    /**
     * A tap opens the room under the finger — **that room, not the one the panel was already on.**
     *
     * The tap has to survive sharing the surface with a drag detector. A finger that goes down and
     * comes up without moving is the one input both are watching for.
     */
    @Test
    fun aFingerTappedOnARoomOpensThatRoom() {
        val editor = HomeEditorModel.bungalow()
        val opened = mutableListOf<Cell>()
        val onGarage = Cell(6, 8)
        assertEquals("GARAGE", editor.roomAt(onGarage)?.name, "the fixture moved under this test")

        surface(editor, onOpenRoom = { opened += it }) {
            down(centreOf(onGarage))
            up()
        }

        assertEquals(listOf(onGarage), opened)
    }

    /** And nothing is painted by a tap. A room with no size is not what the host asked for. */
    @Test
    fun aTapPaintsNothing() {
        val editor = HomeEditorModel.bungalow()
        val before = editor.plan.rooms.size

        surface(editor) {
            down(centreOf(Cell(4, 0)))
            up()
        }

        assertEquals(before, editor.plan.rooms.size, "a tap on bare grid painted a room")
    }

    /**
     * A drag off the edge of the grid stops at the edge rather than stopping.
     *
     * Clamped, not refused: a rectangle that froze when the finger left the plan would read as the
     * app having stopped listening, and the host would lift and try again.
     */
    @Test
    fun aDragThatRunsOffTheGridStopsAtTheEdge() {
        val editor = HomeEditorModel.bungalow()

        surface(editor) {
            down(centreOf(Cell(8, 11)))
            moveTo(Offset(WIDTH + 400f, HEIGHT + 400f))
            up()
        }

        val room = assertNotNull(
            editor.plan.roomNamed("ROOM 1"),
            "the drag painted nothing: ${editor.refusal}",
        )
        assertEquals(
            setOf(Cell(8, 11), Cell(9, 11)), room.cells.toSet(),
            "the rectangle ran past the last column or stopped tracking",
        )
    }

    /**
     * A drag that starts inside a room grows it — the gesture that makes an L-shaped room, which
     * is E4's acceptance criterion and the design's second argument for a grid over rectangles.
     */
    @Test
    fun aFingerDraggedFromInsideARoomGrowsIt() {
        val editor = HomeEditorModel.bungalow()
        val garage = assertNotNull(editor.plan.roomNamed("GARAGE"))
        val before = editor.plan.rooms.size

        // GARAGE is rows 7..9, columns 5..9. Row 11 is bare, and so is row 10 under it.
        surface(editor) {
            down(centreOf(Cell(6, 9)))
            moveTo(centreOf(Cell(6, 10)))
            moveTo(centreOf(Cell(6, 11)))
            up()
        }

        val grown = assertNotNull(editor.plan.roomNamed("GARAGE"))
        assertEquals(before, editor.plan.rooms.size, "the drag made a new room instead of growing one")
        assertTrue(Cell(6, 11) in grown.cells, "the garage did not grow")
        assertTrue(grown.cells.size > garage.cells.size)
        assertEquals("GARAGE", editor.heldName)
    }
}
