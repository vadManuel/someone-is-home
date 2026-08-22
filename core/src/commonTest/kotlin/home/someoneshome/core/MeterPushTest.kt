package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.ClientClass
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The meter reaching a client at all — E-C2-2, closed — and the two different clocks it reaches
 * two audiences on.**
 *
 * D-134 gives a player outside the system the real bars **live**; `gdd.md:192` and `:1002` give the
 * living a number **only at meetings**, batched and then frozen. Before this no effect carried the
 * meter to anybody: `SubroutineGraded` says one thing about one entry and deliberately says nothing
 * about the bar, and `PanelVals` refused to hold an `integrity` field because it would have been a
 * field with no sender.
 *
 * ### Two kinds, because the difference is timing and an allowlist cannot express timing
 *
 * That is what most of this file is about. One kind permitted to both audiences would hand the
 * living every movement the instant it happened, which is a rate signal — *who is working, and how
 * fast* — and it is the per-player read the percentage form exists to keep out of aggregate. So the
 * separation has to live in the **kinds**, and both directions are asserted here: the living get
 * nothing during a shift, and the out get everything.
 */
class MeterPushTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insiders = listOf(Seat(1))
    private val home = (0 until 8).map { MarkerId("m$it") }

    private fun armed(): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 20260822L, seats = seats, insiders = insiders,
            markers = placed(home),
        ),
    ).state

    /** Walk a seat through a scan and a correct hand-over, collecting what the rules emitted. */
    private class Walk(start: GameState) {
        var state: GameState = start
            private set
        val emitted = mutableListOf<Effect>()

        fun step(event: Event): Walk {
            val admission = admit(state, event)
            if (admission is Admission.Admitted) {
                state = admission.reduction.state
                emitted += admission.reduction.effects
            }
            return this
        }

        /**
         * **Put one seat outside the system**, the way the round does it: an Insider arms a Revoke
         * once the opening cooldown has run down (D-132) and touches phones with the target.
         *
         * Needed before any delivery assertion means anything — in a round where nobody is out, the
         * two out classes do not exist and *the live meter reached no out phone* is true of a build
         * that never sends it.
         */
        fun revoke(actor: Seat, target: Seat, at: Long): Walk =
            step(Event.RevokeArmed(Tick(at), actor))
                .step(Event.ContactMade(Tick(at + 1), actor, target))

        /** Scan this seat's own next card and hand over exactly what the house asked for. */
        fun bank(seat: Seat, at: Long): Walk {
            val order = state.workOrderFor(seat) ?: return this
            val entry = order.entries.firstOrNull { order.isActionable(it) } ?: return this
            step(Event.MarkerScanned(Tick(at), seat, entry.anchor!!))
            val asked = state.openSubroutineFor(seat)?.expected ?: return this
            return step(Event.SubroutineReturned(Tick(at + 1), seat, entry.anchor!!, asked))
        }
    }

    private fun deliveredTo(walk: Walk, kind: home.someoneshome.model.MessageKind): List<ClientClass> =
        walk.emitted
            .filter { EmitSchema.kindOf(it) == kind }
            .flatMap { EmitSchema.deliveries(it, walk.state) }
            .map { it.clientClass }
            .distinct()

    /**
     * **THE INJECTION, both directions: the living receive no meter mid-shift and the out do.**
     *
     * A plain Resident banks an entry in the middle of a shift, with no meeting anywhere near it.
     * Exactly one meter push comes out of that, it is the live kind, and the classes it reaches are
     * the two out classes and neither living one.
     *
     * **Written as both halves on purpose.** *No meter reaches the living* is satisfied by a build
     * that pushes the meter to nobody at all — which is the build this closes — so the positive
     * half is what makes the negative half mean anything.
     */
    @Test
    fun `a banked entry pushes the meter live to the out and to no living phone`() {
        val walk = Walk(armed())
            .revoke(actor = Seat(1), target = Seat(5), at = Balance.REVOKE_COOLDOWN)
        assertTrue(walk.state.isOut(Seat(5)), "nobody is out, so no out class exists to deliver to")
        walk.emitted.clear()
        walk.bank(Seat(0), at = Balance.REVOKE_COOLDOWN + 100)

        val live = walk.emitted.filterIsInstance<Effect.SystemIntegrityLive>()
        assertEquals(
            1, live.size,
            "a plain Resident banked an entry and the meter moved: exactly one live push is owed " +
                "to the people outside the system, and ${live.size} came out",
        )
        assertEquals(
            emptyList(),
            walk.emitted.filterIsInstance<Effect.SystemIntegrityBatched>(),
            "the meter was handed to the living in the middle of a shift — the number is theirs " +
                "at a meeting and frozen in between (gdd.md:192, :1002)",
        )
        // **What the round can show and what the table can show are two different assertions.**
        // Only one seat is out in this round and it is a plain Resident, so `Insider/Out` is a
        // class no phone in it holds — asserting a two-element delivery list would be asserting
        // about the fixture. So: the delivery really landed outside the system and on no living
        // phone, and the ROW is exactly the two out classes.
        val reached = deliveredTo(walk, EmitSchema.SYSTEM_INTEGRITY_LIVE)
        assertEquals(
            listOf(ClientClass(Role.Resident, RoundState.Out)), reached,
            "the live meter reached a class other than the seat that is outside the system",
        )
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.SYSTEM_INTEGRITY_LIVE),
            "the live meter's row is not exactly the two classes outside the system — narrowed to " +
                "one role it would be an alignment tell delivered by the allowlist",
        )
    }

    /**
     * **The other half of the same rule: a meeting is the shift boundary, and the living are told.**
     *
     * A shift ends when a meeting interrupts it, so the meeting being called is the moment the batch
     * goes out — and it goes out to the living and to nobody else, because the out have had the
     * number all along.
     */
    @Test
    fun `calling a meeting hands the living the meter once and reaches no out phone`() {
        val walk = Walk(armed())
            .revoke(actor = Seat(1), target = Seat(5), at = Balance.REVOKE_COOLDOWN)
            .bank(Seat(0), at = Balance.REVOKE_COOLDOWN + 100)
        walk.emitted.clear()
        walk.step(
            Event.MeetingCalled(
                Tick(Balance.REVOKE_COOLDOWN + 400), Seat(2), MeetingTrigger.MeetingCard,
            ),
        )

        val batched = walk.emitted.filterIsInstance<Effect.SystemIntegrityBatched>()
        assertEquals(
            1, batched.size,
            "a meeting is a shift boundary and the living are owed the meter exactly once at it",
        )
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            deliveredTo(walk, EmitSchema.SYSTEM_INTEGRITY_BATCHED),
            "the batched meter reached a class other than the two still in the round — and it must " +
                "reach BOTH living roles, or the allowlist itself is an alignment tell",
        )
    }

    /**
     * **The batch is sent whether or not the meter moved, and that is rule 1 at a shift boundary.**
     *
     * A meeting with no push on it would tell the whole room that nobody banked anything all shift —
     * a report on the house's work that nobody asked the app to make, delivered by an absence.
     */
    @Test
    fun `a meeting on a shift where nothing was banked still hands the living the meter`() {
        val walk = Walk(armed())
            .step(Event.MeetingCalled(Tick(400), Seat(2), MeetingTrigger.MeetingCard))

        val batched = walk.emitted.filterIsInstance<Effect.SystemIntegrityBatched>()
        assertEquals(1, batched.size, "the absence of the batch became the message")
        assertEquals(
            100, batched.single().percent,
            "a round in which nothing has been banked is at full System Integrity",
        )
    }

    /**
     * **An Insider's accepted entry moves nothing, so nothing is pushed** (D-109, `gdd.md:382`).
     *
     * The one asymmetry in the game, and it lives where no player can stand. This is the meter half
     * of it: the fake is graded honestly, comes back with the same verdict, and the bar does not
     * move — so there is no movement to report and no push to make.
     */
    @Test
    fun `an Insider banking a fake pushes no meter at all`() {
        val walk = Walk(armed()).bank(Seat(1), at = 100)

        assertEquals(
            Balance.meterTotal(seats.size), walk.state.systemIntegrity,
            "an Insider's accepted entry moved the meter",
        )
        assertEquals(
            emptyList(),
            walk.emitted.filterIsInstance<Effect.SystemIntegrityLive>(),
            "the meter was pushed for a fake that wrote nothing — the push is the movement, and " +
                "a push with no movement behind it is the asymmetry announced",
        )
        // And the verdict still came back, which is the half D-109 is actually about.
        assertTrue(
            walk.emitted.any { it is Effect.SubroutineGraded },
            "the Insider's entry was not graded at all, which is the leak D-109 refuses",
        )
    }

    /**
     * **It is a percentage and there is no denominator anywhere on the wire** (D-103, D-130, D-153).
     *
     * A total of `seats × METER_PER_SEAT` is public arithmetic, so an absolute value on any wire is
     * the Insider count recoverable by division. The effect carries one integer, it is a percentage
     * of the whole bar, and it agrees with `Balance.integrityPercent` — which is the only place that
     * arithmetic is allowed to live.
     */
    @Test
    fun `the meter crosses the boundary as a percentage of the whole bar`() {
        val walk = Walk(armed()).bank(Seat(0), at = 100)
        val push = walk.emitted.filterIsInstance<Effect.SystemIntegrityLive>().single()

        assertEquals(
            Balance.integrityPercent(walk.state.systemIntegrity, seats.size), push.percent,
            "the pushed percentage is not the one Balance computes from the state it was pushed at",
        )
        assertTrue(push.percent in 0..100, "the meter left the house as something other than a percent")
        assertTrue(
            push.percent < 100,
            "an entry banked and the pushed percentage did not move — the number is a fixture",
        )
    }
}
