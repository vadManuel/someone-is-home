package home.someoneshome.model

import kotlin.jvm.JvmInline

/**
 * The stable name of a message shape on the wire.
 *
 * A String rather than an enum, because the allowlist has to be able to *not contain* a kind. An
 * enum-keyed map with an exhaustive `when` cannot express "this message has no entry", which is
 * precisely the state that must ship to nobody.
 */
@JvmInline
value class MessageKind(val name: String) {
    override fun toString(): String = name
}

/**
 * One delivery the emit boundary permitted: this seat, in this class, may receive this kind.
 *
 * Never carries the payload. The payload is whatever the transport renders for that kind, and the
 * rule below is what keeps that safe.
 */
data class Delivery(val seat: Seat, val clientClass: ClientClass, val kind: MessageKind)

/**
 * **Story 0.6b — the per-message schema allowlist at the emit boundary.**
 *
 * An explicit statement of what each client class may receive, keyed on [MessageKind], answering
 * *"was this ever permitted"* rather than *"did this differ"*. The differential harness of story
 * 0.6 is structurally blind to a leak that reaches everyone symmetrically — both its runs come
 * out of the same redaction code — so this table is independently required, not a second opinion.
 *
 * ### It fails closed, and that is the whole design
 *
 * [permits] reads `ALLOWED[kind]` and treats a missing entry as the empty set. A new [Effect]
 * whose kind was never added here therefore **ships to nobody**. The failure mode is *"my thing
 * didn't appear"*, noticed in thirty seconds; never *"my thing appeared to everyone"*, noticed
 * never. Nothing in this file may ever acquire a default branch, an `orEmpty()`-style permit, or
 * a wildcard class.
 *
 * ### One kind, one payload, one permission set
 *
 * A kind is permitted to a class in full or not at all — there is no per-class narrowing of a
 * single kind, because that would be redaction by nulling fields under another name. **If two
 * classes need different content, that is two kinds**, each with its own row here and its own
 * narrower client-facing type. That is what makes an entry in this table a decision somebody made
 * rather than a shape somebody serialised.
 *
 * ### Keyed on the class, which has two axes
 *
 * [ClientClass] is role *and* round-state. Two rows below would be wrong keyed on role alone —
 * [Effect.SubroutineProgressed] and [Effect.MeetingResolved] both go to players who are out and to
 * no living player of either role. Keyed on role, the entry that serves an out Resident serves a
 * living Resident too.
 */
object EmitSchema {

    /**
     * The name of every effect shape. Exhaustive over the sealed type **on purpose**: a new
     * [Effect] does not compile until someone names it.
     *
     * That compile error is not the fail-closed guard — it is the prompt. The guard is that naming
     * a kind here grants it nothing, and the person mechanically fixing compile errors after
     * adding an effect will not be stopped from forgetting [ALLOWED].
     */
    fun kindOf(effect: Effect): MessageKind = when (effect) {
        is Effect.LampSet -> LAMP_SET
        is Effect.AbilityFired -> ABILITY_FIRED
        is Effect.SubroutineGraded -> SUBROUTINE_GRADED
        is Effect.SubroutineProgressed -> SUBROUTINE_PROGRESSED
        is Effect.MessageDelivered -> MESSAGE_DELIVERED
        is Effect.WorkOrderIssued -> WORK_ORDER_ISSUED
        is Effect.ScanAnswered -> SCAN_ANSWERED
        is Effect.PresenceChanged -> PRESENCE_CHANGED
        is Effect.OpeningMessage -> OPENING_MESSAGE
        is Effect.MeetingOpened -> MEETING_OPENED
        is Effect.StandAndWalkIn -> STAND_AND_WALK_IN
        is Effect.CheckInProgressed -> CHECK_IN_PROGRESSED
        is Effect.MeetingPhaseOpened -> MEETING_PHASE_OPENED
        is Effect.ReadyProgressed -> READY_PROGRESSED
        is Effect.VoteHeld -> VOTE_HELD
        is Effect.VoteSelectionShown -> VOTE_SELECTION_SHOWN
        is Effect.VoteProgressed -> VOTE_PROGRESSED
        is Effect.MeetingResult -> MEETING_RESULT
        is Effect.MeetingResolved -> MEETING_RESOLVED
        is Effect.RestrainedTakeover -> RESTRAINED_TAKEOVER
        is Effect.MeetingEnded -> MEETING_ENDED
        is Effect.EgressOpened -> EGRESS_OPENED
        is Effect.EgressHeld -> EGRESS_HELD
        is Effect.SyncPulseAnswered -> SYNC_PULSE_ANSWERED
        is Effect.EgressContained -> EGRESS_CONTAINED
        is Effect.EgressSucceeded -> EGRESS_SUCCEEDED
        is Effect.RoundEnded -> ROUND_ENDED
        is Effect.InsidersRevealed -> INSIDERS_REVEALED
        is Effect.HouseSignedOff -> HOUSE_SIGNED_OFF
    }

    val LAMP_SET = MessageKind("LampSet")
    val ABILITY_FIRED = MessageKind("AbilityFired")
    val SUBROUTINE_GRADED = MessageKind("SubroutineGraded")
    val SUBROUTINE_PROGRESSED = MessageKind("SubroutineProgressed")
    val MESSAGE_DELIVERED = MessageKind("MessageDelivered")
    val WORK_ORDER_ISSUED = MessageKind("WorkOrderIssued")
    val SCAN_ANSWERED = MessageKind("ScanAnswered")

    /**
     * **Named here and permitted nowhere. That is the whole of the presence plane's boundary.**
     *
     * [kindOf] must name it — the `when` is exhaustive — and naming it grants it nothing, which is
     * exactly the distinction [ALLOWED] is built on. See the comment where its row would be.
     */
    val PRESENCE_CHANGED = MessageKind("PresenceChanged")
    val OPENING_MESSAGE = MessageKind("OpeningMessage")
    val MEETING_OPENED = MessageKind("MeetingOpened")
    val STAND_AND_WALK_IN = MessageKind("StandAndWalkIn")
    val CHECK_IN_PROGRESSED = MessageKind("CheckInProgressed")
    val MEETING_PHASE_OPENED = MessageKind("MeetingPhaseOpened")
    val READY_PROGRESSED = MessageKind("ReadyProgressed")
    val VOTE_HELD = MessageKind("VoteHeld")
    val VOTE_SELECTION_SHOWN = MessageKind("VoteSelectionShown")
    val VOTE_PROGRESSED = MessageKind("VoteProgressed")
    val MEETING_RESULT = MessageKind("MeetingResult")
    val MEETING_RESOLVED = MessageKind("MeetingResolved")
    val RESTRAINED_TAKEOVER = MessageKind("RestrainedTakeover")
    val MEETING_ENDED = MessageKind("MeetingEnded")
    val EGRESS_OPENED = MessageKind("EgressOpened")
    val EGRESS_HELD = MessageKind("EgressHeld")
    val SYNC_PULSE_ANSWERED = MessageKind("SyncPulseAnswered")
    val EGRESS_CONTAINED = MessageKind("EgressContained")
    val EGRESS_SUCCEEDED = MessageKind("EgressSucceeded")
    val ROUND_ENDED = MessageKind("RoundEnded")
    val INSIDERS_REVEALED = MessageKind("InsidersRevealed")
    val HOUSE_SIGNED_OFF = MessageKind("HouseSignedOff")

    private val LIVING = setOf(
        ClientClass(Role.Resident, RoundState.Live),
        ClientClass(Role.Insider, RoundState.Live),
    )

    /** Revoked or restrained: in the building, outside the system. */
    private val OUTSIDE = setOf(
        ClientClass(Role.Resident, RoundState.Out),
        ClientClass(Role.Insider, RoundState.Out),
    )

    private val AFTER = setOf(
        ClientClass(Role.Resident, RoundState.Ended),
        ClientClass(Role.Insider, RoundState.Ended),
    )

    /**
     * **The list.** Every row is a decision with a citation; a row with no reason is a row that
     * should not be here.
     *
     * The pre-arm classes appear in no row at all. D-067: nothing in-game runs in the lobby, and
     * D-066 refuses pre-arm events above the rules — so a pre-arm client receiving anything is
     * already a bug by the time this table is consulted, and this table declines it a second time.
     */
    private val ALLOWED: Map<MessageKind, Set<ClientClass>> = mapOf(

        // The lamp is a pure function of state and the device never abandons a player: a revoked
        // player's screen is authored (D-077), and so is the disarmed perimeter after the round.
        LAMP_SET to (LIVING + OUTSIDE + AFTER),

        // Only an Insider has an ability that fires. Rule 1 makes the effect identical whether the
        // revoke landed or not, which is exactly why it must not be widened: shipped to a
        // Resident it would be an unexplained event on a screen with no ability behind it.
        ABILITY_FIRED to setOf(ClientClass(Role.Insider, RoundState.Live)),

        // D-109: the house grades every entry for real, for both roles, in identical words. So
        // this row names BOTH living classes and it is the row that must never be narrowed to one
        // of them -- an Insider whose fake never came back would be a role oracle after a single
        // Subroutine, delivered by the allowlist rather than by a screen. Rule 1's exact shape.
        //
        // Not the out classes. A player outside the system has no Subroutine open and never
        // returns an entry, so a verdict could only reach them addressed to a seat that is out --
        // which happens when somebody is revoked between their scan and their entry. The verdict
        // is then denied, and that denial carries no role information: it is the round-state axis
        // moving, and round-state is publicly observable (D-068). Their screen is D-134's, not a
        // Subroutine's.
        SUBROUTINE_GRADED to LIVING,

        // The live true remaining count. gdd.md:192 — the number is shown to living players ONLY
        // AT MEETINGS, batched and then frozen (gdd.md:1002). Continuous decrements are a rate
        // signal nobody living is entitled to. A player outside the system sees the real bars live
        // (gdd.md:1014), and that privilege is what being out is made of.
        //
        // The meeting-time batched number is a different message with a different shape and does
        // not exist yet, so living players currently receive no progress at all. That is the
        // fail-closed direction and the correct one to be wrong in.
        SUBROUTINE_PROGRESSED to OUTSIDE,

        // A banner buzzes and dims every panel in the house, and a dimming lamp is world-
        // observable, so a notification addressed to fewer than everyone is a beacon (D-076).
        MESSAGE_DELIVERED to (LIVING + OUTSIDE),

        // **Both living classes, and it must never be narrowed to one of them.** An Insider's fake
        // order is drawn by the same rule, at the same length (D-129), so the row that serves a
        // Resident serves an Insider identically -- and a row narrowed to Residents would be a
        // role oracle delivered by the allowlist: an Insider whose phone showed no work at all.
        //
        // Not the out classes. A player outside the system has no work order to be shown, their
        // screen is D-134's, and the effect is addressed to one seat anyway. Denying it on the
        // round-state axis carries no role information -- round-state is publicly observable
        // (D-068) -- and it is the fail-closed direction for a message carrying assignments.
        WORK_ORDER_ISSUED to LIVING,

        // **Both living classes, and for D-109's reason rather than for a new one.** A scan opens
        // an Insider's fake by the same rule that opens a Resident's work, so the row that answers
        // one answers the other identically -- and narrowed to Residents it would be the loudest
        // oracle in the table: an Insider whose every scan came back NOTHING FOR YOU HERE.
        //
        // The null answer and a real opening are ONE kind here, which is what makes that true.
        // Split into two kinds they would be two rows, and the day somebody narrowed one of them
        // the difference between "this card holds nothing for you" and "this card opened work"
        // would be readable from the allowlist without either payload being seen.
        //
        // Not the out classes. A player outside the system is not walking the house looking for
        // work; their screen is D-134's. Denying it on the round-state axis carries no role
        // information -- round-state is publicly observable (D-068) -- and it is the second of the
        // two independent denials a Revoked seat's scan meets, the first being the state gate in
        // the rules.
        SCAN_ANSWERED to LIVING,

        // **PRESENCE_CHANGED HAS NO ROW, DELIBERATELY, AND THAT IS THE DECISION** (D-111, D-136).
        //
        // *The house records, never recites.* The presence plane's one designed consumer is the
        // spectator map's expiry, which is hardware-dependent, unbuilt and frozen -- and D-111 is
        // explicit that this data may never reach a notice, a count, or any surface a living
        // player can read. Until the map exists there is nobody it may go to, so it goes to
        // nobody, which rule 2 does for free the moment a row is not written.
        //
        // **Written down rather than left as an omission**, because an absent row is
        // indistinguishable from a forgotten one and this one must survive somebody tidying up.
        // When the spectator map lands the row becomes OUTSIDE and not one class more: presence
        // reaching a LIVING seat is the leak the work plane was split in two to close.

        // **The house's opening text, and it dims every panel in the building** (D-118). One of
        // exactly two events that do. A dimming lamp is world-observable in a dark house, so a
        // notification that reached fewer than everyone would be a beacon (D-076) -- which is why
        // this row is as wide as MESSAGE_DELIVERED's rather than as narrow as the round allows.
        // At arming nobody is out yet; the row is written for the rule, not for the moment.
        OPENING_MESSAGE to (LIVING + OUTSIDE),

        // **The ring, and it is for the living** (D-134). A player who is out gets no phone call
        // -- they get STAND AND WALK IN, which is the row below. This narrows on the ROUND-STATE
        // axis and never on role, which is the axis the two-axis taxonomy exists for: both roles
        // hear the same ring, and everyone in the house can already see whether the lights are on.
        MEETING_OPENED to LIVING,

        // The other half of the same call. Two kinds rather than one with a flag, because the two
        // audiences are told different things: the living are told who called it, and the out are
        // told to stand up. D-135's long haptic rides this one for a NEWLY Revoked player.
        STAND_AND_WALK_IN to OUTSIDE,

        // D-104's gate is ONE gate -- every living player and every out player -- so it is one
        // count, drawn on both screens. Anonymous: a count and a total, never a list of who
        // (gdd.md:294, and the design's own "anonymous check-ins" steer at gdd.md:177).
        CHECK_IN_PROGRESSED to (LIVING + OUTSIDE),

        // Where the meeting has got to. The talk starting, the ballot opening and the result
        // arriving are each known to the whole room at the moment they happen.
        MEETING_PHASE_OPENED to (LIVING + OUTSIDE),

        // The readiness count, which appears on the living's discussion screen and nowhere else.
        // D-134 lists what the couch watches -- the discussion and vote timers and the live vote
        // -- and this is not on it, so it is not sent. The fail-closed direction, deliberately.
        READY_PROGRESSED to LIVING,

        // **One seat's own ballot, addressed to that seat.** The living see a count and never a
        // selection (D-117), so this is not a widening: it tells a player what THEY hold. It is
        // also the re-assertion that makes a post-READY tap a refusal rather than a silence, and
        // it is emitted on every tap so that its absence can never be the message (rule 1).
        VOTE_HELD to LIVING,

        // **The live selections, and the couch is the only reader** (D-117, D-134). Ghosts see
        // selections rather than a count, and that is most of what makes being out an information
        // privilege rather than a preview. Widened to either living class it would hand the room
        // its own thinking in real time, which is the single largest disclosure in this table.
        VOTE_SELECTION_SHOWN to OUTSIDE,

        // `N OF 6 VOTED`, counting LOCKED seats (D-117). The number the living are entitled to,
        // and the reason VOTE_SELECTION_SHOWN can be denied them without leaving a blank screen.
        VOTE_PROGRESSED to LIVING,

        // The outcome, to everyone: most votes is Restrained, ties resolve to Skip. No role, no
        // confirmation, no reveal -- a correct restraint and a catastrophic one look identical.
        MEETING_RESULT to (LIVING + OUTSIDE),

        // D-075: the vote does not publish attribution. The living see counts; only a player
        // outside the system sees who cast what, and sees it live. This carries the attribution
        // list, so it goes to the out and to nobody else -- MEETING_RESULT is what the living get.
        MEETING_RESOLVED to OUTSIDE,

        // The takeover, addressed to the one seat the room restrained, at the halfway mark. By
        // the time it is emitted that seat classifies Out, because the halfway mark is when the
        // house deauthorises them (gdd.md:1009) -- so this row is OUTSIDE and the addressing does
        // the rest. Permitted to no living class: a takeover reaching a living phone would be the
        // house telling somebody else's device that the vote went against them.
        RESTRAINED_TAKEOVER to OUTSIDE,

        // Lights out. Everybody, because everybody's screen changes.
        MEETING_ENDED to (LIVING + OUTSIDE),

        // **The house catching fire, and it is as wide as OPENING_MESSAGE's row for the same
        // reason.** This is the second of D-118's exactly two dimming events, a dimming lamp is
        // world-observable in a dark house, and a notification that reached fewer than everyone
        // would be a beacon (D-076). It is also what a player outside the system watches while it
        // runs -- the real Egress number, live (gdd.md:1014) -- which their class already has the
        // liveness privilege for, so the same row serves both audiences and needs no split.
        //
        // **It reaches the Insider who fired it, identically.** That is required rather than
        // tolerated: an Insider whose phone did not dim, in a house where every other phone did,
        // is the single loudest tell available in this design (gdd.md:396).
        EGRESS_OPENED to (LIVING + OUTSIDE),

        // The countdown stopping and starting again (D-133). The whole party is standing in one
        // room when it fires, so it is exactly as public as MEETING_PHASE_OPENED and goes to
        // exactly the same people -- a widget still counting down through a meeting the rest of
        // the house had stopped for would be the one screen disagreeing with the room.
        EGRESS_HELD to (LIVING + OUTSIDE),

        // **One participant's own beat, addressed to that participant.** Not a widening: it tells
        // a player what THEIR tap did and carries nothing about anybody else -- not who is at the
        // other node, not how many are waiting. Emitted on every return so that its absence can
        // never be the message (rule 1).
        //
        // Not the out classes. A player outside the system is not standing at a node; the effect
        // is addressed to one seat anyway, and the rules decline them a held offer as well, so
        // this is the second of two independent denials. Round-state is publicly observable
        // (D-068).
        SYNC_PULSE_ANSWERED to LIVING,

        // **It stopped, and nobody learns anything about anybody** (gdd.md:987). To everyone,
        // because every widget in the building reverts at the same moment. The effect carries no
        // seats and no node -- see the type -- so widening it costs nothing and narrowing it would
        // leave somebody counting down toward a loss that is not coming.
        EGRESS_CONTAINED to (LIVING + OUTSIDE),

        // **EGRESS_SUCCEEDED HAS NO ROW ANY MORE, AND THAT IS THE DECISION** (D-131, D-156).
        //
        // It had `LIVING + OUTSIDE` while the terminal fact was the last thing that happened in a
        // round. It is not any more: `egressExpired` is its only emitter, every emission of it now
        // sits in the same reduction that ends the round, and classification uses the state AFTER
        // the event -- so by the moment of delivery every seat is already Ended and the old row
        // could not have delivered it to anybody. **A row that cannot fire is worse than no row**:
        // it reads as a permission somebody granted, and the next person to widen it would be
        // "fixing" a delivery that was never missing.
        //
        // What every phone receives instead is ROUND_ENDED carrying `WinRoute.EgressUncontained`,
        // which is the same fact on the row built for it. **And the deletion is what keeps D-156**:
        // EgressSucceeded rides Haptic.Long, so a widened row would put a second buzz on every
        // phone in the house on one of the four routes and one buzz on the other three. The
        // underlying message count never drives the buzz count -- so the house says this to the
        // recording, and says the ending to the room.
        //
        // **Written down rather than left as an omission**, exactly as PRESENCE_CHANGED is: an
        // absent row is indistinguishable from a forgotten one.

        // ---- The ending -----------------------------------------------------------------------
        //
        // **These three rows are the only rows in this table that name an AFTER class as anything
        // but a passenger, and that is the strongest guarantee any of them has.** `RoundState.Ended`
        // is reachable only through `GameState.outcome`, whose only writer is the transition that
        // emits these effects -- so a reveal constructed at any other moment in the round is
        // offered to classes that do not exist yet and delivered to nobody. Rule 2 doing the work
        // rule 3 would otherwise have to: the audience is bounded by time, in the type.

        // The round is over and who won, to every seat, once each. Both AFTER classes and it must
        // never be narrowed to one of them: an ending that reached only Residents would leave two
        // people looking at a live round in a lit room, and one narrowed the other way is the same
        // sentence with the roles exchanged.
        ROUND_ENDED to AFTER,

        // **The reveal. Everybody, and this is the row the whole app has been avoiding all
        // evening** (gdd.md:213, gdd.md:1063). Names and blackmail in one kind, to both classes --
        // the point of a reveal is that the room learns it together, and a class denied it would be
        // a player watching everybody else stand up.
        INSIDERS_REVEALED to AFTER,

        // **The narrowest row in the table: a Insider, out of a round that has ended, and nothing
        // else.** On any other screen this would be the leak the app exists to prevent. It is
        // lawful here for one reason, and the reason is a row above: INSIDERS_REVEALED has already
        // published who the Insiders were, to everyone, on the screen this message appears on.
        //
        // Written as an explicit one-class set rather than as `AFTER - Residents`, for
        // ABILITY_FIRED's reason -- a row derived by subtraction is a row that widens the day
        // somebody adds a class to the set it was subtracted from.
        HOUSE_SIGNED_OFF to setOf(ClientClass(Role.Insider, RoundState.Ended)),
    )

    /**
     * May this class receive this kind?
     *
     * The `?: emptySet()` is the fail-closed hinge of the whole story. It is not defensive
     * boilerplate and must never be softened into a permit.
     */
    fun permits(kind: MessageKind, clientClass: ClientClass): Boolean =
        (ALLOWED[kind] ?: emptySet()).contains(clientClass)

    /** Every class permitted this kind, ordered. Empty for a kind with no entry. */
    fun classesFor(kind: MessageKind): List<ClientClass> =
        ClientClass.ALL.filter { permits(kind, it) }

    /** Every kind that has an entry, ordered by name. For reporting, never for permitting. */
    fun knownKinds(): List<MessageKind> = ALLOWED.keys.sortedBy { it.name }

    /**
     * Which seats an effect is *addressed* to, before the allowlist is consulted.
     *
     * **A separate concern from the allowlist, and neither subsumes the other.** Addressing stops
     * seat 5 from learning seat 3's lamp — a leak the allowlist cannot see, because both are
     * living Residents in the same class. The allowlist stops a kind reaching a class that may
     * never have it — a leak addressing cannot see, because the seat really was a recipient.
     *
     * Filtered to seated players, so a malformed [Seat] addresses nobody rather than being
     * silently accepted as a recipient.
     *
     * **Known tension, recorded not resolved:** [Effect.MessageDelivered] carries a seat, so it is
     * addressed to one player, while D-076 requires every notification to reach everyone. The type
     * and the decision disagree. Addressing follows the type here rather than quietly overriding
     * it; closing the gap means changing the effect, which is loop work.
     */
    fun audienceOf(effect: Effect, state: GameState): List<Seat> {
        val addressed = when (effect) {
            is Effect.LampSet -> listOf(effect.seat)
            is Effect.AbilityFired -> listOf(effect.actor)
            // The one seat that handed the entry over, and nobody else. Addressed rather than
            // broadcast because a verdict reaching the house at large would publish who is
            // completing work and how often -- which is the whole read the meter is quantised
            // into a percentage to prevent (D-103), arriving per player instead of in aggregate.
            is Effect.SubroutineGraded -> listOf(effect.seat)
            is Effect.MessageDelivered -> listOf(effect.seat)
            // That seat's own order, to that seat. Broadcasting it would publish what everybody
            // else was assigned, which is a map of the round's work handed to a player who is
            // supposed to be walking a dark house to find their own.
            is Effect.WorkOrderIssued -> listOf(effect.seat)
            // The seat with the card in its hand, and no other phone. Broadcasting a scan answer
            // would publish who is standing at which marker finding work, live, to the whole
            // house -- the per-player read the percentage meter (D-103) exists to keep out of
            // aggregate, arriving one scan at a time.
            is Effect.ScanAnswered -> listOf(effect.seat)
            // **The performing seat, and this is the second of the presence plane's two
            // independent denials** -- the first being that the kind has no row at all. Neither
            // subsumes the other: a row written by mistake still could not carry seat 3's window
            // to seat 5, and an addressing widened by mistake still delivers to nobody.
            is Effect.PresenceChanged -> listOf(effect.seat)
            // Addressed per seat and emitted once per seat, which is how the opening message
            // reaches everyone (D-076) without the effect growing a broadcast shape that a later,
            // quieter message could inherit.
            is Effect.OpeningMessage -> listOf(effect.seat)
            is Effect.SubroutineProgressed -> state.seats
            // Broadcast, and left to the allowlist to narrow. Addressing these by round-state
            // here as well would state the same rule in two places, and the day they disagreed
            // the quieter one would win.
            is Effect.MeetingOpened -> state.seats
            is Effect.CheckInProgressed -> state.seats
            is Effect.MeetingPhaseOpened -> state.seats
            is Effect.ReadyProgressed -> state.seats
            is Effect.VoteSelectionShown -> state.seats
            is Effect.VoteProgressed -> state.seats
            is Effect.MeetingResult -> state.seats
            is Effect.MeetingResolved -> state.seats
            is Effect.MeetingEnded -> state.seats
            // The three that name one seat. Each is that player's own screen changing: they were
            // told to walk in, they are being shown their own ballot, or the room restrained them.
            is Effect.StandAndWalkIn -> listOf(effect.seat)
            is Effect.VoteHeld -> listOf(effect.seat)
            is Effect.RestrainedTakeover -> listOf(effect.seat)
            // Addressed per seat and emitted once per seat, which is how the Egress alert reaches
            // everyone (D-076) without the effect growing a broadcast shape. Exactly
            // OpeningMessage's treatment, and for the same reason: these are D-118's two dimming
            // events, and a quieter message inheriting a broadcast shape from one of them is the
            // mistake that would not show up in a diff.
            is Effect.EgressOpened -> listOf(effect.seat)
            // The seat whose finger was on the beat, and no other phone. Broadcasting it would
            // publish who is standing at a node and tapping, live, to the whole house -- which is
            // the presence read D-111 split the two planes to close, arriving one beat at a time.
            is Effect.SyncPulseAnswered -> listOf(effect.seat)
            // Broadcast, and left to the allowlist to narrow. Three facts about the house rather
            // than about anybody in it: the timer stopped, it was contained, it was not.
            is Effect.EgressHeld -> state.seats
            is Effect.EgressContained -> state.seats
            is Effect.EgressSucceeded -> state.seats
            // Addressed per seat and emitted once per seat, exactly as OpeningMessage and
            // EgressOpened are. The ending takes over every screen in the house, so it must reach
            // everyone -- and a broadcast SHAPE here is a thing a quieter message could inherit
            // later, which is the mistake that would not show up in a diff.
            is Effect.RoundEnded -> listOf(effect.seat)
            // Broadcast, and left to the allowlist to narrow. One fact about the round rather than
            // about the phone it arrives on -- and the one fact in the game that is the same
            // sentence on every screen in the building.
            is Effect.InsidersRevealed -> state.seats
            // The one seat the house is talking to, and no other phone. Broadcasting the sign-off
            // would publish who the house owned a second time, in a different shape, on a row that
            // was written for exactly two people -- and it is the addressing rather than the row
            // that stops a Insider reading the sign-off sent to the OTHER Insider.
            is Effect.HouseSignedOff -> listOf(effect.seat)
        }
        return state.seats.filter { seated -> addressed.any { it.index == seated.index } }
    }

    /**
     * The emit boundary. Addressed AND permitted, in seat order.
     *
     * Everything downstream of this function — the per-client transcript recorder, and one day the
     * socket — takes its recipients from here and nowhere else.
     */
    fun deliveries(effect: Effect, state: GameState): List<Delivery> {
        val kind = kindOf(effect)
        return audienceOf(effect, state).mapNotNull { seat ->
            val clientClass = state.clientClassOf(seat)
            if (permits(kind, clientClass)) Delivery(seat, clientClass, kind) else null
        }
    }
}
