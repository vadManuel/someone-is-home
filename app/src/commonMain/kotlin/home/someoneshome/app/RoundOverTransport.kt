package home.someoneshome.app

import home.someoneshome.model.ContactWindow
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.wallClockMillis
import home.someoneshome.platform.transport.IntentOutbox
import home.someoneshome.platform.transport.RoundInbox
import home.someoneshome.ui.FlowModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * ### Two things go up, and they are different in kind
 *
 * [RoundBody.Gap] names no game action, can be refused by nothing, and is answered with bytes this
 * phone was already sent. [RoundBody.Act] is the other thing: a control the player pressed, which
 * the authority may refuse and which may change the round. They are separate members rather than
 * one upward body so that the house's two answers — *replay what you missed* and *put this to the
 * rules* — can never be reached by the same branch.
 *
 * ### What goes up carries no seat, and cannot
 *
 * [act] takes an [IntentMessage], which has no field an actor could ride in. This phone does not
 * know its own seat here and does not need to: the desk at the other end stamps the seat the
 * connection was admitted to. See [IntentMessage].
 */
class RoundOverTransport(
    private val scope: CoroutineScope,
    /**
     * **The one consumer.** Every push in this app reaches a screen through this object and no
     * other; the in-process source hands the same [RoundMessage] to the same method.
     */
    private val flow: FlowModel,
    /**
     * **The motor, and it is not optional** (E-D1-1, closed here).
     *
     * Every push that reaches a screen reaches it through [apply], so this is the one place a
     * delivered message can be turned into a vibration — and it is a **required** constructor
     * parameter rather than a defaulted one on purpose. A default would be exactly the arrangement
     * that made this unit necessary: for weeks `HouseBuzzer` was real, tested, and constructed
     * nowhere but a bench, so five phones in a dark house were pushed thirty-eight kinds of message
     * and not one of them buzzed. With no default, a root that has not decided what the motor is
     * does not compile, and a target with no motor says so out loud by passing
     * [home.someoneshome.platform.SilentHaptics].
     *
     * It is here rather than beside `flow.houseSaid` in `ui` because `ui` cannot see `platform`, and
     * D-134 wants it here anyway: **the buzz rides the effect**, not a table keyed on screens.
     */
    private val buzzer: HouseBuzzer,
    /** Client → host, up this phone's own socket. */
    private val send: suspend (TransportFrame) -> Unit,
    /**
     * **The contact radio, and it is not optional either** (D-217), for [buzzer]'s reason: a root
     * that has not decided what the radio is does not compile. A phone with no radio yet says so
     * out loud by passing [home.someoneshome.platform.NoRadio], and still uploads on the cadence.
     */
    private val radio: ContactRadio,
    /**
     * **The screen kept awake for the round, and it is not optional either** (T4, D-221), for
     * [radio]'s reason. Held from arming (`ContactSeedIssued`) until the round ends or this phone
     * leaves, then put back. A root with no idle timer passes one over
     * [home.someoneshome.platform.NoIdleTimer].
     */
    private val wake: ScreenWake,
    /** The window this phone is in now: the wall clock, which every phone and the house share. */
    private val windowNow: () -> ContactWindow = { ContactWindow.containing(wallClockMillis()) },
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
    /**
     * **Every body this client sends up, before it reaches the socket.**
     *
     * [onInbound]'s mirror, empty in every production construction, and here because the upward
     * channel raises the same question in the harder direction: what comes down is the house's own
     * bytes, but what goes up is composed on a phone that holds a name and a one line in memory.
     * The types make it structurally impossible — no [IntentMessage] member has a field a line
     * could sit in — and this is how that claim is checked against real frames on a real socket
     * rather than argued from the declarations.
     *
     * **Whoever takes this must not print it.**
     */
    private val onOutbound: (String) -> Unit = {},
) {

    private val inbox = RoundInbox()
    private val outbox = IntentOutbox()

    /** This phone's half of the contact handshake (D-217). Fed every applied push; beaten by [upload]. */
    private val contacts = ContactUploader(radio, windowNow) { sighted -> up(RoundWire.encode(sighted)) }

    /** Whether the house has put this phone out, as the uploader was told. For the bench and tests. */
    val outOfTheRound: Boolean get() = contacts.isOut

    /** Whether this phone holds a seed and is uploading on the cadence. For the bench and tests. */
    val uploading: Boolean get() = contacts.inRound

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
                // **The motor first, then the screen, and the order is load-bearing.** A message
                // that moves this phone (`MeetingOpened` to the ring, `ScanAnswered` to the caught
                // scan) reaches `showing` through the panel's own arrival, and `showing` reads what
                // the motor is already doing to decide whether the ring needs stopping. Buzzing
                // second would put the screen change *before* the ring it belongs to, and the phone
                // would answer a call that had not started.
                //
                // Both take the same object. A duplicate is dropped above and a gap is not applied
                // at all, so **a buzz happens exactly where a screen change happens** — the count
                // of times a phone goes off cannot drift from the count of things it was told,
                // which is the channel D-156 closed.
                is RoundInbox.Landing.Apply -> {
                    buzzer.heard(landing.message)
                    flow.houseSaid(landing.message)
                    // The radio last: nothing it does can move a screen, and the seed and the
                    // out-of-the-round facts are read off the same applied message, once.
                    contacts.heard(landing.message)
                    when (keepsTheScreenAwake(landing.message)) {
                        true -> wake.hold()
                        false -> wake.release()
                        null -> Unit
                    }
                }
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
            // Upward only, and this direction is where it would matter most: a request arriving at
            // a client is somebody else's control press being shown to a phone that has no business
            // knowing it happened. Ignored rather than decoded any further.
            is RoundBody.Act -> Unit
            // The Egress tile, upward only, and it is [RoundBody.Act]'s case exactly: a request
            // arriving at a client is somebody else's finger being shown to a phone that has no
            // business knowing it moved — and on this one member that phone would be learning
            // there is an Insider in the house and which socket they are on.
            is RoundBody.Fire -> Unit
            // Upward only, like the two above: what somebody else's radio heard is theirs and the
            // house's, never another phone's (D-212).
            is RoundBody.Sighted -> Unit
        }
    }

    /**
     * **The socket came back: ask for whatever was said while it was gone** (D-097, G3).
     *
     * The same request a gap makes, by its other trigger. Called on every seating and not only on
     * the first — a phone that dropped and resumed is a phone that has missed an unknown amount,
     * and the house's answer costs it a replay of bytes this phone discards.
     */
    fun reseated() {
        // The outbox first, and the order is the decision: this phone has asked this house for
        // nothing on this connection, and the desk at the other end cleared its mark when the
        // socket was admitted. A request made just before the drop is deliberately NOT resent --
        // see IntentOutbox. Asking for the pushes is what corrects the screen instead.
        outbox.reseated()
        ask(inbox.askAgain())
    }

    /**
     * **A control the player pressed, on its way to the house** (rule 10 — a request, not a fact).
     *
     * Fire-and-forget, because D-097 says so: *clients hold no request/response posture*. Nothing
     * here waits for an answer, nothing here retries, and nothing here moves a screen. What the
     * player sees is whatever the house pushes back, whenever it arrives — and a request the house
     * refuses produces a re-assertion that is indistinguishable from ordinary delivery, which is
     * what keeps the refusal invisible.
     *
     * The sequence [IntentOutbox] stamps is a dedup key rather than an ack: a frame delivered twice
     * must not be acted on twice.
     */
    fun act(intent: IntentMessage) = up(RoundWire.encode(outbox.ask(intent)))

    /**
     * **The Egress tile, held down** ([RoundBody.Fire], E-P2-5 / E-P3-7 closed).
     *
     * [act]'s every sentence applies — fire-and-forget, no ack, no retry, and no screen moved. What
     * the actor gets back is `Effect.AbilityFired` and its own clock, and what the *house* gets is
     * an alert on every phone in it; neither is this method's business.
     *
     * It is a second method rather than an [IntentMessage] because the frame is a different member:
     * the house has to make a draw before there is a fact to admit. There is nothing to pass.
     */
    fun fireEgress() = up(RoundWire.encode(outbox.fire()))

    /** A new round, or this phone has left: it holds nothing of the last one, and lets the screen sleep. */
    fun roundEnded() {
        inbox.roundEnded()
        contacts.roundEnded()
        wake.release()
    }

    /**
     * **One beat of the contact upload** (D-212 ruling 3, R8, D-217): one `RoundBody.Sighted`, at one
     * size, while this phone is in a round, whatever it is and whether or not it is out.
     */
    fun beat() = contacts.beat()

    /**
     * **The fixed cadence, forever** — [beat] every `SightingUpload.CADENCE_MILLIS`. Run on the same
     * dispatcher as [run], so the uploader's facts are written and read on one thread.
     */
    suspend fun upload() {
        while (true) {
            delay(SightingUpload.CADENCE_MILLIS)
            beat()
        }
    }

    private fun ask(gap: RoundBody.Gap) = up(RoundWire.encode(gap))

    private fun up(body: String) {
        onOutbound(body)
        scope.launch { send(TransportFrame.Carry(body)) }
    }
}

/**
 * **Whether a push starts or ends the screen being kept awake** (T4, D-221): true at arming, false
 * at the round's end, null for everything else.
 *
 * Arming is `ContactSeedIssued`, the push that starts this phone's radio, and every seated phone is
 * sent one at the same moment, whatever its role. A resume re-asserts it, and a second hold is
 * harmless. One named question so that a later "awake from somewhere else" changes only the answer.
 */
internal fun keepsTheScreenAwake(message: RoundMessage): Boolean? = when (message) {
    is RoundMessage.ContactSeedIssued -> true
    is RoundMessage.RoundEnded -> false
    else -> null
}
