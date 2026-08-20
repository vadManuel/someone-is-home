package home.someoneshome.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * The Documents directory, which is **included in device backup**.
 *
 * Not Caches and not tmp: the OS deletes both under storage pressure, and a house evaporating
 * because a phone got full is the same fifteen minutes lost, arriving without warning on an
 * evening when eight people are already standing in the hall. Nothing here sets
 * `isExcludedFromBackup` — being in the backup is the entire mechanism (game-architecture.md:419).
 */
actual fun houseMapStorageDirectory(): String =
    NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String

private fun houseMapPath(): String {
    val directory = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String

    // Created if absent. A real app launch has Documents already, but a test host does not — and
    // that is how this was found: every write returned false into a Unit-returning function, so
    // saving appeared to work and loading came back empty.
    if (!NSFileManager.defaultManager.fileExistsAtPath(directory)) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    return "$directory/$FILE_NAME"
}

private const val FILE_NAME = "house-map.txt"

/**
 * Atomic. `atomically = true` writes to a temporary file and renames, so a host closing the app
 * mid-save leaves either the old map or the new one, never half of either.
 */
actual fun saveHouseMap(text: String) {
    val path = houseMapPath()
    val wrote = NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    // The return value is the whole point. Discarding it is what made every save silently do
    // nothing while the app carried on as though the house had been stored.
    if (!wrote) throw HouseMapNotSaved(path)
}

actual fun loadHouseMap(): String? =
    NSString.stringWithContentsOfFile(
        path = houseMapPath(),
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

actual fun clearHouseMap() {
    NSFileManager.defaultManager.removeItemAtPath(houseMapPath(), error = null)
}
