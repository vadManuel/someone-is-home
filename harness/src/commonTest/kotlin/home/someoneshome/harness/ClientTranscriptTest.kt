package home.someoneshome.harness

import home.someoneshome.model.ClientClass
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Story 0.5. Every byte destined for a given client, captured per client. */
class ClientTranscriptTest {

    private fun run() = recordPerClient(GameState.EMPTY, round())
    private fun finalState() = record(GameState.EMPTY, round()).first

    /** Seats 0, 2 and 7 are revoked by seat 1 over the course of the fixture round. */
    private fun revokedSeats() = finalState().revoked.map { it.index }.toSet()
    private fun neverRevoked() = SEATS.filter { it.index !in revokedSeats() }

    @Test
    fun `every seated player gets a transcript - including one that receives nothing`() {
        val transcripts = run()
        assertEquals(SEATS.map { it.index }, transcripts.seats.map { it.index })
        assertTrue(revokedSeats().isNotEmpty(), "fixture is stale: nobody is revoked in this round")
    }

    @Test
    fun `the capture is deterministic`() {
        assertEquals(run().toText(), run().toText())
    }

    /**
     * Nothing is invented at the boundary. Every line a client received is a line the authority
     * actually emitted — the recorder redacts, it does not author.
     */
    @Test
    fun `no client receives a line the authority never emitted`() {
        val emitted = record(GameState.EMPTY, round()).second.effectTranscript.toSet()
        for (client in run().perClient) {
            val invented = client.lines.filterNot { it in emitted }
            assertEquals(emptyList(), invented, "seat ${client.seat.index} received unauthored lines")
        }
    }

    /**
     * End-to-end fail-closed. Every kind that reached a phone has a row in the allowlist.
     *
     * The kind is the transcript line's prefix, which is why [Transcript] renders it there.
     */
    @Test
    fun `no client receives a kind that has no allowlist row`() {
        val known = EmitSchema.knownKinds().map { it.name }.toSet()
        for (client in run().perClient) {
            val unlisted = client.lines.map { it.substringBefore('|') }.filterNot { it in known }.distinct()
            assertEquals(emptyList(), unlisted, "seat ${client.seat.index} received unlisted kinds")
        }
    }

    /**
     * **The allowlist, applied.** The live SystemIntegrity decrement and the vote attribution list
     * reach players who are out and no living player of either role.
     */
    @Test
    fun `a player who is never out receives no live progress and no attribution`() {
        val transcripts = run()
        for (seat in neverRevoked()) {
            val lines = transcripts.linesFor(seat)
            assertTrue(lines.isNotEmpty(), "seat ${seat.index} received nothing at all")
            assertFalse(
                lines.any { it.startsWith("SubroutineProgressed") },
                "living seat ${seat.index} received the live progress count",
            )
            assertFalse(
                lines.any { it.startsWith("MeetingResolved") },
                "living seat ${seat.index} received vote attribution",
            )
        }
    }

    /** The other half of the same rule: being out is an information privilege, and it works. */
    @Test
    fun `a player who is out does receive the live progress count`() {
        val transcripts = run()
        val out = revokedSeats().map { Seat(it) }
        val receiving = out.filter { seat ->
            transcripts.linesFor(seat).any { it.startsWith("SubroutineProgressed") }
        }
        assertTrue(
            receiving.isNotEmpty(),
            "no out player received progress; the out classes are unreachable in this round",
        )
    }

    /**
     * Arming lights every seat, and it is the first thing every client receives.
     *
     * This is the assertion that pins classification to the state AFTER the event. Arming is the
     * one transition that moves a client out of [RoundState.PreArm], and the pre-arm classes are
     * permitted nothing — so classifying against the state before the event silently drops the
     * whole opening lamp batch and leaves every phone dark at arming. Nothing else in this file
     * noticed: the round's later effects classify identically either way.
     *
     * It also carries the rule that made [home.someoneshome.core.reduce] emit these at all — every
     * seat is lit identically at arming, because a per-role difference there is a tell delivered
     * at the exact moment everyone is still standing together.
     */
    @Test
    fun `arming lights every seat - first line`() {
        val transcripts = run()
        assertEquals(SEATS.size, transcripts.perClient.size)
        for (client in transcripts.perClient) {
            assertEquals(
                "LampSet|seat=${client.seat.index}|luminance=1",
                client.lines.firstOrNull(),
                "seat ${client.seat.index} was not lit at arming",
            )
        }
    }

    /** Addressing, end to end. A lamp line appears in exactly one transcript. */
    @Test
    fun `a lamp reaches its own seat and no other`() {
        val transcripts = run()
        for (client in transcripts.perClient) {
            val foreign = client.lines
                .filter { it.startsWith("LampSet") }
                .filterNot { it.startsWith("LampSet|seat=${client.seat.index}|") }
            assertEquals(emptyList(), foreign, "seat ${client.seat.index} saw another seat's lamp")
        }
    }

    /**
     * Rule 1's effect is the one that must never widen. It reaches the actor, who is an Insider,
     * and nobody else — a Resident receiving it would be an event on a screen with no ability
     * behind it.
     */
    @Test
    fun `ability fired reaches only the acting Insider`() {
        val transcripts = run()
        val receivers = transcripts.perClient
            .filter { c -> c.lines.any { it.startsWith("AbilityFired") } }
            .map { it.seat.index }
        assertEquals(listOf(1), receivers)
        assertTrue(Seat(1) in INSIDERS)
        assertTrue(
            transcripts.perClient.first { it.seat.index == 1 }
                .lines.all { !it.startsWith("AbilityFired") || it.endsWith("|cooldownStarted=true") },
            "rule 1: the effect must be identical whether the revoke landed",
        )
    }

    /**
     * The class a line was offered under is recorded, and both axes move during the round.
     *
     * Seat 1 is an Insider and is never revoked; a revoked seat is a Resident who was Live and
     * became Out. If [ClientTranscript.classesSeen] only ever held one value, the round-state axis
     * would be inert and every assertion above about "living" versus "out" would be vacuous.
     */
    @Test
    fun `the round-state axis actually moves during the round`() {
        val transcripts = run()
        val movers = transcripts.perClient.filter { it.classesSeen.size > 1 }
        assertTrue(movers.isNotEmpty(), "no client changed class; the second axis is inert")
        assertTrue(
            movers.all { m ->
                m.classesSeen.containsAll(
                    listOf(ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Resident, RoundState.Out))
                )
            },
            "expected Live then Out, got ${movers.map { it.classesSeen }}",
        )
    }

    /**
     * D-066's severity, made visible. Pre-arm events still reach the rules and still emit
     * effects — the gate is decided and not built. What stops those effects reaching a phone
     * today is that an unarmed state has no seats, so the audience is empty. **That is a
     * coincidence of state shape, not a refusal**, and this test says so rather than passing as
     * if the gate existed.
     */
    @Test
    fun `pre-arm effects reach nobody - but only because nobody is seated`() {
        val preArm = listOf(
            Event.MeetingCalled(Tick(0), Seat(3)),
            Event.SubroutineCompleted(Tick(1), Seat(3), home.someoneshome.model.MarkerId("m0")),
        )
        assertTrue(
            effectsOf(GameState.EMPTY, preArm).isNotEmpty(),
            "the rules refused a pre-arm event; D-066's gate may now exist, so update this test",
        )
        assertEquals(emptyList(), recordPerClient(GameState.EMPTY, preArm).perClient.map { it.seat })
    }
}
