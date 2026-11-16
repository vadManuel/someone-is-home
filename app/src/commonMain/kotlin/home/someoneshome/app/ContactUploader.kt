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
 * heard (D-212 ruling 3).
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
 * one `RoundBody.RadioFaulted` carrying that cause and nothing else, the same from every phone and
 * the same length whichever the cause. Before arming and after the round nothing is beaten, so
 * nothing is sent.
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
     * own socket as the one fixed body `RoundBody.RadioFaulted(cause)`. Called on the beat after the
     * radio reports an onset, in a round only, from every phone alike, out or not.
     */
    private val faulted: (RadioCause) -> Unit = {},
    /** Up this phone's own socket. */
    private val send: (RoundBody.Sighted) -> Unit,
) {
    private var seed: ContactSeed? = null
    private var out = false
    private var uploads = 0L
    private var advertising: ContactWindow? = null

    /** Whether this phone is uploading at all: a seed has arrived and the round has not ended. */
    val inRound: Boolean get() = seed != null

    /** Whether the house has put this phone out, as far as this phone has been told. */
    val isOut: Boolean get() = out

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
                radio.scan(true)
            }
            // **Out, by the house's word and by nothing else.** Stop listening; keep advertising
            // (D-008), so a living phone can still find this one and report it.
            is RoundMessage.RevokeLanded, is RoundMessage.RestrainedTakeover -> {
                out = true
                radio.scan(false)
            }
            is RoundMessage.RoundEnded -> {
                seed = null
                advertising = null
                radio.silence()
            }
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
        send(SightingUpload.pack(key, window, uploads++, heard))
        // **Rule 6: silent here, loud at the house** (T2, D-222). The radio reports each fault once,
        // at its onset; nothing on this phone's screen, lamp or motor hears of it.
        radio.faultBegan()?.let(faulted)
    }

    /** A new round is coming: forget this one's seed and stop the radio. */
    fun roundEnded() {
        seed = null
        advertising = null
        radio.silence()
    }
}
