package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Effect
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.platform.CardScanner
import home.someoneshome.platform.RecordingHaptics
import home.someoneshome.platform.ScanUse
import home.someoneshome.platform.ScanWindow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **ACCEPTED says *yes*, REJECTED and NOTHING FOR YOU HERE say *no*, on the phone that asked, and
 * the same on both roles** (the owner's 2026-11-21 ruling, D-320: *add the yes / no buzz. I think
 * the no buzz should be something like two buzzes*).
 *
 * The real [RoundAuthority] in process, armed in the bench's home, every delivery taken through the
 * real schema and projected as the wire projects it, and each phone's messages played on a motor by
 * the real [HouseBuzzer]: what is compared is what a hand would feel. `CardReadTest`'s rig.
 */
class VerdictBuzzTest {

    private val seats = List(5) { Seat(it) }

    private fun inputs() = ArmingInputs(
        seats = seats,
        names = seats.map { SeatNamed(it, "SEAT${it.index}") },
        oneLines = seats.map { OneLineHeld(it, "line ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    private class Bench(inputs: ArmingInputs) {
        val delivered = mutableListOf<Pair<Seat, RoundMessage>>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state ->
                for (d in EmitSchema.deliveries(effect, state)) delivered += d.seat to RoundWire.project(effect)
            },
            note = { said += it },
        )

        /** What [seat]'s motor plays for everything it is sent while [act] runs. */
        suspend fun felt(seat: Seat, act: suspend () -> Unit): Pair<List<RoundMessage>, List<List<home.someoneshome.model.HapticStep>>> {
            val from = delivered.size
            act()
            val heard = delivered.drop(from).filter { it.first.index == seat.index }.map { it.second }
            val motor = RecordingHaptics()
            val buzzer = HouseBuzzer(motor)
            heard.forEach(buzzer::heard)
            return heard to motor.played
        }
    }

    private suspend fun armed(): Bench = Bench(inputs()).also {
        assertTrue(it.house.arm(SEED), "never armed: ${it.said}")
    }

    private fun GameState.workCard(seat: Seat): MarkerId {
        val order = assertNotNull(workOrderFor(seat), "seat ${seat.index} holds no work order")
        // A Subroutine of one card: a Memory Dump source shows its pattern and opens no window (D-325).
        val entry = order.entries.first { order.isActionable(it) && it.work is AssignedWork.Subroutine && it.anchor != null }
        return assertNotNull(entry.anchor, "seat ${seat.index}'s work has no card")
    }

    /** A registered ordinary card this seat's order opens nothing at. */
    private fun GameState.emptyCard(seat: Seat): MarkerId =
        BenchHome.map.registrations.map { it.card.id }.first { workOrderFor(seat)?.openAt(it) == null }

    private val yes = listOf(HouseBuzz.script(Haptic.Short))
    private val no = listOf(HouseBuzz.script(Haptic.Double))

    /**
     * **GUARD: one verdict, one buzz, the same for an Insider's graded fake and a Resident's real
     * entry.** Each scans its own work and hands over a wrong entry (REJECTED, *no*), scans again and
     * hands over the answer (ACCEPTED, *yes*); each scans a registered card holding nothing for it
     * (NOTHING FOR YOU HERE, *no*). The opened scans say nothing: the acquire said *yes*.
     */
    @Test
    fun theVerdictAndNothingForYouHereBuzzTheSameOnBothRoles() = runBlocking<Unit> {
        val bench = armed()
        val state = bench.house.state
        val insider = seats.first { state.roleOf(it) == Role.Insider }
        val resident = seats.first { state.roleOf(it) == Role.Resident }
        val feltBy = mutableMapOf<Role, List<List<List<home.someoneshome.model.HapticStep>>>>()

        for (seat in listOf(insider, resident)) {
            val role = state.roleOf(seat)
            val card = bench.house.state.workCard(seat)
            val nothing = bench.felt(seat) { bench.house.cardRead(seat, bench.house.state.emptyCard(seat)) }
            // The answer itself: since D-322 the order rides every scan beside it, silent, and the
            // buzz below is still read off everything the seat was sent.
            assertEquals(listOf<RoundMessage>(RoundMessage.ScanAnswered(null, Haptic.Double)), nothing.first.filterIsInstance<RoundMessage.ScanAnswered>(), "$role: NOTHING FOR YOU HERE")
            assertEquals(no, nothing.second, "$role: NOTHING FOR YOU HERE did not say no")

            val opened = bench.felt(seat) { bench.house.cardRead(seat, card) }
            assertEquals(emptyList(), opened.second, "$role: a scan that opened work buzzed")
            val first = assertNotNull(bench.house.state.openSubroutineFor(seat), "$role: the card opened nothing")
            val wrong = bench.felt(seat) {
                bench.house.heard(Intent.HandOverEntry(seat, card, first.expected.map { it + 997 }))
            }
            assertEquals(listOf<RoundMessage>(RoundMessage.SubroutineGraded(accepted = false)), wrong.first.filterIsInstance<RoundMessage.SubroutineGraded>(), "$role: a wrong entry was not REJECTED")
            assertEquals(no, wrong.second, "$role: REJECTED did not say no")

            bench.felt(seat) { bench.house.cardRead(seat, card) }
            val again = assertNotNull(bench.house.state.openSubroutineFor(seat), "$role: the second scan opened nothing")
            val right = bench.felt(seat) { bench.house.heard(Intent.HandOverEntry(seat, card, again.expected)) }
            assertEquals(listOf<RoundMessage>(RoundMessage.SubroutineGraded(accepted = true)), right.first.filterIsInstance<RoundMessage.SubroutineGraded>(), "$role: the answer was not ACCEPTED (${again.subroutine})")
            assertEquals(yes, right.second, "$role: ACCEPTED did not say yes")

            feltBy[role] = listOf(nothing.second, wrong.second, right.second)
        }
        assertEquals(feltBy[Role.Resident], feltBy[Role.Insider], "the hand can tell the roles apart by the verdict's buzz")
    }

    /**
     * **GUARD: a node of the running Egress says nothing** (D-295, D-297, D-320). Every phone routes
     * it to the tap control, so a *no* there would refuse the scan the phone is showing as the
     * containment's. The house's answer is the empty card's, and silent.
     */
    @Test
    fun aNodeOfTheEgressSaysNothing() = runBlocking<Unit> {
        val bench = armed()
        bench.house.at(5_000)
        val insider = bench.house.state.livingInsiders.first()
        assertTrue(bench.house.fireEgress(insider), "the Egress did not start")
        val node = requireNotNull(bench.house.state.egress).nodes.first()
        for (seat in seats) {
            val felt = bench.felt(seat) { bench.house.cardRead(seat, node) }
            assertEquals(listOf<RoundMessage>(RoundMessage.ScanAnswered(null, haptic = null)), felt.first.filterIsInstance<RoundMessage.ScanAnswered>(), "seat ${seat.index} at a node")
            assertEquals(emptyList(), felt.second, "seat ${seat.index}: a node of the Egress said no")
        }
    }

    /**
     * **What a hand feels around the scan, written down for the owner** (D-320, D-159, D-160).
     *
     * - The acquire's *yes* (the scan window's one Short) comes first on every read, so NOTHING FOR
     *   YOU HERE is *yes* then *no*, exactly as NOT A MARKER is (D-318).
     * - **REJECTED's *no* and the scan window running out are the same two vibrates**: both are
     *   D-160's *no*, `Haptic.Double`, step for step. They come at different moments (a timeout on
     *   the scan screen, a verdict after a hand-over), and nothing else tells them apart in the hand.
     * - ACCEPTED's *yes* is the acquire's Short.
     */
    @Test
    fun whatTheHandFeelsAroundTheScan() {
        val motor = RecordingHaptics()
        val lens = Lens()
        val window = ScanWindow(lens, motor)
        val buzzer = HouseBuzzer(motor)
        var read: String? = null
        window.open { read = it }
        lens.present("10ABCDE01")
        assertNotNull(read, "the window read nothing")
        buzzer.heard(RoundMessage.ScanAnswered(null, Haptic.Double))
        assertEquals(yes + no, motor.played, "NOTHING FOR YOU HERE was not yes then no")

        val timedOut = RecordingHaptics()
        ScanWindow(Lens(), timedOut).apply { open {} }.expire()
        val rejected = RecordingHaptics().also { HouseBuzzer(it).heard(RoundMessage.SubroutineGraded(accepted = false)) }
        assertEquals(timedOut.played, rejected.played, "REJECTED and a timed-out scan no longer feel the same")
        val accepted = RecordingHaptics().also { HouseBuzzer(it).heard(RoundMessage.SubroutineGraded(accepted = true)) }
        assertEquals(yes, accepted.played)
    }

    private class Lens : CardScanner {
        private var listener: ((String) -> Unit)? = null
        override val use: ScanUse = ScanUse.InRound
        override fun start(onPayload: (String) -> Unit) {
            listener = onPayload
        }
        override fun stop() {
            listener = null
        }
        override fun torch(lit: Boolean) = Unit
        fun present(payload: String) = listener?.invoke(payload)
    }

    private companion object {
        const val SEED = 20261121L
    }
}
