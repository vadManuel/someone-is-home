package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.RevokeState
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
 * **Page 2's two holds, from the action the tile calls to the house's answer, over real sockets**
 * (D-231).
 *
 * The bug this exists for: on two real iPhones the Insider held REVOKE, the tile said ARMED, and the
 * house was never told. `FlowModel.stepRevoke` stepped the tile and sent nothing, and the EGRESS
 * hold walked its own phone to the alert and sent nothing. Every earlier "the Revoke lands" test
 * passed because the bench or the test put `RevokeArmed` (or `Intent.ArmRevoke`) to the house
 * directly. **Nothing here does.**
 *
 * Each phone is built the way `CheatRoot` builds it: a real [FlowModel] whose `send` and
 * `fireEgress` reach a real [RoundClient], which rides the phone's one socket through a real
 * [LobbyOverTransport]; the host's link has `CheatRoot`'s doors into a real [RoundAuthority]. The
 * presses are `flow.actions()`, the lambdas the page-2 tiles hold (`HoldInputTest` drives those
 * tiles with a finger). The touch is a [SimulatedAir]: each phone's radio is simulated, and its
 * uploads go up its own socket on the real cadence to the house's resolver.
 *
 * Faked: Bonjour, the radio, and the house's clock is advanced past a cooldown by hand (the tick
 * driver is not running). Not covered here, because it lives inside a composable: `CheatRoot`
 * passing these two lambdas, which this file copies.
 */
class AbilityWiringTest {

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

    /**
     * One phone. [radioSpot] labels this phone's simulated radio in the air; the seat the house
     * gives it is learnt from the house afterwards, and the resolver reads tokens, never this label.
     */
    private class Phone(
        scope: CoroutineScope,
        val name: String,
        val radioSpot: Seat,
        air: SimulatedAir,
        house: () -> RoundAuthority?,
    ) {
        val bonjour = Bonjour()
        val log = mutableListOf<String>()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null

        /** `CheatRoot`'s two page-2 seams, exactly: `press.value?.act(it)` and `press.value?.fireEgress()`. */
        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
        )

        /** Every push this phone was sent, decoded, in arrival order. */
        val pushed = mutableListOf<RoundMessage>()

        val round = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), air.radioFor(radioSpot), wake,
        ) { log += it }.also { made ->
            client = made
            made.onMessage = { pushed += it }
        }

        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = Kept(),
            // `CheatRoot`'s doors, exactly.
            onIntent = { intent -> scope.launch { house()?.heard(intent) } },
            onEgressFired = { actor -> scope.launch { house()?.fireEgress(actor) } },
            onSighted = { seat, upload -> scope.launch { house()?.sighted(seat, upload) } },
            onRadioFaulted = { seat, cause -> scope.launch { house()?.radioFaulted(seat, cause) } },
            round = round, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
    }

    private class Room(
        val phones: List<Phone>,
        val house: RoundAuthority,
        val air: SimulatedAir,
        val said: MutableList<String>,
    ) {
        fun phoneAt(seat: Seat): Phone {
            val name = house.state.names.single { it.seat.index == seat.index }.name
            return phones.single { it.name == name }
        }

        /** Read once, at LIGHTS OUT: a getter would name somebody else the moment the Revoke lands. */
        val insider: Seat = house.state.insiderSeats.first()
        val target: Seat = house.state.livingPlainResidents.first()
        fun frames(): List<Int> = phones.map { it.pushed.size }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(20) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    /** Four phones in a lobby, the host first, every line in, then LIGHTS OUT through production's path. */
    private fun evening(body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air) { authority }.also { phones += it }
                val said = mutableListOf<String>()
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = { said += it },
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                listOf("ROSE", "DANI", "MARCUS").forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEachIndexed { i, p ->
                    p.model.typeLine("line $i")
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == 4 }
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(authority, hosting = true, seed = SEED, note = { said += it }), "the round never armed: $said")
                until("every phone holds the round's seed") { phones.all { it.round.round.uploading } }
                val room = Room(phones, authority, air, said)
                assertTrue(room.house.state.livingPlainResidents.isNotEmpty(), "the draw left nobody to Revoke")
                scope.body(room)
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    /** Hold the two phones together for two beats of the real cadence, then walk the mover back. */
    private suspend fun Room.touch(mover: Phone, still: Phone) {
        val act = ContactAct(air, beat = { delay(SightingUpload.CADENCE_MILLIS + 250) }, note = { said += it })
        act.touch(mover.radioSpot, still.radioSpot)
        delay(SETTLE_MILLIS)
    }

    // ---- REVOKE -------------------------------------------------------------------------------------

    /**
     * **GUARD (D-231): the REVOKE tile's action arms the Revoke in the house, and the touch after it
     * lands.** Nothing puts `Intent.ArmRevoke` or `RevokeArmed` to the house but the Insider's phone.
     */
    @Test
    fun theRevokeTilesHoldArmsTheHouseAndTheTouchAfterItLands() = evening { room ->
        val insider = room.phoneAt(room.insider)
        val target = room.phoneAt(room.target)
        // Past the opening half-cooldown (D-132), so an arm is not a silent refusal.
        val ready = room.house.state.cooldownFor(room.insider, InsiderAbility.Revoke)?.readyAt?.step ?: 0L
        room.house.advanceTo(Tick(ready))
        val before = room.frames()

        insider.flow.actions().armRevoke()

        until("the house armed the Insider's Revoke from its own phone") {
            room.house.state.cooldownArmed.any { it.index == room.insider.index }
        }
        assertTrue(Intent.ArmRevoke(room.insider) in room.house.heard, "the arm reached the house as somebody else's: ${room.house.heard}")
        assertEquals(RevokeState.Armed, insider.flow.state.revoke, "the tile did not echo ARMED")
        delay(SETTLE_MILLIS)
        assertEquals(before, room.frames(), "arming was answered on a socket; it is silent (D-009, D-142)")

        room.touch(insider, target)
        until("the touch Revoked the target", { "refusals=${room.house.contactRefusals} said=${room.said.takeLast(25)} insider=${insider.log.takeLast(10)}" }) { room.house.state.isRevoked(room.target) }
        until("the Insider's phone was told its Revoke fired") {
            RoundMessage.AbilityFired(cooldownStarted = true) in insider.pushed
        }
        until("the target's phone was told") { RoundMessage.RevokeLanded.Padded in target.pushed }
        until("the target's phone went to the Revoked screen") { target.flow.state.screen == ScreenId.Revoked }
        assertTrue(
            room.house.emitted.any { it is Effect.AbilityFired && it.actor.index == room.insider.index && it.cooldownStarted },
            "the house never spent the Insider's armed Revoke",
        )
    }

    /**
     * **GUARD (rule 1): an arm into a running clock is sent exactly the same way and arms nothing.**
     * The tile echoes ARMED either way; the house answers nobody either way; the touch after it is an
     * ordinary touch.
     */
    @Test
    fun aRevokeHeldIntoARunningClockIsSentTheSameAndArmsNothing() = evening { room ->
        val insider = room.phoneAt(room.insider)
        val target = room.phoneAt(room.target)
        val ready = room.house.state.cooldownFor(room.insider, InsiderAbility.Revoke)?.readyAt?.step ?: 0L
        assertTrue(room.house.clock < ready, "the fixture's clock was already past the opening cooldown")
        val before = room.frames()

        insider.flow.actions().armRevoke()

        until("the arm reached the house") { Intent.ArmRevoke(room.insider) in room.house.heard }
        assertFalse(room.house.state.cooldownArmed.any { it.index == room.insider.index }, "an arm into a running clock armed")
        assertEquals(RevokeState.Armed, insider.flow.state.revoke, "the tile's echo depended on the outcome")
        delay(SETTLE_MILLIS)
        assertEquals(before, room.frames(), "a refused arm was answered on a socket")

        room.touch(insider, target)
        until("the touch was answered") { insider.pushed.any { it is RoundMessage.ContactAnswered } }
        assertFalse(room.house.state.isRevoked(room.target), "an unarmed touch Revoked the target")
    }

    // ---- EGRESS -------------------------------------------------------------------------------------

    /**
     * **GUARD (D-231): the EGRESS tile's action fires the Egress in the house, and the alert reaches
     * every phone by the house's push, the actor's included.** A press before the clock is ready goes
     * up the same way and is answered as every Tier-1 fire into a running clock is (T1·8): the actor
     * alone is told `AbilityFired(cooldownStarted = false)`, which moves nothing, and no other phone
     * hears anything.
     */
    @Test
    fun theEgressTilesHoldFiresTheHouseAndTheAlertReachesEveryPhone() = evening { room ->
        val insider = room.phoneAt(room.insider)
        insider.flow.push(ScreenId.Page2)
        val saidBefore = room.said.size
        val before = room.frames()

        // Before the opening half-cooldown is up.
        assertTrue(room.house.clock < room.house.state.egressReadyAt.step, "the fixture's Egress clock was already ready")
        insider.flow.actions().armEgress()
        until("the early Egress reached the house", { "said=${room.said.takeLast(15)} insider=${insider.log.takeLast(15)}" }) {
            room.said.drop(saidBefore).any { it.startsWith("EGRESS DRAWN") }
        }
        until("the actor was answered") { RoundMessage.AbilityFired(cooldownStarted = false) in insider.pushed }
        delay(SETTLE_MILLIS)
        assertFalse(room.house.state.egressRunning, "an Egress fired into a running clock")
        val after = room.frames()
        assertEquals(
            room.phones.map { if (it === insider) 1 else 0 }, after.zip(before) { a, b -> a - b },
            "an Egress into a running clock was answered to somebody other than its actor, or more than once",
        )
        assertTrue(room.phones.none { p -> p.pushed.any { it is RoundMessage.EgressOpened } }, "an Egress into a running clock opened on a phone")
        assertEquals(ScreenId.Page2, insider.flow.state.screen, "an Egress into a running clock moved the actor's phone")

        room.house.advanceTo(Tick(room.house.state.egressReadyAt.step))
        insider.flow.actions().armEgress()

        until("the house is on fire") { room.house.state.egressRunning }
        until("every phone was told") { room.phones.all { p -> p.pushed.any { it is RoundMessage.EgressOpened } } }
        until("the actor's phone reached the alert by the push") { insider.flow.state.screen == ScreenId.Banner }
        assertTrue(
            room.house.emitted.any { it is Effect.AbilityFired && it.actor.index == room.insider.index },
            "the Egress was not attributed to the Insider's seat",
        )
    }

    private companion object {
        const val SEED = 20261150L
        const val SETTLE_MILLIS = 400L
    }
}
