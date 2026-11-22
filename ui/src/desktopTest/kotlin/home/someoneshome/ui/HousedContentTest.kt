package home.someoneshome.ui

import home.someoneshome.model.Balance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A housed phone draws what the house said, or nothing — never the design's people, counts or
 * times** (D-247; the owner, 2026-11-16: *can we stop having fake things in the app*).
 *
 * One test per stand-in that was still reachable on a phone in a round, each fed the house's own
 * [RoundMessage]s, and then **one sweep that renders every screen in the game on a housed phone in
 * both roles** and asserts that none of the design's fixture strings is anywhere on the glass. The
 * unhoused render harness keeps every fixture, so its pictures still compare to the mockup
 * ([HouseNoticeTest]'s pattern); the last tests here hold that half.
 *
 * The names are deliberately none of the design's: a fixture name on a housed screen is then a
 * fixture and cannot be the house's.
 */
@OptIn(ExperimentalTestApi::class)
class HousedContentTest {

    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")
    private val now = WallTime(hour = 23, minute = 46, weekday = 2, day = 17, month = 11)
    private val line = "i ate the last of the cake"

    /** A phone a house has attached to, with the round's roster and its role. */
    private fun housed(insider: Boolean = false, roster: Boolean = true): FlowModel =
        FlowModel(
            PanelState(screen = ScreenId.Home),
            // The app hands the phone its own saved homes; the design's three are the harness's.
            homes = SavedHomesModel(),
            lobby = LobbyModel(link = MemoryLobbyLink(joined = 1, others = names.drop(1))).also { it.nameResident("ALBA") },
            wallClock = { now },
        ).also { phone ->
            phone.houseAttached(true)
            if (roster) phone.houseSaid(RoundMessage.RosterIssued(names))
            phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN))
            phone.houseSaid(RoundMessage.RoleRevealed(insider = insider, line = if (insider) line else ""))
        }

    /** Every piece of text [phone] draws on [screen], drawn the way `FlowHost` draws it. */
    private fun drawn(phone: FlowModel, screen: ScreenId, state: PanelState = phone.state): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        state.arrivingAt(screen), phone.actions(), phone.editor, phone.homes, phone.lobby,
                        phone.meeting, phone.subroutines, phone.notifications, perms = phone.perms, lamp = phone.lamp,
                    )
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }

    // ---- 1 · The Insider's own line -------------------------------------------------------------

    /** **GUARD: a housed Insider's thread quotes the line that phone handed over, and no stranger's.** */
    @Test
    fun aHousedInsiderIsQuotedTheirOwnLine() {
        val insider = housed(insider = true)
        val vals = PanelVals(insider.state)
        assertTrue(line in vals.houseLine, "the house did not quote the Insider's own line: ${vals.houseLine}")
        assertTrue("Priya" !in vals.houseLine, "a housed Insider was held to the design's spare key")
        assertEquals(vals.houseLine, vals.houseThread.last().second, "the thread's newest text is not the quote")
        val thread = drawn(insider, ScreenId.RevealThread)
        assertTrue(thread.any { line in it }, "the thread screen did not draw the quote: $thread")
        assertTrue(insider.state.toString().contains(line).not(), "the panel's toString printed the line")
    }

    /** A Resident is never quoted, whatever the message carries, and the quote goes with the round. */
    @Test
    fun aResidentIsNeverQuotedAndTheQuoteGoesWithTheRound() {
        val resident = housed()
        resident.houseSaid(RoundMessage.RoleRevealed(insider = false, line = "should never be drawn"))
        assertNull(resident.state.heldOver, "a Resident's phone held a line")
        assertEquals("I am watching you. Thank you for your cooperation.", PanelVals(resident.state).houseLine)

        val insider = housed(insider = true)
        insider.houseSaid(RoundMessage.RoundEnded(Winner.Residents, WinRoute.InsidersRestrained, Haptic.Short, integrity = 60))
        assertNull(insider.state.heldOver, "the quoted line outlived the round")
        assertEquals(PanelVals.HOUSE_INSTRUCTION, PanelVals(insider.state).houseLine)
    }

    // ---- 2 · PERIMETER ARMED's count ----------------------------------------------------------

    /** **GUARD: the armed screen counts the round's seats.** */
    @Test
    fun theArmedScreenCountsTheRoundsSeats() {
        val texts = drawn(housed(), ScreenId.Armed)
        assertTrue(texts.any { "5 RESIDENTS" in it }, "the armed screen did not count five: $texts")
        assertTrue(texts.none { "6 RESIDENTS" in it }, "the armed screen drew the design's six: $texts")
    }

    // ---- 3 · The meeting counts -----------------------------------------------------------------

    /** **GUARD: a housed meeting draws the house's counts, starts each meeting unsaid, and draws no answered count.** */
    @Test
    fun aHousedMeetingDrawsOnlyTheHousesCounts() {
        val phone = housed()
        assertEquals(MeetingCounts.UNSAID, phone.meeting.counts, "a housed phone holds the design's counts")
        phone.houseSaid(RoundMessage.MeetingOpened(Seat(1), MeetingTrigger.MeetingCard, Haptic.Long))
        phone.houseSaid(RoundMessage.MeetingCalling)
        val calling = drawn(phone, ScreenId.Calling)
        assertTrue(calling.none { "ANSWERED" in it }, "the caller's screen drew a count no house sent: $calling")

        phone.houseSaid(RoundMessage.CheckInProgressed(present = 2, expected = 5))
        phone.houseSaid(RoundMessage.ReadyProgressed(ready = 1, expected = 4))
        phone.houseSaid(RoundMessage.VoteProgressed(locked = 3, expected = 4))
        assertTrue(drawn(phone, ScreenId.Assemble).any { it == "2 OF 5 CHECKED IN" }, "the gate's count is not the house's")
        assertTrue(drawn(phone, ScreenId.Discussion).any { it == "1 OF 4 READY" }, "the hands are not the house's")
        assertTrue(drawn(phone, ScreenId.Vote).any { it == "3 OF 4 VOTED" }, "the ballots are not the house's")

        // The next meeting starts unsaid: not the last one's counts, not the design's.
        phone.houseSaid(RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals(MeetingCounts.UNSAID, phone.meeting.counts, "a new meeting kept the last one's counts")
        assertTrue(drawn(phone, ScreenId.Assemble).none { "CHECKED IN" in it }, "an uncounted gate drew a count")
    }

    // ---- 4 · The endings ------------------------------------------------------------------------

    private fun ended(phone: FlowModel, contained: Int): FlowModel = phone.apply {
        repeat(contained) { houseSaid(RoundMessage.EgressContained(Haptic.Long)) }
        houseSaid(RoundMessage.RoundEnded(Winner.Residents, WinRoute.InsidersRestrained, Haptic.Short, integrity = 60))
        houseSaid(RoundMessage.InsidersRevealed(listOf(InsiderNamed(Seat(2), "CYRUS", line))))
    }

    /** **GUARD: the Residents' ending names the house's Residents and counts the house's Egresses.** */
    @Test
    fun theResidentsEndingIsTheHouses() {
        val phone = ended(housed(), contained = 1)
        assertEquals(listOf("ALBA", "BRUNO", "DEVI", "ENZO"), phone.state.residents)
        val texts = drawn(phone, ScreenId.WinResidents)
        for (name in listOf("ALBA", "BRUNO", "DEVI", "ENZO")) assertTrue(name in texts, "$name is not among the Residents: $texts")
        assertTrue("1" in texts, "the contained Egress was not counted: $texts")
        assertTrue(texts.none { it == "2" || "4 OF 6" in it }, "the ending drew the design's counts: $texts")
    }

    /** A round with no contained Egress says none, and a new round starts the count again. */
    @Test
    fun theEgressCountIsThisRounds() {
        val phone = ended(housed(), contained = 2)
        assertEquals(2, phone.state.egressesContained)
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN))
        assertEquals(0, phone.state.egressesContained, "a new round kept the last one's Egresses")
        assertNull(phone.state.residents, "a new round kept the last one's Residents")
    }

    /**
     * **GUARD (decision pack 9b option a, D-284): the ending draws the final System Integrity the
     * house sent with it, even when no meeting ever told this phone the meter.**
     */
    @Test
    fun theEndingDrawsTheFinalMeterWithNoMeetingBehindIt() {
        val phone = housed()
        assertNull(phone.state.integrityPercent, "the fixture phone was told a meter before its ending")
        phone.houseSaid(RoundMessage.RoundEnded(Winner.Insiders, WinRoute.Parity, Haptic.Short, integrity = 85))
        assertEquals("85%", PanelVals(phone.state).integrityPercent, "the ending drew no reading where the house sent one")
        assertTrue("85%" in drawn(phone, ScreenId.WinInsiders), "the Insiders' ending did not draw the final meter")
    }

    // ---- 5 · The reconnect line, the callers, the inbox ----------------------------------------

    /** **GUARD: the reconnecting screen counts no attempts nobody counted.** */
    @Test
    fun aHousedReconnectCountsNoAttempts() {
        val texts = drawn(housed(), ScreenId.Disconnect)
        assertTrue("RECONNECTING" in texts, "the screen did not draw: $texts")
        assertTrue(texts.none { "ATTEMPT" in it }, "a housed phone drew the design's attempts: $texts")
    }

    /** **GUARD: a housed ring names the house's caller, and nobody when the house named nobody.** */
    @Test
    fun aHousedRingNamesOnlyTheHousesCaller() {
        val phone = housed()
        phone.houseSaid(RoundMessage.MeetingOpened(Seat(1), MeetingTrigger.MeetingCard, Haptic.Long))
        assertTrue("BRUNO" in drawn(phone, ScreenId.Call), "the ring did not name the caller")

        val unnamed = housed(roster = false)
        unnamed.houseSaid(RoundMessage.MeetingOpened(Seat(1), MeetingTrigger.MeetingCard, Haptic.Long))
        for (screen in listOf(ScreenId.Call, ScreenId.Found)) {
            val texts = drawn(unnamed, screen)
            assertTrue(texts.none { it == "PRIYA" || it == "ELLIOT" }, "a housed ring named the design's caller on $screen: $texts")
        }
    }

    /** **GUARD: a housed inbox holds the house's row and nothing else** (provisional, owner to confirm). */
    @Test
    fun aHousedInboxHoldsOnlyTheHouse() {
        val vals = PanelVals(housed().state)
        assertEquals(listOf("HOUSE"), vals.inbox.map { it.from }, "a housed inbox held the design's backlog")
        assertEquals(vals.houseThread.size, vals.inboxCount)
    }

    /**
     * **GUARD: a house text arrives under its own words and the time it landed**, not the design's
     * `you're up` from NUMBER WITHHELD at 21:03, and swiping the banner takes it off the lock screen.
     */
    @Test
    fun aHouseTextArrivesAsItself() {
        val phone = housed()
        phone.dismissNotification() // the opening banner, swiped
        phone.houseSaid(RoundMessage.MessageDelivered("Do not run."))
        assertEquals(ScreenId.Quiet, phone.state.screen)
        val texts = drawn(phone, ScreenId.Quiet)
        assertTrue("Do not run." in texts, "the banner did not draw the house's text: $texts")
        assertTrue(texts.any { "23:46" in it }, "the banner did not draw when it landed: $texts")
        assertTrue(texts.none { "you're up" in it || "21:03" in it }, "the banner drew the design's: $texts")
        phone.dismissNotification()
        assertTrue(phone.notifications.standing.none { it.body == "Do not run." }, "the swipe left the text on the lock screen")
    }

    // ---- 6 · The sweep --------------------------------------------------------------------------

    /**
     * **THE SWEEP: no screen in the game draws a design fixture on a housed phone, in either role.**
     *
     * Every [ScreenId], in a round the house has armed, mid-meeting, and the endings after it ended.
     * Matched as whole words and case-blind, so `rose` in a note and `ROSE` on a chip both count.
     */
    @Test
    fun noHousedScreenDrawsAFixture() {
        val fixtures = Regex(
            listOf(
                "ELLIOT", "PRIYA", "MARCUS", "ROSE", "DANI", "TOMAS", "MUM", "BANK", "DELIVERY", "VOICEMAIL",
                "FLAT CHAT", "4 OF 6", "6 RESIDENTS", "ATTEMPT 3", "spare key",
                "20:12", "20:19", "20:31", "20:41", "20:44", "20:47", "20:52", "20:55", "20:58", "20:59",
                "21:01", "21:02", "21:03", "21:04", "21:07", "21:31", "21:38", "SATURDAY 16 AUGUST",
                "you're up", "NUMBER WITHHELD", "1 ITEM", "DISK 01",
            ).joinToString("|", prefix = "\\b(", postfix = ")\\b") { Regex.escape(it) },
            RegexOption.IGNORE_CASE,
        )
        // **The port's bungalow, on every screen of the round** (D-257, unit 4d): its rooms and
        // floors on the couch and the Terminal, the playback's offset, FILES' standing destination.
        // Not swept on the setup screens, because there these words are the editor's own controls:
        // the room panel's preset chips (KITCHEN, LIVING, GARAGE), its STAIRS type and the empty
        // storey's GROUND. Whether a real phone's editor opens on the bungalow is EditorOpeningTest's
        // to hold (D-270): it opens empty.
        val bungalow = Regex(
            listOf("KITCHEN", "LIVING", "HALL", "STAIRS", "STUDY", "GARAGE", "GROUND", "UPPER", "-1:12", "RACK")
                .joinToString("|", prefix = "(?<![\\w-])(", postfix = ")\\b") { Regex.escape(it) },
            RegexOption.IGNORE_CASE,
        )
        val found = mutableListOf<String>()
        for (insider in listOf(false, true)) {
            val phone = housed(insider = insider)
            phone.houseSaid(RoundMessage.NoticeIssued(emptyList(), switchedOff = emptyList()))
            phone.houseSaid(RoundMessage.MessageDelivered("Do not run."))
            phone.houseSaid(RoundMessage.MeetingOpened(Seat(1), MeetingTrigger.MeetingCard, Haptic.Long))
            phone.houseSaid(RoundMessage.CheckInProgressed(present = 2, expected = 5))
            val inRound = phone.state
            val over = ended(housed(insider = insider), contained = 1).state
            for (screen in ScreenId.entries) {
                val state = if (screen == ScreenId.WinInsiders || screen == ScreenId.WinResidents) over else inRound
                for (text in drawn(phone, screen, state)) {
                    val hit = fixtures.find(text) ?: bungalow.find(text)?.takeIf { screen !in PanelVals.PRE }
                    hit?.let { found += "$screen (${if (insider) "Insider" else "Resident"}): '${it.value}' in \"$text\"" }
                }
            }
        }
        assertEquals(emptyList(), found, "fixtures on a housed phone:\n" + found.joinToString("\n"))
    }

    // ---- 7 · The clock before a house, FILES, the plan ------------------------------------------

    /**
     * **GUARD (D-255, H9): a real phone with no house yet draws its own clock**, on the join screen
     * a player sees on first launch and on every screen before a seat, never the design's `20:41`.
     * A real phone is one the app handed a clock; the harness is the one that was handed none.
     */
    @Test
    fun aPhoneWithNoHouseYetDrawsItsOwnClock() {
        for (screen in listOf(ScreenId.Join, ScreenId.Perms, ScreenId.Maps, ScreenId.Lobby, ScreenId.Secret)) {
            val phone = FlowModel(PanelState(screen = screen), wallClock = { now })
            assertEquals(false, phone.state.houseAttached)
            val vals = PanelVals(phone.state)
            assertEquals("23:46", vals.clock, "a phone with no house drew a drawn time on $screen")
            assertEquals(false, vals.renderHarness, "a phone with a clock was taken for the harness on $screen")
            val texts = drawn(phone, screen)
            assertTrue(texts.any { "23:46" in it }, "$screen did not draw the phone's own time: $texts")
            assertTrue(texts.none { "20:41" in it }, "$screen drew the design's 20:41 on a real phone: $texts")
        }
        // The lantern too, before and after a house.
        val unhoused = PanelVals(FlowModel(PanelState(screen = ScreenId.Lock), wallClock = { now }).state)
        assertEquals("23:46", unhoused.lockClock)
        assertEquals(now.date, unhoused.lockDate)
    }

    /** **GUARD (D-257): FILES holds this phone's carry, and nothing when it carries nothing.** */
    @Test
    fun filesFollowsTheCarry() {
        val empty = drawn(housed(), ScreenId.Files)
        assertTrue("0 ITEMS" in empty, "an empty FILES did not say so: $empty")
        assertTrue(empty.none { "DISK 01" in it || "RACK" in it || "1 ITEM" in it }, "an empty FILES drew the design's carry: $empty")

        val phone = housed()
        val carrying = phone.state.copy(carrying = Carried(item = "SPARE 03", to = "DISPOSAL"))
        val held = drawn(phone, ScreenId.Files, carrying)
        assertTrue("1 ITEM" in held, "a carrying FILES did not count its item: $held")
        assertTrue("SPARE 03" in held, "FILES did not draw what this phone carries: $held")
        assertTrue(held.any { "DESTINATION . DISPOSAL" in it }, "FILES did not draw where the carry goes: $held")
        assertTrue(held.none { "DISK 01" in it || "RACK" in it }, "a carrying FILES drew the design's carry: $held")
    }

    /**
     * **GUARD (D-257, unit 4d / H6): no housed Terminal, playback or couch draws the port's bungalow
     * or a count.** No phone is sent the home's plan (D-186) and no message carries occupancy, so
     * each draws the empty frame and a dash: no room name, no floor, and no bare number.
     */
    @Test
    fun noHousedPlanDrawsTheBungalow() {
        val bungalow = Plan.rooms.map { it.name } + listOf("GROUND", "UPPER")
        for (insider in listOf(false, true)) {
            val phone = housed(insider = insider)
            for (screen in listOf(ScreenId.TermLive, ScreenId.Timelapse, ScreenId.Ghost3)) {
                val texts = drawn(phone, screen)
                assertTrue(texts.none { it in bungalow }, "$screen drew the design's bungalow on a housed phone: $texts")
                assertTrue(texts.none { it.matches(Regex("\\d+")) }, "$screen drew a count nobody counted: $texts")
                assertTrue(PanelVals.NO_READING in texts, "$screen drew no empty plan: $texts")
            }
        }
    }

    // ---- 8 · The harness keeps its fixtures -----------------------------------------------------

    /** And a phone with no house still draws every one of the design's, so the harness compares. */
    @Test
    fun anUnhousedPhoneStillDrawsTheDesign() {
        val port = PanelVals(PanelState(role = PanelRole.Insider))
        assertTrue("Priya's spare key" in port.houseLine)
        assertEquals("6 RESIDENTS\nACCOUNTED FOR", port.accountedFor)
        assertEquals(PanelVals.ENDING_RESIDENTS, port.residentNames)
        assertEquals("2", port.egressesContained)
        assertEquals("ATTEMPT 3 . 8S", port.reconnectLine)
        assertEquals("PRIYA", port.callerOr("PRIYA"))
        assertTrue(port.inbox.any { it.from == "MUM" || it.from == "BANK" || it.from == "PRIYA" }, "the harness lost its backlog")
        assertEquals(MeetingCounts(), MeetingModel.sample().counts, "the harness lost its meeting counts")
        val calling = drawn(FlowModel(PanelState(screen = ScreenId.Calling)), ScreenId.Calling)
        assertTrue("4 OF 6 ANSWERED" in calling, "the harness lost the caller's count: $calling")
        // The harness is the phone with no house and no clock (D-255, D-257).
        val harness = PanelVals(PanelState(screen = ScreenId.Join))
        assertTrue(harness.renderHarness)
        assertEquals("20:41", harness.clock, "the harness lost its drawn time")
        assertEquals(PanelVals.FILED, harness.filed, "the harness lost its carry")
        assertEquals(Plan.trueCounts, harness.trueCounts, "the harness lost the couch's bungalow")
        assertEquals(Plan.terminalCounts, harness.terminalCounts, "the harness lost the Terminal's bungalow")
        val couch = drawn(FlowModel(PanelState(screen = ScreenId.Ghost3)), ScreenId.Ghost3)
        assertTrue("KITCHEN" in couch && "GARAGE" in couch, "the harness's couch lost its rooms: $couch")
    }
}
