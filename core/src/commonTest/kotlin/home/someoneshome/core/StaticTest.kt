package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Static — the house-wide jam, the pool it costs, and the anonymity it is made of** (D-186,
 * `gdd.md:447-495`).
 *
 * The fixture is `TierTwoTest`'s: six seats, two Insiders, the pool pushed to ready so a fire is a
 * fire, and every walk through [admit] rather than [reduce] because half the rules are the gate.
 *
 * ### What is asserted here is mostly about what the house CANNOT say
 *
 * A jam reaches every living phone in the building, identically, and the state it leaves behind
 * carries no seat. So most of these tests are the same claim from different sides: **two Insiders
 * firing the same ability at the same moment are indistinguishable**, in the state, in the effects,
 * and in the bytes.
 */
private val ST_SEATS = (0 until 6).map { Seat(it) }
private val ST_INSIDER = Seat(1)
private val ST_OTHER = Seat(4)

private val ST_ACTIVE = listOf(
    home.someoneshome.model.MarkerId("hall-card"),
    home.someoneshome.model.MarkerId("garage-card"),
)

private fun stArmed(ready: Tick = Tick(0)): GameState = GameState.armedRound(
    seed = 20260823L,
    seats = ST_SEATS,
    insiders = listOf(ST_INSIDER, ST_OTHER),
    systemIntegrity = 30,
    activeMarkers = ST_ACTIVE,
    destinations = placed(ST_ACTIVE),
    cooldowns = ST_SEATS.map { Cooldown(it, InsiderAbility.Access, ready) },
)

/** Drive through the gate and keep what came out. `T2Walk`'s shape, kept local for its reason. */
private class StWalk(start: GameState) {
    var state: GameState = start
        private set
    private val emitted = mutableListOf<Effect>()

    fun feed(vararg events: Event): StWalk {
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

class StaticTest {

    /**
     * **A Static jams the house for its own duration and spends the shared pool** (D-186).
     *
     * The base case, and the two halves that make it an ability rather than a no-op: the house's
     * monitoring is down, and the clock the whole support kit stands on moved.
     */
    @Test
    fun `a static jams the house and spends the access pool`() {
        val walk = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_INSIDER))

        assertEquals(
            Tick(10) + Balance.STATIC_DURATION,
            assertNotNull(walk.state.staticUntil, "the fire left no jam"),
            "the jam's lapse is not its own duration off the firing tick",
        )
        assertTrue(walk.state.staticRunning(Tick(11)), "the jam is not running a tick after the fire")
        assertEquals(
            Tick(10) + Balance.STATIC_COOLDOWN,
            assertNotNull(walk.state.cooldownFor(ST_INSIDER, InsiderAbility.Access)).readyAt,
            "the pool was not spent at Static's weight",
        )
        // The other Insider's pool did not move. The per-Insider line D-151 draws, asked of an
        // ability whose EFFECT is house-wide -- which is exactly the pair that could be confused.
        assertEquals(
            Tick(0),
            assertNotNull(walk.state.cooldownFor(ST_OTHER, InsiderAbility.Access)).readyAt,
            "the other Insider's pool was spent by somebody else's Static",
        )
    }

    /**
     * **⚠️ A JAM IS IDENTICAL WHICHEVER INSIDER FIRED IT** (D-186 — *loud and anonymous*).
     *
     * The ability's whole second half, asserted directly rather than left to the shape of the
     * types. Two rounds, identical in every way except which of the two Insiders pressed the tile,
     * produce **the same state and the same effects** — the jam's deadline, the announcement's
     * seconds and its seed all agree, and the only thing that differs anywhere is whose pool was
     * charged, which is on that seat's own phone and nobody else's.
     *
     * Injecting the bug: put the actor on `Effect.StaticOpened`, or fold `event.actor` into
     * `staticSeed`. Either makes the noise on every phone in the house a function of who fired it.
     */
    @Test
    fun `a jam is identical whichever insider fired it`() {
        val mine = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_INSIDER))
        val theirs = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_OTHER))

        assertEquals(
            mine.state.staticUntil, theirs.state.staticUntil,
            "the jam's window depends on who fired it",
        )
        assertEquals(
            mine.drain().filterIsInstance<Effect.StaticOpened>(),
            theirs.drain().filterIsInstance<Effect.StaticOpened>(),
            "the house's announcement is not byte-identical between the two Insiders",
        )
    }

    /**
     * **The announcement carries the window and a seed, and it carries nothing else** (D-186).
     *
     * The seed is a fact about **when**, so two jams at different ticks in the same round draw
     * different noise and the same jam replayed draws the same noise. Asserted here rather than
     * left to the client, because the client cannot check what it was never sent.
     */
    @Test
    fun `the announcement carries the window and a seed that changes with the tick`() {
        val early = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_INSIDER))
            .drain().filterIsInstance<Effect.StaticOpened>().single()
        val late = StWalk(stArmed()).feed(Event.StaticFired(Tick(400), ST_INSIDER))
            .drain().filterIsInstance<Effect.StaticOpened>().single()

        assertEquals(
            (Balance.STATIC_DURATION / Balance.TICKS_PER_SECOND).toInt(), early.seconds,
            "the announced window is not the ruling's ten seconds",
        )
        assertEquals(early.seconds, late.seconds, "the window depends on when it was fired")
        assertTrue(early.seed != late.seed, "two jams a shift apart draw the same noise")

        // Replayed: the same round and the same tick mint the same seed, or the noise is not
        // deterministic and rule 5's authored light is a local animation after all.
        val again = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_INSIDER))
            .drain().filterIsInstance<Effect.StaticOpened>().single()
        assertEquals(early.seed, again.seed, "the same fire in the same round drew a different field")
    }

    /**
     * **A fire into a running pool announces nothing and tells the actor the same thing** (rule 1).
     *
     * The wasteful *moment* rather than a wasteful target — which is the case `AbilityRulesTest`'s
     * sweep was missing entirely until T2's injection #4 found it. What the actor's phone gets is
     * one [Effect.AbilityFired] either way; what the house does not do is announce a jam it did not
     * open.
     */
    @Test
    fun `a static into a running pool leaves no jam and still answers the actor`() {
        val walk = StWalk(stArmed()).feed(
            Event.StaticFired(Tick(10), ST_INSIDER),
            Event.StaticFired(Tick(11), ST_INSIDER),
        )
        val effects = walk.drain()

        assertEquals(
            2, effects.filterIsInstance<Effect.AbilityFired>().size,
            "the second fire was not answered on the actor's own device",
        )
        assertEquals(
            1, effects.filterIsInstance<Effect.StaticOpened>().size,
            "a fire into a running pool announced a second jam",
        )
        assertEquals(
            Tick(10) + Balance.STATIC_DURATION, walk.state.staticUntil,
            "a wasted fire moved the window",
        )
    }

    /**
     * **A second Insider's Static EXTENDS the window and can never shorten it**
     * (`GameState.withStatic`).
     *
     * The pool is per-Insider, so the other Insider can fire into a running jam — and what that
     * buys is a longer jam. A window that could be cut short would make its length on screen a
     * report of how many Insiders reached for it, which is the anonymity leaving through the render.
     */
    @Test
    fun `a second insiders static extends the window and never shortens it`() {
        val walk = StWalk(stArmed()).feed(
            Event.StaticFired(Tick(10), ST_INSIDER),
            Event.StaticFired(Tick(14), ST_OTHER),
        )
        assertEquals(
            Tick(14) + Balance.STATIC_DURATION, walk.state.staticUntil,
            "the later fire did not extend the window",
        )

        // And the other way round: a fire whose window ends *earlier* than the running one leaves
        // the running one alone. Only reachable if the durations ever differ, which is exactly the
        // day this needs to already be right.
        val held = walk.state.withStatic(Tick(15))
        assertEquals(
            Tick(14) + Balance.STATIC_DURATION, held.staticUntil,
            "a shorter jam cut a longer one short",
        )
    }

    /**
     * **A jam fails at the meeting CALL, and nothing is emitted about it** (D-185).
     *
     * *A meeting call terminates every ability effect currently running.* The moment is the call
     * rather than the end: a call snaps every lamp in the house to full white and puts every living
     * player in one room, and an effect that outlived it would land its remaining seconds on the
     * next shift — an effect with no traceable cause.
     *
     * **Nothing is emitted**, asserted by comparing the effect lists of a meeting called with a jam
     * running and one without. There is no surface left showing noise for a message to clear: page 2
     * does not exist during a meeting (D-183) and the Terminal is never readable at one.
     */
    @Test
    fun `a meeting call ends a running jam and says nothing about it`() {
        val call = Event.MeetingCalled(Tick(12), Seat(2), MeetingTrigger.MeetingCard)

        val jammed = StWalk(stArmed()).feed(Event.StaticFired(Tick(10), ST_INSIDER))
        jammed.drain()
        val quiet = StWalk(stArmed())

        jammed.feed(call)
        quiet.feed(call)

        assertFalse(
            jammed.state.staticRunning(Tick(13)),
            "a jam survived the meeting call that was supposed to end it",
        )
        assertEquals(
            null, jammed.state.staticUntil,
            "the jam was paused rather than cleared",
        )
        assertEquals(
            quiet.drain().map { it::class.simpleName },
            jammed.drain().map { it::class.simpleName },
            "the meeting emitted something extra because a jam had been running",
        )
    }

    /**
     * **A jam is really a window rather than a condition** — the arithmetic, not the walk.
     *
     * `Balance.staticIsAWindow`'s claim, asserted where a playtest tuning the numbers will trip it:
     * at or past parity one Insider could hold the house's monitoring dark for a whole shift by
     * re-firing, and a permanent jam is a broken Terminal rather than an ability.
     */
    @Test
    fun `a jam lapses well before its own cooldown does`() {
        assertTrue(
            Balance.staticIsAWindow(),
            "STATIC_DURATION has reached STATIC_COOLDOWN: the house can now be held dark",
        )
        assertTrue(
            Balance.STATIC_COOLDOWN > Balance.ISOLATE_COOLDOWN,
            "Static is no longer heavier than the pool's lightest weight (D-186)",
        )
    }

    /**
     * **The pool is shared with the other two, in both directions** (`gdd.md:306`).
     *
     * A Surge then a Static, and a Static then an Isolate: the second of each pair finds the clock
     * running and does nothing. Asserted both ways round because `spendPool` is one function and a
     * build that forgot to call it from **one** handler would still pass a test that only ever fired
     * that handler first.
     */
    @Test
    fun `static draws on the same pool surge and isolate do`() {
        val afterSurge = StWalk(stArmed()).feed(
            Event.SurgeFired(Tick(10), ST_INSIDER, Seat(3)),
            Event.StaticFired(Tick(11), ST_INSIDER),
        )
        assertFalse(
            afterSurge.state.staticRunning(Tick(12)),
            "a Static landed on a pool a Surge had just spent",
        )

        val afterStatic = StWalk(stArmed()).feed(
            Event.StaticFired(Tick(10), ST_INSIDER),
            Event.IsolateFired(Tick(11), ST_INSIDER, ST_ACTIVE.first()),
        )
        assertTrue(
            afterStatic.state.isolated.isEmpty(),
            "an Isolate landed on a pool a Static had just spent",
        )
    }

    /**
     * **⚠️ THE HOME'S PLAN CROSSES TO NOBODY, AND THE ARMING BURST NO LONGER HOLDS ONE** (D-186).
     *
     * The rollback of T2·4, which flagged the plan reaching a living Insider as *the largest
     * disclosure in the emit table*. Static has no plan to draw, so the row went — and this is the
     * assertion at the rules' end of it: **no effect the arming reduction produces carries house
     * geography beyond the destinations a work order already narrows to one seat's own lines.**
     *
     * **The burst is asserted as a whole rather than by the absence of one type**, because an
     * absence proves nothing about a run somebody adds tomorrow: what arming emits is five runs of
     * one-per-seat and there is no sixth. `EffectPushTest.noFrameCarriesTheHomesGeography` is the
     * same claim read off real frames on a real socket, where a room only a plan could name is
     * planted in the house and swept for.
     *
     * **The fifth run is the role reveal** (E-R2-2), and it is the one run whose *payload* differs
     * by seat. It is on this list rather than exempted from it because the claim this test makes is
     * about the burst's SHAPE: one effect per seat per run, so nothing about the size or the order
     * of what a phone receives at arming says which role it holds. What is inside the reveal is
     * addressed to the one phone it is about — see `EmitSchema.audienceOf`.
     */
    @Test
    fun `the arming burst is five runs and none of them is a plan`() {
        val reduction = reduce(
            GameState.EMPTY,
            armingFor(Tick(1), 20260823L, ST_SEATS, null, placed(ST_ACTIVE)),
        )
        val runs = reduction.effects.map { it::class.simpleName }.distinct()

        assertEquals(
            listOf(
                "LampSet", "WorkOrderIssued", "OpeningMessage", "RoleRevealed",
                "IsolateTargetsOffered",
            ),
            runs,
            "the arming burst gained or lost a run",
        )
        assertTrue(
            reduction.effects.size == runs.size * ST_SEATS.size,
            "a run is not one effect per seat, so the burst is no longer role-independent",
        )
    }
}
