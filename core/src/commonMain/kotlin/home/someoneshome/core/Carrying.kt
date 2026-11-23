package home.someoneshome.core

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Carry
import home.someoneshome.model.EntityId
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick

/*
 * **What a scan of an ordinary card does, now that work can span cards** (D-322).
 *
 * A Subroutine opens at its one card, as it always has. The two structured pieces of work do not:
 *
 * - a **Memory Dump** shows its pattern at the source and takes it back at the target, and the
 *   house holds the pattern in between (`gdd.md:640`);
 * - an **Array Wipe** is three stations and one disk in hand at a time, and **while a disk is held
 *   no other scan opens anything** (`gdd.md:664`, F-014).
 *
 * So a scan's answer depends on what the seat is carrying, and that is decided here, in one
 * function, rather than in a branch per kind inside the handler: [stepAt] says which step the card
 * is, and the handler builds the same three effects for every one of them (rule 1).
 */

/** **Which step of this seat's work a scan of one card is.** Exactly one, always. */
internal sealed interface ScanStep {

    /**
     * D-124's NOTHING FOR YOU HERE — and, while a disk is in hand, every card but its station, and
     * while a pattern is held, every card but its target and its own source (D-328, D-330).
     */
    data object Nothing : ScanStep

    /** A Subroutine at its card: the house draws the question and opens the screen. */
    data class Open(val entry: OrderEntry, val kind: SubroutineKind) : ScanStep

    /**
     * A Memory Dump's source: the house draws a fresh pattern and shows it (`gdd.md:642`). Scanned
     * while that line's pattern is [held], the scan lets the held one go and the fresh one differs
     * from it (D-330).
     */
    data class ShowPattern(val entry: OrderEntry, val held: Carry.Pattern? = null) : ScanStep

    /** A Memory Dump's target, with the pattern held: the house opens the entry (`gdd.md:642`). */
    data class EnterPattern(val entry: OrderEntry, val pattern: Carry.Pattern) : ScanStep

    /** An Array Wipe's Spares, empty-handed: a good disk (`gdd.md:656`). */
    data class TakeDisk(val entry: OrderEntry) : ScanStep

    /** The Rack, a good disk in hand: swapped for a damaged one. */
    data class SwapDisk(val disk: Carry.Disk) : ScanStep

    /** Disposal, a damaged disk in hand: degaussed, and the circuit done at the last one. */
    data class DisposeDisk(val entry: OrderEntry, val disk: Carry.Disk) : ScanStep

    /**
     * **An Array Wipe station step: the scan opened nothing because the scan was the work** (D-113),
     * and the disk moved. Never NOTHING FOR YOU HERE, so it never says D-320's *no*.
     */
    val isStationStep: Boolean get() = this is TakeDisk || this is SwapDisk || this is DisposeDisk
}

/**
 * **The step a scan of [marker] is for [seat]** — the one decision, with no effect in it.
 *
 * ### A disk in hand answers first, and only at its station
 *
 * *While carrying a disk you cannot scan any other subroutine* (`gdd.md:664`): a good disk opens
 * only the Rack and a damaged one only Disposal, and every other card — a Subroutine's, a Memory
 * Dump's, Spares again — is NOTHING FOR YOU HERE. The meeting card and the Terminal are not routed
 * here at all (`routeScan`, D-121, D-199), so a disk in hand never stops a meeting being called.
 *
 * ### Then a pattern held, and only at its target or its own source (D-328, D-330)
 *
 * **While a Memory Dump pattern is held, every card but its target is refused, as a disk is** (the
 * owner's 2026-11-23 ruling, replacing D-322's *a held pattern does not block other scans*): the
 * target opens the entry and every other card is NOTHING FOR YOU HERE at the house (the phone draws
 * it as CARRYING . GO TO <ROOM>, off the order line it already holds). The meeting card and the
 * Terminal stay allowed, for the disk's reason.
 *
 * **One card besides the target answers: that line's own source, which restarts a forgotten
 * pattern** (the owner's 2026-11-23 ruling, D-330, replacing D-328's *the source again included*).
 * It is the source's step with the held pattern handed along, so the scan lets it go and the fresh
 * draw differs from it: the briefing, then BEGIN takes the new one as D-325's BEGIN always has. It
 * is the GDD's *failure: back to the source* without forcing a wrong entry. A card that is both (a
 * home of one card, which no review passes) is the target.
 *
 * ### Empty-handed, the first actionable entry with a step at this card wins
 *
 * In order, which is what makes the work order a menu walked one card at a time (D-114, D-123): a
 * Subroutine at its card, a Memory Dump at its **target if the pattern is held** and otherwise at
 * its source, an Array Wipe at Spares. A Memory Dump whose source and target are both this card
 * (a home of one card, which no review passes) is answered at the target while the pattern is held.
 *
 * `Role` is not read. An Insider's fake walks the same steps (D-109, rule 8).
 */
internal fun stepAt(state: GameState, seat: Seat, marker: MarkerId): ScanStep {
    val order = state.workOrderFor(seat) ?: return ScanStep.Nothing
    val disk = state.diskOf(seat)
    if (disk != null) {
        val entry = order.entries.getOrNull(disk.entry) ?: return ScanStep.Nothing
        val station = entry.markers.getOrNull(if (disk.damaged) DISPOSAL else RACK)
        return when {
            station?.value != marker.value -> ScanStep.Nothing
            disk.damaged -> ScanStep.DisposeDisk(entry, disk)
            else -> ScanStep.SwapDisk(disk)
        }
    }
    val pattern = state.patternHeldBy(seat)
    if (pattern != null) {
        val entry = order.entries.getOrNull(pattern.entry) ?: return ScanStep.Nothing
        return when (marker.value) {
            entry.markers.getOrNull(TARGET)?.value -> ScanStep.EnterPattern(entry, pattern)
            entry.markers.getOrNull(SOURCE)?.value -> ScanStep.ShowPattern(entry, held = pattern)
            else -> ScanStep.Nothing
        }
    }
    for (entry in order.entries) {
        if (!order.isActionable(entry)) continue
        val step = when (val work = entry.work) {
            is AssignedWork.Subroutine ->
                if (entry.anchor?.value == marker.value) ScanStep.Open(entry, work.kind) else null
            AssignedWork.MemoryDump -> {
                val held = state.patternOf(seat, entry.index)
                when {
                    held != null && entry.markers.getOrNull(TARGET)?.value == marker.value ->
                        ScanStep.EnterPattern(entry, held)
                    entry.markers.getOrNull(SOURCE)?.value == marker.value -> ScanStep.ShowPattern(entry)
                    else -> null
                }
            }
            AssignedWork.ArrayWipe ->
                if (entry.markers.getOrNull(SPARES)?.value == marker.value) ScanStep.TakeDisk(entry) else null
        }
        if (step != null) return step
    }
    return ScanStep.Nothing
}

/**
 * **A Memory Dump's pattern, drawn fresh at every scan of the source** (`gdd.md:642`, D-139's
 * re-draw). Off the scan's own minted id, as every instance is, so a replay shows exactly what it
 * showed. An element is one of Replay's four dots, each place once, four long (D-325) — see
 * [MemoryPattern].
 *
 * **Unlike the pattern still held, when there is one** (D-330: the source re-scanned restarts a
 * forgotten pattern, and *the new pattern must differ*). The same stream is shuffled again until it
 * does, so with nothing held this is the draw it always was, and with one held the first shuffle
 * that differs is taken: 23 of the 24 orders of four differ, so it nearly always ends at once.
 */
internal fun patternFor(seed: Long, id: EntityId, unlike: List<Int>? = null): List<Int> {
    val draw = Draw(seed, SeedPurpose.Instance, id.value)
    var pattern = List(MemoryPattern.LENGTH) { it }.shuffledBy(draw)
    while (pattern == unlike) pattern = pattern.shuffledBy(draw)
    return pattern
}

/**
 * **The target's open entry: the house's copy of the pattern is the answer** (`gdd.md:648`). The
 * question is only how many elements to take back; the answer never leaves the house again.
 */
internal fun patternEntry(seat: Seat, entry: OrderEntry, pattern: Carry.Pattern, marker: MarkerId): OpenSubroutine =
    OpenSubroutine(
        seat = seat,
        entry = entry.index,
        subroutine = SubroutineKind.MemoryDump,
        parameters = listOf(MemoryPattern.TARGET, pattern.elements.size),
        expected = pattern.elements,
        armedAt = marker,
    )

/**
 * **[seat]'s entry [entry] is done, and whatever another seat was waiting on it starts its delay**
 * (D-114, D-146, D-322).
 *
 * The one writer of completion for both paths that complete work — a graded hand-over and the
 * circuit's last disposal — so that neither can complete a line without starting the dependency
 * it holds. **Both roles** (D-109, rule 1): an Insider's fake completes, and unlocks, exactly as a
 * Resident's does; only the meter treats the two differently, and that is the caller's.
 */
internal fun GameState.completing(seat: Seat, entry: Int, at: Tick): GameState {
    val order = workOrderFor(seat) ?: return this
    return withWorkOrder(order.withCompleted(entry))
        .withLinks(links.map { if (it.isUp(seat, entry) && it.dueAt == null) it.startedAt(at) else it })
}

/** Whether the house may release [seat]'s [entry] at [at]: a link holds it, started, and due. */
internal fun GameState.chainDue(seat: Seat, entry: Int, at: Tick): Boolean {
    val held = workOrderFor(seat)?.entries?.getOrNull(entry)?.held == true
    return held && links.any { link -> link.isDown(seat, entry) && link.dueAt?.let { it.step <= at.step } == true }
}

/** The circuit's last disk: [Balance.ARRAY_WIPE_DISKS] degaussed. */
internal fun lastDisk(entry: OrderEntry): Boolean = entry.wiped + 1 >= Balance.ARRAY_WIPE_DISKS

private const val SOURCE = 0
private const val TARGET = 1
private const val SPARES = 0
private const val RACK = 1
private const val DISPOSAL = 2
