package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Page 2's clocks are public, the same on both roles, and the REVOKE echo waits for them**
 * (D-239).
 *
 * Two real [FlowModel]s, an Insider's and a Resident's, are sent the same pushes in the same order
 * and counted the same seconds, which is what two phones in one house are. `AbilityWiringTest` in
 * `app` carries the same hold over real sockets to a real house.
 */
class PublicCooldownTest {

    private class Phone(insider: Boolean) {
        val sent = mutableListOf<IntentMessage>()
        val flow = FlowModel(PanelState(screen = ScreenId.Lobby), send = { sent += it }).apply {
            houseSaid(RoundMessage.RoleRevealed(insider = insider))
        }
        fun said(message: RoundMessage) = flow.houseSaid(message)
        fun seconds(count: Int) = repeat(count) { flow.secondPassed() }
        val vals: PanelVals get() = PanelVals(flow.state)
    }

    private val opening = RoundMessage.OpeningMessage(Haptic.Short)
    private val meetingEnded = RoundMessage.MeetingEnded(Haptic.Long)
    private val egressOpened = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(MarkerId("west-card"), "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    /**
     * **What page 2 draws that is not a role's own words.** The names (REVOKE / POWER, EGRESS /
     * SUBSYS) and the resting sub-line (READY . HOLD TO ARM / READY . ALL NOMINAL) are D-142's copy
     * and differ by design; everything the clocks drive is here, and must be equal.
     *
     * **Not the EGRESS and Tier-2 name inks** (the owner's 2026-11-18 ruling, D-286): the Insider's
     * dim while their clocks run and a Resident's do not, which
     * [tier2AndEgressDimOnOnlyTheInsiderAndOnlyWhileTheirClocksRun] pins.
     */
    private fun PanelVals.drawn(): List<Any> = listOf(
        revoke,
        if (revoke == RevokeState.Ready) "(the role's resting line)" else abilitySub,
        revokeBorder, revokeFill, revokeInk, revokeSubInk, revokeBar, revokeBarInk,
        tileBorder, secondBar, secondBarInk,
        tier2Bar, tier2BarInk,
    )

    private fun assertSameTiles(insider: Phone, resident: Phone, moment: String) =
        assertEquals(resident.vals.drawn(), insider.vals.drawn(), "page 2 differed by role at $moment")

    /**
     * **GUARD (D-142, D-239): an Insider's page 2 and a Resident's draw the same tiles at the same
     * public moment, before anybody has held anything.** Every moment a clock restarts or runs out
     * is walked: LIGHTS OUT, part-way, the pad, run out, a meeting's end, an Egress, and a meeting
     * ending during it.
     */
    @Test
    fun anInsidersPage2AndAResidentsAreTheSameAtEveryPublicMoment() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        val both = listOf(insider, resident)
        fun at(moment: String, step: (Phone) -> Unit) {
            both.forEach(step)
            assertSameTiles(insider, resident, moment)
        }

        at("LIGHTS OUT") { it.said(opening) }
        assertEquals(RevokeState.Cooldown, resident.vals.revoke, "LIGHTS OUT did not start the Resident's clock")
        assertEquals(0f, insider.vals.revokeBar, "the Revoke bar did not start empty")
        at("15 s in") { it.seconds(15) }
        assertTrue(insider.vals.revokeBar in 0.4f..0.6f, "the Revoke bar was not part-way at 15 s: ${insider.vals.revokeBar}")
        at("the house's 30 s, inside the pad") { it.seconds(15) }
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "the tile said READY before the pad ran out")
        at("31 s") { it.seconds(1) }
        assertEquals(RevokeState.Ready, insider.vals.revoke, "the tile did not say READY after the pad")
        at("the Access half run out") { it.seconds(15) }
        assertEquals(1f, insider.vals.tier2Bar)
        at("the Egress half run out") { it.seconds(45) }
        assertEquals(1f, insider.vals.secondBar)

        at("a meeting's end") { it.said(meetingEnded) }
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "a meeting's end did not restart the clock")
        assertEquals(0f, insider.vals.secondBar, "a meeting's end with no Egress running did not restart the Egress clock")
        at("an Egress opening") { it.said(egressOpened) }
        at("30 s into the Egress") { it.seconds(30) }
        val running = insider.vals.secondBar
        at("a meeting ending during the Egress") { it.said(meetingEnded) }
        assertEquals(running, insider.vals.secondBar, "a meeting reset the Egress clock while an Egress ran (D-184)")
        assertEquals(RevokeState.Cooldown, insider.vals.revoke)
    }

    /**
     * **GUARD (rule 1, D-239): an early hold sends the request and does not say ARMED; a hold after
     * the public clock sends the same request and does.**
     */
    @Test
    fun anEarlyHoldSendsTheSameRequestAndOnlyTheEchoWaits() {
        val insider = Phone(insider = true)
        insider.said(opening)
        insider.seconds(10)

        insider.flow.armRevoke()
        assertEquals(listOf<IntentMessage>(IntentMessage.ArmRevoke), insider.sent, "the early hold was not sent")
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "an early hold said ARMED")
        assertEquals("COOLING DOWN", insider.vals.abilitySub)

        insider.seconds(insider.flow.state.revokeClock!!.seconds)
        insider.flow.armRevoke()
        assertEquals(listOf<IntentMessage>(IntentMessage.ArmRevoke, IntentMessage.ArmRevoke), insider.sent, "the two holds were not sent alike")
        assertEquals(RevokeState.Armed, insider.vals.revoke, "a hold after the clock did not say ARMED")

        // A new round holds no arm.
        insider.said(opening)
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "an earlier round's ARMED survived LIGHTS OUT")
    }

    /**
     * **GUARD (the owner's 2026-11-18 rulings, D-280, D-286): ARMED comes down 20 s after the hold,
     * on this phone's own clock, and the tile reads COOLING DOWN for the rest of the arm's own
     * cooldown; nothing is sent.** A hold in that span, while the cooldown the arm spent is still
     * running at the house, sends the same request and echoes nothing, because the house refuses it
     * (D-237's ARMED over nothing).
     */
    @Test
    fun armedComesDownAtTwentySecondsAndTheTileReadsCoolingDown() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        listOf(insider, resident).forEach {
            it.said(opening)
            it.seconds(PublicCooldown.Revoke.seconds)
        }
        insider.flow.armRevoke()
        assertEquals(RevokeState.Armed, insider.vals.revoke, "the fixture's hold did not echo ARMED")

        listOf(insider, resident).forEach { it.seconds(PublicCooldown.ARM_WINDOW_SECONDS - 1) }
        assertEquals(RevokeState.Armed, insider.vals.revoke, "ARMED came down before the 20 s window closed")
        listOf(insider, resident).forEach { it.seconds(1) }
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "ARMED outlived its 20 s window, or its close read READY")
        assertEquals(RevokeState.Ready, resident.vals.revoke, "an Insider's arm moved a Resident's tile")
        assertEquals(listOf<IntentMessage>(IntentMessage.ArmRevoke), insider.sent, "the window's close sent something")

        // A hold while the arm's own cooldown runs at the house: sent, and no echo.
        insider.flow.armRevoke()
        assertEquals(2, insider.sent.size, "the hold after the window was not sent")
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "a hold into the arm's own cooldown said ARMED over nothing")
        // Past that cooldown, and its pad, the tile says READY and the next hold echoes again.
        insider.seconds(PublicCooldown.OWN_ARM_SECONDS - PublicCooldown.ARM_WINDOW_SECONDS)
        assertEquals(RevokeState.Ready, insider.vals.revoke, "the tile did not say READY once the arm's cooldown ran out")
        insider.flow.armRevoke()
        assertEquals(RevokeState.Armed, insider.vals.revoke, "a hold after the arm's cooldown did not echo ARMED")
    }

    /**
     * **GUARD (the owner's 2026-11-18 ruling, D-280; overrides D-239 item 4): a meeting call ends
     * ARMED at the ring, and the meeting's end does not bring it back.**
     */
    @Test
    fun aMeetingCallEndsArmed() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        listOf(insider, resident).forEach {
            it.said(opening)
            it.seconds(PublicCooldown.Revoke.seconds)
        }
        insider.flow.armRevoke()
        insider.seconds(3)
        resident.seconds(3)
        assertEquals(RevokeState.Armed, insider.vals.revoke, "the fixture's hold did not echo ARMED")

        val ring = RoundMessage.MeetingOpened(caller = home.someoneshome.model.Seat(0), trigger = home.someoneshome.model.MeetingTrigger.MeetingCard, haptic = Haptic.Long)
        listOf(insider, resident).forEach { it.said(ring) }
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "ARMED survived a meeting call, or the arm's cooldown read READY")
        listOf(insider, resident).forEach { it.said(meetingEnded) }
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "the meeting's end did not restart the public clock")
        assertSameTiles(insider, resident, "a meeting's end after an arm")
        // The house put every seat's Revoke back to half at the meeting's end, the arm's included
        // (`openingCooldowns`), so READY comes with the public clock and not 60 s from the arm.
        listOf(insider, resident).forEach { it.seconds(PublicCooldown.Revoke.seconds) }
        assertEquals(RevokeState.Ready, insider.vals.revoke, "the arm's own cooldown outlived the shift's reset")
        assertSameTiles(insider, resident, "the public clock's end after a meeting")
    }

    /**
     * **GUARD (decision pack 9f option a, D-282): page 2's Egress clock waits with its Egress.** The
     * house pushes the Egress cooldown back by however long the Egress was held, so the phone's copy
     * does not run while a meeting holds it, and at the house's return from off the air (D-281) it
     * gives back what this phone counted down that the house did not.
     */
    @Test
    fun theEgressClockWaitsWithItsEgress() {
        val phone = Phone(insider = false)
        phone.said(opening)
        phone.seconds(PublicCooldown.EgressOpening.seconds)
        phone.said(egressOpened)
        phone.seconds(30)
        fun elapsed() = phone.flow.state.egressClock!!.elapsed
        assertEquals(30, elapsed(), "the fixture's Egress clock did not run")

        val ticks = Balance.TICKS_PER_SECOND
        phone.said(RoundMessage.EgressHeld(remaining = 90 * ticks, running = false, haptic = Haptic.Short))
        phone.seconds(100)
        assertEquals(30, elapsed(), "page 2's Egress clock ran while a meeting held its Egress")
        phone.said(RoundMessage.EgressHeld(remaining = 90 * ticks, running = true, haptic = Haptic.Short))
        phone.seconds(10)
        assertEquals(40, elapsed(), "page 2's Egress clock did not run again after the meeting")

        // Off the air: no hold arrives, the phone counts on, and the return says 80 s are left where
        // this phone had counted down to 30. The 50 s it counted that the house did not come back.
        phone.seconds(50)
        phone.said(RoundMessage.EgressHeld(remaining = 80 * ticks, running = true, haptic = Haptic.Short))
        assertEquals(40, elapsed(), "page 2's Egress clock kept the gap the house was off the air")
    }

    /**
     * **GUARD (D-262, review #2's V3): after a Surge the actor's own Access bar shows the pool
     * cooling, and a fire during it shows not-ready.**
     *
     * Before D-262 the house's `AbilityFired` was dropped, so the bar read the public half, full,
     * while the house's pool ran for the Surge's 150 s, and a hold in that time fired into a running
     * pool and did nothing: REVOKE's silent no-op (D-237), one tier down.
     */
    @Test
    fun aSurgeRestartsTheActorsOwnAccessBarAndAFireIntoItShowsNotReady() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        listOf(insider, resident).forEach {
            it.said(opening)
            it.seconds(PublicCooldown.Access.seconds)
        }
        assertEquals(1f, insider.vals.tier2Bar, "the Access half had not run out")

        insider.flow.fireSurge(0)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        val surge = insider.flow.state.accessClock!!
        assertEquals(PublicCooldown.fired(Balance.SURGE_COOLDOWN), surge, "the Surge's answer did not restart the pool from the Surge's weight")
        assertEquals(Balance.wholeSeconds(Balance.SURGE_COOLDOWN) + PublicCooldown.PAD_SECONDS, surge.seconds)
        assertEquals(0f, insider.vals.tier2Bar, "after a Surge the actor's Access bar read ready")
        // The answer went to the actor alone; nobody else's page 2 moved.
        assertEquals(1f, resident.vals.tier2Bar, "a Resident's Access bar moved on somebody else's fire")

        insider.seconds(10)
        val before = insider.vals.tier2Bar
        assertTrue(before > 0f && before < 1f, "the bar did not fill part-way: $before")
        insider.flow.fireStatic()
        assertEquals(IntentMessage.FireStatic, insider.sent.last(), "the fire into the running pool was not sent (rule 1)")
        insider.said(RoundMessage.AbilityFired(cooldownStarted = false))
        assertEquals(before, insider.vals.tier2Bar, "a fire into the running pool moved the bar")
        assertTrue(insider.flow.state.accessClock!!.running, "the tile read ready during the Surge's pool")

        insider.seconds(surge.seconds)
        assertEquals(1f, insider.vals.tier2Bar, "the bar did not fill when the Surge's pool ran out")

        // Static and Isolate restart it from their own weights.
        insider.flow.fireStatic()
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(PublicCooldown.fired(Balance.STATIC_COOLDOWN), insider.flow.state.accessClock)
        insider.seconds(insider.flow.state.accessClock!!.seconds)
        insider.flow.fireIsolate("west-card")
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(PublicCooldown.fired(Balance.ISOLATE_COOLDOWN), insider.flow.state.accessClock)

        // A meeting's end puts the pool back on the public half, the same on both roles (D-184).
        listOf(insider, resident).forEach { it.said(meetingEnded) }
        assertSameTiles(insider, resident, "the meeting's end after a fire")
    }

    /**
     * **GUARD (D-262): an answer that is not to this phone's own Tier-2 fire moves no Access bar.**
     * An armed Revoke's contact is answered with the same `AbilityFired(true)`, and REVOKE's ARMED
     * is D-239's open question, so neither tile moves; an Egress fire's answer is not the pool's.
     */
    @Test
    fun anAnswerToNoTier2FireMovesNothing() {
        val insider = Phone(insider = true)
        insider.said(opening)
        insider.seconds(insider.flow.state.revokeClock!!.seconds)
        insider.flow.armRevoke()
        val armed = insider.flow.state

        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(armed, insider.flow.state, "a contact's answer moved the panel")

        insider.flow.armEgress()
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(armed.accessClock, insider.flow.state.accessClock, "an Egress fire's answer restarted the Access bar")
        assertEquals(RevokeState.Armed, insider.vals.revoke)

        // A Tier-2 fire whose answer was owed when the meeting reset the pool belongs to no bar.
        insider.flow.fireStatic()
        insider.said(meetingEnded)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(PublicCooldown.Access, insider.flow.state.accessClock, "an answer owed across a meeting restarted the bar")
    }

    /** Both phones, past the public Revoke clock, the Insider holding REVOKE once. */
    private fun armedPair(): Pair<Phone, Phone> {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        listOf(insider, resident).forEach {
            it.said(opening)
            it.seconds(PublicCooldown.Revoke.seconds)
        }
        insider.flow.armRevoke()
        assertEquals(RevokeState.Armed, insider.vals.revoke, "the fixture's hold did not echo ARMED")
        return insider to resident
    }

    /**
     * **GUARD (the owner's 2026-11-18 ruling, D-286): the Insider's REVOKE never reads READY while
     * its own arm is still spending its cooldown.** Second by second from the hold: ARMED for the
     * window, then COOLING DOWN in the dimmed ink with a bar filling over the arm's own cooldown,
     * then READY at [PublicCooldown.OWN_ARM_SECONDS] and not before.
     */
    @Test
    fun theInsiderInTheArmsGapReadsCoolingDownDimmedAndNeverReady() {
        val (insider, _) = armedPair()
        var lastBar = -1f
        for (second in 1 until PublicCooldown.OWN_ARM_SECONDS) {
            insider.seconds(1)
            val vals = insider.vals
            assertTrue(vals.revoke != RevokeState.Ready, "the Insider's tile said READY $second s after its own arm")
            if (second >= PublicCooldown.ARM_WINDOW_SECONDS) {
                assertEquals(RevokeState.Cooldown, vals.revoke, "not COOLING DOWN $second s after the arm")
                assertEquals("COOLING DOWN", vals.abilitySub)
                assertEquals(Ramp.Dim, vals.revokeInk, "REVOKE was not dimmed $second s after the arm")
                assertTrue(vals.revokeBar > lastBar && vals.revokeBar < 1f, "the bar did not fill over the arm's cooldown at $second s: ${vals.revokeBar}")
                lastBar = vals.revokeBar
            }
        }
        insider.seconds(1)
        assertEquals(RevokeState.Ready, insider.vals.revoke, "the tile did not say READY at ${PublicCooldown.OWN_ARM_SECONDS} s")
        assertEquals("READY . HOLD TO ARM", insider.vals.abilitySub)
        assertEquals(Ramp.Ink, insider.vals.revokeInk)
        assertEquals(1f, insider.vals.revokeBar)
    }

    /**
     * **GUARD (D-286, against `core`): a touch while armed leaves the arm's own cooldown running
     * from the arm, not from the touch.** The house spends the 60 s at the arm (`revokeArmed`:
     * `withCooldown(cooldown.restartedAt(event.at, Balance.REVOKE_COOLDOWN))`) and a contact clears
     * the arm without restarting it (`contact`: `revokeArms.filterNot { … }`, answered
     * `AbilityFired(cooldownStarted = true)`), so the tile is COOLING DOWN after ARMED and READY at
     * the arm's 61 s, whenever the touch came.
     */
    @Test
    fun aContactWhileArmedLeavesTheArmsOwnCooldownRunningFromTheArm() {
        val (insider, _) = armedPair()
        insider.seconds(5)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        for (second in 6 until PublicCooldown.OWN_ARM_SECONDS) {
            insider.seconds(1)
            assertTrue(insider.vals.revoke != RevokeState.Ready, "the tile said READY $second s after an arm whose contact landed")
            if (second >= PublicCooldown.ARM_WINDOW_SECONDS) assertEquals(RevokeState.Cooldown, insider.vals.revoke)
        }
        insider.seconds(1)
        assertEquals(RevokeState.Ready, insider.vals.revoke, "the contact restarted the arm's own cooldown")
    }

    /**
     * **GUARD (the owner's 2026-11-18 ruling, D-286): SURGE, STATIC and ISOLATE dim on the Insider
     * while the Access pool runs, and EGRESS while the house-wide Egress clock runs; a Resident's
     * BUS, THERMAL, CACHE and SUBSYS never do.**
     */
    @Test
    fun tier2AndEgressDimOnOnlyTheInsiderAndOnlyWhileTheirClocksRun() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        val both = listOf(insider, resident)
        fun residentBaseline(moment: String) {
            assertEquals(Ramp.Mid, resident.vals.tier2Ink, "a Resident's Tier-2 names moved at $moment")
            assertEquals(Ramp.Ink, resident.vals.secondInk, "a Resident's SUBSYS moved at $moment")
        }

        both.forEach { it.said(opening) }
        assertEquals(Ramp.Dim, insider.vals.tier2Ink, "the Insider's Tier-2 names were not dimmed while the Access half ran")
        assertEquals(Ramp.Dim, insider.vals.secondInk, "the Insider's EGRESS was not dimmed while the Egress half ran")
        residentBaseline("LIGHTS OUT")

        both.forEach { it.seconds(PublicCooldown.Access.seconds) }
        assertEquals(Ramp.Mid, insider.vals.tier2Ink, "the Insider's Tier-2 names stayed dim after the Access half ran out")
        assertEquals(Ramp.Dim, insider.vals.secondInk, "EGRESS lit before its own clock ran out")
        residentBaseline("the Access half's end")

        both.forEach { it.seconds(PublicCooldown.EgressOpening.seconds - PublicCooldown.Access.seconds) }
        assertEquals(Ramp.Ink, insider.vals.secondInk, "the Insider's EGRESS stayed dim after the Egress half ran out")
        residentBaseline("the Egress half's end")

        insider.flow.fireSurge(0)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        assertEquals(Ramp.Dim, insider.vals.tier2Ink, "the Insider's Tier-2 names were not dimmed after its own Surge")
        residentBaseline("the Insider's Surge")
        insider.seconds(insider.flow.state.accessClock!!.seconds)
        resident.seconds(insider.flow.state.accessClock!!.seconds)
        assertEquals(Ramp.Mid, insider.vals.tier2Ink, "the Tier-2 names stayed dim after the Surge's pool ran out")

        both.forEach { it.said(egressOpened) }
        assertEquals(Ramp.Dim, insider.vals.secondInk, "the Insider's EGRESS was not dimmed after an Egress fired")
        residentBaseline("an Egress")
        // The REVOKE and the tile frames are not this ruling's: unchanged on both.
        assertEquals(Ramp.Ink, insider.vals.tileInk)
        assertEquals(Ramp.Line, insider.vals.tileBorder)
    }

    /**
     * **GUARD (D-286: a Resident's page is unchanged): every word, ink and bar on a Resident's page 2
     * is what the code before D-286 drew, at every second of a shift in which an Insider armed,
     * touched, was rung to a meeting, fired a Surge and an Egress.** [before] is that code's
     * arithmetic, written out; the Resident's phone is sent every push the Insider's is.
     */
    @Test
    fun aResidentsPage2IsWhatItWasBeforeD286() {
        fun before(s: PanelState): List<Any> {
            val revoke = when {
                s.revoke == RevokeState.Armed -> RevokeState.Armed
                s.revokeClock?.running == true -> RevokeState.Cooldown
                else -> s.revoke
            }
            return listOf(
                "POWER",
                when (revoke) {
                    RevokeState.Armed -> "ARMED . TOUCH THEIR PHONE"
                    RevokeState.Cooldown -> "COOLING DOWN"
                    RevokeState.Ready -> "READY . ALL NOMINAL"
                },
                if (revoke == RevokeState.Armed) Ramp.Ink else Ramp.Line,
                if (revoke == RevokeState.Armed) Ramp.Fill else androidx.compose.ui.graphics.Color.Transparent,
                if (revoke == RevokeState.Cooldown) Ramp.Dim else Ramp.Ink,
                if (revoke == RevokeState.Armed) Ramp.Ink else Ramp.Dim,
                if (revoke == RevokeState.Cooldown) s.revokeClock?.fraction ?: 0.38f else 1f,
                if (revoke == RevokeState.Cooldown) Ramp.Line else Ramp.Ink,
                "SUBSYS", "ALL SYSTEMS RESPONDING", Ramp.Line, Ramp.Ink,
                s.egressClock?.fraction ?: 0.64f,
                if (s.egressClock?.running == false) Ramp.Ink else Ramp.Line,
                "BUS", "THERMAL", "CACHE", Ramp.Mid,
                s.accessClock?.fraction ?: 0.82f, Ramp.Mid,
                "BUS, THERMAL AND CACHE NOMINAL",
            )
        }
        fun PanelVals.now(): List<Any> = listOf(
            abilityName, abilitySub, revokeBorder, revokeFill, revokeInk, revokeSubInk, revokeBar, revokeBarInk,
            secondName, secondSub, tileBorder, secondInk, secondBar, secondBarInk,
            tier2A, tier2B, tier2C, tier2Ink, tier2Bar, tier2BarInk, tier2Note,
        )

        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        val both = listOf(insider, resident)
        var checked = 0
        fun check(moment: String) {
            assertEquals(before(resident.flow.state), resident.vals.now(), "a Resident's page 2 changed at $moment")
            checked++
        }
        fun seconds(count: Int, what: String) = repeat(count) { i ->
            both.forEach { it.seconds(1) }
            check("$what +${i + 1} s")
        }

        both.forEach { it.said(opening) }
        check("LIGHTS OUT")
        seconds(PublicCooldown.Revoke.seconds, "LIGHTS OUT")
        insider.flow.armRevoke()
        check("the Insider's arm")
        seconds(5, "the arm")
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        seconds(PublicCooldown.OWN_ARM_SECONDS, "the touch")
        insider.flow.fireSurge(0)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        seconds(10, "the Surge")
        insider.flow.armRevoke()
        seconds(3, "a second arm")
        val ring = RoundMessage.MeetingOpened(caller = home.someoneshome.model.Seat(0), trigger = home.someoneshome.model.MeetingTrigger.MeetingCard, haptic = Haptic.Long)
        both.forEach { it.said(ring) }
        check("the ring")
        both.forEach { it.said(meetingEnded) }
        seconds(PublicCooldown.EgressOpening.seconds, "the meeting's end")
        both.forEach { it.said(egressOpened) }
        seconds(30, "the Egress")
        assertTrue(checked > 200, "the walk checked too few moments: $checked")
    }

    /** The phone's clocks are the house's halves, from `Balance`, plus the pad, and nothing else. */
    @Test
    fun theClocksAreTheHousesHalvesAndThePad() {
        val pad = PublicCooldown.PAD_SECONDS
        assertEquals(Balance.wholeSeconds(Balance.REVOKE_COOLDOWN / 2) + pad, PublicCooldown.Revoke.seconds)
        assertEquals(Balance.wholeSeconds(Balance.ACCESS_COOLDOWN / 2) + pad, PublicCooldown.Access.seconds)
        assertEquals(Balance.wholeSeconds(Balance.EGRESS_COOLDOWN / 2) + pad, PublicCooldown.EgressOpening.seconds)
        assertEquals(Balance.wholeSeconds(Balance.EGRESS_COOLDOWN) + pad, PublicCooldown.EgressFired.seconds)
        assertEquals(31, PublicCooldown.Revoke.seconds, "the Revoke's opening moved; D-239 and the READMEs say 30 s")
    }
}
