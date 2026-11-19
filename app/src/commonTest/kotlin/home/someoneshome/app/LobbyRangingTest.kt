package home.someoneshome.app

import home.someoneshome.core.contactSourceFor
import home.someoneshome.model.ContactSource
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PermsModel
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

/**
 * **D-301: every phone tells the house in the lobby how many peers it can range, and the house's
 * arming reads it** to choose whether the round's rules read Bluetooth or UWB distance.
 *
 * Three phones over real loopback sockets, the host's real [LobbyOverTransport] (its desk, ledger and
 * server) and two guests, each handed a capacity as the bench's UWB radio would hand it. Faked:
 * Bonjour. Every phone says it on every seating, right after its name; the house keeps the round's
 * seats' and hands them to the arming, where [contactSourceFor] is the one question.
 */
class LobbyRangingTest {

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

    private class Phone(scope: CoroutineScope, name: String, capacity: Int) {
        val air = Air()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, seat = MemorySeat(), rangingCapacity = { capacity },
            wake = ScreenWake(NoIdleTimer), advertising = air,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
        val flow = FlowModel(PanelState(screen = ScreenId.Join), lobby = model, perms = PermsModel.sample())
    }

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    private fun lobby(capacities: List<Int>, check: (Map<Int, Int>) -> Unit) = runBlocking<Unit> {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val host = Phone(scope, "HOST", capacities[0]).also { phones += it }
                host.model.hostHome(SavedHomesModel.sample().homes.first())
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val dial = NearbyHome("THE BUNGALOW", "127.0.0.1", host.air.ports.single())
                for ((i, capacity) in capacities.withIndex().drop(1)) {
                    val guest = Phone(scope, "GUEST$i", capacity).also { phones += it }
                    guest.model.attachTo(dial)
                    guest.flow.go(ScreenId.Lobby)
                }
                until("every phone said what it can range", { "${host.link.house.armingInputs().ranging}" }) {
                    host.link.house.armingInputs().ranging.size == capacities.size
                }
                check(host.link.house.armingInputs().ranging)
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** **GUARD (D-301): three phones that range, and the arming reads distance.** */
    @Test
    fun everyPhoneThatRangesSaysSoAndTheRoundReadsDistance() = lobby(listOf(3, 3, 2)) { ranging ->
        assertEquals(listOf(2, 3, 3), ranging.values.sorted(), "the house did not keep what each phone said: $ranging")
        assertEquals(ContactSource.Uwb, contactSourceFor(ranging.keys.map { home.someoneshome.model.Seat(it) }) { ranging[it.index] })
    }

    /** **GUARD (D-301): one phone with no UWB radio (today's real phones) takes the round back to Bluetooth.** */
    @Test
    fun onePhoneWithNoRadioTakesTheRoundToBluetooth() = lobby(listOf(3, 0, 3)) { ranging ->
        assertEquals(listOf(0, 3, 3), ranging.values.sorted(), "the house did not keep what each phone said: $ranging")
        assertEquals(ContactSource.Rssi, contactSourceFor(ranging.keys.map { home.someoneshome.model.Seat(it) }) { ranging[it.index] })
    }
}
