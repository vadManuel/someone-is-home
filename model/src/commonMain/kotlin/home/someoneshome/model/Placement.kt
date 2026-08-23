package home.someoneshome.model

/**
 * **Where the house thinks a seat is, and how it came to think so** (D-136).
 *
 * The two grades of placement the design names, as two types rather than one type with a flag,
 * because a flag is a thing a caller can ignore and a type is a thing a caller must handle. The
 * spectator map is the only consumer this will ever have and it is unbuilt, hardware-dependent and
 * frozen — so this exists now, resolved and tested, for one reason: **the dedup rule is the easiest
 * thing in this system to get wrong later**, and it is much cheaper to write down while somebody is
 * thinking about it than to retrofit onto a map that already ships a wrong answer.
 *
 * ### ⚠️ This survived the ability that was built to exercise it, and it survived DELIBERATELY
 *
 * D-186 replaced Spoof with Static, and with Spoof went the phantom occupant — the only thing that
 * has ever constructed an [Inferred]. **D-186 says so explicitly and says this is not swept with
 * it**: *inference never overrides knowledge is a rule about the spectator map, which is still
 * unbuilt and still needs it; what falls is only the phantom's arm of it.*
 *
 * So [Inferred] has no constructor caller anywhere in the game today, and that is the honest state
 * of a grade whose only producer will be a BLE sighting on a map nobody has built. Deleting it
 * would delete the shape of the ruling along with the ability, and the next person to write that
 * map would have to derive D-136 from the log rather than find it in the type.
 *
 * Nothing in `core` branches on this. Nothing in `ui` can see it. See [GameState.placementOf].
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
    data class Inferred(override val seat: Seat, val room: String) : Placement
}
