package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Every phone reads the house's clock, measured rather than assumed, on one schedule** (C1,
 * ARCH D5).
 *
 * A real [TransportHost] answering probes with its own monotonic milliseconds, and real
 * [TransportClient]s over loopback whose own clocks are deliberately wrong. Phones in one process
 * share one monotonic base, so without the skew a client that never measured anything would read
 * the house's clock correctly by accident — the skew is what makes this a test of the estimator.
 */
class ClockProbeLoopbackTest {

    private fun hostMillis(): Long = monotonicNanos() / 1_000_000

    private class House(val host: TransportHost, val port: Int, val seated: Channel<Seat>, val upward: Channel<TransportFrame>)

    private suspend fun house(seats: Int): House {
        var minted = 0
        val seated = Channel<Seat>(Channel.UNLIMITED)
        val upward = Channel<TransportFrame>(Channel.UNLIMITED)
        val host = TransportHost(
            SeatLedger(List(seats) { Seat(it) }, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = ::hostMillis,
            onFrame = { _, frame -> upward.trySend(frame) },
            onSeated = { seated.trySend(it) },
        )
        return House(host, host.start(0), seated, upward)
    }

    /**
     * Two phones, one 3 s ahead of the house and one 2 s behind: within a second of seating, each
     * reads the house's clock to within 20 ms. And nothing of the clock reaches either app.
     */
    @Test
    fun skewedPhonesReadTheHousesClockWithinTwentyMilliseconds() = runBlocking {
        val house = house(seats = 2)
        val jobs = mutableListOf<Job>()
        try {
            withTimeout(30_000) {
                val downward = Channel<TransportFrame>(Channel.UNLIMITED)
                val skews = listOf(3_000L, -2_000L)
                val clients = skews.map { skew ->
                    TransportClient(
                        ClientSession(), "127.0.0.1", house.port,
                        nowMillis = { hostMillis() + skew },
                        onFrame = { downward.trySend(it) },
                    )
                }
                clients.forEach { c -> jobs += launch { c.run() } }
                repeat(clients.size) { house.seated.receive() }
                val seatedAt = hostMillis()

                for ((i, client) in clients.withIndex()) {
                    while (client.hostNowMillis() == null) {
                        check(hostMillis() - seatedAt < 1_000) { "phone $i (skew ${skews[i]}) had no fix a second after seating" }
                        delay(5)
                    }
                }
                for ((i, client) in clients.withIndex()) {
                    val before = hostMillis()
                    val read = client.hostNowMillis()!!
                    val after = hostMillis()
                    val error = when {
                        read < before -> before - read
                        read > after -> read - after
                        else -> 0L
                    }
                    println("C1 CLOCK — phone $i, skew ${skews[i]} ms: off by $error ms, fix ${client.clockFix()}")
                    assertTrue(
                        error <= 20,
                        "phone $i (its clock ${skews[i]} ms off) reads the house's clock $error ms wrong: " +
                            "$read against [$before, $after]",
                    )
                    assertTrue(abs(client.clockFix()!!.targetOffsetMillis + skews[i]) <= 20, "phone $i's offset is not its skew")
                }
                assertEquals(null, downward.tryReceive().getOrNull(), "a TimeMark reached the client's app")
                assertEquals(null, house.upward.tryReceive().getOrNull(), "a TimeProbe reached the host's app")
                assertEquals(0, house.host.malformedCount())

                jobs.forEach { it.cancel() }
            }
        } finally {
            jobs.forEach { it.cancel() }
            house.host.stop()
        }
    }

    /**
     * **The same probe frames from every phone, on the same schedule from its own seating** (rule
     * 1, D-263). Over 72 s of each phone's own clock (run ten times fast), each sent exactly one
     * burst of [PROBES_PER_ROUND] probes, at its seating: the round's uploads refresh the estimate
     * from there (D-295), so there is no 30 s burst any more.
     * Nothing in the schedule reads anything but the phone's own clock, so the count is the same
     * for every phone; a phone that probed more or less often than its neighbours would show here.
     */
    @Test
    fun everyPhoneSendsTheSameProbesOnTheSameSchedule() = runBlocking {
        val house = house(seats = 3)
        val jobs = mutableListOf<Job>()
        try {
            withTimeout(60_000) {
                val speed = 10L
                val origin = hostMillis()
                val clients = listOf(0L, 7_000L, -4_000L).map { skew ->
                    TransportClient(
                        ClientSession(), "127.0.0.1", house.port,
                        nowMillis = { origin + skew + (hostMillis() - origin) * speed },
                        onFrame = {},
                    )
                }
                clients.forEach { c ->
                    jobs += launch { c.run() }
                    house.seated.receive()
                }
                delay(72_000 / speed)
                val sent = clients.map { it.probesSent }
                println("C1 PROBES IN 72 S OF EACH PHONE'S CLOCK — $sent")
                assertEquals(
                    List(clients.size) { PROBES_PER_ROUND }, sent,
                    "the phones did not each send one burst of $PROBES_PER_ROUND probes",
                )
                clients.forEach { assertTrue(it.hostNowMillis() != null, "a phone that probed had no fix") }

                jobs.forEach { it.cancel() }
            }
        } finally {
            jobs.forEach { it.cancel() }
            house.host.stop()
        }
    }

    /**
     * **A mark written beside a stream of pushes does not tear one** (C1's R12 check). The host
     * writes `TimeMark` straight onto the socket from the connection's read loop while that
     * connection's writer is putting pushes on it. Since D-295 a connection probes in one burst, at
     * its seating, so the marks meet only the head of the stream; the uploads' answers, which ride
     * the writer like any frame, are what refresh the clock after that. Every push arrives, whole
     * and in order, and the phone has its fix.
     */
    @Test
    fun marksAndPushesShareOneSocketWithoutTearing() = runBlocking {
        val house = house(seats = 1)
        val jobs = mutableListOf<Job>()
        try {
            withTimeout(30_000) {
                val got = Channel<TransportFrame>(Channel.UNLIMITED)
                // Fast, as it was when the client re-probed every 30 s of its clock. Since D-295 it
                // bursts once, at its seating, which is the moment the pushes below begin.
                val origin = hostMillis()
                val client = TransportClient(
                    ClientSession(), "127.0.0.1", house.port,
                    nowMillis = { origin + (hostMillis() - origin) * 200 },
                    onFrame = { got.trySend(it) },
                )
                jobs += launch { client.run() }
                val seat = house.seated.receive()
                // A push every millisecond for a second and a half, from the moment of seating:
                // the burst's five marks are written into its head.
                var pushes = 0
                val until = hostMillis() + 1_500
                while (hostMillis() < until) {
                    house.host.send(seat, TransportFrame.Carry("push-$pushes-" + "x".repeat(pushes % 97)))
                    pushes++
                    delay(1)
                }
                repeat(pushes) {
                    assertEquals(TransportFrame.Carry("push-$it-" + "x".repeat(it % 97)), got.receive(), "push $it")
                }
                while (client.hostNowMillis() == null) delay(5)
                println("C1 MARKS BESIDE $pushes PUSHES — ${client.probesSent} probes sent, fix ${client.clockFix()}")
                assertEquals(PROBES_PER_ROUND, client.probesSent, "the client did not burst once at its seating")
                assertEquals(null, got.tryReceive().getOrNull(), "a frame arrived that was not a push")
                jobs.forEach { it.cancel() }
            }
        } finally {
            jobs.forEach { it.cancel() }
            house.host.stop()
        }
    }
}
