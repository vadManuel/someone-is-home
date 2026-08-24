package home.someoneshome.app

import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.IntentDesk
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.MeetingModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.VoteChoice
import home.someoneshome.ui.asMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The intent pipeline, end to end, over a real socket** — [EffectPushTest] pointing the other way.
 *
 * What is under test here is the **client half and the stamp**: a finger really lands on a real
 * `MeetingModel`, the request really goes down a websocket, and the host really decides whose it
 * was from the connection it arrived on. The rules are deliberately not in this picture — `app`
 * sees `ui`, `platform` and `model` and not `core`, which is the right way round for the same
 * reason it was right for the push: what is being proved is the carriage.
 *
 * `IntentLoopTest` in `harness` is where `admit` joins the two ends, because that is the one module
 * that can see both.
 */
class IntentPushTest {

    private val line = "i still have priya's spare key"
    private val names = listOf("PRIYA", "MARCUS", "DANI")

    // ---- 1 · The stamp -----------------------------------------------------------------------------

    /**
     * **A control pressed on a phone reaches the house as that phone's request, and it is the
     * connection that says so.**
     *
     * The whole chain, with nothing standing in: `MeetingModel.checkIn()` publishes a seat-free
     * [home.someoneshome.ui.MeetingRequest], `asMessage` turns it into a frame with nowhere to put
     * a seat, [RoundOverTransport] numbers it and writes it, and the desk at the far end stamps the
     * seat the ledger admitted the token to.
     *
     * The four controls are pressed in the order a player presses them, so what is asserted is a
     * **sequence** — a channel that reordered a `SelectVote` and its `LockVote` would lock a ballot
     * the couch never watched being made (D-117).
     */
    @Test
    fun everyMeetingControlRidesUpAndIsStampedWithItsConnectionsSeat() = runBlocking<Unit> {
        val house = House(this)
        val seat: Seat
        try {
            house.start()
            val phone = house.seat()
            seat = phone.seat
            phone.meeting.checkIn()
            phone.meeting.sayReady()
            phone.meeting.choose(VoteChoice.Named("DANI"))
            phone.meeting.readyToVote()
            house.awaitHeard(4)
        } finally {
            house.stop()
        }

        assertEquals(
            listOf(
                Intent.CheckIn(seat),
                Intent.DeclareReadyToVote(seat),
                Intent.SelectVote(seat, Seat(2)),
                Intent.LockVote(seat),
            ),
            house.heard,
            "a control was lost, reordered, or attributed to somebody who did not press it",
        )
    }

    /**
     * **Two phones pressing the same control are two players, and neither of them chose that.**
     *
     * The forge, ruled out by construction: the frames are byte-for-byte identical — the same
     * control, the same sequence number — and the house hears two different seats.
     */
    @Test
    fun twoPhonesPressingTheSameControlAreTwoPlayers() = runBlocking<Unit> {
        val house = House(this)
        val bytes = RoundWire.encode(RoundBody.Act(seq = 1, intent = IntentMessage.CheckIn))
        val first: Seat
        val second: Seat
        try {
            house.start()
            val a = house.seat()
            val b = house.seat()
            first = a.seat
            second = b.seat
            a.raw(bytes)
            b.raw(bytes)
            house.awaitHeard(2)
        } finally {
            house.stop()
        }

        assertTrue(first != second, "both phones took the same seat, so the rig proved nothing")
        assertEquals(
            setOf(Intent.CheckIn(first), Intent.CheckIn(second)), house.heard.toSet(),
            "identical bytes on two connections were heard as one player: $bytes",
        )
    }

    /**
     * **A frame that tries to name a seat is refused, and the honest frame behind it is stamped
     * with the connection's own.**
     *
     * Both halves are asserted, because either alone is weak. That the forge fails proves the
     * decoder is strict; that the next frame on the *same socket* is still attributed correctly
     * proves the forge did not take the connection down with it — a client that could stop its own
     * session with one bad frame would be a denial the player could not diagnose.
     *
     * The forged body is built by hand rather than by any API in this repo, which is the only
     * honest way to write this: **there is no function that would produce it**, and that absence is
     * the property under test.
     */
    @Test
    fun aFrameClaimingAnotherSeatIsRefusedAndTheNextOneIsStillItsOwn() = runBlocking<Unit> {
        val house = House(this)
        val seat: Seat
        try {
            house.start()
            val phone = house.seat()
            seat = phone.seat
            val somebodyElse = (0 until 8).first { it != seat.index }

            // Hand-written, because nothing in this codebase can build it.
            phone.raw("""{"type":"act","seq":1,"intent":{"type":"CheckIn","actor":$somebodyElse}}""")
            phone.raw("""{"type":"act","seq":1,"intent":{"type":"ArmRevoke","seat":$somebodyElse}}""")
            // And an honest one behind them, on the same socket, so the wait below is not a wait
            // on something that was never going to arrive.
            phone.raw(RoundWire.encode(RoundBody.Act(seq = 1, intent = IntentMessage.CheckIn)))
            house.awaitHeard(1)
        } finally {
            house.stop()
        }

        assertEquals(
            listOf<Intent>(Intent.CheckIn(seat)), house.heard,
            "a frame that named a seat reached the rules",
        )
    }

    // ---- 2 · Once is exactly once ------------------------------------------------------------------

    /**
     * **A retried frame is heard once, over the app's own client.**
     *
     * `IntentDeskTest` proves the desk drops a duplicate and `IntentLoopTest` proves the round
     * moves once; this is the leg between them — the number [RoundOverTransport] actually stamps on
     * the wire is the one the desk actually reads.
     */
    @Test
    fun aResentFrameIsHeardOnce() = runBlocking<Unit> {
        val house = House(this)
        val seat: Seat
        try {
            house.start()
            val phone = house.seat()
            seat = phone.seat
            phone.meeting.checkIn()
            // The resend: the same bytes this phone just sent, exactly as a client that gave up
            // waiting would send them.
            val sent = phone.lastSent()
            phone.raw(sent)
            phone.raw(sent)
            phone.meeting.sayReady()
            house.awaitHeard(2)
        } finally {
            house.stop()
        }

        assertEquals(
            listOf(Intent.CheckIn(seat), Intent.DeclareReadyToVote(seat)), house.heard,
            "a resend reached the rules a second time",
        )
    }

    // ---- 3 · The one line, going the other way -----------------------------------------------------

    /**
     * **Nothing this phone sends up carries a line or a name.**
     *
     * `EffectPushTest.noFrameCarriesALineUntilTheRoundEnds` reads every body that comes *down*;
     * this reads every body that goes *up*, which is the harder direction to be sure about — what
     * comes down is the house's own bytes, and what goes up is composed on a phone that is holding
     * a line and a name in memory the whole time.
     *
     * **Self-verifying, and it has to be**, because the honest answer here is that no
     * [IntentMessage] has a field a line could sit in — so a sweep that read nothing would pass for
     * exactly the same reason. The positive control is a frame deliberately built with the line
     * inside a [MarkerId]: it proves the probe reads real bytes and would say so.
     */
    @Test
    fun nothingGoingUpCarriesALineOrAName() = runBlocking<Unit> {
        val house = House(this)
        val sent: List<String>
        try {
            house.start()
            val phone = house.seat()
            phone.meeting.checkIn()
            phone.meeting.sayReady()
            phone.meeting.choose(VoteChoice.Named("DANI"))
            phone.meeting.readyToVote()
            phone.round.act(IntentMessage.ArmRevoke)
            phone.round.act(IntentMessage.CallMeeting)
            phone.round.act(IntentMessage.HandOverEntry(MarkerId("CARD-4"), listOf(3, 1, 4)))
            house.awaitHeard(7)
            sent = phone.outbound()
        } finally {
            house.stop()
        }

        assertTrue(sent.isNotEmpty(), "the probe read nothing, so it swept nothing")
        assertEquals(
            emptyList(), sent.filter { it.contains(line) },
            "a request going up carried somebody's one line",
        )
        assertEquals(
            emptyList(), sent.filter { body -> names.any { body.contains(it) } },
            "a request going up carried a name — the ballot travels as seats (D-115)",
        )

        // ---- The positive control: the probe reads real bytes and would have said so -----------
        val watching = House(this)
        val withALine: List<String>
        try {
            watching.start()
            val phone = watching.seat()
            phone.round.act(IntentMessage.BeginSubroutine(MarkerId(line)))
            watching.awaitHeard(1)
            withALine = phone.outbound()
        } finally {
            watching.stop()
        }
        assertTrue(
            withALine.any { it.contains(line) },
            "the probe did not see a line that really went up, so the sweep above read nothing",
        )
    }

    // ---- The rig -------------------------------------------------------------------------------------

    /**
     * One house on loopback, with the intent desk on the same sockets — [LobbyHouse]'s arrangement,
     * including its one ordered queue, without the advertisement a test has no radio for.
     */
    private inner class House(private val scope: CoroutineScope) {

        private val seated = Channel<Seat>(Channel.UNLIMITED)
        private val put = Channel<Intent>(Channel.UNLIMITED)
        private val arriving = Channel<Pair<Seat, RoundBody.Act>>(Channel.UNLIMITED)

        /** Every request the house heard, attributed, in order. */
        val heard = mutableListOf<Intent>()

        private val intents = IntentDesk()

        private var minted = 0
        private val host = TransportHost(
            SeatLedger(List(8) { Seat(it) }, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
                if (body is RoundBody.Act) arriving.trySend(seat to body)
            },
            onSeated = { seated.trySend(it) },
        )

        private var port = 0
        private var attending: Job? = null
        private val phones = mutableListOf<Phone>()

        suspend fun start() {
            port = host.start(0)
            attending = scope.launch {
                for ((seat, act) in arriving) {
                    val landing = intents.arrived(seat, act)
                    if (landing is IntentDesk.Landing.Put) {
                        heard += landing.intent
                        put.trySend(landing.intent)
                    }
                }
            }
        }

        /** Wait until the house has heard [n] requests. The rig's only synchronisation. */
        suspend fun awaitHeard(n: Int) {
            withTimeout(30_000) { repeat(n - drained) { put.receive(); drained++ } }
        }

        private var drained = 0

        /** One phone, joining over loopback exactly as the host's own does. */
        suspend fun seat(): Phone {
            val outbound = mutableListOf<String>()
            lateinit var round: RoundOverTransport
            val client = TransportClient(
                ClientSession(), "127.0.0.1", port,
                nowMillis = { monotonicNanos() / 1_000_000 },
                onFrame = { frame -> round.frameArrived(frame) },
            )
            round = RoundOverTransport(
                scope, FlowModel(PanelState(screen = ScreenId.Assemble)),
                send = { frame -> client.send(frame) },
                onOutbound = { outbound += it },
            )
            // The real control surface: a meeting model whose four buttons go up this phone's own
            // socket, seat-free, exactly as the app root will wire them.
            val meeting = MeetingModel(
                send = { request -> round.act(request.asMessage(names)) },
                initialNames = names,
            )
            val jobs = listOf(scope.launch { client.run() }, scope.launch { round.run() })
            val seat = withTimeout(30_000) { seated.receive() }
            return Phone(seat, meeting, round, client, outbound, jobs).also { phones += it }
        }

        suspend fun stop() {
            phones.forEach { it.hangUp() }
            attending?.cancel()
            host.stop()
        }
    }

    private class Phone(
        val seat: Seat,
        val meeting: MeetingModel,
        val round: RoundOverTransport,
        private val client: TransportClient,
        private val sent: List<String>,
        private val jobs: List<Job>,
    ) {

        /** Every body this phone put on the wire, unparsed. Never printed. */
        fun outbound(): List<String> = sent.toList()

        fun lastSent(): String = sent.last()

        /** Bytes, exactly as given — how a resend and a forge are both produced. */
        suspend fun raw(body: String) = client.send(TransportFrame.Carry(body))

        fun hangUp() = jobs.forEach { it.cancel() }
    }
}
