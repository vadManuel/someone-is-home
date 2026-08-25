package home.someoneshome.app

import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.RoundWire

/**
 * **What came down the socket, on a phone, and whether any of it was somebody's line.**
 *
 * The lobby's central promise is negative — *the house is the only thing that ever sees your line* —
 * and negative promises are the ones a green build says nothing about. [LobbyWire] makes this one
 * structurally: `Standing` has no `String` field to put a line in, so the leak is not a bug that can
 * be introduced by filling one in. That is the real guarantee and this does not replace it.
 *
 * What this adds is the half a Mac cannot see. Two phones on a real network is where a wrong turn in
 * the host's broadcast would actually show — a `Handover` echoed back, a body from a route nobody
 * meant to open — and **an absent leak and an absent connection look identical from here**. So every
 * body arriving at this phone is classified out loud, and a run whose log says nothing was
 * classified is a run that proved nothing rather than a run that passed.
 *
 * ### It prints a verdict and never a body
 *
 * The obvious instrument logs the frame and lets somebody read it. That instrument writes the thing
 * it is guarding onto a Mac the first time it catches anything, which is the leak, arriving through
 * the leak detector. So what goes to the console is the body's **kind** — the three the wire has,
 * or `UNDECODABLE` — and one boolean: whether this phone's own line was inside it. The text never
 * leaves this object.
 *
 * ### It is not a redaction test and cannot become one
 *
 * `LobbyInputTest` and `OneLineNeverPersistedTest` are the tests; the schema allowlist and the
 * differential harness are the instruments for what a client may receive once a round exists. This
 * is a device probe on the one path that has no round behind it yet, and its whole reach is one
 * evening on one phone.
 */
class LineWatch(private val note: (String) -> Unit) {

    /**
     * What this phone handed over, held so an arrival can be compared against it.
     *
     * Held here rather than asked of [LobbyOverTransport], which deliberately keeps no copy of a
     * line on its way out. The cheat already has the text — it was launched with it — so nothing
     * durable and nothing new is being kept.
     */
    private var mine: String = ""

    /** Every kind seen so far, in arrival order, so the summary is a fact rather than a memory. */
    private val kinds = mutableListOf<String>()

    /** How many of them contained this phone's line. The number the whole class is about. */
    private var carriers = 0

    /** Set once, from the switch this process was launched with. */
    fun mine(line: String) {
        mine = line
    }

    /**
     * One body, off the wire.
     *
     * A `Standing` is what should arrive and the count says so; anything else is named the moment it
     * lands, because *one Handover came back* is a sentence that must not be findable only by
     * reading a log carefully afterwards.
     */
    fun saw(body: String) {
        val kind = when (LobbyWire.decodeOrNull(body)) {
            is LobbyBody.Standing -> "STANDING"
            is LobbyBody.Handover -> "HANDOVER"
            is LobbyBody.Naming -> "NAMING"
            // The host saying the evening turned over. It travels downward by design and carries
            // nothing, so it belongs with STANDING among the frames a client should see rather
            // than among the ones worth shouting about -- see the exemption below, where it is
            // named beside it.
            is LobbyBody.RoundEnded -> "ROUND ENDED"
            // The host closing the house. Downward by design and carrying nothing, exactly as ROUND
            // ENDED is — and named separately rather than folded in with it, because the two mean
            // opposite things about whether there is still a house on the other end of this socket.
            // A probe that reported them as one kind could not tell a second round from a party
            // that ended.
            is LobbyBody.HouseClosed -> "HOUSE CLOSED"
            // **A round body is not an undecodable one**, and telling them apart is the difference
            // between a probe and an alarm nobody reads. This class was written when the lobby's
            // socket carried only the lobby; it carries the round now, and on its first five-seat
            // run it shouted twice per push for the length of a round — hundreds of lines, every
            // one of them reporting a frame that is exactly what should have arrived.
            //
            // **The line-carrying check below is deliberately unchanged and still runs on every
            // body**, round bodies included. That is the assertion this class exists for and it is
            // substring-matched against the raw bytes, so it never depended on decoding anything.
            // What is fixed here is only which frames are worth shouting about.
            null -> if (RoundWire.decodeOrNull(body) != null) "ROUND" else "UNDECODABLE"
        }
        kinds += kind
        val carriesMine = mine.isNotBlank() && body.contains(mine)
        if (carriesMine) carriers++
        // A STANDING is the lobby doing its job, a ROUND body is the house doing its job, and a
        // ROUND ENDED is the host handing every phone back to the lobby. Everything else, and
        // anything at all carrying this phone's line, is shouted about.
        if (kind !in QUIET || carriesMine) {
            note("!!! A CLIENT-BOUND FRAME WAS $kind · CARRIED THIS PHONE'S LINE=$carriesMine")
        }
    }

    /**
     * The whole run, in one line: how many arrived, of which kinds, and how many carried a line.
     *
     * **The count of frames is as load-bearing as the count of carriers.** Zero carriers out of zero
     * frames is what a phone that never connected reports, and it is the answer this probe is most
     * likely to give by accident.
     */
    fun verdict(): String =
        "INBOUND — ${kinds.size} FRAME(S) ${kinds.groupingBy { it }.eachCount()} · " +
            "$carriers CARRIED THIS PHONE'S LINE"

    private companion object {
        /**
         * **The four kinds a client is supposed to receive**, named in one place so the alarm's
         * condition reads as a membership test rather than as a chain of inequalities somebody has
         * to add a clause to. Everything outside this set is shouted about on arrival.
         */
        val QUIET: Set<String> = setOf("STANDING", "ROUND", "ROUND ENDED", "HOUSE CLOSED")
    }
}
