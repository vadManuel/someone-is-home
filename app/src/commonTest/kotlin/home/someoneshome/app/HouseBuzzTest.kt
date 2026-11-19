package home.someoneshome.app

import home.someoneshome.platform.NoRadio
import home.someoneshome.model.ArmedTouch
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Destination
import home.someoneshome.model.DriftPath
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.EgressType
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.Sound
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.platform.RecordingHaptics
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.Subroutine
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **D-102, read off a recording instead of off a phone in somebody's hand.**
 *
 * *The buzz is identical for every player — same pattern, same duration, including when an
 * Insider's own Revoke lands — or it is an audible tell in a silent house.* That is a claim about
 * what a room can feel, and a room feels durations, so what these tests compare is scripts:
 * lists of [HapticStep] as the motor would have played them. A test that compared [Haptic] values
 * would only be asserting that two call sites agreed about a label.
 *
 * **This is the layer where it can escape.** The kinds are constructed in `Rules.kt`, one per
 * push, and `core` has its own tests for that. What is new here is the step between an effect
 * arriving and the motor turning: everything below is about that step having no opinions.
 */
class HouseBuzzTest {

    private val seats = listOf(Seat(0), Seat(3), Seat(7))

    /** Every effect that carries a [Haptic], built at both kinds and across several seats. */
    private fun buzzing(haptic: Haptic): List<Effect> = seats.flatMap { seat ->
        listOf(
            Effect.OpeningMessage(seat, haptic),
            Effect.MeetingOpened(seat, MeetingTrigger.MeetingCard, haptic),
            Effect.MeetingOpened(seat, MeetingTrigger.RevokeReported(Seat(2)), haptic),
            Effect.StandAndWalkIn(seat, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.CheckIn, null, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Discussion, null, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Vote, 45, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Tally, null, haptic),
            Effect.MeetingResult(seat, voted = 6, living = 6, haptic = haptic),
            Effect.MeetingResult(null, voted = 6, living = 6, haptic = haptic),
            Effect.RestrainedTakeover(seat, haptic),
            Effect.MeetingEnded(haptic),
            Effect.EgressOpened(
                seat, EgressType.Beacon, NODES, 180, haptic,
                pulseFrom = 86_402_000L,
            ),
            Effect.EgressOpened(
                seat, EgressType.Tether, OTHER_NODES, 180, haptic,
                pulseFrom = 86_402_000L,
            ),
            Effect.EgressHeld(90, running = true, haptic = haptic),
            Effect.EgressHeld(90, running = false, haptic = haptic),
            Effect.EgressContained(haptic),
            Effect.EgressSucceeded(haptic),
            Effect.RoundEnded(seat, Winner.Residents, WinRoute.SystemIntegrityCleared, haptic, integrity = 60),
            Effect.RoundEnded(seat, Winner.Insiders, WinRoute.EgressUncontained, haptic, integrity = 60),
        )
    }

    /**
     * **THE INJECTION CATCHER.** An event may buzz one of [HouseBuzz]'s kinds or ring, and there
     * is no other thing it may do — not a longer Egress, not a double tap for the ending, not a
     * pattern a screen thought suited it. A call site that reaches past [HouseBuzz] for a duration
     * of its own produces a script this test has never seen, and it fails naming the pattern.
     */
    @Test
    fun noEventBuzzesWithAPatternOfItsOwn() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        Haptic.entries.forEach { kind -> buzzing(kind).forEach(buzzer::heard) }

        val strays = motor.played.filter { it !in HouseBuzz.ALL }
        assertTrue(
            strays.isEmpty(),
            "an event buzzed with a pattern of its own: ${strays.distinct()} is not one of " +
                "${HouseBuzz.ALL}. D-102: same pattern, same duration, for every player.",
        )
    }

    /**
     * **THE RING, AND THE FACT THAT IT IS THE ONLY ONE.** D-160 makes the incoming call the one
     * signal in this game that repeats until answered; a second one would be a phone going off
     * forever in a silent house for a reason nobody in the room can hear the end of.
     *
     * The whole effect surface is walked, not a chosen few: **any** effect that grew a repeating
     * pattern fails here by name. Return `true` from another row of `Effect.ringing` and this is
     * the test that says which one.
     */
    @Test
    fun theCallIsTheOnlyThingThatKeepsGoing() {
        val repeating = mutableListOf<String>()
        for (effect in everyEffect()) {
            val motor = RecordingHaptics()
            HouseBuzzer(motor).heard(effect)
            if (motor.plays.any { it.repeating }) repeating += effect::class.simpleName.orEmpty()
        }
        assertEquals(
            listOf("MeetingOpened"), repeating.distinct(),
            "the set of effects that vibrate until answered is not exactly the incoming call",
        )
    }

    /**
     * The call rings the ring — LONG–pause–LONG — rather than D-135's single Long.
     *
     * **D-135 is amended, not replaced**, so the same test says the other four are untouched: an
     * Egress, STAND AND WALK IN, the Restrained takeover and LIGHTS OUT are one Long each and stop
     * on their own.
     */
    @Test
    fun theCallRingsAndTheOtherFourLongsDoNot() {
        val ringing = RecordingHaptics()
        HouseBuzzer(ringing).heard(Effect.MeetingOpened(Seat(0), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals(listOf(HouseBuzz.RING), ringing.played, "the call did not ring")
        assertTrue(ringing.ringing, "the call played once and gave up")

        val long = HouseBuzz.script(Haptic.Long)
        listOf(
            Effect.EgressOpened(
                Seat(0), EgressType.Beacon, NODES, 180, Haptic.Long,
                pulseFrom = 86_402_000L,
            ),
            Effect.StandAndWalkIn(Seat(0), Haptic.Long),
            Effect.RestrainedTakeover(Seat(0), Haptic.Long),
            Effect.MeetingEnded(Haptic.Long),
        ).forEach { effect ->
            val motor = RecordingHaptics()
            HouseBuzzer(motor).heard(effect)
            assertEquals(
                listOf(long), motor.played,
                "${effect::class.simpleName} left D-135's set of five single Longs",
            )
            assertTrue(!motor.ringing, "${effect::class.simpleName} is still going")
        }
    }

    /**
     * **Answering is what stops it, and nothing else has to.**
     *
     * D-160's *until answered* is only half a promise without something that answers. The other
     * half is that the ring survives everything short of it — the same call arriving twice does
     * not double it up, because the motor replaces rather than queues.
     */
    @Test
    fun theRingStopsWhenThePhoneIsAnsweredAndNotBefore() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        val call = Effect.MeetingOpened(Seat(0), MeetingTrigger.MeetingCard, Haptic.Long)

        buzzer.heard(call)
        buzzer.heard(call)
        assertTrue(motor.ringing, "the phone stopped ringing without anybody answering it")
        assertEquals(0, motor.stopped)

        buzzer.answered()
        assertTrue(!motor.ringing, "the phone was answered and it is still ringing")
        assertEquals(1, motor.stopped)
    }

    /**
     * The same effect, the same kind, a different seat: **one script, not one per player.**
     *
     * An Insider's own Revoke landing is the case D-102 names, and it is this one — the effect is
     * identical apart from whose phone it reached, and the recording must be too.
     */
    @Test
    fun theSameEffectBuzzesTheSameOnEverySeat() {
        Haptic.entries.forEach { kind ->
            val perSeat = seats.map { seat ->
                val motor = RecordingHaptics()
                HouseBuzzer(motor).heard(Effect.StandAndWalkIn(seat, kind))
                motor.played
            }
            assertEquals(
                1, perSeat.distinct().size,
                "STAND AND WALK IN at $kind buzzed differently by seat: $perSeat",
            )
        }
    }

    /**
     * The kind is read off the effect and never recomputed. Short stays Short; Long stays Long.
     *
     * **The call is left out and it is the only thing left out**, because D-160 took it out of the
     * kinds: `MeetingOpened` still carries a `Long` — `Rules.kt` put it there under D-135 and the
     * amendment did not go back and rewrite the reduction — and what the phone does with it is
     * ring. `theCallRingsAndTheOtherFourLongsDoNot` is the test that owns that row.
     */
    @Test
    fun theBuzzIsTheKindTheHousePutOnTheEffect() {
        Haptic.entries.forEach { kind ->
            val motor = RecordingHaptics()
            val buzzer = HouseBuzzer(motor)
            val effects = buzzing(kind).filterNot { it is Effect.MeetingOpened }
            effects.forEach(buzzer::heard)
            assertEquals(effects.size, motor.count, "$kind: not every buzzing effect buzzed")
            assertEquals(
                listOf(HouseBuzz.script(kind)), motor.played.distinct(),
                "an effect carrying $kind was played as something else",
            )
        }
    }

    /**
     * **D-156's channel, closed at this layer too.** *One notification, one haptic, identical for
     * everyone* — F-003 found the reveal leaking because an Insider's phone buzzed four times and
     * a Resident's once, at the moment the whole party is standing in a cluster. The content
     * effects carry no kind, so however much the house has to say, the phone goes off once.
     */
    @Test
    fun howMuchTheHouseHasToSayNeverDrivesTheBuzzCount() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        val seat = Seat(0)

        // What an Insider gets at the reveal: the opening delivery, and then four messages.
        buzzer.heard(Effect.OpeningMessage(seat, Haptic.Short))
        listOf("the blackmail", "the house line", "a partner", "another partner")
            .forEach { buzzer.heard(Effect.MessageDelivered(seat, it)) }
        val insider = motor.count

        motor.clear()
        // What a Resident gets: the opening delivery, and one message.
        buzzer.heard(Effect.OpeningMessage(seat, Haptic.Short))
        buzzer.heard(Effect.MessageDelivered(seat, "the house line"))
        val resident = motor.count

        assertEquals(
            resident, insider,
            "the reveal buzzed $insider time(s) for an Insider and $resident for a Resident — " +
                "F-003 exactly, and a buzz count is both audible and tactile across a cluster",
        )
        assertEquals(1, resident, "the opening delivery is one buzz, for everyone")
    }

    /** Every effect that carries no [Haptic]: counts, progress, content, and their own answers. */
    private fun quiet(): List<Effect> {
        val seat = Seat(1)
        return listOf(
            Effect.LampSet(seat, luminance = 200),
            Effect.AbilityFired(seat, cooldownStarted = true),
            Effect.SubroutineGraded(seat, accepted = true),
            Effect.SubroutineGraded(seat, accepted = false),
            Effect.SubroutineProgressed(remaining = 12),
            Effect.MessageDelivered(seat, "a line"),
            Effect.ScanAnswered(seat, opened = null),
            Effect.TerminalAnswered(seat, reading = true),
            Effect.TerminalAnswered(seat, reading = false),
            Effect.PresenceChanged(seat, at = MarkerId("SEED001"), open = true),
            Effect.CheckInProgressed(present = 3, expected = 8),
            Effect.ReadyProgressed(ready = 3, expected = 8),
            Effect.VoteHeld(seat, selection = Seat(2), locked = true),
            Effect.VoteSelectionShown(voter = seat, selection = Seat(2)),
            Effect.VoteProgressed(locked = 3, expected = 8),
            Effect.MeetingResolved(restrained = seat, attribution = emptyList()),
            Effect.SyncPulseAnswered(seat, held = true),
            Effect.SyncPulseAnswered(seat, held = false),
            Effect.SyncPulseBlanked(seat),
            Effect.InsidersRevealed(emptyList()),
            Effect.HouseSignedOff(seat, "one last thing"),
        )
    }

    /**
     * **Everything this file can build, which is every effect it constructs anywhere.**
     *
     * It is deliberately the union of the two fixtures rather than a third hand-written list: a
     * list nobody else uses drifts, and a drifted list of *every effect* is the worst kind, because
     * the thing it stops covering is exactly the thing somebody just added. The compile-time guard
     * for an effect nobody has built here is `Effect.ringing`'s exhaustive `when`.
     */
    private fun everyEffect(): List<Effect> =
        buzzing(Haptic.Long) + buzzing(Haptic.Short) + quiet()

    /**
     * **Every kind in the game, exactly once** — [everyEffect] plus the four it does not build.
     *
     * The Tier-2 four are not in the fixtures above because they arrived after them and because
     * three of them are silent, which is a bad reason to leave one out: *silent* is a claim, and a
     * claim nothing walks is a comment. This list is what the wire-parity sweep below runs on, and
     * [everyKindIsHere] is what stops it going stale — the kind name and the effect's own name are
     * the same string, so a new effect is a missing entry rather than a passing test.
     */
    private fun everyKind(): List<Effect> = everyEffect() + listOf(
        Effect.Surged(Seat(1), peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short),
        Effect.IsolateTargetsOffered(Seat(1), emptyList()),
        Effect.IsolateTargetsBlanked(Seat(1), emptyList()),
        Effect.AbilityBlanked(Seat(1)),
        Effect.StaticOpened(seconds = 10, seed = 4242L),
        // The three the quiet fixture leaves out because it is a per-seat list and these are not
        // addressed to a seat. They are exactly as silent as the rest of it, and now say so.
        Effect.SystemIntegrityLive(percent = 62),
        Effect.SystemIntegrityBatched(percent = 62),
        Effect.WorkOrderIssued(Seat(1), emptyList()),
        // **The four this list had silently stopped covering.** Two of them are not this unit's:
        // `RoleRevealed` and `MeetingCalling` arrived on the routing day and neither was ever run
        // through the motor, because the two hand-written lists in this file only ever checked each
        // other. The anchor below is what stops it recurring.
        Effect.RoleRevealed(Seat(1), Role.Resident),
        Effect.MeetingCalling(Seat(1)),
        Effect.RosterIssued(Seat(1), emptyList()),
        Effect.ContactSeedIssued(Seat(1), ContactSeed("0123456789abcdef0123456789abcdef")),
        Effect.RevokeLanded(Seat(1), haptic = null),
        // D-290: the two halves of a landed Revoke, which buzz Long, and the armed answer that does not.
        Effect.RevokeLanded(Seat(1), Haptic.Long),
        Effect.AbilityFired(Seat(1), cooldownStarted = true, touch = ArmedTouch.Landed),
        Effect.AbilityFired(Seat(1), cooldownStarted = true, touch = ArmedTouch.Spent),
        // A touch, answered (R1, D-216): silent, because every touch in the house sends one.
        Effect.ContactAnswered(Seat(1)),
        // The house notice (D-187, D-223): silent, because the gate's own push beside it buzzes.
        Effect.NoticeIssued(listOf(Seat(2))),
    )

    /**
     * The fixture really is every kind, checked against a hand-written list.
     *
     * Hand-written for `RecordingTextTest`'s reason: a list derived from the thing it is checking
     * agrees with itself. A new effect forces the two exhaustive `when`s in `HouseBuzzer.kt` to be
     * answered and then forces this line, which is the one that asks whether anybody has ever
     * actually run one through the motor.
     */
    @Test
    fun everyKindIsHere() {
        val walked = everyKind().map { EmitSchema.kindOf(it).name }.toSet()
        assertEquals(
            emptySet(), EVERY_KIND - walked,
            "these kinds are in the game and no fixture here has ever built one",
        )
        assertEquals(
            emptySet(), walked - EVERY_KIND,
            "the fixture builds a kind this list has never heard of",
        )
        // **The anchor, and it is the whole reason this test is worth having.** The two assertions
        // above compare two hand-written lists in one file, which is a pair that agrees with itself
        // — and it did, for three kinds, through two units and a rehearsal: `RoleRevealed` and
        // `MeetingCalling` were in the game and had never been run through the motor by anything
        // here. `RoundWireTest.theFixtureCarriesOneOfEveryEffect` uses this anchor and did not have
        // the gap. The two row-less kinds are named the way it names them: they are permitted to
        // nobody, so they are not in `knownKinds`, and a fixture that skipped them would leave two
        // branches of both exhaustive `when`s in `HouseBuzzer.kt` unrun.
        val known = EmitSchema.knownKinds().map { it.name }.toSet() +
            EmitSchema.PRESENCE_CHANGED.name + EmitSchema.EGRESS_SUCCEEDED.name
        assertEquals(
            emptySet(), known - walked,
            "the emit schema knows a kind this file's fixtures have never built",
        )
        assertEquals(
            emptySet(), walked - known,
            "this file builds a kind the emit schema has never heard of",
        )
    }

    /**
     * **D-290: a landed Revoke buzzes Long on both phones, and no other answer to a touch buzzes**
     * (the owner's 2026-11-18 ruling). In process and off the wire alike: the armed phone's answer
     * that says its Revoke landed, and the Revoked phone's landing. An arm spent on nobody, a Tier-2
     * or Egress answer, an unarmed touch's answer and a couch phone's re-assertion stay silent.
     */
    @Test
    fun aLandedRevokeBuzzesLongOnBothPhonesAndNoOtherTouchDoes() {
        val long = listOf(HouseBuzz.script(Haptic.Long))
        val landed = listOf(
            Effect.AbilityFired(Seat(1), cooldownStarted = true, touch = ArmedTouch.Landed),
            Effect.RevokeLanded(Seat(3), Haptic.Long),
        )
        val silent = listOf(
            Effect.AbilityFired(Seat(1), cooldownStarted = true, touch = ArmedTouch.Spent),
            Effect.AbilityFired(Seat(1), cooldownStarted = true),
            Effect.AbilityFired(Seat(1), cooldownStarted = false),
            Effect.ContactAnswered(Seat(1)),
            Effect.RevokeLanded(Seat(3), haptic = null),
        )
        for (effect in landed + silent) {
            val expected = if (effect in landed) long else emptyList()
            val motor = RecordingHaptics()
            HouseBuzzer(motor).heard(effect)
            assertEquals(expected, motor.played, "in process, $effect")
            val wire = RecordingHaptics()
            HouseBuzzer(wire).heard(RoundWire.project(effect))
            assertEquals(expected, wire.played, "off the wire, $effect")
        }
    }

    /**
     * **The wire buzzes whatever the effect buzzed** — the two lists cannot drift (D-102, D-134).
     *
     * A client never holds an `Effect`. The authority resolves each one through `EmitSchema`,
     * projects it into a `RoundMessage`, and writes *that* down the socket, so the buzzer needs an
     * answer for the message type as well — and two hand-written exhaustive `when`s over two
     * parallel sealed hierarchies is exactly the shape that agrees on the day it is written and
     * disagrees six weeks later, silently, on one branch.
     *
     * **So the wire's answer is not trusted, it is compared.** Every kind in the game is projected
     * the way the house projects it and both questions are asked on both sides. A drift shows up
     * naming the kind, which is what turns *somebody will notice* into *the build stops*.
     *
     * The failure this prevents is not academic: the projection is where a haptic could quietly be
     * dropped from a message, and a phone that stopped buzzing for one kind on one path would be a
     * phone that misses exactly one thing the house says — and the player who missed it has no way
     * to know they did.
     */
    @Test
    fun theWireBuzzesWhateverTheEffectBuzzed() {
        for (effect in everyKind()) {
            val message = RoundWire.project(effect)
            val kind = EmitSchema.kindOf(effect).name
            assertEquals(
                effect.haptic(), message.haptic(),
                "$kind buzzes differently off the wire than it does in process",
            )
            assertEquals(
                effect.ringing(), message.ringing(),
                "$kind rings on one side of the projection and not the other",
            )
        }
    }

    /**
     * **F-003, over the wire the phones really use, and it is the wire half that was never run.**
     *
     * D-156 closed F-003 at the effect: the opening reveal is one delivery with one haptic, and the
     * underlying message count never drives the buzz count. The leak it closed is that in `Known`
     * mode an Insider's phone buzzed four times — blackmail, house line, two partners — and a
     * Resident's buzzed once, **while the whole party is still standing in a cluster**, where a
     * buzz count is both audible and tactile.
     *
     * The guarantee had been asserted against effects handed straight to the buzzer. That is the
     * host's own view of what it said; what a player holds is the projection of it, and the reveal
     * is the one moment where the two roles' streams are legitimately different lengths. So this
     * runs the reveal through `RoundWire`, at the length each role really gets, and counts what the
     * motor did with what came out.
     *
     * **Different content, identical hand.** That sentence is the whole ruling and it is now
     * checked on the side the ruling is about.
     */
    @Test
    fun theRevealBuzzesOnceOnEitherRolesPhoneOverTheWire() {
        val seat = Seat(0)

        fun buzzesFor(bodies: List<String>): List<List<HapticStep>> {
            val motor = RecordingHaptics()
            val buzzer = HouseBuzzer(motor)
            val reveal: List<Effect> = listOf(Effect.OpeningMessage(seat, Haptic.Short)) +
                bodies.map { Effect.MessageDelivered(seat, it) }
            reveal.map(RoundWire::project).forEach(buzzer::heard)
            return motor.played
        }

        val insider = buzzesFor(
            listOf("the blackmail", "the house line", "a partner", "another partner"),
        )
        val resident = buzzesFor(listOf("the house line"))

        assertEquals(
            resident, insider,
            "the reveal buzzed ${insider.size} time(s) for an Insider and ${resident.size} for a " +
                "Resident once it had been through the wire — F-003 exactly, and the room is " +
                "standing in a cluster when it lands",
        )
        assertEquals(
            listOf(HouseBuzz.script(Haptic.Short)), resident,
            "the opening delivery is one Short buzz, for everyone, however much the house had to say",
        )
    }

    private companion object {

        /**
         * Every message kind in the game, by name. The kind's name and the effect's own name are
         * the same string, which is what lets a list this short be a completeness claim.
         */
        val EVERY_KIND: Set<String> = setOf(
            "LampSet", "AbilityFired", "AbilityBlanked", "Surged", "IsolateTargetsOffered", "IsolateTargetsBlanked", "StaticOpened",
            "SubroutineGraded", "SubroutineProgressed", "SystemIntegrityLive",
            "SystemIntegrityBatched", "MessageDelivered", "WorkOrderIssued", "ScanAnswered",
            "TerminalAnswered", "PresenceChanged", "OpeningMessage", "MeetingOpened", "StandAndWalkIn",
            "CheckInProgressed", "MeetingPhaseOpened", "ReadyProgressed", "VoteHeld",
            "VoteSelectionShown", "VoteProgressed", "MeetingResult", "MeetingResolved",
            "RestrainedTakeover", "MeetingEnded", "EgressOpened", "EgressHeld",
            "SyncPulseAnswered", "SyncPulseBlanked", "EgressContained", "EgressSucceeded", "RoundEnded",
            "InsidersRevealed", "HouseSignedOff",
            "RoleRevealed", "MeetingCalling", "RosterIssued", "RevokeLanded",
            "ContactSeedIssued",
            "ContactAnswered",
            "NoticeIssued",
        )

        /** The two nodes, as places. The rooms are never read here; the pair is what buzzes. */
        val NODES: List<Destination> = listOf(
            Destination(MarkerId("SEED001"), "circle", "UTILITY"),
            Destination(MarkerId("SEED002"), "square", "LANDING"),
        )

        val OTHER_NODES: List<Destination> = listOf(
            Destination(MarkerId("SEED003"), "triangle", "PORCH"),
            Destination(MarkerId("SEED004"), "cross", "STUDY"),
        )
    }

    /** The quiet effects are quiet: counts, progress and content update a screen already lit. */
    @Test
    fun theEffectsThatCarryNoKindDoNotBuzz() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        quiet().forEach(buzzer::heard)

        assertEquals(
            emptyList(), motor.played,
            "an effect with no kind on it reached the motor anyway — the buzz rides the effect " +
                "(D-134) and an effect the house did not put a Haptic on is not a buzz",
        )
    }

    /**
     * **Whether an effect was accepted never changes what the phone does.**
     *
     * Rule 1's shape, at the motor: `SubroutineGraded(accepted = true)` and `accepted = false`
     * are the same silence, and a Revoke that landed on a Revoked player buzzes what a Revoke
     * that landed buzzes. An asymmetry here would be a verdict delivered through the hand.
     */
    @Test
    fun successAndRefusalAreIndistinguishableToTheHand() {
        fun recordOf(effect: Effect) = RecordingHaptics().also { HouseBuzzer(it).heard(effect) }.played

        assertEquals(
            recordOf(Effect.SubroutineGraded(Seat(0), accepted = true)),
            recordOf(Effect.SubroutineGraded(Seat(0), accepted = false)),
            "a graded Subroutine buzzes differently depending on whether it was accepted",
        )
        assertEquals(
            recordOf(Effect.AbilityFired(Seat(0), cooldownStarted = true)),
            recordOf(Effect.AbilityFired(Seat(0), cooldownStarted = false)),
            "firing an ability buzzes differently depending on whether it took",
        )
        assertEquals(
            recordOf(Effect.MeetingResult(Seat(4), voted = 6, living = 6, haptic = Haptic.Short)),
            recordOf(Effect.MeetingResult(null, voted = 6, living = 6, haptic = Haptic.Short)),
            "the tally buzzes differently depending on whether the room Restrained anybody",
        )
    }

    // ---- The app root, and whether anything down there is actually playing ------------------------

    /**
     * **EVERY KIND THAT BUZZES HAS A PLAYER IN THE APP ROOT — E-D1-1, MADE IMPOSSIBLE.**
     *
     * This is the test the last four units were missing, and its absence is the whole reason there
     * is an N4. `HouseBuzzer` was real, exhaustive, and covered by nine tests above; every one of
     * them handed it an effect **by hand**. Nothing asked the only question that mattered — *does
     * anything in the shipping app ever call it* — and the answer was no, for weeks, while five
     * phones in a dark house were pushed thirty-eight kinds of message and none of them vibrated.
     *
     * So this drives the **production funnel** instead of the class: every kind in the game, encoded
     * as the house encodes it, arriving at a real [RoundOverTransport] the way a real socket delivers
     * it, with a recording motor at the far end. A kind that carries a `Haptic` or rings and whose
     * arrival produced nothing **fails here by name**, and so does the entire disease: delete the
     * buzzer's call in `apply` and thirteen kinds fail at once.
     *
     * The silent kinds are asserted too, in the same sweep and for the same money. *Silent* is a
     * claim about what a room can feel, and a claim nothing walks is a comment.
     */
    @Test
    fun everyKindThatBuzzesHasAPlayerInTheAppRoot() = runBlocking {
        val motor = RecordingHaptics()
        val kinds = everyKind()
        // The panel is real, because the funnel writes to it and a stub would be testing the stub.
        // Parked on the assembly gate: the meeting kinds in the fixture have a screen waiting there,
        // and a phone on the springboard would take a different route through `houseSaid`.
        val flow = FlowModel(PanelState(screen = ScreenId.Assemble))
        val round = RoundOverTransport(
            this, flow, buzzer = HouseBuzzer(motor), send = { }, radio = NoRadio, wake = home.someoneshome.platform.ScreenWake(home.someoneshome.platform.NoIdleTimer),
        )
        val draining = launch { round.run() }

        // Counted per effect, not per kind: since D-290 one kind (`AbilityFired`, `RevokeLanded`)
        // arrives buzzing in one value and silent in another, and both are walked.
        val played = mutableListOf<Int>()
        kinds.forEachIndexed { index, effect ->
            val before = motor.count
            val push = RoundBody.Push(seq = index + 1L, message = RoundWire.project(effect))
            round.frameArrived(TransportFrame.Carry(RoundWire.encode(push)))
            withTimeout(5_000) { while (round.held < index + 1L) delay(1) }
            played += motor.count - before
        }
        draining.cancel()

        val owed = kinds.indices.filter { kinds[it].haptic() != null || kinds[it].ringing() }.toSet()
        val silent = kinds.indices.toSet() - owed
        fun named(indices: Iterable<Int>) = indices.map { kinds[it] }.toSet()

        assertEquals(
            emptySet(), named(owed.filter { played[it] == 0 }),
            "these kinds carry a haptic and the app root played nothing when one arrived — " +
                "E-D1-1 exactly: a buzz nobody has a caller for is a buzz no player ever feels",
        )
        assertEquals(
            emptySet(), named(silent.filter { played[it] > 0 }),
            "these kinds carry no haptic and the phone went off anyway",
        )
        // And the sweep really did reach the motor, so a rig that silently delivered nothing
        // cannot pass by having asserted two empty sets against each other.
        assertTrue(owed.isNotEmpty() && motor.count >= owed.size, "no kind reached the motor at all")
    }

    /**
     * **The three Subroutines that ARE a haptic play the pattern the house dealt** (D-137, D-140,
     * Handshake's rhythm) — and they play the *instance*, never the port's fixture.
     *
     * The parameters are drawn per scan, ride the `ScanAnswered`, and are read by the same `of` the
     * screens read them with. A phone that fell back to `SubroutineModel.HANDSHAKE` would buzz a
     * rhythm the house never asked for and then be graded on the answer to the one it did — a defect
     * with nothing wrong on any screen, which is why the fixture is asserted **against** here rather
     * than merely not used.
     */
    @Test
    fun aSubroutinePlaysTheQuestionTheHouseDealtAndNotTheFixture() {
        // Deliberately not `SubroutineModel.HANDSHAKE_PARAMETERS` (3, 3, 6, 2): four beats at gaps
        // no fixture in this app uses, so a fallback is a failure rather than a coincidence.
        val dealt = listOf(4, 7, 2, 9)
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)

        buzzer.heard(
            RoundWire.project(
                Effect.ScanAnswered(
                    Seat(0),
                    SubroutineInstance(entry = 2, subroutine = SubroutineKind.Handshake, parameters = dealt),
                ),
            ),
        )
        assertEquals(emptyList(), motor.played, "the scan's answer buzzed the question at the briefing")

        buzzer.showing(ScreenId.SubHandshake)
        assertEquals(
            listOf(HandshakeRhythm.of(dealt).script), motor.played,
            "the Handshake did not play the rhythm the house dealt",
        )
        assertTrue(
            motor.played.single() != SubroutineModel.HANDSHAKE.script,
            "the Handshake played the port's fixture — a rhythm no house ever asked for",
        )
    }

    /**
     * The other two of the three, and the seven that are silent because their question is light.
     *
     * Sniff's two groups and Drift's *now* are each asserted against `model`'s own reading of the
     * same parameters, so a script composed on this side rather than read off the instance fails.
     */
    @Test
    fun sniffAndDriftBuzzTheirQuestionAndTheOtherSevenDoNot() {
        val sniff = listOf(2, 6, 9)
        val drift = SubroutineModel.DRIFT_PARAMETERS
        assertEquals(
            listOf(SniffGroups.of(sniff).script), scriptFor(SubroutineKind.Sniff, sniff),
            "Sniff did not buzz its two groups",
        )
        assertEquals(
            listOf(DriftPath.of(drift).script), scriptFor(SubroutineKind.Drift, drift),
            "Drift did not buzz its now-pulse",
        )

        val quiet = SubroutineKind.entries -
            setOf(SubroutineKind.Handshake, SubroutineKind.Sniff, SubroutineKind.Drift)
        val noisy = quiet.filter { kind ->
            // Their parameters are their own shapes; what is asserted is that nothing is played
            // whatever they carry, so an empty list is the honest input.
            scriptFor(kind, emptyList()).isNotEmpty()
        }
        assertEquals(
            emptyList(), noisy,
            "these Subroutines ask their question with light and buzzed one as well",
        )
    }

    /**
     * **The ring stops when the player walks off it, and stops on nothing else** (D-160).
     *
     * *Until answered* needed something that answers, and until now nothing did: `HouseBuzzer.answered`
     * had no caller in the app at all, so a phone rung by a meeting would have rung until the round
     * ended. Answering a house call is walking off the ring — the six seconds running out to the
     * assembly gate, or `MeetingCalling` moving the caller to their own screen.
     *
     * **The other half is what a screen change may NOT do.** D-135's four remaining Longs push screens
     * of their own, so a stop on every arrival would cut the Egress alert's long buzz in the frame it
     * started — the loudest thing the house says, silenced by the screen it opened.
     */
    @Test
    fun theRingStopsWhenThePhoneWalksOffItAndTheLongsSurviveTheScreenTheyOpen() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)

        buzzer.heard(Effect.MeetingOpened(Seat(0), MeetingTrigger.MeetingCard, Haptic.Long))
        buzzer.showing(ScreenId.Call)
        assertTrue(motor.ringing, "the phone stopped ringing on the ring's own screen")
        assertEquals(0, motor.stopped)

        buzzer.showing(ScreenId.Assemble)
        assertTrue(!motor.ringing, "the phone was answered and it is still ringing")
        assertEquals(1, motor.stopped)

        // And the caller's own route off the ring (E-R1-1), which is the other way it is answered.
        motor.clear()
        buzzer.heard(Effect.MeetingOpened(Seat(0), MeetingTrigger.MeetingCard, Haptic.Long))
        buzzer.showing(ScreenId.Calling)
        assertTrue(!motor.ringing, "the caller's phone rang on past the screen that says they called")

        // The Egress: one Long, then the alert it opens. The Long must still be the last thing said.
        motor.clear()
        buzzer.heard(
            Effect.EgressOpened(Seat(0), EgressType.Beacon, NODES, 180, Haptic.Long, pulseFrom = 86_402_000L),
        )
        buzzer.showing(ScreenId.Banner)
        assertEquals(0, motor.stopped, "the screen the Egress opened cut the Egress's own long buzz")
        assertEquals(listOf(HouseBuzz.script(Haptic.Long)), motor.played)
    }

    /**
     * **The call is the only thing that repeats, and the scripts are included in *only*.**
     *
     * `theCallIsTheOnlyThingThatKeepsGoing` walks the effects; this walks the other channel. A
     * Subroutine's question that looped would be a phone buzzing a rhythm at somebody until they
     * left the screen — in a silent house, on the darkest Subroutine on the roster.
     */
    @Test
    fun theCallIsTheOnlyScriptThatRepeats() {
        val repeating = SubroutineKind.entries.filter { kind ->
            val motor = RecordingHaptics()
            playOn(motor, kind, parametersFor(kind))
            motor.plays.any { it.repeating }
        }
        assertEquals(emptyList(), repeating, "a Subroutine's question repeats until something stops it")

        val ringing = RecordingHaptics()
        HouseBuzzer(ringing).heard(
            Effect.MeetingOpened(Seat(0), MeetingTrigger.MeetingCard, Haptic.Long),
        )
        assertTrue(
            ringing.plays.single().repeating,
            "the one thing in this game that must repeat has stopped repeating",
        )
    }

    /**
     * Walking off a Subroutine stops its question. The screen went away; the phone should not still
     * be asking — a rhythm buzzing in a pocket in another room is the player's own position, audible.
     */
    @Test
    fun leavingASubroutineStopsItsQuestion() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        openOn(buzzer, SubroutineKind.Sniff, parametersFor(SubroutineKind.Sniff))
        buzzer.showing(ScreenId.SubSniff)
        assertEquals(1, motor.count)
        buzzer.showing(ScreenId.Work)
        assertEquals(1, motor.stopped, "the phone kept asking a question the player had walked away from")
    }

    /**
     * **A phone standing on one Subroutine with another one open buzzes nothing.**
     *
     * The fail-closed direction, and it is not hypothetical: BEGIN opened the port's fixture for a
     * whole build (the payoff run), so *the screen and the open instance disagree* is a state this
     * app has really been in. The other direction is a player tapping a rhythm back against an
     * answer key for a different piece of work.
     */
    @Test
    fun aScreenThatIsNotTheOpenSubroutinesBuzzesNothing() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        openOn(buzzer, SubroutineKind.Handshake, parametersFor(SubroutineKind.Handshake))
        buzzer.showing(ScreenId.SubSniff)
        assertEquals(emptyList(), motor.played, "a Sniff screen buzzed the Handshake the house dealt")
    }

    /**
     * A phone that has scanned nothing buzzes nothing, which is every phone with no house attached.
     *
     * The fixture is right there in `ui` and is what a nullable open instance would be defaulted to
     * by anybody tidying this up. [assertNotNull] on the fixture keeps that sentence honest.
     */
    @Test
    fun aPhoneThatHasScannedNothingBuzzesNothing() {
        assertNotNull(SubroutineModel.HANDSHAKE.script.firstOrNull())
        val motor = RecordingHaptics()
        HouseBuzzer(motor).showing(ScreenId.SubHandshake)
        assertEquals(emptyList(), motor.played, "a phone with nothing open buzzed a question anyway")
    }

    /**
     * **THE HANDSHAKE, PLAYABLE — the whole way, from the house's push to the motor.**
     *
     * The owner's report on the payoff run was *"the handshake minigame does not vibrate at all"*,
     * and the reason had two halves that never met: BEGIN opened the port's fixture, and nothing in
     * the app called `HouseBuzzer` outside a bench. Every other test in this file drives one half.
     * This drives the join — **and it is the only one that does**, which is exactly the gap that let
     * a Subroutine be unplayable through four green units.
     *
     * A real work order, a real `ScanAnswered` off the wire, the real `beginSubroutine` with no
     * arguments (the call the button makes), a real [FlowModel] reporting its own arrivals. What is
     * asserted at the end is the rhythm the house dealt, in milliseconds, as a hand would feel it.
     */
    @Test
    fun theHandshakeIsPlayableFromThePushAllTheWayToTheMotor() {
        val dealt = listOf(5, 2, 8)
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        // The seam under test: `ui` reporting where this phone is, and nothing else joining them.
        val flow = FlowModel(PanelState(screen = ScreenId.Work), onScreen = buzzer::showing)

        fun said(message: RoundMessage) {
            buzzer.heard(message)
            flow.houseSaid(message)
        }

        // The work order first, because the caught scan is a join of two pushed facts and an answer
        // naming an entry no line holds moves nothing.
        said(
            RoundWire.project(
                Effect.WorkOrderIssued(
                    Seat(0),
                    listOf(
                        OrderLine.Known(
                            index = 0,
                            work = AssignedWork.Subroutine(SubroutineKind.Handshake),
                            done = false,
                            at = listOf(Destination(MarkerId("SEED001"), "circle", "UTILITY")),
                        ),
                    ),
                ),
            ),
        )
        said(
            RoundWire.project(
                Effect.ScanAnswered(
                    Seat(0),
                    SubroutineInstance(0, SubroutineKind.Handshake, dealt),
                ),
            ),
        )
        assertEquals(ScreenId.ScanCaught, flow.state.screen, "the scan's answer did not reach the briefing")
        assertEquals(emptyList(), motor.played, "the phone buzzed the question at the briefing")

        // BEGIN, called the way the button calls it: no arguments, off this phone's own last read.
        flow.beginSubroutine()

        assertEquals(ScreenId.SubHandshake, flow.state.screen, "BEGIN opened no Handshake")
        assertEquals(
            listOf(HandshakeRhythm.of(dealt).script), motor.played,
            "the Handshake still does not vibrate — the owner's payoff-run report, unchanged",
        )
    }

    /** The house's own answer, opened on this phone, without going near a screen id by hand. */
    private fun openOn(buzzer: HouseBuzzer, kind: SubroutineKind, parameters: List<Int>) =
        buzzer.heard(
            RoundWire.project(
                Effect.ScanAnswered(
                    Seat(0), SubroutineInstance(entry = 0, subroutine = kind, parameters = parameters),
                ),
            ),
        )

    /** Open one and stand on its own screen — the two acts BEGIN is, in order. */
    private fun playOn(motor: RecordingHaptics, kind: SubroutineKind, parameters: List<Int>) {
        val buzzer = HouseBuzzer(motor)
        openOn(buzzer, kind, parameters)
        // Named rather than skipped: a Subroutine with no screen would make every assertion below
        // it vacuously true, which is the shape of pass this project keeps writing down.
        buzzer.showing(assertNotNull(Subroutine.of(kind).screen, "$kind has no screen to stand on"))
    }

    private fun scriptFor(kind: SubroutineKind, parameters: List<Int>): List<List<HapticStep>> {
        val motor = RecordingHaptics()
        playOn(motor, kind, parameters)
        return motor.played
    }

    /**
     * Lawful parameters for the three that buzz, and an empty list for the seven that do not —
     * whose scripts are `null` before anything reads a number, so there is none to supply.
     */
    private fun parametersFor(kind: SubroutineKind): List<Int> = when (kind) {
        SubroutineKind.Handshake -> SubroutineModel.HANDSHAKE_PARAMETERS
        SubroutineKind.Sniff -> SubroutineModel.SNIFF_PARAMETERS
        SubroutineKind.Drift -> SubroutineModel.DRIFT_PARAMETERS
        else -> emptyList()
    }
}
