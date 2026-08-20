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

/** Documents, same reasoning as the token: Caches and tmp evaporate under storage pressure. */
private fun hostAddressPath(): String {
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

private const val FILE_NAME = "host-address.txt"

actual fun saveHostAddress(text: String) {
    val path = hostAddressPath()
    val wrote = NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!wrote) throw HostAddressNotSaved(path)
}

actual fun loadHostAddress(): String? =
    NSString.stringWithContentsOfFile(
        path = hostAddressPath(),
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

actual fun clearHostAddress() {
    NSFileManager.defaultManager.removeItemAtPath(hostAddressPath(), error = null)
}
