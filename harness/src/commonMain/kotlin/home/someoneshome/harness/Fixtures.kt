package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Tick

/**
 * A moment worth snapshotting, named by a predicate rather than by a tick number.
 *
 * A tick number is a fact about one recording. *"The first time anyone was revoked"* is a fact
 * about the game, and it survives the round being re-recorded, the fixture being regenerated, and
 * the rules changing underneath it.
 *
 * [firstWhere] sees the state before the event, the state after it, and the event itself, and
 * fires on the **first** transition where it holds. Before-and-after rather than after alone,
 * because most interesting moments are edges — *became* revoked, *reached* the threshold — and a
 * predicate over the after-state alone matches every event from then on.
 */
class Mark(val label: String, val firstWhere: (GameState, GameState, Event) -> Boolean)

/**
 * Authority state at an interesting moment, produced by the rules rather than by hand.
 *
 * [afterEvent] is the index into the recording's event list, which is what makes this
 * regenerable: the same recording and the same index rebuild it exactly, and rebuild it
 * *differently* once the rules change — which is the entire point.
 */
class Snapshot(
    val label: String,
    val afterEvent: Int,
    val at: Tick,
    val state: GameState,
) {
    override fun toString(): String = "$label @ event $afterEvent, tick ${at.step}"
}

/** Nothing matched a mark. Fatal, because a fixture set that silently shrinks is worse than none. */
class UnmatchedMarks(val labels: List<String>) : IllegalArgumentException(
    "no event in this recording matched: ${labels.joinToString(", ")}. Refusing to return a " +
        "partial fixture set — a test asserting against a fixture that was silently dropped " +
        "passes for the one reason that means nothing."
)

/**
 * **Story 0.10d — fixtures snapshotted out of recordings, at interesting moments.**
 *
 * ### Why not hand-written state builders
 *
 * A hand-written `GameState` encodes the tester's imagination — the same failure as scripted
 * players, and for the same reason: you build the state you expected the rules to reach, so the
 * fixture agrees with your model of the game rather than with the game. A state produced by
 * driving actual events through actual rules cannot do that, and it **changes when the rules
 * change**, which is precisely when a stale fixture would otherwise start certifying the old
 * behaviour.
 *
 * ### It refuses to return a partial set
 *
 * A mark that matched nothing throws [UnmatchedMarks]. A fixture set that quietly comes back
 * short is this project's recurring failure in a new costume — the lint that scanned zero files,
 * the guard that inherited an exit code, the swap that changed nothing. **A test asserting
 * against a fixture that was silently dropped passes for the one reason that means nothing.**
 *
 * ### Refused events do not produce snapshots
 *
 * The walk goes through the admission gate, so an event the gate turned away never reaches a
 * predicate. A snapshot taken at a refused event would describe a moment the rules never saw.
 */
fun snapshots(initial: GameState, events: List<Event>, marks: List<Mark>): List<Snapshot> {
    require(marks.isNotEmpty()) { "no marks: nothing to snapshot" }
    require(marks.map { it.label }.toSet().size == marks.size) {
        "two marks share a label; a fixture set indexed by label would silently keep one"
    }

    val found = LinkedHashMap<String, Snapshot>()
    var before = initial
    drive(initial, events) { index, after, _ ->
        val event = events[index]
        for (mark in marks) {
            if (mark.label !in found && mark.firstWhere(before, after, event)) {
                found[mark.label] = Snapshot(mark.label, index, event.at, after)
            }
        }
        before = after
    }

    val missed = marks.map { it.label }.filterNot { it in found }
    if (missed.isNotEmpty()) throw UnmatchedMarks(missed)
    return marks.map { found.getValue(it.label) }
}

/** As [snapshots], over a recording. */
fun snapshots(recording: Recording, marks: List<Mark>): List<Snapshot> =
    snapshots(GameState.EMPTY, recording.events, marks)

/**
 * Rebuild one snapshot from its recording, to prove it is derived rather than remembered.
 *
 * This is the check that keeps a fixture honest across a rules change: regenerating produces the
 * state the *current* rules reach, so a fixture that has drifted from the rules stops matching
 * instead of quietly outliving them.
 */
fun regenerate(initial: GameState, events: List<Event>, snapshot: Snapshot): GameState =
    record(initial, events.take(snapshot.afterEvent + 1)).first

/**
 * Marks built only from vocabulary that exists.
 *
 * **Deliberately short.** The story names chain collapse, the parity threshold and mid-Egress as
 * interesting moments; none of those exist in the rules yet, and writing predicates for them now
 * would be inventing the events they depend on. [Mark] is the extension point, and these are the
 * ones that can be honest today.
 */
object Marks {

    /** The round begins. Every recording that arms has one. */
    val ARMED = Mark("armed") { before, after, _ -> !before.armed && after.armed }

    /** The first time anyone is revoked — the moment rule 1 exists to protect. */
    val FIRST_REVOCATION = Mark("first-revocation") { before, after, _ ->
        after.revoked.size > before.revoked.size
    }

    /** The first meeting opening, which is where every batched disclosure lands. */
    val FIRST_MEETING = Mark("first-meeting") { _, _, event -> event is Event.MeetingCalled }

    /** The first completed subroutine — the collective meter's first movement. */
    val FIRST_PROGRESS = Mark("first-progress") { before, after, _ ->
        after.systemIntegrity < before.systemIntegrity
    }

    /** A mark that fires when the meter has fallen to [remaining] or below. */
    fun integrityAtOrBelow(remaining: Int) = Mark("integrity<=$remaining") { before, after, _ ->
        before.systemIntegrity > remaining && after.systemIntegrity <= remaining
    }
}
