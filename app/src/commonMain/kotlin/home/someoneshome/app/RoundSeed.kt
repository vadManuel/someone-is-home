package home.someoneshome.app

import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.platform.secureRandomBytes
import home.someoneshome.platform.secureRandomLong

/**
 * **A round's seed, for an evening nobody named one for** (D-215).
 *
 * The seed is the key the whole round is derived from: `armingFor` draws the Insiders from it, and
 * every value that reaches a phone is an HMAC under it (D-212, D-214). An HMAC hides a 64-bit key
 * only as well as the key is unpredictable, so this is drawn from the platform's secure source and
 * from nothing else. It used to be `kotlin.random.Random.nextLong()`, a seeded generator with a
 * published algorithm; `seedSourceCordon` now fails the build on any use of `kotlin.random` in this
 * module.
 *
 * **It is still recorded.** The value drawn here is the value `RoundArmed` carries, so a recording
 * replays the evening exactly; the determinism rules are about what is derived *from* the seed, and
 * that is `armingFor`'s, unchanged. A rehearsal that wants a repeatable evening still names a seed
 * through the bench's switch, which `armingCordon` keeps out of this source set.
 *
 * One named function, so that the one decision it holds is in one place and a caller cannot reach
 * for a different source by accident.
 */
fun drawRoundSeed(): Long = secureRandomLong()

/**
 * **A seat token, the credential a phone resumes its own seat with** (D-094, D-215).
 *
 * Not a round seed, and drawn from the same source for the same reason: a token a modded phone could
 * guess is a resume as somebody else's seat, and that seat's pushes, its role among them. Sixteen
 * secure bytes as 32 lowercase hex characters, one width for every seat.
 */
fun mintSeatToken(): SeatToken = SeatToken(
    secureRandomBytes(SEAT_TOKEN_BYTES).joinToString("") { b ->
        (b.toInt() and 0xff).toString(16).padStart(2, '0')
    },
)

private const val SEAT_TOKEN_BYTES = 16
