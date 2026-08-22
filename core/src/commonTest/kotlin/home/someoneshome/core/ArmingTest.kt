package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.InsiderBand
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **LIGHTS OUT — what the house draws, and everything about the draw that a player could measure.**
 *
 * Most of what is below is a *negative*: the order length must not move with the hidden count, the
 * lamps must not differ, the opening message must reach everyone identically. Those are the
 * assertions that fail on a build that leaks; an assertion that merely said *"a round was armed"*
 * would pass on every one of them.
 */
private fun seatsOf(n: Int) = (0 until n).map { Seat(it) }

/** Eight ordinary markers is D-127's floor, which makes it the honest home to arm in. */
private val MARKERS = (0 until 8).map { MarkerId("m$it") }

private fun arm(
    seats: Int = 8,
    insiders: List<Seat> = listOf(Seat(1)),
    chosen: Int? = null,
    markers: List<MarkerId> = MARKERS,
    seed: Long = 20260821L,
    at: Tick = Tick(0),
): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(at, seed, seatsOf(seats), insiders, chosen, markers),
).state

private fun armEffects(
    seats: Int = 8,
    insiders: List<Seat> = listOf(Seat(1)),
    seed: Long = 20260821L,
): List<Effect> = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed, seatsOf(seats), insiders, null, MARKERS),
).effects

class InsiderDrawTest {

    /**
     * **THE ONE THAT MATTERS: five seats can never draw two Insiders** (D-103 as amended, D-131).
     *
     * The band clamps the **draw** and not only the setting, so this is asserted over every seed
     * rather than over the one that happens to be in the fixture — a clamp that held for seed
     * 20260821 and not for seed 4 would be a clamp that holds until somebody hosts an evening.
     *
     * At five or six seats two Insiders reach parity after one or two Revokes: a round that can be
     * over before the first meeting has anything to discuss.
     */
    @Test
    fun `a five or six seat round always draws exactly one Insider`() {
        for (seats in Balance.MINIMUM_SEATS..6) {
            for (seed in 0L until 500L) {
                val drawn = insidersFor(seed, seatsOf(seats), chosen = null)
                assertEquals(
                    1, drawn.size,
                    "$seats seats, seed $seed: drew ${drawn.size} Insiders — at this party size " +
                        "two of them reach parity before the first meeting",
                )
            }
        }
    }

    /** And a host cannot hand-pick their way past it either. Both edges clamp the setting. */
    @Test
    fun `a hand-picked count outside the band is pulled inside it at the draw`() {
        for (chosen in listOf(0, 2, 3, 9)) {
            assertEquals(
                1, insidersFor(1L, seatsOf(5), chosen).size,
                "a host hand-picked $chosen Insiders into a five-seat round",
            )
        }
        // And at a party size where the band is wider, a lawful pick survives untouched.
        assertEquals(2, insidersFor(1L, seatsOf(8), chosen = 2).size)
    }

    /** Every draw, at every lawful party size, lands inside the band. */
    @Test
    fun `an UNKNOWN draw always lands inside the band`() {
        for (seats in Balance.MINIMUM_SEATS..16) {
            val band = InsiderBand.of(seats)
            for (seed in 0L until 200L) {
                val drawn = insidersFor(seed, seatsOf(seats), chosen = null).size
                assertTrue(drawn in band, "$seats seats, seed $seed: drew $drawn, band is $band")
            }
        }
    }

    /**
     * **UNKNOWN really is a draw**, and not a constant wearing one's clothes.
     *
     * The complement of the two above: every assertion about the band passes on a build that
     * always draws the minimum. At a party size whose band has two members, both must occur.
     */
    @Test
    fun `an UNKNOWN draw is not always the same number`() {
        val seen = (0L until 200L).map { insidersFor(it, seatsOf(12), chosen = null).size }.toSet()
        assertEquals(InsiderBand.of(12).toSet(), seen, "the draw never reached the whole band")
    }

    /** Who holds it moves too, or the round is armed by a rule players learn in one evening. */
    @Test
    fun `who holds the role moves with the seed`() {
        val seen = (0L until 200L).map { insidersFor(it, seatsOf(8), chosen = 1).single().index }
        assertTrue(seen.toSet().size > 1, "the same seat was the Insider in every round")
    }

    /** The whole event, from the one place that should build one. */
    @Test
    fun `arming refuses a party below the minimum before the round exists`() {
        val short = seatsOf(Balance.MINIMUM_SEATS - 1)
        assertFailsWith<IllegalArgumentException> {
            armingFor(Tick(0), seed = 1L, seats = short, chosenInsiders = null, markers = MARKERS)
        }
        val fine = armingFor(
            Tick(0), seed = 1L, seats = seatsOf(Balance.MINIMUM_SEATS),
            chosenInsiders = null, markers = MARKERS,
        )
        assertEquals(1, fine.insiders.size)
        assertEquals(MARKERS, fine.markers)
    }
}

class StationDrawTest {

    /** **D-122 — three stations, and they are three different cards.** */
    @Test
    fun `the stations are three distinct ordinary markers`() {
        for (seed in 0L until 200L) {
            val stations = arm(seed = seed).stations
            assertEquals(Balance.STATIONS, stations.size, "seed $seed drew ${stations.size}")
            assertEquals(
                stations.size, stations.map { it.value }.distinct().size,
                "seed $seed drew the same card twice: $stations",
            )
            assertTrue(stations.all { s -> MARKERS.any { it.value == s.value } })
        }
    }

    /**
     * **The circuit moves every round**, which is the load-bearing half of D-122: a home that
     * remembered where the Rack was would turn the circuit into a fact players learn once and
     * keep, and by the second night in the same house there would be no circuit left to run.
     */
    @Test
    fun `the stations are drawn again every round`() {
        val seen = (0L until 50L).map { arm(seed = it).stations.map { m -> m.value } }.toSet()
        assertTrue(seen.size > 1, "every round put the stations on the same three cards")
    }

    /** **D-123 — the active set is sized to the party, and the rest of the home sits dark.** */
    @Test
    fun `unused markers sit dark`() {
        val big = (0 until 30).map { MarkerId("m$it") }
        val state = arm(seats = 5, markers = big)
        assertEquals(
            Balance.activeMarkers(5, big.size), state.activeMarkers.size,
            "the active set is not the size the party is drawn for",
        )
        assertTrue(
            state.activeMarkers.size < big.size,
            "a thirty-marker home lit every card, so nothing sits dark",
        )
        // The stations are cards this round is using, so they are in the set it draws work from.
        assertTrue(state.stations.all { s -> state.activeMarkers.any { it.value == s.value } })
    }
}

class WorkOrderDrawTest {

    /**
     * **D-129 — every order is the same length, for every seat and both roles.**
     *
     * The Insider's fake is drawn by the same rule at the same length, so there is nothing to
     * compare and nothing to divide the hidden count out of.
     */
    @Test
    fun `every seat's order is the same length`() {
        val state = arm(seats = 8, insiders = listOf(Seat(1), Seat(5)))
        val lengths = state.workOrders.map { it.entries.size }.distinct()
        assertEquals(listOf(Balance.orderSize(8, null)), lengths, "orders differ in length: $lengths")
        assertEquals(8, state.workOrders.size, "a seat was drawn no order at all")
    }

    /**
     * **The order is sized off the PUBLIC setting, in both directions.**
     *
     * Move the hidden draw and the length must not move — that is the leak. Move the *setting*,
     * which is on every phone's lobby row, and it may: the arithmetic is public, so the answer is
     * allowed to be.
     */
    @Test
    fun `the length follows the setting and ignores the draw`() {
        val one = arm(seats = 8, insiders = listOf(Seat(1))).workOrders.first().entries.size
        val two = arm(seats = 8, insiders = listOf(Seat(1), Seat(5))).workOrders.first().entries.size
        assertEquals(one, two, "order length moved with the count D-103 hides")

        val setToMax = arm(seats = 8, insiders = listOf(Seat(1)), chosen = InsiderBand.of(8).last)
        val setToMin = arm(seats = 8, insiders = listOf(Seat(1)), chosen = InsiderBand.of(8).first)
        assertEquals(
            Balance.orderSize(8, InsiderBand.of(8).last),
            setToMax.workOrders.first().entries.size,
        )
        assertEquals(
            Balance.orderSize(8, InsiderBand.of(8).first),
            setToMin.workOrders.first().entries.size,
        )
    }

    /**
     * **An order deeper than the home chains through itself** (D-123).
     *
     * The second visit to a card is blocked by the first, discovered as the player completes rather
     * than announced in advance. A home short of markers absorbs the shortage exactly here, which
     * is why D-129 is enforced at arming and is not a REVIEW gate.
     */
    @Test
    fun `an order deeper than the active set blocks on its own work`() {
        val tiny = listOf(MarkerId("only"))
        val state = arm(seats = 5, markers = tiny)
        val order = state.workOrderFor(Seat(0))!!
        assertTrue(order.entries.size > 1, "the fixture's order is too short to chain")
        assertEquals(
            emptyList(), order.entries.first().blockedBy,
            "the first line of an order was blocked by something before it",
        )
        assertTrue(
            order.entries.drop(1).all { it.blockedBy == listOf(it.index - 1) },
            "a one-card home did not chain each line behind the last: " +
                order.entries.map { it.blockedBy }.toString(),
        )
        assertEquals(
            1, order.entries.count { order.isActionable(it) },
            "more than one line of a self-chained order was actionable at once",
        )
    }

    /**
     * **A self-chained order unblocks in sequence as its blockers complete** — the walk, not the
     * shape.
     *
     * Each pass: scan the one card, hand over what was asked, and the next line becomes actionable
     * and the one after it does not. A build that unblocked the whole tail at once would pass every
     * assertion above.
     */
    @Test
    fun `a self-chained order unblocks one line at a time`() {
        val card = MarkerId("only")
        var state = arm(seats = 5, markers = listOf(card))
        val seat = Seat(0)
        val length = state.workOrderFor(seat)!!.entries.size

        for (step in 0 until length) {
            val order = state.workOrderFor(seat)!!
            val open = order.entries.filter { order.isActionable(it) }
            assertEquals(
                listOf(step), open.map { it.index },
                "at step $step the actionable set was ${open.map { it.index }}",
            )
            state = reduce(state, Event.MarkerScanned(Tick(1), seat, card)).state
            assertEquals(
                step, state.openSubroutineFor(seat)!!.entry,
                "the scan opened a line the order was not up to",
            )
            val expected = order.entries[step].expected
            state = reduce(state, Event.SubroutineReturned(Tick(2), seat, card, expected)).state
        }
        val finished = state.workOrderFor(seat)!!
        assertTrue(finished.entries.all { it.done }, "the order did not finish")
        assertEquals(null, finished.openAt(card), "a finished order still opened work")
    }

    /**
     * **A blocked line is a known unknown on the wire** (D-114): the order is as long as it really
     * is, and the blocked lines carry no name.
     *
     * Not absent, which would shorten the order and make its length a tell; not spelled out, which
     * would hand the player a route they have not earned.
     */
    @Test
    fun `a blocked line reaches the phone with no name on it`() {
        val state = arm(seats = 5, markers = listOf(MarkerId("only")))
        val lines = state.workOrderFor(Seat(0))!!.asLines()
        assertEquals(
            state.workOrderFor(Seat(0))!!.entries.size, lines.size,
            "the order sent to the phone is shorter than the order the house holds",
        )
        assertTrue(lines.first() is OrderLine.Known, "the actionable line arrived without its name")
        assertTrue(
            lines.drop(1).all { it is OrderLine.Blocked },
            "a blocked line arrived carrying what it is",
        )
    }

    /**
     * **D-123 and D-124 — a scan resolves `(seat, card)` to THAT player's work, or to nothing.**
     *
     * A card the house has nothing for *you* at opens nothing, whether it is somebody else's anchor
     * or a card sitting dark this round — and the rules say so by staying silent, which is what
     * lets the client answer NOTHING FOR YOU HERE without the house having composed it. The
     * alternative, a scan that opens whatever that seat has next wherever they happen to be, was
     * the spine's stand-in and it made every card in the house every player's card.
     */
    @Test
    fun `a card that holds nothing for you opens nothing`() {
        val home = (0 until 30).map { MarkerId("m$it") }
        val state = arm(seats = 5, markers = home)
        val seat = Seat(0)
        val mine = state.workOrderFor(seat)!!.entries.map { it.marker.value }.toSet()

        val dark = home.first { it.value !in state.activeMarkers.map { m -> m.value } }
        val elsewhere = state.activeMarkers.first { it.value !in mine }
        for (card in listOf(dark, elsewhere)) {
            val scanned = reduce(state, Event.MarkerScanned(Tick(1), seat, card))
            assertEquals(emptyList(), scanned.effects, "a scan that found nothing announced it")
            assertEquals(
                null, scanned.state.openSubroutineFor(seat),
                "scanning ${card.value} opened work the house did not put there",
            )
        }
        // The complement, so this cannot pass on a build where no scan ever opens anything.
        val ours = MarkerId(mine.first())
        assertNotNull(
            reduce(state, Event.MarkerScanned(Tick(1), seat, ours)).state.openSubroutineFor(seat),
            "the seat's own anchor opened nothing either",
        )
    }

    /** Nothing is open until somebody scans something. The spine left one open all round. */
    @Test
    fun `arming opens no Subroutine at all`() {
        assertEquals(
            emptyList(), arm().openSubroutines,
            "a round opened with work already armed, so the first entry costs no walk",
        )
    }

    /**
     * **`Role` never reaches the draw.** Arm the same seed with every lawful set of Insiders and
     * every seat is asked for exactly the same work at exactly the same cards.
     *
     * This is the differential harness's premise stated locally: if the answer key moved with the
     * role, a role exchange would change what two seats were asked, and the game's central secret
     * would be sitting in the one place the harness reads.
     */
    @Test
    fun `the draw does not read who the Insiders are`() {
        val none = arm(seats = 8, insiders = emptyList())
        val some = arm(seats = 8, insiders = listOf(Seat(0), Seat(3)))
        for (seat in seatsOf(8)) {
            val a = none.workOrderFor(seat)!!.entries
            val b = some.workOrderFor(seat)!!.entries
            assertEquals(a.map { it.marker.value }, b.map { it.marker.value }, "seat ${seat.index}")
            assertEquals(a.map { it.expected }, b.map { it.expected }, "seat ${seat.index}")
            assertEquals(a.map { it.subroutine }, b.map { it.subroutine }, "seat ${seat.index}")
        }
    }
}

class ArmingEffectsTest {

    /**
     * **The opening message reaches every seat, identically** (D-118, D-076).
     *
     * One per seat, in seat order, and nothing on it differs between two of them. The dim is
     * world-observable in a dark house, so a message that reached fewer than everyone — or that
     * arrived differently for one phone — would be a beacon.
     */
    @Test
    fun `the opening message reaches every seat identically`() {
        val opening = armEffects(seats = 8, insiders = listOf(Seat(1), Seat(5)))
            .filterIsInstance<Effect.OpeningMessage>()
        assertEquals(seatsOf(8), opening.map { it.seat }, "the opening message missed a seat")
        assertEquals(
            1, opening.map { it.haptic }.distinct().size,
            "the opening message buzzed differently for different phones",
        )
    }

    /**
     * **It is not one of D-135's five long haptics.**
     *
     * The reserved set is closed for the reason the dim is: in a silent house a long buzz is
     * world-observable through a pocket, and a signal that means five specific things stops meaning
     * them when a sixth is added.
     */
    @Test
    fun `the opening message does not carry the long haptic`() {
        val opening = armEffects().filterIsInstance<Effect.OpeningMessage>()
        assertTrue(opening.isNotEmpty(), "no opening message was sent at all")
        assertTrue(
            opening.all { it.haptic == Haptic.Short },
            "the opening message joined D-135's closed set of five long haptics",
        )
    }

    /** Every seat gets its order, and one lamp, at the same luminance. */
    @Test
    fun `every seat is lit and issued an order`() {
        val effects = armEffects(seats = 8, insiders = listOf(Seat(1), Seat(5)))
        assertEquals(seatsOf(8), effects.filterIsInstance<Effect.LampSet>().map { it.seat })
        assertEquals(
            1, effects.filterIsInstance<Effect.LampSet>().map { it.luminance }.distinct().size,
            "a per-role luminance at arming is a tell delivered while everyone is still clustered",
        )
        val orders = effects.filterIsInstance<Effect.WorkOrderIssued>()
        assertEquals(seatsOf(8), orders.map { it.seat }, "a seat was armed without a work order")
        assertEquals(
            1, orders.map { it.lines.size }.distinct().size,
            "two phones were sent orders of different lengths",
        )
    }

    /**
     * **The order goes back on every return, accepted or not** (rule 1).
     *
     * Sending it only when something changed would make its *presence* a second verdict, arriving
     * beside the real one and readable without being told.
     */
    @Test
    fun `a rejected entry is answered with the same shape as an accepted one`() {
        val state = arm()
        val seat = Seat(0)
        val entry = state.workOrderFor(seat)!!.entries.first()

        fun shapeOf(entered: List<Int>): List<String> {
            val scanned = reduce(state, Event.MarkerScanned(Tick(1), seat, entry.marker)).state
            return reduce(scanned, Event.SubroutineReturned(Tick(2), seat, entry.marker, entered))
                .effects.filterNot { it is Effect.SubroutineProgressed }
                .map { it::class.simpleName ?: "?" }
        }
        assertEquals(
            shapeOf(entry.expected), shapeOf(listOf(9, 9, 9)),
            "a wrong entry came back a different shape from a right one",
        )
    }
}

class OpeningCooldownTest {

    /** **D-132 — every Insider ability starts the round already running, at half.** */
    @Test
    fun `every seat's cooldown starts at half its normal duration`() {
        val state = arm(at = Tick(100))
        assertEquals(8, state.cooldowns.size, "a seat was armed without a cooldown")
        for (seat in seatsOf(8)) {
            val cooldown = assertNotNull(state.cooldownFor(seat, InsiderAbility.Revoke))
            assertEquals(
                Tick(100 + Balance.REVOKE_COOLDOWN / 2), cooldown.readyAt,
                "seat ${seat.index} did not open the round at half a cooldown",
            )
        }
    }

    /**
     * **It is a rule and not a field.** A Revoke armed inside the opening stretch of peace arms
     * nothing — which is what closes the opening-Revoke problem structurally rather than by asking
     * players not to.
     */
    @Test
    fun `a Revoke armed inside the opening stretch arms nothing`() {
        val state = arm()
        val early = reduce(state, Event.RevokeArmed(Tick(1), Seat(1))).state
        assertEquals(emptyList(), early.cooldownArmed, "a Revoke armed during the opening peace")
        val contact = reduce(early, Event.ContactMade(Tick(2), Seat(1), Seat(3)))
        assertFalse(contact.state.isRevoked(Seat(3)), "somebody was Revoked in the opening minute")
        // Rule 1: refused or not, the ability reports firing in exactly the same words.
        assertEquals(
            listOf(Effect.AbilityFired(Seat(1), cooldownStarted = true)), contact.effects,
            "an ability that fired on a spent cooldown said something different",
        )
    }

    /** And once it is off cooldown it arms, and arming it starts a full one. */
    @Test
    fun `arming a Revoke costs a full cooldown`() {
        val state = arm()
        val ready = Balance.REVOKE_COOLDOWN / 2
        val armed = reduce(state, Event.RevokeArmed(Tick(ready), Seat(1))).state
        assertEquals(listOf(Seat(1)), armed.cooldownArmed, "the Revoke did not arm when it was ready")
        assertEquals(
            Tick(ready + Balance.REVOKE_COOLDOWN),
            armed.cooldownFor(Seat(1), InsiderAbility.Revoke)!!.readyAt,
            "arming a Revoke did not start a full cooldown — a botched stalk costs one",
        )
        val again = reduce(armed, Event.RevokeArmed(Tick(ready + 1), Seat(1))).state
        assertEquals(
            listOf(Seat(1)), again.cooldownArmed,
            "arming twice inside one cooldown was accepted",
        )
    }
}
