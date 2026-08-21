package home.someoneshome.model

/**
 * **How long the phone buzzes, decided by the house and carried as data.**
 *
 * D-102 ruled that a screen arriving unasked buzzes and that the buzz is **identical for every
 * player** — same pattern, same duration, including when an Insider's own Revoke lands, or it is
 * an audible tell in a silent house. D-135 closed the remaining question of *which* buzz: every
 * event buzzes, and the long one is reserved for five — the Egress, an incoming phone call,
 * STAND AND WALK IN for a newly Revoked player, the Restrained takeover, and the end of the
 * LIGHTS OUT countdown.
 *
 * **It rides the effect rather than living in a table in `ui`**, for D-134's reason: a client-side
 * lookup that could say *"…and if it was you, buzz differently"* is the device deciding a game
 * answer. The rules construct the value once, at the same place they construct the push, and the
 * device does what it is told.
 *
 * The actual vibration is platform work and is not in this module or the next one.
 */
enum class Haptic {
    /** The default. Every screen that arrives unasked. */
    Short,

    /** One of D-135's five. Adding a sixth is a decision, not a parameter. */
    Long,
}

/**
 * **How a meeting was called — and the only two ways there are** (D-121, D-133).
 *
 * A meeting is called by **physically scanning the meeting card**. The one exception is
 * **reporting a Revoked player**, which works from anywhere. There is no third way and no remote
 * button: *a remote call is a button that summons the whole house from a chair*, and making it a
 * walk to a known place puts the caller somewhere everyone can see them at the moment they would
 * most like to be unseen.
 *
 * **The distinction is load-bearing at exactly one place** and it is not the copy: D-133 makes the
 * meeting card **inert for the duration of an Egress** — a house on fire is not a house that
 * debates — while the report still triggers a meeting from anywhere. That is a refusal in the
 * admission gate, and the gate needs to be able to tell the two apart.
 *
 * A sealed type rather than a nullable `reported: Seat?`, because a discriminator carried by the
 * absence of a field is the shape rule 3 spends its whole length arguing against.
 */
sealed interface MeetingTrigger {

    /** Somebody walked to the meeting card and scanned it. Their scan is their check-in. */
    data object MeetingCard : MeetingTrigger

    /**
     * Somebody touched their phone to a Revoked player's phone (F-011's unconditional claim).
     *
     * [reported] is public by the time it matters — the meeting is where everyone finds out who is
     * out, and a player who has been Revoked walks in and sits down where the room can see them.
     */
    data class RevokeReported(val reported: Seat) : MeetingTrigger
}

/**
 * Where a meeting has got to.
 *
 * **Every phone in the house is on the same one**, which is what makes it safe to tell a client it
 * was refused for a phase reason (D-068, and see [RefusalReason.WrongMeetingPhase]): the players
 * are standing in one room looking at one clock.
 */
enum class MeetingPhase {
    /**
     * Walking in. The gate closes when **every living player and every out player** has been
     * accounted for (D-104) — presence over proceed-without, chosen with the stall named.
     */
    CheckIn,

    /** The talk. House-owned clock; a unanimous READY TO VOTE ends it early. */
    Discussion,

    /** The ballot. Live selections, an irrevocable READY, auto-lock at the buzzer (D-117). */
    Vote,

    /**
     * The result, and the LIGHTS OUT countdown under it.
     *
     * Two things happen inside this phase and they are not the same moment: at the **halfway**
     * mark the Restrained takeover reaches the losing seat (D-102, D-134's E1-1), and at zero
     * everyone else goes back to the round.
     */
    Tally,
}

/**
 * One seat's ballot: what their finger is on, and whether it has stopped being changeable.
 *
 * **[locked] is a fact, not a flag somebody keeps in sync.** D-117 makes READY irrevocable: it
 * converts the current [selection] into the actual vote and after it nothing can be changed. So a
 * later tap from this seat is **refused**, not ignored — see [Effect.VoteHeld], which re-asserts
 * what the house holds rather than letting the absence of an answer be the answer (rule 1).
 *
 * A null [selection] on a locked ballot is a **Skip**, which is D-075 narrowed by D-117:
 * skip-by-silence now applies only to a player who selected nothing at all for the whole window,
 * rather than to anyone who did not press a button.
 */
data class Ballot(val voter: Seat, val selection: Seat?, val locked: Boolean)

/**
 * **A meeting in progress — the whole of it, authority-side.**
 *
 * Held on [GameState] and therefore never `@Serializable`: it carries every seat's live selection,
 * which is the one thing at a meeting that only a player outside the system may read (D-075,
 * D-117). What reaches a client is constructed narrower at the emit boundary, per kind.
 *
 * **Ordered lists throughout, no sets.** Two seats checking in inside one step must produce the
 * same effect order on every replay, and a `Set` iterates in hash order (rule 4).
 */
data class Meeting(
    val caller: Seat,
    val trigger: MeetingTrigger,
    val phase: MeetingPhase,
    /**
     * Who is standing at the meeting area, in seat order.
     *
     * **The caller starts in here** — their scan of the meeting card *is* their check-in (D-121),
     * so the gate needs nothing new for them and they are counted from the instant the meeting
     * exists.
     *
     * Only ever counted, never published as a list: the ledger reports anonymous counts and
     * nothing finer (`gdd.md:294`), because an app that can confirm who was standing where and
     * when is an app adjudicating alibis.
     */
    val checkedIn: List<Seat>,
    /** Hands up for READY TO VOTE, in seat order. Counted, never named, for the same reason. */
    val ready: List<Seat>,
    /**
     * One row per voter, in seat order, created when the vote opens.
     *
     * **The revoked and the restrained are not in here.** Ghosts cast nothing (`gdd.md:417`) —
     * not for fairness but because voting is a communication channel: a player who knows who
     * revoked them and can vote is leaking that knowledge through the ballot, and the tally leaks
     * even when the vote is secret.
     */
    val ballots: List<Ballot>,
    /**
     * The seat the room voted to Restrain, decided at the buzzer and **not yet taken over**.
     *
     * It sits here for the length of the countdown's first half because the design puts the two
     * moments apart on purpose: *the group holds them; the house deauthorises them moments later,
     * because a restrained occupant is no longer useful to it* (`gdd.md:1009`). The takeover
     * screen arrives at the halfway mark so the player does not walk away when the countdown ends
     * (D-102), and that arrival is when the seat actually leaves the round.
     *
     * Null means the room restrained nobody — a Skip, a tie, or a vote nobody won.
     */
    val restrainPending: Seat?,
) {
    fun ballotFor(seat: Seat): Ballot? = ballots.firstOrNull { it.voter.index == seat.index }

    fun hasCheckedIn(seat: Seat): Boolean = checkedIn.any { it.index == seat.index }
    fun hasSaidReady(seat: Seat): Boolean = ready.any { it.index == seat.index }

    /** How many seats have locked a vote. **Locked, not selected** — D-117's `N OF 6 VOTED`. */
    val lockedCount: Int get() = ballots.count { it.locked }

    /** Replace one ballot, leaving the order alone. A seat with no ballot gets no ballot. */
    fun withBallot(replacement: Ballot): Meeting = copy(
        ballots = ballots.map { if (it.voter.index == replacement.voter.index) replacement else it },
    )

    companion object {
        /** A meeting the instant it is called: the caller is standing there and nobody else is. */
        fun called(caller: Seat, trigger: MeetingTrigger): Meeting = Meeting(
            caller = caller,
            trigger = trigger,
            phase = MeetingPhase.CheckIn,
            checkedIn = listOf(caller),
            ready = emptyList(),
            ballots = emptyList(),
            restrainPending = null,
        )
    }
}

/**
 * **Who the room Restrained, read off the locked ballots.**
 *
 * `gdd.md:413` and `:1007`: **most votes is Restrained; ties resolve to Skip.** D-075 leans on
 * that same sentence — *"combined with ties already resolving to Skip, the whole weight of
 * inaction sits behind restraining nobody"* — and D-131 states the consequence: at parity the
 * Insiders can always force the tie, so a Resident **majority** is the Residents' only real
 * instrument. This is that rule and not a stricter one; see the worklog note on D-131's wording.
 *
 * **Skip is a candidate, not an abstention.** It competes in the count, so a room that mostly
 * wants nobody Restrained gets nobody Restrained even when one name leads the rest.
 *
 * **Unlocked ballots are not counted here and cannot be**: the window auto-locks every ballot at
 * the buzzer (D-117), so by the time this runs there are none. Counting them would be the house
 * reading a selection as a vote, which is the one thing READY exists to separate.
 *
 * Counted in seat order into an ordered list. A `groupingBy` here would tally in hash order and
 * a two-way tie would resolve differently on replay — which is rule 4 arriving as *the wrong
 * player was Restrained*.
 */
fun tallyOf(ballots: List<Ballot>): Seat? {
    var skips = 0
    val counts = mutableListOf<Pair<Seat, Int>>()
    for (ballot in ballots) {
        if (!ballot.locked) continue
        val target = ballot.selection
        if (target == null) {
            skips++
            continue
        }
        val at = counts.indexOfFirst { it.first.index == target.index }
        if (at < 0) counts += target to 1 else counts[at] = target to (counts[at].second + 1)
    }

    val top = counts.maxOfOrNull { it.second } ?: return null
    if (top <= skips) return null
    val leaders = counts.filter { it.second == top }
    return if (leaders.size == 1) leaders.single().first else null
}
