package home.someoneshome.harness

import home.someoneshome.model.Event
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

/** A round with every event kind in it, long enough that ordering mistakes have room to show. */
internal fun round(insiders: List<Seat> = INSIDERS): List<Event> = buildList {
    add(Event.RoundArmed(Tick(0), seed = 20260818L, seats = SEATS, insiders = insiders))
    var t = 1L
    repeat(40) { i ->
        val seat = SEATS[i % SEATS.size]
        add(Event.MarkerScanned(Tick(t++), seat, MarkerId("m${i % 7}")))
        add(Event.SubroutineCompleted(Tick(t++), seat, MarkerId("m${i % 7}")))
        if (i % 9 == 0) add(Event.RevokeArmed(Tick(t++), Seat(1)))
        if (i % 9 == 4) add(Event.ContactMade(Tick(t++), Seat(1), SEATS[(i + 3) % SEATS.size]))
        if (i % 17 == 16) {
            add(Event.MeetingCalled(Tick(t++), seat))
            SEATS.forEach { v -> add(Event.VoteCast(Tick(t++), v, if (v.index % 3 == 0) null else Seat(1))) }
            add(Event.MeetingClosed(Tick(t++)))
        }
    }
}
