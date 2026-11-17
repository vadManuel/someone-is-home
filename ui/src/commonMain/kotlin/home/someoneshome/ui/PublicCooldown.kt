package home.someoneshome.ui

import home.someoneshome.model.Balance

/**
 * **A cooldown every phone draws identically, because its restart was a moment every phone was
 * told** (D-239, provisional: owner to confirm).
 *
 * Page 2's three bars draw three of the house's clocks, and each restarts at moments that are
 * pushed to every seat: LIGHTS OUT (`OpeningMessage`), a meeting's end (`MeetingEnded`) and, for the
 * Egress's house-wide clock, an Egress opening (`EgressOpened`). Nothing here is a seat's own
 * clock, so an Insider's page 2 and a Resident's draw the same bar at the same moment (D-142's
 * parity), and nothing here can say that anybody spent anything.
 *
 * **What it gates.** The REVOKE tile's ARMED is input echo (D-231), and before D-239 it was set on
 * every hold, including one into the opening half-cooldown, which the house refuses in silence
 * (D-132). Two real iPhones failed on exactly that: ARMED on the glass, an unarmed house, a touch
 * that did nothing (D-237). Now the echo waits for [Revoke] to run out. The request still goes up
 * on every hold, the same way (rule 1); only the local echo reads the clock, and the clock is public.
 *
 * ### The phone counts the seconds, from the push, and finishes no earlier than the house
 *
 * [elapsed] is advanced once a second by `FlowHost`, the Static window's arrangement, and starts at
 * the push's arrival, which is after the house wrote the deadline. [PAD_SECONDS] covers the one way
 * the house can be behind the push: the host's tick driver starts when its own phone reaches
 * PERIMETER ARMED, a quarter-second poll after the arming. Erring late is the safe direction: a hold
 * in the pad arms the house without the echo, and the next hold (the tile reads READY a second
 * later) echoes ARMED over an arm that is really there. Erring early would be ARMED over nothing,
 * which is the bug.
 */
data class PublicCooldown(val seconds: Int, val elapsed: Int = 0) {

    /** Still running: the echo is refused and the bar is short. */
    val running: Boolean get() = elapsed < seconds

    /** How full the bar is, from empty at the restart to full when it has run out. */
    val fraction: Float get() = if (seconds <= 0) 1f else (elapsed.toFloat() / seconds).coerceIn(0f, 1f)

    /** One second later. A clock that has run out stays run out. */
    fun secondPassed(): PublicCooldown = if (running) copy(elapsed = elapsed + 1) else this

    companion object {
        /** See the class note: the phone may finish late, never early. */
        const val PAD_SECONDS: Int = 1

        private fun restarted(ticks: Long) = PublicCooldown(Balance.wholeSeconds(ticks) + PAD_SECONDS)

        /** The Revoke at half, per seat in the house and the same on every phone (D-132, D-184). */
        val Revoke: PublicCooldown get() = restarted(Balance.REVOKE_OPENING)

        /** The Access pool at half (D-132, D-184). A Tier-2 fire's restart is the actor's own: not drawn. */
        val Access: PublicCooldown get() = restarted(Balance.ACCESS_OPENING)

        /** The house-wide Egress clock at half (D-132, D-184). */
        val EgressOpening: PublicCooldown get() = restarted(Balance.EGRESS_OPENING)

        /** The house-wide Egress clock at full, from the push that told every phone it fired. */
        val EgressFired: PublicCooldown get() = restarted(Balance.EGRESS_COOLDOWN)
    }
}

/**
 * **What the REVOKE tile draws: the echo if this phone held it, the public clock if that is
 * running, and READY otherwise** (D-231, D-239).
 *
 * ARMED wins, because it is this phone's own input and the house holds an arm across a meeting
 * (D-184 resets the clock, not the arm). When ARMED comes back down is not decided here and is
 * the owner's (D-239's open item): today it stays until the round ends, as it did.
 *
 * Public, so the bench reads its tile the way a person does: it holds once, when this says READY.
 */
val PanelState.revokeShown: RevokeState
    get() = when {
        revoke == RevokeState.Armed -> RevokeState.Armed
        revokeClock?.running == true -> RevokeState.Cooldown
        else -> revoke
    }
