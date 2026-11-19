package home.someoneshome.app

import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
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

        val round = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), air.radioFor(radioSpot), wake,
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
     * **Bluetooth, on purpose** (D-301): the one bench through [SimulatedAir] that does not play on
     * distance. Eight phones each touching a partner drawn afresh every round need every pair
     * touchable at once, and D-300's pairing ranges a phone's few nearest and keeps them, so most of
     * these touches could never land on distance (D-301's raised item 1). The question here is the
     * arrival order, which is the same under either source.
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

                val touches = IntArray(phones.size)
                for (pairs in roundRobin(phones.size)) {
                    // Four pairs, each a room from the next, all held together at once.
                    pairs.forEachIndexed { k, (a, b) ->
                        air.place(phones[a].radioSpot, SimulatedAir.Spot(k * SimulatedAir.APART_METRES, 10.0))
                        air.place(phones[b].radioSpot, SimulatedAir.Spot(k * SimulatedAir.APART_METRES + SimulatedAir.TOUCH_METRES, 10.0))
                        touches[a]++
                        touches[b]++
                    }
                    repeat(ContactAct.HOLD_BEATS) { delay(SightingUpload.CADENCE_MILLIS + 250) }
                    air.spreadOut(phones.map { it.radioSpot })
                    delay(SightingUpload.CADENCE_MILLIS + 500)
                }
                assertTrue(!house.state.ended, "the round ended mid-test: $said")

                val seatOf = phones.associateWith { p -> house.state.names.single { it.name == p.name }.seat.index }
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
     * once; every toucher is answered once per touch, and nothing goes out against a newer state.
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

        val air = SimulatedAir()
        val window = { ContactWindow.containing(wallClockMillis()) }
        val pending = mutableListOf<Pair<Seat, RoundBody.Sighted>>()
        val uploaders = seats.associate { seat ->
            seat.index to ContactUploader(air.radioFor(seat), window, send = { pending += seat to it }).also {
                it.heard(RoundMessage.ContactSeedIssued(contactSeedFor(house.state.seed, seat)))
            }
        }
        air.spreadOut(seats)

        /** One beat on every seat, and every upload put to the house at once, each from its own coroutine. */
        suspend fun beat() {
            for (seat in seats) uploaders.getValue(seat.index).beat()
            val batch = pending.toList()
            pending.clear()
            batch.map { (seat, body) -> launch { house.sighted(seat, body) } }.joinAll()
        }

        val touches = IntArray(seats.size)
        for (pairs in roundRobin(seats.size)) {
            pairs.forEachIndexed { k, (a, b) ->
                air.place(seats[a], SimulatedAir.Spot(k * SimulatedAir.APART_METRES, 10.0))
                air.place(seats[b], SimulatedAir.Spot(k * SimulatedAir.APART_METRES + SimulatedAir.TOUCH_METRES, 10.0))
                touches[a]++
                touches[b]++
            }
            repeat(ContactAct.HOLD_BEATS) { beat() }
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
    }
}
