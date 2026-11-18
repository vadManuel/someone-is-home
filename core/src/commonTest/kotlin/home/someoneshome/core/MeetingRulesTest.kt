package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **What a meeting does to everything that was running when it was called** — D-183, D-184 and
 * D-185, which close T1·7 / T2·8 and T1·9 / T2·9 and answer `gdd.md:373`'s open *pauses or resets*.
 *
 * Three rules, and they are one decision seen from three sides: **a meeting is a hard stop.** Tier 2
 * cannot be fired at one, every running ability effect fails at the call, and every per-Insider clock
 * comes out of it at half — which is the round's opening handed to the Insiders again.
 *
 * **The Egress is the exception to all three**, and it is the same exception each time: it is a win
 * condition with a countdown the whole room is looking at, so it is paused rather than cleared
 * (D-133), it survives the meeting, and its cooldown is frozen out of the reset while it runs.
 */
class MeetingRulesTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insider = Seat(1)
    private val card = MarkerId("CARD-11")

    private fun armed(): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(insider)),
    ).state

    /** A round with every per-seat clock ready, so a refusal below is the meeting's and not a pool's. */
    private fun ready(at: Tick): GameState = armed().let { state ->
        state.copy(
            cooldowns = state.cooldowns.map {
                home.someoneshome.model.Cooldown(it.seat, it.ability, at)
            },
            egressReadyAt = at,
        )
    }

    private fun calling(state: GameState, at: Tick): GameState =
        reduce(state, Event.MeetingCalled(at, Seat(0), MeetingTrigger.MeetingCard)).state

    // ---- 1 · Tier 2 is refused at a meeting (T1·7 / T2·8) ---------------------------------------

    /**
     * **All three Tier-2 fires are refused during a meeting, and the refusal costs nothing** (D-183).
     *
     * *There is no surface to fire from*: springboard page 2 does not exist during a meeting for any
     * seat of any role, so no honest client can produce the intent and the refusal is not a
     * client-visible path at all. Rule 1 is about an effect a player should have seen and did not,
     * and nobody can see an effect on a screen nobody is standing on.
     *
     * **Refused at the gate and never inside the rules**, which is what makes it cost nothing: the
     * event does not reach the handler that would spend the pool. Written inside `reduce` it would
     * be `if (…) return Reduction(state, emptyList())`, which is rule 1's forbidden shape and the
     * one that looks most like hygiene.
     *
     * All three are walked rather than one, because the ruling is about the tier and a build that
     * refused two of them is the failure worth catching.
     */
    @Test
    fun `every Tier-2 fire is refused at a meeting and the pool is not spent`() {
        val at = Tick(100)
        val meeting = calling(ready(at), at)
        val poolBefore = assertNotNull(meeting.cooldownFor(insider, InsiderAbility.Access)).readyAt

        val fires = listOf(
            Event.SurgeFired(Tick(200), insider, Seat(3)),
            Event.IsolateFired(Tick(200), insider, card),
            Event.StaticFired(Tick(200), insider),
        )
        for (fire in fires) {
            val admission = admit(meeting, fire)
            assertTrue(
                admission is Admission.Refused,
                "${fire::class.simpleName} reached the rules from inside a meeting",
            )
            assertEquals(
                RefusalReason.MeetingAlreadyRunning, admission.reason,
                "${fire::class.simpleName} was refused for a reason a player could not have read " +
                    "off the room",
            )
        }

        assertEquals(
            poolBefore, assertNotNull(meeting.cooldownFor(insider, InsiderAbility.Access)).readyAt,
            "a refused fire spent the pool — a refusal at the gate must cost nothing",
        )
    }

    /**
     * **And they are admitted the moment there is no meeting**, which is what says the refusal is
     * about the meeting rather than about the ability.
     *
     * The negative half matters more than usual here: a gate that refused Tier 2 outright would
     * pass every assertion above and would delete three abilities from the game, silently, in a way
     * whose only symptom is that nothing ever happens.
     */
    @Test
    fun `Tier 2 is admitted in an ordinary shift`() {
        val at = Tick(100)
        val quiet = ready(at)
        assertNull(quiet.meeting, "the fixture had a meeting running")

        for (fire in listOf(
            Event.SurgeFired(Tick(200), insider, Seat(3)),
            Event.IsolateFired(Tick(200), insider, card),
            Event.StaticFired(Tick(200), insider),
        )) {
            assertTrue(
                admit(quiet, fire) is Admission.Admitted,
                "${fire::class.simpleName} was refused in a round with no meeting in it",
            )
        }
    }

    /**
     * **The Revoke is NOT refused at a meeting, and the boundary is the ruling's.**
     *
     * Tier 1 arms in place on a page the player is already standing on (D-142) and the ruling names
     * Tier 2. A Revoke armed at a meeting is a botched stalk that costs its own cooldown, which is
     * the ordinary case rather than a rule about meetings — and a build that generalised the
     * refusal to *abilities* would take Tier 1 with it.
     */
    @Test
    fun `arming a Revoke at a meeting is not refused`() {
        val at = Tick(100)
        val meeting = calling(ready(at), at)
        assertTrue(
            admit(meeting, Event.RevokeArmed(Tick(200), insider)) is Admission.Admitted,
            "the Tier-2 refusal generalised to Tier 1 and took the Revoke with it",
        )
    }

    // ---- 2 · A meeting ends every running ability effect (T1·9) ---------------------------------

    /**
     * **An isolation fails at the call, and so does a Static jam** (D-185, D-186).
     *
     * An Isolation is a card gone dark in a house nobody is walking, and a jam is a monitoring
     * surface drowned in noise that every living player has this second stopped standing in front
     * of — so both spend a meeting affecting nothing. T1·9's *a meeting can eat one whole* is
     * answered by not making anybody spend it.
     *
     * **At the call and not at the end**, which is the half worth asserting: the meeting has four
     * phases and the effects are useless from the first of them.
     */
    @Test
    fun `an isolation and a static jam fail at the meeting call`() {
        val at = Tick(100)
        val running = ready(at)
            .withIsolated(home.someoneshome.model.Isolation(card, Tick(at.step + Balance.ISOLATE_DURATION)))
            .withStatic(Tick(at.step + Balance.STATIC_DURATION))
        assertTrue(running.isIsolated(card, Tick(at.step + 1)), "the fixture's card was never dark")
        assertTrue(running.staticRunning(Tick(at.step + 1)), "the fixture was never jammed")

        val meeting = calling(running, at)
        assertEquals(emptyList(), meeting.isolated, "an isolation survived the call")
        assertNull(meeting.staticUntil, "a Static jam survived the call")
    }

    /**
     * **Nothing is emitted about either, and that is the whole of what keeps them silent.**
     *
     * A card going dark is discovered by walking to it (`isolateFired` emits one `AbilityFired` and
     * nothing else), so a card coming back is discovered the same way. A message whose *arrival*
     * reported that an ability had been running would be an Insider announced by a clean-up.
     */
    @Test
    fun `nothing is emitted about an ability effect ending`() {
        val at = Tick(100)
        val running = ready(at)
            .withIsolated(home.someoneshome.model.Isolation(card, Tick(at.step + Balance.ISOLATE_DURATION)))

        val withEffect = reduce(running, Event.MeetingCalled(at, Seat(0), MeetingTrigger.MeetingCard))
        val without = reduce(ready(at), Event.MeetingCalled(at, Seat(0), MeetingTrigger.MeetingCard))

        assertEquals(
            without.effects.map { it::class.simpleName },
            withEffect.effects.map { it::class.simpleName },
            "a meeting called with an isolation running produced a different set of effects from " +
                "one called without — the clean-up is a channel",
        )
    }

    /**
     * **The Egress survives, paused** (D-133).
     *
     * The one exception, and it is not a softer version of the rule: a report that cancelled an
     * Egress would make every Egress end the same way, so it is held rather than cleared and the
     * remaining time across the meeting is exactly what it was when the party was called in.
     */
    @Test
    fun `the Egress survives a meeting and is paused`() {
        val at = Tick(100)
        val fired = reduce(
            ready(at),
            Event.EgressFired(at, insider, EgressType.Beacon, listOf(card, MarkerId("CARD-12")), pulseFrom = 4_000_000L),
        ).state
        // Measured at the CALL and not at the fire: the countdown legitimately ran for the hundred
        // ticks of shift in between, and comparing against the fire would assert that a meeting
        // rewinds an Egress rather than that it holds one.
        val called = Tick(200)
        val atTheCall = assertNotNull(fired.egress).remainingAt(called)

        val meeting = calling(fired, called)
        val held = assertNotNull(meeting.egress, "the meeting cleared the Egress along with the rest")
        assertNotNull(held.pausedAt, "the Egress survived the call but was not paused")
        assertEquals(
            atTheCall, held.remainingAt(Tick(4_000)),
            "the countdown moved while the whole party stood in one room",
        )
    }

    // ---- 3 · Every cooldown comes out of a meeting at half --------------------------------------

    /**
     * **A meeting hands every Insider the round's opening again** — D-132's *half at arming*,
     * arriving a second time, and `gdd.md:373`'s *pauses or resets* answered.
     *
     * **Both rows, off the tick the lights went out.** A build that reset the pool and left the
     * Revoke where it was would pass any assertion that only looked at one, and the two clocks are
     * the ruling's own pair.
     */
    @Test
    fun `every per-seat cooldown comes out of a meeting at half`() {
        val called = Tick(100)
        val out = Tick(400)
        val spent = ready(called).let { state ->
            state.copy(
                cooldowns = state.cooldowns.map {
                    home.someoneshome.model.Cooldown(it.seat, it.ability, Tick(9_999))
                },
            )
        }

        var meeting = calling(spent, called)
        meeting = meeting.copy(meeting = meeting.meeting?.copy(phase = MeetingPhase.Tally))
        val after = reduce(meeting, Event.MeetingClosed(out)).state

        assertEquals(
            Tick(out.step + Balance.REVOKE_COOLDOWN / 2),
            assertNotNull(after.cooldownFor(insider, InsiderAbility.Revoke)).readyAt,
            "the Revoke did not come out of the meeting at half",
        )
        assertEquals(
            Tick(out.step + Balance.ACCESS_COOLDOWN / 2),
            assertNotNull(after.cooldownFor(insider, InsiderAbility.Access)).readyAt,
            "the Access pool did not come out of the meeting at half",
        )
    }

    /**
     * **Every seat, and not only the Insiders' — a list whose membership was the Insider list would
     * BE the Insider list.**
     *
     * The same argument arming makes, and it is the one that would be quietly lost by a reset that
     * looped over `insiderSeats` because that reads more efficient. A Resident carries two cooldowns
     * they can never spend, and that is the point.
     */
    @Test
    fun `the reset reaches every seat so the rows say nothing about who is what`() {
        val out = Tick(400)
        var meeting = calling(ready(Tick(100)), Tick(100))
        meeting = meeting.copy(meeting = meeting.meeting?.copy(phase = MeetingPhase.Tally))
        val after = reduce(meeting, Event.MeetingClosed(out)).state

        for (seat in seats) {
            assertEquals(
                Tick(out.step + Balance.REVOKE_COOLDOWN / 2),
                assertNotNull(after.cooldownFor(seat, InsiderAbility.Revoke)).readyAt,
                "seat ${seat.index} came out of the meeting on a different clock from the rest",
            )
        }
    }

    /**
     * **The Egress's house-wide clock resets with the rest when nothing is on fire, and is frozen
     * out of the reset while an Egress runs.**
     *
     * Both halves, because they are the two directions of one ruling and each hides the other's
     * failure. A reset that reached it mid-crisis would hand the Insiders a second Egress to fire
     * on top of the one the Residents are still crossing the house to contain — and D-131 already
     * makes a running Egress outlive its Insiders, so the two together would put the round beyond
     * the Residents on a rule about cooldowns.
     */
    @Test
    fun `the Egress cooldown resets with the rest unless its own Egress is running`() {
        val out = Tick(400)

        // Quiet house: it resets like everything else.
        var quiet = calling(ready(Tick(100)).copy(egressReadyAt = Tick(9_999)), Tick(100))
        quiet = quiet.copy(meeting = quiet.meeting?.copy(phase = MeetingPhase.Tally))
        assertEquals(
            Tick(out.step + Balance.EGRESS_COOLDOWN / 2),
            reduce(quiet, Event.MeetingClosed(out)).state.egressReadyAt,
            "the shared clock did not come out of a quiet meeting at half",
        )

        // House on fire: frozen until containment.
        val fired = reduce(
            ready(Tick(100)),
            Event.EgressFired(Tick(100), insider, EgressType.Beacon, listOf(card, MarkerId("CARD-12")), pulseFrom = 4_000_000L),
        ).state
        val frozen = fired.egressReadyAt
        var crisis = calling(fired, Tick(200))
        crisis = crisis.copy(meeting = crisis.meeting?.copy(phase = MeetingPhase.Tally))
        val after = reduce(crisis, Event.MeetingClosed(out)).state

        assertNotNull(after.egress, "the Egress did not survive the meeting")
        // Not reset, and pushed back by exactly the meeting's length, 200 to 400 (decision pack 9f
        // option a, D-282): the cooldown outlives its own Egress however long the meeting held it.
        assertEquals(
            Tick(frozen.step + (out.step - 200)), after.egressReadyAt,
            "a meeting reset the shared Egress clock while an Egress was still running, or did not " +
                "push it back by the pause — the Insiders were handed a second one on top of the first",
        )
    }

    // ---- 4 · The lie that is gone (T1·8 / T2·7) --------------------------------------------------

    /**
     * **`egressFired` tells the truth about the actor's own clock, and the SHAPE does not move.**
     *
     * It hardcoded `true`, so an Egress fired into a running cooldown told that phone it had spent
     * one it did not and the bar was wrong for three minutes. What is asserted here is both halves
     * at once, because they pull against each other and only one of them is rule 1: **one
     * `AbilityFired`, addressed to the actor, whatever happened** — and the flag inside it reporting
     * what the actor's own clock actually did.
     */
    @Test
    fun `a fire into a running cooldown reports the cooldown it did not spend`() {
        val at = Tick(100)
        val first = reduce(
            ready(at),
            Event.EgressFired(at, insider, EgressType.Beacon, listOf(card, MarkerId("CARD-12")), pulseFrom = 4_000_000L),
        )
        assertEquals(
            listOf(true),
            first.effects.filterIsInstance<Effect.AbilityFired>().map { it.cooldownStarted },
            "the fire that started the Egress did not report its own cooldown spent",
        )

        val second = reduce(
            first.state,
            Event.EgressFired(Tick(200), insider, EgressType.Tether, listOf(card, MarkerId("CARD-12")), pulseFrom = 4_000_000L),
        )
        val answered = second.effects.filterIsInstance<Effect.AbilityFired>()
        assertEquals(
            listOf(false), answered.map { it.cooldownStarted },
            "a fire into the shared cooldown told that phone it had spent one it did not (T1·8)",
        )
        assertEquals(
            first.effects.filterIsInstance<Effect.AbilityFired>().size, answered.size,
            "the refused fire came back a different SHAPE from the accepted one — rule 1",
        )
        assertEquals(listOf(insider), answered.map { it.actor }, "it was addressed to somebody else")
    }
}
