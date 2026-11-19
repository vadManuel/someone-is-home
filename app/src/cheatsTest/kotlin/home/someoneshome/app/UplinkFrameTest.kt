package home.someoneshome.app

import home.someoneshome.core.insidersFor
import home.someoneshome.model.Event
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import home.someoneshome.platform.NoIdleTimer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Every phone's frames up the Wi-Fi are one kind, one length and one cadence, whatever it is and
 * whatever it asks for; and at an Egress every living phone is sent the same frames** (D-263).
 *
 * The leak: `ArmRevoke`, the Egress tile, Surge, Isolate and Static went up as frames of their own
 * the moment they were pressed, and only Insiders press them, so anybody measuring the Wi-Fi saw
 * which phones were the Insiders. And at an Egress's start the actor was sent one frame more than
 * everybody else (D-258's open item). Now every request a player makes in a round rides inside the
 * fixed-cadence upload's request slot, and every living phone but the actor is sent a blank of the
 * actor's answer's length.
 *
 * Five real phones, built as [AbilityWiringTest] builds them: a real [FlowModel] whose page-2 actions
 * reach a real [RoundClient], riding each phone's one socket through a real [LobbyOverTransport] to a
 * real [RoundAuthority] on the host, over loopback. Between each phone's round half and its socket
 * sits a [Tap] that records every frame both ways, as `TransportWire` writes it, with the time, and
 * can lose one upload or one answer on purpose. A guest is the round's only Insider (five seats carry
 * one, D-103), so its requests cross a real socket. The round: the Insider arms its Revoke, Surges
 * (the upload carrying it is lost on the way), everybody calls, checks in, readies and votes a
 * Resident out at a meeting (one phone's answer to its lock is lost), and the Insider fires the Egress.
 *
 * Faked: Bonjour, the radio (a [SimulatedAir] with nobody touching), and the house's clock, which is
 * advanced past each cooldown by hand and has its meeting deadlines minted by hand (no tick driver).
 */
class UplinkFrameTest {

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

    /** One frame on one phone's wire: when, how many bytes `TransportWire` wrote, and what it was. */
    private class Seen(val atMillis: Long, val bytes: Int, val body: RoundBody?)

    /**
     * **The seam between one phone's round half and its socket, both ways recorded.** Every frame the
     * round half hands its socket ([up]) and every frame the socket hands the round half ([down]).
     * [loseAnUploadCarrying] drops the next upload whose slot holds a request it matches, before it
     * reaches the socket: a beat lost on the way up. [loseTheAnswerTo] drops the next answer to an
     * upload whose slot held a request it matches: an answer lost on the way down. A slot holds as
     * many requests as fit (D-275), and either matches when any one of them does.
     */
    private class Tap(private val inner: RoundClient) : RoundSeam {
        private val lock = NSLock()
        private val ups = mutableListOf<Seen>()
        private val downs = mutableListOf<Seen>()
        private val asked = HashMap<String, List<Asked>>()
        var loseAnUploadCarrying: ((Asked) -> Boolean)? = null
        var loseTheAnswerTo: ((Asked) -> Boolean)? = null
        var uploadsLost = 0
        var answersLost = 0

        val up: List<Seen> get() = locked { ups.toList() }
        val down: List<Seen> get() = locked { downs.toList() }

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
            val slot = (body as? RoundBody.Sighted)?.let { RequestSlot.read(it.ask) }
            val request = slot?.asked?.takeIf { it.isNotEmpty() }
            val lose = locked {
                if (slot != null && request != null) asked[slot.id] = request
                val match = loseAnUploadCarrying
                if (request != null && match != null && request.any(match)) {
                    loseAnUploadCarrying = null
                    uploadsLost++
                    true
                } else {
                    ups += Seen(nowMillis(), TransportWire.encode(frame).length, body)
                    false
                }
            }
            if (!lose) send(frame)
        }

        override fun arrived(frame: TransportFrame) {
            val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
            val lose = locked {
                val request = (body as? RoundBody.UploadHeard)?.let { asked[it.id] }
                val match = loseTheAnswerTo
                if (request != null && match != null && request.any(match)) {
                    loseTheAnswerTo = null
                    answersLost++
                    true
                } else {
                    downs += Seen(nowMillis(), TransportWire.encode(frame).length, body)
                    false
                }
            }
            if (!lose) inner.arrived(frame)
        }

        override fun seated() = inner.seated()
        override fun roundEnded() = inner.roundEnded()
        override fun left() = inner.left()
        override fun leftTheForeground() = inner.leftTheForeground()
        override fun backInTheForeground() = inner.backInTheForeground()
    }

    private class Phone(scope: CoroutineScope, val name: String, val radioSpot: Seat, air: SimulatedAir, house: () -> RoundAuthority?) {
        val bonjour = Bonjour()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null

        /** `CheatRoot`'s two page-2 seams, exactly. */
        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
        )
        val pushed = mutableListOf<RoundMessage>()
        val round = RoundClient(scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY)) {}
            .also { made ->
                client = made
                made.onMessage = { pushed += it }
            }
        val tap = Tap(round)
        val link = LobbyOverTransport(
            scope, rangingCapacity = { SimulatedAir.BENCH_CAPACITY }, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = tap, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        /** A control on this phone, the way `MeetingModel` and the Subroutine screens send one. */
        fun press(intent: IntentMessage) = round.act(intent)
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(5) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    @Test
    fun everyPhoneSendsOneKindOfFrameAtOneSizeOnOneCadenceAndEveryLivingPhoneIsSentTheSameAtAnEgress() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air) { authority }.also { phones += it }
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    namesSeats = true,
                    note = {},
                )
                authority = house
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                listOf("ROSE", "DANI", "MARCUS", "PRIYA").forEachIndexed { i, name ->
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
                val seed = aSeedWhereAGuestIsTheOnlyInsider(host.link.house.armingInputs())
                assertTrue(armAtLightsOut(house, hosting = true, seed = seed, note = {}), "the round never armed")
                until("every phone holds the round's seed") { phones.all { it.round.round.uploading } }
                val armedAt = nowMillis()

                fun phoneAt(seat: Seat) = phones.single { it.name == house.state.names.single { n -> n.seat.index == seat.index }.name }
                fun seatOf(p: Phone) = house.state.names.single { it.name == p.name }.seat
                val insiderSeat = house.state.insiderSeats.single()
                val insider = phoneAt(insiderSeat)
                assertFalse(insider === host, "the draw made the host the Insider; this test is a guest's")
                val latencies = mutableListOf<Pair<String, Long>>()

                /** Press, then time until the house heard it (D-263's cost: up to a beat). */
                suspend fun timed(label: String, heard: () -> Boolean, press: () -> Unit) {
                    val from = nowMillis()
                    press()
                    until("the house heard $label", { "heard=${house.heard.takeLast(8)}" }, heard)
                    latencies += label to (nowMillis() - from)
                }
                // Past every opening cooldown, on the house's clock.
                val ready = listOf(
                    house.state.cooldownFor(insiderSeat, InsiderAbility.Revoke)?.readyAt?.step ?: 0L,
                    house.state.cooldownFor(insiderSeat, InsiderAbility.Access)?.readyAt?.step ?: 0L,
                ).max()
                house.advanceTo(Tick(ready))

                // ---- 1 · The Insider arms its Revoke ----------------------------------------------
                timed("the Revoke armed", { house.state.cooldownArmed.any { it.index == insiderSeat.index } }) {
                    insider.flow.actions().armRevoke()
                }

                // ---- 2 · The Insider Surges, and the upload carrying it is lost ------------------
                val surged = phones.first { it !== insider && it !== host }
                val surge = Intent.FireSurge(insiderSeat, seatOf(surged))
                insider.tap.loseAnUploadCarrying = { it is Asked.Act && it.intent is IntentMessage.FireSurge }
                timed("the Surge, one upload lost", { surge in house.heard }) { insider.flow.fireSurge(seatOf(surged).index) }
                assertEquals(1, insider.tap.uploadsLost, "the Surge's upload was never lost, so the retry was not tested")

                // ---- 3 · A meeting: called, checked in, readied, a Resident voted out -----------
                val caller = phones.first { it !== insider && it !== surged && it !== host }
                timed("the meeting call", { house.state.meeting != null }) { caller.press(IntentMessage.CallMeeting) }
                val living = phones.toList()
                for (p in living) timed("${p.name}'s check-in", { Intent.CheckIn(seatOf(p)) in house.heard }) { p.press(IntentMessage.CheckIn) }
                until("the gate closed", { "meeting=${house.state.meeting}" }) { house.state.meeting?.phase == MeetingPhase.Discussion }
                for (p in living) {
                    timed("${p.name}'s ready", { Intent.DeclareReadyToVote(seatOf(p)) in house.heard }) { p.press(IntentMessage.DeclareReadyToVote) }
                }
                until("the vote opened", { "meeting=${house.state.meeting}" }) { house.state.meeting?.phase == MeetingPhase.Vote }
                val restrained = phones.last { it !== insider && it !== host && it !== caller }
                val voter = living.first { it !== insider }
                voter.tap.loseTheAnswerTo = { it is Asked.Act && it.intent is IntentMessage.LockVote }
                for (p in living) {
                    timed("${p.name}'s selection", { Intent.SelectVote(seatOf(p), seatOf(restrained)) in house.heard }) {
                        p.press(IntentMessage.SelectVote(seatOf(restrained)))
                    }
                    timed("${p.name}'s lock", { Intent.LockVote(seatOf(p)) in house.heard }) { p.press(IntentMessage.LockVote) }
                }
                assertEquals(1, voter.tap.answersLost, "the lock's answer was never lost, so the dedup was not tested")
                if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
                house.mint(Event.TallyHalfwayReached(house.now()))
                until("the room restrained ${restrained.name}", { "meeting=${house.state.meeting}" }) { house.state.isOut(seatOf(restrained)) }
                house.mint(Event.MeetingClosed(house.now()))
                until("the meeting closed") { house.state.meeting == null }
                // The lost answer's request went up again under its id and was heard once, and
                // every phone's queue has emptied.
                delay(2 * SightingUpload.CADENCE_MILLIS + 300)
                until("every phone's requests were answered", { phones.joinToString { "${it.name}=${it.round.round.requestsWaiting}" } }) {
                    phones.all { it.round.round.requestsWaiting == 0 }
                }

                // ---- 3b · A burst (D-275): the Insider's Static and four entries, a Resident's five --
                // As many as fit go up a beat, so five requests from each of two phones reach the
                // house within two beats rather than five; the uploads stay one length (checked with
                // every other upload below); and every living phone is sent one frame of one length
                // for the Static, the Insider's answer among the other phones' blanks.
                house.advanceTo(
                    Tick(maxOf(house.clock, house.state.cooldownFor(insiderSeat, InsiderAbility.Access)?.readyAt?.step ?: 0L)),
                )
                val resident = phones.first { it !== insider && it !== host && !house.state.isOut(seatOf(it)) }
                val livingAtBurst = phones.filter { !house.state.isOut(seatOf(it)) }
                val insiderEntries = List(4) { IntentMessage.HandOverEntry(MarkerId("HOME-0$it"), listOf(3, 1, 4, 1, 5)) }
                val residentEntries = List(5) { IntentMessage.HandOverEntry(MarkerId("HOME-1$it"), listOf(2, 7, 1, 8, 2)) }
                val burstRequests = insiderEntries.map { it.attributedTo(insiderSeat) } + Intent.FireStatic(insiderSeat) +
                    residentEntries.map { it.attributedTo(seatOf(resident)) }
                val beforeBurst = livingAtBurst.associateWith { it.tap.down.size }
                val burstFrom = nowMillis()
                insider.press(IntentMessage.FireStatic)
                insiderEntries.forEach(insider::press)
                residentEntries.forEach(resident::press)
                until("the house heard both bursts", { "heard=${house.heard.takeLast(12)}" }) { burstRequests.all { it in house.heard } }
                val burstMillis = nowMillis() - burstFrom
                latencies += "a burst of five from each of two phones" to burstMillis
                delay(SETTLE_MILLIS)
                val staticBurst = livingAtBurst.associate { p ->
                    p.name to p.tap.down.drop(beforeBurst.getValue(p)).filter { it.body is RoundBody.Push }
                }

                // ---- 4 · The Egress ------------------------------------------------------------
                house.advanceTo(
                    Tick(
                        listOf(
                            house.clock,
                            house.state.egressReadyAt.step,
                            house.state.cooldownFor(insiderSeat, InsiderAbility.Access)?.readyAt?.step ?: 0L,
                        ).max(),
                    ),
                )
                insider.flow.push(ScreenId.Page2)
                val livingNow = phones.filter { !house.state.isOut(seatOf(it)) }
                assertEquals(phones.size - 1, livingNow.size, "the vote did not put exactly one phone out")
                val before = livingNow.associateWith { it.tap.down.size }
                timed("the Egress", { house.state.egressRunning }) { insider.flow.actions().armEgress() }
                until("every phone was told") { phones.all { p -> p.pushed.any { it is RoundMessage.EgressOpened } } }
                delay(SETTLE_MILLIS)
                val burst = livingNow.associate { p ->
                    p.name to p.tap.down.drop(before.getValue(p)).filter { it.body is RoundBody.Push }
                }

                // A few more beats with nothing pressed, then stop looking.
                delay(2 * SightingUpload.CADENCE_MILLIS)
                val endedAt = nowMillis()

                // ==== The checks =====================================================================
                println("D-263 REQUEST LATENCY MS — " + latencies.joinToString { "${it.first}=${it.second}" })
                val ms = latencies.map { it.second }
                println("D-263 REQUEST LATENCY MS — n=${ms.size} min=${ms.min()} mean=${ms.average().toLong()} max=${ms.max()}")

                // 1 · Up: every frame every phone sent in the round is an upload of one length.
                val upward = phones.associate { p -> p.name to p.tap.up.filter { it.atMillis in armedAt..endedAt } }
                val notUploads = upward.flatMap { (name, seen) ->
                    seen.filter { it.body !is RoundBody.Sighted }.map { "$name: ${it.body?.let { b -> b::class.simpleName } ?: "UNDECODABLE"}" }
                }
                assertEquals(emptyList(), notUploads, "a phone sent a round frame that was not its upload")
                val upLengths = upward.mapValues { (_, seen) -> seen.map { it.bytes }.toSet() }
                println("D-263 UPLOAD FRAME BYTES PER PHONE — $upLengths")
                assertEquals(1, upLengths.values.flatten().toSet().size, "the uploads are not one length: $upLengths")
                // ... on one cadence: in every window, every phone sent the same number, give or take
                // the one beat a window's edge can fall either side of.
                val windowMillis = 4 * SightingUpload.CADENCE_MILLIS
                var from = armedAt
                while (from + windowMillis <= endedAt) {
                    val counts = upward.mapValues { (_, seen) -> seen.count { it.atMillis in from until from + windowMillis } }
                    assertTrue(
                        counts.values.max() - counts.values.min() <= 1,
                        "in the window from ${from - armedAt} ms the phones sent different numbers of frames: $counts",
                    )
                    from += windowMillis
                }
                val counts = upward.mapValues { it.value.size }
                println("D-263 UPLOADS PER PHONE OVER ${endedAt - armedAt} MS — $counts")

                // 2 · Down: every upload is answered, by one frame of one length.
                val answers = phones.associate { p ->
                    p.name to p.tap.down.filter { it.atMillis in armedAt..endedAt && it.body is RoundBody.UploadHeard }.map { it.bytes }
                }
                assertEquals(1, answers.values.flatten().toSet().size, "the answers are not one length: ${answers.mapValues { it.value.toSet() }}")
                for (p in phones) {
                    val lost = if (p === voter) 1 else 0
                    assertTrue(
                        upward.getValue(p.name).size - answers.getValue(p.name).size - lost in 0..2,
                        "${p.name} sent ${upward.getValue(p.name).size} uploads and was answered ${answers.getValue(p.name).size} times",
                    )
                }

                // 3 · At the Egress, every living phone was sent the same frames, the actor included.
                fun net(seen: Seen): Int {
                    val push = seen.body as RoundBody.Push
                    return seen.bytes - push.seq.toString().length
                }
                val shapes = burst.mapValues { (_, seen) -> seen.map(::net) }
                val kinds = burst.mapValues { (_, seen) -> seen.map { (it.body as RoundBody.Push).message.kind.name } }
                println("D-263 EGRESS START FRAMES PER LIVING PHONE (BYTES NET OF SEQ DIGITS) — $shapes")
                println("D-263 EGRESS START KINDS — $kinds")
                assertEquals(1, shapes.values.toSet().size, "at the Egress the living phones were sent different frames: $kinds")
                assertTrue(
                    kinds.getValue(insider.name).first() == "AbilityFired" &&
                        kinds.filterKeys { it != insider.name }.values.all { it.first() == "AbilityBlanked" },
                    "the actor's answer and the blanks are not where they should be: $kinds",
                )

                // 3b · The burst (D-275): heard within two beats and the settle, each request once, and
                // the Static's frames the same on every living phone.
                assertTrue(
                    burstMillis <= 2 * SightingUpload.CADENCE_MILLIS + SETTLE_MILLIS,
                    "five requests from each of two phones took $burstMillis ms to reach the house",
                )
                for (asked in burstRequests) assertEquals(1, house.heard.count { it == asked }, "$asked was heard other than once")
                fun kindOf(seen: Seen) = (seen.body as RoundBody.Push).message.kind.name
                println("D-275 BURST FRAMES PER LIVING PHONE (BYTES NET OF SEQ DIGITS) — ${staticBurst.mapValues { (_, s) -> s.map(::net) }}")
                println("D-275 BURST KINDS — ${staticBurst.mapValues { (_, s) -> s.map(::kindOf) }}")
                // An entry is answered to the one phone that handed it over, graded and with its next
                // order, on either role (an Insider's fake is graded identically); the order is that
                // seat's draw, whose length is the draw's and not the role's (D-258's table). So the
                // entries' answers are counted and the grades measured, and everything else the burst
                // made, the Static's, must be the same frames on every living phone.
                val entryKinds = setOf("SubroutineGraded", "WorkOrderIssued")
                val graded = staticBurst.mapValues { (_, seen) -> seen.filter { kindOf(it) == "SubroutineGraded" } }
                assertEquals(insiderEntries.size, graded.getValue(insider.name).size, "the Insider's entries were not each answered once")
                assertEquals(residentEntries.size, graded.getValue(resident.name).size, "the Resident's entries were not each answered once")
                assertEquals(1, graded.values.flatten().map(::net).toSet().size, "the grades are not one length on both roles: $graded")
                val staticOnly = staticBurst.mapValues { (_, seen) -> seen.filter { kindOf(it) !in entryKinds } }
                val staticShapes = staticOnly.mapValues { (_, seen) -> seen.map(::net) }
                val staticKinds = staticOnly.mapValues { (_, seen) -> seen.map(::kindOf) }
                assertEquals(1, staticShapes.values.toSet().size, "at the burst the living phones were sent different frames: $staticKinds")
                assertTrue(
                    staticKinds.getValue(insider.name).first() == "AbilityFired" &&
                        staticKinds.filterKeys { it != insider.name }.values.all { it.first() == "AbilityBlanked" },
                    "the Static's answer and the blanks are not where they should be: $staticKinds",
                )

                // 4 · Exactly once, whatever was lost.
                assertEquals(1, house.heard.count { it == surge }, "the Surge was heard other than once: ${house.heard}")
                for (p in living) {
                    assertEquals(1, house.heard.count { it == Intent.LockVote(seatOf(p)) }, "${p.name}'s lock was heard other than once")
                    assertEquals(1, house.heard.count { it == Intent.CheckIn(seatOf(p)) }, "${p.name}'s check-in was heard other than once")
                }
                assertTrue(ms.max() <= SightingUpload.CADENCE_MILLIS * 3, "a request took longer than three beats: $latencies")
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** The first seed from [SEED] up whose draw makes one guest the only Insider. */
    private fun aSeedWhereAGuestIsTheOnlyInsider(inputs: ArmingInputs): Long {
        val host = inputs.names.single { it.name == "HOST" }.seat
        return (SEED until SEED + 10_000).first { seed ->
            insidersFor(seed, inputs.seats, inputs.chosenInsiders).let { it.size == 1 && it.single().index != host.index }
        }
    }

    private companion object {
        const val SEED = 20261117L
        const val SETTLE_MILLIS = 600L

        fun nowMillis(): Long = monotonicNanos() / 1_000_000
    }
}
