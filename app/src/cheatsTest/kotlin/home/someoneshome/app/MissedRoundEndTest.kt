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
import home.someoneshome.ui.NearbyHome
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
import kotlin.test.assertTrue

/**
 * **A phone that was off the air when the house ended a round comes back to the house's next round,
 * and nothing of the last one comes with it** (V1 of the second review, D-267).
 *
 * `LobbyBody.RoundEnded` is sent once, to the phones that are connected at that moment, and is never
 * said again. A guest whose network went during round one came back after NEW ROUND still on round
 * one: its screen on the round, its inbox holding round one's sequence (so round two's opening
 * pushes, numbered from 1, were dropped as duplicates: no role, no seed, no roster), its radio on
 * round one's seed, a report it owed from round one still going up, and **the line it typed for
 * round one sent back up at its seating and filed by the desk as this round's**, so the house could
 * arm round two with it.
 *
 * Five phones over real loopback sockets, built as `CheatRoot` builds them (the real
 * [LobbyOverTransport] with `onRoundEnded` wired to `FlowModel.newRound`, a real [RoundClient] over a
 * [SimulatedAir] radio, the real [RoundAuthority]). One guest reaches the house through [Unplug], a
 * loopback relay the test takes away: its network goes mid-round, while the phone is locked (it
 * owes the house a report of that), the round is played to its ending and NEW ROUND is pressed
 * without it, and then its network comes back and the phone is unlocked.
 */
class MissedRoundEndTest {

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
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(Still)
        val radio = air.radioFor(spot)
        private val seam = DeferredRoundSeam()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, onEvent = { log += it },
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
        val round = RoundClient(scope, flow, LineWatch {}, buzzer, radio, wake, note = { log += it }).also { made ->
            client = made
            made.onMessage = { pushed += it }
            seam.becomes(made)
        }

        /** A different line every round, so one round's cannot pass for another's. */
        fun lineFor(round: Int) = "$name's own secret for round $round"
    }

    private companion object {
        val CLOCK = WallTime(hour = 21, minute = 7, weekday = 6, day = 16, month = 8)
        const val SEED = 20261117L
    }

    private val home = SavedHomesModel.sample().homes.first()
    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")

    private suspend fun until(what: String, withinMillis: Long = 30_000, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(withinMillis) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    /**
     * **GUARD (V1, D-267): a guest off the air from mid-round one until after NEW ROUND is walked to the
     * lobby by the house's standing, sends no line from round one, owes nothing from round one, and
     * is armed into round two with its role, its seed and the roster.**
     */
    @Test
    fun aPhoneThatMissedTheRoundsEndJoinsTheNextRoundHoldingNothingOfTheLast() = runBlocking<Unit> {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        var relay: Unplug? = null
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val said = mutableListOf<String>()
                val host = Phone(scope, names[0], Seat(0), air) { authority }.also { phones += it }
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                val house = authority
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                // Every guest but the last dials the house; the last dials through the relay.
                val unplug = Unplug(scope, port).also { relay = it }
                unplug.plug()
                names.drop(1).forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    val dial = if (i == names.size - 2) unplug.port else port
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", dial))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                host.link.setInsiders(1)
                suspend fun everyLineIn(round: Int) {
                    phones.forEach { p ->
                        p.model.typeLine(p.lineFor(round))
                        check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                    }
                    until("every line reached the house") { host.model.standing.linesIn == phones.size }
                }
                everyLineIn(1)
                val seatOf = host.link.house.armingInputs().names.associate { it.name to it.seat }
                fun phoneAt(seat: Seat) = phones.single { seatOf.getValue(it.name).index == seat.index }

                air.spreadOut(phones.map { seatOf.getValue(it.name) })
                assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = {}), "round one never armed: $said")
                until("every phone holds its role and its order") {
                    phones.all { p -> p.pushed.any { it is RoundMessage.RoleRevealed } && p.pushed.any { it is RoundMessage.WorkOrderIssued } }
                }
                until("every phone is uploading on its seed") { phones.all { it.round.round.uploading } }

                // The guest behind the relay, and who plays round one around it. Its seat is played
                // by the house as it would be: its check-in and its ballot are put to the rules.
                val away = phones.last()
                val awaySeat = seatOf.getValue(away.name)
                val insider = house.state.insiderSeats.single()
                val residents = house.state.livingPlainResidents.filter { it.index != awaySeat.index }
                check(awaySeat !in house.state.insiderSeats) { "the seed drew the relay's guest as the Insider; pick another seed" }
                val (first, restrained, second) = residents.take(3)

                // **Round one, part way: a meeting, so the guest holds a long stream.**
                house.mint(Event.MeetingCalled(house.now(), house.state.livingSeats.first { it != insider }, MeetingTrigger.MeetingCard))
                for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
                if (house.state.meeting?.phase == MeetingPhase.Discussion) house.mint(Event.DiscussionClosed(house.now()))
                for (seat in house.state.livingSeats) {
                    house.heard(Intent.SelectVote(seat, restrained))
                    house.heard(Intent.LockVote(seat))
                }
                if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
                house.mint(Event.TallyHalfwayReached(house.now()))
                house.mint(Event.MeetingClosed(house.now()))
                until("the guest heard the meeting end") { away.pushed.any { it is RoundMessage.MeetingEnded } }
                val heldInRoundOne = away.round.round.held
                assertTrue(heldInRoundOne >= 20, "the guest holds only $heldInRoundOne of round one, so round two's opening could not hide under it")

                // **The guest locks its phone, and its network goes with it.** It owes the house the
                // leave (D-187), which it cannot send.
                away.link.backgrounded()
                assertEquals(1, away.round.round.owing, "the guest did not mark its leave")
                unplug.unplug()
                // The desk, not the standing: mid-round the standing counts the round (D-273).
                until("the house lost the guest") { host.link.house.armingInputs().seats.size == phones.size - 1 }

                // **Round one ends without it**: Revoke, Revoke, and parity.
                suspend fun revoke(target: Seat) {
                    house.advanceTo(house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt ?: Tick(0))
                    house.mint(Event.RevokeArmed(house.now(), insider))
                    val at = house.now()
                    house.mint(Event.ContactMade(at, insider, target))
                    house.mint(Event.ContactMade(at, target, insider))
                }
                revoke(first)
                revoke(second)
                assertEquals(Winner.Insiders, house.state.outcome?.winner, "round one did not end: ${house.state.outcome}")
                val there = phones.filter { it !== away }
                until("every phone still connected stands on the ending") { there.all { it.flow.state.screen == ScreenId.WinInsiders } }
                assertTrue(away.pushed.none { it is RoundMessage.RoundEnded }, "the guest heard the ending, so it was not away")

                // **NEW ROUND, while the guest is still off the air.**
                host.flow.newRound()
                until("every phone still connected is back in the lobby") { there.all { it.flow.state.screen == ScreenId.Lobby } }
                assertEquals(0, host.model.standing.linesIn, "the desk kept a line across NEW ROUND")
                val pushedBefore = away.pushed.size

                // **The network comes back, and the guest unlocks its phone.**
                unplug.plug()
                away.link.foregrounded()
                until("the guest came back to its seat", detail = { away.log.takeLast(5).toString() }) {
                    host.model.standing.joined == phones.size
                }
                until("the guest was walked to the lobby", detail = { "on ${away.flow.state.screen} · ${away.log.takeLast(5)}" }) {
                    away.flow.state.screen == ScreenId.Lobby
                }
                // A few beats: a report still owed, or a stale line, would have gone up by now.
                repeat(3) {
                    phones.forEach { it.round.round.beat() }
                    delay(200)
                }
                assertEquals(0, host.model.standing.linesIn, "the guest's round-one line was filed as round two's")
                assertEquals(0, away.round.round.owing, "the guest still owes a report from round one")
                assertTrue(!away.round.round.uploading, "the guest's radio is still uploading on round one's seed")
                assertEquals(
                    PanelState(screen = ScreenId.Lobby, houseAttached = true, wallTime = CLOCK), away.flow.state,
                    "the guest's panel kept something of round one",
                )
                assertEquals("", away.model.line.text, "the guest's round-one line is still typed on its lobby")
                assertEquals(0L, away.round.round.held, "the guest's inbox kept round one's sequence")

                // **Round two**, with a line from everybody, the guest's typed for round two.
                everyLineIn(2)
                assertTrue(armAtLightsOut(house, hosting = true, seed = SEED + 1, note = {}), "round two never armed: $said")
                until("every phone stands on PERIMETER ARMED", detail = { phones.joinToString { "${it.name} ${it.flow.state.screen}" } }) {
                    phones.all { it.flow.state.screen in Flow.armedOn }
                }
                val roundTwo = away.pushed.drop(pushedBefore)
                assertTrue(roundTwo.any { it is RoundMessage.RoleRevealed }, "the guest was not told its role in round two: $roundTwo")
                assertTrue(roundTwo.any { it is RoundMessage.ContactSeedIssued }, "the guest was not given round two's seed")
                assertTrue(roundTwo.any { it is RoundMessage.RosterIssued }, "the guest was not given round two's roster")
                until("the guest is uploading on round two's seed") { away.round.round.uploading }
                assertEquals(5, away.flow.state.roster?.size, "the guest's panel holds no roster in round two")

                // **Nothing of round one names it in round two**: the first meeting's notice names
                // nobody (D-187), on the guest's phone as on every other.
                repeat(3) {
                    phones.forEach { it.round.round.beat() }
                    delay(200)
                }
                assertEquals(0, away.round.round.owing, "the guest owes a report in round two it never made")
                house.mint(Event.MeetingCalled(house.now(), house.state.livingSeats.first(), MeetingTrigger.MeetingCard))
                for (seat in house.state.seats) house.heard(Intent.CheckIn(seat))
                until("the guest was handed round two's notice") {
                    away.pushed.drop(pushedBefore).any { it is RoundMessage.NoticeIssued }
                }
                val notice = away.pushed.filterIsInstance<RoundMessage.NoticeIssued>().last()
                assertEquals(emptyList(), notice.unreachable, "round two's first notice names a seat for something done in round one: $said")
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            relay?.unplug()
            job.cancel()
        }
    }
}
