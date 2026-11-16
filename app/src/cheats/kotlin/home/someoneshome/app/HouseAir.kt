package home.someoneshome.app

import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.wallClockMillis

/**
 * **The contact act, as a bench plays it: walk two phones together, hold them there for a few
 * beats, walk them apart** (D-217). Shared by [RoundCheat] and by the in-process tests, so the act a
 * rehearsal performs is the act a test proves.
 *
 * [beat] is one beat of the upload cadence on every phone in the round, returning once the house
 * has taken every upload. What carries an upload is the caller's: the phones' own sockets in
 * process, or [HouseAir] across processes.
 */
class ContactAct(
    val air: SimulatedAir,
    private val beat: suspend () -> Unit,
    private val note: (String) -> Unit,
) {
    /** Two beats: both halves of a mutual sighting land within one upload of each other (D-213). */
    suspend fun touch(mover: Seat, still: Seat, beats: Int = HOLD_BEATS) {
        val from = air.spotOf(mover)
        note("THE RADIO — SEAT ${mover.index} WALKS TO SEAT ${still.index} AND HOLDS THE PHONES TOGETHER")
        air.touch(mover, still)
        repeat(beats) { beat() }
        from?.let { air.place(mover, it) }
        note("THE RADIO — SEAT ${mover.index} WALKS AWAY")
    }

    /** Beats with nobody touching: the cadence keeps going whatever is happening (R8). */
    suspend fun idle(beats: Int) = repeat(beats) { beat() }

    companion object {
        const val HOLD_BEATS = 2
    }
}

/**
 * ⚠️ **STAND-IN — the other phones' radios, uploaded by the house, because five processes share no
 * air** (D-217).
 *
 * The rehearsal runs one process per phone, and a simulated radio in one process cannot hear an
 * advertisement in another. So on the host, this holds one [ContactUploader] per seat (the class
 * every phone runs) over one [SimulatedAir], and puts their uploads into [RoundAuthority.sighted],
 * the door the arrival queue feeds. **What is stood in for is the hop up each phone's socket and
 * nothing below it**: the tokens are each seat's own (the house may derive every seed, D-212), the
 * window is the wall clock's, and the resolver, the gate, the rules and every push down are real.
 * Each phone's own uploader keeps uploading what its own radio heard up its own socket all the
 * while (filler only on a Simulator, which has no Bluetooth), so the cadence on the air is the real
 * one too.
 *
 * Out is the house's word, as it is on a phone: a seat the state says is out is told
 * `RevokeLanded`, which is the push its own phone would have had. The in-process tests carry every
 * upload over a real socket instead, which is what proves this hop.
 */
class HouseAir(
    private val house: RoundAuthority,
    private val window: () -> ContactWindow = { ContactWindow.containing(wallClockMillis()) },
) {
    val air = SimulatedAir()
    private val uploaders = mutableMapOf<Int, ContactUploader>()
    private val pending = mutableListOf<Pair<Seat, RoundBody.Sighted>>()

    /** Issue every seat its seed and stand everybody a room apart. Call once the round is armed. */
    fun armed() {
        val seats = house.state.seats
        uploaders.clear()
        for (seat in seats) {
            val up = ContactUploader(air.radioFor(seat), window) { body -> pending += seat to body }
            up.heard(RoundMessage.ContactSeedIssued(contactSeedFor(house.state.seed, seat)))
            uploaders[seat.index] = up
        }
        air.spreadOut(seats)
    }

    /** One beat on every seat, and every upload put to the house in seat order. */
    suspend fun beat() {
        for (seat in house.state.seats) {
            val up = uploaders[seat.index] ?: continue
            if (house.state.isOut(seat) && !up.isOut) up.heard(RoundMessage.RevokeLanded)
            up.beat()
        }
        val batch = pending.toList()
        pending.clear()
        for ((seat, body) in batch) house.sighted(seat, body)
    }
}
