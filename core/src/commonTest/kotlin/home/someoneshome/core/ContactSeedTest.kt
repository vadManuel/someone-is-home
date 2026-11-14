package home.someoneshome.core

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * **D-212 at the rules' end: every seat is issued its own seed at arming, the seed is the round's
 * and the seat's and nothing else, and it reaches that seat's phone alone.**
 */
class ContactSeedTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val markers = (0 until 8).map { MarkerId("m$it") }

    private fun armed(insiders: List<Seat>, seed: Long = 20260823L) = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed, seats, insiders, null, placed(markers)),
    )

    private fun seedsOf(effects: List<Effect>): List<Pair<Seat, ContactSeed>> =
        effects.filterIsInstance<Effect.ContactSeedIssued>().map { it.seat to it.seed }

    /** Pinned to Python's `hmac`, so the derivation is one function wherever it runs. */
    @Test
    fun `a seat's seed is an HMAC of the round's seed and nothing else`() {
        assertEquals(ContactSeed("7609365cd8eef1caf0cfcd6e645f1dd9"), contactSeedFor(20260823L, Seat(0)))
        assertEquals(ContactSeed("b8369a0a447dda23d6d1453fa114c237"), contactSeedFor(20260823L, Seat(1)))
        assertNotEquals(contactSeedFor(20260823L, Seat(0)), contactSeedFor(20260824L, Seat(0)))
    }

    /** One per seat, in seat order, every seat its own, and the same on replay. */
    @Test
    fun `arming issues every seat exactly one seed of its own`() {
        val reduction = armed(listOf(Seat(1)))
        val issued = seedsOf(reduction.effects)
        assertEquals(seats, issued.map { it.first }, "the seed run is not one per seat in seat order")
        assertEquals(seats.size, issued.map { it.second }.distinct().size, "two seats share a seed")
        assertEquals(seats.map { it to contactSeedFor(20260823L, it) }, issued)
        assertEquals(issued, seedsOf(armed(listOf(Seat(1))).effects), "the seeds moved on replay")
    }

    /** **The seed never reads the role.** Who holds the Insider role changes nothing in the run. */
    @Test
    fun `the seeds do not move with who the Insiders are`() {
        val none = seedsOf(armed(emptyList()).effects)
        assertEquals(none, seedsOf(armed(listOf(Seat(1))).effects))
        assertEquals(none, seedsOf(armed(listOf(Seat(0), Seat(5))).effects))
    }

    /**
     * **Through the emit boundary: each seed lands on its own seat and no other**, Insider and
     * Resident alike, and the house can invert every seat's token from the seeds it issued.
     */
    @Test
    fun `each seed is delivered to its own seat alone and the house can invert its tokens`() {
        val reduction = armed(listOf(Seat(1), Seat(4)))
        val deliveries = reduction.effects.filterIsInstance<Effect.ContactSeedIssued>()
            .map { effect -> effect to EmitSchema.deliveries(effect, reduction.state).map { it.seat } }
        for ((effect, to) in deliveries) {
            assertEquals(listOf(effect.seat), to, "seat ${effect.seat.index}'s seed reached $to")
        }
        val book = seats.map { it to contactSeedFor(reduction.state.seed, it) }
        for ((seat, seed) in seedsOf(reduction.effects)) {
            val window = ContactWindow(1_000L + seat.index)
            assertEquals(seat, ContactTokens.seatOf(ContactTokens.of(seed, window), window, book))
        }
    }
}
