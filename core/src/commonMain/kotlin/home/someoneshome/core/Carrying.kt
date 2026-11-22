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

    /** D-124's NOTHING FOR YOU HERE — and, while a disk is in hand, every card but its station. */
    data object Nothing : ScanStep

    /** A Subroutine at its card: the house draws the question and opens the screen. */
    data class Open(val entry: OrderEntry, val kind: SubroutineKind) : ScanStep

    /** A Memory Dump's source: the house draws a fresh pattern and shows it (`gdd.md:642`). */
    data class ShowPattern(val entry: OrderEntry) : ScanStep

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
 * re-draw). Off the scan's own minted id, as every instance is, so a re-scan cannot show what the
 * last one did and a replay shows exactly what it showed. ⚠️ The element is a placeholder — see
 * [MemoryPattern].
 */
internal fun patternFor(seed: Long, id: EntityId): List<Int> {
    val draw = Draw(seed, SeedPurpose.Instance, id.value)
    val length = MemoryPattern.LEAST + draw.next(MemoryPattern.MOST - MemoryPattern.LEAST + 1)
    return List(length) { it }.shuffledBy(draw)
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
