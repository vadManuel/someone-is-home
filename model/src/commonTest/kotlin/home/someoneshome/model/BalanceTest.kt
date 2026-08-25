package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The tripwire under [Balance.MINIMUM_SEATS]'s temporary lowering, and nothing else.**
 *
 * The floor is a ruled number (D-128, revision 29's D-158 family) that has been moved for an
 * evening of three-phone testing. A lowered constant is silent — every other test in this repo
 * reads the constant rather than the literal, so all of them stay green at three and none of them
 * would ever notice the release shipping with a three-seat party floor.
 *
 * This file is the noise. It pins the temporary value, so **putting the floor back to 5 fails
 * here**, and the failure is the reminder.
 */
class BalanceTest {

    /**
     * ⚠️ **DELETE THIS TEST WHEN [Balance.MINIMUM_SEATS] GOES BACK TO 5.**
     *
     * It is not a rule and it is not asserting anything the game wants to be true. It asserts the
     * temporary owner-ordered value from 2026-08-25 so that restoring the ruled minimum cannot
     * happen quietly: the revert breaks this test, and the fix for that break is to delete the
     * whole file along with the ⚠️ block on the constant.
     */
    @Test
    fun minimumSeatsIsTemporarilyLoweredForDeviceTesting() {
        assertEquals(
            3, Balance.MINIMUM_SEATS,
            "the floor is not at its temporary 3 — if it is back at D-128's ruled 5, DELETE this " +
                "test and the TEMPORARILY LOWERED block on Balance.MINIMUM_SEATS",
        )
    }

    /**
     * **The consequence the owner declined to touch, verified rather than adjusted.**
     *
     * Lowering the floor moves [InsiderBand]'s `MINIMUM_SEATS..6` pin down with it, so the smallest
     * lawful party draws exactly one Insider by the existing formula and no band opens at three.
     * Two Insiders in three seats would be parity before the first meeting.
     *
     * Written against the constant, so it stays true at 5 as well and survives the revert.
     */
    @Test
    fun theSmallestLawfulPartyDrawsExactlyOneInsider() {
        assertEquals(
            1..1, InsiderBand.of(Balance.MINIMUM_SEATS),
            "the smallest lawful party is not pinned to a single Insider",
        )
        assertEquals(
            1, InsiderBand.clamp(seats = Balance.MINIMUM_SEATS, chosen = 2),
            "a host hand-picked a second Insider into the smallest lawful party",
        )
    }
}
