package home.someoneshome.ui

import home.someoneshome.model.protocol.LobbyBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lobby beside the panel: what this phone knows, what it typed, and what it does with the
 * counts the house sends it.
 *
 * Everything asserted here is either input the player made or a number that arrived off the wire.
 * There is no third kind, and there must not be: a lobby that computed a game answer would be
 * `ui` answering a question the module boundary exists to stop it asking.
 */
class LobbyModelTest {

    private val secret = "i still have priya's spare key"

    private fun lobbyOf(
        joined: Int = 6,
        linesIn: Int = 0,
        hosting: Boolean = true,
        nearby: List<NearbyHome> = listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)),
    ): Pair<LobbyModel, MemoryLobbyLink> {
        val link = MemoryLobbyLink(joined = joined, linesIn = linesIn)
        val model = LobbyModel(MemoryHomeFinder(nearby), link, hosting = hosting)
        model.look()
        model.nearby.firstOrNull()?.let(model::attachTo)
        return model to link
    }

    // ---- What is nearby --------------------------------------------------------------------

    @Test
    fun `a phone that has attached to nothing stands at nothing`() {
        val model = LobbyModel()
        assertEquals(LobbyBody.Standing(joined = 0, linesIn = 0), model.standing)
        assertEquals(emptyList(), model.residents, "a lobby nobody is in listed somebody")
        assertNull(model.attached)
        assertFalse(model.everyLineIn, "an empty lobby offered LIGHTS OUT")
    }

    /**
     * **Two households may both have called their home THE BUNGALOW.** Dedupe by where it is, not
     * by what it is called, or the second one to answer disappears from the only list a player has.
     */
    @Test
    fun `homes are told apart by where they are and never by what they are called`() {
        val twins = listOf(
            NearbyHome("THE BUNGALOW", "192.168.1.24", 47747),
            NearbyHome("THE BUNGALOW", "192.168.1.31", 47747),
            NearbyHome("THE BUNGALOW", "192.168.1.24", 47747),
        )
        val model = LobbyModel(MemoryHomeFinder(twins))
        model.look()
        assertEquals(2, model.nearby.size, "the neighbours' home with the same name vanished")
        assertEquals(listOf("192.168.1.24", "192.168.1.31"), model.nearby.map { it.address })
    }

    @Test
    fun `a name is bounded by the same number the house bounds it by`() {
        val model = LobbyModel()
        model.nameResident("elliot")
        assertEquals("elliot", model.residentName)
        model.nameResident("a".repeat(200))
        assertEquals(LobbyModel.NAME_LIMIT, model.residentName.length, "the name field has no bottom")
        assertEquals(
            LobbyBody.Naming.LIMIT, LobbyModel.NAME_LIMIT,
            "the field and the wire hold two opinions about how long a name is",
        )
    }

    // ---- The name goes up, and comes back down as the lobby (D-115) --------------------------

    /**
     * **Attaching is what puts the name on the wire, and nothing else does.**
     *
     * The one authorised exit, and it happens once per attach — a name is not a thing that leaks
     * out over time, and a lobby that re-announced on every keystroke would be sending player-
     * identifying text on a timer.
     */
    @Test
    fun `the name leaves the phone at attach and only there`() {
        val link = MemoryLobbyLink(joined = 6, linesIn = 0)
        val model = LobbyModel(
            MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
            link,
        )
        model.look()
        model.nameResident("ELLIOT")
        assertEquals(emptyList(), link.named, "the name went up before anybody attached to anything")
        model.attachTo(model.nearby.first())
        assertEquals(listOf("ELLIOT"), link.named)
        model.typeLine(secret)
        model.handOverLine()
        assertEquals(listOf("ELLIOT"), link.named, "handing the line over sent the name again")
    }

    /** The house's list, drawn as the house sent it: one entry per seat, blanks and all. */
    @Test
    fun `the residents are the house's list and not this phone's arithmetic`() {
        val (model, _) = lobbyOf(joined = 6)
        assertEquals(6, model.residents.size, "the list is not as long as the count beside it")
        assertEquals(listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS"), model.residents.take(5))
        assertEquals("", model.residents.last(), "an unnamed seat was dropped or invented for")
    }

    // ---- The one line ----------------------------------------------------------------------

    @Test
    fun `a blank line is refused and nothing is handed over`() {
        val (model, link) = lobbyOf()
        assertFalse(model.handOverLine(), "an empty line was accepted")
        model.typeLine("   ")
        assertFalse(model.handOverLine(), "whitespace was accepted as a line")
        assertEquals(emptyList(), link.received, "something went to the house anyway")
        assertTrue(model.refusal != null, "it was refused and the player was told nothing")
        assertFalse(model.line.handedOver)
    }

    @Test
    fun `a real line goes to the house exactly once and the count comes back`() {
        val (model, link) = lobbyOf(joined = 6, linesIn = 3)
        model.typeLine(secret)
        assertTrue(model.handOverLine())
        assertEquals(listOf(secret), link.received)
        assertTrue(model.line.handedOver)
        assertNull(model.refusal)
        // The count is the house's answer, not this phone's arithmetic.
        assertEquals(4, model.standing.linesIn)
    }

    /**
     * **Deleted when the round ends** — the promise made on the screen where it was typed, kept on
     * this side as well as on the host's.
     */
    @Test
    fun `the round ending drops the line from this phone too`() {
        val (model, _) = lobbyOf()
        model.typeLine(secret)
        model.handOverLine()
        model.roundEnded()
        assertEquals("", model.line.text, "the line survived the round it was handed over for")
        assertFalse(model.line.handedOver)
    }

    /**
     * **[OneLine.toString] does not contain the line.**
     *
     * Everything that ever accidentally leaks a value leaks it through a `toString` — a log line,
     * a crash report, an enclosing `data class` printing its fields for free. There is no
     * legitimate caller that wants this text interpolated into a string.
     */
    @Test
    fun `the holder never prints what it holds`() {
        val line = OneLine()
        line.type(secret)
        val printed = "$line"
        assertFalse(secret in printed, "the one line printed itself: $printed")
        for (word in secret.split(" ").filter { it.length > 4 }) {
            assertFalse(word in printed, "'$word' printed itself: $printed")
        }
        assertTrue(secret.length.toString() in printed, "it prints nothing useful either")
    }

    /**
     * **Nothing the player typed reaches the only durable thing `ui` can touch.**
     *
     * `HomeStore` is this module's whole view of the phone's filesystem — `ui` cannot see
     * `platform`, so there is no second place for this test to look. The host's side of the same
     * promise is swept against the real Documents directory by
     * `platform`'s `OneLineNeverPersistedTest`.
     */
    @Test
    fun `nothing typed here is ever written to this phone`() {
        val store = RecordingHomeStore()
        val (lobby, _) = lobbyOf()
        val model = FlowModel(
            PanelState(screen = ScreenId.Secret),
            homes = SavedHomesModel(store),
            lobby = lobby,
        )
        model.lobby.nameResident("elliot")
        model.lobby.typeLine(secret)
        model.handOverLine()

        // Every write path this module has, after the line exists.
        model.editor.nameHome("somewhere new")
        model.saveHome()
        model.deleteHome()

        assertTrue(store.writes.isNotEmpty(), "nothing was written at all — this proves nothing")
        for (written in store.writes) {
            assertFalse(secret in written, "the one line was written to this phone: $written")
            for (word in secret.split(" ").filter { it.length > 4 }) {
                assertFalse(word in written, "'$word' from the one line was written: $written")
            }
            // The name is allowed to leave the phone now (D-115) and is still not allowed to stay
            // on it. Round-scoped means round-scoped on both sides of the wire.
            assertFalse("elliot" in written, "the resident name was written to this phone: $written")
        }
        assertFalse(secret in "${model.state}", "the line reached the panel a recording captures")
    }

    private class RecordingHomeStore : HomeStore {
        val writes = mutableListOf<String>()
        private var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) {
            writes += text
            this.text = text
        }
    }

    // ---- D-103, at the control ---------------------------------------------------------------

    @Test
    fun `the Insider setting starts UNKNOWN and says so`() {
        val (model, _) = lobbyOf()
        assertNull(model.standing.insiders)
        assertEquals("UNKNOWN", model.insidersLabel)
    }

    /**
     * **UNKNOWN, then every count in the band, then UNKNOWN again.**
     *
     * A cycle rather than a stepper: UNKNOWN is not below one or above the maximum, it is a
     * different kind of answer, and a control the host had to step *through* to get from two back
     * to one would be a control that keeps setting it by accident.
     */
    @Test
    fun `the control walks the band and comes back to UNKNOWN`() {
        val (model, _) = lobbyOf(joined = 6)
        assertEquals(1..2, model.band)
        val seen = mutableListOf(model.insidersLabel)
        repeat(3) { model.cycleInsiders(); seen += model.insidersLabel }
        assertEquals(listOf("UNKNOWN", "1", "2", "UNKNOWN"), seen)
    }

    /** Twelve seats start at two, because the minimum edge protects the Insider side. */
    @Test
    fun `a bigger home starts the band higher`() {
        val (model, _) = lobbyOf(joined = 12)
        assertEquals(2..3, model.band)
        model.cycleInsiders()
        assertEquals("2", model.insidersLabel)
        model.cycleInsiders()
        assertEquals("3", model.insidersLabel)
        model.cycleInsiders()
        assertEquals("UNKNOWN", model.insidersLabel)
    }

    /**
     * **A phone that is not hosting cannot move the host's setting.** It is not that the control
     * is hidden and the call still works — the call does nothing, so a control that reappeared
     * would still change nothing on anybody's phone.
     */
    @Test
    fun `a client cannot set the Insider count`() {
        val (model, _) = lobbyOf(joined = 6, hosting = false)
        repeat(5) { model.cycleInsiders() }
        assertEquals("UNKNOWN", model.insidersLabel, "a client moved the host's setting")
    }

    // ---- The vote window ----------------------------------------------------------------------

    /**
     * **It opens on the design's 45 seconds** (`gdd.md:412`), which is the assertion that matters:
     * the row read 60S and the flow table said 60 000, and each was a plausible number backed by
     * the other rather than by the design.
     */
    @Test
    fun `the vote window opens on the design's forty-five seconds`() {
        val (model, _) = lobbyOf(joined = 6)
        assertEquals(45, model.voteWindowSeconds)
        assertEquals("45S", model.voteWindowLabel)
        assertEquals(45, LobbyModel.VOTE_WINDOWS.first(), "the default stopped being first in the cycle")
    }

    /** Every window in the list, in order, and then round to the start. */
    @Test
    fun `the vote window control cycles and returns to the default`() {
        val (model, _) = lobbyOf(joined = 6)
        val seen = mutableListOf(model.voteWindowSeconds)
        repeat(LobbyModel.VOTE_WINDOWS.size) { model.cycleVoteWindow(); seen += model.voteWindowSeconds }
        assertEquals(LobbyModel.VOTE_WINDOWS + 45, seen)
    }

    /** The same rule the Insider count has: a client's tap moves nothing, control or no control. */
    @Test
    fun `a client cannot set the vote window`() {
        val (model, _) = lobbyOf(joined = 6, hosting = false)
        repeat(5) { model.cycleVoteWindow() }
        assertEquals(45, model.voteWindowSeconds, "a client moved the host's setting")
    }

    /**
     * **The setting does not reach the wire, and that is the design of it rather than an omission.**
     *
     * [LobbyLink] carries the Insider count because the house clamps it. Widening what a client
     * sends is a protocol decision, not a settings row's, so this proves the row stayed on this
     * phone — if the vote window is ever really enforced it should arrive the way `insiders` does,
     * and this test should be the thing that fails.
     */
    @Test
    fun `moving the vote window changes nothing the house published`() {
        val (model, _) = lobbyOf(joined = 6, linesIn = 4)
        val before = model.standing
        repeat(3) { model.cycleVoteWindow() }
        assertEquals(30, model.voteWindowSeconds, "the control did not move at all")
        assertEquals(before, model.standing, "the vote window reached the house's standing")
    }

    // ---- The gate ---------------------------------------------------------------------------

    @Test
    fun `the gate closes only when every line is in`() {
        val (four, _) = lobbyOf(joined = 6, linesIn = 4)
        assertFalse(four.everyLineIn, "four of six was enough")
        val (six, _) = lobbyOf(joined = 6, linesIn = 6)
        assertTrue(six.everyLineIn)
        val (nobody, _) = lobbyOf(joined = 0, linesIn = 0)
        assertFalse(nobody.everyLineIn, "an empty lobby counted as ready")
    }
}
