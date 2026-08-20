package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat

/**
 * One line where two runs of the same seeded round disagreed, for one seat.
 *
 * `null` on either side means that run's transcript was shorter — reported where the divergence
 * starts rather than at the end, because a truncation and a substitution are different bugs.
 */
class LineDivergence(
    val seat: Seat,
    val index: Int,
    val baseline: String?,
    val variant: String?,
) {
    override fun toString(): String =
        "seat ${seat.index}, line $index: baseline ${baseline ?: "<end>"} / variant ${variant ?: "<end>"}"
}

/**
 * What a role-swapped pair of runs produced.
 *
 * [swappedSeats] are the seats whose own role changed. **Their transcripts are expected to
 * differ** — a player's own role is theirs to know. Everyone else's must be byte-identical, and
 * [unexplained] is the list that must be empty.
 */
class DifferentialResult(
    val swappedSeats: List<Seat>,
    val divergences: List<LineDivergence>,
) {
    val unexplained: List<LineDivergence>
        get() = divergences.filterNot { d -> swappedSeats.any { it.index == d.seat.index } }

    val leaked: Boolean get() = unexplained.isNotEmpty()

    override fun toString(): String = buildString {
        append("swapped ").append(swappedSeats.joinToString(",") { it.index.toString() })
        append(" — ").append(divergences.size).append(" divergence(s), ")
        append(unexplained.size).append(" unexplained")
        unexplained.take(10).forEach { append("\n  ").append(it) }
    }
}

/**
 * A copy of the round with one seat's role toggled, at the only place a role is stated.
 *
 * Toggled rather than reassigned, per the story: *one* player's role swapped. The Insider count
 * therefore changes between the two runs, which is itself a difference — [withRolesExchanged] is
 * the count-preserving variant and is the stronger test where it applies.
 */
fun withRoleSwapped(events: List<Event>, seat: Seat): List<Event> = events.map { event ->
    if (event !is Event.RoundArmed) event else {
        val present = event.insiders.any { it.index == seat.index }
        val insiders =
            if (present) event.insiders.filterNot { it.index == seat.index }
            else (event.insiders + seat).sortedBy { it.index }
        Event.RoundArmed(event.at, event.seed, event.seats, insiders)
    }
}

/** Two seats trade roles, so the number of Insiders is the same in both runs. */
fun withRolesExchanged(events: List<Event>, a: Seat, b: Seat): List<Event> =
    withRoleSwapped(withRoleSwapped(events, a), b)

/**
 * **Story 0.6 — the differential leak harness.**
 *
 * Runs the same seeded round twice, once with a role swapped, and diffs the per-client
 * transcripts. Every seat other than the swapped one must receive byte-identical bytes; anything
 * else is that player's alignment reaching a phone that is not theirs.
 *
 * ### What it cannot see, and why 0.6b is not a second opinion
 *
 * **Symmetric leaks are invisible.** Both runs come out of the same redaction code, so a bug that
 * ships the real Egress progress to *everyone* produces two identical transcripts and passes
 * clean. That is the entire reason story 0.6b exists as an independent requirement rather than as
 * a nicer way of writing this one.
 *
 * **Leaks carried by the lamp are invisible.** A lamp message is addressed to one phone, so a
 * per-role difference in luminance changes only the swapped seat's own transcript and lands in
 * [DifferentialResult.swappedSeats] as expected. In the house it is a tell visible across a dark
 * room. This harness reads the wire, and the lamp leaks through the air.
 *
 * **Divergence is not the same as leak.** A role legitimately changes what happens — an Insider's
 * contact revokes and a Resident's does not — and every downstream consequence of that shows up
 * here. The output is a report to be explained, not a verdict. What it rules out is a divergence
 * nobody can explain.
 *
 * ### A limitation that is true today
 *
 * `reduce` does not read [GameState.roleOf] at all yet, so role is inert in the rules and this
 * harness currently has nothing to find. It passes for that reason and not because redaction was
 * checked. Its value starts the day a rule branches on role, which is why it is built now.
 */
fun differentialOnRoleSwap(
    initial: GameState,
    events: List<Event>,
    seat: Seat,
): DifferentialResult = diff(initial, events, withRoleSwapped(events, seat), listOf(seat))

/** The count-preserving form: [a] and [b] trade roles. Both are expected to differ. */
fun differentialOnRoleExchange(
    initial: GameState,
    events: List<Event>,
    a: Seat,
    b: Seat,
): DifferentialResult = diff(initial, events, withRolesExchanged(events, a, b), listOf(a, b))

/**
 * The diff itself. Walks the union of seats in both runs, so a seat that exists in only one is a
 * divergence rather than a row nobody compared.
 */
private fun diff(
    initial: GameState,
    baseline: List<Event>,
    variant: List<Event>,
    swapped: List<Seat>,
): DifferentialResult {
    val a = recordPerClient(initial, baseline)
    val b = recordPerClient(initial, variant)

    val seats = (a.seats + b.seats).map { it.index }.distinct().sorted().map { Seat(it) }
    val found = mutableListOf<LineDivergence>()
    for (seat in seats) {
        val left = a.linesFor(seat)
        val right = b.linesFor(seat)
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrNull(i)
            val r = right.getOrNull(i)
            if (l != r) found += LineDivergence(seat, i, l, r)
        }
    }
    return DifferentialResult(swapped.sortedBy { it.index }, found)
}
