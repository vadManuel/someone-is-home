package home.someoneshome.model

/**
 * **An Insider ability that has a cooldown to be on.**
 *
 * One member today, and the enum exists so that the second is a row rather than a rewrite. D-132
 * says *every* Insider cooldown starts running at half at arming, and an enum with one entry is
 * how "every" stays a statement about a set instead of a sentence in a comment.
 *
 * **The Egress is deliberately not here.** Its mechanics are unbuilt — no node designation
 * (F-001), no countdown, no completion — and inventing a cooldown for a thing that cannot yet be
 * started would be a second stub pretending to be a rule. When the Egress lands it brings its own
 * duration and this enum gains a member.
 */
enum class InsiderAbility { Revoke }

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
