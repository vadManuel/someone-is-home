package home.someoneshome.model

/**
 * What the rules emit. Authority-side, and deliberately **not** `@Serializable`.
 *
 * Nothing here goes on the wire as-is. Effects are redacted per client at the emit boundary,
 * by constructing a narrower client-facing type — never by nulling fields on this one. A nulled
 * field still exists, and someone makes it non-null later for an unrelated reason.
 *
 * The per-client transcript recorder and the schema allowlist that police that boundary are
 * stories 0.5 and 0.6b. Until they exist, this type simply must not acquire a wire encoding,
 * which `redactionLint` enforces: adding `@Serializable` here fails the build.
 */
sealed interface Effect {
    /**
     * Light is game state. No local animation, tween or easing the rules did not emit — a fade
     * nobody authored is a signal nobody authored.
     */
    data class LampSet(val seat: Seat, val luminance: Int) : Effect

    /**
     * Emitted whether or not the ability changed anything.
     *
     * This is the shape rule 1 demands: firing at an already-revoked target must produce the
     * same effect, with the same cooldown, as firing at a live one. An absent effect is a
     * revoke-detector.
     */
    data class AbilityFired(val actor: Seat, val cooldownStarted: Boolean) : Effect

    /**
     * **The house's answer to a returned entry — the same words on the same schedule for both
     * roles (D-109).**
     *
     * Two fields, and that is deliberately the whole of it. There is no `remaining`, no *which
     * elements were wrong*, no attempt count and no reason: a mismatch reports only that it was a
     * mismatch (`gdd.md:608`), and every extra field would be one more thing that could be
     * generated differently for a fake.
     *
     * ### This is rule 1's exact shape, and it is the reason the effect exists
     *
     * An Insider's Subroutines are fakes: real UI, real progress, real completion, **writing
     * nothing**. The tempting implementation is to say nothing back about a fake, or to roll a
     * plausible failure distribution for one. Both are role oracles — the first after one
     * completion, the second after enough of them — and D-109 refuses both: *run the real rule,
     * emit the real shape.* **The Insider's verdict is emitted, never omitted.**
     *
     * The single asymmetry lives where no player can stand: an Insider's [accepted] verdict does
     * not move `SystemIntegrity`. Nothing here carries that, and nothing on any screen can read it.
     */
    data class SubroutineGraded(val seat: Seat, val accepted: Boolean) : Effect

    data class SubroutineProgressed(val remaining: Int) : Effect
    data class MessageDelivered(val seat: Seat, val body: String) : Effect

    // ---- The meeting -----------------------------------------------------------------------
    //
    // Twelve kinds for one lifecycle looks like a lot until you try to merge two of them. Every
    // split below is a permission set that differs -- EmitSchema's rule is that a kind is
    // permitted to a class in full or not at all, so **if two classes need different content,
    // that is two kinds**, each with its own row and its own reason. The ring and the walk-in,
    // the count and the selections, the result and the attribution: each pair is one fact told
    // to two audiences who may be told different amounts of it, and merging any pair would mean
    // redacting by nulling fields under another name.

    /**
     * **The ring** (D-121, D-134). Goes to the living; a player who is out gets [StandAndWalkIn].
     *
     * Carries who called it, because the caller is public by construction: they walked to the
     * meeting card, which is the whole reason D-121 refused a remote button — *the difference
     * between calling a meeting and being the person who called it.*
     */
    data class MeetingOpened(
        val caller: Seat,
        val trigger: MeetingTrigger,
        val haptic: Haptic,
    ) : Effect

    /**
     * **What a player outside the system gets instead of a call** (D-134).
     *
     * *The ringing call is for the living.* A newly Revoked player is told to stand and walk in,
     * with the long haptic D-135 reserves for it; the couch — previously Revoked and Restrained
     * players — is called in the same way with an ordinary buzz, because D-104's gate does not
     * close until they are standing there too.
     *
     * **The haptic differs by round-state and never by role.** Round-state is publicly observable
     * (D-068) and D-135 names this buzz specifically; a long buzz that meant *this player is an
     * Insider* would be the audible tell D-102 exists to prevent, and nothing here can carry that.
     */
    data class StandAndWalkIn(val seat: Seat, val haptic: Haptic) : Effect

    /**
     * **The gate, as a count** (D-104). Living and out alike, because they are one gate.
     *
     * A count and a total, never a list of who. *The ledger reports anonymous counts and nothing
     * finer* (`gdd.md:294`): an app that confirms who was standing where and when is an app
     * adjudicating alibis, which is considerably more than this design wants.
     */
    data class CheckInProgressed(val present: Int, val expected: Int) : Effect

    /**
     * The meeting moved on. One kind for every phase, and that is deliberate.
     *
     * The phases are not different disclosures — *the talk has started*, *the ballot is open*,
     * *the result is in* are each known to the whole room at the moment they happen, and all
     * three reach exactly the same audience. Splitting them would be three rows in the allowlist
     * that could only ever be edited together.
     */
    data class MeetingPhaseOpened(val phase: MeetingPhase, val haptic: Haptic) : Effect

    /** Hands up for READY TO VOTE, counted. Named for the same reason as [CheckInProgressed]. */
    data class ReadyProgressed(val ready: Int, val expected: Int) : Effect

    /**
     * **The house telling one seat what its ballot actually says** (D-117, rule 1).
     *
     * Emitted on **every** selection tap and every READY from that seat, whatever the state of the
     * ballot. That is the whole point: after READY the vote cannot be changed, and a later tap is
     * **refused, not dropped** — so the house re-asserts, and what comes back is the locked
     * selection rather than the one the finger just landed on.
     *
     * Written the other way — say nothing to a seat whose tap changed nothing — the absence is
     * the message, and the player is left looking at a lit row the house has never heard of. The
     * shape is identical in both cases; only [selection] and [locked] differ, which is state.
     *
     * Addressed to the one seat. It is that player's own ballot and nobody else's business: the
     * living see a count and never a selection (D-117).
     */
    data class VoteHeld(val seat: Seat, val selection: Seat?, val locked: Boolean) : Effect

    /**
     * **A live selection, for the couch** (D-117, D-134).
     *
     * *Ghosts are the only readers who see selections rather than a count.* This is what a player
     * outside the system watches, and it is why being out is an information privilege rather than
     * a preview. It must never reach a living class in either role — the living get
     * [VoteProgressed], which is a number.
     */
    data class VoteSelectionShown(val voter: Seat, val selection: Seat?) : Effect

    /**
     * **`N OF 6 VOTED` — locked seats, not selections** (D-117, E8-1's first question).
     *
     * The living see the count and never the selections. Counting selections instead would leak
     * the shape of the room's thinking in real time to the people still in it.
     */
    data class VoteProgressed(val locked: Int, val expected: Int) : Effect

    /**
     * **The result, to everyone.** Most votes is Restrained; ties resolve to Skip
     * (`gdd.md:413`, `:1007`, D-075).
     *
     * Null means the room Restrained nobody. **No role, no confirmation, no reveal** — *a correct
     * restraint and a catastrophic one look identical* (`gdd.md:1007`), and nothing here has a
     * field that could tell them apart.
     */
    data class MeetingResult(val restrained: Seat?, val haptic: Haptic) : Effect

    /**
     * The ballot with names against it. **The out, and nobody else** (D-075).
     *
     * The living get [MeetingResult], which carries the outcome and no attribution. This carries
     * who cast what, which only a player outside the system ever learns — and it is safe only
     * because of *when*: the room already knows who is out, so there is never a window in which
     * someone outside knows something the living do not.
     */
    data class MeetingResolved(val restrained: Seat?, val attribution: List<Pair<Seat, Seat?>>) : Effect

    /**
     * **The takeover, at the halfway mark, to the losing seat only** (D-102, D-134's E1-1).
     *
     * A house push per seat, not a client-scheduled screen change. The rejected alternative is a
     * table in `ui` that could say *"…and if it was you, this other screen"*, which is the device
     * deciding a game answer — and the answer it would be deciding is the one the whole meeting
     * was about.
     *
     * Halfway rather than at zero *so they do not walk away when the countdown ends*, and the
     * seat leaves the round at this moment rather than at the buzzer: the group holds them, and
     * the house deauthorises them moments later (`gdd.md:1009`).
     */
    data class RestrainedTakeover(val seat: Seat, val haptic: Haptic) : Effect

    /** Lights out. The meeting is over, and D-135's fifth long haptic rides it. */
    data class MeetingEnded(val haptic: Haptic) : Effect
}
