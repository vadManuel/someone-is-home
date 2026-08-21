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
 * The one place setup data is written, shared by the house map and the house plan.
 *
 * Both halves of the setup walk want the same directory, the same atomicity and the same refusal
 * to report a failed write as a success. Written twice they would drift, and the half that drifted
 * would be the half nobody had a test looking at.
 */

/**
 * The Documents directory, which is **included in device backup**.
 *
 * Not Caches and not tmp: the OS deletes both under storage pressure, and a house evaporating
 * because a phone got full is fifteen minutes lost, arriving without warning on an evening when
 * eight people are already standing in the hall. Nothing here sets `isExcludedFromBackup` — being
 * in the backup is the entire mechanism (game-architecture.md:419).
 */
internal fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String

internal fun documentPath(fileName: String): String {
    val directory = documentsDirectory()

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
    return "$directory/$fileName"
}

/**
 * Atomic. `atomically = true` writes to a temporary file and renames, so a host closing the app
 * mid-save leaves either the old file or the new one, never half of either.
 *
 * Returns whether it landed. **The return value is the whole point** — discarding it is what made
 * every save silently do nothing while the app carried on as though the house had been stored.
 */
internal fun writeDocument(path: String, text: String): Boolean =
    NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )

internal fun readDocument(path: String): String? =
    NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

internal fun deleteDocument(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
