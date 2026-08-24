package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Isolation
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Sound
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.WorkOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Tier 2 — Surge and Isolate on the shared Access pool** (`gdd.md:306`, `:313`, `:315`, `:342`,
 * `:392`–`:401`; D-132, D-150, and the owner's 2026-08-22 targeting ruling).
 *
 * Six seats, **two Insiders**, for `EgressTest`'s reason inverted: the pool is per-Insider and not
 * house-wide, and a single-Insider fixture cannot tell those apart either. Both are asserted here —
 * the two abilities share one clock *within* a seat, and two seats' clocks are their own.
 *
 * Every walk goes through [admit] rather than [reduce], because half the rules are the gate.
 */
private val T2_SEATS = (0 until 6).map { Seat(it) }
private val INSIDER = Seat(1)
private val OTHER_INSIDER = Seat(4)

private val HALL = MarkerId("hall-card")
private val GARAGE = MarkerId("garage-card")
private val STUDY = MarkerId("study-card")
private val LANDING = MarkerId("landing-card")
private val ACTIVE = listOf(HALL, GARAGE, STUDY, LANDING)

/**
 * A round with the pool already run down for both Insiders, so a fire is a fire.
 *
 * The clock is pushed rather than typed, for `EgressTest`'s reason: D-132 opens it at half, and a
 * test that fired at whatever tick a counter happened to be at would fire nothing and go on
 * asserting the *shape* of what came back.
 */
private fun t2Armed(
    ready: Tick = Tick(0),
    cooldowns: List<Cooldown> = T2_SEATS.map { Cooldown(it, InsiderAbility.Access, ready) },
): GameState = GameState.armedRound(
    seed = 20260823L,
    seats = T2_SEATS,
    insiders = listOf(INSIDER, OTHER_INSIDER),
    systemIntegrity = 30,
    activeMarkers = ACTIVE,
    destinations = placed(ACTIVE),
    cooldowns = cooldowns,
)

/** Drive events through the **admission gate**, never through [reduce]. `EgressWalk`'s shape. */
private class T2Walk(start: GameState) {
    var state: GameState = start
        private set
    private val emitted = mutableListOf<Effect>()

    fun feed(vararg events: Event): T2Walk {
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

    /** Hand-set state a walk cannot reach cheaply — the meeting's phase, and nothing else. */
    fun holding(next: GameState): T2Walk = also { state = next }

    fun drain(): List<Effect> = emitted.toList().also { emitted.clear() }
}

private fun t2Pool(state: GameState, seat: Seat): Tick =
    assertNotNull(state.cooldownFor(seat, InsiderAbility.Access), "seat has no pool").readyAt

/**
 * A real arming, reduced, so the opening list is read off the rules rather than off a fixture.
 *
 * `t2Armed` builds a state by hand for the walks that need a pool already run down; this is the
 * other question — *what does the house say when the lights go out* — and it has to go through
 * `armingFor` and `reduce` or it would be asserting against a list this test wrote.
 */
private fun t2Arming(): List<Effect> = reduce(
    GameState.EMPTY,
    armingFor(Tick(1), 20260823L, T2_SEATS, null, placed(ACTIVE)),
).effects

/** Four taps landing exactly on four consecutive beats of the house schedule. `EgressTest`'s. */
private fun t2OnBeat(firedAt: Tick): List<Long> =
    (0 until Balance.SYNC_PULSE_BEATS).map { beatAt(firedAt, it.toLong()) }

/** The list this seat was last offered, or null if the house never offered it one. */
private fun t2Offered(effects: List<Effect>, seat: Seat): List<String>? =
    effects.filterIsInstance<Effect.IsolateTargetsOffered>()
        .lastOrNull { it.seat.index == seat.index }
        ?.targets?.map { it.marker.value }

class AccessPoolTest {

    /**
     * **One clock for the whole of Tier 2, and it is per Insider** (`gdd.md:306`, `:318`).
     *
     * *Shared Access pool* is a statement about the three abilities in the row it labels, not about
     * the two Insiders — D-151 draws the house-wide line in exactly one place and the Egress is on
     * the other side of it. So: a Surge and an Isolate from **one** seat cannot both land, and the
     * **other** Insider's kit is untouched by either.
     *
     * The second half is what a single-Insider fixture could never see, and it is the half a build
     * gets wrong by copying `egressReadyAt`.
     */
    @Test
    fun `surge and isolate share one clock and each Insider has their own`() {
        val walk = T2Walk(t2Armed()).feed(Event.SurgeFired(Tick(10), INSIDER, Seat(3)))
        assertEquals(
            Tick(10 + Balance.SURGE_COOLDOWN), t2Pool(walk.state, INSIDER),
            "a Surge did not restart the pool at its own weight",
        )
        assertEquals(
            Tick(0), t2Pool(walk.state, OTHER_INSIDER),
            "one Insider's Surge spent the other Insider's pool — the pool is not house-wide",
        )

        // The same seat, immediately, with a different Tier-2 ability. One clock means this does
        // nothing at all.
        walk.drain()
        walk.feed(Event.IsolateFired(Tick(11), INSIDER, HALL))
        assertTrue(
            walk.state.isolated.isEmpty(),
            "an Isolate landed on a pool a Surge had just spent — the two do not share a clock",
        )
        assertEquals(
            Tick(10 + Balance.SURGE_COOLDOWN), t2Pool(walk.state, INSIDER),
            "a refused Isolate moved the clock anyway",
        )

        // And the other Insider, on the same tick, is unaffected — a whole ability apart.
        walk.feed(Event.IsolateFired(Tick(11), OTHER_INSIDER, HALL))
        assertEquals(
            listOf(HALL.value), walk.state.isolated.map { it.marker.value },
            "the second Insider's Isolate was refused by the first Insider's cooldown",
        )

        // **And an Isolate that LANDS spends the pool at its own weight.** Written after an
        // injection found the hole: a build in which `isolateFired` took the shot without charging
        // for it passed every assertion above, because each of those is about a fire that was
        // REFUSED. Nothing said what a successful one costs, so Isolate was free and repeatable.
        assertEquals(
            Tick(11 + Balance.ISOLATE_COOLDOWN), t2Pool(walk.state, OTHER_INSIDER),
            "an Isolate that landed did not restart the pool — the ability is free",
        )
        walk.drain()
        walk.feed(Event.IsolateFired(Tick(12), OTHER_INSIDER, GARAGE))
        assertEquals(
            listOf(HALL.value), walk.state.isolated.map { it.marker.value },
            "a second Isolate landed on a pool the first had just spent",
        )
        assertEquals(
            listOf(false),
            walk.drain().filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "the actor was told a cooldown started that had not",
        )
    }

    /**
     * **Surge is the longest cooldown in the pool** (`gdd.md:342`).
     *
     * *Surge is a commitment, not a tool.* Asserted as a relationship rather than against a literal,
     * because both numbers are placeholders playtest owns — what is settled is which is longer, and
     * a build that made them equal would delete the commitment while every value still looked
     * plausible.
     */
    @Test
    fun `surge weighs more on the pool than isolate does`() {
        assertTrue(
            Balance.SURGE_COOLDOWN > Balance.ISOLATE_COOLDOWN,
            "Surge is meant to be the longest cooldown in the Access pool",
        )
        // And an Isolation really is temporary — `gdd.md:621` rules out a permanent lock, and a
        // duration at or past its own cooldown lets one Insider hold a card dark all round.
        assertTrue(Balance.isolateIsTemporary(), "an Isolation can outlast its own cooldown")
    }

    /**
     * **A meeting does not touch the pool on the way in, and resets it on the way out** (D-183,
     * D-184, and the owner's 2026-08-24 ruling deleting `GameState.accessPausedAt`).
     *
     * This test used to assert a pause: the pool stopped when the party was called in and its
     * deadline moved forward by the length of the meeting at lights out. **Two rulings took that
     * apart from both ends** and this file recorded them one at a time — D-183 refuses a Tier-2
     * fire at the gate for the whole of a meeting, so nothing can observe the pool's readiness
     * while it would be paused, and D-184 resets every per-Insider clock at lights out, so the
     * deadline the resume would have moved is overwritten a moment later. What was left was a mark
     * nothing could read, and it is gone.
     *
     * ### What is asserted instead, and why it is the same protection
     *
     * That **a meeting is not a free recharge**, which is what the pause was for: the fire is
     * refused at the gate throughout, and the clocks that come out the other side are lights-out's
     * own reset and not a shift the meeting bought. Both rows are checked, because *both clocks
     * move* is D-184's ruling and a build that moved one is the failure worth catching.
     */
    @Test
    fun `a meeting refuses the pool and lights out resets it`() {
        val opensAt = Tick(500)
        // **Both clocks on every seat**, because the assertion at the end of this test is that only
        // one of them moved. A fixture with the Access row alone could not tell a build that paused
        // the pool from one that paused everything.
        val walk = T2Walk(
            t2Armed(
                cooldowns = T2_SEATS.flatMap {
                    listOf(
                        Cooldown(it, InsiderAbility.Access, opensAt),
                        Cooldown(it, InsiderAbility.Revoke, opensAt),
                    )
                },
            ),
        )

        // Called at 100, closed at 400: three hundred ticks of meeting.
        walk.feed(Event.MeetingCalled(Tick(100), Seat(0), MeetingTrigger.MeetingCard))
        assertEquals(
            opensAt, assertNotNull(walk.state.cooldownFor(INSIDER, InsiderAbility.Access)).readyAt,
            "the meeting moved a deadline on the way in — a call is not a clock event any more",
        )
        // **And the fire is REFUSED at the gate, which is the owner's 2026-08-23 ruling** (T1·7 /
        // T2·8, closed). It used to be admitted and answered `cooldownStarted = false`; now it
        // never reaches a handler, so there are no effects at all. **That is not rule 1's forbidden
        // silence** — an event refused above the rules is recorded as a refusal (D-066), and what
        // makes this refusal honest is the most public fact the game has: everybody in the house is
        // standing in one room looking at a meeting screen.
        // Drained first, so what is asserted below is the Surge's own answer and not the meeting
        // call's ring still sitting in the buffer.
        walk.drain()
        walk.feed(Event.SurgeFired(Tick(600), INSIDER, Seat(3)))
        assertEquals(
            emptyList(), walk.drain(),
            "a Tier-2 ability reached the rules from inside a meeting",
        )

        // **Lights out, and every per-seat clock goes back to the round's opening** (the owner's
        // 2026-08-23 ruling). This is what SUPERSEDED the resume arithmetic that used to be
        // asserted here — the deadline moving forward by the length of the meeting, `Tick(500 +
        // 300)`. A reset to half overwrites that answer outright, so the two cannot both be true
        // and the resume is gone rather than sitting beside it.
        walk.holding(
            walk.state.copy(meeting = walk.state.meeting?.copy(phase = MeetingPhase.Tally)),
        )
        walk.feed(Event.MeetingClosed(Tick(400)))
        assertEquals(
            Tick(400 + Balance.ACCESS_COOLDOWN / 2), t2Pool(walk.state, INSIDER),
            "the pool did not come out of the meeting at half, off the tick the lights went out",
        )

        // **The Revoke's clock resets with it, and `gdd.md:373`'s open question is now answered.**
        // That line said only *"pauses or resets at meeting end"* and never chose between the two;
        // the owner chose **resets**, and to half, for every per-Insider clock at once. It is
        // asserted beside the pool rather than trusted to the same code path, because *both rows
        // move* is the ruling and a build that moved one is the failure worth catching.
        assertEquals(
            Tick(400 + Balance.REVOKE_COOLDOWN / 2),
            assertNotNull(walk.state.cooldownFor(INSIDER, InsiderAbility.Revoke)).readyAt,
            "the Revoke's cooldown did not come out of the meeting at half",
        )

        // The fixture's own deadline is neither of the two the reset produces, so what is asserted
        // above is a clock that moved and not one that happened to be there already.
        assertTrue(
            opensAt.step != 400 + Balance.ACCESS_COOLDOWN / 2 &&
                opensAt.step != 400 + Balance.REVOKE_COOLDOWN / 2,
            "the fixture's deadline drifted onto the reset's own answer, so neither assertion " +
                "above can tell a build that resets from one that never moved anything",
        )
    }
}

class SurgeTest {

    /**
     * **One event: the lamp slams to full, then to black, and the phone makes the one noise this
     * game has** (`gdd.md:313`).
     *
     * *Either half alone was a nudge; together it is the loudest, brightest thing that can happen in
     * a silent dark house.* One effect, addressed to the target, carrying both halves — a build that
     * split them into two could deliver one without the other and nothing would say so.
     */
    @Test
    fun `a surge is one effect on the target carrying light and sound together`() {
        val effects = T2Walk(t2Armed()).feed(Event.SurgeFired(Tick(10), INSIDER, Seat(3))).drain()
        val surged = effects.filterIsInstance<Effect.Surged>().single()
        assertEquals(Seat(3).index, surged.seat.index, "the surge landed on somebody else")
        assertEquals(Sound.Surge, surged.sound)
        assertTrue(surged.peak > surged.black, "the lamp did not slam to full and then to black")

        // **The actor is told the ability fired and nothing else** (`gdd.md:398`). No second effect
        // addressed to them, and in particular nothing that says what happened to the target.
        val toActor = effects.filter {
            (it as? Effect.AbilityFired)?.actor?.index == INSIDER.index ||
                (it as? Effect.Surged)?.seat?.index == INSIDER.index
        }
        assertEquals(
            listOf(Effect.AbilityFired::class.simpleName), toActor.map { it::class.simpleName },
            "the attacker's device got something other than the cooldown answer",
        )
    }

    /**
     * **Targeting an already-Revoked player appears to succeed on the actor's device**
     * (`gdd.md:344`).
     *
     * *Same animation, same cooldown spent, no feedback whatsoever. Otherwise the panel becomes a
     * revoke-detector: a Insider could poll the roster and learn exactly who is out without ever
     * finding them.*
     *
     * ### The assertion is on the SHAPE, and it is the whole test
     *
     * Two rounds, identical but for whether the target is in `revoked`, and the effect stream the
     * **rules** produce is compared line for line. Not *the cooldown was spent* — that alone passes
     * for a build that also emits a quiet extra effect for a live target. The kinds, the order and
     * the fields have to match, because rule 1's leak is an effect that is *absent* in one branch.
     *
     * What stops the Revoked target's phone actually flaring is the allowlist, one layer out, and it
     * is a different test in a different module (`EmitSchemaTest`). Two independent denials.
     */
    @Test
    fun `a surge at a revoked seat produces the same effects as a surge at a live one`() {
        val live = T2Walk(t2Armed()).feed(Event.SurgeFired(Tick(10), INSIDER, Seat(3)))
        val out = T2Walk(t2Armed().copy(revoked = listOf(Seat(3))))
            .feed(Event.SurgeFired(Tick(10), INSIDER, Seat(3)))

        assertEquals(
            live.drain().map { it.toString() }, out.drain().map { it.toString() },
            "the rules told the actor something different about a target who was already out",
        )
        assertEquals(
            t2Pool(live.state, INSIDER), t2Pool(out.state, INSIDER),
            "a surge at a revoked seat cost less than a surge at a live one",
        )
    }

    /**
     * **Self-targeting works and shares the pool** (`gdd.md:392`–`:393`, `:401`).
     *
     * *Insiders can target themselves with every ability* — if only Residents could ever be
     * targeted, being visibly targeted would be evidence of innocence and every ability would double
     * as an alignment test. And *self-targeting shares the ability's cooldown*, or self-sabotage is
     * a free alibi generator: buying cover has to cost offensive capability.
     *
     * The self-surge is asserted to be **the same shape** as a surge at anybody else, which is what
     * makes the premium alibi play work: nothing on the wire marks it as self-inflicted.
     */
    @Test
    fun `a self-surge lands on the actor and costs the same pool`() {
        val self = T2Walk(t2Armed()).feed(Event.SurgeFired(Tick(10), INSIDER, INSIDER))
        val effects = self.drain()

        val surged = effects.filterIsInstance<Effect.Surged>().single()
        assertEquals(INSIDER.index, surged.seat.index, "a self-surge did not reach the actor")

        val other = T2Walk(t2Armed()).feed(Event.SurgeFired(Tick(10), INSIDER, Seat(3)))
        assertEquals(
            t2Pool(other.state, INSIDER), t2Pool(self.state, INSIDER),
            "buying cover was cheaper than attacking somebody — a free alibi generator",
        )
        // Same kinds in the same order; only the seat differs, and the seat is who it hit.
        assertEquals(
            other.drain().map { it::class.simpleName }, effects.map { it::class.simpleName },
            "a self-surge is distinguishable from an ordinary one by the shape of what was emitted",
        )
    }
}

class IsolateTest {

    /**
     * **A scan at an Isolated marker gets NOTHING FOR YOU HERE, and nothing else** (D-124, and the
     * ruling recorded on `Isolation`).
     *
     * ⚠️ **The scan's answer at a dark card is UNRULED and this is the build's decision.** The honest
     * default under D-124 and rule 1: one kind, one message per scan, only the content differing —
     * so an Isolated card is byte-identical to a registered card the house has nothing for you at.
     * **An Isolated marker must not announce itself as attacked.**
     *
     * Asserted against the ordinary empty-card answer rather than against a literal, so a build that
     * grows a second vocabulary for one of them fails here whichever one it grows.
     */
    @Test
    fun `a scan at an isolated marker answers exactly as an empty card does`() {
        val order = WorkOrder(
            Seat(0),
            listOf(
                OrderEntry(
                    index = 0,
                    work = AssignedWork.Subroutine(SubroutineKind.Handshake),
                    markers = listOf(HALL),
                    blockedBy = emptyList(),
                    done = false,
                ),
            ),
        )
        val base = t2Armed().copy(workOrders = listOf(order))

        // The control: the same seat, at a card its order does not name. D-124's null.
        val empty = T2Walk(base).feed(Event.MarkerScanned(Tick(20), Seat(0), STUDY)).drain()

        // The card their work IS at, taken offline. Same answer.
        val dark = T2Walk(base.withIsolated(Isolation(HALL, Tick(100))))
            .feed(Event.MarkerScanned(Tick(20), Seat(0), HALL)).drain()

        assertEquals(
            empty.map { it::class.simpleName }, dark.map { it::class.simpleName },
            "a dark card produced a different set of effects from an empty one",
        )
        assertNull(
            dark.filterIsInstance<Effect.ScanAnswered>().single().opened,
            "an Isolated card opened work",
        )
        // And the card is itself again once the isolation runs out — temporary, not a lock.
        val later = T2Walk(base.withIsolated(Isolation(HALL, Tick(100))))
            .feed(Event.MarkerScanned(Tick(100), Seat(0), HALL)).drain()
        assertNotNull(
            later.filterIsInstance<Effect.ScanAnswered>().single().opened,
            "the card never came back",
        )
    }

    /**
     * **The carrier lockout, as far as it can fall out of rules that exist** (`gdd.md:621`).
     *
     * *A Insider who Isolates the Disposal marker does not delay one task — they lock that player
     * out of **every** task until it clears.* The mechanism that has to hold for that to be true is
     * that **a station goes dark exactly like any other card**, with no special case anywhere, so
     * that a carrier standing at Disposal is answered with nothing and keeps holding the disk.
     *
     * ⚠️ **The other half is unbuildable today and this is where that is recorded.**
     * `AssignedWork.ArrayWipe` has no `opensAs`, `workOrderFor` deals no circuit, and there is no
     * carried state anywhere in the app (F-014, E7) — so there is no carrier to lock out. What is
     * asserted is the half this layer owns: a station is not privileged, and D-122's three cards are
     * ordinary markers that Isolate treats as ordinary markers. **Escalated, not skipped.**
     */
    @Test
    fun `an isolated station goes dark exactly as an ordinary marker does`() {
        // The circuit is not dealt, so a station is whatever the draw made one — asserted rather
        // than assumed, because the day a circuit IS dealt this test has to be revisited.
        val withStations = t2Armed().copy(stations = listOf(HALL, GARAGE, STUDY))
        assertTrue(
            withStations.stations.all { it in withStations.activeMarkers },
            "a station is an ordinary marker and must be in the active set",
        )
        assertTrue(
            withStations.workOrders.none { order ->
                order.entries.any { it.work is AssignedWork.ArrayWipe }
            },
            "a circuit is dealt now; the carrier lockout is buildable and this test owes more",
        )

        val ordinary = T2Walk(withStations.withIsolated(Isolation(LANDING, Tick(100))))
            .feed(Event.MarkerScanned(Tick(20), Seat(0), LANDING)).drain()
        val station = T2Walk(withStations.withIsolated(Isolation(STUDY, Tick(100))))
            .feed(Event.MarkerScanned(Tick(20), Seat(0), STUDY)).drain()

        assertEquals(
            ordinary.map { it::class.simpleName }, station.map { it::class.simpleName },
            "Isolate treats a station differently from an ordinary card",
        )
    }

    /**
     * **During an Egress the two nodes leave the target list, and come back when it is contained**
     * (D-150, F-002 amended).
     *
     * *The resolution is exclusion, not immunity: for the duration of an Egress the two nodes do not
     * appear in Isolate's target list.* Three moments in one walk, because the rule is about a
     * transition and a test that only looked at the middle of it would pass for a build that never
     * put them back.
     *
     * **It leaks nothing**, because the house names both nodes to every phone the instant it fires.
     *
     * ### And a fire at an excluded node costs the pool NOTHING
     *
     * The half of D-150 that is about the shared clock: *charging a shared resource for an action
     * that could not work is not concealment, it is a tax on a teammate's budget, paid in the dark
     * by somebody who will never learn why the pool was short later.*
     */
    @Test
    fun `the egress nodes leave the isolate list for the duration and return after`() {
        val opened = t2Arming()
        assertEquals(
            ACTIVE.map { it.value }.sorted(), t2Offered(opened, INSIDER)?.sorted(),
            "the opening list was not the round's active markers",
        )

        val fired = Tick(10)
        val walk = T2Walk(t2Armed())
            .feed(Event.EgressFired(fired, INSIDER, EgressType.Beacon, listOf(HALL, STUDY)))

        val during = assertNotNull(
            t2Offered(walk.drain(), OTHER_INSIDER),
            "no list was re-issued when the house caught fire",
        )
        assertEquals(
            listOf(GARAGE.value, LANDING.value).sorted(), during.sorted(),
            "the Egress nodes were still on Isolate's list",
        )

        // The excluded node, fired at anyway — a stale list, which is a real race rather than a
        // forge: the Egress can start between a list arriving and a finger landing on it.
        val before = t2Pool(walk.state, OTHER_INSIDER)
        walk.feed(Event.IsolateFired(Tick(11), OTHER_INSIDER, HALL))
        assertTrue(walk.state.isolated.isEmpty(), "a node was Isolated mid-Egress")
        assertEquals(
            before, t2Pool(walk.state, OTHER_INSIDER),
            "a refused node fire taxed the shared pool — D-150's whole objection to fake success",
        )
        assertEquals(
            listOf(false),
            walk.drain().filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "the actor was told a cooldown started that had not",
        )

        // Contained: two seats at the two nodes, both on the beat. The list comes back whole.
        walk.holding(
            walk.state
                .withPresence(home.someoneshome.model.Presence(Seat(0), HALL, open = false))
                .withPresence(home.someoneshome.model.Presence(Seat(2), STUDY, open = false)),
        )
        val at = Tick(beatAt(fired, 3))
        walk.feed(
            Event.SyncPulseReturned(at, Seat(0), t2OnBeat(fired)),
            Event.SyncPulseReturned(at, Seat(2), t2OnBeat(fired)),
        )
        assertNull(walk.state.egress, "the Egress was not contained; the rest of this proves nothing")
        assertEquals(
            ACTIVE.map { it.value }.sorted(), t2Offered(walk.drain(), INSIDER)?.sorted(),
            "the two nodes never came back onto the list after containment",
        )
    }

    /**
     * **An already-Isolated marker stays on the list**, and re-firing at one restarts it.
     *
     * Which cards another Insider has taken dark is not this Insider's to know, and a list that
     * omitted them would report it — the argument the node exclusion does not need, because the
     * nodes are public and an isolation is not.
     */
    @Test
    fun `an isolated marker stays on the list and can be isolated again`() {
        val walk = T2Walk(t2Armed().withIsolated(Isolation(HALL, Tick(50))))
        walk.feed(Event.IsolateFired(Tick(20), INSIDER, HALL))
        assertEquals(
            Tick(20 + Balance.ISOLATE_DURATION),
            walk.state.isolated.single { it.marker.value == HALL.value }.until,
            "re-firing at a dark card did not restart it",
        )

        // And the list the house offers still names it. Read off a fresh arming, which is where a
        // list is issued from state.
        val offered = t2Arming()
        assertEquals(
            ACTIVE.map { it.value }.sorted(), t2Offered(offered, INSIDER)?.sorted(),
            "a card was withheld from the list for a reason the house should not publish",
        )
    }

    /**
     * **The list is built for every seat, and nothing in the rules branches on role** (D-079).
     *
     * The narrowing is the allowlist's. This is the assertion the differential harness's finding
     * became: a rules-side filter made the arming burst role-dependent, which retires one of the
     * three independent leak instruments for every future change as well as for this one.
     */
    @Test
    fun `every seat is offered a list and the rules never look at a role`() {
        val effects = t2Arming()
        assertEquals(
            T2_SEATS.map { it.index },
            effects.filterIsInstance<Effect.IsolateTargetsOffered>().map { it.seat.index },
            "the rules issued a list to some seats and not others",
        )
        assertEquals(
            1, effects.filterIsInstance<Effect.IsolateTargetsOffered>()
                .map { it.targets.map { d -> d.marker.value } }.distinct().size,
            "two seats were offered different lists by the rules",
        )
    }
}
