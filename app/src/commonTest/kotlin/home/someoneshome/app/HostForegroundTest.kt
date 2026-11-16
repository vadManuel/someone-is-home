package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Haptic
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.IdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The host's phone leaves the foreground and comes back, and the room comes back with it** (H2,
 * D-225), **with the host's screen held on from its lobby opening to hosting's end** (H1, D-224).
 *
 * The 2026-11-16 device log is the fixture. A host auto-locked in the lobby with three seats in it:
 * every socket dropped (`ENOTCONN`), the advertisement was refused (`-72000`), the house woke with
 * nobody seated and nothing that would seat them, and LIGHTS OUT then reached `WILL NOT ARM — 0
 * SEAT(S)` under a screen that said ARMED.
 *
 * Real sockets on loopback and the real link, desk and ledger; the two things faked are the ones a
 * test cannot have — Bonjour ([Air]) and the OS's foreground notifications (the test calls
 * `backgrounded` and `foregrounded` itself, which is all `deviceForeground` does).
 */
class HostForegroundTest {

    /** [SeatStore] with no filesystem: the token this phone holds. */
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

    /** Bonjour, counted: what went on the air, on which port, and how many times it came off. */
    private class Air : Advertising {
        val ports = mutableListOf<Int>()
        var stopped = 0
        override fun advertise(
            name: String,
            port: Int,
            onEvent: (String) -> Unit,
            onAir: () -> Unit,
            onFailed: (AdvertiseFailure) -> Unit,
        ): () -> Unit {
            ports += port
            onAir()
            return { stopped++ }
        }
    }

    private class Timer : IdleTimer {
        val writes = mutableListOf<Boolean>()
        override var disabled: Boolean = false
            set(value) {
                writes += value
                field = value
            }
    }

    private class Phone(scope: CoroutineScope, name: String, val timer: Timer = Timer()) {
        val air = Air()
        val kept = Kept()
        val wake = ScreenWake(timer)
        val log = mutableListOf<String>()
        val link = LobbyOverTransport(scope, onEvent = { log += it }, seat = kept, wake = wake, advertising = air)
        val model = LobbyModel(link = link).also { it.nameResident(name) }

        fun handOver(line: String) {
            model.typeLine(line)
            check(model.handOverLine()) { "the line was refused" }
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    /**
     * Everything inside runs on a scope this test can end. The house's patience timer and every
     * client loop run forever on purpose, and `runBlocking` would wait for them.
     */
    private fun evening(body: suspend CoroutineScope.(phones: MutableList<Phone>) -> Unit) = runBlocking {
        // A supervisor child: a failing assertion comes back through `await` rather than tearing
        // the scope down before the servers below are stopped.
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

    /** The host on the air, two guests in, every line handed over: the room from the device log. */
    private suspend fun CoroutineScope.room(phones: MutableList<Phone>): List<Phone> {
        val host = Phone(this, "HOST").also { phones += it }
        host.model.hostHome(home)
        until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
        val port = host.air.ports.single()
        val guests = listOf("ROSE", "DANI").map { Phone(this, it).also { p -> phones += p } }
        guests.forEach { it.model.attachTo(NearbyHome(home.name, "127.0.0.1", port)) }
        until("both guests were seated") { host.model.standing.joined == 3 }
        val all = listOf(host) + guests
        all.forEachIndexed { i, p -> p.handOver("line $i") }
        until("every line reached the house") { host.model.standing.linesIn == 3 }
        return all
    }

    // ---- H2: the house goes off the air and comes back ----------------------------------------------

    @Test
    fun aHostThatLeavesTheForegroundComesBackWithEverySeatInItsOwnChair() = evening { phones ->
        val all = room(phones)
        val host = all.first()
        val port = host.air.ports.single()
        assertTrue(host.model.readyToArm, "the room was not ready before the lock")
        val chairs = host.link.house.armingInputs().names
        val lines = host.link.house.armingInputs().oneLines
        val tokens = all.map { it.kept.token }

        // **The lock.**
        host.link.backgrounded()
        assertEquals(1, host.air.stopped, "the advertisement stayed on the air with the house suspended")
        until("every seat dropped with the server") { host.link.house.armingInputs().seats.isEmpty() }
        // The host's own link dropped too, and its LIGHTS OUT is drawn from its own standing.
        until("the host's lobby stopped showing a room it is not in") { host.model.standing.joined == 0 }
        assertFalse(host.model.readyToArm, "LIGHTS OUT was live over an empty house")
        assertEquals("NEEDS 3 RESIDENTS", host.model.waitingFor(), "the inert button did not say why")

        // **The unlock.**
        host.link.foregrounded()
        until("the home went back on the air") { host.air.ports.size == 2 }
        assertEquals(port, host.air.ports.last(), "the house came back on a port no seat is dialling")
        until("every seat came back, with every line") {
            host.model.standing.joined == 3 && host.model.standing.linesIn == 3
        }
        assertEquals(chairs, host.link.house.armingInputs().names, "somebody came back to somebody else's chair")
        assertEquals(lines, host.link.house.armingInputs().oneLines, "a line came back under the wrong seat")
        assertEquals(tokens, all.map { it.kept.token }, "a phone came back holding a token it did not have")
        assertTrue(host.model.readyToArm, "LIGHTS OUT stayed defunct over a room that came back")
        assertTrue(all.none { p -> p.log.any { "JOINING FRESH" in it } }, "a phone rejoined as a stranger")
    }

    /** **A guest's phone is not the house**, so the two calls take nothing off the air there. */
    @Test
    fun aGuestLeavingTheForegroundTakesNothingOffTheAir() = evening { phones ->
        val all = room(phones)
        val guest = all.last()
        guest.link.backgrounded()
        guest.link.foregrounded()
        assertEquals(emptyList(), guest.air.ports, "a guest put a home on the air")
        assertEquals(1, all.first().air.ports.size, "a guest's lock re-advertised the host")
        assertEquals(3, all.first().model.standing.joined, "a guest's lock moved the room")
    }

    // ---- H1: the host's screen, from its lobby opening to hosting's end ------------------------------

    @Test
    fun theHostsScreenStaysOnFromItsLobbyOpeningUntilItLeaves() = evening { phones ->
        val host = Phone(this, "HOST").also { phones += it }
        assertFalse(host.timer.disabled, "the screen was held before there was a lobby")
        host.model.hostHome(home)
        until("the host's lobby opened") { host.model.hosting }
        assertTrue(host.timer.disabled, "the host's lobby is open and the phone can still lock itself")

        val guest = Phone(this, "ROSE").also { phones += it }
        guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", host.air.ports.single()))
        until("the guest was seated") { host.model.standing.joined == 2 }
        assertEquals(emptyList(), guest.timer.writes, "a guest's lobby touched its idle timer (D-221 is unchanged there)")

        host.model.leaveLobby()
        assertFalse(host.timer.disabled, "the host left and its screen is still held on")
    }

    @Test
    fun closingTheHouseLetsTheHostsScreenSleep() = evening { phones ->
        val host = Phone(this, "HOST").also { phones += it }
        host.model.hostHome(home)
        until("the host's lobby opened") { host.model.hosting }
        assertTrue(host.timer.disabled)
        host.model.terminateHouse()
        assertFalse(host.timer.disabled, "the house closed and the host's screen is still held on")
    }

    /**
     * **Lobby, round, the round's end, NEW ROUND** — one idle timer through all four, the host's
     * wake shared with its own round half exactly as the app root shares it.
     */
    @Test
    fun theRoundsEndEndsHostingAndNewRoundOpensItAgain() = evening { phones ->
        val host = Phone(this, "HOST").also { phones += it }
        host.model.hostHome(home)
        until("the host's lobby opened") { host.model.hosting }
        val round = RoundOverTransport(
            this, FlowModel(PanelState(screen = ScreenId.Home)), buzzer = HouseBuzzer(SilentHaptics),
            send = { }, radio = NoRadio, wake = host.wake,
        )

        pushes(round, RoundMessage.ContactSeedIssued(ContactSeed("0123456789abcdef0123456789abcdef")))
        assertEquals(listOf(true), host.timer.writes, "the timer came back on between the lobby and the round")

        pushes(round, RoundMessage.RoundEnded(Winner.Residents, WinRoute.InsidersRestrained, Haptic.Long))
        assertFalse(host.timer.disabled, "the round ended and hosting did not end with it")

        // NEW ROUND: the host walks back into a lobby its phone runs.
        host.link.roundEnded()
        assertTrue(host.timer.disabled, "NEW ROUND opened the host's lobby and its screen can lock")
        // The house's RoundEnded broadcast reaching the host's own round half, after NEW ROUND.
        round.roundEnded()
        assertTrue(host.timer.disabled, "the round half's late release let the re-opened lobby lock")

        host.model.leaveLobby()
        assertFalse(host.timer.disabled, "the host left and its screen is still held on")
    }

    private suspend fun CoroutineScope.pushes(round: RoundOverTransport, vararg messages: RoundMessage) {
        val draining = launch { round.run() }
        messages.forEach { message ->
            val seq = round.held + 1
            round.frameArrived(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))
            withTimeout(5_000) { while (round.held < seq) delay(1) }
        }
        draining.cancel()
    }
}
