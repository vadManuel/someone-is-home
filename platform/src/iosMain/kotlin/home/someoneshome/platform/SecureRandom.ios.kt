package home.someoneshome.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/**
 * `SecRandomCopyBytes` on the default generator: the system's CSPRNG, the one the keychain uses.
 *
 * A status other than success throws (see the expect): nothing here falls back to a weaker source.
 */
actual fun secureRandomBytes(count: Int): ByteArray {
    require(count >= 0) { "a negative number of bytes was asked for" }
    val out = ByteArray(count)
    if (count == 0) return out
    val status = out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, count.convert(), pinned.addressOf(0))
    }
    check(status == errSecSuccess) { "the secure random source failed (status $status)" }
    return out
}
