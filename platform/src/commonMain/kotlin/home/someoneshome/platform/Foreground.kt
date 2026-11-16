package home.someoneshome.platform

/**
 * **Whether this app is in front of its owner: two moments, as callbacks** (H2, D-225).
 *
 * The host's phone runs the house, and iOS suspends an app that leaves the foreground — a lock, the
 * side button, a swipe home. A suspended app's listening socket and its Bonjour advertisement do
 * not survive it: on 2026-11-16 a host that auto-locked in the lobby woke with every seat's socket
 * gone (`ENOTCONN`), its advertisement refused (`-72000`), and nothing that would take a seat back.
 * These two moments are when the house can take itself off the air cleanly and put itself back.
 *
 * An interface over one function so that the decisions hung off it are tested without an OS: a test
 * calls the two callbacks itself. [watch] returns the way to stop watching.
 *
 * It reports and decides nothing. In particular it never touches the lamp or the screen (rules 5
 * and 6): leaving the foreground is not a game event, and nothing a player sees changes because of
 * it.
 */
interface Foreground {
    /** Call [onBackground] when the app leaves the foreground and [onForeground] when it returns. */
    fun watch(onBackground: () -> Unit, onForeground: () -> Unit): () -> Unit
}

/** This phone's foreground. On iOS, `UIApplicationDidEnterBackground` and `WillEnterForeground`. */
expect fun deviceForeground(): Foreground
