package home.someoneshome.model.protocol

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **GUARD (rule 7, D-303): a UWB reading costs nothing to keep.**
 *
 * Nearby Interaction hands each session a reading every 150 ms, so a phone ranging its capacity of
 * pairs takes about twenty a second for the whole round. The pool keeps the nearest and a count per
 * slot in two fixed arrays; a reading that allocated (a boxed distance, a list of the second's
 * readings, a `RangingReading` per reading) would be the radio thread rule 7 names. The second's
 * list is built once a beat, by [RangingPool.drain], and is measured here too.
 *
 * Measured on the JVM by the thread's own counter, as [UploadAllocationTest] measures.
 */
class RangingPoolAllocationTest {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Volatile
    private var sink: Any? = null

    private class Quiet : RangingSessions {
        private var n = 0
        override fun open(slot: Int, generation: Int): ByteArray = ByteArray(320) { (n++ + it).toByte() }
        override fun run(slot: Int, peer: RangingToken): Boolean = true
        override fun rerun(slot: Int) = Unit
        override fun close(slot: Int) = Unit
    }

    private fun perCall(times: Int, block: () -> Unit): Long {
        repeat(times) { block() }
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        repeat(times) { block() }
        return (threads.getThreadAllocatedBytes(id) - before) / times
    }

    private fun ranging(): RangingPool {
        var key = 1L
        val pool = RangingPool(Quiet(), nextKey = { key++ })
        pool.open()
        pool.spares().forEachIndexed { i, spare ->
            pool.start(RangingHandle(10L + i), spare.key, RangingToken.of(ByteArray(330) { it.toByte() }))
        }
        return pool
    }

    @Test
    fun aReadingAllocatesNothing() {
        val pool = ranging()
        var metres = 0.1f
        // The slots the three pairs were started on are 0, 1 and 2, generation 1 each.
        val measured = perCall(200_000) {
            metres = if (metres > 3f) 0.1f else metres + 0.013f
            pool.read(0, 1, metres)
            pool.read(1, 1, -metres)
            pool.read(2, 1, metres * 2)
        }
        assertTrue(measured == 0L, "three UWB readings allocate $measured bytes, over their ceiling of 0 (rule 7, D-303)")
        sink = pool
    }

    @Test
    fun aBeatsDrainAllocatesItsListAndLittleElse() {
        val pool = ranging()
        val measured = perCall(20_000) {
            pool.read(0, 1, 0.2f)
            sink = pool.drain()
        }
        assertTrue(measured <= 256, "draining three pairs allocates $measured bytes a beat, over its ceiling of 256 (rule 7, D-303)")
    }
}
