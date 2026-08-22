package home.someoneshome.platform

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz

/**
 * **The motor, as far as anything above it is allowed to know.**
 *
 * Two ways in, and the difference between them is the whole of D-102:
 *
 * - [buzz] is **the house speaking**, and it takes a [Haptic] and nothing else. There is no
 *   duration parameter, no intensity, no per-screen override — the pattern comes out of
 *   [HouseBuzz] and a call site has no way to ask for one of its own. *The buzz is identical for
 *   every player, same pattern, same duration, including when an Insider's own Revoke lands, or
 *   it is an audible tell in a silent house.*
 * - [play] is **a Subroutine's question**, and its script is authored by the house and carried in
 *   the scan's parameters — Sniff's two groups, Drift's now-pulse. It is not an event's buzz and
 *   must never be used as one: `HouseBuzzTest` reads the recording and fails on any event pattern
 *   that is neither the Short nor the Long.
 *
 * ### A phone with no haptic hardware is silent, and says so to the log and to nobody else
 *
 * The Simulator has no motor. Rule 6 governs what that may look like: **errors are silent to the
 * player, loud to the authority** — no dialog, no toast, no screen state, because an error
 * surfacing as an Insider fires an ability is an alignment tell delivered by the crash handler.
 * So an engine that cannot start does exactly what a phone in a pocket looks like, and complains
 * where only a developer reads.
 *
 * ### Nothing here queues
 *
 * A buzz asked for while another is playing replaces it. A queue would turn two events that
 * happened at once into two buzzes in a row, and *how many times did that phone go off* is
 * precisely the channel D-156 closed when the reveal was leaking through it.
 */
interface Haptics {

    /** The house buzzes this phone. The pattern is [HouseBuzz]'s and never the caller's. */
    fun buzz(haptic: Haptic)

    /** Play a house-authored script — a Subroutine's question, and nothing else. */
    fun play(script: List<HapticStep>)

    /** Stop whatever is playing. The screen went away; the phone should not still be talking. */
    fun stop()
}

/**
 * **The motor this phone actually has.**
 *
 * The app root builds one and holds it: an engine costs tens of milliseconds to start, and D-135's
 * five are the events a player must not miss. A target with no haptic hardware returns something
 * that is silent rather than something that refuses — see the iOS actual for why the difference
 * matters more here than it does in most apps.
 */
expect fun deviceHaptics(): Haptics

/**
 * **A switch set on the process before it started, for the benches that cannot be tapped.**
 *
 * The motor is the one output in this app that a screenshot cannot show and a headless test
 * cannot feel, so the evidence that a real engine fired on a real phone is a device log — and a
 * log needs the buzzes to have happened, which needs somebody's finger, which is exactly what an
 * overnight unit does not have. This is that finger: `devicectl` launches the app with the
 * variable set and the haptic bench drives itself once.
 *
 * **It is not configuration and must never become it.** Balance values lock at arming and stamp
 * into the recording; nothing a round depends on may come from the environment a phone happened
 * to be launched with, because it would not be in the recording and the round would not replay.
 * The only caller is the cheat source set, which is absent from the compilation of a release
 * build.
 */
expect fun launchSwitch(name: String): Boolean

/**
 * **A motor that writes down what it was asked for instead of doing it.**
 *
 * Ships beside the interface the way [SeededCardScanner] does, and for the same reason: the
 * property that matters about haptics cannot be checked by feeling one. *Identical for every
 * player* is a statement about two recordings being equal, and equality of two vibrations felt
 * ten minutes apart by one person is not a check anyone can run.
 *
 * It records **scripts**, not kinds. Recording the kind would assert that the two call sites
 * agreed about a label; recording the script asserts they agreed about what the phone did, which
 * is the thing a room can feel.
 */
class RecordingHaptics : Haptics {

    private val entries = mutableListOf<List<HapticStep>>()

    /** Everything played, in order, as the motor would have played it. */
    val played: List<List<HapticStep>> get() = entries.toList()

    /** How many times the phone went off. D-156's channel, as a number a test can read. */
    val count: Int get() = entries.size

    override fun buzz(haptic: Haptic) {
        entries += HouseBuzz.script(haptic)
    }

    override fun play(script: List<HapticStep>) {
        entries += script
    }

    /** A stop is not a play, and does not erase what already happened. */
    override fun stop() = Unit

    fun clear() = entries.clear()
}

/**
 * **The motor on a target that has none.** Silent, and not an error.
 *
 * The desktop render harness and any host-side test that walks a real screen need a `Haptics` that
 * is a no-op rather than a throw: a screen that crashed on a missing motor would blank, and a
 * phone that blanks in a dark house is indistinguishable from a revocation (rule 5).
 */
object SilentHaptics : Haptics {
    override fun buzz(haptic: Haptic) = Unit
    override fun play(script: List<HapticStep>) = Unit
    override fun stop() = Unit
}
