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
            Effect.MeetingPhaseOpened(MeetingPhase.CheckIn, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Discussion, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Vote, haptic),
            Effect.MeetingPhaseOpened(MeetingPhase.Tally, haptic),
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
     * **THE INJECTION CATCHER.** An event may buzz the Short or the Long and there is no third
     * thing it may buzz — not a longer Egress, not a double tap for the ending, not a pattern a
     * screen thought suited it. A call site that reaches past [HouseBuzz] for a duration of its
     * own produces a script this test has never seen, and it fails naming the pattern.
     */
    @Test
    fun noEventBuzzesWithAPatternOfItsOwn() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        Haptic.entries.forEach { kind -> buzzing(kind).forEach(buzzer::heard) }

        val strays = motor.played.filter { it !in HouseBuzz.ALL }
        assertTrue(
            strays.isEmpty(),
            "an event buzzed with a pattern of its own: ${strays.distinct()} is neither the " +
                "Short (${HouseBuzz.script(Haptic.Short)}) nor the Long " +
                "(${HouseBuzz.script(Haptic.Long)}). D-102: same pattern, same duration, for " +
                "every player.",
        )
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

    /** The kind is read off the effect and never recomputed. Short stays Short; Long stays Long. */
    @Test
    fun theBuzzIsTheKindTheHousePutOnTheEffect() {
        Haptic.entries.forEach { kind ->
            val motor = RecordingHaptics()
            val buzzer = HouseBuzzer(motor)
            val effects = buzzing(kind)
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

    /** The quiet effects are quiet: counts, progress and content update a screen already lit. */
    @Test
    fun theEffectsThatCarryNoKindDoNotBuzz() {
        val motor = RecordingHaptics()
        val buzzer = HouseBuzzer(motor)
        val seat = Seat(1)
        listOf(
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
        ).forEach(buzzer::heard)

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
