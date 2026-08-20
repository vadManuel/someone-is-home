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
 * Documents, like the map — not because the token wants backing up (it outlives nothing but the
 * round) but because Caches and tmp are deleted under storage pressure, and a token evaporating
 * mid-round is a phone that cannot resume, arriving without warning.
 */
private fun seatTokenPath(): String {
    val directory = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
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

private const val FILE_NAME = "seat-token.txt"

/** Atomic, and the write's result is the point — see the map store's history. */
actual fun saveSeatToken(text: String) {
    val path = seatTokenPath()
    val wrote = NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!wrote) throw SeatTokenNotSaved(path)
}

actual fun loadSeatToken(): String? =
    NSString.stringWithContentsOfFile(
        path = seatTokenPath(),
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

actual fun clearSeatToken() {
    NSFileManager.defaultManager.removeItemAtPath(seatTokenPath(), error = null)
}
