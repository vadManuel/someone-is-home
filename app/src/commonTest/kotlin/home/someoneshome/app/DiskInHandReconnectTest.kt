package home.someoneshome.app

import home.someoneshome.model.CircuitStation
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Haptic
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.Flow
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A phone that comes back holds the disk the house says it holds** (D-168, D-322; D-327, the
 * overnight review of 2026-11-22: *the phone may lose its disk on a reconnect*).
 *
 * [home.someoneshome.ui.PanelState.carrying] is written only by `ScanAnswered`, and a station step is
 * read as *the disk moved* (`Flow.stationStepped`, `before != after`), so a phone that came back with
 * nothing in hand while the house still held a disk would draw its next NOTHING FOR YOU HERE as a
 * step. **It does not, and this pins why:** the house keeps every push of the round for every seat
 * and replays it on any ask (`EffectWire.reassert`, D-097, D-268). A phone whose socket dropped
 * keeps its panel and asks from what it holds; a relaunched phone holds nothing, asks from zero and is
 * replayed its whole round, the scan answers that carried the disk included, in order. Either way
 * the last answer's disk is the one in hand, so no new replay of the carry is needed.
 *
 * Real sockets on loopback, the real link, desk, ledger, authority, effect wire and server, and the
 * phone's real round half and panel (`ReplayPacingTest`'s room). Faked: Bonjour, and the scan
 * answers, which the test puts on the wire for the guest's seat rather than a scan producing them.
 */
class DiskInHandReconnectTest {

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

    private class Air : Advertising {
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

    /** One phone's round half on its one socket, with the panel it draws. */
    private class Half(scope: CoroutineScope, wake: ScreenWake) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null
        var seatings = 0
        val flow = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true))
        val round = RoundOverTransport(
            scope, flow,
            buzzer = HouseBuzzer(SilentHaptics),
            send = { frame -> up?.invoke(frame) },
            radio = NoRadio,
            wake = wake,
        )

        init {
            scope.launch { round.run() }
        }

        override fun linked(send: suspend (TransportFrame) -> Unit) {
            up = send
        }
        override fun arrived(frame: TransportFrame) = round.frameArrived(frame)
        override fun seated() {
            seatings++
            round.reseated()
        }
        override fun roundEnded() = round.roundEnded()
    }

    /** One phone, in its own job: cancelling it is the process ending, with its files left behind. */
    private class Phone(parent: CoroutineScope, val name: String, val kept: Kept = Kept()) {
        val job = Job(parent.coroutineContext[Job])
        private val scope = CoroutineScope(parent.coroutineContext + job)
        val air = Air()
        private val wake = ScreenWake(NoIdleTimer)
        val half = Half(scope, wake)
        val link = LobbyOverTransport(scope, seat = kept, round = half, wake = wake, advertising = air)
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        fun handOver(line: String) {
            model.typeLine(line)
            check(model.handOverLine()) { "the line was refused" }
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, withinMillis: Long = 30_000, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(withinMillis) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened within $withinMillis ms: $what ${detail()}", late)
        }
    }

    private class Room(val phones: MutableList<Phone>, val house: RoundAuthority, val scope: CoroutineScope) {
        val host get() = phones.first()
        val guest get() = phones.last()
        private val seats = HashMap<String, Seat>()
        fun seatOf(p: Phone): Seat = seats.getOrPut(p.name) {
            host.link.house.armingInputs().names.single { it.name == p.name }.seat
        }
        val owed = HashMap<Int, Long>()

        suspend fun emit(effect: Effect) {
            for (d in EmitSchema.deliveries(effect, house.state)) owed[d.seat.index] = (owed[d.seat.index] ?: 0L) + 1
            host.link.house.emit(effect, house.state)
        }
    }

    private fun evening(body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val host = Phone(scope, "HOST").also { phones += it }
                lateinit var room: Room
                val said = mutableListOf<String>()
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state ->
                        for (d in EmitSchema.deliveries(effect, state)) room.owed[d.seat.index] = (room.owed[d.seat.index] ?: 0L) + 1
                        host.link.house.emit(effect, state)
                    },
                    note = { said += it },
                )
                room = Room(phones, house, scope)
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.air.ports.single()
                val guests = listOf("ROSE", "DANI", "MARCUS").map { Phone(scope, it).also { p -> phones += p } }
                guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
                until("every guest was seated") { host.model.standing.joined == 1 + guests.size }
                phones.forEachIndexed { i, p -> p.handOver("line $i") }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = { said += it }), "the round never armed: $said")
                until("every phone holds its arming pushes") {
                    phones.all { p -> p.half.round.held >= (room.owed[room.seatOf(p).index] ?: Long.MAX_VALUE) }
                }
                scope.body(room)
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** The guest scans the Rack card with a disk for it: a station step, and the disk in hand moves on. */
    private suspend fun Room.carrying(disk: DiskInHand) {
        val seat = seatOf(guest)
        emit(Effect.ScanAnswered(seat, opened = null, haptic = null, carrying = disk))
        until("the guest holds the disk") { guest.half.round.held >= (owed[seat.index] ?: Long.MAX_VALUE) }
    }

    /** NOTHING FOR YOU HERE, with the disk still in hand: the house's word for a card with nothing on it. */
    private suspend fun Room.nothingHere(phone: Phone, disk: DiskInHand) {
        val seat = seatOf(phone)
        emit(Effect.ScanAnswered(seat, opened = null, haptic = Haptic.Double, carrying = disk))
        until("the phone heard NOTHING FOR YOU HERE") { phone.half.round.held >= (owed[seat.index] ?: Long.MAX_VALUE) }
    }

    private val disk = DiskInHand(disk = 3, to = CircuitStation.Disposal)

    @Test
    fun aPhoneWhoseSocketDroppedStillHoldsItsDisk() = evening { room ->
        room.carrying(disk)
        assertEquals(Flow.carriedOf(disk), room.guest.half.flow.state.carrying, "the arrangement is wrong: no disk in hand")
        val seat = room.seatOf(room.guest)
        room.host.link.house.dropConnection(seat)
        until("the guest came back", detail = { "seatings ${room.guest.half.seatings}" }) { room.guest.half.seatings >= 2 }
        assertEquals(Flow.carriedOf(disk), room.guest.half.flow.state.carrying, "the reconnect lost the disk in hand")
        room.nothingHere(room.guest, disk)
        assertEquals(ScreenId.ScanBad, room.guest.half.flow.state.screen, "NOTHING FOR YOU HERE after a reconnect was drawn as a station step")
    }

    @Test
    fun aRelaunchedPhoneIsReplayedTheDiskItHolds() = evening { room ->
        room.carrying(disk)
        val guest = room.guest
        val seat = room.seatOf(guest)
        guest.job.cancel()
        until("the house lost the guest's connection") { room.host.link.house.armingInputs().seats.size == room.phones.size - 1 }

        // The app opened again over the same files: a fresh panel, holding nothing, by the same token.
        val fresh = Phone(room.scope, guest.name, guest.kept)
        room.phones[room.phones.indexOf(guest)] = fresh
        assertEquals(null, fresh.half.flow.state.carrying, "the arrangement is wrong: a fresh panel holds a disk")
        fresh.model.attachTo(NearbyHome(home.name, "127.0.0.1", room.host.air.ports.last()))
        val owed = room.owed.getValue(seat.index)
        until("the relaunched phone holds its whole stream", detail = { "held ${fresh.half.round.held} of $owed" }) {
            fresh.half.round.held == owed
        }
        assertEquals(Flow.carriedOf(disk), fresh.half.flow.state.carrying, "the relaunched phone was not given back the disk the house holds")
        room.nothingHere(fresh, disk)
        assertEquals(ScreenId.ScanBad, fresh.half.flow.state.screen, "NOTHING FOR YOU HERE after a relaunch was drawn as a station step")
    }

    private companion object {
        const val SEED = 20261150L
    }
}
