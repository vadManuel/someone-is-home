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
import kotlinx.coroutines.CompletableDeferred
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
 * **A phone that leaves the foreground mid-round is named at the next meeting, the host's own seat
 * exactly as a guest's** (D-187, D-229), **and its report reaches the house once, across a socket
 * that failed and across the process ending** (D-245).
 *
 * Real sockets on loopback, the real link, desk, ledger and authority, and each phone's real round
 * half on its one socket, as `RoundClient` puts it there in the app. Faked: Bonjour, the OS's
 * foreground notifications (the test calls `backgrounded` and `foregrounded`, which is all
 * `deviceForeground` does), the radio ([NoRadio]) and the tick driver (the meeting's gate is closed
 * by check-ins put to the authority, as the desk would put them). The beat is called by hand.
 *
 * What a test cannot have is iOS suspending the guest: here a guest's socket stays up while it is
 * "away" unless a test stops it. The host's does not, because its house really stops and reopens
 * (D-225). The D-245 cases stand the two ways a report used to fail: a socket that failed under a
 * suspended phone (frames handed to it vanish until the house drops the connection and the phone
 * resumes), and the process ending (the phone's coroutines cancelled with no goodbye, and a fresh
 * phone built over the same store, as a relaunch reads its files back).
 */
class EvasionNoticeTest {

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
    private class Half(scope: CoroutineScope, wake: ScreenWake, kept: SeatStore, log: MutableList<String>) : RoundSeam {
        private var up: (suspend (TransportFrame) -> Unit)? = null

        /**
         * **This phone's socket failed while it was suspended, and the phone has not noticed yet**
         * (D-245): every frame handed to it vanishes. The next seating is a live socket again.
         */
        var defunctSocket = false

        /** Every push this phone applied. */
        val pushed = mutableListOf<RoundMessage>()

        /** Every body this phone sent up, raw, whether or not its socket carried it. */
        val sent = mutableListOf<String>()
        val round = RoundOverTransport(
            scope, FlowModel(PanelState(screen = ScreenId.Home)),
            buzzer = HouseBuzzer(SilentHaptics),
            send = { frame -> if (!defunctSocket) up?.invoke(frame) },
            radio = NoRadio,
            wake = wake,
            onEvent = { log += it },
            onInbound = { body -> (RoundWire.decodeOrNull(body) as? RoundBody.Push)?.let { pushed += it.message } },
            onOutbound = { sent += it },
            kept = kept,
        )

        init {
            scope.launch { round.run() }
        }

        override fun linked(send: suspend (TransportFrame) -> Unit) {
            up = send
        }
        override fun arrived(frame: TransportFrame) = round.frameArrived(frame)
        override fun seated() {
            defunctSocket = false
            round.reseated()
        }
        override fun roundEnded() = round.roundEnded()
        override fun leftTheForeground() = round.leftTheForeground()
        override fun backInTheForeground() = round.backInTheForeground()

        /** The fault bodies this phone sent up, decoded. */
        val reports: List<RoundBody.RadioFaulted> get() = sent.mapNotNull { RoundWire.decodeOrNull(it) as? RoundBody.RadioFaulted }

        /** The raw fault bodies, for their length. */
        val reportBodies: List<String> get() = sent.filter { RoundWire.decodeOrNull(it) is RoundBody.RadioFaulted }
    }

    /**
     * One phone, in its own job: cancelling it is the process ending (no hang-up, no goodbye).
     * [store] is its files: the seat token and the owed reports, which a relaunch reads back.
     */
    private class Phone(parent: CoroutineScope, val name: String, house: () -> RoundAuthority?, val store: SeatStore = MemorySeat()) {
        val job = Job(parent.coroutineContext[Job])
        private val scope = CoroutineScope(parent.coroutineContext + job)
        val air = Air()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        val half = Half(scope, wake, store, log)
        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = store,
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
    private class Room(
        val all: MutableList<Phone>,
        val house: RoundAuthority,
        val said: MutableList<String>,
        private val made: MutableList<Phone>,
        private val scope: CoroutineScope,
    ) {
        val host get() = all.first()
        fun seatOf(phone: Phone): Seat = house.state.names.single { it.name == phone.name }.seat

        /** The house's log lines naming [seat]'s report as put to the rules. */
        fun reportsPut(seat: Seat): Int = said.count { it.startsWith("RADIO FAULT — SEAT ${seat.index} · PERSON") }

        /** The house's log lines naming a second copy of [seat]'s report, heard and put to nothing. */
        fun repeatsHeard(seat: Seat): Int = said.count { it.startsWith("RADIO FAULT — SEAT ${seat.index} · HEARD AGAIN") }

        /**
         * **[old]'s process ends, and the app is opened again**: [old]'s coroutines cancelled with no
         * hang-up, and a fresh phone over the same files joining the same house, which resumes it by
         * its stored token.
         */
        fun relaunch(old: Phone): Phone {
            old.job.cancel()
            val fresh = Phone(scope, old.name, { house }, old.store).also { made += it }
            all[all.indexOf(old)] = fresh
            fresh.model.attachTo(NearbyHome("", "127.0.0.1", host.air.ports.last()))
            return fresh
        }
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
                val guests = guestNames.map { Phone(scope, it, { authority }).also { p -> phones += p } }
                guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
                until("every guest was seated") { host.model.standing.joined == 1 + guests.size }
                val all = mutableListOf(host).also { it += guests }
                all.forEachIndexed { i, p -> p.handOver("line $i") }
                until("every line reached the house") { host.model.standing.linesIn == all.size }
                scope.body(Room(all, authority, said, phones, scope))
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

    /** Beats on every phone, as the cadence gives them, until [condition] holds. */
    private suspend fun Room.beatUntil(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) {
                while (!condition()) {
                    all.forEach { it.half.round.beat() }
                    delay(SETTLE_MILLIS)
                }
            }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened, beating: $what", late)
        }
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

    /** The one body's length (D-223, D-245): a cause and an onset's name, the same from every phone. */
    private val reportLength = RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Person, "0".repeat(RoundBody.RadioFaulted.ONSET_CHARS))).length

    /**
     * [phone] reported one onset, as a person's act, in the one fixed body: however many copies it
     * sent (one more each time a connection was replaced before the house answered), one name.
     */
    private fun assertOnePersonsOnset(phone: Phone, label: String) {
        val reports = phone.half.reports
        assertTrue(reports.isNotEmpty(), "$label: no report went up")
        assertEquals(setOf(RadioCause.Person), reports.map { it.cause }.toSet(), "$label: the report was not a person's")
        assertEquals(1, reports.map { it.onset }.toSet().size, "$label: one leave went up as more than one onset: $reports")
        assertEquals(setOf(reportLength), phone.half.reportBodies.map { it.length }.toSet(), "$label: the report was not the one fixed body")
    }

    // ---- A guest ------------------------------------------------------------------------------------

    /**
     * **GUARD: a guest that leaves the foreground mid-round is named once, at the next meeting, to
     * every phone in one frame of one length**, its report is the one fixed body, and the house's
     * answer lets it stop keeping the report (D-245).
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
        assertEquals(1, guest.half.reports.size, "the guest's report did not go up once, on a socket that stayed up")
        assertOnePersonsOnset(guest, "the guest")
        until("the house's answer reached the guest") { guest.half.round.owing == 0 }
        assertTrue(room.all.filter { it !== guest }.all { it.half.reports.isEmpty() }, "a phone that never left was reported")
        assertEquals(1, room.reportsPut(seat), "the house's log did not say so once: ${room.said}")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    // ---- D-245: the socket failed while the guest was suspended -----------------------------------------

    /**
     * **GUARD (D-245): a report sent into a socket that failed while the phone was suspended still
     * reaches the house, exactly once.** The guest leaves; while it is away its socket fails, and the
     * phone does not know yet: the report it sends on its return vanishes. Then the house drops the
     * failed connection, the phone resumes by its token, and the report it is still keeping goes up the
     * new connection. The house puts it to the rules once and names the guest once.
     */
    @Test
    fun aReportSentIntoASocketThatFailedWhileSuspendedStillReachesTheHouseOnce() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val guest = room.all.last()
        val seat = room.seatOf(guest)

        guest.link.backgrounded()
        guest.half.defunctSocket = true
        guest.link.foregrounded()
        room.beats()
        assertTrue(guest.half.reports.isNotEmpty(), "the guest never sent its report into the closed socket")
        assertTrue(room.house.state.unreachable.isEmpty(), "a report sent into a closed socket reached the house")
        assertEquals(1, guest.half.round.owing, "the guest stopped keeping a report the house never answered")

        // The connection is found defunct and dropped; the phone comes back to its own seat.
        room.host.link.house.dropConnection(seat)
        until("the guest came back to its seat") { !guest.half.defunctSocket }
        room.beatUntil("the house heard the guest's report") { room.house.state.unreachable.isNotEmpty() }
        room.beatUntil("the house's answer reached the guest") { guest.half.round.owing == 0 }
        room.beats()
        assertEquals(listOf(seat), room.house.state.unreachable, "the house wrote down somebody other than the guest")
        assertOnePersonsOnset(guest, "the guest")
        assertEquals(1, room.reportsPut(seat), "the report was not put to the rules exactly once: ${room.said}")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    /**
     * **GUARD (D-245): a report whose answer is lost is heard once.** The house has the guest's
     * report, but its answer is queued to a socket that has stopped draining, and the connection is
     * dropped with the answer still in it. The guest resumes, still keeping the report, and sends it
     * again: the house knows the onset by its name, puts it to the rules no second time, and answers.
     */
    @Test
    fun aReportWhoseAnswerIsLostIsHeardOnce() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val guest = room.all.last()
        val seat = room.seatOf(guest)

        // From here the guest's socket takes nothing the house writes, as if the phone had gone.
        val drain = CompletableDeferred<Unit>()
        room.host.link.house.socketWrite = { to, write ->
            if (to == seat) drain.await()
            write()
        }
        guest.link.backgrounded()
        guest.link.foregrounded()
        room.beats()
        until("the house heard the guest's report") { room.house.state.unreachable.isNotEmpty() }
        assertEquals(1, guest.half.round.owing, "the guest stopped keeping a report whose answer never reached it")

        // The connection is dropped with the answer in its queue. The next connection drains; the
        // held write is never let go, so the lost answer cannot arrive late.
        room.host.link.house.socketWrite = { _, write -> write() }
        room.host.link.house.dropConnection(seat)
        room.beatUntil("the guest's report went up again on its new connection") { guest.half.reports.size >= 2 }
        room.beatUntil("the house's answer reached the guest") { guest.half.round.owing == 0 }
        room.beats()
        assertOnePersonsOnset(guest, "the guest")
        assertEquals(1, room.reportsPut(seat), "a report heard twice was put to the rules twice: ${room.said}")
        assertTrue(room.repeatsHeard(seat) >= 1, "the second copy never reached the house, so nothing was measured: ${room.said}")
        assertEquals(listOf(seat), room.house.state.unreachable, "the house wrote down somebody other than the guest")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    // ---- D-245: iOS ends the app -----------------------------------------------------------------------

    /**
     * **GUARD (D-245): a report owed when iOS ends the app reaches the house from the relaunch,
     * once.** The guest leaves the foreground (going to Settings to withdraw Camera, say) and iOS
     * ends it there: its process is gone with no goodbye, and only its files are left. The app is
     * opened again: the fresh process resumes its seat by its stored token, hears the round's seed
     * again, reads back the report it owed, and sends it. The house names the guest once, and the
     * answer clears the file.
     */
    @Test
    fun aReportOwedWhenTheAppIsEndedReachesTheHouseFromTheRelaunch() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val guest = room.all.last()
        val seat = room.seatOf(guest)

        guest.link.backgrounded()
        assertTrue(guest.store.owedReports() != null, "the leave was not kept beside the seat token before the phone was suspended")
        val relaunched = room.relaunch(guest)
        until("the relaunched phone holds the round's seed") { relaunched.half.round.uploading }
        assertEquals(guest.store.stored(), relaunched.store.stored(), "the relaunch is not over the same files")
        room.beats()
        room.beatUntil("the house heard the relaunched phone's report") { room.house.state.unreachable.isNotEmpty() }
        room.beatUntil("the house's answer reached the relaunched phone") { relaunched.half.round.owing == 0 }
        assertEquals(null, relaunched.store.owedReports(), "an answered report is still kept beside the token")
        assertEquals(listOf(seat), room.house.state.unreachable, "the house wrote down somebody other than the guest, or lost it")
        assertOnePersonsOnset(relaunched, "the relaunched phone")
        assertEquals(1, room.reportsPut(seat), "the report was not put to the rules exactly once: ${room.said}")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    /** **GUARD (D-036, D-245): a phone that crashes in the foreground and relaunches is named by nobody.** */
    @Test
    fun aPhoneThatCrashesInTheForegroundAndRelaunchesIsNamedByNobody() = evening { room ->
        room.lightsOut()
        room.beats(1)
        val guest = room.all.last()
        val relaunched = room.relaunch(guest)
        until("the relaunched phone holds the round's seed") { relaunched.half.round.uploading }
        room.beats()
        assertTrue(relaunched.half.reports.isEmpty(), "a phone that crashed in the foreground was reported on its relaunch")
        room.meeting()
        room.assertNoticeNames(emptyList())
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
        assertEquals(1, out.half.reports.size, "the Revoked phone's report did not go up once")
        assertOnePersonsOnset(out, "the Revoked phone")
        assertEquals(out.half.reportBodies.map { it.length }, living.half.reportBodies.map { it.length }, "a Revoked phone's report differs on the wire from a living one's")
        // Both answered, out or not (rule 1): the answer says nothing about the seat it goes to.
        until("both phones were answered") { out.half.round.owing == 0 && living.half.round.owing == 0 }
        assertEquals(listOf(livingSeat), house.state.unreachable, "the house wrote down the seat that is out, or lost the living one")

        room.meeting()
        room.assertNoticeNames(listOf(livingSeat))
    }

    // ---- The host ------------------------------------------------------------------------------------

    /**
     * **GUARD: the host is treated like anyone else** (the owner's 2026-11-16 ruling). The host's own
     * lock takes the house off the air (D-225); on the return its round half reports over loopback
     * once the house listens again, the house records its OWN seat once, and the next meeting names
     * it. Its report may go up twice (handed to its socket while it was coming back, and again once
     * seated, before the answer arrives); the house hears the one onset once (D-245).
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
        until("the house's answer reached the host") { host.half.round.owing == 0 }
        assertEquals(listOf(seat), room.house.state.unreachable, "the house named somebody other than its own seat")
        assertOnePersonsOnset(host, "the host")
        assertEquals(1, room.reportsPut(seat), "the host's report was not put to the rules exactly once: ${room.said}")
        assertTrue(room.all.drop(1).all { it.half.reports.isEmpty() }, "a guest whose socket the house dropped was reported")

        room.meeting()
        room.assertNoticeNames(listOf(seat))
    }

    /**
     * **GUARD (D-283, built by D-289): the house records its own seat at arming, and an out HOST who
     * locks is named at the next meeting, while an out guest who locks is not.** The host's seat comes
     * from the token its own loopback client was handed; it reaches the arming and no phone. Six seats,
     * so two Revokes leave the round running.
     */
    @Test
    fun anOutHostWhoLocksIsNamedAndAnOutGuestIsNot() = evening(listOf("ROSE", "DANI", "MARCUS", "TOMAS", "PRIYA")) { room ->
        room.lightsOut()
        room.beats(1)
        val house = room.house
        val hostSeat = room.seatOf(room.host)
        assertEquals(hostSeat, house.state.host, "the house did not record its own seat at arming")
        val insider = house.state.insiderSeats.single()
        assertTrue(insider != hostSeat, "the fixture's draw made the host the Insider, so it cannot be Revoked")
        val guestSeat = house.state.livingPlainResidents.first { it != hostSeat }
        val guest = room.all.single { room.seatOf(it) == guestSeat }

        for (target in listOf(hostSeat, guestSeat)) {
            house.advanceTo(house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt ?: Tick(0))
            house.mint(Event.RevokeArmed(house.now(), insider))
            val at = house.now()
            house.mint(Event.ContactMade(at, insider, target))
            house.mint(Event.ContactMade(at, target, insider))
            assertTrue(house.state.isRevoked(target), "the fixture never put seat ${target.index} out")
        }
        assertTrue(!house.state.ended, "the Revokes ended the round, so nothing after them is measured")

        room.host.link.backgrounded()
        until("every seat dropped with the house") { room.host.link.house.armingInputs().seats.isEmpty() }
        room.host.link.foregrounded()
        room.beats(1)
        until("every seat came back") { room.host.link.house.armingInputs().seats.size == 6 }
        guest.link.backgrounded()
        room.beats(1)
        guest.link.foregrounded()
        room.beats()
        until("the house heard both reports") {
            listOf(hostSeat, guestSeat).all { seat -> room.said.any { it.startsWith("RADIO FAULT — SEAT ${seat.index} · PERSON") } }
        }
        assertEquals(listOf(hostSeat), house.state.unreachable, "the house did not list exactly the out host")

        room.meeting()
        room.assertNoticeNames(listOf(hostSeat))
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
