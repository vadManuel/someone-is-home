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
     * Any ending will do for a test about the round-state axis.
     *
     * Named rather than inlined so that the two tests below are visibly about *the round is over*
     * and not about which of D-131's four routes ended it — the axis does not care, and a literal
     * repeated at two call sites is a thing somebody eventually makes differ.
     */
    private val ROUND_OVER = Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared)

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
     * allowlist itself cannot become the place an Insider's fake goes to fail. Narrowing this to
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
            EmitSchema.kindOf(Effect.ScanAnswered(Seat(0), opened = null, haptic = Haptic.Double)),
            EmitSchema.kindOf(
                Effect.ScanAnswered(
                    Seat(0),
                    SubroutineInstance(0, SubroutineKind.Sniff, listOf(1, 2, 3)),
                    haptic = null,
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
            EmitSchema.deliveries(Effect.ScanAnswered(Seat(3), opened = null, haptic = Haptic.Double), live())
                .map { it.seat },
        )
    }

    /**
     * **NOT A MARKER reaches both living roles, one seat at a time, and nobody else** (D-124, D-318).
     *
     * The other half of D-124, and it is the house's answer to a piece of paper, so the row must not
     * know a role: narrowed to either, a scrap of paper from another home would be a role test. Not
     * the out classes, because an out seat's registered scan is answered on this axis by nothing,
     * and the two must not separate on the couch. And the seat holding the card alone: broadcast, it
     * would be the report D-072 refuses.
     */
    @Test
    fun `not a marker reaches the scanning seat of either living role and nobody else`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.CARD_UNREGISTERED),
        )
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.CardUnregistered(Seat(3)), live()).map { it.seat },
        )
        assertTrue(
            EmitSchema.kindOf(Effect.CardUnregistered(Seat(3))) != EmitSchema.SCAN_ANSWERED,
            "NOT A MARKER and NOTHING FOR YOU HERE are one kind, so D-124's two answers sound alike",
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
     * **THE SURGE REACHES BOTH LIVING ROLES, AND NARROWED TO EITHER IT BECOMES AN ALIGNMENT TEST**
     * (`gdd.md:391`, `:393`).
     *
     * *Every observable effect must be self-inflictable. Insiders can target themselves with every
     * ability. If only Residents could ever **be** targeted, then being visibly targeted would be
     * evidence of innocence, and every ability would double as an alignment test.*
     *
     * Both directions, for [`live selections reach the couch and no living class`]'s reason: a row
     * permitted to nobody satisfies "no Insider sees it" and a row permitted to everybody satisfies
     * "a Resident sees it", so either assertion alone passes a build that is wrong the other way.
     * And it is the loudest row in the table to get wrong — this is the effect the whole house can
     * hear.
     */
    @Test
    fun `the surge reaches both living roles and neither out class`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ).sortedBy { it.toString() },
            EmitSchema.classesFor(EmitSchema.SURGED).sortedBy { it.toString() },
            "the Surge reaches something other than exactly the two living classes",
        )
    }

    /**
     * **A Surge at an already-Revoked seat is delivered to nobody, and the actor is told nothing
     * about it** (`gdd.md:344`, rule 1).
     *
     * The rules construct the effect identically whichever seat it names — that is `TierTwoTest`'s,
     * in `core` — and this is the **second, independent** denial: `SURGED` is permitted to the two
     * living classes and to no out class, so a Revoked target's phone is declined at the boundary.
     *
     * Neither denial is visible as an absence. The actor's own device receives `AbilityFired`
     * either way, addressed to them and permitted to `Insider/Live`, so *targeting an already-revoked
     * player appears to succeed on the actor's device* holds without any handler asking whether it
     * landed. A row widened to the out classes would light a flat phone's lamp in a dark room in
     * front of everybody.
     */
    @Test
    fun `a surge addressed to a seat that is out is denied`() {
        val out = live().copy(revoked = listOf(Seat(3)))
        val surge =
            Effect.Surged(Seat(3), peak = 100, black = 0, haptic = Haptic.Short)
        assertEquals(emptyList(), EmitSchema.deliveries(surge, out))

        // And the actor's answer is delivered regardless, which is the half that makes the denial
        // above invisible rather than merely correct.
        assertEquals(
            listOf(Seat(1).index),
            EmitSchema.deliveries(Effect.AbilityFired(Seat(1), cooldownStarted = true), out)
                .map { it.seat.index },
        )
    }

    /**
     * **The Isolate picker is the narrowest LIVE row in the table: one class** (D-150, and the
     * 2026-08-22 targeting ruling).
     *
     * It carries the round's active markers with their rooms — the map of the round the house has
     * never published, which `Effect.WorkOrderIssued` narrows to one seat's own lines. **Widened by
     * one class it hands every Resident that map.**
     *
     * The rules build one of these for every seat on purpose (see `Rules.isolateOffers`, and the
     * differential harness's finding recorded there), so **this row is the only thing standing
     * between a Resident and the geography.** Asserted in both directions, and addressed as well as
     * permitted: two Insiders are the same class, so the allowlist alone could not stop one reading
     * the other's.
     */
    @Test
    fun `the isolate picker reaches a living Insider and nobody else`() {
        assertEquals(
            listOf(ClientClass(Role.Insider, RoundState.Live)),
            EmitSchema.classesFor(EmitSchema.ISOLATE_TARGETS_OFFERED),
            "the Isolate picker reaches something other than exactly a living Insider",
        )

        val state = live()
        val geography = listOf(Destination(MarkerId("M-1"), shape = "diamond", room = "KITCHEN"))
        // Built for a Resident, exactly as the rules build it, and delivered to nobody.
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.IsolateTargetsOffered(Seat(0), geography), state),
            "a Resident was handed the round's geography",
        )
        // Built for the Insider, and it reaches that seat and no other.
        assertEquals(
            listOf(Seat(1).index),
            EmitSchema.deliveries(Effect.IsolateTargetsOffered(Seat(1), geography), state)
                .map { it.seat.index },
        )
    }

    /**
     * **⚠️ NO KIND IN THIS TABLE CARRIES THE HOME'S PLAN, AND THE ROW THAT DID IS GONE** (D-186).
     *
     * The rollback of T2·4. The plan's row was *the largest disclosure in the emit table* — the
     * whole home's geometry, every room on every floor, to one class — and it existed for one
     * reason: a picker over rooms cannot be drawn without rooms. **Static has no picker.**
     *
     * D-186: *the row goes, and `Destination`'s own warning about the whole map reaching every
     * client returns to being a warning about a thing the app does not do.* This is that warning
     * with a test under it. It reads the table's own list of known kinds rather than naming a
     * constant that no longer exists, so it keeps meaning something the day somebody adds a kind
     * back: **a disclosure that shrinks deserves the same ceremony as one that grows.**
     */
    @Test
    fun `one kind in the table carries the home's plan and it reaches every seat in the round alike`() {
        // D-186 removed the plan's only row, which was one class wide. D-316 brings one back, four
        // classes wide and never more: every seat in a round is sent the same plan at arming, so its
        // arrival says nothing about the phone it lands on. Never the ended or pre-arm classes.
        val geography = EmitSchema.knownKinds().filter {
            it.name.contains("Plan") || it.name.contains("Home")
        }
        assertEquals(listOf(EmitSchema.PLAN_ISSUED), geography, "a second kind carrying the home's geometry is in the table")
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Insider, RoundState.Live),
                ClientClass(Role.Resident, RoundState.Out), ClientClass(Role.Insider, RoundState.Out),
            ).sortedBy { it.toString() },
            EmitSchema.classesFor(EmitSchema.PLAN_ISSUED).sortedBy { it.toString() },
            "the plan's row is not every class in a running round",
        )
    }

    /**
     * **The map's two beat kinds (D-316): the Terminal's counts to both living classes and no out
     * class, true occupancy to both out classes and no living class.** Either narrowed to one role
     * is an alignment test; either widened across the round-state axis hands the living the map
     * without its error, or the couch a reading it has no station for.
     */
    @Test
    fun `the Terminal's counts reach both living classes and true occupancy both out classes`() {
        assertEquals(
            listOf(ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Insider, RoundState.Live)).sortedBy { it.toString() },
            EmitSchema.classesFor(EmitSchema.TERMINAL_READING).sortedBy { it.toString() },
        )
        assertEquals(
            listOf(ClientClass(Role.Resident, RoundState.Out), ClientClass(Role.Insider, RoundState.Out)).sortedBy { it.toString() },
            EmitSchema.classesFor(EmitSchema.TRUE_OCCUPANCY).sortedBy { it.toString() },
        )
    }

    /**
     * **⚠️ STATIC REACHES BOTH LIVING CLASSES AND NEITHER OUT CLASS — THE GHOST EXEMPTION, BOTH
     * DIRECTIONS** (D-186).
     *
     * *House-wide to the living. Ghosts exempt — their true occupancy stays clean.*
     *
     * **Both halves are asserted, because neither alone is the ruling.** A row permitted to nobody
     * satisfies *the ghosts do not see it*; a row permitted to everybody satisfies *the living see
     * it*; and a row permitted to one living class satisfies both while being the worst outcome
     * available — an alignment test delivered by the allowlist, on the loudest ability in the game.
     * So the living are asserted present in **both** roles and the out absent in both.
     *
     * The ghost half is the ruling rather than the usual round-state denial, and it is worth being
     * exact about: the out are not denied because they cannot act on it. They are denied because
     * **their true occupancy is not the house's monitoring** and a jam is the house's own monitoring
     * degrading (M12, D-134, D-145). A build that widened this row would take the liveness privilege
     * away from the couch, quietly, and the symptom would be a ghost's map going to noise for ten
     * seconds with nothing anywhere explaining why.
     */
    @Test
    fun `static reaches both living classes and neither out class`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.STATIC_OPENED),
            "the jam reaches something other than exactly the two living classes",
        )

        // Both axes, both directions, asked of the permission rather than of the list — the shape
        // `vote_attribution_reaches_the_out_and_no_living_player` uses, pointed the other way up.
        for (role in Role.entries) {
            assertTrue(
                EmitSchema.permits(EmitSchema.STATIC_OPENED, ClientClass(role, RoundState.Live)),
                "living $role stopped seeing the jam",
            )
            assertFalse(
                EmitSchema.permits(EmitSchema.STATIC_OPENED, ClientClass(role, RoundState.Out)),
                "an out $role was jammed — the ghost exemption is gone",
            )
        }

        val jam = Effect.StaticOpened(seconds = 10, seed = 4242L)
        val house = live()
        assertEquals(
            house.seats.map { it.index },
            EmitSchema.deliveries(jam, house).map { it.seat.index },
            "the jam did not reach every living seat in the house",
        )
        // **And it is addressed to nobody in particular**, which is the anonymity's own half: the
        // effect carries no seat, so a delivery list is the whole house or it is a bug.
        assertEquals(
            house.seats, EmitSchema.audienceOf(jam, house),
            "the jam acquired an address, so it can now be narrowed to the seat that fired it",
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
     * **The caller's message reaches exactly one phone, and it is the caller's** (E-R1-1).
     *
     * The addressing is the whole message — there is nothing on the wire member to read — so this
     * is the assertion that answers the escalation. A widening would tell every phone in the house
     * it had called the meeting; a narrowing to nobody would put the caller back on the ring with
     * everybody else, which is the state the escalation was raised about.
     *
     * **And it is refused to a seat outside the system**, which is not a disclosure decision so much
     * as a statement about who can call a meeting: you walk to a card or you report a Revoke, and
     * the couch does neither.
     */
    @Test
    fun `the caller's own message reaches exactly the calling seat`() {
        val state = live()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.MeetingCalling(Seat(3)), state).map { it.seat },
            "the house told the wrong number of phones that they had called the meeting",
        )
        assertEquals(
            listOf(Seat(0)),
            EmitSchema.deliveries(Effect.MeetingCalling(Seat(0)), state).map { it.seat },
            "the addressing does not follow the seat on the effect",
        )
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(
                Effect.MeetingCalling(Seat(3)),
                state.copy(restrained = listOf(Seat(3))),
            ),
            "a seat outside the system was told it had called a meeting",
        )
    }

    /**
     * **THE REVEAL AT ARMING: one phone, its own role, and nothing anywhere else** (`gdd.md:923`,
     * E-R2-2).
     *
     * The most alignment-revealing kind in the table, and the two denials that make it lawful are
     * independent — either alone would ship a leak, and neither can see what the other is doing.
     *
     * **The row** permits it to both living classes and to neither out class nor the ending, so an
     * Insider's reveal and a Resident's cross the boundary identically and nothing about the class
     * list can become the answer: a row of `Insider/Live` would deliver the reveal to Insiders and
     * silence to everybody else, which is rule 1's forbidden shape written into the allowlist.
     *
     * **The addressing** is what stops seat 5 learning seat 3's role, and the allowlist cannot help
     * with it — two living Insiders are one class, and so are two living Residents. Asserted by
     * building the leak: the same effect for two different seats reaching two different single
     * phones, and a sweep over every seat in the house showing each is told exactly once and only
     * about itself.
     */
    /**
     * **The roster reaches both living classes, and a row that narrowed it would be a role oracle**
     * (D-115, E-R2-6).
     *
     * It looks like the safest row in the table — the list is byte-identical on every phone, so
     * there is nothing in it to leak. **The leak is in what its absence would say.** Narrowed to
     * `Insider/Live`, every screen that names anybody is populated on an Insider's phone and blank
     * on a Resident's: the ring names its caller, the tally names who was restrained, the Surge
     * picker offers five names — and one role sees all of it while the other sees nothing. That is
     * `ROLE_REVEALED`'s own argument arriving at a message with no role in it, and it is rule 1's
     * forbidden shape written into the allowlist, where it would look like a tightening.
     *
     * **Written because an injection found nothing.** Narrowing this row broke no test in the
     * build; the addressing tests passed, the fuzz property passed, and every screen went on
     * drawing its fixture names because no test had ever put a real roster on one.
     */
    @Test
    fun `the roster reaches both living roles and says nothing about which is reading it`() {
        val state = live()

        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.ROSTER_ISSUED),
            "the roster's row moved: narrowed to one role its ABSENCE is the answer, because " +
                "every screen that names anybody would be blank for the other one",
        )

        // Every seat, told once, and told the SAME list — which is the property that makes a
        // message naming the whole house safe to send to the whole house. Built the way arming
        // builds it: one list, one effect per seat.
        val roster = state.seats.map { state.nameOf(it) ?: "" }
        for (seat in state.seats) {
            val effect = Effect.RosterIssued(seat, roster)
            assertEquals(
                listOf(seat), EmitSchema.deliveries(effect, state).map { it.seat },
                "seat ${seat.index}'s roster did not go to exactly one phone",
            )
        }
    }

    @Test
    fun `a seat is told its own role and no other seat's`() {
        val state = live()

        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.ROLE_REVEALED),
            "the reveal's row moved: narrowed to one role its ABSENCE becomes the answer, and " +
                "widened past Live it re-answers the question on a couch or on the ending screen",
        )

        // Every seat, told once, about itself. Built the way arming builds it — one per seat,
        // carrying that seat's own role — so what is swept is the burst rather than one effect.
        for (seat in seats) {
            val effect = Effect.RoleRevealed(seat, state.roleOf(seat))
            assertEquals(
                listOf(seat), EmitSchema.deliveries(effect, state).map { it.seat },
                "seat ${seat.index}'s role did not go to exactly one phone",
            )
        }

        // The leak, built: an Insider's reveal addressed to a Resident's seat reaches that seat and
        // nobody else — the addressing follows the seat on the effect and never the role in it, so
        // a rule that composed the pair wrongly is a rule that lied to one phone rather than one
        // that published to the house. That is the fail-closed direction and it is why the rules
        // read `state.roleOf(seat)` for the seat they are addressing.
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.RoleRevealed(Seat(3), Role.Insider), state).map { it.seat },
            "a reveal addressed to one seat reached another phone",
        )
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(
                Effect.RoleRevealed(Seat(3), Role.Insider),
                state.copy(revoked = listOf(Seat(3))),
            ),
            "a seat outside the system was told a role: roles are drawn at arming, when nobody " +
                "is out, and the ending is where an alignment is said again",
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
        assertEquals(RoundState.Ended, out.endRound(ROUND_OVER).roundStateOf(Seat(0)))
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
        val finished = live().endRound(ROUND_OVER).copy(armed = false)
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
            EmitSchema.SYNC_PULSE_BLANKED,
            EmitSchema.EGRESS_CONTAINED,
        )
        for (kind in egress) {
            assertTrue(
                EmitSchema.classesFor(kind).isNotEmpty(),
                "$kind has no row: the Egress would run and reach nobody",
            )
        }
        // Completeness. Every permitted kind belonging to this system is in the list above -- so a
        // fifth cannot be added, given a row, and left out of these assertions.
        //
        // **EGRESS_SUCCEEDED is deliberately absent from both sides**, which is what makes this
        // still a completeness check rather than a list somebody shortened: it has no row at all
        // now (see the test that says so), so it is not among the known kinds either, and the two
        // sides agree for the right reason.
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
    }

    /**
     * **The terminal fact reaches nobody, on purpose, and the ending is why** (D-131, D-156).
     *
     * It had a row while it was the last thing that happened in a round. It is not any more: every
     * emission of it now sits in the same reduction that ends the round, and the round-state axis
     * has moved by the time the boundary is consulted — so the row could only ever have delivered
     * to classes that no longer exist. What the room gets is `RoundEnded` carrying
     * `WinRoute.EgressUncontained`: the same fact, once, with one buzz.
     *
     * Asserted both ways round, because a row restored "to fix the missing delivery" would put a
     * second long haptic on every phone in the house on one of the four routes and one on the
     * other three — which is exactly the shape F-003 found and D-156 closed.
     */
    @Test
    fun `the terminal fact reaches nobody and the ending carries it instead`() {
        val fact = Effect.EgressSucceeded(Haptic.Long)
        assertEquals(
            emptyList(), EmitSchema.classesFor(EmitSchema.EGRESS_SUCCEEDED),
            "the terminal fact has a row again; every delivery it makes is a second buzz",
        )
        assertEquals(emptyList(), EmitSchema.deliveries(fact, live()))
        assertEquals(emptyList(), EmitSchema.deliveries(fact, over()))
        assertEquals(
            seats,
            seats.flatMap {
                EmitSchema.deliveries(
                    Effect.RoundEnded(it, Winner.Insiders, WinRoute.EgressUncontained, Haptic.Short, integrity = 60),
                    over(),
                )
            }.map { it.seat },
            "nothing tells the house the Egress ran out",
        )
    }

    // ---- The ending ---------------------------------------------------------------------------

    /** The round, over: every seat classifies [RoundState.Ended] and the three rows can deliver. */
    private fun over() = live().endRound(ROUND_OVER)

    private val REVEAL = Effect.InsidersRevealed(listOf(InsiderNamed(Seat(1), "DANI", "the spare key")))

    /**
     * **THE COMPLETENESS CHECK: no effect in this app states an alignment before the ending.**
     *
     * *Never shown: any confirmation of alignment, at any point, by any path* (`gdd.md:213`). This
     * walks **every kind the schema knows** and asserts that the only ones permitted to a class
     * that exists while a round is running carry no [InsiderNamed] and no [Winner] — which is the
     * property stated as a type rather than as a review note.
     *
     * It is written over `knownKinds()` rather than over a list, so a new effect that named a role
     * and was given a LIVING row would fail here rather than being caught by whoever happened to
     * read the diff. The three ending kinds are named as the exemptions and the exemption list is
     * asserted to be exactly those three: an effect added to it is a decision, and this is where
     * somebody has to make it.
     */
    @Test
    fun `nothing that states an alignment is permitted before the round ends`() {
        val ending = listOf(
            EmitSchema.ROUND_ENDED, EmitSchema.INSIDERS_REVEALED, EmitSchema.HOUSE_SIGNED_OFF,
        )
        for (kind in ending) {
            val classes = EmitSchema.classesFor(kind)
            assertTrue(classes.isNotEmpty(), "$kind has no row: the round would end and reach nobody")
            assertTrue(
                classes.all { it.roundState == RoundState.Ended },
                "$kind is permitted to $classes — an alignment reaches a round that is still running",
            )
        }
        // Completeness, the other way round: these three are the whole of what may name anybody.
        // A fourth cannot be added, given a row, and left out of the assertion above.
        assertEquals(
            ending.map { it.name }.sorted(),
            EmitSchema.knownKinds().map { it.name }
                .filter { it in setOf("RoundEnded", "InsidersRevealed", "HouseSignedOff") }
                .sorted(),
            "an ending kind exists that this test does not name",
        )

        // **The one kind that states an alignment while the round is running, and it is the
        // design's** (`gdd.md:923`, E-R2-2). It is not an exemption from the rule above so much as
        // a different rule: those three name somebody TO THE HOUSE, and this one names a phone to
        // itself. What makes it lawful is the addressing rather than the row, so that is what is
        // asserted here — the same claim `a seat is told its own role and no other seat's` makes
        // in full, restated at the invariant it is the exception to, so that a reader of this test
        // cannot come away believing no live kind carries a role.
        assertTrue(
            EmitSchema.classesFor(EmitSchema.ROLE_REVEALED).all { it.roundState == RoundState.Live },
            "the role reveal reaches a class outside the moment it belongs to",
        )
        assertEquals(
            listOf(Seat(2)),
            EmitSchema.deliveries(Effect.RoleRevealed(Seat(2), Role.Insider), live()).map { it.seat },
            "the reveal that names a role to a living phone reached more than that one phone",
        )
    }

    /**
     * **The reveal is bounded by time, in the type, and that is stronger than any of its
     * contents.**
     *
     * [RoundState.Ended] is reachable only through `GameState.outcome`, whose only writer is the
     * transition that emits these effects. So a reveal constructed at any other moment in the round
     * is offered to classes that do not exist yet and is delivered to **nobody** — rule 2 doing the
     * work rule 3 would otherwise have to. Asserted by building the leak: the same effect, against
     * a live round, reaching no phone in the house.
     */
    @Test
    fun `the reveal reaches nobody while the round is running`() {
        assertEquals(
            emptyList(), EmitSchema.deliveries(REVEAL, live()),
            "the app named an Insider to a living phone",
        )
        assertEquals(
            seats, EmitSchema.deliveries(REVEAL, over()).map { it.seat },
            "the round ended and the reveal reached fewer than everybody",
        )
    }

    /**
     * **The house signs off to the Insiders it owned, and to no Resident** (`gdd.md:1051`).
     *
     * The narrowest row in the table, and it is lawful for exactly one reason: by the time it is
     * delivered, [Effect.InsidersRevealed] has already published who the Insiders were, to
     * everybody, on the screen this message appears on.
     *
     * Both denials are asserted, because they are independent and either alone would be enough to
     * ship a leak. The **row** stops it reaching a Resident's class; the **addressing** stops it
     * reaching the other Insider, who is in the permitted class and is still not the recipient.
     */
    @Test
    fun `the sign-off reaches the addressed Insider and nobody else`() {
        val finished = over()
        assertEquals(
            listOf(ClientClass(Role.Insider, RoundState.Ended)),
            EmitSchema.classesFor(EmitSchema.HOUSE_SIGNED_OFF),
        )
        assertEquals(
            listOf(Seat(1)),
            EmitSchema.deliveries(Effect.HouseSignedOff(Seat(1), "Unfortunate."), finished).map { it.seat },
        )
        // Seat 0 is a Resident. Addressed to them by mistake, the row still declines it -- which is
        // the denial that survives somebody widening the addressing for an unrelated reason.
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.HouseSignedOff(Seat(0), "Unfortunate."), finished),
            "the house spoke to somebody it never owned",
        )
    }

    /**
     * **The contact seed reaches its own seat and no other phone, and only while that seat is
     * living** (D-212).
     *
     * Three denials, each independent. The **row** names both living classes — narrowed to either
     * role it would make the other role the phones the radio never sights. The **addressing** keeps
     * seat 3's key off seat 5's phone, which the row cannot, because two living phones are one
     * class. And the round-state axis declines it to a seat that is out, pre-arm or past the ending.
     */
    @Test
    fun `a contact seed reaches its own seat only and both living roles alike`() {
        assertEquals(
            listOf(
                ClientClass(Role.Resident, RoundState.Live),
                ClientClass(Role.Insider, RoundState.Live),
            ),
            EmitSchema.classesFor(EmitSchema.CONTACT_SEED_ISSUED),
            "the seed's row moved: narrowed to one role, the other is invisible to the radio",
        )
        val state = live()
        val seed = ContactSeed("0123456789abcdef0123456789abcdef")
        for (seat in seats) {
            assertEquals(
                listOf(seat),
                EmitSchema.deliveries(Effect.ContactSeedIssued(seat, seed), state).map { it.seat },
                "seat ${seat.index}'s seed did not go to exactly its own phone",
            )
        }
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.ContactSeedIssued(Seat(3), seed), state.copy(revoked = listOf(Seat(3)))),
            "a seed was issued to a seat that is out",
        )
        assertEquals(emptyList(), EmitSchema.deliveries(Effect.ContactSeedIssued(Seat(3), seed), over()))
        assertEquals(emptyList(), EmitSchema.deliveries(Effect.ContactSeedIssued(Seat(9), seed), state))
    }

    /**
     * **The seed's row is what lets it out at all** — rule 2, asserted on this kind rather than on a
     * made-up one. The kind has a row; a kind spelled one letter differently has none and reaches
     * nobody, so the day the row is deleted the seed ships to nobody rather than to everybody.
     */
    @Test
    fun `a contact seed with no row would ship to nobody`() {
        assertTrue(EmitSchema.CONTACT_SEED_ISSUED in EmitSchema.knownKinds(), "the seed has no row")
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("ContactSeedIssue")))
    }

    /** The ending itself is addressed per seat, and every seat gets exactly one. */
    @Test
    fun `the ending reaches every seat once`() {
        val finished = over()
        assertEquals(
            seats,
            seats.flatMap {
                EmitSchema.deliveries(
                    Effect.RoundEnded(it, Winner.Residents, WinRoute.Parity, Haptic.Short, integrity = 60), finished,
                )
            }.map { it.seat },
        )
    }
}
