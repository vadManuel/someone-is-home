package home.someoneshome.model

/**
 * Authority state. Holds ground truth, and therefore must never be `@Serializable`.
 *
 * **Ordered collections only.** `Map` and `Set` iteration order varies with hashing, and a
 * different iteration order produces a different effect order, which produces a recording that
 * does not replay. Seats are held as a sorted list and looked up by index.
 */
class GameState private constructor(
    val armed: Boolean,
    /**
     * **The round is over, and which of D-131's four routes ended it** — or null while it runs.
     *
     * This was a bare `ended: Boolean` with no writer, carrying the note that the round-end
     * condition was open and that the field existed only so the allowlist could name
     * [RoundState.Ended] and deny it things. Both halves are paid now: the rules write it, and
     * [ended] is derived from it so there is no second field that could disagree with the reason
     * about whether the evening is still going.
     *
     * **Nothing about it reaches a client as itself.** What every phone is told is
     * [Effect.RoundEnded], which carries the same two facts; this is the state they are read from.
     */
    val outcome: Outcome?,
    val seats: List<Seat>,
    val insiderSeats: List<Seat>,
    val revoked: List<Seat>,
    /**
     * **Revoked and not yet through a meeting.**
     *
     * D-134 splits the out in two and the split is a fact about *when*, not about who: a newly
     * Revoked player gets STAND AND WALK IN with a long haptic (D-135), while the couch —
     * everybody Revoked or Restrained at an earlier meeting — is called in with an ordinary buzz.
     * Cleared when a meeting closes, because after that meeting nobody in it is new.
     */
    val newlyRevoked: List<Seat>,
    /**
     * **Restrained: held by the room, then deauthorised by the house** (`gdd.md:1009`).
     *
     * **A second list, never a second use of [revoked].** Rule 9 forbids collapsing the two: a
     * Revoke is system power the house lends an Insider, a Restrain is a physical act the house
     * cannot prevent, and a state that stored them together would make the distinction a
     * convention somebody maintains rather than a fact the type carries. This closes the gap
     * [RoundState.Out] documented as live — *"nothing in GameState stores a restrained player"* —
     * and it closes it the way that comment said it had to be closed.
     */
    val restrained: List<Seat>,
    val cooldownArmed: List<Seat>,
    /**
     * **Every Insider ability's cooldown, already running when the round opens** (D-132).
     *
     * Each begins at **half** its normal duration, so the round opens with a guaranteed stretch of
     * peace — which closes the opening-Revoke problem structurally rather than by asking players
     * not to, and replaces F-004's *no initial cooldowns at arming* gap with a positive rule.
     *
     * A List in seat order, like everything else here, and drawn for **every** seat rather than
     * only for the Insiders. A list whose membership was the Insider list would be the Insider
     * list, and state that answers *is this seat an Insider* twice is state with two chances to be
     * read once.
     */
    val cooldowns: List<Cooldown>,
    val systemIntegrity: Int,
    /**
     * **What each seat currently has open, and what the house will grade it against** (D-109).
     *
     * A List in seat order, for the reason every other collection here is one: two seats' entries
     * graded in hash order emit their verdicts in hash order, and a recording that does not replay
     * is the only debugging instrument this game has, broken.
     *
     * **One row per seat, and the row is one line of that seat's [WorkOrder].** Nothing outside
     * the rules ever reads this: it carries the answer.
     *
     * A seat with no row has nothing open — the fail-closed direction, and the state a scan that
     * found nothing leaves behind (D-124).
     */
    val openSubroutines: List<OpenSubroutine>,
    /**
     * **Every seat's work order, drawn at arming, answer keys included** (D-129, D-114).
     *
     * One per seat and both roles: the Insider's is a fake of the same length drawn by the same
     * rule, so nothing about an order separates the two. In seat order, for the reason every
     * collection here is ordered.
     */
    val workOrders: List<WorkOrder>,
    /**
     * **Spares, Rack and Disposal — the Array Wipe circuit, drawn fresh every round** (D-122).
     *
     * Round state, and **never stored with the home**, which is the load-bearing half: a home that
     * remembered where the Rack was would turn the circuit into a fact players learn once and
     * keep, and by the second night in the same house there would be no circuit left to run. Three
     * distinct ordinary markers; the host designates nothing.
     */
    val stations: List<MarkerId>,
    /**
     * **The markers this round is using. The rest sit dark** (D-123).
     *
     * *A registered marker is a slot the house may use, not work that must be done.* Re-drawn each
     * round like the stations, so a home's geography stops being learnable in one evening.
     */
    val activeMarkers: List<MarkerId>,
    /**
     * **Who has a performance window open, and where** (D-111, D-136). See [Presence].
     *
     * *The house records, never recites.* One row per seat that has ever scanned, in seat order
     * like everything else here. Nothing outside the rules reads it, no effect carries another
     * player's row to any living phone, and the emit schema gives
     * [Effect.PresenceChanged] no entry at all — so the plane the spectator map will one day be
     * built on exists, is recorded, replays, and reaches nobody.
     */
    val presence: List<Presence>,
    /**
     * The meeting in progress, or null. **The whole lifecycle lives in here** — see [Meeting].
     *
     * Never reaches a client as itself: it carries every seat's live selection, which is the one
     * thing at a meeting only a player outside the system may read (D-075, D-117).
     */
    val meeting: Meeting?,
    /**
     * **The Egress in progress, or null. The whole lifecycle lives in here** — see [Egress].
     *
     * This was a `Boolean` stub with no writer, carrying D-133's *the meeting card is inert for the
     * duration* and nothing else, and it named the two things owed when the Egress landed:
     * something had to set it, and the pause needed a timer to pause. Both are paid here — the flag
     * became the state, and [egressRunning] is now derived from it so the rule D-133 wrote against
     * reads exactly as it did.
     *
     * Never reaches a client as itself: it carries every held Sync Pulse offer, which is who is
     * standing at which node with a live beat — presence data by another name (D-136).
     */
    val egress: Egress?,
    /**
     * **When the house will accept another Egress. ONE clock, shared by every Insider.**
     *
     * The panel's own line reads *SHARED WITH THE OTHER INSIDER*, and this is that sentence as
     * state: firing puts every Insider on the same cooldown, so a second Insider cannot follow the
     * first with a fresh one. **Not a list of rows** — see [Cooldown], which explains why a
     * house-wide clock keyed on a seat would make *shared* a convention somebody maintains.
     *
     * It begins the round **already running, at half** its duration (D-132), like every other
     * Insider cooldown: the round opens with a guaranteed stretch of peace, and the opening-Egress
     * problem closes structurally rather than by asking players not to.
     *
     * Not client-facing and never on the wire. An Insider's phone draws its own Egress tile from
     * its own input echo and from [Effect.EgressOpened], which the whole house receives — a
     * cooldown pushed to a subset would be an Insider announced by a timer.
     */
    val egressReadyAt: Tick,
    /**
     * **Every seat's one line, held for the length of one round and dropped when it ends** (D-116).
     *
     * The house holds these so it can do the two things the design asks of them: quote a Insider's
     * own line back to them at the reveal, and **publish the Insiders' lines to everybody when the
     * round ends**. Nothing else may read them, and nothing else does — [lineOf] has two callers in
     * the rules and both of them are the ending.
     *
     * **[endRound] drops the list, and that is the deletion promise made structural.** Not a wipe
     * somebody remembers to call: the transition that ends a round is the transition that empties
     * this, so a state in which the round is over is a state that is physically incapable of
     * quoting anybody. The desk keeps the same promise independently, one layer out — two copies,
     * two wipes, neither trusting the other.
     *
     * A List in seat order, like everything else here, and defaulted empty everywhere: a round
     * armed with no lines publishes blanks, which is the fail-closed direction for text.
     */
    val oneLines: List<OneLineHeld>,
    val nextEntity: Long,
    val seed: Long,
) {
    fun roleOf(seat: Seat): Role =
        if (insiderSeats.any { it.index == seat.index }) Role.Insider else Role.Resident

    fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }

    fun isRestrained(seat: Seat): Boolean = restrained.any { it.index == seat.index }

    /**
     * In the building, outside the system — Revoked or Restrained, said once.
     *
     * The two lists stay two lists (rule 9); this is the question that does not care which, and it
     * is asked by the scan's state gate, where *whether this phone is still in the round* is the
     * whole of what is being decided.
     */
    fun isOut(seat: Seat): Boolean = isRevoked(seat) || isRestrained(seat)

    /**
     * **The round is over.** Derived rather than stored — see [outcome].
     *
     * It feeds [roundStateOf], so it decides what every client is permitted to receive. That is
     * why it may not be a field of its own: a flag and a reason are two things a build can
     * de-synchronise, and this is the half nobody would notice had gone stale.
     */
    val ended: Boolean get() = outcome != null

    /** In the round: seated, and neither Revoked nor Restrained. Ordered, like everything here. */
    val livingSeats: List<Seat> get() = seats.filterNot { isRevoked(it) || isRestrained(it) }

    /**
     * **The Insiders still in the round.** The win check counts these and never the initial draw
     * (`gdd.md:213`).
     *
     * *A Insider who revokes another Insider has moved the goalposts against themselves and will
     * never know it. Do not tell them.* That sentence is a property of this one accessor: nothing
     * about the count reaches an effect, so the goalposts move in silence.
     */
    val livingInsiders: List<Seat> get() = insiderSeats.filterNot { isRevoked(it) || isRestrained(it) }

    /**
     * **The living who are Residents and nothing else** — the left-hand side of D-131's parity.
     *
     * Named *plain* rather than *Resident* because everyone is a Resident and some are also
     * Insiders (the vocabulary's first line): a `livingResidents` that quietly meant *not Insiders*
     * would be the one identifier in the module where the word had lost half its meaning.
     */
    val livingPlainResidents: List<Seat> get() = livingSeats.filterNot { roleOf(it) == Role.Insider }

    /** This seat's one line, or null for a seat that never handed one over. See [oneLines]. */
    fun lineOf(seat: Seat): String? = oneLines.firstOrNull { it.seat.index == seat.index }?.text

    /** In the building, outside the system. The complement of [livingSeats], in seat order. */
    val outSeats: List<Seat> get() = seats.filter { isRevoked(it) || isRestrained(it) }

    /**
     * **The house is on fire** — D-133's condition, unchanged in meaning and no longer a flag.
     *
     * Derived rather than stored, so there is no second field that could disagree with [egress]
     * about whether a countdown is on every widget in the building.
     */
    val egressRunning: Boolean get() = egress != null

    /** The only writer of [egress]. Separate from [copy] so the transition stays greppable. */
    fun withEgress(egress: Egress?): GameState = copy(egress = egress)

    /** What this seat has open, or null for a seat the house has assigned nothing. */
    fun openSubroutineFor(seat: Seat): OpenSubroutine? =
        openSubroutines.firstOrNull { it.seat.index == seat.index }

    /** This seat's work order, or null for a seat that was never drawn one. */
    fun workOrderFor(seat: Seat): WorkOrder? =
        workOrders.firstOrNull { it.seat.index == seat.index }

    /** When this seat's [ability] is ready, or null for a seat the round never drew one for. */
    fun cooldownFor(seat: Seat, ability: InsiderAbility): Cooldown? =
        cooldowns.firstOrNull { it.seat.index == seat.index && it.ability == ability }

    /** Replace one seat's work order, leaving every other row and the list's order alone. */
    fun withWorkOrder(replacement: WorkOrder): GameState = copy(
        workOrders = workOrders.map {
            if (it.seat.index == replacement.seat.index) replacement else it
        },
    )

    /** Replace one cooldown, leaving every other row and the list's order alone. */
    fun withCooldown(replacement: Cooldown): GameState = copy(
        cooldowns = cooldowns.map {
            val same = it.seat.index == replacement.seat.index && it.ability == replacement.ability
            if (same) replacement else it
        },
    )

    /**
     * Open one seat's Subroutine, leaving every other row and the list's order alone.
     *
     * **A seat with no row gets one, appended in seat order.** That is a scan doing its job: the
     * round is armed with nothing open, and the row is created the moment a card resolves to a
     * line of that seat's work order (D-123). The guard against inventing work is one layer up —
     * a scan can only open a line the order already holds — rather than here, where it would have
     * meant a seat's first scan silently doing nothing.
     */
    fun withOpenSubroutine(replacement: OpenSubroutine): GameState {
        val held = openSubroutines.any { it.seat.index == replacement.seat.index }
        return copy(
            openSubroutines =
                if (held) {
                    openSubroutines.map {
                        if (it.seat.index == replacement.seat.index) replacement else it
                    }
                } else {
                    (openSubroutines + replacement).sortedBy { it.seat.index }
                },
        )
    }

    /** This seat's performance window, or null for a seat the house has never placed. */
    fun presenceFor(seat: Seat): Presence? = presence.firstOrNull { it.seat.index == seat.index }

    /**
     * Replace one seat's presence row, leaving every other row and the list's order alone.
     *
     * **A seat with no row gets one, appended in seat order** — the same shape
     * [withOpenSubroutine] has, for a related reason: the round opens with nobody placed anywhere,
     * and the first thing that places a seat is that seat scanning a card (D-136 — a performer's
     * own scan is knowledge, and it is the only knowledge the house has).
     */
    fun withPresence(replacement: Presence): GameState {
        val held = presence.any { it.seat.index == replacement.seat.index }
        return copy(
            presence =
                if (held) {
                    presence.map {
                        if (it.seat.index == replacement.seat.index) replacement else it
                    }
                } else {
                    (presence + replacement).sortedBy { it.seat.index }
                },
        )
    }

    /** Seeded, recorded, monotonic. Never `Uuid.random()` — replay would mint different ids. */
    fun mintId(): Pair<EntityId, GameState> =
        EntityId(nextEntity) to copy(nextEntity = nextEntity + 1)

    /**
     * **The only writer of [outcome], and the only thing that empties [oneLines].**
     *
     * The two happen together on purpose. D-116's promise is *deleted when the round ends*, and
     * the publish **is** the round ending — so the ending effects are built from the state as it
     * stands and this is applied to what is left, which puts the deletion exactly one instruction
     * after the last read. A separate `wipeLines()` would be a second call somebody can forget on
     * the day a fifth win route is added.
     *
     * Separate from [copy] so the transition stays greppable, exactly as [withEgress] is.
     */
    fun endRound(outcome: Outcome): GameState = copy(outcome = outcome, oneLines = emptyList())

    fun copy(
        armed: Boolean = this.armed,
        outcome: Outcome? = this.outcome,
        seats: List<Seat> = this.seats,
        insiderSeats: List<Seat> = this.insiderSeats,
        revoked: List<Seat> = this.revoked,
        newlyRevoked: List<Seat> = this.newlyRevoked,
        restrained: List<Seat> = this.restrained,
        cooldownArmed: List<Seat> = this.cooldownArmed,
        cooldowns: List<Cooldown> = this.cooldowns,
        systemIntegrity: Int = this.systemIntegrity,
        openSubroutines: List<OpenSubroutine> = this.openSubroutines,
        workOrders: List<WorkOrder> = this.workOrders,
        stations: List<MarkerId> = this.stations,
        activeMarkers: List<MarkerId> = this.activeMarkers,
        presence: List<Presence> = this.presence,
        meeting: Meeting? = this.meeting,
        egress: Egress? = this.egress,
        egressReadyAt: Tick = this.egressReadyAt,
        oneLines: List<OneLineHeld> = this.oneLines,
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(
        armed, outcome, seats, insiderSeats, revoked, newlyRevoked, restrained, cooldownArmed,
        cooldowns, systemIntegrity, openSubroutines, workOrders, stations, activeMarkers,
        presence, meeting, egress, egressReadyAt, oneLines, nextEntity, seed,
    )

    companion object {
        /**
         * A freshly armed round. Every field is set explicitly.
         *
         * Deliberately not a `copy()` of the previous state: arming is the clean start of a
         * round, and anything carried across it is invisible to everyone.
         */
        fun armedRound(
            seed: Long,
            seats: List<Seat>,
            insiders: List<Seat>,
            systemIntegrity: Int,
            /**
             * The work order, drawn by the rules and stored here.
             *
             * Defaulted empty so a caller that only cares about the meter — every test that
             * builds an armed state by hand — does not have to invent one. An empty ledger is the
             * fail-closed direction: no seat has anything open, so every entry grades false.
             */
            openSubroutines: List<OpenSubroutine> = emptyList(),
            /**
             * The round's draw. Defaulted empty for the same reason [openSubroutines] is: a test
             * that only cares about the meter should not have to invent a home to arm in, and an
             * empty draw is the fail-closed direction — no order, so no entry grades true; no
             * stations, so no circuit; no active markers, so nothing is lit.
             */
            workOrders: List<WorkOrder> = emptyList(),
            stations: List<MarkerId> = emptyList(),
            activeMarkers: List<MarkerId> = emptyList(),
            cooldowns: List<Cooldown> = emptyList(),
            /**
             * D-132's half-cooldown for the house-wide Egress clock, computed by the rules.
             *
             * Defaulted to the start of time for the same reason [cooldowns] defaults empty: a
             * hand-built state that only cares about the meter should not have to arm an Egress
             * clock. **That default is the permissive direction and is deliberately the only one
             * in this constructor that is** — an unset clock means *ready*, and a test that arms a
             * round by hand can fire an Egress without inventing a tick. The fail-closed direction
             * would be a round nobody could ever hold an Egress in, which is a round that cannot
             * exercise the rule.
             */
            egressReadyAt: Tick = Tick(0),
            /**
             * Every seat's one line, from the house's desk (D-116).
             *
             * Defaulted empty for [openSubroutines]' reason, and empty is the fail-closed
             * direction here too: a round armed without them publishes blank lines at the reveal,
             * which is a visible nothing rather than an invisible somebody else's.
             */
            oneLines: List<OneLineHeld> = emptyList(),
        ) = GameState(
            armed = true,
            outcome = null,
            seats = seats,
            insiderSeats = insiders,
            revoked = emptyList(),
            newlyRevoked = emptyList(),
            restrained = emptyList(),
            cooldownArmed = emptyList(),
            cooldowns = cooldowns,
            systemIntegrity = systemIntegrity,
            openSubroutines = openSubroutines,
            workOrders = workOrders,
            stations = stations,
            activeMarkers = activeMarkers,
            // Nobody is anywhere until somebody scans something. A round that opened with every
            // seat placed would place them where the house had guessed rather than where a card
            // was read, and D-136's whole dedup rule rests on placement being knowledge.
            presence = emptyList(),
            meeting = null,
            egress = null,
            egressReadyAt = egressReadyAt,
            oneLines = oneLines.sortedBy { it.seat.index },
            nextEntity = 1L,
            seed = seed,
        )

        val EMPTY = GameState(
            armed = false,
            outcome = null,
            seats = emptyList(),
            insiderSeats = emptyList(),
            revoked = emptyList(),
            newlyRevoked = emptyList(),
            restrained = emptyList(),
            cooldownArmed = emptyList(),
            cooldowns = emptyList(),
            systemIntegrity = 0,
            openSubroutines = emptyList(),
            workOrders = emptyList(),
            stations = emptyList(),
            activeMarkers = emptyList(),
            presence = emptyList(),
            meeting = null,
            egress = null,
            egressReadyAt = Tick(0),
            oneLines = emptyList(),
            nextEntity = 1L,
            seed = 0L,
        )
    }
}
