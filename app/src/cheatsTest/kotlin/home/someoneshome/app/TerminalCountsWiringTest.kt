package home.someoneshome.app

import home.someoneshome.core.occupancyBeat
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-316 over real sockets: a phone scans T in a running round and is sent the house's counts;
 * nobody else is, and nobody's frames change.**
 *
 * Four phones built as `CheatRoot` builds one (a real [LobbyOverTransport] with the map's door
 * wired, a real [RoundClient], the round armed by a real [RoundAuthority] on the host), standing in
 * [SimulatedAir]. One guest performs at its own card with another guest a metre away, so the house
 * places them and captures that room (D-272); another guest scans the T card and holds the station.
 * The reader's panel then holds the house's plan and the counts the house captured, error and band
 * as the authority holds them; every other phone's panel holds the plan and no counts; and every
 * upload's answer on every phone is one length, reader or not.
 */
class TerminalCountsWiringTest {

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
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val seam = DeferredRoundSeam()
        /** The length of every upload's answer this phone was sent, in order. */
        val answers = mutableListOf<Int>()
        val link: LobbyOverTransport = LobbyOverTransport(
            scope, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onCardRead = HouseDoors(house)::read,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            onMapped = HouseDoors(house)::mapped,
            round = seam, wake = wake, advertising = bonjour,
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
            onBody = { body -> if (RoundWire.decodeOrNull(body) is RoundBody.UploadHeard) answers += body.length },
        ) {}.also { made ->
            client = made
            seam.becomes(made)
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(30_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    @Test
    fun aPhoneThatScansTIsSentTheHousesCountsAndNoOtherPhoneIs() = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI")
                val host = Phone(scope, names[0], Seat(0), air) { authority }.also { phones += it }
                val house = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                authority = house
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                names.drop(1).forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEach { p ->
                    p.model.typeLine("a line from ${p.name}")
                    check(p.model.handOverLine()) { "${p.name}'s line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                val seatOf = host.link.house.armingInputs().names.associate { it.name to it.seat }
                fun phoneAt(seat: Seat) = phones.single { seatOf.getValue(it.name).index == seat.index }
                air.spreadOut(phones.map { seatOf.getValue(it.name) })

                assertTrue(armAtLightsOut(house, hosting = true, seed = 20261121L, note = {}), "the round never armed")
                until("every phone is uploading on its seed") { phones.all { it.round.round.uploading } }

                // The plan reached every phone at arming, the same plan.
                until("every phone holds the plan") { phones.all { it.flow.state.plan != null } }
                assertEquals(1, phones.map { it.flow.state.plan }.distinct().size, "two phones hold two plans")
                assertEquals(house.state().plan, phones.first().flow.state.plan, "the phones' plan is not the house's")

                // BRUNO performs at its own card with DEVI a metre away; CYRUS reads the Terminal.
                val performer = seatOf.getValue("BRUNO")
                val beside = seatOf.getValue("DEVI")
                val reader = seatOf.getValue("CYRUS")
                val anchor = house.state().workOrderFor(performer)!!.entries.first().anchor!!
                val room = house.state().destinationOf(anchor)!!.room
                val at = air.spotOf(performer)!!
                air.place(beside, SimulatedAir.Spot(at.x + 1.0, at.y))
                phoneAt(performer).round.readCard(anchor)
                until("the house captured the performer's room") { house.state().observationOf(room) != null }

                val terminal = assertNotNull(home.map.terminal?.card?.id, "the bench home has no T card")
                phoneAt(reader).round.readCard(terminal)
                until("CYRUS holds the station") { house.state().readerHolding()?.index == reader.index }
                until("CYRUS's panel holds the house's counts") { phoneAt(reader).flow.state.terminalBeat != null }
                // BRUNO stops, so the house stops capturing once its sweep on the close has run (3 s),
                // and the room's latest capture holds still for the comparison.
                phoneAt(performer).round.act(IntentMessage.StopSubroutine)
                delay(5 * SightingUpload.CADENCE_MILLIS)

                val index = house.state().plan.indexOf(room)
                val capture = assertNotNull(house.state().observationOf(room))
                val said = assertNotNull(phoneAt(reader).flow.state.terminalBeat)
                println("D-316 READER — room '$room' (index $index), capture true ${capture.trueCount} reported ${capture.reportedCount}; the reader was sent $said")
                println("D-316 PLAN — ${house.state().plan}")
                assertEquals(capture.reportedCount, said.counts[index], "the reader's count is not the house's reported count")
                assertEquals(house.state().plan.indexOf(house.state().terminalRoom!!), said.here, "the outline is not the station's room")
                val houseSays = occupancyBeat(house.state(), reader, Tick(house.clock)) as OccupancyBeat.Terminal
                assertEquals(houseSays.counts, said.counts, "the reader's counts are not the house's beat")
                assertEquals(ScreenId.TermLive, phoneAt(reader).flow.state.screen, "the reader is not on the live view")

                for (p in phones.filter { seatOf.getValue(it.name).index != reader.index }) {
                    assertNull(p.flow.state.terminalBeat, "${p.name}, not the reader, holds the Terminal's counts")
                    assertNull(p.flow.state.outsideBeat, "${p.name}, living, holds the couch's counts")
                }

                // Every upload's answer on every phone is one length, the reader's included.
                val lengths = phones.flatMap { it.answers }.distinct()
                println("D-316 ANSWER LENGTHS — ${phones.associate { it.name to it.answers.distinct() }}")
                assertEquals(1, lengths.size, "the upload's answer is not one length on every phone: $lengths")

                // Releasing the station stops the counts.
                phoneAt(reader).round.act(IntentMessage.LeaveTerminal)
                until("the house freed the station") { house.state().readerHolding() == null }
                until("CYRUS's counts were cleared") { phoneAt(reader).flow.state.terminalBeat == null }
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            job.cancel()
        }
    }
}
