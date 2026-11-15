package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.JamRings
import home.someoneshome.model.MalformedSubroutineParameters
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.ReplayPattern
import home.someoneshome.model.Seat
import home.someoneshome.model.ShortHold
import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.SubroutineQuestion
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/*
 * **E-L4-3 — the per-kind draw and the tolerance grading, as things that can fail.**
 *
 * The stand-in draw dealt three numbers out of one range with no idea what they meant, so it could
 * deal a Sniff with two groups of the same size, or a Drift whose dot is in plain sight when the
 * phone asks where it is. Both clients refuse those **by name**, which is what makes this file
 * possible: the refusals are the oracle, and *it is impossible to deal an instance a client would
 * refuse* is checked by dealing thousands and reading every one of them.
 *
 * ### The oracle is honest about what it can and cannot catch
 *
 * The readers and the draw now share their bounds, so a test that only read back what was drawn
 * would be circular for the *bounds* — deal inside [1, 7], refuse outside [1, 7], of course it
 * passes. What it is not circular for is everything relational, which is where all four of D-137 to
 * D-140's rulings live: the two Sniff groups differ, the dot is still hidden when the buzz lands,
 * the band fits on the bar, the pattern flashes no dot twice. Those are properties of what was
 * drawn rather than of the range it was drawn from, and they are what the stand-in got wrong.
 */

private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1))
private val MARKERS = (0 until 8).map { MarkerId("m$it") }

private fun armed(seed: Long = 20260822L): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(
        Tick(0), seed = seed, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS),
    ),
).state

/** Every kind, dealt [many] times over from a stream nothing else is drawing from. */
private fun dealt(kind: SubroutineKind, many: Int = 400): List<List<Int>> =
    (0 until many).map { drawParameters(kind, Draw(it.toLong(), SeedPurpose.Instance, kind.ordinal.toLong())) }

/** An instance of [kind] with a known question, built the way the rules build one. */
private fun instance(
    kind: SubroutineKind,
    parameters: List<Int>,
    marker: MarkerId = MARKERS.first(),
): OpenSubroutine = OpenSubroutine(
    seat = Seat(0),
    entry = 0,
    subroutine = kind,
    parameters = parameters,
    expected = answerTo(kind, parameters),
    armedAt = marker,
)

class TheDrawCannotDealAQuestionAClientRefusesTest {

    /**
     * **The unit's central claim, and the only test in the file that is a fuzz.**
     *
     * Four thousand instances — four hundred of each of the ten — every one of them read by the
     * reader the phone that was dealt it would use. **Zero refusals.** A draw that ignored one
     * bound fails here rather than on a landing in the dark.
     *
     * The failure names the kind, the seed and the reader's own words, because *the house asked
     * something this client cannot draw* is useless without which something.
     */
    @Test
    fun `every kind deals only questions its own client accepts`() {
        for (kind in SubroutineKind.entries) {
            dealt(kind).forEachIndexed { seed, parameters ->
                try {
                    SubroutineQuestion.read(kind, parameters)
                } catch (refused: MalformedSubroutineParameters) {
                    throw AssertionError(
                        "the house dealt $kind a question its client refuses. seed $seed, " +
                            "parameters $parameters — ${refused.detail}",
                    )
                }
            }
        }
    }

    /**
     * **Every kind is drawn, and the list is literal so a new one cannot join by inference.**
     *
     * The completeness check is against [SubroutineKind.entries] rather than a `when` that would
     * have been exhaustive anyway: an eleventh Subroutine must be *dealt* somewhere in this file,
     * not merely compile.
     */
    @Test
    fun `the roster this file deals is the whole roster`() {
        val dealt = listOf(
            SubroutineKind.Replay, SubroutineKind.Interrupt, SubroutineKind.ParityCheck,
            SubroutineKind.Sniff, SubroutineKind.Deallocate, SubroutineKind.Drift,
            SubroutineKind.Short, SubroutineKind.SignalTrace, SubroutineKind.Jam,
            SubroutineKind.Handshake,
        )
        assertEquals(SubroutineKind.entries.toSet(), dealt.toSet(), "a kind is dealt by nothing")
        assertEquals(SubroutineKind.entries.size, dealt.size, "a kind is dealt twice")
    }

    /**
     * **D-137: equal groups never occur — and the draw cannot express one.**
     *
     * *The answer must exist and must be unique; a tie is a coin flip the house would then grade.*
     * The injection is to deal the tie by hand and watch the reader refuse it, which is what says
     * this is a property of the draw rather than of the range it happens to land in.
     */
    @Test
    fun `no Sniff is dealt with two groups of the same size`() {
        for (parameters in dealt(SubroutineKind.Sniff)) {
            val groups = SniffGroups.of(parameters)
            assertNotEquals(
                groups.first, groups.second,
                "a tie was dealt: $parameters — D-137 says the answer must be unique",
            )
        }
        assertFailsWith<MalformedSubroutineParameters>(
            "the reader stopped refusing a tie, so this suite's oracle has gone quiet",
        ) { SniffGroups.of(listOf(4, 4, 8)) }
    }

    /**
     * **D-140: the dot is hidden when the phone says *now*.**
     *
     * *The hidden duration is the difficulty and it is invisible* — a buzz that lands with the dot
     * in plain sight is the whole ruling inverted, because the answer is then readable off the
     * screen and the thing the Subroutine tests is not tested. Checked against the cover the
     * client draws, not against the arithmetic that drew it.
     */
    @Test
    fun `no Drift is dealt whose dot is visible at the buzz`() {
        for (parameters in dealt(SubroutineKind.Drift)) {
            val path = DriftPath.of(parameters)
            val where = path.at(path.askAtMillis.toLong())
            assertTrue(
                where != null && path.covered(where),
                "the buzz lands with the dot in sight at step $where: $parameters",
            )
        }
    }

    /**
     * **There is always room for a wait**, whatever the layout drew and however fast the dot goes.
     *
     * The draw picks the hidden duration inside what the seed and the speed left, and that room is
     * never nothing *because the fastest dot the house deals still takes longer than a tenth of a
     * second to cross the narrowest cover.* That is a relationship between two constants in two
     * files, so it is asserted rather than trusted: raise the speed past the cover and this fails
     * before the draw starts refusing to draw.
     */
    @Test
    fun `every layout and speed leaves a wait the dot can hide through`() {
        for (seed in 0 until 512) {
            for (speed in 1..DriftPath.SPAN / DriftPath.COVER_LEAST) {
                assertTrue(
                    DriftPath.hiddenTenthsAvailable(seed, speed) >= 1,
                    "seed $seed at speed $speed leaves no wait with the dot still hidden",
                )
            }
        }
    }

    /**
     * **A Signal Trace lattice the house deals has exactly one shortest route across it.**
     *
     * D-137's principle in another Subroutine's clothes: a graph with two shortest routes is one
     * where a player can walk a correct route and be graded wrong, because the grading is equality.
     * The client cannot refuse those — computing the optimum is the one thing `SignalGraph` exists
     * not to do — so the house never deals them, and there are enough left to deal from.
     */
    @Test
    fun `every Signal Trace dealt has one shortest route and only one`() {
        for (parameters in dealt(SubroutineKind.SignalTrace)) {
            val routes = shortestRoutes(SignalGraph.of(parameters))
            assertEquals(1, routes.size, "seed $parameters has ${routes.size} shortest routes")
        }
        assertTrue(
            traceSeeds.size >= 32,
            "only ${traceSeeds.size} lattices survive the uniqueness sift — the Subroutine would " +
                "repeat itself inside one round",
        )
    }

    /**
     * **Every kind draws more than one question**, which is what says the parameters are drawn
     * rather than fixed.
     *
     * A per-kind draw that returned a constant would pass every other test in this file: the
     * reader would accept it, the answer would grade, and every player in the house would be shown
     * the same picture all round.
     */
    @Test
    fun `no kind deals the same question every time`() {
        for (kind in SubroutineKind.entries) {
            assertTrue(
                dealt(kind, many = 40).toSet().size > 1,
                "$kind deals one question and nothing else",
            )
        }
    }
}

class TheAnswerIsTheAnswerToTheQuestionTest {

    /**
     * **What the house drew as the answer is what a player who read the question would do.**
     *
     * Kind by kind, worked out from the question a second way — off the picture the client draws
     * rather than off `answerTo`'s own arithmetic. A shared bug would have to be made twice, in two
     * shapes, to survive this.
     */
    @Test
    fun `the answer to every kind is the one the question implies`() {
        for (parameters in dealt(SubroutineKind.Replay, many = 60)) {
            assertEquals(ReplayPattern.of(parameters).order, answerTo(SubroutineKind.Replay, parameters))
        }
        for (parameters in dealt(SubroutineKind.ParityCheck, many = 60)) {
            val cells = ParityGrid.of(parameters)
            val odd = answerTo(SubroutineKind.ParityCheck, parameters).single()
            assertNotEquals(ParityGrid.clean(odd), cells[odd], "the answer is not the broken cell")
            assertEquals(1, cells.indices.count { cells[it] != ParityGrid.clean(it) })
        }
        for (parameters in dealt(SubroutineKind.Sniff, many = 60)) {
            val groups = SniffGroups.of(parameters)
            val bigger = if (groups.first > groups.second) 0 else 1
            assertEquals(listOf(bigger), answerTo(SubroutineKind.Sniff, parameters))
        }
        for (parameters in dealt(SubroutineKind.Deallocate, many = 60)) {
            val columns = DotColumns.of(parameters)
            val levelled = columns.zip(answerTo(SubroutineKind.Deallocate, parameters)) { h, off -> h - off }
            assertEquals(
                List(columns.size) { columns.min() }, levelled,
                "D-138: taking the answer off the columns has to leave them level with the shortest",
            )
        }
        for (parameters in dealt(SubroutineKind.Short, many = 20)) {
            assertEquals(
                listOf(ShortHold.of(parameters).fingers),
                answerTo(SubroutineKind.Short, parameters),
            )
        }
        for (parameters in dealt(SubroutineKind.Jam, many = 40)) {
            val walked = JamRings.of(parameters).opening +
                answerTo(SubroutineKind.Jam, parameters).single()
            assertEquals(0, walked, "the answer does not close the gap the house opened")
        }
        for (parameters in dealt(SubroutineKind.Handshake, many = 40)) {
            assertEquals(
                List(HandshakeRhythm.of(parameters).beats) { it },
                answerTo(SubroutineKind.Handshake, parameters),
                "the taps owed are one per beat of the rhythm",
            )
        }
        for (parameters in dealt(SubroutineKind.Interrupt, many = 60)) {
            assertEquals(
                listOf(InterruptSweep.of(parameters).bandAt),
                answerTo(SubroutineKind.Interrupt, parameters),
            )
        }
        for (parameters in dealt(SubroutineKind.Drift, many = 60)) {
            val path = DriftPath.of(parameters)
            assertEquals(
                listOf(path.at(path.askAtMillis.toLong())),
                answerTo(SubroutineKind.Drift, parameters),
                "the answer is not where the dot is when the phone buzzes",
            )
        }
    }

    /**
     * **A Signal Trace answer is a walk the lattice actually has**, edge by edge, source to sink.
     *
     * The route is computed rather than authored, so the thing to check is that it is a route: two
     * consecutive nodes with no edge between them would be an answer no player could enter, and
     * every entry in the round would be refused with the screen showing a graph they had walked
     * correctly.
     */
    @Test
    fun `a Signal Trace answer is a real walk from source to sink`() {
        for (parameters in dealt(SubroutineKind.SignalTrace, many = 60)) {
            val wiring = SignalGraph.of(parameters)
            val route = answerTo(SubroutineKind.SignalTrace, parameters)
            assertEquals(wiring.source, route.first(), "the route does not start at the source")
            assertEquals(wiring.sink, route.last(), "the route does not end at the sink")
            for ((from, to) in route.zipWithNext()) {
                assertTrue(to in wiring.joinedTo(from), "$from and $to are not joined: $route")
            }
        }
    }

    /**
     * **The house's own answer is accepted, for every kind, every time.**
     *
     * The bluntest statement of the whole unit: a question dealt, an answer derived, and the
     * grading that will meet a player's entry agreeing with both. A tolerance that read the wrong
     * parameter, an answer of the wrong length, a grading that fell through to equality where the
     * entry is a position — all of them land here.
     */
    @Test
    fun `the drawn answer grades as accepted for every kind`() {
        for (kind in SubroutineKind.entries) {
            dealt(kind, many = 60).forEach { parameters ->
                val open = instance(kind, parameters)
                assertTrue(
                    grades(open, MARKERS.first(), open.expected),
                    "$kind refused its own answer: $parameters wanted ${open.expected}",
                )
            }
        }
    }
}

class TheToleranceIsRealAndItIsTheHousesTest {

    /**
     * **Interrupt is graded by band membership, and the band is the one the phone drew** (D-139,
     * E-S2-1 closed).
     *
     * Three claims in one walk of the band. A tap **one step off** the middle is accepted — under
     * equality it would not be, which is the injection this test is built around. A tap on each
     * edge is accepted, because the band the player sees includes its edges. And a tap **one step
     * outside** either edge is refused, which is what stops *a tolerance* from meaning *anything
     * goes*.
     */
    @Test
    fun `a tap inside the band is caught and a tap outside it is not`() {
        for (parameters in dealt(SubroutineKind.Interrupt, many = 60)) {
            val sweep = InterruptSweep.of(parameters)
            val open = instance(SubroutineKind.Interrupt, parameters)
            val marker = MARKERS.first()

            assertTrue(
                grades(open, marker, listOf(sweep.bandAt + 1)),
                "a tap one step off the middle of the band was refused — the grading is still " +
                    "equality, and D-139's generous band is a band nobody can land in",
            )
            assertTrue(grades(open, marker, listOf(sweep.bandFrom)), "the near edge is in the band")
            assertTrue(grades(open, marker, listOf(sweep.bandTo)), "the far edge is in the band")
            assertFalse(
                grades(open, marker, listOf(sweep.bandFrom - 1)),
                "a tap outside the near edge was caught: the band has no near edge",
            )
            assertFalse(
                grades(open, marker, listOf(sweep.bandTo + 1)),
                "a tap outside the far edge was caught: the band has no far edge",
            )
        }
    }

    /**
     * **Drift is graded by hit radius, and both sides of the edge are checked** (D-140).
     *
     * The radius is centred on where the dot *is*, which the player cannot see — so the edge is the
     * only part of this grading anybody can reason about, and it is checked on both sides for the
     * reason the band's is: a tolerance nobody ever falls outside is not a tolerance.
     */
    @Test
    fun `a tap within the hit radius lands and a step past it does not`() {
        for (parameters in dealt(SubroutineKind.Drift, many = 60)) {
            val radius = DriftPath.of(parameters).hitRadius
            val open = instance(SubroutineKind.Drift, parameters)
            val marker = MARKERS.first()
            val truth = open.expected.single()

            assertTrue(grades(open, marker, listOf(truth - radius)), "the near edge of the radius")
            assertTrue(grades(open, marker, listOf(truth + radius)), "the far edge of the radius")
            assertFalse(
                grades(open, marker, listOf(truth - radius - 1)),
                "a tap a step outside the radius was accepted",
            )
            assertFalse(
                grades(open, marker, listOf(truth + radius + 1)),
                "a tap a step outside the radius was accepted",
            )
        }
    }

    /**
     * **The eight discrete kinds are graded by equality, and a near miss is a miss.**
     *
     * Stated as a differential against the two above: the same off-by-one that Interrupt and Drift
     * forgive has to be refused everywhere else, or *tolerance* has quietly become the rule rather
     * than the exception D-139 and D-140 carved out.
     */
    @Test
    fun `a near miss on a discrete kind is a miss`() {
        val discrete = listOf(
            SubroutineKind.Replay, SubroutineKind.ParityCheck, SubroutineKind.Sniff,
            SubroutineKind.Deallocate, SubroutineKind.Short, SubroutineKind.SignalTrace,
            SubroutineKind.Jam, SubroutineKind.Handshake,
        )
        assertEquals(
            SubroutineKind.entries - setOf(SubroutineKind.Interrupt, SubroutineKind.Drift),
            discrete,
            "the two graded by tolerance are exactly Interrupt and Drift, and this list disagrees",
        )
        for (kind in discrete) {
            dealt(kind, many = 40).forEach { parameters ->
                val open = instance(kind, parameters)
                val nudged = open.expected.toMutableList().also { it[0] = it[0] + 1 }
                assertFalse(
                    grades(open, MARKERS.first(), nudged),
                    "$kind accepted an entry one off the answer: $nudged for ${open.expected}",
                )
            }
        }
    }

    /**
     * **A Signal Trace route grades the same traced from either end** (D-211).
     *
     * The two ends are drawn alike, so a player who starts at the far one has walked the right
     * route. Three claims, each a different failure: the forward route and its reverse **both** land
     * and land alike; the reverse of a **wrong** route is still wrong, so *either end* has not become
     * *any order*; and a reversed route missing an end is refused, so the reverse is not a prefix
     * match. Graded the same, every time, from the question alone: nothing here reads a role.
     */
    @Test
    fun `a Signal Trace route grades the same traced from either end`() {
        val marker = MARKERS.first()
        for (parameters in dealt(SubroutineKind.SignalTrace, many = 60)) {
            val open = instance(SubroutineKind.SignalTrace, parameters)
            val forward = open.expected
            val reversed = forward.reversed()
            assertNotEquals(forward, reversed, "a route of one node has no second end: $forward")

            assertTrue(grades(open, marker, forward), "the forward route was refused: $forward")
            assertEquals(
                grades(open, marker, forward), grades(open, marker, reversed),
                "the route traced from the sink graded differently from the same route from the " +
                    "source: $reversed",
            )

            val wrong = forward.toMutableList().also { it[1] = it[1] + 1 }
            assertFalse(grades(open, marker, wrong.reversed()), "a wrong route reversed was accepted")
            assertFalse(
                grades(open, marker, reversed.dropLast(1)),
                "a reversed route that never reached the source was accepted",
            )
            assertFalse(
                grades(open, MarkerId("somewhere else"), reversed),
                "a reversed route handed over at the wrong card was accepted",
            )
        }
    }

    /**
     * **An entry handed over at the wrong card is refused, whatever it said** (D-110, D-123).
     *
     * The half of grading that is a fact about a place, and it survived the move of the other half
     * into this module. A tolerance that graded a correct tap from the wrong room would refund the
     * walk the whole Subroutine costs.
     */
    @Test
    fun `the right answer at the wrong card is not an answer`() {
        for (kind in SubroutineKind.entries) {
            val open = instance(kind, dealt(kind, many = 1).single())
            assertFalse(
                grades(open, MarkerId("somewhere else"), open.expected),
                "$kind graded an entry handed over at a card it was not armed at",
            )
        }
    }
}

class ARevokedSeatBanksNothingTest {

    /**
     * **E-L1-2 — a seat that is out cannot bank, and the verdict still leaves the house.**
     *
     * `returned` did not ask whether the actor was out, so a Revoked seat holding an instance from
     * before it went out could hand it over and move SystemIntegrity. Unreachable in play and
     * reachable by the fuzzer, which is the shape of a bug that arrives the day a reconnect
     * replays a queued intent.
     *
     * **Both halves are asserted, and the second is the one that matters.** The meter does not
     * move; and the three effects are emitted exactly as a living seat's are, because the refusal
     * has to be invisible as an absence (rule 1). The allowlist declines to deliver them to an out
     * class, so this is the second of two independent denials.
     */
    @Test
    fun `an out seat's correct entry banks nothing and is still answered`() {
        val base = armed()
        val seat = Seat(0)
        val card = base.workOrderFor(seat)!!.entries.first().anchor!!

        val scanned = reduce(base, Event.MarkerScanned(Tick(1), seat, card)).state
        val answer = scanned.openSubroutineFor(seat)!!.expected

        val living = reduce(scanned, Event.SubroutineReturned(Tick(2), seat, card, answer))
        val wrong = reduce(
            scanned,
            Event.SubroutineReturned(Tick(2), seat, card, answer.map { it + 100 }),
        )
        val out = reduce(
            scanned.copy(revoked = listOf(seat)),
            Event.SubroutineReturned(Tick(2), seat, card, answer),
        )

        assertTrue(
            living.state.systemIntegrity < scanned.systemIntegrity,
            "the fixture's correct entry did not bank, so the guard below proves nothing",
        )
        assertEquals(
            scanned.systemIntegrity, out.state.systemIntegrity,
            "a Revoked seat's entry moved the meter",
        )
        assertEquals(
            listOf(Effect.SubroutineGraded(seat, accepted = false)),
            out.effects.filterIsInstance<Effect.SubroutineGraded>(),
            "the out seat's verdict was withheld — the absent effect is the leak",
        )
        // The differential is against a LIVING seat's refused entry, not against an accepted one:
        // `SubroutineProgressed` is emitted when the meter moves and the meter did not move, which
        // is the same stream a wrong answer produces. Nothing in the shape says which of the two
        // this was.
        assertEquals(
            wrong.effects.map { it::class.simpleName },
            out.effects.map { it::class.simpleName },
            "an out seat's return emitted a different SHAPE from a refused one — rule 1's branch",
        )
    }

    /** A Restrained seat is out by the same rule, and the gate reads [GameState.isOut] not one. */
    @Test
    fun `a Restrained seat's correct entry banks nothing either`() {
        val base = armed()
        val seat = Seat(0)
        val card = base.workOrderFor(seat)!!.entries.first().anchor!!
        val scanned = reduce(base, Event.MarkerScanned(Tick(1), seat, card)).state
        val answer = scanned.openSubroutineFor(seat)!!.expected
        val out = reduce(
            scanned.copy(restrained = listOf(seat)),
            Event.SubroutineReturned(Tick(2), seat, card, answer),
        )
        assertEquals(scanned.systemIntegrity, out.state.systemIntegrity)
    }
}

class TheClockThisFileConvertsThroughTest {

    /**
     * **20 Hz, confirmed by the owner on 2026-08-22, asserted where durations are converted.**
     *
     * E-L5-1 recorded that `Balance` had adopted `FixedTimestep`'s 20 Hz and that nobody had said
     * so out loud. Somebody has now. It is asserted here because this unit deals **two other
     * clocks** — tenths of a second on the wire and milliseconds on the client's own screen clock
     * — and the failure mode of three clocks in one system is that two of them get multiplied
     * together by somebody who thought there was one.
     *
     * Nothing in a Subroutine's parameters is in ticks, and that is the second half of the
     * assertion: a wait is tenths, a buzz is milliseconds, and a cooldown is ticks.
     */
    @Test
    fun `the round runs at twenty ticks a second and no parameter is in ticks`() {
        assertEquals(20L, Balance.TICKS_PER_SECOND, "the tick rate moved without this file moving")
        assertEquals(
            60L * Balance.TICKS_PER_SECOND, Balance.REVOKE_COOLDOWN,
            "D-132's sixty seconds is sixty seconds only while the cooldown reads through the rate",
        )
        assertEquals(100, DriftPath.HIDDEN_TENTHS, "Drift's wait is tenths of a second, in millis")
        assertEquals(100, SniffGroups.GAP_TENTHS, "Sniff's pause is tenths of a second, in millis")
        assertEquals(100, HandshakeRhythm.GAP_TENTHS, "Handshake's gaps are tenths, in millis")
    }
}
