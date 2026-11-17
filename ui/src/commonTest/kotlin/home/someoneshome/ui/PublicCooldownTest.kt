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
    )

    /**
     * **What page 2 draws that is not a role's own words.** The names (REVOKE / POWER, EGRESS /
     * SUBSYS) and the resting sub-line (READY . HOLD TO ARM / READY . ALL NOMINAL) are D-142's copy
     * and differ by design; everything the clocks drive is here, and must be equal.
     */
    private fun PanelVals.drawn(): List<Any> = listOf(
        revoke,
        if (revoke == RevokeState.Ready) "(the role's resting line)" else abilitySub,
        revokeBorder, revokeFill, revokeInk, revokeSubInk, revokeBar, revokeBarInk,
        tileBorder, tileInk, secondBar, secondBarInk,
        tier2Bar, tier2BarInk, tier2Ink,
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

        // The house keeps an arm across a meeting (D-184 resets the clock, not the arm), so does the tile.
        insider.said(meetingEnded)
        assertEquals(RevokeState.Armed, insider.vals.revoke, "a meeting's end dropped an arm the house still holds")
        // A new round holds no arm.
        insider.said(opening)
        assertEquals(RevokeState.Cooldown, insider.vals.revoke, "an earlier round's ARMED survived LIGHTS OUT")
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
