package home.someoneshome.model

/**
 * **The presence plane: one seat's performance window, as the house records it** (D-111, D-136).
 *
 * D-111 split one question into two planes and the split is the whole of this file.
 *
 * **The work plane is absolute and hears nothing.** Walking away means the entry is never sent:
 * the house grades only what arrives, holds no partial answer anywhere, and therefore has no
 * abandonment count that could become a behavioural channel separating a real Subroutine from a
 * fake. Nothing in this type is about an answer, and nothing here may ever become about one.
 *
 * **The presence plane hears the window, and only the window.** While a player is performing,
 * their phone reports *performing here*; stopping ends the report. So the house does know that a
 * window opened and closed — never the half-finished answer inside it, and nothing at all about
 * correctness.
 *
 * ### The rule this type exists to make keepable: the house RECORDS, never RECITES
 *
 * This data has exactly one designed consumer — the spectator map's expiry (D-136), which is
 * hardware-dependent, unbuilt and frozen — and it *may never reach a notice, a count, or any
 * surface a living player can read*. Anything else rebuilds the leak the work plane just closed:
 * an app that publishes where players were standing and when is an app adjudicating alibis, which
 * is considerably more than this design wants.
 *
 * That rule is held in two independent places, neither of which subsumes the other:
 * `EmitSchema` gives [Effect.PresenceChanged] **no row at all**, so it ships to nobody (rule 2),
 * and `EmitSchema.audienceOf` addresses it to the performing seat alone, so it could not reach
 * another player even if somebody wrote a row.
 *
 * ### Not a `data class`, and never `@Serializable`
 *
 * Same posture [OpenSubroutine] holds. There is no client-facing narrowing of this type and there
 * must never be one: the narrower view of "where everybody is" is the spectator map, which is
 * inference built on top of this rather than a redaction of it (D-136).
 */
class Presence(
    val seat: Seat,
    /**
     * The card the window is anchored at, or **null for a seat the house has not placed**.
     *
     * A card is a place (D-123), and the house knows where somebody is for exactly one reason:
     * they scanned a card whose room it knows. Before a seat's first scan it knows nothing, and
     * that ignorance is a value here rather than an absent row — a row that appeared only once
     * somebody had been seen would make its own existence a report.
     *
     * **The room is one lookup away and deliberately not stored.** `HouseMap` turns a card into a
     * room and it lives with the map, not with the round; the rules have never held house
     * geography and this is not the field to start with. See the worklog escalation.
     */
    val at: MarkerId?,
    /** Whether a performance window is open right now. The whole of what the map may expire on. */
    val open: Boolean,
)
