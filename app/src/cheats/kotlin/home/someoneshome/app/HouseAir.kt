package home.someoneshome.app

import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.wallClockMillis

/**
 * **The contact act, as a bench plays it: walk two phones together, hold them there until the house
 * concludes or the hold runs out, walk them apart** (D-217). Shared by [RoundCheat] and by the
 * in-process tests, so the act a rehearsal performs is the act a test proves.
 *
 * [beat] is one beat of the upload cadence on every phone in the round, returning once the house
 * has taken every upload. What carries an upload is the caller's: the phones' own sockets in
 * process, or [HouseAir] across processes.
 *
 * **On distance, a hold takes seconds** (D-308): the house pairs the two phones only once Bluetooth
 * says they are close and a rule needs them (D-306), each phone is told to start on its next upload,
 * and the radio reads a second after that. So a touch is held for up to [beats] beats and stops as
 * soon as [until] says the house concluded.
 */
class ContactAct(
    val air: SimulatedAir,
    private val beat: suspend () -> Unit,
    private val note: (String) -> Unit,
) {
    /** Hold [mover] against [still] for up to [beats] beats, ending early once [until] is true. */
    suspend fun touch(mover: Seat, still: Seat, beats: Int = HOLD_BEATS, until: () -> Boolean = { false }) {
        val from = air.spotOf(mover)
        note("THE RADIO — SEAT ${mover.index} WALKS TO SEAT ${still.index} AND HOLDS THE PHONES TOGETHER")
        air.touch(mover, still)
        for (i in 0 until beats) {
            if (until()) break
            beat()
        }
        from?.let { air.place(mover, it) }
        note("THE RADIO — SEAT ${mover.index} WALKS AWAY")
    }

    /** Beats with nobody touching: the cadence keeps going whatever is happening (R8). */
    suspend fun idle(beats: Int) = repeat(beats) { beat() }

    companion object {
        /**
         * **Ten beats** (D-308, D-302): three to pair two phones the house was not ranging, one for
         * both phones' readings, and for a report two more of the hold, with four to spare. A hold
         * with an [until] stops as soon as the touch or the call lands.
         */
        const val HOLD_BEATS = 10
    }
}

/**
 * ⚠️ **STAND-IN — the other phones' radios, because five processes share no air** (D-217, D-308).
 *
 * The rehearsal runs one process per phone, on simulators, and a simulated radio in one process
 * cannot hear an advertisement in another; a simulator has no Nearby Interaction ranging at all
 * (its radio is `NoRanging`). So on the host this holds one [ContactUploader] per seat (the class
 * every phone runs), each with a [SimulatedRadio] and a [SimulatedRanging] in one [SimulatedAir],
 * and **splices what they hear into each phone's own upload as it reaches the host's door**
 * ([StandInDoor]): the Bluetooth slots and the ranging report are the stand-in's, and the request
 * slot, the clock probe and the main view field are the phone's own. The house's plan for the seat
 * goes back to the stand-in's uploader as well as down the phone's socket, where the phone's own
 * `NoRanging` applies nothing.
 *
 * **Why a splice and not a second upload** (D-308): every rule reads distance now, and the house
 * holds one upload stream per seat. A phone's own upload beside the stand-in's would say capacity 0
 * every second, and the pairing would let the stand-in's pairs go; and its empty distances would
 * cancel a report's hold the second it began. One upload per seat per second, as on phones.
 *
 * **What is stood in for is the radios and nothing above them**: the tokens are each seat's own
 * (the house may derive every seed, D-212), the window is the wall clock's, and the resolver, the
 * pairing, the gate, the rules and every push down are real. Out is the house's word, as it is on a
 * phone: a seat the state says is out is told `RevokeLanded`, which is the push its own phone would
 * have had. `ContactRadioBenchTest` plays the splice over real sockets.
 */
class HouseAir(
    private val house: RoundAuthority,
    private val window: () -> ContactWindow = { ContactWindow.containing(wallClockMillis()) },
) {
    val air = SimulatedAir()
    private val uploaders = mutableMapOf<Int, ContactUploader>()
    private var sent: RoundBody.Sighted? = null

    /** Issue every seat its seed and stand everybody a room apart. Call once the round is armed. */
    fun armed() {
        val seats = house.state.seats
        uploaders.clear()
        for (seat in seats) {
            val up = ContactUploader(
                air.radioFor(seat), window,
                ranging = air.rangingFor(seat, SimulatedAir.BENCH_CAPACITY),
                send = { body -> sent = body },
            )
            up.heard(RoundMessage.ContactSeedIssued(contactSeedFor(house.state.seed, seat)))
            uploaders[seat.index] = up
        }
        air.spreadOut(seats)
    }

    /**
     * **[seat]'s own upload, with the stand-in's radios in it**: one beat of the seat's stand-in
     * uploader, its Bluetooth slots and ranging report put in place of the phone's. A seat the
     * stand-in does not hold keeps its own upload.
     */
    fun spliced(seat: Seat, upload: RoundBody.Sighted): RoundBody.Sighted {
        val up = uploaders[seat.index] ?: return upload
        if (house.state.isOut(seat) && !up.isOut) up.heard(RoundMessage.RevokeLanded.Silent)
        sent = null
        up.beat()
        val standIn = sent ?: return upload
        return upload.copy(slots = standIn.slots, ranging = standIn.ranging)
    }

    /** The house's [plan] for [seat], to the stand-in's radio. */
    fun planned(seat: Seat, plan: String?) {
        if (plan != null) uploaders[seat.index]?.planned(plan)
    }
}

/**
 * **The host's door for every upload, with the rehearsal's stand-in spliced in while it is on the
 * air** (D-308). [air] is null in every round but a rehearsal's act with the radio, and then this is
 * [HouseDoors.sighted] and nothing else.
 */
class StandInDoor(private val doors: HouseDoors) {
    /** The stand-in on the air, or null. Set and cleared by [RoundCheat]. */
    var air: HouseAir? = null

    suspend fun sighted(seat: Seat, upload: RoundBody.Sighted): String? {
        val standIn = air ?: return doors.sighted(seat, upload)
        val plan = doors.sighted(seat, standIn.spliced(seat, upload))
        standIn.planned(seat, plan)
        return plan
    }
}
