package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.platform.transport.AckLedger.Standing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AckLedgerTest {

    private val seats = List(3) { Seat(it) }.toSet()

    @Test
    fun everyoneAckedCommitsAtLastAckPlusLead() {
        val ledger = AckLedger()
        ledger.propose(1, seats, nowMillis = 1_000)
        ledger.ack(1, Seat(0), nowMillis = 1_050)
        ledger.ack(1, Seat(1), nowMillis = 1_120)
        assertIs<Standing.Waiting>(ledger.standing(1, nowMillis = 1_130))
        ledger.ack(1, Seat(2), nowMillis = 1_200)
        val ready = assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 1_210))
        assertEquals(1_200 + COMMIT_LEAD_MILLIS, ready.commitAtMillis, "commit is last_ack + 500 (D5)")
        assertEquals(emptySet(), ready.missing)
    }

    @Test
    fun aClientThatNeverAcksDoesNotStallTheCaller() {
        val ledger = AckLedger()
        ledger.propose(1, seats, nowMillis = 1_000)
        ledger.ack(1, Seat(0), nowMillis = 1_100)
        ledger.ack(1, Seat(1), nowMillis = 1_100)
        assertIs<Standing.Waiting>(ledger.standing(1, nowMillis = 2_999))
        val ready = assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 3_000))
        assertEquals(setOf(Seat(2)), ready.missing, "the commit names who it proceeded without")
        assertEquals(3_000 + COMMIT_LEAD_MILLIS, ready.commitAtMillis)
    }

    @Test
    fun aFixedCommitNeverMoves() {
        // Once given out, the commit time is a fact phones may already have scheduled against.
        val ledger = AckLedger()
        ledger.propose(1, seats, nowMillis = 1_000)
        val first = assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 3_500))
        ledger.ack(1, Seat(0), nowMillis = 3_600)
        val second = assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 4_000))
        assertEquals(first, second, "polling again or a late ack moved a commit phones already heard")
    }

    @Test
    fun lateAndUnexpectedAcksAreRecordedNotDropped() {
        val ledger = AckLedger()
        ledger.propose(1, setOf(Seat(0), Seat(1)), nowMillis = 1_000)
        ledger.ack(1, Seat(9), nowMillis = 1_100) // never expected
        assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 3_100))
        ledger.ack(1, Seat(1), nowMillis = 3_200) // after the commit was fixed
        assertEquals(setOf(Seat(9), Seat(1)), ledger.lateAcks(1))
    }

    @Test
    fun proposalsAreIndependent() {
        val ledger = AckLedger()
        ledger.propose(1, seats, nowMillis = 1_000)
        ledger.propose(2, seats, nowMillis = 1_500)
        seats.forEach { ledger.ack(1, it, nowMillis = 1_600) }
        assertIs<Standing.Ready>(ledger.standing(1, nowMillis = 1_700))
        assertIs<Standing.Waiting>(ledger.standing(2, nowMillis = 1_700))
    }
}
