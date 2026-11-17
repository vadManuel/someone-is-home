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
private fun seatTokenPath(): String = seatFilePath(FILE_NAME)

/** The owed reports (D-245), in the same directory as the token and for the same reason. */
private fun owedReportsPath(): String = seatFilePath(OWED_FILE_NAME)

private fun seatFilePath(name: String): String {
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
    return "$directory/$name"
}

private const val FILE_NAME = "seat-token.txt"
private const val OWED_FILE_NAME = "owed-reports.txt"

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

/** Atomic, like the token: a report half-written is a report that cannot be read back. */
actual fun saveOwedReports(text: String) {
    val path = owedReportsPath()
    val wrote = NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!wrote) throw OwedReportsNotSaved(path)
}

actual fun loadOwedReports(): String? =
    NSString.stringWithContentsOfFile(
        path = owedReportsPath(),
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

actual fun clearOwedReports() {
    NSFileManager.defaultManager.removeItemAtPath(owedReportsPath(), error = null)
}
