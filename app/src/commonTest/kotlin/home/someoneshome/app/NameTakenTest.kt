package home.someoneshome.app

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
import kotlin.test.assertTrue

/**
 * **The lobby refuses a name already taken, case-insensitively, and the person types another**
 * (D-288, the owner's ruling 9d; approved wording "NAME TAKEN", no second line).
 *
 * Three phones over real loopback sockets: the host's real [LobbyOverTransport] (its desk, ledger and
 * server), and two guests whose `onNameTaken` is wired to `FlowModel.nameTaken` as `CheatRoot` wires
 * it. Faked: Bonjour.
 */
class NameTakenTest {

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

    private class Phone(scope: CoroutineScope, name: String) {
        val air = Air()
        val log = mutableListOf<String>()
        val store = MemorySeat()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = store,
            onNameTaken = { flow.nameTaken() },
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

    /**
     * **GUARD (D-288): a second "sam" beside SAM is refused and walked to JOIN with NAME TAKEN**,
     * holding no house, and the lobby never lists it; it types SAMMY and taps the home again, and is
     * seated under the new name. The first SAM is never refused.
     */
    @Test
    fun aNameAnotherSeatHasIsRefusedAndThePersonTypesAnother() = runBlocking<Unit> {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val host = Phone(scope, "HOST").also { phones += it }
                host.model.hostHome(SavedHomesModel.sample().homes.first())
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val dial = NearbyHome("THE BUNGALOW", "127.0.0.1", host.air.ports.single())

                val first = Phone(scope, "SAM").also { phones += it }
                first.model.attachTo(dial)
                first.flow.go(ScreenId.Lobby)
                until("the first SAM was seated and named", { "${host.model.standing}" }) { "SAM" in host.model.residents }

                val second = Phone(scope, " sam").also { phones += it }
                second.model.attachTo(dial)
                second.flow.go(ScreenId.Lobby)
                until("the second sam was refused", { "${second.flow.state.screen} ${second.model.refusal} ${second.log}" }) {
                    second.flow.state.screen == ScreenId.Join && second.model.refusal != null
                }
                assertEquals(LobbyModel.NAME_TAKEN, second.model.refusal, "the refusal was not NAME TAKEN")
                assertEquals(null, second.model.attached, "the refused phone still holds the house")
                assertEquals(0, second.model.standing.joined, "the refused phone still holds a lobby")
                assertTrue(host.log.any { it.startsWith("A NAME WAS REFUSED, ALREADY TAKEN") }, "the house recorded no refusal: ${host.log}")
                until("the house let the refused phone go", { "${host.model.standing}" }) { host.model.standing.joined == 2 }
                assertEquals(1, host.model.residents.count { it.equals("SAM", ignoreCase = true) }, "the lobby lists two SAMs: ${host.model.residents}")
                assertEquals(null, first.model.refusal, "the first SAM was refused")
                assertEquals(ScreenId.Lobby, first.flow.state.screen, "the first SAM was walked out of its lobby")

                // **The person types another and taps the home again.**
                second.model.nameResident("SAMMY")
                assertEquals(null, second.model.refusal, "typing did not clear NAME TAKEN")
                second.model.attachTo(dial)
                second.flow.go(ScreenId.Lobby)
                until("SAMMY was seated under the new name", { "${host.model.standing}" }) {
                    host.model.standing.joined == 3 && "SAMMY" in host.model.residents
                }
                assertEquals(null, second.model.refusal)
                assertEquals(ScreenId.Lobby, second.flow.state.screen)
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }
}
