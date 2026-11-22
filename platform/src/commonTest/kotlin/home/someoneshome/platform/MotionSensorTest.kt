package home.someoneshome.platform

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The motion sensor keeps the MOTION & FITNESS grant** (D-161, D-310, D-321).
 *
 * Only a phone whose row stands GRANTED is sampled; every other answer starts nothing, so no sample
 * reaches the budget and the budget holds. The grant is asked at every start, so one switched off
 * in Settings between cards is kept at the next card.
 */
class MotionSensorTest {

    private class Counting : MotionSink {
        var samples = 0
        override fun sample(x: Double, y: Double, z: Double) {
            samples++
        }
    }

    @Test
    fun `a granted phone is sampled`() {
        val permissions = NoPermissions().apply { says(DevicePermission.Motion, Grant.Granted) }
        val sensor = DrivenMotion()
        val sink = Counting()
        PermittedMotion.of(sensor, permissions).start(sink)
        assertTrue(sensor.running)
        repeat(100) { sensor.feed(0.1, 0.0, 0.0) }
        assertEquals(100, sink.samples)
    }

    @Test
    fun `a phone without the grant is never sampled`() {
        for (grant in Grant.entries - Grant.Granted) {
            val permissions = NoPermissions().apply { says(DevicePermission.Motion, grant) }
            val sensor = DrivenMotion()
            val sink = Counting()
            PermittedMotion.of(sensor, permissions).start(sink)
            assertFalse(sensor.running, "$grant: the sensor started")
            assertEquals(0, sensor.starts, "$grant: the sensor was reached")
            repeat(100) { sensor.feed(0.3, 0.3, 0.3) }
            assertEquals(0, sink.samples, "$grant: a sample reached the budget")
        }
    }

    @Test
    fun `the grant is asked at every start`() {
        val permissions = NoPermissions().apply { says(DevicePermission.Motion, Grant.Granted) }
        val sensor = DrivenMotion()
        val gated = PermittedMotion.of(sensor, permissions)
        val sink = Counting()
        gated.start(sink)
        gated.stop()
        assertFalse(sensor.running)

        // Switched off in Settings between one card and the next.
        permissions.says(DevicePermission.Motion, Grant.Refused)
        gated.start(sink)
        assertFalse(sensor.running, "a grant switched off was not kept at the next card")
        sensor.feed(0.3, 0.3, 0.3)
        assertEquals(0, sink.samples)

        permissions.says(DevicePermission.Motion, Grant.Granted)
        gated.start(sink)
        assertTrue(sensor.running, "a grant switched back on was not read")
    }

    @Test
    fun `a stopped sensor delivers nothing`() {
        val sensor = DrivenMotion()
        val sink = Counting()
        sensor.start(sink)
        sensor.feed(0.1, 0.0, 0.0)
        sensor.stop()
        sensor.feed(0.1, 0.0, 0.0)
        assertEquals(1, sink.samples)
    }

    @Test
    fun `the rate is M2's`() {
        assertEquals(100, MOTION_HZ)
    }
}
