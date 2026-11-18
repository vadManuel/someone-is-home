package home.someoneshome.app

import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.Intent
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Placement
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
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
 * seat scans its own work, then its phone's upload hears a second seat at −60.
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
        house.heard(Intent.BeginSubroutine(Seat(0), card))
        assertTrue(house.state.presenceFor(Seat(0))!!.open, "seat 0's own work opened no window")
        val room = house.state.roomOfCard(card)!!

        val heard = listOf(Sighting(ContactTokens.of(seeds.getValue(3), window), -60, window))
        val upload = SightingUpload.pack(seeds.getValue(0), window, 1, heard)
        val before = pushed.size
        house.sighted(Seat(0), upload)
        assertEquals(before, pushed.size, "the house pushed something for an upload: ${pushed.drop(before)}")

        val placed = house.state.placementOf(Seat(3), Tick(house.clock))
        assertIs<Placement.Inferred>(placed, "seat 3 was not placed by seat 0's upload")
        assertEquals(room, placed.room)
        val seen = assertNotNull(house.state.observationOf(room), "the sensor's room was not observed")
        assertEquals(2, seen.trueCount)
        assertEquals(2L, house.state.nextObservation)

        // A second delivery of the same upload is one capture, not two: its error is rolled once.
        house.sighted(Seat(0), upload)
        assertEquals(2L, house.state.nextObservation, "a transport's repeat was captured again")
        // An upload from a seat that is not a sensor places nobody and observes nothing.
        house.sighted(Seat(4), SightingUpload.pack(seeds.getValue(4), window, 1, heard))
        assertEquals(2L, house.state.nextObservation, "a seat that scanned nothing sensed")
        assertEquals(before, pushed.size, "the house pushed something for an upload")
        assertTrue(house.contactRefusals.isEmpty(), "a Sensed was refused: ${house.contactRefusals}")
    }

    private companion object {
        const val SEED = 20261117L
    }
}
