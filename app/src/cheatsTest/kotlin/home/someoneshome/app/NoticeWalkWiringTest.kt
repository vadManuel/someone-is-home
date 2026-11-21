package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.Flow
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
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
import kotlin.test.assertTrue

/**
 * **A housed phone walks off the house notice and through the whole meeting on the house's pushes,
 * with nobody named and with somebody named** (D-250, H2).
 *
 * The simulator hunt found every living seat on HOUSE MEETING · NOTICES from the gate's close to the
 * next LIGHTS OUT: the notice's nine seconds were switched off on a housed phone, there was no row
 * for a push to take it off, and with NOBODY NAMED there is no card to swipe.
 *
 * Five phones over a real loopback socket, built as [HousedContentWiringTest] builds them: a real
 * [FlowModel] behind a real [RoundClient] and [LobbyOverTransport], and the real [RoundAuthority].
 * These phones have no `FlowHost`, so the notice's nine seconds are fired here by the same three
 * calls `FlowHost` makes ([Flow.runsItsOwnTimer], `pending`, `fallThrough`); `HousedPhoneTest`
 * proves `FlowHost` makes them on the frame clock. Every other step is a push from the house.
 */
class NoticeWalkWiringTest {

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
        val screen: ScreenId get() = flow.state.screen

        /** What `FlowHost` does when the notice's nine seconds run out, and only if it may. */
        fun noticeLapsed(): Boolean {
            val rule = flow.pending ?: return false
            if (!Flow.runsItsOwnTimer(flow.state)) return false
            flow.push(flow.fallThrough(rule))
            return true
        }
    }

    private val home = SavedHomesModel.sample().homes.first()
    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    @Test
    fun aHousedPhoneWalksOffTheNoticeAndThroughTheMeetingOnTheHousesPushes() = runBlocking {
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
                    p.model.typeLine("a line from ${p.name}")
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261117L, note = {}), "the round never armed")
                until("every phone holds its role") { phones.all { p -> p.pushed.any { it is RoundMessage.RoleRevealed } } }
                // Back on the springboard, as a phone is once LIGHTS OUT has been read.
                phones.forEach { it.flow.push(ScreenId.Home) }
                val caller = house.state.seats.first()

                suspend fun toTheNotice(label: String, named: Boolean) {
                    house.mint(Event.MeetingCalled(house.now(), caller, MeetingTrigger.MeetingCard))
                    for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
                    assertEquals(MeetingPhase.Discussion, house.state.meeting?.phase, "$label: the gate never closed")
                    until("$label: every phone stood on the notice") { phones.all { it.screen == ScreenId.Notice } }
                    for (p in phones) {
                        assertEquals(named, p.flow.state.notice.orEmpty().isNotEmpty(), "$label: ${p.name}'s notice named the wrong people")
                    }
                }

                suspend fun throughTheMeeting(label: String) {
                    house.mint(Event.DiscussionClosed(house.now()))
                    until("$label: every phone stood on the ballot") { phones.all { it.screen == ScreenId.Vote } }
                    for (seat in house.state.livingSeats) house.heard(Intent.LockVote(seat))
                    if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
                    until("$label: every phone stood on the tally") { phones.all { it.screen == ScreenId.Tally } }
                    house.mint(Event.TallyHalfwayReached(house.now()))
                    house.mint(Event.MeetingClosed(house.now()))
                    until("$label: every phone went back to the round") { phones.all { it.screen == ScreenId.Home } }
                }

                suspend fun walk(label: String, named: Boolean) {
                    toTheNotice(label, named)
                    // The notice's nine seconds, as FlowHost runs them: the house opened the talk.
                    for (p in phones) {
                        assertTrue(p.noticeLapsed(), "$label: ${p.name} had no way off the notice (H2)")
                        assertEquals(ScreenId.Discussion, p.screen, "$label: ${p.name}'s notice went somewhere else")
                    }
                    throughTheMeeting(label)
                }

                // 1 · Nobody named: the notice has no card to swipe.
                walk("nobody named", named = false)

                // 2 · Somebody named: a seat switched its radio off between meetings (D-187).
                house.mint(Event.RadioFaulted(house.now(), house.state.seats.last(), RadioCause.Person))
                walk("somebody named", named = true)

                // 3 · The talk ends while every phone is still on the notice: the ballot's push.
                toTheNotice("closed early", named = false)
                throughTheMeeting("closed early")
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
