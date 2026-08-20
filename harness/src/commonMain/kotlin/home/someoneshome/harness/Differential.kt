package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat

/**
 * One line where two runs of the same seeded round disagreed, for one seat.
 *
 * `null` on either side means that run's transcript was shorter, so a truncation is visible as a
 * truncation rather than as a run of substitutions.
 *
 * **[index] `-1` is not a line.** It reports that the seat itself was present in one run and
 * absent from the other, which the line walk cannot express: an absent seat and a silent one both
 * read as an empty transcript.
 *
 * **Every mismatching index is reported, so one inserted line cascades.** A count here is a count
 * of divergent lines, never of distinct problems, and the first entry is the one to read.
 */
class LineDivergence(
    val seat: Seat,
    val index: Int,
    val baseline: String?,
    val variant: String?,
) {
    override fun toString(): String =
        if (index < 0) "seat ${seat.index}, presence: baseline $baseline / variant $variant"
        else "seat ${seat.index}, line $index: baseline ${baseline ?: "<end>"} / variant ${variant ?: "<end>"}"
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
        append(" — ").append(divergences.size).append(" divergent line(s), ")
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
        // `copy`, not a positional constructor call. `seats` and `insiders` are both List<Seat>
        // and adjacent, so reordering the parameters of RoundArmed would silently arm every seat
        // as an Insider and still compile — and the resulting divergence would read as a leak.
        event.copy(insiders = insiders)
    }
}

/**
 * Two seats **trade** roles, so the number of Insiders is identical in both runs.
 *
 * Not two toggles. Toggling each in turn flips both off when both are already Insiders, taking
 * the count from two to zero — which is the one thing this function claims not to do, and which
 * matters the moment a rule reads the Insider count (F-005's denominator is a win condition).
 *
 * Exchanging two seats of the same role is a no-op by definition, and [differentialOnRoleExchange]
 * refuses to report on it rather than measuring a round against itself.
 */
fun withRolesExchanged(events: List<Event>, a: Seat, b: Seat): List<Event> = events.map { event ->
    if (event !is Event.RoundArmed) event else {
        val aWas = event.insiders.any { it.index == a.index }
        val bWas = event.insiders.any { it.index == b.index }
        if (aWas == bWas) event else {
            val rest = event.insiders.filterNot { it.index == a.index || it.index == b.index }
            val moved = buildList { if (bWas) add(a); if (aWas) add(b) }
            event.copy(insiders = (rest + moved).sortedBy { it.index })
        }
    }
}

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
): DifferentialResult = refusingANoOp(
    initial, events, withRoleSwapped(events, seat), listOf(seat),
    how = "swapping seat ${seat.index}",
)

/** The count-preserving form: [a] and [b] trade roles. Both are expected to differ. */
fun differentialOnRoleExchange(
    initial: GameState,
    events: List<Event>,
    a: Seat,
    b: Seat,
): DifferentialResult = refusingANoOp(
    initial, events, withRolesExchanged(events, a, b), listOf(a, b),
    how = "exchanging seats ${a.index} and ${b.index}",
)

/**
 * **Refuses to report on two runs that are the same round.**
 *
 * Every verdict this file produces is a claim about two DIFFERENT rounds. A swap that changes
 * nothing leaves `unexplained` empty for the one reason that means nothing, and the report is
 * then indistinguishable from a real pass — which is the failure this project has already had
 * four times: a confident PASS from an instrument measuring nothing.
 *
 * Three ways in, all silent before this check: no `RoundArmed` in the event list (the caller
 * passed an already-armed `initial`), a seat that is not seated (`reduce` filters `insiders` down
 * to seated players, so the rewritten event has no effect), and an exchange between two seats that
 * already hold the same role.
 *
 * Compared on the final authority state rather than on the event list, because the event list
 * changing is not the same as the round changing.
 */
private fun refusingANoOp(
    initial: GameState,
    baseline: List<Event>,
    variant: List<Event>,
    swapped: List<Seat>,
    how: String,
): DifferentialResult {
    val before = Transcript.render(drive(initial, baseline) { _, _, _ -> })
    val after = Transcript.render(drive(initial, variant) { _, _, _ -> })
    require(before != after) {
        "$how left the round unchanged, so there is nothing to diff. Both runs end at $before. " +
            "Reporting zero divergence here would be a pass from an instrument measuring nothing."
    }
    return diff(initial, baseline, variant, swapped)
}

/**
 * The diff itself. Walks the union of seats in both runs, so a seat that exists in only one is a
 * divergence rather than a row nobody compared.
 */
internal fun diff(
    initial: GameState,
    baseline: List<Event>,
    variant: List<Event>,
    swapped: List<Seat>,
): DifferentialResult {
    val a = recordPerClient(initial, baseline)
    val b = recordPerClient(initial, variant)

    val seats = (a.seats + b.seats).map { it.index }.distinct().sorted().map { Seat(it) }
    val seatsA = a.seats.map { it.index }.toSet()
    val seatsB = b.seats.map { it.index }.toSet()
    val found = mutableListOf<LineDivergence>()
    for (seat in seats) {
        // Presence first, because `linesFor` returns an empty list both for a seat that received
        // nothing and for a seat that never existed. Without this the line walk compares empty
        // against empty and a seat vanishing between runs goes unreported.
        val inA = seat.index in seatsA
        val inB = seat.index in seatsB
        if (inA != inB) {
            found += LineDivergence(
                seat, -1,
                if (inA) "seated" else "absent",
                if (inB) "seated" else "absent",
            )
        }
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
