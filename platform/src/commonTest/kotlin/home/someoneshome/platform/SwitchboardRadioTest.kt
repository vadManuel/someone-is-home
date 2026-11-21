package home.someoneshome.platform

import home.someoneshome.model.ContactToken
import home.someoneshome.model.RadioCause
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

        /**
         * A start asked for and not yet answered. CoreBluetooth reports itself not advertising in
         * that gap, and a second start then fails with "Advertising has already started".
         */
        var pending = false

        /** Starts asked for while another was still unanswered: each is that failure on a phone. */
        var startedTwice = 0

        override fun wake() { wakes++; calls += "wake" }
        override fun advertise(token: ByteArray) {
            calls += "advertise"
            if (pending) startedTwice++
            pending = true
            advertising = token.copyOf()
            advertisedHex += token.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        override fun stopAdvertising() { calls += "stopAdvertising"; advertising = null }
        override fun startScan() { calls += "startScan"; scanning = true; scanStarts++ }
        override fun stopScan() { calls += "stopScan"; scanning = false }

        /** What a power change does to the real thing: the OS stops both when the radio goes down. */
        fun lost() { advertising = null; scanning = false; pending = false }

        /** CoreBluetooth answers the start it was asked for: `peripheralManagerDidStartAdvertising`. */
        fun answer(radio: SwitchboardRadio, ok: Boolean = true) {
            pending = false
            radio.advertisingStarted(ok)
        }
    }

    private val t1 = ContactToken("00112233445566778899aabbccddeeff")
    private val t2 = ContactToken("ffeeddccbbaa99887766554433221100")
    private val stranger = ByteArray(16) { (0x40 + it).toByte() }

    private fun on(radio: SwitchboardRadio) {
        radio.advertiserPower(RadioPower.On)
        radio.scannerPower(RadioPower.On)
    }

    /** The radio back up for the whole settle, so the next outage is a new onset. */
    private fun settle(radio: SwitchboardRadio) {
        repeat(ContactAdvertisement.FAULT_SETTLE_CYCLES) { radio.cycle() }
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
        hw.answer(radio)
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
     * **D-233: the owner's ruling, the scan turned over every 100 ms, ten times a second** (it was
     * 250 ms, D-218). Literal numbers, not [ContactAdvertisement.SCAN_CYCLE_MILLIS], so moving it
     * either way fails here. The counts of cycles are derived from it, and the durations they stand
     * for must not move with it: the tally every 10 s, the settle and the patience 2 s each.
     */
    @Test
    fun theScanTurnsOverTenTimesASecondAndTheCountedDurationsHold() {
        assertEquals(100L, ContactAdvertisement.SCAN_CYCLE_MILLIS, "the scan restart moved without a ruling: the owner set it at 100 ms (D-233)")
        val hw = RecordingHardware()
        val lines = mutableListOf<String>()
        val radio = SwitchboardRadio(hw, log = { lines += it })
        radio.scan(true)
        on(radio)
        val starts = hw.scanStarts
        repeat(10) { radio.cycle() }
        assertEquals(starts + 10, hw.scanStarts, "ten cycles are one second (D-233) and did not restart the scan ten times")

        assertEquals(2_000L, ContactAdvertisement.FAULT_SETTLE_CYCLES * ContactAdvertisement.SCAN_CYCLE_MILLIS, "the settle is no longer two seconds")
        assertEquals(2_000L, ContactAdvertisement.START_PATIENCE_CYCLES * ContactAdvertisement.SCAN_CYCLE_MILLIS, "the patience is no longer two seconds")

        repeat(89) { radio.cycle() }
        assertEquals(emptyList(), lines.filter { it.startsWith("TALLY") }, "the tally came before ten seconds of cycles")
        radio.cycle()
        assertEquals(1, lines.count { it.startsWith("TALLY 10 S") }, "ten seconds of cycles did not tally once: ${lines.filter { it.startsWith("TALLY") }}")
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
        hw.answer(radio)
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

    // ---- T2: one fault onset, however many callbacks (D-222), and why (D-223) -------------------

    /** A Bluetooth switch-off reaches both managers; the house hears of it once, as a person's act. */
    @Test
    fun aSwitchOffIsOneFaultHoweverManyCallbacks() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        assertNull(radio.faultBegan(), "a radio that came up reported a fault")
        radio.advertiserPower(RadioPower.Off)
        assertEquals(RadioCause.Person, radio.faultBegan(), "Bluetooth switched off was not reported as a person's act")
        // Collected between callbacks, as the uploader collects once a beat.
        radio.scannerPower(RadioPower.Off)
        assertNull(radio.faultBegan(), "the scanner's callback for the same switch-off was reported as a second fault")
        radio.scannerPower(RadioPower.Resetting)
        radio.advertiserPower(RadioPower.Unauthorized)
        radio.advertise(t2)
        assertNull(radio.faultBegan(), "callbacks inside one outage were reported as more faults")
    }

    /**
     * **GUARD: the permission withdrawn mid-round is a permission switched off** (D-187, D-161,
     * D-310): named with the owner's warning, and the one cause that counts toward closing the house.
     */
    @Test
    fun aPermissionWithdrawnIsAPermissionSwitchedOff() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.scannerPower(RadioPower.Unauthorized)
        assertEquals(RadioCause.Permission, radio.faultBegan(), "a permission withdrawn mid-round was not a permission switched off")
    }

    /**
     * **GUARD: a reset is the phone's own, for the whole outage.** A reset that passes through Off is
     * still a reset, so the house never names the player (D-187: genuine radio loss earns no notice).
     */
    @Test
    fun aResetIsTheDevicesOwnEvenThroughOff() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.advertiserPower(RadioPower.Resetting)
        assertEquals(RadioCause.Device, radio.faultBegan(), "a reset was reported as a person's act")
        radio.scannerPower(RadioPower.Resetting)
        radio.advertiserPower(RadioPower.Off)
        radio.scannerPower(RadioPower.Off)
        assertNull(radio.faultBegan(), "a reset passing through Off became a second, deliberate, fault")
        on(radio)
        settle(radio)
        radio.advertiserPower(RadioPower.Unsupported)
        assertEquals(RadioCause.Device, radio.faultBegan(), "unsupported hardware was reported as a person's act")
    }

    /**
     * The line itself, every pairing: the phone's own failure wins a disagreement; then a permission
     * withdrawn on either manager is a permission's (D-310); otherwise Bluetooth switched off is a
     * person's.
     */
    @Test
    fun theCauseIsAPersonsOnlyWhenNothingFailed() {
        val down = listOf(RadioPower.Off, RadioPower.Unauthorized, RadioPower.Resetting, RadioPower.Unsupported)
        val failed = setOf(RadioPower.Resetting, RadioPower.Unsupported)
        for (a in RadioPower.entries) for (b in RadioPower.entries) {
            if (a !in down && b !in down) continue
            val expected = when {
                a in failed || b in failed -> RadioCause.Device
                a == RadioPower.Unauthorized || b == RadioPower.Unauthorized -> RadioCause.Permission
                else -> RadioCause.Person
            }
            assertEquals(expected, causeOf(a, b), "advertiser $a, scanner $b")
        }
    }

    /** Back up on both managers, then down again: a second onset, with its own cause. */
    @Test
    fun aRadioThatRecoversAndFailsAgainIsASecondFault() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        radio.advertiserPower(RadioPower.Resetting)
        assertEquals(RadioCause.Device, radio.faultBegan())
        radio.advertiserPower(RadioPower.On)
        assertNull(radio.faultBegan(), "a recovery was reported as a fault")
        settle(radio)
        radio.scannerPower(RadioPower.Unauthorized)
        assertEquals(RadioCause.Permission, radio.faultBegan(), "the second onset was swallowed by the first, or took its cause")
        // One manager back is not the radio back: still down, so no new onset.
        radio.scannerPower(RadioPower.Off)
        assertNull(radio.faultBegan(), "a manager changing state inside an outage was reported as a new fault")
    }

    /**
     * **GUARD: before arming, nothing is reported.** Before the round's first advertise there is
     * nobody to tell; already down at arming, it is told, because the radio is down in the round.
     */
    @Test
    fun aRadioAlreadyDownAtArmingIsOneFaultOfThatRound() {
        val radio = SwitchboardRadio(RecordingHardware())
        radio.scan(true)
        radio.advertiserPower(RadioPower.Off)
        radio.scannerPower(RadioPower.Off)
        assertNull(radio.faultBegan(), "a fault was reported before the round's radio had a token")
        radio.advertise(t1)
        assertEquals(RadioCause.Person, radio.faultBegan(), "a radio switched off at arming was never reported")
        // The round ends with it still down; the next round's first advertise finds it again.
        radio.silence()
        assertNull(radio.faultBegan(), "a fault outlived the round it was reported in")
        radio.advertise(t2)
        assertEquals(RadioCause.Person, radio.faultBegan(), "the next round never heard of a radio still down")
    }

    // ---- The first device session (2026-11-16): one FAULT per switch-off, and one start in flight --

    /**
     * **GUARD: a switch-off seen from Settings is one fault for the whole radio.** On a phone that
     * comes back from Settings, CoreBluetooth hands over each manager's Off and On in turn, so both
     * managers are On for an instant between the scanner's pair and the advertiser's. The first
     * device session logged that as two FAULT lines. It is one onset, logged and reported once; the
     * next is only after both managers have stayed up for the settle and then gone down again.
     */
    @Test
    fun aSwitchOffSeenFromSettingsIsOneFaultForTheWholeRadio() {
        val hw = RecordingHardware()
        val lines = mutableListOf<String>()
        val reported = mutableListOf<Pair<RadioSide, RadioPower>>()
        val radio = SwitchboardRadio(hw, log = { lines += it }) { side, power -> reported += side to power }
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        hw.answer(radio)
        settle(radio)

        // The burst on the way back from Settings, in the order the phone logged it, with a cycle
        // of the scan timer landing inside it as it can.
        hw.lost()
        radio.scannerPower(RadioPower.Off)
        radio.scannerPower(RadioPower.On)
        radio.cycle()
        radio.advertiserPower(RadioPower.Off)
        radio.advertiserPower(RadioPower.On)
        val first = radio.faultBegan()
        val faultLines = lines.count { it.startsWith("FAULT") }
        assertEquals(RadioCause.Person, first, "Bluetooth switched off was not reported as a person's act")
        assertEquals(1, faultLines, "one switch-off logged $faultLines FAULT lines: ${lines.filter { it.startsWith("FAULT") }}")
        assertNull(radio.faultBegan(), "one switch-off was reported to the house twice")
        assertEquals(
            listOf(RadioSide.Scanner to RadioPower.Off, RadioSide.Advertiser to RadioPower.Off),
            reported,
            "each manager's own outage line is still its own",
        )

        // Inside the settle, a manager going down again is still the same switch-off.
        repeat(ContactAdvertisement.FAULT_SETTLE_CYCLES - 1) { radio.cycle() }
        radio.scannerPower(RadioPower.Off)
        radio.scannerPower(RadioPower.On)
        assertNull(radio.faultBegan(), "a manager going down inside the settle was reported as a new fault")
        assertEquals(1, lines.count { it.startsWith("FAULT") }, "a manager going down inside the settle logged a new FAULT")

        // Back up for the whole settle, then switched off again: a second onset, once.
        settle(radio)
        radio.advertiserPower(RadioPower.Off)
        radio.scannerPower(RadioPower.Off)
        assertEquals(RadioCause.Person, radio.faultBegan(), "a second switch-off after recovery was swallowed")
        assertNull(radio.faultBegan(), "the second switch-off was reported twice")
        assertEquals(2, lines.count { it.startsWith("FAULT") })
    }

    /**
     * **GUARD: a start is never asked twice before it is answered** (the first device session's
     * "Advertising has already started"). Bluetooth comes back and starts the old token; the window
     * turns before CoreBluetooth has answered. The new token waits for the answer, goes up then, and
     * nothing is reported to the house or to the outage line.
     */
    @Test
    fun aStartIsNeverAskedTwiceBeforeItIsAnswered() {
        val hw = RecordingHardware()
        val lines = mutableListOf<String>()
        val reported = mutableListOf<Pair<RadioSide, RadioPower>>()
        val radio = SwitchboardRadio(hw, log = { lines += it }) { side, power -> reported += side to power }
        radio.scan(true)
        radio.advertise(t1)
        on(radio)
        hw.answer(radio)
        settle(radio)
        assertNull(radio.faultBegan())

        // Recovery and the window turning, back to back.
        hw.lost()
        radio.advertiserPower(RadioPower.Off)
        radio.advertiserPower(RadioPower.On)
        radio.advertise(t2)
        radio.advertise(t2)
        assertEquals(0, hw.startedTwice, "a second start was asked while the first was unanswered")
        assertEquals(t1.hex, hw.advertisedHex.last())
        hw.answer(radio)
        assertEquals(t2.hex, hw.advertisedHex.last(), "the token asked for while a start waited never went up")
        hw.answer(radio)
        val starts = hw.calls.count { it == "advertise" }
        radio.advertise(t2)
        assertEquals(starts, hw.calls.count { it == "advertise" }, "the token already on the air was started again")
        assertEquals(0, hw.startedTwice)

        assertEquals(RadioCause.Person, radio.faultBegan(), "the switch-off itself is still one fault")
        assertNull(radio.faultBegan(), "a start was reported as a fault")
        assertEquals(listOf(RadioSide.Advertiser to RadioPower.Off), reported, "a start was reported as an outage")
        assertTrue(lines.none { it.contains("NOT STARTED") }, "a start that began was logged as failed")
    }

    /** A double start with no power change at all: one start reaches the hardware, no failure. */
    @Test
    fun aDoubleStartGivesNoFailure() {
        val hw = RecordingHardware()
        val reported = mutableListOf<Pair<RadioSide, RadioPower>>()
        val radio = SwitchboardRadio(hw) { side, power -> reported += side to power }
        radio.advertise(t1)
        radio.advertiserPower(RadioPower.On)
        radio.advertise(t1)
        radio.advertise(t1)
        assertEquals(1, hw.calls.count { it == "advertise" }, "one token was started more than once")
        assertEquals(0, hw.startedTwice)
        hw.answer(radio)
        assertNull(radio.faultBegan())
        assertEquals(emptyList(), reported)
    }

    /**
     * A start that failed is the device log's alone, not an outage and not a fault (rule 6), and it
     * is asked again after the patience rather than at once.
     */
    @Test
    fun aFailedStartIsAskedAgainAfterThePatienceAndReportedToNobody() {
        val hw = RecordingHardware()
        val lines = mutableListOf<String>()
        val reported = mutableListOf<Pair<RadioSide, RadioPower>>()
        val radio = SwitchboardRadio(hw, log = { lines += it }) { side, power -> reported += side to power }
        radio.advertise(t1)
        radio.advertiserPower(RadioPower.On)
        hw.answer(radio, ok = false)
        assertTrue(lines.any { it.startsWith("ADVERTISING NOT STARTED") })
        repeat(ContactAdvertisement.START_PATIENCE_CYCLES - 1) { radio.cycle() }
        assertEquals(1, hw.calls.count { it == "advertise" }, "a failed start was asked again at once")
        radio.cycle()
        assertEquals(2, hw.calls.count { it == "advertise" }, "a failed start was never asked again")
        assertEquals(0, hw.startedTwice)
        assertNull(radio.faultBegan(), "a failed start was reported as a fault")
        assertEquals(emptyList(), reported, "a failed start was reported as an outage")
    }

    /** The round ends while a start waits; the start begins late, and is taken off the air. */
    @Test
    fun aStartThatBeginsAfterTheRoundEndedIsStopped() {
        val hw = RecordingHardware()
        val radio = SwitchboardRadio(hw)
        radio.advertise(t1)
        radio.advertiserPower(RadioPower.On)
        radio.silence()
        hw.advertising = ByteArray(16) // CoreBluetooth began the start the stop overtook
        hw.answer(radio)
        assertNull(hw.advertising, "a start that began after the round ended stayed on the air")
    }
}
