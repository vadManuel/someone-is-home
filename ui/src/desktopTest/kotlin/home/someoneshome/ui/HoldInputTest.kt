package home.someoneshome.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import home.someoneshome.model.FloorView
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.PlanCell
import home.someoneshome.model.RoomOutline
import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **The hold family** (D-141) — *hold what cannot be taken back* — driven with a real finger.
 *
 * Five controls take the two-second hold, and they are five separate things that are irreversible
 * at the moment they complete: the vote lock, arming a Revoke, arming an Egress, the host's LIGHTS
 * OUT and StairsWarn's UNREGISTER AND CONTINUE. Four of the five are pressed in the dark by a thumb
 * that cannot see what it is over.
 *
 * The vote and LIGHTS OUT are driven where their own screens are — `MeetingInputTest` and
 * `LobbyInputTest` — because a hold is not the interesting thing about either of those screens.
 * What is here is page 2, the stairs warning, and the one control D-141 explicitly **refuses** to
 * put friction on.
 *
 * ### Every one of these is asserted twice: it fires at two seconds, and not at 1.9
 *
 * A hold that fired the moment a finger landed would pass a test that only ever held the full two
 * seconds, and it would be the exact bug the gesture exists to prevent. So each is held to just
 * short of the window, released, and checked to have reached nothing.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class HoldInputTest {

    /** Most of a hold: the design's two seconds, a tenth short. */
    private val nearly = HOLD_MILLIS - 100L

    /** A full hold, with a couple of frames of margin so the completion really lands. */
    private val enough = HOLD_MILLIS + 200L

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
        mainClock.autoAdvance = false
    }

    private fun on(screen: ScreenId, role: PanelRole = PanelRole.Resident): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Home, role = role)).also { it.push(screen) }

    // ---- Page 2: the Insider arms, and a Resident's page is not a control ------------------------

    /**
     * **REVOKE arms on two seconds of a finger, and on nothing shorter** (D-141).
     *
     * An accidental arm spends a full cooldown and there is no cancel (D-009), so the cost of the
     * hold being wrong is a whole ability, silently, in the dark. The tile is the same tile it
     * always was — arming happens on page 2 *in place*, and nothing opens (D-142).
     */
    @Test
    fun revokeArmsOnAFullHoldAndNotBefore() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.Page2, PanelRole.Insider)
        show(model)
        val before = model.state.revoke

        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        assertEquals(before, model.state.revoke, "the Revoke armed before two seconds were up")
        onNodeWithText("REVOKE").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertEquals(before, model.state.revoke, "letting go early armed the Revoke anyway")

        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertNotEquals(before, model.state.revoke, "a full hold on REVOKE reached nothing")
        assertEquals(ScreenId.Page2, model.state.screen, "arming opened a view; it happens in place")
    }

    /**
     * **EGRESS is the same gesture on the same page**, and it is the misclick the design calls
     * game-ending.
     */
    @Test
    fun egressArmsOnAFullHoldAndNotBefore() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.Page2, PanelRole.Insider)
        show(model)

        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        onNodeWithText("EGRESS").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertEquals(ScreenId.Page2, model.state.screen, "most of a hold fired the Egress")

        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(ScreenId.Banner, model.state.screen, "a full hold on EGRESS reached nothing")
    }

    /**
     * **D-142, and the whole of it: a Resident's page 2 is identical at rest and inert under the
     * thumb.**
     *
     * Not *refused after two seconds* — refused from the first millisecond. A hold that filled and
     * then declined would be a self-test: press it, watch it, learn your own role. It would also be
     * worse than useless, because a bar filling in a dark house is world-observable to whoever is
     * standing behind the shoulder — the resting tell the parity was built against must not be
     * answered by opening a behavioural one.
     *
     * So this is asserted in pixels as well as in state. Both tiles are held for a full two seconds
     * and the screen is compared with itself before anything was touched: **not one pixel may
     * move**, which is the only form of the claim that cannot be satisfied by a bar somebody
     * decided to draw faintly.
     *
     * Injecting the bug: give either tile `enabled = true` regardless of role, or draw the hold
     * line for both. The first fails on the state, the second on the pixels, and both name the tile.
     */
    /**
     * The three tier-2 tiles and the view each hold opens, so the sweeps below cannot drift apart.
     *
     * Written as a list rather than repeated per test because what is being asserted about all
     * three is the *same* claim — a full hold and no less than one opens a view — and three copies
     * of it is three chances for one of them to be quietly weakened.
     */
    private val TIER_2_TILES = listOf(
        "SURGE" to ScreenId.SurgeTargets,
        "SPOOF" to ScreenId.SpoofRooms,
        "ISOLATE" to ScreenId.IsolateTargets,
    )

    @Test
    fun aResidentsPageTwoAnswersNeitherTileAndDrawsNothingUnderTheThumb() {
        // **All five tiles now**, and the three new ones are the tier-2 row: `BUS`, `THERMAL` and
        // `CACHE` are what a Resident's phone calls Surge, Spoof and Isolate. **All three open a
        // view on an Insider's phone**, so a Resident's copy of them is the only kind of control in
        // this game whose Insider version WALKS SOMEWHERE — which makes the screen assertion below
        // load-bearing rather than incidental.
        for (tile in listOf("POWER", "SUBSYS", "BUS", "THERMAL", "CACHE")) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val sent = mutableListOf<IntentMessage>()
                val model = FlowModel(
                    PanelState(screen = ScreenId.Home, role = PanelRole.Resident),
                    send = { sent += it },
                ).also { it.push(ScreenId.Page2) }
                show(model)
                val before = model.state
                val atRest: BufferedImage = onRoot().captureToImage().toAwtImage()

                onNodeWithText(tile).performTouchInput { down(center) }
                // Held well past the window, and looked at WITH THE FINGER STILL DOWN — a bar that
                // ran and then reset on the lift would be invisible to a check made afterwards.
                mainClock.advanceTimeBy(enough)
                val held: BufferedImage = onRoot().captureToImage().toAwtImage()
                onNodeWithText(tile).performTouchInput { up() }
                mainClock.advanceTimeBy(enough)

                assertEquals(before.revoke, model.state.revoke, "$tile: a Resident armed a Revoke")
                assertEquals(before.screen, model.state.screen, "$tile: a Resident's page 2 walked somewhere")
                // **And nothing went up the wire.** The state and the pixels are about this phone;
                // this is about the house. A tier-2 tile that reached the picker would not show up
                // as a Revoke and might not show up as a pixel, but it would show up here.
                assertEquals(
                    emptyList<IntentMessage>(), sent.toList(),
                    "$tile: a Resident's page 2 asked the house for something",
                )
                val (moved, at) = diff(atRest, held)
                assertEquals(
                    0, moved,
                    "$tile: a Resident's page 2 changed under the thumb — $moved pixels, first at $at",
                )
            }
        }
    }

    /**
     * **All three tier-2 tiles arm on a full hold and open a view** (D-141, the owner's targeting
     * rulings).
     *
     * *Surge and Isolate pick from a LIST — people, markers respectively; **Spoof picks from the
     * MAP**.* This is the one place the design asks a hold to **open a view** rather than to arm in
     * place: D-142 settled arming-in-place about the Revoke and the Egress, which have no target to
     * pick, and these three do.
     *
     * **Spoof was previously asserted the other way** — a test named
     * `spoofsTileIsInertForAnInsiderBecauseNothingIsBehindIt` said its tile walked nowhere, written
     * so that wiring it would have to come here and delete a test claiming it was not wired. That is
     * what happened; the row below is what replaced it.
     *
     * Held short first, for the reason every hold in this file is: a control that fired the moment a
     * finger landed would pass a test that only ever held the full two seconds, and a picker opening
     * under a stray thumb is a lit rectangle in a dark house at a moment nobody chose.
     */
    @Test
    fun theTierTwoTilesOpenTheirPickerOnAFullHoldAndNotBefore() {
        for ((tile, picker) in TIER_2_TILES) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val model = on(ScreenId.Page2, PanelRole.Insider)
                show(model)

                onNodeWithText(tile).performTouchInput { down(center) }
                mainClock.advanceTimeBy(nearly)
                onNodeWithText(tile).performTouchInput { up() }
                mainClock.advanceTimeBy(enough)
                assertEquals(ScreenId.Page2, model.state.screen, "$tile: most of a hold opened the picker")

                onNodeWithText(tile).performTouchInput { down(center) }
                mainClock.advanceTimeBy(enough)
                assertEquals(picker, model.state.screen, "$tile: a full hold reached nothing")
            }
        }
    }


    /**
     * **A row of the Surge picker asks the house for that seat, and says nothing back**
     * (`gdd.md:392`, `:398`).
     *
     * Three claims in one walk, and each is a different failure:
     *
     * 1. **The request names the row's seat by position.** The roster is seat-ordered, and turning a
     *    row into a seat is a lookup against the same list the house sent — the ballot's move
     *    (`MeetingRequest.asMessage`), one ability over. A build that sorted the list fires at
     *    somebody else and this is what says so.
     * 2. **Your own row is on the list and works.** *Insiders can target themselves with every
     *    ability* (`gdd.md:393`); a roster that omitted the actor would delete the alibi play and
     *    make being visibly targeted evidence of innocence.
     * 3. **Nothing on the actor's phone reports what happened.** The walk back to page 2 is the
     *    whole of it — no verdict field, no line, nothing that could differ between a shot that
     *    landed and one that did not.
     */
    @Test
    fun aSurgeRowAsksForThatSeatAndTellsTheActorNothing() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            val model = FlowModel(
                PanelState(
                    screen = ScreenId.Home,
                    role = PanelRole.Insider,
                    roster = listOf("PRIYA", "MARCUS", "DANI"),
                ),
                send = { sent += it },
            ).also { it.push(ScreenId.SurgeTargets) }
            show(model)

            val before = model.state
            // **The row at position 1 has to be seat 1's PERSON**, and that is the assertion an
            // injection found missing: a picker that sorted its roster still numbered its rows 0,
            // 1, 2, so a test that only ever clicked "row 1" fired at seat 1 either way and passed
            // while pointing at somebody else's name. Seat order is the wire here — see
            // `PanelState.roster` — so the name and the index have to be checked against each
            // other, not each against itself.
            onNodeWithTag("${TARGET_ROW}1").assert(hasText("MARCUS"))
            onNodeWithTag("${TARGET_ROW}0").assert(hasText("PRIYA"))

            onNodeWithTag("${TARGET_ROW}1").performClick()
            mainClock.advanceTimeBy(enough)

            assertEquals(
                listOf<IntentMessage>(IntentMessage.FireSurge(Seat(1))), sent.toList(),
                "the row asked for a different seat",
            )
            assertEquals(ScreenId.Page2, model.state.screen, "firing did not walk back to page 2")
            assertEquals(before.verdict, model.state.verdict, "the actor's phone reported something")
        }

    /**
     * **A row of the Isolate picker asks the house for that card**, and the picker filters nothing.
     *
     * The Egress-node exclusion is the **house's** (D-150): it arrives as a shorter list, and this
     * phone draws whatever it was sent. So the second half of this test hands the screen a list with
     * two of the four cards missing and asserts that what is drawn is the list — a client that
     * re-derived the exclusion would be a device deciding what an ability may be fired at.
     */
    @Test
    fun anIsolateRowAsksForThatCardAndThePickerFiltersNothing() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            val offered = listOf(
                Destination(MarkerId("garage-card"), shape = "diamond", room = "GARAGE"),
                Destination(MarkerId("landing-card"), shape = "cross", room = "LANDING"),
            )
            val model = FlowModel(
                PanelState(
                    screen = ScreenId.Home,
                    role = PanelRole.Insider,
                    isolateTargets = offered,
                ),
                send = { sent += it },
            ).also { it.push(ScreenId.IsolateTargets) }
            show(model)

            // The two rows the house sent are there; the two it withheld are not, and the screen
            // did not invent them.
            onNodeWithTag("${TARGET_ROW}garage-card").assertExists()
            onNodeWithTag("${TARGET_ROW}landing-card").assertExists()
            onNodeWithTag("${TARGET_ROW}hall-card").assertDoesNotExist()

            onNodeWithTag("${TARGET_ROW}landing-card").performClick()
            mainClock.advanceTimeBy(enough)
            assertEquals(
                listOf<IntentMessage>(IntentMessage.FireIsolate(MarkerId("landing-card"))),
                sent.toList(),
                "the row asked for a different card",
            )
            assertEquals(ScreenId.Page2, model.state.screen, "firing did not walk back to page 2")
        }

    /**
     * **A tap on the plan asks the house for that ROOM, and the screen says nothing back**
     * (`gdd.md:313`, `:398`, the owner's targeting ruling).
     *
     * The two list pickers' test, for the picker that is a picture. Four claims:
     *
     * 1. **A tap inside a room asks for that room by name.** The tap resolves a coordinate to a cell
     *    to a room — one `pointerInput` for the whole grid — so the coordinate and the name have to
     *    be checked against each other. Two different rooms are tapped, at coordinates chosen off
     *    the plan handed in, because a build that always answered with the first room would pass a
     *    test that only ever tapped one.
     * 2. **A tap on the STAIRS asks for nothing.** *Stairs hold nothing*; nobody checks in at a
     *    staircase. The refusal is the screen's and is argued in `ScreensSpoof`.
     * 3. **The plan drawn is the plan the house sent**, whole. The picker filters nothing, for the
     *    Isolate picker's reason.
     * 4. **Nothing on the actor's phone reports what happened.** This matters more here than for
     *    the other two, because a Spoof's whole consequence is invisible — so the walk back to page
     *    2 is genuinely all there is, and it must stay all there is.
     */
    @Test
    fun aSpoofTapAsksForThatRoomAndTellsTheActorNothing() {
        // A hand-built plan rather than the port's, so the coordinates below are read off something
        // this test states rather than off a fixture somebody may repaint.
        val plan = HomePlanView(
            listOf(
                FloorView(
                    "GROUND",
                    listOf(
                        RoomOutline("KITCHEN", cells = cells(0..2, 0..2)),
                        RoomOutline("GARAGE", cells = cells(6..9, 8..11)),
                        RoomOutline("STAIRS", stairs = true, cells = cells(4..5, 4..7)),
                    ),
                ),
            ),
        )

        // **Two rooms at opposite corners, each in its own walk**, because a tap fires and walks
        // back to page 2 — so a second tap in the same visit would be a tap on a screen the phone
        // has left. Two answers rather than one is the point: a build that resolved every tap to
        // the first room in the list would pass a test that only ever tapped one.
        for ((cell, room) in listOf((1 to 1) to "KITCHEN", (8 to 10) to "GARAGE")) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val sent = mutableListOf<IntentMessage>()
                val model = FlowModel(
                    PanelState(screen = ScreenId.Home, role = PanelRole.Insider, homePlan = plan),
                    send = { sent += it },
                ).also { it.push(ScreenId.SpoofRooms) }
                show(model)

                val before = model.state
                onNodeWithTag(GROUND).assertExists("the plan the house sent was not drawn")
                // The room labels prove the whole plan was drawn and none of it filtered — the
                // Isolate picker's claim, for a picker with no rows to count.
                onNodeWithText("KITCHEN").assertExists()
                onNodeWithText("GARAGE").assertExists()
                onNodeWithText("STAIRS").assertExists()

                // Cell centres resolved off the node's real bounds ONCE, before any gesture:
                // querying the tree from inside a `performTouchInput` block fails, and the failure
                // reads as "the plan was not drawn" rather than as "the test asked at the wrong
                // time".
                val bounds = onNodeWithTag(GROUND).fetchSemanticsNode().size
                fun at(x: Int, y: Int) = Offset(
                    bounds.width * (x + 0.5f) / Plan.COLS,
                    bounds.height * (y + 0.5f) / Plan.ROWS,
                )

                // **The stairs first**, so a build that answered every tap with a room is caught
                // before the sends list has anything real in it to hide behind. *Stairs hold
                // nothing*; nobody checks in at a staircase, and the refusal is the screen's — see
                // `ScreensSpoof`, where it is argued that a finger that never became a fire is not
                // rule 1's forbidden shape.
                onNodeWithTag(GROUND).performTouchInput { click(at(4, 5)) }
                mainClock.advanceTimeBy(enough)
                assertEquals(
                    emptyList<IntentMessage>(), sent.toList(),
                    "a tap on the stairs claimed a check-in; stairs hold nothing",
                )
                assertEquals(
                    ScreenId.SpoofRooms, model.state.screen,
                    "a tap on the stairs walked away as though it had fired",
                )

                onNodeWithTag(GROUND).performTouchInput { click(at(cell.first, cell.second)) }
                mainClock.advanceTimeBy(enough)
                assertEquals(
                    listOf<IntentMessage>(IntentMessage.FireSpoof(room)), sent.toList(),
                    "the tap at $cell asked for a room other than $room",
                )
                assertEquals(ScreenId.Page2, model.state.screen, "firing did not walk back to page 2")
                // **Nothing on the actor's phone reports what happened**, and this matters more
                // here than on either list picker: a Spoof's whole consequence is invisible, so the
                // walk back to page 2 is genuinely all there is and must stay all there is.
                assertEquals(
                    before.verdict, model.state.verdict,
                    "the actor's phone reported something about a Spoof",
                )
            }
        }
    }

    /**
     * And the tiles are drawn for both roles, which is what makes the test above mean something.
     *
     * A page 2 that had simply stopped drawing the second tile for Residents would pass every
     * inertness check on this page while failing the parity the inertness exists to protect.
     */
    @Test
    fun bothRolesGetTwoTilesInTheSamePlaces() {
        for ((role, names) in mapOf(
            PanelRole.Insider to listOf("REVOKE", "EGRESS"),
            PanelRole.Resident to listOf("POWER", "SUBSYS"),
        )) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                show(on(ScreenId.Page2, role))
                for (name in names) {
                    onNodeWithText(name).assertExists("$role lost the $name tile")
                }
            }
        }
    }

    // ---- The stairs warning ----------------------------------------------------------------------

    /**
     * **UNREGISTER AND CONTINUE is a hold**: it discards a registration the host climbed the stairs
     * to make, and the only way back is climbing them again with the cards in hand.
     */
    @Test
    fun unregisterAndContinueTakesTwoSeconds() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.RoomEdit)
        model.editor.open("GARAGE")
        model.pickRoomType(RoomKind.Stairs)
        assertEquals(ScreenId.StairsWarn, model.state.screen, "the warning did not come up")
        show(model)

        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertNotEquals(
            RoomKind.Stairs, model.editor.heldKind,
            "most of a hold unregistered the cards",
        )
        assertEquals(ScreenId.StairsWarn, model.state.screen)

        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(RoomKind.Stairs, model.editor.heldKind, "a full hold changed nothing")
        assertEquals(ScreenId.Editor, model.state.screen, "the host was left on the warning")
    }

    /** The safe answer beside it is still one tap, because nothing about it is irreversible. */
    @Test
    fun moveThemFirstIsStillOneTap() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.RoomEdit)
        model.editor.open("GARAGE")
        model.pickRoomType(RoomKind.Stairs)
        show(model)

        onNodeWithText("MOVE THEM FIRST").performClick()
        mainClock.advanceTimeBy(50)
        assertEquals(ScreenId.MarkerSheet, model.state.screen, "the safe answer needs a hold")
        assertNotEquals(RoomKind.Stairs, model.editor.heldKind, "asking to move them moved the room")
    }

    // ---- The one place friction is refused --------------------------------------------------------

    /**
     * **STOP NOW is an instant tap, everywhere it appears, and this test exists to keep it one.**
     *
     * D-141 puts friction on five controls and refuses it here, deliberately. It is the panic exit
     * from a Subroutine, and D-111 made abandonment free by design: the work plane hears nothing,
     * no partial state is held, and the next scan restarts. **Friction on the exit would undo
     * that.** A player who hears somebody in the doorway has to be able to be looking at nothing in
     * the time it takes to lift a thumb — and a two-second bar filling while they wait is both a
     * delay and a lit rectangle on the screen of somebody trying to stop being interesting.
     *
     * So this is a test that fails if somebody adds friction: it sweeps every screen in the game,
     * finds the ones that say STOP NOW, and requires each to be reachable by a single click. Turning
     * one into a hold takes its click action away and fails here by name.
     */
    @Test
    fun stopNowIsOneTapOnEveryScreenThatOffersIt() {
        val found = mutableListOf<String>()
        for (id in ScreenId.entries) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val model = on(id)
                show(model)
                if (onAllNodes(hasText("STOP NOW")).fetchSemanticsNodes().isEmpty()) {
                    return@runDesktopComposeUiTest
                }
                found += id.name
                assertEquals(
                    1,
                    onAllNodes(hasClickAction() and hasText("STOP NOW")).fetchSemanticsNodes().size,
                    "$id: STOP NOW is not a single-tap control",
                )
                onNodeWithText("STOP NOW").performClick()
                mainClock.advanceTimeBy(50)
                assertNotEquals(id, model.state.screen, "$id: one tap on STOP NOW went nowhere")
            }
        }
        // The scan viewfinder and every built Subroutine screen. Asserted as a floor rather than a
        // list, so building a seventh Subroutine does not have to edit this test — but a sweep that
        // silently found none would otherwise pass, which is the failure this line exists for.
        assertTrue(
            found.size >= Subroutine.built.size + 1,
            "STOP NOW was found on only ${found.size} screen(s): $found",
        )
    }

    // ---- No button dismissals anywhere (D-105) ----------------------------------------------------

    /**
     * **There is no DISMISS button left in the game**, on any screen, in either role.
     *
     * D-105 deleted read state and D-119 made the swipe the acknowledgment, which leaves a
     * dismissal *control* with nothing honest to be: it would be the one thing in the app claiming
     * to know what a player has looked at. The house notice was the last one holding out.
     */
    @Test
    fun nothingInTheGameOffersAButtonThatDismisses() {
        val offenders = mutableListOf<String>()
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 300, height = 650) {
                    show(on(id, role))
                    val found = onAllNodes(
                        hasClickAction() and hasText("DISMISS", substring = true, ignoreCase = true),
                    ).fetchSemanticsNodes().size
                    if (found > 0) offenders += "$id/${role.name}"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "a notification can be put away by pressing something: $offenders",
        )
    }

    /**
     * The cells of a rectangle, so a test plan can be written as two ranges rather than as a list.
     *
     * `RoomOutline` holds cells rather than rectangles on purpose — an L-shaped room is a set of
     * squares and nothing else — and this is the rectangle case spelled out where the fixture is,
     * not a helper the production type is missing.
     */
    private fun cells(xs: IntRange, ys: IntRange): List<PlanCell> =
        ys.flatMap { y -> xs.map { x -> PlanCell(x, y) } }

    /** The one storey every Spoof fixture in this file paints, tagged. */
    private val GROUND = "${SPOOF_FLOOR}GROUND"

    /** How many pixels differ, and the first place they do. */
    private fun diff(a: BufferedImage, b: BufferedImage): Pair<Int, String?> {
        var count = 0
        var first: String? = null
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++
                    if (first == null) first = "($x,$y)"
                }
            }
        }
        return count to first
    }
}
