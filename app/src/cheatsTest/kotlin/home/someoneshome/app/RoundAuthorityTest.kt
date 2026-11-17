package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The join, checked without a radio under it.**
 *
 * [RoundAuthority] takes its two seams as functions rather than taking a [LobbyHouse], and this
 * file is why: `LobbyHouse.start` waits on an advertisement no test has a radio for (E-P2-2), so
 * a class that held one could only ever be checked by playing an evening on two phones. What is
 * under test here is the thing that unit could not reach — *does arming read the lobby, does it
 * refuse a room that is short, does every seat get told, and does a line ever come back out.*
 */
class RoundAuthorityTest {

    // ---- The fixture ---------------------------------------------------------------------------

    private val seats = List(5) { Seat(it) }

    private val names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") }

    /** Deliberately distinctive, so a sweep for them cannot pass by matching something ordinary. */
    private val lines = seats.mapIndexed { i, s ->
        OneLineHeld(s, "i borrowed the ladder and never gave it back $i")
    }

    private fun inputs(
        seats: List<Seat> = this.seats,
        ready: Boolean = true,
    ): ArmingInputs = ArmingInputs(
        seats = seats,
        names = names.filter { it.seat in seats },
        oneLines = lines.filter { it.seat in seats },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = ready,
        // The bench's fixture, hosted as a home would be — see BenchHome.
        home = BenchHome.home,
    )

    /** The authority, with the two seams as recorders. */
    private class Bench(inputs: ArmingInputs) {
        val pushed = mutableListOf<Pair<Effect, GameState>>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> pushed += effect to state },
            note = { said += it },
        )
    }

    // ---- 1 · Arming reads the lobby, and every seat is told ------------------------------------

    /**
     * **The opening message lands on every seat — E-D3-3's closing proof, at the authority end.**
     *
     * `OpeningMessage` is emitted one per seat on arming (L3), and it is the one push in this game
     * that every phone gets for certain. Asserted as *the set of seats*, not as a count, because a
     * count of five with one seat told twice is the bug this is here to catch.
     */
    @Test
    fun armingTellsEverySeatAndTellsEachOfThemOnce() = runBlocking<Unit> {
        val bench = Bench(inputs())
        assertTrue(bench.house.arm(SEED), "the lobby was ready and the house armed nothing")

        val told = bench.pushed.map { it.first }.filterIsInstance<Effect.OpeningMessage>()
        assertEquals(
            seats, told.map { it.seat },
            "the opening message did not reach every seat exactly once",
        )
        assertEquals(
            seats.size,
            bench.pushed.map { it.first }.filterIsInstance<Effect.WorkOrderIssued>().size,
            "a seat was armed into a round with no work in it",
        )
    }

    /**
     * **A room one short of the floor arms nothing, and says so.**
     *
     * The gate is the lobby's own `readyToArm`, and this is pre-arm on the host's phone in a lit
     * room — the one place in this game a refusal is allowed to be loud (`armingFor`'s own KDoc).
     */
    @Test
    fun aLobbyThatIsNotReadyArmsNothingAndSaysWhy() = runBlocking<Unit> {
        val bench = Bench(inputs(seats = seats.dropLast(1), ready = false))
        assertFalse(bench.house.arm(SEED), "a lobby that was not ready armed a round")
        assertFalse(bench.house.armed, "the state was armed by a refusal")
        assertTrue(bench.pushed.isEmpty(), "a refused arming still pushed something to a phone")
        assertTrue(
            bench.said.any { it.contains("WILL NOT ARM") },
            "the house refused to arm and recorded nothing: ${bench.said}",
        )
    }

    // ---- 2 · The one line rides the arming and comes back out of nothing -----------------------

    /**
     * **The lines reach `GameState` and never reach anything that can be read.**
     *
     * They have to be on the arming event — the reveal publishes them at the end of the round
     * (D-116) and `endRound` is what drops them. What must never happen is that the *path* they
     * take to get there keeps a copy: this sweeps everything the authority said out loud, and
     * every effect it pushed, for the text five people typed.
     *
     * **One effect carries a line in the round, by design, and prints none** (D-247, D-101):
     * `RoleRevealed` quotes an Insider's own line back to that Insider's phone. So the sweep reads
     * every pushed effect as it would be printed, and a second check holds the one carrier to its
     * rule — the line is on the Insider's own reveal, on no Resident's, and on nothing else.
     *
     * **It is self-verifying.** A sweep that reads nothing passes for the same reason a sweep that
     * reads everything does, so the first assertion is that the lines really did arrive.
     */
    @Test
    fun theLinesRideTheArmingAndNothingTheHouseSaysCarriesOne() = runBlocking<Unit> {
        val bench = Bench(inputs())
        bench.house.arm(SEED)

        // The positive control: they are really on the state, so the sweep below is looking at a
        // round that has something to leak.
        assertEquals(
            lines.map { it.text }.sorted(), bench.house.state.oneLines.map { it.text }.sorted(),
            "the lines never reached the round, so the sweep found nothing because there was nothing",
        )

        val everythingSaid = bench.said.joinToString("\n")
        val everythingPushed = bench.pushed.joinToString("\n") { it.first.toString() }
        for (line in lines.map { it.text }) {
            assertFalse(
                everythingSaid.contains(line),
                "the house printed a one line: it is in the log this bench writes to a console",
            )
            assertFalse(
                everythingPushed.contains(line),
                "a one line was printed off an effect while the round was still running",
            )
        }
        val carried = bench.pushed.map { it.first }.filter { effect ->
            lines.any { it.text in (effect as? Effect.RoleRevealed)?.line.orEmpty() }
        }
        assertEquals(
            bench.house.state.insiderSeats.map { seat -> lines.single { it.seat.index == seat.index }.text }.sorted(),
            carried.map { (it as Effect.RoleRevealed).line }.sorted(),
            "the Insiders' own reveals are not the only carriers of a line",
        )
        for (reveal in bench.pushed.map { it.first }.filterIsInstance<Effect.RoleRevealed>()) {
            val own = lines.single { it.seat.index == reveal.seat.index }.text
            assertEquals(
                if (bench.house.state.roleOf(reveal.seat) == home.someoneshome.model.Role.Insider) own else "",
                reveal.line, "seat ${reveal.seat.index}'s reveal carried somebody else's line, or a Resident's",
            )
        }
        // And the count is reported, because *nothing was typed* and *something was typed and did
        // not arrive* are two different faults and a log has to separate them.
        assertTrue(
            bench.said.any { it.contains("${lines.size} LINE(S)") },
            "the arming log does not say how many lines it armed with: ${bench.said}",
        )
    }

    /**
     * **Nor does it name who was drawn.**
     *
     * The arming log prints the Insider *count*, which is public — it is the host's own lobby row.
     * Which seats hold it is the single most consequential fact in the game, and a device console
     * is a file on a Mac that outlives the evening.
     */
    @Test
    fun theArmingLogGivesTheCountAndNeverTheDraw() = runBlocking<Unit> {
        val bench = Bench(inputs())
        bench.house.arm(SEED)
        val drawn = bench.house.state.insiderSeats
        assertTrue(drawn.isNotEmpty(), "this round drew no Insiders, so nothing could have leaked")

        val log = bench.said.joinToString("\n")
        assertTrue(log.contains("INSIDERS ${drawn.size}"), "the count was not reported: $log")
        for (seat in drawn) {
            assertFalse(
                log.contains("SEAT ${seat.index}"),
                "the arming log named a seat that was drawn as an Insider",
            )
        }
    }

    // ---- 3 · One gate, two doors ----------------------------------------------------------------

    /**
     * **A request from a phone and an event the house minted reach the same rules.**
     *
     * `admit(state, intent, at)` is `admit(state, factOf(intent, at))` and nothing else (P2), and
     * this is that identity asserted through the object that actually calls both: the same
     * check-in, once as an attributed [Intent] and once as the [Event] it becomes, has to leave
     * the same round behind and push the same things.
     */
    @Test
    fun aRequestAndAMintedEventLeaveTheSameRound() = runBlocking<Unit> {
        val viaRequest = Bench(inputs())
        viaRequest.house.arm(SEED)
        viaRequest.house.mint(
            Event.MeetingCalled(
                viaRequest.house.now(), Seat(4), home.someoneshome.model.MeetingTrigger.MeetingCard,
            )
        )
        val before = viaRequest.pushed.size
        viaRequest.house.heard(Intent.CheckIn(Seat(0)))

        assertTrue(
            viaRequest.pushed.size > before,
            "a check-in reached the rules and moved nothing — the gate did not see it",
        )
        val gate = viaRequest.pushed.map { it.first }
            .filterIsInstance<Effect.CheckInProgressed>()
            .last()
        assertEquals(
            seats.size, gate.expected,
            "the gate is against the whole house and the total drifted",
        )
        assertEquals(
            listOf<Intent>(Intent.CheckIn(Seat(0))), viaRequest.house.heard.toList(),
            "the authority did not record the request it was handed",
        )
    }

    /**
     * **A refusal is recorded and nothing is pushed** (D-066, rule 6).
     *
     * A vote locked outside a meeting is refused above the rules, and the phone that pressed it is
     * told nothing at all — its screen keeps saying what the house last said. The authority's own
     * record is the only place it exists.
     */
    @Test
    fun aRefusedRequestIsRecordedAndReachesNoPhone() = runBlocking<Unit> {
        val bench = Bench(inputs())
        bench.house.arm(SEED)
        val before = bench.pushed.size

        bench.house.heard(Intent.LockVote(Seat(0)))

        assertEquals(before, bench.pushed.size, "a refused request pushed something to a phone")
        assertEquals(1, bench.refusalCount(), "the refusal was not recorded: an invisible drop")
    }

    private fun Bench.refusalCount(): Int = house.refusals.size

    private companion object {
        const val SEED: Long = 20260822L
    }
}
