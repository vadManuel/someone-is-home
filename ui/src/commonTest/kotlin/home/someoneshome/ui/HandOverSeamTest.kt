package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **BEGIN carries the card, or the return half of the game does not run** — the payoff run's top
 * finding, written as the two assertions that were red when it was found.
 *
 * ### What was broken, and how it presented
 *
 * `Flow.viaActions` wired the button as `beginSubroutine = { beginSubroutine() }` — **both
 * parameters defaulted**. The first default is the port's fixture, so every BEGIN on every phone in
 * every round opened Handshake whatever the house had dealt; the second is `null`, so
 * `SubroutineModel.posted` refused the hand-over and the screen sat on `RETURNED . WAITING` for a
 * verdict nobody had been asked for. A person hit both in one evening and reported them as two
 * bugs — *"all tasks are the same"* and *"just hanging on returned - waiting"* — and they are one
 * line.
 *
 * The payoff run reproduced it with a throwaway probe and then **deleted the probe**, because a
 * test that asserts today's broken behaviour is a test somebody has to delete before they can fix
 * it. This file is that probe written the other way round: it is the probe's exact shape, asserting
 * what the game needs rather than what it did.
 *
 * ### Why it is driven through [PanelActions] and never through [FlowModel] directly
 *
 * **The defect was in the wiring, not in the method.** `FlowModel.beginSubroutine(subroutine,
 * marker)` was correct and tested — `SubroutineTest` has been calling it with an explicit
 * Subroutine for units — and every one of those tests stayed green through the whole outage,
 * because none of them went through the lambda the button actually holds. `ScreensWork` binds
 * `onClick = actions.beginSubroutine`, so the actions layer **is** the finger, and a guard that
 * skips it is a guard that cannot see this bug.
 *
 * ### The no-house contract is asserted here too
 *
 * `beginSubroutine`'s KDoc has always promised that a phone with no house attached opens the work,
 * takes the answer and *posts it nowhere*. That promise is what made the outage invisible — the
 * application was permanently in the bench's mode — so it is pinned in the same file as the fix, in
 * [aPhoneWithNoHouseOpensTheFixtureAndPostsNowhere].
 */
class HandOverSeamTest {

    private val hall = MarkerId("card-hall")
    private val study = MarkerId("card-study")

    /**
     * This seat's own order: a Replay it has done and a Jam it has not, each at a card.
     *
     * The Jam is entry 1 deliberately. `currentFrom` joins the scan's answer to the order on the
     * house's own index, so an entry at position 0 would pass a join that ignored the index.
     */
    private val order: List<OrderLine> = listOf(
        OrderLine.Known(
            index = 0,
            work = AssignedWork.Subroutine(SubroutineKind.Replay),
            done = true,
            at = listOf(Destination(hall, shape = "diamond", room = "HALL")),
        ),
        OrderLine.Known(
            index = 1,
            work = AssignedWork.Subroutine(SubroutineKind.Jam),
            done = false,
            at = listOf(Destination(study, shape = "square_frame", room = "STUDY")),
        ),
    )

    /** Everything this phone put on the wire, in order. The evidence, and the whole of it. */
    private class Wire {
        val sent = mutableListOf<IntentMessage>()
        val handOvers: List<IntentMessage.HandOverEntry>
            get() = sent.filterIsInstance<IntentMessage.HandOverEntry>()
    }

    private fun phone(wire: Wire): FlowModel = FlowModel(
        PanelState(screen = ScreenId.Scan),
        subroutines = SubroutineModel(send = { intent -> wire.sent += intent }),
    )

    /**
     * A card is read and the house answers it — the two acts that precede every BEGIN in play.
     *
     * The order goes first because the caught scan is a **join**: the answer names the entry and
     * the kind, the order names where that entry is. A scan answered against an order this phone
     * has not been issued moves no screen at all, which is the fail-closed direction and is not
     * what this file is about.
     */
    private fun FlowModel.scanned(marker: MarkerId, kind: SubroutineKind, entry: Int) {
        houseSaid(RoundMessage.WorkOrderIssued(order))
        scanning(marker)
        houseSaid(
            RoundMessage.ScanAnswered(
                SubroutineInstance(entry = entry, subroutine = kind, parameters = SubroutineModel.benchParameters(kind)),
            ),
        )
    }

    // ---- 1 · The screen the card opened ----------------------------------------------------------

    /**
     * **RED FIRST: a Jam was dealt, so BEGIN opens the Jam.**
     *
     * Against the broken build this asserted `SubHandshake`, on a seat whose own caught-scan screen
     * said `WORK JAM` one tap earlier — the two phones' ledgers on the night read exactly that
     * disagreement. Nine of the ten Subroutines were unreachable by a finger and the house's draw
     * was healthy the whole time, which is why the report *the draw is stuck* would have sent
     * somebody into `core` for a week.
     */
    @Test
    fun `BEGIN opens the Subroutine the house dealt and not the fixture's`() {
        val wire = Wire()
        val phone = phone(wire)
        phone.scanned(study, SubroutineKind.Jam, entry = 1)

        assertEquals(ScreenId.ScanCaught, phone.state.screen, "the caught scan never opened")
        assertEquals(
            Subroutine.Jam, phone.state.current?.subroutine,
            "the caught scan read the house's answer wrong, and this file is about the tap after it",
        )

        phone.actions().beginSubroutine()

        assertEquals(
            ScreenId.SubJam, phone.state.screen,
            "BEGIN opened a Subroutine the house did not deal — the button is ignoring the card",
        )
    }

    /**
     * **Every kind the draw can deal is reachable by a finger** — the claim the single case above
     * cannot make.
     *
     * A fix that read the dealt kind but mapped it through anything of its own would pass the Jam
     * case and strand some other kind, which is the outage's own shape: one Subroutine reachable,
     * nine not. So the sweep is over `SubroutineKind` — the house's ten, not the client's roster —
     * and it is the house's list that decides when this test needs another row.
     */
    @Test
    fun `every kind the house can deal opens its own screen from BEGIN`() {
        for (kind in SubroutineKind.entries) {
            val wire = Wire()
            val phone = phone(wire)
            phone.houseSaid(RoundMessage.WorkOrderIssued(order))
            phone.scanning(study)
            phone.houseSaid(
                RoundMessage.ScanAnswered(
                    SubroutineInstance(entry = 1, subroutine = kind, parameters = SubroutineModel.benchParameters(kind)),
                ),
            )
            phone.actions().beginSubroutine()

            assertEquals(
                Subroutine.of(kind).screen, phone.state.screen,
                "$kind was dealt and BEGIN opened somewhere else",
            )
        }
    }

    // ---- 2 · The entry, on its way to the house --------------------------------------------------

    /**
     * **RED FIRST: complete the work and the hand-over reaches the wire, naming the card that was
     * scanned.**
     *
     * Against the broken build the wire was empty. `posted` reads `standingAt`, `standingAt` is set
     * by `beginSubroutine`'s marker parameter, and the button passed `null` — so the send was
     * refused at the last line before it, the screen rendered `RETURNED . WAITING` off its own
     * state, and the phone waited for a verdict the house had never been asked for. **Measured on
     * the night across two phones: four `BeginSubroutine` scans up, zero `HandOverEntry`.**
     *
     * The marker is asserted by value rather than merely for presence. A hand-over that named the
     * wrong card is graded, silently, against work the player never did — and nothing on any screen
     * would look wrong.
     */
    @Test
    fun `a completed Subroutine reaches the wire against the card that opened it`() {
        val wire = Wire()
        val phone = phone(wire)
        phone.scanned(study, SubroutineKind.Jam, entry = 1)

        val actions = phone.actions()
        actions.beginSubroutine()
        actions.tapSubroutine(Subroutine.Jam, 1)
        actions.handOverSubroutine(Subroutine.Jam)

        assertEquals(
            1, wire.handOvers.size,
            "the entry went nowhere — this is the hang the owner reported as RETURNED · WAITING",
        )
        assertEquals(
            study, wire.handOvers.single().marker,
            "the entry was filed against a card this phone did not scan",
        )
    }

    /**
     * The same sweep as the screens, one system further on: **every kind can be handed over.**
     *
     * Driven through the actions layer with the input a screen offers — taps, then the hand-over
     * control — because that is what a player has. The three sequences go on their own last element
     * and the rest go on the control; both routes funnel through `posted`, and a fix that carried
     * the marker to one of them and not the other would leave part of the roster hanging exactly
     * the way the whole roster hung.
     */
    @Test
    fun `every kind the house can deal can be handed over against its own card`() {
        for (kind in SubroutineKind.entries) {
            val wire = Wire()
            val phone = phone(wire)
            phone.houseSaid(RoundMessage.WorkOrderIssued(order))
            phone.scanning(study)
            phone.houseSaid(
                RoundMessage.ScanAnswered(
                    SubroutineInstance(entry = 1, subroutine = kind, parameters = SubroutineModel.benchParameters(kind)),
                ),
            )
            val subroutine = Subroutine.of(kind)
            val actions = phone.actions()
            actions.beginSubroutine()
            repeat(8) { actions.tapSubroutine(subroutine, it) }
            actions.handOverSubroutine(subroutine)

            assertTrue(
                wire.handOvers.isNotEmpty(),
                "${subroutine.label}: the work was done and nothing left the phone",
            )
            assertEquals(
                study, wire.handOvers.first().marker,
                "${subroutine.label}: the entry was filed against the wrong card",
            )
        }
    }

    // ---- 3 · The contract the outage was hiding behind --------------------------------------------

    /**
     * **A phone with no house attached opens the work, takes the answer, and posts it nowhere.**
     *
     * `beginSubroutine`'s KDoc has promised this since E-P2-1 and it must keep promising it: the
     * port renders and screenshots every Subroutine with no house in the room, and a BEGIN that
     * refused to open without a card would take the bench's whole roster away.
     *
     * ⚠️ **This is also the disease.** The application was permanently in this mode because its one
     * call site never passed what the house sent, so *the honest bench behaviour* and *the broken
     * product behaviour* were the same behaviour and nothing could tell them apart. Pinning it here
     * is what makes the difference a thing a build can check: **no scan, no marker, no send** —
     * and, one test up, **a scan, a marker, a send.**
     *
     * ⚠️ **The work is finished before the silence is asserted, and the first version of this test
     * did not finish it.** It tapped the fixture's Subroutine once, which for a sequence is a player
     * still mid-answer — so `posted` refused the send for want of a *completed entry* and the
     * missing marker was never the thing under test. It stayed green through an injection that
     * defaulted the marker to a card nobody had read, which is the exact leak it exists to catch.
     * The entry has to be **gone** for the absent card to be the only thing left standing between
     * it and the wire, so that is asserted first and separately.
     */
    @Test
    fun aPhoneWithNoHouseOpensTheFixtureAndPostsNowhere() {
        val wire = Wire()
        val bench = phone(wire)
        val fixture = PanelVals.CURRENT.subroutine

        val actions = bench.actions()
        actions.beginSubroutine()
        assertEquals(
            fixture.screen, bench.state.screen,
            "the port's own BEGIN stopped opening the fixture's Subroutine",
        )

        repeat(8) { actions.tapSubroutine(fixture, it) }
        actions.handOverSubroutine(fixture)
        assertTrue(
            bench.subroutines.entry(fixture)?.gone == true,
            "the fixture's entry never finished, so the assertion below would pass for free",
        )
        assertTrue(
            wire.handOvers.isEmpty(),
            "a phone that has scanned nothing filed an entry against a card it never read",
        )
    }
}
