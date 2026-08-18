package home.someoneshome.model

import kotlin.jvm.JvmInline

/**
 * The two roles, and the complete set of them.
 *
 * Stated positively on purpose (D-061): the rules used to be written as a list of what not to
 * say, and now name only what the vocabulary *is* and declare the list exhaustive. Shorter,
 * easier to check, and it keeps borrowed words out of a public repo entirely.
 *
 * `Insider` replaced `Guest` because a guest is someone you invited; the game is about someone
 * already inside.
 */
enum class Role { Resident, Insider }

/**
 * The seat a player occupies, which is what intents are attributed to.
 *
 * Attribution is **by connection**, never by a client naming itself — a lobby code gets you *a*
 * seat, never *that* seat. Without that, a second websocket asserting it is another player is
 * accepted, and it is the only cheat in this game that is remote, undetectable, and requires no
 * physical act.
 */
@JvmInline
value class Seat(val index: Int)
