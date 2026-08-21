package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The clocks: one value behind every countdown, and a device that never runs one.**
 *
 * A countdown is the most tempting thing in this interface to implement locally — it is one
 * coroutine, it looks right immediately, and it is wrong in a way nobody can see from inside the
 * game. Six phones each counting down from their own arrival moment is six meetings ending at six
 * different times, each phone certain and none of them agreeing, in a house where the whole
 * mechanism is that everybody is looking at the same thing.
 *
 * These hold the shape that makes that impossible: the window is the auto-advance that ends it,
 * the position is a value that arrives, and the bar is the same value as the number. The proof
 * that nothing *ticks* is a rendering test — `MeetingInputTest` holds a screen still for a minute
 * of test clock and reads the figure off it — because a coroutine is not visible from here.
 */
class CountdownTest {

    /**
     * **Every clock's window is the auto-advance that ends it.**
     *
     * A countdown is a promise that something happens at zero. Written as two numbers — one on
     * the screen, one in the table that moves the phone — they drift, and the drift is a screen
     * that says nine seconds and moves after fifteen. Nobody debugs that from a dark hallway.
     */
    @Test
    fun everyClockCountsDownToTheThingThatEndsIt() {
        assertTrue(Countdowns.screens.isNotEmpty(), "no screen carries a clock; the table emptied")
        for (screen in Countdowns.screens) {
            val rule = Flow.autoAdvance[screen]
                ?: throw AssertionError("$screen draws a countdown and nothing happens when it ends")
            val clock = assertNotNull(Countdowns.on(screen))
            assertEquals(
                rule.afterMillis / 1000, clock.ofSeconds,
                "$screen counts down ${clock.ofSeconds}s and moves after ${rule.afterMillis}ms",
            )
            assertTrue(
                clock.secondsLeft in 0..clock.ofSeconds,
                "$screen is drawn at ${clock.secondsLeft}s of a ${clock.ofSeconds}s window",
            )
        }
    }

    /**
     * The five screens that carry one, named — so a sixth cannot appear without somebody saying
     * so, and so one cannot quietly stop having a clock.
     */
    @Test
    fun theScreensWithClocksAreExactlyTheOnesNamed() {
        assertEquals(
            setOf(
                ScreenId.Scan,        // the safety window: ten seconds, then the light dies
                ScreenId.Discussion,  // ninety seconds of talk
                ScreenId.Vote,        // the vote window
                ScreenId.Tally,       // lights out
                ScreenId.GhostMeeting, // the same vote window, watched from outside
            ),
            Countdowns.screens,
        )
    }

    /**
     * **The bars the design drew, reproduced exactly by deriving them.**
     *
     * This is the test that earns the change. The scan's twelve lit segments and its `6S LEFT`
     * were two constants; the ghost meeting's twelve lit segments and its `0:24` were two more.
     * Deriving both from one value has to land on the same picture the design drew, or the
     * derivation is a redesign wearing a refactor's clothes.
     */
    @Test
    fun theDerivedBarsAreTheOnesTheDesignDrew() {
        val scan = assertNotNull(Countdowns.on(ScreenId.Scan))
        assertEquals(12, scan.litOf(PanelVals.SCAN_SEGMENTS), "the scan bar moved off the design")
        assertEquals("6", scan.text)

        val ghost = assertNotNull(Countdowns.on(ScreenId.GhostMeeting))
        assertEquals(12, ghost.litOf(PanelVals.VOTE_SEGMENTS), "the ghost meeting bar moved")
        assertEquals("0:24", ghost.text)

        // The result screen's bar is two weights rather than segments: 9 of 15 left, drawn as
        // 9 against 6, which is what the design has.
        val tally = assertNotNull(Countdowns.on(ScreenId.Tally))
        assertEquals(0.6f, tally.remaining)
        assertEquals("9", tally.text)
    }

    /**
     * `m:ss` past a minute, bare seconds below one — read off the length of the WINDOW rather
     * than off the screen, so no screen can pick the wrong form.
     *
     * Both forms are the design's own: `1:04 REMAINING` on the ninety-second discussion,
     * `LIGHTS OUT IN 9` on the fifteen-second result.
     */
    @Test
    fun theTwoFormsFollowTheWindowRatherThanTheScreen() {
        assertEquals("1:04", Countdown(64, 90).text)
        assertEquals("0:38", Countdown(38, 60).text)
        assertEquals("0:09", Countdown(9, 60).text, "a minute-long window keeps its m:ss under ten")
        assertEquals("9", Countdown(9, 15).text)
        assertEquals("0", Countdown(0, 15).text)
        assertEquals("1:00", Countdown(60, 60).text)
    }

    /** **The house's number wins.** The drawn moment is a stand-in and stands down. */
    @Test
    fun whatTheHouseSaysReplacesTheFixture() {
        val said = assertNotNull(Countdowns.on(ScreenId.Vote, said = 7))
        assertEquals(7, said.secondsLeft)
        assertEquals("0:07", said.text)
        assertEquals(60, said.ofSeconds, "the house's number changed the window as well as the hand")

        // And through the panel, which is the path a screen actually reads.
        val panel = PanelVals(PanelState(screen = ScreenId.Vote, secondsLeft = 7))
        assertEquals("0:07", panel.countdown.text)
    }

    /**
     * **A screen with no clock has none, even when the house sends a number for it.**
     *
     * The fail-closed direction. A countdown that does not appear is noticed in thirty seconds;
     * a countdown appearing on a screen nobody decided should have one is noticed never, and it
     * would be a promise about what happens next that nothing behind it keeps.
     */
    @Test
    fun aScreenWithNoClockGetsNoneWhateverTheHouseSends() {
        assertNull(Countdowns.on(ScreenId.Home, said = 30))
        assertNull(Countdowns.on(ScreenId.Assemble, said = 5), "Assemble has a fall-through, not a clock")
        assertEquals(
            Countdown.NONE,
            PanelVals(PanelState(screen = ScreenId.Home, secondsLeft = 30)).countdown,
        )
        assertEquals("0", Countdown.NONE.text)
        assertEquals(0f, Countdown.NONE.remaining, "a spent clock cannot claim time left")
    }

    /**
     * A number outside the window is clamped rather than drawn.
     *
     * More time left than the window holds is the phone promising a delay the house will not
     * honour; a negative one is a bar drawn backwards. Neither is worth a crash and neither is
     * worth believing.
     */
    @Test
    fun aNumberOutsideTheWindowIsClamped() {
        assertEquals(60, assertNotNull(Countdowns.on(ScreenId.Vote, said = 900)).secondsLeft)
        assertEquals(0, assertNotNull(Countdowns.on(ScreenId.Vote, said = -5)).secondsLeft)
        assertEquals(1f, Countdown(90, 60).remaining)
        assertEquals(0f, Countdown(-1, 60).remaining)
    }
}
