package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.Flow
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.MeetingCounts
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.OutBy
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.SubroutineModel
import home.someoneshome.ui.WallTime
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
 * **A second round starts from nothing the first one wrote, and keeps everything the evening holds**
 * (D-259; found by the 2026-11-17 simulator hunt).
 *
 * After NEW ROUND the five-seat hunt's second PERIMETER ARMED still read the first round's OUT
 * REVOKED, OUT RESTRAINED, METER 96% and work order: `FlowModel.newRound` walked the screen to the
 * lobby and copied every other field across.
 *
 * Five phones over a real loopback socket, built as `CheatRoot` builds them (the real
 * [LobbyOverTransport] with `onRoundEnded` wired to `FlowModel.newRound`, a real [RoundClient] over a
 * [SimulatedAir] radio, the real [RoundAuthority]), each with a wall clock. Round one is played to an
 * Insider ending with one seat Revoked, one Restrained and another Revoked; the host presses NEW
 * ROUND. Then, on the host and every guest: **every round-scoped field is back to what a phone that
 * never played holds**, and every lobby-scoped field (the name, the seat token, the home, hosting,
 * the house attached, the clock) is what it was. The second round then arms, and nobody's ARMED is
 * drawn out.
 */
class RoundBoundaryTest {

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

    private class Phone(scope: CoroutineScope, val name: String, spot: Seat, air: SimulatedAir, house: () -> RoundAuthority?) {
        val bonjour = Bonjour()
        val kept = Kept()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(Still)
        val radio = air.radioFor(spot)
        private val seam = DeferredRoundSeam()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, rangingCapacity = { SimulatedAir.BENCH_CAPACITY }, onEvent = {},
            seat = kept,
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = seam,
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
            wallClock = { CLOCK },
        )
        val pushed = mutableListOf<RoundMessage>()
        val round = RoundClient(scope, flow, LineWatch {}, buzzer, radio, wake, ranging = air.rangingFor(spot, SimulatedAir.BENCH_CAPACITY)) {}.also { made ->
            client = made
            made.onMessage = { pushed += it }
            seam.becomes(made)
        }
        val line = "my own secret, from $name"
    }

    private companion object {
        val CLOCK = WallTime(hour = 21, minute = 7, weekday = 6, day = 16, month = 8)
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

    /**
     * **Every field of [state] that differs from [fresh]**, by name. The check below compares the
     * whole panel at once, so a field added to `PanelState` later is covered without this list
     * knowing it exists; the list is for the message and for proving round one wrote something.
     */
    private fun written(state: PanelState, fresh: PanelState): List<String> =
        FIELDS.filter { (_, read) -> read(state) != read(fresh) }.map { it.first }

    private val FIELDS: List<Pair<String, (PanelState) -> Any?>> = listOf(
        "screen" to { it.screen },
        "houseAttached" to { it.houseAttached },
        "role" to { it.role },
        "revoke" to { it.revoke },
        "markersOn" to { it.markersOn },
        "torch" to { it.torch },
        "outBy" to { it.outBy },
        "verdict" to { it.verdict },
        "secondsLeft" to { it.secondsLeft },
        "egressType" to { it.egressType },
        "egressNodes" to { it.egressNodes },
        "egressRunning" to { it.egressRunning },
        "egressHeld" to { it.egressHeld },
        "egressLeft" to { it.egressLeft },
        "beatHeld" to { it.beatHeld },
        "current" to { it.current },
        "scannedAt" to { it.scannedAt },
        "revealed" to { it.revealed },
        "signOff" to { it.signOff },
        "order" to { it.order },
        "ballots" to { it.ballots },
        "tally" to { it.tally },
        "caller" to { it.caller },
        "meetingPhase" to { it.meetingPhase },
        "voteWindowSeconds" to { it.voteWindowSeconds },
        "integrityPercent" to { it.integrityPercent },
        "seats" to { it.seats },
        "roster" to { it.roster },
        "notice" to { it.notice },
        "isolateTargets" to { it.isolateTargets },
        "staticJam" to { it.staticJam },
        "revokeClock" to { it.revokeClock },
        "accessClock" to { it.accessClock },
        "egressClock" to { it.egressClock },
        "carrying" to { it.carrying },
        "wallTime" to { it.wallTime },
        "openedAt" to { it.openedAt },
        "houseTexts" to { it.houseTexts },
        "endedAt" to { it.endedAt },
        "heldOver" to { it.heldOver },
        "egressesContained" to { it.egressesContained },
        "residents" to { it.residents },
        "inboxSeed" to { it.inboxSeed },
        "noteSeed" to { it.noteSeed },
    )

    @Test
    fun newRoundForgetsTheRoundOnEveryPhoneAndKeepsTheEvening() = runBlocking {
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
                // One Insider, so a Revoke, a Restrain and a second Revoke are what it takes to end.
                host.link.setInsiders(1)
                suspend fun everyLineIn() {
                    phones.forEach { p ->
                        p.model.typeLine(p.line)
                        check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                    }
                    until("every line reached the house") { host.model.standing.linesIn == phones.size }
                }
                everyLineIn()
                val seatOf = host.link.house.armingInputs().names.associate { it.name to it.seat }
                fun phoneAt(seat: Seat) = phones.single { seatOf.getValue(it.name).index == seat.index }

                air.spreadOut(phones.map { seatOf.getValue(it.name) })
                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261117L, note = {}), "the first round never armed")
                until("every phone holds its role and its order") {
                    phones.all { p -> p.pushed.any { it is RoundMessage.RoleRevealed } && p.pushed.any { it is RoundMessage.WorkOrderIssued } }
                }
                // The lobby the evening holds, before any round has been played in it.
                val tokens = phones.associate { it.name to it.kept.token }
                assertTrue(tokens.values.all { it != null }, "a phone held no seat token after being seated")
                val attached = phones.associate { it.name to it.model.attached }

                // **Round one, to an Insider ending**: a Revoke, a meeting that restrains, a Revoke.
                val insider = house.state.insiderSeats.single()
                val (first, restrained, second) = house.state.livingPlainResidents.take(3)
                suspend fun revoke(target: Seat) {
                    house.advanceTo(house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt ?: Tick(0))
                    house.mint(Event.RevokeArmed(house.now(), insider))
                    val at = house.now()
                    house.mint(Event.ContactMade(at, insider, target))
                    house.mint(Event.ContactMade(at, target, insider))
                    assertTrue(house.state.isRevoked(target), "the fixture's Revoke put nobody out")
                }
                until("every phone is uploading on its seed") { phones.all { it.round.round.uploading } }
                revoke(first)
                until("the first Revoked phone is out") { phoneAt(first).flow.state.outBy == OutBy.Revoked }
                until("the Revoked phone's radio heard it is out") { phoneAt(first).round.round.outOfTheRound }

                val caller = house.state.livingSeats.first { it != insider }
                house.mint(Event.MeetingCalled(house.now(), caller, MeetingTrigger.MeetingCard))
                for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
                if (house.state.meeting?.phase == MeetingPhase.Discussion) house.mint(Event.DiscussionClosed(house.now()))
                for (seat in house.state.livingSeats) {
                    house.heard(Intent.SelectVote(seat, restrained))
                    house.heard(Intent.LockVote(seat))
                }
                if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
                house.mint(Event.TallyHalfwayReached(house.now()))
                house.mint(Event.MeetingClosed(house.now()))
                assertTrue(house.state.restrained.any { it.index == restrained.index }, "the room restrained nobody")
                assertTrue(!house.ended, "the meeting ended the round, so the second Revoke never happens")
                until("the Restrained phone is out") { phoneAt(restrained).flow.state.outBy == OutBy.Restrained }

                revoke(second)
                assertEquals(Winner.Insiders, house.state.outcome?.winner, "the round did not end the Insiders' way")
                until("every phone stands on the Insiders' ending, with the reveal") {
                    phones.all { p -> p.flow.state.screen == ScreenId.WinInsiders && p.flow.state.revealed != null }
                }

                // **What round one wrote**, so what follows is not a check of fields nobody filled.
                val fresh = PanelState(screen = ScreenId.Lobby, houseAttached = true, wallTime = CLOCK)
                val insiderPhone = phoneAt(insider)
                assertEquals(PanelRole.Insider, insiderPhone.flow.state.role)
                assertEquals(OutBy.Revoked, phoneAt(first).flow.state.outBy, "the first Revoked phone lost its cause before the ending")
                assertEquals(OutBy.Restrained, phoneAt(restrained).flow.state.outBy, "the Restrained phone lost its cause before the ending")
                // Every phone was dealt, named, timed and shown the ending; the meeting's fields reached
                // the phones the allowlist sends them to, which is not every phone.
                val wroteAnywhere = mutableSetOf<String>()
                for (p in phones) {
                    val wrote = written(p.flow.state, fresh)
                    wroteAnywhere += wrote
                    for (field in listOf("order", "roster", "seats", "revealed", "endedAt", "openedAt", "revokeClock")) {
                        assertTrue(field in wrote, "${p.name}: round one never wrote '$field', so its reset proves nothing: $wrote")
                    }
                }
                for (field in listOf("role", "outBy", "caller", "tally", "meetingPhase", "residents", "egressesContained")) {
                    assertTrue(field in wroteAnywhere, "no phone was written '$field' in round one, so its reset proves nothing: $wroteAnywhere")
                }

                // **NEW ROUND, pressed on the host's ending screen.**
                host.flow.newRound()
                until("every phone is back in the lobby") { phones.all { it.flow.state.screen == ScreenId.Lobby } }
                until("every name came back to the desk") {
                    host.link.house.armingInputs().names.map { it.name }.toSet() == names.toSet()
                }

                for (p in phones) {
                    val who = if (p === host) "the host (${p.name})" else "the guest ${p.name}"
                    // **The whole panel, at once**: every round field at its default, the evening's kept.
                    assertEquals(
                        fresh, p.flow.state,
                        "$who carried round one into the lobby: ${written(p.flow.state, fresh)}",
                    )
                    // The models beside the panel.
                    assertEquals(MeetingCounts.UNSAID, p.flow.meeting.counts, "$who kept round one's meeting counts")
                    assertTrue(!p.flow.meeting.checkedIn && p.flow.meeting.choice == null && !p.flow.meeting.locked, "$who kept round one's ballot")
                    assertEquals(emptyList(), p.flow.meeting.names, "$who kept round one's roster on its ballot")
                    assertNull(p.flow.subroutines.standingAt, "$who still stands at round one's card")
                    assertEquals(emptyList(), p.flow.notifications.standing, "$who kept a house text from round one under the clock")
                    // The app's round half.
                    assertEquals(emptyList(), p.round.order, "$who's round client kept round one's order")
                    assertNull(p.round.standingAt, "$who's round client kept round one's card")
                    assertEquals(0L, p.round.round.held, "$who's inbox kept round one's sequence")
                    assertTrue(!p.round.round.uploading, "$who is still uploading for round one")
                    assertTrue(!p.round.round.outOfTheRound, "$who's radio still says it is out of the round")
                    assertEquals(0, p.round.round.owing, "$who still owes a report from round one")
                    assertTrue(p.radio.advertised == null && !p.radio.scanning, "$who's radio is still on after the round")
                    // The evening: the name, the seat, the home, the house.
                    assertEquals(p.name, p.model.residentName, "$who lost its name")
                    assertEquals(tokens[p.name], p.kept.token, "$who lost its seat token")
                    assertEquals(attached[p.name], p.model.attached, "$who lost the home it is in")
                    assertEquals(p === host, p.model.hosting, "$who changed whether it hosts")
                }
                assertEquals(phones.size, host.model.standing.joined, "a seat left the lobby with the round")

                // **And the second round's ARMED is nobody's last round.**
                everyLineIn()
                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261117L, note = {}), "the second round never armed")
                until("every phone stands on PERIMETER ARMED") { phones.all { it.flow.state.screen in Flow.armedOn } }
                for (p in phones) {
                    assertNull(p.flow.state.outBy, "${p.name}'s second ARMED is drawn out of the round")
                    assertNull(p.flow.state.tally, "${p.name}'s second ARMED holds round one's tally")
                    assertNull(p.flow.state.revealed, "${p.name}'s second ARMED holds round one's reveal")
                }
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
