package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-075, read off the screens: the living get a count, the out get the ballot.**
 *
 * *The vote does not publish attribution.* Only a player outside the system ever learns who cast
 * what, and the asymmetry is safe only because of when it arrives — by the time an out player can
 * see it the room already knows they are out, so there is never a window in which someone outside
 * knows something the living do not.
 *
 * This is the second-most dangerous disclosure in the game after alignment itself, and it has the
 * quality that makes a leak here permanent: **it would work.** The screen would render, the build
 * would be green, the game would be playable — and it would be a different and much worse game,
 * because *your own vote stays yours* is what makes a meeting an argument rather than an audit.
 *
 * The type-level half of the claim is in `MeetingTest`: [OutsideView] is a separate type from
 * [MeetingModel], so a living phone's meeting state is physically incapable of carrying
 * attribution. This is the half that survives somebody adding a field anyway — it looks at every
 * screen in the game, in both roles, for the one mark that means a pairing.
 */
@OptIn(ExperimentalTestApi::class)
class MeetingDisclosureTest {

    /**
     * **The live ballot as the house pushes it**, rather than the port's fixture.
     *
     * The sweep below renders every screen in the game *with this populated*, which is the version
     * of the guard that matters now that `PanelState.ballots` exists: before the field, a living
     * screen could only have drawn attribution by reaching into [OutsideView]; with it, a living
     * screen can draw attribution by reading its own state, which is a much shorter mistake to
     * make and looks entirely reasonable in a diff.
     *
     * The names are deliberately not the fixture's, so a screen reading the fixture and a screen
     * reading the push are distinguishable rather than both green.
     */
    private val pushed: List<Ballot> = listOf(
        Ballot("HOLLIS", "WREN"),
        Ballot("WREN", "HOLLIS"),
        Ballot("NADIA", null),
    )

    /** Every screen in the game, both roles, and the ones on which [words] can be read. */
    private fun screensShowing(words: String): Set<ScreenId> {
        val found = linkedSetOf<ScreenId>()
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(role = role, ballots = pushed).arrivingAt(id))
                        }
                    }
                    if (onAllNodes(hasText(words, substring = true)).fetchSemanticsNodes().isNotEmpty()) {
                        found += id
                    }
                }
            }
        }
        return found
    }

    /**
     * **The mark that means "voted for" is drawn on exactly one screen.**
     *
     * A ballot row is three separate pieces of text — a voter, a mark, a target — so no single
     * node ever reads `ELLIOT → DANI` and no substring search can find the pairing whole. The mark
     * between them is what makes it a pairing rather than two names, which is why
     * [OutsideView.CAST_FOR] is a named constant the screen draws rather than a glyph typed into
     * it: the guard is tied to the meaning, and a screen that starts attributing has to reach for
     * the same constant or invent a new way to say it in front of a reviewer.
     */
    @Test
    fun onlyAPlayerOutsideTheSystemIsEverShownWhoVotedForWhom() {
        assertEquals(
            setOf(ScreenId.GhostMeeting), screensShowing(OutsideView.CAST_FOR),
            "attribution reached a screen a living player can be on. The living see how many " +
                "have voted, never what (D-075)",
        )
    }

    /**
     * The converse, because a rule that only ever says *fewer screens* is satisfied by an app with
     * no screens on it: the out player really is shown the whole ballot, both halves of every row,
     * and no count above it (decision pack 9a, D-285).
     */
    /**
     * **The other direction, on the live push: the couch really is drawn the selections.**
     *
     * D-134 gives the out the live vote and D-117 makes them *the only readers who see selections
     * rather than a count*. A build that denied them would satisfy every assertion above — the mark
     * would reach no screen at all — so the widening and the narrowing are both failures and both
     * are named. This is the half that fails if `VoteSelectionShown` stops reaching the couch.
     */
    @Test
    fun theCouchIsShownTheVoteTheHouseIsPushing() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(ballots = pushed).arrivingAt(ScreenId.GhostMeeting))
                }
            }
            for (ballot in pushed) {
                assertTrue(
                    onAllNodes(hasText(ballot.by)).fetchSemanticsNodes().isNotEmpty(),
                    "${ballot.by} cast a ballot the house pushed and is not on the outside view",
                )
                if (ballot.forWhom != null) {
                    assertTrue(
                        onAllNodes(hasText(ballot.forWhom)).fetchSemanticsNodes().isNotEmpty(),
                        "${ballot.by}'s live selection has no target on screen — the couch's whole " +
                            "privilege is seeing it happen (D-117, D-134)",
                    )
                }
            }
            // **And no count over the pushed rows** (the owner's 2026-11-18 ruling, decision pack 9a;
            // D-285). It read `2 OF 3` here, off the rows heard so far, where the living read their
            // own locked count: the couch sees who is choosing what, and that is all it is drawn.
            onAllNodes(hasText("VOTES CAST", substring = true)).assertCountEquals(0)
            onAllNodes(hasText("2 OF 3", substring = true)).assertCountEquals(0)
            // And the port's five are gone, which is what makes this a wiring test.
            assertEquals(
                0, onAllNodes(hasText("ELLIOT")).fetchSemanticsNodes().size,
                "the outside view is still drawing the port's fixture over the house's push",
            )
        }

    @Test
    fun aPlayerOutsideTheSystemIsShownAllOfIt() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState().arrivingAt(ScreenId.GhostMeeting))
                }
            }
            assertTrue(OutsideView.ballots.any { it.forWhom != null }, "the fixture has no cast ballots; this proves nothing")
            for (ballot in OutsideView.ballots) {
                // Every name is looked for as "at least one", never "exactly one": DANI is on this
                // screen three times, as one voter and as two people's votes.
                assertTrue(
                    onAllNodes(hasText(ballot.by)).fetchSemanticsNodes().isNotEmpty(),
                    "${ballot.by} cast a ballot and is not on the outside view at all",
                )
                if (ballot.forWhom != null) {
                    assertTrue(
                        onAllNodes(hasText(ballot.forWhom)).fetchSemanticsNodes().isNotEmpty(),
                        "${ballot.by}'s vote has no target on screen — the outside view's whole " +
                            "privilege is seeing all of it",
                    )
                }
            }
            // **And no count** (decision pack 9a, D-285): the couch sees who is choosing what.
            onAllNodes(hasText("VOTES CAST", substring = true)).assertCountEquals(0)
            onAllNodes(hasText(" OF ${OutsideView.ballots.size}", substring = true)).assertCountEquals(0)
        }

    /**
     * **The living's own vote screen names a target and never a voter.**
     *
     * The rows there are people you may vote *for*. Attribution arriving on that screen would be
     * the most natural-looking version of this bug there is, and the count at the foot of it —
     * *4 OF 6 VOTED* — is what the design puts there instead.
     */
    @Test
    fun theVoteScreenCountsAndDoesNotAttribute() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState().arrivingAt(ScreenId.Vote))
                }
            }
            assertEquals(
                0,
                onAllNodes(hasText(OutsideView.CAST_FOR, substring = true)).fetchSemanticsNodes().size,
                "the vote screen drew a pairing",
            )
            onNodeWithText("4 OF 6 VOTED").assertExists()
        }

    /**
     * **And the result screen shows a count, not a ballot and not a tally** (the owner's 2026-08-24
     * ruling, which closed E-R2-9).
     *
     * The design's own note: *attribution shown, alignment never* was the GDD's line and D-075
     * reversed the first half of it. The screen then spent every round drawing the second half —
     * three rows with names and counts against them, from a fixture, because nothing on a living
     * phone's wire could fill them — and the owner ruled that out too: **a per-candidate tally is
     * the shape of the room's thinking published to the people still in it.** What is left is how
     * many of the room voted and who the room restrained, both of which everybody watched happen.
     *
     * The rows are gone from the type as well as from the screen, so this is the half of the guard
     * that survives somebody adding a field back: it renders the result **with a house-pushed
     * outcome and a roster to draw from**, and asserts that the only name on it is the one the room
     * restrained.
     */
    @Test
    fun theResultScreenShowsACountAndNeitherABallotNorATally() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val roster = listOf("HOLLIS", "WREN", "NADIA", "MARCUS")
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(
                            roster = roster,
                            tally = MeetingTally(restrained = "WREN", voted = 4, living = 6),
                        ).arrivingAt(ScreenId.Tally),
                    )
                }
            }
            assertEquals(
                0,
                onAllNodes(hasText(OutsideView.CAST_FOR, substring = true)).fetchSemanticsNodes().size,
                "the result screen drew who voted for whom",
            )
            onNodeWithText("4 OF 6 VOTED").assertExists()
            onNodeWithText("WREN WAS RESTRAINED").assertExists()
            // **Nobody else is named, which is what count-only means on a rendered screen.** A
            // per-candidate row cannot be drawn without naming a candidate, so a build that brought
            // the rows back — off the wire or off a fixture — fails here whatever it counts.
            for (name in roster - "WREN") {
                assertEquals(
                    0, onAllNodes(hasText(name, substring = true)).fetchSemanticsNodes().size,
                    "$name is on the living's result screen, and the only name that may be is the " +
                        "one the room restrained (the owner's 2026-08-24 ruling)",
                )
            }
            // And the port's own three, which is what the screen used to draw on every phone.
            for (drawn in listOf("MARCUS 3", "SKIP", "PRIYA")) {
                assertEquals(
                    0, onAllNodes(hasText(drawn, substring = true)).fetchSemanticsNodes().size,
                    "the result screen is still drawing the port's per-candidate fixture",
                )
            }
        }
}
