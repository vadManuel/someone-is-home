package home.someoneshome.app

import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.ui.FlowModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * **The cycle E-P2-3 named, broken by one indirection.**
 *
 * `RoundClient` wants a `FlowModel`, which wants a `LobbyModel`, which wants the
 * [LobbyOverTransport] that wants the [RoundSeam]. Something has to be built before the thing it
 * needs, and this is the smallest something: the lobby is handed *this*, the round is built
 * afterwards, and [becomes] joins them.
 *
 * **It buffers the way up rather than dropping it**, which is the only line in here that is a
 * decision. `linked` fires when a client connects, and on a phone that hosts its own house that
 * can happen before the round half exists — a seam that forgot the send would leave that phone
 * able to receive everything and press nothing, which is a failure with no symptom on either
 * screen.
 *
 * E-P2-3's real answer is a decision about how the app root holds the round's client half, and
 * this is deliberately not that decision: it is a bench getting out of its own way.
 */
class DeferredRoundSeam : RoundSeam {

    private var target: RoundSeam? = null
    private var buffered: (suspend (TransportFrame) -> Unit)? = null

    /** The round's client half exists now. Anything the lobby said before this is replayed. */
    fun becomes(round: RoundSeam) {
        target = round
        buffered?.let { round.linked(it) }
        buffered = null
    }

    override fun linked(send: suspend (TransportFrame) -> Unit) {
        target?.linked(send) ?: run { buffered = send }
    }

    override fun arrived(frame: TransportFrame) {
        target?.arrived(frame)
    }

    override fun seated() {
        target?.seated()
    }

    override fun left() {
        target?.left()
    }

    override fun roundEnded() {
        target?.roundEnded()
    }

    override fun leftTheForeground() {
        target?.leftTheForeground()
    }

    override fun backInTheForeground() {
        target?.backInTheForeground()
    }
}

/**
 * **One phone's half of the round: the lobby's socket, the gap detector, and the screens.**
 *
 * [RoundSeam] filled in — the first caller that seam has ever had. It owns a [RoundOverTransport]
 * and does two things around it that the transport deliberately does not: it remembers the way up
 * across reconnects, and it **reads the stream out loud** so that a device console can be evidence
 * about what this phone was told.
 *
 * ### What it prints, and the two things it will not
 *
 * Every push is logged by **kind**, with the fields that are counts and phases, and the running
 * sequence number. That is what makes a five-seat log answer *did this reach every phone* and
 * *did this reach only one*, which is the whole question the per-seat streams exist to raise.
 *
 * **It never prints a one line and it never prints a name.** The reveal carries both on purpose
 * (D-116) and this is the one place in the round where they are legitimately on a phone — so the
 * reveal is logged as *how many* Insiders were named and whether each carried a line, never as
 * who or what. A line in a device log is a line on a phone, and D3 settled that for the lobby;
 * this is the same rule one wire down. [LineWatch] is pointed at the same bodies and answers the
 * sharper question — *did anything carrying MY line come down* — from the raw bytes.
 */
class RoundClient(
    private val scope: CoroutineScope,
    private val flow: FlowModel,
    private val watch: LineWatch,
    /**
     * **The motor this seat's pushes reach**, handed straight down to [RoundOverTransport].
     *
     * Taken rather than built, because there is one Taptic Engine on a phone and one session's worth
     * of it: `Cheats` builds the motor once and lends it to the haptic bench, the camera's scan
     * window and this. Two engines on one phone is two engines competing for one motor, and an
     * engine costs tens of milliseconds to start — which is spent at the moment of D-135's five.
     */
    private val buzzer: HouseBuzzer,
    /**
     * **This seat's contact radio**, handed straight down to [RoundOverTransport] (D-217).
     * The CoreBluetooth radio on a phone (`bluetoothContactRadio`, D-218); a `SimulatedRadio` on a
     * bench that plays seats in one process.
     */
    private val radio: ContactRadio,
    /**
     * **This phone's screen, kept awake through the round** (T4, D-221), handed straight down to
     * [RoundOverTransport]. The UIKit idle timer on a phone; [home.someoneshome.platform.NoIdleTimer]
     * on a bench.
     */
    private val wake: ScreenWake,
    /**
     * **Where this phone keeps the reports it owes the house** (D-245): beside the seat token.
     * `CheatRoot` passes [DeviceSeat]; a test's phone keeps them in memory unless it names a store.
     */
    private val kept: SeatStore = MemorySeat(),
    private val note: (String) -> Unit,
) : RoundSeam {

    /** The way up, re-handed on every connect. Null until this phone has a socket. */
    private var up: (suspend (TransportFrame) -> Unit)? = null

    /** Every kind this phone has been pushed, in arrival order. The evidence, as a tally. */
    private val kinds = mutableListOf<String>()

    /** What the house last issued this seat, so the bench can scan a card its own order names. */
    var order: List<OrderLine> = emptyList()
        private set

    /** The Subroutine this phone has open, as the client is allowed to see it — the question. */
    var open: SubroutineInstance? = null
        private set

    /** The card this phone last scanned, which is what an entry has to be handed over against. */
    var standingAt: MarkerId? = null
        private set

    /** What the script wants told about each push, over and above the log line. */
    var onMessage: (RoundMessage) -> Unit = {}

    val round: RoundOverTransport = RoundOverTransport(
        scope = scope,
        flow = flow,
        buzzer = buzzer,
        // The transport holds no socket of its own: there is one connection per phone and the
        // lobby owns it. A round that opened a second one would be a second seat.
        send = { frame -> up?.invoke(frame) },
        radio = radio,
        wake = wake,
        onEvent = note,
        onInbound = { body ->
            // The probe first, on the raw bytes, before anything has decided what they are — a
            // decoder that dropped an unrecognised body would hide exactly the frame worth seeing.
            watch.saw(body)
            read(body)
        },
        // The upward sweep: what goes up is composed on a phone that holds a name and a one line
        // in memory, which is the harder direction of the same question.
        onOutbound = { body -> watch.saw(body) },
        kept = kept,
        // A later round's push ended this phone's round (V1, D-267): the bench's copy goes too.
        onRoundTurned = {
            note("A LATER ROUND'S PUSH — THE ROUND'S END WAS MISSED, AND THIS PHONE HOLDS NOTHING OF IT")
            forgetTheBench()
        },
    )

    init {
        // Drained on the caller's dispatcher, which here is the composition's — the main thread,
        // and the only place a screen may be written from.
        scope.launch { round.run() }
        // **The contact upload's cadence, on the same dispatcher** (D-217). It sends nothing until a
        // seed arrives and nothing after the round ends, and inside a round it never stops: one
        // upload a beat, at one size, out or not (R8).
        scope.launch { round.upload() }
    }

    /**
     * **This phone has a way up to a house — which is the whole of what *a house is attached* means.**
     *
     * The one place in the app that fact exists, and therefore the one place it may be published
     * from. `LobbyOverTransport` calls this on every connect, on the **host over loopback** (it runs
     * the house and then joins it like everybody else) and on **every client** over the network —
     * and on nothing that is not in a round.
     *
     * ⚠️ **Telling the panel is not bookkeeping.** Until this line, whether the app was allowed to
     * invent things was decided by launch switches: the fake house, the presentation timers and
     * every screen fixture each asked *how was this process started* and none of them asked *is
     * there a house*. So the owner played three meetings that were not happening, opened a
     * Subroutine the house had not dealt, and read a meter nobody had sent. See
     * [FlowModel.houseAttached] and [PanelState.houseAttached] for what the answer switches off.
     *
     * Written on the main thread, which is where a connect is asked for: a lobby row under a thumb,
     * or the host's own LIGHTS OUT.
     */
    override fun linked(send: suspend (TransportFrame) -> Unit) {
        up = send
        note("A HOUSE IS ATTACHED — NOTHING ON THIS PHONE STANDS IN FOR IT FROM HERE")
        flow.houseAttached(true)
    }

    override fun arrived(frame: TransportFrame) = round.frameArrived(frame)

    override fun seated() {
        note("SEATED ON THIS CONNECTION — ASKING FOR WHAT WAS SAID WHILE IT WAS GONE")
        round.reseated()
    }

    override fun left() {
        note("THIS PHONE HUNG UP — IT HOLDS NOTHING OF THE ROUND, AND THE SCREEN MAY SLEEP")
        round.left()
        forgetTheBench()
    }

    override fun roundEnded() {
        note("THE HOUSE ENDED THE ROUND — THIS PHONE HOLDS NOTHING OF IT")
        round.roundEnded()
        forgetTheBench()
    }

    override fun leftTheForeground() = round.leftTheForeground()

    override fun backInTheForeground() = round.backInTheForeground()

    private fun forgetTheBench() {
        kinds.clear()
        order = emptyList()
        open = null
        standingAt = null
    }

    /** A control the player pressed. No seat rides with it and none can. */
    fun act(intent: IntentMessage) {
        note("PRESSED — ${intent::class.simpleName}")
        round.act(intent)
    }

    /**
     * **The Egress tile, held down** ([RoundBody.Fire]).
     *
     * A second method rather than an [IntentMessage] because the frame is a different member — the
     * house has to make a draw before there is a fact to admit, so the request carries nothing but
     * its dedup sequence. See [RoundOverTransport.fireEgress].
     *
     * **It is pressed on every living seat and admitted on one.** Nothing in this build tells a
     * phone its role, so a bench that pressed only where it would work would be a bench that knew
     * something no phone knows. Four of the five presses are refused in silence, cost nothing, and
     * are the guarantee working — see [RoundCheat.pressing].
     */
    fun fireEgress() {
        note("PRESSED — THE EGRESS TILE")
        round.fireEgress()
    }

    /**
     * **What this phone has been told, as a tally of kinds** — never as content.
     *
     * Printed beside every change rather than once at the end, for [LobbyCheat]'s reason: this
     * bench has no end, so a summary after the loop is a summary that never gets printed.
     */
    fun verdict(): String =
        "HELD ${round.held} · ${kinds.size} PUSH(ES) " +
            kinds.groupingBy { it }.eachCount().entries.sortedBy { it.key }.joinToString(
                prefix = "{", postfix = "}",
            ) { entry -> "${entry.key}=${entry.value}" }

    /**
     * One body, read for the log and handed to the script.
     *
     * This runs on whatever thread the socket delivered on, deliberately **before**
     * [RoundOverTransport] has queued anything: what is wanted here is a record of what *arrived*,
     * and the inbox's own decisions — in order, already held, missing something — are the next
     * question rather than this one. So a duplicate shows up here and a duplicate is worth seeing.
     */
    private fun read(body: String) {
        val push = RoundWire.decodeOrNull(body) as? RoundBody.Push ?: return
        val message = push.message
        kinds += message::class.simpleName ?: "?"
        note("PUSH ${push.seq} — ${describe(message)}")
        when (message) {
            is RoundMessage.WorkOrderIssued -> order = message.lines
            is RoundMessage.ScanAnswered -> {
                open = message.opened
                if (message.opened == null) standingAt = null
            }
            else -> Unit
        }
        onMessage(message)
    }

    /**
     * The card this phone is about to scan, remembered so an entry can be filed against it.
     *
     * ⚠️ **It tells the phone's own model as well now, and until N1 it did not** — which is how a
     * rehearsal handed entries over all evening while no phone a person held could. This class kept
     * [standingAt] for the script's benefit, `FlowModel` kept nothing, and BEGIN therefore had no
     * card to give `SubroutineModel.posted`. The bench's copy was the only one in the tree, so the
     * bench proved the seam and the product never reached it.
     *
     * [standingAt] stays, because it answers a different question: it is what the *script* knows it
     * scanned, and a rehearsal that read the phone's own state to decide what the phone should do
     * would be an instrument agreeing with itself. `FlowModel.scanning` is the one the player's
     * finger drives, through the scan bridge in `Cheats`.
     */
    fun scanning(marker: MarkerId) {
        standingAt = marker
        flow.scanning(marker)
    }

    /**
     * **One push, in words, with nothing in it that names a person or repeats a line.**
     *
     * The `when` is over the kinds this rehearsal actually drives and falls through to the class
     * name for the rest — which is honest about the sixteen kinds E-P1-4 records as having no
     * route and no field: they arrive, they are counted, and there is nothing more to say about
     * them than that they arrived.
     */
    private fun describe(m: RoundMessage): String = when (m) {
        is RoundMessage.OpeningMessage -> "OPENING MESSAGE · HAPTIC ${m.haptic}"
        // **This phone's own role, which is this phone's own business** (E-R2-2). Printed for
        // the same reason the caller's header is: the log is one seat's, and a rehearsal that
        // could not see the reveal arrive could not tell a phone that was never told from one
        // that was told Resident.
        is RoundMessage.RoleRevealed -> "WHAT THIS PHONE IS — ${if (m.insider) "INSIDER" else "RESIDENT"}"
        // **Counted, never quoted** — `InsidersRevealed`'s rule, and it applies here for the same
        // reason one message earlier in the round: a device log is a file on a Mac, and D-115
        // scopes a name to the round. What a rehearsal needs is that the list arrived and how long
        // it was, which is what makes *this phone can name a seat* checkable at all.
        is RoundMessage.RosterIssued ->
            "THE ROSTER — ${m.roster.count { it != null }} SEAT(S) · " +
                "${m.roster.count { !it.isNullOrBlank() }} NAMED · NONE PRINTED HERE"
        is RoundMessage.RevokeLanded -> "THE HOUSE WITHDREW MY LAMP"
        is RoundMessage.WorkOrderIssued ->
            "WORK ORDER · ${m.lines.size} LINE(S) · " +
                "${m.lines.count { it is OrderLine.Blocked }} BLOCKED"
        is RoundMessage.ScanAnswered ->
            m.opened?.let { "SCAN OPENED ${it.subroutine} · ENTRY ${it.entry} · " +
                "${it.parameters.size} PARAMETER(S)" } ?: "SCAN OPENED NOTHING"
        is RoundMessage.SubroutineGraded -> "VERDICT — ${if (m.accepted) "ACCEPTED" else "REJECTED"}"
        is RoundMessage.SystemIntegrityLive -> "SYSTEM INTEGRITY (LIVE) ${m.percent}%"
        is RoundMessage.SystemIntegrityBatched -> "SYSTEM INTEGRITY (BATCHED) ${m.percent}%"
        is RoundMessage.SubroutineProgressed -> "SUBROUTINES REMAINING ${m.remaining}"
        is RoundMessage.LampSet -> "LAMP ${m.luminance}"
        is RoundMessage.AbilityFired -> "ABILITY FIRED · COOLDOWN STARTED=${m.cooldownStarted}"
        is RoundMessage.ContactAnswered -> "A TOUCH, ANSWERED"
        is RoundMessage.MeetingOpened -> "MEETING CALLED BY SEAT ${m.caller.index} · ${m.trigger}"
        // No seat, because the message has none: this bench line is as narrow as the wire is.
        is RoundMessage.MeetingCalling -> "I AM THE ONE CALLING"
        is RoundMessage.StandAndWalkIn -> "STAND AND WALK IN · HAPTIC ${m.haptic}"
        is RoundMessage.CheckInProgressed -> "CHECK-IN GATE ${m.present}/${m.expected}"
        // The seats as numbers, never as names (D-223): what the notice names is the house's.
        is RoundMessage.NoticeIssued ->
            "NOTICE — " + (m.unreachable.takeIf { it.isNotEmpty() }?.joinToString(",") { "SEAT ${it.index} UNREACHABLE" } ?: "NOBODY NAMED")
        is RoundMessage.MeetingPhaseOpened ->
            "PHASE ${m.phase}" + (m.voteWindowSeconds?.let { " · ${it}S" } ?: "")
        is RoundMessage.ReadyProgressed -> "READY ${m.ready}/${m.expected}"
        is RoundMessage.VoteHeld ->
            "MY BALLOT — ${m.selection?.let { "SEAT ${it.index}" } ?: "SKIP"} · LOCKED=${m.locked}"
        is RoundMessage.VoteProgressed -> "LOCKED ${m.locked}/${m.expected}"
        is RoundMessage.VoteSelectionShown ->
            "SEAT ${m.voter.index} SELECTED ${m.selection?.let { "SEAT ${it.index}" } ?: "SKIP"}"
        is RoundMessage.MeetingResult ->
            "RESTRAINED — ${m.restrained?.let { "SEAT ${it.index}" } ?: "NOBODY"}"
        is RoundMessage.MeetingResolved -> "THE BALLOT — ${m.attribution.size} VOTE(S) ATTRIBUTED"
        is RoundMessage.RestrainedTakeover -> "THE ROOM HELD ME · HAPTIC ${m.haptic}"
        is RoundMessage.MeetingEnded -> "MEETING ENDED · HAPTIC ${m.haptic}"
        is RoundMessage.EgressOpened ->
            "EGRESS ${m.type} · NODES ${m.nodes.joinToString { it.room }} · ${m.remaining} TICK(S)"
        is RoundMessage.EgressHeld -> "EGRESS ${if (m.running) "RESUMED" else "HELD"} · ${m.remaining}"
        is RoundMessage.SyncPulseAnswered -> "SYNC PULSE — HELD=${m.held}"
        is RoundMessage.EgressContained -> "EGRESS CONTAINED"
        is RoundMessage.EgressSucceeded -> "EGRESS SUCCEEDED"
        is RoundMessage.RoundEnded -> "ROUND ENDED — ${m.winner} BY ${m.by}"
        // **Counted, never quoted.** The reveal is the one push in the round that legitimately
        // carries names and lines, and a device log is a file on a Mac.
        is RoundMessage.InsidersRevealed ->
            "THE REVEAL — ${m.insiders.size} INSIDER(S) NAMED · " +
                "${m.insiders.count { it.line.isNotBlank() }} WITH A LINE · NEITHER PRINTED HERE"
        is RoundMessage.HouseSignedOff -> "THE HOUSE SIGNED OFF · ${m.body.length} CHAR(S)"
        else -> m::class.simpleName ?: "?"
    }
}
