package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
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
 * **The host's lobby settings reach every guest's lobby, and page 2's EGRESS line follows the
 * Insider count, over the wire the phones use** (the owner's 2026-11-21 rulings, D-320).
 *
 * Phones over real loopback sockets, built as `CheatRoot` builds them (the real
 * [LobbyOverTransport], a real [RoundClient] over a [SimulatedAir] radio, the real
 * [RoundAuthority]); `RoundBoundaryTest`'s rig.
 *
 * 1. **Guests draw the host's numbers.** D-317 found that a guest's VOTING, DISCUSSION, EGRESS
 *    TIMER and REVOKE COOLDOWN were its own phone's, because nothing went down the lobby wire. The
 *    host presses each row; every guest's row reads the host's choice.
 * 2. **The EGRESS line.** A round of five holds one Insider, and the Insider's page 2 drops *SHARED
 *    WITH THE OTHER INSIDER*; a round of seven the host set to two keeps it on both Insiders' pages.
 *    No Resident's page 2 moves.
 */
class LobbySettingsWireTest {

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
            scope, onEvent = {},
            seat = kept,
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onCardRead = HouseDoors(house)::read,
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

        fun rows(): List<String> =
            listOf(model.voteWindowLabel, model.discussionLabel, model.egressTimerLabel, model.revokeCooldownLabel)

        /** Page 2's EGRESS tile, as this phone draws it. */
        fun egressLine(): String = PanelVals(flow.state.copy(screen = ScreenId.Page2)).secondSub
    }

    private companion object {
        val CLOCK = WallTime(hour = 21, minute = 7, weekday = 6, day = 16, month = 8)
        const val SEED = 20261121L
    }

    private val home = SavedHomesModel.sample().homes.first()
    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO", "FARAH", "GIDEON")

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    /**
     * A lobby of [party] phones over loopback, the host's INSIDERS set to [insiders] (null is
     * UNKNOWN), [before] run on it, every line in, and the round armed. Returns the phones.
     */
    private fun evening(party: Int, insiders: Int?, before: suspend (host: Phone, guests: List<Phone>) -> Unit = { _, _ -> }, after: suspend (List<Phone>) -> Unit) = runBlocking {
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
                names.take(party).drop(1).forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                host.link.setInsiders(insiders)
                until("every phone's lobby holds the host's INSIDERS", { phones.joinToString { "${it.name} ${it.model.standing.insiders}" } }) {
                    phones.all { it.model.standing.insiders == insiders }
                }
                before(host, phones.drop(1))
                phones.forEach { p ->
                    p.model.typeLine(p.line)
                    check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                air.spreadOut(host.link.house.armingInputs().seats)
                assertTrue(armAtLightsOut(house, hosting = true, seed = SEED, note = {}), "the round never armed")
                until("every phone holds its role and its roster", { phones.joinToString { "${it.name} ${it.flow.state.seats}" } }) {
                    phones.all { p -> p.pushed.any { it is RoundMessage.RoleRevealed } && p.flow.state.seats == party }
                }
                after(phones)
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            job.cancel()
        }
    }

    /**
     * **GUARD: every guest's lobby draws the host's VOTING, DISCUSSION, EGRESS TIMER and REVOKE
     * COOLDOWN** (D-320, closing the gap D-317 found). Before a press, every phone reads the
     * defaults; after one press on each row, every phone reads the host's choice, off the standing.
     */
    @Test
    fun everyGuestsLobbyDrawsTheHostsNumbers() = evening(
        party = 5,
        insiders = null,
        before = { host, guests ->
            val defaults = listOf("45S", "90S", "120S", "60S")
            for (p in guests + host) assertEquals(defaults, p.rows(), "${p.name}'s lobby did not open on the defaults")
            host.model.cycleVoteWindow()
            host.model.cycleDiscussion()
            host.model.cycleEgressTimer()
            host.model.cycleRevokeCooldown()
            val chosen = listOf("60S", "120S", "150S", "90S")
            until("every guest drew the host's four numbers", { guests.joinToString { "${it.name} ${it.rows()}" } }) {
                guests.all { it.rows() == chosen }
            }
            assertEquals(chosen, host.rows(), "the host's own rows did not draw what the house holds")
            // A guest's press reaches nothing: its rows are readings of the host's.
            guests.first().model.cycleDiscussion()
            delay(200)
            assertEquals(chosen, guests.first().rows(), "a guest's press moved a row")
            assertEquals(120, host.link.house.armingInputs().discussionSeconds, "a guest's press reached the desk")
        },
        after = { phones ->
            val host = phones.first()
            val armed = host.link.house.armingInputs()
            assertEquals(listOf(60, 120, 150, 90), listOf(armed.voteWindowSeconds, armed.discussionSeconds, armed.egressTimerSeconds, armed.revokeCooldownSeconds))
        },
    )

    /**
     * **GUARD: a round of five holds one Insider, and the Insider's EGRESS line is gone** (D-320).
     * Every Resident's page 2 still reads ALL SYSTEMS RESPONDING.
     */
    @Test
    fun aRoundWithOneInsiderHidesTheSharedLine() = evening(party = 5, insiders = null) { phones ->
        val insiders = phones.filter { it.flow.state.role == PanelRole.Insider }
        assertEquals(1, insiders.size, "a round of five drew ${insiders.size} Insiders")
        assertEquals("", insiders.single().egressLine(), "the only Insider's page 2 still says the Egress is shared")
        for (p in phones - insiders.toSet()) assertEquals("ALL SYSTEMS RESPONDING", p.egressLine(), "${p.name}'s page 2 moved")
    }

    /** **GUARD: a round of seven the host set to two keeps the line on both Insiders' pages** (D-320). */
    @Test
    fun aRoundWithTwoInsidersShowsTheSharedLine() = evening(party = 7, insiders = 2) { phones ->
        val insiders = phones.filter { it.flow.state.role == PanelRole.Insider }
        assertEquals(2, insiders.size, "a round of seven set to two drew ${insiders.size} Insiders")
        for (p in insiders) assertEquals("SHARED WITH THE OTHER INSIDER", p.egressLine(), "${p.name}'s page 2 hid the shared clock")
        for (p in phones - insiders.toSet()) assertEquals("ALL SYSTEMS RESPONDING", p.egressLine(), "${p.name}'s page 2 moved")
    }
}
