package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Sighting
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.secureRandomBytes

/**
 * **The phone's half of the contact handshake: advertise this window's token, and say what was
 * heard, once a beat, at one size** (D-212, D-217).
 *
 * It holds three facts and every one of them came down this phone's own socket: the seed the house
 * issued it at arming (`ContactSeedIssued`), whether the house has put it out (`RevokeLanded`,
 * `RestrainedTakeover`), and whether the round is over (`RoundEnded`). **It never holds a role or an
 * armed state, and it could not**: nothing it is told carries either, and nothing it sends depends
 * on anything but those three facts and what the radio heard (D-212 ruling 5).
 *
 * ### One upload per beat, from every phone in a round, at one size
 *
 * [beat] is called on `SightingUpload.CADENCE_MILLIS` by whoever owns the clock. Every call in a
 * round sends exactly one `RoundBody.Sighted`, packed to `SightingUpload.BODY_CHARS` whatever was
 * heard (D-212 ruling 3). Each upload also carries this beat's request slot ([ask], D-263): every
 * request the player makes in a round rides here rather than as a frame of its own.
 *
 * ### R8: a phone that is out keeps uploading, and uploads nothing heard
 *
 * The owner's 2026-11-15 ruling 8. A Revoked or Restrained phone stops scanning and keeps
 * advertising (D-008), and **keeps uploading on the same cadence**, with nothing in it but filler. A
 * phone that fell silent when it went out would tell the Wi-Fi who is out, and a Revoke is the one
 * fact the house keeps until somebody finds the body of it. What the radio buffered is drained and
 * dropped every beat, so a reading taken a moment before the house's push cannot ride out later.
 *
 * ### A radio fault goes up on the beat after it begins (T2, D-222; D-223)
 *
 * The radio reports an onset once, with its cause ([ContactRadio.faultBegan]); the next beat sends
 * one `RoundBody.RadioFaulted` carrying that cause and the onset's name, the same from every phone
 * and the same length whichever the cause. Before arming and after the round nothing is beaten, so
 * nothing is sent.
 *
 * ### A phone that leaves the foreground mid-round says so once, when it is back (D-187, D-229)
 *
 * Locking the phone or leaving the app is D-187's evasion: with no background modes (D-221) the app
 * is suspended, so its radio falls silent and its socket with it. [leftTheForeground] marks the
 * onset, in a round only; [backInTheForeground] makes the report due; the next beat sends it as the
 * same `RoundBody.RadioFaulted` with [RadioCause.Person] that a Bluetooth switch-off sends (D-223),
 * because that is what it is: this phone's radio down by its owner's hand. One body, so one length,
 * from every phone whatever it is, the host's own seat included. However many times the phone leaves
 * and returns between two beats, it is one report; and the radio's own onset on that beat is folded
 * into it, so one beat never sends two. Before arming, after the round, and on a phone that relaunched
 * into its seat after a crash in the foreground (a crash is not a person's act, D-036), nothing is
 * owed.
 *
 * ### Every report is kept until the house says it has it (D-245)
 *
 * A report goes up the socket this phone has, and that socket may be defunct without the phone knowing
 * yet: a guest's connection that failed while it was suspended takes the frame with it
 * (`TransportClient` is at-most-once). And the process may end under it: iOS ends an app whose
 * Camera or Motion grant is withdrawn in Settings, which is a person's leave. So each onset is a
 * report with its own random name (`RoundBody.RadioFaulted.onset`), **kept** from the moment it is
 * owed until the house's `RoundBody.FaultHeard` names it, and **sent again on every seating** until
 * then, one body a beat. The house hears each onset once however often it arrives. A report owed at
 * a leave is written to [kept] (beside the seat token) at the leave itself, before the phone is
 * suspended, so a relaunched process reads it back when the round's seed arrives on its resume and
 * owes it still. A crash in the foreground writes nothing, so D-036 holds.
 *
 * ### Why the one early exit here is not rule 1's
 *
 * [beat] sends nothing before a seed has arrived, which is before arming and after the round has
 * ended: every phone is in that state at the same moments and for the same reason, so its absence
 * tells nobody anything about anybody. Inside a round it never declines.
 */
class ContactUploader(
    private val radio: ContactRadio,
    /** The window this phone is in now. The wall clock in production (`wallClockMillis`). */
    private val windowNow: () -> ContactWindow,
    /**
     * **This phone's radio went down: say so, once, and why** (T2, D-222; D-223). Up this phone's
     * own socket as the one fixed body `RoundBody.RadioFaulted(cause, onset)`. Called on the beat
     * after the radio reports an onset, in a round only, from every phone alike, out or not; and
     * again on each new connection until the house answers (D-245).
     */
    private val faulted: (RoundBody.RadioFaulted) -> Unit = {},
    /**
     * **Where the reports this phone owes are kept across the process** (D-245): beside the seat
     * token. [DeviceSeat] on a phone; an object of its own, which fails with this one, by default.
     */
    private val kept: SeatStore = MemorySeat(),
    /** A fresh onset's random name, `RoundBody.RadioFaulted.ONSET_CHARS` hex characters. */
    private val onsetName: () -> String = ::freshOnset,
    /** This phone's log, and only its log: a report read back, or a store that would not write. */
    private val onEvent: (String) -> Unit = {},
    /**
     * **This beat's request slot** (D-263): `SlotRequests.slot` on a phone, read once per upload, so
     * every request this phone makes in a round rides inside the upload it was going to send anyway.
     * An empty slot by default, for a construction with nothing to ask.
     */
    private val ask: () -> String = { SightingUpload.EMPTY_ASK },
    /** Up this phone's own socket. */
    private val send: (RoundBody.Sighted) -> Unit,
) {
    private var seed: ContactSeed? = null
    private var out = false
    private var uploads = 0L
    private var advertising: ContactWindow? = null

    /** This phone left the foreground in this round and has not come back yet (D-229). */
    private var away = false

    /**
     * **One onset the house has not yet said it has** (D-245). [due] is false while this phone is
     * still away from the leave it reports; [sentOn] is the connection it last went up on, and it is
     * sent again on each new one until the house answers.
     */
    private class Owed(val onset: String, var cause: RadioCause, var due: Boolean, var sentOn: Int = NEVER_SENT)

    /** Every report owed, oldest first. Written to [kept] whenever it changes. */
    private val owed = ArrayList<Owed>()

    /** Which connection this phone is on: one more on every seating. */
    private var connection = 0

    /** Whether this phone is uploading at all: a seed has arrived and the round has not ended. */
    val inRound: Boolean get() = seed != null

    /** Whether the house has put this phone out, as far as this phone has been told. */
    val isOut: Boolean get() = out

    /** How many reports this phone owes the house and has not had answered. For tests and the log. */
    val owing: Int get() = owed.size

    /**
     * **A push, read for the three facts this holds.** Called with every message the inbox applies,
     * after the inbox has decided it is new, so a re-assertion of an old seed changes nothing.
     */
    fun heard(message: RoundMessage) {
        when (message) {
            is RoundMessage.ContactSeedIssued -> {
                seed = message.seed
                out = false
                uploads = 0L
                advertising = null
                away = false
                // A relaunched process hears its round's seed again on its resume, and reads back
                // what it owed in that round. Any other seed is a new round, and nothing is owed.
                owed.clear()
                owed += restore(message.seed)
                if (owed.isNotEmpty()) onEvent("${owed.size} REPORT(S) OWED FROM BEFORE THIS PROCESS — THE NEXT BEATS TELL THE HOUSE (D-245)")
                radio.scan(true)
            }
            // **Out, by the house's word and by nothing else.** Stop listening; keep advertising
            // (D-008), so a living phone can still find this one and report it.
            is RoundMessage.RevokeLanded, is RoundMessage.RestrainedTakeover -> {
                out = true
                radio.scan(false)
            }
            is RoundMessage.RoundEnded -> roundEnded()
            else -> Unit
        }
    }

    /** **One beat of the fixed cadence**: rotate the token if the window turned, then upload once. */
    fun beat() {
        val key = seed ?: return
        val window = windowNow()
        if (advertising != window) {
            radio.advertise(ContactTokens.of(key, window))
            advertising = window
        }
        val readings = radio.drain()
        val heard = if (out) emptyList() else readings.map { Sighting(it.token, it.rssi, window) }
        send(SightingUpload.pack(key, window, uploads++, heard, ask()))
        // **Rule 6: silent here, loud at the house** (T2, D-222). The radio reports each fault once,
        // at its onset; nothing on this phone's screen, lamp or motor hears of it. A return from
        // leaving the foreground is a person's act and wins the beat (D-229): the radio's onset is
        // folded into a report not yet sent, and one beat never sends two bodies.
        radio.faultBegan()?.let { cause ->
            val unsent = owed.lastOrNull { it.due && it.sentOn == NEVER_SENT }
            if (unsent != null) {
                if (cause == RadioCause.Person) unsent.cause = RadioCause.Person
            } else {
                owe(Owed(onsetName(), cause, due = !away))
            }
            keep()
        }
        // **The oldest report this connection has not carried, and only one** (D-245): a new onset,
        // or one that went up a connection since replaced and was never answered.
        val next = owed.firstOrNull { it.due && it.sentOn != connection } ?: return
        next.sentOn = connection
        faulted(RoundBody.RadioFaulted(next.cause, next.onset))
    }

    /**
     * **The house has the report of [onset]** (`RoundBody.FaultHeard`, D-245): it is no longer kept,
     * here or beside the token. An answer for an onset this phone does not hold (answered twice, or
     * from before a new round) is nothing.
     */
    fun acknowledged(onset: String) {
        if (owed.removeAll { it.onset == onset }) keep()
    }

    /**
     * **This phone is seated on a new connection** (D-245). Whatever it owes and the house has not
     * answered may have failed with the last one, so each goes up again, one a beat, and the house
     * hears each onset once.
     */
    fun reseated() {
        connection++
    }

    /**
     * **This phone left the foreground: the onset of D-187's evasion, if it is in a round** (D-229).
     * True when it counted. Before arming and after the round there is no round to evade, and
     * nothing is marked.
     *
     * **A phone that is out marks it too, and that is rule 1** (R7, D-242). Whether a seat that is
     * out is named is the house's question, and the house answers no (`onTheNotice`). A phone that
     * stayed quiet on its return because it was out would put one frame fewer on the Wi-Fi than a
     * living phone that did the same thing, and the missing frame would say who is out.
     */
    fun leftTheForeground(): Boolean {
        if (seed == null) return false
        away = true
        // Flicks between two beats are one onset: a report not yet sent takes this leave too.
        val unsent = owed.lastOrNull { it.sentOn == NEVER_SENT }
        if (unsent != null) {
            unsent.cause = RadioCause.Person
            unsent.due = false
        } else {
            owe(Owed(onsetName(), RadioCause.Person, due = false))
        }
        // **Kept now, before the phone is suspended** (D-245): iOS may end this process before it
        // ever comes back, and the relaunch reads this back.
        keep()
        return true
    }

    /**
     * **This phone is back: the next beat tells the house, once** (D-229). True when a report is
     * now owed. A return with no onset in this round owes nothing.
     */
    fun backInTheForeground(): Boolean {
        if (!away) return false
        away = false
        owed.forEach { it.due = true }
        return true
    }

    /**
     * A new round is coming: forget this one's seed, whether it put this phone out, and stop the
     * radio. **Out goes with the round** (D-259): it was cleared only by the next round's seed, so a
     * phone Revoked in one round said it was out of the round all through the lobby before the next.
     */
    fun roundEnded() {
        seed = null
        advertising = null
        out = false
        uploads = 0L
        forgetTheRound()
        radio.silence()
    }

    /** This round is over for this phone: nothing is owed in it, here or in [kept]. */
    private fun forgetTheRound() {
        away = false
        owed.clear()
        try {
            kept.forgetOwedReports()
        } catch (failed: IllegalStateException) {
            onEvent("THE OWED REPORTS COULD NOT BE FORGOTTEN — ${failed.message}")
        }
    }

    /** One more report owed. Bounded: past [OWED_MOST] the oldest is let go, and the log says so. */
    private fun owe(report: Owed) {
        if (owed.size >= OWED_MOST) {
            owed.removeAt(0)
            onEvent("MORE THAN $OWED_MOST REPORTS OWED — THE OLDEST IS LET GO")
        }
        owed += report
    }

    /**
     * **[owed], written to [kept]**: the round's fingerprint, then one line per report. A store that
     * would not write is said in this phone's log (rule 6); the report is still owed in memory.
     */
    private fun keep() {
        val key = seed ?: return
        try {
            if (owed.isEmpty()) {
                kept.forgetOwedReports()
            } else {
                val lines = listOf(fingerprintOf(key)) + owed.map { "${it.onset} ${it.cause.name.lowercase()}" }
                kept.keepOwedReports(lines.joinToString(LINE))
            }
        } catch (failed: IllegalStateException) {
            onEvent("A REPORT OWED COULD NOT BE KEPT — IT FAILS WITH THIS PROCESS (${failed.message})")
        }
    }

    /**
     * **What [kept] holds for the round of [key], or nothing.** Anything kept for another round, or
     * unreadable, is forgotten. Every report read back is due and has gone up no connection of this
     * process.
     */
    private fun restore(key: ContactSeed): List<Owed> {
        val text = try {
            kept.owedReports()
        } catch (_: IllegalStateException) {
            null
        }
        val lines = text?.split(LINE).orEmpty()
        val reports = if (lines.firstOrNull() == fingerprintOf(key)) {
            lines.drop(1).mapNotNull { line ->
                val parts = line.split(' ')
                val onset = parts.first()
                val cause = RadioCause.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(1) }
                if (cause == null || !isOnset(onset)) null else Owed(onset, cause, due = true)
            }.takeLast(OWED_MOST)
        } else {
            emptyList()
        }
        if (reports.isEmpty() && text != null) {
            try {
                kept.forgetOwedReports()
            } catch (_: IllegalStateException) {
                // Read back as nothing either way; the next keep overwrites it.
            }
        }
        return reports
    }

    private companion object {
        const val NEVER_SENT = -1
        const val LINE = "\n"

        /** Reports one phone may owe at once. A phone that owes this many is not reaching the house. */
        const val OWED_MOST = 8

        /**
         * The window whose token names a round in [kept]: one no phone is ever in, so the name is
         * never on the air. One-way, like every token (D-212): it does not give back the seed.
         */
        val STORE_WINDOW = ContactWindow(-1)

        fun fingerprintOf(key: ContactSeed): String = ContactTokens.of(key, STORE_WINDOW).hex

        fun isOnset(text: String): Boolean =
            text.length == RoundBody.RadioFaulted.ONSET_CHARS && text.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

/** A fresh onset's name: secure random bytes, as lowercase hex (D-245). Never a counter or a time. */
fun freshOnset(): String = buildString(RoundBody.RadioFaulted.ONSET_CHARS) {
    for (b in secureRandomBytes(RoundBody.RadioFaulted.ONSET_CHARS / 2)) {
        val v = b.toInt() and 0xff
        append(HEX[v shr 4])
        append(HEX[v and 0x0f])
    }
}

private const val HEX = "0123456789abcdef"
