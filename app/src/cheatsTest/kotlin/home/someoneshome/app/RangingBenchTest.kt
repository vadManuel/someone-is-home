package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.monotonicMillis
import home.someoneshome.platform.transport.AdvertiseFailure
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLock
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The UWB pairing over real sockets, through the fake radio (U6, D-300)**: phones with
 * [SimulatedRanging] radios are handed plans in their uploads' answers, start ranging the right
 * peers, and their distances come back to the house; a Revoked phone keeps ranging; every answer and
 * every upload is one length on every phone throughout.
 *
 * Five phones built as `CheatRoot` builds one: a real [LobbyOverTransport] and [RoundClient] over
 * loopback, a real [RoundAuthority] on the host armed at LIGHTS OUT, each phone's uploads on the
 * real one-second cadence. Each phone's Bluetooth is a [SimulatedRadio] and its UWB a
 * [SimulatedRanging] at capacity 1, in one [SimulatedAir]: two pairs of phones stand close, a room
 * apart from each other, and the fifth stands alone. Bluetooth decides nothing but who ranges whom;
 * the house's clock runs at real time on the driver's 20 ticks a second.
 */
class RangingBenchTest {

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

    /** Every upload going up and every answer coming down one phone's socket, by encoded length. */
    private class Tap(private val inner: RoundClient) : RoundSeam {
        private val lock = NSLock()
        private val ups = mutableListOf<Int>()
        private val answers = mutableListOf<Int>()
        val up: List<Int> get() = locked { ups.toList() }
        val down: List<Int> get() = locked { answers.toList() }

        private fun <T> locked(what: () -> T): T {
            lock.lock()
            try {
                return what()
            } finally {
                lock.unlock()
            }
        }

        override fun linked(send: suspend (TransportFrame) -> Unit) = inner.linked { frame ->
            val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
            if (body is RoundBody.Sighted) locked { ups += TransportWire.encode(frame).length }
            send(frame)
        }

        override fun arrived(frame: TransportFrame) {
            val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
            if (body is RoundBody.UploadHeard) locked { answers += TransportWire.encode(frame).length }
            inner.arrived(frame)
        }

        override fun seated() = inner.seated()
        override fun roundEnded() = inner.roundEnded()
        override fun left() = inner.left()
        override fun leftTheForeground() = inner.leftTheForeground()
        override fun backInTheForeground() = inner.backInTheForeground()
    }

    private class Phone(scope: CoroutineScope, val name: String, val spot: Seat, air: SimulatedAir, house: () -> RoundAuthority?) {
        val bonjour = Bonjour()
        private val wake = ScreenWake(NoIdleTimer)
        val ranging: SimulatedRanging = air.rangingFor(spot, capacity = 1)
        val round = RoundClient(
            scope, FlowModel(PanelState(screen = ScreenId.Home)), LineWatch {}, HouseBuzzer(SilentHaptics),
            air.radioFor(spot), wake, ranging = ranging,
        ) {}
        val tap = Tap(round)
        val link = LobbyOverTransport(
            scope, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = tap, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    @Test
    fun phonesAreHandedPlansRangeTheRightPeersAndTheirDistancesComeBackRevokedOrNot() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "EMRE")
                val host = Phone(scope, names[0], Seat(0), air) { authority }.also { phones += it }
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                authority = house
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                names.drop(1).forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEach { p ->
                    p.model.typeLine("a line from ${p.name}")
                    check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                // Two pairs of phones close together, a room apart, and one alone. Radios by spot.
                air.place(Seat(0), SimulatedAir.Spot(0.0, 0.0))
                air.place(Seat(1), SimulatedAir.Spot(0.8, 0.0))
                air.place(Seat(2), SimulatedAir.Spot(10.0, 0.0))
                air.place(Seat(3), SimulatedAir.Spot(10.0, 0.6))
                air.place(Seat(4), SimulatedAir.Spot(25.0, 0.0))
                val seatOf = host.link.house.armingInputs().names.associate { it.name to it.seat }
                fun houseSeat(p: Phone) = seatOf.getValue(p.name)

                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261119L, note = {}), "the round never armed")
                // The house's clock at real time, the tick driver's 20 a second, from the moment the
                // Insider's Revoke is ready: one jump, before any pair forms, so no pair is aged past
                // its grace by a jump no house's clock makes.
                val insider = house.state.livingInsiders.single()
                val ready = house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt?.step ?: 0L
                house.advanceTo(Tick(ready))
                val started = monotonicMillis()
                scope.launch {
                    while (true) {
                        house.advanceTo(Tick(ready + (monotonicMillis() - started) / 50))
                        delay(50)
                    }
                }
                until("every phone is uploading on its seed") { phones.all { it.round.round.uploading } }

                // The plans: within a few beats each close pair is ranging, each phone the other.
                until("both close pairs are ranging each other", {
                    phones.joinToString { p -> "${p.spot.index}→${p.ranging.ranging.map { p.ranging.peerOf(it)?.index }}" }
                }) {
                    listOf(0 to 1, 1 to 0, 2 to 3, 3 to 2).all { (a, b) ->
                        val r = phones[a].ranging
                        r.ranging.size == 1 && r.peerOf(r.ranging.single()) == Seat(b)
                    }
                }
                assertTrue(phones[4].ranging.ranging.isEmpty(), "the phone standing alone was paired")
                assertEquals(2, house.ranging.pairs.size, "the house keeps other pairs than the two close ones: ${house.ranging.pairs}")

                // The distances come back: 0.8 m and 0.6 m, ±3 cm, 6 or 7 readings a second.
                suspend fun distancesComeBack(p: Phone, expectedCm: Int) {
                    until("${p.name}'s distance reached the house", { "${house.ranging.reports}" }) {
                        house.ranging.reports.firstOrNull { it.seat == houseSeat(p) }?.report?.readings?.singleOrNull()?.readings in 6..7
                    }
                    val reading = house.ranging.reports.first { it.seat == houseSeat(p) }.report.readings.single()
                    val cm = assertNotNull(reading.nearestCm)
                    assertTrue(abs(cm - expectedCm) <= SimulatedAir.UWB_CLEAR_NOISE_CM, "${p.name} read $cm cm at $expectedCm")
                    val pair = house.ranging.pairs.single { it.handle == reading.handle }
                    assertTrue(pair.has(houseSeat(p)), "${p.name} reported a pair the house did not deal it")
                }
                distancesComeBack(phones[0], 80)
                distancesComeBack(phones[1], 80)
                distancesComeBack(phones[2], 60)
                distancesComeBack(phones[3], 60)

                // A Revoke puts one of the first pair out; its phone keeps ranging and keeps reporting.
                val target = listOf(phones[1], phones[0]).map(::houseSeat).first { it != insider }
                house.heard(Intent.ArmRevoke(insider))
                house.mint(Event.ContactMade(house.now(), insider, target))
                assertTrue(house.state.isRevoked(target), "the fixture revoked nobody: ${house.refusals}")
                val outPhone = phones.single { houseSeat(it) == target }
                until("the Revoked phone knows it is out") { outPhone.round.round.outOfTheRound }
                val handle = outPhone.ranging.ranging.single()
                delay(3 * 1_000L + 300)
                assertEquals(listOf(handle), outPhone.ranging.ranging, "the Revoked phone stopped ranging its pair")
                val partner = phones.single { it !== outPhone && it.ranging.ranging == listOf(handle) }
                distancesComeBack(outPhone, 80)
                distancesComeBack(partner, 80)

                // One length for every upload and every answer, on every phone, all round.
                val ups = phones.flatMap { it.tap.up }.toSet()
                val downs = phones.flatMap { it.tap.down }.toSet()
                println("D-300 BENCH — uploads ${phones.map { it.tap.up.size }} at $ups B, answers ${phones.map { it.tap.down.size }} at $downs B")
                assertEquals(1, ups.size, "uploads differ in length between phones or over the round: $ups")
                assertEquals(1, downs.size, "answers differ in length between phones or over the round: $downs")
                assertTrue(phones.all { it.tap.down.size >= 5 }, "a phone was answered fewer than five times")
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            job.cancel()
        }
    }
}
