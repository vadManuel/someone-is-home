package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
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
import home.someoneshome.ui.WallTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A round in progress cannot be joined: a phone not seated at LIGHTS OUT is refused at the door,
 * and a seat in the round that drops and relaunches still comes back** (D-287, the owner's ruling 9c;
 * it replaces D-273's waiting lobby, raised in D-267).
 *
 * A token returns its seat at any time (D-225). The 5-seat hunt (`hunt.sh --relaunch-guest 2`,
 * `/tmp/someones-home-hunt/t1-r5b`) stopped a guest at round one's ending and relaunched it after round
 * two armed with four: it was seated, and its lobby read 5 JOINED beside a round of four. D-273 made it
 * a waiting lobby, which stood lit for the whole dark round; the owner ruled it refused instead.
 *
 * Five phones over real loopback sockets, built as `CheatRoot` builds them (the real
 * [LobbyOverTransport] with `onRoundEnded` wired to `FlowModel.newRound` and `onRoundInProgress` to
 * `FlowModel.roundInProgress`, a real [RoundClient], the real [RoundAuthority]). Round one is played
 * to its ending by all five; one guest is stopped there; NEW ROUND, and round two arms with the four
 * left; the guest is relaunched (a new process, the same stored token) mid-round two and refused, a
 * stranger is refused, and a round guest drops, relaunches and is seated again; round two ends, the
 * guest taps the home again and waits on the host, NEW ROUND, and round three arms with all five.
 */
class RoundUnderWayTest {

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

    /** One process. A relaunch is a new one of these over the same [kept] token. */
    private class Phone(
        scope: CoroutineScope,
        val name: String,
        spot: Seat,
        air: SimulatedAir,
        val kept: Kept = Kept(),
        house: () -> RoundAuthority?,
    ) {
        val bonjour = Bonjour()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(Still)
        private val seam = DeferredRoundSeam()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, rangingCapacity = { SimulatedAir.BENCH_CAPACITY }, onEvent = { log += it },
            seat = kept,
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = seam,
            onRoundEnded = { flow.newRound() },
            // As `CheatRoot` wires it (D-287): refused at the door, walked to JOIN.
            onRoundInProgress = { flow.roundInProgress() },
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
        val round = RoundClient(scope, flow, LineWatch {}, buzzer, air.radioFor(spot), wake, ranging = air.rangingFor(spot, SimulatedAir.BENCH_CAPACITY), note = { log += it }).also { made ->
            client = made
            made.onMessage = { pushed += it }
            seam.becomes(made)
        }

        fun lineFor(round: Int) = "$name's own secret for round $round"
    }

    private companion object {
        /** The join screen's refusal, the waiting screen's two lines (D-287). */
        val ROUND_IN_PROGRESS = "${LobbyModel.ROUND_IN_PROGRESS}\n${LobbyModel.YOU_JOIN_THE_NEXT_ONE}"

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
     * **GUARD (D-287): a guest relaunched mid-round two whose seat sat the round out, and a stranger,
     * are refused at JOIN and land on the join screen saying ROUND IN PROGRESS / YOU JOIN THE NEXT
     * ONE, seated nowhere and pushed nothing; a round guest that drops and relaunches is seated again
     * and handed its round; after the ending the refused guest is seated, and is armed into round
     * three.**
     */
    @Test
    fun aRoundInProgressRefusesAPhoneNotInItAndReadmitsOneThatIs() = runBlocking<Unit> {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val alive = mutableListOf<Phone>()
        val everyProcess = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val said = mutableListOf<String>()
                fun phone(name: String, spot: Seat, kept: Kept = Kept()) =
                    Phone(scope, name, spot, air, kept) { authority }.also { everyProcess += it; alive += it }
                val host = phone(names[0], Seat(0))
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                val house = authority
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                val dial = NearbyHome(home.name, "127.0.0.1", port)
                names.drop(1).forEachIndexed { i, name ->
                    phone(name, Seat(i + 1)).model.attachTo(dial)
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                host.link.setInsiders(1)

                suspend fun everyLineIn(round: Int) {
                    alive.forEach { p ->
                        p.model.typeLine(p.lineFor(round))
                        check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                    }
                    until("every line reached the house", detail = { "${host.model.standing}" }) {
                        host.model.standing.linesIn == alive.size && host.model.standing.joined == alive.size
                    }
                }
                suspend fun arm(round: Int) {
                    air.spreadOut(host.link.house.armingInputs().seats)
                    val before = alive.associateWith { it.pushed.size }
                    assertTrue(armAtLightsOut(house, hosting = true, seed = SEED + round, note = {}), "round $round never armed: $said")
                    until("every phone in round $round holds its role and left the lobby", detail = { alive.joinToString { "${it.name} ${it.flow.state.screen}" } }) {
                        alive.all { p ->
                            p.flow.state.screen != ScreenId.Lobby &&
                                p.pushed.drop(before.getValue(p)).any { it is RoundMessage.RoleRevealed }
                        }
                    }
                }
                // Revoke after Revoke until the round ends (parity). The Insider is never a target.
                suspend fun playToTheEnd(round: Int) {
                    val insider = house.state.insiderSeats.first()
                    while (house.state.outcome == null) {
                        val target = house.state.livingPlainResidents.first()
                        house.advanceTo(house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt ?: Tick(0))
                        house.mint(Event.RevokeArmed(house.now(), insider))
                        val at = house.now()
                        house.mint(Event.ContactMade(at, insider, target))
                        house.mint(Event.ContactMade(at, target, insider))
                    }
                    until("every phone in round $round stands on its ending", detail = { alive.joinToString { "${it.name} ${it.flow.state.screen}" } }) {
                        alive.all { it.flow.state.screen in setOf(ScreenId.WinInsiders, ScreenId.WinResidents) }
                    }
                }
                suspend fun newRound() {
                    host.flow.newRound()
                    until("every phone is back in the lobby") { alive.all { it.flow.state.screen == ScreenId.Lobby } }
                }

                // **Round one, all five, to its ending.**
                everyLineIn(1)
                arm(1)
                assertEquals(5, house.state.seats.size)
                playToTheEnd(1)

                // **The guest is stopped on the ending**: its socket closes and its process is gone.
                val stopped = alive.last()
                alive.remove(stopped)
                stopped.link.leave()
                until("the house lost the stopped guest") { host.link.house.armingInputs().seats.size == 4 }

                // **NEW ROUND, and round two arms with the four left.**
                newRound()
                everyLineIn(2)
                arm(2)
                assertEquals(4, house.state.seats.size, "round two did not arm with four")
                val roundTwoNames = house.state.seats.sortedBy { it.index }.map { house.state.nameOf(it).orEmpty() }

                // **The guest is relaunched mid-round two**: a new process, the same stored token. Its
                // seat sat the round out, so it was not seated at LIGHTS OUT: it is refused at the door
                // (D-287), and lands on JOIN with the waiting screen's two lines, seated nowhere.
                val back = phone(stopped.name, Seat(4), kept = stopped.kept)
                alive.remove(back)
                back.model.attachTo(dial)
                // Where the join row's tap walks a phone, as the bench walks it.
                back.flow.go(ScreenId.Lobby)
                until("the relaunched guest was turned away at the door", detail = { "${back.flow.state.screen} ${back.model.refusal} ${back.log.takeLast(5)}" }) {
                    back.flow.state.screen == ScreenId.Join && back.model.refusal != null
                }
                assertEquals(ROUND_IN_PROGRESS, back.model.refusal, "the refused guest was not told the round is in progress")
                assertTrue(back.log.any { it == "REFUSED BY THE HOUSE — RoundLocked" }, "the refusal was not the door's: ${back.log}")
                assertEquals(null, back.model.attached, "the refused guest still holds the house")
                assertEquals(0, back.model.standing.joined, "the refused guest holds a lobby")
                assertFalse(back.model.waitingForNextRound, "the refused guest stands on a waiting lobby")
                assertEquals(4, host.link.house.armingInputs().seats.size, "the house seated the refused guest on its desk")
                assertTrue(host.log.any { it == "A CONNECTION WAS REFUSED — RoundLocked" }, "the house recorded no refusal: ${host.log.takeLast(5)}")
                assertEquals(stopped.kept.token, back.kept.token, "the refusal cost the guest its seat's token")

                // **A stranger, never seated anywhere, is refused the same way.**
                val stranger = phone("FREYA", Seat(5))
                alive.remove(stranger)
                stranger.model.attachTo(dial)
                stranger.flow.go(ScreenId.Lobby)
                until("the stranger was turned away at the door", detail = { "${stranger.flow.state.screen} ${stranger.log.takeLast(5)}" }) {
                    stranger.flow.state.screen == ScreenId.Join && stranger.model.refusal == ROUND_IN_PROGRESS
                }
                assertEquals(null, stranger.kept.token, "the stranger was handed a seat's token")

                // **A seat in the round drops and relaunches mid-round: that is a rejoin, and it works.**
                val dropped = alive[1]
                dropped.link.leave()
                until("the house lost the dropped guest") { host.link.house.armingInputs().seats.size == 3 }
                val rejoined = phone(dropped.name, Seat(1), kept = dropped.kept)
                alive.remove(rejoined)
                alive[1] = rejoined
                rejoined.model.attachTo(dial)
                rejoined.flow.go(ScreenId.Lobby)
                until("the relaunched round guest was seated again", detail = { "${rejoined.log.takeLast(5)}" }) {
                    host.link.house.armingInputs().seats.size == 4 && rejoined.model.standing.joined == 4
                }
                assertEquals(null, rejoined.model.refusal, "a seat in the round was refused its own chair")
                assertFalse(rejoined.model.waitingForNextRound, "a seat in the round was told to wait for the next one")
                withTimeout(30_000) {
                    while (!(rejoined.pushed.any { it is RoundMessage.RoleRevealed } && rejoined.flow.state.screen != ScreenId.Lobby)) {
                        alive.forEach { it.round.round.beat() }
                        delay(150)
                    }
                }

                // A few beats: anything of round two meant for the refused phones would have arrived.
                repeat(3) {
                    (alive + back + stranger).forEach { it.round.round.beat() }
                    delay(200)
                }
                assertTrue(back.pushed.none { it is RoundMessage.RoleRevealed || it is RoundMessage.RosterIssued }, "the refused guest was pushed round two: ${back.pushed}")
                assertTrue(stranger.pushed.isEmpty(), "the refused stranger was pushed round two: ${stranger.pushed}")
                // The phones in the round are told the round's count and are not told to wait.
                until("the host's standing counts the round", detail = { "${host.model.standing}" }) {
                    host.model.standing.joined == 4 && !host.model.waitingForNextRound
                }

                // **Round two ends: the door opens, and the guest taps the home again.** The room is
                // in the light; it waits for NEW ROUND on WAITING FOR THE HOST (D-274).
                playToTheEnd(2)
                back.model.attachTo(dial)
                back.flow.go(ScreenId.Lobby)
                until("the guest was seated at the round's ending", detail = { "${back.model.standing} ${back.log.takeLast(5)}" }) {
                    back.model.waitingForNextRound && back.model.standing.roundOver
                }
                assertEquals(null, back.model.refusal, "the guest was refused at the ending")
                assertEquals(roundTwoNames, back.model.residents, "the waiting guest's lobby lists other than the round's names")
                assertFalse(host.model.standing.roundOver, "a phone in the round was sent the waiting phone's flag")
                alive += back
                newRound()
                until("the guest was seated in the next round's lobby", detail = { "${back.model.standing}" }) {
                    !back.model.waitingForNextRound && back.model.standing.joined == 5
                }
                everyLineIn(3)
                val pushedBefore = back.pushed.size
                arm(3)
                assertEquals(5, house.state.seats.size, "round three did not arm with the guest")
                val roundThree = back.pushed.drop(pushedBefore)
                assertTrue(roundThree.any { it is RoundMessage.RoleRevealed }, "the guest was not told its role in round three: $roundThree")
                assertEquals(5, back.flow.state.roster?.size, "the guest holds no roster of five in round three")
            }.await()
        } finally {
            everyProcess.forEach { it.link.leave() }
            everyProcess.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
