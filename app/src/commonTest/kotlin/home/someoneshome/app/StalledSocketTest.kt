package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
 * **One phone whose socket has stopped draining holds up nobody else's frames, and every phone's
 * stream is still the house's, in the house's order** (R12, D-244).
 *
 * D-241 put the house's reduction and its pushes under one lock, which is right for the order and
 * wrong for the wire if the push is a socket write: a guest whose phone is suspended, out of range or
 * half-defunct stops reading, its socket's buffers fill, `ws.send` suspends, and every other phone's
 * frames wait behind it under the lock. That is the lamp's same-frame promise (rule 7) and the
 * evasion return path both waiting on the slowest phone in the house.
 *
 * Real sockets on loopback, the real link, desk, ledger, authority, effect wire and server. Faked:
 * Bonjour, and **one guest's socket write is held back** at the server's real write
 * ([LobbyHouse.socketWrite]) until the test lets it go, which is what a phone that stopped draining
 * looks like from the house: the write does not return.
 */
class StalledSocketTest {

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

    /** One phone's round half on its one socket, as `RoundClient` builds it. */
    private class Half(scope: CoroutineScope, wake: ScreenWake) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null

        /** Every push this phone applied, in order. */
        val pushed = mutableListOf<RoundMessage>()
        val round = RoundOverTransport(
            scope, FlowModel(PanelState(screen = ScreenId.Home)),
            buzzer = HouseBuzzer(SilentHaptics),
            send = { frame -> up?.invoke(frame) },
            radio = NoRadio,
            wake = wake,
            onInbound = { body -> (RoundWire.decodeOrNull(body) as? RoundBody.Push)?.let { pushed += it.message } },
        )

        init {
            scope.launch { round.run() }
        }

        override fun linked(send: suspend (TransportFrame) -> Unit) {
            up = send
        }
        override fun arrived(frame: TransportFrame) = round.frameArrived(frame)
        override fun seated() = round.reseated()
        override fun roundEnded() = round.roundEnded()
    }

    private class Phone(scope: CoroutineScope, val name: String) {
        val air = Air()
        private val wake = ScreenWake(NoIdleTimer)
        val half = Half(scope, wake)
        val link = LobbyOverTransport(scope, seat = Kept(), round = half, wake = wake, advertising = air)
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        fun handOver(line: String) {
            model.typeLine(line)
            check(model.handOverLine()) { "the line was refused" }
        }
    }

    /** What the house handed `emit`, in order, and to whom the schema sent each. */
    private class Outbox {
        val sent = mutableListOf<Pair<Effect, List<Int>>>()

        fun record(effect: Effect, state: GameState) {
            sent += effect to EmitSchema.deliveries(effect, state).map { it.seat.index }
        }

        fun streamOf(seat: Int): List<RoundMessage> =
            sent.filter { (_, to) -> seat in to }.map { (effect, _) -> RoundWire.project(effect) }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    private suspend fun until(what: String, withinMillis: Long = 20_000, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(withinMillis) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened within ${withinMillis} ms: $what ${detail()}", late)
        }
    }

    /**
     * **GUARD (R12): a guest whose socket stops draining delays nobody else.** Four phones in a
     * lobby; one guest's writes are held back from just before LIGHTS OUT. The arming's pushes and a
     * meeting's after it reach every other phone within [BOUND_MILLIS], the authority's doors return,
     * and every other phone's stream is exactly the house's, in the house's order. Then the held
     * guest's socket drains, and its stream is the house's, in order, too.
     */
    @Test
    fun aGuestWhoseSocketStopsDrainingHoldsUpNobodyElse() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        val drain = CompletableDeferred<Unit>()
        try {
            scope.async {
                val outbox = Outbox()
                val host = Phone(scope, "HOST").also { phones += it }
                val said = mutableListOf<String>()
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state ->
                        outbox.record(effect, state)
                        host.link.house.emit(effect, state)
                    },
                    note = { said += it },
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.air.ports.single()
                val guests = listOf("ROSE", "DANI", "MARCUS").map { Phone(scope, it).also { p -> phones += p } }
                guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
                until("every guest was seated") { host.model.standing.joined == 1 + guests.size }
                phones.forEachIndexed { i, p -> p.handOver("line $i") }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }

                // The first guest's phone stops draining: from here its socket's writes do not return.
                val stalled = guests.first()
                val stalledSeat = host.link.house.armingInputs().names.single { it.name == stalled.name }.seat
                host.link.house.socketWrite = { seat, write ->
                    if (seat == stalledSeat) drain.await()
                    write()
                }
                val others = phones.filter { it !== stalled }
                fun seatOf(p: Phone): Int = host.link.house.armingInputs().names.single { it.name == p.name }.seat.index
                val seats = phones.associateWith(::seatOf)

                // LIGHTS OUT, through production's arming path, timed from the press.
                val pressed = nowMillis()
                val armed = scope.async { armAtLightsOut(house, hosting = true, seed = SEED, note = { said += it }) }
                until("LIGHTS OUT returned while one guest's socket was not draining", BOUND_MILLIS) { armed.isCompleted }
                assertTrue(armed.await(), "the round never armed: $said")
                until(
                    "every other phone held its arming pushes while one guest's socket was not draining", BOUND_MILLIS,
                    { others.joinToString { "${it.name} ${it.half.pushed.size}/${outbox.streamOf(seats.getValue(it)).size}" } },
                ) { others.all { it.half.pushed.size >= outbox.streamOf(seats.getValue(it)).size } }

                // A second reduction behind the first, through another door: a meeting called.
                val caller = house.state.livingSeats.first()
                val meeting = scope.async { house.mint(Event.MeetingCalled(house.now(), caller, MeetingTrigger.MeetingCard)) }
                until("the meeting's door returned while one guest's socket was not draining", BOUND_MILLIS) { meeting.isCompleted }
                until(
                    "every other phone heard the meeting while one guest's socket was not draining", BOUND_MILLIS,
                    { others.joinToString { "${it.name} ${it.half.pushed.size}/${outbox.streamOf(seats.getValue(it)).size}" } },
                ) { others.all { it.half.pushed.size >= outbox.streamOf(seats.getValue(it)).size } }
                val took = nowMillis() - pressed
                println("R12 — EVERY OTHER PHONE HELD BOTH REDUCTIONS ${took} MS AFTER THE PRESS · THE HELD GUEST HELD ${stalled.half.pushed.size}")
                assertTrue(
                    stalled.half.pushed.size < outbox.streamOf(seats.getValue(stalled)).size,
                    "the held guest's socket was not actually holding anything back, so nothing was measured",
                )
                for (p in others) {
                    assertEquals(outbox.streamOf(seats.getValue(p)), p.half.pushed, "${p.name}'s stream is not the house's, in the house's order")
                }

                // The held phone drains, and its stream is the house's, in order.
                drain.complete(Unit)
                until("the held guest caught up") { stalled.half.pushed.size >= outbox.streamOf(seats.getValue(stalled)).size }
                delay(SETTLE_MILLIS)
                for (p in phones) {
                    assertEquals(outbox.streamOf(seats.getValue(p)), p.half.pushed, "${p.name}'s stream is not the house's, in the house's order")
                }
            }.await()
        } finally {
            drain.complete(Unit)
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    private companion object {
        const val SEED = 20261150L

        /** How long another phone may wait on a held one. Loopback delivers in milliseconds. */
        const val BOUND_MILLIS = 2_000L
        const val SETTLE_MILLIS = 300L
    }
}
