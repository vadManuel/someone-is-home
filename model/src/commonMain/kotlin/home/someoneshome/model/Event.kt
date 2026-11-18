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
 * lifecycle is here. **Surge, Isolate and Static are all here now** — the first two on the owner's
 * targeting rulings, and Static on D-186, which replaced the third ability outright and left the
 * only fire in the game that names nothing at all.
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
     *
     * ### They carry the room and the glyph now, which is E-L4-1's mechanism taken
     *
     * They were bare [MarkerId]s, on the argument that keeping host-authored geography out of
     * `core` was worth the cost — and the cost turned out to be the whole destination column of
     * every work order (E-C2-1). A [Destination] is a card, a glyph id and a room name, which is
     * what a player in a dark house navigates by and is nothing else: no seat, no work, no answer.
     * The rules hold the table and hand out one seat's rows of it; nothing publishes the map.
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
        val markers: List<Destination> = emptyList(),
        /**
         * **How long the ballot runs, in seconds — the host's lobby setting, locked here** (D-117).
         *
         * *45 seconds, host-changeable in lobby settings.* The control has existed on the host's
         * lobby row since U6 and reached nothing: `LobbyLink` had no arm, `LobbyBody` had no arming
         * case, and this event had no settings, so the number moved and no clock followed it. This
         * field is the whole of that gap.
         *
         * **Public, like [chosenInsiders] and for the same reason** — the host set it in a lit room
         * with the party standing there. It is not drawn from anything and it is not re-derived:
         * balance values lock at arming and stamp into the recording, so a replay runs the ballot
         * the evening actually ran rather than the default this build happens to carry.
         *
         * Defaulted to [Balance.VOTE_WINDOW_SECONDS] so a round armed to exercise something else
         * gets the design's own number rather than zero — a nought-second ballot is not the
         * fail-closed direction here, it is a meeting nobody can vote at.
         */
        val voteWindowSeconds: Int = Balance.VOTE_WINDOW_SECONDS,
        /**
         * **What to call each seat, off the house's desk** (D-115). See [GameState.names].
         *
         * A recorded input like [oneLines] and held to the same promise: round-scoped, dropped when
         * the round ends, and rendered by `Transcript` as **seats and never as names**. The house
         * takes custody at arming so that the reveal can say who somebody is without the rules ever
         * having held a lobby.
         *
         * Defaulted empty. A round armed without them reveals blank names, which is a visible
         * nothing rather than an invisible somebody else's.
         */
        val names: List<SeatNamed> = emptyList(),
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
        /**
         * **The T card, so that a scan of it can be answered as the station** (D-199).
         *
         * The reserved cards used to stay off this event entirely, and the cost was that the rules
         * could not tell the Terminal from any other registered card: a scan of it came back
         * NOTHING FOR YOU HERE, and the station had no way to hold a reader. **The id and nothing
         * else** -- no room, because the station's room is said aloud by the host during setup
         * and the rules need only recognise the paper.
         *
         * Defaulted null for [markers]' reason: a round armed to exercise something else has no
         * station, and a scan in it can only be work.
         */
        val terminal: MarkerId? = null,
        /**
         * **The home's plan, narrowed: storeys, cells and room names, no card of any kind** (O2).
         *
         * A recorded input, like [markers], and for the same reason: the house holds the plan for
         * the round (`RoundAuthority.home`) and the rules had no copy, so nothing they decide could
         * know which rooms touch which. The one thing that reads it is the spectator map's error
         * (O4: a phantom lands only beside an occupied room, `gdd.md:491`). See [HomePlanView] for
         * why it is the narrow type and why it reaches no client.
         *
         * Defaulted empty for [markers]' reason. **The recording's parser defaults nothing.**
         */
        val plan: HomePlanView = HomePlanView.EMPTY,
        /**
         * **The room the host put the T card in, or null** (O2). So that a seat placed at the
         * station resolves to a room on the house's own map, as every ordinary card already does
         * through [markers].
         *
         * [terminal] still carries the id alone, and that sentence about it stands for every
         * phone: *the station's room is the host's to say aloud*. This is the house's copy, held
         * in the rules and reaching no client (no effect names it). Defaulted null for
         * [terminal]'s reason.
         */
        val terminalRoom: String? = null,
    ) : Event

    /**
     * **A performer's phone heard these phones nearby, so the house places them in its room**
     * (D-136, D-212, O4).
     *
     * *While a player scans and performs a Subroutine, their phone BLE-scans for nearby phones and
     * places those players in that room.* This is that sentence as a fact. The house mints it above
     * the rules from a sighting upload, exactly as it mints [ContactMade] (D-213): the sensor is the
     * uploading seat, [room] is where the house already *knows* the sensor stands (the card it
     * scanned, resolved on the house's own map), and [heard] is every living seat whose token the
     * upload carried at room range, in seat order. A recorded input, so a replay places the same
     * seats in the same rooms without a radio.
     *
     * [heard] may be empty: the sensor's own room is still observed, and an empty room is a fact.
     *
     * **It emits nothing, to anybody, ever.** The rules write it into the spectator map's own
     * collection and capture one [Observation]; no effect carries either, so no schema row is
     * asked for. Nothing about it reads a role, so a role-swapped round mints the same stream.
     */
    data class Sensed(
        override val at: Tick,
        val sensor: Seat,
        val room: String,
        /** A List, not a Set, in seat order: recorded input. */
        val heard: List<Seat>,
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

    /**
     * **The reader is no longer reading the Terminal** (D-199, D-203).
     *
     * One event for every way a reading ends, and **it carries no reason**, for [PerformanceEnded]'s
     * argument: a reason would be a record of how somebody left the station, and the only thing
     * the lock needs is that they did. A phone sends it when its live view or timelapse closes for
     * any cause -- BACK, STOP NOW, the motion budget -- and the host composes it for a seat whose
     * connection dropped, because a phone that is gone cannot say so and the station would
     * otherwise stay held for the rest of the round.
     *
     * From a seat that is not the reader it changes nothing, and it emits nothing either way.
     */
    data class TerminalLeft(override val at: Tick, val actor: Seat) : Event

    /** Armed, not fired. The cooldown starts here — a botched stalk costs a full cooldown. */
    data class RevokeArmed(override val at: Tick, val actor: Seat) : Event

    // ---- Tier 2, on the shared Access pool ------------------------------------------------------
    //
    // Both are a finger on a row of a list the house sent, so both are fully determined by what the
    // phone did, which connection did it, and when it arrived -- which is the line `factOf` holds,
    // and it is why these two DO have an Intent where `EgressFired` does not. The house makes no
    // draw here: there is no coin toss and no geography to consult, only the target the player
    // picked off a roster the house had already narrowed for them.
    //
    // Static is the third and it passes the same test by having nothing in it at all: no target
    // seat, no target card, no room. What the phone did is press a tile, which connection did it is
    // the actor, and when it arrived is the tick -- there is nothing left for the house to compose
    // from a second source, which is the narrowest a fire can be (D-186).

    /**
     * **A Insider fired a Surge at a seat** (`gdd.md:313`, `:342`, `:392`).
     *
     * [target] is a seat and it is **content, not attribution** — who the ability is aimed at, off a
     * roster the house drew, exactly as [VoteSelected]'s target is. The actor still comes from the
     * connection and from nowhere else.
     *
     * **[target] may be [actor], and nothing anywhere special-cases it.** *Insiders can target
     * themselves with every ability* (`gdd.md:393`), because if only Residents could ever be
     * targeted then being visibly targeted would be evidence of innocence and every ability would
     * double as an alignment test. Self-targeting shares the pool (`gdd.md:401`): buying cover has
     * to cost offensive capability, or self-sabotage is a free alibi generator.
     *
     * ### Nothing here says whether it will land
     *
     * The pool may still be running, in which case the house does nothing with this beyond answering
     * the actor — see the rules. **The event is a fact about a finger on a row**, and it is recorded
     * whether or not a phone screamed, because a refusal that left no trace is the invisible drop
     * D-066 exists to refuse. [EgressFired] carries the same sentence and it is the same decision.
     */
    data class SurgeFired(override val at: Tick, val actor: Seat, val target: Seat) : Event

    /**
     * **A Insider fired an Isolate at a marker** (`gdd.md:315`, `:621`, D-150).
     *
     * [target] is a printed card, off the list the house sent that seat
     * ([Effect.IsolateTargetsOffered]) — so it is content in the same sense
     * [Event.MarkerScanned]'s marker is: a card id, and no claim about where anybody is standing.
     * **Range is not checked and there is no mechanism to check it with** — see the rules, where
     * that is written down as the flag it is.
     *
     * ### An Egress node arriving here is a stale list, not a forge
     *
     * The nodes are excluded from the list for the duration of an Egress (D-150), and the gap
     * between a list being drawn and a finger landing on it is real: the Egress can fire in between.
     * The rules refuse it there — **without charging the pool**, which is the half of D-150 that
     * matters, because charging a *shared* budget for a shot the house had already decided could not
     * land is a tax on a teammate paid in the dark by somebody who will never learn why.
     */
    data class IsolateFired(override val at: Tick, val actor: Seat, val target: MarkerId) : Event

    /**
     * **A Insider fired a Static: the house's monitoring goes down for ten seconds** (D-186).
     *
     * ### It has NO TARGET, and the absence is the ability rather than a field somebody forgot
     *
     * The Surge names a seat off a roster and the Isolate names a card off a list, both drawn by the
     * house and published to that phone. **Static names nothing.** There is no picker, no list and
     * no plan: it is fired in place from page 2 under the two-second hold, and its scope is the
     * whole house. D-186 replaced an ability whose target was a room on a map with one that asks the
     * Insider to reason about nothing at all — *you fire it because you are about to do something,
     * and it costs you nothing to understand.*
     *
     * That also supersedes D-175's third answer: *Surge picks a person from a LIST, Isolate picks a
     * marker from a LIST, Spoof picks a room from the MAP* — **the map answer is gone with the
     * ability.** The first two stand, and so does the argument under them.
     *
     * ### Self-targeting is not a case here either, and for a wider reason than Spoof's
     *
     * `gdd.md:393` requires every ability to be self-inflictable. Static jams the firing Insider's
     * own monitoring alongside everybody else's, because the jam is house-wide and the house does
     * not know who fired it — so an Insider standing at the Terminal when their own Static lands
     * reads the same noise a Resident does. The requirement is satisfied by the ability's shape and
     * there is nothing here to special-case.
     *
     * ### Nothing here says whether it will land
     *
     * [SurgeFired]'s sentence and the same decision: the Access pool may still be running, in which
     * case the house does nothing with this beyond answering the actor. **The event is a fact about
     * a finger on a tile**, recorded either way, because a refusal that left no trace is the
     * invisible drop D-066 exists to refuse.
     */
    data class StaticFired(override val at: Tick, val actor: Seat) : Event

    /** Phone touched to phone. Physical, so it is reported, never requested. */
    data class ContactMade(override val at: Tick, val actor: Seat, val target: Seat) : Event

    /**
     * **This phone's radio went down, and on which side of D-187's line** (T2, D-222; D-223).
     *
     * A fact the phone reports, never a request (rule 10): its own radio told it so. Once per fault
     * onset, whatever the radio does next, and the same from every phone whatever its role or
     * whether it is out. **[cause] is the only thing it says**: [RadioCause.Person] for Bluetooth
     * switched off or its permission withdrawn, or the phone locked or taken out of the app
     * mid-round (suspended, so its radio is silent; reported once it is back, D-229),
     * [RadioCause.Device] for a reset or unsupported hardware. The upward body is one fixed shape of one length (`RoundBody.RadioFaulted`), so it
     * cannot say more about one phone than another. The seat is the connection's.
     *
     * Silent on the phone in every case (rule 6). A [RadioCause.Person] outage is deliberate
     * evasion and the house names the seat in the next meeting's notice; a [RadioCause.Device] one
     * is recorded here, in the round's recording and the house's log, and announced to nobody
     * (D-187: *genuine radio loss earns no notice*).
     */
    data class RadioFaulted(override val at: Tick, val seat: Seat, val cause: RadioCause) : Event

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
        /**
         * **When the Sync Pulse's first pulse lands, on the host's monotonic clock, in
         * milliseconds** (C2): the moment the house drew the Egress plus
         * [Balance.SYNC_PULSE_LEAD_MILLIS], every later pulse [Balance.SYNC_PULSE_INTERVAL_MILLIS]
         * after it.
         *
         * Milliseconds of the host's clock and not a tick, because ticks do not keep wall time
         * (see [Balance.SYNC_PULSE_LEAD_MILLIS]) and a haptic beat in two hands has to; it is the
         * clock every phone measures its offset to (C1). [at] stays a tick (ARCH D5). **It is a
         * recorded input, read at the edge** like the nodes, so a replay grades the same taps
         * against the same grid without anybody reading a clock.
         */
        val pulseFrom: Long,
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
     * ### The taps are times on the house's clock, not a verdict
     *
     * [taps] is when the finger landed, **in milliseconds of the host's monotonic clock** as this
     * phone measured it (C1, C2) — the timeline [EgressFired.pulseFrom] is on — sampled at the edge
     * like every other timestamp. Not ticks: no phone knows the house's tick. A boolean *"I was on
     * the beat"* would be the phone grading its own work, which is the one thing the whole
     * Subroutine treatment is built to keep off the device.
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
