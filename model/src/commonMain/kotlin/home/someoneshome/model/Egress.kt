package home.someoneshome.model

/**
 * **Which Egress the house started — and in v1 it is a label and nothing else** (`gdd.md:349`).
 *
 * *A Insider triggers it; the house picks which one.* **Beacon** broadcasts outward, **Tether**
 * piggybacks a cellular radio. The two are **mechanically identical**: same timer, same nodes, same
 * Sync Pulse, same outcome. The difference is fiction, and it is drawn at fire time rather than
 * chosen by the actor, so nothing about which one appeared says anything about who fired it.
 *
 * **It is carried rather than re-derived** for the reason `Event.RoundArmed.insiders` is: the draw
 * happens above the rules, rides the event, and lands in the recording as an input. A type
 * re-rolled at display time would be a round that replayed as a different evening.
 */
enum class EgressType { Beacon, Tether }

/**
 * **One participant's Sync Pulse beat, graded and waiting for a partner** (`gdd.md:355`).
 *
 * *Unlimited participants; holders at one node pair with holders at the other; extras form
 * concurrent pairs; first success contains.* That is what this list is: every seat that has hit the
 * beat recently and has not yet found somebody at the **other** node.
 *
 * [node] is the house's own placement and never the phone's claim — see
 * [Event.SyncPulseReturned], which carries no node at all.
 */
data class PulseOffer(val seat: Seat, val node: MarkerId, val at: Tick)

/**
 * **A seat that missed the beat, and the cost of having missed it** (`gdd.md:359`).
 *
 * *A failed attempt costs a 2–3s lockout* — which makes stalling meaningfully more expensive and
 * stops spam-retry from being optimal. Without it, a Insider standing at a node could hammer the
 * button and hold the slot for nothing.
 *
 * **It reaches no client as itself.** What the seat gets back is [Effect.SyncPulseAnswered] with
 * `held = false`, which is the same shape a badly-timed beat gets — so a locked-out attempt and a
 * missed one are indistinguishable from the phone, and neither is distinguishable from outside.
 */
data class Lockout(val seat: Seat, val until: Tick)

/**
 * **An Egress in progress — the whole of it, authority-side** (`gdd.md:349`, D-131, D-133).
 *
 * Held on [GameState] and never `@Serializable`. Most of what is in here is authority-only: the
 * held offers name who is standing at which node with a live beat, which is presence data by
 * another name (D-136) and reaches nobody.
 *
 * **Ordered lists throughout, no sets** (rule 4). Two offers landing inside one step must pair in
 * the same order on every replay, and a `Set` iterates in hash order — which here would mean *a
 * different pair contained the Egress* on the second run of the same recording.
 *
 * ### The timer pauses and never resets (D-133)
 *
 * A meeting can only be called during an Egress by **reporting a Revoked player** — the card is
 * inert (D-133, and the refusal is [RefusalReason.EgressRunning]) — and that meeting **pauses**
 * this timer, resuming it when the lights go out. A reset would make the report a free Egress
 * cancellation and every Egress would end the same way; a pause makes it a decision about spending
 * time. [pausedAt] is how that is held: the deadline does not move while the party is assembled,
 * and it moves by exactly the length of the meeting when they disperse.
 *
 * ### It outlives its Insiders (D-131)
 *
 * *Restraining the last Insider during an Egress does not end the round: the house does not stop
 * what it was told to start.* Nothing in this type refers to who fired it, and nothing anywhere
 * cancels it on a Restrain. That is the rule, expressed as an absence that is written down.
 */
data class Egress(
    val type: EgressType,
    /**
     * **The two nodes, in draw order** (F-001, ratified).
     *
     * Two ordinary registered markers in **non-adjacent rooms**, chosen at fire time from the
     * round's active set — no setup step, and a geographically different Egress every time, which a
     * fixed pair could never be. Chosen above the rules, because choosing them needs the home's
     * shape and the rules have never held one.
     *
     * A `List` rather than a `Pair` for the reason every collection in this module is one: it is
     * recorded, it is rendered, and it is read back. [namesTwoNodes] is what holds it to two.
     */
    val nodes: List<MarkerId>,
    val firedAt: Tick,
    /**
     * When it succeeds if nobody contains it. **Moved by a pause, never by a reset** (D-133).
     */
    val deadline: Tick,
    /** The moment the timer stopped, or null while it is running. See the type KDoc. */
    val pausedAt: Tick?,
    /** Graded beats waiting for a partner at the *other* node, in arrival order. */
    val offers: List<PulseOffer>,
    /** Seats paying for a missed beat, in seat order. */
    val lockouts: List<Lockout>,
) {
    /**
     * **Exactly two, and they are distinct** — the invariant containment rests on.
     *
     * *Two people at two separate markers.* A pair that was secretly one marker would make
     * containment impossible while looking exactly like an Egress that could be contained, and the
     * Residents would lose a round to a bug they could not see. Asserted rather than assumed
     * because the selection degrades in a small home and a degrade is where a pair collapses.
     */
    val namesTwoNodes: Boolean
        get() = nodes.size == 2 && nodes[0].value != nodes[1].value

    /**
     * Whether [node] is one of the two. A marker that is not is not part of this Egress.
     *
     * ### ⚠️ ISOLATE MUST ASK THIS, AND ISOLATE DOES NOT EXIST YET (F-002, amended by ruling)
     *
     * **Egress nodes are excluded from Isolate's target list for the duration of an Egress.**
     * Isolate takes a marker offline; a Insider who Isolates a node mid-Egress makes containment
     * impossible and wins with no counterplay, which is F-002 exactly.
     *
     * **The ruling amends F-002's proposed resolution and the amendment is the important half.**
     * F-002 proposed making the nodes *immune* — the ability fires, appears to succeed, and does
     * nothing, per the rule that abilities never report failure. The owner ruled the other way:
     * **exclude them from the target list outright. No fake success.** It leaks nothing, because
     * the two nodes are already named to every phone in the house — a roster that omits them omits
     * something everybody is looking at — and a fake success would spend the Insider's cooldown on
     * a shot the house had already decided could not land.
     *
     * That is the whole of what this build can record: **there is no Isolate, no Access pool, and
     * no target list.** The note is here rather than in a document because this is the function the
     * exclusion will be written against.
     */
    fun isNode(node: MarkerId): Boolean = nodes.any { it.value == node.value }

    /**
     * How long is left, in ticks. **Frozen while paused**, which is the whole of D-133's third rule.
     *
     * Floored at zero: a deadline already behind us is nought remaining, never a negative number
     * that a screen would render as a countdown running backwards.
     */
    fun remainingAt(at: Tick): Long =
        (deadline.step - (pausedAt ?: at).step).coerceAtLeast(0L)

    /** Whether this seat is paying for a missed beat at [at]. */
    fun lockedAt(seat: Seat, at: Tick): Boolean =
        lockouts.any { it.seat.index == seat.index && at < it.until }

    /**
     * A held offer from **another** seat at the **other** node, recent enough to be simultaneous.
     *
     * Three conditions and every one of them is load-bearing. *Another seat*, or a single player
     * with a live offer would contain the Egress by tapping twice. *The other node* — this is
     * `gdd.md:352`'s **two people at two separate markers**, and a pair standing at the same node
     * must not contain, or the whole geography of the containment is decoration. *Recent*, because
     * the pulse is simultaneous by design and an offer that stood all round would let two people
     * who were never in the house at the same moment pair with each other.
     *
     * The first match in arrival order wins, so *first success contains* is decided by the
     * recording's order rather than by a hash.
     */
    fun partnerFor(seat: Seat, node: MarkerId, at: Tick, window: Long): PulseOffer? =
        offers.firstOrNull {
            it.seat.index != seat.index &&
                it.node.value != node.value &&
                at.step - it.at.step <= window
        }

    /**
     * Hold this seat's graded beat, dropping anything that has gone stale.
     *
     * One offer per seat: a second beat from a seat that is already waiting **replaces** the first
     * rather than joining it, or a player could bank offers and pair with their own history.
     */
    fun holding(offer: PulseOffer, window: Long): Egress = copy(
        offers = offers
            .filterNot { it.seat.index == offer.seat.index }
            .filter { offer.at.step - it.at.step <= window } + offer,
    )

    /** Start this seat's lockout, replacing any earlier one. In seat order, like everything here. */
    fun lockingOut(seat: Seat, until: Tick): Egress = copy(
        lockouts = (lockouts.filterNot { it.seat.index == seat.index } + Lockout(seat, until))
            .sortedBy { it.seat.index },
    )

    /**
     * The party has been called in and the house holds its breath (D-133).
     *
     * **A second pause does not move [pausedAt]**, because the second call cannot happen — one
     * meeting at a time ([RefusalReason.MeetingAlreadyRunning]) — and a pause that reset the mark
     * would be the one shape that could silently extend the timer.
     */
    fun pausedAt(at: Tick): Egress = if (pausedAt != null) this else copy(pausedAt = at)

    /**
     * Lights out, and the countdown picks up **exactly where it stopped**.
     *
     * The deadline moves forward by the length of the meeting and by nothing else. This is the
     * arithmetic D-133 is about: `deadline += (now − pausedAt)` leaves the remaining time
     * unchanged across the meeting, where `deadline = now + EGRESS_TIMER` would hand the room a
     * free cancellation and every Egress would end the same way.
     *
     * **Offers do not survive it.** Everybody walked to the meeting area; nobody is standing at a
     * node any more, and a beat held across a meeting would pair with somebody who has not been in
     * the room for ninety seconds.
     */
    fun resumedAt(at: Tick): Egress {
        val paused = pausedAt ?: return this
        return copy(
            deadline = Tick(deadline.step + (at.step - paused.step)),
            pausedAt = null,
            offers = emptyList(),
        )
    }

    companion object {
        /** An Egress the instant it fires: full timer, running, nobody at a node yet. */
        fun fired(at: Tick, type: EgressType, nodes: List<MarkerId>, timer: Long): Egress = Egress(
            type = type,
            nodes = nodes,
            firedAt = at,
            deadline = Tick(at.step + timer),
            pausedAt = null,
            offers = emptyList(),
            lockouts = emptyList(),
        )
    }
}
