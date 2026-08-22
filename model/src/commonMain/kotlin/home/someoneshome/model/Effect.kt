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

    /**
     * **A seat's work order, as that seat may see it** (D-114, D-129, D-112).
     *
     * Carries [OrderLine]s and never [OrderEntry]s: the answer key, the anchors and the dependency
     * graph all stay on the authority side, and a blocked line is a different *type* rather than
     * the same type with its name blanked (rule 3).
     *
     * ### Emitted on every return, not only on the ones that changed something
     *
     * Completing an entry unblocks whatever was waiting behind it, so the order has to be re-sent
     * — but sending it only when something changed would make its **presence** the verdict, one
     * effect after [SubroutineGraded] has already delivered the real one. Two answers to the same
     * question is one answer too many, and the second would be the one a client could read without
     * being told. So it goes out on every return, constructed once outside the branch, exactly as
     * the cooldown is in the revoke path.
     *
     * ### Every order is the same length and that is load-bearing
     *
     * [Balance.orderSize] is computed from public lobby facts alone — seats and the host's visible
     * setting — so an Insider's fake order is exactly as long as everybody else's (D-129). Length
     * is not a channel, in either direction.
     */
    data class WorkOrderIssued(val seat: Seat, val lines: List<OrderLine>) : Effect

    /**
     * **The house's answer to a scan — and it is the same answer whether or not there was work
     * there** (D-124, D-110, rule 1).
     *
     * [opened] carries the instance the scan drew, or is null for **NOTHING FOR YOU HERE**.
     *
     * ### The null is the ruling, not a saving
     *
     * The tempting shapes are two: emit nothing when a card holds no work for this seat, or split
     * the answer into two kinds. Both are the same bug in different clothes.
     *
     * *Emit nothing* is rule 1's forbidden shape at its purest. A scan that opened work would
     * produce a message and a scan that did not would produce silence, so the **absence** of the
     * effect is the answer — and the player's phone is left holding a question the house has never
     * heard of, indistinguishable from a dead radio.
     *
     * *Two kinds* is the same leak one layer out. Two kinds mean two rows in the allowlist, two
     * shapes on the wire, and two different things for anything downstream to count — so a card
     * that holds nothing for you becomes separable from one that does by a reader who never sees
     * either payload. **One kind, one row, one message per scan**, and only the content differs.
     * `VoteHeld` carries a nullable selection for the same reason and it is the same decision.
     *
     * ### D-124's two vocabularies are not both here
     *
     * A **registered** card the house has nothing for you at is this effect with a null, and it is
     * deliberately unremarkable. **Unregistered paper never reaches the rules at all** — it is
     * refused at the scan's routing, reported to nobody (D-072), and answered by the client's own
     * alert. Collapsing the two would build the detector D-124 refuses: a Resident sweeping cards
     * could separate *registered but not mine* from *not registered at all*.
     *
     * **Addressed to the scanning seat and to nobody else.** Broadcast, it would publish who is
     * standing where and finding work, which is the whole read the meter is quantised into a
     * percentage to prevent, arriving one player at a time.
     */
    data class ScanAnswered(val seat: Seat, val opened: SubroutineInstance?) : Effect

    /**
     * **The presence plane's whole output: a window opened, or a window closed** (D-111, D-136).
     *
     * See [Presence]. The house **records, never recites** — this effect has no row in the emit
     * schema, so it ships to nobody, and it is addressed to the performing seat alone so that it
     * could not reach another player even if somebody wrote one.
     *
     * ### It carries no reason, and adding one would rebuild the leak D-111 closed
     *
     * A window closes when the entry is handed over, when the player presses STOP NOW, and when
     * they step away from the marker. **Those three are one fact here and must stay one fact.**
     * A `reason` field is an abandonment record, and an abandonment record is the behavioural
     * channel that separates a real Subroutine from a fake — somebody who walks away from work
     * that was never going to count, at a rate nobody designed and nobody can see.
     */
    data class PresenceChanged(val seat: Seat, val at: MarkerId?, val open: Boolean) : Effect

    /**
     * **The house's opening text — one of exactly two events that dim the house** (D-118).
     *
     * The other is the Egress. Every other notification is quiet: no dim, no brightness spike,
     * nothing world-observable at all. A separate kind from [MessageDelivered] rather than a flag
     * on it, and the reason is the one A2 gave for splitting the presentation: **a second dim on
     * the second text would spend the first one's meaning**, and a flag is a thing somebody can
     * pass `true` for by accident.
     *
     * **It carries no words**, for [WorkOrderIssued]'s reason and D-112's: the client holds the
     * copy, the house sends the fact. The words are identical for both roles anyway — what differs
     * by role is what is *behind* the message, on a thread that has to be opened to read — so
     * putting them on the wire would buy a drift risk and nothing else.
     *
     * **It reaches every seat.** The dim is world-observable, so a notification addressed to fewer
     * than everyone is a beacon (D-076).
     *
     * **[haptic] is Short, and that is a ruling rather than a default.** D-135 closes the long
     * haptic to five events — the Egress, an incoming phone call, STAND AND WALK IN, the Restrained
     * takeover, and the end of the LIGHTS OUT IN *n* countdown — and this is not one of them. In a
     * silent house a long buzz is world-observable through a pocket, and a signal that means five
     * specific things stops meaning them the moment a sixth is added.
     */
    data class OpeningMessage(val seat: Seat, val haptic: Haptic) : Effect

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

    // ---- The Egress ---------------------------------------------------------------------------
    //
    // Four kinds, and the audiences are the reason there are four rather than two. The house
    // catching fire, the timer stopping, the containment landing and the containment failing are
    // each one fact, but one of them -- SyncPulseAnswered -- is addressed to a single seat and
    // carries what that seat's own beat did, while the other three reach the whole building. A
    // kind is permitted to a class in full or not at all (EmitSchema's rule), so a merge would be
    // redaction by nulling fields under another name.

    /**
     * **The house is on fire, and this is where everyone finds out** (`gdd.md:349`, D-118, D-076).
     *
     * The heavy notification and the widget takeover both ride this one effect, which is what makes
     * *the alert and the widget cannot name different rooms* a property of the wire rather than a
     * discipline two screens have to keep. Two copies of that pair would send two people who may
     * not speak to two different places, and the mistake would look like a typo in a diff.
     *
     * **It reaches every seat, living and out.** This is one of exactly two events that **dim the
     * house** (D-118) — the other is [OpeningMessage] — and a dimming lamp is world-observable, so
     * a notification addressed to fewer than everyone is a beacon (D-076). It is also what a player
     * outside the system watches: their class already carries the liveness privilege that lets them
     * see the real Egress number while it runs (`gdd.md:1014`).
     *
     * **[haptic] is Long, and it is D-135's first.** *The Egress* is named at the head of that
     * closed set of five, and this is the effect it names.
     *
     * **The actor gets exactly this and nothing more** (`gdd.md:396`). No actor-side feedback on
     * firing any ability: the Insider who pressed the button receives the same dim, the same buzz
     * and the same two room names as everybody else. Their own phone knows what it pressed and what
     * its cooldown now says, and that is input echo rather than a game answer.
     */
    data class EgressOpened(
        val seat: Seat,
        val type: EgressType,
        /** Both nodes, in draw order. Named to everyone, because coordination is required and
         *  nobody may speak (`gdd.md:363`). */
        val nodes: List<MarkerId>,
        /** Ticks on the clock at the moment it started. The device holds no opinion of its own. */
        val remaining: Long,
        val haptic: Haptic,
    ) : Effect

    /**
     * **The countdown stopped, or started again** (D-133).
     *
     * A meeting called by reporting a Revoked player is the one meeting that can happen during an
     * Egress, and it **pauses** the timer — never resets it. Both halves are this one kind, because
     * they are one fact told to one audience: *the number on the widget is moving, or it is not.*
     * A separate kind for each would be two rows in the allowlist that could only ever be edited
     * together.
     *
     * **[running] is state, not a redaction flag.** It says which of the two happened, both
     * audiences get the same value, and there is no narrowing hiding inside it — [PresenceChanged]
     * carries `open` for the same reason and it is the same decision.
     *
     * **Everyone, living and out.** The whole party is standing in one room when it fires: a pause
     * that reached fewer than everybody would leave somebody's widget counting down through a
     * meeting the rest of the house had stopped for.
     */
    data class EgressHeld(val remaining: Long, val running: Boolean, val haptic: Haptic) : Effect

    /**
     * **The house's answer to one participant's beat — and it is the same answer either way**
     * (`gdd.md:355`, rule 1).
     *
     * [held] says whether the house is now holding this seat's beat, waiting for somebody at the
     * other node. It is `false` for a beat that missed the schedule, for a seat serving a lockout,
     * for a seat that is not standing at a node, and for a seat outside the system — **one shape,
     * one kind, one message per return**, with only the value differing.
     *
     * ### Written the tempting way it is a node-detector
     *
     * *Emit nothing unless the beat was good* leaves the absence as the answer, and the absence is
     * readable: a Insider standing anywhere in the house could tap, watch for silence, and learn
     * that the house does not think they are at a node — which is a claim about the presence plane,
     * delivered to a living phone, that D-111 split the two planes to prevent. `ScanAnswered`'s
     * null and `VoteHeld`'s selection are the same decision one system over.
     *
     * ### It says nothing about anybody else
     *
     * Not who is at the other node, not how many are waiting, not whether a partner is close.
     * Containment is a fact about the house and arrives as [EgressContained] to everybody at once;
     * anything finer here would be a live report of who is standing where, addressed to a living
     * player, which is the leak the whole presence plane is denied a schema row to close.
     */
    data class SyncPulseAnswered(val seat: Seat, val held: Boolean) : Effect

    /**
     * **Contained. The widget reverts and nobody learns anything about anybody** (`gdd.md:987`).
     *
     * **It carries no seats and no node, and that is the whole design of it.** Naming the pair that
     * contained it would publish two players who were demonstrably standing at known places at a
     * known moment — an alibi, minted by the app, at the one moment in the round when everybody is
     * moving and nobody can speak. Naming the node would publish where they were. The house says
     * *it stopped*, and the room works the rest out or does not.
     *
     * Everyone, living and out: the widget goes back to being System Integrity on every phone in
     * the building, and a reversion that reached fewer than everybody would leave somebody
     * counting down toward a loss that is not coming.
     */
    data class EgressContained(val haptic: Haptic) : Effect

    /**
     * **Uncontained. The terminal fact: the Insiders win outright** (`gdd.md:361`, D-131).
     *
     * *A running Egress outlives its Insiders and must still be stopped* — so this can arrive after
     * the room has Restrained every Insider it had, and it still ends the round in their favour.
     *
     * **It ends nothing by itself, deliberately, and that is stated rather than hidden.** This
     * effect and its event are the *fact*; the win conditions as a set, `GameState.ended`, and the
     * screens that follow are the ending unit's, and building half of them here would put the
     * round's most consequential transition in two places. What is guaranteed today is that the
     * fact is emitted, recorded, and replays.
     */
    data class EgressSucceeded(val haptic: Haptic) : Effect
}
