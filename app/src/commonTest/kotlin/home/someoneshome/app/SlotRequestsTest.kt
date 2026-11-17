package home.someoneshome.app

import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A phone's requests wait for the beat, go up one a beat in the order they were made, and are sent
 * again under one id until the house answers** (D-263). The socket-level half is `UplinkFrameTest`.
 */
class SlotRequestsTest {

    private var n = 0
    private fun ids() = { (n++).toString(16).padStart(RequestSlot.ID_CHARS, '0') }

    private fun SlotRequests.next(): RequestSlot.Read = RequestSlot.read(slot())!!

    @Test
    fun anEmptyQueueSendsAnEmptySlotUnderAFreshIdEveryBeat() {
        val requests = SlotRequests(ids())
        val a = requests.next()
        val b = requests.next()
        assertNull(a.asked)
        assertNull(b.asked)
        assertNotEquals(a.id, b.id, "two empty beats shared an id, so an answer could not say which it was")
        assertEquals(RequestSlot.CHARS, requests.slot().length)
    }

    @Test
    fun aRequestGoesUpEveryBeatUnderOneIdUntilItIsAnsweredAndThenTheNextOne() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(2))))
        requests.ask(Asked.Act(IntentMessage.LockVote))
        val first = requests.next()
        val again = requests.next()
        assertEquals(Asked.Act(IntentMessage.SelectVote(Seat(2))), first.asked)
        assertEquals(first, again, "an unanswered request went up under a new id, so the house would hear it twice")
        requests.heard("ffffffffffffffff")
        assertEquals(first, requests.next(), "an answer to another slot let the request go")
        requests.heard(first.id)
        val second = requests.next()
        assertEquals(Asked.Act(IntentMessage.LockVote), second.asked, "the lock overtook, or was lost behind, its selection")
        requests.heard(second.id)
        assertNull(requests.next().asked)
        assertEquals(0, requests.count)
    }

    @Test
    fun aSelectionNotYetSentIsReplacedByTheNextAndOneSentIsNot() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(1))))
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(2))))
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(3))))
        assertEquals(1, requests.count, "selections the finger had already moved past were kept")
        val sent = requests.next()
        assertEquals(Asked.Act(IntentMessage.SelectVote(Seat(3))), sent.asked)
        // Sent, so it may have been heard: the next selection queues behind it.
        requests.ask(Asked.Act(IntentMessage.SelectVote(null)))
        assertEquals(2, requests.count)
        // And nothing but a selection is ever replaced.
        requests.ask(Asked.Act(IntentMessage.LockVote))
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(4))))
        assertEquals(4, requests.count, "a lock was replaced, or a selection jumped a lock")
    }

    @Test
    fun theEgressTileAndEveryControlRideTheSameQueue() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.ArmRevoke))
        requests.ask(Asked.Fire)
        requests.ask(Asked.Act(IntentMessage.FireIsolate(MarkerId("HOME-03"))))
        val seen = mutableListOf<Asked?>()
        repeat(4) {
            val slot = requests.next()
            seen += slot.asked
            requests.heard(slot.id)
        }
        assertEquals(listOf(Asked.Act(IntentMessage.ArmRevoke), Asked.Fire, Asked.Act(IntentMessage.FireIsolate(MarkerId("HOME-03"))), null), seen)
    }

    @Test
    fun aRequestTooLongIsNotQueuedAndTheLogSaysSo() {
        val log = mutableListOf<String>()
        val requests = SlotRequests(ids(), onEvent = { log += it })
        requests.ask(Asked.Act(IntentMessage.HandOverEntry(MarkerId("HOME-01"), List(200) { it })))
        assertEquals(0, requests.count)
        assertTrue(log.single().contains("TOO LONG"), "the phone's log did not say: $log")
    }

    @Test
    fun clearingForgetsEverythingWaiting() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.CheckIn))
        requests.clear()
        assertNull(requests.next().asked)
    }

    @Test
    fun aFreshIdIsSixteenRandomLowercaseHexCharacters() {
        val a = freshRequestId()
        val b = freshRequestId()
        assertTrue(RequestSlot.isId(a) && RequestSlot.isId(b), "$a / $b")
        assertNotEquals(a, b)
    }
}
