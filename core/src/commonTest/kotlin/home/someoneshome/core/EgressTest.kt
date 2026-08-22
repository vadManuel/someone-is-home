package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.CardPayload
import home.someoneshome.model.CellRect
import home.someoneshome.model.Effect
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.Floor
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Presence
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The Egress lifecycle, as things that can fail.**
 *
 * Six seats, seat 1 and seat 4 the Insiders — two, because the whole of the cooldown ruling is that
 * they share one clock, and a single-Insider fixture cannot tell a shared cooldown from a per-seat
 * one.
 *
 * The house is a four-room bungalow: `WEST — MIDDLE — EAST` in a row, with `LANDING` under
 * `MIDDLE`. Adjacency is four-neighbourhood, so `LANDING` touches only `MIDDLE` — it meets `WEST`
 * and `EAST` at a corner, which is not a doorway. Restricting the active set to the strip leaves
 * exactly one non-adjacent pair, `WEST` and `EAST`, which is what turns the selection test into an
 * assertion rather than a coincidence; the whole four gives the draw more than one right answer.
 *
 * Taking rooms away is also how the small-home degrade is exercised, rather than by building a
 * second house.
 */
private val SEATS = (0 until 6).map { Seat(it) }
private val INSIDERS = listOf(Seat(1), Seat(4))

private val WEST = MarkerId("west-card")
private val MIDDLE = MarkerId("middle-card")
private val EAST = MarkerId("east-card")
private val LANDING = MarkerId("landing-card")

/** `WEST — MIDDLE — EAST` in a row, with `LANDING` under `MIDDLE`. Adjacency is cell neighbours. */
private fun plan(): HousePlan = HousePlan.of(
    listOf(
        Floor(
            "GROUND",
            listOf(
                PlanRoom(Room("WEST"), listOf(CellRect(0, 0, 1, 1))),
                PlanRoom(Room("MIDDLE"), listOf(CellRect(1, 0, 1, 1))),
                PlanRoom(Room("EAST"), listOf(CellRect(2, 0, 1, 1))),
                PlanRoom(Room("LANDING"), listOf(CellRect(1, 1, 1, 1))),
            ),
        ),
    ),
)

private fun card(id: MarkerId, shape: Int) =
    MarkerCard(CardPayload.VERSION, MarkerShapes.all[shape], id)

private fun map(): HouseMap = HouseMap.of(
    listOf(
        Registration(card(WEST, 1), Room("WEST")),
        Registration(card(MIDDLE, 2), Room("MIDDLE")),
        Registration(card(EAST, 3), Room("EAST")),
        Registration(card(LANDING, 4), Room("LANDING")),
    ),
)

/**
 * A round armed in that house, with the four cards active and the shared Egress clock already run
 * down.
 *
 * The clock is pushed rather than typed: D-132 starts it at half, and a test that fired at whatever
 * tick a counter happened to be at would fire nothing and go on asserting the *shape* of what came
 * back. `readyToFire` is the fact read off the state.
 */
private fun armed(): GameState = GameState.armedRound(
    seed = 20260822L,
    seats = SEATS,
    insiders = INSIDERS,
    systemIntegrity = 30,
    activeMarkers = listOf(WEST, MIDDLE, EAST, LANDING),
    egressReadyAt = Tick(50),
)

private fun readyToFire(state: GameState): Tick = state.egressReadyAt

/** Drive events through the **admission gate**, never through [reduce] — half the rules are here. */
private class EgressWalk(start: GameState) {
    var state: GameState = start
        private set
    val emitted = mutableListOf<Effect>()
    val refusals = mutableListOf<RefusalReason>()

    fun feed(vararg events: Event): EgressWalk {
        for (event in events) {
            when (val admission = admit(state, event)) {
                is Admission.Admitted -> {
                    state = admission.reduction.state
                    emitted += admission.reduction.effects
                }
                is Admission.Refused -> refusals += admission.reason
            }
        }
        return this
    }

    fun drain(): List<Effect> = emitted.toList().also { emitted.clear() }
}

/** Four taps landing exactly on four consecutive beats of the house schedule. */
private fun onBeat(firedAt: Tick, from: Long = 0L): List<Long> =
    (0 until Balance.SYNC_PULSE_BEATS).map { beatAt(firedAt, from + it) }

/**
 * Four taps a hair outside the window, on four consecutive beats.
 *
 * One tick past generous, which is the honest failure to write: a build with no grading at all and
 * a build whose window is a whole beat wide both pass a test that taps halfway between two beats.
 */
private fun offBeat(firedAt: Tick, from: Long = 0L): List<Long> =
    onBeat(firedAt, from).map { it + Balance.SYNC_PULSE_WINDOW + 1 }

/** A round already on fire, with both nodes named and two seats standing at them. */
private fun burning(
    at: Tick = Tick(60),
    nodes: List<MarkerId> = listOf(WEST, EAST),
): GameState = armed()
    .withEgress(Egress.fired(at, EgressType.Beacon, nodes, Balance.EGRESS_TIMER))
    .withPresence(Presence(Seat(0), nodes[0], open = false))
    .withPresence(Presence(Seat(2), nodes[1], open = false))

class EgressTest {

    // ---- F-001: what the house names ----------------------------------------------------------

    /**
     * **Two ordinary markers in non-adjacent rooms, drawn at fire time** (F-001, ratified).
     *
     * Across the strip exactly one pair is non-adjacent — `WEST` and `EAST`, two hops apart through
     * `MIDDLE` — so the answer is not merely *some* pair that satisfies the rule; it is the only
     * one. A build that picked adjacent rooms, or picked at random, cannot pass this.
     *
     * Repeated over a run of ticks, because a single draw that happened to be right proves nothing
     * about a rule.
     */
    @Test
    fun `the house names two markers in non-adjacent rooms`() {
        val strip = armed().copy(activeMarkers = listOf(WEST, MIDDLE, EAST))
        for (at in 60L..90L) {
            val fired = egressFor(Tick(at), Seat(1), strip, map(), plan())
            assertNotNull(fired, "a three-room home could not supply an Egress")
            assertEquals(
                listOf(EAST.value, WEST.value),
                fired.nodes.map { it.value }.sorted(),
                "the only non-adjacent pair in this strip is WEST and EAST",
            )
        }
    }

    /**
     * **The small-home degrade is the same rule, not an exception to it** (owner's ruling).
     *
     * *Small homes are lawful — pick the farthest available; never fail to fire.* Here the home is
     * `WEST — MIDDLE`, two rooms that touch, so **no** non-adjacent pair exists. The Egress still
     * fires and still names two distinct markers, because the rule is *farthest apart* and a
     * distance of one is what farthest means in a house this small.
     */
    @Test
    fun `a home with no non-adjacent pair still fires at the farthest it has`() {
        val small = armed().copy(activeMarkers = listOf(WEST, MIDDLE))
        val fired = egressFor(Tick(60), Seat(1), small, map(), plan())
        assertNotNull(fired, "a two-room home refused to fire; small homes are lawful")
        assertEquals(2, fired.nodes.distinctBy { it.value }.size, "the pair collapsed to one marker")
    }

    /**
     * **A round that cannot supply two distinct markers holds no Egress at all.**
     *
     * Not the degrade — D-127 floors a reviewable home at eight markers, so this is a round armed
     * against an empty home. Firing there would start an Egress **physically impossible to
     * contain** that looked exactly like one that was not, and the Residents would lose to a shape
     * they could never see. Declining is the fail-closed direction and it is the Residents' side.
     */
    @Test
    fun `a round with fewer than two markers cannot hold an Egress`() {
        val bare = armed().copy(activeMarkers = listOf(WEST))
        assertNull(egressFor(Tick(60), Seat(1), bare, map(), plan()))
        assertNull(egressFor(Tick(60), Seat(1), armed().copy(activeMarkers = emptyList()), map(), plan()))
    }

    /**
     * **The draw replays** (rule 4).
     *
     * Same round, same tick, same pair and same label — every time, in this process and in the one
     * that reads the recording back next month. A different tick is allowed to draw differently and
     * usually does; what is asserted is only that the *same* inputs cannot disagree with themselves.
     */
    @Test
    fun `the type and the nodes are drawn deterministically`() {
        val a = egressFor(Tick(60), Seat(1), armed(), map(), plan())
        val b = egressFor(Tick(60), Seat(1), armed(), map(), plan())
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(a.nodes.map { it.value }, b.nodes.map { it.value })
        assertEquals(a.type, b.type)
    }

    /**
     * **Beacon and Tether are both reachable, and neither is a constant.**
     *
     * They are mechanically identical, so the only way this can be wrong is by being *always the
     * same one* — which nobody would notice for months, and which would quietly delete half of the
     * fiction the design wrote for this ability.
     */
    @Test
    fun `both types are drawn over a run of Egresses`() {
        val drawn = (60L..120L).mapNotNull {
            egressFor(Tick(it), Seat(1), armed(), map(), plan())?.type
        }.distinct()
        assertEquals(
            listOf(EgressType.Beacon, EgressType.Tether).sortedBy { it.name },
            drawn.sortedBy { it.name },
            "the house only ever picked one kind of Egress",
        )
    }

    // ---- The shared cooldown ------------------------------------------------------------------

    /**
     * **One clock for the whole house** (owner's ruling; the panel's *SHARED WITH THE OTHER
     * INSIDER*).
     *
     * Seat 1 fires. Seat 4 — the other Insider — fires immediately afterwards and the house does
     * nothing, because there is no second Egress to be had. **And seat 4 is answered anyway**, in
     * the same shape seat 1 was: `AbilityFired`, cooldown spent. That is rule 1 — the absence of an
     * answer would tell seat 4 something about a clock they are not entitled to read directly.
     */
    @Test
    fun `one Egress cooldown is shared by every Insider`() {
        val start = armed()
        val walk = EgressWalk(start).feed(
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, EAST)),
        )
        val first = walk.drain()
        assertNotNull(walk.state.egress, "the first fire did not start an Egress")
        assertTrue(first.any { it is Effect.EgressOpened }, "the house did not catch fire")

        walk.feed(
            Event.EgressFired(readyToFire(start) + 1, Seat(4), EgressType.Tether, listOf(MIDDLE, LANDING)),
        )
        val second = walk.drain()
        assertEquals(
            EgressType.Beacon, walk.state.egress?.type,
            "the second Insider's fire replaced the first Insider's Egress",
        )
        assertTrue(
            second.none { it is Effect.EgressOpened },
            "a second Egress opened on top of the first",
        )
        // Rule 1: the shape does not move. Both Insiders were told the same thing.
        assertEquals(
            first.filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            second.filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "the refused fire came back a different shape from the accepted one",
        )
        assertEquals(1, second.filterIsInstance<Effect.AbilityFired>().size)
    }

    /**
     * **Containing one Egress does not hand the Insiders the next one.**
     *
     * The clock is spent at the *fire*, not at the outcome — so a contained Egress leaves the house
     * with the full cooldown still to run. Written because the obvious bug is invisible while an
     * Egress is up: a build that never spends the clock is indistinguishable from this one until
     * the moment containment lands, and then the Residents win a fight and lose the round to a
     * second Egress starting on top of their victory.
     */
    @Test
    fun `containing an Egress does not refund the shared cooldown`() {
        val start = armed()
        val firedAt = readyToFire(start)
        val walk = EgressWalk(start).feed(
            Event.EgressFired(firedAt, Seat(1), EgressType.Beacon, listOf(WEST, EAST)),
        )
        // Both go to the nodes and keep time. The Egress is contained.
        walk.feed(
            Event.MarkerScanned(Tick(firedAt.step + 1), Seat(0), WEST),
            Event.MarkerScanned(Tick(firedAt.step + 1), Seat(2), EAST),
            Event.SyncPulseReturned(Tick(beatAt(firedAt, 3)), Seat(0), onBeat(firedAt)),
            Event.SyncPulseReturned(Tick(beatAt(firedAt, 3)), Seat(2), onBeat(firedAt)),
        )
        assertNull(walk.state.egress, "the fixture did not actually contain the Egress")
        walk.drain()

        // And the very next tick cannot hold another one.
        walk.feed(
            Event.EgressFired(Tick(beatAt(firedAt, 3) + 1), Seat(4), EgressType.Tether,
                listOf(MIDDLE, LANDING)),
        )
        assertNull(
            walk.state.egress,
            "a second Egress started immediately after the first was contained; the shared " +
                "cooldown was never spent",
        )
        assertEquals(
            listOf(Effect.AbilityFired(Seat(4), cooldownStarted = true)), walk.drain(),
            "the refused fire came back a different shape from one the house allowed",
        )
    }

    /**
     * **The round opens with the shared clock already running, at half** (D-132).
     *
     * *The round therefore opens with a guaranteed stretch of peace*, and it closes the
     * opening-Egress problem structurally rather than by asking players not to. Read off arming
     * rather than off a hand-built state, because arming is the only thing that sets it.
     */
    @Test
    fun `the shared Egress cooldown starts the round at half`() {
        val state = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 1L, seats = SEATS, insiders = INSIDERS),
        ).state
        assertEquals(Tick(Balance.EGRESS_COOLDOWN / 2), state.egressReadyAt)

        // And it is enforced: a fire one tick early starts nothing and is answered identically.
        val early = EgressWalk(state).feed(
            Event.EgressFired(Tick(Balance.EGRESS_COOLDOWN / 2 - 1), Seat(1), EgressType.Beacon,
                listOf(WEST, EAST)),
        )
        assertNull(early.state.egress, "an Egress fired inside the opening cooldown")
        assertEquals(
            listOf(Effect.AbilityFired(Seat(1), cooldownStarted = true)),
            early.drain(),
            "a fire the cooldown refused came back a different shape from one it allowed",
        )
    }

    /**
     * **A pair that is secretly one marker never becomes an Egress.**
     *
     * The nodes are drawn above the rules, so this is the one thing the rules can check without
     * holding geography: two distinct markers, or nothing. An Egress at one marker is impossible to
     * contain and indistinguishable from one that is not.
     */
    @Test
    fun `an Egress whose two nodes are one marker does not start`() {
        val start = armed()
        val walk = EgressWalk(start).feed(
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, WEST)),
        )
        assertNull(walk.state.egress, "an uncontainable Egress started")
        assertTrue(walk.drain().none { it is Effect.EgressOpened })
    }

    /**
     * **The alert reaches every phone in the building, naming both nodes and the type** (D-076,
     * D-118, D-135).
     *
     * One per seat, the long haptic on every one of them, and the same two rooms on every one of
     * them — including the Insider who pressed the button. A phone that did not dim in a house
     * where every other phone did is the loudest tell this design has.
     */
    @Test
    fun `the alert names both nodes to everybody including the actor`() {
        val start = armed()
        val opened = EgressWalk(start).feed(
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Tether, listOf(WEST, EAST)),
        ).drain().filterIsInstance<Effect.EgressOpened>()

        assertEquals(SEATS, opened.map { it.seat }, "the Egress alert missed a phone")
        assertEquals(
            listOf(listOf(WEST, EAST)), opened.map { it.nodes }.distinct(),
            "two phones were sent to different places",
        )
        assertEquals(listOf(EgressType.Tether), opened.map { it.type }.distinct())
        assertEquals(listOf(Balance.EGRESS_TIMER), opened.map { it.remaining }.distinct())
        assertEquals(
            listOf(Haptic.Long), opened.map { it.haptic }.distinct(),
            "D-135 names the Egress at the head of the long haptic's closed set of five",
        )
    }

    // ---- The Sync Pulse -----------------------------------------------------------------------

    /**
     * **Two people at two SEPARATE markers, simultaneously** (`gdd.md:352`).
     *
     * The whole geography of the containment is in the word *separate*. Two seats standing at the
     * **same** node, both perfectly on the beat, must not contain it — otherwise the two rooms are
     * decoration and one player with a friend beside them ends every Egress.
     */
    @Test
    fun `a pair at the same node does not contain`() {
        val fired = Tick(60)
        val together = armed()
            .withEgress(Egress.fired(fired, EgressType.Beacon, listOf(WEST, EAST), Balance.EGRESS_TIMER))
            .withPresence(Presence(Seat(0), WEST, open = false))
            .withPresence(Presence(Seat(2), WEST, open = false))

        val at = beatAt(fired, 3)
        val walk = EgressWalk(together).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat(fired)),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat(fired)),
        )
        assertNotNull(walk.state.egress, "two people at ONE marker contained an Egress")
        assertTrue(
            walk.drain().none { it is Effect.EgressContained },
            "containment was announced for a pair standing in the same room",
        )
    }

    /** The same two beats, at two different nodes, contain it. The control for the test above. */
    @Test
    fun `a pair at the two nodes contains it`() {
        val fired = Tick(60)
        val at = beatAt(fired, 3)
        val walk = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat(fired)),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat(fired)),
        )
        assertNull(walk.state.egress, "two people at two nodes did not contain it")
        val effects = walk.drain()
        assertEquals(
            1, effects.filterIsInstance<Effect.EgressContained>().size,
            "containment was announced once, or not at all",
        )
    }

    /**
     * **Nobody learns anything about anybody** (`gdd.md:987`).
     *
     * The pair that contained it get exactly what the rest of the house gets. The only effect
     * addressed to either of them is their own beat answer, which says whether the house held
     * *their* tap and nothing about who else tapped.
     */
    @Test
    fun `containment attributes nothing to the pair that did it`() {
        val fired = Tick(60)
        val at = beatAt(fired, 3)
        val effects = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat(fired)),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat(fired)),
        ).drain()

        // Nothing but the two beat answers and the containment. Any third kind here would be the
        // house saying something extra at the one moment it must say nothing -- and the obvious
        // candidate is an attribution somebody added for the recording's sake.
        val unexpected = effects.filterNot {
            it is Effect.SyncPulseAnswered || it is Effect.EgressContained
        }
        assertEquals(
            emptyList(), unexpected,
            "an effect other than the beat answer and the containment reached a phone",
        )
        // Both participants were answered, and the answers say only what their own tap did.
        assertEquals(
            listOf(Seat(0), Seat(2)),
            effects.filterIsInstance<Effect.SyncPulseAnswered>().map { it.seat },
        )
    }

    /**
     * **A good beat with nobody at the other node is held, and the house says so** (rule 1).
     *
     * `held = true` is what one participant is told while they wait. It says nothing about anybody
     * else — not who is at the other node, not how many are waiting, not whether one is close.
     */
    @Test
    fun `a good beat with no partner is held`() {
        val fired = Tick(60)
        val walk = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(beatAt(fired, 3)), Seat(0), onBeat(fired)),
        )
        assertEquals(
            listOf(Effect.SyncPulseAnswered(Seat(0), held = true)), walk.drain(),
        )
        assertEquals(listOf(Seat(0)), walk.state.egress?.offers?.map { it.seat })
    }

    /**
     * **A missed beat costs a lockout, and every failure has one shape** (`gdd.md:359`, rule 1).
     *
     * Four ways to fail here and all four come back as `SyncPulseAnswered(held = false)`: taps off
     * the schedule, a seat serving the lockout that follows, a seat standing nowhere near a node,
     * and a seat outside the system. Written the tempting way — say nothing unless the beat was
     * good — the *absence* would report the house's opinion of where that player is standing, to a
     * living phone, which the presence plane has no schema row precisely to prevent.
     */
    @Test
    fun `a missed beat costs a lockout and every failure has the same shape`() {
        val fired = Tick(60)
        val start = burning(fired).withPresence(Presence(Seat(3), MIDDLE, open = false))

        // Off the beat, from somebody actually standing at a node.
        val missed = Tick(beatAt(fired, 3) + 1)
        val walk = EgressWalk(start).feed(Event.SyncPulseReturned(missed, Seat(0), offBeat(fired)))
        assertEquals(listOf(Effect.SyncPulseAnswered(Seat(0), held = false)), walk.drain())
        assertEquals(
            listOf(Seat(0)), walk.state.egress?.lockouts?.map { it.seat },
            "a missed beat cost nothing; stalling at a node is free",
        )
        assertEquals(
            listOf(Tick(missed.step + Balance.SYNC_PULSE_LOCKOUT)),
            walk.state.egress?.lockouts?.map { it.until },
        )

        // Perfect taps, one tick into the lockout: still refused, still the same shape, and NOT
        // re-locked -- an extension would turn a mistimed retry into a permanent exclusion.
        val during = Tick(missed.step + 1)
        walk.feed(Event.SyncPulseReturned(during, Seat(0), onBeat(fired, from = 10)))
        assertEquals(listOf(Effect.SyncPulseAnswered(Seat(0), held = false)), walk.drain())
        assertEquals(
            listOf(Tick(missed.step + Balance.SYNC_PULSE_LOCKOUT)),
            walk.state.egress?.lockouts?.map { it.until },
            "a locked-out seat's retry extended its own lockout",
        )
        assertTrue(walk.state.egress?.offers.orEmpty().none { it.seat.index == 0 })

        // Standing at MIDDLE, which is not a node. Same shape, and no lockout: this seat attempted
        // nothing, so there is nothing to price.
        walk.feed(Event.SyncPulseReturned(Tick(beatAt(fired, 20)), Seat(3), onBeat(fired, from = 20)))
        assertEquals(listOf(Effect.SyncPulseAnswered(Seat(3), held = false)), walk.drain())
        assertTrue(walk.state.egress?.lockouts.orEmpty().none { it.seat.index == 3 })

        // Outside the system, standing at a node. Same shape again.
        val out = EgressWalk(start.copy(revoked = listOf(Seat(0))))
            .feed(Event.SyncPulseReturned(Tick(beatAt(fired, 3)), Seat(0), onBeat(fired)))
        assertEquals(listOf(Effect.SyncPulseAnswered(Seat(0), held = false)), out.drain())
        assertTrue(out.state.egress?.offers.orEmpty().isEmpty())
    }

    /**
     * **Four taps inside one beat's window is not keeping time** (`gdd.md:355`).
     *
     * The generous window is there to absorb device skew, not to make the beat optional. A player
     * hammering the screen hits the same beat four times, and that has to fail — otherwise the
     * whole coordination beat is a tap-anywhere button with a delay in front of it.
     */
    @Test
    fun `four taps on one beat is not a Sync Pulse`() {
        val fired = Tick(60)
        val single = beatAt(fired, 3)
        assertFalse(onTheBeat(fired, List(Balance.SYNC_PULSE_BEATS) { single }))
        assertTrue(onTheBeat(fired, onBeat(fired)), "four consecutive beats were graded as a miss")
        assertFalse(onTheBeat(fired, onBeat(fired).drop(1)), "three taps were graded as four")
        assertFalse(onTheBeat(fired, offBeat(fired)), "four taps outside the window were accepted")
    }

    /**
     * **The beat has to be missable, and the window is what decides whether it is.**
     *
     * At half the interval every possible tap is inside *some* beat's window, and the grading stops
     * grading — a player hammering the screen passes, a player who never looked up passes, and the
     * Sync Pulse is a four-tap button with a delay in front of it. **Nothing about that failure is
     * visible**: every test above still passes, every pair still contains, and the whole
     * coordination beat is decoration.
     *
     * The numbers are playtest's, so this is asserted against the constants rather than assumed of
     * them — the person who widens the window to be kind is not reading the KDoc when they do it.
     */
    @Test
    fun `the tap window cannot swallow the whole beat`() {
        assertTrue(
            Balance.syncPulseIsGradeable(),
            "the tap window is at least half the beat interval, so every tap is on some beat and " +
                "nothing can miss",
        )
        // Demonstrated rather than only computed: a tap right between two beats must fail.
        val fired = Tick(60)
        val between = (0 until Balance.SYNC_PULSE_BEATS).map {
            beatAt(fired, it.toLong()) + Balance.SYNC_PULSE_INTERVAL / 2
        }
        assertFalse(onTheBeat(fired, between), "a tap halfway between two beats was graded good")
    }

    // ---- D-133: the pause ---------------------------------------------------------------------

    /**
     * **A report meeting pauses the countdown and never resets it** (D-133).
     *
     * The arithmetic, exactly: fired at 60 with a 120-tick timer, twenty ticks in the party is
     * called and the clock reads 100; ninety ticks of meeting later the lights go out and the clock
     * still reads 100. A reset would read 120 and would make the report a free Egress cancellation,
     * so every Egress would end the same way.
     */
    @Test
    fun `the timer pauses across a report meeting and resumes exactly where it stopped`() {
        val fired = Tick(60)
        val called = Tick(80)
        val closed = Tick(170)
        val start = burning(fired)

        val walk = EgressWalk(start).feed(
            Event.MeetingCalled(called, Seat(0), MeetingTrigger.RevokeReported(Seat(5))),
        )
        val paused = walk.state.egress
        assertNotNull(paused)
        assertEquals(called, paused.pausedAt)
        assertEquals(Balance.EGRESS_TIMER - 20L, paused.remainingAt(called))
        assertEquals(
            listOf(Effect.EgressHeld(Balance.EGRESS_TIMER - 20L, running = false,
                haptic = Haptic.Short)),
            walk.drain().filterIsInstance<Effect.EgressHeld>(),
        )

        // Frozen: asking later in the meeting gives the same number.
        assertEquals(Balance.EGRESS_TIMER - 20L, paused.remainingAt(Tick(160)))

        // Walk the meeting to its end and let the lights go out.
        walk.feed(*SEATS.map { Event.MeetingCheckedIn(Tick(85), it) }.toTypedArray())
        walk.feed(*SEATS.map { Event.ReadyToVoteDeclared(Tick(90), it) }.toTypedArray())
        walk.feed(*SEATS.map { Event.VoteLocked(Tick(95), it) }.toTypedArray())
        assertEquals(MeetingPhase.Tally, walk.state.meeting?.phase)
        walk.drain()
        walk.feed(Event.MeetingClosed(closed))

        val resumed = walk.state.egress
        assertNotNull(resumed, "the meeting cancelled the Egress")
        assertNull(resumed.pausedAt)
        assertEquals(
            Balance.EGRESS_TIMER - 20L, resumed.remainingAt(closed),
            "the countdown did not pick up exactly where it stopped",
        )
        assertEquals(
            Tick(fired.step + Balance.EGRESS_TIMER + (closed.step - called.step)),
            resumed.deadline,
            "the deadline moved by something other than the length of the meeting",
        )
        assertEquals(
            listOf(Effect.EgressHeld(Balance.EGRESS_TIMER - 20L, running = true,
                haptic = Haptic.Short)),
            walk.drain().filterIsInstance<Effect.EgressHeld>(),
        )
    }

    /**
     * **The card is inert and the report is not, with a real Egress underneath it** (D-133, D-121).
     *
     * Both directions, because either alone passes on a build that refuses everything or on one
     * that refuses nothing. `MeetingTest` asserts the same rule against a hand-built Egress; this
     * asserts it against one the rules actually started, which is the version that can rot.
     */
    @Test
    fun `a called meeting is refused during an Egress while the report still works`() {
        val start = armed()
        val walk = EgressWalk(start).feed(
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, EAST)),
        )
        walk.drain()

        walk.feed(Event.MeetingCalled(Tick(70), Seat(0), MeetingTrigger.MeetingCard))
        assertEquals(listOf(RefusalReason.EgressRunning), walk.refusals)
        assertNull(walk.state.meeting, "a meeting started while the house was on fire")

        walk.feed(Event.MeetingCalled(Tick(71), Seat(0), MeetingTrigger.RevokeReported(Seat(5))))
        assertEquals(1, walk.refusals.size, "D-121's one exception was refused")
        assertNotNull(walk.state.meeting)
    }

    /**
     * **A beat during a paused Egress is refused above the rules, and recorded.**
     *
     * Everybody is standing in the meeting room; nobody is at a node. The refusal names no seat and
     * is exactly as public as the meeting it is about, which is what lets it sit in the gate rather
     * than inside a rule.
     */
    @Test
    fun `an Egress event is refused when there is no Egress or it is held`() {
        val quiet = EgressWalk(armed()).feed(
            Event.SyncPulseReturned(Tick(60), Seat(0), onBeat(Tick(60))),
            Event.EgressExpired(Tick(61)),
        )
        assertEquals(
            listOf(RefusalReason.EgressNotRunning, RefusalReason.EgressNotRunning),
            quiet.refusals,
        )
        assertEquals(emptyList(), quiet.drain(), "a refused event emitted something")

        val fired = Tick(60)
        val held = EgressWalk(burning(fired)).feed(
            Event.MeetingCalled(Tick(80), Seat(0), MeetingTrigger.RevokeReported(Seat(5))),
        )
        held.drain()
        held.feed(Event.SyncPulseReturned(Tick(90), Seat(0), onBeat(fired, from = 30)))
        assertEquals(listOf(RefusalReason.EgressNotRunning), held.refusals)
        assertEquals(emptyList(), held.drain())
    }

    // ---- The outcomes -------------------------------------------------------------------------

    /**
     * **Uncontained: the terminal fact, to everybody** (`gdd.md:361`, D-131).
     *
     * And the countdown stops being on anybody's widget. The round is deliberately **not** ended
     * here — the win conditions as a set are the ending unit's — so what is asserted is the fact,
     * its audience, and the state it leaves behind.
     */
    @Test
    fun `expiry emits the terminal fact`() {
        val fired = Tick(60)
        val walk = EgressWalk(burning(fired)).feed(Event.EgressExpired(Tick(fired.step + Balance.EGRESS_TIMER)))
        assertEquals(
            listOf(Effect.EgressSucceeded(Haptic.Long)), walk.drain(),
        )
        assertNull(walk.state.egress, "the countdown outlived its own expiry")
        assertEquals(emptyList(), walk.refusals)
    }

    /**
     * **A running Egress outlives its Insiders** (D-131).
     *
     * *Restraining the last Insider during an Egress does not end the round: the house does not
     * stop what it was told to start.* Both Insiders are restrained here, mid-Egress, and the
     * countdown is still running afterwards — which is a rule expressed as an absence, so it is
     * asserted rather than trusted.
     */
    @Test
    fun `restraining every Insider does not stop a running Egress`() {
        val fired = Tick(60)
        val start = burning(fired).copy(restrained = INSIDERS)
        assertNotNull(start.egress, "the fixture is stale")

        val walk = EgressWalk(start).feed(Event.EgressExpired(Tick(fired.step + Balance.EGRESS_TIMER)))
        assertEquals(
            listOf(Effect.EgressSucceeded(Haptic.Long)), walk.drain(),
            "an Egress with no Insiders left to win it was quietly cancelled",
        )
    }

    /**
     * **A held offer goes stale**, so two people who were never in the house at the same moment
     * cannot pair with each other.
     *
     * The pulse is simultaneous by design. An offer that stood for the whole Egress would let
     * somebody who tapped at the Utility and walked away be paired with a stranger arriving at the
     * Landing a minute later, which is the opposite of what the two nodes are for.
     */
    @Test
    fun `an offer older than the pair window cannot contain`() {
        val fired = Tick(60)
        val walk = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(beatAt(fired, 3)), Seat(0), onBeat(fired)),
        )
        walk.drain()

        val late = beatAt(fired, 3) + Balance.SYNC_PULSE_PAIR_WINDOW + 1
        walk.feed(Event.SyncPulseReturned(Tick(late), Seat(2), onBeat(fired, from = late)))
        assertNotNull(walk.state.egress, "a stale offer contained an Egress")
        assertEquals(
            listOf(Effect.SyncPulseAnswered(Seat(2), held = true)), walk.drain(),
            "the late arrival was not held for a partner of its own",
        )
    }
}
