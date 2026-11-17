package home.someoneshome.app

import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import home.someoneshome.platform.secureRandomBytes

/**
 * **This phone's requests in a round, waiting for the next beat's slot** (D-263, provisional).
 *
 * A request no longer goes up as a frame of its own: the phones that put a frame on the air between
 * beats were the ones that had pressed something only an Insider can press. So a request made in a
 * round is queued here, and every beat's upload carries [slot]: the oldest request the house has not
 * answered, under its own id, or an empty slot under a fresh one. The cost is up to one beat
 * (`SightingUpload.CADENCE_MILLIS`) of wait before the house hears a press.
 *
 * ### Exactly once, across a lost beat and a lost socket
 *
 * A request keeps the id it was given here for every beat it goes up on, and stays at the head until
 * the house's `RoundBody.UploadHeard` names it ([heard]). A beat lost on the way, a socket that failed
 * under it, or an answer that never came back all end the same way: the next beat sends it again
 * under the same id, and the house puts each id to the rules once (`IntentDesk.slotted`). Requests go
 * strictly in the order they were made, one a beat, so a `LockVote` never overtakes its `SelectVote`
 * (D-117).
 *
 * ### A live selection replaces one not yet sent
 *
 * A finger moving down the ballot makes a selection per row (D-117), and one a beat would trail it by
 * seconds. So a `SelectVote` made while the last request waiting is a `SelectVote` that has not gone
 * up yet takes its place: the house hears the row the finger is on now, as soon as it can. One that
 * has gone up may have been heard, and is never replaced.
 *
 * ### Rule 6
 *
 * A request too long for the slot ([RequestSlot.PAYLOAD_BYTES]) is said in this phone's log and not
 * queued. None today comes near it.
 *
 * Not thread-safe, for [ContactUploader]'s reason: written and read on the round's own dispatcher.
 */
class SlotRequests(
    /** A fresh id: [RequestSlot.ID_CHARS] lowercase hex characters, at random. */
    private val freshId: () -> String = ::freshRequestId,
    /** This phone's log, and only its log. */
    private val onEvent: (String) -> Unit = {},
) {
    private class Waiting(val id: String, val asked: Asked, var sent: Boolean = false)

    private val waiting = ArrayDeque<Waiting>()

    /** How many requests the house has not answered. For tests and the log. */
    val count: Int get() = waiting.size

    /** One request, to go up in a coming beat's slot. */
    fun ask(asked: Asked) {
        val id = freshId()
        if (RequestSlot.of(id, asked) == null) {
            onEvent("A REQUEST TOO LONG FOR THE SLOT WAS NOT SENT — ${kindOf(asked)}")
            return
        }
        val last = waiting.lastOrNull()
        if (last != null && !last.sent && isSelection(last.asked) && isSelection(asked)) waiting.removeLast()
        waiting.addLast(Waiting(id, asked))
    }

    /**
     * **This beat's slot**: the oldest request not yet answered, under its id, or an empty slot under
     * a fresh id. Always [RequestSlot.CHARS] characters.
     */
    fun slot(): String {
        val head = waiting.firstOrNull() ?: return RequestSlot.empty(freshId())
        head.sent = true
        return RequestSlot.of(head.id, head.asked) ?: RequestSlot.empty(freshId())
    }

    /** **The house has the slot named [id]** (`RoundBody.UploadHeard`): the request under it is done. */
    fun heard(id: String) {
        if (waiting.firstOrNull()?.id == id) waiting.removeFirst()
    }

    /** The round is over for this phone: nothing waiting is asked of the next one. */
    fun clear() {
        waiting.clear()
    }

    private fun isSelection(asked: Asked) = asked is Asked.Act && asked.intent is IntentMessage.SelectVote

    private fun kindOf(asked: Asked): String = when (asked) {
        is Asked.Act -> asked.intent::class.simpleName ?: "?"
        is Asked.Fire -> "THE EGRESS TILE"
    }
}

/** A fresh request id: secure random bytes, as lowercase hex (D-263). Never a counter or a time. */
fun freshRequestId(): String = buildString(RequestSlot.ID_CHARS) {
    for (b in secureRandomBytes(RequestSlot.ID_CHARS / 2)) {
        val v = b.toInt() and 0xff
        append(ID_HEX[v shr 4])
        append(ID_HEX[v and 0x0f])
    }
}

private const val ID_HEX = "0123456789abcdef"
