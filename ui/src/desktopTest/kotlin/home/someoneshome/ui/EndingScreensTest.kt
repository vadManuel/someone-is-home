package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The two endings, read off the screens** (D-131, D-153, D-157, `gdd.md:1051`).
 *
 * The reveal is the one moment this app ever states an alignment, and it is therefore the one place
 * where every guard in the design is deliberately switched off at once. That makes the screens
 * worth reading rather than reasoning about: the effects can be correct and the panel can still
 * name somebody a page early, print a denominator, or hand a Resident the house's goodbye.
 *
 * Its companion is `MeterDisclosureTest`, which owns the percentage sweep, and `EndingTest` in
 * `core`, which owns what the house actually emits. This file owns the pixels.
 */
@OptIn(ExperimentalTestApi::class)
class EndingScreensTest {

    /**
     * A lobby that is attached but is **not** running the house.
     *
     * `LobbyModel.sample()` is hosting — the host's phone runs the house and joins it over
     * loopback like everybody else — so it is the wrong fixture for reading a control that is only
     * a control on one phone. This is the same lobby with the one difference, built the same way
     * the sample is so the two cannot drift into two different rooms.
     */
    private fun clientLobby(): LobbyModel {
        val model = LobbyModel(
            finder = MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
            link = MemoryLobbyLink(joined = 6, linesIn = 4),
            hosting = false,
        )
        model.look()
        model.nameResident("PRIYA")
        model.attachTo(model.nearby.first())
        return model
    }

    private fun texts(id: ScreenId, role: PanelRole, hosting: Boolean = false): List<String> {
        val found = mutableListOf<String>()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(screen = id, role = role),
                        lobby = if (hosting) LobbyModel.sample() else clientLobby(),
                    )
                }
            }
            onAllNodesWithText("", substring = true).fetchSemanticsNodes().forEach { node ->
                node.config.filter { it.key.name == "Text" }.forEach { entry ->
                    found += entry.value.toString()
                }
            }
        }
        return found
    }

    private fun showsOn(id: ScreenId, role: PanelRole, words: String, hosting: Boolean = false): Boolean =
        texts(id, role, hosting).any { it.contains(words) }

    /**
     * **The reveal reaches both endings and both roles, identically** (`gdd.md:1063`).
     *
     * *Everyone learns who and why.* One disclosure to one room, so the names and the lines are on
     * the winning side's screen exactly as they are on the losing side's — a reveal that a Resident
     * could not read would be the room being told to look at somebody else's phone.
     *
     * Both halves of each entry are asserted. The name alone was what the port drew (`DANI`, with
     * `BLACKMAILED` beside it), and the fact without the content is precisely what the reveal is
     * not: the line is what turns a result into a person.
     */
    @Test
    fun bothEndingsPublishBothNamesAndBothLines() {
        for (id in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            for (role in PanelRole.entries) {
                val drawn = texts(id, role)
                for (who in PanelVals.REVEALED) {
                    assertTrue(
                        drawn.any { it.contains(who.name) },
                        "$id/$role did not name ${who.name}",
                    )
                    assertTrue(
                        drawn.any { it.contains(who.line) },
                        "$id/$role named ${who.name} without publishing what was held over them",
                    )
                }
            }
        }
    }

    /**
     * **Nothing names an Insider before the ending.**
     *
     * The screen-level half of `EmitSchemaTest`'s completeness check. That one proves the house
     * cannot *send* an alignment early; this one proves no panel draws one from the fixture it
     * already holds — which is a different failure with the same consequence, and the more likely
     * one, because [PanelVals.REVEALED] is a constant any composable could reach.
     */
    @Test
    fun noScreenBeforeTheEndingNamesAnInsider() {
        val endings = setOf(ScreenId.WinInsiders, ScreenId.WinResidents)
        for (id in ScreenId.entries - endings) {
            for (role in PanelRole.entries) {
                val drawn = texts(id, role)
                for (who in PanelVals.REVEALED) {
                    assertFalse(
                        drawn.any { it.contains(who.line) },
                        "$id/$role published a one line before the round ended",
                    )
                }
            }
        }
    }

    /**
     * **The house speaks last, and only to the people it owned** (`gdd.md:1051`).
     *
     * *Thank you for your cooperation* on a Insider win; *Unfortunate* on a Resident win — both to
     * the Insiders and to no Resident. It is lawful only because of where it is drawn: the reveal
     * is on the same screen, so by the time this appears there is nothing left for it to disclose.
     *
     * A Resident's phone reading it would be a leak in the ordinary sense — it would tell them
     * which of the six phones in the room the house had been texting — and it is asserted here
     * rather than only in the allowlist because the panel branches on `insider` and a branch is a
     * thing that gets inverted.
     */
    @Test
    fun theHouseSignsOffToTheInsidersOnBothEndings() {
        assertTrue(showsOn(ScreenId.WinInsiders, PanelRole.Insider, "Thank you for your cooperation"))
        assertTrue(showsOn(ScreenId.WinResidents, PanelRole.Insider, "Unfortunate"))

        assertFalse(
            showsOn(ScreenId.WinInsiders, PanelRole.Resident, "Thank you for your cooperation"),
            "a Resident's phone read the message the house sent the Insiders",
        )
        assertFalse(
            showsOn(ScreenId.WinResidents, PanelRole.Resident, "Unfortunate"),
            "a Resident's phone read the message the house sent the Insiders",
        )
    }

    /**
     * **PERIMETER DISARMED, wired to the push** (`gdd.md:203`).
     *
     * The status row has read `ARMED` for twenty-five minutes and the Resident ending is what
     * changes it — a glyph, drained, in the place the lit one was, so the fact is not repeated as
     * text on thirty screens. Landing on `WinResidents` is the whole of the wiring, which is why
     * the two booleans are asserted against each other rather than separately: a build that lit
     * both would draw two irises, and one that lit neither would take the perimeter off the status
     * row entirely at the moment somebody most wants to read it.
     */
    @Test
    fun theResidentEndingDisarmsThePerimeterAndTheInsiderEndingDoesNot() {
        val residents = PanelVals(PanelState(screen = ScreenId.WinResidents))
        assertTrue(residents.disarmedGlyph, "the Residents won and the perimeter stayed armed")
        assertFalse(residents.armedGlyph, "both irises are lit at once")

        val insiders = PanelVals(PanelState(screen = ScreenId.WinInsiders))
        assertTrue(insiders.armedGlyph, "the Insiders won and the house disarmed itself")
        assertFalse(insiders.disarmedGlyph)

        // And in words, on the banner, for the eye looking at the middle of the screen.
        assertTrue(
            showsOn(ScreenId.WinResidents, PanelRole.Resident, "PERIMETER DISARMED"),
            "the Resident ending does not say the perimeter is down",
        )
    }

    /**
     * **NEW ROUND is on both endings, and it is the host's** (D-157).
     *
     * Present and inert on a phone that is not hosting, with `HOST ONLY` beside it, exactly as
     * `END SESSION` is on the settings screen. Absent, it would be a layout that changes between
     * phones; a live control on a client would be one phone starting a round for six.
     */
    @Test
    fun newRoundIsOnBothEndingsAndSaysWhoseItIs() {
        for (id in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            assertTrue(showsOn(id, PanelRole.Resident, "NEW ROUND"), "$id has no way to a second round")
            assertTrue(
                showsOn(id, PanelRole.Resident, "HOST ONLY"),
                "$id offers NEW ROUND to a phone that cannot start one, and does not say so",
            )
            assertTrue(
                showsOn(id, PanelRole.Resident, "EVERYONE RETURNS", hosting = true),
                "$id does not tell the host what NEW ROUND does to everybody else",
            )
        }
    }

    /**
     * The endings draw the meter and draw it as a percentage. `MeterDisclosureTest` owns the sweep
     * for absent denominators; this is the other direction — a screen that says nothing at all
     * about the meter also has no denominator on it, and would pass that sweep for the wrong
     * reason.
     */
    @Test
    fun bothEndingsStillReportTheMeter() {
        for (id in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            assertTrue(
                showsOn(id, PanelRole.Insider, "SYSTEM INTEGRITY"),
                "$id stopped reporting the meter instead of reporting it as a percentage",
            )
        }
        assertEquals("0%", PanelVals(PanelState(screen = ScreenId.WinResidents)).integrityPercent)
        assertEquals("43%", PanelVals(PanelState(screen = ScreenId.WinInsiders)).integrityPercent)
    }
}
