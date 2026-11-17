package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The owed reports are kept in a file of their own, beside the seat token, and read back whole**
 * (D-245). On the Simulator's real filesystem, through the same calls a phone makes.
 */
class OwedReportsStoreTest {

    @AfterTest
    fun clean() = clearOwedReports()

    @Test
    fun whatIsKeptIsReadBackWholeAndGoesWhenCleared() {
        clearOwedReports()
        assertNull(loadOwedReports(), "a cleared store read back something")
        val text = "0123456789abcdef0123456789abcdef\n0011223344556677 person\n8899aabbccddeeff device"
        saveOwedReports(text)
        assertEquals(text, loadOwedReports(), "the owed reports did not come back as they were kept")
        saveOwedReports("short")
        assertEquals("short", loadOwedReports(), "a second save did not replace the first whole")
        clearOwedReports()
        assertNull(loadOwedReports(), "clearing left the owed reports behind")
    }

    @Test
    fun theOwedReportsAndTheSeatTokenAreTwoFiles() {
        val token = loadSeatToken()
        try {
            saveSeatToken("tk-owed-test")
            saveOwedReports("owed")
            clearOwedReports()
            assertEquals("tk-owed-test", loadSeatToken(), "clearing the owed reports took the seat token with it")
            saveOwedReports("owed")
            clearSeatToken()
            assertEquals("owed", loadOwedReports(), "clearing the seat token took the owed reports with it")
        } finally {
            if (token != null) saveSeatToken(token) else clearSeatToken()
        }
    }
}
