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
     * **A work order reaches both living roles, in the same class list, and nobody else** (D-129).
     *
     * Written out in full for the verdict row's reason. Narrowed to `Resident/Live` this is a role
     * oracle delivered by the allowlist rather than by a screen: an Insider whose phone showed no
     * work at all, on the one surface a player navigates a dark house by. Widened to the out
     * classes it would hand a couch spectator somebody else's assignments.
     */
    @Test
    fun `a work order reaches both living roles and nobody else`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.WORK_ORDER_ISSUED),
        )
    }

    /**
     * **The answer to a scan reaches both living roles, in one class list, and nobody else**
     * (D-124, D-109).
     *
     * Written out in full for the verdict row's reason. Narrowed to `Resident/Live` an Insider's
     * every scan would come back NOTHING FOR YOU HERE — a role oracle after one card, delivered by
     * the allowlist rather than by a screen, on the one surface a player navigates a dark house by.
     *
     * **And the null answer is the same kind as a real opening, which is what makes one row
     * enough.** Split into two kinds this table would hold two rows, and the day somebody narrowed
     * one of them the difference between *this card holds nothing for you* and *this card opened
     * work* would be readable from the permissions without either payload being seen.
     */
    @Test
    fun `a scan answer reaches both living roles and nobody else`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.SCAN_ANSWERED),
        )
        assertEquals(
            EmitSchema.kindOf(Effect.ScanAnswered(Seat(0), opened = null)),
            EmitSchema.kindOf(
                Effect.ScanAnswered(
                    Seat(0),
                    SubroutineInstance(0, SubroutineKind.Sniff, listOf(1, 2, 3)),
                ),
            ),
            "NOTHING FOR YOU HERE and a real opening became two kinds, so the allowlist can now " +
                "tell them apart",
        )
    }

    /** A scan answer is that player's own card in their own hand, and nobody else's business. */
    @Test
    fun `a scan answer is addressed to its own seat only`() {
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.ScanAnswered(Seat(3), opened = null), live())
                .map { it.seat },
        )
    }

    /**
     * **THE PRESENCE PLANE'S ROW, AND IT DOES NOT EXIST** (D-111, D-136, rule 2).
     *
     * *The house records, never recites.* The one designed consumer is the spectator map's expiry
     * and it is frozen, so presence goes to nobody — and it goes to nobody by having no row, which
     * is the fail-closed hinge being used deliberately rather than by accident.
     *
     * Both directions, for `live selections reach the couch`'s reason: a build that permitted it to
     * the living and a build that permitted it to everybody are different bugs, and the second
     * would look like *the map works now*.
     */
    @Test
    fun `presence has no row and reaches nobody`() {
        assertEquals(emptyList(), EmitSchema.classesFor(EmitSchema.PRESENCE_CHANGED))
        assertTrue(
            EmitSchema.PRESENCE_CHANGED !in EmitSchema.knownKinds(),
            "presence acquired a row; if the spectator map has landed, this test is the review",
        )
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(
                Effect.PresenceChanged(Seat(3), MarkerId("m1"), open = true), live(),
            ),
        )
        // The other half: it is addressed to one seat too, so the two denials are independent.
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.audienceOf(
                Effect.PresenceChanged(Seat(3), MarkerId("m1"), open = true), live(),
            ),
        )
    }

    /**
     * **The opening message reaches everybody in the house** (D-118, D-076).
     *
     * It is one of exactly two events that dim every panel, and a dimming lamp is world-observable
     * in a dark house — so a notification addressed to fewer than everyone is a beacon. This is the
     * row that would have to be narrowed for that to happen, and it is written out in full so a
     * narrowing is a visible edit.
     */
    @Test
    fun `the opening message reaches every class in the house`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.OPENING_MESSAGE),
        )
    }

    /**
     * **An order is addressed to its own seat**, for the verdict's reason one step further on: a
     * broadcast order would publish where everybody else's work is, which is a map of the round
     * handed to a player who is supposed to be walking a dark house to find their own.
     */
    @Test
    fun `a work order is addressed to its own seat only`() {
        val state = live()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.WorkOrderIssued(Seat(3), emptyList()), state)
                .map { it.seat },
        )
    }

    /**
     * **A blocked line is a different type and cannot be given a name** (D-114, rule 3).
     *
     * The redaction here is structural rather than a field left null, so this reads the type rather
     * than a value: `OrderLine.Blocked` has one property and it is the index. A `subroutine` that
     * arrived as `null` would be the same disclosure one refactor away from being populated.
     */
    @Test
    fun `a blocked line has nothing on it but its position`() {
        val blocked: OrderLine = OrderLine.Blocked(4)
        assertEquals(4, blocked.index)
        assertFalse(
            blocked.toString().contains("subroutine", ignoreCase = true),
            "a blocked line carries a Subroutine: $blocked",
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

    // ---- The Egress -------------------------------------------------------------------------

    /**
     * **Every Egress kind has a row, and the list is written out rather than derived.**
     *
     * Rule 2 makes a forgotten row ship to nobody, which is the right failure and a *silent* one:
     * the Egress would fire, the house would not dim, and the countdown would appear on no phone in
     * the building. Nobody would call that a redaction bug. So the five are named literally, and
     * the completeness check below is what stops a sixth being added without one.
     */
    @Test
    fun `every Egress kind is permitted to somebody`() {
        val egress = listOf(
            EmitSchema.EGRESS_OPENED,
            EmitSchema.EGRESS_HELD,
            EmitSchema.SYNC_PULSE_ANSWERED,
            EmitSchema.EGRESS_CONTAINED,
            EmitSchema.EGRESS_SUCCEEDED,
        )
        for (kind in egress) {
            assertTrue(
                EmitSchema.classesFor(kind).isNotEmpty(),
                "$kind has no row: the Egress would run and reach nobody",
            )
        }
        // Completeness. Every permitted kind belonging to this system is in the list above -- so a
        // sixth cannot be added, given a row, and left out of these assertions.
        assertEquals(
            egress.map { it.name }.sorted(),
            EmitSchema.knownKinds().map { it.name }
                .filter { it.startsWith("Egress") || it.startsWith("SyncPulse") }
                .sorted(),
            "an Egress kind exists that this test does not name",
        )
    }

    /**
     * **The house catching fire reaches every class in the house** (D-118, D-076).
     *
     * The second of D-118's two dimming events, and the row is as wide as the opening message's
     * for the same reason: a dimming lamp is world-observable, so a notification addressed to fewer
     * than everyone is a beacon. **Including the Insider who fired it** — a phone that did not dim
     * in a house where every other phone did is the loudest tell this design has (`gdd.md:396`).
     */
    @Test
    fun `the Egress alert reaches every class in the house`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
                ClientClass(Role.Resident, RoundState.Out),
                ClientClass(Role.Insider, RoundState.Out),
            ),
            EmitSchema.classesFor(EmitSchema.EGRESS_OPENED),
        )
        // Neither this nor the pause splits on ROLE. Both roles hear the same Egress; what a player
        // outside the system gets extra is the meter, and that is a different kind entirely.
        for (kind in listOf(EmitSchema.EGRESS_OPENED, EmitSchema.EGRESS_HELD)) {
            val roles = EmitSchema.classesFor(kind).map { it.role }.distinct().sortedBy { it.name }
            assertEquals(listOf(Role.Insider, Role.Resident), roles, "$kind split on role")
        }
    }

    /**
     * **A beat answer is addressed to the one seat that tapped, and to no player who is out.**
     *
     * Both halves. Broadcast it would publish who is standing at a node and tapping, live, to the
     * whole house — which is the presence read D-111 split the two planes to close, arriving one
     * beat at a time. Permitted to the out it would put the same read on the couch's screen, where
     * the spectator map is still not allowed to carry it.
     */
    @Test
    fun `a beat answer is addressed to its own seat and reaches only the living`() {
        val state = live()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.audienceOf(Effect.SyncPulseAnswered(Seat(3), held = true), state),
        )
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.SYNC_PULSE_ANSWERED),
        )
        // A seat that is out gets nothing at all: the row denies the class and the addressing
        // denies everybody else, and neither denial depends on the other.
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(
                Effect.SyncPulseAnswered(Seat(3), held = true),
                state.copy(revoked = listOf(Seat(3))),
            ),
        )
    }

    /**
     * **Containment carries no attribution, and the type is what guarantees it** (`gdd.md:987`).
     *
     * *Nobody learns anything about anybody.* This is not a rule the emit boundary enforces — it is
     * a rule the effect has no field to break. Asserted here because the tempting edit is to add a
     * `by: List<Seat>` for the recording's sake, and that field would mint two players an alibi at
     * the one moment in the round when everybody is moving and nobody may speak.
     */
    @Test
    fun `containment names nobody and still reaches everybody`() {
        // It goes to every seat in the building, which is only safe because it says nothing. If a
        // seat list is ever added to this effect, this broadcast becomes the leak -- and it would
        // arrive looking like an unchanged test.
        assertEquals(
            seats,
            EmitSchema.deliveries(Effect.EgressContained(Haptic.Short), live()).map { it.seat },
        )
        assertEquals(
            seats,
            EmitSchema.deliveries(Effect.EgressSucceeded(Haptic.Long), live()).map { it.seat },
        )
    }
}
