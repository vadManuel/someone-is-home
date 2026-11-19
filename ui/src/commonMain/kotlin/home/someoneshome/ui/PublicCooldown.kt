package home.someoneshome.ui

import home.someoneshome.model.Balance

/**
 * **A cooldown every phone draws identically, because its restart was a moment every phone was
 * told** (D-239, provisional: owner to confirm).
 *
 * Page 2's three bars draw three of the house's clocks, and each restarts at moments that are
 * pushed to every seat: LIGHTS OUT (`OpeningMessage`), a meeting's end (`MeetingEnded`) and, for the
 * Egress's house-wide clock, an Egress opening (`EgressOpened`). Until a phone fires something,
 * nothing here is a seat's own clock, so an Insider's page 2 and a Resident's draw the same bar at
 * the same moment (D-142's parity), and nothing here can say that anybody spent anything.
 *
 * **The one exception is the actor's own** ([fired], D-262): when this phone fires a Tier-2 ability
 * and the house answers *this phone's* request with `AbilityFired(cooldownStarted = true)`, the
 * Access bar restarts from that ability's weight. That is input echo plus the house's answer to
 * the one phone that asked, which nobody else is sent; it is what `gdd.md:357` calls *same
 * cooldown spent* on the actor's device.
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

    /**
     * **This clock, [whole] seconds after it started** (V4, D-269): read against the moment its push
     * arrived rather than counted up a second at a time. It never reads past its end.
     */
    fun at(whole: Int): PublicCooldown {
        val clamped = whole.coerceIn(0, seconds.coerceAtLeast(0))
        return if (clamped == elapsed) this else copy(elapsed = clamped)
    }

    companion object {
        /** See the class note: the phone may finish late, never early. */
        const val PAD_SECONDS: Int = 1

        /**
         * **How long the REVOKE tile says ARMED after the hold that echoed it** (the owner's
         * 2026-11-18 ruling): [Balance.REVOKE_ARM_WINDOW], on this phone's own clock from its own
         * hold. No pad: the hold is before the house stamps the arm, so the tile comes down no
         * later than the house's window closes.
         */
        val ARM_WINDOW_SECONDS: Int = Balance.wholeSeconds(Balance.REVOKE_ARM_WINDOW)

        /**
         * **How long this phone's own arm holds the next echo back**: the full cooldown the house
         * spent at that arm, plus the pad, so the echo errs late like every clock here.
         */
        val OWN_ARM_SECONDS: Int = Balance.wholeSeconds(Balance.REVOKE_COOLDOWN) + PAD_SECONDS

        private fun restarted(ticks: Long) = PublicCooldown(Balance.wholeSeconds(ticks) + PAD_SECONDS)

        /** The Revoke at half, per seat in the house and the same on every phone (D-132, D-184). */
        val Revoke: PublicCooldown get() = restarted(Balance.REVOKE_OPENING)

        /** The Access pool at half (D-132, D-184). A Tier-2 fire's restart is the actor's own: [fired]. */
        val Access: PublicCooldown get() = restarted(Balance.ACCESS_OPENING)

        /**
         * **This phone's own Access pool, from the house's answer to its own Tier-2 fire** (D-262).
         * [weight] is the ability's (`Balance.SURGE_COOLDOWN`, `STATIC_COOLDOWN`,
         * `ISOLATE_COOLDOWN`), the one `core` spends the pool by; counted from the answer's
         * arrival, which is after the house wrote the deadline, plus the pad, so it errs late too.
         */
        fun fired(weight: Long): PublicCooldown = restarted(weight)

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
 * ARMED wins, because it is this phone's own input. **It comes down 20 s after the hold, or at a
 * meeting's call, whichever comes first** (the owner's 2026-11-18 ruling, closing D-239's open
 * item and overriding its *the house holds an arm across a meeting*): `FlowModel` writes it back
 * to READY at either moment, silently, and this then draws the public clock like every phone's.
 * **It also comes down at a touch that spent it** (the owner's 2026-11-18 ruling, D-290): the
 * armed phone's answer says so, and the tile goes straight to COOLING DOWN below.
 *
 * ### Never READY while this phone's own arm is still spending (the owner's 2026-11-18 ruling, D-286)
 *
 * After ARMED comes down the house's cooldown from that arm runs on to 60 s and refuses a hold, so
 * until D-286 the Insider's tile read READY over a Revoke that was not ready. Now COOLING DOWN
 * holds while [PanelState.ownArmClock] runs as well as while the public clock does, and the bar
 * follows whichever ends later ([revokeCooling]). Only an Insider's tile can hold, so a
 * Resident's tile reads exactly what it read before. *Parity is structure, not content*: the two
 * roles' page 2 keep one layout and one wire, and the Insider's says what its own Revoke is doing.
 *
 * Public, so the bench reads its tile the way a person does: it holds once, when this says READY.
 */
val PanelState.revokeShown: RevokeState
    get() = when {
        revoke == RevokeState.Armed -> RevokeState.Armed
        revokeCooling != null -> RevokeState.Cooldown
        else -> revoke
    }

/**
 * **The Revoke clock the tile's bar draws while it is COOLING DOWN**: of the public clock and this
 * phone's own arm (D-286), whichever is running and ends later, or null when neither runs.
 */
val PanelState.revokeCooling: PublicCooldown?
    get() = listOfNotNull(revokeClock, ownArmClock)
        .filter { it.running }
        .maxByOrNull { it.seconds - it.elapsed }
