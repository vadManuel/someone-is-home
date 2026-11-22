package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.core.ContactGate
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.Intent
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Placement
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.toView
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **O2 and O4 on the real authority: the hosted home's plan reaches the rules, a performer's upload
 * places the seats it heard, and not one frame goes to any phone for either.**
 *
 * The real [RoundAuthority] armed in the bench's home, every effect it hands its `emit` counted. A
 * seat scans its own work, then its phone and a second seat's, a metre apart, upload every second
 * through the real uploader and a simulated UWB radio: the house pairs them (the map needs the
 * pair, D-306), and the performer's distance places the second seat (D-301; every round, D-308).
 */
class PlacementWiringTest {

    private val seats = List(6) { Seat(it) }
    private val window = ContactWindow.containing(1_763_400_000_000L)

    private fun inputs() = ArmingInputs(
        seats = seats,
        names = seats.map { SeatNamed(it, "SEAT${it.index}") },
        oneLines = seats.map { OneLineHeld(it, "line ${it.index}") },
        chosenInsiders = null,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    /** **GUARD: an upload from a performer lands a sighting and an observation, and sends nothing.** */
    @Test
    fun aPerformersUploadPlacesWhatItHeardAndSendsNothing() = runBlocking<Unit> {
        val pushed = mutableListOf<Effect>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = ::inputs,
            emit = { effect, _ -> pushed += effect },
            contactWindow = { window },
            note = { said += it },
        )
        assertTrue(house.arm(SEED), "never armed: $said")
        // O2: the hosted home's plan is the rules' plan, and the T card's room came with it.
        assertEquals(BenchHome.home.plan.toView(), house.state.plan)
        assertEquals(BenchHome.home.map.terminal?.room?.name, house.state.terminalRoom)
        assertTrue(house.state.plan.rooms.isNotEmpty(), "the bench's home has no plan; this proves nothing")

        val seeds = pushed.filterIsInstance<Effect.ContactSeedIssued>().associate { it.seat.index to it.seed }
        val card = house.state.workOrderFor(Seat(0))!!.entries.first().anchor!!
        house.cardRead(Seat(0), card)
        assertTrue(house.state.presenceFor(Seat(0))!!.open, "seat 0's own work opened no window")
        val room = house.state.roomOfCard(card)!!

        // Seats 0 and 3 a metre apart, seat 4 a room away; each phone's real uploader and radios.
        val air = SimulatedAir()
        val sent = mutableMapOf<Int, RoundBody.Sighted>()
        val phones = listOf(0, 3, 4).associateWith { i ->
            ContactUploader(
                air.radioFor(Seat(i)), { window },
                ranging = air.rangingFor(Seat(i), SimulatedAir.BENCH_CAPACITY),
                send = { sent[i] = it },
            ).also { it.heard(RoundMessage.ContactSeedIssued(seeds.getValue(i))) }
        }
        air.place(Seat(0), SimulatedAir.Spot(0.0, 0.0))
        air.place(Seat(3), SimulatedAir.Spot(1.0, 0.0))
        air.place(Seat(4), SimulatedAir.Spot(20.0, 0.0))
        val before = pushed.size
        var placing: RoundBody.Sighted? = null
        for (second in 0 until 10) {
            if (placing != null) break
            house.advanceTo(Tick(house.clock + ContactGate.UPLOAD_TICKS))
            for (i in listOf(0, 3)) {
                phones.getValue(i).beat()
                val body = sent.getValue(i)
                phones.getValue(i).planned(house.sighted(Seat(i), body))
                if (i == 0 && placing == null && house.state.placementOf(Seat(3), Tick(house.clock)) is Placement.Inferred) placing = body
            }
        }
        val upload = assertNotNull(placing, "seat 0's uploads never placed seat 3 by distance: ${house.ranging?.pairs()}")
        assertEquals(before, pushed.size, "the house pushed something for an upload: ${pushed.drop(before)}")

        val placed = house.state.placementOf(Seat(3), Tick(house.clock))
        assertIs<Placement.Inferred>(placed, "seat 3 was not placed by seat 0's upload")
        assertEquals(room, placed.room)
        val seen = assertNotNull(house.state.observationOf(room), "the sensor's room was not observed")
        assertEquals(2, seen.trueCount)
        val captured = house.state.nextObservation

        // A second delivery of the same upload is one capture, not two: its error is rolled once.
        house.sighted(Seat(0), upload)
        assertEquals(captured, house.state.nextObservation, "a transport's repeat was captured again")
        // An upload from a seat that is not a sensor places nobody and observes nothing.
        phones.getValue(4).beat()
        house.sighted(Seat(4), sent.getValue(4))
        assertEquals(captured, house.state.nextObservation, "a seat that scanned nothing sensed")
        assertEquals(before, pushed.size, "the house pushed something for an upload")
        assertTrue(house.contactRefusals.isEmpty(), "a Sensed was refused: ${house.contactRefusals}")
    }

    private companion object {
        const val SEED = 20261117L
    }
}
