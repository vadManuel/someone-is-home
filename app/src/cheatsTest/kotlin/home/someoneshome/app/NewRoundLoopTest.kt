package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
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
import platform.Foundation.NSLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **NEW ROUND ends the round once, returns every seat to the lobby under its own name, and the
 * second round arms** (H1 of the 2026-11-17 hunt, D-252).
 *
 * The hunt's five- and six-seat rounds never reached a second round. The host's NEW ROUND sent
 * `RoundEnded`; the host's own phone heard it over loopback like everybody else, answered it with
 * `FlowModel.newRound` as `CheatRoot` wires it, and that call ended the round again: hundreds of
 * `THE HOUSE ENDED THE ROUND` lines, a seat that logged 5932 frames in seconds, names flipping to
 * UNNAMED as the desk was wiped on every pass, and a host that crashed.
 *
 * Five phones over a real loopback socket, built as `CheatRoot` builds them — the real
 * [LobbyOverTransport] with **`onRoundEnded` wired to `FlowModel.newRound`**, which is the wire the
 * loop ran through — a real [RoundClient], and the real [RoundAuthority]. The round is played to its
 * ending, the host presses NEW ROUND, and every phone counts the `RoundEnded` bodies that reach it.
 */
class NewRoundLoopTest {

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
        private val counting = NSLock()
        private var endedFrames = 0

        /** How many `RoundEnded` bodies reached this phone's socket. Counted as they land. */
        val roundEndedFrames: Int get() = counting.lock().let { try { endedFrames } finally { counting.unlock() } }

        // **CheatRoot's knot, tied the way CheatRoot ties it**: the link first, over a deferred
        // seam, so the flow can be built over the lobby the link carries, and the round last.
        private val seam = DeferredRoundSeam()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, onEvent = {},
            onInbound = { body ->
                if (LobbyWire.decodeOrNull(body) is LobbyBody.RoundEnded) {
                    counting.lock()
                    try {
                        endedFrames++
                    } finally {
                        counting.unlock()
                    }
                }
            },
            seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = seam,
            // **CheatRoot's wire, exactly**: the house's push is answered with NEW ROUND's own call.
            onRoundEnded = { flow.newRound() },
            wake = wake, advertising = bonjour,
        )
        val model: LobbyModel = LobbyModel(link = link).also { it.nameResident(name) }
        val flow: FlowModel = FlowModel(
            PanelState(screen = ScreenId.Home),
            homes = SavedHomesModel(),
            lobby = model,
            subroutines = SubroutineModel(send = { intent -> client?.act(intent) }),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
            onScreen = buzzer::showing,
        )
        val pushed = mutableListOf<RoundMessage>()
        val round = RoundClient(scope, flow, LineWatch {}, buzzer, air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY)) {}.also { made ->
            client = made
            made.onMessage = { pushed += it }
            seam.becomes(made)
        }
        val line = "my own secret, from $name"
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
    fun newRoundEndsTheRoundOnceKeepsEveryNameAndTheSecondRoundArms() = runBlocking {
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
                suspend fun everyLineIn() {
                    phones.forEach { p ->
                        p.model.typeLine(p.line)
                        check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                    }
                    until("every line reached the house") { host.model.standing.linesIn == phones.size }
                }
                everyLineIn()
                val chairs = host.link.house.armingInputs().names
                assertEquals(names.toSet(), chairs.map { it.name }.toSet(), "the lobby did not hold the five names")
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261117L, note = {}), "the first round never armed")
                until("every phone holds its role") { phones.all { p -> p.pushed.count { it is RoundMessage.RoleRevealed } == 1 } }

                // The round, played to its ending: the living restrain the Insiders.
                val callerSeat = house.state.livingSeats.first { it !in house.state.insiderSeats }
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
                until("every phone heard the round end") { phones.all { p -> p.pushed.any { it is RoundMessage.RoundEnded } } }
                assertTrue(phones.all { it.roundEndedFrames == 0 }, "a phone was sent back to the lobby before NEW ROUND")

                // **NEW ROUND, pressed on the host's ending screen.**
                host.flow.newRound()
                until("every phone is back in the lobby") { phones.all { it.flow.state.screen == ScreenId.Lobby } }
                until("every name came back to the desk") {
                    host.link.house.armingInputs().names.map { it.name }.toSet() == names.toSet()
                }
                // A loop needs no more than this to show itself: the hunt's host logged hundreds of
                // ends in this long.
                delay(2_000)
                for (p in phones) {
                    assertEquals(1, p.roundEndedFrames, "${p.name} was sent the round's end ${p.roundEndedFrames} times")
                    assertEquals(ScreenId.Lobby, p.flow.state.screen, "${p.name} left the lobby it was returned to")
                }
                assertEquals(chairs, host.link.house.armingInputs().names, "a seat came back under somebody else's name, or none")
                assertEquals(phones.size, host.model.standing.joined, "a seat dropped out of the lobby")
                assertEquals(0, host.model.standing.linesIn, "last round's lines survived NEW ROUND")
                assertTrue(host.model.standing.names.none { it.isBlank() }, "the lobby lists an UNNAMED seat: ${host.model.standing.names}")

                // **The second round.** Every phone hands a line over again, and LIGHTS OUT arms.
                everyLineIn()
                assertTrue(
                    armAtLightsOut(house, hosting = true, seed = 20261118L, note = {}),
                    "the second round never armed after NEW ROUND",
                )
                until("every phone holds a second role") {
                    phones.all { p -> p.pushed.count { it is RoundMessage.RoleRevealed } == 2 }
                }
                assertTrue(!house.ended, "the second round armed already over")
                assertTrue(phones.all { it.roundEndedFrames == 1 }, "arming the second round ended something")
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
