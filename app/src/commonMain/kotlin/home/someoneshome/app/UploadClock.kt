package home.someoneshome.app

import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.UploadProbe
import home.someoneshome.platform.secureRandomBytes

/**
 * **This phone's half of the clock that rides the uploads** (D-295; the owner's ruling of
 * 2026-11-18: containment's offset is refreshed every second on the uploads every phone already
 * sends, not measured once at join).
 *
 * [stamp] draws each upload's probe and remembers when it was drawn, on this phone's clock;
 * [answered] finds the probe an answer echoes and hands the round trip on as one sample: sent here,
 * read and answered by the house on its clock, received here. The estimate itself is
 * `ClockSync`'s, below the app: the minimum round trip of the last sixteen samples, slewed.
 *
 * ### The same for every phone
 *
 * Nothing here reads a seat, a role or whether the phone is out. Every phone in a round stamps one
 * probe a beat, because every phone in a round uploads one a beat (D-212, D-217), and every answer
 * the house sends comes back to the phone that asked. The probe is random, never a counter or a
 * time (D-245's reason): this phone's own clock does not leave it.
 *
 * Single-threaded: the beat and the answers are both read on the round half's own scope.
 */
class UploadClock(
    /** This phone's monotonic milliseconds: the clock its socket's estimator is on. */
    private val nowMillis: () -> Long,
    /** A fresh probe. Secure random bytes in production; a test may name its own. */
    private val draw: () -> String = ::freshProbe,
) {

    /** The probes still waiting for an answer, newest last, and when each was drawn. A ring. */
    private val probes = arrayOfNulls<String>(KEPT)
    private val sentAt = LongArray(KEPT)
    private var next = 0

    /** **This upload's probe**, remembered with the moment it was drawn. */
    fun stamp(): String {
        val probe = draw()
        probes[next] = probe
        sentAt[next] = nowMillis()
        next = (next + 1) % KEPT
        return probe
    }

    /**
     * **The house's answer to one upload, received at [receivedAtMillis]**, and [sample] told its
     * round trip; or nothing, for an answer to a probe this phone no longer holds (answered twice,
     * older than [KEPT] beats, or one from before [forget]). Each probe is used once.
     */
    inline fun answered(
        answer: RoundBody.UploadHeard,
        receivedAtMillis: Long,
        sample: (sentAtMillis: Long, heardAtMillis: Long, answeredAtMillis: Long, receivedAtMillis: Long) -> Unit,
    ) {
        val sent = take(answer.probe) ?: return
        sample(sent, answer.heardAtMillis, answer.answeredAtMillis, receivedAtMillis)
    }

    /** When [probe] was drawn, once, or null when it is not one of the last [KEPT]. */
    fun take(probe: String): Long? {
        if (probe == UploadProbe.NONE) return null
        for (i in 0 until KEPT) {
            if (probes[i] == probe) {
                probes[i] = null
                return sentAt[i]
            }
        }
        return null
    }

    /** A round ended, or this phone left: nothing waiting is matched again. */
    fun forget() {
        probes.fill(null)
    }

    companion object {
        /**
         * Probes kept waiting. Eight beats: an answer later than that is a round trip of seconds,
         * which the estimate's minimum would never choose, so dropping it loses nothing.
         */
        const val KEPT: Int = 8
    }
}

/** A fresh clock probe: secure random bytes, as lowercase hex (D-295). Never a counter or a time. */
fun freshProbe(): String = buildString(UploadProbe.CHARS) {
    for (b in secureRandomBytes(UploadProbe.CHARS / 2)) {
        val v = b.toInt() and 0xff
        append(PROBE_HEX[v shr 4])
        append(PROBE_HEX[v and 0x0f])
    }
}

private const val PROBE_HEX = "0123456789abcdef"
