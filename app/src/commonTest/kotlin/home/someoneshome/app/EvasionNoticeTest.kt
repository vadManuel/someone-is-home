package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.Tick
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A phone that leaves the foreground mid-round is named at the next meeting, the host's own seat
 * exactly as a guest's** (D-187, D-229).
 *
 * Real sockets on loopback, the real link, desk, ledger and authority, and each phone's real round
 * half on its one socket, as `RoundClient` puts it there in the app. Faked: Bonjour, the OS's
 * foreground notifications (the test calls `backgrounded` and `foregrounded`, which is all
 * `deviceForeground` does), the radio ([NoRadio]) and the tick driver (the meeting's gate is closed
 * by check-ins put to the authority, as the desk would put them). The beat is called by hand.
 *
 * What a test cannot have is iOS suspending the guest: here a guest's socket stays up while it is
 * "away". The host's does not, because its house really stops and reopens (D-225), so the host's
 * report is the one that has to survive a reconnect, and does.
 */
class EvasionNoticeTest {

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
    private class Half(scope: CoroutineScope, wake: ScreenWake, log: MutableList<String>) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null

        /** Every push this phone applied. */
        val pushed = mutableListOf<RoundMessage>()

        /** Every body this phone sent up, raw. */
        val sent = mutableListOf<String>()
        val round = RoundOverTransport(
            scope, FlowModel(PanelState(screen = ScreenId.Home)),
            buzzer = HouseBuzzer(SilentHaptics),
            send = { frame -> up?.invoke(frame) },
            radio = NoRadio,
            wake = wake,
            onEvent = { log += it },
            onInbound = { body -> (RoundWire.decodeOrNull(body) as? RoundBody.Push)?.let { pushed += it.message } },
            onOutbound = { sent += it },
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
        override fun leftTheForeground() = round.leftTheForeground()
        override fun backInTheForeground() = round.backInTheForeground()

        /** The person's-fault bodies this phone sent up. */
        val reports: List<String> get() = sent.filter { RoundWire.decodeOrNull(it) is RoundBody.RadioFaulted }
    }

    private class Phone(scope: CoroutineScope, val name: String, house: () -> RoundAuthority?) {
        val air = Air()
        val kept = Kept()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        val half = Half(scope, wake, log)
        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = kept,
            // `CheatRoot`'s fourth door, exactly: a phone's report, put to the rules and awaited (D-241).
            onRadioFaulted = HouseDoors(house)::faulted,
            round = half, wake = wake, advertising = air,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        fun handOver(line: String) {
            model.typeLine(line)
            check(model.handOverLine()) { "the line was refused" }
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    /** Three phones in a lobby, the host first, every line handed over; and the host's authority. */
    private class Room(val all: List<Phone>, val house: RoundAuthority, val said: MutableList<String>) {
        val host get() = all.first()
        fun seatOf(phone: Phone): Seat = house.state.names.single { it.name == phone.name }.seat
    }

    private fun evening(
        guestNames: List<String> = listOf("ROSE", "DANI"),
        body: suspend CoroutineScope.(Room) -> Unit,
    ) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST") { authority }.also { phones += it }
                val said = mutableListOf<String>()
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.air.ports.single()
                val guests = guestNames.map { Phone(scope, it) { authority }.also { p -> phones += p } }
                guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
                until("every guest was seated") { host.model.standing.joined == 1 + guests.size }
                val all = listOf(host) + guests
                all.forEachIndexed { i, p -> p.handOver("line $i") }
                until("every line reached the house") { host.model.standing.linesIn == all.size }
                scope.body(Room(all, authority, said))
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** LIGHTS OUT, through production's arming path, until every phone holds its seed. */
    private suspend fun Room.lightsOut() {
        assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = { said += it }), "the round never armed: $said")
        until("every phone holds the round's seed") { all.all { it.half.round.uploading } }
    }

    /** A few beats on every phone, as the cadence would give them. */
    private suspend fun Room.beats(n: Int = 3) = repeat(n) {
        all.forEach { it.half.round.beat() }
        delay(SETTLE_MILLIS)
    }

    /** A meeting called at the card, and every seat checked in: the gate closes and the notice goes. */
    private suspend fun Room.meeting() {
        val caller = house.state.livingSeats.first()
        house.mint(Event.MeetingCalled(house.now(), caller, MeetingTrigger.MeetingCard))
        for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
        until("every phone was handed the notice") {
            all.all { p -> p.half.pushed.any { it is RoundMessage.NoticeIssued } }
        }
    }

    /**
     * Every phone's notices at this meeting, re-encoded as the frame the house wrote: one, the same
     * on every phone, naming [expected].
     */
    private fun Room.assertNoticeNames(expected: List<Seat>) {
        val frame = RoundWire.encode(RoundBody.Push(1, RoundMessage.NoticeIssued(expected)))
        for (p in all) {
            val notices = p.half.pushed.filterIsInstance<RoundMessage.NoticeIssued>()
                .map { RoundWire.encode(RoundBody.Push(1, it)) }
            assertEquals(listOf(frame), notices, "${p.name} was not told exactly once, naming $expected")
        }
        assertTrue(house.state.unreachable.isEmpty(), "the notice's list was not emptied at the gate")
    }

    /** The one body a person's outage sends (D-223), the same bytes from every phone. */
    private val personsReport = RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Person))

    // ---- A guest ------------------------------------------------------------------------------------

    /**
     * **GUARD: a guest that leaves the foreground mid-round is named once, at the next meeting, to
     * every phone in one frame of one length**, and its report is the one fixed body.
     */
    @Test
    fun aGuestBackgroundedMidRoundIsNamedOnceAtTheNextMeeting() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val guest = room.all.last()
        val seat = room.seatOf(guest)

        guest.link.backgrounded()
        room.beats(1)
        assertTrue(room.house.state.unreachable.isEmpty(), "the house was told while the guest was still away")
        guest.link.foregrounded()
        room.beats()
        until("the house heard the guest's report") { room.house.state.unreachable.isNotEmpty() }
        assertEquals(listOf(seat), room.house.state.unreachable, "the house wrote down somebody other than the guest")
        assertEquals(listOf(personsReport), guest.half.reports, "the guest's report was not the one fixed body, once")
        assertTrue(room.all.filter { it !== guest }.all { it.half.reports.isEmpty() }, "a phone that never left was reported")
        assertTrue(
            room.said.count { it.startsWith("RADIO FAULT — SEAT ${seat.index} · PERSON · NAMED AT THE NEXT MEETING") } == 1,
            "the house's log did not say so once: ${room.said}",
        )

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    // ---- A seat that is out (R7, D-242, provisional) ---------------------------------------------------

    /**
     * **GUARD (R7): a Revoked phone that locks sends the same body a living phone sends, and nobody
     * is told about it.** Four seats, so one Revoke does not end the round by parity. A Revoked guest
     * and a living guest both leave the foreground and come back: both phones send the one fixed
     * body (rule 1, nothing on the wire says which is out), the house writes down only the living
     * one, and the next meeting names only the living one.
     *
     * The Revoke is minted at the house (`RevokeArmed`, both halves of a `ContactMade`): what is
     * under test is what happens after a seat is out, and `AbilityWiringTest` is the Revoke's own
     * end-to-end path from the tile.
     */
    @Test
    fun aPhoneThatIsOutLocksAndIsNamedByNobody() = evening(listOf("ROSE", "DANI", "MARCUS")) { room ->
        room.lightsOut()
        room.beats(1)
        val house = room.house
        val insider = house.state.insiderSeats.single()
        // Guests only: the host's own lock takes its house off the air, which is the test below.
        val residents = house.state.livingPlainResidents.filter { it != room.seatOf(room.host) }
        assertTrue(residents.size >= 2, "the fixture's draw left fewer than two guests who are plain Residents")
        val (outSeat, livingSeat) = residents[0] to residents[1]
        val out = room.all.single { room.seatOf(it) == outSeat }
        val living = room.all.single { room.seatOf(it) == livingSeat }

        house.advanceTo(house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt ?: Tick(0))
        house.mint(Event.RevokeArmed(house.now(), insider))
        val at = house.now()
        house.mint(Event.ContactMade(at, insider, outSeat))
        house.mint(Event.ContactMade(at, outSeat, insider))
        assertTrue(house.state.isRevoked(outSeat), "the fixture never put anybody out")
        assertTrue(!house.state.ended, "the Revoke ended the round, so nothing after it is measured")
        until("the Revoked phone was told it is out") { out.half.pushed.any { it is RoundMessage.RevokeLanded } }

        for (phone in listOf(out, living)) {
            phone.link.backgrounded()
            room.beats(1)
            phone.link.foregrounded()
        }
        room.beats()
        until("the house heard both reports") {
            listOf(outSeat, livingSeat).all { seat -> room.said.any { it.startsWith("RADIO FAULT — SEAT ${seat.index} · PERSON") } }
        }
        assertEquals(listOf(personsReport), out.half.reports, "the Revoked phone's report was not the one fixed body, once")
        assertEquals(out.half.reports, living.half.reports, "a Revoked phone's report differs on the wire from a living one's")
        assertEquals(listOf(livingSeat), house.state.unreachable, "the house wrote down the seat that is out, or lost the living one")

        room.meeting()
        room.assertNoticeNames(listOf(livingSeat))
    }

    // ---- The host ------------------------------------------------------------------------------------

    /**
     * **GUARD: the host is treated like anyone else** (the owner's 2026-11-16 ruling). The host's own
     * lock takes the house off the air (D-225); on the return its round half reports over loopback
     * once the house listens again, the house records its OWN seat, and the next meeting names it.
     */
    @Test
    fun theHostBackgroundedMidRoundNamesItsOwnSeat() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val host = room.host
        val seat = room.seatOf(host)

        host.link.backgrounded()
        until("every seat dropped with the house") { host.link.house.armingInputs().seats.isEmpty() }
        host.link.foregrounded()
        // Beats from the moment it is back: the host's own socket is still coming back, so the
        // report waits in its outbox until the seat is taken again.
        room.beats(1)
        until("every seat came back") { host.link.house.armingInputs().seats.size == 3 }
        room.beats()
        until("the house heard its own seat's report") { room.house.state.unreachable.isNotEmpty() }
        assertEquals(listOf(seat), room.house.state.unreachable, "the house named somebody other than its own seat")
        assertEquals(listOf(personsReport), host.half.reports, "the host's report was not the one fixed body, once")
        assertTrue(room.all.drop(1).all { it.half.reports.isEmpty() }, "a guest whose socket the house dropped was reported")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    // ---- Before arming ---------------------------------------------------------------------------------

    /** **GUARD: before arming, nothing is named** — a host and a guest that leave in the lobby. */
    @Test
    fun leavingInTheLobbyIsNamedByNobody() = evening { room ->
        val guest = room.all.last()
        guest.link.backgrounded()
        guest.link.foregrounded()
        room.host.link.backgrounded()
        until("every seat dropped with the house") { room.host.link.house.armingInputs().seats.isEmpty() }
        room.host.link.foregrounded()
        until("every seat came back, every line with it") {
            room.host.link.house.armingInputs().seats.size == 3 && room.host.model.standing.linesIn == 3
        }
        room.lightsOut()
        room.beats()
        assertTrue(room.all.all { it.half.reports.isEmpty() }, "a phone that left in the lobby was reported")
        room.meeting()
        room.assertNoticeNames(emptyList())
    }

    private companion object {
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 150L
    }
}
