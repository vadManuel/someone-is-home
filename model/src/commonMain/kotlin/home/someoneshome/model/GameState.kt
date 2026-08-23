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
    /**
     * **The moment the Access pool stopped, or null while it is running** (`gdd.md:373`, the Shift
     * ruling).
     *
     * A shift is the work interval between meetings (D-170), and the pool **runs during a shift and
     * pauses during a meeting**. Held exactly as [Egress.pausedAt] is, and for the same reason: a
     * cooldown is an absolute deadline, so pausing one means moving the deadline forward by the
     * length of the meeting when the lights go out. A recording that held only the deadline could
     * not tell a paused pool from a moved one.
     *
     * **One mark for the whole state rather than a field per [Cooldown]**, because the thing that
     * paused is not one seat's clock — it is the round. Every Insider walked to the same room at the
     * same moment.
     *
     * ⚠️ **It covers the Access pool and nothing else, and that is a scope rather than a ruling.**
     * `gdd.md:373` says of the Revoke only *"pauses or resets at meeting end"* and has never chosen
     * between the two; the Egress's own clock is D-151's and equally unruled across a meeting. Both
     * are left running here, unchanged, and the question is flagged rather than answered — see the
     * worklog. Widening this mark to them is a one-line change and a decision somebody has to take.
     */
    val accessPausedAt: Tick?,
    /**
     * **The markers this round has taken offline, and when each comes back** (`gdd.md:315`).
     *
     * See [Isolation], which carries what a scan at one of them answers and why. In marker order as
     * they were isolated, like every collection here; **it reaches no client at all** — no effect
     * carries it, so there is no schema row for one, and which cards are dark is something a player
     * finds out by walking to one.
     *
     * A marker with no row is online, which is the fail-closed direction: a round built by hand has
     * nothing dark rather than everything.
     */
    val isolated: List<Isolation>,
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
     * **The home, as far as the round is concerned: every ordinary card, its glyph and its room.**
     *
     * Recorded input, off the arming event, and the first host-authored geography the rules have
     * ever held (E-L4-1's mechanism, taken). It is here so that a work order can say *where*: a
     * client is handed the destinations of its own lines and nothing else, which is the narrowing
     * [WorkOrder.asLines] performs.
     *
     * **Nothing derives anything from it.** No draw reads it, no rule branches on it, and no
     * outcome depends on it — it is a lookup table, and the day something in `core` starts making
     * decisions about which room is where is the day the rules have acquired an opinion about a
     * house they have never been in.
     *
     * A List in registration order, like everything else here. Empty is lawful and means a round
     * armed without a home: every order comes out with no destinations, visibly, on every row.
     */
    val destinations: List<Destination>,
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
     * **When the Static jam lapses, or null when the house's monitoring is clear** (D-186).
     *
     * ### ONE value, house-wide, and it carries no actor — which is the ability
     *
     * *Loud and anonymous* is D-186's whole description of Static, and the anonymity is structural
     * here rather than a filter applied on the way out: **there is no seat on this field.** The
     * house cannot report who jammed it because the house did not keep it. A row per Insider, or a
     * `firedBy`, would be the one fact in this game that names an Insider outright, sitting in the
     * state waiting for somebody to render it — which is exactly the shape the presence plane was
     * split in two to avoid.
     *
     * It is house-wide for the same reason [egressReadyAt] is one number: the jam is a fact about
     * the house's monitoring, not about a seat. **The pool that pays for it is still per-seat**
     * ([cooldowns]), so two Insiders can each fire; what that produces is one window, not two.
     *
     * ### A second fire EXTENDS and can never shorten
     *
     * [withStatic] takes the later deadline. A jam that a second fire could cut short would make
     * firing into a running jam an act against your own side, and — worse — would make the length of
     * the window on screen a report about how many Insiders reached for it.
     *
     * **It fails at a meeting call and is not paused** (D-185). See `meetingCalled` in the rules.
     */
    val staticUntil: Tick?,
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
    /**
     * **What each seat asked to be called, for the length of one round** (D-115).
     *
     * Held for one job — putting a name on the reveal, so that the house says who it owned rather
     * than which chair they were sitting in — and dropped by [endRound] in the same instruction as
     * the lines. Two round-scoped texts, one wipe, and the wipe is the transition rather than a
     * call somebody has to remember.
     *
     * **Unlike [oneLines] this is not a secret**, and it is still not allowed to outlive the round:
     * D-115 scoped names to the round on purpose, the desk drops them, and `Transcript` renders
     * seats and never names. A List in seat order, like everything else here.
     */
    val names: List<SeatNamed>,
    /**
     * **How long the ballot runs, in seconds — the host's setting, locked at arming** (D-117).
     *
     * Read once, by the transition that opens the vote, and published on that push so every phone
     * in the house draws one clock from one number. Nothing else reads it and nothing may: a
     * second reader is a second place the ballot's length could be decided.
     */
    val voteWindowSeconds: Int,
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

    /** What to call this seat, or null for a phone that never said. See [names]. */
    fun nameOf(seat: Seat): String? = names.firstOrNull { it.seat.index == seat.index }?.name

    /**
     * Where this card is, or null for a card this round was not armed with.
     *
     * Null is the fail-closed direction and it is visible: a line whose card resolves to nothing
     * comes out with no destination, so a screen draws a blank rather than a room somebody invented.
     */
    fun destinationOf(marker: MarkerId): Destination? =
        destinations.firstOrNull { it.marker.value == marker.value }

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

    /**
     * **Whether this seat's Access pool is ready — asked against the pause, never against the raw
     * tick** (the Shift ruling, [accessPausedAt]).
     *
     * `accessPausedAt ?: at` is the whole of it, and it is [Egress.remainingAt]'s arithmetic one
     * system over: while the party is assembled the pool is frozen at the moment they were called
     * in, and when the lights go out the deadline moves forward by the length of the meeting. Both
     * halves are needed — the deadline shift alone would let a pool that expired *during* a meeting
     * read ready to a phone in the room, which is the pause not being a pause for as long as
     * anybody is looking.
     *
     * **The one reader of the pool's readiness**, so the rule lives in one place rather than in
     * however many Tier-2 handlers exist. A seat with no row has no pool: the fail-closed direction,
     * exactly as a seat with no Revoke row has no Revoke to arm.
     */
    fun accessReady(seat: Seat, at: Tick): Boolean =
        cooldownFor(seat, InsiderAbility.Access)?.readyBy(accessPausedAt ?: at) == true

    /**
     * **Whether this card is offline at [at]** (`gdd.md:315`). See [Isolation].
     *
     * Strictly before the return tick, so an isolation whose moment has come is over rather than
     * lasting one step longer — the same edge [Egress.lockedAt] takes, and taken the same way so
     * that two temporary states in one round cannot disagree about what *until* means.
     */
    fun isIsolated(marker: MarkerId, at: Tick): Boolean =
        isolated.any { it.marker.value == marker.value && at < it.until }

    /**
     * Take a card offline, replacing any isolation it is already serving.
     *
     * **Replaces rather than joins**, exactly as [Egress.lockingOut] does: two rows for one card
     * would make *is this dark* a question about which row somebody read first, and re-firing at an
     * already-dark marker is a lawful thing for an Insider to do — it is on the list, because a list
     * that omitted dark markers would report which cards another Insider had already taken.
     */
    fun withIsolated(isolation: Isolation): GameState = copy(
        isolated = isolated.filterNot { it.marker.value == isolation.marker.value } + isolation,
    )

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

    /** Whether the house's monitoring is jammed at [at]. See [staticUntil]. */
    fun staticRunning(at: Tick): Boolean = staticUntil?.let { at < it } == true

    /**
     * Open a jam until [until], **or extend one that is already running past it**.
     *
     * The later deadline wins and nothing here can shorten a window — see [staticUntil], where the
     * reason is that a shortened jam would report how many Insiders reached for it. A jam that has
     * already lapsed is simply replaced, because a stale deadline is not a jam.
     */
    fun withStatic(until: Tick): GameState {
        val held = staticUntil
        return copy(staticUntil = if (held != null && until < held) held else until)
    }

    /**
     * **Where the house thinks this seat is, and knowledge beats inference** (D-136).
     *
     * *The dedup rule only runs one way: a performer's own scan placement is ground truth, and no
     * other phone's sighting may relocate them. **Inference never overrides knowledge.*** This
     * function is that sentence, and it is the only place in the game where the two planes meet.
     *
     * A presence row with a card is [Placement.Known] and wins outright. **Nothing else can win**,
     * which is the whole of the ordering: the answer is a scan or it is nothing.
     *
     * ### ⚠️ The inference arm was DELETED with the ability that produced it, and this is not the
     * rule being dropped
     *
     * There used to be a second arm here reading a phantom occupant, and D-186 replaced the ability
     * that manufactured phantoms with one that has no target at all. **The ruling is explicit that
     * D-136 survives the sweep**: *inference never overrides knowledge is a rule about the spectator
     * map, which is still unbuilt and still needs it; what falls is only the phantom's arm of it.*
     *
     * So this function is the ordering with one grade currently constructible, and the day the
     * spectator map's BLE sightings land they belong **below** the presence row and are read here.
     * [Placement.Inferred] is kept for them. Writing a sighting into [presence] instead would be one
     * line of last-write-wins away from an inference erasing the house's own knowledge, and that is
     * the mistake this function exists to make impossible rather than merely discouraged.
     *
     * Nothing in `core` calls this today.
     */
    fun placementOf(seat: Seat): Placement? =
        presenceFor(seat)?.at?.let { Placement.Known(seat, it) }

    /** Seeded, recorded, monotonic. Never `Uuid.random()` — replay would mint different ids. */
    fun mintId(): Pair<EntityId, GameState> =
        EntityId(nextEntity) to copy(nextEntity = nextEntity + 1)

    /**
     * **The only writer of [outcome], and the only thing that empties [oneLines] and [names].**
     *
     * They happen together on purpose. D-116's promise is *deleted when the round ends*, D-115
     * scopes the names to the round, and the publish **is** the round ending — so the ending
     * effects are built from the state as it stands and this is applied to what is left, which
     * puts both deletions exactly one instruction after the last read. A separate `wipeLines()`
     * would be a second call somebody can forget on the day a fifth win route is added, and a
     * separate `wipeNames()` would be a third.
     *
     * Separate from [copy] so the transition stays greppable, exactly as [withEgress] is.
     */
    fun endRound(outcome: Outcome): GameState =
        copy(outcome = outcome, oneLines = emptyList(), names = emptyList())

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
        accessPausedAt: Tick? = this.accessPausedAt,
        isolated: List<Isolation> = this.isolated,
        systemIntegrity: Int = this.systemIntegrity,
        openSubroutines: List<OpenSubroutine> = this.openSubroutines,
        workOrders: List<WorkOrder> = this.workOrders,
        stations: List<MarkerId> = this.stations,
        activeMarkers: List<MarkerId> = this.activeMarkers,
        destinations: List<Destination> = this.destinations,
        presence: List<Presence> = this.presence,
        staticUntil: Tick? = this.staticUntil,
        meeting: Meeting? = this.meeting,
        egress: Egress? = this.egress,
        egressReadyAt: Tick = this.egressReadyAt,
        oneLines: List<OneLineHeld> = this.oneLines,
        names: List<SeatNamed> = this.names,
        voteWindowSeconds: Int = this.voteWindowSeconds,
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(
        armed, outcome, seats, insiderSeats, revoked, newlyRevoked, restrained, cooldownArmed,
        cooldowns, accessPausedAt, isolated, systemIntegrity, openSubroutines, workOrders, stations,
        activeMarkers, destinations, presence, staticUntil, meeting, egress, egressReadyAt, oneLines,
        names, voteWindowSeconds, nextEntity, seed,
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
            /**
             * The home's cards, glyphs and rooms. Defaulted empty for [workOrders]' reason, and
             * empty is the fail-closed direction: no destination resolves, so every work order
             * comes out with an empty destination column rather than a room somebody invented.
             */
            destinations: List<Destination> = emptyList(),
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
            /**
             * What each seat asked to be called (D-115). Defaulted empty for [oneLines]' reason,
             * and empty is the same fail-closed direction: a round armed without them reveals
             * blank names rather than somebody else's.
             */
            names: List<SeatNamed> = emptyList(),
            /**
             * **The ballot's window, in seconds. The one default here that is the design's own
             * number rather than an emptiness** (D-117, `gdd.md:412`).
             *
             * Empty would be a nought-second ballot, which is not a fail-closed round — it is a
             * meeting nobody can vote at, arriving in a dark house with the party standing in one
             * room. Forty-five is what the design says a ballot is when nobody has said otherwise.
             */
            voteWindowSeconds: Int = Balance.VOTE_WINDOW_SECONDS,
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
            // Nothing is paused and nothing is dark. A round that carried either across an arming
            // would open with a pool frozen by a meeting that happened in a different evening, or
            // with a marker offline that nobody had ever fired at -- and neither is announced.
            accessPausedAt = null,
            isolated = emptyList(),
            systemIntegrity = systemIntegrity,
            openSubroutines = openSubroutines,
            workOrders = workOrders,
            stations = stations,
            activeMarkers = activeMarkers,
            destinations = destinations,
            // Nobody is anywhere until somebody scans something. A round that opened with every
            // seat placed would place them where the house had guessed rather than where a card
            // was read, and D-136's whole dedup rule rests on placement being knowledge.
            presence = emptyList(),
            // The house's monitoring is clear until an Insider jams it (D-186).
            staticUntil = null,
            meeting = null,
            egress = null,
            egressReadyAt = egressReadyAt,
            oneLines = oneLines.sortedBy { it.seat.index },
            names = names.sortedBy { it.seat.index },
            voteWindowSeconds = voteWindowSeconds,
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
            accessPausedAt = null,
            isolated = emptyList(),
            systemIntegrity = 0,
            openSubroutines = emptyList(),
            workOrders = emptyList(),
            stations = emptyList(),
            activeMarkers = emptyList(),
            destinations = emptyList(),
            presence = emptyList(),
            staticUntil = null,
            meeting = null,
            egress = null,
            egressReadyAt = Tick(0),
            oneLines = emptyList(),
            names = emptyList(),
            voteWindowSeconds = Balance.VOTE_WINDOW_SECONDS,
            nextEntity = 1L,
            seed = 0L,
        )
    }
}
