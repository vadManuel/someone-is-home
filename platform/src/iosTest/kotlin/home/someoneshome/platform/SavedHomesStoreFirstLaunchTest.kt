package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import platform.Foundation.NSFileManager

/**
 * **First launch, where the storage directory does not exist yet.**
 *
 * This is the case that actually broke, and it is true exactly once per install — so a test that
 * has already run once has destroyed the condition it wants to check. The directory is removed
 * here deliberately, because the alternative is a guard that only ever fires on a machine nobody
 * is testing on.
 *
 * Found the hard way: the first version of the store discarded `writeToFile`'s Boolean, so every
 * save on a fresh install did nothing and every load came back null, with the app showing no sign
 * anything was wrong until the next evening.
 *
 * It was written against the house-map store, which is gone; the create-if-absent step it is
 * really about is `documentPath`'s and is shared by every store in this package. Pointing it at
 * the homes is pointing it at the only setup file there is.
 */
class SavedHomesStoreFirstLaunchTest {

    @AfterTest fun finish() = clearSavedHomes()

    @Test
    fun savingCreatesTheDirectoryWhenItDoesNotExist() {
        val directory = savedHomesStorageDirectory()
        NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        assertFalse(
            NSFileManager.defaultManager.fileExistsAtPath(directory),
            "the fixture could not remove $directory, so this test proves nothing",
        )

        saveSavedHomes("someone-is-home/saved-homes/1\n")
        assertEquals("someone-is-home/saved-homes/1\n", loadSavedHomes())
    }

    /** And a load before anything was ever saved is null rather than a crash. */
    @Test
    fun loadingBeforeTheDirectoryExistsIsNull() {
        NSFileManager.defaultManager.removeItemAtPath(savedHomesStorageDirectory(), error = null)
        assertEquals(null, loadSavedHomes())
    }
}
