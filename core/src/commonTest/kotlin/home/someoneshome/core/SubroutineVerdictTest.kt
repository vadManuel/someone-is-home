package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **D-109 and D-110 — the verdict spine, as things that can fail.**
 *
 * The house grades every entry for real, for both roles, in identical words; a handed-over entry
 * is spent and re-arms only on a fresh scan. Everything here is written as a *differential*
 * between two cases that must be indistinguishable, for the reason
 * [AbsentEffectIsTheLeakTest] is: an assertion that merely said *"a correct entry is accepted"*
 * would pass on every build that leaks.
 */
private val SEATS = (0 until 8).map { Seat(it) }

/** Seat 1 is the Insider throughout, so *the same seat index* is never the variable under test. */
private val INSIDERS = listOf(Seat(1))

private val CARD = MarkerId("m0")
private val OTHER_CARD = MarkerId("m3")

private fun armed(): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed = 20260821L, seats = SEATS, insiders = INSIDERS),
).state

private fun asked(state: GameState, seat: Seat): List<Int> =
    state.openSubroutineFor(seat)?.expected ?: error("seat ${seat.index} was assigned nothing")

/** Scan then return, the walk D-110 describes, collecting everything emitted along the way. */
private fun walk(
    from: GameState,
    seat: Seat,
    entered: List<Int>,
    scan: MarkerId? = CARD,
    at: MarkerId = CARD,
): Pair<GameState, List<Effect>> {
    val effects = mutableListOf<Effect>()
    var state = from
    if (scan != null) {
        val r = reduce(state, Event.MarkerScanned(Tick(1), seat, scan))
        state = r.state
        effects += r.effects
    }
    val r = reduce(state, Event.SubroutineReturned(Tick(2), seat, at, entered))
    return r.state to (effects + r.effects)
}

class VerdictIsNeverWithheldTest {

    /**
     * **The unit's whole point: an Insider's fake is graded and answered, never met with silence.**
     *
     * Injecting the bug means writing the tempting line — *if this seat is an Insider, say
     * nothing* — anywhere in `returned`. The effect streams then differ by exactly one line, and
     * that missing line is a role oracle after **one** Subroutine. Rule 1: the absent effect IS
     * the leak.
     */
    @Test
    fun `an Insider's correct entry is answered exactly as a Resident's is`() {
        val base = armed()
        val (_, resident) = walk(base, Seat(0), asked(base, Seat(0)))
        val (_, insider) = walk(base, Seat(1), asked(base, Seat(1)))

        assertEquals(Role.Resident, base.roleOf(Seat(0)))
        assertEquals(Role.Insider, base.roleOf(Seat(1)))
        assertEquals(
            listOf(Effect.SubroutineGraded(Seat(0), accepted = true)),
            resident.filterIsInstance<Effect.SubroutineGraded>(),
        )
        assertEquals(
            listOf(Effect.SubroutineGraded(Seat(1), accepted = true)),
            insider.filterIsInstance<Effect.SubroutineGraded>(),
            "the Insider's verdict was withheld, changed, or arrived a beat later — a fake that " +
                "is met with silence is a role oracle after one Subroutine",
        )
    }

    /** The same for a wrong entry: failure looks like failure for both roles. */
    @Test
    fun `an Insider's wrong entry is answered exactly as a Resident's is`() {
        val base = armed()
        val wrong = listOf(9, 9, 9)
        val (_, resident) = walk(base, Seat(0), wrong)
        val (_, insider) = walk(base, Seat(1), wrong)

        assertEquals(
            listOf(Effect.SubroutineGraded(Seat(0), accepted = false)),
            resident.filterIsInstance<Effect.SubroutineGraded>(),
        )
        assertEquals(
            listOf(Effect.SubroutineGraded(Seat(1), accepted = false)),
            insider.filterIsInstance<Effect.SubroutineGraded>(),
        )
    }

    /**
     * **The verdict is one effect per return, in every case there is** — including the ones a
     * defensive programmer would guard.
     *
     * An entry against a Subroutine nobody armed, at a card it was not armed at, from a seat the
     * house assigned nothing, or one already spent: every one of them is somewhere an
     * `if (…) return Reduction(state, emptyList())` looks like ordinary hygiene, and every one of
     * them would be a silence that says something.
     */
    @Test
    fun `every way an entry can be refused still produces exactly one verdict`() {
        val base = armed()
        val right = asked(base, Seat(0))
        val cases = listOf(
            "never scanned" to walk(base, Seat(0), right, scan = null),
            "scanned another card" to walk(base, Seat(0), right, scan = OTHER_CARD, at = CARD),
            "returned at another card" to walk(base, Seat(0), right, scan = CARD, at = OTHER_CARD),
            "assigned nothing" to walk(base, Seat(99), listOf(0, 0)),
            "empty entry" to walk(base, Seat(0), emptyList()),
        )
        for ((how, result) in cases) {
            val verdicts = result.second.filterIsInstance<Effect.SubroutineGraded>()
            assertEquals(1, verdicts.size, "$how produced ${verdicts.size} verdicts, not one")
            assertFalse(verdicts.single().accepted, "$how was graded correct")
        }
    }

    /**
     * **The grading is not inverted, and it is not stuck.**
     *
     * The complement of everything above. Every assertion in this file about *shape* would pass on
     * a build that graded every entry `true`, or every entry `false`, or the wrong ones right —
     * because all of those emit exactly one verdict of the right type per return. This is the one
     * test that reads the boolean, and it reads it both ways round.
     */
    @Test
    fun `the right entry is accepted and the wrong one is not`() {
        val base = armed()
        val right = asked(base, Seat(0))
        val wrong = right.reversed().map { it + 1 }
        assertNotEquals(right, wrong, "the fixture's wrong answer is the right one")

        assertTrue(
            walk(base, Seat(0), right).second
                .filterIsInstance<Effect.SubroutineGraded>().single().accepted,
            "the entry the house asked for was graded wrong",
        )
        assertFalse(
            walk(base, Seat(0), wrong).second
                .filterIsInstance<Effect.SubroutineGraded>().single().accepted,
            "an entry the house did not ask for was graded right",
        )
    }

    /** A scan on its own tells nobody anything, whether or not it found work. */
    @Test
    fun `a scan emits nothing to anybody`() {
        val base = armed()
        assertEquals(
            emptyList(),
            reduce(base, Event.MarkerScanned(Tick(1), Seat(0), CARD)).effects,
            "a scan that found work announced it",
        )
        assertEquals(
            emptyList(),
            reduce(base, Event.MarkerScanned(Tick(1), Seat(99), CARD)).effects,
            "a scan that found nothing announced that",
        )
    }
}

/** **D-110 — one attempt per scan, and the walk back is the cost.** */
class OneAttemptPerScanTest {

    /**
     * A second entry with no scan between grades false, whatever it says.
     *
     * The house never re-arms silently. If it did, the *first* wrong answer would cost nothing and
     * D-110's whole ruling — that the price of a mistake is standing at the marker again, in the
     * dark, where the house asked you to stand — would be a comment rather than a rule.
     */
    @Test
    fun `a second entry without a re-scan is spent`() {
        val base = armed()
        val right = asked(base, Seat(0))
        val (after, first) = walk(base, Seat(0), right)
        assertTrue(first.filterIsInstance<Effect.SubroutineGraded>().single().accepted)

        val again = reduce(after, Event.SubroutineReturned(Tick(3), Seat(0), CARD, right))
        assertEquals(
            listOf(Effect.SubroutineGraded(Seat(0), accepted = false)),
            again.effects.filterIsInstance<Effect.SubroutineGraded>(),
            "the entry re-armed itself, so an attempt costs nothing",
        )
        assertEquals(
            after.systemIntegrity, again.state.systemIntegrity,
            "a spent entry banked a second time — the meter is farmable without moving",
        )
    }

    /** And a re-scan is the way back, which is the other half of the same ruling. */
    @Test
    fun `a re-scan arms it again`() {
        val base = armed()
        val right = asked(base, Seat(0))
        val (afterWrong, _) = walk(base, Seat(0), listOf(9, 9))
        assertFalse(afterWrong.openSubroutineFor(Seat(0))!!.armed, "a returned entry is spent")

        val (afterRight, effects) = walk(afterWrong, Seat(0), right)
        assertTrue(
            effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted,
            "scanning the marker again did not re-arm the Subroutine",
        )
        assertEquals(
            base.systemIntegrity - 1, afterRight.systemIntegrity,
            "the second, correct attempt did not bank",
        )
    }

    /**
     * **The failure carries no state a later screen could read as a retry count.**
     *
     * Three wrong entries in a row leave the ledger in exactly the state one does. D-110 hands the
     * Insider a decision in public — pay for cover with a second walk, or do not — and that is
     * intended social-read material precisely because the house publishes no statistic about it.
     */
    @Test
    fun `attempts are not counted anywhere`() {
        val base = armed()
        var once = base
        var thrice = base
        repeat(1) { once = walk(once, Seat(0), listOf(9, 9)).first }
        repeat(3) { thrice = walk(thrice, Seat(0), listOf(9, 9)).first }
        assertEquals(
            once.openSubroutineFor(Seat(0))!!.armedAt,
            thrice.openSubroutineFor(Seat(0))!!.armedAt,
        )
        assertEquals(once.systemIntegrity, thrice.systemIntegrity)
    }
}
