package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.ContactMemory
import home.someoneshome.core.ContactResolution
import home.someoneshome.core.ContactUpload
import home.someoneshome.core.admit
import home.someoneshome.core.contactSeedFor
import home.someoneshome.core.resolveContacts
import home.someoneshome.model.Balance
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **D-213 under the differential harness: the resolver's output does not move with a role or an
 * arm.** The host mints a contact for every qualifying pair and leaves the outcome to the rules, so
 * the same arrivals must mint the same events, and leave the same memory, whoever holds which role
 * and whoever is armed. The rules then diverge, as they should; that is the 0.6 diff's to explain.
 *
 * Stricter than the stock diff, which tolerates divergence on the swapped seats: here nothing may
 * differ on any seat.
 */
class ContactResolverDifferentialTest {

    private val ready = Balance.REVOKE_COOLDOWN / 2

    /** Seat 1 (an Insider) Revokes seat 6, so every variant has one Revoked seat to report. */
    private val events: List<Event> = listOf(
        Event.RoundArmed(
            Tick(0), seed = 20260818L, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS),
            names = NAMES, oneLines = LINES, terminal = STATION,
        ),
        Event.RevokeArmed(Tick(ready + 1), Seat(1)),
        Event.ContactMade(Tick(ready + 2), Seat(1), Seat(6)),
    )

    /** The second Insider holds an armed Revoke. */
    private fun armed(e: List<Event>, seat: Seat) = e + Event.RevokeArmed(Tick(ready + 3), seat)

    private fun walk(e: List<Event>): GameState = e.fold(GameState.EMPTY) { s, event ->
        (admit(s, event) as? Admission.Admitted)?.reduction?.state ?: s
    }

    private val w = ContactWindow(9_000L)

    /** The arrivals, built against the seeds the baseline round issued, which every variant shares. */
    private fun arrivals(seed: Long, huddleHearsTheRevoked: Boolean = true): List<Pair<Long, List<ContactUpload>>> {
        fun hears(seat: Int, rssi: Int = -22) =
            Sighting(ContactTokens.of(contactSeedFor(seed, Seat(seat)), w), rssi, w)
        var n = 0L
        fun up(seat: Int, vararg heard: Sighting) = ContactUpload(
            Seat(seat),
            SightingUpload.unpack(SightingUpload.pack(contactSeedFor(seed, Seat(seat)), w, n++, heard.toList()))!!,
        )
        val t = ready + 200
        return listOf(
            // The armed Insider in a huddle with two Residents, and one of the Residents hears seat 6.
            t to listOf(
                up(5, hears(2), hears(4)),
                if (huddleHearsTheRevoked) up(2, hears(5), hears(4), hears(6)) else up(2, hears(5), hears(4)),
                up(4, hears(2), hears(5)),
            ),
            // The other Insider touches a Resident one-sidedly, then both ways.
            t + 10 to listOf(up(1, hears(3)), up(6)),
            t + 20 to listOf(up(3, hears(1, -30)), up(7, hears(6, -36))),
            // Seat 3 and seat 7, a Resident pair, and seat 7 reports seat 6.
            t + 140 to listOf(up(3, hears(7)), up(7, hears(3), hears(6))),
        )
    }

    private fun resolve(state: GameState, huddleHearsTheRevoked: Boolean = true): List<ContactResolution> {
        var memory = ContactMemory.EMPTY
        return arrivals(state.seed, huddleHearsTheRevoked).map { (tick, uploads) ->
            resolveContacts(memory, Tick(tick), w, uploads, state).also { memory = it.memory }
        }
    }

    @Test
    fun `the resolver mints the same events whoever holds the roles and whoever is armed`() {
        val baseline = walk(events)
        val output = resolve(baseline)
        val minted = output.flatMap { it.events }
        assertTrue(minted.filterIsInstance<Event.ContactMade>().size >= 6, "too few contacts to compare: $minted")
        assertTrue(minted.any { it is Event.MeetingCalled }, "no report to compare: $minted")

        val variants = mapOf(
            "seats 1 and 3 exchanged" to withRolesExchanged(events, Seat(1), Seat(3)),
            "seats 5 and 2 exchanged" to withRolesExchanged(events, Seat(5), Seat(2)),
            "seat 4 toggled to Insider" to withRoleSwapped(events, Seat(4)),
            "seat 5 armed" to armed(events, Seat(5)),
            "seats 1 and 3 exchanged, seat 5 armed" to armed(withRolesExchanged(events, Seat(1), Seat(3)), Seat(5)),
            "seats 5 and 2 exchanged, seat 2 armed" to armed(withRolesExchanged(events, Seat(5), Seat(2)), Seat(2)),
        )
        for ((label, variant) in variants) {
            val other = walk(variant)
            // The comparison is only worth something if the variant really is different underneath
            // and the same where the resolver may look.
            assertNotEquals(
                SEATS.map { baseline.roleOf(it) } to baseline.cooldownArmed,
                SEATS.map { other.roleOf(it) } to other.cooldownArmed,
                "$label: the variant is the baseline; this compares nothing",
            )
            assertEquals(baseline.revoked, other.revoked, "$label: the Revoked seats differ, so the reports would")
            assertEquals(output, resolve(other), "$label: the resolver's output moved with a role or an arm")
        }
    }

    /** And downstream the rules do diverge, which is the point: the outcome is theirs, not the resolver's. */
    @Test
    fun `the same events land differently once the rules read the roles`() {
        fun revokedAfter(state: GameState, huddleHearsTheRevoked: Boolean) =
            resolve(state, huddleHearsTheRevoked).flatMap { it.events }
                .fold(state) { s, e -> (admit(s, e) as? Admission.Admitted)?.reduction?.state ?: s }
                .revoked.map { it.index }
        val unarmed = walk(events)
        val armed = walk(armed(events, Seat(5)))
        assertEquals(Role.Insider, armed.roleOf(Seat(5)))
        assertEquals(listOf(6), revokedAfter(unarmed, huddleHearsTheRevoked = false))
        assertEquals(
            listOf(2, 6), revokedAfter(armed, huddleHearsTheRevoked = false),
            "the armed Insider's huddle should Revoke seat 2",
        )
        // **One touch, landed in its first half, before the report sorted after it** (T1, D-220).
        // When seat 2 also hears the Revoked seat 6, its report sorts after its own half of the touch
        // with seat 5 (seat order, D-213). Since T1 that half answers the whole touch and lands seat
        // 5's armed Revoke, so seat 2 is out before its report is admitted, and R3 refuses a report
        // from an out seat. Until T1 the Revoke waited for seat 5's own half, sorted after the
        // report's meeting, and R5 refused it; an Insider seated below seat 2 already landed it
        // first. The outcome no longer depends on which of the two sits higher.
        assertEquals(
            listOf(2, 6), revokedAfter(armed, huddleHearsTheRevoked = true),
            "the armed Revoke did not land in the touch's first half, ahead of the report sorted after it",
        )
    }
}
