package home.someoneshome.ui

import home.someoneshome.model.HapticStep
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // ---- Sniff's question ------------------------------------------------------------------------

    /**
     * **Equal groups are refused by name, and that is D-137 rather than defensive coding.**
     *
     * *The answer must exist and must be unique — a tie is a coin flip the house would then grade,
     * and D-109 grades entries on their merits or not at all.* There is no clamp, no nudge and no
     * arbitrary winner: a tie is a question this client cannot ask, and it says so.
     *
     * It is not hypothetical. The stand-in draw in `core` produces three numbers out of one range
     * with no idea what they mean (E-L4-3), so it can draw one of these today.
     */
    @Test
    fun sniffRefusesTwoGroupsOfTheSameSize() {
        for (size in 1..4) {
            val thrown = assertFailsWith<MalformedSubroutineParameters>(
                "SNIFF accepted two groups of $size, which is a tie the house would then grade",
            ) { SniffGroups.of(listOf(size, size, 8)) }
            assertTrue(
                thrown.detail.contains("equal groups never occur"),
                "the refusal does not name what is wrong with it: ${thrown.detail}",
            )
        }
        // And the other direction, so this is a guard rather than a type that refuses everything.
        val asked = SniffGroups.of(listOf(2, 5, 8))
        assertEquals(2, asked.first)
        assertEquals(5, asked.second)
    }

    /**
     * The three other ways a Sniff instance can fail to be a question, each refused by name.
     *
     * A list of the wrong length is a client reading a Subroutine it was not sent. A group with no
     * buzzes in it is one group, not two. A pause of nothing is the same fault wearing a different
     * field: what separates the groups is the silence, and without it the player feels one run.
     */
    @Test
    // Named `…ThatIsNotAQuestion` rather than `…ItCannotAsk`, which the vocabulary lint reads as
    // the forbidden word for Subroutine hiding inside `cannotAsk`. Containment, not prefix — and
    // the lint is right to be that blunt.
    fun sniffRefusesAnInstanceThatIsNotAQuestion() {
        val malformed = listOf(
            listOf(3, 5) to "two numbers cannot say how long the pause is",
            listOf(3, 5, 8, 2) to "a fourth number is a Subroutine this client is not reading",
            listOf(0, 5, 8) to "a group of nothing is not a group",
            listOf(3, 0, 8) to "and neither is the second one",
            listOf(3, 5, 0) to "two groups with no pause between them are one group",
        )
        for ((parameters, why) in malformed) {
            assertFailsWith<MalformedSubroutineParameters>("SNIFF accepted $parameters — $why") {
                SniffGroups.of(parameters)
            }
        }
    }

    /**
     * **The script is two groups and one pause, and every buzz in it is the same length.**
     *
     * The pulse count is the whole question, so a script that buzzed unevenly would be handing the
     * player a rhythm — which is Handshake, and the reason D-137 makes Sniff a magnitude judgment
     * is that there is nothing to hold. Checked as *one rest longer than every other*, because that
     * is what "two groups separated by a pause" comes to when it is read off a list of durations.
     */
    @Test
    fun theSniffScriptIsTwoGroupsSeparatedByOnePauseAndCarriesNoRhythm() {
        val groups = SniffGroups.of(listOf(3, 5, 8))
        val buzzes = groups.script.filterIsInstance<HapticStep.Buzz>()
        assertEquals(
            groups.first + groups.second, buzzes.size,
            "the script does not buzz as many times as the two groups say",
        )
        assertEquals(
            1, buzzes.map { it.millis }.toSet().size,
            "the buzzes are not all the same length, so the script carries a rhythm",
        )

        val rests = groups.script.filterIsInstance<HapticStep.Rest>()
        val between = rests.filter { it.millis == groups.gapMillis }
        assertEquals(1, between.size, "there is not exactly one pause between the two groups")
        assertTrue(
            rests.all { it.millis <= groups.gapMillis },
            "a gap inside a group is as long as the gap between them, so there are not two groups",
        )
        // The pulses before the pause are the first group and the ones after it are the second.
        val at = groups.script.indexOf(between.single())
        assertEquals(
            groups.first, groups.script.take(at).count { it is HapticStep.Buzz },
            "the pause is in the wrong place — the first group is the wrong size",
        )
        assertTrue(groups.script.last() is HapticStep.Buzz, "the script ends on silence")
    }

    // ---- Deallocate's columns and its entry -------------------------------------------------------

    /**
     * **A column can be taken below level, and the entry simply counts it.**
     *
     * D-138: *over-taps are the player's to make. Columns can go below level, the screen only
     * echoes the tap, and the house rejects a wrong final state on hand-over.* A column that
     * refused to go below level would be the phone forming an opinion about the answer, and D-125
     * is explicit — clamp only what players cannot perceive, and a column's height is the one thing
     * here a player can.
     *
     * Injecting the bug this exists for means handing [ColumnEntry] the distribution and refusing a
     * removal that would take a column past the shortest.
     */
    @Test
    fun aColumnTakesEveryTapIncludingTheOnesPastLevel() {
        val entry = ColumnEntry(columns = 3)
        repeat(9) { entry.remove(1) }
        assertEquals(listOf(0, 9, 0), entry.removed, "a removal was refused")
        assertEquals(9, entry.taken(1))
        assertEquals(0, entry.taken(0), "a column nobody touched came back short")
    }

    /**
     * The entry counts its own taps and nothing else, and a column it does not have is ignored.
     *
     * Silent rather than thrown: nothing on a drawn screen can produce an index that is not a
     * column, and a `when` that threw would turn a routing mistake into a dead phone in a dark
     * home (rule 6).
     */
    @Test
    fun aColumnEntryCountsOnlyItsOwnTapsAndIgnoresAColumnItDoesNotHave() {
        val entry = ColumnEntry(columns = 2)
        assertFalse(entry.touched, "it started out already touched")
        entry.remove(5)
        entry.remove(-1)
        assertEquals(listOf(0, 0), entry.removed, "a tap on a column that is not there landed")
        assertFalse(entry.touched)
        entry.remove(0)
        assertTrue(entry.touched)
        entry.handOver()
        entry.remove(0)
        assertEquals(listOf(1, 0), entry.removed, "a tap landed after the entry had gone")
        assertTrue(entry.locked)
    }

    /**
     * **SUBMIT with nothing removed is accepted**, which is [ScalarEntry]'s refusal to refuse.
     *
     * An inert SUBMIT would be the phone saying *the distribution you were dealt is not already
     * level*, and it has no way of knowing: nothing in the entry is a height and nothing in it is
     * the shortest column.
     */
    @Test
    fun columnsCanBeSentWithoutATap() {
        val entry = ColumnEntry(columns = 4)
        entry.handOver()
        assertEquals(listOf(0, 0, 0, 0), entry.handedOver, "an untouched distribution was refused")
    }

    /**
     * **A distribution the panel cannot draw is refused, and one it can is returned untouched.**
     *
     * Not a difficulty rule. Dots clipped off the top of a column change the height a player
     * reads, so a truncated distribution asks a *different question* from the one the house sent —
     * and the player answers the picture, correctly, and is graded wrong.
     *
     * **Equal columns are deliberately absent from this list.** A distribution that arrives level
     * asks for no removals, which is a trivial instance rather than a malformed one, and refusing
     * it would be this client deciding how much work a piece of work has to contain.
     */
    @Test
    fun theDistributionIsRefusedOnlyWhereThePanelCannotDrawIt() {
        val malformed = listOf(
            listOf(4) to "one column is already level and there is nothing to compare it with",
            emptyList<Int>() to "no columns at all",
            List(DotColumns.MOST_COLUMNS + 1) { 3 } to "more strips than the panel has room for",
            listOf(3, 0, 4) to "a column with no dots in it is a column that is not there",
            listOf(3, DotColumns.MOST_DOTS + 1) to "a column taller than the panel draws",
        )
        for ((parameters, why) in malformed) {
            assertFailsWith<MalformedSubroutineParameters>(
                "DEALLOCATE accepted $parameters — $why",
            ) { DotColumns.of(parameters) }
        }

        assertContentEquals(
            listOf(1, DotColumns.MOST_DOTS),
            DotColumns.of(listOf(1, DotColumns.MOST_DOTS)),
            "the edges of what the panel can draw were refused",
        )
        // Already level, and accepted: see the note above.
        assertContentEquals(listOf(2, 2, 2), DotColumns.of(listOf(2, 2, 2)))
    }

    /**
     * **The four fixtures are questions with an answer in them, and the answer is computed HERE.**
     *
     * The arithmetic below — the shortest column, and what each of the others owes over it; whether
     * a sweep position is inside the band; where a hidden dot is at the instant it is asked about —
     * is the answer key, which is why there is no function in `ui` that does any of it.
     * `DotColumns` returns the heights and computes nothing; [InterruptSweep] draws the band and
     * draws the sweep and never compares them; [DriftPath] renders the motion and holds no answer.
     * Every one of those helpers would be one call away from a screen that could tell a player how
     * they had done, on Subroutines whose entire content is working that out for yourself.
     *
     * This is a test, it never ships, and it is **the only place in the module where the two halves
     * of any of these instances are in the same expression.**
     */
    @Test
    fun theFixtureQuestionsHaveAnAnswerAndItIsNotOnThePhone() {
        assertTrue(
            SubroutineModel.SNIFF.first != SubroutineModel.SNIFF.second,
            "the SNIFF fixture is a tie, which D-137 says never occurs",
        )

        val columns = SubroutineModel.DEALLOCATE
        val level = columns.min()
        val owed = columns.sumOf { it - level }
        assertTrue(owed > 0, "the DEALLOCATE fixture arrives level, so there is nothing to look at")
        assertTrue(
            columns.count { it == level } == 1,
            "more than one column is already at level, so the fixture is easier than it reads",
        )
        assertTrue(
            columns.indexOf(level) !in listOf(0, columns.lastIndex),
            "the shortest column is on an end, so the Subroutine can be done without looking at " +
                "the whole screen",
        )

        // **The band is catchable and is not the whole bar**, which is the Interrupt fixture being
        // a question: a band the sweep is always inside would grade every tap the same, and one it
        // never reaches would grade none of them.
        val sweep = SubroutineModel.INTERRUPT
        val band = sweep.bandFrom..sweep.bandTo
        val visited = (0L..20_000L step 25).map { sweep.at(it) }
        assertTrue(visited.any { it in band }, "the fixture's sweep never enters its own band")
        assertTrue(visited.any { it !in band }, "the fixture's sweep is never outside its own band")

        // And the fixture frame is a catch that MISSED. A default render marking the middle of the
        // band would read as this phone having graded it — see `SubroutineModel.sample`.
        assertTrue(
            InterruptSweep.SPAN / 3 !in band,
            "the fixture's caught position sits inside the band, so every default render of " +
                "INTERRUPT looks like a phone saying well done",
        )
        // **The other direction, and it is the one Deallocate taught.** The parity sweeps drive
        // Interrupt to half way along the bar, and the reason that number is worth anything is
        // that it is INSIDE the band: a screen tempted to write *well done* somewhere can only be
        // caught on a frame where it would have. Asserted here rather than assumed there, because
        // a fixture edited for some other reason would take the coverage with it silently.
        assertTrue(
            InterruptSweep.SPAN / 2 in band,
            "the parity sweeps' caught position is outside the band, so every guard that renders " +
                "a caught INTERRUPT is walking past the one frame a verdict could be drawn on",
        )

        // **Drift's answer is where the dot is when the phone buzzes, and it is under cover.** The
        // one expression in this module that puts the pulse and the position together.
        val path = SubroutineModel.DRIFT
        val answer = path.at(path.askAtMillis.toLong())
        assertNotNull(answer, "the DRIFT fixture buzzes after the dot has left the lane")
        assertTrue(
            path.covered(answer),
            "the DRIFT fixture buzzes with the dot at $answer in plain sight of ${path.cover}",
        )
    }

    // ---- Interrupt's sweep -----------------------------------------------------------------------

    /**
     * **The sweep bounces: it stays on the bar, reaches both ends, and never jumps.**
     *
     * D-139 chose ping-pong over a wrap, and the difference between them is exactly one property —
     * **there is no discontinuity at the edge.** A wrapping sweep leaps the full width of the bar
     * once a cycle, and a player who cannot reliably time the band can time *that* instead, which
     * is a different Subroutine with a worse answer. So the step between two consecutive
     * milliseconds is asserted to be small everywhere, including across both turns.
     *
     * Swept over a whole cycle at a fine grain rather than at a few sampled instants, because the
     * fault would be at one edge and a sampler that stepped over it would report a clean bounce.
     */
    @Test
    fun theSweepStaysOnTheBarAndTurnsRatherThanJumping() {
        val sweep = InterruptSweep.of(listOf(58, 14, 30))
        var lowest = InterruptSweep.SPAN
        var highest = 0
        var last = sweep.at(0)
        // Two full cycles at the fixture's speed, in milliseconds.
        for (millis in 1L..(2L * InterruptSweep.CYCLE * 1000 / sweep.speed)) {
            val at = sweep.at(millis)
            assertTrue(at in 0..InterruptSweep.SPAN, "the sweep left the bar at ${millis}ms: $at")
            assertTrue(
                kotlin.math.abs(at - last) <= 1,
                "the sweep jumped from $last to $at at ${millis}ms — a bar that leaps at the edge " +
                    "is a wrap, and D-139 chose ping-pong so there is nothing to time but the band",
            )
            lowest = minOf(lowest, at)
            highest = maxOf(highest, at)
            last = at
        }
        assertEquals(0, lowest, "the sweep never reached the near end of the bar")
        assertEquals(InterruptSweep.SPAN, highest, "the sweep never reached the far end")
    }

    /**
     * **The same instance draws the same sweep, forever, and a different one draws a different
     * sweep.**
     *
     * The whole of D-139's architecture rests on this: *the house sends the parameters and the
     * client renders the motion deterministically from them.* If two readings of one instance could
     * differ, a replay would not be a replay and the house would be grading a tap against a picture
     * it cannot reconstruct — which is the second promise in `project-context`, broken by a drawing
     * helper.
     *
     * The second half is the one that stops this passing on a constant: **a re-scan re-draws band
     * position and phase (D-139), so a different phase has to produce a different sweep.**
     */
    @Test
    fun theSameSweepIsDrawnEveryTimeAndADifferentPhaseIsNot() {
        val instance = listOf(58, 14, 30)
        val sampled = (0L..20_000L step 37).map { InterruptSweep.of(instance).at(it) }
        val again = (0L..20_000L step 37).map { InterruptSweep.of(instance).at(it) }
        assertContentEquals(sampled, again, "one instance drew two different sweeps")

        val rescanned = (0L..20_000L step 37).map { InterruptSweep.of(listOf(58, 14, 31)).at(it) }
        assertTrue(
            sampled != rescanned,
            "a re-drawn phase drew the same sweep, so a retry is a second run at a picture the " +
                "player has already memorised",
        )
    }

    /**
     * **There is no timeout in the sweep, and an hour in it is still moving.**
     *
     * D-139: *the sweep runs forever until tapped or abandoned. Hesitation is taxed by exposure,
     * not by a clock.* The screen's half of this is `SubroutineMotionTest`; this is the arithmetic's
     * half, and it is the one that would silently acquire a ceiling — a `coerceAtMost`, an `Int`
     * that stopped being enough, a modulus that came out negative on the far side of an overflow.
     */
    @Test
    fun theSweepIsStillRunningAnHourIn() {
        val sweep = InterruptSweep.of(SubroutineModel.INTERRUPT_PARAMETERS)
        val hour = 60L * 60L * 1000L
        val moved = (0..40).map { sweep.at(hour + it * 250L) }.toSet()
        assertTrue(
            moved.size > 1,
            "the sweep has stopped an hour in, so something in it is counting down: $moved",
        )
        assertTrue(
            moved.all { it in 0..InterruptSweep.SPAN },
            "the sweep left the bar an hour in: $moved",
        )
    }

    /**
     * The three ways an Interrupt instance can fail to be a question, each refused by name.
     *
     * A band running off the end of the bar is [DotColumns]' clipped column one Subroutine along:
     * the player answers the picture, correctly, and is graded against a band they were never
     * shown. A sweep at no speed is not a moment to catch. And a phase outside the cycle is the one
     * that would be tempting to wrap quietly — the house grades a position this client draws, so a
     * parameter the client reinterprets is the two of them disagreeing about where the sweep began.
     *
     * It is not hypothetical: the stand-in draw in `core` produces three numbers out of one range
     * with no idea what they mean (E-L4-3), and most of that range is a band hard against the near
     * end of the bar.
     */
    @Test
    fun interruptRefusesAnInstanceThatIsNotAQuestion() {
        val malformed = listOf(
            listOf(58, 14) to "two numbers cannot say where in the bounce the sweep starts",
            listOf(58, 14, 30, 2) to "a fourth number is a Subroutine this client is not reading",
            listOf(InterruptSweep.BAND_HALF - 1, 14, 30) to "the band runs off the near end",
            listOf(InterruptSweep.SPAN, 14, 30) to "and off the far end",
            listOf(58, 0, 30) to "a sweep that does not move is not a moment to catch",
            listOf(58, 14, InterruptSweep.CYCLE) to "a phase past the end of the bounce",
            listOf(58, 14, -1) to "and one before the start of it",
        )
        for ((parameters, why) in malformed) {
            assertFailsWith<MalformedSubroutineParameters>(
                "INTERRUPT accepted $parameters — $why",
            ) { InterruptSweep.of(parameters) }
        }

        // And the edges of what is drawable, so this is a guard rather than a reader that refuses
        // everything: a band exactly touching each end of the bar is a band the bar can draw.
        assertEquals(InterruptSweep.BAND_HALF, InterruptSweep.of(listOf(8, 1, 0)).bandAt)
        assertEquals(
            InterruptSweep.SPAN - InterruptSweep.BAND_HALF,
            InterruptSweep.of(listOf(92, 1, InterruptSweep.CYCLE - 1)).bandAt,
        )
    }

    // ---- Drift's path ----------------------------------------------------------------------------

    /**
     * **THE ONE THAT MATTERS: the dot is out of sight at the instant the phone buzzes, or the
     * instance is refused.**
     *
     * D-140's whole content is that the answer *cannot be read off the screen* — the player carries
     * the dot in their head across a hidden stretch whose length they were never shown. A path
     * whose buzz landed while the dot was in the clear would still render, still accept a tap, and
     * still be graded; it would simply be a Subroutine that tests nothing, and nobody would ever
     * see the difference by looking at it.
     *
     * Swept across seeds, speeds and waits rather than checked on the fixture, because the property
     * has to hold for every instance the house can draw and the fixture is one of them.
     */
    @Test
    fun theDotIsOutOfSightWhenTheHouseAsksOrTheInstanceIsRefused() {
        var asked = 0
        var refused = 0
        for (seed in 0 until 40) {
            for (speed in 6..18 step 3) {
                for (wait in 1..12) {
                    val path = try {
                        DriftPath.of(listOf(seed, speed, wait))
                    } catch (refusal: MalformedSubroutineParameters) {
                        refused++
                        assertTrue(
                            refusal.detail.contains("back in sight"),
                            "an instance was refused for something other than the dot being " +
                                "visible: ${refusal.detail}",
                        )
                        continue
                    }
                    val at = path.at(path.askAtMillis.toLong())
                    assertTrue(
                        at != null && path.covered(at),
                        "seed $seed at speed $speed buzzes ${wait} tenths in, and the dot is at " +
                            "$at with the cover at ${path.cover} — the answer is on the screen",
                    )
                    asked++
                }
            }
        }
        assertTrue(asked > 0, "every instance was refused, so nothing was checked")
        assertTrue(
            refused > 0,
            "no wait was long enough to bring the dot back into sight, so the refusal that " +
                "matters has nothing to refuse and this sweep is not exercising it",
        )
    }

    /**
     * **The cover is three separate pieces on a lane the dot starts clear of.**
     *
     * Two properties, and both are the picture being the question. Pieces that touched would be one
     * wider piece, and the dot would then be hidden for a stretch the layout does not look like it
     * covers — so the player reads one question and answers another. And a lane whose cover began
     * at the start would hide the dot before anybody had seen it move, which is a question with no
     * observation in front of it.
     */
    @Test
    fun theCoverIsSeparatePiecesAndTheDotIsSeenBeforeItHides() {
        for (seed in 0 until 60) {
            val path = DriftPath.of(listOf(seed, 12, 1))
            assertEquals(DriftPath.COVER, path.cover.size, "seed $seed lost a piece of cover")
            assertTrue(
                path.cover.first().first >= DriftPath.RUN_IN,
                "seed $seed hides the dot ${path.cover.first().first} steps in, before anybody " +
                    "has watched it move",
            )
            assertTrue(
                path.cover.last().last < DriftPath.SPAN,
                "seed $seed runs its cover off the end of the lane: ${path.cover}",
            )
            for ((before, after) in path.cover.zipWithNext()) {
                assertTrue(
                    after.first - before.last > DriftPath.CLEAR,
                    "seed $seed leaves ${after.first - before.last} steps between two pieces of " +
                        "cover, which the eye reads as one wider piece: ${path.cover}",
                )
            }
        }
    }

    /**
     * **The dot drifts at one speed and then it is gone.**
     *
     * *Constant velocity* is the design's word and it is the whole of what a player can rely on:
     * every equal stretch of time is an equal stretch of lane, or the mental model the Subroutine
     * tests cannot be built. And it leaves rather than parking on the end — a dot sitting at the
     * far edge is one a player reads as *still there*, and D-125 clamps only what a player cannot
     * perceive.
     */
    @Test
    fun theDotDriftsEvenlyAndThenLeaves() {
        val path = DriftPath.of(SubroutineModel.DRIFT_PARAMETERS)
        val second = 1_000L
        val steps = (1..6).map { path.at(it * second)!! - path.at((it - 1) * second)!! }
        assertEquals(
            1, steps.toSet().size,
            "the dot covers a different amount of lane in different seconds: $steps",
        )
        assertNull(
            path.at((DriftPath.SPAN + 5).toLong() * 1_000L / path.speed),
            "the dot is still on the lane after the end of it",
        )
    }

    /**
     * **The pulse is one short buzz after the wait, and there is nothing else in the script.**
     *
     * D-140 says so in as many words: *the pulse is a short one — D-135 reserves the long haptic
     * for five events and this is not among them.* A script with two buzzes in it would be a rhythm
     * to read, and a long one would be the phone saying something the house reserves for five other
     * things.
     */
    @Test
    fun theDriftScriptIsOneShortBuzzAndNothingElse() {
        val path = DriftPath.of(SubroutineModel.DRIFT_PARAMETERS)
        val buzzes = path.script.filterIsInstance<HapticStep.Buzz>()
        assertEquals(1, buzzes.size, "the script does not say *now* exactly once: ${path.script}")
        assertEquals(
            SniffGroups.PULSE_MILLIS, buzzes.single().millis,
            "the buzz that says *now* is not the short one D-135 leaves available",
        )
        assertEquals(
            path.askAtMillis,
            path.script.filterIsInstance<HapticStep.Rest>().single().millis,
            "the script buzzes at a different moment from the one the screen is rendering",
        )
    }

    /**
     * The four ways a Drift instance can fail to be a question, each refused by name.
     *
     * The last is D-140's and is the one with teeth: **a wait longer than the crossing puts the dot
     * back in sight before the buzz**, and the player answers by looking rather than by carrying it
     * — which is the ruling inverted rather than merely relaxed.
     */
    @Test
    fun driftRefusesAnInstanceThatIsNotAQuestion() {
        val malformed = listOf(
            listOf(7, 12) to "two numbers cannot say how long the dot stays hidden",
            listOf(7, 12, 6, 2) to "a fourth number is a Subroutine this client is not reading",
            listOf(7, 0, 6) to "a dot that does not drift never reaches the cover",
            listOf(7, 12, 0) to "a buzz as it goes in asks where the player just watched it go",
            listOf(7, 12, 60) to "six seconds hidden behind a piece of cover it crosses in one",
        )
        for ((parameters, why) in malformed) {
            assertFailsWith<MalformedSubroutineParameters>(
                "DRIFT accepted $parameters — $why",
            ) { DriftPath.of(parameters) }
        }
        // And one it accepts, so this is a guard rather than a reader that refuses everything.
        assertEquals(12, DriftPath.of(listOf(7, 12, 6)).speed)
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

    /**
     * **The house's answer arrives, stays put, and is gone the moment the player walks anywhere.**
     *
     * Three properties of the one push a Subroutine screen receives (D-109), each of which would
     * be a different bug.
     *
     * A verdict that **moved the phone** would decide for the player that they are finished with
     * this marker — and after a rejection it would walk them away from the one place D-110 says
     * they have to stand to try again.
     *
     * A verdict that **survived a navigation** would sit under the next Subroutine's work, so the
     * first thing a player saw at their next marker would be the last one's answer.
     *
     * A verdict that **survived a re-scan** is the same bug at its most dangerous: BEGIN clears the
     * entries, so the screen would come back ready, with nothing entered, and REJECTED still on it.
     */
    @Test
    fun theHousesVerdictLandsWithoutMovingThePhoneAndDoesNotOutliveTheSubroutine() {
        for (subroutine in Subroutine.built) {
            val screen = subroutine.screen!!
            val model = FlowModel(PanelState(screen = screen))

            model.houseGraded(SubroutineVerdict.Rejected)
            assertEquals(
                screen, model.state.screen,
                "${subroutine.label}: the house's answer moved the phone",
            )
            assertEquals(SubroutineVerdict.Rejected, model.state.verdict)

            // The walk back to the marker: BEGIN on a fresh scan, which is D-110's only way to
            // re-arm anything at all.
            model.beginSubroutine(subroutine)
            assertEquals(
                null, model.state.verdict,
                "${subroutine.label}: a re-scan left the previous attempt's verdict standing",
            )
            assertEquals(screen, model.state.screen)

            // And any other walk. STOP NOW is the one every Subroutine screen has.
            model.houseGraded(SubroutineVerdict.Accepted)
            model.go(ScreenId.Work)
            assertEquals(
                null, model.state.verdict,
                "${subroutine.label}: the verdict followed the player off the screen",
            )
        }
    }

    /**
     * **Nothing on the device can produce a verdict, so the field starts and stays null on its own.**
     *
     * Every tap and every hand-over on every built Subroutine, driven through the actions layer,
     * and the pushed field never moves. It is the type-level claim — the entries have no room for
     * an answer — asserted through the thing that would actually break it: somebody wiring a
     * hand-over to set the verdict locally so the screen "feels responsive".
     */
    @Test
    fun noAmountOfInputEverProducesAVerdict() {
        for (subroutine in Subroutine.built) {
            val model = FlowModel(PanelState(screen = subroutine.screen!!))
            val actions = model.actions()
            repeat(8) { actions.tapSubroutine(subroutine, it) }
            actions.handOverSubroutine(subroutine)
            assertEquals(
                null, model.state.verdict,
                "${subroutine.label} answered its own entry",
            )
        }
    }
}
