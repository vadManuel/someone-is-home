package home.someoneshome.app

import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import home.someoneshome.platform.secureRandomBytes

/**
 * **This phone's requests in a round, waiting for the next beat's slot** (D-263, D-275, provisional).
 *
 * A request no longer goes up as a frame of its own: the phones that put a frame on the air between
 * beats were the ones that had pressed something only an Insider can press. So a request made in a
 * round is queued here, and every beat's upload carries [slot]: **every request waiting that fits**,
 * oldest first, under one id, or an empty slot under a fresh one. The cost is up to one beat
 * (`SightingUpload.CADENCE_MILLIS`) of wait before the house hears a press.
 *
 * ### As many as fit, a beat (D-275)
 *
 * One request a beat put a burst of Subroutine entries tens of seconds behind the player. So the slot
 * takes the oldest requests waiting, in order, for as long as the next one still fits
 * ([RequestSlot.fits]), and stops at the first that does not: a request is never sent ahead of one
 * made before it, so a `LockVote` never overtakes its `SelectVote` (D-117). The upload is the same
 * length whatever the slot holds, so nothing on the wire says how many went.
 *
 * ### Exactly once, across a lost beat and a lost socket
 *
 * The requests a slot took keep that slot's id, **and stay exactly that list**, for every beat it goes
 * up on, until the house's `RoundBody.UploadHeard` names it ([heard]). A beat lost on the way, a socket
 * that failed under it, or an answer that never came back all end the same way: the next beat sends the
 * same list again under the same id, and the house puts each id to the rules once
 * (`IntentDesk.slotted`). A request made while a list is out waits behind it, for the next list: one
 * added to a list the house may already have heard would be dropped with the resend.
 *
 * ### A live selection replaces one not yet sent
 *
 * A finger moving down the ballot makes a selection per row (D-117). So a `SelectVote` made while the
 * last request waiting is a `SelectVote` that has not gone up yet takes its place: the house hears the
 * row the finger is on now. One that has gone up may have been heard, and is never replaced.
 *
 * ### Rule 6
 *
 * A request too long for a slot on its own ([RequestSlot.fitsAlone]) is said in this phone's log and
 * not queued. None today comes near it.
 *
 * Not thread-safe, for [ContactUploader]'s reason: written and read on the round's own dispatcher.
 */
class SlotRequests(
    /** A fresh id: [RequestSlot.ID_CHARS] lowercase hex characters, at random. */
    private val freshId: () -> String = ::freshRequestId,
    /** This phone's log, and only its log. */
    private val onEvent: (String) -> Unit = {},
) {
    /** One request, its size in a slot's list, and the id of the slot that took it, once one has. */
    private class Waiting(val asked: Asked, val bytes: Int, var slotId: String? = null)

    private val waiting = ArrayDeque<Waiting>()

    /** How many requests the house has not answered. For tests and the log. */
    val count: Int get() = waiting.size

    /** One request, to go up in a coming beat's slot. */
    fun ask(asked: Asked) {
        val bytes = RequestSlot.bytesOf(asked)
        if (!RequestSlot.fitsAlone(bytes)) {
            onEvent("A REQUEST TOO LONG FOR THE SLOT WAS NOT SENT — ${kindOf(asked)}")
            return
        }
        val last = waiting.lastOrNull()
        if (last != null && last.slotId == null && isSelection(last.asked) && isSelection(asked)) waiting.removeLast()
        waiting.addLast(Waiting(asked, bytes))
    }

    /**
     * **This beat's slot**: the list already out, again under its id, if the house has not answered it;
     * otherwise every request waiting that fits, oldest first, under a fresh id; or an empty slot under
     * a fresh id. Always [RequestSlot.CHARS] characters.
     */
    fun slot(): String {
        val head = waiting.firstOrNull() ?: return RequestSlot.empty(freshId())
        val id = head.slotId ?: freshId().also(::take)
        val taken = waiting.takeWhile { it.slotId == id }.map { it.asked }
        return RequestSlot.of(id, taken) ?: RequestSlot.empty(freshId())
    }

    /** Give [id] the oldest requests waiting, for as long as the next one still fits beside them. */
    private fun take(id: String) {
        var bytes = 0
        var members = 0
        for (next in waiting) {
            if (!RequestSlot.fits(bytes + next.bytes, members + 1)) break
            bytes += next.bytes
            members++
            next.slotId = id
        }
    }

    /** **The house has the slot named [id]** (`RoundBody.UploadHeard`): every request under it is done. */
    fun heard(id: String) {
        while (waiting.firstOrNull()?.slotId == id) waiting.removeFirst()
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
