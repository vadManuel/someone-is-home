package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeatTokenStoreTest {

    @AfterTest
    fun clean() = clearSeatToken()

    @Test
    fun aStoredTokenComesBackVerbatim() {
        saveSeatToken("tk-relaunch-proof")
        assertEquals("tk-relaunch-proof", loadSeatToken())
    }

    @Test
    fun aClearedTokenIsGone() {
        saveSeatToken("tk-old-round")
        clearSeatToken()
        assertNull(loadSeatToken(), "a token for a round that ended must not linger to confuse a resume")
    }

    @Test
    fun aRewriteReplacesWholesale() {
        saveSeatToken("tk-first")
        saveSeatToken("tk-second")
        assertEquals("tk-second", loadSeatToken())
    }
}
