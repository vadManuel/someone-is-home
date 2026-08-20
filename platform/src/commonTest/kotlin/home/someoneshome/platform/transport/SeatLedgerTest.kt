package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.platform.transport.SeatLedger.Admission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SeatLedgerTest {

    private fun ledger(seats: Int = 3): SeatLedger {
        var next = 0
        return SeatLedger(List(seats) { Seat(it) }, mint = { SeatToken("tk-${next++}") })
    }

    @Test
    fun everyJoinGetsADistinctSeatAndADistinctToken() {
        val ledger = ledger(seats = 3)
        val seated = List(3) { assertIs<Admission.Seated>(ledger.join()) }
        assertEquals(3, seated.map { it.seat }.toSet().size, "a seat was issued twice")
        assertEquals(3, seated.map { it.token }.toSet().size, "a token was issued twice")
    }

    @Test
    fun aFullLobbyRefusesWithNoFreeSeat() {
        val ledger = ledger(seats = 1)
        assertIs<Admission.Seated>(ledger.join())
        val refused = assertIs<Admission.Refused>(ledger.join())
        assertEquals(TransportRefusal.NoFreeSeat, refused.reason)
    }

    @Test
    fun resumeReturnsThatSeatNotASeat() {
        // The property the type exists for: G1, "a lobby code gets you A seat, never THAT seat" —
        // inverted for the token, which gets you exactly THAT seat, however often, whatever else
        // happened in between.
        val ledger = ledger(seats = 3)
        val first = assertIs<Admission.Seated>(ledger.join())
        assertIs<Admission.Seated>(ledger.join())
        ledger.lock()
        repeat(3) {
            val back = assertIs<Admission.Seated>(ledger.resume(first.token))
            assertEquals(first.seat, back.seat, "resume handed back a different seat")
            assertEquals(first.token, back.token)
        }
    }

    @Test
    fun anUnknownTokenIsRefusedNotReseated() {
        val ledger = ledger()
        ledger.join()
        val refused = assertIs<Admission.Refused>(ledger.resume(SeatToken("forged")))
        assertEquals(TransportRefusal.UnknownToken, refused.reason)
    }

    @Test
    fun lockEndsJoiningButNeverResuming() {
        val ledger = ledger(seats = 2)
        val seated = assertIs<Admission.Seated>(ledger.join())
        ledger.lock()
        val refused = assertIs<Admission.Refused>(ledger.join())
        assertEquals(TransportRefusal.RoundLocked, refused.reason)
        assertIs<Admission.Seated>(ledger.resume(seated.token))
    }

    @Test
    fun aDisconnectFreesNothing() {
        // A dropped socket is not an operation on the ledger at all: the seat waits for its
        // token. With every seat held, a stranger joining pre-arm is refused NoFreeSeat rather
        // than being quietly handed a seat whose owner is standing in the hallway rebooting.
        val ledger = ledger(seats = 1)
        assertIs<Admission.Seated>(ledger.join())
        // ...the holder's phone dies here, and no code runs...
        val refused = assertIs<Admission.Refused>(ledger.join())
        assertEquals(TransportRefusal.NoFreeSeat, refused.reason)
    }

    @Test
    fun releaseReturnsTheSeatPreArmAndThrowsAfterLock() {
        val ledger = ledger(seats = 1)
        val seated = assertIs<Admission.Seated>(ledger.join())
        ledger.release(seated.token)
        val again = assertIs<Admission.Seated>(ledger.join())
        assertEquals(seated.seat, again.seat, "the released seat did not return to the pool")
        assertTrue(again.token != seated.token, "a released token must never work again")

        ledger.lock()
        assertFailsWith<IllegalArgumentException> { ledger.release(again.token) }
        assertIs<Admission.Seated>(ledger.resume(again.token))
    }

    @Test
    fun aReleasedTokenIsAStranger() {
        val ledger = ledger(seats = 2)
        val seated = assertIs<Admission.Seated>(ledger.join())
        ledger.release(seated.token)
        val refused = assertIs<Admission.Refused>(ledger.resume(seated.token))
        assertEquals(TransportRefusal.UnknownToken, refused.reason)
    }

    @Test
    fun aCollidingMintFailsLoud() {
        val ledger = SeatLedger(List(2) { Seat(it) }, mint = { SeatToken("same") })
        assertIs<Admission.Seated>(ledger.join())
        assertFailsWith<IllegalArgumentException> { ledger.join() }
    }
}
