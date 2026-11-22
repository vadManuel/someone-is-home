package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN on the host's lobby: VOTING's control, VOTING's
 * restriction, and the numbers the rulings give** (the owner's 2026-11-21 rulings, D-317, D-320).
 *
 * The lobby half of the road `LobbySettingsArmingTest` (app) and `LobbySettingsTest` (core) walk the
 * rest of, plus the phone half at the other end: once a round runs, what a phone draws for each is
 * the number the house pushed, never this build's default.
 */
class LobbySettingsTest {

    private fun lobbyOf(hosting: Boolean): Pair<LobbyModel, MemoryLobbyLink> {
        val link = MemoryLobbyLink(joined = 6, linesIn = 0)
        val model = LobbyModel(MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))), link, hosting = hosting)
        // `LobbyModelTest`'s arrangement: named, then attached, whichever phone this is.
        model.look()
        model.nameResident("ELLIOT")
        model.attachTo(model.nearby.first())
        return model to link
    }

    /** One row: its label, its press, and what its link was told. */
    private class Row(val name: String, val label: () -> String, val press: () -> Unit, val told: () -> List<Int>)

    private fun rows(model: LobbyModel, link: MemoryLobbyLink) = listOf(
        Row("DISCUSSION", { model.discussionLabel }, model::cycleDiscussion, { link.discussions }),
        Row("EGRESS TIMER", { model.egressTimerLabel }, model::cycleEgressTimer, { link.egressTimers }),
        Row("REVOKE COOLDOWN", { model.revokeCooldownLabel }, model::cycleRevokeCooldown, { link.revokeCooldowns }),
    )

    /**
     * **GUARD (D-320): each row offers the owner's range, and starts at the unchanged default** (the
     * owner's 2026-11-21 ruling: DISCUSSION 60/90/120, EGRESS TIMER 90/120/150, REVOKE COOLDOWN
     * 45/60/90; defaults 90, 120, 60).
     */
    @Test
    fun eachRowOffersTheOwnersRangeFromTheUnchangedDefault() {
        assertEquals(listOf(60, 90, 120), LobbyModel.DISCUSSIONS, "DISCUSSION does not offer the owner's range")
        assertEquals(listOf(90, 120, 150), LobbyModel.EGRESS_TIMERS, "EGRESS TIMER does not offer the owner's range")
        assertEquals(listOf(45, 60, 90), LobbyModel.REVOKE_COOLDOWNS, "REVOKE COOLDOWN does not offer the owner's range")
        assertEquals(
            listOf(90, 120, 60),
            listOf(Balance.DISCUSSION_SECONDS, Balance.EGRESS_TIMER_SECONDS, Balance.REVOKE_COOLDOWN_SECONDS),
            "a default moved",
        )
        val (model, link) = lobbyOf(hosting = true)
        assertEquals(listOf("90S", "120S", "60S"), rows(model, link).map { it.label() })
    }

    /**
     * **GUARD (D-317's two bounds, D-320): no choice breaks them.** The Egress cooldown (180 s) must
     * outlive the Egress timer, and the Revoke cooldown must outlive the 20 s arm window. Read
     * against `Balance`'s own numbers, so a cooldown that moved is checked as well as a range.
     */
    @Test
    fun noChoiceBreaksTheTwoBounds() {
        for (seconds in LobbyModel.EGRESS_TIMERS) {
            assertTrue(
                seconds * Balance.TICKS_PER_SECOND < Balance.EGRESS_COOLDOWN,
                "an EGRESS TIMER of ${seconds}s does not end before the ${Balance.EGRESS_COOLDOWN / Balance.TICKS_PER_SECOND}s Egress cooldown",
            )
        }
        for (seconds in LobbyModel.REVOKE_COOLDOWNS) {
            assertTrue(
                seconds * Balance.TICKS_PER_SECOND > Balance.REVOKE_ARM_WINDOW,
                "a REVOKE COOLDOWN of ${seconds}s does not outlive the ${Balance.REVOKE_ARM_WINDOW / Balance.TICKS_PER_SECOND}s arm window",
            )
        }
    }

    /**
     * **GUARD (D-320): the host's press cycles through the range and back, and the house is told
     * each choice.** Up from the default, round to the lowest, back to the default: the number on
     * the row is the one the house holds, as the standing carries it back.
     */
    @Test
    fun theHostsPressCyclesEachRowThroughItsRange() {
        val (model, link) = lobbyOf(hosting = true)
        val expected = mapOf(
            "DISCUSSION" to listOf(120, 60, 90),
            "EGRESS TIMER" to listOf(150, 90, 120),
            "REVOKE COOLDOWN" to listOf(90, 45, 60),
        )
        for (row in rows(model, link)) {
            assertEquals(emptyList(), row.told(), "an untouched ${row.name} row told the house something")
            val seen = mutableListOf<String>()
            repeat(3) {
                row.press()
                seen += row.label()
            }
            val cycle = expected.getValue(row.name)
            assertEquals(cycle, row.told(), "the host pressed ${row.name} three times and the house was told ${row.told()}")
            assertEquals(cycle.map { "${it}S" }, seen, "${row.name} did not draw the number the house holds")
        }
    }

    /** **And only the host's**: a guest's press reaches nothing and moves nothing. */
    @Test
    fun aGuestsPressOnEachRowReachesNothing() {
        val (model, link) = lobbyOf(hosting = false)
        for (row in rows(model, link) + Row("VOTING", { model.voteWindowLabel }, model::cycleVoteWindow, { link.windows })) {
            val before = row.label()
            repeat(5) { row.press() }
            assertEquals(emptyList(), row.told(), "a phone that is not hosting sent the house a ${row.name}")
            assertEquals(before, row.label(), "a guest moved the host's ${row.name}")
        }
    }

    /**
     * **GUARD (D-320): a guest's rows draw the host's numbers, not its own phone's.** The gap D-317
     * found: the four timed settings never went down the lobby wire, so a guest's VOTING, DISCUSSION,
     * EGRESS TIMER and REVOKE COOLDOWN were this phone's defaults whatever the host chose. The
     * standing carries them now, and the guest draws what it says.
     */
    @Test
    fun aGuestsRowsDrawTheHostsNumbers() {
        val (model, link) = lobbyOf(hosting = false)
        assertEquals(listOf("45S", "90S", "120S", "60S"), guestRows(model), "a guest's lobby did not start at the defaults")
        link.hostMoved(voteWindowSeconds = 90, discussionSeconds = 120, egressTimerSeconds = 150, revokeCooldownSeconds = 45)
        assertEquals(
            listOf("90S", "120S", "150S", "45S"), guestRows(model),
            "a guest's lobby drew its own phone's numbers, not the host's",
        )
    }

    private fun guestRows(model: LobbyModel) =
        listOf(model.voteWindowLabel, model.discussionLabel, model.egressTimerLabel, model.revokeCooldownLabel)

    // ---- Once the round runs, the phone draws the locked number --------------------------------

    private fun phone() = FlowModel(PanelState(screen = ScreenId.Lobby)).apply {
        houseSaid(RoundMessage.RoleRevealed(insider = true))
    }

    /**
     * **Page 2's Revoke clock is half of the cooldown the house pushed, not of the default** (D-132,
     * D-239, D-317): a 30 s cooldown opens at 15 s, plus the pad, at LIGHTS OUT and at a meeting's end.
     */
    @Test
    fun theRevokeClockIsHalfOfThePushedCooldown() {
        val thirty = 30L * Balance.TICKS_PER_SECOND
        val phone = phone()
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, thirty))
        assertEquals(
            15 + PublicCooldown.PAD_SECONDS, phone.state.revokeClock?.seconds,
            "LIGHTS OUT drew the Revoke clock against the default cooldown, not the locked one",
        )
        phone.houseSaid(RoundMessage.MeetingEnded(Haptic.Long, thirty))
        assertEquals(
            15 + PublicCooldown.PAD_SECONDS, phone.state.revokeClock?.seconds,
            "a meeting's end drew the Revoke clock against the default cooldown, not the locked one",
        )
    }

    /** **An Insider's own arm holds for the whole of the pushed cooldown** (D-286, D-317). */
    @Test
    fun anOwnArmHoldsForThePushedCooldown() {
        val phone = phone()
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, 30L * Balance.TICKS_PER_SECOND))
        repeat(PublicCooldown.revoke(30L * Balance.TICKS_PER_SECOND).seconds) { phone.secondPassed() }
        phone.armRevoke()
        assertEquals(
            30 + PublicCooldown.PAD_SECONDS, phone.state.ownArmClock?.seconds,
            "an arm under a 30 s cooldown held its echo for the default's sixty",
        )
    }

    /** **The talk's clock starts at the length the house pushed** (D-243, D-317). */
    @Test
    fun theTalksClockStartsAtThePushedLength() {
        val phone = phone()
        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Discussion, voteWindowSeconds = null, discussionSeconds = 60, haptic = Haptic.Short))
        assertEquals(60, phone.state.secondsLeft, "the talk's clock started at the default, not at the locked 60 s")
        assertEquals(60, phone.state.discussionSeconds)
        // The ballot's push does not blank it: the couch draws the whole meeting from one screen.
        phone.houseSaid(RoundMessage.MeetingPhaseOpened(MeetingPhase.Vote, voteWindowSeconds = 45, discussionSeconds = null, haptic = Haptic.Short))
        assertEquals(60, phone.state.discussionSeconds, "the ballot's push blanked the talk's length")
    }
}
