package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import kotlin.jvm.JvmInline

/**
 * The transport wire format — story 0.8's frames, and nothing of the game's.
 *
 * These are the shapes the websocket carries, **below the effect boundary**. Everything the game
 * says to a client rides inside [TransportFrame.Carry] as an opaque body that the emit boundary
 * ([home.someoneshome.model.EmitSchema]) already addressed and permitted; the frame layer cannot
 * widen an audience because it never learns what it is carrying. The only things a frame may name
 * are transport identity and delivery bookkeeping — a seat token, a proposal number — which is
 * what keeps this layer buildable while the core loop is still being reconsidered.
 *
 * ### Versioned, and the discriminators are frozen
 *
 * D8: wire formats are versioned JSON. [TRANSPORT_PROTOCOL] names this shape; the `@SerialName`
 * strings are the wire and may never be renamed in place — a rename is a new protocol version. A
 * test pins each one.
 */
const val TRANSPORT_PROTOCOL: Int = 1

/**
 * The credential that gets a client back into *its own* seat, and nothing else.
 *
 * Issued by the host at join, stored by the client, presented on resume. Attribution is by
 * connection — a client never names itself, and **a lobby code gets you *a* seat, never *that*
 * seat**. The token is the whole mechanism behind that sentence: without it, a second websocket
 * asserting it is another player is the one cheat in this game that is remote, undetectable and
 * requires no physical act.
 *
 * Carries no ground truth: it is an opaque string the host minted, meaningful only to the host's
 * own ledger.
 */
@ClientFacing
@kotlinx.serialization.Serializable
@JvmInline
value class SeatToken(val value: String)

/**
 * Why the host declined a connection. Names a fact about the *connection*, never about the game.
 *
 * D-068 is what makes a round-state reason safe to send: whether the round is armed is publicly
 * observable by everyone standing in the house, so telling a connection "the round is locked" is
 * telling it what its own eyes already see.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class TransportRefusal {
    /** The round is armed. Joining ended when the lights went out; only resume readmits. */
    RoundLocked,

    /** The presented token matches no seat. The connection is nobody the ledger knows. */
    UnknownToken,

    /** Every seat is held. */
    NoFreeSeat,
}

/**
 * One websocket message, either direction.
 *
 * Client → host: [Hello], [Resume], [Ack], [Carry].
 * Host → client: [Seated], [Refused], [Proposed], [Commit], [Carry].
 *
 * Marked [ClientFacing] as a whole because every frame is physically incapable of carrying ground
 * truth: the game content in [Carry] is an already-redacted body this layer never constructs, and
 * every other field is transport bookkeeping.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class TransportFrame {

    /** A fresh connection asking for **a** seat. Pre-arm only; carries no identity at all. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("hello")
    data class Hello(val protocol: Int) : TransportFrame()

    /** A returning connection presenting its stored token, asking for **that** seat. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("resume")
    data class Resume(val token: SeatToken) : TransportFrame()

    /** The host seats the connection. The token is the client's to store; it learns nothing else. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("seated")
    data class Seated(val token: SeatToken) : TransportFrame()

    /**
     * The host declines the connection — a distinct kind, not a quieter success.
     *
     * The same shape D-080 gave the admission gate: refusing has to be a different *kind* of
     * answer, because an absent effect is this project's definition of a leak.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("refused")
    data class Refused(val reason: TransportRefusal) : TransportFrame()

    /**
     * The event protocol's first leg: broadcast → ack → commit (D5). The body is opaque here for
     * the same reason [Carry]'s is.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("proposed")
    data class Proposed(val proposal: Long, val body: String) : TransportFrame()

    /** A client confirming it holds [proposal]. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("ack")
    data class Ack(val proposal: Long) : TransportFrame()

    /** The host committing [proposal] after acks (or after the 2 s timeout, without the missing). */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("commit")
    data class Commit(val proposal: Long) : TransportFrame()

    /**
     * The opaque channel. The body is whatever the emit boundary rendered for this client —
     * addressed and permitted before this layer ever saw it — or, client → host, an intent the
     * authority may refuse. The frame layer carries it and can do nothing else with it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("carry")
    data class Carry(val body: String) : TransportFrame()
}
