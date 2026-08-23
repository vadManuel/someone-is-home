package home.someoneshome.model

/**
 * **An Insider ability that has a cooldown to be on.**
 *
 * Two members, and the enum exists so that the third is a row rather than a rewrite. D-132 says
 * *every* Insider cooldown starts running at half at arming, and an enum is how "every" stays a
 * statement about a set instead of a sentence in a comment.
 *
 * **A member here is a CLOCK and not an ability**, which is what lets [Access] carry three abilities
 * on one row. The Revoke has its own because it is time-critical and must always be ticking toward
 * available independently (`gdd.md:318`); Tier 2 shares one because sharing it is the design's only
 * balance knob for the whole support kit.
 *
 * **The Egress is still deliberately not here, and the reason changed when it was built.**
 *
 * It used to be absent because its mechanics were unbuilt. They are built now, and it stays absent
 * because **the Egress cooldown is not one seat's cooldown**: it is one clock for the whole house,
 * shared by every Insider, and [Cooldown] is keyed on a seat. Held as a list of rows it would be
 * N rows that must all move together, and *shared* would become a convention somebody maintains
 * rather than a fact the state carries — which is exactly the shape rule 3 spends its length
 * arguing against, one field over. It lives on `GameState.egressReadyAt`, as one value, so there
 * is nothing for a bug to de-synchronise.
 */
enum class InsiderAbility {
    Revoke,

    /**
     * **The Access pool — Tier 2's one clock, shared by Surge, Static and Isolate** (`gdd.md:306`,
     * `:318`).
     *
     * **Named for the pool and not for an ability, and that is the whole of it.** The design's cost
     * column for Tier 2 reads *Shared Access pool* for all three, and *one tunable number governs
     * the entire support kit and self-targeting without touching either win condition*. Three enum
     * members would be three rows on [GameState.cooldowns], and *shared* would become a convention
     * somebody maintains rather than a fact the state carries — which is exactly the argument this
     * file already makes one field over about the Egress's house-wide clock, arriving from the other
     * direction.
     *
     * ### Shared between the abilities, NOT between the Insiders
     *
     * A [Cooldown] is keyed on a seat and this one stays keyed on a seat. D-151 draws the
     * house-wide/per-Insider line and draws it in exactly one place: *the Egress cooldown is shared
     * house-wide across all Insiders; Revoke stays per-Insider.* The Access pool is not on either
     * side of that ruling, and *shared* in `gdd.md:306` is a statement about the three abilities in
     * the row it labels. So one pool per Insider, and a second Insider's kit is their own.
     *
     * ### The abilities weigh differently on it
     *
     * Firing restarts the pool for **that ability's** duration: `Balance.SURGE_COOLDOWN` is the
     * longest in the pool (`gdd.md:342` — *Surge is a commitment, not a tool*), `STATIC_COOLDOWN`
     * is shorter and `ISOLATE_COOLDOWN` shorter again. `Balance.ACCESS_COOLDOWN` is the pool's
     * nominal, and it exists so that D-132's *half at arming* has one number to be half of rather
     * than three.
     *
     * **An ability was replaced on this pool without this member moving** (D-186): Spoof left the
     * game, Static took its slot, and the change was a weight in `Balance` and a handler in the
     * rules. No row here moved, which is the point of the member being the pool.
     *
     * ⚠️ **Isolate arrives owing one rule and it is paid** — see [Egress.isNode]: Egress nodes are
     * excluded from Isolate's target list while an Egress is running, outright, with no fake success
     * (F-002 as amended by D-150).
     */
    Access,
}

/**
 * **When one seat's ability is ready again.**
 *
 * A tick, never a remaining count: the rules have no clock (`core` sees no datetime), so *ready
 * at step 30* is a fact that survives replay and *thirty seconds left* is a fact about the moment
 * somebody asked. Ticks arrive on events, sampled at the edge.
 *
 * Not `@Serializable` and not client-facing. A phone draws its own cooldown from its own input
 * echo — the player pressed the button and the house heard it — and no other phone is told
 * anything: an Insider whose cooldown reached the house at large would be an Insider announced by
 * a timer.
 */
class Cooldown(val seat: Seat, val ability: InsiderAbility, val readyAt: Tick) {

    fun readyBy(at: Tick): Boolean = at >= readyAt

    fun restartedAt(at: Tick, duration: Long): Cooldown =
        Cooldown(seat, ability, Tick(at.step + duration))
}
