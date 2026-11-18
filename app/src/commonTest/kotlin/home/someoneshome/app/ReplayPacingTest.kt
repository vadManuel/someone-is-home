package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.platform.transport.OUTBOUND_FRAMES
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
 * **A phone that asks to be caught up is caught up, once, at its own pace, however far behind it is**
 * (V2 of the second review, D-268).
 *
 * Until D-268 every ask was answered with a full copy of everything after it, written at once onto
 * the seat's one live queue, which holds [OUTBOUND_FRAMES] (512) before the house drops the link
 * (D-244). A relaunched phone in a seat with more than 511 pushes was dropped by its own replay, came
 * back, asked, and was dropped again, forever. A phone behind a hole asked again on every push after
 * it, and a burst behind one lost frame queued that many copies.
 *
 * Real sockets on loopback, the real link, desk, ledger, authority, effect wire and server, and each
 * phone's real round half. Faked: Bonjour; the effects are put on the wire by the test (a backlog of
 * meter pushes, and a burst of `VoteProgressed`, the push every lock in a vote sends every seat) rather
 * than played; and for the lagging phone, the server's real write to it is slowed
 * ([LobbyHouse.socketWrite]).
 */
class ReplayPacingTest {

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

        /** Every push that arrived, applied or not. */
        var arrivedPushes = 0
        var seatings = 0

        /** Every body this phone sent up. */
        val sent = mutableListOf<String>()
        val round = RoundOverTransport(
            scope, FlowModel(PanelState(screen = ScreenId.Home)),
            buzzer = HouseBuzzer(SilentHaptics),
            send = { frame -> up?.invoke(frame) },
            radio = NoRadio,
            wake = wake,
            onInbound = { body -> if (RoundWire.decodeOrNull(body) is RoundBody.Push) arrivedPushes++ },
            onOutbound = { sent += it },
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

        /** The asks this phone made to be caught up. */
        val gaps: List<RoundBody.Gap> get() = sent.mapNotNull { RoundWire.decodeOrNull(it) as? RoundBody.Gap }
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

    private class Room(
        val phones: MutableList<Phone>,
        val house: RoundAuthority,
        val said: MutableList<String>,
        val scope: CoroutineScope,
    ) {
        val host get() = phones.first()
        /** Each phone's seat, read off the desk once every phone is seated; a dropped phone keeps its own. */
        private val seats = HashMap<String, Seat>()
        fun seatOf(p: Phone): Seat = seats.getOrPut(p.name) {
            host.link.house.armingInputs().names.single { it.name == p.name }.seat
        }

        /** How many pushes the house has filed for [seat] this round: everything it is owed. */
        var owed = HashMap<Int, Long>()

        /** One effect, on the wire, counted against every seat the schema sends it to. */
        suspend fun emit(effect: Effect) {
            for (d in EmitSchema.deliveries(effect, house.state)) owed[d.seat.index] = (owed[d.seat.index] ?: 0L) + 1
            host.link.house.emit(effect, house.state)
        }
    }

    private fun evening(body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val host = Phone(scope, "HOST").also { phones += it }
                val said = mutableListOf<String>()
                lateinit var room: Room
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state ->
                        for (d in EmitSchema.deliveries(effect, state)) room.owed[d.seat.index] = (room.owed[d.seat.index] ?: 0L) + 1
                        host.link.house.emit(effect, state)
                    },
                    note = { said += it },
                )
                room = Room(phones, house, said, scope)
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

    /** [count] meter pushes to every seat, in batches every connected phone keeps up with. */
    private suspend fun Room.backlog(count: Int, connected: List<Phone>) {
        var left = count
        var n = 0
        while (left > 0) {
            repeat(minOf(BATCH, left)) { emit(Effect.SystemIntegrityBatched(n++ % 100)) }
            left -= BATCH
            until("every connected phone kept up with the backlog") {
                connected.all { p -> p.half.round.held >= (owed[seatOf(p).index] ?: 0L) }
            }
        }
    }

    // ---- A relaunched phone, a thousand pushes behind -----------------------------------------------

    /**
     * **GUARD (V2, D-268): a phone relaunched into a seat with over a thousand pushes is caught up,
     * once, without its link being dropped.** A guest's process ends; while it is gone the house puts
     * more than a thousand pushes on its stream (twice [OUTBOUND_FRAMES]); the app is opened again
     * over the same files, resumes its seat by its token holding nothing, and asks. It holds the
     * whole stream, in order, from one ask, on the connection it asked on.
     */
    @Test
    fun aRelaunchedPhoneAThousandPushesBehindIsCaughtUpOnce() = evening { room ->
        val guest = room.phones.last()
        val seat = room.seatOf(guest)
        guest.job.cancel()
        // The desk, not the standing: mid-round the standing counts the round (D-273).
        until("the house lost the guest's connection") { room.host.link.house.armingInputs().seats.size == room.phones.size - 1 }

        room.backlog(BACKLOG, room.phones.filter { it !== guest })
        val owed = room.owed.getValue(seat.index)
        assertTrue(owed > 2L * OUTBOUND_FRAMES, "the backlog ($owed) is not past the cap, so nothing is measured")

        // A phone on Wi-Fi takes a frame in about a millisecond, never as fast as the house can make
        // them: on loopback with no delay the writer keeps up with any replay and nothing is measured.
        room.host.link.house.socketWrite = { to, write ->
            if (to == seat) delay(WIFI_MILLIS)
            write()
        }
        val fresh = Phone(room.scope, guest.name, guest.kept)
        room.phones[room.phones.indexOf(guest)] = fresh
        fresh.model.attachTo(NearbyHome(home.name, "127.0.0.1", room.host.air.ports.last()))
        until("the relaunched phone holds its whole stream", detail = {
            "held ${fresh.half.round.held} of $owed · ${fresh.half.seatings} seating(s) · ${fresh.half.gaps.size} ask(s) · " +
                "${fresh.half.arrivedPushes} push(es) arrived"
        }) { fresh.half.round.held == owed }
        delay(SETTLE_MILLIS)

        println(
            "V2 RELAUNCH — HELD ${fresh.half.round.held}/$owed · SEATINGS ${fresh.half.seatings} · " +
                "ASKS ${fresh.half.gaps.map { it.held }} · PUSHES ARRIVED ${fresh.half.arrivedPushes}",
        )
        assertEquals(1, fresh.half.seatings, "the relaunched phone's link was dropped while it was being caught up")
        assertEquals(listOf(0L), fresh.half.gaps.map { it.held }, "the relaunched phone asked more than once, from more than one point")
        assertEquals(owed, fresh.half.arrivedPushes.toLong(), "the replay was not one copy of the stream")
    }

    // ---- A lagging phone, under a burst ------------------------------------------------------------

    /**
     * **GUARD (V2, D-268): a lagging phone that is being caught up while a vote's burst goes out is
     * sent each push once, asks once, and keeps its link.** A guest's socket is slow (every write to
     * it waits [LAG_MILLIS]). Its link drops and while it is away the house files [BEHIND] pushes for
     * it. It comes back and asks; while its replay is going out, a burst of [BURST] `VoteProgressed`
     * pushes goes to every seat. Every phone ends holding everything it is owed. The lagging phone
     * asked once, was never dropped, and was sent every push it was owed once, plus at most what was
     * already on its old link when it dropped.
     */
    @Test
    fun aLaggingPhoneUnderAVoteBurstIsSentEachPushOnce() = evening { room ->
        val lagging = room.phones.last()
        val seat = room.seatOf(lagging)
        val others = room.phones.filter { it !== lagging }
        room.host.link.house.socketWrite = { to, write ->
            if (to == seat) delay(LAG_MILLIS)
            write()
        }
        val heldBefore = lagging.half.round.held
        val arrivedBefore = lagging.half.arrivedPushes
        val asksBefore = lagging.half.gaps.size

        // The link drops; the phone is away for the first reconnect's wait, and the house files on.
        room.host.link.house.dropConnection(seat)
        room.backlog(BEHIND, others)
        until("the lagging phone came back", detail = { "seatings ${lagging.half.seatings}" }) { lagging.half.seatings >= 2 }

        // The burst, while its replay is going out: every lock in a long vote, one push to every seat.
        until("the replay is under way") { lagging.half.round.held > heldBefore }
        repeat(BURST) { i -> room.emit(Effect.VoteProgressed(locked = i % 18 + 1, expected = 18)) }

        val owed = room.owed.getValue(seat.index)
        until("every phone holds everything it is owed", detail = {
            room.phones.joinToString { "${it.name} ${it.half.round.held}/${room.owed[room.seatOf(it).index]}" } +
                " · lagging asks ${lagging.half.gaps.drop(asksBefore).map { it.held }} · seatings ${lagging.half.seatings}"
        }) { room.phones.all { p -> p.half.round.held == room.owed[room.seatOf(p).index] } }
        delay(SETTLE_MILLIS)

        val asks = lagging.half.gaps.drop(asksBefore).map { it.held }
        val sent = lagging.half.arrivedPushes - arrivedBefore
        val needed = owed - heldBefore
        println("V2 LAG — NEEDED $needed · ARRIVED $sent · ASKS $asks · SEATINGS ${lagging.half.seatings}")
        assertEquals(2, lagging.half.seatings, "the lagging phone's link was dropped again while it was caught up")
        assertTrue(asks.size == 1, "the lagging phone asked more than once on one connection: $asks")
        assertTrue(sent <= needed + SLACK, "the lagging phone was sent $sent pushes for $needed it needed: a copy per ask")
        assertTrue(sent >= needed, "the lagging phone holds what it was never sent ($sent < $needed), so nothing was measured")
    }

    private companion object {
        const val SEED = 20261150L
        const val BACKLOG = 1_100
        const val BATCH = 100
        const val BEHIND = 400
        const val BURST = 300
        const val LAG_MILLIS = 2L
        const val WIFI_MILLIS = 1L
        const val SETTLE_MILLIS = 300L

        /** What can be on the old link's socket when it drops, and is sent again: one batch. */
        const val SLACK = BATCH.toLong()
    }
}
