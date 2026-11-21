package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Seat
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.monotonicMillis
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.model.protocol.SightingUpload
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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Over real sockets, every phone's estimate of the house's clock is refreshed by every upload it
 * sends, the host's own phone included** (D-295).
 *
 * Four phones built as `CheatRoot` builds one: a real [LobbyOverTransport] whose client clock is
 * deliberately wrong (the host's own phone 2 s behind, the guests 3 s ahead, 1.5 s behind and 0.7 s
 * ahead), a real [RoundClient] handed that clock and the link's `uploadAnswered`, and the round armed
 * at LIGHTS OUT by a real [RoundAuthority] on the host. After the seating burst each phone's
 * estimate has one sample; from LIGHTS OUT, every upload's answer adds one, stamped by the house at
 * arrival and at answer. Within a few beats every phone reads the house's clock to within 20 ms, and
 * every phone, Insider or Resident, has taken the same number of samples to the beat.
 */
class UploadClockWiringTest {

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

    private class Phone(scope: CoroutineScope, val name: String, val skew: Long, spot: Seat, air: SimulatedAir, house: () -> RoundAuthority?) {
        val bonjour = Bonjour()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val seam = DeferredRoundSeam()
        val clientMillis: () -> Long = { monotonicMillis() + skew }
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = seam, wake = wake, advertising = bonjour,
            clientMillis = clientMillis,
        )
        val model: LobbyModel = LobbyModel(link = link).also { it.nameResident(name) }
        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            lobby = model,
            send = { intent -> client?.act(intent) },
            houseNow = link::houseNow,
        )
        val round = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(Still), air.radioFor(spot), wake, ranging = air.rangingFor(spot, SimulatedAir.BENCH_CAPACITY),
            clientMillis = link::clientNow, clock = link::uploadAnswered,
        ) {}.also { made ->
            client = made
            seam.becomes(made)
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    @Test
    fun everyUploadRefreshesEveryPhonesClockAndEveryPhoneTakesTheSameSamples() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val specs = listOf("ALBA" to -2_000L, "BRUNO" to 3_000L, "CYRUS" to -1_500L, "DEVI" to 700L)
                val host = Phone(scope, specs[0].first, specs[0].second, Seat(0), air) { authority }.also { phones += it }
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                authority = house
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                specs.drop(1).forEachIndexed { i, (name, skew) ->
                    val guest = Phone(scope, name, skew, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEach { p ->
                    p.model.typeLine("a line from ${p.name}")
                    check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                val seatOf = host.link.house.armingInputs().names.associate { it.name to it.seat }
                air.spreadOut(phones.map { seatOf.getValue(it.name) })

                // The seating burst: one sample each, before any upload.
                until("every phone has a fix from its seating burst") { phones.all { it.link.houseNow() != null } }
                val atSeating = phones.associate { it.name to it.link.clockSamples() }

                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261118L, note = {}), "the round never armed")
                until("every phone is uploading on its seed") { phones.all { it.round.round.uploading } }
                delay(5 * SightingUpload.CADENCE_MILLIS + 400)

                val samples = phones.associate { it.name to it.link.clockSamples() - atSeating.getValue(it.name) }
                println("D-295 SAMPLES FROM UPLOADS, PER PHONE — $samples (at seating $atSeating)")
                assertTrue(
                    samples.values.all { it >= 4 },
                    "the uploads did not reach every phone's clock: $samples samples from uploads in five beats",
                )
                assertTrue(
                    samples.values.max() - samples.values.min() <= 1,
                    "the phones took different numbers of samples: $samples",
                )
                for (p in phones) {
                    val before = monotonicMillis()
                    val read = p.link.houseNow()!!
                    val after = monotonicMillis()
                    val error = when {
                        read < before -> before - read
                        read > after -> read - after
                        else -> 0L
                    }
                    println("D-295 CLOCK — ${p.name}, its clock ${p.skew} ms off: reads the house's ${error} ms wrong")
                    assertTrue(abs(error) <= 20, "${p.name} (its clock ${p.skew} ms off) reads the house's clock $error ms wrong")
                }
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            job.cancel()
        }
    }
}
