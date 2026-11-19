package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The bench's UWB, against the spike (D-300)**: ±3 cm on a clear line, 3 to 5 m long and never
 * short through a body, 6.7 readings a second, and a pair that reads only when both phones run it.
 */
class SimulatedRangingTest {

    private val h = RangingHandle("0000000000000001")

    /** Two radios in one air, [metres] apart, paired by hand as the house's plan would pair them. */
    private fun paired(metres: Double, startBoth: Boolean = true): Triple<SimulatedAir, SimulatedRanging, SimulatedRanging> {
        val air = SimulatedAir()
        air.place(Seat(0), SimulatedAir.Spot(0.0, 0.0))
        air.place(Seat(1), SimulatedAir.Spot(metres, 0.0))
        val a = air.rangingFor(Seat(0))
        val b = air.rangingFor(Seat(1))
        a.open()
        b.open()
        val sa = a.spares().first()
        val sb = b.spares().first()
        a.start(h, sa.key, sb.token)
        if (startBoth) b.start(h, sb.key, sa.token)
        return Triple(air, a, b)
    }

    @Test
    fun aClearLineReadsWithinThreeCentimetresAtSixOrSevenASecond() {
        for (metres in listOf(0.02, 0.15, 0.30, 1.0, 4.0)) {
            val (_, a, b) = paired(metres)
            val counts = mutableListOf<Int>()
            repeat(30) {
                for (r in listOf(a, b)) {
                    val reading = r.drain().single()
                    val cm = reading.nearestCm!!
                    assertTrue(kotlin.math.abs(cm - (metres * 100).toInt()) <= 3, "at $metres m a clear line read $cm cm")
                    if (r === a) counts += reading.readings
                }
            }
            assertTrue(counts.all { it in 6..7 }, "readings a second: $counts")
            assertEquals(200, counts.sum(), "30 s at 150 ms is 200 readings, read ${counts.sum()}")
        }
    }

    @Test
    fun aBodyInTheWayReadsThreeToFiveMetresLongAndNeverShort() {
        val (air, a, _) = paired(1.0)
        air.bodyBetween(Seat(0), Seat(1))
        val read = List(200) { a.drain().single().nearestCm!! }
        assertTrue(read.all { it in 100 + 300..100 + 500 }, "through a body at 1 m: ${read.min()}..${read.max()} cm")
        air.bodyBetween(Seat(0), Seat(1), on = false)
        assertTrue(a.drain().single().nearestCm!! in 97..103, "the body stepped away and the reading stayed long")
    }

    @Test
    fun aPairReadsOnlyWhenBothPhonesRunItAndAStopOpensAFreshSpare() {
        val (_, a, b) = paired(0.5, startBoth = false)
        assertEquals(null, a.drain().single().nearestCm, "one phone alone read a distance")
        assertEquals(emptyList(), b.drain(), "a phone that never started the pair listed it")
        val before = a.spares().map { it.key }
        a.stop(h)
        assertEquals(emptyList(), a.ranging)
        assertEquals(RangingWire.STARTING_CAPACITY, a.spares().size, "a stop did not open a session in its place")
        assertTrue(a.spares().map { it.key }.containsAll(before), "a stop took an idle session with it")
        a.refused()
        assertEquals(RangingWire.STARTING_CAPACITY - 1, a.capacity(), "iOS refusing a session did not lower the capacity")
        assertEquals(RangingWire.STARTING_CAPACITY - 1, a.spares().size)
    }
}
