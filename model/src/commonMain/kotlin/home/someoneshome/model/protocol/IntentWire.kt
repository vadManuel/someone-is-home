package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat

/**
 * **What one phone asks the house for, in the opaque body of a [TransportFrame.Carry].**
 *
 * The upward half of the round's channel, and [RoundMessage]'s mirror in every respect that
 * matters. [RoundMessage] is a fact the house states; this is a request the house may refuse.
 *
 * ### Why an [Intent] does not simply travel
 *
 * [Intent] is deliberately not `@Serializable`, for the reason [home.someoneshome.model.Effect] is
 * not: the wire type is **constructed**, never annotated onto the authority's own shape. But where
 * the effect side constructs a *narrower* type to keep ground truth off a phone, this side
 * constructs a narrower type to keep an **attribution** off a phone — and the field it narrows away
 * is the one field every member of [Intent] has.
 *
 * ### Every [Intent] carries an actor. Nothing here does, and nothing here can.
 *
 * *Intents are attributed by connection, never by a client naming itself. A client that could name
 * its own seat could claim another player's — the only cheat in this game that is remote,
 * undetectable, and requires no physical act.*
 *
 * `MeetingModel` has kept that discipline in `ui` since L2 by publishing a seat-free request and
 * attaching the seat one layer out. **This is that discipline becoming the wire's**, which is a
 * strictly stronger statement: a screen keeping its hands clean is a convention somebody can break
 * in one edit, whereas a frame with nowhere to put a seat is a frame that cannot carry a claim
 * about one. [attributedTo] is the only way an [Intent] is ever built from something that arrived
 * on a socket, and the seat it takes comes from the connection the bytes came in on.
 *
 * `IntentWireTest.noMessageCanSayWhoSentIt` reads the rendered JSON of every member and sweeps it
 * for a field that could be an actor, which is the assertion that survives somebody adding one.
 *
 * ### The seats that do appear are content, exactly as they are on the way down
 *
 * [SelectVote] carries a seat and [RoundMessage.VoteSelectionShown] carries two. The rule is the
 * same in both directions: a seat may ride in a payload when the seat **is** what the message is
 * about, and never when it is who the message is from. A vote's target is a row the house drew on
 * this phone's ballot and publishes live to the couch (D-117); it is the content of the request and
 * the house re-states it to everyone who may see it.
 *
 * ### What is deliberately absent, and why each one is a decision rather than a gap
 *
 * Three client-originated facts have **no member here**, and none of them is an oversight:
 *
 * - **The Revoke firing** ([home.someoneshome.model.Event.ContactMade]) — *phone touched to phone.
 *   Physical, so it is reported, never requested.* The event names a target seat that only the
 *   authority can resolve, because D7 puts every phone's advertised identity behind a token that
 *   rotates every fifteen seconds and is resolvable by the house alone. A client that could send
 *   this would be naming the seat it revoked, which is the forge this whole file exists to make
 *   unrepresentable.
 * - **The Revoked report** ([home.someoneshome.model.MeetingTrigger.RevokeReported]) — the same act
 *   and therefore the same answer. It carries `reported`, a seat, arrived at by touching a phone to
 *   another phone.
 * - **The Egress firing** ([home.someoneshome.model.Event.EgressFired]) — the finger on the button
 *   is a client's, but the event is not: its type and its two nodes are the *house's* draw, made
 *   above the rules against a home's shape the phone has never held. A request whose event cannot
 *   be built from what the phone did, who did it and when is not carriage — it is the house
 *   composing a fact from two sources, and that join is the loop's.
 *
 * That is the line this file draws and the line `core`'s `factOf` holds: **an [Intent] may exist
 * only where the [home.someoneshome.model.Event] it becomes is fully determined by what the phone
 * did, which connection did it, and when it arrived.**
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class IntentMessage {

    /** Arm the Revoke. Armed, not fired — the firing is contact, and see this file's header. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ArmRevoke")
    data object ArmRevoke : IntentMessage()

    /**
     * A card was read. The card's own id and nothing about where the reader was standing.
     *
     * [MarkerId] is the printed card, which the house registered to a room during the setup walk —
     * so this reports a *reading*, and where that puts the player is the house's conclusion rather
     * than the phone's claim (D-136).
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("BeginSubroutine")
    data class BeginSubroutine(val marker: MarkerId) : IntentMessage()

    /**
     * **Surge that seat** (`gdd.md:313`, the owner's 2026-08-22 targeting ruling).
     *
     * [target] is a seat and it stays, for [SelectVote]'s reason exactly: *a seat may ride in a
     * payload when the seat **is** what the message is about, and never when it is who the message
     * is from.* This is who the Surge is aimed at, picked off a roster the house drew and published
     * to this phone.
     *
     * **It may be this phone's own seat, and there is nowhere here to say so.** Self-targeting is
     * required (`gdd.md:393`) and it needs no expression on the wire — the desk stamps the actor
     * from the connection, and a target equal to it is a self-surge without anything having to
     * announce itself as one. There is deliberately no `self: Boolean`: a flag would be a second
     * statement of a fact the payload already carries, settable independently of it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("FireSurge")
    data class FireSurge(val target: Seat) : IntentMessage()

    /**
     * **Isolate that marker** (`gdd.md:315`, D-150).
     *
     * [MarkerId] is the printed card, off the list the house sent this seat
     * (`RoundMessage.IsolateTargetsOffered`) — so like [BeginSubroutine] this reports a **choice
     * among what the house offered**, and carries nothing about where the player is standing.
     *
     * **A card the list did not offer is refused by the rules and costs nothing**, which is the one
     * place the two halves of D-150 meet: the exclusion is what the *list* does, and the refusal
     * without a charge is what the *rules* do about the gap between a list arriving and a finger
     * landing on it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("FireIsolate")
    data class FireIsolate(val target: MarkerId) : IntentMessage()

    /**
     * **Claim to be in this room** (`gdd.md:313`, [Intent.FireSpoof]).
     *
     * [room] is a name off the plan the house sent this seat, and it is the only target in the
     * protocol that is a host-authored string rather than an id. The house does not resolve it —
     * see the event, where a room the plan does not hold being representable is argued as
     * deliberate rather than as a hole.
     *
     * **No target seat.** A Spoof moves the actor's own claimed location and there is no other
     * reading of it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("FireSpoof")
    data class FireSpoof(val room: String) : IntentMessage()

    /**
     * The meeting card was scanned.
     *
     * **The trigger is which member of this type was sent, and is not a field.** The house has to
     * tell the card from the Revoked report because one is inert during an Egress and the other is
     * not (D-133), and a `trigger` field here would be a client choosing which of the two admission
     * rules it would like to be judged by. Two controls, two members — D-079's *if two classes need
     * different content, that is two kinds* pointed the other way up the wire. The report itself is
     * not here at all; see this file's header.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("CallMeeting")
    data object CallMeeting : IntentMessage()

    /** I'M HERE. One phone at the meeting area; the gate that closes on it is the house's (D-104). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("CheckIn")
    data object CheckIn : IntentMessage()

    /** READY TO VOTE. One hand up. Only a unanimous one ends the talk early. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("DeclareReadyToVote")
    data object DeclareReadyToVote : IntentMessage()

    /**
     * A finger on a name, or on Skip.
     *
     * The voter is the socket; [target] is somebody else and stays, for the reason
     * [RoundMessage.VoteSelectionShown] keeps both of its seats — a selection is a fact about a
     * ballot the house drew and publishes live (D-117), not a claim about who is asking.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SelectVote")
    data class SelectVote(val target: Seat?) : IntentMessage()

    /**
     * READY: turn the current selection into the vote, irrevocably (D-117).
     *
     * **Carries no target**, exactly as [Intent.LockVote] and
     * [home.someoneshome.model.Event.VoteLocked] do not: a target here would let a client lock a
     * vote the couch never watched it make.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("LockVote")
    data object LockVote : IntentMessage()

    /**
     * **An entry handed over at a marker — a report of what was entered, never a claim that it was
     * right** (D-109).
     *
     * [entered] is the player's own taps in the order they made them, and there is nothing here to
     * compare them against: the entry types in `ui` are physically incapable of holding what the
     * house asked for, so this frame could not carry a verdict even if somebody wanted it to. The
     * house holds what it asked, receives what was entered, and concludes.
     *
     * [marker] is which card opened the work, because a player may have scanned a second one since
     * — it is not a claim about where they are standing now, which the house has never taken from a
     * phone (D-136).
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("HandOverEntry")
    data class HandOverEntry(
        val marker: MarkerId,
        /** A List, not a Set. Order is the answer in half of the ten, and this is recorded input. */
        val entered: List<Int>,
    ) : IntentMessage()

    /**
     * **The one construction of an [Intent] from anything that arrived on a socket.**
     *
     * [actor] comes from the connection and from nowhere else — the desk stamps the seat its
     * [SeatToken] was admitted to, so the same bytes arriving on two connections build two
     * different intents. That is asserted directly, because it is the property that makes the forge
     * impossible rather than merely inconvenient: there is no argument this function could be
     * passed that a client chose.
     *
     * Exhaustive over this type on purpose and for [RoundWire.project]'s reason exactly — a new
     * member does not compile until somebody names the request it makes.
     */
    fun attributedTo(actor: Seat): Intent = when (this) {
        is ArmRevoke -> Intent.ArmRevoke(actor)
        is FireSurge -> Intent.FireSurge(actor, target)
        is FireIsolate -> Intent.FireIsolate(actor, target)
        is FireSpoof -> Intent.FireSpoof(actor, room)
        is BeginSubroutine -> Intent.BeginSubroutine(actor, marker)
        is CallMeeting -> Intent.CallMeeting(actor)
        is CheckIn -> Intent.CheckIn(actor)
        is DeclareReadyToVote -> Intent.DeclareReadyToVote(actor)
        is SelectVote -> Intent.SelectVote(actor, target)
        is LockVote -> Intent.LockVote(actor)
        is HandOverEntry -> Intent.HandOverEntry(actor, marker, entered)
    }
}
