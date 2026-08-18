package home.someoneshome.core

/**
 * The shape every rule returns: the next state, plus the effects it emitted.
 *
 * Pure. Events with integer timestamps in, state and effects out — no clock read, no coroutine,
 * no platform type. That is what lets a 25-minute eight-player round replay byte-identically
 * from its recording, and lets the rules be tested headless in milliseconds instead of with
 * eight phones in a dark room.
 */
data class Reduction<S, E>(val state: S, val effects: List<E>)
