package home.someoneshome.model.protocol

import home.someoneshome.model.ArmedTouch
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.ClientFacing
import home.someoneshome.model.ContactSeed
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
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Role
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
     *
     * ### One digit, so the frame does not say whether the request took (D-258)
     *
     * As a Boolean the field was `true` or `false`, four characters or five, so the frame's size
     * told anybody measuring the Wi-Fi whether a Surge, Static, Isolate or Egress request had
     * started a cooldown or fallen into a running one. On the wire it is now [cooldown], one digit
     * ([STARTED] or [NOT_STARTED]), still under the name `cooldownStarted`; the call sites keep
     * `AbilityFired(cooldownStarted = …)` and read [cooldownStarted]. A value of any other width
     * cannot be constructed or decoded. [ContactAnswered] and [RevokeLanded] are padded to this
     * frame's one length (D-216, D-219).
     *
     * ### Two more digits, for an armed phone's touch (the owner's 2026-11-18 ruling, D-290)
     *
     * An armed phone's answer to a touch is this message, and until D-290 it was `STARTED`, the
     * same frame an Egress into a ready clock is answered with, so the phone could not take ARMED
     * down at the touch. It is now [SPENT_AT_A_TOUCH] or [LANDED_AT_A_TOUCH]: still one digit, so
     * the frame is the touch's one length whichever it says, and still under the name
     * `cooldownStarted`, because the touch's cooldown was started at the arm. [cooldownStarted] is
     * true for both; [touch] says which, and [haptic] is the buzz the house put on it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("AbilityFired")
    data class AbilityFired(
        @kotlinx.serialization.SerialName("cooldownStarted") val cooldown: Int,
    ) : RoundMessage() {

        /** The message for this phone: whether its cooldown started. Not a touch's answer. */
        constructor(cooldownStarted: Boolean) : this(if (cooldownStarted) STARTED else NOT_STARTED)

        init {
            require(cooldown in NOT_STARTED..LANDED_AT_A_TOUCH) {
                "a cooldown is $NOT_STARTED, $STARTED, $SPENT_AT_A_TOUCH or $LANDED_AT_A_TOUCH"
            }
        }

        /** Whether the ability spent its cooldown: every answer but [NOT_STARTED]. */
        val cooldownStarted: Boolean get() = cooldown != NOT_STARTED

        /** What a touch did with this phone's armed Revoke, or [ArmedTouch.None] for every other fire. */
        val touch: ArmedTouch
            get() = when (cooldown) {
                SPENT_AT_A_TOUCH -> ArmedTouch.Spent
                LANDED_AT_A_TOUCH -> ArmedTouch.Landed
                else -> ArmedTouch.None
            }

        /** The buzz the house put on it: Long when this phone's Revoke landed (D-290), nothing otherwise. */
        val haptic: Haptic? get() = touch.haptic

        override fun toString(): String = "AbilityFired(cooldownStarted=$cooldownStarted, touch=$touch)"

        companion object {
            const val STARTED: Int = 1
            const val NOT_STARTED: Int = 0

            /** An armed phone's touch spent its Revoke and it landed on nobody (D-290). */
            const val SPENT_AT_A_TOUCH: Int = 2

            /** An armed phone's touch landed its Revoke (D-290). */
            const val LANDED_AT_A_TOUCH: Int = 3

            /** The house's answer, as one digit: a touch's own value, or whether the cooldown started. */
            fun of(cooldownStarted: Boolean, touch: ArmedTouch): AbilityFired = AbilityFired(
                when (touch) {
                    ArmedTouch.Landed -> LANDED_AT_A_TOUCH
                    ArmedTouch.Spent -> SPENT_AT_A_TOUCH
                    ArmedTouch.None -> if (cooldownStarted) STARTED else NOT_STARTED
                },
            )
        }
    }

    /**
     * **Somebody's ability was answered, and this phone is sent a frame of [AbilityFired]'s length
     * at the same moment** (D-263, [Effect.AbilityBlanked]).
     *
     * Every living phone but the actor's is sent this where the actor is sent [AbilityFired], so a
     * reader of the Wi-Fi sees one frame of one length land on every living phone and cannot pick
     * out the one that asked. It says nothing: [paddingDigits] is always [ZERO], one digit, and a
     * value of any other width cannot be constructed or decoded. The serial name is two characters
     * longer than `AbilityFired` and the field name two shorter than `cooldownStarted`, so the two
     * frames are the same number of bytes at every sequence number. Nothing reads it: `Flow` and
     * `HouseBuzzer` name it as a no-op.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("AbilityBlanked")
    data class AbilityBlanked(val paddingDigits: Int) : RoundMessage() {
        init {
            require(paddingDigits == ZERO) { "the blank carries $ZERO and nothing else" }
        }

        companion object {
            const val ZERO: Int = 0

            /** The one blank there is. */
            val Padded: AbilityBlanked = AbilityBlanked(ZERO)
        }
    }

    /**
     * **A touch, answered, at exactly [AbilityFired]'s length on the wire** (R1, D-216,
     * [Effect.ContactAnswered]).
     *
     * The seat comes off: it is the recipient's, and the socket is the address. What is left is
     * [pad], which carries nothing and exists so that this frame and an armed Insider's
     * `AbilityFired(cooldownStarted = true)` are the same number of bytes once each is pushed and
     * carried. Always [PAD] and never anything else: a field whose width could vary would be a
     * field a reader of the Wi-Fi could measure. `ContactAnswerSizeTest` encodes both through the
     * real `RoundWire` and `TransportWire` and fails if a rename on either side moves one of them.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ContactAnswered")
    data class ContactAnswered(val pad: String) : RoundMessage() {
        companion object {
            /** Six characters: `"ContactAnswered","pad":"…"` against `"AbilityFired","cooldownStarted":1` (D-258). */
            const val PAD: String = "000000"
        }
    }

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
     *
     * ### Hex on the wire, so a Resident's [IsolateTargetsBlanked] can be exactly as long (D-258)
     *
     * Every living Resident is sent [IsolateTargetsBlanked] at the moment every living Insider is
     * sent this, so that the frame count does not follow the role. The two must then be the same
     * number of bytes once pushed, encoded and carried. A list of destinations is JSON whose width
     * on the wire depends on which characters the room names hold, because each layer escapes some
     * characters and not others; so the list travels as [targetsHex], its JSON's UTF-8 bytes in
     * lowercase hex, which no layer escapes. The serial names are both 21 characters and the field
     * names both 10, so a blank of the same hex width is a frame of the same length.
     * `IsolateFrameSizeTest` measures the two over the real sockets. The call sites keep
     * `IsolateTargetsOffered(targets)` and read [targets].
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("IsolateTargetsOffered")
    data class IsolateTargetsOffered(val targetsHex: String) : RoundMessage() {

        /** The offer for this phone: these destinations, sealed. */
        constructor(targets: List<Destination>) : this(seal(targets))

        init {
            require(targetsHex.length % 2 == 0 && WireHex.isHex(targetsHex)) {
                "an offer's targets are lowercase hex, two characters a byte"
            }
            open(targetsHex)
        }

        /** The destinations this Insider may Isolate, read back off the hex. */
        val targets: List<Destination> get() = open(targetsHex)

        override fun toString(): String = "IsolateTargetsOffered(targets=$targets)"

        companion object {
            private val TARGETS = kotlinx.serialization.builtins.ListSerializer(Destination.serializer())

            /** The list as hex: its JSON's UTF-8 bytes, two lowercase characters each. */
            fun seal(targets: List<Destination>): String =
                WireHex.of(Json.encodeToString(TARGETS, targets).encodeToByteArray())

            /** [seal] backwards. Throws an `IllegalArgumentException` for hex that is not a list. */
            fun open(hex: String): List<Destination> =
                Json.decodeFromString(TARGETS, WireHex.bytes(hex).decodeToString())
        }
    }

    /**
     * **What a living Resident is sent where a living Insider is sent [IsolateTargetsOffered]:
     * padding, and nothing else** (D-258, [Effect.IsolateTargetsBlanked]).
     *
     * [paddingHex] is zeros, exactly as many as the offer's [IsolateTargetsOffered.targetsHex] at
     * the same moment, and never anything else: a Resident's phone has no list here to read,
     * because there is no list here. It moves no screen and the phone ignores it. Serial name and
     * field name are the offer's lengths, so the two frames are one length at every moment.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("IsolateTargetsBlanked")
    data class IsolateTargetsBlanked(val paddingHex: String) : RoundMessage() {
        init {
            require(paddingHex.all { it == '0' }) { "a blank carries zeros and nothing else" }
        }

        companion object {
            /** The blank for a moment whose offer is [targets]: as wide as the offer, all zeros. */
            fun over(targets: List<Destination>): IsolateTargetsBlanked =
                IsolateTargetsBlanked("0".repeat(IsolateTargetsOffered.seal(targets).length))
        }
    }

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
     * The answer to a scan of the T card: the live view opens, or ONE READER AT A TIME (D-199).
     * One kind for both, for [ScanAnswered]'s reason. See [Effect.TerminalAnswered].
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("TerminalAnswered")
    data class TerminalAnswered(val reading: Boolean) : RoundMessage()

    /**
     * **NOT A MARKER: the card this phone read is not part of this home** (D-124, D-318), and it
     * carries nothing.
     *
     * The seat comes off as it does for every addressed kind, and there is nothing left: which
     * phone it means is the socket it goes down. No card id rides it either — the phone read the
     * card, and the house's answer is only *not here*. One shape and one size on every phone, for
     * both roles. See [home.someoneshome.model.Effect.CardUnregistered], which says why the rules
     * never produce it and why it is outside the recording.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("CardUnregistered")
    data object CardUnregistered : RoundMessage() {
        /** D-160's *no*, the effect's own and copied here (D-318, provisional). */
        val haptic: Haptic get() = Haptic.Double
    }

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

    /**
     * **What this phone is** (`gdd.md:923`, E-R2-2).
     *
     * ### A Boolean, because [Role] is not allowed on a wire and must never become allowed
     *
     * `Role` carries no `@Serializable` and no `@ClientFacing`, and that is a decision rather than
     * an omission — `redactionLint`'s own KDoc records the day the guard default-permitted *the
     * most alignment-revealing type in the game*. So the projection narrows it the way rule 3 says:
     * a **new, narrower type** rather than the authority's own shape with an annotation added. What
     * survives is one bit about the recipient.
     *
     * ### The seat goes, like every addressed kind here
     *
     * The socket is the address. A seat riding inside would be a second, quieter statement of whose
     * role this is — and this is the one message in the protocol where the two disagreeing would
     * hand a phone somebody else's alignment.
     *
     * **There is nothing here that could name anybody else, and there must never be.** Known mode's
     * *a message from each fellow Insider* is text on a thread (`gdd.md:923`), diegetic and
     * deliberately not a roster; a list on this member would be the endgame reveal arriving at
     * arming.
     *
     * **[line] is this phone's own one line, and only an Insider's is ever sent** (D-116, D-247).
     * The house's blackmail text quotes it back (D-101); a Resident's message carries a blank,
     * because the house never uses a Resident's line. It is the recipient's own words, so it names
     * nobody the recipient did not name. Defaulted blank: a message that never carried one quotes
     * nothing rather than somebody else's.
     *
     * ### One length on the wire for both roles (D-249)
     *
     * Every seat is sent exactly one of these, at the same place in the arming burst, so its size
     * is a reading of the role for anybody who can measure the Wi-Fi. As D-247 shipped it, a
     * Resident's was `"insider":false` with the blank line left out (the JSON drops a default) and
     * an Insider's was `"insider":true,"line":"…"`: the short frame was the Resident. Even before
     * D-247, `true` and `false` are four and five characters.
     *
     * So the wire carries two fields whose widths cannot vary: [role] is one digit
     * ([INSIDER] or [RESIDENT]), and [lineHex] is the line's UTF-8 bytes in lowercase hex, padded
     * with zero bytes to [LINE_BYTES], always [LINE_CHARS] characters. A Resident's is all padding.
     * [LINE_BYTES] is the longest line `LobbyBody.Handover.LIMIT` allows at three bytes a UTF-16
     * unit (a surrogate pair is four bytes for two units), and a longer one is cut at a character
     * so it still fits. Hex rather than the text itself because JSON escapes some characters and
     * not others, so a string's width on the wire is not its length. Both are checked when a
     * message is built, so one of the wrong width cannot be decoded or constructed.
     * `RoleRevealedFrameSizeTest` measures the two over the real sockets.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("RoleRevealed")
    data class RoleRevealed(val role: Int, val lineHex: String) : RoundMessage() {

        /** The message for this phone: its role, and its own words or none. */
        constructor(insider: Boolean, line: String = "") : this(if (insider) INSIDER else RESIDENT, sealLine(line))

        init {
            require(role == INSIDER || role == RESIDENT) { "a reveal's role is $INSIDER or $RESIDENT" }
            require(lineHex.length == LINE_CHARS && lineHex.all { it in '0'..'9' || it in 'a'..'f' }) {
                "a reveal's line is $LINE_CHARS lowercase hex characters"
            }
        }

        /** Whether page 2 answers this phone's thumb. */
        val insider: Boolean get() = role == INSIDER

        /** The words, with the padding off. Blank on a Resident's. */
        val line: String get() = openLine(lineHex)

        /** The length, never the words: `Effect.RoleRevealed`'s rule, one layer out (D-247). */
        override fun toString(): String = "RoleRevealed(insider=$insider, line=${line.length} chars)"

        companion object {
            const val INSIDER: Int = 1
            const val RESIDENT: Int = 0

            /** The most bytes a line can take: [LobbyBody.Handover.LIMIT] units at three bytes each. */
            const val LINE_BYTES: Int = LobbyBody.Handover.LIMIT * 3

            /** [lineHex]'s one width. */
            const val LINE_CHARS: Int = LINE_BYTES * 2

            private const val DIGITS = "0123456789abcdef"

            /** The line as [LINE_CHARS] hex characters: cut at a character to fit, zero-padded. */
            fun sealLine(line: String): String {
                var words = line
                var bytes = words.encodeToByteArray()
                while (bytes.size > LINE_BYTES) {
                    words = words.dropLast(1)
                    bytes = words.encodeToByteArray()
                }
                val out = StringBuilder(LINE_CHARS)
                for (b in bytes) {
                    val v = b.toInt() and 0xff
                    out.append(DIGITS[v shr 4]).append(DIGITS[v and 0xf])
                }
                while (out.length < LINE_CHARS) out.append('0')
                return out.toString()
            }

            /** [sealLine] backwards: the bytes up to the padding, read as UTF-8. */
            fun openLine(hex: String): String {
                val bytes = ByteArray(hex.length / 2) { i ->
                    ((DIGITS.indexOf(hex[2 * i]) shl 4) or DIGITS.indexOf(hex[2 * i + 1])).toByte()
                }
                var end = bytes.size
                while (end > 0 && bytes[end - 1] == 0.toByte()) end--
                return bytes.decodeToString(0, end)
            }
        }
    }

    /**
     * **Who is in the round, in seat order** (D-115, E-R2-6, C3·4).
     *
     * The seat comes off like every addressed kind here; the list does not, because the list is
     * the message. It is **byte-identical on every phone in the house**, which is what makes it the
     * one named collection in this protocol that can be sent to everybody: there is nothing in it
     * that varies by recipient, so a phone still cannot read its own seat out of a frame.
     *
     * **One entry per seat, in seat order, blank where a phone never said what to call its owner.**
     * The client reads it by index in both directions -- a `Seat` to a name for the tally and the
     * ring, and a tapped row back to a `Seat` for the Surge picker and the ballot -- so a list that
     * omitted the unnamed would shift every index after the gap and fire an ability at the wrong
     * person. `LobbyBody.Standing.names` keeps the same rule for the same reason one channel over.
     *
     * **The index is the seat's, so a seat not in the round is a null slot** (D-274): a round armed
     * with seats 0, 1, 3 and 4 sends five entries with null at 2. See [Effect.RosterIssued].
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("RosterIssued")
    data class RosterIssued(val roster: List<String?>) : RoundMessage()

    /**
     * **The home's plan, the same on every phone** (D-316, [Effect.PlanIssued]). Storeys, cells,
     * room names and kinds, and nothing a card could be read off: the Terminal and the couch draw
     * it, and every other screen ignores it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("PlanIssued")
    data class PlanIssued(val plan: home.someoneshome.model.HomePlanView) : RoundMessage()

    /**
     * **This phone's contact seed, and nothing else** (D-212, [Effect.ContactSeedIssued]).
     *
     * The seat comes off like every addressed kind here: the socket is the address, and a seat
     * riding inside would tell a phone which seat it is. What is left is the key this phone's
     * radio derives its tokens from. It is the same width on every phone and is drawn without
     * reference to role, so the message is the same size and shape on an Insider's phone as on a
     * Resident's.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ContactSeedIssued")
    data class ContactSeedIssued(val seed: ContactSeed) : RoundMessage()

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
    /**
     * **The house notice: who was unreachable on purpose since the last meeting, named to the whole
     * room** (D-187, D-223). Every phone gets the same list, empty at a meeting where nobody evaded,
     * and an empty one draws nothing. See [home.someoneshome.model.Effect.NoticeIssued].
     *
     * [unreachable] is the seats that only left the app, [switchedOff] the seats that switched off
     * something this game needs, no seat on both (D-311), the same on every phone: every phone draws
     * the leavers' sentence first and then the owner's permission sentence, with its warning.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("NoticeIssued")
    data class NoticeIssued(val unreachable: List<Seat>, val switchedOff: List<Seat>) : RoundMessage()

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

    /**
     * The result, to everyone: **a count and an outcome**. No role, no confirmation, no reveal
     * (`gdd.md:1007`), and no per-candidate field — see [Effect.MeetingResult], which holds the
     * ruling and the argument that the pair discloses nothing [VoteProgressed] did not.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("MeetingResult")
    data class MeetingResult(
        val restrained: Seat?,
        val voted: Int,
        val living: Int,
        val haptic: Haptic,
    ) : RoundMessage()

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

    /**
     * **The Revoke landed on this phone** (D-134, E-R2-5).
     *
     * [MeetingCalling]'s member exactly: the seat goes because it was the recipient's, and **the
     * message is empty afterwards.** What survives the projection is that the house sent it at all;
     * which phone it means is the socket it goes down. The two ways out of the round are symmetric
     * on the wire now, which is what the two screens behind them always claimed — `ScreenId.Revoked`
     * and `ScreenId.Restrained` are one screen with a different cause on it, and the cause is which
     * of these two messages arrived.
     *
     * **There is nothing here that says who fired it, and there is nowhere to put it.** A field
     * naming the Insider would be the alignment reveal, delivered to the one player with the
     * strongest possible motive to say it out loud and no rule left stopping them.
     *
     * **A haptic on the touch that put this phone out, and none on any other** (D-290; see
     * [Effect.RevokeLanded], where D-156 carries the second half): the effect is constructed on
     * every contact, so a buzz on every one would fire again each time somebody touched a player who
     * was already out.
     *
     * ### Padded to the touch's one frame size (S1 of the owner's 2026-11-15 rulings, D-219)
     *
     * This is the one frame the Revoked target's phone receives for the touch that put it out, and
     * every other phone that touched is sent [AbilityFired] or [ContactAnswered], 120 bytes carried.
     * Empty, this was 95: on a Wi-Fi that can be read or merely measured, the phone that had just
     * been Revoked was the one whose touch came back short. It was padded to `AbilityFired(true)`'s
     * length by a nine-character constant, [ContactAnswered]'s arrangement.
     *
     * ### One digit in place of the pad (the owner's 2026-11-18 ruling, D-290)
     *
     * The touch that Revokes this phone now buzzes it Long, and a re-assertion on a phone already
     * out does not, so the message carries the buzz, as [buzz]: [LONG] or [SILENT], one digit
     * either way. Its wire name, `hapticAtLanding`, is fifteen characters, `cooldownStarted`'s
     * length, so `"RevokeLanded","hapticAtLanding":1` is `"AbilityFired","cooldownStarted":1` to
     * the byte, pushed, encoded and carried, whichever digit it holds. `ContactAnswerSizeTest`
     * pins all of them together.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("RevokeLanded")
    data class RevokeLanded(
        @kotlinx.serialization.SerialName("hapticAtLanding") val buzz: Int,
    ) : RoundMessage() {
        init {
            require(buzz == SILENT || buzz == LONG) { "a Revoke landing is $SILENT or $LONG" }
        }

        /** The buzz the house put on it: Long on the touch that Revoked this phone, nothing otherwise. */
        val haptic: Haptic? get() = if (buzz == LONG) Haptic.Long else null

        companion object {
            const val SILENT: Int = 0
            const val LONG: Int = 1

            /** A touch on a phone already out: the frame, and no buzz (D-156). */
            val Silent: RevokeLanded = RevokeLanded(SILENT)

            /** The touch that Revoked this phone: the frame, and the Long buzz (D-290). */
            val Buzzing: RevokeLanded = RevokeLanded(LONG)

            /** The message for the haptic the rules put on the effect, which is Long or nothing. */
            fun of(haptic: Haptic?): RevokeLanded = when (haptic) {
                Haptic.Long -> Buzzing
                null -> Silent
                Haptic.Short, Haptic.Double, Haptic.Lockout -> error("a Revoke landing buzzes Long or not at all, never $haptic")
            }
        }
    }

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
        /** Card, glyph and room, for the reason [Effect.EgressOpened.nodes] gives: the widget
         *  NAMES the two nodes, and a card id is not a name. */
        val nodes: List<Destination>,
        val remaining: Long,
        val haptic: Haptic,
        /**
         * **The Sync Pulse's first pulse, in the host's monotonic milliseconds** (C2): pulse `n`
         * lands at `pulseFrom + n · Balance.SYNC_PULSE_INTERVAL_MILLIS` of the house's clock, which
         * this phone reads through its measured offset (C1). The same number on every phone.
         */
        val pulseFrom: Long,
    ) : RoundMessage()

    /** The countdown stopped, or started again (D-133). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("EgressHeld")
    data class EgressHeld(val remaining: Long, val running: Boolean, val haptic: Haptic) : RoundMessage()

    /**
     * One participant's own beat, and the same answer either way (rule 1).
     *
     * ### One digit, so the frame does not say whether the beat was held (C2, D-258's fix)
     *
     * As a Boolean the field was `true` or `false`, four characters or five, so the frame's size
     * told anybody measuring the Wi-Fi whether a return at a node had been held or missed. On the
     * wire it is now [answer], one digit ([HELD] or [NOT_HELD]), still under the name `held`; the
     * call sites keep `SyncPulseAnswered(held = …)` and read [held]. A value of any other width
     * cannot be constructed or decoded. [SyncPulseBlanked] is this frame's length.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SyncPulseAnswered")
    data class SyncPulseAnswered(
        @kotlinx.serialization.SerialName("held") val answer: Int,
    ) : RoundMessage() {

        /** The answer for this phone: whether the house is holding its beat. */
        constructor(held: Boolean) : this(if (held) HELD else NOT_HELD)

        init {
            require(answer == HELD || answer == NOT_HELD) { "an answer is $HELD or $NOT_HELD" }
        }

        /** Whether this phone's run was good, four in a row (D-298): held for a partner, or containing. */
        val held: Boolean get() = answer == HELD

        /** The lockout's vibration on [NOT_HELD], nothing on [HELD]: [Effect.SyncPulseAnswered.haptic]. */
        val haptic: Haptic? get() = if (held) null else Haptic.Lockout

        override fun toString(): String = "SyncPulseAnswered(held=$held)"

        companion object {
            const val HELD: Int = 1
            const val NOT_HELD: Int = 0
        }
    }

    /**
     * **Somebody's Sync Pulse was answered, and this phone is sent a frame of [SyncPulseAnswered]'s
     * length at the same moment** (C2, [Effect.SyncPulseBlanked]; D-263's pattern).
     *
     * Every living phone but the tapper's is sent this where the tapper is sent its answer, so a
     * reader of the Wi-Fi sees one frame of one length land on every living phone. It says nothing:
     * [blank] is always [ZERO], one digit, and a value of any other width cannot be constructed or
     * decoded. The serial name is one character shorter than `SyncPulseAnswered` and the field one
     * longer than `held`, so the two frames are the same number of bytes at every sequence number.
     * Nothing reads it: `Flow` and `HouseBuzzer` name it as a no-op.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("SyncPulseBlanked")
    data class SyncPulseBlanked(val blank: Int) : RoundMessage() {
        init {
            require(blank == ZERO) { "the blank carries $ZERO and nothing else" }
        }

        companion object {
            const val ZERO: Int = 0

            /** The one blank there is. */
            val Padded: SyncPulseBlanked = SyncPulseBlanked(ZERO)
        }
    }

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
        /** The final System Integrity, the one percentage every phone draws on its ending (9b). */
        val integrity: Int,
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
     * **THE HOUSE IS CLOSED** (D-310): a permission was switched off again, and everyone starts over.
     * No field, so every phone is sent the same bytes whatever it is; the two lines are the phone's.
     * See [home.someoneshome.model.Effect.HouseClosedOnPermission].
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("HouseClosedOnPermission")
    data object HouseClosedOnPermission : RoundMessage()

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
            is AbilityBlanked -> EmitSchema.ABILITY_BLANKED
            is ContactAnswered -> EmitSchema.CONTACT_ANSWERED
            is Surged -> EmitSchema.SURGED
            is IsolateTargetsOffered -> EmitSchema.ISOLATE_TARGETS_OFFERED
            is IsolateTargetsBlanked -> EmitSchema.ISOLATE_TARGETS_BLANKED
            is StaticOpened -> EmitSchema.STATIC_OPENED
            is SubroutineGraded -> EmitSchema.SUBROUTINE_GRADED
            is SubroutineProgressed -> EmitSchema.SUBROUTINE_PROGRESSED
            is SystemIntegrityLive -> EmitSchema.SYSTEM_INTEGRITY_LIVE
            is SystemIntegrityBatched -> EmitSchema.SYSTEM_INTEGRITY_BATCHED
            is MessageDelivered -> EmitSchema.MESSAGE_DELIVERED
            is WorkOrderIssued -> EmitSchema.WORK_ORDER_ISSUED
            is ScanAnswered -> EmitSchema.SCAN_ANSWERED
            is TerminalAnswered -> EmitSchema.TERMINAL_ANSWERED
            is CardUnregistered -> EmitSchema.CARD_UNREGISTERED
            is PresenceChanged -> EmitSchema.PRESENCE_CHANGED
            is OpeningMessage -> EmitSchema.OPENING_MESSAGE
            is RoleRevealed -> EmitSchema.ROLE_REVEALED
            is RosterIssued -> EmitSchema.ROSTER_ISSUED
            is PlanIssued -> EmitSchema.PLAN_ISSUED
            is ContactSeedIssued -> EmitSchema.CONTACT_SEED_ISSUED
            is MeetingOpened -> EmitSchema.MEETING_OPENED
            is MeetingCalling -> EmitSchema.MEETING_CALLING
            is StandAndWalkIn -> EmitSchema.STAND_AND_WALK_IN
            is CheckInProgressed -> EmitSchema.CHECK_IN_PROGRESSED
            is MeetingPhaseOpened -> EmitSchema.MEETING_PHASE_OPENED
            is NoticeIssued -> EmitSchema.NOTICE_ISSUED
            is ReadyProgressed -> EmitSchema.READY_PROGRESSED
            is VoteHeld -> EmitSchema.VOTE_HELD
            is VoteSelectionShown -> EmitSchema.VOTE_SELECTION_SHOWN
            is VoteProgressed -> EmitSchema.VOTE_PROGRESSED
            is MeetingResult -> EmitSchema.MEETING_RESULT
            is MeetingResolved -> EmitSchema.MEETING_RESOLVED
            is RestrainedTakeover -> EmitSchema.RESTRAINED_TAKEOVER
            is RevokeLanded -> EmitSchema.REVOKE_LANDED
            is MeetingEnded -> EmitSchema.MEETING_ENDED
            is EgressOpened -> EmitSchema.EGRESS_OPENED
            is EgressHeld -> EmitSchema.EGRESS_HELD
            is SyncPulseAnswered -> EmitSchema.SYNC_PULSE_ANSWERED
            is SyncPulseBlanked -> EmitSchema.SYNC_PULSE_BLANKED
            is EgressContained -> EmitSchema.EGRESS_CONTAINED
            is EgressSucceeded -> EmitSchema.EGRESS_SUCCEEDED
            is RoundEnded -> EmitSchema.ROUND_ENDED
            is InsidersRevealed -> EmitSchema.INSIDERS_REVEALED
            is HouseSignedOff -> EmitSchema.HOUSE_SIGNED_OFF
            is HouseClosedOnPermission -> EmitSchema.HOUSE_CLOSED_ON_PERMISSION
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
 * - [Sighted] goes **client → host**. What this phone's radio heard: a report of fact, never a
 *   request, on a fixed cadence and at a fixed size (D-212). In a round it also carries the one
 *   request slot every request rides in (D-263), so a phone sends no [Act] and no [Fire] once it
 *   is in an armed round.
 * - [UploadHeard] goes **host → client**, to the phone whose upload it answers, once per upload: the
 *   slot's id, so the phone stops sending that request (D-263).
 * - [RadioFaulted] goes **client → host**. This phone's radio went down: a report of fact, one fixed
 *   shape carrying D-187's line (a person's act, or the phone's) and the onset's random name, once
 *   per fault onset and again on each resume until it is answered (T2, D-222; D-223; D-245).
 * - [FaultHeard] goes **host → client**, to the one phone that reported: the answer that lets it
 *   stop keeping the report (D-245).
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
     *
     * ### [round] says which round the stream belongs to (V1, D-267)
     *
     * [seq] starts again at 1 every round, so on its own it cannot tell round 2's push 7 from round
     * 1's. A phone that was off the air when the house ended a round never heard
     * `LobbyBody.RoundEnded`, which is sent once and never replayed, and came back holding round 1's
     * sequence: round 2's opening pushes all landed as duplicates (no role, no seed, no roster) and
     * the phone stayed in round 1. With the round on every push, a phone that sees a newer one ends
     * its own round there and then, and starts the new one from nothing.
     *
     * The house's round count for the evening, from 1, and the same number on every seat's push at
     * the same moment, so it says nothing about a seat. **0 is unmarked**: no house sends it, and a
     * stream of unmarked pushes is judged by [seq] alone, as before.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("push")
    data class Push(val seq: Long, val message: RoundMessage, val round: Int = 0) : RoundBody()

    /**
     * **Client → host. "I hold everything up to [held]; say the rest again."**
     *
     * Sent when a push arrives out of order and when a socket comes back, which are the same
     * request with different causes (D-097: *one mechanism, two triggers*). A phone that has never
     * held anything sends zero and is told the whole round it was permitted, which is what a
     * mid-round reconnect is.
     *
     * [round] is the round [held] belongs to (V1, D-267). A house on a later round answers from the
     * start of its own, because a sequence held in another round says nothing about this one. 0 is
     * unmarked, and [held] is taken as it stands.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("gap")
    data class Gap(val held: Long, val round: Int = 0) : RoundBody()

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

    /**
     * **Client → host. "This is what my radio heard."** A fact, reported (rule 10, D-212).
     *
     * ### Why it is not an [Act], and why it is not a [Fire]
     *
     * [IntentMessage]'s header names the contact as deliberately absent: *physical, so it is
     * reported, never requested*, and the seat it would name is one only the house can resolve from
     * a rotating token (D7). So the phone does not report a contact at all. It reports what it heard
     * — tokens, signal strengths, windows — and the contact is the house's conclusion (unit 2), made
     * above the rules the way [Fire]'s draw is. Nothing here can be refused, because nothing here
     * asks for anything; an upload the house cannot read is dropped.
     *
     * ### Fixed size, and that is the ruling rather than a tidiness
     *
     * [slots] is always [SightingUpload.BODY_CHARS] lowercase hex characters, built by
     * [SightingUpload.pack] and by nothing else. **There is no sequence number**: a counter's digits
     * grow, and a field whose width grew with what this phone had done would be a field a reader of
     * the Wi-Fi could measure. A delivered-twice upload has to be harmless to the house's contact
     * rule, which is the resolver's to guarantee (unit 2): the owner has ruled one contact per pair
     * per five seconds.
     *
     * No seat and no actor: the seat comes off the connection, like every request's.
     *
     * ### [ask]: every request a player makes in a round rides here (D-263, provisional)
     *
     * One [RequestSlot] of [RequestSlot.CHARS] characters, in every upload: the phone's oldest
     * unanswered requests, as many as fit, under one id (D-275), or an empty slot under a fresh id. A
     * request no longer goes up as a frame of its own between beats, because the phones that did that
     * were the ones with something only an Insider can press. The house reads the slot first, puts the
     * requests of an id it has not heard to the rules in order, then judges the sightings, then
     * answers [UploadHeard] with the slot's id.
     * No default, so a construction that forgot the slot does not compile: a field left at its
     * default is a field the encoder leaves out, and the upload would be shorter.
     *
     * ### [probe]: the house's clock is measured on every upload (D-295)
     *
     * [UploadProbe.CHARS] lowercase hex characters, drawn fresh at random by the phone for each
     * upload and echoed back in this upload's [UploadHeard] with the house's clock at its arrival
     * and at its answer. The phone keeps when it sent which probe, so every beat is one round trip
     * on the house's clock: the offset is refreshed every second, on the frame every phone already
     * sends, rather than measured once at join (the owner's ruling, 2026-11-18). A number, never a
     * time: this phone's own clock does not leave it. **Its own field**, beside [slots] and [ask]
     * rather than inside either, so the sightings body can be replaced (the UWB plan's
     * `DistanceUpload`) without touching it. One width from every seat, out seats included (D-217).
     * No default, for [ask]'s reason.
     *
     * ### [ranging]: what this phone's UWB radio can hold, offers and read (U6, D-300)
     *
     * [RangingWire.REPORT_CHARS] characters from every phone in every upload, built by
     * [RangingWire.packReport] and sealed under this seat's contact seed: its capacity, its idle
     * sessions' tokens and the nearest distance per pair this second, padded to
     * [RangingWire.MAX_PEERS] of each. A phone with no UWB sends the same width. **Its own field**,
     * beside [slots], which keeps Bluetooth's 24 slots exactly as they were (U6: Bluetooth ranks who
     * is near). No rule reads it yet: the house's pairing does, and only to choose which phones range
     * which. No default, for [ask]'s reason.
     *
     * ### [view]: whether this phone is on the main view (D-307)
     *
     * [MainViewField.CHARS] character from every phone in every upload, [MainViewField.ON] or
     * [MainViewField.OFF]: page 1 or 2, or the report screen. The house's
     * report hold reads it (a reader off the main view starts no hold and cancels one), and nothing
     * else does. One width from every seat, out seats included (D-217). No default, for [ask]'s
     * reason.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("sighted")
    data class Sighted(val slots: String, val ask: String, val probe: String, val ranging: String, val view: String) : RoundBody()

    /**
     * **Host → the one phone whose upload this answers. "I have the slot named [id]."** (D-263)
     *
     * Sent for **every** upload that arrives, after the house has done with it, whatever the slot
     * held: a request heard for the first time, one heard before, or nothing at all. So every phone
     * in a round is sent one of these a beat, of one length ([RequestSlot.ID_CHARS] hex characters),
     * and it says nothing about whether the phone asked for anything or what the rules made of it
     * (rule 1). The phone stops sending a request once its id comes back. Not a [Push]: transport
     * bookkeeping like [FaultHeard], spends no sequence and reaches no screen.
     *
     * ### And the house's clock, twice (D-295)
     *
     * [probe] is the upload's own [Sighted.probe], echoed. [heard] is the house's monotonic
     * milliseconds when the upload arrived, before it waited in the house's queue; [answered] is the
     * same clock when this answer was made, after the rules were done with it. Both are on the clock
     * the Sync Pulse is anchored on (D-265), so every beat gives the phone one NTP-style sample in
     * which the house's queue and rules are not counted as round trip. All three are
     * [UploadProbe.CHARS] hex characters, never decimal digits, so the frame is one length on every
     * phone at every moment. A probe that did not read is answered as [UploadProbe.NONE], all the same.
     *
     * ### And the house's ranging plan for this phone (U6, D-300)
     *
     * [plan] is [RangingWire.PLAN_CHARS] characters in every answer to every upload: for each pair
     * this phone is in, start it (on one of its idle sessions, against the peer's token), keep it, or
     * stop it, padded to [RangingWire.MAX_PEERS] steps and sealed under this seat's contact seed. A
     * phone the house has nothing to say to is sent the same width, so every phone gets one plan a
     * second, of one length, whatever changed. A handle names a pair and never a seat.
     *
     * ### And what this phone may draw of the map, this beat (D-316)
     *
     * [map] is [OccupancyWire.CHARS] characters in every answer to every upload, sealed under this
     * seat's contact seed like [plan]: the Terminal's counts to the phone holding the station's
     * lock, true occupancy to a phone on the couch, and nothing to every other phone, all at one
     * width. Without the seed a reading and nothing are the same uniform bytes, so the Wi-Fi cannot
     * tell who is reading the Terminal or who is out (D-217, D-306). A phone the house has no seed
     * for is sent random bytes of the same width, which open on no phone.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("uploadHeard")
    data class UploadHeard(val id: String, val probe: String, val heard: String, val answered: String, val plan: String, val map: String) : RoundBody() {
        init {
            require(RequestSlot.isId(id)) { "an upload's id is ${RequestSlot.ID_CHARS} lowercase hex characters" }
            require(UploadProbe.isProbe(probe)) { "an upload's probe is ${UploadProbe.CHARS} lowercase hex characters" }
            require(UploadProbe.isProbe(heard) && UploadProbe.isProbe(answered)) {
                "the house's clock is ${UploadProbe.CHARS} lowercase hex characters"
            }
            require(RangingWire.isPlanField(plan)) { "a ranging plan is ${RangingWire.PLAN_CHARS} characters of URL-safe base64" }
            require(OccupancyWire.isField(map)) { "an occupancy field is ${OccupancyWire.CHARS} characters of URL-safe base64" }
        }

        /** [heard], as the house's monotonic milliseconds. */
        val heardAtMillis: Long get() = UploadProbe.millisOf(heard)

        /** [answered], as the house's monotonic milliseconds. */
        val answeredAtMillis: Long get() = UploadProbe.millisOf(answered)

        companion object {
            /** The answer to the upload whose slot was [id] and whose probe was [probe], stamped, with [plan] and [map]. */
            fun of(id: String, probe: String, heardAtMillis: Long, answeredAtMillis: Long, plan: String, map: String): UploadHeard =
                UploadHeard(id, probe, UploadProbe.hexOf(heardAtMillis), UploadProbe.hexOf(answeredAtMillis), plan, map)
        }
    }

    /**
     * **Client → host. "My radio went down", and on which side of D-187's line** (rule 10; T2,
     * D-222; D-223).
     *
     * **One fixed shape, one field, one length.** [cause] is `permit` (a permission switched off,
     * D-310, or the Bluetooth radio turned off, D-311), `person` (the phone locked or taken out of the app mid-round
     * and now back, D-229) or `device` (reset, unsupported), three serial names of six letters
     * each, so the body is the same number of bytes whichever it carries and from every phone,
     * whatever its role and whether it is out. No reason beyond that, no time, no seat. A reader of
     * the Wi-Fi learns that some phone's radio went down and whether a person did it, which the
     * room learns at the next meeting for a person anyway, and nothing about a role. Sent once per
     * fault onset (`SwitchboardRadio`), never per callback. The seat comes off the connection, like
     * every upward body's.
     *
     * ### [onset] names the onset, so a report sent twice is heard once (D-245)
     *
     * A report is kept on the phone until the house answers [FaultHeard], and sent again on every
     * resume until then, across a socket that failed while the phone was suspended and across the
     * process itself (iOS ends an app whose Camera or Motion grant is withdrawn). So the house can be
     * handed one onset twice, and [onset] is how it tells: [ONSET_CHARS] lowercase hex characters,
     * drawn fresh and at random for each onset by the phone. **Random and fixed-width, never a
     * counter or a time**: a counter's digits would grow with how often this phone had faulted, and
     * a time would say when; this says neither, to the house or to a reader of the Wi-Fi.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("radioFaulted")
    data class RadioFaulted(val cause: RadioCause, val onset: String) : RoundBody() {
        init {
            require(onset.length == ONSET_CHARS && onset.all { it in '0'..'9' || it in 'a'..'f' }) {
                "an onset is $ONSET_CHARS lowercase hex characters"
            }
        }

        companion object {
            /** Eight random bytes, as hex: one onset's name (D-245). */
            const val ONSET_CHARS: Int = 16
        }
    }

    /**
     * **Host → the one phone that reported. "I have your report of [onset]."** (D-245)
     *
     * The house's answer to [RadioFaulted], sent to the seat whose connection carried it and to no
     * other, **whatever the rules made of the report**: named, recorded only, refused, or heard
     * before. So it says nothing about the phone that receives it, about a role or about whether the
     * seat is out (rule 1): every report is answered, once per time it arrives, by this one body of
     * one length. Not a [Push]: it is transport bookkeeping, like [Gap], spends no sequence number
     * and reaches no screen. The phone stops keeping the report when it arrives.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("faultHeard")
    data class FaultHeard(val onset: String) : RoundBody()
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
    fun decodeOrNull(text: String): RoundBody? = if (foreign.isForeign(text)) {
        // A lobby body, by its leading type: what the decode below would have thrown over (D-276).
        null
    } else {
        try {
            json.decodeFromString(RoundBody.serializer(), text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private val foreign = ForeignBody(RoundBody.serializer().descriptor)

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
        is Effect.AbilityFired -> RoundMessage.AbilityFired.of(effect.cooldownStarted, effect.touch)
        // The seat comes off and only the padding is left: AbilityFired's length (D-263).
        is Effect.AbilityBlanked -> RoundMessage.AbilityBlanked.Padded
        // The seat comes off; the pad is what makes this frame an armed Insider's length.
        is Effect.ContactAnswered -> RoundMessage.ContactAnswered(RoundMessage.ContactAnswered.PAD)
        is Effect.Surged ->
            RoundMessage.Surged(effect.peak, effect.black, effect.sound, effect.haptic)
        is Effect.IsolateTargetsOffered -> RoundMessage.IsolateTargetsOffered(effect.targets)
        // The targets do not cross: only the width of the offer they make does (D-258).
        is Effect.IsolateTargetsBlanked -> RoundMessage.IsolateTargetsBlanked.over(effect.targets)
        is Effect.StaticOpened -> RoundMessage.StaticOpened(effect.seconds, effect.seed)
        is Effect.SubroutineGraded -> RoundMessage.SubroutineGraded(effect.accepted)
        is Effect.SubroutineProgressed -> RoundMessage.SubroutineProgressed(effect.remaining)
        is Effect.SystemIntegrityLive -> RoundMessage.SystemIntegrityLive(effect.percent)
        is Effect.SystemIntegrityBatched -> RoundMessage.SystemIntegrityBatched(effect.percent)
        is Effect.MessageDelivered -> RoundMessage.MessageDelivered(effect.body)
        is Effect.WorkOrderIssued -> RoundMessage.WorkOrderIssued(effect.lines)
        is Effect.ScanAnswered -> RoundMessage.ScanAnswered(effect.opened)
        is Effect.TerminalAnswered -> RoundMessage.TerminalAnswered(effect.reading)
        // The seat comes off and nothing is left: the socket is the address (D-318).
        is Effect.CardUnregistered -> RoundMessage.CardUnregistered
        is Effect.PresenceChanged -> RoundMessage.PresenceChanged(effect.at, effect.open)
        is Effect.OpeningMessage -> RoundMessage.OpeningMessage(effect.haptic)
        // **The seat comes off and the role narrows to one bit about the recipient.**
        // `Role` is deliberately neither `@Serializable` nor `@ClientFacing`, so there is
        // no way to put the authority's own type on a socket and no reason to want one:
        // what a phone needs is whether page 2 answers its thumb, and rule 3 says redact
        // by constructing a narrower type rather than by carrying the wide one.
        is Effect.RoleRevealed -> RoundMessage.RoleRevealed(effect.role == Role.Insider, effect.line)
        // The seat comes off; the list is the same list on every wire it goes down.
        is Effect.RosterIssued -> RoundMessage.RosterIssued(effect.roster)
        // The seat comes off; the plan is the same plan on every wire it goes down (D-316).
        is Effect.PlanIssued -> RoundMessage.PlanIssued(effect.plan)
        // The seat comes off; the seed is the message.
        is Effect.ContactSeedIssued -> RoundMessage.ContactSeedIssued(effect.seed)
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
        is Effect.NoticeIssued -> RoundMessage.NoticeIssued(effect.unreachable, effect.switchedOff)
        is Effect.ReadyProgressed -> RoundMessage.ReadyProgressed(effect.ready, effect.expected)
        is Effect.VoteHeld -> RoundMessage.VoteHeld(effect.selection, effect.locked)
        is Effect.VoteSelectionShown ->
            RoundMessage.VoteSelectionShown(effect.voter, effect.selection)
        is Effect.VoteProgressed -> RoundMessage.VoteProgressed(effect.locked, effect.expected)
        is Effect.MeetingResult ->
            RoundMessage.MeetingResult(effect.restrained, effect.voted, effect.living, effect.haptic)
        is Effect.MeetingResolved -> RoundMessage.MeetingResolved(
            effect.restrained,
            effect.attribution.map { (voter, selection) -> RoundMessage.Cast(voter, selection) },
        )
        is Effect.RestrainedTakeover -> RoundMessage.RestrainedTakeover(effect.haptic)
        // The seat comes off and only the pad is left: the touch's one frame size (S1, D-219).
        is Effect.RevokeLanded -> RoundMessage.RevokeLanded.of(effect.haptic)
        is Effect.MeetingEnded -> RoundMessage.MeetingEnded(effect.haptic)
        is Effect.EgressOpened ->
            RoundMessage.EgressOpened(effect.type, effect.nodes, effect.remaining, effect.haptic, effect.pulseFrom)
        is Effect.EgressHeld ->
            RoundMessage.EgressHeld(effect.remaining, effect.running, effect.haptic)
        is Effect.SyncPulseAnswered -> RoundMessage.SyncPulseAnswered(effect.held)
        // The seat comes off and only the padding is left: SyncPulseAnswered's length (C2).
        is Effect.SyncPulseBlanked -> RoundMessage.SyncPulseBlanked.Padded
        is Effect.EgressContained -> RoundMessage.EgressContained(effect.haptic)
        is Effect.EgressSucceeded -> RoundMessage.EgressSucceeded(effect.haptic)
        is Effect.RoundEnded -> RoundMessage.RoundEnded(effect.winner, effect.by, effect.haptic, effect.integrity)
        is Effect.InsidersRevealed -> RoundMessage.InsidersRevealed(effect.insiders)
        is Effect.HouseSignedOff -> RoundMessage.HouseSignedOff(effect.body)
        is Effect.HouseClosedOnPermission -> RoundMessage.HouseClosedOnPermission
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

/**
 * Bytes as lowercase hex and back: the one encoding on this wire that no layer escapes, so a
 * field's width is its length (D-249, D-258).
 */
internal object WireHex {
    private const val DIGITS = "0123456789abcdef"

    fun of(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out.append(DIGITS[v shr 4]).append(DIGITS[v and 0xf])
        }
        return out.toString()
    }

    fun bytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        ((DIGITS.indexOf(hex[2 * i]) shl 4) or DIGITS.indexOf(hex[2 * i + 1])).toByte()
    }

    fun isHex(text: String): Boolean = text.all { it in '0'..'9' || it in 'a'..'f' }
}

/**
 * **Whether a body is plainly another family's, read off its leading `"type"` without decoding it**
 * (rule 7, D-276).
 *
 * One `Carry` holds a lobby body or a round body, and each side asks both decoders in turn: every
 * phone hands every round frame to `LobbyWire` first, and the host hands every lobby body to
 * `RoundWire` first. The losing decode used to find out by throwing, and an exception on Kotlin/Native
 * captures its stack and builds a long message: a few kilobytes for every push and every upload's
 * answer, on every phone, all round.
 *
 * Exact, not a heuristic: it answers true only for a body that begins `{"type":"NAME"` where NAME is
 * not one of [family]'s serial names, read off its own descriptor so it cannot fall behind a new
 * member. That body's decode is a polymorphic lookup of NAME in [family], which fails, so the answer
 * was null either way. Anything else, whitespace, another key first, an escape in the name, is
 * decoded in full as it always was.
 */
internal class ForeignBody(family: kotlinx.serialization.descriptors.SerialDescriptor) {
    private val names: List<String> = family.getElementDescriptor(1).let { members ->
        List(members.elementsCount) { members.getElementName(it) }
    }

    fun isForeign(text: String): Boolean {
        if (!text.startsWith(LEAD)) return false
        val end = text.indexOf('"', LEAD.length)
        if (end < 0) return false
        val length = end - LEAD.length
        for (name in names) {
            if (name.length == length && text.regionMatches(LEAD.length, name, 0, length)) return false
        }
        // An escape inside the name is decoded in full, whatever it spells.
        for (i in LEAD.length until end) if (text[i] == '\\') return false
        return true
    }

    private companion object {
        const val LEAD = "{\"type\":\""
    }
}
