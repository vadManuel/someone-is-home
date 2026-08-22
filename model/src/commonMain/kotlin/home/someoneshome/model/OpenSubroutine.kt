package home.someoneshome.model

/**
 * **The one Subroutine a seat has open, and the entry the house will grade it against (D-109).**
 *
 * Authority state of the strongest kind: it holds *the answer*, which is the whole of what the
 * house knows and the phone does not. The client-facing half of this type is
 * [SubroutineInstance] below, which carries the question and is physically incapable of carrying
 * the answer — the narrowing is the two types, never a field left null (rule 3).
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
 * ### It is one line of a work order, and [entry] is what says which
 *
 * The spine drew one entry per seat and left it open all round, so the same assignment could be
 * completed over and over and the meter was farmable without moving. It is now **a live instance
 * of one [OrderEntry]**, opened by a scan of the card that entry is anchored at (D-123), and
 * spending it marks that entry done — which is what advances the order and what unblocks whatever
 * was waiting behind it (D-114).
 *
 * ### The instance is drawn by the SCAN, and re-drawn by every re-scan (D-139, D-140)
 *
 * [parameters] and [expected] arrive together, at scan time, and neither survives a re-scan.
 * That is E-L3-2's ruling made real: *the house sends the parameters — band position, speed,
 * phase, path, the hidden duration — when the scan opens the Subroutine, the client renders
 * deterministically from them, the tap is the entry, the house grades it.* A retry under D-110 is
 * therefore **a fresh judgment rather than a second run at a picture the player has memorised**,
 * which is the whole reason the re-draw exists and the reason the answer could not stay on the
 * order entry where arming left it.
 *
 * What was never provisional is the shape: a seat has one open Subroutine, a scan arms it, an
 * entry spends it.
 */
class OpenSubroutine(
    val seat: Seat,
    /**
     * Which line of [WorkOrder] this instance came from.
     *
     * Carried rather than looked up by comparing [expected], because two entries in one order may
     * legitimately ask for the same thing — and a completion filed against the wrong line would
     * unblock the wrong work while leaving the right line open for a second bank.
     */
    val entry: Int,
    /**
     * **The question, as the client is allowed to see it** — what [SubroutineInstance] carries.
     *
     * Held here as well so the state row records what was asked, not merely what would be
     * accepted. A recording that stored the answer and not the question could not tell a round
     * where the house asked something different from one where it graded differently.
     */
    val parameters: List<Int>,
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

    /** The entry has gone. Spent whatever it said, and whoever sent it. */
    fun spent(): OpenSubroutine = OpenSubroutine(seat, entry, parameters, expected, null)

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

/**
 * **A Subroutine as the player standing at the marker may see it** — the question, never the
 * answer (D-139, D-140, rule 3).
 *
 * The narrowing is the type. [OpenSubroutine] holds `expected` and this cannot: there is no field
 * here that a later change could populate with the answer, and no comment standing where a
 * boundary should be.
 *
 * ### [parameters] is one canonical shape for every Subroutine, and that is deliberate
 *
 * D-139 sends band position, speed and phase; D-140 sends path, occluder layout and the hidden
 * duration. Those are **six numbers or two, depending on which Subroutine is talking**, and the
 * temptation is a wire type per kind. [OpenSubroutine.expected] refused that for the answer and
 * this refuses it for the question, for the same reason: a shape per Subroutine is ten chances to
 * write an eleventh that leaks, and *which* Subroutine is the client's presentation problem — it
 * holds the roster (D-112) and reads its own parameters out of this list.
 *
 * **It carries no marker.** Where the work is, is what walking the house in the dark is for, and
 * the player scanning already has the card in their hand.
 */
@ClientFacing
data class SubroutineInstance(
    /** Which line of the work order opened, so the client can strike it off its own list. */
    val entry: Int,
    val subroutine: SubroutineKind,
    val parameters: List<Int>,
)
