package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
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

/**
 * How far the clock moves between passes. Wider than half a Revoke cooldown, deliberately — see
 * where it is used.
 */
private const val ROUND_STRIDE = 40L

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
internal fun round(insiders: List<Seat> = INSIDERS): List<Event> {
    val arming = Event.RoundArmed(
        Tick(0), seed = 20260818L, seats = SEATS, insiders = insiders, markers = MARKERS,
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
            val order = state.workOrderFor(seat)
            val entry = order?.entries?.firstOrNull { order.isActionable(it) }
            val marker = entry?.marker ?: MARKERS[i % MARKERS.size]
            add(walk(Event.MarkerScanned(Tick(t++), seat, marker)))
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
            if (i % 9 == 0) {
                // Pushed to the moment the house says this seat is ready, so the fixture's Revokes
                // land instead of being refused inside the opening stretch of peace.
                val ready = state.cooldownFor(Seat(1), InsiderAbility.Revoke)?.readyAt?.step ?: 0L
                t = maxOf(t, ready)
                add(walk(Event.RevokeArmed(Tick(t++), Seat(1))))
            }
            if (i % 9 == 4) {
                add(walk(Event.ContactMade(Tick(t++), Seat(1), SEATS[(i + 3) % SEATS.size])))
            }
            if (i % 17 == 16) {
                // A whole meeting, walked. It has to be whole: D-104's gate does not close a
                // player short, so a fixture that checked in four of six would stall in CheckIn
                // and every phase after it would be refused rather than reduced -- a fixture round
                // that quietly stopped exercising the vote.
                add(walk(Event.MeetingCalled(Tick(t++), seat, MeetingTrigger.MeetingCard)))
                SEATS.forEach { v -> add(walk(Event.MeetingCheckedIn(Tick(t++), v))) }
                add(walk(Event.DiscussionClosed(Tick(t++))))
                SEATS.forEach { v ->
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
        }
    }
}
