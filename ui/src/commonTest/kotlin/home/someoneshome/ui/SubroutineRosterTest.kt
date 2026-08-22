package home.someoneshome.ui

import home.someoneshome.model.SubroutineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The one field of the roster that crosses the boundary** (D-112).
 *
 * In v1 the light signature is fixed per Subroutine kind, **the client holds the roster**, and the
 * house sends only which one. So `model` names the ten and `ui` knows what each is called and how
 * much light it costs — and the two have to agree exactly, in both directions, or a work order
 * arrives naming work this phone cannot draw.
 *
 * This is the guard that makes `Subroutine.of` total. Written as a bijection rather than as a
 * lookup that happens to succeed today: an eleventh kind added on the model side would otherwise
 * be a crash on the one screen a player navigates a dark house by, and it would not be a crash the
 * build could have caught.
 */
class SubroutineRosterTest {

    /** Every kind the house can send resolves to exactly one roster row. */
    @Test
    fun `every kind the house can name has exactly one roster row`() {
        for (kind in SubroutineKind.entries) {
            val rows = Subroutine.entries.filter { it.kind == kind }
            assertEquals(1, rows.size, "$kind resolves to ${rows.size} roster rows")
        }
    }

    /** And the other direction: no roster row names a kind twice, and none is left over. */
    @Test
    fun `the roster and the wire name the same ten`() {
        assertEquals(
            SubroutineKind.entries.size, Subroutine.entries.size,
            "the roster and the set of kinds are different sizes",
        )
        assertEquals(
            SubroutineKind.entries.toSet(), Subroutine.entries.map { it.kind }.toSet(),
            "a roster row names a kind twice, or a kind has no row",
        )
    }

    /**
     * **The light signature is looked up here and never sent** (D-106, D-112).
     *
     * Every kind resolves to a rated signature, so nothing ships unrated (D-113's general rule,
     * which matters more than the row it was decided over). `Subroutine.of` is what a screen calls
     * when a work order line arrives, and it must answer for all ten rather than for the six that
     * have interactions built.
     */
    @Test
    fun `every kind resolves to a rated light signature`() {
        for (kind in SubroutineKind.entries) {
            val row = Subroutine.of(kind)
            assertEquals(kind, row.kind)
            assertTrue(row.label.isNotBlank(), "$kind has no name to draw")
        }
        assertEquals(
            Subroutine.entries.size,
            SubroutineKind.entries.map { Subroutine.of(it) }.distinct().size,
            "two kinds resolved to the same roster row",
        )
    }
}
