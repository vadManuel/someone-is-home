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
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
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

    /** One phone's round half on its one socket, built once its panel exists, as `RoundClient` builds it. */
    private class Half(private val scope: CoroutineScope, private val wake: ScreenWake, private val log: MutableList<String>) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null

        /** Every push this phone applied, raw, as the house wrote it. */
        val pushedRaw = mutableListOf<String>()

        /** Every body this phone sent up, raw. */
        val sent = mutableListOf<String>()

        /** The OS's answer to *is a grant this phone was seated with switched off*. The test throws it. */
        var grantOff = false

        lateinit var round: RoundOverTransport

        fun attach(flow: FlowModel) {
            round = RoundOverTransport(
                scope, flow,
                buzzer = HouseBuzzer(SilentHaptics),
                send = { frame -> up?.invoke(frame) },
                radio = NoRadio,
                wake = wake,
                onEvent = { log += it },
                onInbound = { body -> if (RoundWire.decodeOrNull(body) is RoundBody.Push) pushedRaw += body },
                onOutbound = { sent += it },
                refusedGrants = { if (grantOff) 1 else 0 },
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

    private class Phone(parent: CoroutineScope, val name: String, house: () -> RoundAuthority?) {
        val job = Job(parent.coroutineContext[Job])
        private val scope = CoroutineScope(parent.coroutineContext + job)
        val air = Air()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        val half = Half(scope, wake, log)

        /** Every lobby body this phone was sent, decoded. */
        val lobbyBodies = mutableListOf<LobbyBody>()
        private var answer: () -> Unit = {}
        val link = LobbyOverTransport(
            scope, onEvent = { log += it },
            onInbound = { body -> LobbyWire.decodeOrNull(body)?.let { lobbyBodies += it } },
            seat = MemorySeat(),
            onRadioFaulted = HouseDoors(house)::faulted,
            round = half, wake = wake, advertising = air,
            // `CheatRoot`'s wiring: the house's `RoundEnded` is answered by the panel's NEW ROUND.
            onRoundEnded = { answer() },
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
        val flow = FlowModel(PanelState(screen = ScreenId.Lobby, houseAttached = true), lobby = model, perms = PermsModel.sample())

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

    private class Room(val all: List<Phone>, val house: RoundAuthority, val said: MutableList<String>) {
        val host get() = all.first()
        val guests get() = all.drop(1)
        fun seatOf(phone: Phone): Seat = house.state.names.single { it.name == phone.name }.seat
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
                scope.body(Room(all, authority, said))
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

    /** The last notice every phone holds: one frame, the same bytes on every phone, naming [names], warned or not. */
    private fun Room.assertLastNotice(names: List<Seat>, warned: Boolean, words: String) {
        val frames = all.map { p ->
            p.half.pushedRaw.last { (RoundWire.decodeOrNull(it) as? RoundBody.Push)?.message is RoundMessage.NoticeIssued }
        }
        assertEquals(1, frames.map { it.substringAfter("\"message\"") }.toSet().size, "the notice was not the same on every phone: $frames")
        for (p in all) {
            val notice = p.half.pushed.filterIsInstance<RoundMessage.NoticeIssued>().last()
            assertEquals(RoundMessage.NoticeIssued(names, permissionSwitchedOff = warned), notice, "${p.name} was told something else")
            assertEquals(words, PanelVals(p.flow.state).notice, "${p.name}'s notices screen said something else")
        }
    }

    private val closedLines = "$HOUSE_IS_CLOSED\n$SWITCHED_OFF_AGAIN"

    private fun warning(name: String) = "Resident $name was unreachable this shift. Occupancy data for this interval is incomplete. " +
        "A permission was switched off. If anyone switches one off again, the house closes and everyone starts over."

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
        room.assertLastNotice(listOf(room.seatOf(mia)), warned = true, words = warning("MIA"))
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
        room.assertLastNotice(listOf(room.seatOf(jo)), warned = false, words = leaveOnly("JO"))
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

    private companion object {
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 150L
    }
}
