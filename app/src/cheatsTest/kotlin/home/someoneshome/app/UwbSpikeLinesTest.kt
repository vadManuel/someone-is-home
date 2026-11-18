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

    @Test
    fun everyNearbyInteractionErrorCodeHasAName() {
        for (code in -5889L..-5880L) assertFalse(errorName(code) == "Unknown", "code $code")
        assertEquals("ActiveSessionsLimitExceeded", errorName(-5885L))
        assertEquals("Unknown", errorName(0L))
    }
}
