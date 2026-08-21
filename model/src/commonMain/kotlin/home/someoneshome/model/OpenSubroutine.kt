package home.someoneshome.model

/**
 * **The one Subroutine a seat has open, and the entry the house will grade it against (D-109).**
 *
 * Authority state of the strongest kind: it holds *the answer*, which is the whole of what the
 * house knows and the phone does not. There is no client-facing narrowing of this type and there
 * must never be one — the question is already on the player's screen, and the only thing left to
 * send is the verdict, which is [Effect.SubroutineGraded] and carries a boolean.
 *
 * Not a `data class` and never `@Serializable`: a generated `copy()` is public, and the two
 * transitions below are the only ones the rules are allowed to make.
 *
 * ### [armedAt] is the whole of D-110
 *
 * *One attempt per scan, and the walk back is the cost.* A handed-over entry is **spent** — this
 * goes back to `armedAt = null` whatever the entry said — and it re-arms only when the player
 * scans the marker again. The house never re-arms it on a schedule and the phone never re-arms it
 * at all: a screen that becomes ready again on its own is the phone forming an opinion about an
 * answer.
 *
 * The marker is stored rather than merely a flag because a scan is a *place*: the entry the house
 * grades is the one handed over at the card it was armed at (D-123 — the house resolves
 * `(seat, card)` to that player's current Subroutine).
 *
 * ### What is spine and what is L3's
 *
 * **[expected] is drawn at arming by a placeholder and one Subroutine stays open all round.** The
 * real work order — D-129's `K` per Resident, the dependency graph, D-114's blocked entries, and a
 * fresh instance drawn on every re-scan — is L3's, and every one of those replaces something here.
 * What is *not* provisional is the shape: a seat has one open Subroutine, it is armed by a scan,
 * and it is spent by an entry.
 */
class OpenSubroutine(
    val seat: Seat,
    /**
     * What the house asked for, as the ordered integers the player's own screen deals in — an
     * element sequence, a chosen cell, a finger count, a signed offset, a walked route.
     *
     * One canonical shape for all six built Subroutines rather than a shape per Subroutine,
     * because grading is one comparison and the *kind* of work is the client's presentation
     * problem. A wire type per Subroutine would be six chances to write a seventh that leaks.
     */
    val expected: List<Int>,
    /** The marker this instance was armed at, or null while it is spent. D-110. */
    val armedAt: MarkerId?,
) {
    val armed: Boolean get() = armedAt != null

    /** A scan of [marker]. D-110's only way back to ready. */
    fun armedAt(marker: MarkerId): OpenSubroutine = OpenSubroutine(seat, expected, marker)

    /** The entry has gone. Spent whatever it said, and whoever sent it. */
    fun spent(): OpenSubroutine = OpenSubroutine(seat, expected, null)

    /**
     * True when [entered], handed over at [marker], is the entry this Subroutine asked for.
     *
     * **Three conditions, and no branch on who is asking.** Armed by a scan, handed over at the
     * card it was armed at, and equal to [expected]. `Role` does not appear in this file: D-109
     * grades every entry for real, for both roles, by the same rule.
     */
    fun accepts(marker: MarkerId, entered: List<Int>): Boolean =
        armedAt?.value == marker.value && expected == entered
}
