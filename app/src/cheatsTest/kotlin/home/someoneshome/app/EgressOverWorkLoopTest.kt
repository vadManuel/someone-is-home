package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.Subroutine
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **An Egress fired while people are mid-Subroutine, through the real house and the real phones**
 * (the owner's E1 and E2 rulings, D-297).
 *
 * `HandOverLoopTest`'s bench: the rules, the allowlist, `RoundWire.project` and every phone's own
 * [FlowModel], joined without a socket. What it adds is the crisis arriving in the middle of the
 * work: the phone keeps the work with the alert over it, the entry still goes up and the house
 * grades it (E1); and a seat that scans a node card lands on the tap control while the house opens
 * nothing there (E2).
 */
class EgressOverWorkLoopTest {

    private val seats = List(5) { Seat(it) }

    private val inputs = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    private class Bench(inputs: ArmingInputs) {
        /** Every message a seat was sent, in order. */
        val told = mutableMapOf<Int, MutableList<RoundMessage>>()
        val requests = mutableListOf<Intent>()
        private val pending = mutableListOf<Intent>()

        val phones: Map<Seat, FlowModel> = inputs.seats.associateWith { seat ->
            FlowModel(
                PanelState(screen = ScreenId.Boot),
                subroutines = SubroutineModel(send = { intent -> up(seat, intent) }),
            )
        }

        val house: RoundAuthority = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> emit(effect, state) },
            note = {},
        )

        private fun emit(effect: Effect, state: GameState) {
            val message = RoundWire.project(effect)
            for (delivery in EmitSchema.deliveries(effect, state)) {
                told.getOrPut(delivery.seat.index) { mutableListOf() } += message
                phones[delivery.seat]?.houseSaid(message)
            }
        }

        private fun up(seat: Seat, intent: IntentMessage) {
            pending += when (intent) {
                is IntentMessage.HandOverEntry -> Intent.HandOverEntry(seat, intent.marker, intent.entered)
                IntentMessage.StopSubroutine -> Intent.StopSubroutine(seat)
                else -> return
            }
        }

        suspend fun drain() {
            while (pending.isNotEmpty()) {
                val intent = pending.removeAt(0)
                requests += intent
                house.heard(intent)
            }
        }

        /** A card read on [seat]'s phone and put to the house, as the scan bridge does (D-318). */
        suspend fun scan(seat: Seat, marker: MarkerId) {
            phones.getValue(seat).scanning(marker)
            house.cardRead(seat, marker)
        }
    }

    private fun FlowModel.firstCard(): MarkerId? =
        state.order.orEmpty().filterIsInstance<OrderLine.Known>()
            .firstOrNull { !it.done && it.at.isNotEmpty() && it.work is AssignedWork.Subroutine }
            ?.at?.first()?.marker

    @Test
    fun workInProgressKeepsItsScreenThroughTheEgressAndIsGradedByTheHouse() = runBlocking {
        val bench = Bench(inputs)
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
        val insider = bench.house.state.livingInsiders.first()
        val resident = seats.first { it !in bench.house.state.insiderSeats && bench.phones.getValue(it).firstCard() != null }
        val working = listOf(resident to "Resident", insider to "Insider")

        // Both at work, BEGIN pressed, one input made.
        for ((seat, role) in working) {
            val phone = bench.phones.getValue(seat)
            bench.scan(seat, assertNotNull(phone.firstCard(), "$role has no card"))
            assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the scan caught nothing")
            phone.actions().beginSubroutine()
            assertNotNull(Subroutine.on(phone.state.screen), "$role: BEGIN opened no Subroutine")
        }

        // Past the shared cooldown, and the Egress fires.
        bench.house.at(5_000)
        assertTrue(bench.house.fireEgress(insider), "the Egress did not start, so this measured nothing")

        for ((seat, role) in working) {
            val phone = bench.phones.getValue(seat)
            val screen = phone.state.screen
            val subroutine = assertNotNull(Subroutine.on(screen), "$role: the Egress took the phone off its work")
            assertTrue(phone.state.egressAlertUp, "$role: no alert over the work")
            assertEquals(subroutine.screen, PanelVals(phone.state).arrival?.under, "$role: the alert is not over the work")

            phone.actions().dismissNotification()
            assertEquals(screen, phone.state.screen, "$role: the swipe moved the phone")

            val gradedBefore = bench.house.emitted.count { it is Effect.SubroutineGraded && it.seat == seat }
            fun gone() = phone.subroutines.entry(subroutine)?.gone == true
            repeat(8) { if (!gone()) phone.actions().tapSubroutine(subroutine, it) }
            if (!gone()) phone.actions().handOverSubroutine(subroutine)
            bench.drain()

            val graded = bench.house.emitted.count { it is Effect.SubroutineGraded && it.seat == seat }
            assertEquals(gradedBefore + 1, graded, "$role: the house graded nothing after the Egress opened")
            assertNotNull(phone.state.verdict, "$role: the verdict never landed on the work")
            assertEquals(screen, phone.state.screen, "$role: the verdict moved the phone")
        }
        val refusals = bench.house.refusals
        assertTrue(refusals.isEmpty(), "the house refused something the screens did: $refusals")
    }

    /**
     * **The caught scan and the scan keep their screens through the Egress, and a card read under
     * the alert is answered as any other** (the owner's 2026-11-19 ruling, D-299), for a Resident and
     * the Insider through the real house.
     */
    @Test
    fun theBriefingAndTheScanKeepTheirScreensThroughTheEgressAndTheReadStillLands() = runBlocking {
        // On the briefing when it fires: the screen stays, and BEGIN after the swipe opens the work.
        run {
            val bench = Bench(inputs)
            assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
            val insider = bench.house.state.livingInsiders.first()
            val resident = seats.first { it !in bench.house.state.insiderSeats && bench.phones.getValue(it).firstCard() != null }
            val working = listOf(resident to "Resident", insider to "Insider")
            for ((seat, role) in working) {
                val phone = bench.phones.getValue(seat)
                phone.go(ScreenId.Scan)
                bench.scan(seat, assertNotNull(phone.firstCard(), "$role has no card"))
                assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the scan caught nothing")
            }
            bench.house.at(5_000)
            assertTrue(bench.house.fireEgress(insider), "the Egress did not start, so this measured nothing")
            for ((seat, role) in working) {
                val phone = bench.phones.getValue(seat)
                assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the Egress took the phone off the briefing")
                assertTrue(phone.state.egressAlertUp, "$role: no alert over the briefing")
                phone.actions().dismissNotification()
                assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the swipe moved the phone")
                phone.actions().beginSubroutine()
                assertNotNull(Subroutine.on(phone.state.screen), "$role: BEGIN after the Egress opened no Subroutine")
            }
            assertTrue(bench.house.refusals.isEmpty(), "the house refused something the screens did: ${bench.house.refusals}")
        }

        // On the scan when it fires: the screen stays with the alert over it, and the read lands.
        run {
            val bench = Bench(inputs)
            assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
            val insider = bench.house.state.livingInsiders.first()
            val resident = seats.first { it !in bench.house.state.insiderSeats && bench.phones.getValue(it).firstCard() != null }
            val working = listOf(resident to "Resident", insider to "Insider")
            working.forEach { (seat, _) -> bench.phones.getValue(seat).go(ScreenId.Scan) }
            bench.house.at(5_000)
            assertTrue(bench.house.fireEgress(insider), "the Egress did not start, so this measured nothing")
            val nodes = bench.house.state.egress!!.nodes
            for ((seat, role) in working) {
                val phone = bench.phones.getValue(seat)
                assertEquals(ScreenId.Scan, phone.state.screen, "$role: the Egress took the camera away")
                assertTrue(phone.state.egressAlertUp, "$role: no alert over the scan")
                val card = assertNotNull(
                    phone.state.order.orEmpty().filterIsInstance<OrderLine.Known>()
                        .firstOrNull { !it.done && it.work is AssignedWork.Subroutine && it.at.any { d -> d.marker !in nodes } }
                        ?.at?.first { it.marker !in nodes }?.marker,
                    "$role has no card off the Egress's nodes",
                )
                bench.scan(seat, card)
                assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the read under the alert reached no briefing")
                assertTrue(!phone.state.egressAlertUp, "$role: the alert followed the phone onto the briefing")
                phone.actions().beginSubroutine()
                assertNotNull(Subroutine.on(phone.state.screen), "$role: BEGIN after a read under the alert opened no Subroutine")
            }
            assertTrue(bench.house.refusals.isEmpty(), "the house refused something the screens did: ${bench.house.refusals}")
        }
    }

    /**
     * **E2 end to end**: a seat whose own work is at one of the Egress's two cards scans it, lands on
     * the tap control, and the house opens nothing there. Searched over pinned seeds for a round in
     * which some seat's order holds a node card, because the draw decides both.
     */
    @Test
    fun aNodeCardHoldingWorkOpensTheTapControlAndNothingAtTheHouse() = runBlocking {
        var found = false
        for (seed in SEEDS) {
            val bench = Bench(inputs)
            assertTrue(bench.house.arm(seed), "seed $seed never armed a round")
            bench.house.at(5_000)
            val insider = bench.house.state.livingInsiders.first()
            assertTrue(bench.house.fireEgress(insider), "seed $seed: the Egress did not start")
            val nodes = bench.house.state.egress!!.nodes
            val hit = seats.flatMap { seat -> nodes.map { seat to it } }.firstOrNull { (seat, node) ->
                bench.house.state.workOrderFor(seat)?.openAt(node) != null
            } ?: continue
            found = true
            val (seat, node) = hit
            val role = if (seat in bench.house.state.insiderSeats) "Insider" else "Resident"
            val phone = bench.phones.getValue(seat)
            val before = bench.told[seat.index].orEmpty().size

            bench.scan(seat, node)

            assertEquals(ScreenId.EgressWidget, phone.state.screen, "$role: the node scan did not reach the tap control")
            assertTrue(PanelVals(phone.state).pulseLive, "$role: the control is not live")
            assertNull(
                bench.house.state.openSubroutineFor(seat)?.armedAt,
                "$role: the house opened work at a node card the phone cannot reach",
            )
            assertEquals(node, bench.house.state.presenceFor(seat)?.at, "$role: the node scan did not place the seat")
            assertEquals(
                listOf<RoundMessage>(RoundMessage.ScanAnswered(null)),
                // Every ordinary scan re-sends the seat's own order beside the answer (D-322), the same
                // order whatever the card held; the answer itself is the empty card's.
                bench.told[seat.index].orEmpty().drop(before).filterNot { it is RoundMessage.WorkOrderIssued },
                "$role: the node scan was answered with something other than an empty card's answer",
            )
            break
        }
        assertTrue(found, "no pinned seed dealt work at an Egress node, so this proved nothing")
    }

    private companion object {
        val SEEDS = listOf(RoundCheat.SEED, 7L, 19L, 41L, 101L, 211L, 307L, 401L, 503L, 601L)
    }
}
