package home.someoneshome.app

import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RequestSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **A phone's requests wait for the beat, go up as many a beat as fit in the order they were made, and
 * are sent again as the same list under one id until the house answers** (D-263, D-275). The
 * socket-level half is `UplinkFrameTest`.
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
        assertTrue(a.asked.isEmpty())
        assertTrue(b.asked.isEmpty())
        assertNotEquals(a.id, b.id, "two empty beats shared an id, so an answer could not say which it was")
        assertEquals(RequestSlot.CHARS, requests.slot().length)
    }

    @Test
    fun aSlotGoesUpEveryBeatUnderOneIdUntilItIsAnsweredAndThenTheNextOne() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(2))))
        requests.ask(Asked.Act(IntentMessage.LockVote))
        val first = requests.next()
        val again = requests.next()
        assertEquals(
            listOf(Asked.Act(IntentMessage.SelectVote(Seat(2))), Asked.Act(IntentMessage.LockVote)), first.asked,
            "the lock overtook, or was left behind, its selection",
        )
        assertEquals(first, again, "an unanswered slot went up under a new id, so the house would hear it twice")
        requests.heard("ffffffffffffffff")
        assertEquals(first, requests.next(), "an answer to another slot let the requests go")
        requests.heard(first.id)
        assertTrue(requests.next().asked.isEmpty())
        assertEquals(0, requests.count)
    }

    /**
     * **D-275: a request made while a slot is out waits behind it, and the slot goes up again
     * unchanged.** Added to a list the house may already have put, it would be dropped as a resend.
     */
    @Test
    fun aRequestMadeWhileASlotIsOutWaitsForTheNextSlotAndTheResendIsUnchanged() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.CheckIn))
        val out = requests.next()
        requests.ask(Asked.Act(IntentMessage.DeclareReadyToVote))
        assertEquals(out, requests.next(), "a request joined a slot already out, under its old id")
        requests.heard(out.id)
        val next = requests.next()
        assertNotEquals(out.id, next.id)
        assertEquals(listOf<Asked>(Asked.Act(IntentMessage.DeclareReadyToVote)), next.asked)
    }

    /**
     * **D-275, the latency: ten Subroutine entries handed over at once are all answered within three
     * beats**, not ten. One request a beat took a beat apiece.
     */
    @Test
    fun tenEntriesHandedOverAtOnceAreAllAnsweredWithinThreeBeats() {
        val requests = SlotRequests(ids())
        val entries = List(10) { Asked.Act(IntentMessage.HandOverEntry(MarkerId("HOME-0$it"), listOf(3, 1, 4, 1, 5))) }
        entries.forEach(requests::ask)
        val heard = mutableListOf<Asked>()
        var beats = 0
        while (requests.count > 0 && beats < 10) {
            val slot = requests.next()
            heard += slot.asked
            requests.heard(slot.id)
            beats++
        }
        assertEquals<List<Asked>>(entries, heard, "the entries went up out of order, or one was lost or sent twice")
        assertTrue(beats <= 3, "ten entries took $beats beats")
    }

    /** **A request that does not fit beside the ones before it waits, and nothing behind it jumps it.** */
    @Test
    fun aRequestThatDoesNotFitWaitsAndNothingBehindItGoesFirst() {
        val requests = SlotRequests(ids())
        val big = Asked.Act(IntentMessage.HandOverEntry(MarkerId("HOME-01"), List(60) { 50 + it % 40 }))
        requests.ask(big)
        requests.ask(big)
        requests.ask(Asked.Act(IntentMessage.CheckIn))
        val first = requests.next()
        assertEquals(listOf<Asked>(big), first.asked, "two that do not fit together went together, or the small one jumped")
        requests.heard(first.id)
        assertEquals<List<Asked>>(listOf(big, Asked.Act(IntentMessage.CheckIn)), requests.next().asked)
    }

    @Test
    fun aSelectionNotYetSentIsReplacedByTheNextAndOneSentIsNot() {
        val requests = SlotRequests(ids())
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(1))))
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(2))))
        requests.ask(Asked.Act(IntentMessage.SelectVote(Seat(3))))
        assertEquals(1, requests.count, "selections the finger had already moved past were kept")
        val sent = requests.next()
        assertEquals(listOf<Asked>(Asked.Act(IntentMessage.SelectVote(Seat(3)))), sent.asked)
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
        val slot = requests.next()
        requests.heard(slot.id)
        assertEquals(listOf(Asked.Act(IntentMessage.ArmRevoke), Asked.Fire, Asked.Act(IntentMessage.FireIsolate(MarkerId("HOME-03")))), slot.asked)
        assertTrue(requests.next().asked.isEmpty())
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
        assertTrue(requests.next().asked.isEmpty())
    }

    @Test
    fun aFreshIdIsSixteenRandomLowercaseHexCharacters() {
        val a = freshRequestId()
        val b = freshRequestId()
        assertTrue(RequestSlot.isId(a) && RequestSlot.isId(b), "$a / $b")
        assertNotEquals(a, b)
    }
}
