package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.MeetingCounts
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What a housed phone draws comes off the house's socket: the Insider's own line, the seat count,
 * the meeting's counts and caller, and the Residents' ending** (D-247; the owner, 2026-11-16: *can
 * we stop having fake things in the app*).
 *
 * Five phones over a real loopback socket, built as `CheatRoot` builds them ([SubroutineQuestionWiringTest]'s
 * harness): a real [FlowModel] behind a real [RoundClient] and [LobbyOverTransport], each phone
 * typing and handing over its own one line in the lobby. The house is the real [RoundAuthority].
 * Every assertion reads the phone's own [PanelVals] — what its screens draw — and the names are
 * none of the design's, so a fixture cannot pass for the house.
 */
class HousedContentWiringTest {

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

    private object Still : Haptics {
        override fun buzz(haptic: Haptic) = Unit
        override fun play(script: List<HapticStep>, repeating: Boolean) = Unit
        override fun stop() = Unit
    }

    private class Phone(scope: CoroutineScope, val name: String, val radioSpot: Seat, air: SimulatedAir, house: () -> RoundAuthority?) {
        val bonjour = Bonjour()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(Still)
        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            homes = SavedHomesModel(),
            subroutines = SubroutineModel(send = { intent -> client?.act(intent) }),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
            onScreen = buzzer::showing,
        )
        val pushed = mutableListOf<RoundMessage>()
        val round = RoundClient(scope, flow, LineWatch {}, buzzer, air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY)) {}.also { made ->
            client = made
            made.onMessage = { pushed += it }
        }
        val link = LobbyOverTransport(
            scope, onEvent = {}, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = round, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
        val line = "my own secret, from $name"
        val vals: PanelVals get() = PanelVals(flow.state)
    }

    private val home = SavedHomesModel.sample().homes.first()
    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    @Test
    fun aHousedPhoneDrawsTheHousesContentAndNoFixture() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val host = Phone(scope, names[0], Seat(0), air) { authority }.also { phones += it }
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                val house = authority
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                names.drop(1).forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEach { p ->
                    p.model.typeLine(p.line)
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261117L, note = {}), "the round never armed")
                until("every phone holds its role and the roster") {
                    phones.all { p -> p.pushed.any { it is RoundMessage.RoleRevealed } && p.flow.state.roster != null }
                }

                fun phoneAt(seat: Seat): Phone = phones.single { it.name == house.state.names.single { n -> n.seat.index == seat.index }.name }
                val insiders = house.state.insiderSeats.map(::phoneAt)
                val residents = phones - insiders.toSet()
                assertTrue(insiders.isNotEmpty() && residents.isNotEmpty(), "the round drew no Insider or no Resident")

                // 1 · The Insider's own line, quoted back on that phone and no other.
                for (p in insiders) {
                    assertTrue(p.line in p.vals.houseLine, "${p.name} (Insider) was not quoted their own line: ${p.vals.houseLine}")
                    assertTrue("Priya" !in p.vals.houseLine, "${p.name} was held to the design's spare key")
                }
                for (p in residents) {
                    assertNull(p.flow.state.heldOver, "${p.name} (Resident) was sent a line")
                    val wire = p.pushed.filterIsInstance<RoundMessage.RoleRevealed>().single()
                    assertEquals("", wire.line, "a Resident's reveal carried words over the socket")
                }
                for (p in phones) {
                    for (other in phones.filter { it !== p }) {
                        assertTrue(other.line !in p.vals.houseLine, "${other.name}'s line reached ${p.name}'s phone")
                    }
                }

                // 2 · PERIMETER ARMED counts the round's five.
                for (p in phones) assertEquals("5 RESIDENTS\nACCOUNTED FOR", p.vals.accountedFor, "${p.name}'s armed count")

                // 3 · A meeting: the house's caller, the gate's count, and no answered count.
                val caller = residents.first()
                val callerSeat = house.state.names.single { it.name == caller.name }.seat
                house.mint(Event.MeetingCalled(house.now(), callerSeat, MeetingTrigger.MeetingCard))
                until("every phone heard the gate's count") { phones.all { it.flow.meeting.counts.present != null } }
                for (p in phones) {
                    val counts = p.flow.meeting.counts
                    assertNull(counts.answered, "${p.name} drew an answered count no house sent")
                    assertEquals(5, counts.seats, "${p.name}'s gate is not against the round's seats")
                    assertEquals(caller.name, p.vals.callerOr("PRIYA"), "${p.name}'s ring named somebody else")
                }

                // 4 · The meeting run to the ending: every seat checks in and the living restrain the Insiders.
                while (!house.ended) {
                    val target = house.state.livingInsiders.first()
                    if (house.state.meeting == null) house.mint(Event.MeetingCalled(house.now(), callerSeat, MeetingTrigger.MeetingCard))
                    for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
                    if (house.state.meeting?.phase == MeetingPhase.Discussion) house.mint(Event.DiscussionClosed(house.now()))
                    for (seat in house.state.livingSeats) {
                        house.heard(Intent.SelectVote(seat, target))
                        house.heard(Intent.LockVote(seat))
                    }
                    if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
                    house.mint(Event.TallyHalfwayReached(house.now()))
                    if (!house.ended) house.mint(Event.MeetingClosed(house.now()))
                }
                assertEquals(Winner.Residents, house.state.outcome?.winner)
                until("every phone holds the Residents") { phones.all { it.flow.state.residents != null } }
                val residentNames = residents.map { it.name }.sortedBy { names.indexOf(it) }
                for (p in phones) {
                    assertEquals(residentNames, p.vals.residentNames, "${p.name}'s ending named the wrong Residents")
                    assertNull(p.vals.residentsStanding, "${p.name}'s ending drew a standing count no house sent")
                    assertEquals("0", p.vals.egressesContained, "${p.name}'s ending counted Egresses nobody contained")
                    assertNull(p.flow.state.heldOver, "${p.name} kept a line past the round")
                    assertTrue(p.flow.meeting.counts != MeetingCounts(), "${p.name} holds the design's meeting counts")
                }
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
