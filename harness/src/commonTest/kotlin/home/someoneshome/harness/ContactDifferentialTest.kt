package home.someoneshome.harness

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-212 through the leak instruments: the seed and the upload, under the differential harness
 * (0.6) and the per-client allowlist (0.6b).**
 *
 * The diff tolerates divergence on the swapped seats, so "no unexplained divergence" alone would
 * pass a seed that moved with the role. These tests are stricter: the seed lines are compared on
 * **every** seat, swapped ones included, and must be identical.
 */
class ContactDifferentialTest {

    private val kind = EmitSchema.CONTACT_SEED_ISSUED.name

    private fun seedLines(t: ClientTranscripts, seat: Seat) =
        t.linesFor(seat).filter { it.startsWith("$kind|") }

    private fun seedOf(line: String) = ContactSeed(line.substringAfter("|seed="))

    /** 0.6b, per client: each phone receives exactly one seed, and it is its own. */
    @Test
    fun `every phone is sent one seed and it is its own`() {
        val transcripts = recordPerClient(GameState.EMPTY, round())
        val seen = mutableListOf<ContactSeed>()
        for (seat in SEATS) {
            val lines = seedLines(transcripts, seat)
            assertEquals(1, lines.size, "seat ${seat.index} was sent ${lines.size} seeds: $lines")
            assertTrue(lines.single().startsWith("$kind|seat=${seat.index}|"), "seat ${seat.index} got $lines")
            seen += seedOf(lines.single())
        }
        assertEquals(SEATS.size, seen.distinct().size, "two phones were sent the same seed")
    }

    /** 0.6: a role swap and a role exchange leave every seat's seed exactly where it was. */
    @Test
    fun `the seeds do not move when roles do`() {
        val baseline = recordPerClient(GameState.EMPTY, round())
        for (variant in listOf(
            withRoleSwapped(round(), Seat(1)),
            withRolesExchanged(round(), Seat(1), Seat(3)),
            withRolesExchanged(round(), Seat(5), Seat(6)),
        )) {
            val other = recordPerClient(GameState.EMPTY, variant)
            for (seat in SEATS) {
                val a = seedLines(baseline, seat)
                assertTrue(a.isNotEmpty(), "seat ${seat.index} has no seed; this compares nothing")
                assertEquals(a, seedLines(other, seat), "seat ${seat.index}'s seed moved with a role")
            }
        }
        val exchanged = diff(GameState.EMPTY, round(), withRolesExchanged(round(), Seat(1), Seat(3)), listOf(Seat(1), Seat(3)))
        assertEquals(
            emptyList(),
            exchanged.divergences.filter { it.baseline?.startsWith(kind) == true || it.variant?.startsWith(kind) == true },
        )
    }

    /**
     * **The upload, built from the seed each phone was really sent, in both runs.** The same
     * sighting list packs to the same bytes on a seat whatever role it holds, and every seat's frame
     * is one length: an armed Insider touching somebody, the other Insider, a Resident, and a phone
     * that heard nothing.
     */
    @Test
    fun `uploads built from delivered seeds are one size and do not move with roles`() {
        val window = ContactWindow(88_000L)
        val baseline = recordPerClient(GameState.EMPTY, round())
        val exchanged = recordPerClient(GameState.EMPTY, withRolesExchanged(round(), Seat(1), Seat(3)))
        val book = SEATS.map { it to seedOf(seedLines(baseline, it).single()) }
        fun heard(of: Int, rssi: Int) = Sighting(ContactTokens.of(book[of].second, window), rssi, window)

        // Seats 1 and 5 hold the Insider role in the fixture; seat 1 is touching seat 2.
        val what = mapOf(
            Seat(1) to listOf(heard(2, -21), heard(0, -74)),
            Seat(5) to listOf(heard(6, -66)),
            Seat(2) to listOf(heard(1, -23), heard(3, -80), heard(4, -91)),
            Seat(7) to emptyList(),
        )
        fun frame(t: ClientTranscripts, seat: Seat) = TransportWire.encode(
            TransportFrame.Carry(
                RoundWire.encode(SightingUpload.pack(seedOf(seedLines(t, seat).single()), window, 12, what.getValue(seat))),
            ),
        )
        val lengths = what.keys.associateWith { frame(baseline, it).length }
        assertEquals(1, lengths.values.distinct().size, "an upload's size says something: $lengths")
        for (seat in what.keys) {
            assertEquals(frame(baseline, seat), frame(exchanged, seat), "seat ${seat.index}'s upload moved with a role")
        }
    }
}
