package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.CardPayload
import home.someoneshome.model.CellRect
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
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
import home.someoneshome.model.Outcome
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Presence
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
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

/** An ability's answer as the rules build it (D-263): the actor's own, and a blank for every other seat, seat by seat. */
private fun answeredTo(actor: Seat, cooldownStarted: Boolean): List<Effect> =
    SEATS.map { if (it == actor) Effect.AbilityFired(actor, cooldownStarted) else Effect.AbilityBlanked(it) }
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
 * **Where the four cards are** — the same rows `Rules.armed` carries across from the home.
 *
 * The fixture built rounds with active markers and no destinations, which was invisible while the
 * Egress alert carried bare card ids and is not any more: the alert **names** the two nodes, so a
 * round whose cards resolve to no room sends the whole house to two places with no names on them.
 * Read off [map] rather than typed, so the rooms here and the registrations above cannot disagree.
 */
private fun where(): List<Destination> = map().registrations.map {
    Destination(it.card.id, it.card.shape.id, it.room.name)
}

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
    destinations = where(),
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

/**
 * **The pulse grid's anchor in these tests: the host's monotonic millisecond of pulse 0** (C2).
 *
 * Deliberately nothing like any tick in this file, and half a pulse off the grid a tick-reading
 * grader would build from the ticks here (60 + a lead of 40 is 100, and 7 340 350 − 100 is 750 past a
 * multiple of 1 500), so a grader that read the tick the Egress fired
 * at instead of this would grade every tap here as a miss.
 */
private const val PULSE_FROM = 7_340_350L

/** The anchor `egressFor` is handed: the host's clock at the draw, a lead before pulse 0. */
private const val ANCHOR = PULSE_FROM - Balance.SYNC_PULSE_LEAD_MILLIS

/**
 * The house TICK that pulse [n] lands on, had the house's ticks kept wall time. Only for stamping
 * when an event arrives; taps are never ticks any more (C2).
 */
private fun pulseTick(firedAt: Tick, n: Long): Long =
    firedAt.step + Balance.SYNC_PULSE_LEAD + n * Balance.SYNC_PULSE_INTERVAL

/** Four taps landing exactly on four consecutive pulses of the house schedule, in host ms. */
private fun onBeat(from: Long = 0L): List<Long> =
    (0 until Balance.SYNC_PULSE_BEATS).map { pulseAt(PULSE_FROM, from + it) }

/**
 * Four taps a hair outside the window, on four consecutive pulses.
 *
 * One millisecond past generous, which is the honest failure to write: a build with no grading at
 * all and a build whose window is a whole pulse wide both pass a test that taps halfway between.
 */
private fun offBeat(from: Long = 0L): List<Long> =
    onBeat(from).map { it + Balance.SYNC_PULSE_WINDOW_MILLIS + 1 }

/** What a return answers: the tapper's own answer, and every other seat's blank, seat by seat. */
private fun answered(seat: Seat, held: Boolean): List<Effect> =
    SEATS.map { if (it == seat) Effect.SyncPulseAnswered(seat, held) else Effect.SyncPulseBlanked(it) }

/** A round already on fire, with both nodes named and two seats standing at them. */
private fun burning(
    at: Tick = Tick(60),
    nodes: List<MarkerId> = listOf(WEST, EAST),
): GameState = armed()
    .withEgress(Egress.fired(at, EgressType.Beacon, nodes, Balance.EGRESS_TIMER, pulseFrom = PULSE_FROM))
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
            val fired = egressFor(Tick(at), Seat(1), strip, map(), plan(), anchorMillis = ANCHOR)
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
        val fired = egressFor(Tick(60), Seat(1), small, map(), plan(), anchorMillis = ANCHOR)
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
        assertNull(egressFor(Tick(60), Seat(1), bare, map(), plan(), anchorMillis = ANCHOR))
        assertNull(egressFor(Tick(60), Seat(1), armed().copy(activeMarkers = emptyList()), map(), plan(), anchorMillis = ANCHOR))
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
        val a = egressFor(Tick(60), Seat(1), armed(), map(), plan(), anchorMillis = ANCHOR)
        val b = egressFor(Tick(60), Seat(1), armed(), map(), plan(), anchorMillis = ANCHOR)
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
            egressFor(Tick(it), Seat(1), armed(), map(), plan(), anchorMillis = ANCHOR)?.type
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
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        )
        val first = walk.drain()
        assertNotNull(walk.state.egress, "the first fire did not start an Egress")
        assertTrue(first.any { it is Effect.EgressOpened }, "the house did not catch fire")

        walk.feed(
            Event.EgressFired(readyToFire(start) + 1, Seat(4), EgressType.Tether, listOf(MIDDLE, LANDING), pulseFrom = PULSE_FROM),
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
        // **Rule 1: the SHAPE does not move.** One `AbilityFired`, addressed to the actor, and
        // nothing else — whichever Insider fired and whatever the house did about it. That is the
        // property, and it is asserted on the shape rather than on the flag.
        assertEquals(
            first.filterIsInstance<Effect.AbilityFired>().map { it.actor }.size,
            second.filterIsInstance<Effect.AbilityFired>().map { it.actor }.size,
            "the refused fire came back a different shape from the accepted one",
        )
        assertEquals(1, second.filterIsInstance<Effect.AbilityFired>().size)

        // **And the flag tells the truth, which it did not until T1·8 was closed.** It hardcoded
        // `true`, so this second Insider was told they had spent the house's longest cooldown on a
        // press that spent nothing, and their own bar was wrong for three minutes. It is not a leak
        // and never was: the row permits this kind to `Insider/Live`, the effect is addressed to
        // the actor, and what it says is *your own clock* — input echo, which is exactly why it is
        // allowed to differ where the shape is not.
        assertEquals(
            listOf(true), first.filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "the fire that started the Egress did not report its own cooldown spent",
        )
        assertEquals(
            listOf(false), second.filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "a fire into the shared cooldown told that phone it had spent one it did not (T1·8)",
        )
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
            Event.EgressFired(firedAt, Seat(1), EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        )
        // Both go to the nodes and keep time. The Egress is contained.
        walk.feed(
            Event.MarkerScanned(Tick(firedAt.step + 1), Seat(0), WEST),
            Event.MarkerScanned(Tick(firedAt.step + 1), Seat(2), EAST),
            Event.SyncPulseReturned(Tick(pulseTick(firedAt, 3)), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(pulseTick(firedAt, 3)), Seat(2), onBeat()),
        )
        assertNull(walk.state.egress, "the fixture did not actually contain the Egress")
        walk.drain()

        // And the very next tick cannot hold another one.
        walk.feed(
            Event.EgressFired(Tick(pulseTick(firedAt, 3) + 1), Seat(4), EgressType.Tether,
                listOf(MIDDLE, LANDING), pulseFrom = PULSE_FROM),
        )
        assertNull(
            walk.state.egress,
            "a second Egress started immediately after the first was contained; the shared " +
                "cooldown was never spent",
        )
        // One `AbilityFired` and nothing else — the same shape a fire that caught alight produces.
        // The flag is `false` because the shared cooldown really was still running: T1·8 closed,
        // and it is the actor's own clock rather than a report on the house.
        assertEquals(
            answeredTo(Seat(4), cooldownStarted = false), walk.drain(),
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
                listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        )
        assertNull(early.state.egress, "an Egress fired inside the opening cooldown")
        assertEquals(
            answeredTo(Seat(1), cooldownStarted = false),
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
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, WEST), pulseFrom = PULSE_FROM),
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
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Tether, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        ).drain().filterIsInstance<Effect.EgressOpened>()

        assertEquals(SEATS, opened.map { it.seat }, "the Egress alert missed a phone")
        assertEquals(
            // **The CARDS are compared and the rooms ride with them.** The effect carries
            // destinations now, so the pair is asserted through the ids the draw actually made —
            // and `distinct()` is still what proves every phone was sent the same two.
            listOf(listOf(WEST, EAST)), opened.map { it.nodes.map { node -> node.marker } }.distinct(),
            "two phones were sent to different places",
        )
        assertEquals(listOf(EgressType.Tether), opened.map { it.type }.distinct())
        assertEquals(listOf(Balance.EGRESS_TIMER), opened.map { it.remaining }.distinct())
        assertEquals(
            listOf(Haptic.Long), opened.map { it.haptic }.distinct(),
            "D-135 names the Egress at the head of the long haptic's closed set of five",
        )
        // **The rooms are on it, which is what NAMES the two nodes** (`gdd.md:374`, E-R2-3). The
        // widget takeover says where to stand and a nine-character card id says nothing to a person
        // in a dark house — so a pair that resolved to no destination would be an alert that reached
        // every phone and told none of them anything. `mapNotNull` makes that failure a SHORT pair
        // rather than a blank one, so this asserts the length as well as the words.
        assertEquals(
            listOf(listOf("WEST", "EAST")), opened.map { it.nodes.map { node -> node.room } }.distinct(),
            "the alert reached every phone and named nowhere",
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
            .withEgress(Egress.fired(fired, EgressType.Beacon, listOf(WEST, EAST), Balance.EGRESS_TIMER, pulseFrom = PULSE_FROM))
            .withPresence(Presence(Seat(0), WEST, open = false))
            .withPresence(Presence(Seat(2), WEST, open = false))

        val at = pulseTick(fired, 3)
        val walk = EgressWalk(together).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat()),
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
        val at = pulseTick(fired, 3)
        val walk = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat()),
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
        val at = pulseTick(fired, 3)
        val effects = EgressWalk(burning(fired)).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat()),
        ).drain()

        // Nothing but the two beat answers and the containment. Any third kind here would be the
        // house saying something extra at the one moment it must say nothing -- and the obvious
        // candidate is an attribution somebody added for the recording's sake.
        //
        // `IsolateTargetsOffered` is the one third kind that is lawful here and it is named rather
        // than swept in: D-150 excludes the two nodes from Isolate's list *for the duration of an
        // Egress*, and this is the duration ending, so the list has to come back. **It says nothing
        // about who contained it** — it is the round's active markers, addressed to Insiders, and it
        // would be identical if the Egress had expired instead.
        val unexpected = effects.filterNot {
            it is Effect.SyncPulseAnswered ||
                // Every other seat's blank of the answer (C2): it carries nothing at all.
                it is Effect.SyncPulseBlanked ||
                it is Effect.EgressContained ||
                it is Effect.IsolateTargetsOffered ||
                // Its other half, on a Resident's phone (D-258): one frame each, and it carries
                // nothing at all, so it says nothing about who contained it either.
                it is Effect.IsolateTargetsBlanked
        }
        assertEquals(
            emptyList(), unexpected,
            "an effect other than the beat answer, its blanks and the containment reached a phone",
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
            Event.SyncPulseReturned(Tick(pulseTick(fired, 3)), Seat(0), onBeat()),
        )
        assertEquals(
            answered(Seat(0), held = true), walk.drain(),
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
        val missed = Tick(pulseTick(fired, 3) + 1)
        val walk = EgressWalk(start).feed(Event.SyncPulseReturned(missed, Seat(0), offBeat()))
        assertEquals(answered(Seat(0), held = false), walk.drain())
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
        walk.feed(Event.SyncPulseReturned(during, Seat(0), onBeat(from = 10)))
        assertEquals(answered(Seat(0), held = false), walk.drain())
        assertEquals(
            listOf(Tick(missed.step + Balance.SYNC_PULSE_LOCKOUT)),
            walk.state.egress?.lockouts?.map { it.until },
            "a locked-out seat's retry extended its own lockout",
        )
        assertTrue(walk.state.egress?.offers.orEmpty().none { it.seat.index == 0 })

        // Standing at MIDDLE, which is not a node. Same shape, and no lockout: this seat attempted
        // nothing, so there is nothing to price.
        walk.feed(Event.SyncPulseReturned(Tick(pulseTick(fired, 20)), Seat(3), onBeat(from = 20)))
        assertEquals(answered(Seat(3), held = false), walk.drain())
        assertTrue(walk.state.egress?.lockouts.orEmpty().none { it.seat.index == 3 })

        // Outside the system, standing at a node. Same shape again.
        val out = EgressWalk(start.copy(revoked = listOf(Seat(0))))
            .feed(Event.SyncPulseReturned(Tick(pulseTick(fired, 3)), Seat(0), onBeat()))
        assertEquals(answered(Seat(0), held = false), out.drain())
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
        val single = pulseAt(PULSE_FROM, 3)
        assertFalse(onThePulse(PULSE_FROM, List(Balance.SYNC_PULSE_BEATS) { single }))
        assertTrue(onThePulse(PULSE_FROM, onBeat()), "four consecutive beats were graded as a miss")
        assertFalse(onThePulse(PULSE_FROM, onBeat().drop(1)), "three taps were graded as four")
        assertFalse(onThePulse(PULSE_FROM, offBeat()), "four taps outside the window were accepted")
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
            pulseAt(PULSE_FROM, it.toLong()) + Balance.SYNC_PULSE_INTERVAL_MILLIS / 2
        }
        assertFalse(onThePulse(PULSE_FROM, between), "a tap halfway between two beats was graded good")
        assertTrue(Balance.syncPulseIsGradeableInMillis(), "the millisecond window swallows the whole pulse")
        assertEquals(
            Balance.SYNC_PULSE_WINDOW * 1_000 / Balance.TICKS_PER_SECOND, Balance.SYNC_PULSE_WINDOW_MILLIS,
            "the millisecond window is not the tick window",
        )
    }

    // ---- C2: the pulse on the host's clock ---------------------------------------------------

    /**
     * **The draw puts the first pulse a lead after the host's clock, and every phone is told it**
     * (C2). The anchor is handed in; the event carries the result; `EgressOpened` carries it to
     * every seat, the same number on each.
     */
    @Test
    fun `the pulse grid is anchored on the host clock and named to every seat`() {
        val anchor = 3_000_017L
        val start = armed()
        val fired = assertNotNull(egressFor(readyToFire(start), Seat(1), start, map(), plan(), anchorMillis = anchor))
        assertEquals(anchor + Balance.SYNC_PULSE_LEAD_MILLIS, fired.pulseFrom, "pulse 0 is not a lead after the draw")
        assertEquals(2_000L, Balance.SYNC_PULSE_LEAD_MILLIS)
        assertEquals(1_500L, Balance.SYNC_PULSE_INTERVAL_MILLIS)
        assertEquals(400L, Balance.SYNC_PULSE_WINDOW_MILLIS)

        val walk = EgressWalk(start).feed(fired)
        assertEquals(fired.pulseFrom, walk.state.egress?.pulseFrom)
        val opened = walk.drain().filterIsInstance<Effect.EgressOpened>()
        assertEquals(SEATS, opened.map { it.seat }, "EgressOpened did not reach every seat once")
        assertEquals(setOf(fired.pulseFrom), opened.map { it.pulseFrom }.toSet(), "the seats were told different grids")
    }

    /**
     * **The grid is the host clock's and not the house's ticks** (C2). The same taps, on the same
     * host-clock grid, are graded the same for an Egress that fired at tick 60 and one that fired at
     * tick 60 000 — the house's ticks drift from wall time, and a phone has no way to know them —
     * and taps on the old tick grid miss. **Whatever tick each return arrives at.**
     */
    @Test
    fun `taps are graded on the host clock whatever tick the house is at`() {
        for (firedAt in listOf(Tick(60), Tick(60_000))) {
            for (arrival in listOf(0L, 7L, 90L)) {
                val at = Tick(pulseTick(firedAt, 3) + arrival)
                val walk = EgressWalk(burning(firedAt)).feed(
                    Event.SyncPulseReturned(at, Seat(0), onBeat()),
                    Event.SyncPulseReturned(at, Seat(2), onBeat()),
                )
                assertNull(walk.state.egress, "fired at $firedAt, arriving +$arrival: the pair on the host grid did not contain")
            }
            val onTicks = (0 until Balance.SYNC_PULSE_BEATS).map { pulseTick(firedAt, it.toLong()) }
            assertFalse(onThePulse(PULSE_FROM, onTicks), "fired at $firedAt: taps on the house's ticks were graded good")
        }
    }

    /** **A pause does not move the grid** (C2, D-133): the countdown stops, the pulse does not. */
    @Test
    fun `a pause does not move the pulse grid`() {
        val fired = Tick(60)
        val before = burning(fired)
        val egress = assertNotNull(before.egress)
        val resumed = egress.pausedAt(Tick(400)).resumedAt(Tick(4_000))
        assertEquals(egress.pulseFrom, resumed.pulseFrom, "the meeting moved the pulse grid")
        assertTrue(resumed.deadline.step > egress.deadline.step, "the fixture's pause moved nothing")
        val at = Tick(4_100)
        val walk = EgressWalk(before.withEgress(resumed)).feed(
            Event.SyncPulseReturned(at, Seat(0), onBeat(from = 40)),
            Event.SyncPulseReturned(at, Seat(2), onBeat(from = 40)),
        )
        assertNull(walk.state.egress, "after the pause, taps on the unmoved grid did not contain")
    }

    /**
     * **Every living seat gets one pulse frame of one length whenever a return is answered** (C2;
     * D-263's pattern). Through the gate, the allowlist and the real encoders, with seat 3 Revoked:
     * a held beat, a missed one, a return from nowhere near a node, an Insider's held beat, and the
     * return that contains. Whatever the outcome and whoever tapped, every living phone is sent one
     * `SyncPulseAnswered` or `SyncPulseBlanked`, and they are all one length.
     */
    @Test
    fun `every living seat gets one pulse frame of one length whenever a return is answered`() {
        fun pulseFrames(effects: List<Effect>, state: GameState): Map<Int, List<Int>> = effects
            .filter { it is Effect.SyncPulseAnswered || it is Effect.SyncPulseBlanked }
            .flatMap { effect ->
                EmitSchema.deliveries(effect, state).map { delivery ->
                    val push = RoundWire.encode(RoundBody.Push(1, RoundWire.project(effect)))
                    delivery.seat.index to TransportWire.encode(TransportFrame.Carry(push)).length
                }
            }
            .groupBy({ it.first }, { it.second })

        val living = (SEATS.map { it.index } - 3).toSet()
        val fired = Tick(60)
        val at = Tick(pulseTick(fired, 3))
        val start = burning(fired)
            .withPresence(Presence(Seat(1), WEST, open = false))
            .withPresence(Presence(Seat(5), MIDDLE, open = false))
            .copy(revoked = listOf(Seat(3)))
        val moments = listOf(
            "a Resident's held beat" to listOf(Event.SyncPulseReturned(at, Seat(0), onBeat())),
            "a Resident's missed beat" to listOf(Event.SyncPulseReturned(at, Seat(0), offBeat())),
            "a return from no node" to listOf(Event.SyncPulseReturned(at, Seat(5), onBeat())),
            "an Insider's held beat" to listOf(Event.SyncPulseReturned(at, Seat(1), onBeat())),
            "an Insider's missed beat" to listOf(Event.SyncPulseReturned(at, Seat(1), offBeat())),
        )
        val lengths = mutableSetOf<Int>()
        for ((moment, events) in moments) {
            val walk = EgressWalk(start).feed(*events.toTypedArray())
            assertTrue(walk.refusals.isEmpty(), "$moment was refused, so this measured nothing: ${walk.refusals}")
            val frames = pulseFrames(walk.drain(), walk.state)
            assertEquals(living, frames.keys, "$moment: the pulse frames reached another set of seats: $frames")
            for ((seat, got) in frames) assertEquals(1, got.size, "$moment: seat $seat got ${got.size} pulse frames")
            lengths += frames.values.flatten()
        }
        // And the return that contains: the second of a pair, after the first was held.
        val pair = EgressWalk(start).feed(Event.SyncPulseReturned(at, Seat(1), onBeat()))
        pair.drain()
        pair.feed(Event.SyncPulseReturned(at, Seat(2), onBeat()))
        assertNull(pair.state.egress, "an Insider and a Resident at the two nodes did not contain")
        val containing = pulseFrames(pair.drain(), pair.state)
        assertEquals(living, containing.keys, "containment: the pulse frames reached another set of seats: $containing")
        lengths += containing.values.flatten()
        assertEquals(1, lengths.size, "the pulse frame's length follows the outcome or the seat: $lengths")
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
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
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
     * **An Egress pressed into a meeting is refused, and the shared cooldown is not spent**
     * (`gdd.md:386`, *blocked during meetings*; review r4 F1, D-279).
     *
     * The honest race: the press rides the next upload beat and the meeting is admitted first.
     * Admitted, it started an Egress with no pause on it -- D-133's pause is applied by the call,
     * which had already happened -- and its countdown ran through the meeting and could expire
     * there, with the whole house in one room.
     */
    @Test
    fun `an Egress fired during a meeting is refused and starts nothing`() {
        val start = armed()
        val walk = EgressWalk(start).feed(
            Event.MeetingCalled(readyToFire(start), Seat(0), MeetingTrigger.MeetingCard),
        )
        assertNotNull(walk.state.meeting, "the fixture called no meeting")
        walk.drain()

        walk.feed(
            Event.EgressFired(readyToFire(start) + 1, Seat(1), EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        )
        assertEquals(
            listOf(RefusalReason.MeetingAlreadyRunning), walk.refusals,
            "an Egress was admitted during a meeting",
        )
        assertNull(walk.state.egress, "an Egress started in the middle of a meeting")
        assertEquals(emptyList(), walk.drain(), "a refused fire emitted something")
        assertEquals(
            start.egressReadyAt, walk.state.egressReadyAt,
            "a refused fire spent the shared cooldown",
        )
    }

    /**
     * **The second wall: nothing expires and nobody pulses while a meeting runs, even against an
     * Egress with no pause mark on it** (D-133; review r4 F1, D-279).
     *
     * Hand-built, because the branch above makes it unreachable through the rules -- which is the
     * point of a second wall. Before it, `HouseClock` minting the expiry against an unpaused Egress
     * inside a meeting was admitted, and the Insiders won in the middle of the vote.
     */
    @Test
    fun `an Egress event during a meeting is refused even against an unpaused Egress`() {
        val start = armed()
        val walk = EgressWalk(start).feed(
            Event.MeetingCalled(Tick(60), Seat(0), MeetingTrigger.MeetingCard),
        )
        assertNotNull(walk.state.meeting, "the fixture called no meeting")
        val slipped = Egress.fired(Tick(61), EgressType.Beacon, listOf(WEST, EAST), Balance.EGRESS_TIMER, pulseFrom = PULSE_FROM)
        val inside = EgressWalk(
            walk.state.withEgress(slipped)
                .withPresence(Presence(Seat(0), WEST, open = false))
                .withPresence(Presence(Seat(2), EAST, open = false)),
        )

        inside.feed(
            Event.SyncPulseReturned(Tick(70), Seat(0), onBeat()),
            Event.EgressExpired(Tick(61 + Balance.EGRESS_TIMER)),
        )
        assertEquals(
            listOf(RefusalReason.EgressNotRunning, RefusalReason.EgressNotRunning), inside.refusals,
            "an Egress event was admitted in the middle of a meeting",
        )
        assertFalse(inside.state.ended, "an Egress expired in the middle of a meeting and ended the round")
        assertEquals(emptyList(), inside.drain(), "a refused Egress event emitted something")
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
            Event.SyncPulseReturned(Tick(60), Seat(0), onBeat()),
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
        held.feed(Event.SyncPulseReturned(Tick(90), Seat(0), onBeat(from = 30)))
        assertEquals(listOf(RefusalReason.EgressNotRunning), held.refusals)
        assertEquals(emptyList(), held.drain())
    }

    // ---- The outcomes -------------------------------------------------------------------------

    /**
     * **Uncontained: the terminal fact, and it takes the round with it** (`gdd.md:361`, D-131).
     *
     * The countdown stops being on anybody's widget **and** the round ends, in that order, in one
     * reduction. Two effects rather than one because they are two disclosures: the Egress is over,
     * and the evening is over. The fact is asserted first because everything downstream of it
     * depends on the Egress having actually been cleared.
     */
    @Test
    fun `expiry emits the terminal fact`() {
        val fired = Tick(60)
        val walk = EgressWalk(burning(fired)).feed(Event.EgressExpired(Tick(fired.step + Balance.EGRESS_TIMER)))
        val emitted = walk.drain()

        assertEquals(Effect.EgressSucceeded(Haptic.Long), emitted.first())
        assertNull(walk.state.egress, "the countdown outlived its own expiry")
        assertEquals(emptyList(), walk.refusals)

        assertEquals(
            Outcome(Winner.Insiders, WinRoute.EgressUncontained), walk.state.outcome,
            "a countdown reached zero with the doors open and the round carried on",
        )
        assertEquals(
            SEATS.map { Effect.RoundEnded(it, Winner.Insiders, WinRoute.EgressUncontained, Haptic.Short) },
            emitted.filterIsInstance<Effect.RoundEnded>(),
            "the ending has to reach every seat, once each",
        )
    }

    /**
     * **A running Egress outlives its Insiders, and then wins the round for them** (D-131).
     *
     * *Restraining the last Insider during an Egress does not end the round: the house does not
     * stop what it was told to start.* Both Insiders are restrained here, mid-Egress. Two things
     * are asserted and the first is the one written as an absence: the round is **still running**
     * after the Restrain, even though `WinRoute.InsidersRestrained` is otherwise satisfied — and
     * then the expiry ends it in the Insiders' favour, from a state in which not one of them is
     * left to enjoy it.
     */
    @Test
    fun `restraining every Insider does not stop a running Egress`() {
        val fired = Tick(60)
        val start = burning(fired).copy(restrained = INSIDERS)
        assertNotNull(start.egress, "the fixture is stale")
        assertTrue(start.livingInsiders.isEmpty(), "the fixture is stale")

        val walk = EgressWalk(start).feed(Event.EgressExpired(Tick(fired.step + Balance.EGRESS_TIMER)))
        assertEquals(
            Effect.EgressSucceeded(Haptic.Long), walk.drain().first(),
            "an Egress with no Insiders left to win it was quietly cancelled",
        )
        assertEquals(
            Outcome(Winner.Insiders, WinRoute.EgressUncontained), walk.state.outcome,
            "the house stopped what it was told to start",
        )
    }

    /**
     * **The other half of the same rule: containment wins it for the Residents.**
     *
     * With no Insider left in the round, `WinRoute.InsidersRestrained` is held back by exactly one
     * clause — the running Egress — and the instant the pair contains it, that clause stops
     * holding and the round ends. Nothing new fires; the same predicate that declined on the
     * previous transition answers on this one.
     *
     * This is the pair to `restraining every Insider does not stop a running Egress` and neither
     * is worth much alone: together they say the Egress *decides* a round its own Insiders are no
     * longer in, either way.
     */
    @Test
    fun `containment ends a round whose Insiders are all Restrained`() {
        val fired = Tick(60)
        val start = burning(fired).copy(restrained = INSIDERS)

        val walk = EgressWalk(start)
        walk.feed(Event.SyncPulseReturned(Tick(pulseTick(fired, 3)), Seat(0), onBeat()))
        assertNull(walk.state.outcome, "a held offer ended the round on its own")

        walk.drain()
        walk.feed(Event.SyncPulseReturned(Tick(pulseTick(fired, 3)), Seat(2), onBeat()))
        val emitted = walk.drain()

        assertTrue(
            emitted.any { it is Effect.EgressContained },
            "the pair did not contain; the rest of this test proves nothing",
        )
        assertEquals(
            Outcome(Winner.Residents, WinRoute.InsidersRestrained), walk.state.outcome,
            "the house was no longer on fire and nobody was left to have set it, and the round ran on",
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
            Event.SyncPulseReturned(Tick(pulseTick(fired, 3)), Seat(0), onBeat()),
        )
        walk.drain()

        val late = pulseTick(fired, 3) + Balance.SYNC_PULSE_PAIR_WINDOW + 1
        walk.feed(Event.SyncPulseReturned(Tick(late), Seat(2), onBeat(from = late)))
        assertNotNull(walk.state.egress, "a stale offer contained an Egress")
        assertEquals(
            answered(Seat(2), held = true), walk.drain(),
            "the late arrival was not held for a partner of its own",
        )
    }

    /**
     * **A held offer from a seat that has since gone out does not pair, and a live one behind it
     * still does** (`containment-spec-2026-11-17.md`, *a Revoked partner's held offer no longer
     * pairs*; review r4 F2, D-279).
     *
     * Seat 0 taps at WEST and is Revoked inside the pair window. Seat 2's beat at EAST must not
     * pair with it -- one living player and one out is not *two people at two separate markers*.
     * Then the same again with seat 3 also holding at WEST, behind seat 0: seat 2's beat pairs with
     * seat 3, because the out offer is skipped rather than taken as the end of the search, so the
     * first LIVE match in arrival order is the one that wins.
     */
    @Test
    fun `a held offer from a seat that has gone out does not pair`() {
        val fired = Tick(60)
        val at = pulseTick(fired, 3)

        // Alone at WEST, then Revoked.
        val alone = EgressWalk(burning(fired)).feed(Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()))
        val lone = EgressWalk(alone.state.copy(revoked = listOf(Seat(0))))
            .feed(Event.SyncPulseReturned(Tick(at + 1), Seat(2), onBeat()))
        assertNotNull(lone.state.egress, "an out seat's held offer contained the Egress")
        assertEquals(
            answered(Seat(2), held = true), lone.drain(),
            "the living tapper was not held for a living partner",
        )

        // A live offer behind the out one, at the same node, in arrival order.
        val two = EgressWalk(burning(fired).withPresence(Presence(Seat(3), WEST, open = false))).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(at), Seat(3), onBeat()),
        )
        val behind = two.state.copy(revoked = listOf(Seat(0)))
        val held = assertNotNull(behind.egress)
        assertEquals(
            listOf(Seat(0), Seat(3)), held.offers.map { it.seat },
            "the fixture did not hold the out offer ahead of the live one",
        )
        assertEquals(
            Seat(3),
            held.partnerFor(Seat(2), EAST, Tick(at + 1), Balance.SYNC_PULSE_PAIR_WINDOW, behind::isOut)?.seat,
            "the first live match in arrival order did not win",
        )
        val paired = EgressWalk(behind).feed(Event.SyncPulseReturned(Tick(at + 1), Seat(2), onBeat()))
        assertNull(paired.state.egress, "a living pair at the two nodes did not contain it")
    }

    /**
     * **A node scanned before the meeting places nobody after it** (D-136; review r4 F3, D-279).
     *
     * Seats 0 and 2 stand at the two nodes, a report pauses the Egress, and at lights out both tap
     * from the meeting room without scanning again. Their presence rows still name the nodes --
     * lights out wipes the sightings, not the rows -- but the rows are older than the shift, and
     * `placementOf` would place neither of them. Neither does the pulse: no pair, no offer, and no
     * lockout, because a seat standing nowhere attempted nothing. The control: the same two taps
     * from rows written in this shift contain it.
     */
    @Test
    fun `a node scanned before the meeting does not count after it`() {
        val fired = Tick(60)
        val closed = Tick(170)
        val walk = EgressWalk(burning(fired)).feed(
            Event.MeetingCalled(Tick(80), Seat(0), MeetingTrigger.RevokeReported(Seat(5))),
        )
        walk.feed(*SEATS.map { Event.MeetingCheckedIn(Tick(85), it) }.toTypedArray())
        walk.feed(*SEATS.map { Event.ReadyToVoteDeclared(Tick(90), it) }.toTypedArray())
        walk.feed(*SEATS.map { Event.VoteLocked(Tick(95), it) }.toTypedArray())
        walk.feed(Event.MeetingClosed(closed))
        assertNull(walk.state.meeting, "the fixture never reached lights out")
        assertEquals(closed, walk.state.shiftStartedAt)
        assertEquals(WEST, walk.state.presenceFor(Seat(0))?.at, "the fixture's rows did not survive lights out")
        walk.drain()
        val lit = walk.state

        val stale = EgressWalk(lit).feed(
            Event.SyncPulseReturned(Tick(175), Seat(0), onBeat(from = 80)),
            Event.SyncPulseReturned(Tick(175), Seat(2), onBeat(from = 80)),
        )
        val egress = assertNotNull(stale.state.egress, "two seats contained the Egress from nodes scanned before the meeting")
        assertEquals(emptyList(), egress.offers, "a pre-meeting scan put an offer on the house")
        assertEquals(emptyList(), egress.lockouts, "a seat standing nowhere was charged a lockout")
        assertEquals(
            answered(Seat(0), held = false) + answered(Seat(2), held = false), stale.drain(),
            "the answer changed shape for a seat the house no longer places",
        )

        // The control: the same two scans, made in this shift.
        val fresh = EgressWalk(
            lit.withPresence(Presence(Seat(0), WEST, open = false, since = Tick(171)))
                .withPresence(Presence(Seat(2), EAST, open = false, since = Tick(172))),
        ).feed(
            Event.SyncPulseReturned(Tick(175), Seat(0), onBeat(from = 80)),
            Event.SyncPulseReturned(Tick(175), Seat(2), onBeat(from = 80)),
        )
        assertNull(fresh.state.egress, "two seats at the two nodes in this shift did not contain it")
    }

    // ---- D-258: one Isolate frame per living seat, of one length ------------------------------

    /**
     * **At arming, when an Egress starts and when one is contained, every living seat is sent
     * exactly one Isolate frame, and every one of them is the same length on the wire** (D-258).
     *
     * The offer reaches living Insiders only, so before D-258 an Insider's socket carried one frame
     * more than a Resident's at each of these moments. The blank is built beside each seat's offer
     * and reaches living Residents only. Measured through the allowlist and the real wire encoders,
     * with seat 3 Revoked so the out are seen to get neither.
     */
    @Test
    fun `every living seat gets one answer frame of one length whenever an ability is answered`() {
        // D-263: the actor's `AbilityFired` and every other seat's `AbilityBlanked`, through the
        // gate, the allowlist and the real encoders, with seat 3 Revoked. Each of the four Tier
        // abilities and an Egress, taking and falling into a running clock, fired by either Insider.
        fun answerFrames(effects: List<Effect>, state: GameState): Map<Int, List<Int>> = effects
            .filter { it is Effect.AbilityFired || it is Effect.AbilityBlanked }
            .flatMap { effect ->
                EmitSchema.deliveries(effect, state).map { delivery ->
                    val push = RoundWire.encode(RoundBody.Push(1, RoundWire.project(effect)))
                    delivery.seat.index to TransportWire.encode(TransportFrame.Carry(push)).length
                }
            }
            .groupBy({ it.first }, { it.second })

        val living = SEATS.map { it.index } - 3
        val start = armed().copy(revoked = listOf(Seat(3)))
        val late = Tick(readyToFire(start).step + Balance.EGRESS_COOLDOWN)
        val moments = INSIDERS.flatMap { actor ->
            listOf(
                "a Surge by $actor" to Event.SurgeFired(late, actor, Seat(2)),
                "an Isolate by $actor" to Event.IsolateFired(late, actor, MIDDLE),
                "a Static by $actor" to Event.StaticFired(late, actor),
                "an Egress by $actor" to Event.EgressFired(late, actor, EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
                "an early Egress by $actor" to Event.EgressFired(Tick(1), actor, EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
            )
        }
        for ((moment, event) in moments) {
            val walk = EgressWalk(start).feed(event)
            assertTrue(walk.refusals.isEmpty(), "$moment was refused, so this measured nothing: ${walk.refusals}")
            val frames = answerFrames(walk.drain(), walk.state)
            assertEquals(living.toSet(), frames.keys, "$moment: the answer frames reached another set of seats: $frames")
            for ((seat, lengths) in frames) assertEquals(1, lengths.size, "$moment: seat $seat got ${lengths.size} answer frames")
            assertEquals(1, frames.values.flatten().toSet().size, "$moment: the answer frame's length follows the seat: $frames")
        }
    }

    @Test
    fun `every living seat gets one Isolate frame of one length at each of the three moments`() {
        fun isolateFrames(effects: List<Effect>, state: GameState): Map<Int, List<Int>> = effects
            .filter { it is Effect.IsolateTargetsOffered || it is Effect.IsolateTargetsBlanked }
            .flatMap { effect ->
                EmitSchema.deliveries(effect, state).map { delivery ->
                    val push = RoundWire.encode(RoundBody.Push(1, RoundWire.project(effect)))
                    delivery.seat.index to TransportWire.encode(TransportFrame.Carry(push)).length
                }
            }
            .groupBy({ it.first }, { it.second })

        val living = SEATS.map { it.index } - 3
        fun holds(moment: String, frames: Map<Int, List<Int>>) {
            assertEquals(living.toSet(), frames.keys, "$moment: the Isolate frames reached another set of seats: $frames")
            for ((seat, lengths) in frames) assertEquals(1, lengths.size, "$moment: seat $seat got ${lengths.size} Isolate frames")
            assertEquals(1, frames.values.flatten().toSet().size, "$moment: the Isolate frame's length follows the role: $frames")
        }

        val arming = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 20260822L, seats = SEATS, insiders = INSIDERS, markers = where()),
        )
        holds("arming", isolateFrames(arming.effects, arming.state.copy(revoked = listOf(Seat(3)))))

        val start = armed().copy(revoked = listOf(Seat(3)))
        val walk = EgressWalk(start).feed(
            Event.EgressFired(readyToFire(start), Seat(1), EgressType.Beacon, listOf(WEST, EAST), pulseFrom = PULSE_FROM),
        )
        assertNotNull(walk.state.egress, "the Egress never started, so this measured nothing")
        holds("an Egress starting", isolateFrames(walk.drain(), walk.state))

        val fired = Tick(60)
        val at = pulseTick(fired, 3)
        val contained = EgressWalk(burning(fired).copy(revoked = listOf(Seat(3)))).feed(
            Event.SyncPulseReturned(Tick(at), Seat(0), onBeat()),
            Event.SyncPulseReturned(Tick(at), Seat(2), onBeat()),
        )
        assertNull(contained.state.egress, "the Egress was not contained, so this measured nothing")
        holds("containment", isolateFrames(contained.drain(), contained.state))
    }
}
