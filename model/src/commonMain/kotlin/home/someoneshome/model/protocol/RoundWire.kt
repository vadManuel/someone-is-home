package home.someoneshome.model.protocol

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.ClientFacing
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.MessageKind
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import kotlinx.serialization.json.Json

/**
 * **What the house says to one phone during a round, in the opaque body of a
 * [TransportFrame.Carry].**
 *
 * The lobby's [LobbyBody] is the pre-arm half of this channel; this is the half that exists once
 * there is a round. Everything here has already been through the emit boundary — addressed by
 * [EmitSchema.audienceOf] and permitted by [EmitSchema.permits] — before a single byte was
 * rendered, which is what lets the frame layer stay unable to see what it is carrying.
 *
 * ### Why an [Effect] does not simply travel
 *
 * [Effect] is deliberately not `@Serializable` and `redactionLint` fails the build on any attempt
 * to make it so. The wire type is therefore **constructed**, never annotated onto the authority's
 * own shape — rule 3's *redact by constructing a narrower type* applied to the transport rather
 * than to the rules. [project] is that construction and it is exhaustive over [Effect], so a new
 * effect does not compile until somebody decides what, if anything, of it goes down a socket.
 *
 * ### The narrowing is real and it is the recipient's own seat
 *
 * Sixteen of the thirty-one effects are addressed to a single seat, and for every one of them the
 * message below **has no seat field at all**. The socket is the address: a frame arriving on this
 * phone's connection is this phone's, and a seat number riding inside it would be a second,
 * quieter statement of the same fact that could disagree with the first. The kinds that keep a
 * seat are the broadcast ones whose *content* is a seat — who called the meeting, who the room
 * restrained, who cast what — and every one of those is a disclosure its own allowlist row already
 * decided.
 *
 * ### One kind, one payload
 *
 * [kind] is the same [MessageKind] the allowlist is keyed on, and `RoundWireTest` pins
 * `project(effect).kind == EmitSchema.kindOf(effect)` for every effect there is. There is no
 * per-class variant of any payload here: if two classes need different content that is two kinds
 * upstream, in [Effect], with two rows — which is D-079's rule arriving at the wire unchanged.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class RoundMessage {

    /** Light is game state. The luminance the rules emitted, and nothing about who it is for. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("LampSet")
    data class LampSet(val luminance: Int) : RoundMessage()

    /**
     * The ability fired — identically whether it changed anything (rule 1).
     *
     * The actor is the recipient, so the actor is the socket. What is left is the cooldown, which
     * is this phone's own business and is on nobody else's screen.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("AbilityFired")
    data class AbilityFired(val cooldownStarted: Boolean) : RoundMessage()

    /** The house's answer to a returned entry. One boolean, for [Effect.SubroutineGraded]'s reason. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SubroutineGraded")
    data class SubroutineGraded(val accepted: Boolean) : RoundMessage()

    /** The ledger's raw remaining count, to the two classes outside the system. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SubroutineProgressed")
    data class SubroutineProgressed(val remaining: Int) : RoundMessage()

    /** The meter, live, percentage only (D-103, D-130). Never a count against a total. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SystemIntegrityLive")
    data class SystemIntegrityLive(val percent: Int) : RoundMessage()

    /** The meter as the living are told it: once, at a shift boundary, then frozen. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SystemIntegrityBatched")
    data class SystemIntegrityBatched(val percent: Int) : RoundMessage()

    /** A message body, to the one phone it was addressed to. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MessageDelivered")
    data class MessageDelivered(val body: String) : RoundMessage()

    /**
     * This seat's own order, whole.
     *
     * [OrderLine] is already the client-facing half of the pair — a blocked line is a different
     * type with one field on it — so the narrowing this wire needed had been done in the rules
     * (D-114). What is dropped here is only the seat.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("WorkOrderIssued")
    data class WorkOrderIssued(val lines: List<OrderLine>) : RoundMessage()

    /**
     * The answer to a scan, **including the null that means NOTHING FOR YOU HERE**.
     *
     * One kind and one message per scan, exactly as [Effect.ScanAnswered] argues: split in two on
     * the wire, a reader who never sees either payload could separate *this card holds nothing for
     * you* from *this card opened work*.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ScanAnswered")
    data class ScanAnswered(val opened: SubroutineInstance?) : RoundMessage()

    /**
     * **Projected and permitted to nobody, exactly as its kind is named in [EmitSchema.kindOf] and
     * granted nothing** (D-111, D-136).
     *
     * The presence plane has no allowlist row, so [EmitSchema.deliveries] produces no delivery for
     * it and this is never constructed by [project] in any round that runs. It exists because the
     * projection is exhaustive over [Effect] — and being named here grants it exactly as much as
     * being named in `kindOf` does, which is nothing. **Written rather than left as a hole**: a
     * missing branch would be a compile error somebody fixes with whatever is nearest.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("PresenceChanged")
    data class PresenceChanged(val at: MarkerId?, val open: Boolean) : RoundMessage()

    /** The house's opening text. It carries no words — the client holds the copy (D-112). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("OpeningMessage")
    data class OpeningMessage(val haptic: Haptic) : RoundMessage()

    /** The ring, to the living. Carries who called it, who is public by construction (D-121). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingOpened")
    data class MeetingOpened(
        val caller: Seat,
        val trigger: MeetingTrigger,
        val haptic: Haptic,
    ) : RoundMessage()

    /** What a player outside the system gets instead of a call (D-134). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("StandAndWalkIn")
    data class StandAndWalkIn(val haptic: Haptic) : RoundMessage()

    /** The gate as a count and a total, never a list of who (D-104, `gdd.md:294`). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("CheckInProgressed")
    data class CheckInProgressed(val present: Int, val expected: Int) : RoundMessage()

    /** Where the meeting has got to, and the ballot's window on the push that opens it (D-117). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingPhaseOpened")
    data class MeetingPhaseOpened(
        val phase: MeetingPhase,
        val voteWindowSeconds: Int?,
        val haptic: Haptic,
    ) : RoundMessage()

    /** Hands up for READY TO VOTE, counted. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ReadyProgressed")
    data class ReadyProgressed(val ready: Int, val expected: Int) : RoundMessage()

    /**
     * One seat's own ballot, to that seat.
     *
     * The voter is the socket; [selection] is somebody else and stays. Emitted on every tap so
     * that its absence can never be the message (rule 1) — which is a property of the rules and is
     * preserved here only by this kind having nothing that could make a re-assertion look
     * different from a first answer.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("VoteHeld")
    data class VoteHeld(val selection: Seat?, val locked: Boolean) : RoundMessage()

    /** A live selection, for the couch and nobody else (D-117, D-134). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("VoteSelectionShown")
    data class VoteSelectionShown(val voter: Seat, val selection: Seat?) : RoundMessage()

    /** `N OF 6 VOTED` — locked seats, never selections (D-117). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("VoteProgressed")
    data class VoteProgressed(val locked: Int, val expected: Int) : RoundMessage()

    /** The result, to everyone. No role, no confirmation, no reveal (`gdd.md:1007`). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingResult")
    data class MeetingResult(val restrained: Seat?, val haptic: Haptic) : RoundMessage()

    /** The ballot with names against it. The out, and nobody else (D-075). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingResolved")
    data class MeetingResolved(
        val restrained: Seat?,
        val attribution: List<Cast>,
    ) : RoundMessage()

    /**
     * One entry of an attribution list: who voted, and for whom.
     *
     * A named type rather than a `Pair`, because a pair on a wire is two anonymous positions and
     * the day somebody swaps them the leak is that the room learns a different set of voters.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    data class Cast(val voter: Seat, val selection: Seat?)

    /** The takeover, to the one seat the room restrained (D-102, D-134). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("RestrainedTakeover")
    data class RestrainedTakeover(val haptic: Haptic) : RoundMessage()

    /** Lights out. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingEnded")
    data class MeetingEnded(val haptic: Haptic) : RoundMessage()

    /**
     * The house on fire, to everybody (D-118, D-076).
     *
     * The seat goes because the effect is emitted once per seat and the seat it carries is the
     * recipient's; the nodes stay, because coordination is required and nobody may speak.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("EgressOpened")
    data class EgressOpened(
        /**
         * Beacon or Tether. **On the wire it is `egress`, not `type`**: `type` is the polymorphic
         * discriminator this file's JSON already uses to say which message it is, and a payload
         * field of the same name makes the whole sealed hierarchy unserialisable. Caught by
         * `everyMessageSurvivesTheWire`, which is what a round-trip sweep is for.
         */
        @kotlinx.serialization.SerialName("egress")
        val type: EgressType,
        val nodes: List<MarkerId>,
        val remaining: Long,
        val haptic: Haptic,
    ) : RoundMessage()

    /** The countdown stopped, or started again (D-133). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("EgressHeld")
    data class EgressHeld(val remaining: Long, val running: Boolean, val haptic: Haptic) : RoundMessage()

    /** One participant's own beat, and the same answer either way (rule 1). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SyncPulseAnswered")
    data class SyncPulseAnswered(val held: Boolean) : RoundMessage()

    /** Contained, and nobody learns anything about anybody (`gdd.md:987`). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("EgressContained")
    data class EgressContained(val haptic: Haptic) : RoundMessage()

    /**
     * **Projected and permitted to nobody**, exactly as [PresenceChanged] is and for the reason
     * [EmitSchema] gives where its row used to be: every emission of it sits in the same reduction
     * that ends the round, so by delivery time every seat is already Ended. What the room is told
     * is [RoundEnded] carrying `WinRoute.EgressUncontained`.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("EgressSucceeded")
    data class EgressSucceeded(val haptic: Haptic) : RoundMessage()

    /** The round is over and who won. The seat goes; it was the recipient's. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("RoundEnded")
    data class RoundEnded(
        val winner: Winner,
        val by: WinRoute,
        val haptic: Haptic,
    ) : RoundMessage()

    /**
     * **THE INSIDERS WERE — the one message in this file that carries a one line, and the one
     * moment it is lawful** (`gdd.md:1063`, D-116).
     *
     * Every other kind here is checked by `TheOneLineTest` to be incapable of carrying one. This
     * one carries them on purpose: the publish **is** the round ending, `GameState.endRound` empties
     * the desk in the same transition that builds this effect, and the allowlist permits the kind to
     * the two Ended classes and to nothing that exists before them. So the guarantee worth holding is
     * not *no frame ever carries a line* — it is that **no frame carries one until the round is
     * over**, which is exactly the shape W1 gave the same question about names.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("InsidersRevealed")
    data class InsidersRevealed(val insiders: List<InsiderNamed>) : RoundMessage()

    /** The house speaks last, and only to the people it owned (`gdd.md:1051`). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("HouseSignedOff")
    data class HouseSignedOff(val body: String) : RoundMessage()

    /**
     * **The allowlist's own key, off the message rather than beside it.**
     *
     * Carried as a computed property and never as a field: a `kind` field would be a second
     * statement of what the message is, settable independently of what it actually is, and the
     * pipeline that reads it to consult the allowlist would be asking the message what it is
     * allowed to be. `RoundWireTest` pins this against [EmitSchema.kindOf] for every effect.
     */
    val kind: MessageKind
        get() = when (this) {
            is LampSet -> EmitSchema.LAMP_SET
            is AbilityFired -> EmitSchema.ABILITY_FIRED
            is SubroutineGraded -> EmitSchema.SUBROUTINE_GRADED
            is SubroutineProgressed -> EmitSchema.SUBROUTINE_PROGRESSED
            is SystemIntegrityLive -> EmitSchema.SYSTEM_INTEGRITY_LIVE
            is SystemIntegrityBatched -> EmitSchema.SYSTEM_INTEGRITY_BATCHED
            is MessageDelivered -> EmitSchema.MESSAGE_DELIVERED
            is WorkOrderIssued -> EmitSchema.WORK_ORDER_ISSUED
            is ScanAnswered -> EmitSchema.SCAN_ANSWERED
            is PresenceChanged -> EmitSchema.PRESENCE_CHANGED
            is OpeningMessage -> EmitSchema.OPENING_MESSAGE
            is MeetingOpened -> EmitSchema.MEETING_OPENED
            is StandAndWalkIn -> EmitSchema.STAND_AND_WALK_IN
            is CheckInProgressed -> EmitSchema.CHECK_IN_PROGRESSED
            is MeetingPhaseOpened -> EmitSchema.MEETING_PHASE_OPENED
            is ReadyProgressed -> EmitSchema.READY_PROGRESSED
            is VoteHeld -> EmitSchema.VOTE_HELD
            is VoteSelectionShown -> EmitSchema.VOTE_SELECTION_SHOWN
            is VoteProgressed -> EmitSchema.VOTE_PROGRESSED
            is MeetingResult -> EmitSchema.MEETING_RESULT
            is MeetingResolved -> EmitSchema.MEETING_RESOLVED
            is RestrainedTakeover -> EmitSchema.RESTRAINED_TAKEOVER
            is MeetingEnded -> EmitSchema.MEETING_ENDED
            is EgressOpened -> EmitSchema.EGRESS_OPENED
            is EgressHeld -> EmitSchema.EGRESS_HELD
            is SyncPulseAnswered -> EmitSchema.SYNC_PULSE_ANSWERED
            is EgressContained -> EmitSchema.EGRESS_CONTAINED
            is EgressSucceeded -> EmitSchema.EGRESS_SUCCEEDED
            is RoundEnded -> EmitSchema.ROUND_ENDED
            is InsidersRevealed -> EmitSchema.INSIDERS_REVEALED
            is HouseSignedOff -> EmitSchema.HOUSE_SIGNED_OFF
        }
}

/**
 * **What rides in the round's half of the [TransportFrame.Carry] channel.**
 *
 * Two bodies, each travelling one way only, exactly as [LobbyBody]'s three do:
 *
 * - [Push] goes **host → client**. One permitted message, with the sequence number that seat's
 *   stream is at.
 * - [Gap] goes **client → host**. A seat saying which sequence it last held, so the house can
 *   re-assert the rest.
 *
 * ### [Gap] is not an Intent, and the distinction is not pedantry
 *
 * An Intent is a request to change the round that the authority may refuse (rule 10), and building
 * those is not this unit's. A gap names no game action, has no refusal, mints no [home.someoneshome.model.Event]
 * and reaches no rule: the house's whole answer is bytes it already sent to that phone, which is
 * why it can exist while the intent channel does not. **It also cannot ask for anything it was not
 * already given** — the house replays its own per-seat outbox, so a client asking for a sequence
 * belonging to somebody else's stream is asking for its own stream at that number.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class RoundBody {

    /**
     * **Host → client. One message, and where it sits in this seat's stream.**
     *
     * [seq] is per seat and starts at 1. It is transport bookkeeping and nothing else: it counts
     * frames this phone was sent, so it can say nothing about how much work the house is doing or
     * how many phones there are — two seats in the same round hold entirely different numbers.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("push")
    data class Push(val seq: Long, val message: RoundMessage) : RoundBody()

    /**
     * **Client → host. "I hold everything up to [held]; say the rest again."**
     *
     * Sent when a push arrives out of order and when a socket comes back, which are the same
     * request with different causes (D-097: *one mechanism, two triggers*). A phone that has never
     * held anything sends zero and is told the whole round it was permitted, which is what a
     * mid-round reconnect is.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("gap")
    data class Gap(val held: Long) : RoundBody()
}

/**
 * The one encoder/decoder for [RoundBody], for the reason [LobbyWire] is the one for the lobby's:
 * what the JSON looks like has exactly one home, and its round-trip test lives beside it.
 */
object RoundWire {

    private val json = Json

    fun encode(body: RoundBody): String = json.encodeToString(RoundBody.serializer(), body)

    /**
     * Null for anything that is not a round body — a lobby body included. A body that does not
     * decode must become a fact the shell can report, never an exception on an I/O thread taking a
     * player's connection down with it.
     */
    fun decodeOrNull(text: String): RoundBody? = try {
        json.decodeFromString(RoundBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * **The projection: one [Effect], one [RoundMessage], no decisions.**
     *
     * Exhaustive over [Effect] on purpose and for [EmitSchema.kindOf]'s reason exactly — a new
     * effect does not compile until somebody names it here. **And naming it here grants it
     * nothing**: what a client actually receives is decided by [EmitSchema.deliveries] before this
     * function is reached, so a message projected for a kind with no allowlist row is constructed
     * for nobody, in a round in which nothing ever calls it.
     *
     * Nothing in here branches on a viewer, a class or a role. There is no argument for one: a kind
     * is permitted to a class in full or not at all (D-079), so a projection that varied by
     * recipient would be per-class narrowing of a single kind, which is redaction by nulling fields
     * under another name.
     */
    fun project(effect: Effect): RoundMessage = when (effect) {
        is Effect.LampSet -> RoundMessage.LampSet(effect.luminance)
        is Effect.AbilityFired -> RoundMessage.AbilityFired(effect.cooldownStarted)
        is Effect.SubroutineGraded -> RoundMessage.SubroutineGraded(effect.accepted)
        is Effect.SubroutineProgressed -> RoundMessage.SubroutineProgressed(effect.remaining)
        is Effect.SystemIntegrityLive -> RoundMessage.SystemIntegrityLive(effect.percent)
        is Effect.SystemIntegrityBatched -> RoundMessage.SystemIntegrityBatched(effect.percent)
        is Effect.MessageDelivered -> RoundMessage.MessageDelivered(effect.body)
        is Effect.WorkOrderIssued -> RoundMessage.WorkOrderIssued(effect.lines)
        is Effect.ScanAnswered -> RoundMessage.ScanAnswered(effect.opened)
        is Effect.PresenceChanged -> RoundMessage.PresenceChanged(effect.at, effect.open)
        is Effect.OpeningMessage -> RoundMessage.OpeningMessage(effect.haptic)
        is Effect.MeetingOpened ->
            RoundMessage.MeetingOpened(effect.caller, effect.trigger, effect.haptic)
        is Effect.StandAndWalkIn -> RoundMessage.StandAndWalkIn(effect.haptic)
        is Effect.CheckInProgressed ->
            RoundMessage.CheckInProgressed(effect.present, effect.expected)
        is Effect.MeetingPhaseOpened ->
            RoundMessage.MeetingPhaseOpened(effect.phase, effect.voteWindowSeconds, effect.haptic)
        is Effect.ReadyProgressed -> RoundMessage.ReadyProgressed(effect.ready, effect.expected)
        is Effect.VoteHeld -> RoundMessage.VoteHeld(effect.selection, effect.locked)
        is Effect.VoteSelectionShown ->
            RoundMessage.VoteSelectionShown(effect.voter, effect.selection)
        is Effect.VoteProgressed -> RoundMessage.VoteProgressed(effect.locked, effect.expected)
        is Effect.MeetingResult -> RoundMessage.MeetingResult(effect.restrained, effect.haptic)
        is Effect.MeetingResolved -> RoundMessage.MeetingResolved(
            effect.restrained,
            effect.attribution.map { (voter, selection) -> RoundMessage.Cast(voter, selection) },
        )
        is Effect.RestrainedTakeover -> RoundMessage.RestrainedTakeover(effect.haptic)
        is Effect.MeetingEnded -> RoundMessage.MeetingEnded(effect.haptic)
        is Effect.EgressOpened ->
            RoundMessage.EgressOpened(effect.type, effect.nodes, effect.remaining, effect.haptic)
        is Effect.EgressHeld ->
            RoundMessage.EgressHeld(effect.remaining, effect.running, effect.haptic)
        is Effect.SyncPulseAnswered -> RoundMessage.SyncPulseAnswered(effect.held)
        is Effect.EgressContained -> RoundMessage.EgressContained(effect.haptic)
        is Effect.EgressSucceeded -> RoundMessage.EgressSucceeded(effect.haptic)
        is Effect.RoundEnded -> RoundMessage.RoundEnded(effect.winner, effect.by, effect.haptic)
        is Effect.InsidersRevealed -> RoundMessage.InsidersRevealed(effect.insiders)
        is Effect.HouseSignedOff -> RoundMessage.HouseSignedOff(effect.body)
    }
}

// ---------------------------------------------------------------------------------------------
// The types the projection reaches through, gathered here so that marking each one was a decision
// somebody made rather than a chain of consequences somebody followed:
//
//   Seat                     -- an index and no more; a role has never been derivable from one.
//   Haptic, MeetingPhase,    -- every value of every one of them is a fact the whole house
//   MeetingTrigger,             watched happen, at the moment it happened.
//   EgressType, Winner, WinRoute
//   MarkerId, Destination,   -- the client-facing halves the rules already construct, and the
//   AssignedWork, OrderLine,    narrowing that made them so is D-114's and D-112's rather than
//   SubroutineInstance          anything this file decided.
//   InsiderNamed             -- the reveal, and only the reveal. See RoundMessage.InsidersRevealed.
//
// **Role is not on that list and never will be.** It is the one type in the model whose presence
// on a wire would end the game, and nothing in `project` can reach it.
// ---------------------------------------------------------------------------------------------
