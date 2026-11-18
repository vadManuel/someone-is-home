package home.someoneshome.model

/**
 * **Where the house thinks a seat is, and how it came to think so** (D-136).
 *
 * The two grades of placement the design names, as two types rather than one type with a flag,
 * because a flag is a thing a caller can ignore and a type is a thing a caller must handle.
 *
 * ### The spectator map's producer exists now, house-side only (O4)
 *
 * D-186 took the phantom occupant, the only thing that had ever constructed an [Inferred], and
 * kept the grade on purpose: *inference never overrides knowledge is a rule about the spectator
 * map, which is still unbuilt and still needs it.* O4 builds that map's input. A performer's
 * phone hears the phones near it, the house mints `Event.Sensed`, and the rules write one
 * [Inferred] per heard seat into `GameState.sightings` — **a different collection from the
 * presence plane**, which is the separation this file was kept for.
 *
 * Nothing reaches any client from here: no effect carries a placement, so the emit schema has
 * nothing to permit. Nothing in `ui` can see it. See [GameState.placementOf], the one place the two
 * grades are ordered.
 */
sealed interface Placement {

    val seat: Seat

    /**
     * **Knowledge: this seat scanned a card, and the house knows the card's room** (D-136).
     *
     * *A performer's own scan is knowledge.* The house did not infer this from a radio, it was
     * handed a card id by the phone that read the card, and the room is a lookup away in
     * [GameState.destinationOf].
     *
     * It carries the marker rather than a room because that is what the house actually holds; the
     * caller resolves it, which keeps the geography lookup in one place instead of copied into
     * every placement row.
     */
    data class Known(override val seat: Seat, val at: MarkerId) : Placement

    /**
     * **Inference: somebody said so, and nothing confirmed it.**
     *
     * The spectator map's BLE sightings are this grade — *adjacent-room bleed is accepted* (D-136),
     * which is the design saying out loud that the map is not an oracle. **Nothing constructs one
     * today**; see this file's header for why the grade is kept anyway.
     *
     * **It is a separate branch of the type from [Known] and it must stay one.** The obvious build
     * writes an inferred placement into [GameState.presence] and lets the newest write win — one
     * line of last-write-wins away from a sighting erasing the house's own knowledge of where
     * somebody is standing, with nothing in a test looking wrong because the row would be
     * well-formed and in the right place. The separation is not a check somebody has to keep
     * making; it is a thing the code cannot express, and [GameState.placementOf] is the one place
     * the two grades are ever ordered.
     */
    data class Inferred(
        override val seat: Seat,
        val room: String,
        /** When the house heard it: the tick of the sensor's upload (O4). Expiry runs from here. */
        val at: Tick,
    ) : Placement
}

/**
 * **How long each grade of placement lasts** (O4). ⚠️ **Every number here is provisional**: the
 * occupancy spec's Q5 and `gdd.md` A-7/A-8 leave all of them to playtest, and nothing in the design
 * names one. They are named once so a playtest changes one line each.
 *
 * In ticks of the house's clock, which the tick driver keeps to real time (D-269).
 */
object PlacementTiming {

    /**
     * ⚠️ **Provisional, 10 s.** A check-in that opened no window (NOTHING FOR YOU HERE, the T card)
     * still put the seat at a card, and is knowledge for this long: no sighting relocates them
     * inside it, exactly as an open window holds (D-136).
     */
    const val KNOWN_HOLD: Long = 10L * Balance.TICKS_PER_SECOND

    /**
     * ⚠️ **Provisional, 45 s.** A closed window's last place, or a sighting, stands this long and
     * then the seat is unplaced: a coverage hole, which is a feature (`gdd.md:524`).
     */
    const val EXPIRY: Long = 45L * Balance.TICKS_PER_SECOND
}
