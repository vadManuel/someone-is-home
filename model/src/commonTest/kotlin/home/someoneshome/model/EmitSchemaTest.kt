package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Story 0.6b. The question is *"was this ever permitted"*, so every assertion here is about the
 * table rather than about a round.
 */
class EmitSchemaTest {

    private val seats = (0..5).map { Seat(it) }
    private val insiders = listOf(Seat(1))

    private fun live() = GameState.armedRound(
        seed = 7L, seats = seats, insiders = insiders, systemIntegrity = 42,
    )

    /**
     * **The fail-closed hinge.**
     *
     * A kind with no row in the allowlist is permitted to none of the eight classes. This is the
     * exact state a new [Effect] is in the moment before someone remembers to add its row, and
     * the state it stays in if they never do.
     */
    @Test
    fun a_kind_with_no_entry_ships_to_nobody() {
        val unlisted = MessageKind("EgressProgressed")
        assertTrue(unlisted !in EmitSchema.knownKinds(), "fixture is stale: this kind now exists")

        val permitted = ClientClass.ALL.filter { EmitSchema.permits(unlisted, it) }
        assertEquals(emptyList(), permitted, "an unlisted kind reached $permitted")
        assertEquals(8, ClientClass.ALL.size, "the taxonomy is 2 roles x 4 round-states")
    }

    /** The same, through the public entry point every recipient list comes from. */
    @Test
    fun classes_for_an_unlisted_kind_is_empty() {
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("Whatever")))
    }

    /** D-067: nothing in-game runs in the lobby. No kind reaches a pre-arm client. */
    @Test
    fun no_kind_reaches_a_pre_arm_client() {
        for (kind in EmitSchema.knownKinds()) {
            for (role in Role.entries) {
                assertFalse(
                    EmitSchema.permits(kind, ClientClass(role, RoundState.PreArm)),
                    "$kind reaches $role before the round is armed",
                )
            }
        }
    }

    /**
     * **Why the taxonomy has two axes.**
     *
     * The live SystemIntegrity decrement goes to players who are out and to no living player of
     * either role. Keyed on [Role] alone, the entry that serves an out Resident serves a living
     * one, and this assertion is what an allowlist keyed on role would fail.
     */
    @Test
    fun the_live_progress_count_reaches_the_out_and_no_living_player() {
        val kind = EmitSchema.SUBROUTINE_PROGRESSED
        assertEquals(
            listOf(ClientClass(Role.Resident, RoundState.Out), ClientClass(Role.Insider, RoundState.Out)),
            EmitSchema.classesFor(kind),
        )
        for (role in Role.entries) {
            assertFalse(EmitSchema.permits(kind, ClientClass(role, RoundState.Live)), "living $role")
        }
    }

    /** D-075: only a player outside the system sees who cast what. Same two-axis shape. */
    @Test
    fun vote_attribution_reaches_the_out_and_no_living_player() {
        val kind = EmitSchema.MEETING_RESOLVED
        for (role in Role.entries) {
            assertTrue(EmitSchema.permits(kind, ClientClass(role, RoundState.Out)))
            assertFalse(EmitSchema.permits(kind, ClientClass(role, RoundState.Live)), "living $role")
        }
    }

    /** Only an Insider has an ability that fires. One class, and it is named. */
    @Test
    fun ability_fired_reaches_one_class_only() {
        assertEquals(
            listOf(ClientClass(Role.Insider, RoundState.Live)),
            EmitSchema.classesFor(EmitSchema.ABILITY_FIRED),
        )
    }

    /**
     * **D-109's row, and the one that must never be narrowed to one role.**
     *
     * A verdict reaches a living Resident and a living Insider, in the same class list, so the
     * allowlist itself cannot become the place an Insider's fake goes to die. Narrowing this to
     * `Insider/Live` or to `Resident/Live` is a one-word edit that reads like a tightening and is
     * a role oracle after a single Subroutine — which is why the expected list is written out in
     * full rather than asserted as *"contains both living classes"*.
     */
    @Test
    fun `a verdict reaches both living roles and nobody else`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.SUBROUTINE_GRADED),
        )
    }

    /**
     * **The verdict is addressed to the seat that returned the entry, and to no other phone.**
     *
     * The allowlist cannot see this: every living Resident is in the same class, so a verdict
     * broadcast to the house would pass every row in the table. What it would publish is who is
     * completing work and how often — the per-player read that the percentage-only meter (D-103)
     * exists to keep out of aggregate, arriving one player at a time instead.
     */
    @Test
    fun `a verdict is addressed to its own seat only`() {
        val state = live()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.SubroutineGraded(Seat(3), accepted = true), state)
                .map { it.seat },
        )
    }

    /**
     * A seat revoked between its scan and its entry is out, and the verdict is denied.
     *
     * Fail-closed, and it carries no role information: the round-state axis moved, and round-state
     * is publicly observable (D-068). Asserted rather than left implicit because the tempting fix
     * — widening the row to the out classes so the effect "always lands" — would put a Subroutine
     * verdict on a screen that no longer has a Subroutine on it.
     */
    @Test
    fun `a verdict addressed to a seat that is out is denied`() {
        val out = live().copy(revoked = listOf(Seat(3)))
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.SubroutineGraded(Seat(3), accepted = true), out),
        )
    }

    /**
     * **THE MEETING'S SHARPEST ROW, BOTH DIRECTIONS: the couch sees selections, the living cannot.**
     *
     * D-117: *the living see the count and never the selections; the ghosts see the live
     * selections and are the only readers who do.* Two assertions rather than one, because either
     * alone passes on a build that is wrong the other way — a row permitted to nobody satisfies
     * "no living class sees it", and a row permitted to everybody satisfies "the out see it".
     *
     * Written out in full for the reason the verdict's row is: narrowing or widening this by one
     * class is a one-word edit, and what it would hand over is the room's own thinking in real
     * time, to the people still in the room.
     */
    @Test
    fun `live selections reach the couch and no living class`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.VOTE_SELECTION_SHOWN),
        )
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.VOTE_PROGRESSED),
            "the count is the living's half of the same disclosure and must not have moved with it",
        )
    }

    /** The other half of D-075, unchanged by D-117: attribution is the couch's and nobody's else. */
    @Test
    fun `attribution reaches the couch and the result reaches everyone`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.MEETING_RESOLVED),
        )
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.MEETING_RESULT),
        )
    }

    /**
     * **The ring and the walk-in split on round-state and never on role** (D-134).
     *
     * Both rows name both roles or neither. A row that named one would make the meeting's very
     * first push an alignment tell, delivered before anybody had said a word.
     */
    @Test
    fun `the ring is for the living and the walk-in for the out - both roles either way`() {
        for (kind in listOf(EmitSchema.MEETING_OPENED, EmitSchema.STAND_AND_WALK_IN)) {
            val roles = EmitSchema.classesFor(kind).map { it.role }.distinct().sortedBy { it.name }
            assertEquals(listOf(Role.Insider, Role.Resident), roles, "$kind split on role")
        }
        assertEquals(
            listOf(RoundState.Live),
            EmitSchema.classesFor(EmitSchema.MEETING_OPENED).map { it.roundState }.distinct(),
        )
        assertEquals(
            listOf(RoundState.Out),
            EmitSchema.classesFor(EmitSchema.STAND_AND_WALK_IN).map { it.roundState }.distinct(),
        )
    }

    /**
     * A seat's own ballot goes to that seat. The allowlist cannot see this — every living player
     * is in the same class as every other, so a broadcast [Effect.VoteHeld] would pass every row
     * in the table and hand the room a running commentary on one player's finger.
     */
    @Test
    fun `a ballot answer is addressed to its own seat only`() {
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.VoteHeld(Seat(3), Seat(2), locked = true), live())
                .map { it.seat },
        )
    }

    /**
     * **The takeover reaches the one seat the room restrained, and it reaches them as an out
     * client** — which is only true because the Restrain has already landed in state by then.
     *
     * Both halves are asserted. Addressed to a seat still classified `Live` the row would deny it
     * and the player would never be told; broadcast, it would tell five other phones how the vote
     * went against somebody before the countdown had finished running.
     */
    @Test
    fun `the takeover reaches exactly the restrained seat`() {
        val after = live().copy(restrained = listOf(Seat(3)))
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.RestrainedTakeover(Seat(3), Haptic.Long), after)
                .map { it.seat },
        )
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.RestrainedTakeover(Seat(3), Haptic.Long), live()),
            "a takeover addressed to a seat the house has not yet deauthorised was delivered",
        )
    }

    /**
     * A Restrain puts a seat in the out classes, by its own list.
     *
     * The gap [RoundState.Out] used to document — *nothing in GameState stores a restrained
     * player* — closed here, and closed the way that comment required: a second list, never a
     * second use of `revoked` (rule 9).
     */
    @Test
    fun `a restrained seat is out without borrowing the revoked list`() {
        val after = live().copy(restrained = listOf(Seat(3)))
        assertEquals(ClientClass(Role.Resident, RoundState.Out), after.clientClassOf(Seat(3)))
        assertTrue(after.revoked.isEmpty(), "a Restrain was stored as a Revoke")
        assertEquals(ClientClass(Role.Resident, RoundState.Live), after.clientClassOf(Seat(4)))
    }

    /**
     * Addressing is a second, independent gate.
     *
     * Both seats here are living Residents in the same class, so the allowlist permits the kind to
     * both. Only addressing stops seat 5 learning seat 3's lamp.
     */
    @Test
    fun a_lamp_is_addressed_to_its_own_seat_only() {
        val state = live()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.LampSet(Seat(3), luminance = 1), state).map { it.seat },
        )
        assertTrue(EmitSchema.permits(EmitSchema.LAMP_SET, state.clientClassOf(Seat(5))))
    }

    /** An unseated seat addresses nobody rather than being accepted as a recipient. */
    @Test
    fun an_unseated_seat_addresses_nobody() {
        val state = live()
        assertEquals(emptyList(), EmitSchema.audienceOf(Effect.LampSet(Seat(99), 1), state))
        assertEquals(emptyList(), EmitSchema.audienceOf(Effect.LampSet(Seat(-3), 1), state))
    }

    /** Round-state is read off authority state, and the precedence is deliberate. */
    @Test
    fun round_state_precedence() {
        assertEquals(RoundState.PreArm, GameState.EMPTY.roundStateOf(Seat(0)))

        val state = live()
        assertEquals(RoundState.Live, state.roundStateOf(Seat(0)))
        assertEquals(RoundState.PreArm, state.roundStateOf(Seat(99)))

        val out = state.copy(revoked = listOf(Seat(0)))
        assertEquals(RoundState.Out, out.roundStateOf(Seat(0)))
        assertEquals(RoundState.Ended, out.endRound().roundStateOf(Seat(0)))
    }

    /**
     * An ended round stays Ended once the perimeter disarms.
     *
     * `LAMP_SET` is permitted to the Ended classes precisely so the lamp survives round end. If
     * `!armed` outranks `ended`, a disarmed finished round classifies every seat as PreArm, which
     * is permitted nothing, and every phone in the house goes dark — presenting as a missing
     * effect rather than as a precedence bug.
     */
    @Test
    fun `an ended round outranks a disarmed perimeter`() {
        val finished = live().endRound().copy(armed = false)
        assertEquals(RoundState.Ended, finished.roundStateOf(Seat(0)))
        assertTrue(EmitSchema.permits(EmitSchema.LAMP_SET, finished.clientClassOf(Seat(0))))
    }

    /** Both axes, together, off one state. */
    @Test
    fun client_class_reads_both_axes() {
        val state = live().copy(revoked = listOf(Seat(1)))
        assertEquals(ClientClass(Role.Resident, RoundState.Live), state.clientClassOf(Seat(0)))
        assertEquals(ClientClass(Role.Insider, RoundState.Out), state.clientClassOf(Seat(1)))
    }

    /**
     * A living player receives nothing that carries the live progress count or the attribution
     * list, through the emit boundary rather than through the table.
     */
    @Test
    fun a_living_player_receives_neither_progress_nor_attribution() {
        val state = live()
        val progress = EmitSchema.deliveries(Effect.SubroutineProgressed(remaining = 41), state)
        val resolved = EmitSchema.deliveries(
            Effect.MeetingResolved(restrained = Seat(2), attribution = listOf(Seat(0) to Seat(2))),
            state,
        )
        assertEquals(emptyList(), progress, "live progress reached ${progress.map { it.clientClass }}")
        assertEquals(emptyList(), resolved, "attribution reached ${resolved.map { it.clientClass }}")

        // The same two effects, once seat 4 is out. Same code, same state, one axis moved.
        val withOut = state.copy(revoked = listOf(Seat(4)))
        assertEquals(
            listOf(Seat(4)),
            EmitSchema.deliveries(Effect.SubroutineProgressed(41), withOut).map { it.seat },
        )
    }
}
