package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The Subroutine entries, and the one property that matters more than all the others: they
 * cannot tell you whether you were right.**
 *
 * A Subroutine's pattern arrives as an Effect, the screen echoes taps, and the *server* verifies
 * what comes back (D-042). That is not a round trip being saved — it is a judgement that must not
 * happen on the device, because an Insider's Subroutines are fakes and the fake is only
 * indistinguishable while nothing on the phone adjudicates. The moment a screen decides a sequence
 * was correct, the two roles need two behaviours, and the difference is on a lit screen in a dark
 * room.
 *
 * These read the entries rather than the screens; `SubroutineParityTest` does the same claim from
 * the pixels.
 */
class SubroutineTest {

    // ---- The roster ----------------------------------------------------------------------------

    /**
     * **The design's ten, spread 3 bright / 4 medium / 3 dark.**
     *
     * The spread is not decoration: Sniff is *the* short dark, the cell where a Resident can take a
     * quick one without becoming a beacon, and the whole light-signature axis is the claim that a
     * six-second bright one and a thirty-second dark one are different decisions rather than two
     * points on "how long". A roster that quietly drifted to nine bright would still render, still
     * play, and would be a different game.
     */
    @Test
    fun theRosterIsTheDesignsTenWithTheDesignsSpread() {
        assertEquals(10, Subroutine.entries.size, "the design's roster is ten Subroutines")
        assertEquals(
            mapOf(
                LightSignature.Bright to 3,
                LightSignature.Medium to 4,
                LightSignature.Dark to 3,
            ),
            Subroutine.entries.groupingBy { it.light }.eachCount(),
            "the roster spreads 3 bright / 4 medium / 3 dark (gdd.md:561)",
        )
    }

    /**
     * A built Subroutine has a screen of its own; an unbuilt one has none.
     *
     * Two Subroutines sharing a screen would be one of them silently opening the other's work, and
     * it would look like a routing bug rather than a missing feature — which is why the null is
     * the honest state and not a slot to fill with the nearest neighbour.
     */
    @Test
    fun everyBuiltSubroutineHasAScreenOfItsOwn() {
        val screens = Subroutine.built.mapNotNull { it.screen }
        assertEquals(Subroutine.built.size, screens.size, "a built Subroutine with no screen")
        assertEquals(screens.size, screens.toSet().size, "two Subroutines share one screen")
        for (subroutine in Subroutine.entries - Subroutine.built.toSet()) {
            assertNull(subroutine.screen, "${subroutine.label} is unbuilt and has a screen")
        }
        for (subroutine in Subroutine.built) {
            assertEquals(
                subroutine, Subroutine.on(subroutine.screen!!),
                "a screen does not lead back to the Subroutine that claims it",
            )
        }
    }

    /**
     * **A built Subroutine has an entry behind it, and a tap on it reaches that entry.**
     *
     * The way a Subroutine ships half-built is not that somebody forgets the screen — the screen is
     * the part you can see. It is that the roster gets a row, the screen gets a `when` branch, and
     * the *dispatch* in [SubroutineModel.tap] keeps its `else -> Unit`, so every finger that lands
     * on the new screen goes nowhere and the screen simply never echoes. In a game whose Subroutine
     * screens are mostly echo, that looks like a screen that has not loaded yet.
     *
     * One is a number the screens agree on: it is a finger on the first element, the first cell,
     * one finger on the glass, one press of +, a tap on the first node. Every one of them is
     * something.
     */
    @Test
    fun everyBuiltSubroutineHasAnEntryAndATapReachesIt() {
        for (subroutine in Subroutine.built) {
            val model = SubroutineModel()
            val entry = model.entry(subroutine)
            assertNotNull(entry, "${subroutine.label} has a screen and no entry behind it")
            assertFalse(entry.touched, "${subroutine.label} started out already touched")
            model.tap(subroutine, 1)
            assertTrue(
                model.entry(subroutine)!!.touched,
                "a tap on ${subroutine.label} reached nothing — it has a screen, a roster row and " +
                    "no wiring in SubroutineModel.tap",
            )
        }
        for (subroutine in Subroutine.entries - Subroutine.built.toSet()) {
            assertNull(
                SubroutineModel().entry(subroutine),
                "${subroutine.label} is unbuilt and has an entry that would take input",
            )
        }
    }

    /** A screen that is not a Subroutine's belongs to no Subroutine. */
    @Test
    fun everyOtherScreenBelongsToNoSubroutine() {
        val theirs = Subroutine.built.mapNotNull { it.screen }.toSet()
        for (id in ScreenId.entries - theirs) {
            assertNull(Subroutine.on(id), "$id was claimed by a Subroutine and is not one")
        }
    }

    // ---- The entries ---------------------------------------------------------------------------

    /**
     * **The whole point, stated as something that can fail.**
     *
     * Two players enter four elements each. One of them is right and one of them is wrong — and
     * there is no way to say which from here, which is exactly the claim: the entry progresses
     * identically, completes on the same tap, hands over at the same moment. Nothing about the
     * observable state depends on the content.
     *
     * Injecting the bug this exists for means giving [SequenceEntry] the pattern and having
     * `enter` refuse a wrong element, or expose a `correct` flag. Either one splits these two.
     */
    @Test
    fun anEntryProgressesIdenticallyWhateverWasEntered() {
        val length = 4
        val right = SequenceEntry(length)
        val wrong = SequenceEntry(length)
        for (step in 0 until length) {
            right.enter(step)
            // The same length of nonsense: every element the same, and out of order besides.
            wrong.enter(0)
            assertEquals(
                right.entered.size, wrong.entered.size,
                "the two entries disagree about how far in they are at step $step",
            )
            assertEquals(right.complete, wrong.complete, "they disagree about being complete")
            assertEquals(right.handedOver, wrong.handedOver, "they disagree about having gone")
            assertEquals(right.remaining, wrong.remaining, "they disagree about what is owed")
        }
        assertTrue(right.handedOver && wrong.handedOver, "neither sequence went to the house")
    }

    /**
     * A sequence hands itself over on its last element, because there is nothing left to change.
     *
     * And it accepts nothing afterwards: a control that appears to take input it discards is worse
     * than one that does not move, and on Handshake — near-black, entered by feel — the player has
     * no way to see that it did.
     */
    @Test
    fun aSequenceGoesOnItsLastElementAndTakesNothingAfter() {
        val entry = SequenceEntry(3)
        entry.enter(1)
        entry.enter(1)
        assertFalse(entry.handedOver, "it went before the sequence was finished")
        assertEquals(1, entry.remaining)
        entry.enter(2)
        assertTrue(entry.handedOver)
        entry.enter(0)
        assertContentEquals(listOf(1, 1, 2), entry.entered, "a tap landed after it had gone")
    }

    /** The echo, and its only claim: this is an element you touched. */
    @Test
    fun anEntryEchoesWhatWasTouchedAndNothingElse() {
        val entry = SequenceEntry(4)
        entry.enter(2)
        entry.enter(2)
        assertTrue(entry.holds(2))
        assertFalse(entry.holds(0), "a dot nobody touched came back lit")
        assertFalse(entry.holds(3))
    }

    /**
     * **One answer, changeable until it is handed over** — the vote's shape, for the vote's reason.
     *
     * A tap that went straight to the house would make a mis-touch final, in a game whose input
     * vocabulary is explicitly no twitch timing and no precise dragging, played standing up in an
     * unlit room by somebody watching a doorway.
     */
    @Test
    fun aChoiceMovesUntilItIsHandedOverAndIsFrozenAfter() {
        val entry = ChoiceEntry()
        entry.choose(4)
        assertTrue(entry.holds(4))
        assertFalse(entry.locked, "it claimed to be submitted before it was")
        entry.choose(9)
        assertTrue(entry.holds(9), "the mark did not move")
        assertFalse(entry.holds(4))
        entry.handOver()
        assertTrue(entry.locked)
        entry.choose(1)
        assertTrue(entry.holds(9), "the mark moved after it had gone to the house")
    }

    /**
     * SUBMIT with nothing chosen is refused, rather than quietly handing over an empty answer.
     *
     * The same refusal LOCK IN makes at a meeting: a control that sends nothing and looks like it
     * sent something is a phone lying about what the house holds.
     */
    @Test
    fun submitIsRefusedWithNothingChosen() {
        val entry = ChoiceEntry()
        entry.handOver()
        assertNull(entry.handedOver, "an empty answer went to the house")
        assertFalse(entry.locked)
    }

    /** Scanning the marker again is a fresh start, not a half-returned sequence ten minutes old. */
    @Test
    fun openingASubroutineAgainClearsEveryEntry() {
        val model = SubroutineModel.sample()
        for (subroutine in Subroutine.built) {
            assertTrue(
                model.entry(subroutine)!!.touched,
                "the fixture leaves ${subroutine.label} untouched, so clearing it proves nothing",
            )
        }
        model.beganAgain()
        for (subroutine in Subroutine.built) {
            val entry = model.entry(subroutine)!!
            assertFalse(entry.touched, "${subroutine.label} kept an entry across a fresh scan")
            assertFalse(entry.gone, "${subroutine.label} began already handed over")
        }
    }

    // ---- The hold, the scalar and the route ----------------------------------------------------

    /**
     * **Short's hold ends on the clock, and it goes with whatever hand was on the glass.**
     *
     * The property that keeps one screen serving both roles, stated for the one Subroutine where
     * it would be easiest to get wrong: a hold that completed *when the right number of fingers
     * arrived* would be the device grading the answer, and it would grade it visibly, because a
     * wrong hand would sit there while a right one went.
     *
     * Injecting the bug this exists for means giving [HoldEntry] the asked-for count and refusing
     * [HoldEntry.handOver] unless [HoldEntry.fingers] matches it.
     */
    @Test
    fun aHoldGoesWithWhateverHandWasOnTheGlass() {
        for (fingers in 1..5) {
            val entry = HoldEntry()
            entry.press(fingers)
            entry.handOver()
            assertEquals(
                fingers, entry.handedOver,
                "a hold of $fingers finger(s) did not go, or went as something else",
            )
        }
    }

    /**
     * An empty glass has made no hold, and lifting off after it has gone does not withdraw it.
     *
     * The second half is the echo: what is drawn after the hand-over is what this phone *sent*,
     * and a count draining back to nothing while the house has not answered would read as the
     * entry being taken back.
     */
    @Test
    fun anEmptyGlassSendsNothingAndAHandLiftedAfterwardsTakesNothingBack() {
        val entry = HoldEntry()
        entry.handOver()
        assertNull(entry.handedOver, "a hold nobody was making went to the house")

        entry.press(3)
        entry.handOver()
        entry.press(0)
        assertEquals(3, entry.handedOver)
        assertEquals(3, entry.fingers, "the echo drained after the entry had gone")
    }

    /**
     * **Jam knows how far it has walked and not how far it has to go.**
     *
     * [ScalarEntry] holds the net steps pressed from wherever the house opened the Subroutine.
     * Nothing in it is the target, so nothing in it can be subtracted from anything to find the
     * gap — which is [ParityGrid]'s discipline applied to a number instead of to a grid.
     *
     * SUBMIT is accepted with nothing pressed on purpose: refusing it would be the phone asserting
     * that the opening position is not the answer.
     */
    @Test
    fun aScalarWalksBothWaysAndCanBeSentWithoutMoving() {
        val untouched = ScalarEntry(reach = 4)
        untouched.handOver()
        assertEquals(0, untouched.handedOver, "an unmoved shape was refused")

        val entry = ScalarEntry(reach = 4)
        entry.step(1)
        entry.step(1)
        entry.step(-1)
        assertEquals(1, entry.offset)
        assertEquals(3, entry.presses, "a press that moved nothing still happened")
        entry.handOver()
        entry.step(1)
        assertEquals(1, entry.offset, "it moved after it had gone to the house")
    }

    /** The reach is a stop, and it is symmetric: a shape cannot be driven off either edge. */
    @Test
    fun aScalarStopsAtItsReachInBothDirections() {
        val entry = ScalarEntry(reach = 3)
        repeat(10) { entry.step(1) }
        assertEquals(3, entry.offset)
        repeat(20) { entry.step(-1) }
        assertEquals(-3, entry.offset)
    }

    /**
     * **A route accepts a move the graph does not have.**
     *
     * Rule 1 as a type: *never early-return on invalid in a client-visible path — the absent effect
     * is the leak.* A [PathEntry] that refused a tap on an unconnected node would be a screen
     * telling a player their move was illegal, which is the house's answer arriving from the phone.
     *
     * Injecting the bug this exists for means handing [PathEntry] the wiring and having
     * [PathEntry.walk] check [SignalGraph.Wiring.joinedTo] before it accepts.
     */
    @Test
    fun aRouteTakesEveryTapWhetherOrNotTheNodesAreJoined() {
        val wiring = SignalGraph.of(SubroutineModel.TRACE_SEED)
        val far = wiring.nodes.indices.first { it !in wiring.joinedTo(wiring.source) && it != wiring.source }
        val entry = PathEntry()
        entry.walk(wiring.source)
        entry.walk(far)
        assertContentEquals(
            listOf(wiring.source, far), entry.walked,
            "a tap on a node the graph does not join was refused, which is the house's answer " +
                "being given by the phone",
        )
    }

    /** Tapping a node already walked steps back to it; tapping the one you are on changes nothing. */
    @Test
    fun aRouteRetracesToANodeItAlreadyHolds() {
        val entry = PathEntry()
        listOf(0, 3, 5, 8).forEach(entry::walk)
        entry.walk(3)
        assertContentEquals(listOf(0, 3), entry.walked, "the route did not step back")
        entry.walk(3)
        assertContentEquals(listOf(0, 3), entry.walked, "standing still moved the route")
        entry.handOver()
        entry.walk(9)
        assertContentEquals(listOf(0, 3), entry.walked, "the route changed after it had gone")
    }

    /** SUBMIT with an empty route is refused, the same refusal LOCK IN makes on an empty ballot. */
    @Test
    fun anEmptyRouteIsRefused() {
        val entry = PathEntry()
        entry.handOver()
        assertNull(entry.handedOver, "an empty route went to the house")
    }

    // ---- The signal graph ----------------------------------------------------------------------

    /**
     * **Every graph is walkable, and the generator does not say how.**
     *
     * [SignalGraph.of] returns nodes and edges and nothing else — the walk it used to guarantee
     * connectivity is a local, discarded on return, exactly as [ParityGrid] discards the cell it
     * flipped. So the only way to check its work is the way a player does it: breadth-first across
     * the wiring that came back.
     *
     * Swept over many seeds because the fault would be a seed whose sink is stranded, and one
     * fixture seed would never show it.
     */
    @Test
    fun everyGraphHasARouteFromSourceToSink() {
        for (seed in 0 until 120) {
            val wiring = SignalGraph.of(seed)
            assertTrue(wiring.source >= 0 && wiring.sink >= 0, "seed $seed lost an end of the route")
            assertTrue(
                hops(wiring) != null,
                "seed $seed draws a graph whose sink cannot be reached from its source",
            )
        }
    }

    /**
     * **No node floats.** A node with no edge is a dot drawn in the middle of nothing, which reads
     * as a rendering fault rather than as a dead end — and a dead end is the whole point of a decoy.
     */
    @Test
    fun noNodeIsDrawnWithoutAnEdge() {
        for (seed in 0 until 120) {
            val wiring = SignalGraph.of(seed)
            for (at in wiring.nodes.indices) {
                assertTrue(
                    wiring.joinedTo(at).isNotEmpty(),
                    "seed $seed strands node $at with no edge on it",
                )
            }
        }
    }

    /**
     * The decoys are really there, and they really are extra.
     *
     * The count is a placeholder rather than a tuning value ([SignalGraph.DECOYS]), but *whether
     * decoys exist at all* is not: a graph that was only its own route is a corridor, and the
     * Subroutine's whole content is choosing among the ways across.
     */
    @Test
    fun everyGraphCarriesMoreNodesThanItsShortestRoute() {
        for (seed in 0 until 120) {
            val wiring = SignalGraph.of(seed)
            val shortest = hops(wiring)
            assertNotNull(shortest, "seed $seed has no route at all, so there is none to measure")
            assertTrue(
                wiring.nodes.size > shortest + 1,
                "seed $seed draws ${wiring.nodes.size} node(s) for a route of ${shortest + 1} — " +
                    "there is nothing to choose between",
            )
        }
    }

    /** How many edges the shortest route crosses, or null if the sink cannot be reached. */
    private fun hops(wiring: SignalGraph.Wiring): Int? {
        val seen = mutableSetOf(wiring.source)
        var frontier = listOf(wiring.source)
        var distance = 0
        while (frontier.isNotEmpty()) {
            if (wiring.sink in frontier) return distance
            frontier = frontier.flatMap { wiring.joinedTo(it) }.filter { seen.add(it) }
            distance++
        }
        return null
    }

    // ---- The parity grid -----------------------------------------------------------------------

    /**
     * **Exactly one cell breaks the pattern — recomputed from the cells, not asked of the
     * generator.**
     *
     * [ParityGrid.of] discards the index it flipped before it returns, so the only way to check
     * its work is to look at the grid the way a player does: compare every cell with the
     * checkerboard it should have been. That is the point of the object — a screen holding this
     * list *cannot* mark the answer, however carelessly it is later edited, because the list does
     * not contain it. The same discipline as `Observation` against `ObservationView`: the
     * client-facing type is not the authority's with a field hidden, it never had the field.
     *
     * Swept over many seeds because the fault would be a seed that produces two violations or
     * none, and one fixture seed would never show it.
     */
    @Test
    fun theParityGridHasExactlyOneCellBreakingThePattern() {
        for (seed in 0 until ParityGrid.SIZE * 3) {
            val cells = ParityGrid.of(seed)
            assertEquals(ParityGrid.SIZE, cells.size, "seed $seed produced the wrong size of grid")
            val breaking = cells.indices.filter { cells[it] != ParityGrid.clean(it) }
            assertEquals(
                1, breaking.size,
                "seed $seed breaks the pattern in ${breaking.size} places, not one: $breaking",
            )
        }
    }

    /**
     * The corners are as valid a hiding place as the middle, and the sweep reaches all of them.
     *
     * A generator that avoided the edges would teach players where never to look, which is a
     * difficulty setting nobody chose — and it is the kind of thing that gets added as a
     * "readability" fix by somebody who has never played in the dark.
     */
    @Test
    fun everyCellCanBeTheOneThatBreaksIt() {
        val reached = (0 until ParityGrid.SIZE).mapTo(mutableSetOf()) { seed ->
            ParityGrid.of(seed).indices.first { ParityGrid.of(seed)[it] != ParityGrid.clean(it) }
        }
        assertEquals(ParityGrid.SIZE, reached.size, "some cells can never be the odd one")
    }
}
