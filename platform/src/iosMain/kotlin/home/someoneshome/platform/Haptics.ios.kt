package home.someoneshome.platform

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import platform.CoreHaptics.CHHapticDynamicParameter
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticPattern
import platform.CoreHaptics.CHHapticPatternPlayerProtocol
import platform.posix.getenv

/**
 * One [CoreHapticsMotor]. On the Simulator this is the same class discovering it has no hardware
 * and going quiet, which is the honest shape: there is no second implementation to keep in step,
 * and no build flag deciding which one a phone got.
 */
actual fun deviceHaptics(): Haptics = CoreHapticsMotor()

/** Set, at any value including the empty string. Absent is false; there is no parsing here. */
actual fun launchSwitch(name: String): Boolean = getenv(name) != null

/**
 * **The real motor: CoreHaptics, one engine, and patterns built out of the script it was given.**
 *
 * CoreHaptics rather than `UIImpactFeedbackGenerator` for one reason that decides it: the feedback
 * generators are a fixed vocabulary of taps whose durations Apple owns, and this game's buzzes are
 * durations *the design owns* — D-135's long haptic is long because a pocket must be able to tell
 * it from the short one, and `.heavy` is not a duration. A script of alternating buzzes and rests
 * (Sniff's two groups) has no expression in the generators at all.
 *
 * ### Every buzz is one continuous event at full intensity, and that is the doctrine as code
 *
 * Not a transient tap and not a curve: a continuous event has a duration, and duration is the only
 * thing D-102 lets a buzz differ in — [SHARPNESS] and intensity are constants here, identical for
 * every kind, every Subroutine and every player. The pattern for the two house kinds comes out of
 * [HouseBuzz]; this class chooses nothing.
 *
 * ### A phone that cannot buzz is silent and tells the log
 *
 * `capabilitiesForHardware().supportsHaptics` is false on the Simulator and on any handset without
 * a Taptic Engine, and the engine also refuses to start while some other audio session owns the
 * hardware. Every one of those paths does the same thing: nothing happens, and [log] says why.
 * Rule 6 — errors are silent to the *player* and loud to the *authority*: a dialog here would be
 * a screen appearing at the exact moment an ability landed, which is an alignment tell delivered
 * by the error handler.
 *
 * ### The engine is started once and left running under auto-shutdown
 *
 * Starting per buzz costs tens of milliseconds and D-135's five are the events a player must not
 * miss. `autoShutdownEnabled` hands the hardware back when nothing is playing, so a phone in a
 * pocket for four minutes between meetings is not holding the motor awake — which matters here in
 * a way it does not in most apps, because the evening runs on batteries in a dark house.
 */
class CoreHapticsMotor(private val log: (String) -> Unit = { println(it) }) : Haptics {

    private val supported: Boolean = CHHapticEngine.capabilitiesForHardware().supportsHaptics()

    private var engine: CHHapticEngine? = null
    private var player: CHHapticPatternPlayerProtocol? = null

    init {
        if (!supported) {
            log("$TAG this target has no haptic hardware — every buzz will be a silent no-op")
        }
    }

    override fun buzz(haptic: Haptic) {
        // The pattern is HouseBuzz's. There is no branch here on role, on seat, on which effect
        // asked, or on anything else a room could feel the difference of (D-102).
        play(HouseBuzz.script(haptic), what = haptic.name.uppercase())
    }

    override fun play(script: List<HapticStep>) = play(script, what = "SCRIPT")

    override fun stop() {
        val playing = player ?: return
        player = null
        runCatching { playing.stopAtTime(0.0, null) }
            .onFailure { log("$TAG stop failed: ${it.message}") }
    }

    private fun play(script: List<HapticStep>, what: String) {
        if (!supported) {
            log("$TAG $what silent — no haptic hardware")
            return
        }
        val events = eventsOf(script)
        if (events.isEmpty()) {
            log("$TAG $what has no buzzes in it — nothing to play")
            return
        }
        val running = started() ?: return
        try {
            // Named, because `parameters:` and `parameterCurves:` are two initialisers that both
            // accept an empty list and mean different things.
            val pattern = CHHapticPattern(
                events = events,
                parameters = emptyList<CHHapticDynamicParameter>(),
                error = null,
            )
            val next = running.createPlayerWithPattern(pattern, null)
            if (next == null) {
                log("$TAG $what could not build a player")
                return
            }
            // Replaces rather than queues: two events at once must not become two buzzes in a
            // row, because how many times a phone went off is exactly the channel D-156 closed.
            stop()
            player = next
            next.startAtTime(0.0, null)
            log("$TAG $what played — ${events.size} event(s), ${script.millis()}ms")
        } catch (e: Throwable) {
            // Loud to the authority, invisible to the player. A throw that reached the screen
            // would blank it, and a screen that blanks in the dark is a revocation to everyone
            // looking at it.
            log("$TAG $what failed: ${e.message}")
        }
    }

    /** The engine, started, or null with the reason in the log. Started once and kept. */
    private fun started(): CHHapticEngine? {
        engine?.let { return it }
        return try {
            val fresh = CHHapticEngine(null)
            fresh.playsHapticsOnly = true
            fresh.setAutoShutdownEnabled(true)
            // The system stops the engine when the app backgrounds or the media server resets.
            // Both are ordinary in a four-hour evening; neither may be a phone that has quietly
            // stopped buzzing for the rest of the round.
            fresh.stoppedHandler = { reason ->
                log("$TAG engine stopped, reason $reason — it will restart on the next buzz")
                engine = null
                player = null
            }
            fresh.resetHandler = {
                log("$TAG engine reset — restarting")
                runCatching { fresh.startAndReturnError(null) }
                    .onFailure { log("$TAG restart after reset failed: ${it.message}") }
            }
            fresh.startAndReturnError(null)
            engine = fresh
            log("$TAG engine started")
            fresh
        } catch (e: Throwable) {
            log("$TAG engine would not start: ${e.message}")
            null
        }
    }

    /**
     * The script as CoreHaptics events. A [HapticStep.Rest] is not an event — it is the gap
     * between two of them, so it moves the cursor and emits nothing.
     */
    private fun eventsOf(script: List<HapticStep>): List<CHHapticEvent> {
        var cursor = 0.0
        val events = mutableListOf<CHHapticEvent>()
        for (step in script) {
            when (step) {
                is HapticStep.Rest -> cursor += step.millis.seconds()
                is HapticStep.Buzz -> {
                    events += CHHapticEvent(
                        eventType = CHHapticEventTypeHapticContinuous,
                        parameters = listOf(
                            CHHapticEventParameter(CHHapticEventParameterIDHapticIntensity, INTENSITY),
                            CHHapticEventParameter(CHHapticEventParameterIDHapticSharpness, SHARPNESS),
                        ),
                        relativeTime = cursor,
                        duration = step.millis.seconds(),
                    )
                    cursor += step.millis.seconds()
                }
            }
        }
        return events
    }

    private fun Int.seconds(): Double = this / MILLIS_PER_SECOND

    private fun List<HapticStep>.millis(): Int = sumOf {
        when (it) {
            is HapticStep.Buzz -> it.millis
            is HapticStep.Rest -> it.millis
        }
    }

    private companion object {

        /** What the device log is grepped for. The evidence a unit that cannot feel a phone has. */
        const val TAG = "[haptics]"

        /** Full. A buzz that could be quieter is a buzz that could differ between two players. */
        const val INTENSITY = 1.0f

        /** Middle. One value for every buzz in the game, for [INTENSITY]'s reason. */
        const val SHARPNESS = 0.5f

        const val MILLIS_PER_SECOND = 1000.0
    }
}
