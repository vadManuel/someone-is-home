package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Effect
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.clientClassOf
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.CardScanner
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.RecordingHaptics
import home.someoneshome.platform.ScanUse
import home.someoneshome.platform.ScanWindow
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * **One raw card read, routed by the house against the map** (the owner's 2026-11-21 ruling, D-318;
 * D-124, D-169, D-072).
 *
 * D-313 stopped on this: a card this home never registered went up as `BeginSubroutine`, so the
 * rules placed its reader at a card that is no place in the home, spent their open work and
 * answered NOTHING FOR YOU HERE instead of NOT A MARKER; and a spare meeting card called a meeting,
 * because `CallMeeting` carried no card and nothing checked it. Now every decoded read goes up as
 * the card's id, the house routes it (`routeScan`), and paper the home never registered is answered
 * NOT A MARKER on the scanning phone alone, outside the recording.
 *
 * The first half drives the real [RoundAuthority] in process, armed in the bench's home, every
 * effect its `emit` is handed counted. The second plays a five-phone evening over loopback sockets,
 * each phone built as `CheatRoot` builds it, and reads the read off each phone's own socket.
 */
class CardReadTest {

    // ---- The house, in process -------------------------------------------------------------------

    private val seats = List(5) { Seat(it) }

    private fun inputs() = ArmingInputs(
        seats = seats,
        names = seats.map { SeatNamed(it, "SEAT${it.index}") },
        oneLines = seats.map { OneLineHeld(it, "line ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    /** The authority, with `emit` as a recorder of what went to whom, through the real schema. */
    private class Bench(inputs: ArmingInputs) {
        val pushed = mutableListOf<Effect>()
        val delivered = mutableListOf<Pair<Seat, RoundMessage>>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state ->
                pushed += effect
                for (d in EmitSchema.deliveries(effect, state)) delivered += d.seat to RoundWire.project(effect)
            },
            note = { said += it },
        )
    }

    private suspend fun armed(): Bench = Bench(inputs()).also {
        assertTrue(it.house.arm(SEED), "never armed: ${it.said}")
    }

    /** A card this seat's own order holds work at, the first actionable one. */
    private fun GameState.workCard(seat: Seat): MarkerId {
        val order = assertNotNull(workOrderFor(seat), "seat ${seat.index} holds no work order")
        val entry = order.entries.first { order.isActionable(it) && it.work.opensAs != null && it.anchor != null }
        return assertNotNull(entry.anchor, "seat ${seat.index}'s work has no card")
    }

    /**
     * **GUARD: an unregistered card is NOT A MARKER on that phone only, and nothing else happens.**
     *
     * The seat scans its own work first, so it is standing somewhere with work open. Then it reads
     * paper the home never registered. The answer is one [Effect.CardUnregistered] to that seat and
     * to nobody else; **no event reaches the rules**, which is the state object itself being the same
     * one (no reduction ran), so nothing is recorded, the seat is not placed and the open work is not
     * spent; no meeting is called; and the rules' own record of effects does not grow.
     */
    @Test
    fun anUnregisteredCardIsNotAMarkerOnThatPhoneOnlyAndNothingElseHappens() = runBlocking<Unit> {
        val bench = armed()
        val seat = Seat(2)
        bench.house.cardRead(seat, bench.house.state.workCard(seat))
        val open = assertNotNull(bench.house.state.openSubroutineFor(seat), "the seat's own card opened no work")
        val placed = assertNotNull(bench.house.state.presenceFor(seat), "the seat's own card placed it nowhere")

        val before = bench.house.state
        val emitted = bench.house.emitted.size
        val refusals = bench.house.refusals.size
        val pushed = bench.pushed.size
        val delivered = bench.delivered.size
        bench.house.cardRead(seat, STRAY)

        assertEquals(
            listOf<Effect>(Effect.CardUnregistered(seat)), bench.pushed.drop(pushed),
            "an unregistered card was not answered NOT A MARKER, once, to that seat (D-124, D-318)",
        )
        assertEquals(
            listOf(seat to (RoundMessage.CardUnregistered as RoundMessage)), bench.delivered.drop(delivered),
            "NOT A MARKER reached a phone other than the one holding the card",
        )
        assertSame(before, bench.house.state, "an unregistered card reached the rules (D-072: it is not a game event)")
        assertEquals(emitted, bench.house.emitted.size, "the rules produced an effect for paper that is not part of the home")
        assertEquals(refusals, bench.house.refusals.size, "the gate was asked about paper that is not part of the home")
        assertEquals(open, bench.house.state.openSubroutineFor(seat), "an unregistered card spent the seat's open work")
        assertEquals(placed, bench.house.state.presenceFor(seat), "an unregistered card placed the seat somewhere")
        assertNull(bench.house.state.meeting, "an unregistered card called a meeting")
        assertEquals(listOf<Effect>(Effect.CardUnregistered(seat)), bench.house.answeredOutsideTheRules)
    }

    /**
     * **A spare meeting card from the house's own deck, never registered here, calls no meeting**
     * (D-169, D-318). Its shape is the meeting card's; its id is not on the map.
     */
    @Test
    fun aSpareMeetingCardCallsNoMeetingAndIsNotAMarker() = runBlocking<Unit> {
        val bench = armed()
        val before = bench.house.state
        val pushed = bench.pushed.size
        bench.house.cardRead(Seat(1), SPARE_MEETING_CARD)
        assertNull(bench.house.state.meeting, "a spare meeting card the home never registered called a meeting")
        assertSame(before, bench.house.state, "a spare meeting card reached the rules")
        assertEquals(listOf<Effect>(Effect.CardUnregistered(Seat(1))), bench.pushed.drop(pushed))
    }

    /**
     * **A registered card of every kind still routes as it did** (D-124, D-199, D-121, D-297): work
     * opens work and places the seat; the Terminal is the station; an Egress node is work the rules
     * answer with nothing and still places the seat there; the registered meeting card calls the
     * meeting. None of them is NOT A MARKER.
     */
    @Test
    fun aRegisteredCardOfEveryKindRoutesAsBefore() = runBlocking<Unit> {
        val bench = armed()
        val resident = seats.first { bench.house.state.roleOf(it) == Role.Resident }
        val card = bench.house.state.workCard(resident)

        // Work.
        var from = bench.pushed.size
        bench.house.cardRead(resident, card)
        val answer = assertIs<Effect.ScanAnswered>(bench.pushed.drop(from).single { it is Effect.ScanAnswered })
        assertNotNull(answer.opened, "the seat's own card opened nothing")
        assertEquals(card, bench.house.state.presenceFor(resident)?.at, "work did not place the seat at its card")

        // The Terminal.
        from = bench.pushed.size
        val terminal = requireNotNull(BenchHome.map.terminal).card.id
        bench.house.cardRead(resident, terminal)
        assertIs<Effect.TerminalAnswered>(bench.pushed.drop(from).single { it is Effect.TerminalAnswered })
        assertEquals(terminal, bench.house.state.presenceFor(resident)?.at, "the Terminal did not place the seat there")
        bench.house.heard(Intent.LeaveTerminal(resident))

        // An Egress node, while one runs.
        bench.house.at(5_000)
        val insider = bench.house.state.livingInsiders.first()
        assertTrue(bench.house.fireEgress(insider), "the Egress did not start, so the node was not tried")
        val node = requireNotNull(bench.house.state.egress).nodes.first()
        from = bench.pushed.size
        bench.house.cardRead(resident, node)
        val atNode = assertIs<Effect.ScanAnswered>(bench.pushed.drop(from).single { it is Effect.ScanAnswered })
        assertNull(atNode.opened, "a node of the running Egress opened work (D-297)")
        assertEquals(node, bench.house.state.presenceFor(resident)?.at, "the node did not place the seat there")

        // None of the three was NOT A MARKER.
        assertTrue(bench.house.answeredOutsideTheRules.isEmpty(), "a registered card was answered NOT A MARKER")

        // The meeting card, on a fresh round so no Egress is running to make it inert (D-133).
        val fresh = armed()
        fresh.house.cardRead(Seat(3), BenchHome.meetingCard.id)
        assertNotNull(fresh.house.state.meeting, "the registered meeting card did not call a meeting")
        assertTrue(fresh.house.answeredOutsideTheRules.isEmpty(), "the meeting card was answered NOT A MARKER")
    }

    /**
     * **The answer is the same for both roles** (D-109's rule, D-318). An Insider and a Resident read
     * the same paper: each is sent one NOT A MARKER, the same projected message, delivered to its
     * own seat in its own living class, and the buzz is the same.
     */
    @Test
    fun anInsiderAndAResidentAreAnsweredAlike() = runBlocking<Unit> {
        val bench = armed()
        val insider = bench.house.state.livingInsiders.first()
        val resident = seats.first { bench.house.state.roleOf(it) == Role.Resident }

        val from = bench.delivered.size
        fun heardBy(seat: Seat): List<RoundMessage> = bench.delivered.drop(from).filter { it.first == seat }.map { it.second }
        val before = bench.house.state
        bench.house.cardRead(insider, STRAY)
        bench.house.cardRead(resident, STRAY)

        assertEquals(listOf<RoundMessage>(RoundMessage.CardUnregistered), heardBy(insider))
        assertEquals(heardBy(insider), heardBy(resident), "the same paper was answered differently by role")
        assertEquals(
            listOf(RoundState.Live), listOf(insider, resident).map { bench.house.state.clientClassOf(it).roundState }.distinct(),
        )
        fun buzz(message: RoundMessage) = RecordingHaptics().also { HouseBuzzer(it).heard(message) }.played
        assertEquals(buzz(heardBy(insider).single()), buzz(heardBy(resident).single()), "the hand can tell the roles apart")
        assertSame(before, bench.house.state, "either read reached the rules")
    }

    /**
     * **The buzz: acquire says *yes*, NOT A MARKER says *no*, on that phone** (D-159, D-160, D-318).
     *
     * The scan window's own Short on the acquire stays, and the house's answer adds D-160's two
     * vibrates. Read off one motor, the scan window and the buzzer both playing on it as `Cheats`
     * wires them, so what is compared is what a hand would feel.
     */
    @Test
    fun theAcquireSaysYesAndNotAMarkerSaysNo() {
        val motor = RecordingHaptics()
        val lens = Lens()
        val window = ScanWindow(lens, motor)
        val buzzer = HouseBuzzer(motor)
        var read: String? = null
        window.open { read = it }
        lens.present("10ZZZZ001")
        assertNotNull(read, "the window read nothing, so the acquire was not felt")
        buzzer.heard(RoundMessage.CardUnregistered)
        assertEquals(
            listOf(HouseBuzz.script(Haptic.Short), HouseBuzz.script(Haptic.Double)), motor.played,
            "the hand did not feel yes and then no",
        )
        assertEquals(Haptic.Double, Effect.CardUnregistered(Seat(0)).haptic)
    }

    /** A camera a test decides the deliveries of. */
    private class Lens : CardScanner {
        private var listener: ((String) -> Unit)? = null
        override val use: ScanUse = ScanUse.InRound
        override fun start(onPayload: (String) -> Unit) {
            listener = onPayload
        }
        override fun stop() {
            listener = null
        }
        override fun torch(lit: Boolean) = Unit
        fun present(payload: String) = listener?.invoke(payload)
    }

    // ---- The evening, over loopback sockets --------------------------------------------------------

    private class Kept : SeatStore {
        var token: String? = null
        override fun stored(): String? = token
        override fun keep(token: String, hostAddress: String) {
            this.token = token
        }
        override fun forget() {
            token = null
        }
    }

    private class Bonjour : Advertising {
        val ports = mutableListOf<Int>()
        override fun advertise(
            name: String,
            port: Int,
            onEvent: (String) -> Unit,
            onAir: () -> Unit,
            onFailed: (AdvertiseFailure) -> Unit,
        ): () -> Unit {
            ports += port
            onAir()
            return {}
        }
    }

    /** The motor, as a record of every buzz the house put on it. */
    private class Motor : Haptics {
        val buzzed = mutableListOf<Haptic>()
        override fun buzz(haptic: Haptic) {
            buzzed += haptic
        }
        override fun play(script: List<HapticStep>, repeating: Boolean) = Unit
        override fun stop() = Unit
    }

    private class Phone(
        scope: CoroutineScope,
        val name: String,
        val radioSpot: Seat,
        air: SimulatedAir,
        house: () -> RoundAuthority?,
    ) {
        val bonjour = Bonjour()
        val log = mutableListOf<String>()
        val motor = Motor()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(motor)

        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            subroutines = SubroutineModel(send = { intent -> client?.act(intent) }),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
            onScreen = buzzer::showing,
        )

        val pushed = mutableListOf<RoundMessage>()

        val round = RoundClient(
            scope, flow, LineWatch {}, buzzer, air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY),
        ) { log += it }.also { made ->
            client = made
            made.onMessage = { pushed += it }
        }

        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onCardRead = HouseDoors(house)::read,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = round, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        /** The scan bridge's two halves, in `Cheats`' order: the phone keeps its read, the id goes up. */
        fun read(card: MarkerId) {
            round.scanning(card)
            round.readCard(card)
        }
    }

    private class Room(val phones: List<Phone>, val house: RoundAuthority) {
        fun phoneAt(seat: Seat): Phone {
            val name = house.state.names.single { it.seat.index == seat.index }.name
            return phones.single { it.name == name }
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    private fun evening(body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air) { authority }.also { phones += it }
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                listOf("ROSE", "DANI", "MARCUS", "PRIYA").forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEachIndexed { i, p ->
                    p.model.typeLine("line $i")
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(authority, hosting = true, seed = SEED, note = {}), "the round never armed")
                until("every phone holds the round's seed") { phones.all { it.round.round.uploading } }
                scope.body(Room(phones, authority))
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /**
     * **GUARD, over real sockets: NOT A MARKER reaches the scanning phone alone, as the screen it
     * already has, with D-160's *no*; the work it had open stays open; no other phone hears a thing;
     * a spare meeting card calls nothing; and the registered meeting card still rings the house.**
     *
     * Both roles, one after the other, so the two phones' wires can be compared.
     */
    @Test
    fun overTheWireNotAMarkerIsThatPhonesAloneAndTheMeetingCardStillCalls() = evening { room ->
        val insider = room.house.state.livingInsiders.first()
        val resident = room.house.state.seats.first { room.house.state.roleOf(it) == Role.Resident }
        val answers = mutableMapOf<Role, List<RoundMessage>>()

        for ((seat, role) in listOf(resident to Role.Resident, insider to Role.Insider)) {
            val phone = room.phoneAt(seat)
            val others = room.phones.filter { it !== phone }

            // Its own card first, so it has work open at a place.
            val scans = phone.pushed.count { it is RoundMessage.ScanAnswered }
            phone.read(room.house.state.workCard(seat))
            until("$role's own card was answered") { phone.pushed.count { it is RoundMessage.ScanAnswered } > scans }
            until("$role reached the caught scan") { phone.flow.state.screen == ScreenId.ScanCaught }
            val open = assertNotNull(room.house.state.openSubroutineFor(seat), "$role's own card opened no work")
            val placed = room.house.state.presenceFor(seat)

            // Then paper this home never registered.
            val pushedBefore = phone.pushed.size
            val othersBefore = others.associateWith { it.pushed.size }
            val buzzedBefore = phone.motor.buzzed.size
            val rulesSaid = room.house.emitted.size
            phone.read(STRAY)
            until("$role was told NOT A MARKER", { "log=${phone.log.takeLast(4)}" }) { phone.flow.state.screen == ScreenId.ScanUnknown }
            // Two beats, so a push that went anywhere else has had time to arrive there.
            delay(2 * SightingUpload.CADENCE_MILLIS)

            val heard = phone.pushed.drop(pushedBefore)
            assertEquals(listOf<RoundMessage>(RoundMessage.CardUnregistered), heard, "$role: NOT A MARKER was not the one answer")
            answers[role] = heard
            assertEquals(listOf(Haptic.Double), phone.motor.buzzed.drop(buzzedBefore), "$role: NOT A MARKER did not buzz no, once")
            for (other in others) {
                assertEquals(
                    emptyList(), other.pushed.drop(othersBefore.getValue(other)),
                    "${other.name} learned something when $role read paper the home never registered",
                )
            }
            // Every card the rules are put emits a `ScanAnswered`, a `TerminalAnswered` or a ring for
            // its reader. The state itself moves with every beat's upload, so it is not the probe here.
            assertEquals(
                emptyList(),
                room.house.emitted.drop(rulesSaid).filter {
                    (it is Effect.ScanAnswered && it.seat == seat) || (it is Effect.PresenceChanged && it.seat == seat) ||
                        (it is Effect.TerminalAnswered && it.seat == seat) || it is Effect.MeetingOpened
                },
                "$role's unregistered card reached the rules",
            )
            assertEquals(open, room.house.state.openSubroutineFor(seat), "$role's open work was spent by paper")
            assertEquals(placed, room.house.state.presenceFor(seat), "$role was placed by paper")
            assertNull(room.house.state.meeting, "$role's unregistered card called a meeting")

            // A spare meeting card: the same answer, and no meeting.
            phone.read(SPARE_MEETING_CARD)
            until("$role's spare meeting card was answered") {
                phone.pushed.count { it == RoundMessage.CardUnregistered } == 2
            }
            assertNull(room.house.state.meeting, "$role's spare meeting card called a meeting")
        }
        assertEquals(answers[Role.Resident], answers[Role.Insider], "the two roles were answered differently")

        // The meeting card this home registered still calls one, from a phone's read.
        val caller = room.phoneAt(resident)
        caller.read(requireNotNull(home.map.meeting).card.id)
        until("the registered meeting card called a meeting") { room.house.state.meeting != null }
        until("every phone was rung") { room.phones.all { p -> p.pushed.any { it is RoundMessage.MeetingOpened } } }
    }

    private companion object {
        const val SEED = 20261121L

        /** Seven characters this game could print, on no map in this file. */
        val STRAY = MarkerId("ZZZZ001")

        /** A second `u_shape` from the deck, with an id no home here registered. */
        val SPARE_MEETING_CARD = MarkerId("ZZZZ002")
    }
}
