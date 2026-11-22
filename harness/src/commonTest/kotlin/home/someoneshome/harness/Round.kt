package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick

/**
 * The shared round fixture.
 *
 * One copy, because stories 0.3, 0.4, 0.5 and 0.6 all assert against the same round and two
 * copies of a fixture drift into two different rounds without either test noticing.
 */
internal val SEATS = (0 until 8).map { Seat(it) }
internal val INSIDERS = listOf(Seat(1), Seat(5))

/** The home the fixture round is played in. Eight ordinary markers is D-127's floor. */
internal val MARKERS = (0 until 8).map { MarkerId("m$it") }

/** The T card. Not among [MARKERS]: the reserved cards are never round markers (D-122). */
internal val STATION = MarkerId("TERM")

/**
 * **The same home, with a glyph and a room on every card** — what arming actually takes.
 *
 * Two cards to a room, because a real home puts them that way and a fixture with one room per card
 * could never produce two lines of one order pointing at the same room name. The glyphs come off
 * the real roster in order, so no two cards here share a shape.
 */
internal fun placed(cards: List<MarkerId>): List<Destination> =
    cards.mapIndexed { i, card ->
        Destination(card, MarkerShapes.all[i % MarkerShapes.all.size].id, "ROOM ${i / 2}")
    }

/**
 * **Every seat's one line, on the fixture's path** (D-116).
 *
 * On the path rather than only in a unit test, because what is being covered is a *rendering*
 * decision: `Transcript` writes these as a list of seats and never as text, so a recording is
 * physically incapable of quoting anybody, and the parser rebuilds blanks. A fixture that armed
 * without them would round-trip an empty list and prove nothing about either half.
 *
 * The text names its own seat so that a leak is legible on sight — `line-3` appearing anywhere in
 * a recording is the whole of the report.
 */
internal val LINES = SEATS.map { OneLineHeld(it, "line-${it.index}") }

/**
 * **What each seat asked to be called** (D-115), on the fixture's path for [LINES]' reason: the
 * rendering decision is the thing being covered, and a recording writes these as seats and never
 * as names. Deliberately distinguishable from a line on sight, so a leak of either is legible.
 */
internal val NAMES = SEATS.map { SeatNamed(it, "NAME-${it.index}") }

/**
 * How far the clock moves between passes. Wider than half a Revoke cooldown, deliberately — see
 * where it is used.
 */
private const val ROUND_STRIDE = 40L

/**
 * **The one seat every ability in this fixture is fired from: an Insider who is still in the
 * round.**
 *
 * ⚠️ **It used to be `Seat(1)` outright, and that was a fixture firing from the couch.** The room
 * restrains seat 1 at the fixture's first meeting, and every Revoke, every Tier-2 fire and the
 * whole Egress happen after it — so from the moment the tally landed, this round was the exact
 * thing E-R2-1 is about: a seat outside the round pressing the Insider's kit and the house
 * answering. Nothing said so, because nothing checked. It says so now, as nine refusals, which is
 * how this line came to be written.
 *
 * **Read off the walked state rather than named**, exactly as the fixture reads its cards and its
 * entries: who is still in the round by pass thirty is a consequence of this fixture rather than
 * something it can assert in advance.
 *
 * **The LAST living Insider rather than the first, and that is the differential's requirement.**
 * `first()` would be seat 1 until the room holds it and somebody else afterwards — an actor that
 * moves mid-round, which puts an `AbilityFired` on a different phone in each of two runs and
 * reports it as an unexplained divergence on a seat that never traded anything. The last one is
 * the Insider this fixture never puts out, in every draw the differential tests exchange.
 *
 * The fallback is only reachable if a caller arms a round whose Insiders are all out, which no
 * fixture here does; it keeps the shape of the events rather than dropping them, so a caller that
 * managed it would see refusals rather than a shorter round.
 */
private fun actor(state: GameState, insiders: List<Seat>): Seat =
    state.livingInsiders.lastOrNull() ?: insiders.last()

/**
 * A round with every event kind in it, long enough that ordering mistakes have room to show.
 *
 * ### The Subroutine half is now a walk rather than a claim
 *
 * D-109 moved the verdict to the house, so a client no longer reports a completion — it scans a
 * marker, which arms whatever it has open, and hands over an entry, which the house grades. The
 * fixture therefore does both, in that order, at the same card.
 *
 * **The entries are the ones the house actually asked for, read off the rules rather than typed
 * in.** A fixture holding a hand-written answer key would agree with somebody's model of the
 * grading rather than with the grading, and would keep agreeing after the rule changed — the same
 * failure `snapshots` refuses fixtures for.
 *
 * **The entries are baked into the event list, which is what makes the differential harness sharp
 * here.** `withRoleSwapped` rewrites the arming event and nothing else, so both runs are asked the
 * identical questions and hand over the identical answers — and any divergence in a verdict is
 * then unambiguously the grading having noticed who was asking.
 */
/**
 * **The card this seat's next step is at**, read off the draw and the carry (D-322): the station a
 * disk in hand goes to next, or else the first actionable line's own step — a Subroutine's card, a
 * Memory Dump's target once the house holds its pattern and its source before, an Array Wipe's
 * Spares. So the fixture walks circuits and Memory Dumps as a player would, and the differential
 * harness diffs them, rather than taking a disk at Spares and scanning past it for the rest of the
 * round.
 */
internal fun nextCard(state: GameState, seat: Seat): MarkerId? {
    val order = state.workOrderFor(seat) ?: return null
    state.diskOf(seat)?.let { disk ->
        return order.entries.getOrNull(disk.entry)?.markers?.getOrNull(if (disk.damaged) 2 else 1)
    }
    val entry = order.entries.firstOrNull { order.isActionable(it) } ?: return null
    return when (entry.work) {
        is AssignedWork.Subroutine -> entry.anchor
        AssignedWork.MemoryDump ->
            if (state.patternOf(seat, entry.index) != null) entry.markers.getOrNull(1) else entry.anchor
        AssignedWork.ArrayWipe -> entry.anchor
    }
}

internal fun round(insiders: List<Seat> = INSIDERS): List<Event> {
    val arming = Event.RoundArmed(
        Tick(0), seed = 20260818L, seats = SEATS, insiders = insiders, markers = placed(MARKERS), names = NAMES,
        oneLines = LINES, terminal = STATION,
    )
    // **The round is walked forward as it is built.** Each seat's next piece of work depends on
    // what it has already completed, and an Insider's Revoke depends on a cooldown that started at
    // arming (D-132) — neither is knowable from the opening state alone. A fixture that read both
    // off the arming would scan the same card forty times, bank once per seat, and quietly stop
    // exercising the order it was written to walk.
    //
    // Through the gate rather than through the rules, for the harness's own reason: half the
    // meeting's rules are refusals, and a fixture built against a path no client can reach would
    // record events the real driver then declines.
    var state = GameState.EMPTY
    fun walk(event: Event): Event {
        (admit(state, event) as? Admission.Admitted)?.let { state = it.reduction.state }
        return event
    }
    return buildList {
        add(walk(arming))
        var t = 1L
        repeat(40) { i ->
            // **Each pass starts a stride further on, and the stride is wider than an opening
            // cooldown** (D-132). At one tick per event the whole fixture round finished inside
            // the guaranteed stretch of peace the round now opens with, so not one Revoke armed,
            // nobody was ever revoked, and every property that rests on somebody being out held
            // over a round in which the rule under test never fired.
            t = maxOf(t, i.toLong() * ROUND_STRIDE)
            val seat = SEATS[i % SEATS.size]
            // The next piece of work the house will actually open for this seat, read off the draw
            // rather than typed in. A fixture that named a card would be asserting against its own
            // idea of where the work is, and would keep agreeing after the draw moved.
            val marker = nextCard(state, seat) ?: MARKERS[i % MARKERS.size]
            add(walk(Event.MarkerScanned(Tick(t++), seat, marker)))
            // **BEGIN where the scan offered a carry** (D-325): a Memory Dump's source holds its
            // pattern, and an Array Wipe's first Spares its disk, only once BEGIN is pressed.
            if (state.offerOf(seat) != null) add(walk(Event.CarryBegun(Tick(t++), seat)))
            // **Read AFTER the scan, because the scan is what draws it** (D-139, D-140). The
            // question and its answer are a property of the instance the scan opened, re-drawn on
            // every re-scan; a fixture that read the order's own row would hand over the answer to
            // a question the house never asked, and every entry in the round would grade false.
            add(
                walk(
                    Event.SubroutineReturned(
                        Tick(t++), seat, marker,
                        state.openSubroutineFor(seat)?.takeIf { it.armed }?.expected ?: emptyList(),
                    )
                )
            )
            // Somebody walks away from a marker without handing anything over, twice a round. It
            // closes the presence window and spends what the scan armed (D-111), and it is on the
            // fixture's path so that the replay guarantee covers a window that shut without an
            // entry — the one way a window closes that the work plane never hears about.
            if (i % 13 == 7) add(walk(Event.PerformanceEnded(Tick(t++), SEATS[(i + 2) % SEATS.size])))
            // **The station, three times a round** (D-199, D-203): one seat reads, a second is
            // refused ONE READER AT A TIME, and the reader leaves. On the fixture's path so the
            // role-swapped differential covers the lock -- an answer that noticed who was asking
            // would diverge here -- and so the recording round-trips both the answer and the leave.
            if (i % 11 == 3) {
                add(walk(Event.MarkerScanned(Tick(t++), seat, STATION)))
                add(walk(Event.MarkerScanned(Tick(t++), SEATS[(i + 3) % SEATS.size], STATION)))
                add(walk(Event.TerminalLeft(Tick(t++), seat)))
            }
            if (i % 9 == 0) {
                // Pushed to the moment the house says this seat is ready, so the fixture's Revokes
                // land instead of being refused inside the opening stretch of peace.
                val ready = state.cooldownFor(actor(state, insiders), InsiderAbility.Revoke)
                    ?.readyAt?.step ?: 0L
                t = maxOf(t, ready)
                add(walk(Event.RevokeArmed(Tick(t++), actor(state, insiders))))
            }
            if (i % 9 == 4) {
                // **An arm lasts 20 s, or until a meeting is called** (the owner's 2026-11-18 ruling,
                // D-280). Pass 30's Tier-2 fires push the clock past the window pass 27 opened, so
                // an arm that has lapsed is made again, at the moment the house says it may be, and
                // the touch below still lands rather than becoming an unarmed one.
                val insider = actor(state, insiders)
                if (state.cooldownArmed.any { it.index == insider.index } && !state.armedAt(insider, Tick(t))) {
                    val ready = state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt?.step ?: 0L
                    t = maxOf(t, ready)
                    add(walk(Event.RevokeArmed(Tick(t++), insider)))
                }
                add(
                    walk(
                        Event.ContactMade(
                            Tick(t++), actor(state, insiders), SEATS[(i + 3) % SEATS.size],
                        )
                    )
                )
            }
            if (i % 17 == 16) {
                // A whole meeting, walked. It has to be whole: D-104's gate does not close a
                // player short, so a fixture that checked in four of six would stall in CheckIn
                // and every phase after it would be refused rather than reduced -- a fixture round
                // that quietly stopped exercising the vote.
                //
                // **Called by the living** (D-279, F5): the couch cannot act, so a card call from
                // an out seat is a forged frame the gate refuses -- and this pass's seat is out by
                // the second meeting, which used to leave the whole meeting refused behind it.
                val caller = seat.takeUnless { state.isOut(it) } ?: state.livingSeats.first()
                add(walk(Event.MeetingCalled(Tick(t++), caller, MeetingTrigger.MeetingCard)))
                SEATS.forEach { v -> add(walk(Event.MeetingCheckedIn(Tick(t++), v))) }
                // **READY TO VOTE, pressed by some of the room** (D-117), which the fixture never
                // recorded — found by the same assertion that found the Tier-2 gap. Not everybody,
                // so the discussion still closes on its clock rather than on the unanimous skip:
                // the point is that the event reaches a recording at all, not that it decides
                // anything here.
                SEATS.filter { it.index % 2 == 0 }
                    .forEach { v -> add(walk(Event.ReadyToVoteDeclared(Tick(t++), v))) }
                add(walk(Event.DiscussionClosed(Tick(t++))))
                // **The living vote, and only the living** (D-252): the couch has no ballot to
                // tap, so a vote from an out seat is a forged frame the gate refuses, and a fixture
                // that cast one would no longer be a round that refuses nothing.
                state.livingSeats.forEach { v ->
                    add(
                        walk(
                            Event.VoteSelected(
                                Tick(t++), v, if (v.index % 3 == 0) null else Seat(1),
                            )
                        )
                    )
                    // Two seats never press READY, so the buzzer's auto-lock is on the fixture's
                    // path rather than only on the fuzzer's.
                    if (v.index % 5 != 4) add(walk(Event.VoteLocked(Tick(t++), v)))
                }
                add(walk(Event.VoteWindowClosed(Tick(t++))))
                add(walk(Event.TallyHalfwayReached(Tick(t++))))
                add(walk(Event.MeetingClosed(Tick(t++))))
            }
            if (i == TIER_2_PASS) {
                // **All three Tier-2 fires, on the fixture's own path** (`gdd.md:306`, `:313`,
                // `:315`).
                //
                // ⚠️ **They were missing, and their absence hid a real bug.** `RecordingText`'s
                // `expectedFields` had no row for `SurgeFired` or `IsolateFired`, so **any
                // recording containing either was unreadable** — the parser built the event, fell
                // through to the unknown-field check, and threw. Nothing said so, because no
                // round-trip test had ever recorded a Tier-2 ability. Adding Spoof's row without
                // adding the three fires here would have left the same trap set for the next one.
                //
                // **The CLOCK is pushed to the pool's deadline before each, because the pool is
                // shared**: three fires in a row would otherwise be one fire and two that spent
                // nothing, and a fire that spent nothing carries none of what this fixture is here
                // to carry.
                //
                // ⚠️ **It used to push the cooldown instead of the clock, and that push was a lie
                // this fixture told itself.** `state.withCooldown(...)` writes the fixture's own
                // walked state and is not an event, so a replay of the list never applies it: the
                // Surge and the Static were fired into a running pool every time, came back
                // `cooldownStarted=false`, and nobody saw it because the actor was Restrained and
                // the allowlist declined the delivery. Reading the deadline and moving the clock to
                // it is the same intent expressed in the one currency the event list carries.
                // **The same Insider the fixture's Egress and Revokes fire with** — one actor for
                // the whole round, which is [actor]'s doing rather than this line's.
                // `ClientTranscriptTest`'s claim is that `AbilityFired` reaches the acting Insider
                // **and nobody else**, and it says so by naming the fixture's one actor — so a
                // fixture with a second actor would loosen that test into an assertion about a
                // list of seats rather than about addressing.
                val actor = actor(state, insiders)
                val victim = state.livingSeats.first { it.index != actor.index }
                val card = state.activeMarkers.first()
                for (fire in listOf<(Tick) -> Event>(
                    { at -> Event.SurgeFired(at, actor, victim) },
                    { at -> Event.StaticFired(at, actor) },
                    { at -> Event.IsolateFired(at, actor, card) },
                )) {
                    t = maxOf(t, state.cooldownFor(actor, InsiderAbility.Access)?.readyAt?.step ?: t)
                    add(walk(fire(Tick(t++))))
                }
            }
            if (i == EGRESS_PASS) {
                // **A whole Egress, walked: fired, paused by a report meeting, resumed, and
                // contained.** On the fixture's path rather than only in a unit test, because the
                // replay guarantee is what covers the pause arithmetic — a build that reset the
                // deadline instead of moving it produces the same effect stream and a different
                // state row, which is the one class of regression state rows exist to catch.
                //
                // Expiry is deliberately not here: an Egress ends once, and the pause is the half
                // whose arithmetic can silently drift.
                t = maxOf(t, state.egressReadyAt.step)
                val nodes = state.activeMarkers.distinctBy { it.value }.take(2)
                val firedAt = Tick(t++)
                add(walk(Event.EgressFired(firedAt, actor(state, insiders), EgressType.Beacon, nodes, FIXTURE_PULSE_FROM)))

                // The two who go. Read off the state, because who is still in the round by this
                // point is a consequence of the fixture rather than something it can name.
                val pair = state.livingSeats.take(2)
                add(walk(Event.MarkerScanned(Tick(t++), pair[0], nodes[0])))
                add(walk(Event.MarkerScanned(Tick(t++), pair[1], nodes[1])))

                // D-121's one exception, which is the only meeting that can happen now — and it
                // pauses the countdown rather than resetting it (D-133). Everybody Skips, so the
                // pair who are about to contain it are still in the round afterwards.
                val reported = state.revoked.firstOrNull() ?: SEATS.last()
                add(walk(Event.MeetingCalled(Tick(t++), pair[0], MeetingTrigger.RevokeReported(reported))))
                SEATS.forEach { v -> add(walk(Event.MeetingCheckedIn(Tick(t++), v))) }
                add(walk(Event.DiscussionClosed(Tick(t++))))
                add(walk(Event.VoteWindowClosed(Tick(t++))))
                add(walk(Event.TallyHalfwayReached(Tick(t++))))
                add(walk(Event.MeetingClosed(Tick(t++))))

                // **And they walk back and scan again** (D-279, F3). A scan from before the meeting
                // places nobody after it: lights out starts a new shift, and the pulse reads the
                // presence row only when it was written in this one, as `placementOf` does.
                add(walk(Event.MarkerScanned(Tick(t++), pair[0], nodes[0])))
                add(walk(Event.MarkerScanned(Tick(t++), pair[1], nodes[1])))

                // Both hit the same four pulses off the house schedule, which runs from the anchor
                // the Egress was fired with, on the host's clock (C2), and is untouched by the pause.
                val beats = (0 until Balance.SYNC_PULSE_BEATS).map {
                    FIXTURE_PULSE_FROM + it * Balance.SYNC_PULSE_INTERVAL_MILLIS
                }
                // They arrive after the fourth pulse, which is a house tick, not a tap time.
                t = maxOf(t, firedAt.step + Balance.SYNC_PULSE_LEAD + Balance.SYNC_PULSE_BEATS * Balance.SYNC_PULSE_INTERVAL)
                add(walk(Event.SyncPulseReturned(Tick(t++), pair[0], beats)))
                add(walk(Event.SyncPulseReturned(Tick(t++), pair[1], beats)))
            }
        }
    }
}

/**
 * **The fixture's pulse grid, on the host's clock in milliseconds** (C2): what `egressFor` would
 * have computed from the host's clock at the draw. Nothing like any tick in the fixture.
 */
internal const val FIXTURE_PULSE_FROM: Long = 86_402_000L

/**
 * Late enough that the shared Egress cooldown has run down on its own, and after the fixture's
 * meetings so that both halves are exercised in a round that already has people out of it.
 */
private const val EGRESS_PASS = 34

/**
 * Before the Egress, so that the Isolate's D-150 node exclusion is not what this pass is exercising
 * — it is here to put the three fires in a recording, and a pass that also happened to be during an
 * Egress would be two claims in one place.
 */
private const val TIER_2_PASS = 30

/**
 * **[round], carried through to an ending** — a second Egress, fired and left to run out (D-131).
 *
 * Built by appending to the shared fixture rather than by writing a second round, so the two cannot
 * drift into two different games: everything the replay guarantee already covers is still on the
 * path, and what is added is the transition it did not cover.
 *
 * **Expiry rather than a Restrain, and that is the pragmatic choice as well as the sharp one.** It
 * is the one route whose outcome is a *fact* rather than a predicate over state — `outcomeOf` has
 * to read the event to know an Egress ran out rather than being contained — so it is the route a
 * replay is most able to get wrong while looking right. It is also two events, where driving a
 * round to parity through real meetings is thirty and depends on who the fixture's Revokes happened
 * to reach.
 *
 * The nodes and the tick are read off the state the fixture actually reached, for [round]'s reason:
 * a fixture that named a card or a moment would be asserting against its own idea of the round and
 * would keep agreeing after the draw moved.
 */
internal fun roundThatEnds(): List<Event> {
    val base = round()
    val after = record(GameState.EMPTY, base).first
    check(!after.ended) { "the shared fixture round now ends on its own; this helper has nothing to add" }

    val actor = after.livingInsiders.first()
    val nodes = after.activeMarkers.distinctBy { it.value }.take(2)
    val firedAt = maxOf(after.egressReadyAt.step, base.last().at.step + 1)
    return base +
        Event.EgressFired(Tick(firedAt), actor, EgressType.Tether, nodes, FIXTURE_PULSE_FROM + 600_000L) +
        Event.EgressExpired(Tick(firedAt + Balance.EGRESS_TIMER))
}
