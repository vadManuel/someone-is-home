package home.someoneshome.app

import home.someoneshome.platform.IdleTimer
import home.someoneshome.platform.ScreenWake
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A guest is told when the house is gone, and is not told when the host only locked** (H4 of
 * the 2026-11-17 hunt, D-253).
 *
 * The hunt stopped the host and every guest stayed where it was: on its last screen, or on a lobby
 * still listing the defunct host. `DisconnectScreen` existed and nothing walked to it. Now a guest
 * whose link has been down for [GONE] is walked there, and walked back to where it was when its
 * seat is taken again. **A lock the host comes back from inside that window walks nobody** (D-225):
 * the link drops for every seat, the house reopens the same port, and every seat resumes by token.
 *
 * Real sockets on loopback and the real link, desk and ledger, as [HostForegroundTest] has them;
 * the window is shortened from [HOUSE_GONE_AFTER_MILLIS] so the test does not wait fifteen seconds.
 */
class HouseGoneTest {

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

    private class Timer : IdleTimer {
        override var disabled: Boolean = false
    }

    private class Phone(scope: CoroutineScope, name: String) {
        val air = Air()
        var lost = 0
        var back = 0
        var lostAtMillis = 0L
        private var flowRef: FlowModel? = null
        private val gone = HouseGone { flowRef }
        val link = LobbyOverTransport(
            scope, seat = Kept(), wake = ScreenWake(Timer()), advertising = air,
            onHouseLost = {
                lost++
                lostAtMillis = nowMillis()
                gone.lost()
            },
            onHouseBack = {
                back++
                gone.back()
            },
            houseGoneAfterMillis = GONE,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
        val flow = FlowModel(PanelState(screen = ScreenId.Lobby), homes = SavedHomesModel(), lobby = model)
            .also { flowRef = it }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private companion object {
        /** The window, shortened for the test. The app's is [HOUSE_GONE_AFTER_MILLIS]. */
        const val GONE = 3_000L

        fun nowMillis(): Long = monotonicNanos() / 1_000_000
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    private fun evening(body: suspend CoroutineScope.(phones: MutableList<Phone>) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async { scope.body(phones) }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** The host on the air and two guests seated in its lobby. */
    private suspend fun CoroutineScope.room(phones: MutableList<Phone>): Pair<Phone, List<Phone>> {
        val host = Phone(this, "HOST").also { phones += it }
        host.model.hostHome(home)
        until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
        val port = host.air.ports.single()
        val guests = listOf("ROSE", "DANI").map { Phone(this, it).also { p -> phones += p } }
        guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
        until("both guests were seated") { host.model.standing.joined == 3 }
        return host to guests
    }

    @Test
    fun aHouseThatFailsPutsEveryGuestOnTheDisconnectScreen() = evening { phones ->
        val (host, guests) = room(phones)
        assertTrue(guests.all { it.flow.state.screen == ScreenId.Lobby })

        // **The host's app fails**: the server goes, and no `HouseClosed` is said on the way out.
        val failedAt = nowMillis()
        host.link.house.stop()

        until("every guest was told the house is gone") { guests.all { it.lost == 1 } }
        for (g in guests) {
            assertTrue(
                g.lostAtMillis - failedAt >= GONE - 100,
                "a guest was told after ${g.lostAtMillis - failedAt} ms, inside the window a lock gets",
            )
            assertEquals(ScreenId.Disconnect, g.flow.state.screen, "a guest stayed on a lobby with nobody behind it")
        }
        delay(GONE * 2)
        assertTrue(guests.all { it.lost == 1 }, "a guest was told more than once")
    }

    @Test
    fun aHostThatLocksAndComesBackWalksNobody() = evening { phones ->
        val (host, guests) = room(phones)

        // **The lock** (D-225): the house goes off the air, every seat's link drops.
        host.link.backgrounded()
        until("the guests' seats dropped with the server") { host.link.house.armingInputs().seats.isEmpty() }
        delay(300)
        // **And the unlock**, inside the window.
        host.link.foregrounded()
        until("every seat came back") { host.link.house.armingInputs().seats.size == 3 }

        delay(GONE * 2)
        for (g in guests) {
            assertEquals(0, g.lost, "a lock the host came straight back from walked a guest to the disconnect screen")
            assertEquals(ScreenId.Lobby, g.flow.state.screen)
        }
    }

    @Test
    fun aGuestComesBackToItsOwnScreenWhenTheHouseDoes() = evening { phones ->
        val (host, guests) = room(phones)

        // A lock longer than the window: the guests are told.
        host.link.backgrounded()
        until("every guest was told the house is gone") { guests.all { it.lost == 1 } }
        assertTrue(guests.all { it.flow.state.screen == ScreenId.Disconnect })

        host.link.foregrounded()
        until("every guest was told the house is back") { guests.all { it.back == 1 } }
        until("every guest is back on the lobby it left") { guests.all { it.flow.state.screen == ScreenId.Lobby } }
        until("every seat came back") { host.link.house.armingInputs().seats.size == 3 }
        assertEquals(0, host.lost, "the host's own locked phone was told its own house is gone")
    }
}
