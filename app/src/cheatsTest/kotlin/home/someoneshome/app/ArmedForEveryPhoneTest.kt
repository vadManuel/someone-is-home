package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.NotificationKind
import home.someoneshome.ui.Notifications
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenGraph
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
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
 * **PERIMETER ARMED on every phone, from the house, over real sockets** (D-234).
 *
 * The bug: on two real iPhones (2026-11-16), after LIGHTS OUT the host's phone said PERIMETER ARMED
 * and the guests' did not. The host walked itself there on the house's yes (D-226), a call made on
 * the host's phone alone; the house's `OpeningMessage` raised a banner, so each guest went from the
 * lobby straight to the springboard. Every earlier arming test either held one phone's `FlowModel`
 * or started its phones on the springboard, so none of them could see a guest's first frame.
 *
 * Each phone here is built the way `CheatRoot` builds it: a real [FlowModel] holding a real
 * [LobbyModel], standing on the lobby; the link promised a [DeferredRoundSeam] that becomes a real
 * [RoundClient] over that same flow; and the host's `onLightsOut` reaching [armAtLightsOut] exactly
 * as `CheatRoot` wires it (the seam's walk not called). LIGHTS OUT is `flow.lightsOut()`, the
 * lambda the host's hold calls. What is asserted is each phone's screen.
 *
 * Faked: Bonjour (the port is handed over directly) and the contact radio (a [SimulatedAir]); the
 * sockets, the desk, the link and the authority are the real ones, on loopback.
 */
class ArmedForEveryPhoneTest {

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

    /** One phone, wired as `CheatRoot` wires it, standing on the lobby. */
    private class Phone(
        scope: CoroutineScope,
        val name: String,
        radioSpot: Seat,
        air: SimulatedAir,
        house: () -> RoundAuthority?,
        said: MutableList<String>,
    ) {
        val bonjour = Bonjour()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        private val seam = DeferredRoundSeam()

        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onCardRead = HouseDoors(house)::read,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = seam, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        /**
         * `CheatRoot`'s LIGHTS OUT, exactly: the press reaches the house; the seam's walk is not
         * called. And `CheatRoot`'s two ways up the socket, a control's intent and the Egress tile,
         * reach this phone's [round] as they do there, where [RoundClient] logs each as PRESSED.
         */
        val flow: FlowModel = FlowModel(
            PanelState(screen = ScreenId.Lobby),
            lobby = model,
            send = { intent -> round.act(intent) },
            fireEgress = { round.fireEgress() },
            onLightsOut = { _ ->
                val rules = house()
                scope.launch {
                    if (rules == null) return@launch
                    armAtLightsOut(rules, hosting = model.hosting, seed = SEED, note = { said += it })
                }
            },
        )

        /** Every push this phone was sent, decoded, in arrival order. */
        val pushed = mutableListOf<RoundMessage>()

        val round = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY),
        ) { log += it }.also { made ->
            made.onMessage = { pushed += it }
            seam.becomes(made)
        }
    }

    private class Room(val host: Phone, val guests: List<Phone>, val house: RoundAuthority, val said: List<String>) {
        val phones: List<Phone> get() = listOf(host) + guests
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    /** A host and [guests] in a lobby, every line in, nobody has pressed anything yet. */
    private fun lobby(guests: List<String>, body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                val said = mutableListOf<String>()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air, { authority }, said).also { phones += it }
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                val seated = guests.mapIndexed { i, name ->
                    Phone(scope, name, Seat(i + 1), air, { authority }, said).also { guest ->
                        phones += guest
                        guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                        until("$name was seated") { host.model.standing.joined == i + 2 }
                    }
                }
                phones.forEachIndexed { i, p ->
                    p.model.typeLine("line $i")
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                scope.body(Room(host, seated, authority, said))
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /**
     * **GUARD (D-234): the host's LIGHTS OUT puts PERIMETER ARMED on every guest's phone, by the
     * house's push, and on the host's by the same push.**
     *
     * One guest is on the one-line screen when the lights go out (it reopened it to hand its line
     * over again), which is the other place a seated phone stands.
     *
     * **And it lands plain ARMED, undimmed, on every phone** (D-293): the opening banner and its
     * dim wait for each phone's own CONTINUE, whatever the role the burst then reveals.
     */
    @Test
    fun lightsOutWalksEveryPhoneToArmedByTheHousesPush() = lobby(listOf("ROSE", "DANI")) { room ->
        room.guests.last().flow.go(ScreenId.Secret)
        until("every lobby read READY") { room.host.model.readyToArm }

        room.host.flow.lightsOut()

        until("the house armed", { "said=${room.said}" }) { room.house.armed }
        // The guests first: theirs is the screen the owner found missing.
        for (phone in room.guests + room.host) {
            until(
                "${phone.name}'s phone reached PERIMETER ARMED",
                { "screen=${phone.flow.state.screen} pushed=${phone.pushed.map { it::class.simpleName }}" },
            ) { phone.flow.state.screen == ScreenId.Armed }
            assertTrue(
                phone.pushed.any { it is RoundMessage.OpeningMessage },
                "${phone.name} reached ARMED with no opening push from the house",
            )
        }
        // The burst after the opening (the roster, the role, the seed, the work order) moves nobody
        // off it: ARMED is the house's screen, and the next thing that moves a phone is its thumb.
        delay(SETTLE_MILLIS)
        for (phone in room.phones) {
            assertEquals(ScreenId.Armed, phone.flow.state.screen, "${phone.name} was walked off ARMED by the rest of the burst")
            assertEquals(null, PanelVals(phone.flow.state).arrival, "${phone.name}'s phone drew a banner before its CONTINUE")
            assertFalse(Notifications.dims(phone.flow.state.screen), "${phone.name}'s phone dimmed before its CONTINUE")
        }
    }

    /**
     * **GUARD (D-293): each phone's CONTINUE raises the opening banner and its dim on that phone, at
     * its tap, and sends nothing up its socket.**
     *
     * The phones tap one at a time, over the real link, with the house's clock running. After each
     * tap the phone that tapped stands under the opening banner, dimmed, and every phone that has not
     * tapped is still on ARMED, undimmed: the banner is no longer one moment for the whole house.
     * Neither a control's intent nor the Egress tile reached the round client on any phone
     * (`RoundClient` logs both as PRESSED), so the house heard nothing it could time.
     */
    @Test
    fun eachPhonesContinueRaisesTheBannerOnThatPhoneAlone() = lobby(listOf("ROSE", "DANI")) { room ->
        until("every lobby read READY") { room.host.model.readyToArm }
        room.host.flow.lightsOut()
        for (phone in room.phones) {
            until("${phone.name} reached ARMED", { "screen=${phone.flow.state.screen}" }) { phone.flow.state.screen == ScreenId.Armed }
        }
        delay(SETTLE_MILLIS)
        val before = room.phones.associateWith { it.log.size }

        // ARMED's one control, which is CONTINUE (`ScreenGraph`; `ScreenGraphTest` taps it on the
        // rendered screen and holds the graph to what it does).
        val continueTo = ScreenGraph.exitsOf(ScreenId.Armed).single()
        val tapped = mutableListOf<Phone>()
        for (phone in listOf(room.guests.first(), room.host, room.guests.last())) {
            phone.flow.go(continueTo)
            tapped += phone
            delay(SETTLE_MILLIS)
            for (other in room.phones) {
                val screen = other.flow.state.screen
                if (other in tapped) {
                    assertEquals(ScreenId.Notify, screen, "${other.name}'s CONTINUE did not raise the opening banner")
                    assertEquals(
                        NotificationKind.Opening, PanelVals(other.flow.state).arrival?.notification?.kind,
                        "${other.name}'s banner is not the house's opening text",
                    )
                    assertTrue(Notifications.dims(screen), "${other.name}'s opening banner did not dim (D-118)")
                } else {
                    assertEquals(ScreenId.Armed, screen, "${phone.name}'s CONTINUE moved ${other.name}")
                    assertFalse(Notifications.dims(screen), "${phone.name}'s CONTINUE dimmed ${other.name}")
                }
            }
        }
        for (phone in room.phones) {
            val after = phone.log.drop(before.getValue(phone))
            assertEquals(
                emptyList(), after.filter { "PRESSED" in it },
                "${phone.name}'s CONTINUE went up the socket",
            )
        }
    }

    /**
     * **And a press the house refuses moves nobody**, the host included (D-226): a lobby below
     * D-128's floor, two phones, every line in.
     */
    @Test
    fun aRefusedLightsOutLeavesEveryPhoneOnTheLobby() = lobby(listOf("ROSE")) { room ->
        room.host.flow.lightsOut()

        until("the house said it would not arm", { "said=${room.said}" }) { room.said.any { it.startsWith("WILL NOT ARM") } }
        delay(SETTLE_MILLIS)
        for (phone in room.phones) {
            assertEquals(ScreenId.Lobby, phone.flow.state.screen, "${phone.name} left the lobby on a refused press")
            assertTrue(phone.pushed.none { it is RoundMessage.OpeningMessage }, "${phone.name} was sent an opening")
        }
    }

    private companion object {
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 400L
    }
}
