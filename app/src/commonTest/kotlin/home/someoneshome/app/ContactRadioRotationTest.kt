package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.RadioHardware
import home.someoneshome.platform.RadioPower
import home.someoneshome.platform.SwitchboardRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The uploader driving the real radio's switchboard, over a recorder standing in for
 * CoreBluetooth** (D-218): what the air carries, beat by beat, on a clock the test turns.
 *
 * The uploader beats on `SightingUpload.CADENCE_MILLIS` off the wall clock; this turns that clock by
 * hand, so the rotation's timing is a number the test reads rather than a race it hopes to win.
 */
class ContactRadioRotationTest {

    private class Air : RadioHardware {
        var woken = false
        /** Every token put on the air, with the clock reading at the moment it went up. */
        val rotations = mutableListOf<Pair<Long, String>>()
        var scanning = false
        var now = 0L
        lateinit var radio: SwitchboardRadio
        override fun wake() { woken = true }
        override fun advertise(token: ByteArray) {
            rotations += now to token.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            // CoreBluetooth answers every start it accepts; the switchboard asks for no next one
            // until it has. Here the answer comes at once.
            radio.advertisingStarted(true)
        }
        override fun stopAdvertising() = Unit
        override fun startScan() { scanning = true }
        override fun stopScan() { scanning = false }
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")

    private fun rig(start: Long): Triple<ContactUploader, Air, MutableList<RoundBody.Sighted>> {
        val air = Air().also { it.now = start }
        val radio = SwitchboardRadio(air).also { air.radio = it }
        radio.advertiserPower(RadioPower.On)
        radio.scannerPower(RadioPower.On)
        val sent = mutableListOf<RoundBody.Sighted>()
        val up = ContactUploader(radio, { ContactWindow.containing(air.now) }) { sent += it }
        return Triple(up, air, sent)
    }

    /**
     * **The advertisement turns at every window boundary, within one beat of it, to that window's
     * token, and only then.** A minute of beats starting mid-window: five windows, five tokens, each
     * up no later than one cadence after its window began.
     */
    @Test
    fun theTokenRotatesAtEveryWindowBoundaryWithinOneBeat() {
        val start = 7 * ContactWindow.MILLIS + 9_300L
        val (up, air, sent) = rig(start)
        up.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(60) {
            up.beat()
            air.now += SightingUpload.CADENCE_MILLIS
        }
        assertEquals(60, sent.size)
        val windows = (7L..11L).map { ContactWindow(it) }
        assertEquals(
            windows.map { ContactTokens.of(seed, it).hex },
            air.rotations.map { it.second },
            "the air did not carry exactly one token per window, in order",
        )
        for ((at, _) in air.rotations.drop(1)) {
            val lag = at - ContactWindow.containing(at).index * ContactWindow.MILLIS
            assertTrue(lag in 0 until SightingUpload.CADENCE_MILLIS, "the token turned $lag ms after its window began")
        }
    }

    /** D-067: until the house issues the seed, the radio is never woken and nothing goes up. */
    @Test
    fun beforeTheSeedTheRadioIsNeverBuilt() {
        val (up, air, sent) = rig(0L)
        repeat(5) { up.beat() }
        assertFalse(air.woken, "the radio was built before arming")
        assertEquals(emptyList(), air.rotations)
        assertEquals(0, sent.size)
    }

    /**
     * **Story 9.4 end to end: Revoked, the phone keeps advertising and rotating, never scans, and
     * keeps uploading at one size (R8).**
     */
    @Test
    fun aRevokedPhoneKeepsRotatingAndNeverScans() {
        val (up, air, sent) = rig(0L)
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertTrue(air.scanning, "a living phone in a round is not scanning")
        up.beat()
        up.heard(RoundMessage.RevokeLanded.Buzzing)
        assertFalse(air.scanning, "a Revoked phone kept scanning")
        repeat(30) {
            air.now += SightingUpload.CADENCE_MILLIS
            up.beat()
        }
        assertFalse(air.scanning)
        assertEquals(3, air.rotations.size, "a Revoked phone stopped turning its token")
        assertEquals(31, sent.size)
        assertTrue(sent.all { it.slots.length == SightingUpload.BODY_CHARS })
    }
}
