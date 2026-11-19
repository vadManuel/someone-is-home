package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.ContactDistance
import home.someoneshome.core.ContactGate
import home.someoneshome.core.ContactMemory
import home.someoneshome.core.ContactResolution
import home.someoneshome.core.ContactUpload
import home.someoneshome.core.admit
import home.someoneshome.core.contactSeedFor
import home.someoneshome.core.resolveContacts
import home.someoneshome.model.Balance
import home.someoneshome.model.ContactSource
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **D-213 and D-301 under the differential harness, rebuilt on distances: in a round that reads UWB,
 * the resolver's output does not move with a role or an arm.** The same arrivals, carrying the same
 * distances, must mint the same events, leave the same memory and tell every phone the same report
 * slot, whoever holds which role and whoever is armed. The rules then diverge, as they should.
 *
 * [ContactResolverDifferentialTest] keeps the Bluetooth half: both sources are live, one per round.
 * Stricter than the stock diff: here nothing may differ on any seat, slots included.
 */
class DistanceResolverDifferentialTest {

    private val ready = Balance.REVOKE_COOLDOWN / 2
    private val second = ContactGate.UPLOAD_TICKS

    /** Seat 1 (an Insider) Revokes seat 6, so every variant has one Revoked seat to hold and report. */
    private val events: List<Event> = listOf(
        Event.RoundArmed(
            Tick(0), seed = 20260818L, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS),
            names = NAMES, oneLines = LINES, terminal = STATION, contactSource = ContactSource.Uwb,
        ),
        Event.RevokeArmed(Tick(ready + 1), Seat(1)),
        Event.ContactMade(Tick(ready + 2), Seat(1), Seat(6)),
    )

    private fun armed(e: List<Event>, seat: Seat) = e + Event.RevokeArmed(Tick(ready + 3), seat)

    private fun walk(e: List<Event>): GameState = e.fold(GameState.EMPTY) { s, event ->
        (admit(s, event) as? Admission.Admitted)?.reduction?.state ?: s
    }

    private val w = ContactWindow(9_000L)

    /** The arrivals: a huddle at touching distance, a one-sided reading, a hold that is cancelled, and two holds that report. */
    private fun arrivals(seed: Long): List<Pair<Long, List<ContactUpload>>> {
        var n = 0L
        fun up(seat: Int, vararg d: Pair<Int, Int>) = ContactUpload(
            Seat(seat),
            SightingUpload.unpack(SightingUpload.pack(contactSeedFor(seed, Seat(seat)), w, n++, emptyList()))!!,
            d.map { (peer, cm) -> ContactDistance(Seat(peer), cm) },
        )
        val t = ready + 200
        return listOf(
            // A huddle of three at touching distance; seat 2 also reads the Revoked seat 6 at 25 cm.
            t to listOf(up(5, 2 to 4, 4 to 8), up(2, 5 to 3, 4 to 6, 6 to 25), up(4, 2 to 7, 5 to 9)),
            // The other Insider reads seat 3 at 5 cm one-sidedly, then both ways; seat 7 reads seat 6 at 28 cm.
            t + 10 to listOf(up(1, 3 to 5), up(6, 1 to 2), up(7, 6 to 28)),
            t + 20 to listOf(up(3, 1 to 4), up(2, 6 to 24), up(7, 6 to 29)),
            // Seat 2 walks off (cancelled); seat 7 holds on.
            t + 40 to listOf(up(2, 6 to 60), up(7, 6 to 18)),
            t + 60 to listOf(up(7, 6 to 18), up(3, 6 to 12)),
            t + 80 to listOf(up(3, 6 to 12)),
            t + 100 to listOf(up(3, 6 to 12)),
        )
    }

    private fun resolve(state: GameState): List<ContactResolution> {
        var memory = ContactMemory.EMPTY
        return arrivals(state.seed).map { (tick, uploads) ->
            resolveContacts(memory, Tick(tick), w, uploads, state).also { memory = it.memory }
        }
    }

    private val variants = mapOf(
        "seats 1 and 3 exchanged" to withRolesExchanged(events, Seat(1), Seat(3)),
        "seats 5 and 2 exchanged" to withRolesExchanged(events, Seat(5), Seat(2)),
        "seat 4 toggled to Insider" to withRoleSwapped(events, Seat(4)),
        "seat 5 armed" to armed(events, Seat(5)),
        "seats 1 and 3 exchanged, seat 5 armed" to armed(withRolesExchanged(events, Seat(1), Seat(3)), Seat(5)),
        "seats 5 and 2 exchanged, seat 2 armed" to armed(withRolesExchanged(events, Seat(5), Seat(2)), Seat(2)),
    )

    @Test
    fun `the resolver mints the same events and slots on distances whoever holds the roles and whoever is armed`() {
        val baseline = walk(events)
        assertEquals(ContactSource.Uwb, baseline.contactSource)
        val output = resolve(baseline)
        val minted = output.flatMap { it.events }
        assertTrue(minted.filterIsInstance<Event.ContactMade>().size >= 8, "too few contacts to compare: $minted")
        assertEquals(
            listOf(7, 3),
            minted.filterIsInstance<Event.MeetingCalled>().map { it.caller.index },
            "the holds did not report as scripted: $minted",
        )
        assertTrue(minted.filterIsInstance<Event.MeetingCalled>().all { it.trigger == MeetingTrigger.RevokeReported(Seat(6)) })
        val slots = output.flatMap { r -> r.slots.map { it.seat.index to it.slot } }
        assertTrue((2 to ReportSlot.Cancelled) in slots, "no hold was cancelled to compare: $slots")
        assertTrue(slots.any { it.second is ReportSlot.Pending }, "no hold was pending to compare: $slots")

        for ((label, variant) in variants) {
            val other = walk(variant)
            assertNotEquals(
                SEATS.map { baseline.roleOf(it) } to baseline.cooldownArmed,
                SEATS.map { other.roleOf(it) } to other.cooldownArmed,
                "$label: the variant is the baseline; this compares nothing",
            )
            assertEquals(baseline.revoked, other.revoked, "$label: the Revoked seats differ, so the holds would")
            assertEquals(output, resolve(other), "$label: the resolver's output on distances moved with a role or an arm")
        }
    }

    /** And downstream the rules do diverge, which is the point: the outcome is theirs, not the resolver's. */
    @Test
    fun `the same distances land differently once the rules read the roles`() {
        fun revokedAfter(state: GameState) = resolve(state).flatMap { it.events }
            .fold(state) { s, e -> (admit(s, e) as? Admission.Admitted)?.reduction?.state ?: s }
            .revoked.map { it.index }
        assertEquals(listOf(6), revokedAfter(walk(events)), "an unarmed huddle Revoked somebody")
        assertEquals(listOf(2, 6), revokedAfter(walk(armed(events, Seat(5)))), "the armed Insider's huddle did not Revoke seat 2")
    }
}
