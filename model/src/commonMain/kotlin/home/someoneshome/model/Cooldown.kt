package home.someoneshome.model

/**
 * **An Insider ability that has a cooldown to be on.**
 *
 * One member today, and the enum exists so that the second is a row rather than a rewrite. D-132
 * says *every* Insider cooldown starts running at half at arming, and an enum with one entry is
 * how "every" stays a statement about a set instead of a sentence in a comment.
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

    /*
     * ⚠️ **Isolate belongs here when somebody builds it, and it arrives owing one rule** — see
     * [Egress.isNode], which is where that rule will be written: Egress nodes are excluded from
     * Isolate's target list while an Egress is running, outright, with no fake success. Isolating a
     * node mid-Egress otherwise wins the round with no counterplay (F-002).
     */
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
