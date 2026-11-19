package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.HandshakeTaps
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * **D-296: Handshake grades each gap between taps to within ±40 % of the dealt gap** (the owner's
 * Q7 ruling, option C, 2026-11-18).
 *
 * Until D-296 the entry was the ordinal of each tap and every completed Handshake passed. These
 * read the grading at its edges, both sides of each: 39 % off passes and 41 % fails, early and late,
 * at every gap of every rhythm the draw deals. A missing tap and an extra tap fail. And the
 * Insider's fake goes through the same function with the same tolerance, read off the rules' own
 * verdicts rather than off a claim about them.
 */

private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1))
private val MARKERS = (0 until 8).map { MarkerId("m$it") }

private fun armed(seed: Long): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed = seed, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS)),
).state

/** Handshake rhythms as the draw deals them, [many] times over. */
private fun rhythms(many: Int = 200): List<List<Int>> =
    (0 until many).map {
        drawParameters(SubroutineKind.Handshake, Draw(it.toLong(), SeedPurpose.Instance, 99L))
    }

private fun open(parameters: List<Int>): OpenSubroutine = OpenSubroutine(
    seat = Seat(0),
    entry = 0,
    subroutine = SubroutineKind.Handshake,
    parameters = parameters,
    expected = answerTo(SubroutineKind.Handshake, parameters),
    armedAt = MARKERS.first(),
)

/** The dealt gaps with the one at [at] moved by [percent] of itself, early or late. */
private fun nudged(gaps: List<Int>, at: Int, percent: Int): List<Int> =
    gaps.mapIndexed { i, g -> if (i == at) g + g * percent / 100 else g }

/** Taps stamped so that their gaps are [gaps], from an arbitrary monotonic reading. */
private fun stamped(gaps: List<Int>, from: Long = 7_340_221L): List<Long> =
    gaps.runningFold(from) { at, gap -> at + gap }

class HandshakeGradingTest {

    private val card = MARKERS.first()

    /**
     * **The edge, both sides, at every gap: 39 % passes and 41 % fails, early and late.**
     *
     * Every dealt gap is a whole tenth of a second, so 39 % and 41 % of it are whole milliseconds and
     * the walk is exact. The other gaps are returned perfectly, so each verdict is about one gap.
     */
    @Test
    fun `each gap passes at 39 percent off and fails at 41 early and late`() {
        for (parameters in rhythms()) {
            val gaps = HandshakeRhythm.of(parameters).gapsMillis
            val open = open(parameters)
            for (at in gaps.indices) {
                for (sign in listOf(1, -1)) {
                    val inside = HandshakeTaps.of(stamped(nudged(gaps, at, sign * 39)))
                    val outside = HandshakeTaps.of(stamped(nudged(gaps, at, sign * 41)))
                    assertTrue(
                        grades(open, card, inside),
                        "gap $at of $gaps returned ${sign * 39} % off was refused: " +
                            "${HandshakeTaps.gapsIn(inside)}",
                    )
                    assertFalse(
                        grades(open, card, outside),
                        "gap $at of $gaps returned ${sign * 41} % off was accepted: " +
                            "${HandshakeTaps.gapsIn(outside)}",
                    )
                }
            }
        }
    }

    /** **Within is inclusive**: exactly 40 % off is within ±40 %, on every gap at once. */
    @Test
    fun `exactly 40 percent off is within on every gap at once`() {
        for (parameters in rhythms(many = 40)) {
            val gaps = HandshakeRhythm.of(parameters).gapsMillis
            val open = open(parameters)
            for (sign in listOf(1, -1)) {
                val all = gaps.map { it + sign * it * HandshakeRhythm.TOLERANCE_PERCENT / 100 }
                assertTrue(grades(open, card, HandshakeTaps.of(stamped(all))), "$all against $gaps")
            }
        }
    }

    /**
     * **A rhythm at any tempo no longer passes**: the count-only grading D-238 named and Q7 ended.
     * Every tap at once, and every tap two seconds apart: the right number of taps, and wrong.
     */
    @Test
    fun `the right number of taps at the wrong tempo is refused`() {
        for (parameters in rhythms(many = 40)) {
            val beats = HandshakeRhythm.of(parameters).beats
            val open = open(parameters)
            assertFalse(grades(open, card, HandshakeTaps.of(List(beats) { 5_000L })), "all at once passed")
            assertFalse(
                grades(open, card, HandshakeTaps.of(List(beats) { 5_000L + it * 2_000L })),
                "two seconds apart passed",
            )
        }
    }

    /**
     * **A missing tap fails and an extra tap fails** (D-296). A missing tap leaves a dealt gap with
     * nothing to grade; an extra one puts a gap where the rhythm dealt none. Both are timed
     * perfectly otherwise, so the count is the only thing wrong with them.
     */
    @Test
    fun `a missing tap and an extra tap are both refused`() {
        for (parameters in rhythms()) {
            val gaps = HandshakeRhythm.of(parameters).gapsMillis
            val open = open(parameters)
            assertTrue(grades(open, card, HandshakeTaps.of(stamped(gaps))), "the perfect return was refused: $gaps")
            assertFalse(
                grades(open, card, HandshakeTaps.of(stamped(gaps.dropLast(1)))),
                "a return one tap short was accepted: $gaps",
            )
            assertFalse(
                grades(open, card, HandshakeTaps.of(stamped(gaps + gaps.last()))),
                "a return one tap over was accepted: $gaps",
            )
        }
    }

    /**
     * **What is not an entry is a miss, never an exception**: the old ordinals, a short list, a
     * number that is neither a gap nor the padding, seven taps. A malformed client can neither crash
     * the grading nor slip past it.
     */
    @Test
    fun `what is not an entry is refused`() {
        for (parameters in rhythms(many = 40)) {
            val beats = HandshakeRhythm.of(parameters).beats
            val open = open(parameters)
            val right = open.expected
            assertFalse(grades(open, card, List(beats) { it }), "the pre-D-296 ordinals passed")
            assertFalse(grades(open, card, right.dropLast(1)), "a short entry passed")
            assertFalse(grades(open, card, right + HandshakeTaps.NONE), "a long entry passed")
            assertFalse(
                grades(open, card, listOf(HandshakeTaps.NONE + 1) + right.drop(1)),
                "a stray number passed",
            )
            assertFalse(
                grades(open, card, HandshakeTaps.of(List(HandshakeRhythm.MOST_BEATS + 1) { 1_000L * it })),
                "seven taps passed",
            )
        }
    }

    /** The answer key is an entry like any other: the dealt gaps exactly, padded. */
    @Test
    fun `the house's own answer passes and is what a perfect return sends`() {
        for (parameters in rhythms(many = 40)) {
            val gaps = HandshakeRhythm.of(parameters).gapsMillis
            val open = open(parameters)
            assertEquals(HandshakeTaps.of(stamped(gaps)), open.expected)
            assertTrue(grades(open, card, open.expected))
        }
    }

    /**
     * **The Insider's fake is graded by the same function, with the same tolerance**, read off the
     * rules' own verdict on a Handshake really opened by a scan, for a Resident and for an Insider.
     *
     * The same seven returns, each built from that seat's own dealt gaps, go to both: perfect, 39 %
     * off both ways, 41 % off both ways, a tap short and a tap over. The verdicts must be the same
     * list for the two roles, and the one `grades` gives. What differs is only what the rules always
     * did with an accepted fake: it banks nothing.
     */
    @Test
    fun `the Insider's fake is graded exactly as a Resident's Handshake is`() {
        val verdicts = Role.entries.associateWith { role ->
            val (scanned, seat, at) = handshakeOpenedFor(role)
            val open = scanned.openSubroutineFor(seat)!!
            val gaps = HandshakeRhythm.of(open.parameters).gapsMillis
            returns(gaps).map { taps ->
                val entry = HandshakeTaps.of(stamped(taps))
                val reduced = reduce(scanned, Event.SubroutineReturned(Tick(2), seat, at, entry))
                val verdict = reduced.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted
                assertEquals(grades(open, at, entry), verdict, "the rules and grades disagree for a $role")
                if (role == Role.Insider) {
                    assertEquals(scanned.systemIntegrity, reduced.state.systemIntegrity, "the fake banked")
                }
                verdict
            }
        }
        assertEquals(listOf(true, true, true, false, false, false, false), verdicts[Role.Resident])
        assertEquals(verdicts[Role.Resident], verdicts[Role.Insider], "the Insider's fake was graded differently")
    }

    private fun returns(gaps: List<Int>): List<List<Int>> = listOf(
        gaps,
        gaps.map { it + it * 39 / 100 },
        gaps.map { it - it * 39 / 100 },
        nudged(gaps, 0, 41),
        nudged(gaps, gaps.lastIndex, -41),
        gaps.dropLast(1),
        gaps + gaps.first(),
    )

    /**
     * A round in which a seat of [role] has scanned a card and opened a Handshake. The draw decides
     * what each card opens, so this looks through seeds for one; it is deterministic in the seed.
     */
    private fun handshakeOpenedFor(role: Role): Triple<GameState, Seat, MarkerId> {
        for (seed in 0L until 400L) {
            val base = armed(seed)
            for (seat in SEATS) {
                if (base.roleOf(seat) != role) continue
                val order = base.workOrderFor(seat) ?: continue
                for (line in order.entries) {
                    if (!order.isActionable(line)) continue
                    val at = line.anchor ?: continue
                    val scanned = reduce(base, Event.MarkerScanned(Tick(1), seat, at)).state
                    if (scanned.openSubroutineFor(seat)?.subroutine == SubroutineKind.Handshake) {
                        return Triple(scanned, seat, at)
                    }
                }
            }
        }
        throw AssertionError("no seed in 400 opened a Handshake for a $role; this test proves nothing")
    }
}
