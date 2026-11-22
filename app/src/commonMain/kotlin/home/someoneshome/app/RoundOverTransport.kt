package home.someoneshome.app

import home.someoneshome.model.ContactWindow
import home.someoneshome.model.MarkerId
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.NoRanging
import home.someoneshome.platform.RangingRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.monotonicMillis
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
    /**
     * **Where the reports this phone owes the house are kept across the process** (D-245), beside
     * the seat token: [DeviceSeat] on a phone. By default a store of this object's own, which fails
     * with it, as every report did before D-245.
     */
    private val kept: SeatStore = MemorySeat(),
    /**
     * **This phone ended its round on a later round's push** (V1, D-267), after its own half and
     * before the panel's. `RoundClient` forgets its bench's copy of the round here; empty otherwise.
     */
    private val onRoundTurned: () -> Unit = {},
    /**
     * **This phone's monotonic milliseconds: the clock its socket's estimator is on** (D-295). The
     * same function `LobbyOverTransport` hands its `TransportClient`, so a probe's send and its
     * answer's arrival are read on the clock the offset is an offset of.
     */
    private val clientMillis: () -> Long = ::monotonicMillis,
    /**
     * **One upload's round trip on the house's clock, handed down to the socket's estimator**
     * (D-295): `LobbyOverTransport.uploadAnswered` on a phone. Every answer to every upload this
     * phone sends is one sample, so the offset is refreshed every second rather than measured once
     * at join. Nothing by default, for a construction with no socket to refresh.
     */
    private val clock: suspend (sentAtMillis: Long, heardAtMillis: Long, answeredAtMillis: Long, receivedAtMillis: Long) -> Unit =
        { _, _, _, _ -> },
    /**
     * **This phone's UWB radio** (U6, D-300), handed to the uploader. The app's root passes the
     * Nearby Interaction radio in every round (D-308); defaulted to [NoRanging], unlike [radio], for
     * a test whose question is not ranging, which then plays a round in which nobody can be touched
     * or reported.
     */
    private val ranging: RangingRadio = NoRanging,
    /**
     * **The grants that read switched off now, one bit a row** (D-310), handed to the uploader:
     * `Permissions.refusedGrants` on a phone. None by default, for a construction with no OS behind
     * it.
     */
    private val refusedGrants: () -> Int = { 0 },
) {

    private val inbox = RoundInbox()
    private val outbox = IntentOutbox()

    /**
     * **Every request this phone makes in a round, waiting for a beat's slot** (D-263). Read once a
     * beat by the uploader; answered by `RoundBody.UploadHeard`; emptied when the round ends here.
     */
    private val requests = SlotRequests(onEvent = onEvent)

    /** **Each upload's clock probe, and the answers matched to them** (D-295). On [scope], with the beat. */
    private val uploadClock = UploadClock(clientMillis)

    /** This phone's half of the contact handshake (D-217). Fed every applied push; beaten by [upload]. */
    private val contacts = ContactUploader(
        radio, windowNow,
        send = { sighted -> up(RoundWire.encode(sighted)) },
        // **The radio went down: one fixed body, up this phone's own socket** (T2, D-222; D-223).
        // Kept until the house answers it, and sent again on each new connection until then (D-245).
        faulted = { body -> up(RoundWire.encode(body)) },
        kept = kept,
        onEvent = onEvent,
        // **Every request rides the beat** (D-263): the slot is filled at the upload, never sent alone.
        ask = requests::slot,
        // **And the house's clock rides the beat too** (D-295): one probe an upload, every phone alike.
        probe = uploadClock::stamp,
        // **And the UWB report** (D-300): capacity, spare tokens, nearest distances, one width.
        ranging = ranging,
        // **And whether the person is on the main view** (D-307): asked of the panel at each beat,
        // one character, every phone alike. The house's report hold reads it and nothing else does.
        mainView = { flow.onMainView },
        // **And the grants, so a permission switched off is told from a leave** (D-310).
        refusedGrants = refusedGrants,
    )

    /** How many requests this phone has made in the round that the house has not answered. For tests. */
    val requestsWaiting: Int get() = requests.count

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
    private val inbound = Channel<Arrived>(Channel.UNLIMITED)

    /**
     * **A frame, and when it reached this phone** (D-295). Stamped on the socket's thread, before
     * the queue: a wait for the main thread is not round trip, and counted as one it would read as
     * offset.
     */
    private class Arrived(val frame: TransportFrame, val atMillis: Long)

    /** How far this phone is, in its own stream. For the bench and the authority's log. */
    val held: Long get() = inbox.held

    /**
     * A frame arrived on this phone's connection. **Safe to call from anywhere** — it queues, and
     * nothing here touches a screen.
     */
    fun frameArrived(frame: TransportFrame) {
        inbound.trySend(Arrived(frame, clientMillis()))
    }

    /**
     * Drain the queue on the caller's dispatcher, forever. The caller is the app root, on the main
     * thread, which is where a screen may be written from and the only place it may be.
     */
    suspend fun run() {
        for (arrived in inbound) apply(arrived.frame, arrived.atMillis)
    }

    /**
     * One frame, applied.
     *
     * Anything that is not a round body is left alone — a `Carry` also holds the lobby's standing,
     * and a round that treated an unrecognised body as an error would take a player's connection
     * down over a message meant for another part of the app.
     */
    private suspend fun apply(frame: TransportFrame, arrivedAtMillis: Long) {
        val carried = (frame as? TransportFrame.Carry)?.body ?: return
        onInbound(carried)
        val body = RoundWire.decodeOrNull(carried) ?: return
        // **A later round's push to a phone still in an earlier one** (V1, D-267): the house ended a
        // round while this phone was off the air, and `LobbyBody.RoundEnded` is said once. This
        // phone ends its own round here, everything the round's end does on it, before the push is
        // applied, so round 2's role, seed and roster land on a phone that holds nothing of round 1.
        if (body is RoundBody.Push && inbox.turns(body)) {
            onEvent("A PUSH OF ROUND ${body.round} ON A PHONE STILL IN ROUND ${inbox.round} — THE ROUND'S END WAS MISSED, AND IS TAKEN HERE")
            roundEnded()
            onRoundTurned()
            flow.houseBeganAnotherRound()
        }
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
                    gapBeats = null
                    buzzer.heard(landing.message)
                    flow.houseSaid(landing.message)
                    // The radio last: nothing it does can move a screen, and the seed and the
                    // out-of-the-round facts are read off the same applied message, once.
                    contacts.heard(landing.message)
                    // A round begins or ends with nothing waiting from another one (D-263): a
                    // request left over from the last round must never be put to the next.
                    if (landing.message is RoundMessage.ContactSeedIssued || landing.message is RoundMessage.RoundEnded ||
                        landing.message is RoundMessage.HouseClosedOnPermission
                    ) {
                        requests.clear()
                    }
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
                    // **Once per point, per connection** (V2, D-268). Every push after a hole is a
                    // gap at the same point until the replay arrives, and each used to ask again:
                    // a burst behind one lost frame was that many full replays queued on this
                    // seat, past the cap, and the link dropped in a loop. The house answers one
                    // ask with everything after it; the next ask waits for a new point, or a new
                    // connection, or [GAP_PATIENCE_BEATS] beats with nothing applied.
                    if (gapBeats == null) gapBeats = 0
                    if (askedFrom != landing.held) {
                        askedFrom = landing.held
                        // Loud to the authority, invisible to the player (rule 6). The screen keeps
                        // saying what it last legitimately said until the house has caught it up.
                        onEvent("A FRAME WAS MISSED — HOLDING AT ${landing.held}")
                        ask(RoundBody.Gap(landing.held, inbox.round))
                    }
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
            // Upward only: another phone's radio going down is the house's to announce, at the
            // meeting, to everybody at once (T2, D-222), never one phone's to tell another.
            is RoundBody.RadioFaulted -> Unit
            // **The house has this phone's report** (D-245): it is no longer kept. Not a push, so it
            // spends no sequence and reaches no screen, lamp or motor.
            is RoundBody.FaultHeard -> contacts.acknowledged(body.onset)
            // **The house has this beat's slot** (D-263, D-275): every request under that id is done. Every
            // upload is answered with one of these, so it says nothing, and it reaches no screen.
            //
            // **And it is the house's clock, read twice** (D-295): one round trip of this phone's
            // probe, handed to the socket's estimator. Every answer to every upload, on every phone.
            is RoundBody.UploadHeard -> {
                requests.heard(body.id)
                // **And the house's ranging plan for this second** (D-300): to the radio.
                contacts.planned(body.plan)
                // **And the report's slot it carried, to the panel** (D-307): the report screen and
                // its circle, from page 1 or 2 only, back to the page on a cancel. Every answer on
                // every phone; on a phone that is not holding one it is idle and moves nothing.
                flow.reportSaid(contacts.report)
                // **And the map's field, to the panel** (D-316): the Terminal's counts on the reader's
                // phone, true occupancy on the couch, nothing on every other phone. Every answer, on
                // every phone; nothing moves a screen, it only feeds the plan the screen draws.
                flow.occupancySaid(contacts.mapped(body.map))
                uploadClock.answered(body, arrivedAtMillis) { sent, heard, answered, received ->
                    clock(sent, heard, answered, received)
                }
            }
        }
    }

    /** How many reports this phone owes the house and has not had answered (D-245). For tests. */
    val owing: Int get() = contacts.owing

    /**
     * **The report's slot, as the house last said it to this phone** (D-301): idle, held for so
     * long, or cancelled, off every answer's plan. The report screen draws it (D-307).
     */
    val report: home.someoneshome.model.protocol.ReportSlot get() = contacts.report

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
        // **Except a report the house has not answered** (D-245): it is a fact, not a request, and
        // the house hears each onset once, so it goes up again on this connection. On [scope],
        // where [run] and the beat read the uploader: a seating arrives on the socket's thread.
        scope.launch { contacts.reseated() }
        // On [scope] too, where [run] reads the inbox (V2, D-268): a new connection asks once, from
        // where this phone is, and the point it asked from is this connection's.
        scope.launch {
            askedFrom = inbox.held
            ask(inbox.askAgain())
        }
    }

    /**
     * **The point this connection last asked the house to replay from** (V2, D-268), or null. A gap
     * at that same point does not ask again. Read and written on [scope] only.
     */
    private var askedFrom: Long? = null

    /**
     * **Beats since a gap was seen with nothing applied after it** (V2, D-268), or null when no gap
     * is open. At [GAP_PATIENCE_BEATS] the ask goes up again: an answer that never came (a replay
     * that met a failing socket) must not leave a phone holding at one point for the rest of the round.
     */
    private var gapBeats: Int? = null

    /** One beat's look at an open gap (V2, D-268): see [gapBeats]. */
    private fun gapWatch() {
        val beats = gapBeats ?: return
        if (beats + 1 < GAP_PATIENCE_BEATS) {
            gapBeats = beats + 1
            return
        }
        gapBeats = 0
        askedFrom = inbox.held
        onEvent("A GAP STILL OPEN AT ${inbox.held} AFTER $GAP_PATIENCE_BEATS BEATS — ASKING AGAIN")
        ask(RoundBody.Gap(inbox.held, inbox.round))
    }

    /**
     * **A control the player pressed, on its way to the house** (rule 10 — a request, not a fact).
     *
     * Fire-and-forget as far as the player is concerned, because D-097 says so: *clients hold no
     * request/response posture*. Nothing here moves a screen or waits on an answer; the one thing
     * that does wait, in a round, is the transport's own resend of a lost beat (below). What the
     * player sees is whatever the house pushes back, whenever it arrives — and a request the house
     * refuses produces a re-assertion that is indistinguishable from ordinary delivery, which is
     * what keeps the refusal invisible.
     *
     * The sequence [IntentOutbox] stamps is a dedup key rather than an ack: a frame delivered twice
     * must not be acted on twice.
     *
     * ### In a round it is never a frame of its own (D-263)
     *
     * Once this phone holds the round's seed, the request waits for the next beat's slot and rides
     * up inside the upload every phone sends a beat at one size, retried under one id until the house
     * answers it ([SlotRequests]). Only before arming, when no request means anything to the rules and
     * every phone is in the same state, does it go up as its own `RoundBody.Act`.
     */
    fun act(intent: IntentMessage) {
        if (contacts.inRound) requests.ask(Asked.Act(intent)) else up(RoundWire.encode(outbox.ask(intent)))
    }

    /**
     * **The Egress tile, held down** ([RoundBody.Fire], E-P2-5 / E-P3-7 closed).
     *
     * [act]'s every sentence applies — fire-and-forget, no ack, no retry, and no screen moved. What
     * the actor gets back is `Effect.AbilityFired` and its own clock, and what the *house* gets is
     * an alert on every phone in it; neither is this method's business.
     *
     * It is a second method rather than an [IntentMessage] because the frame is a different member:
     * the house has to make a draw before there is a fact to admit. There is nothing to pass.
     *
     * In a round it rides the next beat's slot, for [act]'s reason (D-263): this is the press only an
     * Insider makes, and on its own frame it was the loudest one on the Wi-Fi.
     */
    fun fireEgress() {
        if (contacts.inRound) requests.ask(Asked.Fire) else up(RoundWire.encode(outbox.fire()))
    }

    /**
     * **A card this phone's camera read, on its way to the house as the id and nothing else**
     * (the owner's 2026-11-21 ruling, D-318).
     *
     * Every decoded in-round scan goes up as this, whatever is printed on the card: the same
     * request, the same shape and, in the fixed-width slot, the same size on every phone and for
     * both roles. **This phone decides nothing about the card.** It holds no map, so whether the
     * paper is work, the meeting card or NOT A MARKER is the house's answer, and the screen moves on
     * that answer and never on the read.
     *
     * In a round it rides the next beat's slot, for [act]'s reason (D-263). **Outside one it does
     * not go at all**: no phone holds a seed then, every phone is in the same state, and there is no
     * map to route a card against (D-067), so the read is said in this phone's log and nowhere else.
     */
    fun readCard(card: MarkerId) {
        if (contacts.inRound) requests.ask(Asked.Read(card)) else onEvent("A CARD READ BEFORE THE ROUND — NOTHING GOES UP (D-318)")
    }

    /**
     * **The house ended the round**: this phone holds nothing of it, and lets the screen sleep. What
     * the round's sequence still brings is stale (V1, D-267); the next round's starts afresh.
     */
    fun roundEnded() {
        inbox.roundEnded()
        forgetTheRound()
    }

    /**
     * **This phone walked out** (LEAVE, or a house it gave up on): [roundEnded]'s every reset, and no
     * round closed to it, so a phone that comes back to the same round by its token is told it again
     * from the start (D-097, D-260).
     */
    fun left() {
        inbox.left()
        forgetTheRound()
    }

    private fun forgetTheRound() {
        askedFrom = null
        gapBeats = null
        contacts.roundEnded()
        requests.clear()
        uploadClock.forget()
        wake.release()
    }

    /**
     * **One beat of the contact upload** (D-212 ruling 3, R8, D-217): one `RoundBody.Sighted`, at one
     * size, while this phone is in a round, whatever it is and whether or not it is out.
     */
    fun beat() {
        contacts.beat()
        gapWatch()
    }

    /**
     * **This phone left the foreground** (D-187, D-229): a lock, the side button or a swipe home. In
     * a round it is the onset of the evasion D-187 names, and the house is told once, when the phone
     * is back ([backInTheForeground]). Every phone alike, the host's own seat included; nothing on
     * the screen, the lamp or the motor hears of it (rules 5 and 6). Before arming and after the
     * round it is nothing.
     */
    fun leftTheForeground() {
        if (contacts.leftTheForeground()) onEvent("THIS PHONE LEFT THE FOREGROUND IN A ROUND — THE HOUSE IS TOLD WHEN IT IS BACK (D-187)")
    }

    /**
     * **This phone is back** (D-229). If it left during this round, the next beat sends the one fixed
     * body a person's radio outage sends, up this phone's own socket: `RoundBody.RadioFaulted` with
     * `RadioCause.Person`, or `RadioCause.Permission` when a grant now reads switched off (D-310). A
     * socket still coming back holds it until it is seated.
     */
    fun backInTheForeground() {
        if (contacts.backInTheForeground()) onEvent("BACK IN THE FOREGROUND — THE NEXT BEAT TELLS THE HOUSE, ONCE · PERSON")
    }

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
 * **Beats a gap may stay open with nothing applied before the phone asks again** (V2, D-268). Five
 * seconds at the upload's cadence: far past a replay in flight, short enough that a phone whose
 * answer was lost is not left holding for long.
 */
internal const val GAP_PATIENCE_BEATS: Int = 5

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
    // The house closed on a permission switched off again (D-310): the round is over.
    is RoundMessage.HouseClosedOnPermission -> false
    else -> null
}
