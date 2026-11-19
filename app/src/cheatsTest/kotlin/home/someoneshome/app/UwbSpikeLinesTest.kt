package home.someoneshome.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The shapes `tools/uwb-spike/sample.sh` and `run.sh` grep for.**
 *
 * The UWB spike is measured from its log and nothing else: `run.sh` relays the token it reads off a
 * `S1 TOKEN` line, and `sample.sh` summarises a window of `S1 UPDATE` lines by their `t=` and `d=`
 * fields. A line that changes shape without this failing is a window the script reports as empty and
 * a relay that never fires, both of which look, on the night, like UWB not working.
 *
 * In `src/cheatsTest` beside the spike, for `LineWatchTest`'s reason: release does not compile it.
 */
class UwbSpikeLinesTest {

    @Test
    fun anUpdateCarriesItsFieldsInTheOrderTheScriptReads() {
        val line = updateLine(
            session = 1,
            tMs = 12_345.6789,
            r = UwbReading(
                distance = 0.4234f,
                direction = Triple(0.1f, -0.2f, -0.97f),
                azimuthDegrees = 5.74f,
                horizontalDegrees = 6.0f,
                vertical = "SAME",
            ),
        )
        assertEquals(
            "S1 UPDATE t=12345.679 d=0.423 dir=0.100,-0.200,-0.970 az=5.7 hang=6.0 vert=SAME",
            line,
        )
    }

    @Test
    fun anUpdateWithNothingInItSaysNilRatherThanDroppingTheField() {
        assertEquals(
            "S2 UPDATE t=1.000 d=nil dir=nil az=nil hang=nil vert=UNKNOWN",
            updateLine(2, 1.0, UwbReading.NONE),
        )
    }

    @Test
    fun theCapabilityLineNamesEveryFlag() {
        val line = capabilityLine(
            UwbCapabilities(
                preciseDistance = true,
                direction = false,
                cameraAssistance = true,
                extendedDistance = false,
                isSupported = true,
            ),
        )
        assertEquals(
            "CAPABILITY precise_distance=true direction=false camera_assistance=true " +
                "extended_distance=false is_supported=true",
            line,
        )
    }

    @Test
    fun numbersAreFixedPointWithNoExponentAndNoNegativeZero() {
        assertEquals("0.000", fmt(-0.0001, 3))
        assertEquals("-1.50", fmt(-1.499, 2))
        assertEquals("123456789.0", fmt(123_456_789.0, 1))
        assertEquals("0.001", fmt(0.0006, 3))
        assertEquals("42", fmt(41.6, 0))
    }

    @Test
    fun onlyAUnitVectorIsADirection() {
        assertTrue(isUnitVector(0f, 0f, -1f))
        assertTrue(isUnitVector(0.6f, 0f, 0.8f))
        assertFalse(isUnitVector(0f, 0f, 0f))
        assertFalse(isUnitVector(Float.NaN, 0f, 1f))
        assertFalse(isUnitVector(2f, 0f, 0f))
    }

    @Test
    fun theRelayFileIsOneBase64LineAndNothingElseIsTakenForAToken() {
        assertEquals("YWJj+/0=", cleanToken("  YWJj+/0=\n"))
        assertNull(cleanToken(""))
        assertNull(cleanToken("\n"))
        assertNull(cleanToken("[uwb] S1 TOKEN YWJj"))
    }

    // ---------------------------------------------------------------------------------------------
    // Three phones. Every line keeps its two-phone shape, with ` peer=<letter>` on the end, so the
    // old regexes still match and the new ones can tell A-B from A-C.

    @Test
    fun aTwoPhoneLineCarriesNoPeerFieldAndAThreePhoneLineEndsInOne() {
        val r = UwbReading(0.5f, null, null, null, "SAME")
        val two = updateLine(1, 10.0, r)
        assertEquals("S1 UPDATE t=10.000 d=0.500 dir=nil az=nil hang=nil vert=SAME", two)
        assertEquals(two, updateLine(1, 10.0, r, tag = null))
        assertEquals("S2 UPDATE t=10.000 d=0.500 dir=nil az=nil hang=nil vert=SAME peer=C", updateLine(2, 10.0, r, tag = "C"))
        assertEquals("S3 UPDATE t=1.000 d=nil dir=nil az=nil hang=nil vert=UNKNOWN peer=-", updateLine(3, 1.0, UwbReading.NONE, IDLE_TAG))
    }

    @Test
    fun theTokenLineIsTheOneRunShReads() {
        // run.sh's two-phone grep: `\[uwb\] S1 TOKEN [A-Za-z0-9+/=]+`, then awk's fourth field.
        assertEquals("S1 TOKEN YWJj+/0= gen=2 t=5.000", tokenLine(1, "YWJj+/0=", 2, 5.0, null))
        assertEquals("S1 TOKEN nil gen=1 t=5.000", tokenLine(1, null, 1, 5.0, null))
        // Three phones: the token for B only ever rides on a `peer=B` line, an idle one on `peer=-`.
        assertEquals("S2 TOKEN YWJj gen=1 t=5.000 peer=B", tokenLine(2, "YWJj", 1, 5.0, "B"))
        assertEquals("S4 TOKEN YWJj gen=1 t=5.000 peer=-", tokenLine(4, "YWJj", 1, 5.0, IDLE_TAG))
        val fields = tokenLine(2, "YWJj", 1, 5.0, "B").split(' ')
        assertEquals("YWJj", fields[fields.indexOf("TOKEN") + 1])
    }

    @Test
    fun thePeerTokenLineIsWhatRunShWaitsFor() {
        assertEquals("S1 PEER TOKEN READ chars=416 t=2.500", peerTokenLine(1, true, 416, 2.5, null))
        assertEquals("S1 PEER TOKEN UNREADABLE chars=3 t=2.500", peerTokenLine(1, false, 3, 2.5, null))
        assertEquals("S2 PEER TOKEN READ chars=460 t=2.500 peer=C", peerTokenLine(2, true, 460, 2.5, "C"))
    }

    @Test
    fun theTallyAndTheMarkKeepTheirTwoPhoneShape() {
        assertEquals("S1 TALLY t=1.000 updates=67 per_s=6.7 state=RUNNING", tallyLine(1, 1.0, 67, 6.66, "RUNNING", null))
        assertEquals("S2 TALLY t=1.000 updates=67 per_s=6.7 state=RUNNING peer=C", tallyLine(2, 1.0, 67, 6.66, "RUNNING", "C"))
        assertEquals("MARK 3 t=1.000 d=0.021", markLine(3, 1.0, listOf(null to 0.0214f)))
        assertEquals("MARK 3 t=1.000 d=nil", markLine(3, 1.0, listOf(null to null)))
        assertEquals("MARK 3 t=1.000 d_B=0.021 d_C=nil", markLine(3, 1.0, listOf("B" to 0.0214f, "C" to null)))
    }

    @Test
    fun theLimitLineNamesEveryCountAndWhyTheProbeStopped() {
        assertEquals(
            "LIMIT live=2 idle=5 total=7 model=iPhone17,1 os=iOS-26.6.2 stop=ActiveSessionsLimitExceeded t=9.500",
            limitLine(2, 5, "iPhone17,1", "iOS-26.6.2", "ActiveSessionsLimitExceeded", 9.5),
        )
        assertEquals(
            "LIMIT live=1 idle=19 total=20 model=m os=o stop=cap-20 t=1.000",
            limitLine(1, 19, "m", "o", "cap-${UwbSpike.PROBE_CAP}", 1.0),
        )
    }

    @Test
    fun theConfigLineNamesEveryOptionOnOrOff() {
        assertEquals(
            "CONFIG camera_assistance=false extended_distance=false delegate_queue=false probe=false",
            configLine(UwbOptions(), probe = false),
        )
        assertEquals(
            "CONFIG camera_assistance=true extended_distance=true delegate_queue=true probe=true",
            configLine(UwbOptions(cameraAssistance = true, extendedDistance = true, delegateQueue = true), probe = true),
        )
        assertEquals("PEERS me=A peers=B,C", peersLine("A", listOf("B", "C")))
    }

    @Test
    fun eachPeerHasItsOwnTokenFileAndTheTwoPhoneFileIsUnchanged() {
        assertEquals("uwb-peer-token.txt", peerTokenFile(null))
        assertEquals("uwb-peer-token-B.txt", peerTokenFile("B"))
    }

    @Test
    fun thePeerListTakesSingleLettersOnceAndNeverThisPhone() {
        assertEquals(listOf("B", "C"), parsePeers("B,C", "A"))
        assertEquals(listOf("A", "C"), parsePeers(" A , C ,A", "B"))
        assertEquals(listOf("C"), parsePeers("B,C", "B"))
        assertEquals(emptyList(), parsePeers(null, "A"))
        assertEquals(emptyList(), parsePeers("", null))
        assertEquals(emptyList(), parsePeers("BC,b,1,-", "A"))
    }

    @Test
    fun twoPhonesHoldOneLiveSessionAndThreeHoldOnePerPeerFirst() {
        // Two phones, exactly as before: session 1 live, the rest idle, no tag anywhere.
        assertEquals(listOf(SessionSlot(1, true, null)), sessionPlan(emptyList(), 1))
        assertEquals(
            listOf(SessionSlot(1, true, null), SessionSlot(2, false, null), SessionSlot(3, false, null)),
            sessionPlan(emptyList(), 3),
        )
        // Three: one live session per peer even when fewer sessions were asked for, then idle ones.
        assertEquals(listOf(SessionSlot(1, true, "B"), SessionSlot(2, true, "C")), sessionPlan(listOf("B", "C"), 1))
        assertEquals(
            listOf(SessionSlot(1, true, "A"), SessionSlot(2, true, "C"), SessionSlot(3, false, IDLE_TAG), SessionSlot(4, false, IDLE_TAG)),
            sessionPlan(listOf("A", "C"), 4),
        )
    }

    @Test
    fun everyNearbyInteractionErrorCodeHasAName() {
        for (code in -5889L..-5880L) assertFalse(errorName(code) == "Unknown", "code $code")
        assertEquals("ActiveSessionsLimitExceeded", errorName(-5885L))
        assertEquals("Unknown", errorName(0L))
    }
}
