package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-103's band, against the four worked examples the decision itself gives.
 *
 * The numbers are placeholders and playtest owns them; what is being held here is that the band
 * is a **balance envelope with two load-bearing edges** and that it clamps the *setting*, not
 * only the draw. A version that consulted the band when drawing and let a host hand-pick
 * anything would satisfy every other test in this repo.
 */
class InsiderBandTest {

    /** The decision's own table: 6 → 1–2, 8 → 1–2, 12 → 2–3, 16 → 2–4. */
    @Test
    fun theWorkedExamplesFromTheDecision() {
        assertEquals(1..2, InsiderBand.of(6))
        assertEquals(1..2, InsiderBand.of(8))
        assertEquals(2..3, InsiderBand.of(12))
        assertEquals(2..4, InsiderBand.of(16))
    }

    /**
     * **Both edges clamp the setting itself.** A host cannot hand-pick a count outside the band
     * any more than UNKNOWN can land on one.
     */
    @Test
    fun aHandPickedCountIsPulledInsideTheBand() {
        assertEquals(2, InsiderBand.clamp(seats = 6, chosen = 5), "the maximum edge did not hold")
        assertEquals(1, InsiderBand.clamp(seats = 6, chosen = 0), "the minimum edge did not hold")
        assertEquals(2, InsiderBand.clamp(seats = 12, chosen = 1), "twelve seats want at least two")
        assertEquals(4, InsiderBand.clamp(seats = 16, chosen = 9))
        // Inside the band, untouched.
        assertEquals(2, InsiderBand.clamp(seats = 8, chosen = 2))
    }

    /**
     * UNKNOWN is a setting, not an unset value.
     *
     * Clamping must never quietly turn "let the house decide, and tell nobody" into a number —
     * that would be the one setting in the lobby whose whole purpose is to hide, silently
     * becoming the thing it hides from.
     */
    @Test
    fun unknownClampsToUnknown() {
        assertNull(InsiderBand.clamp(seats = 6, chosen = null))
        assertNull(InsiderBand.clamp(seats = 16, chosen = null))
    }

    /**
     * An empty lobby has no lawful count, and the honest answer is to say so rather than invent a
     * floor. The formula's own arithmetic produces `1..0` there; nothing rounds it up.
     */
    @Test
    fun aLobbyWithNobodyInItOffersNothingButUnknown() {
        assertTrue(InsiderBand.of(0).isEmpty())
        assertNull(InsiderBand.clamp(seats = 0, chosen = 1))
    }

    /**
     * The maximum protects the Resident side: they must still be able to complete
     * `(seats − insiders) × 7`, so the band may never take every seat.
     */
    @Test
    fun theBandNeverSwallowsTheWholeHome() {
        for (seats in 2..24) {
            val band = InsiderBand.of(seats)
            assertTrue(band.last < seats, "$seats seats admit ${band.last} Insiders and no Residents")
            assertTrue(band.first <= band.last, "the band inverted at $seats seats: $band")
        }
    }
}
