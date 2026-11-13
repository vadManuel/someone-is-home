package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **What the status strip says, value by value** (D-204, D-077, D-168).
 *
 * The strip is on screen all evening, so a value in it that differed by role would be read off
 * somebody else's phone for the whole round. And it carries two rulings of its own: the padlock
 * marks the two places nothing else opens, and a concealment screen's strip is no brighter than
 * the rest of it.
 */
class StatusStripTest {

    private val disk = Carried(item = "DISK 01", to = "RACK")

    private fun vals(screen: ScreenId, role: PanelRole = PanelRole.Resident, carrying: Carried? = null) =
        PanelVals(PanelState(screen = screen, role = role, carrying = carrying))

    @Test
    fun `no strip value depends on the role`() {
        for (screen in ScreenId.entries) {
            val r = vals(screen, PanelRole.Resident)
            val i = vals(screen, PanelRole.Insider)
            assertEquals(
                listOf(r.stripInk, r.stripDim, r.stripEdge, r.signalOn, r.signalOff, r.carrier,
                    r.armedGlyph, r.disarmedGlyph, r.lockChip, r.clock),
                listOf(i.stripInk, i.stripDim, i.stripEdge, i.signalOn, i.signalOff, i.carrier,
                    i.armedGlyph, i.disarmedGlyph, i.lockChip, i.clock),
                "$screen: the strip differs by role",
            )
        }
    }

    @Test
    fun `the padlock is on Files and while carrying and nowhere else`() {
        for (role in PanelRole.entries) {
            assertTrue(vals(ScreenId.Files, role).lockChip, "$role: Files has no padlock")
            assertTrue(vals(ScreenId.Home, role, carrying = disk).lockChip, "$role: carrying has no padlock")
            assertFalse(vals(ScreenId.Home, role).lockChip, "$role: the padlock is up with nothing carried")
        }
        val locked = ScreenId.entries.filter { vals(it).lockChip }
        assertEquals(listOf(ScreenId.Files), locked, "the padlock is on screens nothing locks")
    }

    @Test
    fun `a concealment screen strip is no brighter than C08A2E`() {
        for (screen in listOf(ScreenId.Revoked, ScreenId.Disconnect)) {
            assertEquals(Ramp.Dim, vals(screen).stripInk, "$screen's strip is lit above concealment")
        }
        assertEquals(Ramp.Ink, vals(ScreenId.Home).stripInk, "the springboard's strip was dimmed")
        assertEquals(Amber.BoneInk, vals(ScreenId.Boot).stripInk, "the bone strip is not bone ink")
    }
}
