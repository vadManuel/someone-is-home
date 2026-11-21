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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A permission switched off is named with the owner's warning, and the second one closes the
 * house** (the owner's 2026-11-20 rulings, D-310).
 *
 * The rules' half, through the admission gate as every arrival goes, with what each seat was
 * delivered read off the emit schema: the warning rides the notice, the same on every phone; a
 * phone that only left the app is named without it and never counts; several switches in one shift
 * are one warning; once a notice carrying the warning has been issued, anyone's permission closes
 * the house, which every seat is told in the same one effect; and a new round starts with no
 * warning. The close itself is minted by the house when [closesTheHouse] says so, which is what
 * [report] does here, as `RoundAuthority` does on a phone.
 */
class PermissionCloseTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insider = Seat(1)
    private val host = Seat(0)
    private var tick = 0L
    private fun t() = Tick(tick++)

    private fun round(): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(t(), seed = 11L, seats = seats, insiders = listOf(insider), host = host)).state

    private val delivered = seats.associate { it.index to mutableListOf<Effect>() }

    private fun admitted(s: GameState, event: Event): GameState {
        val admission = admit(s, event)
        check(admission is Admission.Admitted) { "the fixture's $event was refused: $admission" }
        for (effect in admission.reduction.effects) {
            for (d in EmitSchema.deliveries(effect, admission.reduction.state)) delivered.getValue(d.seat.index) += effect
        }
        return admission.reduction.state
    }

    private fun feed(start: GameState, vararg events: Event): GameState = events.fold(start, ::admitted)

    /**
     * **One radio report, as the house puts it to the rules** (`RoundAuthority.faulted`): admitted,
     * and when the rules say it closes the house, the close minted after it and put to the same gate.
     */
    private fun report(start: GameState, seat: Seat, cause: RadioCause): GameState {
        val event = Event.RadioFaulted(t(), seat, cause)
        val after = admitted(start, event)
        return if (closesTheHouse(after, event)) admitted(after, Event.HouseClosedOnPermission(t(), seat)) else after
    }

    /** A meeting called at the card, every seat checked in: the gate closes and the notice goes. */
    private fun meeting(start: GameState): GameState =
        feed(
            start,
            Event.MeetingCalled(t(), Seat(0), MeetingTrigger.MeetingCard),
            *start.seats.drop(1).map { Event.MeetingCheckedIn(t(), it) }.toTypedArray(),
        )

    /** The talk, the ballot with nobody voting, the tally and lights out: the next shift. */
    private fun endMeeting(start: GameState): GameState =
        feed(start, Event.DiscussionClosed(t()), Event.VoteWindowClosed(t()), Event.TallyHalfwayReached(t()), Event.MeetingClosed(t()))

    private fun notices(): Map<Int, List<Effect.NoticeIssued>> =
        delivered.mapValues { (_, effects) -> effects.filterIsInstance<Effect.NoticeIssued>() }

    private fun closes(): Map<Int, List<Effect>> =
        delivered.mapValues { (_, effects) -> effects.filterIsInstance<Effect.HouseClosedOnPermission>() }

    private fun forget() = delivered.values.forEach { it.clear() }

    /** Every seat was delivered [expected] as its notices, and nothing else: one frame, the same everywhere. */
    private fun assertEveryPhoneNoticed(expected: Effect.NoticeIssued, label: String) {
        for (seat in seats) {
            assertEquals(listOf(expected), notices().getValue(seat.index), "$label: seat ${seat.index} was not told exactly once")
        }
    }

    // ---- The warning --------------------------------------------------------------------------------

    /**
     * **GUARD: a permission switched off is named at the next meeting with the warning**, to every
     * seat, the Insider's and the host's included, in one notice; and issuing it gives the warning.
     */
    @Test
    fun aPermissionSwitchedOffIsNamedWithTheWarning() {
        tick = 0
        val reported = report(round(), Seat(3), RadioCause.Permission)
        assertTrue(delivered.values.all { it.isEmpty() }, "a permission switched off reached a phone before the meeting")
        assertEquals(listOf(Seat(3)), reported.switchedOff, "the permission was not listed beside the name")
        assertFalse(reported.warned, "the warning counted as given before any notice carried it")

        val met = meeting(reported)
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(Seat(3))), "the first permission")
        assertTrue(met.warned, "a notice carrying the warning did not give it")
        assertTrue(met.switchedOff.isEmpty() && met.unreachable.isEmpty(), "the shift's lists were not emptied at the gate")
        assertFalse(met.ended, "the warning ended the round")
    }

    /**
     * **GUARD: a phone that only left the app is named without the warning, and never closes the
     * house**, before the warning and after it.
     */
    @Test
    fun aLeaveIsNamedWithoutTheWarningAndNeverClosesTheHouse() {
        tick = 0
        val left = meeting(report(round(), Seat(3), RadioCause.Person))
        assertEveryPhoneNoticed(Effect.NoticeIssued(listOf(Seat(3)), switchedOff = emptyList()), "a leave")
        assertFalse(left.warned, "a leave gave the warning")

        // The warning, given for somebody else's permission.
        forget()
        val warned = meeting(report(endMeeting(left), Seat(2), RadioCause.Permission))
        assertTrue(warned.warned, "the fixture never gave the warning")

        // After it: two leaves, from the warned seat and another. Named, and the house stays open.
        forget()
        var shift = endMeeting(warned)
        for (seat in listOf(Seat(2), Seat(4))) {
            val event = Event.RadioFaulted(t(), seat, RadioCause.Person)
            shift = admitted(shift, event)
            assertFalse(closesTheHouse(shift, event), "leaving the app after the warning counted toward closing the house")
        }
        assertFalse(shift.ended, "a leave closed the house")
        assertTrue(closes().values.all { it.isEmpty() }, "a leave sent a phone the close")
        meeting(shift)
        assertEveryPhoneNoticed(Effect.NoticeIssued(listOf(Seat(2), Seat(4)), switchedOff = emptyList()), "two leaves after the warning")
    }

    /**
     * **GUARD: only a switch-off counts** (D-310, D-311). The Bluetooth radio turned off reaches the
     * rules as a permission (`causeOf`, platform), so it counts; a leave and a reset never do.
     */
    @Test
    fun onlyASwitchOffCountsAndALeaveOrAResetNever() {
        tick = 0
        assertFalse(countsTowardClosing(RadioCause.Person))
        assertFalse(countsTowardClosing(RadioCause.Device))
        assertTrue(countsTowardClosing(RadioCause.Permission))
        assertTrue(namedByTheNotice(RadioCause.Permission) && namedByTheNotice(RadioCause.Person))
        // A reset is the phone's own: listed nowhere, warning nobody.
        val reset = meeting(report(round(), Seat(3), RadioCause.Device))
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = emptyList()), "a reset")
        assertFalse(reset.warned)
    }

    /**
     * **GUARD: several permissions in one shift are one warning, not a close.** Before the warning is
     * given every one of them is listed for the one notice that gives it, the same seat twice
     * included, and nothing closes.
     */
    @Test
    fun twoSwitchesInOneShiftAreOneWarningAndNoClose() {
        tick = 0
        var shift = round()
        for (seat in listOf(Seat(4), Seat(2), Seat(4))) shift = report(shift, seat, RadioCause.Permission)
        assertFalse(shift.ended, "a second permission in the shift closed the house before the warning was given")
        assertTrue(closes().values.all { it.isEmpty() }, "a phone was sent the close before the warning")
        val met = meeting(shift)
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(Seat(2), Seat(4))), "two permissions in one shift")
        assertTrue(met.warned)
        assertFalse(met.ended)
    }

    /** A leave and a permission from the same seat in one shift: the seat is named once, with the warning. */
    @Test
    fun aLeaveThenAPermissionFromOneSeatIsNamedOnceWithTheWarning() {
        tick = 0
        val shift = report(report(round(), Seat(3), RadioCause.Person), Seat(3), RadioCause.Permission)
        meeting(shift)
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(Seat(3))), "a leave then a permission")
    }

    /**
     * **GUARD: the notice's two lists, in every ordering** (the owner's 2026-11-21 rulings, D-311).
     * Only leavers: the leavers' list alone, the notice as it was. Only switchers: the permission
     * list alone. Both: each seat on its own list. A seat that both left and switched off: on the
     * permission list only, once. Every seat is told the same notice, the Insider's and the host's.
     */
    @Test
    fun theNoticeKeepsLeaversAndSwitchersApartInEveryOrdering() {
        tick = 0
        // Only leavers.
        var shift = report(report(round(), Seat(4), RadioCause.Person), Seat(2), RadioCause.Person)
        var met = meeting(shift)
        assertEveryPhoneNoticed(Effect.NoticeIssued(listOf(Seat(2), Seat(4)), switchedOff = emptyList()), "only leavers")
        assertFalse(met.warned, "a notice of leavers alone gave the warning")

        // Only switchers.
        forget()
        tick = 0
        shift = report(report(round(), Seat(5), RadioCause.Permission), Seat(2), RadioCause.Permission)
        met = meeting(shift)
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(Seat(2), Seat(5))), "only switchers")
        assertTrue(met.warned)

        // Both, and a seat that did both: seat 2 left, seat 4 switched off, seat 3 left and then
        // switched off, and seat 5 switched off and then left.
        forget()
        tick = 0
        shift = round()
        shift = report(shift, Seat(2), RadioCause.Person)
        shift = report(shift, Seat(4), RadioCause.Permission)
        shift = report(shift, Seat(3), RadioCause.Person)
        shift = report(shift, Seat(3), RadioCause.Permission)
        shift = report(shift, Seat(5), RadioCause.Permission)
        shift = report(shift, Seat(5), RadioCause.Person)
        met = meeting(shift)
        assertEveryPhoneNoticed(
            Effect.NoticeIssued(listOf(Seat(2)), switchedOff = listOf(Seat(3), Seat(4), Seat(5))),
            "leavers and switchers, two seats that did both",
        )
        assertTrue(met.warned)
        assertFalse(met.ended)
    }

    // ---- The close ----------------------------------------------------------------------------------

    /**
     * **GUARD: a permission switched off after the warning closes the house for every seat.**
     * Anyone's: the warning was given for seat 3 and seat 5 switches one off in the next shift. Every
     * seat, the living Residents, the Insider, the Revoked seat and the host's, is sent the one close
     * and nothing else from it; the round is over, with no winner, and every later arrival is refused.
     */
    @Test
    fun aSwitchAfterTheWarningClosesTheHouseForEverySeat() {
        tick = 0
        val warned = meeting(report(round(), Seat(3), RadioCause.Permission))
        assertTrue(warned.warned)
        // Seat 4 Revoked in the next shift, past the half-cooldown lights out resets (D-184): an out
        // phone is told too.
        val nextShift = endMeeting(warned)
        tick += Balance.REVOKE_COOLDOWN / 2 + 1L
        val revoked = feed(nextShift, Event.RevokeArmed(t(), insider), Event.ContactMade(t(), insider, Seat(4)))
        assertTrue(revoked.isRevoked(Seat(4)), "the fixture never put anybody out")

        forget()
        val closed = report(revoked, Seat(5), RadioCause.Permission)
        assertTrue(closed.ended && closed.closed, "a permission after the warning did not close the house")
        assertEquals(null, closed.outcome, "the close named a winner")
        for (seat in seats) {
            assertEquals(
                listOf<Effect>(Effect.HouseClosedOnPermission), delivered.getValue(seat.index),
                "seat ${seat.index} was not sent the close, and nothing else, exactly once",
            )
        }
        assertTrue(closed.oneLines.isEmpty() && closed.names.isEmpty(), "the close kept the round's lines or names")
        val late = admit(closed, Event.RadioFaulted(t(), Seat(2), RadioCause.Permission))
        assertEquals(Admission.Refused(RefusalReason.RoundAlreadyEnded), late, "a report after the close was put to a closed round")
    }

    /** After the warning, a permission switched off during the very meeting that gave it closes the house. */
    @Test
    fun aSwitchAtTheMeetingThatWarnedClosesTheHouse() {
        tick = 0
        val warned = meeting(report(round(), Seat(3), RadioCause.Permission))
        forget()
        val closed = report(warned, Seat(3), RadioCause.Permission)
        assertTrue(closed.closed, "the warned seat's second permission, at the meeting, did not close the house")
        assertTrue(seats.all { delivered.getValue(it.index) == listOf<Effect>(Effect.HouseClosedOnPermission) })
    }

    /**
     * An out seat is named by no notice (R7, D-242), so its permission neither warns nor closes; the
     * host's does both, out or not (D-283, D-289, D-230).
     */
    @Test
    fun anOutSeatNeitherWarnsNorClosesAndTheHostDoesBoth() {
        tick = 0
        val start = round()
        tick = Balance.REVOKE_COOLDOWN / 2 + 1L
        val revoked = feed(start, Event.RevokeArmed(t(), insider), Event.ContactMade(t(), insider, Seat(4)))
        assertTrue(revoked.isRevoked(Seat(4)), "the fixture never put anybody out")
        val quiet = meeting(report(revoked, Seat(4), RadioCause.Permission))
        assertFalse(quiet.warned, "an out seat's permission gave the warning")

        forget()
        val warnedByHost = meeting(report(endMeeting(quiet), host, RadioCause.Permission))
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(host)), "the host's permission")
        assertTrue(warnedByHost.warned)

        forget()
        val outAgain = report(endMeeting(warnedByHost), Seat(4), RadioCause.Permission)
        assertFalse(outAgain.ended, "an out seat's permission closed the house")
        val closed = report(outAgain, host, RadioCause.Permission)
        assertTrue(closed.closed, "the host's permission after the warning did not close the house")
    }

    /** **GUARD: the count is per round.** A new round starts with no warning given. */
    @Test
    fun aNewRoundStartsWithNoWarning() {
        tick = 0
        val warned = meeting(report(round(), Seat(3), RadioCause.Permission))
        assertTrue(warned.warned)
        val next = admitted(warned, Event.RoundArmed(t(), seed = 12L, seats = seats, insiders = listOf(insider), host = host))
        assertFalse(next.warned, "the warning outlived its round")
        assertFalse(next.closed)
        forget()
        val reported = report(next, Seat(3), RadioCause.Permission)
        assertFalse(reported.ended, "the first permission of a new round closed the house")
        meeting(reported)
        assertEveryPhoneNoticed(Effect.NoticeIssued(emptyList(), switchedOff = listOf(Seat(3))), "the new round's first permission")

        // And a closed round is followed by an open one.
        val closed = report(meeting(report(round(), Seat(3), RadioCause.Permission)), Seat(2), RadioCause.Permission)
        assertTrue(closed.closed)
        val again = admitted(closed, Event.RoundArmed(t(), seed = 13L, seats = seats, insiders = listOf(insider), host = host))
        assertFalse(again.ended || again.closed || again.warned, "a new round after a close was not a fresh one")
    }

    /** The close carries nothing: one object, the same for every seat, and its kind is permitted to the ended round alone. */
    @Test
    fun theCloseIsPermittedOnlyToTheEndedRound() {
        tick = 0
        val running = round()
        assertTrue(EmitSchema.deliveries(Effect.HouseClosedOnPermission, running).isEmpty(), "the close was permitted to a running round")
        val closed = running.closeHouse()
        assertEquals(seats, EmitSchema.deliveries(Effect.HouseClosedOnPermission, closed).map { it.seat })
    }
}
