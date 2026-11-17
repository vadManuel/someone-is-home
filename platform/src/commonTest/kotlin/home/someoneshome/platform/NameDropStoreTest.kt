package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The NameDrop answer survives a relaunch** (D-248), on the real filesystem: a process reading
 * back what an earlier one wrote is what a relaunch is.
 */
class NameDropStoreTest {

    @BeforeTest
    fun start() = clearNameDropOff()

    @AfterTest
    fun clean() = clearNameDropOff()

    /** **A fresh install has not been told**, so the step is shown. */
    @Test
    fun aFreshInstallHasNotBeenTold() {
        assertFalse(loadNameDropOff())
    }

    @Test
    fun aSavedAnswerComesBack() {
        saveNameDropOff()
        assertTrue(loadNameDropOff())
    }

    @Test
    fun aClearedAnswerIsGone() {
        saveNameDropOff()
        clearNameDropOff()
        assertFalse(loadNameDropOff())
    }
}
