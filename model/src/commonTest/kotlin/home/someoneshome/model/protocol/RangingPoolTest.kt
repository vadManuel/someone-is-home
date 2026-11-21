package home.someoneshome.model.protocol

import home.someoneshome.model.RadioCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The phone's UWB pool, against a fake of Nearby Interaction** (D-303, the U6 plan's unit 4).
 *
 * The real sessions are `NISession`s and run only on a phone; this is everything around them that
 * does not touch iOS: the capacity, the spares, the plan applied to them, the readings table, the
 * suspension and the faults. Runs on the JVM (and on the iOS Simulator as Kotlin, which says nothing
 * about Nearby Interaction).
 */
class RangingPoolTest {

    /** Nearby Interaction, faked: what was opened, run, run again and closed, and a token per open. */
    private class FakeSessions : RangingSessions {
        val open = mutableMapOf<Int, Int>()
        val runs = mutableListOf<Pair<Int, RangingToken>>()
        val reruns = mutableListOf<Int>()
        val closes = mutableListOf<Int>()
        var opens = 0
        var peakOpen = 0

        /** What the next open hands back instead of a good token, once. */
        var nextToken: ByteArray? = null
        var refuseToken = false

        override fun open(slot: Int, generation: Int): ByteArray? {
            check(slot !in open) { "slot $slot opened twice without a close" }
            open[slot] = generation
            opens++
            peakOpen = maxOf(peakOpen, open.size)
            nextToken?.let { nextToken = null; return it }
            if (refuseToken) return null
            return ByteArray(312 + opens % 34) { (opens * 31 + it).toByte() }
        }

        override fun run(slot: Int, peer: RangingToken): Boolean {
            check(slot in open) { "slot $slot run without a session" }
            runs += slot to peer
            return true
        }

        override fun rerun(slot: Int) {
            reruns += slot
        }

        override fun close(slot: Int) {
            closes += slot
            open.remove(slot)
        }

        fun generationOf(slot: Int): Int = open.getValue(slot)
    }

    private var keys = 100L
    private val sessions = FakeSessions()
    private val pool = RangingPool(sessions, nextKey = { keys++ })

    private fun peer(n: Int) = RangingToken.of(ByteArray(320) { (n + it).toByte() })

    private fun startOn(spare: RangingSpare, handle: Long, peerN: Int): Int {
        val before = sessions.runs.size
        pool.start(RangingHandle(handle), spare.key, peer(peerN))
        assertEquals(before + 1, sessions.runs.size, "the start did not run a session")
        return sessions.runs.last().first
    }

    @Test
    fun aRoundOpensTheStartingCapacityAsSparesEachWithItsOwnKeyAndToken() {
        pool.open()
        assertEquals(RangingWire.STARTING_CAPACITY, pool.capacity())
        val spares = pool.spares()
        assertEquals(RangingWire.STARTING_CAPACITY, spares.size, "the pool held $spares")
        assertEquals(spares.size, spares.map { it.key }.toSet().size, "two spares share a key")
        assertEquals(spares.size, spares.map { it.token }.toSet().size, "two spares share a token")
        assertEquals(RangingWire.STARTING_CAPACITY, sessions.open.size)
        assertTrue(pool.drain().isEmpty(), "an idle pool listed a pair")
    }

    @Test
    fun aStartRunsTheNamedSpareAgainstThePeerAndItsReadingsAreTheNearestOfTheSecond() {
        pool.open()
        val spare = pool.spares()[1]
        val slot = startOn(spare, 7L, 1)
        assertEquals(peer(1), sessions.runs.single().second, "the session ran against another token")
        assertEquals(2, pool.spares().size, "a ranging session is still offered")
        assertTrue(spare !in pool.spares(), "the session being ranged is still offered")

        // Listed with no reading before any came.
        assertEquals(listOf(RangingReading(RangingHandle(7L), null, 0)), pool.drain())

        val g = sessions.generationOf(slot)
        pool.read(slot, g, 0.42f)
        pool.read(slot, g, 0.031f)
        pool.read(slot, g, -0.083f)
        pool.read(slot, g, 0.10f)
        pool.read(slot, g, Float.NaN)
        assertEquals(listOf(RangingReading(RangingHandle(7L), -8, 4)), pool.drain(), "the nearest of the second, in cm, and the count")
        assertEquals(listOf(RangingReading(RangingHandle(7L), null, 0)), pool.drain(), "a drained second was read twice")
    }

    @Test
    fun aStartForAPairAlreadyHeldOrASpareThatIsNotIdleIsNothing() {
        pool.open()
        val spares = pool.spares()
        startOn(spares[0], 7L, 1)
        // The house resends a start until it sees the pair listed (D-300).
        pool.start(RangingHandle(7L), spares[1].key, peer(1))
        pool.start(RangingHandle(8L), spares[0].key, peer(2))
        pool.start(RangingHandle(9L), SpareKey(-1L), peer(3))
        assertEquals(1, sessions.runs.size, "a held pair, a busy spare or an unknown key ran a session: ${sessions.runs}")
    }

    @Test
    fun aStopRetiresTheSessionAndOffersAFreshOneWithANewKeyAndToken() {
        pool.open()
        val spare = pool.spares()[0]
        val slot = startOn(spare, 7L, 1)
        val old = sessions.generationOf(slot)
        pool.stop(RangingHandle(7L))
        assertTrue(slot in sessions.closes, "the stopped session was not closed")
        val after = pool.spares()
        assertEquals(RangingWire.STARTING_CAPACITY, after.size, "the stop did not open a fresh spare")
        assertTrue(after.none { it.key == spare.key || it.token == spare.token }, "a used session was offered again")
        // Whatever the retired session says now is nothing.
        pool.read(slot, old, 0.05f)
        assertTrue(pool.drain().isEmpty(), "a retired session's reading was listed")
        // A stop for a pair not held is nothing.
        val opens = sessions.opens
        pool.stop(RangingHandle(99L))
        assertEquals(opens, sessions.opens)
    }

    @Test
    fun aSpareTheHouseDealsAgainAfterItsFirstPeerNeverStartedRunsOnceWithTheSecond() {
        // D-302 decision 4: the house dealt this spare to a pair this phone never took up (the
        // start never came), handed it back, and deals it to another peer. On this phone it is a
        // session that never ran: its one run is against the second peer.
        pool.open()
        val spare = pool.spares()[0]
        assertTrue(spare in pool.spares())
        val slot = startOn(spare, 12L, 2)
        assertEquals(slot to peer(2), sessions.runs.single())
        // And a session that has run is never run again with another peer: once stopped, it is gone.
        pool.stop(RangingHandle(12L))
        pool.start(RangingHandle(13L), spare.key, peer(3))
        assertEquals(1, sessions.runs.size, "a session that had run was run again against another peer")
    }

    @Test
    fun theLimitLowersTheCapacityByOneAndThePoolHoldsNoMoreThanIt() {
        pool.open()
        val spares = pool.spares()
        val slot = startOn(spares[0], 7L, 1)
        pool.ended(slot, sessions.generationOf(slot), SessionEnd.LimitReached)
        assertEquals(RangingWire.STARTING_CAPACITY - 1, pool.capacity())
        assertEquals(RangingWire.STARTING_CAPACITY - 1, sessions.open.size, "the pool holds more sessions than its capacity")
        assertTrue(pool.drain().isEmpty(), "the refused pair is still listed")
        assertEquals(RadioCause.Device, pool.faultBegan())
        assertNull(pool.faultBegan(), "one onset was handed over twice")

        // Down to nothing, and never below.
        repeat(5) {
            val left = pool.spares().firstOrNull() ?: return@repeat
            val s = startOn(left, 20L + it, 3)
            pool.ended(s, sessions.generationOf(s), SessionEnd.LimitReached)
        }
        assertEquals(0, pool.capacity())
        assertTrue(pool.spares().isEmpty())
        // The next round starts at the starting capacity again.
        pool.open()
        assertEquals(RangingWire.STARTING_CAPACITY, pool.capacity())
        assertEquals(RangingWire.STARTING_CAPACITY, pool.spares().size)
    }

    @Test
    fun thePoolNeverHoldsMoreSessionsThanItsCapacityThroughAPlanAppliedStopsFirst() {
        pool.open()
        var handle = 1L
        val running = mutableListOf<Long>()
        repeat(40) { beat ->
            // The plan: stop the oldest pair when full, then start on every idle spare.
            if (running.size == pool.capacity()) {
                pool.stop(RangingHandle(running.removeAt(0)))
            }
            for (spare in pool.spares()) {
                pool.start(RangingHandle(handle), spare.key, peer(beat))
                running += handle++
            }
            pool.drain()
        }
        assertEquals(RangingWire.STARTING_CAPACITY, sessions.peakOpen, "the pool held ${sessions.peakOpen} sessions at once")
    }

    @Test
    fun aRefusalIsThePersonsFaultAndTheRoundRangesNothingUntilTheNext() {
        pool.open()
        val slot = startOn(pool.spares()[0], 7L, 1)
        val other = pool.spares()[0]
        pool.ended(slot, sessions.generationOf(slot), SessionEnd.NotAllowed)
        assertEquals(0, pool.capacity())
        assertTrue(pool.spares().isEmpty() && pool.drain().isEmpty(), "a refused pool still offers ${pool.spares().size} spares")
        assertTrue(sessions.open.isEmpty(), "a refused pool still holds sessions: ${sessions.open}")
        // **NEARBY DEVICES withdrawn is a permission switched off** (D-310).
        assertEquals(RadioCause.Permission, pool.faultBegan())
        // The other sessions' own refusals come late, and are one onset.
        pool.start(RangingHandle(8L), other.key, peer(2))
        assertEquals(1, sessions.runs.size, "a refused pool ran a session")
        pool.open()
        assertEquals(RangingWire.STARTING_CAPACITY, pool.spares().size)
    }

    @Test
    fun aRangingSessionThatFailsIsTheDevicesFaultAndAnIdleOneIsReplacedQuietly() {
        pool.open()
        val slot = startOn(pool.spares()[0], 7L, 1)
        repeat(3) { pool.drain() }
        // An idle spare ended by iOS: replaced, nobody told.
        val idleAt = sessions.open.entries.first { (s, _) -> s != slot }.key
        pool.ended(idleAt, sessions.generationOf(idleAt), SessionEnd.Failed)
        assertNull(pool.faultBegan(), "an idle spare's end was reported as a fault")
        assertEquals(RangingWire.STARTING_CAPACITY, sessions.open.size, "the idle spare was not replaced")
        // A ranging session ended: the device's fault, and a fresh spare in its place.
        pool.ended(slot, sessions.generationOf(slot), SessionEnd.Failed)
        assertEquals(RadioCause.Device, pool.faultBegan())
        assertEquals(RangingWire.STARTING_CAPACITY, pool.spares().size)
        assertTrue(pool.drain().isEmpty(), "the failed pair is still listed")
    }

    @Test
    fun aSuspensionIsRunAgainOnTheSamePeerWithNoNewToken() {
        pool.open()
        val slot = startOn(pool.spares()[0], 7L, 1)
        val g = sessions.generationOf(slot)
        val spares = pool.spares()
        pool.suspended(slot, g)
        assertEquals(listOf(RangingReading(RangingHandle(7L), null, 0)), pool.drain(), "a suspended pair is not listed")
        pool.resumed(slot, g)
        assertEquals(listOf(slot), sessions.reruns, "the suspension's end did not run the session again")
        assertEquals(spares, pool.spares(), "a suspension changed what is offered")
        assertNull(pool.faultBegan(), "a suspension was reported as a fault (leaving the foreground already is, D-229)")
        // A timeout runs it again; a peer that ended its own session waits for the house's stop.
        pool.lost(slot, g, timedOut = true)
        pool.lost(slot, g, timedOut = false)
        assertEquals(listOf(slot, slot), sessions.reruns)
    }

    @Test
    fun aCallbackFromARetiredSessionIsNothing() {
        pool.open()
        val slot = startOn(pool.spares()[0], 7L, 1)
        val old = sessions.generationOf(slot)
        pool.stop(RangingHandle(7L))
        // The fresh session may land in the same slot; the old one's word is still nothing.
        pool.ended(slot, old, SessionEnd.LimitReached)
        pool.resumed(slot, old)
        assertEquals(RangingWire.STARTING_CAPACITY, pool.capacity(), "a retired session's end lowered the capacity")
        assertNull(pool.faultBegan())
        assertTrue(sessions.reruns.isEmpty())
    }

    @Test
    fun faultsInsideTheSettleAreOneOnsetAndAPersonsRefusalIsNeverFolded() {
        pool.open()
        val a = startOn(pool.spares()[0], 1L, 1)
        val b = startOn(pool.spares()[0], 2L, 2)
        pool.ended(a, sessions.generationOf(a), SessionEnd.Failed)
        pool.ended(b, sessions.generationOf(b), SessionEnd.Failed)
        assertEquals(RadioCause.Device, pool.faultBegan())
        assertNull(pool.faultBegan(), "two failures in one beat were two onsets")

        // Inside the settle still: another failure is the same onset.
        pool.drain()
        val c = startOn(pool.spares()[0], 3L, 3)
        pool.ended(c, sessions.generationOf(c), SessionEnd.Failed)
        assertNull(pool.faultBegan(), "a failure inside the settle was a new onset")

        // A person's refusal inside a device's onset is its own.
        val d = startOn(pool.spares()[0], 4L, 4)
        pool.ended(d, sessions.generationOf(d), SessionEnd.NotAllowed)
        assertEquals(RadioCause.Permission, pool.faultBegan(), "a refusal was folded into a device fault")

        // Quiet for the settle, then a new onset.
        pool.open()
        repeat(RangingPool.SETTLE_DRAINS.toInt() + 1) { pool.drain() }
        val e = startOn(pool.spares()[0], 5L, 5)
        pool.ended(e, sessions.generationOf(e), SessionEnd.Failed)
        assertEquals(RadioCause.Device, pool.faultBegan())
    }

    @Test
    fun aSlotWhoseSessionsEndYoungIsLeftEmptyAndTheOpeningsAreBounded() {
        pool.open()
        // iOS ending every fresh idle session at once: the pool must not open sessions for ever.
        repeat(200) {
            val slot = sessions.open.keys.firstOrNull() ?: return@repeat
            pool.ended(slot, sessions.generationOf(slot), SessionEnd.Failed)
        }
        assertTrue(sessions.opens <= RangingWire.STARTING_CAPACITY + RangingWire.MAX_PEERS * RangingPool.MOST_FAST_FAILS,
            "a pool whose sessions all end young opened ${sessions.opens} sessions")
    }

    @Test
    fun aTokenThatDoesNotFitIsTheDevicesFaultAndNeverOffered() {
        sessions.nextToken = ByteArray(RangingWire.TOKEN_BYTES + 1)
        pool.open()
        assertEquals(RadioCause.Device, pool.faultBegan())
        val spares = pool.spares()
        assertEquals(RangingWire.STARTING_CAPACITY, spares.size, "the next slot did not take the oversized one's place")
        assertTrue(spares.all { it.token.size <= RangingWire.TOKEN_BYTES })
        assertNotEquals(0, sessions.closes.size, "the session with the oversized token was not closed")
    }

    @Test
    fun aDistanceIsClampedToTheWireAndTheCountToItsMost() {
        pool.open()
        val slot = startOn(pool.spares()[0], 7L, 1)
        val g = sessions.generationOf(slot)
        pool.read(slot, g, 1_000f)
        pool.read(slot, g, Float.POSITIVE_INFINITY)
        assertEquals(listOf(RangingReading(RangingHandle(7L), Short.MAX_VALUE.toInt(), 1)), pool.drain())
        repeat(400) { pool.read(slot, g, 0.5f) }
        assertEquals(listOf(RangingReading(RangingHandle(7L), 50, RangingWire.MOST_READINGS)), pool.drain())
    }
}
