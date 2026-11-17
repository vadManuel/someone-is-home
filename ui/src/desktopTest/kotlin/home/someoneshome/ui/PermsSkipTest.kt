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
 *
 * The phone here has already confirmed the NameDrop step (D-248), which is what lets the skip land on
 * the join screen; [NameDropStepTest] holds the fresh install's half.
 */
@OptIn(ExperimentalTestApi::class)
class PermsSkipTest {

    private fun afterTheSelfTest(os: MutableMap<DevicePermission, Grant>, meanwhile: () -> Unit = {}): ScreenId {
        var landed = ScreenId.Boot
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(
                PanelState(screen = ScreenId.Boot),
                perms = PermsModel(read = { os[it] ?: Grant.Unasked }),
                // An install that has already said NameDrop is off, so a skip lands on the join
                // screen; a fresh install's skip lands on the NameDrop step (NameDropStepTest).
                nameDrop = NameDropModel.sample(),
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
            for (answer in listOf(Grant.Unasked, Grant.Refused)) {
                // Local Network can never be confirmed, so Refused opens its row (DevicePermission
                // .opensTheGate); Unasked is the one answer that blocks it.
                if (!row.confirmable && answer == Grant.Refused) continue
                val os = everything.also { it[row] = answer }
                assertEquals(
                    ScreenId.Perms, afterTheSelfTest(os),
                    "${row.label} $answer and the phone skipped the Perms screen",
                )
            }
        }
    }

    /**
     * **Asked when the self-test runs out, not when it is drawn.** Notifications answers through a
     * callback that lands after launch; a decision taken on the first frame would show every
     * granted phone the screen.
     */
    @Test
    fun aRowAnsweredDuringTheSelfTestCounts() {
        val os = everything.also { it.remove(DevicePermission.Notifications) }
        val landed = afterTheSelfTest(os) { os[DevicePermission.Notifications] = Grant.Granted }
        assertEquals(ScreenId.Join, landed, "the answer that arrived during the self-test was not read")
    }

    private companion object {
        /** `Flow.autoAdvance`'s Boot row, read rather than restated, plus a frame. */
        val BOOT_MILLIS: Long = Flow.autoAdvance.getValue(ScreenId.Boot).afterMillis.toLong() + 100L
    }
}
