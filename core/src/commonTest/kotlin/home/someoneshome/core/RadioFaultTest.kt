package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **T2: a radio that went down is recorded silently and announced to the whole room at the next
 * meeting, the same whoever reported it** (the owner's 2026-11-15 ruling, D-222).
 *
 * Rule 6 both ways round: nothing reaches any phone when the report arrives, and at the meeting
 * every phone, living and out, is delivered the same notice. Rule 1: the notice is sent at every
 * meeting, empty when no radio went down, so a meeting's frame count does not say whether one did.
 */
class RadioFaultTest {

    private val seats = (0 until 6).map { Seat(it) }
    private var tick = 0L
    private fun t() = Tick(tick++)

    private fun round(insider: Seat): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(t(), seed = 11L, seats = seats, insiders = listOf(insider))).state

    /** Through the gate, as every arrival goes; what each seat was delivered, read off the state after. */
    private fun feed(start: GameState, vararg events: Event): Pair<GameState, Map<Int, List<Effect>>> {
        var s = start
        val got = seats.associate { it.index to mutableListOf<Effect>() }
        for (event in events) {
            val admission = admit(s, event)
            check(admission is Admission.Admitted) { "the fixture's $event was refused: $admission" }
            s = admission.reduction.state
            for (effect in admission.reduction.effects) {
                for (d in EmitSchema.deliveries(effect, s)) got.getValue(d.seat.index) += effect
            }
        }
        return s to got
    }

    /** A meeting called at the card, every seat checked in: the gate closes on the last. */
    private fun meeting(): Array<Event> =
        (listOf<Event>(Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard)) +
            seats.drop(1).map { Event.MeetingCheckedIn(t(), it) }).toTypedArray()

    private fun notices(got: Map<Int, List<Effect>>) =
        got.mapValues { (_, effects) -> effects.filterIsInstance<Effect.RadioFaultsAnnounced>() }

    @Test
    fun aReportReachesNobodyAndTheNextMeetingTellsEveryPhone() {
        tick = 0
        val (reported, quiet) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(3)))
        assertEquals(seats.associate { it.index to emptyList<Effect>() }, quiet, "a radio fault reached a phone before the meeting")
        assertEquals(listOf(3), reported.radioFaulted.map { it.index })

        val (after, got) = feed(reported, *meeting())
        for (seat in seats) {
            assertEquals(
                listOf(Effect.RadioFaultsAnnounced(listOf(Seat(3)))), notices(got).getValue(seat.index),
                "seat ${seat.index} was not delivered the notice exactly once",
            )
        }
        assertTrue(after.radioFaulted.isEmpty(), "an announced fault is still waiting for a meeting")
    }

    @Test
    fun theNoticeReachesTheOutAsWellAsTheLiving() {
        tick = 0
        val insider = Seat(1)
        val start = round(insider)
        tick = Balance.REVOKE_COOLDOWN / 2 + 1L
        val (revoked, _) = feed(
            start,
            Event.RevokeArmed(t(), insider),
            Event.ContactMade(t(), insider, Seat(4)),
        )
        assertTrue(revoked.isRevoked(Seat(4)), "the fixture never put anybody out")
        val (_, got) = feed(revoked, Event.RadioFaulted(t(), Seat(2)), *meeting())
        assertEquals(
            listOf(Effect.RadioFaultsAnnounced(listOf(Seat(2)))), notices(got).getValue(4),
            "the Revoked seat at the meeting was not told",
        )
    }

    /** Rule 1: the notice is in every meeting's stream, so its presence is not the news. */
    @Test
    fun aMeetingWithNoFaultStillSendsTheNoticeEmpty() {
        tick = 0
        val (_, got) = feed(round(Seat(1)), *meeting())
        for (seat in seats) {
            assertEquals(listOf(Effect.RadioFaultsAnnounced(emptyList())), notices(got).getValue(seat.index))
        }
    }

    /** The same report from an Insider and from a Resident: the same stream to every phone, bar the name. */
    @Test
    fun anInsidersFaultAndAResidentsFaultAreAnnouncedAlike() {
        tick = 0
        val (_, byInsider) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(1)), *meeting())
        tick = 0
        val (_, byResident) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(2)), *meeting())
        for (seat in seats) {
            val a = byInsider.getValue(seat.index)
            val b = byResident.getValue(seat.index)
            assertEquals(a.map { it::class.simpleName }, b.map { it::class.simpleName }, "seat ${seat.index}'s stream differs by role")
            assertEquals(listOf(Effect.RadioFaultsAnnounced(listOf(Seat(1)))), notices(byInsider).getValue(seat.index))
            assertEquals(listOf(Effect.RadioFaultsAnnounced(listOf(Seat(2)))), notices(byResident).getValue(seat.index))
        }
    }

    @Test
    fun aSeatIsNamedOnceAndInSeatOrderAndTheNextMeetingStartsClean() {
        tick = 0
        val (held, _) = feed(
            round(Seat(1)),
            Event.RadioFaulted(t(), Seat(5)),
            Event.RadioFaulted(t(), Seat(2)),
            Event.RadioFaulted(t(), Seat(5)),
            Event.RadioFaulted(t(), Seat(9)),
        )
        assertEquals(listOf(2, 5), held.radioFaulted.map { it.index }, "the list is not the house's seats, once each, in order")
        val (afterFirst, _) = feed(held, *meeting())
        assertTrue(afterFirst.radioFaulted.isEmpty())
    }

    @Test
    fun aReportBeforeArmingIsRefusedAndRecordedNotDropped() {
        assertEquals(
            Admission.Refused(RefusalReason.RoundNotArmed),
            admit(GameState.EMPTY, Event.RadioFaulted(Tick(1), Seat(0))),
        )
    }
}
