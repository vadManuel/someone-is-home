package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.RadioCause
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.NoRanging
import home.someoneshome.platform.RadioPower
import home.someoneshome.platform.RadioReading
import home.someoneshome.platform.RangingRadio
import home.someoneshome.platform.causeOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **One switch-off, one report, whoever heard it** (the owner's 2026-11-21 rulings, D-311; the
 * overnight review's findings 1 and 4).
 *
 * The contact radio, the ranging radio and the grants read can each hear the same switch-off, on
 * different beats and across a relaunch. A second report of one switch-off after the warning would
 * close the house for something the room was already warned about, so the uploader keys every
 * source by its grant and keeps what it has reported beside the owed reports.
 */
class OneReportAGrantTest {

    /** A contact radio that reports one onset when told, with the cause the production line gives it. */
    private class Radio : ContactRadio {
        var pending: RadioCause? = null
        override fun advertise(token: ContactToken) = Unit
        override fun scan(on: Boolean) = Unit
        override fun drain(): List<RadioReading> = emptyList()
        override fun silence() = Unit
        override fun faultBegan(): RadioCause? = pending.also { pending = null }
    }

    /** A ranging radio that refuses once when told: Nearby Interaction not allowed. */
    private class Ranging : RangingRadio by NoRanging {
        var pending: RadioCause? = null
        override fun faultBegan(): RadioCause? = pending.also { pending = null }
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")
    private val bluetooth = 1 shl DevicePermission.Bluetooth.ordinal
    private val nearby = 1 shl DevicePermission.NearbyDevices.ordinal

    private fun uploader(
        radio: Radio,
        grants: () -> Int,
        kept: SeatStore = MemorySeat(),
        ranging: RangingRadio = NoRanging,
    ): Pair<ContactUploader, MutableList<RoundBody.RadioFaulted>> {
        val reports = mutableListOf<RoundBody.RadioFaulted>()
        return ContactUploader(radio, { ContactWindow(10) }, faulted = { reports += it }, kept = kept, refusedGrants = grants, ranging = ranging, send = { }) to reports
    }

    /**
     * **GUARD (D-311): the Bluetooth radio turned off mid-round goes up as a switch-off**, the cause
     * the production line (`causeOf`) gives both managers going Off, never as a leave.
     */
    @Test
    fun bluetoothTurnedOffGoesUpAsASwitchOff() {
        val radio = Radio()
        val (up, reports) = uploader(radio, { 0 })
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat()
        radio.pending = causeOf(RadioPower.Off, RadioPower.Off)
        repeat(3) { up.beat() }
        assertEquals(listOf(RadioCause.Permission), reports.map { it.cause }, "Bluetooth turned off did not go up as a switch-off")
    }

    /**
     * **GUARD (D-311, finding 4): one Bluetooth switch-off is one report**, whether the radio and the
     * grants read hear it on the same beat or the read is one or two beats behind.
     */
    @Test
    fun oneBluetoothSwitchOffIsOneReportFromEverySource() {
        for (lag in 0..2) {
            var off = 0
            val radio = Radio()
            val (up, reports) = uploader(radio, { off })
            up.heard(RoundMessage.ContactSeedIssued(seed))
            up.beat()
            radio.pending = causeOf(RadioPower.Off, RadioPower.Unauthorized)
            if (lag == 0) off = bluetooth
            up.beat()
            repeat(lag) { up.beat() }
            off = bluetooth
            repeat(4) { up.beat() }
            assertEquals(listOf(RadioCause.Permission), reports.map { it.cause }, "lag $lag: one switch-off went up as something else, or twice")
        }
    }

    /** **Back on and off again is a second switch-off** (D-310's rule, kept): it counts again. */
    @Test
    fun bluetoothBackOnAndOffAgainIsASecondReport() {
        var off = 0
        val radio = Radio()
        val (up, reports) = uploader(radio, { off })
        up.heard(RoundMessage.ContactSeedIssued(seed))
        radio.pending = RadioCause.Permission
        off = bluetooth
        repeat(3) { up.beat() }
        off = 0
        repeat(3) { up.beat() }
        radio.pending = RadioCause.Permission
        off = bluetooth
        repeat(3) { up.beat() }
        assertEquals(listOf(RadioCause.Permission, RadioCause.Permission), reports.map { it.cause }, "a second switch-off was not its own report")
    }

    /**
     * **GUARD (D-311, finding 4, across a relaunch): the radio's report and the read's are one.** The
     * radio told the house before the process ended; the relaunch reads the grant off, and its own
     * radio reports the radio still down at its first advertise. Neither is a second switch-off.
     */
    @Test
    fun aRelaunchDoesNotReportABluetoothSwitchOffAgain() {
        val store = MemorySeat()
        val radio = Radio()
        val (first, firstReports) = uploader(radio, { 0 }, store)
        first.heard(RoundMessage.ContactSeedIssued(seed))
        radio.pending = RadioCause.Permission
        repeat(2) { first.beat() }
        first.acknowledged(firstReports.single().onset)
        first.leftTheForeground()
        val again = Radio().also { it.pending = RadioCause.Permission }
        val (second, secondReports) = uploader(again, { bluetooth }, store)
        second.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { second.beat() }
        assertEquals(listOf(RadioCause.Person), secondReports.map { it.cause }, "the relaunch told the house of the same switch-off again, or lost the leave")
    }

    /**
     * **GUARD (D-311, finding 1, the ranging half): NEARBY DEVICES refused is one report a round,
     * across a relaunch.** The pool forgets its refusal when it opens again (`RangingPool.open`), and
     * the relaunched phone's launch probe reads the row refused: neither is a second switch-off.
     */
    @Test
    fun nearbyDevicesRefusedIsOneReportARoundAcrossARelaunch() {
        val store = MemorySeat()
        val (first, firstReports) = uploader(Radio(), { 0 }, store, Ranging().also { it.pending = RadioCause.Permission })
        first.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { first.beat() }
        assertEquals(listOf(RadioCause.Permission), firstReports.map { it.cause })
        first.acknowledged(firstReports.single().onset)
        first.leftTheForeground()
        val (second, secondReports) = uploader(Radio(), { nearby }, store, Ranging().also { it.pending = RadioCause.Permission })
        second.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { second.beat() }
        assertEquals(listOf(RadioCause.Person), secondReports.map { it.cause }, "the relaunch told the house of NEARBY DEVICES again, or lost the leave")
    }

    /** A new round forgets what the last one reported: its first switch-off is reported. */
    @Test
    fun aNewRoundReportsAfresh() {
        val store = MemorySeat()
        val radio = Radio()
        val (up, reports) = uploader(radio, { bluetooth }, store)
        up.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(2) { up.beat() }
        up.roundEnded()
        up.heard(RoundMessage.ContactSeedIssued(ContactSeed("fedcba9876543210fedcba9876543210")))
        repeat(2) { up.beat() }
        assertEquals(listOf(RadioCause.Permission, RadioCause.Permission), reports.map { it.cause })
    }
}
