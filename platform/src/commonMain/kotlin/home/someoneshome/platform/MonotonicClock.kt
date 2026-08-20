package home.someoneshome.platform

/**
 * Real elapsed time, for driving the fixed-timestep tick (story 0.2).
 *
 * **Monotonic, not wall-clock.** A wall clock jumps: NTP corrections, the user changing the time,
 * daylight saving. A jump backwards would make the tick source read negative elapsed, and a jump
 * forwards would abandon a backlog that never existed. Neither has a symptom anyone would trace
 * to the clock.
 *
 * **This is the only clock the authority reads, and it is read at the EDGE.** The rules see no
 * datetime type at all — `core`'s boundary check fails the build if one reaches it — because a
 * clock read inside the rules is an input nobody recorded, and a round that reads the clock cannot
 * replay. A real timestamp is an *input*: sampled here, converted to a [home.someoneshome.model.Tick],
 * and written onto an event before anything reasons about it.
 *
 * **The value has no meaning on its own.** Only differences between two readings do — the origin
 * is arbitrary and differs between processes and devices. Never record one, never send one, never
 * compare one across phones.
 */
expect fun monotonicNanos(): Long
