package home.someoneshome.harness

import home.someoneshome.core.reduce
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
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
    val arming = Event.RoundArmed(Tick(0), seed = 20260818L, seats = SEATS, insiders = insiders)
    val opening = reduce(GameState.EMPTY, arming).state
    return buildList {
        add(arming)
        var t = 1L
        repeat(40) { i ->
            val seat = SEATS[i % SEATS.size]
            val marker = MarkerId("m${i % 7}")
            add(Event.MarkerScanned(Tick(t++), seat, marker))
            add(
                Event.SubroutineReturned(
                    Tick(t++), seat, marker,
                    opening.openSubroutineFor(seat)?.expected ?: emptyList(),
                )
            )
            if (i % 9 == 0) add(Event.RevokeArmed(Tick(t++), Seat(1)))
            if (i % 9 == 4) add(Event.ContactMade(Tick(t++), Seat(1), SEATS[(i + 3) % SEATS.size]))
            if (i % 17 == 16) {
                add(Event.MeetingCalled(Tick(t++), seat))
                SEATS.forEach { v -> add(Event.VoteCast(Tick(t++), v, if (v.index % 3 == 0) null else Seat(1))) }
                add(Event.MeetingClosed(Tick(t++)))
            }
        }
    }
}
