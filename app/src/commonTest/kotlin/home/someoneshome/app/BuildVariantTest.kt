package home.someoneshome.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pins each variant to the epic's table: release carries nothing, playtest is recording + cheats
 * + marker with debug surfaces compiled out, debug is everything.
 *
 * The test compiles against whichever variant this build selected, so one run certifies ONE
 * column — which is why `:app:check` also runs the other two variants in nested builds
 * (`variantSweep*`). A plain green here says nothing about the columns it did not compile.
 */
class BuildVariantTest {

    private data class Contract(
        val recording: Boolean,
        val cheats: Boolean,
        val debugSurfaces: Boolean,
        val marker: String,
    )

    @Test
    fun theSelectedVariantKeepsItsContract() {
        val expected = when (BuildVariant.NAME) {
            "release" -> Contract(recording = false, cheats = false, debugSurfaces = false, marker = "")
            "playtest" -> Contract(recording = true, cheats = true, debugSurfaces = false, marker = "PLAYTEST")
            "debug" -> Contract(recording = true, cheats = true, debugSurfaces = true, marker = "DEBUG")
            else -> fail("unknown variant '${BuildVariant.NAME}'")
        }
        val actual = Contract(
            recording = BuildVariant.RECORDING_ON,
            cheats = BuildVariant.CHEATS_ON,
            debugSurfaces = BuildVariant.DEBUG_SURFACES_ON,
            marker = BuildVariant.MARKER,
        )
        assertEquals(expected, actual, "variant '${BuildVariant.NAME}' has drifted from 0.10b's table")
    }

    @Test
    fun theMarkerExistsExactlyWhereCheatsDo() {
        // One implies the other by design: a build a player could hold in a round either carries
        // nothing or announces itself. A cheat build with no marker is the dangerous quadrant.
        assertEquals(
            BuildVariant.CHEATS_ON,
            BuildVariant.MARKER.isNotEmpty(),
            "a build with cheats must carry the visible marker, and a clean build must not",
        )
    }
}
