package home.someoneshome.app

import home.someoneshome.core.ContactGate
import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.Balance
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.monotonicMillis
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.platform.wallClockMillis
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Arrivals are attended strictly in order: one frame per toucher per touch, and effects on the
 * wire in the order of their reductions, however many touches land at once** (R6, D-241).
 *
 * The bug: the app root's doors launched one coroutine per arrival, so the arrival queue took the
 * next upload while the last was still pushing. `sighted` pushes between a touch's two halves, a
 * second touch reduced in that gap replaced `GameState.touches`, and the first touch's second half
 * was taken for a fresh touch: both of its phones were answered twice (T1, D-220), and two
 * reductions' effects went out interleaved. The fix is two layers, and each has a test here.
 *
 * 1. **The queue awaits the rules** ([HouseDoors], `LobbyHouse.attend`). The first test is eight
 *    phones over the real loopback transport, built as `CheatRoot` builds them, every upload up its
 *    own socket on the real cadence, four pairs touching at once in each of seven rounds (every pair
 *    of the eight touches once). The house's `emit` yields before it writes, as a real send does
 *    whenever a socket's buffer is full or the host's lock is held: without it the window is open
 *    only when Ktor happens to suspend, and the test would pass on luck.
 * 2. **The authority takes one reduction at a time** (`RoundAuthority`'s lock), because the queue is
 *    not its only caller: the tick driver and the bench reach it from their own coroutines. The
 *    second test is concurrent callers on the authority alone.
 *
 * Asserted in both: every phone that touched is answered once per touch and nobody else is; the
 * state each effect goes out with is the house's state at that moment (no reduction landed between
 * an effect's reduction and its send); and, over sockets, every phone's stream is exactly the
 * schema's deliveries in the order the house made them.
 *
 * **On distance, as every round is** (D-308). The house ranges only close pairs the game needs
 * (D-306), and a touch with no armed Revoke in it is needed only when one of its phones is a sensor
 * that could place the other on the map. So every phone here first opens a window at its own card
 * ([performEverywhere]): each is a sensor, every close pair is needed, every touch is measured and
 * answered, and the question, the arrival order, is asked of 28 touches as it was on Bluetooth.
 */
class ArrivalOrderTest {

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

    /** One phone, as `CheatRoot` builds it, minus the screen. [radioSpot] labels its radio in the air. */
    private class Phone(
        scope: CoroutineScope,
        val name: String,
        val radioSpot: Seat,
        air: SimulatedAir,
        house: () -> RoundAuthority?,
    ) {
        val bonjour = Bonjour()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        val flow = FlowModel(PanelState(screen = ScreenId.Home), send = { intent -> client?.act(intent) })

        /** Every push this phone was sent, decoded, in arrival order. */
        val pushed = mutableListOf<RoundMessage>()

        // Its UWB radio at the phones' own three peers (D-308): every touch is read on distance.
        val round = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), air.radioFor(radioSpot), wake,
            ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY),
        ) {}.also { made ->
            client = made
            made.onMessage = { pushed += it }
        }

        val link = LobbyOverTransport(
            scope, onEvent = {}, seat = Kept(),
            // `CheatRoot`'s doors, exactly.
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = round, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        /** The touch answers this phone was sent: the padded answer, a spent Revoke, a Revoke landing. */
        val answers: Int
            get() = pushed.count {
                it is RoundMessage.ContactAnswered || it is RoundMessage.AbilityFired || it is RoundMessage.RevokeLanded
            }
    }

    /**
     * What the house handed `emit`, checked at the moment it was handed: [stale] is every effect whose
     * state was not the house's state then, which is a reduction landing between an effect's own
     * reduction and its send.
     */
    private class Outbox {
        val sent = mutableListOf<Pair<Effect, List<Int>>>()
        val stale = mutableListOf<Effect>()

        suspend fun emitting(effect: Effect, state: GameState, house: RoundAuthority?, write: suspend () -> Unit) {
            if (house != null && state !== house.state) stale += effect
            sent += effect to EmitSchema.deliveries(effect, state).map { it.seat.index }
            // A send that suspends, as a real one does under a full buffer or a held lock.
            yield()
            write()
            if (house != null && state !== house.state) stale += effect
        }

        /** What [seat] should have been sent, in the order the house sent it. */
        fun streamOf(seat: Int): List<RoundMessage> =
            sent.filter { (_, to) -> seat in to }.map { (effect, _) -> RoundWire.project(effect) }

        fun answersTo(seat: Int): Int = sent.count { (effect, to) ->
            seat in to && (effect is Effect.ContactAnswered || effect is Effect.AbilityFired || effect is Effect.RevokeLanded)
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    /**
     * **Every seat opens a window at its own first card** (D-136, D-306): each is then a sensor, so
     * every close pair of living phones is one the map could use, and the house ranges it. Minted as
     * the house's own scan answer would be: a stand-in for eight people walking to eight cards.
     */
    private suspend fun performEverywhere(house: RoundAuthority) {
        for (seat in house.state.seats) {
            val card = house.state.workOrderFor(seat)?.entries?.firstOrNull()?.anchor ?: error("seat ${seat.index} has no card to open")
            house.mint(Event.MarkerScanned(house.now(), seat, card))
            check(house.state.presenceFor(seat)?.open == true) { "seat ${seat.index}'s card opened no window: ${house.refusals}" }
        }
    }

    /** Every pair of [n] spots exactly once, as rounds of n/2 disjoint pairs (the circle method). */
    private fun roundRobin(n: Int): List<List<Pair<Int, Int>>> {
        val ring = (1 until n).toMutableList()
        return List(n - 1) {
            val order = listOf(0) + ring
            val pairs = (0 until n / 2).map { i -> order[i] to order[n - 1 - i] }
            ring.add(0, ring.removeAt(ring.lastIndex))
            pairs
        }
    }

    // ---- 1 · Over the sockets -------------------------------------------------------------------------

    /**
     * **GUARD (R6): eight phones, seven rounds of four simultaneous touches, over loopback.** Every
     * phone is answered exactly once per touch it was in, nothing goes out against a newer state, and
     * every phone's stream is exactly what the house sent it, in the house's order.
     *
     * **On distance** (D-308), every phone a sensor at its own card, so every close pair is needed
     * (D-306) and three peers a phone. Each pair is held until its touch has landed, each round's
     * within [TOUCH_LANDS_MILLIS], then let go, inside the pair's five-second grace, so a touch is one
     * touch.
     */
    @Test
    fun manyTouchesAtOnceOverTheSocketsAnswerEachToucherOncePerTouch() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                val outbox = Outbox()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air) { authority }.also { phones += it }
                val said = mutableListOf<String>()
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> outbox.emitting(effect, state, authority) { host.link.house.emit(effect, state) } },
                    note = { said += it },
                )
                val house = authority
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                GUESTS.forEachIndexed { i, name ->
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
                assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = { said += it }), "the round never armed: $said")
                until("every phone holds the round's seed") { phones.all { it.round.round.uploading } }
                performEverywhere(house)
                val seatOf = phones.associateWith { p -> house.state.names.single { it.name == p.name }.seat.index }
                // The house's clock runs on real time, as the app's tick driver runs it; only the
                // clock moves, nothing is minted. The pairing's grace (D-300) and the pair's grace
                // after a touch are counted on it, and a clock that never moves holds both open.
                val started = monotonicMillis()
                val base = house.clock
                scope.launch {
                    while (true) {
                        house.advanceTo(Tick(base + (monotonicMillis() - started) * Balance.TICKS_PER_SECOND / 1_000L))
                        delay(CLOCK_STEP_MILLIS)
                    }
                }

                val touches = IntArray(phones.size)
                val landedAfter = mutableListOf<Long>()
                for ((r, pairs) in roundRobin(phones.size).withIndex()) {
                    // Four pairs, each a room from the next, all held together at once.
                    pairs.forEachIndexed { k, (a, b) ->
                        air.place(phones[a].radioSpot, SimulatedAir.Spot(k * SimulatedAir.APART_METRES, 10.0))
                        air.place(phones[b].radioSpot, SimulatedAir.Spot(k * SimulatedAir.APART_METRES + SimulatedAir.TOUCH_METRES, 10.0))
                        touches[a]++
                        touches[b]++
                    }
                    val heldAt = monotonicMillis()
                    val toucher = pairs.flatMap { (a, b) -> listOf(a, b) }
                    // Held until every touch of the round has landed, then let go.
                    until("round $r: every held touch landed", {
                        toucher.joinToString { "${phones[it].name} ${outbox.answersTo(seatOf.getValue(phones[it]))}/${touches[it]}" } +
                            " · the house keeps ${house.ranging?.pairs()?.map { it.low.index to it.high.index }}" +
                            " · the phones range ${phones.map { p -> seatOf.getValue(p) to air.rangingFor(p.radioSpot).ranging.size }}" +
                            " · seats ${phones.map { it.name to seatOf.getValue(it) }}" +
                            " · idle spares ${phones.map { p -> air.rangingFor(p.radioSpot).spares().size }}" +
                            " · formed ${house.ranging?.pairsFormed} at tick ${house.clock}"
                    }) { toucher.all { outbox.answersTo(seatOf.getValue(phones[it])) >= touches[it] } }
                    landedAfter += monotonicMillis() - heldAt
                    air.spreadOut(phones.map { it.radioSpot })
                    delay(SightingUpload.CADENCE_MILLIS + 500)
                }
                assertTrue(!house.state.ended, "the round ended mid-test: $said")
                println("D-308 EIGHT PHONES ON DISTANCE — each round's touches all landed after $landedAfter ms")
                landedAfter.forEachIndexed { r, ms ->
                    assertTrue(ms <= TOUCH_LANDS_MILLIS, "round $r's held touches took $ms ms to land, over $TOUCH_LANDS_MILLIS")
                }

                until("every phone holds what the house sent it", {
                    phones.joinToString { "${it.name} ${it.pushed.size}/${outbox.streamOf(seatOf.getValue(it)).size}" }
                }) { phones.all { it.pushed.size >= outbox.streamOf(seatOf.getValue(it)).size } }
                delay(SETTLE_MILLIS)

                val answered = phones.associate { it.name to it.answers }
                println("R6 ANSWERS PER PHONE — $answered · TOUCHES PER PHONE ${touches.toList()}")
                assertEquals(emptyList(), outbox.stale, "an effect went out after another reduction had landed")
                phones.forEachIndexed { i, p ->
                    val seat = seatOf.getValue(p)
                    assertEquals(touches[i], outbox.answersTo(seat), "the house answered ${p.name} (seat $seat) other than once per touch")
                    assertEquals(touches[i], p.answers, "${p.name} (seat $seat) received other than one answer per touch")
                    assertEquals(outbox.streamOf(seat), p.pushed, "${p.name}'s stream is not the house's, in the house's order")
                }
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    // ---- 2 · Concurrent callers on the authority ---------------------------------------------------

    /**
     * **GUARD (R6): the authority takes one reduction at a time, whoever calls it.** Eight seats,
     * every upload of a beat put to `sighted` from its own coroutine at once (as the tick driver and
     * the bench call beside the queue), with an `emit` that suspends. Every pair of the eight touches
     * once, on distance, every seat a sensor at its own card; every toucher is answered once per
     * touch, and nothing goes out against a newer state.
     */
    @Test
    fun concurrentCallersOnTheAuthorityTakeOneReductionAtATime() = runBlocking {
        val seats = List(GUESTS.size + 1) { Seat(it) }
        val inputs = ArmingInputs(
            seats = seats,
            names = seats.map { SeatNamed(it, "NAME${it.index}") },
            oneLines = seats.map { OneLineHeld(it, "line ${it.index}") },
            chosenInsiders = 1,
            voteWindowSeconds = 45,
            readyToArm = true,
            home = BenchHome.home,
        )
        val outbox = Outbox()
        var authority: RoundAuthority? = null
        authority = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> outbox.emitting(effect, state, authority) {} },
            note = {},
        )
        val house = authority
        assertTrue(house.arm(SEED), "the round never armed")
        performEverywhere(house)

        val air = SimulatedAir()
        val window = { ContactWindow.containing(wallClockMillis()) }
        val pending = mutableListOf<Pair<Seat, RoundBody.Sighted>>()
        val uploaders = seats.associate { seat ->
            seat.index to ContactUploader(
                air.radioFor(seat), window,
                ranging = air.rangingFor(seat, SimulatedAir.BENCH_CAPACITY),
                send = { pending += seat to it },
            ).also {
                it.heard(RoundMessage.ContactSeedIssued(contactSeedFor(house.state.seed, seat)))
            }
        }
        air.spreadOut(seats)

        /**
         * One second on the house's clock, then one beat on every seat, and every upload put to the
         * house at once, each from its own coroutine; each plan back to its phone's radio.
         */
        suspend fun beat() {
            house.advanceTo(Tick(house.clock + ContactGate.UPLOAD_TICKS))
            for (seat in seats) uploaders.getValue(seat.index).beat()
            val batch = pending.toList()
            pending.clear()
            batch.map { (seat, body) -> launch { uploaders.getValue(seat.index).planned(house.sighted(seat, body)) } }.joinAll()
        }

        val touches = IntArray(seats.size)
        for (pairs in roundRobin(seats.size)) {
            pairs.forEachIndexed { k, (a, b) ->
                air.place(seats[a], SimulatedAir.Spot(k * SimulatedAir.APART_METRES, 10.0))
                air.place(seats[b], SimulatedAir.Spot(k * SimulatedAir.APART_METRES + SimulatedAir.TOUCH_METRES, 10.0))
                touches[a]++
                touches[b]++
            }
            // Held until every touch of the round has landed, then let go inside the pair's grace.
            for (i in 0 until ContactAct.HOLD_BEATS) {
                if (seats.all { outbox.answersTo(it.index) >= touches[it.index] }) break
                beat()
            }
            air.spreadOut(seats)
            beat()
        }

        assertTrue(!house.state.ended, "the round ended mid-test")
        assertEquals(emptyList(), outbox.stale, "an effect went out after another reduction had landed")
        for (seat in seats) {
            assertEquals(touches[seat.index], outbox.answersTo(seat.index), "seat ${seat.index} was answered other than once per touch")
        }
    }

    private companion object {
        val GUESTS = listOf("ROSE", "DANI", "MARCUS", "ADA", "TEO", "IVY", "SOL")
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 500L

        /** How often the bench moves the house's clock to real time: a tick is 50 ms. */
        const val CLOCK_STEP_MILLIS = 50L

        /**
         * **A held touch lands within eight seconds** (D-302): pairing two phones the house was not
         * ranging, starting both, and both phones' readings, with room for a loaded machine's late beat.
         */
        const val TOUCH_LANDS_MILLIS = 8_000L
    }
}
