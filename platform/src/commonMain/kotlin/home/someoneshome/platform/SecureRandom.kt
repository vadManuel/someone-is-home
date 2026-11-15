package home.someoneshome.platform

/**
 * **[count] bytes from the operating system's cryptographically secure source** (D-215).
 *
 * The round's seed is the key every secret in a round is derived from: the Insider draw, every
 * seat's contact seed and therefore every radio token, the jam's noise, the work orders (D-212,
 * D-214). An HMAC hides a 64-bit key only as well as the key is unpredictable, so a seed a phone
 * could guess is a round a phone could redraw. `kotlin.random.Random` is a seeded generator with a
 * published algorithm; it is never the source of a seed, and `seedSourceCordon` fails the build on
 * any use of it in `app`.
 *
 * **It throws rather than falling back.** A secure source that failed and was quietly replaced by a
 * weaker one would be the failure nobody notices, in the one value whose quality cannot be seen.
 * Its callers draw before a round exists, on the host's phone, in a lit room: a LIGHTS OUT that does
 * nothing and says why on the authority's log is the loud direction rule 6 asks for.
 *
 * iOS: `SecRandomCopyBytes`. There is no JVM or desktop actual because this module has no JVM target,
 * and no JVM code anywhere in the tree arms a round.
 */
expect fun secureRandomBytes(count: Int): ByteArray

/** Eight secure bytes, big-endian, as a [Long]. Every one of the 2⁶⁴ values is reachable. */
fun secureRandomLong(): Long =
    secureRandomBytes(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
