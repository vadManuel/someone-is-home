package home.someoneshome.platform.transport

import home.someoneshome.platform.monotonicNanos
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Advertise and browse in one process, on the simulator's network stack.
 *
 * NSNetService delivers everything through the run loop, so the test pumps the main loop by hand
 * until the callback lands — there is no other thread to wait on. The instance name is unique
 * per run because the browser sees the whole LAN: a phone in the room advertising the real
 * service must not be able to satisfy, or fail, this assertion.
 */
class HostDiscoveryTest {

    @Test
    fun anAdvertisedHostIsFoundAndResolvedToItsPort() {
        val instance = "loopback-proof-${monotonicNanos()}"
        val events = mutableListOf<String>()
        val advertiser = HostAdvertiser(instance, 47811, onEvent = { events.add(it) })
        var found: Pair<String, Int>? = null
        val browser = HostBrowser(
            onFound = { name, address, port ->
                if (name == instance && found == null) found = address to port
            },
            onEvent = { events.add(it) },
        )
        advertiser.start()
        browser.start()
        try {
            val deadline = monotonicNanos() + 20_000_000_000
            while (found == null && monotonicNanos() < deadline) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.25))
            }
        } finally {
            browser.stop()
            advertiser.stop()
        }
        val hit = assertNotNull(found, "the advertised service was not found within 20 s; events: $events")
        assertEquals(47811, hit.second, "the browser resolved a different port than was advertised")
        assertTrue(
            hit.first.matches(Regex("""\d+\.\d+\.\d+\.\d+""")),
            "the resolved address is not a dotted IPv4: ${hit.first}",
        )
    }
}
