package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Seat
import home.someoneshome.model.SyncPulseGrid
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.platform.RecordingHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A failed containment attempt vibrates the tapping phone for the whole 3 s lockout, and nothing
 * else does** (the owner's O4, 2026-11-18, D-298), and **only a phone tapping feels the pulse** (O2).
 *
 * Driven through the real rules (`reduce`/`admit`), the real emit boundary (`EmitSchema.deliveries`),
 * the real projection and wire (`RoundWire`), and one real [HouseBuzzer] per seat over a recording
 * motor, so what is asserted is what each phone in the house was asked to play.
 */
class LockoutBuzzTest {

    private val markers = listOf("KITCHEN", "HALL", "STUDY", "LANDING", "GARAGE", "PORCH")
        .mapIndexed { i, room -> Destination(MarkerId("card-$i"), MarkerShapes.all[i + 1].id, room) }
    private val seats = (0..5).map { Seat(it) }
    private val insiders = listOf(Seat(2), Seat(4))
    private val pulseFrom = 86_402_000L
    private fun pulse(n: Long) = SyncPulseGrid.at(pulseFrom, n)

    /** Six phones, each with its own motor, hearing exactly what the house sends its seat. */
    private inner class House {
        var state: GameState = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = insiders, markers = markers),
        ).state
        val motors = seats.associateWith { RecordingHaptics() }
        private val buzzers = seats.associateWith { HouseBuzzer(motors.getValue(it)) }
        val nodes: List<MarkerId>
        var tick: Long

        /** What each seat's phone was sent for the last event, encoded as the wire carries it. */
        var frames: Map<Int, List<String>> = emptyMap()

        init {
            val firedAt = state.egressReadyAt
            nodes = listOf(state.activeMarkers.first(), state.activeMarkers.last())
            tick = firedAt.step
            put(Event.EgressFired(Tick(tick++), Seat(4), EgressType.Beacon, nodes, pulseFrom))
            motors.values.forEach { it.clear() }
        }

        fun put(event: Event) {
            val admission = admit(state, event)
            check(admission is Admission.Admitted) { "the bench's $event was refused: $admission" }
            state = admission.reduction.state
            val sent = mutableMapOf<Int, MutableList<String>>()
            for (effect in admission.reduction.effects) {
                for (delivery in EmitSchema.deliveries(effect, state)) {
                    val message = RoundWire.project(effect)
                    sent.getOrPut(delivery.seat.index) { mutableListOf() } +=
                        RoundWire.encode(RoundBody.Push(seq = 1L, message = message))
                    buzzers.getValue(delivery.seat).heard(message)
                }
            }
            frames = sent
        }

        fun scan(seat: Seat, card: MarkerId) = put(Event.MarkerScanned(Tick(tick++), seat, card))

        fun tapped(seat: Seat, taps: List<Long>) {
            // The lockout is on the house's ticks; later tests step past it explicitly.
            put(Event.SyncPulseReturned(Tick(tick++), seat, taps))
        }
    }

    private val lockout = listOf(HapticStep.Buzz(3_000))

    @Test
    fun theLockoutPatternIsOneUnbrokenBuzzExactlyAsLongAsTheHousesLockout() {
        assertEquals(lockout, HouseBuzz.script(Haptic.Lockout), "the lockout is not one 3 s buzz")
        assertEquals(
            Balance.SYNC_PULSE_LOCKOUT * 1_000L / Balance.TICKS_PER_SECOND, HouseBuzz.LOCKOUT_MILLIS.toLong(),
            "the vibration and the house's lockout are different lengths",
        )
        assertTrue(lockout in HouseBuzz.ALL, "the lockout is not one of the house's patterns")
    }

    /**
     * **Once, at the failed attempt, on that phone only**, for a Resident's attempt and an
     * Insider's, and the two hands feel exactly the same: one 3 s buzz, not repeating, on the tapper;
     * nothing on the five other phones, which were sent the blank.
     */
    @Test
    fun aFailedAttemptVibratesTheTappingPhoneOnceForTheLockoutAndNoOtherPhone() {
        val felt = listOf(Seat(0), Seat(2)).map { tapper ->
            val house = House()
            house.scan(tapper, house.nodes[0])
            house.motors.values.forEach { it.clear() }
            house.tapped(tapper, listOf(0L, 1L, 2L, 4L).map(::pulse))
            for ((seat, motor) in house.motors) {
                if (seat == tapper) {
                    assertEquals(listOf(RecordingHaptics.Play(lockout, repeating = false)), motor.plays, "the tapper ${seat.index} did not vibrate once for the lockout")
                } else {
                    assertEquals(emptyList(), motor.plays, "seat ${seat.index}, not tapping, felt seat ${tapper.index}'s failed attempt")
                }
            }
            house.motors.getValue(tapper).plays
        }
        assertEquals(felt[0], felt[1], "an Insider's failed attempt is not a Resident's in the hand")
    }

    /**
     * **Every failure is the same 3 s, and no success buzzes** (rule 1): a missed run, a seat the
     * house does not place at a node, a seat still locked; against a good run held for a partner and
     * the run that contains, which are silent on the tapper's phone (the containment's own Short is
     * every phone's, `EgressContained`).
     */
    @Test
    fun everyFailureFeelsTheSameAndNoGoodRunBuzzes() {
        val house = House()
        house.scan(Seat(0), house.nodes[0])
        house.scan(Seat(1), house.nodes[1])
        val tapper = house.motors.getValue(Seat(0))

        house.motors.values.forEach { it.clear() }
        house.tapped(Seat(0), listOf(0L, 1L, 2L).map(::pulse))
        val missed = tapper.plays.toList()
        tapper.clear()
        house.tapped(Seat(0), listOf(4L, 5L, 6L, 7L).map(::pulse))
        val locked = tapper.plays.toList()
        tapper.clear()
        house.tapped(Seat(3), listOf(4L, 5L, 6L, 7L).map(::pulse))
        val notAtANode = house.motors.getValue(Seat(3)).plays.toList()
        assertEquals(listOf(RecordingHaptics.Play(lockout)), missed, "a missed run")
        assertEquals(missed, locked, "a locked seat's attempt does not feel like a missed one")
        assertEquals(missed, notAtANode, "a seat not at a node does not feel like a missed one: a node detector in the hand")

        house.tick += Balance.SYNC_PULSE_LOCKOUT
        house.motors.values.forEach { it.clear() }
        house.tapped(Seat(0), listOf(8L, 9L, 10L, 11L).map(::pulse))
        assertEquals(emptyList(), tapper.plays, "a good run held for a partner buzzed")
        house.tapped(Seat(1), listOf(9L, 10L, 11L, 12L).map(::pulse))
        assertEquals(null, house.state.egress, "the overlapping runs did not contain")
        val containing = house.motors.getValue(Seat(1)).plays
        assertEquals(
            listOf(RecordingHaptics.Play(HouseBuzz.script(Haptic.Short))), containing,
            "the containing phone felt more than the containment every phone feels",
        )
    }

    /**
     * **The leak principle on the downlink**: for a failed attempt, a held run, a run four pulses off
     * its partner's (held, not paired) and the containing run, every living phone is sent one frame
     * of one length at that moment, the tapper's answer or the blank, whatever the answer says.
     */
    @Test
    fun theAnswerIsOneFrameOfOneLengthOnEveryPhoneWhateverItSays() {
        val house = House()
        house.scan(Seat(0), house.nodes[0])
        house.scan(Seat(1), house.nodes[1])
        fun pulseFrames(): Map<Int, List<Int>> = house.frames.mapValues { (_, fs) ->
            fs.filter { "SyncPulse" in it }.map { it.length }
        }
        val outcomes = mutableListOf<Map<Int, List<Int>>>()
        house.tapped(Seat(0), listOf(0L, 1L, 3L, 4L).map(::pulse))
        outcomes += pulseFrames()
        house.tick += Balance.SYNC_PULSE_LOCKOUT
        house.tapped(Seat(0), listOf(8L, 9L, 10L, 11L).map(::pulse))
        outcomes += pulseFrames()
        house.tapped(Seat(1), listOf(12L, 13L, 14L, 15L).map(::pulse))
        outcomes += pulseFrames()
        assertTrue(house.state.egress != null, "runs four pulses apart contained")
        house.tapped(Seat(0), listOf(13L, 14L, 15L, 16L).map(::pulse))
        outcomes += pulseFrames()
        assertEquals(null, house.state.egress, "the overlapping runs did not contain")
        val first = outcomes.first()
        assertEquals(seats.map { it.index }.toSet(), first.keys, "a living phone was sent no pulse frame")
        assertTrue(first.values.all { it.size == 1 }, "a phone was sent more or less than one pulse frame: $first")
        assertEquals(1, first.values.flatten().toSet().size, "the pulse frames differ in length across phones: $first")
        for (outcome in outcomes) assertEquals(first, outcome, "a different answer is a different frame on the wire")
    }

    /**
     * **O2: only a phone whose control is live feels the pulse** (D-295, confirmed). The felt pulse
     * is played by [PulseBuzzer] off `FlowModel.pulseFelt`; a phone whose `pulseFelt` is null buzzes
     * nothing, however long the Egress runs.
     */
    @Test
    fun aPhoneThatIsNotTappingFeelsNoPulse() = runBlocking {
        val motor = RecordingHaptics()
        var now = pulseFrom - 200
        val buzzer = PulseBuzzer(motor, pulseFrom = { null }, houseNow = { now })
        val job = launch { buzzer.run() }
        repeat(30) {
            now += 100
            delay(5)
        }
        job.cancel()
        assertEquals(0, buzzer.buzzed, "a phone with no live control buzzed the pulse")
        assertEquals(emptyList(), motor.plays)
    }
}
