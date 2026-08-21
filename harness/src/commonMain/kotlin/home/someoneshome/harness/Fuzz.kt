package home.someoneshome.harness

import home.someoneshome.core.reduce
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
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
    val arming = Event.RoundArmed(Tick(t), seed, seats, insiders)

    /**
     * What the house asked this seat for at the opening arming.
     *
     * **The absurd player has to score sometimes.** A fuzzer whose entries were always wrong would
     * never once take the graded-and-correct path — the only one that moves the meter, and the one
     * D-109's asymmetry lives on — so every property below would hold over 150 rounds in which the
     * rule under test never fired. That is this project's recurring failure, not a stricter test.
     *
     * Read off the rules rather than reimplemented, so it cannot drift from what is being graded.
     * A round that re-arms mid-way moves on to different questions and these go stale, which is
     * left alone: a stale answer is a wrong answer, and wrong answers are the other half of the mix.
     */
    val opening = reduce(GameState.EMPTY, arming).state
    fun asked(seat: Seat): List<Int> = opening.openSubroutineFor(seat)?.expected ?: emptyList()

    return buildList {
        if (armFirst) add(arming.also { t++ })
        repeat(events) {
            when (rng.nextInt(9)) {
                0 -> add(Event.RoundArmed(Tick(t++), rng.nextLong(), seats, insiders))
                1 -> add(Event.MarkerScanned(Tick(t++), anySeat(), rng.pick(markers)))
                2, 3 -> {
                    // Sometimes the whole walk — scan the card, then hand over exactly what was
                    // asked for — and sometimes any of the ways that goes wrong: an entry against
                    // a Subroutine nobody armed, at a card it was not armed at, or simply wrong.
                    val seat = anySeat()
                    val marker = rng.pick(markers)
                    if (rng.chance(2)) add(Event.MarkerScanned(Tick(t++), seat, marker))
                    val entered =
                        if (rng.chance(2)) asked(seat)
                        else List(rng.nextInt(4)) { rng.nextInt(4) }
                    add(Event.SubroutineReturned(Tick(t++), seat, marker, entered))
                }
                4 -> add(Event.RevokeArmed(Tick(t++), anySeat()))
                5 -> add(Event.ContactMade(Tick(t++), anySeat(), anySeat()))
                6, 7 -> {
                    // **A whole meeting, about half the time, and the rest of the time any of the
                    // ways one goes wrong.** Same lesson the entries learned: a fuzzer that only
                    // ever calls a meeting and taps at random never once closes the check-in gate,
                    // never opens a ballot and never reads one -- so the tally, the auto-lock, the
                    // takeover and every property that rests on them would hold over 150 rounds in
                    // which the rules under test never fired.
                    if (rng.chance(2)) addAll(wholeMeeting(rng, seats) { t++ }) else {
                        add(Event.MeetingCalled(Tick(t++), anySeat(), anyTrigger(rng, ::anySeat)))
                        add(Event.MeetingCheckedIn(Tick(t++), anySeat()))
                        add(Event.ReadyToVoteDeclared(Tick(t++), anySeat()))
                        add(
                            Event.VoteSelected(
                                Tick(t++), anySeat(), if (rng.chance(4)) null else anySeat(),
                            )
                        )
                        add(Event.VoteLocked(Tick(t++), anySeat()))
                    }
                }
                else -> add(
                    // The four clock events, on their own, out of order and mostly out of phase.
                    when (rng.nextInt(4)) {
                        0 -> Event.DiscussionClosed(Tick(t++))
                        1 -> Event.VoteWindowClosed(Tick(t++))
                        2 -> Event.TallyHalfwayReached(Tick(t++))
                        else -> Event.MeetingClosed(Tick(t++))
                    }
                )
            }
        }
    }
}

/** Either way a meeting can be called, the report naming a seat that may not exist. */
private fun anyTrigger(rng: Rng, anySeat: () -> Seat): MeetingTrigger =
    if (rng.chance(2)) MeetingTrigger.MeetingCard else MeetingTrigger.RevokeReported(anySeat())

/**
 * **A meeting walked end to end**, so the lifecycle's own rules get to run.
 *
 * Everybody checks in — D-104's gate does not close a player short, so a meeting that skipped one
 * would stall in CheckIn and every phase after it would be refused, which is a fuzzer quietly
 * testing the admission gate and nothing else.
 *
 * Inside the vote it stays absurd: seats select more than once, some press READY and some do not,
 * and some tap again after pressing it. The buzzer then auto-locks whatever is left, which is the
 * path D-117 rests on.
 *
 * The revoked and restrained are still handed ballots to tap on, because they are not supposed to
 * have any and the point of a fuzzer is to hand them one.
 */
private fun wholeMeeting(rng: Rng, seats: List<Seat>, tick: () -> Long): List<Event> = buildList {
    val caller = rng.pick(seats)
    add(Event.MeetingCalled(Tick(tick()), caller, anyTrigger(rng) { rng.pick(seats) }))
    for (seat in seats) add(Event.MeetingCheckedIn(Tick(tick()), seat))

    for (seat in seats) if (rng.chance(2)) add(Event.ReadyToVoteDeclared(Tick(tick()), seat))
    add(Event.DiscussionClosed(Tick(tick())))

    for (seat in seats) {
        if (rng.chance(3)) continue
        add(Event.VoteSelected(Tick(tick()), seat, if (rng.chance(4)) null else rng.pick(seats)))
        if (rng.chance(2)) {
            add(Event.VoteLocked(Tick(tick()), seat))
            // A tap after READY. It must come back refused and re-asserted, never silently.
            if (rng.chance(2)) add(Event.VoteSelected(Tick(tick()), seat, rng.pick(seats)))
        }
    }
    add(Event.VoteWindowClosed(Tick(tick())))
    add(Event.TallyHalfwayReached(Tick(tick())))
    add(Event.MeetingClosed(Tick(tick())))
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
