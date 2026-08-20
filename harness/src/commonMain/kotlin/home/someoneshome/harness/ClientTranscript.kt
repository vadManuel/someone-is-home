package home.someoneshome.harness

import home.someoneshome.model.ClientClass
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat

/**
 * Everything one client received, in the order it received it (story 0.5).
 *
 * [lines] is the payload stream and nothing else — no class, no seat, no authority state. It is
 * the bytes destined for that phone, and it is meant to be readable as *what this player could
 * have known*.
 *
 * [classesSeen] is metadata about the capture, kept out of [lines] on purpose. A client is never
 * sent its own classification; recording it inside the payload stream would make the transcript
 * a description of the round rather than a copy of the wire.
 */
class ClientTranscript(
    val seat: Seat,
    val lines: List<String>,
    val classesSeen: List<ClientClass>,
) {
    fun toText(): String = buildString {
        appendLine("C seat=${seat.index}")
        lines.forEach { appendLine("M $it") }
    }
}

/**
 * The per-client transcripts of a whole round, one per seat, ordered by seat.
 *
 * **A List, not a Map.** Hash order varies, and these transcripts are compared byte for byte by
 * story 0.6 — an unstable iteration order would make the differential harness report divergence
 * that is an artifact of the container.
 *
 * **Unlike a [Recording], these hold no authority state.** Each one is exactly what one player
 * could have seen, so a single transcript is safe to look at. The *set* of them is not: reading
 * two side by side is the one view of the round no player ever has.
 */
class ClientTranscripts(val perClient: List<ClientTranscript>) {

    /** Empty for a seat that received nothing, and for a seat that never existed. */
    fun linesFor(seat: Seat): List<String> =
        perClient.firstOrNull { it.seat.index == seat.index }?.lines ?: emptyList()

    val seats: List<Seat> get() = perClient.map { it.seat }

    fun toText(): String = buildString {
        appendLine(HEADER)
        perClient.forEach { append(it.toText()) }
    }

    companion object {
        const val HEADER = "someone-is-home/client-transcripts/1"
    }
}

/**
 * **Story 0.5 — the per-client transcript recorder at the effect boundary.**
 *
 * Runs a round and captures, per client, every byte destined for it.
 *
 * ### It has no delivery policy of its own
 *
 * Recipients come from [EmitSchema.deliveries] and from nowhere else. **The recorder is checked
 * against the allowlist, not the allowlist against the recorder** — a recorder that decided for
 * itself who got what would be a second, untested copy of the redaction rules, and the copy that
 * ships would be the other one.
 *
 * ### Classification uses the state AFTER the event
 *
 * The round-state axis moves during a round, and it moves *because* of the event being processed.
 * A player revoked by a contact is out at the moment the effects of that contact land, so those
 * effects are offered to them as an out client. Classifying against the state before the event
 * would give a revoked player one last frame of living-client permissions, which is a leak with a
 * one-event lifetime and no symptom.
 *
 * ### Payload
 *
 * One kind, one payload — [Transcript.render], the same canonical rendering the recording uses.
 * There is deliberately no per-class variant of a payload: narrowing content per recipient inside
 * one message kind is redaction by nulling fields under another name. If two classes need
 * different content, that is two kinds and two rows in the allowlist.
 */
fun recordPerClient(initial: GameState, events: List<Event>): ClientTranscripts {
    val lines = mutableMapOf<Int, MutableList<String>>()
    val classes = mutableMapOf<Int, MutableList<ClientClass>>()
    val order = mutableListOf<Seat>()

    fun slot(seat: Seat) {
        if (order.none { it.index == seat.index }) order += seat
    }

    // Every seat that is ever seated gets a transcript, including one that receives nothing.
    // An absent transcript and an empty one are different claims, and only one of them is
    // checkable — a seat missing from the output would silently pass every assertion about what
    // it must not contain.
    //
    // Seeded from the INITIAL state as well as from every post-event state. Harvesting only from
    // post-event states meant an already-armed round with no events returned zero transcripts for
    // eight seated players, and every "seat N must not contain X" assertion then passed against a
    // transcript that did not exist.
    initial.seats.forEach { slot(it) }

    drive(initial, events) { _, after, emitted ->
        after.seats.forEach { slot(it) }
        for (effect in emitted) {
            val payload = Transcript.render(effect)
            for (delivery in EmitSchema.deliveries(effect, after)) {
                slot(delivery.seat)
                lines.getOrPut(delivery.seat.index) { mutableListOf() } += payload
                classes.getOrPut(delivery.seat.index) { mutableListOf() } += delivery.clientClass
            }
        }
    }

    return ClientTranscripts(
        order.sortedBy { it.index }.map { seat ->
            ClientTranscript(
                seat = seat,
                lines = lines[seat.index].orEmpty().toList(),
                classesSeen = classes[seat.index].orEmpty().distinct(),
            )
        }
    )
}
