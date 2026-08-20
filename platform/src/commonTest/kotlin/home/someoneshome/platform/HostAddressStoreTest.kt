package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostAddressStoreTest {

    @AfterTest
    fun clean() = clearHostAddress()

    @Test
    fun aStoredAddressComesBackVerbatim() {
        saveHostAddress("192.168.1.189")
        assertEquals("192.168.1.189", loadHostAddress())
    }

    @Test
    fun aClearedAddressIsGone() {
        saveHostAddress("192.168.1.189")
        clearHostAddress()
        assertNull(loadHostAddress())
    }
}
