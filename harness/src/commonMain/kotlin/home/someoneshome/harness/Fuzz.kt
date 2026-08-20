package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick

/**
 * A deterministic pseudo-random source, written out rather than borrowed.
 *
 * **Not `kotlin.random.Random`.** Its algorithm is stable today and guaranteed by nothing across
 * a language version, and the whole value of a fuzzer is that *"seed 4 194 fails"* stays true
 * next month when someone comes back to fix it. Same reasoning as [Transcript] refusing to lean
 * on a generated `toString()`.
 *
 * xorshift64*, which is small enough to read and has no state this file does not show.
 */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        s = s xor (s shl 13)
        s = s xor (s ushr 7)
        s = s xor (s shl 17)
        return s * -0x61c8864680b583ebL
    }

    /** Non-negative, below [bound]. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val v = nextLong() ushr 1
        return (v % bound).toInt()
    }

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]
    fun chance(oneIn: Int): Boolean = nextInt(oneIn) == 0
}

/**
 * **Story 0.10c's generator — the absurd player, not the reasonable one.**
 *
 * Scripted "reasonable" players only confirm your assumptions at scale. This emits sequences no
 * sensible client would produce: voting twice, calling meetings inside meetings, contacting
 * yourself, arming a revoke you already armed, and — at low probability — **seats that do not
 * exist**.
 *
 * Out-of-range seats are deliberate. `Seat(99)` and `Seat(-3)` are accepted by the reducer today;
 * that is a known validation gap sitting underneath D-066, and a fuzzer that politely stayed in
 * range would be a fuzzer built from the same assumptions as the code.
 *
 * **The event mix is loop-shaped and expected to be replaced.** What is not loop-shaped is
 * everything it feeds: record, replay, the text round-trip, resume, the emit allowlist and the
 * differential harness. Those are what the properties assert about, and they do not care which
 * events exist.
 */
fun fuzzRound(
    seed: Long,
    seatCount: Int = 8,
    insiderCount: Int = 2,
    events: Int = 60,
    armFirst: Boolean = true,
): List<Event> {
    val rng = Rng(seed)
    val seats = (0 until seatCount).map { Seat(it) }
    val insiders = seats.shuffledBy(rng).take(insiderCount).sortedBy { it.index }
    val markers = (0 until 7).map { MarkerId("m$it") }

    // A seat that may not exist. Low probability, deliberately including negatives.
    fun anySeat(): Seat = when {
        rng.chance(40) -> Seat(seatCount + rng.nextInt(100))
        rng.chance(60) -> Seat(-1 - rng.nextInt(5))
        else -> rng.pick(seats)
    }

    var t = 0L
    return buildList {
        if (armFirst) add(Event.RoundArmed(Tick(t++), seed, seats, insiders))
        repeat(events) {
            val at = Tick(t++)
            add(
                when (rng.nextInt(9)) {
                    0 -> Event.RoundArmed(at, rng.nextLong(), seats, insiders)
                    1 -> Event.MarkerScanned(at, anySeat(), rng.pick(markers))
                    2, 3 -> Event.SubroutineCompleted(at, anySeat(), rng.pick(markers))
                    4 -> Event.RevokeArmed(at, anySeat())
                    5 -> Event.ContactMade(at, anySeat(), anySeat())
                    6 -> Event.MeetingCalled(at, anySeat())
                    7 -> Event.VoteCast(at, anySeat(), if (rng.chance(4)) null else anySeat())
                    else -> Event.MeetingClosed(at)
                }
            )
        }
    }
}

/** Deterministic shuffle. `List.shuffled()` reaches for the platform's default random source. */
private fun <T> List<T>.shuffledBy(rng: Rng): List<T> {
    val out = toMutableList()
    for (i in out.indices.reversed()) {
        val j = rng.nextInt(i + 1)
        val tmp = out[i]; out[i] = out[j]; out[j] = tmp
    }
    return out
}
