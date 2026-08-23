package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Placement
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Spoof — the phantom, the pool, and the dedup rule that has no consumer yet** (`gdd.md:313`,
 * `:392`–`:401`; D-136, and the owner's targeting ruling).
 *
 * The fixture is `TierTwoTest`'s: six seats, two Insiders, the pool pushed to ready so a fire is a
 * fire, and every walk through [admit] rather than [reduce] because half the rules are the gate.
 *
 * ### Most of what is asserted here is about a screen nobody has built
 *
 * A phantom reaches no client — `PHANTOM_PLACED` has no schema row — so the only artifacts that
 * hold one are the state and the recording. That is exactly why these tests exist **now**: the
 * dedup rule is the easiest thing in this system to get wrong, and the spectator map that will
 * finally read a phantom is hardware-dependent, frozen, and will not be written by whoever wrote
 * the ability.
 */
private val SP_SEATS = (0 until 6).map { Seat(it) }
private val SP_INSIDER = Seat(1)
private val SP_OTHER = Seat(4)

private val KITCHEN = "KITCHEN"
private val GARAGE_ROOM = "GARAGE"

private fun spArmed(ready: Tick = Tick(0)): GameState = GameState.armedRound(
    seed = 20260823L,
    seats = SP_SEATS,
    insiders = listOf(SP_INSIDER, SP_OTHER),
    systemIntegrity = 30,
    activeMarkers = SP_ACTIVE,
    destinations = placed(SP_ACTIVE),
    cooldowns = SP_SEATS.map { Cooldown(it, InsiderAbility.Access, ready) },
)

private val SP_ACTIVE = listOf(
    home.someoneshome.model.MarkerId("hall-card"),
    home.someoneshome.model.MarkerId("garage-card"),
)

/** Drive through the gate and keep what came out. `T2Walk`'s shape, kept local for its reason. */
private class SpWalk(start: GameState) {
    var state: GameState = start
        private set
    private val emitted = mutableListOf<Effect>()

    fun feed(vararg events: Event): SpWalk {
        for (event in events) {
            when (val admission = admit(state, event)) {
                is Admission.Admitted -> {
                    state = admission.reduction.state
                    emitted += admission.reduction.effects
                }
                is Admission.Refused -> Unit
            }
        }
        return this
    }

    fun drain(): List<Effect> = emitted.toList().also { emitted.clear() }
}

class SpoofTest {

    /**
     * **A Spoof places a phantom for the actor's own seat and spends the shared pool** (`gdd.md:313`,
     * `:401`).
     *
     * The base case, and the two halves that make it an ability rather than a no-op: the state
     * carries the claim, and the clock the whole support kit stands on moved.
     */
    @Test
    fun `a spoof places a phantom for the actor and spends the access pool`() {
        val walk = SpWalk(spArmed()).feed(Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN))

        val phantom = assertNotNull(
            walk.state.phantomFor(SP_INSIDER, Tick(11)),
            "the spoof left no phantom",
        )
        assertEquals(KITCHEN, phantom.room, "the phantom is in a room nobody named")
        assertEquals(
            Tick(10) + Balance.SPOOF_DURATION, phantom.until,
            "the phantom's lapse is not its own duration off the firing tick",
        )
        assertEquals(
            Tick(10) + Balance.SPOOF_COOLDOWN,
            assertNotNull(walk.state.cooldownFor(SP_INSIDER, InsiderAbility.Access)).readyAt,
            "the pool was not spent at Spoof's weight",
        )
        // Nobody else's pool moved, and nobody else claims anything. The per-Insider line D-151
        // draws, asked of the third ability on the pool.
        assertNull(walk.state.phantomFor(SP_OTHER, Tick(11)), "the other Insider gained a phantom")
        assertEquals(
            Tick(0),
            assertNotNull(walk.state.cooldownFor(SP_OTHER, InsiderAbility.Access)).readyAt,
            "the other Insider's pool was spent by somebody else's Spoof",
        )
    }

    /**
     * **A PHANTOM NEVER DISPLACES THE SAME SEAT'S SCAN GROUND TRUTH — SCAN FIRST** (D-136).
     *
     * *The dedup rule only runs one way: a performer's own scan placement is ground truth, and no
     * other phone's sighting may relocate them. **Inference never overrides knowledge.***
     *
     * The seat scans the hall card — which is knowledge, because a card is a place the house knows —
     * and then claims the kitchen. [GameState.placementOf] still says the hall.
     *
     * Injecting the bug: have `spoofFired` call `withPresence` instead of `withPhantom`, or have
     * `placementOf` ask the phantom first.
     */
    @Test
    fun `a scan then a spoof leaves the scan as the placement`() {
        val hall = SP_ACTIVE.first()
        val walk = SpWalk(spArmed()).feed(
            Event.MarkerScanned(Tick(5), SP_INSIDER, hall),
            Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN),
        )

        assertEquals(
            Placement.Known(SP_INSIDER, hall),
            walk.state.placementOf(SP_INSIDER, Tick(11)),
            "a lie relocated a seat the house had actually placed",
        )
        // **And the phantom is still there**, which is the half that proves the guarantee is the
        // resolution rather than a refusal. A build that dropped the phantom would pass the line
        // above and would be refusing an ability on a client-visible path to do it.
        assertNotNull(
            walk.state.phantomFor(SP_INSIDER, Tick(11)),
            "the spoof was refused rather than out-ranked",
        )
    }

    /**
     * **A PHANTOM NEVER DISPLACES THE SAME SEAT'S SCAN GROUND TRUTH — SPOOF FIRST** (D-136).
     *
     * The other order, and the one a last-write-wins build gets right by accident and then loses
     * the day somebody reorders two lines. The claim is made at minute one and the scan lands at
     * minute two; the scan is knowledge and the answer is the same.
     *
     * **Both orders are one test written twice on purpose.** They cannot be collapsed: what makes
     * them the same answer is that [GameState.placementOf] is a *read* rather than a write, and a
     * build that resolved at write time would pass exactly one of them.
     */
    @Test
    fun `a spoof then a scan leaves the scan as the placement`() {
        val hall = SP_ACTIVE.first()
        val walk = SpWalk(spArmed()).feed(
            Event.SpoofFired(Tick(5), SP_INSIDER, KITCHEN),
            Event.MarkerScanned(Tick(10), SP_INSIDER, hall),
        )

        assertEquals(
            Placement.Known(SP_INSIDER, hall),
            walk.state.placementOf(SP_INSIDER, Tick(11)),
            "a lie told first outranked the house's own knowledge",
        )
        assertNotNull(
            walk.state.phantomFor(SP_INSIDER, Tick(11)),
            "the scan deleted the phantom instead of out-ranking it",
        )
    }

    /**
     * **A seat the house has never placed reads off its phantom, and only until it lapses.**
     *
     * The one case where inference is all there is, which is what makes a Spoof worth firing at
     * all — and the expiry, which is what stops a lie from becoming a fixture (`Balance
     * .SPOOF_DURATION`).
     *
     * Injecting the bug: drop the `at < it.until` from `phantomFor`, and a phantom stands for the
     * rest of the round.
     */
    @Test
    fun `an unplaced seat reads off its phantom until the phantom lapses`() {
        val walk = SpWalk(spArmed()).feed(Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN))
        val lapses = Tick(10) + Balance.SPOOF_DURATION

        assertEquals(
            Placement.Inferred(SP_INSIDER, KITCHEN),
            walk.state.placementOf(SP_INSIDER, Tick(lapses.step - 1)),
            "the phantom was not readable while it was live",
        )
        // Strictly before the return tick, exactly as `isIsolated` takes the edge, so that two
        // temporary states in one round cannot disagree about what *until* means.
        assertNull(
            walk.state.placementOf(SP_INSIDER, lapses),
            "the phantom outlived its own deadline by a tick",
        )
        assertNull(
            walk.state.placementOf(SP_INSIDER, lapses + 900L),
            "the phantom is permanent",
        )
    }

    /**
     * **A phantom is really a check-in rather than a fixture** — the arithmetic, not the walk.
     *
     * `Balance.phantomIsTemporary`'s claim, asserted where a playtest tuning the numbers will trip
     * it: at or past parity, one Insider could keep a permanent false occupant standing in a room
     * by re-firing, which is a fixture and not a check-in.
     */
    @Test
    fun `a phantom lapses before its own cooldown does`() {
        assertTrue(
            Balance.phantomIsTemporary(),
            "SPOOF_DURATION has reached SPOOF_COOLDOWN: a phantom can now be held permanently",
        )
    }

    /**
     * **The pool is shared with the other two, in both directions** (`gdd.md:306`).
     *
     * A Surge then a Spoof, and a Spoof then an Isolate: the second of each pair finds the clock
     * running and does nothing. Asserted both ways round because `spendPool` is one function and a
     * build that forgot to call it from **one** handler would still pass a test that only ever
     * fired that handler first.
     */
    @Test
    fun `spoof draws on the same pool surge and isolate do`() {
        val afterSurge = SpWalk(spArmed()).feed(
            Event.SurgeFired(Tick(10), SP_INSIDER, Seat(3)),
            Event.SpoofFired(Tick(11), SP_INSIDER, KITCHEN),
        )
        assertNull(
            afterSurge.state.phantomFor(SP_INSIDER, Tick(12)),
            "a Spoof landed on a pool a Surge had just spent",
        )

        val afterSpoof = SpWalk(spArmed()).feed(
            Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN),
            Event.IsolateFired(Tick(11), SP_INSIDER, SP_ACTIVE.first()),
        )
        assertTrue(
            afterSpoof.state.isolated.isEmpty(),
            "an Isolate landed on a pool a Spoof had just spent",
        )
    }

    /**
     * **One Spoof replaces the actor's own phantom rather than joining it** (`GameState.withPhantom`).
     *
     * Two claims by one seat about where it is would make *where does the house think seat 1 is* a
     * question about which row somebody read first. `withIsolated`'s decision, one plane over.
     */
    @Test
    fun `a second spoof replaces the actors own phantom`() {
        val walk = SpWalk(spArmed()).feed(Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN))
        // Push the pool back to ready by hand: this is about the list's shape, not about the clock.
        val ready = walk.state.withCooldown(
            Cooldown(SP_INSIDER, InsiderAbility.Access, Tick(200)),
        )
        val again = SpWalk(ready).feed(Event.SpoofFired(Tick(300), SP_INSIDER, GARAGE_ROOM))

        assertEquals(
            1, again.state.phantoms.count { it.seat.index == SP_INSIDER.index },
            "one seat is claiming two rooms at once",
        )
        assertEquals(
            GARAGE_ROOM,
            assertNotNull(again.state.phantomFor(SP_INSIDER, Tick(301))).room,
            "the older claim won",
        )
    }

    /**
     * **A room the plan does not hold is recorded rather than refused** (`Event.SpoofFired`).
     *
     * The rules have never held house geography and this does not start. What comes back is a
     * phantom in a room that does not exist — visible to nobody, costing an Insider a pool — and
     * **the effects are the same shape they are for a real room**, which is the part that matters:
     * a refusal here would be a branch on a client-visible path for a case only a forged client can
     * reach.
     */
    @Test
    fun `a room the house never heard of produces the same shape as a real one`() {
        val real = SpWalk(spArmed()).feed(Event.SpoofFired(Tick(10), SP_INSIDER, KITCHEN))
        val invented = SpWalk(spArmed()).feed(Event.SpoofFired(Tick(10), SP_INSIDER, "ATLANTIS"))

        assertEquals(
            real.drain().map { it::class.simpleName },
            invented.drain().map { it::class.simpleName },
            "a room the house has never heard of came back a different shape",
        )
        assertEquals(
            "ATLANTIS",
            assertNotNull(invented.state.phantomFor(SP_INSIDER, Tick(11))).room,
            "the room was validated somewhere",
        )
    }

    /**
     * **The plan is published at arming, to every seat, and is stored nowhere** (`gdd.md:313`).
     *
     * `isolateOffers`' posture and its finding: the run is built for every seat so the arming burst
     * stays role-independent, and the narrowing is the allowlist's. The second half is this build's
     * own guarantee — after the arming reduction there is no plan anywhere in [GameState] for a
     * rule to consult.
     */
    @Test
    fun `the arming burst offers the plan to every seat and the state keeps none of it`() {
        val plan = home.someoneshome.model.HomePlanView(
            listOf(
                home.someoneshome.model.FloorView(
                    "GROUND",
                    listOf(
                        home.someoneshome.model.RoomOutline(
                            KITCHEN,
                            cells = listOf(home.someoneshome.model.PlanCell(0, 0)),
                        ),
                    ),
                ),
            ),
        )
        val reduction = reduce(
            GameState.EMPTY,
            armingFor(Tick(1), 20260823L, SP_SEATS, null, placed(SP_ACTIVE), plan = plan),
        )
        val offers = reduction.effects.filterIsInstance<Effect.HomePlanOffered>()

        assertEquals(
            SP_SEATS.map { it.index }, offers.map { it.seat.index },
            "the plan was not offered to every seat in seat order",
        )
        assertTrue(
            offers.all { it.plan == plan },
            "a seat was offered a different plan from its neighbour",
        )
        // The whole point of the pass-through: nothing in the state can answer *where is the
        // kitchen*, so no rule can ever branch on it.
        assertEquals(
            emptyList<String>(),
            reduction.state.destinations.filter { it.room == KITCHEN }.map { it.room },
            "the plan's rooms leaked into the round's destinations",
        )
    }
}
