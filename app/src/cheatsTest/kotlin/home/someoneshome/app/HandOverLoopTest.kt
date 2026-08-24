package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.Subroutine
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The whole return half of the core loop, with nobody in the room: scan, catch, BEGIN, work,
 * hand over, and the house's answer landing back on the phone that sent it.**
 *
 * `HandOverSeamTest` in `ui` proves the client half against a recording seam, and it is the guard
 * that fails first when BEGIN stops carrying the card. It cannot prove the thing that matters
 * though, because `ui` may never see `core`: **that the id the phone files an entry against is one
 * the rules will accept, at a marker the house really dealt this seat work at.** A client-side
 * marker that was internally consistent and wrong to the house would pass every assertion in that
 * file and grade nothing in a real round.
 *
 * This is the module where the join can be made — `app` sees `ui`, `platform`, `model` **and**
 * `core` — so this is where it is made.
 *
 * ### What is real here and what is not
 *
 * Real: the rules, the draw, the work orders, `EmitSchema`'s allowlist, `RoundWire.project`'s
 * redaction, `FlowModel`, `SubroutineModel`, and every act driven through [FlowModel.actions] —
 * **the lambdas the buttons hold**, which is where the outage lived. What stands in is the socket:
 * [Bench.emit] hands the projected message straight to the addressed seat's model instead of
 * writing it down a websocket. `IntentPushTest` and `EffectPushTest` own the carriage on either
 * side of that, and re-proving it here would only add a radio to a test about a card.
 *
 * The seat on a request is stamped by the bench rather than by [IntentDesk] for the same reason,
 * and it is the same stamp: nothing a phone puts on the wire can name an actor — `IntentMessage`
 * has no field for one — so [Bench.up] is the only place a seat can be attached, exactly as the
 * desk is in production.
 */
class HandOverLoopTest {

    private val seats = List(5) { Seat(it) }

    private val inputs = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
    )

    /**
     * Five phones and a house, joined without a radio.
     *
     * Each seat gets its own [FlowModel] because the whole question is per-seat: what one phone was
     * dealt, what it scanned and what it may see. One model shared between seats would be five
     * players holding the same phone, and every disclosure assertion in the tree would become
     * meaningless at once.
     */
    private class Bench(inputs: ArmingInputs) {
        val said = mutableListOf<String>()

        /** Every request that reached the rules, in order, with the seat the bench stamped on it. */
        val requests = mutableListOf<Intent>()

        val phones: Map<Seat, FlowModel> = inputs.seats.associateWith { seat ->
            FlowModel(
                PanelState(screen = ScreenId.Boot),
                subroutines = SubroutineModel(send = { intent -> up(seat, intent) }),
            )
        }

        val house: RoundAuthority = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> emit(effect, state) },
            note = { said += it },
        )

        /**
         * **The emit boundary, minus the socket.**
         *
         * `EmitSchema.deliveries` decides who hears it and `RoundWire.project` decides what they
         * hear, which is `EffectWire.emit`'s own pair of calls in its own order. A bench that
         * addressed every phone, or that handed down the `Effect` instead of the projection, would
         * be a bench that could not see a leak — and this file drives a live round through it.
         */
        private fun emit(effect: Effect, state: GameState) {
            val message = RoundWire.project(effect)
            for (delivery in EmitSchema.deliveries(effect, state)) {
                phones[delivery.seat]?.houseSaid(message)
            }
        }

        /**
         * **The desk's stamp.** The seat comes from which phone the request left, never from the
         * request — see the class KDoc. Held as a queue rather than dispatched inline because
         * `SubroutineModel.send` is not a suspending seam and `RoundAuthority.heard` is.
         */
        private val pending = mutableListOf<Intent>()

        private fun up(seat: Seat, intent: IntentMessage) {
            pending += when (intent) {
                is IntentMessage.HandOverEntry ->
                    Intent.HandOverEntry(seat, intent.marker, intent.entered)
                is IntentMessage.BeginSubroutine -> Intent.BeginSubroutine(seat, intent.marker)
                IntentMessage.StopSubroutine -> Intent.StopSubroutine(seat)
                else -> return
            }
        }

        /** Everything the phones have published since the last drain, put to the rules in order. */
        suspend fun drain() {
            while (pending.isNotEmpty()) {
                val intent = pending.removeAt(0)
                requests += intent
                house.heard(intent)
            }
        }

        suspend fun heard(intent: Intent) {
            requests += intent
            house.heard(intent)
        }
    }

    private fun bench(block: suspend (Bench) -> Unit) = runBlocking { block(Bench(inputs)) }

    /** This seat's order as its own phone holds it — the list the house pushed, and nothing else. */
    private fun FlowModel.knownLines(): List<OrderLine.Known> =
        state.order.orEmpty().filterIsInstance<OrderLine.Known>().filter { !it.done }

    /**
     * **One card, played the whole way through, on one seat's real screens.**
     *
     * Returns the kind the house dealt at that card **once the rules have graded the entry**, and
     * null where the house had nothing for this seat there — which is a lawful answer (D-124) and
     * not a failure.
     *
     * ⚠️ **The grade is what makes the return value worth anything, and the first version of this
     * method did not check it.** It returned the kind as soon as BEGIN had opened the right screen,
     * which made [everyKindTheDrawCanDealIsPlayableEndToEnd] a test of the routing half only — it
     * stayed green through an injection that put BEGIN's marker back to `null` and stopped every
     * entry in the round from reaching the wire, while the two tests above it went red. A sweep
     * that says *end to end* and stops at the screen is the instrument lying quietly, which this
     * project has now caught six times.
     */
    private suspend fun Bench.playOneCard(seat: Seat, marker: MarkerId): SubroutineKind? {
        val phone = phones.getValue(seat)

        // The read goes up, and the phone remembers its own read. Both halves, in the order the
        // scan bridge does them.
        phone.scanning(marker)
        heard(Intent.BeginSubroutine(seat, marker))

        val current = phone.state.current ?: return null
        if (phone.state.screen != ScreenId.ScanCaught) return null

        val subroutine = current.subroutine
        val actions = phone.actions()

        // BEGIN — the lambda the button holds, never the model method behind it.
        actions.beginSubroutine()
        assertEquals(
            subroutine.screen, phone.state.screen,
            "${subroutine.label} was dealt at ${marker.value} and BEGIN opened somewhere else",
        )

        // The work, with the input a screen offers. The three sequences go on their own last
        // element and the rest go on the control; both routes end at `posted`.
        val gradedBefore = house.emitted.count { it is Effect.SubroutineGraded }
        repeat(8) { actions.tapSubroutine(subroutine, it) }
        actions.handOverSubroutine(subroutine)
        drain()

        val graded = house.emitted.count { it is Effect.SubroutineGraded } > gradedBefore
        return if (graded) subroutine.kind else null
    }

    // ---- 1 · The loop closes ----------------------------------------------------------------------

    /**
     * **A scan, the work, the hand-over, and the house's verdict on the screen that sent it.**
     *
     * This is the sentence the payoff run could not write: *no entry can be handed over, so System
     * Integrity cannot move, so the Residents cannot win a round.* Every link is asserted
     * separately, because each one failing looks different on a phone in a dark house — a scan that
     * caught nothing, a BEGIN that opened somebody else's work, a submit that hung on
     * `RETURNED . WAITING`, and a verdict that never came.
     */
    @Test
    fun aCardIsScannedWorkedAndHandedOverAndTheHouseAnswersTheSeatThatSentIt() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")

        val seat = seats.first { bench.phones.getValue(it).knownLines().isNotEmpty() }
        val phone = bench.phones.getValue(seat)
        val line = phone.knownLines().first { it.at.isNotEmpty() }
        val marker = line.at.first().marker

        val kind = bench.playOneCard(seat, marker)
        assertNotNull(
            kind,
            "the card came off this seat's own order and the round still did not reach a grade",
        )

        val filed = bench.requests.filterIsInstance<Intent.HandOverEntry>()
        assertEquals(
            1, filed.size,
            "the entry never reached the rules — this is RETURNED · WAITING with nothing behind it",
        )
        assertEquals(seat, filed.single().actor, "the entry was filed for a seat that did not send it")
        assertEquals(marker, filed.single().marker, "the entry was filed against a card nobody scanned")

        assertNotNull(
            phone.state.verdict,
            "the house never answered, so the screen is still waiting — the hang, one layer down",
        )
    }

    /**
     * **The rules accepted it**, which is the half `ui` is structurally unable to check.
     *
     * A hand-over that reached `admit` and was refused would leave every client-side assertion in
     * this tree green and still grade nothing: the phone would have done everything right and the
     * house would have thrown it away in silence, because a refusal is answered by re-assertion and
     * never by an explanation (D-066, D-097). So the refusal ledger is read directly.
     */
    @Test
    fun theRulesAcceptTheEntryTheScreenFiled() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")

        val seat = seats.first { bench.phones.getValue(it).knownLines().any { l -> l.at.isNotEmpty() } }
        val marker = bench.phones.getValue(seat).knownLines().first { it.at.isNotEmpty() }.at.first().marker

        val before = bench.house.refusals.size
        bench.playOneCard(seat, marker)

        assertEquals(
            before, bench.house.refusals.size,
            "the house refused something the screens did: ${bench.house.refusals.drop(before)}",
        )
        assertTrue(
            bench.house.emitted.any { it is Effect.SubroutineGraded },
            "the entry reached the rules and nothing was graded",
        )
    }

    // ---- 2 · Every kind the draw can deal --------------------------------------------------------

    /**
     * **All ten, end to end, off the house's own draw.**
     *
     * The `ui` sweep drives every kind by handing the model a fixture, which proves the routing and
     * cannot prove that the draw can *reach* each one through a real order at a real card. This
     * plays whatever the house deals across every seat and every line of their orders, over a fixed
     * list of seeds, and asserts the ten kinds all came out the other end **graded**.
     *
     * The seeds are pinned rather than random, because a sweep that draws its own inputs is a sweep
     * that fails on somebody else's machine on a Tuesday. If the draw's shape ever changes, this
     * fails with the kinds it did reach printed beside the ones it did not, which is the failure a
     * person can act on.
     */
    @Test
    fun everyKindTheDrawCanDealIsPlayableEndToEnd() {
        val reached = mutableSetOf<SubroutineKind>()
        for (seed in SEEDS) {
            bench { bench ->
                assertTrue(bench.house.arm(seed), "seed $seed never armed a round")
                for (seat in seats) {
                    val phone = bench.phones.getValue(seat)
                    val markers = phone.knownLines().flatMap { it.at }.map { it.marker }.distinct()
                    for (marker in markers) {
                        bench.playOneCard(seat, marker)?.let { reached += it }
                    }
                }
            }
        }

        assertEquals(
            SubroutineKind.entries.toSet(), reached,
            "the draw never routed these all the way through a screen: " +
                "${SubroutineKind.entries.toSet() - reached}",
        )
    }

    private companion object {
        /** Pinned. See [everyKindTheDrawCanDealIsPlayableEndToEnd]. */
        val SEEDS = listOf(RoundCheat.SEED, 7L, 19L, 41L, 101L)
    }
}
