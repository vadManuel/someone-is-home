package home.someoneshome.model

/**
 * **A marker the house has taken offline, and the tick it comes back at** (`gdd.md:315`).
 *
 * *Take a marker — or the Terminal — offline temporarily.* Held on [GameState] and never
 * `@Serializable`: which cards are dark is a fact about the house that reaches **no client at all**,
 * and there is deliberately no effect that carries it.
 *
 * ### The silence is the ability
 *
 * An Isolated marker answers a scan with D-124's **NOTHING FOR YOU HERE** — the same
 * [Effect.ScanAnswered] with the same null a registered card holding no work for you produces. That
 * is the whole of what a player learns, and it has to be: an announcement (*this marker is offline*,
 * *this marker was attacked*) would turn Isolate from a delay into a broadcast, and the broadcast
 * would name a room an Insider had chosen to interfere with. **An Isolated marker must not announce
 * itself as attacked.**
 *
 * ⚠️ **UNRULED, decided here, and flagged for the owner.** Nothing in the design says what a scan at
 * an Isolated marker shows. This is the honest default under D-124 and rule 1 — one kind, one
 * message per scan, only the content differing — and the alternative shapes are both worse: a new
 * refusal vocabulary builds the detector D-124 refuses (*registered but not mine* becomes separable
 * from *offline*), and emitting nothing is rule 1's forbidden shape outright.
 *
 * ⚠️ **Also unruled: an isolation does not pause across a meeting.** The Access pool does (see
 * [GameState.accessPausedAt]); this runs on the round's own clock, so a meeting called during one
 * can eat it. That is the simpler reading of *offline for N seconds* and it is recorded rather than
 * defended.
 *
 * **[until] is a tick, never a remaining count** — [Cooldown]'s argument exactly: the rules have no
 * clock, so *back at step 900* replays and *forty seconds left* is a fact about the moment somebody
 * asked.
 */
data class Isolation(val marker: MarkerId, val until: Tick)
