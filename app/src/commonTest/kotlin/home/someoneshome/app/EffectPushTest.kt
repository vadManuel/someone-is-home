package home.someoneshome.app

import home.someoneshome.platform.NoRadio
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineKind
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
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.model.EgressType
import home.someoneshome.ui.Countdown
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.WallTime
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
     * Whether [body] carries [text] as itself or as the UTF-8 hex the wire seals some fields in
     * (`RoleRevealed.lineHex`, D-249; `IsolateTargetsOffered.targetsHex`, D-258). A sweep for the
     * plain text alone reads straight past a hex field, so it would pass with the leak in it.
     */
    private fun carries(body: String, text: String): Boolean =
        body.contains(text) ||
            body.contains(text.encodeToByteArray().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') })

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
        Effect.MeetingResult(restrained = null, voted = 3, living = 3, haptic = Haptic.Short),
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
        // The gate's push walks Assemble → Notice and the ballot's walks Notice → Vote (D-250). Until
        // D-250 this said Notice: the ballot's push landed on a notice with no row for it, which is
        // the H2 phone parked on the notice for the whole meeting, written down as the expectation.
        assertEquals(ScreenId.Vote, inProcess.state.screen, "the meeting pushes did not walk the phone to the ballot")
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

        val leaking = duringTheShift.filter { body -> carries(body, line) }
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

    /**
     * **⚠️ NO FRAME CARRIES THE HOME'S GEOGRAPHY, AND ONE CARRIES THIS SEAT'S OWN LINES** (D-186,
     * D-129).
     *
     * The rollback of T2·4, read off real bytes on a real socket. Until D-186 the arming burst put
     * the whole home's plan — every room on every floor, including rooms with no card in them — on
     * an Insider's phone, because a picker over rooms cannot be drawn without rooms. **Static has no
     * picker**, so the plan crosses to nobody, and this is the sweep that says so about frames
     * rather than about types: a type can be deleted and the disclosure can come back on another
     * one.
     *
     * `noFrameCarriesALineUntilTheRoundEnds`' probe, pointed at geography. The house holds a room
     * **no work order names** — the conservatory, which is exactly what only a plan could publish —
     * and the sweep is that no body reaching this phone contains it.
     *
     * **Self-verifying, and that is what makes it more than a test of an empty string.** The same
     * probe has to FIND the room this seat's own order sends it to, in the same frames, in the same
     * run. A sweep that read no bytes passes for the same reason a sweep that read all of them does
     * — so the positive control is the assertion that comes first.
     */
    @Test
    fun noFrameCarriesTheHomesGeography() = runBlocking {
        val state = round()
        val bodies = bodiesReachingSeatZero(state, aShift(state) + geography())
        assertTrue(bodies.isNotEmpty(), "the probe read nothing, so it swept nothing")

        // The positive control: this seat's OWN destination is on its OWN order, and the probe can
        // see it. D-129 — knowing where your own next Subroutine is, is the game.
        assertTrue(
            bodies.any { carries(it, MINE) },
            "the seat's own work order carried no destination, so the sweep below read nothing",
        )
        assertEquals(
            emptyList(), bodies.filter { carries(it, UNPUBLISHED) },
            "a frame carried a room this seat's own order never names — house geography is " +
                "crossing to a Resident again",
        )
    }

    /** A room this seat's order really does name, so the probe above has something to find. */
    private val MINE = "KITCHEN"

    /**
     * **A room this seat is not entitled to know about**, and the whole probe turns on it.
     *
     * Seat 0 is a Resident in this fixture. The conservatory is nowhere on seat 0's work order, so
     * the only messages that could ever put it on this phone are the ones that carry the house's
     * geography rather than one seat's own lines: the Isolate picker's list, which the allowlist
     * permits to a living **Insider** and to nobody else, and the home's plan, which no longer
     * exists at all.
     */
    private val UNPUBLISHED = "CONSERVATORY"

    /**
     * The two effects that carry geography at all, **built for seat 0 exactly as the rules build
     * them for every seat** — which is `isolateOffers`' recorded posture and the reason the arming
     * burst stays role-independent.
     *
     * So the picker below is constructed for a Resident and is expected to be delivered to nobody.
     * That is the whole of the narrowing: it is the allowlist's, not this phone's, and a row widened
     * by one class puts the conservatory on a Resident's socket. The work order is the control — it
     * names this seat's own destination and it does reach the phone (D-129).
     */
    private fun geography(): List<Effect> = listOf(
        Effect.WorkOrderIssued(
            Seat(0),
            listOf(
                OrderLine.Known(
                    index = 0,
                    work = AssignedWork.Subroutine(SubroutineKind.Handshake),
                    done = false,
                    at = listOf(Destination(MarkerId("card-kitchen"), "diamond", MINE)),
                ),
            ),
        ),
        Effect.IsolateTargetsOffered(
            Seat(0),
            listOf(
                Destination(MarkerId("card-kitchen"), "diamond", MINE),
                Destination(MarkerId("card-cons"), "cross", UNPUBLISHED),
            ),
        ),
        // Its blank, which DOES reach this Resident (D-258): as wide as the list above and
        // carrying none of it, which is what this sweep checks of it.
        Effect.IsolateTargetsBlanked(
            Seat(0),
            listOf(
                Destination(MarkerId("card-kitchen"), "diamond", MINE),
                Destination(MarkerId("card-cons"), "cross", UNPUBLISHED),
            ),
        ),
    )

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

    // ---- 4 · Real clocks and real times (D-243) --------------------------------------------------------

    /**
     * **GUARD: off a real socket, every clock a housed phone draws is the house's and every time is
     * the phone's own** (D-243; the owner's *is the clock fake??*).
     *
     * The rules' own effects go down a real websocket into a housed [FlowModel] with a wall clock the
     * test owns. The meeting's phase clocks start at the house's windows and count down a second at a
     * time (the test is `FlowHost`'s second hand, as in `AbilityWiringTest`); the Egress countdown is
     * the house's `remaining`, held and restarted by `EgressHeld`; a later text carries the minute it
     * landed. Before D-243 each of these was the design's drawn value on a housed phone.
     */
    @Test
    fun theClocksAndTimesOnAHousedPhoneAreWhatTheHousePushed() = runBlocking {
        val state = round()
        var now = WallTime(hour = 21, minute = 50, weekday = 6, day = 14, month = 11)
        val flow = FlowModel(PanelState(screen = ScreenId.Assemble, houseAttached = true), wallClock = { now })
        val house = House(this)
        var sent = 0L
        val phone: Phone
        try {
            house.start()
            phone = house.seat(flow)
            house.awaitSeated()
            suspend fun say(vararg effects: Effect) {
                for (effect in effects) house.effects.emit(effect, state)
                sent += forSeatZero(effects.toList(), state).size
                phone.awaitHeld(sent)
            }

            say(Effect.OpeningMessage(Seat(0), Haptic.Long))
            assertEquals("21:50", flow.state.openedAt, "LIGHTS OUT was not stamped with the phone's time")
            assertEquals("21:50", PanelVals(flow.state).clock, "the strip is not the phone's own clock")

            say(
                Effect.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long),
                Effect.MeetingPhaseOpened(MeetingPhase.Discussion, voteWindowSeconds = null, haptic = Haptic.Short),
            )
            assertEquals(90, flow.state.secondsLeft, "the talk did not start at the house's ninety")
            repeat(26) { flow.secondPassed() }
            assertEquals(64, flow.state.secondsLeft)

            say(Effect.MeetingPhaseOpened(MeetingPhase.Vote, voteWindowSeconds = 30, haptic = Haptic.Short))
            assertEquals(30, flow.state.secondsLeft, "the ballot did not start at the window the house sent")
            repeat(7) { flow.secondPassed() }
            assertEquals(23, flow.state.secondsLeft)

            say(Effect.MeetingEnded(Haptic.Long))
            assertEquals(null, flow.state.secondsLeft, "a meeting's clock outlived the meeting")

            say(Effect.EgressOpened(Seat(0), EgressType.Tether, emptyList(), remaining = 2_400, haptic = Haptic.Long))
            assertEquals(Countdown(120, 120), flow.state.egressLeft, "the Egress did not start at the house's remaining")
            repeat(20) { flow.secondPassed() }
            assertEquals("1:40", PanelVals(flow.state).egressLeftText)

            say(Effect.EgressHeld(remaining = 1_210, running = false, haptic = Haptic.Short))
            repeat(5) { flow.secondPassed() }
            assertEquals(Countdown(61, 120), flow.state.egressLeft, "the held Egress is not the house's number, or it moved")

            now = now.copy(hour = 22, minute = 4)
            say(Effect.MessageDelivered(Seat(0), "Do not run."))
            assertEquals("22:04", flow.notifications.standing.first().at, "the text was not stamped with its arrival")
            assertEquals("22:04" to "Do not run.", PanelVals(flow.state).houseThread.last())
        } finally {
            house.stop()
        }
        phone.hangUp()
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
                // This rig's subject is what a phone is TOLD, and it compares two [PanelState]s.
                // A silent motor says so rather than being defaulted into: the constructor has no
                // default, so nothing that carries pushes can be built having forgotten the motor.
                buzzer = HouseBuzzer(SilentHaptics),
                send = { frame -> client.send(frame) },
                radio = NoRadio, wake = home.someoneshome.platform.ScreenWake(home.someoneshome.platform.NoIdleTimer),
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
