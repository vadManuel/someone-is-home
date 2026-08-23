package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Outcome
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The push pipeline, end to end, over a real socket** (E-D3-3's first half).
 *
 * On 2026-08-21 LIGHTS OUT armed one phone because there was no host→client effect push for any
 * effect at all. This is that path: the rules' own [Effect]s, through the emit boundary, down a
 * websocket, into the screens — and the two things that have to be true about it that a unit test
 * of either end cannot see.
 *
 * ### The two sources have to agree
 *
 * A message applied in process and the same message applied over loopback have to leave a phone in
 * the same state, or the bench and a real round are two different games and the one that gets
 * looked at is the bench.
 *
 * ### A gap has to converge
 *
 * D-097: a phone that missed something is answered by the house saying it again, and the phone that
 * asked ends up holding what the phone that never dropped holds.
 */
class EffectPushTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    private val seats = (0..2).map { Seat(it) }
    private val line = "i still have priya's spare key"

    /**
     * A round with one Insider, one line and one name per seat.
     *
     * The lines are here on purpose: this fixture is what `noFrameCarriesALineUntilTheRoundEnds`
     * sweeps, and a state with nothing to leak would pass that test for the wrong reason.
     */
    private fun round(): GameState = GameState.armedRound(
        seed = 7L,
        seats = seats,
        insiders = listOf(Seat(1)),
        systemIntegrity = 42,
        oneLines = seats.map { OneLineHeld(it, "$line ${it.index}") },
        names = seats.map { SeatNamed(it, "SEAT${it.index}") },
    )

    /**
     * **A shift's worth of what the house says**, hand-built.
     *
     * `app` sees `ui`, `platform` and `model` and deliberately not `core`, so the rules cannot be
     * run from here — which is right: what is under test is the carriage, and a fixture list is a
     * sharper input than a round because it can contain kinds a fixture round would never reach.
     */
    private fun aShift(state: GameState): List<Effect> = listOf(
        Effect.OpeningMessage(Seat(0), Haptic.Short),
        Effect.WorkOrderIssued(Seat(0), emptyList()),
        Effect.SystemIntegrityBatched(percent = 87),
        Effect.SubroutineGraded(Seat(0), accepted = true),
        Effect.SystemIntegrityLive(percent = 86),
        Effect.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long),
        Effect.CheckInProgressed(present = 3, expected = 3),
        Effect.MeetingPhaseOpened(MeetingPhase.Discussion, voteWindowSeconds = null, haptic = Haptic.Short),
        Effect.MeetingPhaseOpened(MeetingPhase.Vote, voteWindowSeconds = 30, haptic = Haptic.Short),
        Effect.VoteProgressed(locked = 2, expected = 3),
        Effect.MeetingResult(restrained = null, haptic = Haptic.Short),
        Effect.MeetingEnded(Haptic.Long),
        Effect.SubroutineGraded(Seat(0), accepted = false),
    ).also { require(it.isNotEmpty() && state.armed) }

    /** The ending, which is the one moment a line may travel (D-116). */
    private fun theEnding(): Pair<GameState, List<Effect>> {
        val over = round().endRound(Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared))
        return over to listOf(
            Effect.RoundEnded(Seat(0), Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short),
            Effect.InsidersRevealed(listOf(InsiderNamed(Seat(1), "SEAT1", "$line 1"))),
        )
    }

    /** What seat 0 is permitted, projected — the in-process source. */
    private fun forSeatZero(effects: List<Effect>, state: GameState) =
        effects.filter { effect ->
            EmitSchema.deliveries(effect, state).any { it.seat.index == 0 }
        }.map(RoundWire::project)

    // ---- 1 · One consumer, two sources -------------------------------------------------------------

    /**
     * **The same effects, in process and over loopback, leave the same phone.**
     *
     * The one assertion this unit exists to be able to make. If it ever fails, the bench and a real
     * round have drifted — and the failure will be in the direction nobody looks, because the bench
     * is the thing that gets run.
     */
    @Test
    fun inProcessAndOverLoopbackLeaveIdenticalState() = runBlocking {
        val state = round()
        val effects = aShift(state)

        val messages = forSeatZero(effects, state)

        // **Standing at the assembly gate**, so that the meeting push in the fixture has a screen
        // waiting for it. Started on the springboard, every message would land on a phone that no
        // table has a transition for and the two states would agree about a screen neither of them
        // moved — which is the shape of a comparison that proves nothing.
        val start = PanelState(screen = ScreenId.Assemble)

        // ---- In process: the same messages, handed straight to the one consumer ---------------
        val inProcess = FlowModel(start)
        messages.forEach(inProcess::houseSaid)

        // ---- Over loopback: a real host, a real socket, a real client -------------------------
        val overTheWire = FlowModel(start)
        val house = House(this)
        val phone: Phone
        try {
            house.start()
            phone = house.seat(overTheWire)
            house.awaitSeated()
            for (effect in effects) house.effects.emit(effect, state)
            phone.awaitHeld(messages.size.toLong())
        } finally {
            house.stop()
        }
        phone.hangUp()

        assertEquals(
            inProcess.state, overTheWire.state,
            "a push applied in process and the same push off a socket left two different phones",
        )

        // ---- And none of that is vacuous ------------------------------------------------------
        assertTrue(inProcess.state != start, "the fixture shift changed nothing on this phone")
        assertEquals(ScreenId.Notice, inProcess.state.screen, "the meeting push moved nobody")
        assertEquals(87, inProcess.state.integrityPercent, "the meter never reached the phone")
        assertEquals(30, inProcess.state.voteWindowSeconds, "the ballot's window never reached the phone")
        assertEquals(
            messages.size, effects.size - 1,
            "every effect reached seat 0, so the allowlist filtered nothing in this fixture",
        )
    }

    // ---- 2 · A gap converges -----------------------------------------------------------------------

    /**
     * **A phone that missed a frame asks, the house says it again, and the two phones agree**
     * (D-097).
     *
     * The gap is made by the client, not by the network: the first push is swallowed on the way in,
     * which is exactly what a dropped frame looks like from inside the app and is the only way to
     * produce one reliably over loopback. What is under test is everything after it — the detector,
     * the ask, the house's replay, and the convergence.
     */
    @Test
    fun aDroppedFrameIsNoticedAskedAboutAndMadeGoodOnItsOwn() = runBlocking {
        val state = round()
        val effects = aShift(state)
        val expected = forSeatZero(effects, state)

        val whole = FlowModel(PanelState(screen = ScreenId.Assemble))
        expected.forEach(whole::houseSaid)

        val gappy = FlowModel(PanelState(screen = ScreenId.Assemble))
        val house = House(this)
        val missed = Channel<String>(Channel.UNLIMITED)
        val phone: Phone
        try {
            house.start()
            // Swallow the very first frame this phone is sent, and nothing after it.
            var swallowed = false
            phone = house.seat(
                gappy,
                onEvent = { missed.trySend(it) },
                swallow = { if (swallowed) false else { swallowed = true; true } },
            )
            house.awaitSeated()
            for (effect in effects) house.effects.emit(effect, state)
            phone.awaitHeld(expected.size.toLong())
        } finally {
            house.stop()
        }
        phone.hangUp()

        val recorded = generateSequence { missed.tryReceive().getOrNull() }.toList()
        assertNotNull(
            recorded.firstOrNull { it.contains("A FRAME WAS MISSED") },
            "a frame went missing and the authority was told nothing: $recorded",
        )
        assertEquals(
            whole.state, gappy.state,
            "the phone that lost a frame converged somewhere other than where the house is",
        )
    }

    // ---- 3 · The one line ---------------------------------------------------------------------------

    /**
     * **No frame carries anybody's one line until the round is over, and then exactly one does**
     * (D-116, `gdd.md:1063`).
     *
     * The restore unit's inbound probe, extended to round effects: every body that reaches this
     * phone is read whole, before anything is done with it, and swept for the text three people in
     * this house typed. The sweep is **self-verifying** — the same probe is run across the ending,
     * where a line is published on purpose, and it has to find it there. A test that reads no bytes
     * passes for the same reason a test that reads all of them does.
     *
     * The charter's rule is stated absolutely — *nothing in this pipeline may carry a one-line
     * down* — and read absolutely it would forbid the reveal, which is a ruled disclosure and the
     * half of the round that makes it land. So it is held in W1's shape for the same question about
     * names: **nothing carries one until the round ends.**
     */
    @Test
    fun noFrameCarriesALineUntilTheRoundEnds() = runBlocking {
        val state = round()
        val duringTheShift = bodiesReachingSeatZero(state, aShift(state))
        assertTrue(duringTheShift.isNotEmpty(), "the probe read nothing, so it swept nothing")

        val leaking = duringTheShift.filter { body -> body.contains(line) }
        assertEquals(
            emptyList(), leaking,
            "${leaking.size} frame(s) carried a one line during the round",
        )
        // Names are the same rule and the same shift (W1's `no effect carries a name until the
        // round ends`), checked here because this probe is the only thing that sees real frames.
        assertEquals(
            emptyList(), duringTheShift.filter { it.contains("SEAT1") },
            "a seat's name reached a phone in the middle of the round",
        )

        // ---- The positive control: at the ending, the line is published on purpose -------------
        val (over, ending) = theEnding()
        val atTheEnd = bodiesReachingSeatZero(over, ending)
        assertTrue(
            atTheEnd.any { it.contains("$line 1") },
            "the reveal published no line, so the sweep above was looking at nothing",
        )
        assertTrue(
            atTheEnd.none { it.contains("$line 0") || it.contains("$line 2") },
            "the reveal published a line the house never used — a Resident's",
        )
    }

    /** Every body that really reached [seat], off a real socket, unparsed. */
    private suspend fun CoroutineScope.bodiesReachingSeatZero(
        state: GameState,
        effects: List<Effect>,
    ): List<String> {
        val seen = Channel<String>(Channel.UNLIMITED)
        val flow = FlowModel(PanelState(screen = ScreenId.Home))
        val house = House(this)
        val expected = forSeatZero(effects, state).size
        val phone: Phone
        try {
            house.start()
            phone = house.seat(flow, onInbound = { seen.trySend(it) })
            house.awaitSeated()
            for (effect in effects) house.effects.emit(effect, state)
            phone.awaitHeld(expected.toLong())
        } finally {
            house.stop()
        }
        phone.hangUp()
        return generateSequence { seen.tryReceive().getOrNull() }.toList()
    }

    // ---- The rig -------------------------------------------------------------------------------------

    /**
     * One house on loopback: a ledger, a server, and the effect wire on the same sockets — exactly
     * [LobbyHouse]'s arrangement without the advertisement, which needs a radio no test has.
     */
    private class House(private val scope: CoroutineScope) {

        private val seated = Channel<Seat>(Channel.UNLIMITED)
        private val gaps = Channel<Pair<Seat, Long>>(Channel.UNLIMITED)

        private var minted = 0
        private val host = TransportHost(
            SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
                if (body is RoundBody.Gap) gaps.trySend(seat to body.held)
            },
            onSeated = { seated.trySend(it) },
        )

        /** The one thing on the host that this unit built. Its `send` is the server's. */
        val effects = EffectWire(send = { seat, frame -> host.send(seat, frame) })

        private var port = 0
        private var answering: Job? = null

        suspend fun start() {
            port = host.start(0)
            // The house answering the one thing a client may ask it: say that again (D-097).
            answering = scope.launch { for ((seat, held) in gaps) effects.reassert(seat, held) }
        }

        suspend fun awaitSeated(): Seat = withTimeout(30_000) { seated.receive() }

        /**
         * One phone, joining over loopback exactly as the host's own does.
         *
         * [swallow] is how a dropped frame is produced: the network will not lose one on demand
         * over loopback, and what is under test is the client's answer to a hole rather than the
         * hole itself.
         */
        fun seat(
            flow: FlowModel,
            onEvent: (String) -> Unit = {},
            onInbound: (String) -> Unit = {},
            swallow: (TransportFrame) -> Boolean = { false },
        ): Phone {
            lateinit var round: RoundOverTransport
            lateinit var client: TransportClient
            client = TransportClient(
                ClientSession(), "127.0.0.1", port,
                nowMillis = { monotonicNanos() / 1_000_000 },
                onFrame = { frame -> if (!swallow(frame)) round.frameArrived(frame) },
            )
            round = RoundOverTransport(
                scope, flow,
                send = { frame -> client.send(frame) },
                onEvent = onEvent,
                onInbound = onInbound,
            )
            val jobs = listOf(scope.launch { client.run() }, scope.launch { round.run() })
            return Phone(round, jobs)
        }

        suspend fun stop() {
            answering?.cancel()
            host.stop()
        }
    }

    private class Phone(private val round: RoundOverTransport, private val jobs: List<Job>) {

        /** Wait until this phone holds [n] frames in order. The rig's only synchronisation. */
        suspend fun awaitHeld(n: Long) {
            withTimeout(30_000) {
                while (round.held < n) delay(10)
            }
        }

        fun hangUp() = jobs.forEach { it.cancel() }
    }
}
