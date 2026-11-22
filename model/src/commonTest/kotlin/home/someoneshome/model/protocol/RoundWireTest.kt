package home.someoneshome.model.protocol

import home.someoneshome.model.Balance
import home.someoneshome.model.ArmedTouch
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.CircuitStation
import home.someoneshome.model.Destination
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.RoomKind
import home.someoneshome.model.RoomView
import home.someoneshome.model.StoreyView
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The projection, and the three things it must never be able to get wrong.**
 *
 * It must name the same kind the allowlist is keyed on; it must survive a round trip through the
 * socket's own encoder; and a message addressed to one seat must be unable to say which seat.
 */
class RoundWireTest {

    /**
     * **One of every effect there is**, so that the tests below are sweeps rather than samples.
     *
     * The completeness of this list is itself asserted — a new effect that nobody adds here fails
     * `theFixtureCarriesOneOfEveryEffect` rather than quietly reducing every sweep by one.
     */
    private val everyEffect: List<Effect> = listOf(
        Effect.LampSet(Seat(3), luminance = 1),
        Effect.AbilityFired(Seat(3), cooldownStarted = true),
        Effect.AbilityBlanked(Seat(3)),
        Effect.Surged(Seat(3), peak = 100, black = 0, haptic = Haptic.Short),
        Effect.IsolateTargetsOffered(
            Seat(3),
            listOf(Destination(MarkerId("M-1"), shape = "diamond", room = "KITCHEN")),
        ),
        Effect.IsolateTargetsBlanked(
            Seat(3),
            listOf(Destination(MarkerId("M-1"), shape = "diamond", room = "KITCHEN")),
        ),
        Effect.StaticOpened(seconds = 10, seed = 4242L),
        Effect.PlanIssued(
            Seat(3),
            HomePlanView(
                listOf(StoreyView("GROUND", 2, 1, listOf(0, 1))),
                listOf(RoomView("KITCHEN", RoomKind.Room), RoomView("HALL", RoomKind.Room)),
            ),
        ),
        Effect.SubroutineGraded(Seat(3), accepted = true),
        Effect.UpstreamWaiting(Seat(3), entry = 4),
        Effect.WorkUnblocked(Seat(3)),
        Effect.SubroutineProgressed(remaining = 41),
        Effect.SystemIntegrityLive(percent = 87),
        Effect.SystemIntegrityBatched(percent = 87),
        Effect.MessageDelivered(Seat(3), body = "THE HOUSE GROWS WEAKER"),
        Effect.WorkOrderIssued(
            Seat(3),
            listOf(
                OrderLine.Known(
                    index = 0,
                    work = AssignedWork.Subroutine(SubroutineKind.Handshake),
                    done = false,
                    at = listOf(Destination(MarkerId("M-1"), shape = "diamond", room = "KITCHEN")),
                ),
                OrderLine.Blocked(index = 1),
            ),
        ),
        Effect.ScanAnswered(
            Seat(3),
            SubroutineInstance(entry = 0, subroutine = SubroutineKind.Handshake, parameters = listOf(2, 5)),
            haptic = null,
            // The disk in hand rides every answer (D-322), so the sweep carries one.
            carrying = DiskInHand(disk = 1, to = CircuitStation.Rack),
        ),
        Effect.TerminalAnswered(Seat(3), reading = true),
        Effect.CardUnregistered(Seat(3)),
        Effect.PresenceChanged(Seat(3), at = MarkerId("M-1"), open = true),
        Effect.OpeningMessage(Seat(3), Haptic.Short, Balance.REVOKE_COOLDOWN),
        // The Insider's copy, because the projection's one decision on this kind is the
        // narrowing of `Role` to a bit and `Role.Resident` would project `false` either way.
        Effect.RoleRevealed(Seat(3), Role.Insider),
        Effect.RosterIssued(Seat(3), listOf("PRIYA", "MARCUS", "", "ROSE")),
        Effect.ContactSeedIssued(Seat(3), ContactSeed("0123456789abcdef0123456789abcdef")),
        Effect.ContactAnswered(Seat(3)),
        Effect.MeetingOpened(Seat(3), MeetingTrigger.MeetingCard, Haptic.Long),
        Effect.MeetingCalling(Seat(3)),
        Effect.StandAndWalkIn(Seat(3), Haptic.Long),
        Effect.CheckInProgressed(present = 4, expected = 6),
        Effect.MeetingPhaseOpened(MeetingPhase.Vote, voteWindowSeconds = 45, discussionSeconds = null, haptic = Haptic.Short),
        Effect.ReadyProgressed(ready = 2, expected = 6),
        Effect.VoteHeld(Seat(3), selection = Seat(2), locked = true),
        Effect.VoteSelectionShown(voter = Seat(3), selection = Seat(2)),
        Effect.VoteProgressed(locked = 3, expected = 6),
        Effect.MeetingResult(restrained = Seat(2), voted = 6, living = 6, haptic = Haptic.Short),
        Effect.MeetingResolved(restrained = Seat(2), attribution = listOf(Seat(3) to Seat(2), Seat(4) to null)),
        Effect.RestrainedTakeover(Seat(3), Haptic.Long),
        Effect.RevokeLanded(Seat(4), Haptic.Long),
        Effect.NoticeIssued(listOf(Seat(2)), switchedOff = listOf(Seat(5))),
        Effect.MeetingEnded(Haptic.Long, Balance.REVOKE_COOLDOWN),
        Effect.EgressOpened(
            Seat(3), EgressType.Tether,
            // Destinations, because the widget NAMES the two nodes. The room is host-authored text
            // and rides the same round trip every other string on this wire does.
            listOf(
                Destination(MarkerId("M-1"), "circle", "UTILITY"),
                Destination(MarkerId("M-2"), "square", "LANDING"),
            ),
            remaining = 900, haptic = Haptic.Long,
            pulseFrom = 86_402_000L,
        ),
        Effect.EgressHeld(remaining = 400, running = false, haptic = Haptic.Short),
        Effect.SyncPulseAnswered(Seat(3), held = true),
        Effect.SyncPulseBlanked(Seat(3)),
        Effect.EgressContained(Haptic.Short),
        Effect.EgressSucceeded(Haptic.Short),
        Effect.RoundEnded(Seat(3), Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short, integrity = 60),
        Effect.InsidersRevealed(listOf(InsiderNamed(Seat(1), name = "PRIYA", line = "a line"))),
        Effect.HouseSignedOff(Seat(1), body = "Unfortunate."),
        Effect.HouseClosedOnPermission,
    )

    /**
     * The sweep's own guard. Every kind the allowlist knows about is here, and so are the two that
     * are named and permitted to nobody — [EmitSchema.PRESENCE_CHANGED] and
     * [EmitSchema.EGRESS_SUCCEEDED] — because the projection is exhaustive over [Effect] and a
     * fixture that skipped them would leave two branches of it unrun.
     */
    @Test
    fun theFixtureCarriesOneOfEveryEffect() {
        val covered = everyEffect.map { EmitSchema.kindOf(it) }
        assertEquals(covered.size, covered.distinct().size, "the fixture repeats a kind")
        // The map's beat kinds (D-316) have rows and no effect: they ride the upload's answer.
        val missing = EmitSchema.knownKinds().filterNot { it in covered || it in EmitSchema.BEAT_KINDS }
        assertEquals(emptyList(), missing, "the fixture has no effect for $missing")
        assertTrue(EmitSchema.PRESENCE_CHANGED in covered, "the row-less presence kind is unswept")
        assertTrue(EmitSchema.EGRESS_SUCCEEDED in covered, "the row-less egress kind is unswept")
    }

    /**
     * **The drift guard.**
     *
     * The projection and the allowlist are two `when`s over the same sealed type in two files, and
     * the day they disagree the pipeline consults one for permission and sends the other's message.
     * This is what makes [RoundMessage.kind] safe to be the pipeline's only question.
     */
    @Test
    fun everyEffectProjectsToItsOwnKind() {
        for (effect in everyEffect) {
            assertEquals(
                EmitSchema.kindOf(effect), RoundWire.project(effect).kind,
                "the projection of $effect names a different kind than the allowlist does",
            )
        }
    }

    /** What goes down a socket has to come back the same. D8: versioned JSON, round-tripped. */
    @Test
    fun everyMessageSurvivesTheWire() {
        for ((index, effect) in everyEffect.withIndex()) {
            val push = RoundBody.Push(seq = index + 1L, message = RoundWire.project(effect))
            val back = RoundWire.decodeOrNull(RoundWire.encode(push))
            assertEquals(push, back, "$effect did not survive the round trip")
        }
    }

    /** The client's one upward body, which is bookkeeping and carries a number. */
    @Test
    fun aGapSurvivesTheWire() {
        val gap = RoundBody.Gap(held = 17)
        assertEquals(gap, RoundWire.decodeOrNull(RoundWire.encode(gap)))
    }

    /**
     * **A message addressed to one seat cannot say which seat it is for.**
     *
     * Not a saving. The socket is the address, and a seat number riding inside the payload as well
     * would be a second statement of the same fact — one that can be wrong on its own, and one a
     * phone could read to learn a number the house never meant to hand it. Projecting the same
     * effect for two different recipients has to produce the same bytes.
     *
     * The kinds not in this list are the broadcast ones whose *content* is a seat: who called the
     * meeting, who was restrained, who cast what. Those are disclosures their own allowlist rows
     * already decided, and they are asserted to keep their seats below.
     */
    @Test
    fun aSeatAddressedMessageCannotTellWhichSeatItIsFor() {
        fun bothWays(of: (Seat) -> Effect) {
            assertEquals(
                RoundWire.project(of(Seat(2))), RoundWire.project(of(Seat(5))),
                "this message changes with its recipient: ${of(Seat(2))}",
            )
        }
        bothWays { Effect.LampSet(it, luminance = 1) }
        bothWays { Effect.AbilityFired(it, cooldownStarted = true) }
        bothWays { Effect.SubroutineGraded(it, accepted = true) }
        bothWays { Effect.MessageDelivered(it, body = "x") }
        bothWays { Effect.WorkOrderIssued(it, emptyList()) }
        bothWays { Effect.ScanAnswered(it, opened = null, haptic = Haptic.Double) }
        bothWays { Effect.TerminalAnswered(it, reading = false) }
        bothWays { Effect.CardUnregistered(it) }
        bothWays { Effect.PresenceChanged(it, at = null, open = false) }
        bothWays { Effect.OpeningMessage(it, Haptic.Short, Balance.REVOKE_COOLDOWN) }
        bothWays { Effect.StandAndWalkIn(it, Haptic.Long) }
        bothWays { Effect.VoteHeld(it, selection = Seat(2), locked = true) }
        bothWays { Effect.RestrainedTakeover(it, Haptic.Long) }
        // D-290: what a touch did with the arm, and the landing's buzz, say nothing about the seat.
        bothWays { Effect.AbilityFired(it, cooldownStarted = true, touch = ArmedTouch.Landed) }
        bothWays { Effect.RevokeLanded(it, Haptic.Long) }
        bothWays { Effect.RevokeLanded(it, haptic = null) }
        // **E-R1-1's whole safety.** The house says *you are the one calling* by choosing a socket;
        // if the seat rode inside the payload as well, the routing that answers the escalation would
        // have handed every phone the number the wire is built never to hand it.
        bothWays { Effect.MeetingCalling(it) }
        bothWays { Effect.EgressOpened(it, EgressType.Beacon, emptyList(), 1, Haptic.Long, pulseFrom = 86_402_000L) }
        bothWays { Effect.SyncPulseAnswered(it, held = true) }
        bothWays { Effect.SyncPulseBlanked(it) }
        bothWays { Effect.RoundEnded(it, Winner.Insiders, WinRoute.Parity, Haptic.Short, integrity = 60) }
        bothWays { Effect.HouseSignedOff(it, body = "x") }
    }

    /**
     * The other half of the same rule: a message whose content **is** a seat keeps it.
     *
     * Written down so that the test above cannot be "fixed" one day by stripping seats from
     * everything — the room learning who it restrained is the meeting's whole output, and a
     * `MeetingResult` that had lost its seat would be a vote with no result in it.
     */
    @Test
    fun aMessageWhoseContentIsASeatKeepsIt() {
        val resolved = RoundWire.project(
            Effect.MeetingResolved(Seat(2), listOf(Seat(3) to Seat(2), Seat(4) to null)),
        ) as RoundMessage.MeetingResolved
        assertEquals(Seat(2), resolved.restrained)
        assertEquals(
            listOf(RoundMessage.Cast(Seat(3), Seat(2)), RoundMessage.Cast(Seat(4), null)),
            resolved.attribution,
            "the attribution list lost who voted for whom",
        )

        val shown = RoundWire.project(Effect.VoteSelectionShown(Seat(3), Seat(2)))
        assertEquals(RoundMessage.VoteSelectionShown(Seat(3), Seat(2)), shown)

        val opened = RoundWire.project(Effect.MeetingOpened(Seat(4), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals(Seat(4), (opened as RoundMessage.MeetingOpened).caller)
    }

    /**
     * **The two channels share one opaque body and must not answer for each other.**
     *
     * A `Carry` holds the lobby's standing as readily as a round's push, and a decoder that
     * accepted the wrong one would let a lobby message drive a round screen — or, on the host,
     * let a client's standing be read as a request for somebody's stream.
     */
    @Test
    fun neitherWireAnswersForTheOther() {
        val standing = LobbyWire.encode(LobbyBody.Standing(joined = 3, linesIn = 2))
        assertNull(RoundWire.decodeOrNull(standing), "a lobby body decoded as a round body")

        val push = RoundWire.encode(RoundBody.Push(1, RoundMessage.MeetingEnded(Haptic.Long, Balance.REVOKE_COOLDOWN)))
        assertNull(LobbyWire.decodeOrNull(push), "a round body decoded as a lobby body")

        assertNull(RoundWire.decodeOrNull("not json at all"))
        assertNotNull(RoundWire.decodeOrNull(push))
    }

    /**
     * **The reveal really does publish the line, which is the half of D-116 that makes the round
     * land** (`gdd.md:1063`).
     *
     * The positive control, and it is here rather than only in the round test for the reason
     * `OneLineNeverPersistedTest` keeps one: a sweep that finds no line passes for the same reason
     * whether the pipeline is clean or the sweep is looking at nothing. **The negative half — that
     * no frame carries a line until the round is over — is a property of a round and not of a
     * type**, so it is asserted over real frames from a real socket in `EffectPushTest`.
     */
    @Test
    fun theRevealPublishesTheLineItIsThereToPublish() {
        val line = "i still have priya's spare key"
        val reveal = RoundWire.project(Effect.InsidersRevealed(listOf(InsiderNamed(Seat(1), "PRIYA", line))))
        assertTrue(
            RoundWire.encode(RoundBody.Push(1, reveal)).contains(line),
            "the reveal published no line at all",
        )
        assertEquals(
            listOf(ClientClassNames.INSIDER_ENDED, ClientClassNames.RESIDENT_ENDED),
            EmitSchema.classesFor(EmitSchema.INSIDERS_REVEALED).map { it.toString() }.sorted(),
            "the one kind that carries a line reaches a class that exists before the round ends",
        )
    }

    /** The two class names the reveal may reach, written out so the assertion above reads. */
    private object ClientClassNames {
        const val INSIDER_ENDED = "Insider/Ended"
        const val RESIDENT_ENDED = "Resident/Ended"
    }
}
