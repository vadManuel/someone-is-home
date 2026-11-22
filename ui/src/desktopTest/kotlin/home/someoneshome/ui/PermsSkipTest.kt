package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The Perms screen is skipped on a phone the OS already grants everything, and shown otherwise**
 * (D-235; D-161).
 *
 * Driven through [FlowHost] on the virtual clock, the way the app root drives it: the self-test is
 * drawn, its 2.4 seconds run out, and the screen it falls through to is asserted. The OS is a map
 * read through [PermsModel]'s own `read` seam, which is the seam `CheatRoot` hands the real
 * permissions to.
 */
@OptIn(ExperimentalTestApi::class)
class PermsSkipTest {

    private fun afterTheSelfTest(os: MutableMap<DevicePermission, Grant>, meanwhile: () -> Unit = {}): ScreenId {
        var landed = ScreenId.Boot
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(
                PanelState(screen = ScreenId.Boot),
                perms = PermsModel(read = { os[it] ?: Grant.Unasked }),
            )
            setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(1_000)
            meanwhile()
            mainClock.advanceTimeBy(BOOT_MILLIS)
            waitForIdle()
            landed = model.state.screen
        }
        return landed
    }

    private val everything get() = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()

    /** **GUARD: every row granted, and the phone goes straight from the self-test to the join screen.** */
    @Test
    fun aPhoneGrantedEverythingSkipsThePermsScreen() {
        assertEquals(ScreenId.Join, afterTheSelfTest(everything), "a fully granted phone was shown the Perms screen")
    }

    /** **GUARD: any one row missing, and the Perms screen is shown**, row by row over the whole set. */
    @Test
    fun aPhoneMissingAnyOneRowIsShownThePermsScreen() {
        for (row in DevicePermission.entries) {
            // D-309: every row must read GRANTED, so ASKED (NEARBY DEVICES' probe with no clear
            // answer) is short as well.
            for (answer in listOf(Grant.Unasked, Grant.Asked, Grant.Refused)) {
                val os = everything.also { it[row] = answer }
                assertEquals(
                    ScreenId.Perms, afterTheSelfTest(os),
                    "${row.label} $answer and the phone skipped the Perms screen",
                )
            }
        }
    }

    /**
     * **D-309: NEARBY DEVICES reading ASKED is not skipped past**, and a probe that answers GRANTED
     * while the self-test runs (the app root's refresh probes on launch) is.
     */
    @Test
    fun aPhoneWhoseNearbyDevicesReadsAskedIsShownThePermsScreen() {
        val asked = everything.also { it[DevicePermission.NearbyDevices] = Grant.Asked }
        assertEquals(
            ScreenId.Perms, afterTheSelfTest(asked),
            "NEARBY DEVICES reads ASKED and the phone skipped the Perms screen",
        )
        val probed = everything.also { it[DevicePermission.NearbyDevices] = Grant.Asked }
        val landed = afterTheSelfTest(probed) { probed[DevicePermission.NearbyDevices] = Grant.Granted }
        assertEquals(ScreenId.Join, landed, "the probe answered GRANTED during the self-test and was not read")
    }

    /**
     * **Asked when the self-test runs out, not when it is drawn.** Bluetooth's state answers through
     * a callback that lands after launch; a decision taken on the first frame would show every
     * granted phone the screen.
     */
    @Test
    fun aRowAnsweredDuringTheSelfTestCounts() {
        val os = everything.also { it.remove(DevicePermission.Bluetooth) }
        val landed = afterTheSelfTest(os) { os[DevicePermission.Bluetooth] = Grant.Granted }
        assertEquals(ScreenId.Join, landed, "the answer that arrived during the self-test was not read")
    }

    private companion object {
        /** `Flow.autoAdvance`'s Boot row, read rather than restated, plus a frame. */
        val BOOT_MILLIS: Long = Flow.autoAdvance.getValue(ScreenId.Boot).afterMillis.toLong() + 100L
    }
}
