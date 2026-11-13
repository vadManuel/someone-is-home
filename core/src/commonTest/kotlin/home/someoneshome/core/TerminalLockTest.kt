package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The Terminal is a station, and one person reads it at a time** (D-199, D-203, `gdd.md:472`).
 *
 * Written as differentials where the two cases must look alike -- open and held, Insider and
 * Resident, the reader leaving and somebody else saying they left -- and as properties a build
 * whose lock never engaged, or never came free, would fail.
 */
class TerminalLockTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insider = Seat(1)
    private val station = MarkerId("TTTTTTT")
    private val markers = (0 until 12).map { MarkerId("m$it") }

    private fun armed(terminal: MarkerId? = station): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 20261112L, seats = seats, insiders = listOf(insider),
            markers = placed(markers), terminal = terminal,
        ),
    ).state

    /** Through the gate, as every real request arrives. */
    private fun put(state: GameState, event: Event): Pair<GameState, List<Effect>> =
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> admission.reduction.state to admission.reduction.effects
            is Admission.Refused -> error("refused: ${admission.reason}")
        }

    private fun scanT(state: GameState, seat: Seat, at: Long = 1L) =
        put(state, Event.MarkerScanned(Tick(at), seat, station))

    private fun answer(effects: List<Effect>): Effect.TerminalAnswered =
        effects.filterIsInstance<Effect.TerminalAnswered>().single()

    /** The kinds of effect, in order, with the seat and the answer blanked: the message's shape. */
    private fun shapeOf(effects: List<Effect>): List<String> = effects.map { it::class.simpleName!! }

    @Test
    fun `a scan of the T card at a free station makes the scanner the reader`() {
        val (after, effects) = scanT(armed(), Seat(0))
        assertEquals(true, answer(effects).reading)
        assertEquals(Seat(0), after.readerHolding())
        assertTrue(
            effects.none { it is Effect.ScanAnswered },
            "the T card was also answered as a marker: ${shapeOf(effects)}",
        )
    }

    /**
     * **ONE READER AT A TIME, and it is the same message as the open station** (rule 1). A held
     * station that answered with silence, or with a different kind, would be the station telling
     * the scanner something by what did not arrive.
     */
    @Test
    fun `a second scanner is refused on the same shape and the reader keeps the station`() {
        val (held, opened) = scanT(armed(), Seat(0))
        val (after, refused) = scanT(held, Seat(2), at = 2L)
        assertEquals(false, answer(refused).reading)
        assertEquals(Seat(0), after.readerHolding(), "a second scanner took the station")
        assertEquals(shapeOf(opened), shapeOf(refused), "open and held are different shapes")
    }

    /** **Either role may hold the station, on the same terms** (`gdd.md:526`). */
    @Test
    fun `an Insider and a Resident are answered identically`() {
        val (_, byResident) = scanT(armed(), Seat(0))
        val (_, byInsider) = scanT(armed(), insider)
        assertEquals(shapeOf(byResident), shapeOf(byInsider))
        assertEquals(answer(byResident).reading, answer(byInsider).reading)
    }

    @Test
    fun `the reader scanning again is still the reader`() {
        val (held, _) = scanT(armed(), Seat(0))
        val (after, again) = scanT(held, Seat(0), at = 2L)
        assertEquals(true, answer(again).reading)
        assertEquals(Seat(0), after.readerHolding())
    }

    /**
     * **Leaving frees the station, and says nothing either way.** The reader leaving and somebody
     * else claiming to have left both emit nothing, so neither can be told from the other by what
     * comes back; only the reader's frees anything.
     */
    @Test
    fun `the reader leaving frees the station and a stranger leaving does not`() {
        val (held, _) = scanT(armed(), Seat(0))

        val (stranger, strangerSaid) = put(held, Event.TerminalLeft(Tick(2), Seat(3)))
        assertEquals(Seat(0), stranger.readerHolding(), "somebody else's leave freed the station")

        val (freed, readerSaid) = put(held, Event.TerminalLeft(Tick(2), Seat(0)))
        assertNull(freed.readerHolding())
        assertEquals(emptyList(), strangerSaid)
        assertEquals(emptyList(), readerSaid)

        val (_, next) = scanT(freed, Seat(3), at = 3L)
        assertEquals(true, answer(next).reading, "the station stayed held after its reader left")
    }

    /** The phone's request becomes the same fact the host composes for a dropped connection. */
    @Test
    fun `leaving is a request that becomes the leave event`() {
        assertEquals(
            Event.TerminalLeft(Tick(4), Seat(0)),
            factOf(Intent.LeaveTerminal(Seat(0)), Tick(4)),
        )
    }

    @Test
    fun `a meeting call frees the station`() {
        val (held, _) = scanT(armed(), Seat(0))
        val (called, _) = put(held, Event.MeetingCalled(Tick(2), Seat(4), MeetingTrigger.MeetingCard))
        assertNull(called.readerHolding(), "the reader kept the station through the meeting call")
    }

    /** No honest phone scans during a meeting; a late or forged frame must not claim the lock. */
    @Test
    fun `nobody becomes the reader during a meeting`() {
        val (called, _) = put(armed(), Event.MeetingCalled(Tick(1), Seat(4), MeetingTrigger.MeetingCard))
        val (after, effects) = scanT(called, Seat(0), at = 2L)
        assertEquals(false, answer(effects).reading)
        assertNull(after.readerHolding())
    }

    /** **A reader who is put out holds nothing**, whichever list puts them there (D-203). */
    @Test
    fun `a reader who is revoked or restrained holds nothing`() {
        val (held, _) = scanT(armed(), Seat(0))
        for (out in listOf(held.copy(revoked = listOf(Seat(0))), held.copy(restrained = listOf(Seat(0))))) {
            assertNull(out.readerHolding())
            val (_, next) = scanT(out, Seat(3), at = 2L)
            assertEquals(true, answer(next).reading, "an out reader still held the station")
        }
    }

    @Test
    fun `a seat that is out never becomes the reader`() {
        val out = armed().copy(revoked = listOf(Seat(0)))
        val (after, effects) = scanT(out, Seat(0))
        assertEquals(false, answer(effects).reading)
        assertNull(after.reader)
    }

    /** The second denial: the allowlist declines the station's answer to an out class anyway. */
    @Test
    fun `the answer reaches the scanner alone and never an out seat`() {
        val living = armed()
        assertEquals(
            listOf(Seat(0)),
            EmitSchema.deliveries(Effect.TerminalAnswered(Seat(0), reading = true), living).map { it.seat },
        )
        val out = living.copy(revoked = listOf(Seat(0)))
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.TerminalAnswered(Seat(0), reading = false), out),
        )
    }

    /** In a round armed without a station, the T card is any registered card: no lock, no answer. */
    @Test
    fun `a round armed without a station answers the T card as a marker`() {
        val (after, effects) = scanT(armed(terminal = null), Seat(0))
        assertTrue(effects.none { it is Effect.TerminalAnswered })
        assertNull(effects.filterIsInstance<Effect.ScanAnswered>().single().opened)
        assertNull(after.reader)
    }
}
