package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.platform.RecordingHaptics
import kotlin.test.Test
import kotlin.test.assertEquals
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
            Effect.MeetingResult(seat, haptic),
            Effect.MeetingResult(null, haptic),
            Effect.RestrainedTakeover(seat, haptic),
            Effect.MeetingEnded(haptic),
            Effect.EgressOpened(
                seat, EgressType.Beacon, listOf(MarkerId("SEED001"), MarkerId("SEED002")), 180, haptic,
            ),
            Effect.EgressOpened(
                seat, EgressType.Tether, listOf(MarkerId("SEED003"), MarkerId("SEED004")), 180, haptic,
            ),
            Effect.EgressHeld(90, running = true, haptic = haptic),
            Effect.EgressHeld(90, running = false, haptic = haptic),
            Effect.EgressContained(haptic),
            Effect.EgressSucceeded(haptic),
            Effect.RoundEnded(seat, Winner.Residents, WinRoute.SystemIntegrityCleared, haptic),
            Effect.RoundEnded(seat, Winner.Insiders, WinRoute.EgressUncontained, haptic),
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
                Seat(0), EgressType.Beacon, listOf(MarkerId("SEED001"), MarkerId("SEED002")), 180, Haptic.Long,
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
            Effect.PresenceChanged(seat, at = MarkerId("SEED001"), open = true),
            Effect.CheckInProgressed(present = 3, expected = 8),
            Effect.ReadyProgressed(ready = 3, expected = 8),
            Effect.VoteHeld(seat, selection = Seat(2), locked = true),
            Effect.VoteSelectionShown(voter = seat, selection = Seat(2)),
            Effect.VoteProgressed(locked = 3, expected = 8),
            Effect.MeetingResolved(restrained = seat, attribution = emptyList()),
            Effect.SyncPulseAnswered(seat, held = true),
            Effect.SyncPulseAnswered(seat, held = false),
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
            recordOf(Effect.MeetingResult(Seat(4), Haptic.Short)),
            recordOf(Effect.MeetingResult(null, Haptic.Short)),
            "the tally buzzes differently depending on whether the room Restrained anybody",
        )
    }
}
