package home.someoneshome.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The fake house may never drive a phone that has a real one** — [standsInForTheHouse].
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
 * ### The switches are gone, and with them the gap the switched version flagged on itself
 *
 * The first fix took both switches and answered *neither*, and wrote down what it could not reach:
 * *a phone somebody taps through the lobby into a real house is launched with neither switch, so it
 * lands in the bench's branch and is driven by the fake house anyway.* **That phone is the product.**
 * Every real evening, once the benches are gone, is launched with no switches at all.
 *
 * So what is asked now is the fact rather than the arrangement — *is a house attached* — and the
 * sweep below is over what a phone can actually be rather than over four combinations of launch
 * environment. `HousedPhoneTest` in `ui` is the other half: this pins the decision, that one pins
 * what the decision does to a screen.
 */
class HouseStandInTest {

    /**
     * **A phone with a house attached is never driven by the fake one**, however it got the house.
     *
     * The switched version needed three rows here — the scripted evening, the host's arming switch,
     * and both at once — because it was enumerating launch arrangements and a missed row was a
     * missed round. There is one row now because there is one fact, and a fourth way into a round
     * arrives already answered: it will arrive through `RoundClient.linked` like the other three.
     */
    @Test
    fun aPhoneInARealRoundIsNeverDrivenByTheFakeHouse() {
        assertFalse(
            standsInForTheHouse(houseAttached = true),
            "a house was attached and the bench's fake house drove the screens anyway",
        )
    }

    /**
     * **A phone with no house still gets the stand-in**, and that is the harness working rather
     * than an oversight.
     *
     * Without it the ported meeting stops at the check-in screen and stays there — true, and
     * unwalkable, which is how a ported flow stops being looked at. Pinned so that a later fix
     * cannot quietly delete the bench by making the condition stricter.
     *
     * **The phone this now describes is a bench and only a bench.** Under the switched version this
     * case also caught every phone a person tapped into a real house through the lobby, which is
     * what made a passing test compatible with three fictional meetings.
     */
    @Test
    fun aBenchWithNoHouseKeepsItsStandIn() {
        assertTrue(
            standsInForTheHouse(houseAttached = false),
            "a bench with no house attached lost the stand-in, so its meeting is unwalkable",
        )
    }
}
