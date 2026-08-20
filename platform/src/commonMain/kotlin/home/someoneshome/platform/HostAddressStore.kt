package home.someoneshome.platform

/**
 * Where the host's address lives between process deaths — the other half of resuming, found on
 * the first two-phone evening (2026-08-20).
 *
 * The seat token answers *who this phone is*; nothing answered *where the house is*. A killed
 * and relaunched client held its token and dutifully tried to resume into 127.0.0.1, because the
 * address had only ever lived in memory. mDNS discovery is the real answer and replaces this for
 * finding a host; until it exists, the last known address is stored beside the token with the
 * same doctrine: text in, text out, atomic write, a failed save throws.
 */
expect fun saveHostAddress(text: String)

/** The stored address, or null if none. */
expect fun loadHostAddress(): String?

/** Forget the stored address. */
expect fun clearHostAddress()

/** A save that did not happen. The phone knows who it is but not where the house is. */
class HostAddressNotSaved(val path: String) : IllegalStateException(
    "the host address was not written to $path. A relaunched phone could not find the round; " +
        "do not report the seating as durable."
)
