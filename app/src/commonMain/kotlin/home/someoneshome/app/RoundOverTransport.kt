package home.someoneshome.app

import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.transport.RoundInbox
import home.someoneshome.ui.FlowModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * **The round's client half: frames off the socket, into the one consumer.**
 *
 * `ui` cannot see `platform` and `platform` cannot see `ui`, so this is where the gap detector and
 * the screens are introduced — the same seam [LobbyOverTransport] is for the lobby, and the same
 * reason [SavedHomesDocument] exists for the filesystem.
 *
 * ### It holds no policy of its own
 *
 * What arrives is decided by [RoundInbox] — in order, already held, or missing something — and what
 * a message *does* is decided by [FlowModel.houseSaid], which is the one consumer and the only
 * place a push may touch a screen. This class does the two things neither of those can: it decodes,
 * and it asks.
 *
 * ### Asking is not an Intent
 *
 * The one thing this sends upward is [RoundBody.Gap], which names no game action, can be refused by
 * nothing, and is answered with bytes this phone was already sent. The intent channel — a tap the
 * authority may turn down — does not exist yet and is not built here.
 */
class RoundOverTransport(
    private val scope: CoroutineScope,
    /**
     * **The one consumer.** Every push in this app reaches a screen through this object and no
     * other; the in-process source hands the same [RoundMessage] to the same method.
     */
    private val flow: FlowModel,
    /** Client → host, up this phone's own socket. */
    private val send: suspend (TransportFrame) -> Unit,
    private val onEvent: (String) -> Unit = {},
    /**
     * **Every body that arrives at this client, before anything is done with it.**
     *
     * Empty in every production construction, exactly as [LobbyOverTransport]'s is, and here for
     * the reason that one was added: *nothing carrying a line ever comes down* is a claim the types
     * make structurally, and a phone on a real network is where a wrong turn in the house's
     * delivery would actually show. It is handed the raw body rather than the decoded shape, so
     * that a body this build cannot decode is still seen.
     *
     * **Whoever takes this must not print it.**
     */
    private val onInbound: (String) -> Unit = {},
) {

    private val inbox = RoundInbox()

    /**
     * **Frames waiting to be applied, so that a screen is never written from a socket's thread.**
     *
     * [TransportClient] delivers on the client engine's threads and [PanelState] is Compose state
     * read by the composition — so the hand-over has to be a queue, and there has to be exactly one
     * of them or two frames could be applied at once and land in either order. This is D4's *one
     * ordered event queue* at the client end, and [run] is the single consumer that drains it.
     */
    private val inbound = Channel<TransportFrame>(Channel.UNLIMITED)

    /** How far this phone is, in its own stream. For the bench and the authority's log. */
    val held: Long get() = inbox.held

    /**
     * A frame arrived on this phone's connection. **Safe to call from anywhere** — it queues, and
     * nothing here touches a screen.
     */
    fun frameArrived(frame: TransportFrame) {
        inbound.trySend(frame)
    }

    /**
     * Drain the queue on the caller's dispatcher, forever. The caller is the app root, on the main
     * thread, which is where a screen may be written from and the only place it may be.
     */
    suspend fun run() {
        for (frame in inbound) apply(frame)
    }

    /**
     * One frame, applied.
     *
     * Anything that is not a round body is left alone — a `Carry` also holds the lobby's standing,
     * and a round that treated an unrecognised body as an error would take a player's connection
     * down over a message meant for another part of the app.
     */
    private fun apply(frame: TransportFrame) {
        val carried = (frame as? TransportFrame.Carry)?.body ?: return
        onInbound(carried)
        val body = RoundWire.decodeOrNull(carried) ?: return
        when (body) {
            is RoundBody.Push -> when (val landing = inbox.arrived(body)) {
                is RoundInbox.Landing.Apply -> flow.houseSaid(landing.message)
                // The re-assertion arriving over what this phone already holds. Silent by design:
                // there is nothing for a player to do about it and nothing for them to see.
                is RoundInbox.Landing.Duplicate -> Unit
                is RoundInbox.Landing.Gap -> {
                    // Loud to the authority, invisible to the player (rule 6). The screen keeps
                    // saying what it last legitimately said until the house has caught it up.
                    onEvent("A FRAME WAS MISSED — HOLDING AT ${landing.held}")
                    ask(RoundBody.Gap(landing.held))
                }
            }
            // The house's to send, never to receive. A client asking another client for its stream
            // is saying nothing this phone is obliged to hear.
            is RoundBody.Gap -> Unit
        }
    }

    /**
     * **The socket came back: ask for whatever was said while it was gone** (D-097, G3).
     *
     * The same request a gap makes, by its other trigger. Called on every seating and not only on
     * the first — a phone that dropped and resumed is a phone that has missed an unknown amount,
     * and the house's answer costs it a replay of bytes this phone discards.
     */
    fun reseated() = ask(inbox.askAgain())

    /** A new round: this phone holds nothing of the last one. */
    fun roundEnded() = inbox.roundEnded()

    private fun ask(gap: RoundBody.Gap) {
        scope.launch { send(TransportFrame.Carry(RoundWire.encode(gap))) }
    }
}
