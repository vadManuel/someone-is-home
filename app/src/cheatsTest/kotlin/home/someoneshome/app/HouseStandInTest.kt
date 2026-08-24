package home.someoneshome.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The fake house may never drive a phone that has a real one** — [standsInForTheHouse], swept
 * rather than sampled.
 *
 * `FlowHost`'s stand-in fires whatever push the current screen is waiting for after
 * `HOUSE_STAND_IN_MS`, which is what makes a ported flow with no house attached walkable. Its own
 * KDoc states the rule: *it must never be true in a real round.* Two things moving one screen race
 * on every meeting, and the loser is the meeting.
 *
 * ⚠️ **This is written because the rule was broken in production and nothing could see it.** The
 * expression read one launch switch — *this process is playing the scripted evening* — and the
 * payoff run added a second way to have a real round, the host's arming switch, without teaching
 * this line about it. The owner's evening is the evidence: ASSEMBLE to NOTICE in 4.0 seconds, which
 * is `HOUSE_STAND_IN_MS` to the millisecond on a screen that has no `autoAdvance` entry at all.
 *
 * **The sweep is over both switches and all four combinations**, deliberately. A test that only
 * checked the arming switch would pass a fix that broke the scripted round, and the whole failure
 * being guarded is *somebody added a second way in and only the first one was checked*.
 */
class HouseStandInTest {

    /**
     * **Both ways of being in a real round switch the stand-in off, and so does carrying both.**
     *
     * The fourth row is not padding: a host phone that plays the scripted evening has *always*
     * armed one, so `armed` implies `armsTheHouse` on that path and a build that read them as
     * mutually exclusive would leave the host — the one phone running the rules — driven by a fake
     * house.
     */
    @Test
    fun aPhoneInARealRoundIsNeverDrivenByTheFakeHouse() {
        val real = listOf(
            "the scripted evening" to (true to false),
            "the host's arming switch" to (false to true),
            "both switches at once" to (true to true),
        )
        for ((what, switches) in real) {
            val (scripted, arms) = switches
            assertFalse(
                standsInForTheHouse(playsAScriptedRound = scripted, armsTheHouse = arms),
                "$what: a real round was being played and the bench's fake house drove the screens",
            )
        }
    }

    /**
     * **A phone with neither switch still gets the stand-in**, and that is the harness working
     * rather than an oversight.
     *
     * Without it the ported meeting stops at the check-in screen and stays there — true, and
     * unwalkable, which is how a ported flow stops being looked at. Pinned so that a later fix
     * cannot quietly delete the bench by making the condition stricter.
     *
     * ⚠️ **It is also the residual gap, stated as a passing test rather than hidden.** A phone
     * somebody taps through the lobby into a real house is launched with neither switch, so it
     * lands here and is driven by the fake house anyway. Closing that needs *the house has spoken
     * to me* instead of *how was this process started*, and that belongs with the production
     * arming path (E-PAY-1).
     */
    @Test
    fun aBenchWithNoRoundKeepsItsStandIn() {
        assertTrue(
            standsInForTheHouse(playsAScriptedRound = false, armsTheHouse = false),
            "a bench with no round attached lost the stand-in, so its meeting is unwalkable",
        )
    }
}
