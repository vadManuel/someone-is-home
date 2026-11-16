package home.someoneshome.app

import home.someoneshome.model.CellRect
import home.someoneshome.model.Effect
import home.someoneshome.model.Floor
import home.someoneshome.model.GameState
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.OrderLine
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.SavedHome
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The two halves of an evening that could not start: nothing armed a round, and the round was
 * never in anybody's house** (E-PAY-1 and E-PAY-3).
 *
 * ### Why it is in `commonTest` and not beside the bench
 *
 * Because what it tests ships. [armAtLightsOut] and [RoundAuthority] are compiled into the release
 * variant, so their tests have to be — a test in `src/cheatsTest/` is a test the one build that
 * goes to a real round never runs. That is not hypothetical: it is exactly why `cheatsTest` exists
 * at all, discovered when a cheat's test was written into `commonTest` and `variantSweepRelease`
 * refused the build. This is the same rule pointing the other way.
 *
 * ### The home here is NOT the bench's, and that is the instrument
 *
 * Every assertion below would pass against a fixture if the fixture were the thing being armed
 * with — which is precisely how three units of rehearsals ran green over E-PAY-3. So this hosts a
 * home with rooms and card ids that exist nowhere else in the tree, and asks whether *that* home's
 * cards are what the work orders were drawn from. A round still armed in `BenchHome` fails by
 * name, on the ids.
 */
class HostArmingTest {

    // ---- A home somebody walked -----------------------------------------------------------------

    /**
     * Rooms in a row, one cell each, so the adjacency graph is a path — `egressFor` picks the two
     * markers whose rooms are farthest apart, and a home where everything touches everything would
     * make that draw prove nothing.
     *
     * The names are ordinary house words and are deliberately **not** the bench's five.
     */
    private val rooms = listOf("PORCH", "PANTRY", "SNUG", "BOOT ROOM", "LOFT")

    private val plan = HousePlan.of(
        listOf(
            Floor(
                name = "GROUND",
                rooms = rooms.mapIndexed { i, name ->
                    PlanRoom(Room(name), listOf(CellRect(x = i, y = 0, width = 1, height = 1)))
                },
            )
        )
    )

    /**
     * Ten ordinary cards, two to a room. Ten clears D-127's floor of eight.
     *
     * The ids are seven characters out of the printable alphabet, which is what a real card
     * carries — a fixture whose ids could not be printed would be a home nobody could scan.
     */
    private val ordinary = List(10) { i ->
        Registration(
            card = MarkerCard(1, MarkerShapes.registrable[i], MarkerId("GIBSN-" + ('A' + i))),
            room = Room(rooms[i / 2]),
        )
    }

    private val home = SavedHome(
        name = "THE PLACE ON GIBSON STREET",
        plan = plan,
        map = HouseMap.of(
            registrations = ordinary,
            terminal = Registration(
                MarkerCard(1, MarkerShapes.TERMINAL, MarkerId("GIBSN-T")), Room(rooms.first()),
            ),
            meeting = Registration(
                MarkerCard(1, MarkerShapes.MEETING, MarkerId("GIBSN-U")), Room(rooms[2]),
            ),
        ),
    )

    // ---- A lobby with a party in it -------------------------------------------------------------

    private val seats = List(5) { Seat(it) }

    private fun inputs(
        seats: List<Seat> = this.seats,
        ready: Boolean = true,
        home: SavedHome? = this.home,
    ) = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = ready,
        home = home,
    )

    private class Bench(inputs: ArmingInputs) {
        val pushed = mutableListOf<Effect>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect: Effect, _: GameState -> pushed += effect },
            note = { said += it },
        )
    }

    private fun bench(
        seats: List<Seat> = this.seats,
        ready: Boolean = true,
        home: SavedHome? = this.home,
    ) = Bench(inputs(seats, ready, home))

    // ---- 1 · Something in the app arms a round (E-PAY-1) -----------------------------------------

    /**
     * **The press arms the house.** This is the whole of E-PAY-1: on the night it was measured,
     * five seats joined a real lobby, the host's hold landed, and there was no `ARMED` line on any
     * of the five.
     */
    @Test
    fun theHostsPressArmsTheRound() = runBlocking<Unit> {
        val bench = bench()

        assertTrue(
            armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it }),
            "LIGHTS OUT armed no round at all: ${bench.said}",
        )
        assertTrue(bench.house.armed, "the house came back from arming with no round on it")
        assertEquals(
            seats, bench.pushed.filterIsInstance<Effect.OpeningMessage>().map { it.seat },
            "the opening message did not reach every seat exactly once",
        )
    }

    /**
     * **And nobody else's press does.** The app root builds an authority on every seat and it is
     * inert on four of them; the test for which phone is the house is the lobby's own answer, not
     * the presence of an object. P3 read that the other way and put five abstentions on one ballot.
     */
    @Test
    fun aClientsPressArmsNothing() = runBlocking<Unit> {
        val bench = bench()

        assertFalse(
            armAtLightsOut(bench.house, hosting = false, seed = SEED, note = { bench.said += it }),
            "a phone that is not hosting armed a round",
        )
        assertFalse(bench.house.armed, "a client's press armed the house it does not run")
    }

    /**
     * **LIGHTS OUT below the minimum is refused, and the host's screen never says ARMED** (D-226).
     *
     * The 2026-11-16 device log, as a test: the host's phone locked in the lobby, every seat
     * dropped, and the press reached a house with nobody in it. The house refused — `WILL NOT ARM —
     * 0 SEAT(S)` — and the screen walked to ARMED anyway, because the walk happened on the press.
     * Driven through `FlowModel.lightsOut` with the seam wired the way the app root wires it, so the
     * screen is the thing asserted.
     */
    @Test
    fun aLightsOutTheHouseRefusesLeavesTheHostOnItsLobby() = runBlocking<Unit> {
        for (party in listOf(emptyList(), seats.take(2))) {
            val bench = bench(seats = party, ready = false)
            val answered = CompletableDeferred<Boolean>()
            val flow = FlowModel(
                PanelState(screen = ScreenId.Lobby),
                onLightsOut = { armed ->
                    launch {
                        answered.complete(
                            armAtLightsOut(bench.house, hosting = true, seed = SEED, armed = armed, note = { bench.said += it }),
                        )
                    }
                },
            )

            flow.lightsOut()

            assertFalse(answered.await(), "the house armed ${party.size} seat(s)")
            assertFalse(bench.house.armed, "a round was armed with ${party.size} seat(s) in the lobby")
            assertEquals(
                ScreenId.Lobby, flow.state.screen,
                "the house refused ${party.size} seat(s) and the host's screen said ARMED anyway",
            )
            assertTrue(
                bench.said.any { it.startsWith("WILL NOT ARM — ${party.size} SEAT(S)") },
                "the refusal was not said out loud to the authority: ${bench.said}",
            )
        }
    }

    /** **And a house that arms walks the host to ARMED**, through the same wiring. */
    @Test
    fun aLightsOutTheHouseArmsWalksTheHostToArmed() = runBlocking<Unit> {
        val bench = bench()
        val answered = CompletableDeferred<Boolean>()
        val flow = FlowModel(
            PanelState(screen = ScreenId.Lobby),
            onLightsOut = { armed ->
                launch {
                    answered.complete(
                        armAtLightsOut(bench.house, hosting = true, seed = SEED, armed = armed, note = { bench.said += it }),
                    )
                }
            },
        )

        flow.lightsOut()

        assertTrue(answered.await(), "the house did not arm a ready lobby: ${bench.said}")
        assertEquals(ScreenId.Armed, flow.state.screen, "the house armed and the host's screen stayed put")
    }

    /** A second press on a running round arms nothing and says so, rather than refusing silently. */
    @Test
    fun aSecondPressDoesNotArmASecondRound() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })
        val openings = bench.pushed.filterIsInstance<Effect.OpeningMessage>().size

        assertFalse(
            armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it }),
            "a second LIGHTS OUT armed a second round over a round that was already running",
        )
        assertEquals(
            openings, bench.pushed.filterIsInstance<Effect.OpeningMessage>().size,
            "the second press pushed the round's opening at everybody a second time",
        )
    }

    /**
     * **And then it stops.** No contact, no report, no meeting, no ability, no ending — every one
     * of which the bench script mints. The evening after the lights go out belongs to the people
     * holding the phones, and a version of this that quietly ran a shift would look correct in
     * every log until somebody wondered why the room was being played for them.
     */
    @Test
    fun nothingIsPlayedForTheRoomAfterTheLightsGoOut() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })

        val acted = bench.pushed.filter {
            it is Effect.AbilityFired || it is Effect.MeetingOpened ||
                it is Effect.StaticOpened || it is Effect.EgressOpened ||
                it is Effect.RoundEnded
        }
        assertEquals(
            emptyList(), acted,
            "arming played the round for the room: it minted ${acted.map { it::class.simpleName }}",
        )
    }

    // ---- 2 · The round is played in the hosted home (E-PAY-3) ------------------------------------

    /**
     * **The markers the round was armed with are exactly the hosted home's ordinary cards.**
     *
     * Asserted as the whole list rather than a count or a sample: a round armed with nine of the
     * ten and one card from somewhere else is the failure this is here to catch, and a count of
     * ten would sit green through it.
     *
     * The two reserved cards are **not** in it — the Terminal and the meeting card are places
     * rather than work — which is `HouseMap.destinations`' own rule and is under test here because
     * a draw that anchored an order line on the meeting card would send somebody to do a
     * Subroutine at the card the whole party meets on.
     */
    @Test
    fun theRoundIsArmedWithTheHostedHomesOwnCards() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })

        assertEquals(
            home.map.destinations, bench.house.state.destinations,
            "the round was armed with a different set of markers than the home the host is hosting",
        )
        assertFalse(
            bench.house.state.destinations.any { it.marker.value == "GIBSN-T" },
            "the Terminal was dealt as ordinary work: it is a place, not a line of anybody's order",
        )
        assertFalse(
            bench.house.state.destinations.any { it.marker.value == "GIBSN-U" },
            "the meeting card was dealt as ordinary work (D-121)",
        )
    }

    /**
     * **Every line of every seat's order names a card and a room of the home that was hosted.**
     *
     * This is the assertion the payoff run's table is written against, turned into a test: *reads
     * their work order — it names HALL, KITCHEN, LOUNGE, STUDY, CELLAR, rooms they do not have.*
     * A round still armed with the bench's fixture fails here on the id, by name.
     */
    @Test
    fun noCardFromAnywhereElseReachesAnOrderLine() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })

        val ids = home.map.registrations.map { it.card.id.value }.toSet()
        for (seat in seats) {
            val state = bench.house.state
            val lines = state.workOrderFor(seat)?.asLines(state.destinations).orEmpty()
            assertTrue(lines.isNotEmpty(), "seat ${seat.index} was dealt no work order at all")
            for (line in lines.filterIsInstance<OrderLine.Known>()) {
                for (at in line.at) {
                    assertTrue(
                        at.marker.value in ids,
                        "seat ${seat.index} was sent to '${at.marker.value}' in '${at.room}', " +
                            "which is not a card in '${home.name}'",
                    )
                    assertTrue(
                        at.room in rooms,
                        "seat ${seat.index} was sent to '${at.room}', which is not a room in " +
                            "'${home.name}' — the host does not have one",
                    )
                }
            }
        }
    }

    /**
     * **THE PLAN CROSSES TO NOBODY** — the probe, and the ruling it is holding.
     *
     * Static deleted `Effect.HomePlanOffered`, the largest disclosure the emit table ever held, and
     * the ruling has stood since: **a client has no map.** Handing the host's home to the arming
     * draw is the first time in this project's history that the whole of a real house has been in
     * the same object as the thing that talks to five phones, so the narrowing is worth a test at
     * the moment it became possible to lose.
     *
     * What a seat learns is the destinations of **its own** lines and nothing else
     * (`WorkOrder.asLines`). So this asserts the containment in the direction that matters: no row
     * a seat is handed names a card that seat has no work at. A widening of the narrowing — one
     * call site handing over `state.destinations` whole — puts the entire geography of somebody's
     * house on every phone in it, and every card on it would look perfectly ordinary.
     */
    @Test
    fun noSeatIsToldWhereAnybodyElsesWorkIs() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })
        val state = bench.house.state

        for (seat in seats) {
            val order = state.workOrderFor(seat) ?: error("seat ${seat.index} has no order")
            val mine = order.entries.flatMap { it.markers }.map { it.value }.toSet()
            val told = order.asLines(state.destinations)
                .filterIsInstance<OrderLine.Known>()
                .flatMap { it.at }
                .map { it.marker.value }
                .toSet()

            assertTrue(told.isNotEmpty(), "seat ${seat.index} was told nowhere to go at all")
            assertEquals(
                emptySet(), told - mine,
                "seat ${seat.index} was handed ${told - mine}, which is not on its own order — " +
                    "the home's geography is reaching a phone that has no work there",
            )
        }
    }

    /**
     * **A phone hosting no home arms nothing and says why**, rather than reaching for a fixture.
     *
     * The refusal is the point of the field: for three units there was no way to be in this state,
     * because the draw always had a home to use and it was never the host's.
     */
    @Test
    fun aPhoneHostingNoHomeArmsNothingAndSaysSo() = runBlocking<Unit> {
        val bench = bench(home = null)

        assertFalse(
            armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it }),
            "a phone with no home on the air armed a round anyway",
        )
        assertTrue(
            bench.said.any { it.contains("HOSTING NO HOME") },
            "the refusal never reached the host's own phone: ${bench.said}",
        )
    }

    /**
     * A lobby that is short is refused out loud on the host's own phone in a lit room, which is
     * where `armingFor` says a refusal belongs. The gate is `LobbyDesk.readyToArm` — the same one
     * the button reads — so this is the four-seat press, and it still does nothing.
     */
    @Test
    fun aLobbyOfFourArmsNothingAndSaysSo() = runBlocking<Unit> {
        val bench = bench(seats = seats.take(4), ready = false)

        assertFalse(
            armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it }),
            "a four-seat lobby armed a round",
        )
        assertTrue(
            bench.said.any { it.contains("WILL NOT ARM") },
            "the refusal never reached the host's own phone: ${bench.said}",
        )
    }

    /**
     * **The Egress is drawn in the hosted home too**, which is the other half of E-PAY-3: the
     * crisis used to open at two ends of a corridor nobody owns.
     */
    @Test
    fun theEgressOpensAtTwoOfTheHostedHomesOwnCards() = runBlocking<Unit> {
        val bench = bench()
        armAtLightsOut(bench.house, hosting = true, seed = SEED, note = { bench.said += it })
        bench.house.at(SECONDS_PAST_THE_EGRESS_COOLDOWN)

        // The Insider the seed drew, not seat 0: a Resident's fire is refused at the gate, and
        // which seat the seed draws moved with D-214.
        assertTrue(
            bench.house.fireEgress(bench.house.state.insiderSeats.first()),
            "the house drew no Egress at all: ${bench.said}",
        )
        val ids = home.map.registrations.map { it.card.id.value }.toSet()
        val nodes = bench.house.state.egress?.nodes.orEmpty()
        assertEquals(2, nodes.size, "an Egress opened with ${nodes.size} node(s)")
        for (node in nodes) {
            assertTrue(
                node.value in ids,
                "the Egress opened at '${node.value}', which is not a card in '${home.name}'",
            )
        }
    }

    private companion object {

        /** Written down rather than drawn: an assertion that cannot be repeated is not one. */
        const val SEED: Long = 20260824L

        /** Comfortably past the Egress's half-cooldown at arming (D-132, D-184). */
        const val SECONDS_PAST_THE_EGRESS_COOLDOWN: Long = 600L
    }
}
