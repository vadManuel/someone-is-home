package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Haptic
import home.someoneshome.model.RadioCause
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RadioReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The phone's half of the handshake, alone: one upload a beat at one size, and R8** (D-217).
 *
 * A radio that always hears a stranger at contact range, so that what the uploader does with a
 * reading is visible in every upload: a living phone reports it, an out phone never does.
 */
class ContactUploaderTest {

    private class LoudRadio : ContactRadio {
        val advertised = mutableListOf<ContactToken>()
        var scanning = false
        var silenced = 0
        val stranger = ContactToken("ffeeddccbbaa99887766554433221100")
        override fun advertise(token: ContactToken) { advertised += token }
        override fun scan(on: Boolean) { scanning = on }
        override fun drain(): List<RadioReading> = listOf(RadioReading(stranger, -31))
        override fun silence() { silenced++; scanning = false }
        var faults = 0
        var cause = RadioCause.Person
        override fun faultBegan(): RadioCause? = if (faults > 0) cause.also { faults-- } else null
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")

    private fun rig(window: () -> ContactWindow = { ContactWindow(10) }): Triple<ContactUploader, LoudRadio, MutableList<RoundBody.Sighted>> {
        val radio = LoudRadio()
        val sent = mutableListOf<RoundBody.Sighted>()
        return Triple(ContactUploader(radio, window) { sent += it }, radio, sent)
    }

    private fun heardIn(body: RoundBody.Sighted, token: ContactToken): Boolean =
        SightingUpload.unpack(body)!!.any { it.token == token }

    @Test
    fun nothingGoesUpBeforeTheSeedAndOneUploadAtOneSizeGoesUpEveryBeatAfter() {
        val (up, radio, sent) = rig()
        repeat(3) { up.beat() }
        assertEquals(0, sent.size, "a phone uploaded before it was in a round")
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertTrue(radio.scanning, "a living phone in a round is not scanning")
        repeat(5) { up.beat() }
        assertEquals(5, sent.size)
        assertTrue(sent.all { it.slots.length == SightingUpload.BODY_CHARS })
        assertTrue(sent.all { heardIn(it, radio.stranger) }, "a living phone did not report what it heard")
    }

    /** R8, for both ways out: the cadence and the size hold, scanning stops, nothing heard goes up. */
    @Test
    fun anOutPhoneKeepsUploadingOnTheBeatAtOneSizeAndReportsNothing() {
        for (out in listOf<RoundMessage>(RoundMessage.RevokeLanded.Padded, RoundMessage.RestrainedTakeover(Haptic.Long))) {
            val (up, radio, sent) = rig()
            up.heard(RoundMessage.ContactSeedIssued(seed))
            up.beat()
            up.heard(out)
            assertFalse(radio.scanning, "$out: an out phone kept scanning (D-008)")
            repeat(4) { up.beat() }
            assertEquals(5, sent.size, "$out: an out phone stopped uploading on the beat (R8)")
            assertTrue(sent.all { it.slots.length == SightingUpload.BODY_CHARS }, "$out: the size moved")
            assertTrue(heardIn(sent.first(), radio.stranger), "the living upload did not carry the reading")
            assertTrue(sent.drop(1).none { heardIn(it, radio.stranger) }, "$out: an out phone reported what its radio held")
            assertTrue(up.isOut)
        }
    }

    @Test
    fun theAdvertisedTokenRotatesWithTheWindowAndOutPhonesKeepAdvertising() {
        var w = 10L
        val (up, radio, _) = rig(window = { ContactWindow(w) })
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat(); up.beat()
        up.heard(RoundMessage.RevokeLanded.Padded)
        w = 11L
        up.beat()
        assertEquals(
            listOf(ContactTokens.of(seed, ContactWindow(10)), ContactTokens.of(seed, ContactWindow(11))),
            radio.advertised,
            "the token did not rotate once per window, or an out phone stopped advertising",
        )
    }

    @Test
    fun theRoundEndingStopsTheUploadsAndSilencesTheRadio() {
        val (up, radio, sent) = rig()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat()
        up.heard(RoundMessage.RoundEnded(Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short))
        repeat(3) { up.beat() }
        assertEquals(1, sent.size, "a phone kept uploading after the round ended")
        assertEquals(1, radio.silenced)
    }

    /**
     * **T2: a fault goes up once, on the beat after its onset, with its cause, from a living phone
     * and an out one alike, and never outside a round** (D-222; D-223). **GUARD: before arming,
     * nothing goes up**, so nothing can be announced.
     */
    @Test
    fun aRadioFaultGoesUpOnceOnTheBeatAfterItBeganInARoundOnly() {
        for (out in listOf(false, true)) for (cause in RadioCause.entries) {
            val radio = LoudRadio()
            radio.cause = cause
            val faults = mutableListOf<RadioCause>()
            val up = ContactUploader(radio, { ContactWindow(10) }, send = { }, faulted = { faults += it })
            radio.faults = 1
            up.beat()
            assertEquals(emptyList(), faults, "a fault went up before the phone was in a round")
            up.heard(RoundMessage.ContactSeedIssued(seed))
            if (out) up.heard(RoundMessage.RevokeLanded.Padded)
            repeat(3) { up.beat() }
            assertEquals(listOf(cause), faults, "out=$out, $cause: one onset did not go up exactly once, as itself")
        }
    }

    /**
     * **GUARD: the upward body is one shape of one length**, the same bytes from every phone whatever
     * it is, and the same length whichever side of D-187's line it reports (D-223). A cause whose
     * serial name was longer would let a reader of the Wi-Fi tell a reset from a switch-off by size.
     */
    @Test
    fun theFaultBodyIsOneFixedLength() {
        assertEquals("{\"type\":\"radioFaulted\",\"cause\":\"person\"}", RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Person)))
        assertEquals("{\"type\":\"radioFaulted\",\"cause\":\"device\"}", RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Device)))
        assertEquals(
            1, RadioCause.entries.map { RoundWire.encode(RoundBody.RadioFaulted(it)).length }.toSet().size,
            "the fault body's length depends on its cause",
        )
    }
}
