package home.someoneshome.model

/**
 * **A phantom occupant: one seat, claiming a room it is not in, until a tick** (`gdd.md:313`).
 *
 * *False check-in in a room you are not in. Manufactures a phantom occupant.* This is that
 * sentence as state, and it is the only thing a Spoof leaves behind anywhere in the game.
 *
 * ### It is a SEPARATE LIST from [Presence], and the separation IS the dedup rule
 *
 * D-136: *the dedup rule only runs one way — a performer's own scan placement is ground truth, and
 * no other phone's sighting may relocate them. **Inference never overrides knowledge.*** A Spoof is
 * inference-grade: nobody scanned anything, no card resolved to a room, and the house has been told
 * a thing by a phone rather than shown it by a card.
 *
 * The obvious build writes the phantom into [GameState.presence] and lets the newest write win.
 * That build is one line of "last write wins" away from a Spoof erasing the house's own knowledge
 * of where somebody is standing, and nothing in a test would look wrong, because the row would be
 * well-formed and in the right place. **So the phantom cannot reach that list at all**:
 * [GameState.phantoms] is a different collection of a different type, [GameState.withPhantom] is
 * the only writer, and it does not know how to touch a [Presence]. The rule is not a check somebody
 * has to keep making — it is a thing the code cannot express.
 *
 * The ordering between the two planes is asked once, at read time, by [GameState.placementOf], and
 * that is what makes *both orders* the same answer: a scan before a Spoof and a scan after one both
 * end up as a presence row and a phantom row sitting side by side, resolved the same way.
 *
 * ### It is anchored on a ROOM, where a [Presence] is anchored on a card
 *
 * A [Presence] names a [MarkerId] because the house learned it from a card, and a card is a place
 * (D-123). A phantom names a room, because the Insider **picked a room off the home's plan** (the
 * owner's targeting ruling) and there is no card involved anywhere — that is the whole fiction of
 * the ability. The two types are therefore not interchangeable even in shape, which is a second
 * reason the wrong list is not writable by accident.
 *
 * The room is the host's own name for it, exactly as [Destination.room] is, and for the same
 * reason: it is the one piece of house geography that crosses the boundary, it is not a secret —
 * the room is a room in a house everybody is standing in — and it is host-authored text the rules
 * carry and never interpret.
 *
 * ### Not a `data class`, and never `@Serializable`
 *
 * [Presence]'s posture exactly, and it must stay that way for a stronger reason than presence's:
 * this row says *seat 3 claims to be in the Kitchen*, and a living player who could read it would
 * hold the one fact in the game that names an Insider outright — nobody but an Insider can put a
 * phantom anywhere.
 */
class Phantom(
    val seat: Seat,
    /** The host's own name for the room, off the plan the Insider tapped. See the KDoc above. */
    val room: String,
    /**
     * When it lapses. **An absolute deadline off the firing tick**, which is [Isolation.until]'s
     * arithmetic and every other cooldown's in the game: a remaining count would be a fact about
     * the moment somebody asked, and a tick replays.
     *
     * ⚠️ **The presence plane has no expiry and this one does**, which is not an inconsistency —
     * D-136 puts presence's expiry in the *spectator map*, driven by the performance window opening
     * and closing rather than by a clock, and that map is unbuilt. A phantom has no window to close
     * because nobody is standing anywhere, so a clock is the only thing that can end it. Escalated
     * in the worklog: the day the map lands, the two expiries have to be looked at together.
     */
    val until: Tick,
)

/**
 * **Where the house thinks a seat is, and how it came to think so** (D-136).
 *
 * The two grades of placement the design names, as two types rather than one type with a flag,
 * because a flag is a thing a caller can ignore and a type is a thing a caller must handle. The
 * spectator map is the only consumer this will ever have and it is unbuilt, hardware-dependent and
 * frozen — so this exists now, resolved and tested, for one reason: **the dedup rule is the easiest
 * thing in this system to get wrong later**, and it is much cheaper to write down while the
 * ability that first makes it matter is being built than to retrofit onto a map that already ships
 * a wrong answer.
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
     * Today the only way to build one of these is a Spoof. When the spectator map's BLE sightings
     * land they are the same grade and belong here — *adjacent-room bleed is accepted* (D-136), so
     * a sighting is exactly as trustworthy as a lie, which is the design saying out loud that the
     * map is not an oracle.
     *
     * **That a phantom and a sighting are the same type is the point**, not a shortcut: the day the
     * map can tell them apart is the day a Spoof is detectable, and detectable is the one thing
     * this ability may never be.
     */
    data class Inferred(override val seat: Seat, val room: String) : Placement
}
