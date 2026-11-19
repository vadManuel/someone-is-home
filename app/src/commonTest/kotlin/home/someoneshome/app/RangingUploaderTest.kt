package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Haptic
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPlan
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingReport
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.SpareKey
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.RangingRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The phone's UWB plumbing, alone (U6, D-300)**: every upload carries the radio's report at one
 * width, an out phone's included, and the house's plan reaches the radio as stops then starts.
 */
class RangingUploaderTest {

    /** A radio that says what it is told to say, and writes down what it is asked to do. */
    private class ToldRadio : RangingRadio {
        var capacity = 3
        var spares = listOf(
            RangingSpare(SpareKey.ofHex("00000000000000a1"), RangingToken.ofHex("ab".repeat(312))),
            RangingSpare(SpareKey.ofHex("00000000000000a2"), RangingToken.ofHex("cd".repeat(345))),
        )
        var readings = listOf(RangingReading(RangingHandle.ofHex("00000000000000b1"), 7, 6))
        val did = mutableListOf<String>()
        override fun capacity(): Int = capacity
        override fun spares(): List<RangingSpare> = spares
        override fun drain(): List<RangingReading> = readings
        override fun start(handle: RangingHandle, own: SpareKey, peer: RangingToken) { did += "start ${handle.hex} ${own.hex}" }
        override fun stop(handle: RangingHandle) { did += "stop ${handle.hex}" }
        override fun open() { did += "open" }
        override fun silence() { did += "silence" }
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")
    private val another = ContactSeed("fedcba9876543210fedcba9876543210")
    private var n = 0

    private fun nonce() = ByteArray(RangingWire.NONCE_BYTES) { (n++).toByte() }

    private fun rig(): Triple<ContactUploader, ToldRadio, MutableList<RoundBody.Sighted>> {
        val radio = ToldRadio()
        val sent = mutableListOf<RoundBody.Sighted>()
        val up = ContactUploader(NoRadio, { ContactWindow(10) }, ranging = radio, rangingNonce = ::nonce) { sent += it }
        return Triple(up, radio, sent)
    }

    @Test
    fun everyUploadCarriesWhatTheRadioSaysAtOneWidth() {
        val (up, radio, sent) = rig()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertEquals(listOf("open"), radio.did, "the seed did not open the pool")
        up.beat()
        radio.capacity = 0
        radio.spares = emptyList()
        radio.readings = emptyList()
        up.beat()
        radio.capacity = 6
        radio.spares = (1..6).map { RangingSpare(SpareKey.ofHex("00000000000000c$it"), RangingToken.ofHex("ee".repeat(384))) }
        radio.readings = (1..6).map { RangingReading(RangingHandle.ofHex("00000000000000d$it"), -9, 7) }
        up.beat()
        assertEquals(setOf(RangingWire.REPORT_CHARS), sent.map { it.ranging.length }.toSet(), "the report's width moved")
        val reports = sent.map { RangingWire.unpackReport(seed, it.ranging) }
        assertEquals(3, reports[0]?.capacity)
        assertEquals(ToldRadio().spares, reports[0]?.spares)
        assertEquals(listOf(RangingReading(RangingHandle.ofHex("00000000000000b1"), 7, 6)), reports[0]?.readings)
        assertEquals(RangingReport.NONE, reports[1])
        assertEquals(6, reports[2]?.readings?.size)
    }

    /** D-300: out, the phone keeps ranging and keeps saying so; only its Bluetooth goes quiet (R8). */
    @Test
    fun anOutPhoneKeepsReportingItsRanging() {
        val (up, _, sent) = rig()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat()
        up.heard(RoundMessage.RevokeLanded.Buzzing)
        up.beat()
        val (living, out) = sent.map { RangingWire.unpackReport(seed, it.ranging) }
        assertEquals(living, out, "an out phone's ranging report changed")
        assertEquals(1, out?.readings?.size, "an out phone dropped its distances")
    }

    @Test
    fun aPlanReachesTheRadioAsStopsThenStartsAndNothingElseDoes() {
        val (up, radio, _) = rig()
        val plan = RangingPlan(
            listOf(
                RangingStep.Start(RangingHandle.ofHex("00000000000000e1"), SpareKey.ofHex("00000000000000a1"), RangingToken.ofHex("12".repeat(320))),
                RangingStep.Keep(RangingHandle.ofHex("00000000000000e2")),
                RangingStep.Stop(RangingHandle.ofHex("00000000000000e3")),
            ),
        )
        // Before the seed there is no plan to apply.
        up.planned(RangingWire.packPlan(seed, nonce(), plan))
        assertEquals(emptyList(), radio.did)
        up.heard(RoundMessage.ContactSeedIssued(seed))
        radio.did.clear()
        up.planned(RangingWire.packPlan(seed, nonce(), plan))
        assertEquals(listOf("stop 00000000000000e3", "start 00000000000000e1 00000000000000a1"), radio.did)
        // A plan for another seat, a blank, the test's zeros, a damaged field: nothing.
        radio.did.clear()
        up.planned(RangingWire.packPlan(another, nonce(), plan))
        up.planned(RangingWire.blankPlan(ByteArray(RangingWire.PLAN_FIELD_BYTES) { 9 }))
        up.planned(RangingWire.NO_PLAN)
        val good = RangingWire.packPlan(seed, nonce(), plan)
        up.planned(good.replaceRange(100, 101, if (good[100] == 'A') "B" else "A"))
        assertEquals(emptyList(), radio.did, "a plan that does not open under this phone's seed moved the radio")
    }

    @Test
    fun theRoundEndingSilencesTheRanging() {
        val (up, radio, _) = rig()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.heard(RoundMessage.RoundEnded(Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short, integrity = 60))
        assertTrue("silence" in radio.did, "the round ended and the ranging radio kept its sessions")
    }
}
