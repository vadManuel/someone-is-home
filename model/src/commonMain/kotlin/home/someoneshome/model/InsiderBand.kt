package home.someoneshome.model

/**
 * **D-103's balance envelope, and the reason it clamps the setting rather than only the draw.**
 *
 * The lobby's Insider-count setting defaults to UNKNOWN: the house draws the count at arming,
 * locks it, and tells no one until the round ends. What makes hiding affordable is a display
 * rule elsewhere — SystemIntegrity reaches a panel only as a percentage, never as `28/42`, or the
 * denominator hands every reader the count by division.
 *
 * Both edges of the band are load-bearing, and they protect opposite sides:
 *
 * - **the minimum protects the Insider side** — one Insider against fifteen Residents is
 *   unwinnable for the one;
 * - **the maximum protects the Resident side**, who must still be able to complete
 *   `(seats − insiders) × 7` and who are guaranteed a living population of at least two by the
 *   parity rule (F-016).
 *
 * So the band clamps the **setting itself**, not just the draw: a host cannot hand-pick a count
 * outside it any more than UNKNOWN can land on one. That is the D-103 amendment, and it is why
 * [clamp] exists beside [of] — a caller that only consulted the band when drawing would let the
 * host type a 5 into a six-seat home and find out at arming.
 *
 * ### The amendment: at five and six seats there is exactly one Insider
 *
 * D-103 as amended by revision 29 replaces the band's lower reach with a fixed value for the two
 * smallest lawful parties. **Seats 5–6 → exactly 1. From 7 the band resumes.**
 *
 * The arithmetic that forced it: at five or six seats two Insiders reach parity (D-131) after one
 * or two Revokes — not a hard round for the Residents but a round that can be over before the
 * first meeting has anything to discuss. **Seven seats is where a one-plain-Resident buffer
 * survives two Revokes**, so seven is where the second Insider becomes legal.
 *
 * ### The numbers are placeholders and this file does not own them
 *
 * `max(1, ⌊seats/6⌋)` to `⌈seats/4⌉` from seven seats up — 8 → 1–2, 12 → 2–3, 16 → 2–4 — with
 * 5 and 6 pinned to 1 by the amendment. **Playtest owns these, as it owns the 7.** They are
 * written here once so that the lobby, the host's settings and whatever arms the round cannot each
 * hold a different opinion; they are not a decision this layer is making. Player count is
 * virtually unlimited by design intent — the engineering posture is *designed for 6–10, no hard
 * cap built*.
 */
object InsiderBand {

    /**
     * The band for a home this many seats are sitting in.
     *
     * Below one seat the formula's own arithmetic produces an empty range (`1..0`), and that is
     * the honest answer rather than an invented floor: there is no lawful Insider count for a
     * lobby with nobody in it, so the setting has nothing to offer but UNKNOWN. [clamp] reads it
     * that way.
     */
    fun of(seats: Int): IntRange {
        // The amendment, and it is a pin rather than a floor: at five and six seats the band has
        // ONE member, so a host cannot hand-pick two Insiders into a five-seat round and UNKNOWN
        // cannot land on one either. Written before the general formula because it replaces it
        // rather than bounding it.
        if (seats in AMENDED_TO_ONE) return 1..1
        val low = maxOf(1, seats / 6)
        // Ceiling division, integer-only: `model` carries no floating point anywhere near a
        // balance number, because a band edge that depends on rounding mode is a band edge that
        // differs between two phones.
        val high = (seats + 3) / 4
        return low..high
    }

    /**
     * A host's chosen count, pulled inside the band — or refused outright.
     *
     * Null in, null out: UNKNOWN is a setting, not an unset value, and clamping must never
     * quietly turn "let the house decide" into a number. Null out for a chosen count too when
     * the band is empty, because there is no nearest lawful value to move it to.
     */
    fun clamp(seats: Int, chosen: Int?): Int? {
        val band = of(seats)
        if (band.isEmpty()) return null
        if (chosen == null) return null
        return chosen.coerceIn(band.first, band.last)
    }

    /**
     * The two party sizes D-103's amendment pins to a single Insider.
     *
     * A range rather than two literals so that the pin reads as what it is — the band's lower
     * reach replaced over a stretch of party sizes, ending where D-131's parity arithmetic says
     * a second Insider becomes legal.
     */
    private val AMENDED_TO_ONE = Balance.MINIMUM_SEATS..6
}
