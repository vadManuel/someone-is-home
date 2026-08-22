package home.someoneshome.harness

import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MessageKind
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
    ignoring: Set<MessageKind> = emptySet(),
): DifferentialResult = refusingANoOp(
    initial, events, withRoleSwapped(events, seat), listOf(seat),
    how = "swapping seat ${seat.index}",
    ignoring = ignoring,
)

/** The count-preserving form: [a] and [b] trade roles. Both are expected to differ. */
fun differentialOnRoleExchange(
    initial: GameState,
    events: List<Event>,
    a: Seat,
    b: Seat,
    ignoring: Set<MessageKind> = emptySet(),
): DifferentialResult = refusingANoOp(
    initial, events, withRolesExchanged(events, a, b), listOf(a, b),
    how = "exchanging seats ${a.index} and ${b.index}",
    ignoring = ignoring,
)

/**
 * **D-109's one asymmetry, named so that it can be subtracted and then measured on its own.**
 *
 * A plain Resident's accepted entry moves SystemIntegrity and an Insider's does not, so the meter
 * genuinely depends on *which* seats hold the role, not merely on how many do. That reaches
 * players outside the system — who see the real bars live (`gdd.md:1014`) — and nobody else.
 *
 * **Subtracting it is not softening the harness, and the difference matters.** One inserted or
 * withheld line shifts every later line in a transcript, so a raw diff of two runs that banked at
 * different moments reports lamps and meetings as divergent too — the cascade [LineDivergence]
 * warns about. Dropping this one kind from *both* runs asks the question that is actually open:
 * **is there anything OTHER than the meter that depends on who the Insiders are?** The tests that
 * use it pair it with the complementary assertion — that the meter divergence is really there, and
 * that it reaches no living player — so nothing is hidden by being filtered.
 *
 * **Never add a kind here to make a test pass.** Every entry is a documented, ruled-on asymmetry;
 * a second one would need its own decision-log number before it needed a line of code.
 */
val METER_ASYMMETRY: Set<MessageKind> = setOf(EmitSchema.SUBROUTINE_PROGRESSED)

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
    ignoring: Set<MessageKind> = emptySet(),
): DifferentialResult {
    val before = Transcript.render(drive(initial, baseline) { _, _, _ -> })
    val after = Transcript.render(drive(initial, variant) { _, _, _ -> })
    require(before != after) {
        "$how left the round unchanged, so there is nothing to diff. Both runs end at $before. " +
            "Reporting zero divergence here would be a pass from an instrument measuring nothing."
    }
    return diff(initial, baseline, variant, swapped, ignoring)
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
    ignoring: Set<MessageKind> = emptySet(),
): DifferentialResult {
    val a = recordPerClient(initial, baseline)
    val b = recordPerClient(initial, variant)

    // The kind is the line's prefix, which is why Transcript renders it there. Dropped from BOTH
    // runs before the walk, so an ignored kind cannot shift the indices of the lines around it.
    val ignoredNames = ignoring.map { it.name }.toSet()
    fun kept(lines: List<String>): List<String> =
        if (ignoredNames.isEmpty()) lines
        else lines.filterNot { it.substringBefore('|') in ignoredNames }

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
        val left = untilTheRoundEnds(kept(a.linesFor(seat)), kept(b.linesFor(seat)))
        val right = untilTheRoundEnds(kept(b.linesFor(seat)), kept(a.linesFor(seat)))
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrNull(i)
            val r = right.getOrNull(i)
            if (l != r) found += LineDivergence(seat, i, l, r)
        }
    }
    return DifferentialResult(swapped.sortedBy { it.index }, found)
}

/**
 * **The second ruled asymmetry, and unlike the meter it cannot be subtracted by kind** (D-131).
 *
 * ### Why the round ending is a function of who holds the role
 *
 * Parity counts **living plain Residents against living Insiders**. That is not an incidental
 * dependency on the assignment — it *is* the assignment, arithmetic on it. So exchanging two
 * seats' roles genuinely ends one round and not the other: in the shared fixture, seat 1 restrained
 * and seats 0, 2 and 7 revoked leaves `3 > 1` under the baseline draw and `2 <= 2` under the
 * exchange, and the second round is over at that meeting. `WinRoute.InsidersRestrained` is the same
 * story in a different arithmetic.
 *
 * ### Why [METER_ASYMMETRY]'s treatment does not work here
 *
 * The meter asymmetry is one message kind, so it can be dropped from both runs and the surrounding
 * lines stay aligned. **An ending is not a message — it is the round stopping.** The admission gate
 * refuses everything afterwards, so the shorter run's transcript simply stops, for every seat at
 * once. Dropping the three ending kinds would leave that truncation untouched and every remaining
 * line of the longer run would be reported as divergent: the cascade [LineDivergence] warns about,
 * several hundred lines of it, with nothing in it that anybody could act on.
 *
 * ### What this therefore does, and what it deliberately keeps
 *
 * It compares **everything that happened while both rounds were still running** — up to, and not
 * including, the first ending line in either — and the comparison there is exactly as strict as it
 * ever was. Past that point the two runs are not two views of one round, they are two different
 * games, and a diff between them is a category error rather than a leak report.
 *
 * **The truncation detection is untouched in every other case.** When neither run ends there is no
 * `RoundEnded` line to find, both lists come back whole, and a seat that went silent early is still
 * a run of `<end>` divergences. This narrows nothing except the one thing D-131 makes legitimately
 * asymmetric.
 *
 * **It is a subtraction and must be visible as one**, exactly as the meter's is: `DifferentialTest`
 * pairs it with the assertion that the two runs really do end differently, so it can never quietly
 * become a filter over two identical rounds. ⚠️ **Escalated in the worklog**: this weakens a leak
 * instrument, and a second reader should agree that ending-time is not a channel — the argument is
 * that in any real round exactly one assignment exists, nothing follows the ending, and the reveal
 * on that very message states the alignment outright.
 *
 * **`internal`, because the two direct comparisons in `DifferentialTest` need the same rule.** They
 * compare verdict streams and effect streams without going through [diff], and a second hand-rolled
 * copy of *stop where the round stopped* is two definitions of one concept — the day they disagreed,
 * the quieter one would be the one deciding whether a leak had been found.
 */
internal fun untilTheRoundEnds(lines: List<String>, other: List<String>): List<String> {
    fun endsAt(rows: List<String>) = rows.indexOfFirst { it.startsWith(ENDING_PREFIX) }
    val cut = listOf(endsAt(lines), endsAt(other)).filter { it >= 0 }.minOrNull() ?: return lines
    return lines.take(cut)
}

/** The kind that says the round is over, as it appears at the head of a transcript line. */
private const val ENDING_PREFIX = "RoundEnded|"
