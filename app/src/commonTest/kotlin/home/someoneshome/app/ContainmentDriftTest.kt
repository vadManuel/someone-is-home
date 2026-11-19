package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Hmac
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Seat
import home.someoneshome.model.SyncPulseGrid
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.transport.ClockSync
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Two phones whose clocks differ by seconds and drift apart for half an hour still pulse in unison,
 * and contain an Egress at minute 30** (D-295; the owner's question, *wouldn't clock sync at join
 * drift enough to fail during the duration of a match?*).
 *
 * A bench in virtual time, so thirty minutes take a moment. The house's clock is true time. Phone A's
 * clock is **3 s ahead and runs 100 ppm fast**; phone B's is **2 s behind and runs 100 ppm slow**, so
 * the two drift apart by 360 ms over the round, past the GDD's ±150 ms coordinated-flip budget
 * (GDD:1291) on their own. Every second each phone makes the upload it really makes: its probe drawn
 * by the real [UploadClock], packed by the real [SightingUpload.pack], through [RoundWire] both ways,
 * stamped by the house with [RoundBody.UploadHeard.of] at arrival and at answer, and fed to the real
 * [ClockSync]. The Wi-Fi is seeded jitter: 2–40 ms each way, one beat in seven 50–400 ms late, one
 * in fifty stalled 0.8–1.5 s, and the house's own queue and rules 0–60 ms.
 *
 * Checked every ten seconds from the first minute: each phone puts the house's moment within
 * [EACH_WITHIN] ms, and the two within GDD:1291's [UNISON_WITHIN] ms of each other. At minute 30 an
 * Egress fires on the real rules, anchored on the house's clock; each phone feels its pulses where
 * its estimate puts them ([PulseBuzzer.nextPulse]), a person taps a quarter second either side of
 * each, the phone stamps each tap with its estimate, and the rules grade the two returns. They
 * contain it.
 */
class ContainmentDriftTest {

    private class Phone(val seat: Seat, val startMillis: Long, val ppm: Long, private val trueNow: () -> Long) {
        /** This phone's own clock at true time [t]. */
        fun local(t: Long): Long = startMillis + t + t * ppm / 1_000_000
        val sync = ClockSync()
        val clock = UploadClock(nowMillis = { local(trueNow()) })
        /** The house's clock as this phone reads it at true time [t], or null before any sample. */
        fun houseAt(t: Long): Long? = sync.hostNowMillis(local(t))
        var uploads = 0L
        var samples = 0
    }

    private class Due(val at: Long, val run: () -> Unit)

    private class Bench(seed: Int) {
        val random = Random(seed)
        var now = 0L
        /** Due events, latest first, so the next is always at the end. Equal times keep their order. */
        private val queue = ArrayList<Due>()
        fun at(t: Long, run: () -> Unit) {
            var i = queue.size
            while (i > 0 && queue[i - 1].at <= t) i--
            queue.add(i, Due(t, run))
        }
        fun advanceTo(t: Long) {
            while (queue.isNotEmpty() && queue.last().at <= t) {
                val next = queue.removeAt(queue.size - 1)
                now = maxOf(now, next.at)
                next.run()
            }
            now = maxOf(now, t)
        }
        /** One way across the Wi-Fi. */
        fun hop(): Long {
            var d = 2L + random.nextLong(0, 39)
            if (random.nextInt(7) == 0) d += random.nextLong(50, 401)
            if (random.nextInt(50) == 0) d += random.nextLong(800, 1_501)
            return d
        }
    }

    /** The house's clock is true time, from an uptime of a day. */
    private fun house(t: Long): Long = 86_400_000L + t

    private val contactSeed = ContactSeed.of(Hmac.sha256(byteArrayOf(9), "drift".encodeToByteArray()))

    private fun upload(bench: Bench, phone: Phone) {
        val sentAt = bench.now
        val body = SightingUpload.pack(
            contactSeed, ContactWindow(1L), phone.uploads++, emptyList(), probe = phone.clock.stamp(),
        )
        val up = RoundWire.encode(body)
        val heardAt = sentAt + bench.hop()
        bench.at(heardAt) {
            val heard = RoundWire.decodeOrNull(up) as RoundBody.Sighted
            val answeredAt = heardAt + bench.random.nextLong(0, 61)
            bench.at(answeredAt) {
                val down = RoundWire.encode(
                    RoundBody.UploadHeard.of("0123456789abcdef", heard.probe, house(heardAt), house(answeredAt)),
                )
                bench.at(answeredAt + bench.hop()) {
                    val answer = RoundWire.decodeOrNull(down) as RoundBody.UploadHeard
                    phone.clock.answered(answer, phone.local(bench.now)) { s, h, a, r ->
                        phone.sync.onSample(s, h, a, r)
                        phone.samples++
                    }
                }
            }
        }
    }

    /** The seating burst: five probes, each answered with the house's clock alone. */
    private fun seat(bench: Bench, phone: Phone) {
        val probes = phone.sync.dueProbes(phone.local(bench.now))
        for (probe in probes) {
            val atHouse = bench.now + bench.hop()
            bench.at(atHouse + bench.hop()) { phone.sync.onMark(probe, house(atHouse), phone.local(bench.now)) }
        }
    }

    @Test
    fun twoDriftingPhonesStayInUnisonForThirtyMinutesAndContainAtMinuteThirty() {
        val bench = Bench(seed = 2026_11_18)
        val a = Phone(Seat(0), startMillis = 3_000_000L + 3_000L, ppm = 100, trueNow = { bench.now })
        val b = Phone(Seat(1), startMillis = 3_000_000L - 2_000L, ppm = -100, trueNow = { bench.now })
        val phones = listOf(a, b)

        // Seated at true 0; uploads every second of each phone's own clock, from LIGHTS OUT at 5 s.
        phones.forEach { seat(bench, it) }
        val roundEnd = 30L * 60_000L
        for (phone in phones) {
            var t = 5_000L + phone.seat.index * 137L
            while (t <= roundEnd + 60_000L) {
                bench.at(t) { upload(bench, phone) }
                t += SightingUpload.CADENCE_MILLIS
            }
        }

        var worstEach = 0L
        var worstApart = 0L
        val log = mutableListOf<String>()
        var t = 60_000L
        while (t <= roundEnd) {
            bench.advanceTo(t)
            val errors = phones.map { it.houseAt(t)!! - house(t) }
            val apart = errors[0] - errors[1]
            worstEach = maxOf(worstEach, errors.maxOf { abs(it) })
            worstApart = maxOf(worstApart, abs(apart))
            if (t % 300_000L == 0L) log += "min ${t / 60_000}: A ${errors[0]} ms, B ${errors[1]} ms, apart $apart ms"
            assertTrue(
                errors.all { abs(it) <= EACH_WITHIN },
                "at ${t / 1000} s a phone puts the house's moment ${errors} ms off (A, B); within $EACH_WITHIN ms is the bound",
            )
            assertTrue(
                abs(apart) <= UNISON_WITHIN,
                "at ${t / 1000} s the two phones pulse $apart ms apart, past GDD:1291's ±$UNISON_WITHIN ms",
            )
            t += 10_000L
        }
        println("D-295 DRIFT BENCH — ${log.joinToString(" · ")}")
        println("D-295 DRIFT BENCH — worst single-phone error $worstEach ms, worst A−B $worstApart ms, samples A ${a.samples} B ${b.samples}")
        assertTrue(a.samples > 1_700 && b.samples > 1_700, "the uploads did not refresh the clock: ${a.samples}, ${b.samples} samples")

        // ---- Minute 30: an Egress, felt and tapped on two drifting phones, graded by the rules ------
        val fireAt = roundEnd
        bench.advanceTo(fireAt)
        val markers = listOf("KITCHEN", "HALL", "STUDY", "LANDING", "GARAGE", "PORCH")
            .mapIndexed { i, room -> Destination(MarkerId("card-$i"), MarkerShapes.all[i + 1].id, room) }
        val seats = (0..5).map { Seat(it) }
        var state = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(Seat(2), Seat(4)), markers = markers),
        ).state
        fun put(event: Event) {
            val admission = admit(state, event)
            check(admission is Admission.Admitted) { "the bench's $event was refused: $admission" }
            state = admission.reduction.state
        }
        val firedAt = state.egressReadyAt
        val nodes = listOf(state.activeMarkers.first(), state.activeMarkers.last())
        val pulseFrom = house(fireAt) + Balance.SYNC_PULSE_LEAD_MILLIS
        put(Event.EgressFired(firedAt, Seat(4), EgressType.Beacon, nodes, pulseFrom))
        var tick = firedAt.step + 1
        put(Event.MarkerScanned(Tick(tick++), a.seat, nodes[0]))
        put(Event.MarkerScanned(Tick(tick++), b.seat, nodes[1]))

        // Each phone buzzes pulses 4..7 where its own estimate puts them; the person taps a quarter
        // second late, early, late, early; the phone stamps each tap on its estimate.
        val human = listOf(240L, -240L, 240L, -240L)
        val taps = phones.associateWith { mutableListOf<Long>() }
        val felt = phones.associateWith { mutableListOf<Long>() }
        for ((k, n) in (4L..7L).withIndex()) {
            for (phone in phones) {
                val pulse = PulseBuzzer.pulseAt(pulseFrom, n)
                // The true moment this phone's estimate reaches the pulse: where its motor goes off.
                var buzz = pulse - house(0) - 1_000L
                while (phone.houseAt(buzz)!! < pulse) buzz += 1
                felt.getValue(phone) += buzz - (pulse - house(0))
                val tapAt = buzz + human[k]
                bench.advanceTo(maxOf(bench.now, tapAt))
                taps.getValue(phone) += phone.houseAt(tapAt)!!
            }
        }
        println("D-295 DRIFT BENCH — minute 30: A felt pulses ${felt.getValue(a)} ms from the house's moments, B ${felt.getValue(b)}")
        // **Four in a row** (the owner's O3, D-298): each phone's four stamps are pulses 4 to 7 of the
        // house's grid, consecutive, by the function the house grades with, and the two runs share
        // every pulse (option B's pairing).
        for (phone in phones) {
            assertEquals(4L, SyncPulseGrid.inARow(pulseFrom, taps.getValue(phone)), "seat ${phone.seat.index}'s taps are not four in a row from pulse 4")
        }
        tick = maxOf(tick, firedAt.step + Balance.SYNC_PULSE_LEAD + Balance.SYNC_PULSE_BEATS * Balance.SYNC_PULSE_INTERVAL)
        put(Event.SyncPulseReturned(Tick(tick++), a.seat, taps.getValue(a)))
        put(Event.SyncPulseReturned(Tick(tick++), b.seat, taps.getValue(b)))
        assertEquals(null, state.egress, "two drifting phones at two nodes did not contain the Egress at minute 30: taps ${taps.values}")
        for ((phone, moments) in felt) {
            assertTrue(moments.all { abs(it) <= EACH_WITHIN }, "seat ${phone.seat.index} felt the pulse ${moments} ms from the house's moment")
        }
    }

    private companion object {
        /** How far one phone may put the house's moment: a tenth of the ±150 ms budget, and far inside the 400 ms window. */
        const val EACH_WITHIN = 25L

        /** GDD:1291: *all devices flip within ±150 ms of each other — "reads as one event"*. */
        const val UNISON_WITHIN = 150L
    }
}
