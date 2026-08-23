package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ClientClass
import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **THE THREE RULES THAT ARE ABOUT THE WHOLE KIT RATHER THAN ABOUT ANY ABILITY** (`gdd.md:392`–
 * `:401`).
 *
 * Revoke, Egress, Surge, Static and Isolate are built in three different units by three different
 * hands, and every one of them has its own test file asserting its own behaviour. **None of those
 * files can see the rule that is being broken here**, because the rules in question are properties
 * of the *set*: they are true of the kit or they are not true at all, and an ability added later
 * satisfies them by accident or not at all.
 *
 * So this file sweeps. Each test names its rule, walks **every ability there is**, and is written
 * so that adding a sixth means adding a row here rather than discovering in playtest that the new
 * one is the only one that behaves differently.
 *
 * 1. **Self-targeting works on every ability and shares its clock** (`gdd.md:393`, `:401`).
 * 2. **A wasted fire appears to succeed on the actor's device** (`gdd.md:344`, rule 1).
 * 3. **No actor-side feedback on firing any ability** (`gdd.md:398`).
 *
 * ### Why these are worth a file rather than three more cases in `TierTwoTest`
 *
 * Rule M9 is the design's own section heading — *channel safety* — and its corollaries are marked
 * *all non-negotiable*. The reason it is a section rather than a note is that **each corollary is
 * cheap to keep for one ability and easy to lose across five**: the build that breaks it will be a
 * perfectly reasonable change to a single handler, reviewed by somebody looking at that handler.
 */
private val K_SEATS = (0 until 6).map { Seat(it) }
private val K_INSIDER = Seat(1)
private val K_SECOND_INSIDER = Seat(4)
private val K_RESIDENT = Seat(3)

private val K_HALL = MarkerId("hall-card")
private val K_GARAGE = MarkerId("garage-card")
private val K_STUDY = MarkerId("study-card")
private val K_ACTIVE = listOf(K_HALL, K_GARAGE, K_STUDY)

/**
 * A round with every Insider clock already run down, so that *a fire is a fire* for all five.
 *
 * The Revoke's arming list is empty on purpose: arming is a separate event and the tests that need
 * a landed Revoke walk it. What is pushed here is only the clocks.
 */
private fun kitArmed(at: Tick = Tick(0)): GameState = GameState.armedRound(
    seed = 20260823L,
    seats = K_SEATS,
    insiders = listOf(K_INSIDER, K_SECOND_INSIDER),
    systemIntegrity = 30,
    activeMarkers = K_ACTIVE,
    destinations = placed(K_ACTIVE),
    cooldowns = K_SEATS.flatMap {
        listOf(
            Cooldown(it, InsiderAbility.Revoke, at),
            Cooldown(it, InsiderAbility.Access, at),
        )
    },
    egressReadyAt = at,
)

private fun kitWalk(state: GameState, vararg events: Event): Pair<GameState, List<Effect>> {
    var next = state
    val out = mutableListOf<Effect>()
    for (event in events) {
        when (val admission = admit(next, event)) {
            is Admission.Admitted -> {
                next = admission.reduction.state
                out += admission.reduction.effects
            }
            is Admission.Refused -> Unit
        }
    }
    return next to out
}

/**
 * **Every ability in the game, as a fire aimed at a chosen seat and a name.**
 *
 * The Revoke's fire is a `ContactMade`, which is the only one that is *reported* rather than
 * requested — it resolves an identity off a rotating token — and it is on this list anyway, because
 * the three rules are about what an ability does to the actor's phone rather than about how the
 * request reached the house.
 */
private class Fire(
    val name: String,
    val ability: InsiderAbility,
    /**
     * What has to have happened before the fire means anything.
     *
     * Only the Revoke has one, and it is the two-step: `gdd.md`'s Revoke is *arm in-app, then touch
     * your phone to the target's*, so a `ContactMade` from a seat that never armed spends nothing
     * and lands nothing. **This field exists because leaving it out made the sweep pass vacuously**
     * — the first build of `self targeting works on every ability` reported *Revoke: a self-target
     * was free*, and it was right: the fixture had never armed one, so every Revoke row in every
     * sweep here was walking an event the rules correctly ignored.
     */
    val arm: (Tick) -> List<Event> = { emptyList() },
    val at: (Tick, Seat) -> Event,
)

/**
 * The five, and the two clocks between them.
 *
 * ⚠️ **`InsiderAbility` has two members and there are five abilities**, which is deliberate — see
 * `Cooldown`: Access is a clock, not an ability, and three of the five share it. That is precisely
 * why this list is written out by hand rather than derived from the enum: an enum-derived sweep
 * would walk two rows and call the kit covered.
 */
private val EVERY_ABILITY = listOf(
    Fire(
        "Revoke", InsiderAbility.Revoke,
        arm = { at -> listOf(Event.RevokeArmed(at, K_INSIDER)) },
    ) { at, target -> Event.ContactMade(at, K_INSIDER, target) },
    Fire("Surge", InsiderAbility.Access) { at, target -> Event.SurgeFired(at, K_INSIDER, target) },
    Fire("Static", InsiderAbility.Access) { at, _ -> Event.StaticFired(at, K_INSIDER) },
    Fire("Isolate", InsiderAbility.Access) { at, _ -> Event.IsolateFired(at, K_INSIDER, K_HALL) },
)

/**
 * The Egress, kept off [EVERY_ABILITY] because it has neither a target seat nor either of the two
 * per-seat clocks — it is D-151's house-wide timer, and a sweep that tried to read
 * `cooldownFor(seat, ...)` for it would be asking the wrong state a question.
 *
 * It is still walked by every rule below; it is just walked separately and said so.
 */
private fun egressFire(at: Tick): Event =
    Event.EgressFired(at, K_INSIDER, EgressType.Tether, listOf(K_GARAGE, K_STUDY))

class AbilityRulesTest {

    /**
     * **RULE 1 OF THREE: self-targeting works on every ability, and shares that ability's clock**
     * (`gdd.md:393`, `:401`).
     *
     * *Insiders can target themselves with every ability. If only Residents could ever be targeted,
     * then being visibly targeted would be evidence of innocence, and every ability would double as
     * an alignment test.* And: *self-targeting shares the ability's cooldown — otherwise
     * self-sabotage is a free alibi generator. Buying cover must cost offensive capability.*
     *
     * Both halves, for each ability that has a target: the fire aimed at the actor's own seat
     * produces **the same effect shapes** as one aimed at somebody else, and it spends **the same
     * clock by the same amount**.
     *
     * ### Static's reading, written down because it is not obvious
     *
     * `gdd.md:393` says *every ability*, and Static has no target of any kind. Read as a gap, that
     * would make Static the one ability that cannot be self-inflicted — which is exactly the shape
     * the corollary forbids. Read correctly, **Static is self-inflicted every time it is fired**:
     * the jam is house-wide, so it covers the firing Insider's own monitoring, and an Insider
     * standing at the Terminal when their own Static lands reads the same noise a Resident does.
     * There is no *other*-targeted variant to compare against because there is no target.
     *
     * That is asserted below in the only form available: the fire is walked, the pool is charged,
     * and the jam that comes out covers the house the actor is standing in.
     */
    @Test
    fun `self targeting works on every ability and spends the same clock`() {
        for (fire in EVERY_ABILITY) {
            val (atSelf, selfEffects) =
                kitWalk(kitArmed(), *fire.arm(Tick(5)).toTypedArray(), fire.at(Tick(10), K_INSIDER))
            val (atOther, otherEffects) =
                kitWalk(kitArmed(), *fire.arm(Tick(5)).toTypedArray(), fire.at(Tick(10), K_RESIDENT))

            assertEquals(
                otherEffects.map { it::class.simpleName },
                selfEffects.map { it::class.simpleName },
                "${fire.name}: a self-target came back a different shape from an other-target",
            )
            assertEquals(
                assertNotNull(atOther.cooldownFor(K_INSIDER, fire.ability)).readyAt,
                assertNotNull(atSelf.cooldownFor(K_INSIDER, fire.ability)).readyAt,
                "${fire.name}: self-targeting is cheaper than targeting somebody else",
            )
            assertTrue(
                assertNotNull(atSelf.cooldownFor(K_INSIDER, fire.ability)).readyAt > Tick(10),
                "${fire.name}: a self-target was free",
            )
        }

        // **Static satisfies self-targeting by jamming the actor's own monitoring with everybody
        // else's** (D-186). There is no target seat to point at itself and nothing here to
        // special-case: what is asserted is that the jam the actor bought covers the whole house,
        // which necessarily includes the phone that fired it.
        val (jammed, _) = kitWalk(kitArmed(), Event.StaticFired(Tick(10), K_INSIDER))
        assertTrue(
            jammed.staticRunning(Tick(11)),
            "Static: the actor's own fire left the house unjammed",
        )

        // And the Egress, on the house-wide clock rather than either per-seat one. There is no
        // target to aim at itself; what is asserted is that firing costs the clock, which is the
        // half of the rule that is meaningful for an ability nobody can be the target of.
        val (afterEgress, _) = kitWalk(kitArmed(), egressFire(Tick(10)))
        assertEquals(
            Tick(10) + Balance.EGRESS_COOLDOWN, afterEgress.egressReadyAt,
            "Egress: the house-wide clock was not spent",
        )
    }

    /**
     * **RULE 2 OF THREE: a wasted fire appears to succeed on the actor's device** (`gdd.md:344`,
     * project rule 1).
     *
     * *Targeting an already-revoked player must appear to succeed on the actor's device — same
     * animation, same cooldown spent, no feedback whatsoever. **Otherwise the panel becomes a
     * revoke-detector**: a Insider could poll the roster and learn exactly who is out without ever
     * finding them.*
     *
     * T1 asserted this for the Surge against a Revoked target. **Extended here across the kit**,
     * and generalised past *revoked* to the real claim: whatever makes a shot worthless, the actor's
     * phone receives the same thing.
     *
     * Three kinds of waste are walked, because they fail differently in the code:
     *
     * - **A target who is already out.** The rules never ask, and the boundary declines delivery.
     * - **A target who is a fellow Insider.** The one every "helpful" branch gets added for.
     *
     * ### ⚠️ The actor targeting THEMSELVES is deliberately not one of them
     *
     * The first build of this test swept it and failed on the Surge, reporting that a self-surge
     * told the actor's phone something a landed shot does not. **The test was wrong and the build
     * was right**, and the distinction is worth stating because getting it backwards breaks the
     * game in one direction or the other.
     *
     * A self-surge lands on the actor's phone **because they are the target**, not because they
     * fired: *you appear to be attacked in the most dramatic way available, but it burns your
     * longest cooldown and pulls the whole house to your location* (`gdd.md:342`). A build that
     * suppressed it to keep the actor's device inert would delete the premium alibi play and would
     * make being visibly surged into proof you did not fire it. That case is asserted on its own
     * below, where the claim is the sharp one: a self-surge is **indistinguishable from being
     * surged by somebody else**.
     *
     * The assertion is on the **effects the actor receives**, resolved through the emit boundary
     * rather than off the raw reduction — because that is what the phone actually gets, and a build
     * that leaked through addressing rather than through the rules would pass a raw comparison.
     */
    @Test
    fun `a wasted fire looks exactly like a landed one on the actors own device`() {
        for (fire in EVERY_ABILITY) {
            val prelude = fire.arm(Tick(5)).toTypedArray()
            val landed = kitWalk(kitArmed(), *prelude, fire.at(Tick(10), K_RESIDENT))

            // The target is out. Walked through the rules rather than hand-set, so the state a
            // wasted shot meets is a state a round actually produces — and revoked by the OTHER
            // Insider, so this seat's own arming is untouched.
            val revokedFirst = kitWalk(
                kitArmed(),
                Event.RevokeArmed(Tick(1), K_SECOND_INSIDER),
                Event.ContactMade(Tick(2), K_SECOND_INSIDER, K_RESIDENT),
            ).first
            val atOut = kitWalk(revokedFirst, *prelude, fire.at(Tick(10), K_RESIDENT))
            val atInsider = kitWalk(kitArmed(), *prelude, fire.at(Tick(10), K_SECOND_INSIDER))

            // **A fire into a clock that is still running**, which is the waste an Insider produces
            // most often and the one project rule 1 is written about: *never early-return on
            // invalid — the absent effect IS the leak.* Walked by firing twice, so the state the
            // second shot meets is one the rules produced rather than one this test hand-set.
            //
            // ⚠️ **This case was missing and an injection found it.** Making the third handler
            // return
            // `Reduction(state, emptyList())` on a running pool failed nothing: the sweep had only
            // ever walked wasteful *targets*, never a wasteful *moment*, so the one shape rule the
            // project puts first was unguarded for every ability on the list.
            val onCooldown = kitWalk(
                kitWalk(kitArmed(), *prelude, fire.at(Tick(10), K_RESIDENT)).first,
                *fire.arm(Tick(11)).toTypedArray(),
                fire.at(Tick(12), K_RESIDENT),
            )

            for ((what, walked) in listOf(
                "a target already out" to atOut,
                "a fellow Insider" to atInsider,
                "a clock that had not come back" to onCooldown,
            )) {
                assertEquals(
                    actorAlone(landed.second, landed.first),
                    actorAlone(walked.second, walked.first),
                    "${fire.name}: $what told the firing phone something a landed shot does not",
                )
            }
        }

        // ### ⚠️ The Egress's waste is asserted on the NARROWER claim, and the difference is the
        // design's own
        //
        // A landed Egress sends `EgressOpened` to every phone in the building and re-lists the two
        // nodes off every Insider's Isolate picker (D-150); a wasted one sends neither. So the two
        // do **not** produce the same arrivals on the actor's phone — and they are not required to.
        // Project rule 1's guarantee is *device-side only*, and the Egress is the most
        // world-observable thing in the game: every lamp in the house dims and every phone buzzes
        // Long (D-118, D-135). Nobody has to check a screen to know one started.
        //
        // What must still hold is the part that is about the actor alone: **nothing arrives on the
        // firing phone that does not arrive on somebody else's**. That is `actorAlone`, and it is
        // the same claim the sweep above makes — asked in the form that survives an ability whose
        // landing is announced to the room.
        val spent = kitWalk(kitArmed(), egressFire(Tick(10))).first
        val landedEgress = kitWalk(kitArmed(), egressFire(Tick(10)))
        val wastedEgress = kitWalk(spent, egressFire(Tick(11)))
        assertEquals(
            actorAlone(landedEgress.second, landedEgress.first),
            actorAlone(wastedEgress.second, wastedEgress.first),
            "Egress: a fire into a running cooldown told the firing phone something a landed one " +
                "does not, over and above what the whole house was told",
        )
    }

    /**
     * **RULE 3 OF THREE: no actor-side feedback on firing any ability** (`gdd.md:398`).
     *
     * *No actor-side feedback on firing any ability. The attacker's device stays inert. **All
     * observable effect happens on the target.***
     *
     * A completeness check rather than a walk: for every ability, the set of effects that actually
     * reach the **actor's** phone must be `AbilityFired` and nothing else. `AbilityFired` is the one
     * permitted arrival and it is not feedback about the shot — it carries `cooldownStarted`, which
     * is a fact about the actor's own clock, drawn on the actor's own tile as input echo.
     *
     * ### Why this is asserted through the emit boundary and not off the reduction
     *
     * The rules emit plenty that names the actor's seat. What matters is what is **delivered**, and
     * that is two independent things — the allowlist's row and the effect's addressing — neither of
     * which the reduction knows about. A test read off `reduction.effects` would pass on a build
     * whose `SURGED` row had been widened to reach the person who fired it.
     *
     * ### Injecting the violation
     *
     * Give `PHANTOM_PLACED` a row of `Insider/Live`, or add any field-carrying effect addressed to
     * the actor, and this fails naming the ability and the kind.
     */
    @Test
    fun `nothing but the cooldown state ever reaches the phone that fired`() {
        val permitted = setOf(EmitSchema.ABILITY_FIRED)

        for (fire in EVERY_ABILITY) {
            // **Targets other than the actor.** When the actor IS the target, what arrives on their
            // phone arrives as the TARGET's and is required to — see the self-surge test below, and
            // `gdd.md:342`. `gdd.md:398`'s claim is about the attacker's device, which is only a
            // separate device from the victim's when the two are different seats.
            for (target in listOf(K_RESIDENT, K_SECOND_INSIDER)) {
                val (state, effects) =
                    kitWalk(kitArmed(), *fire.arm(Tick(5)).toTypedArray(), fire.at(Tick(10), target))
                val alone = actorAlone(effects, state)
                assertTrue(
                    alone.all { it in permitted },
                    "${fire.name} at $target: the firing phone received ${alone - permitted}, " +
                        "which no other phone received — that is feedback that it fired",
                )
            }
        }

        // The Egress is the exception the design already made, and it is asserted rather than
        // skipped: `EgressOpened` reaches the actor because it reaches EVERY phone in the building
        // at once (D-076, D-118) — a dimming lamp is world-observable, so a notification narrower
        // than everybody would be a beacon. It is not feedback about who fired; it is the house
        // announcing an Egress to the house.
        val (state, effects) = kitWalk(kitArmed(), egressFire(Tick(10)))
        val opened = effects.filterIsInstance<Effect.EgressOpened>()
        assertEquals(
            K_SEATS.map { it.index }, opened.map { it.seat.index },
            "Egress: the alarm reached a subset of the house, which is a beacon (D-076)",
        )
        assertEquals(
            setOf(EmitSchema.ABILITY_FIRED), actorAlone(effects, state),
            "Egress: the firing phone received something no other phone did",
        )
    }

    /**
     * **A SELF-SURGE IS INDISTINGUISHABLE FROM BEING SURGED BY SOMEBODY ELSE** (`gdd.md:342`,
     * `:393`).
     *
     * The other side of `gdd.md:398`, and the reason the sweep above walks targets who are not the
     * actor. *Self-surge is the premium alibi play: you appear to be attacked in the most dramatic
     * way available, but it burns your longest cooldown and pulls the whole house to your location.*
     *
     * For that play to exist, what arrives on the actor's phone when they surge themselves has to
     * be **exactly** what arrives when the other Insider surges them: same effect, same luminances,
     * same sound, same haptic. Anything that differed — a suppressed lamp, a missing sound, a flag
     * — would make the alibi detectable **by the person using it**, and then by anybody who ever
     * watched them use it.
     *
     * ### The two directions this can be broken, and both are plausible changes
     *
     * A build that suppressed the self-landing to honour *the attacker's device stays inert* would
     * fail here. A build that marked it — so the picker could grey out your own row afterwards, say
     * — would fail here too. Both are the sort of change somebody makes for a good local reason.
     *
     * `AbilityFired` is excluded from the comparison and named as excluded: it is the actor's own
     * clock, it arrives on whichever phone pressed the control, and it is the one thing the two
     * cases are *supposed* to differ by.
     */
    @Test
    fun `a self surge reaches the actor exactly as an attack from across the house does`() {
        val (selfState, selfEffects) =
            kitWalk(kitArmed(), Event.SurgeFired(Tick(10), K_INSIDER, K_INSIDER))
        val (otherState, otherEffects) =
            kitWalk(kitArmed(), Event.SurgeFired(Tick(10), K_SECOND_INSIDER, K_INSIDER))

        fun landedOn(effects: List<Effect>, state: GameState): List<Effect> = effects
            .filter { EmitSchema.kindOf(it) != EmitSchema.ABILITY_FIRED }
            .filter { effect ->
                EmitSchema.deliveries(effect, state).any { it.seat.index == K_INSIDER.index }
            }

        assertEquals(
            landedOn(otherEffects, otherState),
            landedOn(selfEffects, selfState),
            "a self-surge is not what being attacked looks like; the alibi is detectable",
        )
        // And it is not nothing, which is what makes the comparison mean something: a build that
        // delivered neither would satisfy the line above and would have deleted the ability.
        assertTrue(
            landedOn(selfEffects, selfState).any { it is Effect.Surged },
            "a self-surge landed nothing at all on the actor's own phone",
        )
    }

    /**
     * **The one surviving tier-2 picker publishes nothing to a Resident, whatever the house builds.**
     *
     * The rules construct the Isolate list **for every seat** — that is `isolateOffers`' recorded
     * finding, and the arming burst has to stay role-independent or the differential harness stops
     * working. So the whole of the narrowing is the allowlist, and this asserts it at the boundary
     * the arming burst actually crosses.
     *
     * ### ⚠️ There were two of these and D-186 deleted one
     *
     * The home's plan rode the same burst under the same argument and it is **gone**: Static has no
     * picker, so there is no plan to narrow and no row to widen by mistake. `StaticTest.the arming
     * burst is four runs and none of them is a plan` is the other end of the same claim — this one
     * says the surviving picker is still narrowed, that one says nothing took the deleted one's
     * place.
     *
     * Read off a real arming rather than off hand-built effects, because what is being checked is
     * that **the burst a round produces** reaches a Resident with nothing tier-2 in it.
     */
    @Test
    fun `the arming burst hands a Resident no picker and no plan`() {
        val reduction = reduce(
            GameState.EMPTY,
            armingFor(
                Tick(1), 20260823L, K_SEATS, chosenInsiders = null,
                markers = placed(K_ACTIVE),
            ),
        )
        val insiders = reduction.state.insiderSeats.map { it.index }.toSet()
        val residents = K_SEATS.map { it.index }.filterNot { it in insiders }

        val tierTwo = setOf(EmitSchema.ISOLATE_TARGETS_OFFERED)
        for (seat in residents) {
            val reached = reduction.effects
                .flatMap { EmitSchema.deliveries(it, reduction.state) }
                .filter { it.seat.index == seat }
                .map { it.kind }
                .toSet()
            assertEquals(
                emptySet(), reached intersect tierTwo,
                "seat $seat is a Resident and was handed the round's geography at arming",
            )
        }
        // And the other direction, so this does not pass on a build that publishes to nobody.
        assertTrue(insiders.isNotEmpty(), "the fixture drew no Insider and proves nothing")
        for (seat in insiders) {
            val reached = reduction.effects
                .flatMap { EmitSchema.deliveries(it, reduction.state) }
                .filter { it.seat.index == seat }
                .map { it.kind }
                .toSet()
            assertEquals(
                tierTwo, reached intersect tierTwo,
                "seat $seat is an Insider and was handed no picker at all",
            )
        }
        assertTrue(
            EmitSchema.classesFor(EmitSchema.ISOLATE_TARGETS_OFFERED) ==
                listOf(ClientClass(Role.Insider, RoundState.Live)),
            "the picker's row moved and this sweep would no longer be about the allowlist",
        )
    }
}

/**
 * **THE KINDS THAT REACHED THE FIRING PHONE AND NO OTHER PHONE.**
 *
 * `gdd.md:398` — *the attacker's device stays inert; all observable effect happens on the target* —
 * is a claim about **asymmetry**, not about silence. The Egress is what makes that distinction
 * necessary rather than pedantic: firing one dims every lamp in the building and buzzes every phone
 * Long, and the actor's phone is one of those phones. A test that asked *did the actor receive
 * anything* would report that as a violation and be wrong; the alarm is not feedback about who
 * fired, it is the house announcing an Egress to the house.
 *
 * So what is measured is the **difference**: kinds delivered to the firing seat and to nobody else.
 * That set is what a bystander could never account for, and it is exactly what would make an
 * Insider's phone identifiable if somebody glanced at it.
 *
 * Kinds rather than payloads, because a `Delivery` carries no effect to read one off — and because
 * the channel being closed is *which messages arrive*, which a payload comparison only samples.
 */
private fun actorAlone(effects: List<Effect>, state: GameState): Set<MessageKind> {
    val byKind = effects.flatMap { EmitSchema.deliveries(it, state) }
    val toActor = byKind.filter { it.seat.index == K_INSIDER.index }.map { it.kind }.toSet()
    val toAnyoneElse = byKind.filterNot { it.seat.index == K_INSIDER.index }.map { it.kind }.toSet()
    return toActor - toAnyoneElse
}
