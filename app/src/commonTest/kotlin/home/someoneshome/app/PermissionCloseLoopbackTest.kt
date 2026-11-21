package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.model.ContactToken
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RadioPower
import home.someoneshome.platform.RadioReading
import home.someoneshome.platform.causeOf
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.Flow
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.HOUSE_IS_CLOSED
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.PermsModel
import home.someoneshome.ui.SWITCHED_OFF_AGAIN
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A permission switched off is named with the warning, and the second one closes the house on
 * every phone, the host's included** (the owner's 2026-11-20 rulings, D-310).
 *
 * Real sockets on loopback, the real link, desk, ledger and authority, and on each phone the real
 * round half and the real panel and lobby models, wired as `CheatRoot` wires them, so the close's
 * push walks each phone exactly as it would. Faked: Bonjour, the OS's foreground notifications (the
 * test calls `backgrounded` and `foregrounded`), the radio ([NoRadio]), the tick driver (the meeting
 * is moved by events put to the authority) and the OS's grants: each phone's `switchedOff` is a
 * switch the test throws, standing for `Permissions.refusedGrants` reading a grant switched off
 * when the phone is back from Settings. The beat is called by hand.
 */
class PermissionCloseLoopbackTest {

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

    /**
     * **A contact radio the test can turn off** (D-311): quiet, until [turnOff] hands the uploader one
     * onset with the cause the production line (`causeOf`) gives both managers going Off.
     */
    private class Radio : ContactRadio {
        private var pending: home.someoneshome.model.RadioCause? = null
        fun turnOff() {
            pending = causeOf(RadioPower.Off, RadioPower.Off)
        }
        override fun advertise(token: ContactToken) = Unit
        override fun scan(on: Boolean) = Unit
        override fun drain(): List<RadioReading> = emptyList()
        override fun silence() = Unit
        override fun faultBegan(): home.someoneshome.model.RadioCause? = pending.also { pending = null }
    }

    /** One phone's round half on its one socket, built once its panel exists, as `RoundClient` builds it. */
    private class Half(
        private val scope: CoroutineScope,
        private val wake: ScreenWake,
        private val kept: SeatStore,
        private val log: MutableList<String>,
        /** The grants the OS reads refused now, one bit a row, as `Permissions.refusedGrants` gives them. */
        private val refused: () -> Int,
    ) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null

        /** Every push this phone applied, raw, as the house wrote it. */
        val pushedRaw = mutableListOf<String>()

        /** Every body this phone sent up, raw. */
        val sent = mutableListOf<String>()

        /** **This phone's contact radio** (D-311): one onset when the test turns its Bluetooth off. */
        val contact = Radio()

        /** The OS's answer to *is a grant this phone was seated with switched off*. The test throws it. */
        var grantOff = false

        lateinit var round: RoundOverTransport

        fun attach(flow: FlowModel) {
            round = RoundOverTransport(
                scope, flow,
                buzzer = HouseBuzzer(SilentHaptics),
                send = { frame -> up?.invoke(frame) },
                radio = contact,
                wake = wake,
                onEvent = { log += it },
                onInbound = { body -> if (RoundWire.decodeOrNull(body) is RoundBody.Push) pushedRaw += body },
                onOutbound = { sent += it },
                refusedGrants = { (if (grantOff) 1 else 0) or refused() },
                kept = kept,
            )
            scope.launch { round.run() }
        }

        override fun linked(send: suspend (TransportFrame) -> Unit) {
            up = send
        }
        override fun arrived(frame: TransportFrame) = round.frameArrived(frame)
        override fun seated() = round.reseated()
        override fun roundEnded() = round.roundEnded()
        override fun left() = round.left()
        override fun leftTheForeground() = round.leftTheForeground()
        override fun backInTheForeground() = round.backInTheForeground()

        val pushed: List<RoundMessage> get() = pushedRaw.mapNotNull { (RoundWire.decodeOrNull(it) as? RoundBody.Push)?.message }

        val reports: List<RoundBody.RadioFaulted> get() = sent.mapNotNull { RoundWire.decodeOrNull(it) as? RoundBody.RadioFaulted }
    }

    private class Phone(
        parent: CoroutineScope,
        val name: String,
        house: () -> RoundAuthority?,
        /** Beside the seat token, as `DeviceSeat` is on a phone: the link's token and the round's record, one store. */
        val store: SeatStore = MemorySeat(),
        screen: ScreenId = ScreenId.Lobby,
    ) {
        val job = Job(parent.coroutineContext[Job])
        private val scope = CoroutineScope(parent.coroutineContext + job)
        val air = Air()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        /** **What the OS reads on this phone** (D-311): every row granted until the test switches one off. */
        val os = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()
        val half = Half(scope, wake, store, log) {
            os.entries.fold(0) { bits, (row, grant) -> if (grant == Grant.Refused) bits or (1 shl row.ordinal) else bits }
        }

        /** Every lobby body this phone was sent, decoded. */
        val lobbyBodies = mutableListOf<LobbyBody>()
        private var answer: () -> Unit = {}
        val link = LobbyOverTransport(
            scope, onEvent = { log += it },
            onInbound = { body -> LobbyWire.decodeOrNull(body)?.let { lobbyBodies += it } },
            seat = store,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = half, wake = wake, advertising = air,
            // `CheatRoot`'s wiring: the house's `RoundEnded` is answered by the panel's NEW ROUND.
            onRoundEnded = { answer() },
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
        val flow = FlowModel(PanelState(screen = screen, houseAttached = true), lobby = model, perms = PermsModel(read = { os.getValue(it) }))

        init {
            half.attach(flow)
            answer = { flow.newRound() }
        }

        fun handOver(line: String) {
            model.typeLine(line)
            check(model.handOverLine()) { "the line was refused" }
        }

        /** Out to Settings and back, with a grant switched off there or not. */
        fun leaveAndReturn(switchingAGrantOff: Boolean) {
            link.backgrounded()
            half.grantOff = half.grantOff || switchingAGrantOff
            link.foregrounded()
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

    private class Room(
        val all: MutableList<Phone>,
        val house: RoundAuthority,
        val said: MutableList<String>,
        private val scope: CoroutineScope,
        private val made: MutableList<Phone>,
    ) {
        val host get() = all.first()
        val guests get() = all.drop(1)
        fun seatOf(phone: Phone): Seat = house.state.names.single { it.name == phone.name }.seat

        /**
         * **iOS ends [old]'s process, and the person opens the app again** (D-245, D-311). The old
         * process goes with no goodbye; only its files are left. The fresh one cold-starts on the
         * self-test, over the same files, with what the OS reads now in [os].
         */
        fun relaunch(old: Phone, os: Map<DevicePermission, Grant>): Phone {
            old.job.cancel()
            val fresh = Phone(scope, old.name, { house }, old.store, screen = ScreenId.Boot).also { made += it }
            fresh.os.putAll(os)
            all[all.indexOf(old)] = fresh
            return fresh
        }

        /** The port the host's house listens on. */
        val port: Int get() = host.air.ports.last()
    }

    private fun evening(body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", { authority }).also { phones += it }
                val said = mutableListOf<String>()
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.air.ports.single()
                val guests = listOf("MIA", "JO", "DANI").map { Phone(scope, it, { authority }).also { p -> phones += p } }
                guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
                until("every guest was seated") { host.model.standing.joined == 1 + guests.size }
                val all = listOf(host) + guests
                all.forEachIndexed { i, p -> p.handOver("line $i") }
                until("every line reached the house") { host.model.standing.linesIn == all.size }
                scope.body(Room(all.toMutableList(), authority, said, scope, phones))
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    private suspend fun Room.lightsOut() {
        assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = { said += it }), "the round never armed: $said")
        until("every phone holds the round's seed") { all.all { it.half.round.uploading } }
        all.forEach { it.flow.push(ScreenId.Home) }
    }

    private suspend fun Room.beats(n: Int = 3) = repeat(n) {
        all.forEach { it.half.round.beat() }
        delay(SETTLE_MILLIS)
    }

    private suspend fun Room.beatUntil(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) {
                while (!condition()) {
                    all.forEach { it.half.round.beat() }
                    delay(SETTLE_MILLIS)
                }
            }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened, beating: $what · ${said.takeLast(8)}", late)
        }
    }

    /** A meeting called at the card and every seat checked in: the notice goes to every phone. */
    private suspend fun Room.meeting(): Int {
        val before = all.map { p -> p.half.pushed.count { it is RoundMessage.NoticeIssued } }
        house.mint(Event.MeetingCalled(house.now(), house.state.livingSeats.first(), MeetingTrigger.MeetingCard))
        for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
        until("every phone was handed the notice") {
            all.indices.all { i -> all[i].half.pushed.count { it is RoundMessage.NoticeIssued } > before[i] }
        }
        return before.first()
    }

    /** The talk, the ballot with nobody voting, the tally and lights out: the next shift. */
    private suspend fun Room.endMeeting() {
        house.mint(Event.DiscussionClosed(house.now()))
        house.mint(Event.VoteWindowClosed(house.now()))
        house.mint(Event.TallyHalfwayReached(house.now()))
        house.mint(Event.MeetingClosed(house.now()))
        until("the meeting closed") { house.state.meeting == null }
    }

    /**
     * The last notice every phone holds: one frame, the same bytes on every phone, naming [left] as
     * leavers and [switched] in the permission sentence (D-311).
     */
    private fun Room.assertLastNotice(left: List<Seat>, switched: List<Seat>, words: String) {
        val frames = all.map { p ->
            p.half.pushedRaw.last { (RoundWire.decodeOrNull(it) as? RoundBody.Push)?.message is RoundMessage.NoticeIssued }
        }
        assertEquals(1, frames.map { it.substringAfter("\"message\"") }.toSet().size, "the notice was not the same on every phone: $frames")
        for (p in all) {
            val notice = p.half.pushed.filterIsInstance<RoundMessage.NoticeIssued>().last()
            assertEquals(RoundMessage.NoticeIssued(left, switchedOff = switched), notice, "${p.name} was told something else")
            assertEquals(words, PanelVals(p.flow.state).notice, "${p.name}'s notices screen said something else")
        }
    }

    private val closedLines = "$HOUSE_IS_CLOSED\n$SWITCHED_OFF_AGAIN"

    /** The owner's permission sentence (2026-11-21, D-311). */
    private fun warning(name: String) = "Resident $name switched off a permission this game needs. " +
        "If anyone switches one off again, the house closes and everyone starts over."

    private fun leaveOnly(name: String) = "Resident $name was unreachable this shift. Occupancy data for this interval is incomplete."

    /**
     * **GUARD: the whole evening of D-310 over real sockets.** A guest goes to Settings and switches a
     * grant off: its report goes up as a permission, and the next meeting names it with the warning
     * on every phone. After the warning a guest only leaves the app: named without the warning, and
     * the house stays open. Then a different guest switches a grant off: the house closes, every
     * phone is pushed the one close in the same bytes, every guest is walked to JOIN A HOME with the
     * owner's two lines and hangs up, and the host goes back to its own lobby, still on the air, with
     * the same two lines; no guest is sent the host's turnover.
     */
    @Test
    fun aSecondPermissionClosesTheHouseOnEveryPhoneAndALeaveNeverDoes() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val mia = room.guests[0]
        val jo = room.guests[1]
        // Read now: the close drops the round's names with its lines (D-115).
        val joSeat = room.seatOf(jo)

        // 1 · Mia switches a grant off in Settings.
        mia.leaveAndReturn(switchingAGrantOff = true)
        room.beatUntil("the house listed Mia's permission") { room.house.state.switchedOff.isNotEmpty() }
        assertEquals(setOf(RadioCause.Permission), mia.half.reports.map { it.cause }.toSet(), "Mia's trip to Settings went up as something else")
        assertTrue(room.said.any { it.startsWith("RADIO FAULT — SEAT ${room.seatOf(mia).index} · PERMISSION") }, "the house's log did not say permission: ${room.said}")
        room.meeting()
        room.assertLastNotice(emptyList(), listOf(room.seatOf(mia)), words = warning("MIA"))
        assertTrue(room.house.state.warned, "the notice carrying the warning did not give it")
        room.endMeeting()

        // 2 · After the warning, Jo only leaves the app: named, no warning, no close.
        jo.leaveAndReturn(switchingAGrantOff = false)
        room.beatUntil("the house listed Jo's leave") { room.house.state.unreachable.isNotEmpty() }
        assertEquals(setOf(RadioCause.Person), jo.half.reports.map { it.cause }.toSet(), "Jo's leave went up as something else")
        room.beats()
        assertFalse(room.house.state.ended, "a phone that only left the app closed the house")
        assertTrue(room.all.none { p -> p.half.pushed.any { it is RoundMessage.HouseClosedOnPermission } }, "a leave sent a phone the close")
        room.meeting()
        room.assertLastNotice(listOf(room.seatOf(jo)), emptyList(), words = leaveOnly("JO"))
        room.endMeeting()

        // 3 · Jo now switches a grant off: anyone's second time closes the house.
        jo.leaveAndReturn(switchingAGrantOff = true)
        room.beatUntil("the house closed") { room.house.state.closed }
        assertTrue(room.said.any { it.startsWith("THE HOUSE IS CLOSED — SEAT ${joSeat.index}") }, "the house's log did not say so: ${room.said}")
        until("every phone was walked out of the round") {
            room.guests.all { it.flow.state.screen == ScreenId.Join } && room.host.flow.state.screen == ScreenId.Lobby
        }
        val closes = room.all.map { p -> p.half.pushedRaw.filter { (RoundWire.decodeOrNull(it) as? RoundBody.Push)?.message is RoundMessage.HouseClosedOnPermission } }
        assertTrue(closes.all { it.size == 1 }, "a phone was not pushed the close exactly once: $closes")
        assertEquals(1, closes.map { it.single().substringAfter("\"message\"") }.toSet().size, "the close was not the same on every phone: $closes")
        for (guest in room.guests) {
            assertEquals(closedLines, guest.model.refusal, "${guest.name} was not shown the two lines")
            assertEquals(null, guest.model.attached, "${guest.name} is still attached to the house")
            assertFalse(guest.half.round.uploading, "${guest.name} is still in the round")
        }
        assertEquals(closedLines, room.host.model.refusal, "the host was not shown the two lines")
        assertTrue(room.host.model.hosting, "the close took the host's house off the air")
        until("every guest left the host's lobby") { room.host.model.standing.joined == 1 }
        room.beats()
        assertTrue(
            room.guests.none { g -> g.lobbyBodies.any { it is LobbyBody.RoundEnded } },
            "a guest was sent the host's turnover: ${room.guests.map { it.lobbyBodies }}",
        )
        assertTrue(room.guests.all { it.flow.state.screen == ScreenId.Join }, "a guest was walked off JOIN A HOME")
        assertEquals(ScreenId.Lobby, room.host.flow.state.screen)
    }

    /**
     * **GUARD (D-311): the Bluetooth radio turned off mid-round counts exactly as a permission
     * switched off**, over real sockets. Dani turns Bluetooth off in Control Centre (no leave: the app
     * is not suspended) while Jo only leaves the app: the next meeting names Jo first in the
     * unchanged sentence and Dani in the owner's permission sentence, the same bytes and the same
     * words on every phone. After that warning, Mia turns Bluetooth off: the house closes on every
     * phone.
     */
    @Test
    fun bluetoothTurnedOffIsNamedInThePermissionSentenceAndThenClosesTheHouse() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val mia = room.guests[0]
        val jo = room.guests[1]
        val dani = room.guests[2]
        val miaSeat = room.seatOf(mia)

        dani.half.contact.turnOff()
        jo.leaveAndReturn(switchingAGrantOff = false)
        room.beatUntil("the house listed Dani's switch-off and Jo's leave") {
            room.house.state.switchedOff.isNotEmpty() && room.house.state.unreachable.size == 2
        }
        assertEquals(listOf(RadioCause.Permission), dani.half.reports.map { it.cause }, "Bluetooth turned off went up as something else")
        assertEquals(listOf(RadioCause.Person), jo.half.reports.map { it.cause }, "Jo's leave went up as something else")
        room.meeting()
        room.assertLastNotice(
            listOf(room.seatOf(jo)), listOf(room.seatOf(dani)),
            words = leaveOnly("JO") + " " + warning("DANI"),
        )
        assertTrue(room.house.state.warned, "Bluetooth turned off did not give the warning")
        room.endMeeting()

        mia.half.contact.turnOff()
        room.beatUntil("the house closed") { room.house.state.closed }
        assertTrue(room.said.any { it.startsWith("THE HOUSE IS CLOSED — SEAT ${miaSeat.index}") }, "the house's log did not say so: ${room.said}")
        until("every phone was walked out of the round") {
            room.guests.all { it.flow.state.screen == ScreenId.Join } && room.host.flow.state.screen == ScreenId.Lobby
        }
        assertTrue(room.guests.all { it.model.refusal == closedLines }, "a guest was not shown the two lines")
    }

    /**
     * **GUARD (D-311): in the lobby, a guest that switches the Bluetooth radio off leaves and lands on
     * PERMISSIONS, and the round armed at LIGHTS OUT does not hold it**, over real sockets. Dani's
     * radio goes off before lights out: its phone's check lets it go (it hangs up, the row reads
     * REFUSED), the host's standing drops to three, the house arms the three still seated, and Dani's
     * phone is sent no seed.
     */
    @Test
    fun aGuestSwitchingOffInTheLobbyLeavesItAndIsNotInTheRound() = evening { room ->
        val dani = room.guests[2]
        dani.os[DevicePermission.Bluetooth] = Grant.Refused
        dani.flow.permissionsChecked()
        assertEquals(ScreenId.Perms, dani.flow.state.screen, "Dani was not walked to PERMISSIONS")
        assertEquals(Grant.Refused, dani.flow.perms.grant(DevicePermission.Bluetooth), "the row does not show as refused")
        assertEquals(null, dani.model.attached, "Dani still holds the lobby")
        until("the house dropped Dani from the lobby") { room.host.model.standing.joined == 3 }
        // The others are untouched.
        room.all.filter { it !== dani }.forEach { it.flow.permissionsChecked() }
        assertTrue(room.all.filter { it !== dani }.all { it.flow.state.screen == ScreenId.Lobby }, "a phone with nothing off was moved")

        assertTrue(armAtLightsOut(room.house, hosting = true, seed = SEED, note = { room.said += it }), "the round never armed: ${room.said}")
        until("every seated phone holds the round's seed") { room.all.filter { it !== dani }.all { it.half.round.uploading } }
        assertEquals(3, room.house.state.seats.size, "the round holds a seat it should not")
        assertTrue(room.house.state.names.none { it.name == dani.name }, "Dani, let go from the lobby, is in the round")
        room.beats(2)
        assertFalse(dani.half.round.uploading, "Dani's phone was handed the round's seed")
        assertEquals(ScreenId.Perms, dani.flow.state.screen)
    }

    /**
     * **GUARD (the overnight review's finding 2): a phone iOS ended for Camera switched off mid-round
     * gets back to its seat with Camera still off, and its trip to Settings is named as a switch-off**,
     * over real sockets, by a relaunch and not by handing the seed over. Mia leaves for Settings and
     * switches Camera off; iOS ends the app; the fresh process cold-starts on the self-test, falls
     * through to JOIN A HOME, not PERMISSIONS, is let past the gate, is seated back in its round by
     * its token, and its owed leave goes up as a permission: the next meeting names Mia in the
     * owner's permission sentence.
     */
    @Test
    fun aPhoneRelaunchedMidRoundWithCameraOffGetsBackToItsSeat() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val mia = room.guests[0]
        val seat = room.seatOf(mia)
        mia.link.backgrounded()
        assertTrue(mia.store.owedReports() != null, "the leave was not kept beside the seat token before the phone was suspended")

        val relaunched = room.relaunch(mia, mapOf(DevicePermission.Camera to Grant.Refused))
        val fell = relaunched.flow.fallThrough(Flow.autoAdvance.getValue(ScreenId.Boot))
        assertEquals(ScreenId.Join, fell, "a phone holding a round seat was held at PERMISSIONS")
        relaunched.flow.push(fell)
        relaunched.flow.attachToHome(NearbyHome(home.name, "127.0.0.1", room.port))
        assertEquals(null, relaunched.model.refusal, "the gate refused a phone going back to its round seat")
        until("the relaunched phone holds the round's seed") { relaunched.half.round.uploading }
        room.beatUntil("the house listed the relaunched phone's switch-off") { room.house.state.switchedOff.any { it.index == seat.index } }
        assertEquals(listOf(RadioCause.Permission), relaunched.half.reports.map { it.cause }.distinct(), "the trip to Settings went up as something else")
        room.meeting()
        room.assertLastNotice(emptyList(), listOf(seat), words = warning("MIA"))
    }

    private companion object {
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 150L
    }
}
