package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The one gate, and the number beside it that never gates anything** (D-127).
 *
 * A home passes REVIEW with one terminal, one meeting card and at least eight ordinary markers.
 * Capacity is `markers − 3` and is shown to the host rather than enforced on them.
 */
class HomeReviewTest {

    private fun review(markers: Int, terminal: Boolean = true, meeting: Boolean = true) =
        HomeReview.of(markers, hasTerminal = terminal, hasMeeting = meeting)

    /** The floor is arithmetic, not taste: the smallest party plus the circuit's stations. */
    @Test
    fun `eight is five plus three and says so`() {
        assertEquals(8, HomeReview.MARKERS)
        assertEquals(HomeReview.MIN_PARTY + HomeReview.STATIONS, HomeReview.MARKERS)
        assertEquals(3, HomeReview.STATIONS)
    }

    @Test
    fun `a home with a terminal a meeting card and eight markers passes`() {
        val passing = review(8)
        assertTrue(passing.passes)
        assertEquals(emptyList(), passing.missing)
    }

    /**
     * **Each requirement named, not the first one that fails.**
     *
     * A host sent back for a terminal, then for a meeting card, then for three more markers walks
     * their own house three times over a fact the app knew on the first pass (D-126).
     */
    @Test
    fun `an empty home names all three at once`() {
        val nothing = review(0, terminal = false, meeting = false)
        assertFalse(nothing.passes)
        assertEquals(3, nothing.missing.size)
        assertEquals(HomeReview.Missing.Terminal, nothing.missing[0])
        assertEquals(HomeReview.Missing.MeetingCard, nothing.missing[1])
        assertIs<HomeReview.Missing.Markers>(nothing.missing[2])
    }

    @Test
    fun `a missing terminal is named and nothing else is`() {
        val short = review(8, terminal = false)
        assertEquals(listOf(HomeReview.Missing.Terminal), short.missing)
    }

    @Test
    fun `a missing meeting card is named and nothing else is`() {
        val short = review(8, meeting = false)
        assertEquals(listOf(HomeReview.Missing.MeetingCard), short.missing)
    }

    /** How many short, so the screen can ask for that many rather than restate the floor. */
    @Test
    fun `too few markers is named with what the home has and what it needs`() {
        val short = review(5)
        val markers = short.missing.single()
        assertIs<HomeReview.Missing.Markers>(markers)
        assertEquals(5, markers.have)
        assertEquals(8, markers.need)
        assertEquals(3, markers.short)
    }

    /** Eight passes and seven does not — the boundary, asserted rather than described. */
    @Test
    fun `the marker floor is a floor and not a ceiling`() {
        assertTrue(review(8).passes)
        assertFalse(review(7).passes)
        assertTrue(review(40).passes)
    }

    @Test
    fun `capacity is the markers minus the three stations`() {
        assertEquals(5, review(8).hosts)
        assertEquals(7, review(10).hosts)
        assertEquals(0, review(3).hosts)
    }

    /**
     * Never negative. A home with two markers hosts up to nobody; `-1` on a screen is arithmetic
     * leaking out of a model.
     */
    @Test
    fun `capacity never goes below nobody`() {
        assertEquals(0, review(0).hosts)
        assertEquals(0, review(2).hosts)
    }

    /**
     * **Capacity is not a gate, and the smallest passing home proves it.**
     *
     * Eight markers passes REVIEW and hosts up to five, which is the minimum party — so the gate
     * and the guidance agree at the boundary rather than contradicting each other on the one home
     * where a host would notice.
     */
    @Test
    fun `the smallest passing home hosts exactly the smallest party`() {
        val smallest = review(HomeReview.MARKERS)
        assertTrue(smallest.passes)
        assertEquals(HomeReview.MIN_PARTY, smallest.hosts)
    }
}
