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
import home.someoneshome.model.Sound
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

    /**
     * **The Surge, landing on this phone** (`gdd.md:313`, `:342`).
     *
     * The seat goes: it was the recipient's, and the socket is the address. What is left is the
     * gesture the rules authored — two luminances, the one sound this game has, and a buzz.
     *
     * **[peak] and [black] are on the wire because the lamp is a pure function of state** (rule 5):
     * a device handed a bare *you were surged* token would be a device inventing an animation, and a
     * fade nobody authored is a signal nobody authored. What the phone returns to afterwards is the
     * last [LampSet] it was sent, which it is already holding.
     *
     * **There is nothing here that says who fired it**, and there is nowhere to put it. A self-surge
     * and a surge from across the house are the same frame, which is what makes the alibi play work
     * (`gdd.md:342`).
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("Surged")
    data class Surged(
        val peak: Int,
        val black: Int,
        val sound: Sound,
        val haptic: Haptic,
    ) : RoundMessage()

    /**
     * **The markers this Insider may Isolate** (`gdd.md:315`, D-150).
     *
     * The seat goes, like every addressed kind here. The list stays whole and is re-sent rather than
     * diffed — see [Effect.IsolateTargetsOffered], which carries the disclosure argument, the
     * Egress-node exclusion, and the flag.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("IsolateTargetsOffered")
    data class IsolateTargetsOffered(val targets: List<Destination>) : RoundMessage()

    /**
     * **STATIC: the monitoring is jammed, for this many seconds, and the noise is drawn from this
     * seed** (D-186).
     *
     * **No seat, and there was never one to drop.** Every other addressed kind here loses a seat in
     * the projection because the frame goes to one phone; this one carries no seat on the effect
     * either, because the jam is house-wide and anonymous. Every living phone in the building
     * receives byte-identical bytes, including the Insider who fired it — see [Effect.StaticOpened],
     * where *loud and anonymous* is argued.
     *
     * [seconds] rather than a deadline, exactly as [EgressOpened]'s `remaining` is: a tick is the
     * house's own clock and means nothing on a phone. [seed] is what the noise is generated from,
     * so the animation is authored by the rules rather than invented by the device (rule 5).
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("StaticOpened")
    data class StaticOpened(val seconds: Int, val seed: Long) : RoundMessage()

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

    /**
     * **You are the one calling this meeting** (E-R1-1), and it carries nothing.
     *
     * **The empty body is the whole design.** `MeetingOpened.caller` is a `Seat` and no client can
     * use it, because a phone is never told which seat it is — so the house says *you* by choosing
     * the socket, and this member has no field for it to say anything else with. Two meetings called
     * by two different people put identical bytes on two different wires.
     *
     * `RestrainedTakeover`'s shape one message over, minus the haptic: the ring is `MeetingOpened`'s
     * and this phone is already receiving that. One phone in a silent house buzzing differently from
     * the other five would be the caller announced through a pocket.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingCalling")
    data object MeetingCalling : RoundMessage()

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
            is Surged -> EmitSchema.SURGED
            is IsolateTargetsOffered -> EmitSchema.ISOLATE_TARGETS_OFFERED
            is StaticOpened -> EmitSchema.STATIC_OPENED
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
            is MeetingCalling -> EmitSchema.MEETING_CALLING
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
 * Three bodies, each travelling one way only, exactly as [LobbyBody]'s three do:
 *
 * - [Push] goes **host → client**. One permitted message, with the sequence number that seat's
 *   stream is at.
 * - [Gap] goes **client → host**. A seat saying which sequence it last held, so the house can
 *   re-assert the rest.
 * - [Act] goes **client → host**. One request, with the sequence number this connection is at.
 * - [Fire] goes **client → host**. The Egress tile, held down — the one request whose fact the
 *   house must *compose* rather than translate, which is why it is not an [Act].
 *
 * ### [Gap] is not an Intent, and the distinction is not pedantry
 *
 * An Intent is a request to change the round that the authority may refuse (rule 10). A gap names
 * no game action, has no refusal, mints no [home.someoneshome.model.Event] and reaches no rule: the
 * house's whole answer is bytes it already sent to that phone. **It also cannot ask for anything it
 * was not already given** — the house replays its own per-seat outbox, so a client asking for a
 * sequence belonging to somebody else's stream is asking for its own stream at that number. [Act]
 * is the other thing entirely, and the two are separate members rather than one *upward* body so
 * that the desk's two answers — *replay what you missed* and *put this to the rules* — can never be
 * reached by the same branch.
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

    /**
     * **Client → host. "I am asking for this, and it is the [seq]th thing I have asked for."**
     *
     * ### The frame carries no seat, and that is the whole security property of this channel
     *
     * [intent] is an [IntentMessage], which has no field an actor could ride in. The desk stamps
     * the seat the *connection* was admitted to, so a client is not merely discouraged from naming
     * another seat — it has nowhere to write one. See [IntentMessage] for the argument and for the
     * three requests deliberately absent from it.
     *
     * ### [seq] is the connection's, and it is a dedup key rather than an order
     *
     * It counts requests this connection has made, starting at 1, and the house keeps the highest
     * it has seen per seat: **a request at or below that mark has already been put to the rules and
     * is dropped.** D-097 makes intents fire-and-forget — *clients hold no request/response
     * posture* — so nothing here is an ack and nothing waits for one; the number exists because a
     * frame delivered twice must not be *acted on* twice, and `ArmRevoke` applied twice is two
     * cooldowns spent on one press.
     *
     * **It resets with the connection at both ends**, because that is the only window in which a
     * resend can happen: a phone whose process failed and resumed its seat starts counting again from
     * 1, and a house that kept the old mark would silently swallow every request it ever made
     * again. See `IntentOutbox` and `IntentDesk.seated`.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("act")
    data class Act(val seq: Long, val intent: IntentMessage) : RoundBody()

    /**
     * **Client → host. "I held the Egress tile down."** And that is the whole message.
     *
     * ### Why it is not an [Act], and the reason is `factOf`'s line rather than tidiness
     *
     * An [Act] carries an [IntentMessage], and an [IntentMessage] may exist only where the
     * `Event` it becomes is **fully determined by what the phone did, which connection did it, and
     * when it arrived.** [home.someoneshome.model.Event.EgressFired] fails that test and always
     * will: its type and its two nodes are the *house's* draw, made above the rules against a
     * home's shape the phone has never held.
     *
     * Both ways of forcing it into [Act] break something real. A member carrying the type and the
     * nodes would let a client **choose its own Egress** — pick the shortest pair, name the two
     * markers in one room — which is the forge [IntentMessage] exists to make unrepresentable. A
     * member carrying nothing would need a `factOf` arm that composes a fact from a second source,
     * which is the one thing that function refuses to do and the reason it can stay total.
     *
     * So the request gets its own frame and the desk answers it at its own door: *put this to the
     * rules* and *make the draw, then put THAT to the rules* are two different answers, and two
     * members is what stops them being reachable by one branch. [Gap] is the precedent for a
     * client→host body that is not an [Act]; this is the second, and it is the opposite sort — a
     * gap names no game action and reaches no rule, where this one names the largest action in the
     * game.
     *
     * ### There is nothing in it, and there must not be
     *
     * No type, no nodes, no actor, no target. **The tile is the entire message**, and the seat comes
     * off the connection like every other request's. A field added here is a field a client fills.
     *
     * ### [seq] is the connection's, shared with [Act]'s
     *
     * One counter per connection across both members, because the thing being deduplicated is *this
     * phone's requests* and not *this phone's requests of one kind*. An Egress delivered twice would
     * be the loudest double-press in the game — the house's longest cooldown spent on one finger —
     * and two counters would be two marks a resend could slip between.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("fire")
    data class Fire(val seq: Long) : RoundBody()
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
        is Effect.Surged ->
            RoundMessage.Surged(effect.peak, effect.black, effect.sound, effect.haptic)
        is Effect.IsolateTargetsOffered -> RoundMessage.IsolateTargetsOffered(effect.targets)
        is Effect.StaticOpened -> RoundMessage.StaticOpened(effect.seconds, effect.seed)
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
        // **The seat comes off here, and the message is empty afterwards.** What survives the
        // projection is that the house sent it at all; which phone it means is the socket it goes
        // down (`EmitSchema.deliveries`), never a field a client could read its own seat out of.
        is Effect.MeetingCalling -> RoundMessage.MeetingCalling
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
//   Sound                    -- one member, and the licence for it is that Surge is
//                               self-inflictable (gdd.md:393). See the type.
//   MarkerId, Destination,   -- the client-facing halves the rules already construct, and the
//   AssignedWork, OrderLine,    narrowing that made them so is D-114's and D-112's rather than
//   SubroutineInstance          anything this file decided.
//   InsiderNamed             -- the reveal, and only the reveal. See RoundMessage.InsidersRevealed.
//
// **Role is not on that list and never will be.** It is the one type in the model whose presence
// on a wire would end the game, and nothing in `project` can reach it.
// ---------------------------------------------------------------------------------------------
