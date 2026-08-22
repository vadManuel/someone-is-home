package home.someoneshome.model

/**
 * A fact. Past tense, already happened, and the only thing the rules consume.
 *
 * Events are what the recording stores and what replay feeds back. Every one carries the [Tick]
 * it occurred at, sampled at the edge — the rules never ask what time it is.
 *
 * **This set is deliberately partial.** It covers the spine the round trace walks; the systems it
 * does not name are still blocked on open design decisions, and inventing events for those here
 * would encode a guess as a decision. **F-001 is no longer one of them** — Egress node designation
 * was ratified as *two ordinary markers in non-adjacent rooms, drawn at fire time* — so the Egress
 * lifecycle is here. Isolate, Surge and the rest of the Access pool are not.
 */
sealed interface Event {
    val at: Tick

    /**
     * Balance values lock here and stamp into the recording. No mid-round edits.
     *
     * ### Two Insider numbers, and the whole of D-103 is in the difference
     *
     * [chosenInsiders] is the **host's setting** — a public fact, on every phone's lobby row, and
     * null for UNKNOWN. [insiders] is **what the house drew**, hidden from everyone until the round
     * ends. Only the first may size anything a player can measure: D-129 computes the work order's
     * length from public lobby facts alone, because sizing it against the draw would let order
     * length divide out the number D-103 spent a whole revision hiding.
     *
     * **The draw is recorded rather than re-derived**, which is why it is a field here at all. The
     * band clamps it before it arrives (`Arming.insidersFor`), balance values stamp into the
     * recording at arming, and the differential harness's whole method is rewriting this one field
     * — a draw computed inside the rules could not be exchanged between two seats, and the harness
     * would lose the only handle it has on role.
     *
     * [markers] are the home's **ordinary registered markers**, from which the house draws the
     * three stations (D-122) and the round's active set (D-123). The Terminal and the meeting card
     * are not among them: they are reserved shapes with fixed jobs, not slots the round may use.
     */
    data class RoundArmed(
        override val at: Tick,
        val seed: Long,
        val seats: List<Seat>,
        /** A List, not a Set. Hash order varies, and this is recorded input. */
        val insiders: List<Seat>,
        /** The host's setting, or null for UNKNOWN. **Public** — see the KDoc above. */
        val chosenInsiders: Int? = null,
        /**
         * The home's ordinary markers, in registration order. A List for [insiders]' reason.
         *
         * Defaulted empty so that a round armed to exercise something else — a meeting, the
         * admission gate — does not have to invent a house to hold it in. An empty home draws no
         * stations, lights no markers and issues an empty work order, which is the fail-closed
         * direction: nothing is assigned, so nothing grades true. **The recording's parser
         * defaults nothing** — a field missing from a recorded arming is fatal there, which is
         * where a silent default would actually cost something.
         */
        val markers: List<MarkerId> = emptyList(),
        /**
         * **Every seat's one line, off the house's desk** (D-116). See [GameState.oneLines].
         *
         * An input like [insiders] and for the same reason — the lines are typed in the lobby, on
         * a desk the rules have never held, and arming is the moment the house takes custody of
         * them. Defaulted empty like [markers]: a round armed to exercise a meeting does not have
         * to invent six confessions to hold it in.
         *
         * **It is the one recorded input the recording does not hold.** `Transcript` renders these
         * as a count and never as text, and the parser rebuilds blanks — so a recording replays
         * the same round and is physically incapable of quoting anybody. That is D-116's promise
         * kept in the one artefact of this game that outlives the evening.
         */
        val oneLines: List<OneLineHeld> = emptyList(),
    ) : Event

    /**
     * A card was read. **This is what arms a Subroutine, and the only thing that does** (D-110).
     *
     * It emits nothing to anybody, in either outcome — a scan that found work for you and a scan
     * that did not look identical from outside this event, which is D-124's ruling held one layer
     * down from the copy that implements it.
     */
    data class MarkerScanned(override val at: Tick, val actor: Seat, val marker: MarkerId) : Event

    /**
     * **An entry handed over at a marker — a report of what was entered, never a claim that it was
     * right** (D-109).
     *
     * This replaced `SubroutineCompleted`, and the rename is the ruling. A client asserting a
     * *completion* is a client grading its own work, which is the one thing the whole Subroutine
     * treatment is built to keep off the phone: the entry types in `ui` are physically incapable
     * of holding the answer, so the phone could not honestly send that event even if it wanted to.
     * The house holds what was asked, receives what was entered, and concludes.
     *
     * **Abandonment is not here and must not be added** (D-111). Walking away means the entry is
     * never sent: the house grades only what arrives, holds no partial state, and therefore has no
     * abandonment count that could become a behavioural channel separating a real Subroutine from
     * a fake. The presence plane reports *performing at room X* and that is a different report with
     * a single consumer (D-136).
     */
    data class SubroutineReturned(
        override val at: Tick,
        val actor: Seat,
        val marker: MarkerId,
        /** A List, not a Set. Order is the answer in half of the six, and this is recorded input. */
        val entered: List<Int>,
    ) : Event

    /**
     * **The performance window closed without an entry** — STOP NOW, or a step away from the
     * marker (D-111).
     *
     * One event for both, and **it carries no reason**. That is the ruling rather than a saving:
     * a reason field is an abandonment record, and an abandonment record is exactly the
     * behavioural channel D-111 split the two planes to close — how often somebody walks away from
     * work that was never going to count is a statistic separating a real Subroutine from a fake,
     * published by nobody and readable by anyone holding the log.
     *
     * **It carries no marker either.** Where this player was, is what the house recorded when they
     * scanned; a report naming its own location would be a phone asserting a placement, and
     * *inference never overrides knowledge* (D-136).
     *
     * The step-away half needs proximity hardware that does not exist yet, so on today's phones
     * only STOP NOW produces this. D-110 recorded that as design intent and this is where it
     * lands: a device-side rule about the player's own phone, adding no report to the work plane.
     */
    data class PerformanceEnded(override val at: Tick, val actor: Seat) : Event

    /** Armed, not fired. The cooldown starts here — a botched stalk costs a full cooldown. */
    data class RevokeArmed(override val at: Tick, val actor: Seat) : Event

    /** Phone touched to phone. Physical, so it is reported, never requested. */
    data class ContactMade(override val at: Tick, val actor: Seat, val target: Seat) : Event

    /**
     * **A meeting was called, and how** (D-121).
     *
     * Two ways and no third: the caller scanned the meeting card, or somebody reported a Revoked
     * player. [MeetingTrigger] carries which, because the admission gate has to tell them apart —
     * the card is inert during an Egress and the report is not (D-133).
     *
     * The caller's check-in is not a second event. Their scan *is* their check-in and they are
     * counted from the instant the meeting exists.
     */
    data class MeetingCalled(
        override val at: Tick,
        val caller: Seat,
        val trigger: MeetingTrigger,
    ) : Event

    /** I'M HERE. One phone, standing at the meeting area. The gate is the house's (D-104). */
    data class MeetingCheckedIn(override val at: Tick, val seat: Seat) : Event

    /** READY TO VOTE. One hand up; only a **unanimous** one ends the talk early. */
    data class ReadyToVoteDeclared(override val at: Tick, val seat: Seat) : Event

    /**
     * A finger landed on a name, or on Skip.
     *
     * **Every selection tap transmits live** (D-117) — the stream is not an optimisation, it
     * exists so players outside the system can watch the vote happen, which is most of what makes
     * the out's meeting screen worth looking at (D-134).
     */
    data class VoteSelected(override val at: Tick, val voter: Seat, val target: Seat?) : Event

    /**
     * READY: the current selection becomes the vote, **irrevocably** (D-117).
     *
     * **It carries no target, and that is the ruling rather than a saving.** The button is a
     * readiness signal that converts whatever is already selected; a target here would let a
     * client lock a vote it never transmitted, and would make the live selection stream — the
     * whole reason the out have anything to watch — optional decoration.
     *
     * This and [VoteSelected] together replaced a single `VoteCast(voter, target)`, which was the
     * *changeable until the clock ends* model D-117 superseded.
     */
    data class VoteLocked(override val at: Tick, val voter: Seat) : Event

    /** The discussion clock ran out. The house's clock, sampled at the edge like every other. */
    data class DiscussionClosed(override val at: Tick) : Event

    /**
     * The buzzer. **Whatever is selected when the clock ends locks itself** (D-117), and a seat
     * that selected nothing at all for the whole window is a Skip (D-075, narrowed).
     */
    data class VoteWindowClosed(override val at: Tick) : Event

    /**
     * The LIGHTS OUT countdown reached its halfway mark.
     *
     * A separate fact from the countdown ending because two different things happen: here the
     * Restrained takeover reaches the losing seat — *so they do not walk away when the countdown
     * ends* (D-102) — and only at zero does everybody else go back to the round.
     */
    data class TallyHalfwayReached(override val at: Tick) : Event

    /** Lights out. The meeting is over and the round resumes. */
    data class MeetingClosed(override val at: Tick) : Event

    // ---- The Egress ---------------------------------------------------------------------------

    /**
     * **A Insider fired an Egress, and the house has already picked what kind and where**
     * (`gdd.md:349`, F-001 as ratified).
     *
     * ### Both draws ride the event, and neither is re-derived
     *
     * [type] is the house's coin toss between Beacon and Tether — *a Insider triggers it; the house
     * picks which one* — and [nodes] are the two ordinary registered markers containment has to
     * happen at. Both are chosen **above the rules** by `egressFor`, for the reason `routeScan` and
     * `insidersFor` sit there: choosing the nodes needs the home's *shape* — which rooms touch
     * which — and the rules have never held house geography.
     *
     * So they arrive as recorded inputs, exactly as `RoundArmed.insiders` does, and a replay
     * reproduces the same pair and the same label rather than re-rolling them against a home the
     * recording does not carry.
     *
     * ### Nothing here says whether it will land
     *
     * The shared cooldown may still be running, in which case the house does nothing with this
     * beyond spending the actor's answer — see the rules. **The event is a fact about a finger on a
     * button**, and it is recorded whether or not a house caught fire, because a refusal that left
     * no trace is the invisible drop D-066 exists to refuse.
     */
    data class EgressFired(
        override val at: Tick,
        val actor: Seat,
        val type: EgressType,
        /** Two ordinary registered markers in non-adjacent rooms, in draw order (F-001). */
        val nodes: List<MarkerId>,
    ) : Event

    /**
     * **One participant's four taps on the Sync Pulse, handed over** (`gdd.md:355`).
     *
     * *Both phones pulse haptically in unison off a house-scheduled timestamp; both players tap on
     * the beat four times.* This is one phone's half of that, and it is named for
     * [SubroutineReturned]'s reason: **a report of what was entered, never a claim that it was
     * right.** The device holds the taps; the house holds the schedule and grades them.
     *
     * ### It carries no node, and that is the ruling rather than a saving
     *
     * Where this player is standing is what the house recorded when they **scanned** the node's
     * card — *a performer's own scan is knowledge* (D-136) — and a phone naming its own node would
     * be a client asserting a placement. It would also be the cheapest cheat in the game: sit on
     * the couch, claim the Landing, and contain an Egress from a chair. [PerformanceEnded] carries
     * no marker for exactly this reason and this is the same decision.
     *
     * ### The taps are ticks, not a verdict
     *
     * [taps] is when the finger landed, sampled at the edge like every other timestamp. A boolean
     * *"I was on the beat"* would be the phone grading its own work, which is the one thing the
     * whole Subroutine treatment is built to keep off the device.
     */
    data class SyncPulseReturned(
        override val at: Tick,
        val actor: Seat,
        /** A List, not a Set. Order is part of what was entered, and this is recorded input. */
        val taps: List<Long>,
    ) : Event

    /**
     * **The countdown reached zero and nobody contained it** (`gdd.md:361`, D-131).
     *
     * A house push on the authority's own clock, exactly as [DiscussionClosed] and
     * [VoteWindowClosed] are: the rules have no clock, so *the timer ran out* arrives as an event
     * stamped at the edge. The admission gate refuses it when there is no Egress to expire and
     * while one is paused, so the rules never have to ask whether the house means it.
     *
     * **Insiders win outright**, and a running Egress outlives its Insiders (D-131) — Restraining
     * the last one does not stop this arriving.
     */
    data class EgressExpired(override val at: Tick) : Event
}
