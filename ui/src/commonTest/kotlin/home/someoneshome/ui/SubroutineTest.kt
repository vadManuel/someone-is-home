package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(model.handshake.entered.isNotEmpty(), "the fixture proves nothing when empty")
        model.beganAgain()
        assertTrue(model.handshake.entered.isEmpty())
        assertTrue(model.replay.entered.isEmpty())
        assertNull(model.parity.choice)
        assertFalse(model.handshake.handedOver)
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
