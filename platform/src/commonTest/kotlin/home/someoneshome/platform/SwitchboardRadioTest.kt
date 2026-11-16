package home.someoneshome.platform

import home.someoneshome.model.ContactToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The radio's decisions, over a recorder standing in for CoreBluetooth** (D-218).
 *
 * The Simulator has no Bluetooth, so this is where the radio is tested before a phone: off before
 * arming (D-067), one advertisement at a time and only the token, a Revoked phone that advertises
 * and never scans (D-008, story 9.4) through every reset, and an outage that reaches the log and
 * nothing else (rule 6).
 */
class SwitchboardRadioTest {

    /** What CoreBluetooth was asked to do, and what it is doing now. */
    class RecordingHardware : RadioHardware {
        var wakes = 0
        var advertising: ByteArray? = null
        val advertisedHex = mutableListOf<String>()
        var scanning = false
        var scanStarts = 0
        val calls = mutableListOf<String>()

        override fun wake() { wakes++; calls += "wake" }
        override fun advertise(token: ByteArray) {
            calls += "advertise"
            advertising = token.copyOf()
            advertisedHex += token.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        override fun stopAdvertising() { calls += "stopAdvertising"; advertising = null }
        override fun startScan() { calls += "startScan"; scanning = true; scanStarts++ }
        override fun stopScan() { calls += "stopScan"; scanning = false }

        /** What a power change does to the real thing: the OS stops both when the radio goes down. */
        fun lost() { advertising = null; scanning = false }
    }

    private val t1 = ContactToken("00112233445566778899aabbccddeeff")
    private val t2 = ContactToken("ffeeddccbbaa99887766554433221100")
    private val stranger = ByteArray(16) { (0x40 + it).toByte() }

    private fun on(radio: SwitchboardRadio) {
        radio.advertiserPower(RadioPower.On)
        radio.scannerPower(RadioPower.On)
    }

    /** D-067: built and left alone, or told to stop before anything began, nothing is built. */
    @Test
    fun beforeArmingNothingIsBuiltAndNothingIsOnTheAir() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.scan(false)
        radio.silence()
        repeat(10) { radio.cycle() }
        assertEquals(emptyList(), radio.drain())
        assertEquals(0, hw.wakes, "the managers were built before arming")
        assertFalse(radio.woken)
        assertEquals(emptyList(), hw.calls, "the radio did something before arming")
    }

    @Test
    fun theFirstAdvertiseOrScanBuildsTheManagersOnce() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.scan(true)
        radio.advertise(t1)
        radio.advertise(t2)
        assertEquals(1, hw.wakes)
    }

    /** The token goes on the air only once the advertiser is on, byte for byte, and alone. */
    @Test
    fun theTokenIsAdvertisedOnceTheAdvertiserIsOnAndRotationReplacesIt() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.advertise(t1)
        assertNull(hw.advertising, "advertised before the advertiser was on")
        radio.advertiserPower(RadioPower.On)
        assertEquals(t1.hex, hw.advertisedHex.single())
        radio.advertise(t2)
        assertEquals(listOf(t1.hex, t2.hex), hw.advertisedHex, "the window turned and the token did not")
        assertEquals(t2.hex, hw.advertising!!.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') })
    }

    @Test
    fun aLivingPhoneScansContinuouslyAndTheCycleTurnsTheScanOver() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.scan(true)
        assertFalse(hw.scanning, "scanned before the scanner was on")
        on(radio)
        assertTrue(hw.scanning)
        repeat(8) { radio.cycle() }
        assertTrue(hw.scanning, "the cycle left the scanner off")
        assertEquals(9, hw.scanStarts, "the cycle did not restart the scan each tick")
        radio.sighted(stranger, -33)
        assertEquals(-33, radio.drain().single().rssi)
    }

    /**
     * **D-008 and story 9.4: told to stop scanning, it keeps advertising and never scans again** —
     * not on the cycle, not after a reset, not after Bluetooth comes back on, and nothing heard in
     * flight reaches an upload. The window still turns the token.
     */
    @Test
    fun aRevokedPhoneAdvertisesAndNeverScansThroughEveryReset() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.sighted(stranger, -40)
        radio.scan(false)
        assertFalse(hw.scanning, "a Revoked phone kept scanning")
        assertEquals(emptyList(), radio.drain(), "what was heard before the push rode out after it")
        radio.sighted(stranger, -31)
        repeat(8) { radio.cycle() }
        hw.lost()
        radio.scannerPower(RadioPower.Resetting)
        radio.advertiserPower(RadioPower.Resetting)
        radio.scannerPower(RadioPower.On)
        radio.advertiserPower(RadioPower.On)
        radio.advertise(t2)
        assertFalse(hw.scanning, "a reset started a Revoked phone's scanner")
        assertEquals(1, hw.scanStarts, "the scanner was started after scan(false)")
        assertEquals(emptyList(), radio.drain(), "a Revoked phone's radio heard something")
        assertEquals(t2.hex, hw.advertisedHex.last(), "a Revoked phone stopped advertising (D-008)")
    }

    /** Rule 6: an outage is reported once, the asked-for state is kept, and coming back restores it. */
    @Test
    fun anOutageIsReportedAndTheRadioComesBackOnItsOwn() {
        val hw = RecordingHardware()
        val reported = mutableListOf<Pair<RadioSide, RadioPower>>()
        val lines = mutableListOf<String>()
        val radio = SwitchboardRadio(hw, log = { lines += it }) { side, power -> reported += side to power }
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        hw.lost()
        radio.scannerPower(RadioPower.Off)
        radio.scannerPower(RadioPower.Off)
        radio.advertiserPower(RadioPower.Off)
        radio.advertiserPower(RadioPower.Unauthorized)
        repeat(4) { radio.cycle() }
        assertFalse(hw.scanning, "the cycle drove a scanner that is off")
        assertEquals(
            listOf(
                RadioSide.Scanner to RadioPower.Off,
                RadioSide.Advertiser to RadioPower.Off,
                RadioSide.Advertiser to RadioPower.Unauthorized,
            ),
            reported,
        )
        assertTrue(lines.any { it.startsWith("OUTAGE") }, "the outage did not reach the device log")
        radio.scannerPower(RadioPower.On)
        radio.advertiserPower(RadioPower.On)
        assertTrue(hw.scanning, "Bluetooth came back and the scan did not")
        assertEquals(t1.hex, hw.advertisedHex.last(), "Bluetooth came back and the token did not")
    }

    /** The round is over: nothing on the air, and power coming back puts nothing back on it. */
    @Test
    fun silenceTakesItOffTheAirForGood() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.sighted(stranger, -40)
        radio.silence()
        assertNull(hw.advertising)
        assertFalse(hw.scanning)
        assertEquals(emptyList(), radio.drain())
        hw.lost()
        radio.scannerPower(RadioPower.Resetting)
        radio.advertiserPower(RadioPower.Resetting)
        radio.scannerPower(RadioPower.On)
        radio.advertiserPower(RadioPower.On)
        repeat(4) { radio.cycle() }
        assertNull(hw.advertising, "a reset put a silenced radio back on the air")
        assertFalse(hw.scanning, "a reset started a silenced radio's scanner")
    }

    // ---- T2: one fault onset, however many callbacks (D-222) -------------------------------------

    /** A Bluetooth switch-off reaches both managers; the house hears of it once. */
    @Test
    fun aSwitchOffIsOneFaultHoweverManyCallbacks() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        assertFalse(radio.faultBegan(), "a radio that came up reported a fault")
        radio.advertiserPower(RadioPower.Off)
        assertTrue(radio.faultBegan(), "the radio went down and the house was never told")
        // Collected between callbacks, as the uploader collects once a beat.
        radio.scannerPower(RadioPower.Off)
        assertFalse(radio.faultBegan(), "the scanner's callback for the same switch-off was reported as a second fault")
        radio.scannerPower(RadioPower.Resetting)
        radio.advertiserPower(RadioPower.Unauthorized)
        radio.advertise(t2)
        assertFalse(radio.faultBegan(), "callbacks inside one outage were reported as more faults")
    }

    /** Back up on both managers, then down again: a second onset. */
    @Test
    fun aRadioThatRecoversAndFailsAgainIsASecondFault() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.advertiserPower(RadioPower.Resetting)
        assertTrue(radio.faultBegan())
        radio.advertiserPower(RadioPower.On)
        assertFalse(radio.faultBegan(), "a recovery was reported as a fault")
        radio.scannerPower(RadioPower.Unauthorized)
        assertTrue(radio.faultBegan(), "the second onset was swallowed by the first")
        // One manager back is not the radio back: still down, so no new onset.
        radio.scannerPower(RadioPower.Off)
        assertFalse(radio.faultBegan(), "a manager changing state inside an outage was reported as a new fault")
    }

    /** Before the round's first advertise there is nobody to tell; already down at arming, it is told. */
    @Test
    fun aRadioAlreadyDownAtArmingIsOneFaultOfThatRound() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertiserPower(RadioPower.Off)
        radio.scannerPower(RadioPower.Off)
        assertFalse(radio.faultBegan(), "a fault was reported before the round's radio had a token")
        radio.advertise(t1)
        assertTrue(radio.faultBegan(), "a radio down at arming was never reported")
        // The round ends with it still down; the next round's first advertise finds it again.
        radio.silence()
        assertFalse(radio.faultBegan(), "a fault outlived the round it was reported in")
        radio.advertise(t2)
        assertTrue(radio.faultBegan(), "the next round never heard of a radio still down")
    }
}
