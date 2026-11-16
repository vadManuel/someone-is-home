package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-187 at the house: a radio switched off or its permission withdrawn is named at the next
 * meeting; a radio that reset is recorded and named by nobody** (T2, D-222; D-223, the owner's
 * 2026-11-15 ruling).
 *
 * Rule 6 both ways round: nothing reaches any phone when a report arrives, whatever its cause, and
 * at the meeting every phone, living and out, is delivered the same notice. Rule 1: the notice is
 * sent at every meeting, empty when nobody evaded, so a meeting's frame count does not say whether
 * anybody did; and a reset and a switch-off produce the same nothing at the moment they arrive.
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
        got.mapValues { (_, effects) -> effects.filterIsInstance<Effect.NoticeIssued>() }

    private val nobody: Map<Int, List<Effect>> = seats.associate { it.index to emptyList() }

    /**
     * **GUARD: Bluetooth switched off, or its permission withdrawn, mid-round is named.** A person's
     * act (D-187): the seat waits silently for the meeting, and the gate closing tells every phone.
     */
    @Test
    fun aDeliberateOutageReachesNobodyAndTheNextMeetingNamesItToEveryPhone() {
        tick = 0
        val (reported, quiet) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(3), RadioCause.Person))
        assertEquals(nobody, quiet, "a radio switched off reached a phone before the meeting")
        assertEquals(listOf(3), reported.unreachable.map { it.index })

        val (after, got) = feed(reported, *meeting())
        for (seat in seats) {
            assertEquals(
                listOf(Effect.NoticeIssued(listOf(Seat(3)))), notices(got).getValue(seat.index),
                "seat ${seat.index} was not delivered the notice naming seat 3 exactly once",
            )
        }
        assertTrue(after.unreachable.isEmpty(), "a named seat is still waiting for a meeting")
    }

    /**
     * **GUARD: a reset produces no notice.** D-187: *genuine radio loss earns no notice.* The report
     * is admitted (so it is in the recording, for the authority) and moves nothing; the meeting's
     * notice is the empty one every meeting gets.
     */
    @Test
    fun aResetIsRecordedAndNamedByNoNotice() {
        tick = 0
        val start = round(Seat(1))
        val admission = admit(start, Event.RadioFaulted(t(), Seat(3), RadioCause.Device))
        assertTrue(admission is Admission.Admitted, "a reset was refused, so it is not recorded: $admission")
        assertEquals(emptyList(), admission.reduction.effects, "a reset reached a phone")
        assertTrue(admission.reduction.state.unreachable.isEmpty(), "a reset was put on the notice's list")

        val (_, got) = feed(admission.reduction.state, *meeting())
        for (seat in seats) {
            assertEquals(
                listOf(Effect.NoticeIssued(emptyList())), notices(got).getValue(seat.index),
                "a reset was named to seat ${seat.index}: D-187 says genuine radio loss earns no notice",
            )
        }
    }

    /** Rule 1 at the moment of the report: a reset and a switch-off look the same to every phone. */
    @Test
    fun aResetAndASwitchOffArriveAsTheSameNothing() {
        for (cause in RadioCause.entries) {
            tick = 0
            val (_, got) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(3), cause))
            assertEquals(nobody, got, "$cause reached a phone when it was reported")
        }
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
        val (_, got) = feed(revoked, Event.RadioFaulted(t(), Seat(2), RadioCause.Person), *meeting())
        assertEquals(
            listOf(Effect.NoticeIssued(listOf(Seat(2)))), notices(got).getValue(4),
            "the Revoked seat at the meeting was not told",
        )
    }

    /** Rule 1: the notice is in every meeting's stream, so its presence is not the news. */
    @Test
    fun aMeetingWithNobodyUnreachableStillSendsTheNoticeEmpty() {
        tick = 0
        val (_, got) = feed(round(Seat(1)), *meeting())
        for (seat in seats) {
            assertEquals(listOf(Effect.NoticeIssued(emptyList())), notices(got).getValue(seat.index))
        }
    }

    /** The same report from an Insider and from a Resident: the same stream to every phone, bar the name. */
    @Test
    fun anInsidersOutageAndAResidentsAreNamedAlike() {
        tick = 0
        val (_, byInsider) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(1), RadioCause.Person), *meeting())
        tick = 0
        val (_, byResident) = feed(round(Seat(1)), Event.RadioFaulted(t(), Seat(2), RadioCause.Person), *meeting())
        for (seat in seats) {
            val a = byInsider.getValue(seat.index)
            val b = byResident.getValue(seat.index)
            assertEquals(a.map { it::class.simpleName }, b.map { it::class.simpleName }, "seat ${seat.index}'s stream differs by role")
            assertEquals(listOf(Effect.NoticeIssued(listOf(Seat(1)))), notices(byInsider).getValue(seat.index))
            assertEquals(listOf(Effect.NoticeIssued(listOf(Seat(2)))), notices(byResident).getValue(seat.index))
        }
    }

    @Test
    fun aSeatIsNamedOnceAndInSeatOrderAndTheNextMeetingStartsClean() {
        tick = 0
        val (held, _) = feed(
            round(Seat(1)),
            Event.RadioFaulted(t(), Seat(5), RadioCause.Person),
            Event.RadioFaulted(t(), Seat(2), RadioCause.Person),
            Event.RadioFaulted(t(), Seat(4), RadioCause.Device),
            Event.RadioFaulted(t(), Seat(5), RadioCause.Person),
            Event.RadioFaulted(t(), Seat(9), RadioCause.Person),
        )
        assertEquals(listOf(2, 5), held.unreachable.map { it.index }, "the list is not the house's seats, once each, in order, persons only")
        val (afterFirst, _) = feed(held, *meeting())
        assertTrue(afterFirst.unreachable.isEmpty())
    }

    /**
     * **GUARD: before arming, nothing is announced.** A report from the lobby is refused at the gate
     * (and recorded as a refusal, not dropped), and the round that then arms names nobody at its
     * first meeting.
     */
    @Test
    fun aReportBeforeArmingIsRefusedAndTheRoundsFirstNoticeNamesNobody() {
        for (cause in RadioCause.entries) {
            assertEquals(
                Admission.Refused(RefusalReason.RoundNotArmed),
                admit(GameState.EMPTY, Event.RadioFaulted(Tick(1), Seat(0), cause)),
            )
        }
        tick = 2
        val (_, got) = feed(round(Seat(1)), *meeting())
        for (seat in seats) {
            assertEquals(
                listOf(Effect.NoticeIssued(emptyList())), notices(got).getValue(seat.index),
                "a report from before arming was named at the round's first meeting",
            )
        }
    }
}
