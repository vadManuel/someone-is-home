package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Cell
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.RoomKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The flow layer's properties — the ones that hold over the whole graph at once, which is exactly
 * the kind nobody checks by walking a phone.
 *
 * A screen list is easy to eyeball. A screen *graph* is not: an auto-advance pointing at a defunct
 * end, a screen with no inbound edge, a meeting you can step backwards out of — each is one wrong
 * line among sixty, invisible in review, and each is only ever discovered by somebody standing in
 * a dark house holding a phone that will not move.
 *
 * The companion to this is `ScreenGraphTest`, which renders every screen and fires every tap
 * target it publishes, and fails if [ScreenGraph] and the ported screens disagree. Between the
 * two, the graph is checked against the code and the code is checked against itself.
 */
class FlowTest {

    /**
     * Screens with no way onward at all, and the reason each one has none: **the house has to
     * move you**.
     *
     * A revoked player sits in the dark until a meeting is called; a restrained player waits for
     * the others to finish reading the result; a player outside the system waits for the round to
     * end.
     *
     * **The disconnect screen left this set** (D-260). It waits for the house to come back, and a
     * house that never does left the guest with no way off it but stopping the app; LEAVE THIS HOUSE
     * is its way onward now ([Flow.viaActions]).
     *
     * **The two endings used to be in here and are not any more** (D-157). They were terminals —
     * the evening finished wherever the round did — and NEW ROUND is the walk off both of them.
     * The set shrinking is the change; a test that had left them in would have gone on asserting
     * that the app could not have a second round.
     *
     * Everything else must publish a control or say it will move on. A screen that does neither
     * is a phone that has stopped, and in this game a phone that has stopped is a player standing
     * in the dark with no instruction.
     */
    private val awaitingTheHouse = setOf(
        ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost3,
    )

    /**
     * **The one screen that stops on purpose, with no house behind it: the launch gate** (D-292).
     *
     * A phone without Ultra Wideband is told it cannot play and has no way forward. It is not in
     * [awaitingTheHouse], because no house exists to move it: the app root draws it at launch and
     * builds nothing else.
     */
    private val stopsByDesign = setOf(ScreenId.NoUwb)

    /**
     * Every way off a screen: what its own controls reach, what the actions layer reaches for the
     * one chip that hands over its decision, what the screen does on its own, **and what the house
     * does to it**.
     *
     * The last is not a loosening. A house push is a real way onward — it is just not one this
     * phone takes, which is the whole distinction between [Flow.autoAdvance] and
     * [Flow.housePushes]. Left out, the meeting's four screens would read as blind alleys the moment
     * their timers became pushes, and the honest reading of that would have been to add them to
     * [awaitingTheHouse] — which is the set of screens nothing ever moves you off at all.
     */
    private fun onward(id: ScreenId): Set<ScreenId> =
        ScreenGraph.exitsOf(id) + Flow.viaActions[id].orEmpty() +
            setOfNotNull(Flow.autoAdvance[id]?.to) + setOfNotNull(Flow.housePushes[id]?.to)

    /** The card marked T, as a piece of paper the deck could have printed. */
    private fun terminalCard(id: String = "SEEDT01") =
        MarkerCard(CardPayload.VERSION, MarkerShapes.TERMINAL, MarkerId(id))

    /** The meeting card, the same way. */
    private fun meetingCard(id: String = "SEEDU01") =
        MarkerCard(CardPayload.VERSION, MarkerShapes.MEETING, MarkerId(id))

    private fun card(shape: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shape), MarkerId(id))

    // ---- The graph -------------------------------------------------------------------------

    @Test
    fun everyScreenEitherOffersAWayOnwardOrIsWaitingForTheHouse() {
        val stuck = ScreenId.entries.filter { onward(it).isEmpty() }.toSet()
        val expected = awaitingTheHouse + stopsByDesign
        assertEquals(
            expected, stuck,
            "screens with no exit and no fall-through must be exactly the ones the house has to " +
                "move, and the launch gate: unexpectedly stuck ${stuck - expected}, " +
                "unexpectedly moving ${expected - stuck}",
        )
    }

    @Test
    fun noScreenListsItselfAsAWayOnward() {
        for (id in ScreenId.entries) {
            assertFalse(id in onward(id), "$id leads to itself")
        }
    }

    /**
     * **The whole screen list is accounted for.**
     *
     * Start at the two screens a launch can open on, [ScreenId.Boot] and the launch gate
     * [ScreenId.NoUwb] (D-292), and walk every tap and every fall-through; then start again at each
     * screen only the house can push, and at the two the port cannot route to. Between them, every
     * screen in the game must be covered.
     *
     * A screen missing from the result is an orphan: it exists, `Screen` draws it, and no route in
     * the app or on paper reaches it. That is how a designed screen quietly stops being part of
     * the game.
     */
    @Test
    fun everyScreenIsReachedByWalkingOrIsOneTheHouseMustPush() {
        val seen = mutableSetOf<ScreenId>()
        val queue = ArrayDeque(
            listOf(ScreenId.NoUwb, ScreenId.Boot) + Flow.housePushed + Flow.unrouted + Flow.houseOpened,
        )
        seen += queue
        while (queue.isNotEmpty()) {
            for (next in onward(queue.removeFirst())) if (seen.add(next)) queue.addLast(next)
        }
        assertEquals(
            emptySet(), ScreenId.entries.toSet() - seen,
            "orphaned screens — drawn, and reachable by nothing",
        )
    }

    /**
     * The other half of the same claim: a screen listed as house-pushed must really be
     * unreachable, or the list is a comforting fiction.
     *
     * This is the one that bites. Wiring a tap straight to `Revoked` — a debug shortcut, a
     * convenience during a playtest — would make the client able to put itself out of the round,
     * and the list here would still say the house does it.
     */
    @Test
    fun nothingWalksToAScreenOnlyTheHouseCanPush() {
        // WALKED to, not pushed to. [onward] counts a house push as a way onward, which is right
        // for "is this screen a blind alley" and exactly wrong here: every screen in [housePushed]
        // is reached by the house, and asking whether the house reaches it would make this test
        // assert its own negation.
        fun walked(id: ScreenId): Set<ScreenId> =
            ScreenGraph.exitsOf(id) + Flow.viaActions[id].orEmpty() +
                setOfNotNull(Flow.autoAdvance[id]?.to)

        val walkedTo = ScreenId.entries.flatMapTo(mutableSetOf()) { walked(it) }
        for (id in Flow.housePushed + Flow.unrouted) {
            assertFalse(
                id in walkedTo,
                "$id is listed as unreachable by tapping, but something reaches it: " +
                    ScreenId.entries.filter { id in walked(it) },
            )
        }
    }

    /**
     * **A view the house opens is entered from nowhere else** (D-199, [Flow.houseOpened]).
     *
     * The narrower rule that stands in for [nothingWalksToAScreenOnlyTheHouseCanPush] on a screen
     * the player uses once it is open. Something may walk to it, but only a screen that is itself
     * reached from that view and from nothing else -- the live view's own timelapse coming back.
     * A tile, a dock button or a refusal that walked to the live view would be the NO SIGNAL
     * screen's unconditional RETRY again: the map opened from somewhere other than the station.
     */
    @Test
    fun nothingWalksIntoAHouseOpenedViewFromOutsideIt() {
        fun walked(id: ScreenId): Set<ScreenId> =
            ScreenGraph.exitsOf(id) + Flow.viaActions[id].orEmpty() +
                setOfNotNull(Flow.autoAdvance[id]?.to)

        fun walkersOf(id: ScreenId): Set<ScreenId> =
            ScreenId.entries.filterTo(mutableSetOf()) { id in walked(it) }

        for (view in Flow.houseOpened) {
            for (walker in walkersOf(view)) {
                assertEquals(
                    setOf(view), walkersOf(walker),
                    "$walker walks into $view, and $walker is reachable from outside it",
                )
            }
        }
    }

    // ---- Auto-advance ------------------------------------------------------------------------

    @Test
    fun everyAutoAdvanceLandsSomewhereWithAWayOnward() {
        for ((from, rule) in Flow.autoAdvance) {
            assertTrue(
                rule.to in ScreenGraph.exits,
                "$from advances to ${rule.to}, which is not in the screen graph",
            )
            assertTrue(
                onward(rule.to).isNotEmpty() || rule.to in awaitingTheHouse,
                "$from advances to ${rule.to}, which is a blind alley",
            )
        }
    }

    @Test
    fun everyAutoAdvanceCarriesAReasonAndARealDelay() {
        for ((from, rule) in Flow.autoAdvance) {
            assertTrue(rule.afterMillis > 0, "$from advances after ${rule.afterMillis}ms")
            assertTrue(rule.why.isNotBlank(), "$from advances for no stated reason")
        }
    }

    /**
     * The scan's ten seconds are the bar's, not a second opinion about it.
     *
     * The countdown on screen and the moment the light goes dark are the same fact; written as two
     * numbers they drift, and the drift is a lit phone held at a wall for longer than the bar
     * said it would be.
     */
    @Test
    fun theScanWindowIsTheBarThatDrawsIt() {
        val rule = Flow.autoAdvance.getValue(ScreenId.Scan)
        assertEquals(PanelVals.SCAN_SEGMENTS * 500, rule.afterMillis)
        assertEquals(10_000, rule.afterMillis, "the design's final scan window is ten seconds")
        assertEquals(ScreenId.Home, rule.to, "the phone goes back to where it was")
    }

    /**
     * The meeting is a line: ring, walk in, notices, talk, vote, result, lights out. No branches,
     * and every step taken by the house rather than waited on.
     *
     * **Four of those steps are now really taken by the house**, which is what D-134's E8-2
     * recorded them as. The walk is over the union of the two tables and the line is unchanged;
     * what changed is which table each step is in, and that is asserted directly below.
     */
    @Test
    fun theMeetingRunsFromTheRingToTheLightsGoingOut() {
        val line = listOf(
            ScreenId.Assemble, ScreenId.Notice, ScreenId.Discussion, ScreenId.Vote,
            ScreenId.Tally, ScreenId.Home,
        )
        for (start in listOf(ScreenId.Calling, ScreenId.Call, ScreenId.Found)) {
            var at = start
            for (expected in line) {
                val next = Flow.autoAdvance[at]?.to ?: Flow.housePushes[at]?.to
                    ?: throw AssertionError("$at does not move on; the meeting stalls there")
                assertEquals(expected, next, "from $at")
                at = next
            }
        }
    }

    /**
     * **The four transitions D-134's E8-2 named are pushes, and nothing about them is a timer.**
     *
     * The gate closing, the talk ending or a unanimous READY arriving, the window closing, and the
     * ghost walk-in. Each is a count of phones — every living player and every out player standing
     * at the meeting area (D-104), every hand up, every ballot locked — and a phone cannot count
     * phones. A row for any of these back in [Flow.autoAdvance] would be a device moving a player
     * on while the room is still talking, and six devices doing it at six different moments.
     *
     * Asserted both ways round, because a build that added the push and left the timer in place
     * would pass the walk above and race itself in play.
     */
    @Test
    fun theMeetingsOwnTransitionsAreTheHousesAndNotTimers() {
        val pushed = listOf(
            ScreenId.Assemble, ScreenId.Discussion, ScreenId.Vote, ScreenId.Tally,
            ScreenId.Ghost2, ScreenId.GhostMeeting,
        )
        for (screen in pushed) {
            assertTrue(
                Flow.housePushes.containsKey(screen),
                "$screen is not pushed by the house; the meeting stalls or a phone guesses",
            )
            assertNull(
                Flow.autoAdvance[screen],
                "$screen moves on a timer as well as on a push, and the two will race",
            )
        }
    }

    /**
     * Every push names a message the emit schema actually knows.
     *
     * A push waiting on a kind nobody emits is a screen a player never leaves, and it would look
     * exactly like a push that works. Keying [Flow.HousePush.on] on [MessageKind] rather than on a
     * sentence is what makes that checkable from `ui` without `ui` ever seeing `core`.
     */
    @Test
    fun everyHousePushWaitsOnAMessageTheSchemaEmits() {
        val known = EmitSchema.knownKinds().toSet()
        for ((from, push) in Flow.housePushes) {
            assertTrue(push.on in known, "$from waits on ${push.on}, which nothing emits")
            assertTrue(push.why.isNotBlank(), "$from is pushed for no stated reason")
            assertTrue(
                push.to in ScreenGraph.exits, "$from is pushed to ${push.to}, not in the graph",
            )
            assertTrue(
                onward(push.to).isNotEmpty() || push.to in awaitingTheHouse,
                "$from is pushed to ${push.to}, which is a blind alley",
            )
        }
    }

    /** The same meeting from outside the system, and the outside view arrives only after it. */
    @Test
    fun aPlayerWhoIsOutWalksInWatchesAndOnlyThenSeesOutside() {
        assertEquals(ScreenId.GhostMeeting, Flow.housePushes.getValue(ScreenId.Ghost2).to)
        assertEquals(ScreenId.Ghost3, Flow.housePushes.getValue(ScreenId.GhostMeeting).to)
    }

    // ---- The house is driving ----------------------------------------------------------------

    /**
     * D-102, read backwards: **a screen that arrives unasked buzzes**, so a screen that buzzes is
     * one the house put you on, and there is nothing behind it to step back into.
     *
     * The doctrine names one exception and this asserts it is the only one. The host's
     * registration scan buzzes because the phone is against a card with the display angled away —
     * the host tapped REGISTER MARKER to get there, so back is theirs to use.
     */
    @Test
    fun everyScreenThatArrivesUnaskedIsOneTheHouseIsDriving() {
        val unasked = PanelVals.BUZZING - ScreenId.ScanMarker
        assertEquals(
            emptySet(), unasked - Flow.houseDriving,
            "these buzz, so they arrived unasked, so back must be refused on them",
        )
        assertFalse(
            ScreenId.ScanMarker in Flow.houseDriving,
            "the host's registration scan buzzes for the card, not because it arrived unasked",
        )
    }

    /**
     * The no-back set, named in full, so it cannot grow or shrink by accident.
     *
     * The derived half above only proves the set is big enough. This proves it is not bigger:
     * every screen beyond the ones that arrive unasked has to be argued for, and the argument is
     * at [Flow.houseDriving]. Adding a screen here on a hunch strands a player on something they
     * opened themselves — in a dark house, on a phone with no way back, which means asking someone
     * out loud in a game played in silence.
     */
    @Test
    fun theHousesScreensAreExactlyTheOnesNamed() {
        val arrivedUnasked = PanelVals.BUZZING - ScreenId.ScanMarker
        val andTheseEight = setOf(
            ScreenId.NoUwb,       // the launch gate (D-292); nothing is behind it and nothing ahead
            ScreenId.Boot,        // the app just started; nothing is behind it
            ScreenId.Calling,     // you called the meeting and are now waiting like everyone else
            ScreenId.Discussion,  // mid-meeting, on the room's clock
            ScreenId.Vote,        // the same
            ScreenId.Ghost2,      // out, and walking in
            ScreenId.Ghost3,      // out, and watching from outside
            ScreenId.Disconnect,  // the house is gone; every screen behind assumes it is not
        )
        assertEquals(
            arrivedUnasked + andTheseEight, Flow.houseDriving,
            "the no-back set changed. Widening it strands a player on a screen they opened; " +
                "narrowing it lets a phone walk backwards out of a meeting. Neither is a tidy-up",
        )
    }

    /**
     * Back is refused on exactly the house's screens — and works everywhere else.
     *
     * Both halves matter. Refusing too little lets a phone walk backwards out of a meeting;
     * refusing too much strands a player on a screen they opened themselves, and in a dark house
     * a phone with no way back is a player who has to ask someone out loud.
     */
    @Test
    fun backIsRefusedOnTheHousesScreensAndNowhereElse() {
        for (id in ScreenId.entries) {
            val model = FlowModel(PanelState(screen = ScreenId.Home))
            model.go(id)
            val moved = model.back()
            if (id in Flow.houseDriving) {
                assertFalse(moved, "$id let the player step backwards; the house is driving")
                assertEquals(id, model.state.screen, "$id refused back and moved anyway")
            } else {
                assertTrue(moved, "$id refused back, stranding the player who opened it")
                assertEquals(ScreenId.Home, model.state.screen, "$id went back to the wrong place")
            }
        }
    }

    /**
     * **The browsing surfaces and the house's screens do not overlap, at all** (E-PAY-6).
     *
     * [Flow.backable] is derived from three tables and the derivation is the guard — but a
     * derivation is a claim about how three sets interact, and this asserts the consequence
     * directly. Somebody rewriting `backable` as a typed-out list (which is the obvious "clearer"
     * refactor) has to keep this true, and a list with one meeting screen in it fails here by name.
     *
     * The failure this catches is not subtle in its effect and is completely invisible on the
     * screen: a phone able to step backwards off `Vote` is a phone asserting a ballot that has
     * already been read.
     */
    @Test
    fun noBrowsingSurfaceIsAScreenTheHouseIsDriving() {
        assertEquals(
            emptySet(), Flow.backable intersect (Flow.houseDriving + Flow.housePushed),
            "a screen the house puts you on is offering a way backwards off it",
        )
    }

    /**
     * **The lobby is not a browsing surface, and the exclusion is deliberate** (E-PAY-6).
     *
     * The one screen subtracted by name rather than by rule. Nothing about the lobby is
     * house-driven — the lights are on and nothing has been decided — so every derivation in
     * `backable` would include it and a back arrow there would look entirely reasonable. It would
     * also be a lie: the socket stays up, the seat stays held, and five other phones go on counting
     * somebody who is looking at the join screen. **Leaving a lobby is said to the house**, by
     * `LobbyModel.leaveLobby`, and never walked away from.
     */
    @Test
    fun theLobbyIsLeftByASayingSoAndNeverByWalkingBack() {
        assertFalse(
            ScreenId.Lobby in Flow.backable,
            "the lobby gained a back arrow. A phone that walks off it is still seated, still " +
                "counted, and still holding a line the house has — see LobbyModel.leaveLobby",
        )
    }

    /**
     * **Nothing on any phone walks to the caller's ring** (D-121, E-PAY-6).
     *
     * The springboard drew a PHONE tile straight to `Calling`, which made a house meeting callable
     * from a chair — *no remote calls, ever*. The tile is deleted and the screen is now in
     * [Flow.housePushed], so `nothingWalksToAScreenOnlyTheHouseCanPush` covers it as well; this
     * states the rule in the words the ruling is written in, so that re-adding a tile fails with
     * D-121 in the message rather than with a set difference.
     */
    @Test
    fun aMeetingIsCalledByAWalkAndNeverByATile() {
        val reaches = ScreenId.entries.filter {
            ScreenId.Calling in ScreenGraph.exitsOf(it) + Flow.viaActions[it].orEmpty()
        }
        assertEquals(
            emptyList(), reaches,
            "$reaches can call a meeting without walking to the meeting card. D-121: meetings are " +
                "called only by physically scanning it — a remote call is a button that summons " +
                "the whole house from a chair",
        )
    }

    @Test
    fun backReturnsYouWhereYouCameFrom() {
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        model.go(ScreenId.Files)
        model.go(ScreenId.Home)
        model.go(ScreenId.Notes)
        assertTrue(model.back()); assertEquals(ScreenId.Home, model.state.screen)
        assertTrue(model.back()); assertEquals(ScreenId.Files, model.state.screen)
        assertTrue(model.back()); assertEquals(ScreenId.Home, model.state.screen)
        assertFalse(model.back(), "the trail is spent")
    }

    /**
     * Walking out of a meeting leaves nothing behind to walk back into. The round has moved on,
     * and every screen on the trail described one that has not.
     */
    @Test
    fun leavingTheHousesScreensLeavesNoTrail() {
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        model.go(ScreenId.Files)
        model.go(ScreenId.Calling)
        model.go(ScreenId.Assemble)
        model.push(ScreenId.Home)
        assertFalse(model.canGoBack, "the springboard before the meeting is still on the trail")
        assertFalse(model.back())
    }

    @Test
    fun theHousePushingClearsTheTrail() {
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        model.go(ScreenId.Files)
        model.push(ScreenId.Notify)
        assertFalse(model.back(), "a push is not somewhere you came from")
    }

    /**
     * The trail does not grow all round.
     *
     * Twenty-five minutes of tapping around a springboard against a whole-app allocation budget of
     * about half a megabyte a second — and the lamp has to fail in the same frame as phone contact.
     * A list that only ever grows is the wrong shape to have in the room.
     */
    @Test
    fun theTrailIsCapped() {
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        repeat(500) { model.go(if (it % 2 == 0) ScreenId.Files else ScreenId.Home) }
        var steps = 0
        while (model.back()) steps++
        assertTrue(steps in 1..64, "the trail kept $steps screens; it is supposed to be capped")
    }

    // ---- Arriving --------------------------------------------------------------------------

    /**
     * The two ways out are not synonyms, and the carrier says which one happened.
     *
     * `Revoke` is system power lent by the house; `Restrain` is a physical act by the room that
     * the house cannot prevent. Both end on the same couch and the same screens, so the screen
     * alone cannot tell them apart — the cause has to be carried, and a phone with no house
     * attached has to carry it too or the chrome reads UNREGISTERED and looks broken.
     */
    @Test
    fun steppingOntoAnOutScreenCarriesTheCause() {
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        model.go(ScreenId.Revoked)
        assertEquals(OutBy.Revoked, model.state.outBy)
        assertEquals("REVOKED", PanelVals(model.state).carrier)

        model.push(ScreenId.Restrained)
        assertEquals(OutBy.Restrained, model.state.outBy)
        assertEquals("RESTRAINED", PanelVals(model.state).carrier)
    }

    // ---- The one place a chip navigates ------------------------------------------------------

    /**
     * **Stairs hold nothing**, so turning an occupied room into one unregisters every card in it.
     * The host is told what that costs *before* it happens, which is why the chip hands its
     * decision to this layer instead of naming a screen: there is a question in between.
     */
    @Test
    fun pickingStairsAsksBeforeItChangesAnything() {
        val model = FlowModel(PanelState(screen = ScreenId.RoomEdit))
        model.editor.open("GARAGE")
        assertTrue(model.editor.holdsAnything("GARAGE"), "the fixture room holds no cards")

        model.pickRoomType(RoomKind.Stairs)
        assertEquals(ScreenId.StairsWarn, model.state.screen)
        assertEquals(RoomKind.Room, model.editor.heldKind, "the room changed before the host answered")

        // MOVE THEM FIRST: the host backs out and the room is untouched.
        model.go(ScreenId.MarkerSheet)
        assertEquals(RoomKind.Room, model.editor.heldKind)
        assertEquals(2, model.editor.heldMarkers.size, "the cards were given up by the question")

        // UNREGISTER AND CONTINUE: now, and only now.
        val confirmed = FlowModel(PanelState(screen = ScreenId.StairsWarn))
        confirmed.editor.open("GARAGE")
        confirmed.confirmStairs()
        assertEquals(RoomKind.Stairs, confirmed.editor.heldKind)
        assertEquals(emptyList(), confirmed.editor.heldMarkers, "stairs hold nothing")
        assertEquals(ScreenId.Editor, confirmed.state.screen)
    }

    /**
     * The other half, and the one the port could not express: **an empty room changes in place.**
     *
     * The design's fixture room always held cards, so the warning was unconditional, and a host
     * tagging a bare stairwell was interrogated about cards that were never there. The plan knows
     * what a room holds now, so the question is only asked when there is an answer worth having.
     */
    @Test
    fun anEmptyRoomBecomesStairsWithNothingToWarnAbout() {
        val model = FlowModel(PanelState(screen = ScreenId.RoomEdit))
        val name = paintAnEmptyRoom(model)

        model.pickRoomType(RoomKind.Stairs)
        assertEquals(ScreenId.RoomEdit, model.state.screen, "nothing was at stake and it asked anyway")
        assertEquals(RoomKind.Stairs, model.editor.heldKind)
        assertEquals(name, model.editor.heldName)
    }

    /**
     * [Flow.viaActions] is the only part of the graph no rendering test can read off a screen, so
     * it is the only part that could quietly become a description of something the app no longer
     * does. This walks all three of them.
     */
    @Test
    fun theEdgesTheActionsLayerOwnsAreEdgesItReallyWalks() {
        // A tap on the plan, landing on a room the editor knows is there.
        val plan = FlowModel(PanelState(screen = ScreenId.Editor))
        val garage = plan.editor.plan.roomNamed("GARAGE")!!
        plan.openRoomAt(garage.cells.first())
        assertEquals(Flow.viaActions.getValue(ScreenId.Editor), setOf(plan.state.screen))
        assertEquals("GARAGE", plan.editor.heldName, "it opened a room, but not the one under the finger")

        val chip = FlowModel(PanelState(screen = ScreenId.RoomEdit))
        chip.editor.open("GARAGE")
        chip.pickRoomType(RoomKind.Stairs)
        assertEquals(Flow.viaActions.getValue(ScreenId.RoomEdit), setOf(chip.state.screen))

        val confirm = FlowModel(PanelState(screen = ScreenId.StairsWarn))
        confirm.confirmStairs()
        assertEquals(Flow.viaActions.getValue(ScreenId.StairsWarn), setOf(confirm.state.screen))

        // A save that landed. The refused one stays where it is, which is the reason this edge is
        // the actions layer's rather than the button's.
        val save = FlowModel(
            PanelState(screen = ScreenId.SaveName),
            homes = SavedHomesModel(MemoryHomeStore()),
        )
        save.editor.nameHome("somewhere new")
        save.saveHome()
        assertEquals(Flow.viaActions.getValue(ScreenId.SaveName), setOf(save.state.screen))

        // Two seconds of a finger, which publishes no click action for a rendering test to fire.
        val delete = FlowModel(PanelState(screen = ScreenId.Delete))
        delete.deleteHome()
        assertEquals(Flow.viaActions.getValue(ScreenId.Delete), setOf(delete.state.screen))

        // HOST WITH THIS HOME, on a home that went on the air. The refused one stays where it is
        // — the reason this edge is the actions layer's rather than the button's (E-D3-2) — and
        // that half is walked immediately below, because an edge that is only ever proved in the
        // direction it moves is an edge nobody has checked can refuse.
        // A phone whose setup is done: what is walked here is the RADIO's edge, and D-161's gate
        // stands one step in front of it — see SetupGateTest, which walks the gate itself.
        val onAir = FlowModel(PanelState(screen = ScreenId.HomeDetail), perms = PermsModel.sample())
        onAir.hostHome("THE BUNGALOW")
        assertEquals(Flow.viaActions.getValue(ScreenId.HomeDetail), setOf(onAir.state.screen))

        val offAir = FlowModel(
            PanelState(screen = ScreenId.HomeDetail),
            lobby = LobbyModel(link = RefusingLobbyLink()),
            perms = PermsModel.sample(),
        )
        offAir.hostHome("THE BUNGALOW")
        assertEquals(
            ScreenId.HomeDetail, offAir.state.screen,
            "a home the radio refused walked into a lobby no other phone can join",
        )
        assertNotNull(offAir.lobby.refusal, "it stayed put and said nothing about why")

        // Either reserved card, read in a room while this home already has one somewhere else.
        // Not a tap at all: the camera raises it, and where it lands depends on what the map says
        // about the card. Both are walked, because the edge set has two members and a walk that
        // only ever fired the T card would let the meeting card's route rot unnoticed.
        val reserved = ScreenId.ScanMarker.let { from ->
            listOf(terminalCard(), meetingCard()).mapTo(mutableSetOf()) { card ->
                val scan = FlowModel(PanelState(screen = from))
                scan.editor.open("GARAGE")
                scan.cardScanned(CardPayload.encode(card))
                scan.state.screen
            }
        }
        assertEquals(Flow.viaActions.getValue(ScreenId.ScanMarker), reserved)

        // A line that was real, handed over. The refused one stays put, which is the reason this
        // edge is the actions layer's rather than the button's — see the walk below.
        val hand = FlowModel(PanelState(screen = ScreenId.Secret))
        hand.lobby.typeLine("i still have priya's spare key")
        hand.handOverLine()
        assertEquals(Flow.viaActions.getValue(ScreenId.Secret), setOf(hand.state.screen))

        // A tap on a network row, on a phone that may really take a seat. Refusable twice over —
        // D-161's setup gate and, since E-PAY-6, a phone that has not said what to call its owner —
        // and both refusals stay on the join screen, which is why the edge is here rather than on
        // the row. The refused half is walked in `LobbyClosedFlowTest`.
        val joinRow = FlowModel(PanelState(screen = ScreenId.Join), perms = PermsModel.sample())
        joinRow.lobby.nameResident("ROSE")
        joinRow.lobby.look()
        joinRow.attachToHome(joinRow.lobby.nearby.first())
        assertEquals(Flow.viaActions.getValue(ScreenId.Join), setOf(joinRow.state.screen))

        // **The lobby's two holds, and they are two members of one edge set** (D-141): the lights
        // going out, and the way out of the lobby (E-PAY-6). Neither publishes a click action.
        //
        // LEAVE and CLOSE THE HOUSE land on the same screen by construction — see
        // `FlowModel.leaveLobby` and `FlowModel.terminateHouse` — so one of them is walked here and
        // the pair of them, plus the push a client gets, is walked in `LobbyClosedFlowTest`.
        val lobbyExits = setOf(
            FlowModel(PanelState(screen = ScreenId.Lobby))
                .apply { lightsOut() }.state.screen,
            FlowModel(PanelState(screen = ScreenId.Lobby))
                .apply { leaveLobby() }.state.screen,
        )
        assertEquals(Flow.viaActions.getValue(ScreenId.Lobby), lobbyExits)

        // **Page 2's gestures, and the two Tier-1 holds publish no edge** (D-231). The Revoke and
        // the Egress each send a request and stay on page 2; the alert is the house's push
        // (`EgressOpened`), so `Banner` is in `Flow.housePushed`. The two targeted tier-2 tiles each
        // open a view, which is the targeting rulings and the one place the design asks a hold to
        // open one; Static fires in place (D-186).
        for (hold in listOf<FlowModel.() -> Unit>({ armEgress() }, { armRevoke() }, { fireStatic() })) {
            assertEquals(
                ScreenId.Page2,
                FlowModel(PanelState(screen = ScreenId.Page2, role = PanelRole.Insider)).apply(hold).state.screen,
                "a page-2 hold that arms in place walked this phone somewhere",
            )
        }
        val page2 = setOf(
            // The swipe back to page 1, which both roles have (`gdd.md:561`). The gesture itself
            // is performed in `SpringboardSwipeTest`; this is where it lands.
            FlowModel(PanelState(screen = ScreenId.Page2, role = PanelRole.Resident))
                .apply { go(ScreenId.Home) }.state.screen,
            FlowModel(PanelState(screen = ScreenId.Page2, role = PanelRole.Insider))
                .apply { go(ScreenId.SurgeTargets) }.state.screen,
            FlowModel(PanelState(screen = ScreenId.Page2, role = PanelRole.Insider))
                .apply { go(ScreenId.IsolateTargets) }.state.screen,
        )
        assertEquals(Flow.viaActions.getValue(ScreenId.Page2), page2)

        // Firing, off a row of either picker. **The walk is identical for a shot that landed and a
        // shot that did not**, which is `gdd.md:398` held by the navigation as well as by the
        // screen: this phone goes back to page 2 and is told nothing.
        val surge = FlowModel(PanelState(screen = ScreenId.SurgeTargets, role = PanelRole.Insider))
        surge.fireSurge(0)
        assertEquals(Flow.viaActions.getValue(ScreenId.SurgeTargets), setOf(surge.state.screen))

        val isolate =
            FlowModel(PanelState(screen = ScreenId.IsolateTargets, role = PanelRole.Insider))
        isolate.fireIsolate("card-hall")
        assertEquals(Flow.viaActions.getValue(ScreenId.IsolateTargets), setOf(isolate.state.screen))

        // **And STATIC, which fires without navigating at all** (D-186). It is on page 2 already
        // and it stays there — the two Tier-1 tiles' behaviour, not the two pickers'. Asserted as a
        // screen that did not move, because the failure worth catching is a walk appearing: a phone
        // that jumped anywhere on a Static would be announcing the fire to whoever was looking at
        // it.
        val jam = FlowModel(PanelState(screen = ScreenId.Page2, role = PanelRole.Insider))
        jam.fireStatic()
        assertEquals(
            ScreenId.Page2, jam.state.screen,
            "firing Static moved the phone, which is a walk a bystander could read",
        )

        // A house notice, swiped up — the last button dismissal in the app, retired (D-105).
        val notice = FlowModel(PanelState(screen = ScreenId.Notice))
        notice.dismissNotice()
        assertEquals(Flow.viaActions.getValue(ScreenId.Notice), setOf(notice.state.screen))

        // EVERY notification, swiped away (D-105, D-119). A drag rather than a tap, so no
        // rendering test that fires click actions can reach it, and where it lands is the screen
        // the notification arrived over rather than anything the notification itself names.
        // Walked off the arrivals map rather than off two fixtures, so a kind given a screen and
        // no gesture off it fails here.
        //
        // **The page swipe is walked beside it** (`gdd.md:561`): every screen that draws page 1
        // turns to page 2, the banners' screens included, because the page is still under the thumb.
        val drawsPageOne = setOf(
            ScreenId.Home, ScreenId.Notify, ScreenId.Quiet, ScreenId.Banner, ScreenId.EgressWidget,
        )
        fun turned(from: ScreenId): Set<ScreenId> =
            if (from in drawsPageOne) {
                setOf(FlowModel(PanelState(screen = from)).apply { go(ScreenId.Page2) }.state.screen)
            } else {
                emptySet()
            }
        for (from in drawsPageOne - Notifications.arrivals.keys) {
            assertEquals(Flow.viaActions.getValue(from), turned(from), "$from does not turn the page")
        }
        for ((from, arrival) in Notifications.arrivals) {
            val model = FlowModel(PanelState(screen = from))
            model.dismissNotification()
            assertEquals(
                Flow.viaActions.getValue(from), setOf(model.state.screen) + turned(from),
                "swiping the ${arrival.notification.kind} notification away left the phone on " +
                    "${model.state.screen}; it arrived over ${arrival.under}",
            )
        }

        // BEGIN, on a caught scan. The screen names no target because which Subroutine opens is a
        // fact about the card that was read — so this walks the roster rather than one fixture,
        // which is also what proves the declared set is every built Subroutine and not a list
        // somebody typed. An unbuilt one opens nothing and the phone stays where it is, which is
        // asserted rather than assumed: the failure mode has to be a Subroutine that does not
        // open, never a player put through somebody else's work.
        val opened = Subroutine.entries.mapNotNullTo(mutableSetOf()) { subroutine ->
            val begin = FlowModel(PanelState(screen = ScreenId.ScanCaught))
            begin.beginSubroutine(subroutine)
            begin.state.screen.takeIf { it != ScreenId.ScanCaught }
        }
        assertEquals(Flow.viaActions.getValue(ScreenId.ScanCaught), opened)
        assertEquals(
            Subroutine.built.size, opened.size,
            "every built Subroutine opens on its own screen, and no unbuilt one opens at all",
        )

        // **STOP NOW, off every Subroutine screen** — the actions layer's since it stopped being
        // only a navigation: it reports the performance window closing to the house before it
        // moves (`Intent.StopSubroutine`, D-111), so the control publishes an action rather than a
        // target and no rendering pass can read the destination off the screen.
        //
        // Every built one is walked rather than one of them, because ten screens sharing one
        // control is exactly the arrangement in which nine keep working and the tenth is wired to
        // nothing — and in the dark all ten look identical.
        for (subroutine in Subroutine.built) {
            val screen = subroutine.screen ?: continue
            val stop = FlowModel(PanelState(screen = screen))
            stop.stopSubroutine()
            assertEquals(
                Flow.viaActions.getValue(screen), setOf(stop.state.screen),
                "STOP NOW on $screen went somewhere the actions table does not name",
            )
        }

        // NEW ROUND, off both endings, and it is the actions layer's for LIGHTS OUT's reason:
        // whether the screen publishes it as a control depends on whether this phone is running
        // the house. Both are walked, because the control is on both and one that rotted on the
        // losing side's screen would be an evening that could only continue if it had gone one
        // particular way.
        for (ending in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            val again = FlowModel(PanelState(screen = ending), lobby = LobbyModel.sample())
            again.newRound()
            assertEquals(
                Flow.viaActions.getValue(ending), setOf(again.state.screen),
                "NEW ROUND on $ending went somewhere the actions table does not name",
            )
        }

        // **LEAVE THIS HOUSE, off RECONNECTING** (D-260): the lobby's own way out, for a house
        // that is not coming back. A hold, so no rendering test fires it; `RoundForgottenTest`
        // performs the hold, and `HouseGoneTest` walks it on a real socket.
        val gone = FlowModel(PanelState(screen = ScreenId.Disconnect), lobby = LobbyModel.sample())
        gone.leaveLobby()
        assertEquals(Flow.viaActions.getValue(ScreenId.Disconnect), setOf(gone.state.screen))

        assertEquals(
            setOf(
                // The join screen's network row, which is refusable twice over and therefore the
                // actions layer's rather than the row's (E-PAY-6).
                ScreenId.Join,
                ScreenId.Editor, ScreenId.RoomEdit, ScreenId.StairsWarn,
                ScreenId.SaveName, ScreenId.Delete, ScreenId.HomeDetail, ScreenId.ScanMarker,
                ScreenId.Secret, ScreenId.Lobby, ScreenId.ScanCaught, ScreenId.Disconnect,
                ScreenId.WinInsiders, ScreenId.WinResidents,
                // Page 2's three holds, and the two pickers a row fires off. The third hold fires
                // in place and declares no edge (D-186).
                ScreenId.Page2, ScreenId.SurgeTargets, ScreenId.IsolateTargets,
                ScreenId.Notice,
                ScreenId.Notify, ScreenId.Banner, ScreenId.Quiet, ScreenId.LockNotify,
                // The page swipe off the two page-1 screens that carry no banner (`gdd.md:561`).
                ScreenId.Home, ScreenId.EgressWidget,
                // STOP NOW's ten. Named in full rather than derived from the roster, for this
                // assertion's whole reason: a set computed from the thing it is checking agrees
                // with itself, and what is being caught here is an edge somebody declared and
                // nothing walks.
                ScreenId.SubHandshake, ScreenId.SubReplay, ScreenId.SubParity,
                ScreenId.SubShort, ScreenId.SubTrace, ScreenId.SubJam,
                ScreenId.SubSniff, ScreenId.SubDeallocate,
                ScreenId.SubInterrupt, ScreenId.SubDrift,
            ),
            Flow.viaActions.keys,
            "a new action edge was declared and nothing here walks it",
        )
    }

    /**
     * **A swipe on a screen with no banner moves nothing.**
     *
     * The fail-closed direction, and not a theoretical one: the gesture is a vertical drag on a
     * springboard whose two banner screens draw the *same* springboard underneath. A dismissal
     * that navigated regardless of whether anything was up would send a player who flicked at a
     * tile back to page 1 from wherever they were, and it would be blamed on the touchscreen.
     */
    @Test
    fun swipingWhereThereIsNoBannerDoesNothing() {
        for (id in ScreenId.entries) {
            if (Notifications.onScreen(id) != null) continue
            val model = FlowModel(PanelState(screen = id))
            model.dismissNotification()
            assertEquals(id, model.state.screen, "$id has no banner and a swipe moved the phone")
        }
    }

    /**
     * **Exactly two events dim the house, and the design named both (D-118).**
     *
     * The ruling this unit exists for, and the one a later kind will break by accident: the dim is
     * not a notification style, it is a two-member vocabulary. A light change that means one
     * specific thing is a signal; a light change that happens twenty times a round is noise, and
     * it is paid for out of the readability everything else in this game is built on.
     *
     * Written as the exact pair rather than as a count. `assertEquals(2, ...)` would pass on a
     * build where the Egress had gone quiet and a Subroutine notification had gone heavy — which
     * is not a smaller mistake than three, it is a bigger one.
     */
    @Test
    fun exactlyTwoKindsOfNotificationDimTheHouse() {
        assertEquals(
            listOf(NotificationKind.Opening, NotificationKind.Egress),
            NotificationKind.entries.filter { it.heavy },
            "the Egress and the house's opening message are the whole light vocabulary (D-118)",
        )
    }

    /**
     * **The ten seconds, and the screens that must never have them (D-119).**
     *
     * Heavy notifications clear themselves after [HEAVY_HOLD] whether anybody touches them or not,
     * because the two of them dim every phone in the building and the light has to come back for
     * a player who is holding theirs as a lamp with both hands busy. **Quiet ones never do**: they
     * sit until swiped, and the swipe is the only acknowledgment D-105 left in the game.
     *
     * Read off [Notifications.arrivals] against [Flow.autoAdvance], which are two independent
     * lists — the timing table is written out by hand where a reader of the flow will find it, and
     * this is the check that it says what the kinds say.
     */
    @Test
    fun heavyNotificationsClearThemselvesAndQuietOnesNeverDo() {
        // THE NUMBER IS WRITTEN OUT, not read back off the constant being checked. Comparing the
        // table against `HEAVY_HOLD` alone is a value asserted equal to itself: it passed green
        // with the hold moved to seven seconds, and the whole point of ten is that it is the
        // design's, chosen for a player holding the phone as a lamp with both hands busy.
        assertEquals(10_000, HEAVY_HOLD, "D-119 gives a heavy notification ten seconds")

        for ((from, arrival) in Notifications.arrivals) {
            val rule = Flow.autoAdvance[from]
            if (arrival.notification.kind.heavy) {
                assertNotNull(rule, "$from is heavy and dims the house with nothing to undim it")
                assertEquals(HEAVY_HOLD, rule.afterMillis, "$from holds the dim for the wrong time")
                assertEquals(
                    arrival.under, rule.to,
                    "$from expires onto ${rule.to} but was swiped away onto ${arrival.under}",
                )
            } else {
                assertNull(
                    rule,
                    "$from clears itself after ${rule?.afterMillis}ms — a quiet notification sits " +
                        "until it is swiped, and the swipe is the only acknowledgment there is",
                )
            }
        }
    }

    /**
     * **What is stored, and what the storage ruling actually partitions (D-119).**
     *
     * Quiet notifications are stored — they stand on the lock screen until swiped, because a
     * player walking back from a marker has to be able to find the thing again. The two heavy ones
     * are not: they clear themselves, and what they were about is in its own home. **An Egress is
     * never a stored notification** and a house notice at a meeting is stored nowhere at all.
     *
     * Named per kind rather than derived, because the derivation is the thing that would go wrong:
     * `!heavy && heldBy != null` is true of everything here today and would silently decide the
     * next kind's storage for whoever adds it.
     */
    @Test
    fun theStoredNotificationsAreTheQuietOnesTheHouseWantsFoundAgain() {
        assertEquals(
            listOf(NotificationKind.Text, NotificationKind.Unblocked),
            NotificationKind.entries.filter { it.stored },
            "a later text and an unblocked Subroutine are what stands under the clock (D-119)",
        )
        assertFalse(
            NotificationKind.Egress.stored,
            "the Egress persists on its widget and never as a stored notification",
        )
        assertFalse(
            NotificationKind.Notice.stored,
            "a house notice is shown once at the meeting it is about and stored nowhere",
        )
        assertEquals(
            Notifications.all.filter { it.kind.stored }.toSet(), Notifications.stored.toSet(),
            "the lock screen's list and the storage ruling disagree",
        )
    }

    /**
     * **Swiping is removal, not a mark.**
     *
     * The whole of what a dismissal does to the stored list, and the whole of what it is allowed
     * to do. There is no method here that could answer *was this one seen* and no field that could
     * hold the answer — a notification is standing or it is gone, and D-105 is that sentence.
     */
    @Test
    fun aSwipedNotificationIsRemovedRatherThanMarked() {
        val model = FlowModel(PanelState(screen = ScreenId.Lock))
        val first = model.notifications.standing.first()
        assertTrue(model.notifications.standing.size >= 2, "nothing to prove with one row")

        model.dismissStanding(first)
        assertFalse(first in model.notifications.standing, "the swipe left it on the lock screen")
        assertEquals(
            ScreenId.Lock, model.state.screen,
            "swiping one notification off the lock screen moved the phone somewhere",
        )

        // AND THE BANNER'S SWIPE IS THE SAME ACKNOWLEDGMENT. A quiet notification dismissed on the
        // springboard must not be waiting under the clock afterwards — a swipe that has to be made
        // twice is a swipe that means nothing.
        val quiet = FlowModel(PanelState(screen = ScreenId.Quiet))
        quiet.dismissNotification()
        assertFalse(
            Notifications.unblocked in quiet.notifications.standing,
            "the quiet banner was swiped away and the same sentence is still on the lock screen",
        )
    }

    /**
     * **The five kinds, and what each one leaves behind (D-105).**
     *
     * The persistence claim is a field rather than a sentence in a comment precisely so it can be
     * checked, and this is half of the check: that the screen a kind names is a real screen, and
     * that exactly one kind names nothing. The other half is `NotificationsTest`, which renders
     * every screen in the game and looks for the words.
     */
    @Test
    fun exactlyOneKindOfNotificationSurvivesNowhere() {
        val nowhere = NotificationKind.entries.filter { it.heldBy == null }
        assertEquals(
            listOf(NotificationKind.Notice), nowhere,
            "a house notice is the one thing shown once and held nowhere",
        )
        for (kind in NotificationKind.entries - NotificationKind.Notice) {
            assertNotNull(kind.heldBy, "$kind claims to persist and names nowhere it persists in")
        }
        // Every kind is a notification somebody can actually receive. A kind with no notification
        // is a persistence rule about nothing, and it would pass every test above.
        assertEquals(
            NotificationKind.entries.toSet(), Notifications.all.map { it.kind }.toSet(),
            "a kind was declared and nothing was written for it",
        )

        // WHERE A NOTIFICATION OPENS IS WHERE IT SURVIVES. Not a coincidence of the three that
        // exist: the reason to tap a banner rather than swipe it away is to go to the thing it is
        // about, and the thing it is about is exactly what is still there afterwards. A banner
        // that opened somewhere it does not persist would leave a player who followed it standing
        // on a screen with no trace of what they came to look at.
        for (notification in Notifications.all) {
            assertEquals(
                notification.kind.heldBy, notification.opens,
                "the ${notification.kind} notification opens ${notification.opens} and survives " +
                    "into ${notification.kind.heldBy}",
            )
        }
    }

    // ---- The saved homes ---------------------------------------------------------------------

    /**
     * **MAP A NEW HOME starts an empty house**, not a copy of whichever one the editor was last
     * holding.
     *
     * One storey rather than none, because a plan with no storey has nowhere to paint and the
     * host's first drag would be refused for a reason that is not theirs.
     */
    @Test
    fun mappingANewHomeEmptiesTheEditorAndClosesWhatWasOpen() {
        val model = FlowModel(PanelState(screen = ScreenId.Maps))
        assertEquals(9, model.editor.roomCount, "the fixture moved under this test")

        model.mapNewHome()

        assertNull(model.homes.openName, "the new home would have replaced the old one on save")
        assertEquals(0, model.editor.roomCount)
        assertEquals(1, model.editor.floorCount)
        assertEquals(HomeEditorModel.GROUND, model.editor.floorName)
        assertEquals("HOME 1", model.editor.name, "a new home needs a name to save under")
        assertFalse(model.editor.hasTerminal)
    }

    /**
     * Opening a home is looking at it. **The editor is untouched** — a host who taps a row and
     * backs out again has not asked for the house they were painting to be thrown away.
     */
    @Test
    fun openingASavedHomeDoesNotTouchTheEditor() {
        val model = FlowModel(PanelState(screen = ScreenId.Maps))
        model.mapNewHome()
        val painting = model.editor.name

        model.openSavedHome("THE LAKE PLACE")

        assertEquals("THE LAKE PLACE", model.homes.openName)
        assertEquals(painting, model.editor.name, "the half-painted house was replaced by a tap")
        assertEquals(0, model.editor.roomCount)
    }

    /** EDIT THE PLAN and RENAME both say they will replace it, and both do — plan, cards and all. */
    @Test
    fun editingAnOpenHomeLoadsTheWholeThing() {
        val model = FlowModel(PanelState(screen = ScreenId.HomeDetail))
        model.mapNewHome()
        model.openSavedHome("THE BUNGALOW")

        model.editOpenHome()

        assertEquals("THE BUNGALOW", model.editor.name)
        assertEquals(9, model.editor.roomCount)
        assertEquals(9, model.editor.markerCount, "the cards stayed behind")
        assertEquals("HALL", model.editor.terminal, "the terminal stayed behind")
    }

    /**
     * A refused save stays put and says why. The list on screen is the list on the phone, and a
     * host who was told nothing would go looking for a house that is not there.
     */
    @Test
    fun aRefusedSaveDoesNotLeaveTheScreen() {
        val model = FlowModel(PanelState(screen = ScreenId.SaveName))
        model.mapNewHome()
        model.editor.nameHome("the lake place")

        model.saveHome()

        assertEquals(ScreenId.SaveName, model.state.screen)
        assertEquals("A HOME IS ALREADY CALLED THE LAKE PLACE", model.homes.refusal)
        assertEquals(3, model.homes.homes.size)
    }

    /**
     * **With a home open, saving under a new name renames it** — one house, moved, rather than
     * two houses with the same rooms in them.
     *
     * This is the RENAME row's whole path: the open home goes into the editor, the field changes
     * the name, and SAVE HOME writes it back over the one it came from.
     */
    @Test
    fun savingAnOpenHomeUnderANewNameRenamesIt() {
        val model = FlowModel(PanelState(screen = ScreenId.SaveName))
        model.openSavedHome("THE BUNGALOW")
        model.editOpenHome()
        model.editor.nameHome("the annexe")

        model.saveHome()

        assertEquals(ScreenId.HomeDetail, model.state.screen)
        val saved = assertNotNull(model.homes.open)
        assertEquals("THE ANNEXE", saved.name)
        assertEquals(9, saved.roomCount)
        assertEquals(9, saved.markerCount, "the cards did not come across with the name")
        assertEquals(3, model.homes.homes.size, "a rename left a second copy behind")
        assertNull(model.homes.homes.firstOrNull { it.name == "THE BUNGALOW" })
    }

    /** With none open, the same act adds. The list grows and nothing is replaced. */
    @Test
    fun savingWithNoHomeOpenAddsOne() {
        val model = FlowModel(PanelState(screen = ScreenId.SaveName))
        model.homes.closeHome()
        model.editor.nameHome("the annexe")

        model.saveHome()

        assertEquals(4, model.homes.homes.size)
        assertEquals("THE ANNEXE", model.homes.homes.first().name, "the newest is not at the top")
        assertEquals("THE ANNEXE", model.homes.openName)
    }

    /**
     * A tap on bare grid opens nothing.
     *
     * Most of a plan is not a room. If an empty cell opened the panel it would open it on
     * whatever was held last, and the host would rename a room they were not looking at.
     */
    @Test
    fun aTapWhereThereIsNoRoomOpensNothing() {
        val model = FlowModel(PanelState(screen = ScreenId.Editor))
        val bare = (0 until HomeEditorModel.COLS * HomeEditorModel.ROWS)
            .map { Cell(x = it % HomeEditorModel.COLS, y = it / HomeEditorModel.COLS) }
            .first { model.editor.roomAt(it) == null }

        model.openRoomAt(bare)
        assertEquals(ScreenId.Editor, model.state.screen, "an empty cell opened the room panel")
    }

    @Test
    fun pickingTheTypeARoomAlreadyHasChangesNothing() {
        val model = FlowModel(PanelState(screen = ScreenId.RoomEdit))
        model.editor.open("STAIRS")
        model.pickRoomType(RoomKind.Stairs)
        assertEquals(
            ScreenId.RoomEdit, model.state.screen,
            "a room that is already stairs has nothing to warn about",
        )
    }

    // ---- NEW ROUND (D-157) ---------------------------------------------------------------------

    /**
     * **NEW ROUND returns this phone to the lobby and takes its one line with it** (D-157, D-116).
     *
     * Both halves, from both endings. The walk is the visible half; the deletion is the half that
     * cannot be seen on any screen, which is exactly why it is asserted here rather than trusted
     * to whichever composable happens to be drawn.
     *
     * **THE INJECTION** for the desk: dropping `lobby.roundEnded()` out of `FlowModel.newRound`
     * leaves `linesIn` at four and this fails by name — the house is still holding six confessions
     * and the next round's LIGHTS OUT gate opens with nobody having typed anything.
     */
    @Test
    fun newRoundGoesBackToTheLobbyWithTheLinesGone() {
        for (ending in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            val model = FlowModel(PanelState(screen = ending), lobby = LobbyModel.sample())
            model.lobby.typeLine("I still have your spare key.")
            model.lobby.handOverLine()
            assertTrue(model.lobby.line.handedOver, "$ending: the fixture handed nothing over")

            model.newRound()

            assertEquals(ScreenId.Lobby, model.state.screen, "$ending: NEW ROUND went somewhere else")
            assertEquals("", model.lobby.line.text, "$ending: the line survived the round it was for")
            assertFalse(model.lobby.line.handedOver)
            assertEquals(
                0, model.lobby.standing.linesIn,
                "$ending: the house is still holding lines after the round ended",
            )
        }
    }

    /**
     * **What survives is the home, the seats and the settings** (D-157).
     *
     * *It would be absurd to walk the home again at midnight.* The failure this guards is the one
     * `LobbyModel.roundEnded` used to have: it kept the deletion promise by calling `link.leave()`
     * and taking the whole house down, which was correct while the app had no second round and
     * became an evening that ends after one the moment NEW ROUND existed.
     */
    @Test
    fun newRoundKeepsTheHouseTheSeatsAndTheSettings() {
        val model = FlowModel(PanelState(screen = ScreenId.WinResidents), lobby = LobbyModel.sample())
        val attached = model.lobby.attached
        val seated = model.lobby.standing.joined
        val setting = model.lobby.standing.insiders
        assertTrue(attached != null && seated > 0, "the fixture is not in a lobby")

        model.newRound()

        assertEquals(attached, model.lobby.attached, "NEW ROUND detached this phone from the house")
        assertEquals(seated, model.lobby.standing.joined, "NEW ROUND emptied the seats")
        assertEquals(setting, model.lobby.standing.insiders, "NEW ROUND discarded the host's setting")
    }

    /**
     * **The ending is not a row in [Flow.housePushes] and must not become one.**
     *
     * That table is keyed on the screen you are standing on; the ending can land on any of them.
     * `Tally` is the sharp case — it already has a row, and a second one keyed on the same screen
     * would replace the first *silently*, so the meeting would stop ending and nothing would say
     * so. The two [Flow.endings] are keyed on the winner instead, which is what decides the screen.
     */
    @Test
    fun theEndingIsKeyedOnTheWinnerAndNotOnTheScreen() {
        assertEquals(
            setOf(ScreenId.WinResidents, ScreenId.WinInsiders),
            Flow.endings.values.map { it.to }.toSet(),
            "the two endings do not lead to the two ending screens",
        )
        assertTrue(
            Flow.housePushes.values.none { it.to == ScreenId.WinInsiders || it.to == ScreenId.WinResidents },
            "an ending crept into the per-screen push table, where it can only be right for one screen",
        )
        // Every push names a kind somebody actually emits -- the same check the meeting's rows get,
        // extended to the two rows that were added outside that table.
        for (push in Flow.endings.values) {
            assertTrue(
                push.on in EmitSchema.knownKinds(),
                "${push.on} is not a kind the schema permits to anybody; the ending would never arrive",
            )
        }
    }

    /** Drag two corners over bare grid. Returns the provisional name the new room was given. */
    private fun paintAnEmptyRoom(model: FlowModel): String {
        val editor = model.editor
        val bare = (0 until HomeEditorModel.COLS * HomeEditorModel.ROWS)
            .map { Cell(x = it % HomeEditorModel.COLS, y = it / HomeEditorModel.COLS) }
            .first { editor.roomAt(it) == null }
        editor.dragFrom(bare)
        editor.dragTo(bare)
        return editor.dropDrag() ?: throw AssertionError("the drag painted nothing: ${editor.refusal}")
    }
}
