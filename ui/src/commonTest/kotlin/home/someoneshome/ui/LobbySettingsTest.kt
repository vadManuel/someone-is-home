package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN on the host's lobby: VOTING's control, VOTING's
 * restriction, and the numbers the rulings give** (the owner's 2026-11-21 ruling, D-317).
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
     * **Each row offers the ruled number and only it** (D-014, `gdd.md:382`, `:431`, `:930`,
     * `:1181`-`:1186`). No ruling gives a range or a step, so the current value is the only
     * choice; the owner sets the rest. A build that invented a range fails here.
     */
    @Test
    fun eachRowOffersTheRuledNumberAndNothingElse() {
        assertEquals(listOf(90), LobbyModel.DISCUSSIONS, "DISCUSSION offers a choice no ruling gives")
        assertEquals(listOf(120), LobbyModel.EGRESS_TIMERS, "EGRESS TIMER offers a choice no ruling gives")
        assertEquals(listOf(60), LobbyModel.REVOKE_COOLDOWNS, "REVOKE COOLDOWN offers a choice no ruling gives")
        val (model, _) = lobbyOf(hosting = true)
        assertEquals(listOf("90S", "120S", "60S"), rows(model, MemoryLobbyLink()).map { it.label() })
    }

    /**
     * **The host's press goes to the house, every time, as VOTING's does** (D-117's shape). With one
     * choice the number does not move, and the press still reaches the desk a round is armed from,
     * so a range the owner rules is a list that grows and nothing else.
     */
    @Test
    fun theHostsPressOnEachRowReachesTheHouse() {
        val (model, link) = lobbyOf(hosting = true)
        for (row in rows(model, link)) {
            assertEquals(emptyList(), row.told(), "an untouched ${row.name} row told the house something")
            val before = row.label()
            repeat(3) { row.press() }
            assertEquals(
                List(3) { before.removeSuffix("S").toInt() }, row.told(),
                "the host pressed ${row.name} three times and the house was told ${row.told()}",
            )
            assertEquals(before, row.label(), "${row.name} moved to a number no ruling gives")
        }
    }

    /** **And only the host's**: a guest's press reaches nothing and moves nothing. */
    @Test
    fun aGuestsPressOnEachRowReachesNothing() {
        val (model, link) = lobbyOf(hosting = false)
        for (row in rows(model, link)) {
            val before = row.label()
            repeat(5) { row.press() }
            assertEquals(emptyList(), row.told(), "a phone that is not hosting sent the house a ${row.name}")
            assertEquals(before, row.label(), "a guest moved the host's ${row.name}")
        }
    }

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
