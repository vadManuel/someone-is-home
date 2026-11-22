package home.someoneshome.app

import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.Effect
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.JamRings
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.ReplayPattern
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.ShortHold
import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.Subroutine
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The house's question, over a real socket, onto the phone's own screens — answered from what
 * the phone holds and graded by the real rules, for all ten Subroutines and for the Insider's fake**
 * (D-238).
 *
 * The bug: every Subroutine screen drew the port's fixture while the house dealt a fresh question
 * per scan and graded against its own. Played from the screen against the real rules before the fix
 * (seeds 20260822, 7, 19, 41 and 101), a correct answer to what the phone showed was graded right
 * 0 of 25 times for Replay, 0 of 12 for Parity Check, 0 of 11 for Deallocate, 0 of 17 for Signal
 * Trace, 1 of 23 for Jam, 8 of 22 for Short, 5 of 22 for Handshake, 12 of 23 for Interrupt and 17 of
 * 23 for Drift. Sniff, buzzed by `HouseBuzzer` off the house's own instance, was 22 of 22.
 *
 * ### Nothing here reads the answer key
 *
 * Every answer is worked out **from what the phone holds** — `SubroutineModel`'s question slots,
 * which are what the screens draw (`HousedSubroutineQuestionTest` in `ui` holds the pixels to
 * them), and the motor's own script for the two that are felt. The house's `OpenSubroutine` is read
 * once per card and only to assert that the parameters the phone decoded are the ones the house
 * drew. A test that answered from `expected` would pass against a phone drawing anything at all.
 *
 * Each phone is built as `CheatRoot` builds it: a real [FlowModel] whose Subroutine `send` reaches
 * a real [RoundClient] over the phone's one socket through a real [LobbyOverTransport], its
 * `onScreen` wired to the [HouseBuzzer] as `Cheats` wires it. The scan is the scan bridge's two
 * halves — `RoundClient.scanning` and `IntentMessage.BeginSubroutine` — and every press after it is
 * `flow.actions()`, the lambdas the controls hold. Faked: Bonjour, the radio (spread out, so nobody
 * touches), and the camera resolving the card.
 */
class SubroutineQuestionWiringTest {

    private class Kept : SeatStore {
        var token: String? = null
        override fun stored(): String? = token
        override fun keep(token: String, hostAddress: String) {
            this.token = token
        }
        override fun forget() {
            token = null
        }
    }

    private class Bonjour : Advertising {
        val ports = mutableListOf<Int>()
        override fun advertise(
            name: String,
            port: Int,
            onEvent: (String) -> Unit,
            onAir: () -> Unit,
            onFailed: (AdvertiseFailure) -> Unit,
        ): () -> Unit {
            ports += port
            onAir()
            return {}
        }
    }

    /** The motor, as a record of every script the house's questions put on it. */
    private class Motor : Haptics {
        val played = mutableListOf<List<HapticStep>>()
        override fun buzz(haptic: Haptic) = Unit
        override fun play(script: List<HapticStep>, repeating: Boolean) {
            played += script
        }
        override fun stop() = Unit
    }

    private class Phone(
        scope: CoroutineScope,
        val name: String,
        val radioSpot: Seat,
        air: SimulatedAir,
        house: () -> RoundAuthority?,
    ) {
        val bonjour = Bonjour()
        val log = mutableListOf<String>()
        val motor = Motor()
        private val wake = ScreenWake(NoIdleTimer)
        private var client: RoundClient? = null
        private val buzzer = HouseBuzzer(motor)

        /** The phone's monotonic clock, as `CheatRoot` hands it down; this one moves when a finger waits. */
        var now: Long = 52_118_904L

        /** `CheatRoot`'s seams: the Subroutine's `send`, the panel's `send`, and the motor on `onScreen`. */
        val flow = FlowModel(
            PanelState(screen = ScreenId.Home),
            subroutines = SubroutineModel(send = { intent -> client?.act(intent) }, monotonicMillis = { now }),
            send = { intent -> client?.act(intent) },
            fireEgress = { client?.fireEgress() },
            onScreen = buzzer::showing,
        )

        val pushed = mutableListOf<RoundMessage>()

        val round = RoundClient(
            scope, flow, LineWatch {}, buzzer, air.radioFor(radioSpot), wake, ranging = air.rangingFor(radioSpot, SimulatedAir.BENCH_CAPACITY),
        ) { log += it }.also { made ->
            client = made
            made.onMessage = { pushed += it }
        }

        val link = LobbyOverTransport(
            scope, onEvent = { log += it }, seat = Kept(),
            onIntent = HouseDoors(house)::heard,
            onEgressFired = HouseDoors(house)::fired,
            onCardRead = HouseDoors(house)::read,
            onSighted = HouseDoors(house)::sighted,
            onRadioFaulted = HouseDoors(house)::faulted,
            round = round, wake = wake, advertising = bonjour,
        )
        val model = LobbyModel(link = link).also { it.nameResident(name) }
    }

    private class Room(val phones: List<Phone>, val house: RoundAuthority) {
        fun phoneAt(seat: Seat): Phone {
            val name = house.state.names.single { it.seat.index == seat.index }.name
            return phones.single { it.name == name }
        }
    }

    private val home = SavedHomesModel.sample().homes.first()

    private suspend fun until(what: String, detail: () -> String = { "" }, condition: () -> Boolean) {
        try {
            withTimeout(20_000) { while (!condition()) delay(10) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what ${detail()}", late)
        }
    }

    private fun evening(seed: Long, body: suspend CoroutineScope.(Room) -> Unit) = runBlocking {
        val job = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = CoroutineScope(coroutineContext + job)
        val phones = mutableListOf<Phone>()
        try {
            scope.async {
                val air = SimulatedAir()
                var authority: RoundAuthority? = null
                val host = Phone(scope, "HOST", Seat(0), air) { authority }.also { phones += it }
                authority = RoundAuthority(
                    inputs = { host.link.house.armingInputs() },
                    emit = { effect, state -> host.link.house.emit(effect, state) },
                    note = {},
                )
                host.model.hostHome(home)
                until("the host walked into its own lobby") { host.model.hosting && host.model.standing.joined == 1 }
                val port = host.bonjour.ports.single()
                listOf("ROSE", "DANI", "MARCUS", "PRIYA").forEachIndexed { i, name ->
                    val guest = Phone(scope, name, Seat(i + 1), air) { authority }.also { phones += it }
                    guest.model.attachTo(NearbyHome(home.name, "127.0.0.1", port))
                    until("$name was seated") { host.model.standing.joined == i + 2 }
                }
                phones.forEachIndexed { i, p ->
                    p.model.typeLine("line $i")
                    check(p.model.handOverLine()) { "the line was refused" }
                }
                until("every line reached the house") { host.model.standing.linesIn == phones.size }
                air.spreadOut(phones.map { it.radioSpot })
                assertTrue(armAtLightsOut(authority, hosting = true, seed = seed, note = {}), "the round never armed")
                until("every phone holds its work order") { phones.all { it.flow.state.order != null } }
                scope.body(Room(phones, authority))
            }.await()
        } finally {
            phones.forEach { it.link.leave() }
            phones.forEach { it.link.house.closing?.join() }
            job.cancel()
        }
    }

    // ---- What the phone holds, and the answers worked out from it ---------------------------------

    /** What the screen of [kind] draws, read off the model — the same projection as in `ui`. */
    private fun drawnFrom(m: SubroutineModel, kind: SubroutineKind): Any = when (kind) {
        SubroutineKind.Replay -> listOf(m.pattern.order, m.replay.length)
        SubroutineKind.Interrupt -> m.sweep.let { listOf(it.bandAt, it.bandHalf, it.speed, it.phase) }
        SubroutineKind.ParityCheck -> m.grid
        SubroutineKind.Sniff -> Unit
        SubroutineKind.Deallocate -> listOf(m.columns, m.deallocate.columns)
        SubroutineKind.Drift -> m.path.let { listOf(it.lane, it.cover, it.speed, it.askAtMillis, it.hitRadius) }
        SubroutineKind.Short -> m.hold.fingers
        SubroutineKind.SignalTrace -> m.wiring.let { listOf(it.nodes, it.edges, it.source, it.sink) }
        SubroutineKind.Jam -> m.rings.opening
        SubroutineKind.Handshake -> m.handshake.length
    }

    /** The same projection off the reader, for the parameters that came down the socket. */
    private fun asked(kind: SubroutineKind, p: List<Int>): Any = when (kind) {
        SubroutineKind.Replay -> ReplayPattern.of(p).order.let { listOf(it, it.size) }
        SubroutineKind.Interrupt -> InterruptSweep.of(p).let { listOf(it.bandAt, it.bandHalf, it.speed, it.phase) }
        SubroutineKind.ParityCheck -> ParityGrid.of(p)
        SubroutineKind.Sniff -> Unit
        SubroutineKind.Deallocate -> DotColumns.of(p).let { listOf(it, it.size) }
        SubroutineKind.Drift -> DriftPath.of(p).let { listOf(it.lane, it.cover, it.speed, it.askAtMillis, it.hitRadius) }
        SubroutineKind.Short -> ShortHold.of(p).fingers
        SubroutineKind.SignalTrace -> SignalGraph.of(p).let { listOf(it.nodes, it.edges, it.source, it.sink) }
        SubroutineKind.Jam -> JamRings.of(p).opening
        SubroutineKind.Handshake -> HandshakeRhythm.of(p).beats
    }

    /** The shortest route over the lattice the phone draws — the player's work, done by a test. */
    private fun shortest(w: SignalGraph.Wiring): List<Int> {
        var live = listOf(listOf(w.source))
        repeat(w.nodes.size) {
            live.firstOrNull { it.last() == w.sink }?.let { return it }
            live = live.flatMap { r -> w.joinedTo(r.last()).filter { it !in r }.sorted().map { r + it } }
        }
        error("the lattice the phone draws has no route from end to end")
    }

    /** Sniff's two groups as the motor buzzed them: split at the one rest that is not a group's gap. */
    private fun felt(script: List<HapticStep>): Pair<Int, Int> {
        val pause = script.indexOfFirst { it is HapticStep.Rest && it.millis != SniffGroups.PULSE_GAP_MILLIS }
        check(pause > 0) { "the motor's Sniff has no pause between two groups: $script" }
        return script.take(pause).count { it is HapticStep.Buzz } to
            script.drop(pause + 1).count { it is HapticStep.Buzz }
    }

    /**
     * **Work [subroutine] on the phone's own screens, rightly or wrongly, from what the phone holds.**
     *
     * Returns false where the screen offers no wrong answer at all. Since D-296 there is none:
     * Handshake's wrong answer is its rhythm returned with every gap half again too long.
     */
    private fun Phone.work(subroutine: Subroutine, right: Boolean): Boolean {
        val a = flow.actions()
        val m = flow.subroutines
        val s = subroutine
        when (s) {
            Subroutine.Replay -> {
                val order = m.pattern.order
                (if (right) order else order.reversed()).forEach { a.tapSubroutine(s, it) }
            }
            Subroutine.Handshake -> {
                // What the motor buzzed is what the screen owes: one tap per beat, and the rests
                // between the buzzes are the gaps the finger returns (D-296). Wrong is every gap
                // half again as long, outside the ±40 % the house grades within.
                val script = motor.played.last()
                val beats = script.count { it is HapticStep.Buzz }
                assertEquals(beats, m.handshake.length, "the motor buzzed $beats beats and the screen owes ${m.handshake.length}")
                val rests = script.filterIsInstance<HapticStep.Rest>().map { it.millis.toLong() }
                repeat(m.handshake.length) { at ->
                    if (at > 0) now += if (right) rests[at - 1] else rests[at - 1] * 3 / 2
                    a.tapSubroutine(s, at)
                }
            }
            Subroutine.ParityCheck -> {
                val g = m.grid
                val odd = g.indices.single { g[it] != ParityGrid.clean(it) }
                a.tapSubroutine(s, if (right) odd else (odd + 1) % g.size)
                a.handOverSubroutine(s)
            }
            Subroutine.Short -> {
                val f = m.hold.fingers
                a.tapSubroutine(s, if (right) f else f + 1)
                a.handOverSubroutine(s)
            }
            Subroutine.Jam -> {
                val opening = m.rings.opening
                if (right) repeat(abs(opening)) { a.tapSubroutine(s, if (opening < 0) 1 else -1) }
                a.handOverSubroutine(s)
            }
            Subroutine.SignalTrace -> {
                val route = shortest(m.wiring)
                (if (right) route else route.take(1)).forEach { a.tapSubroutine(s, it) }
                a.handOverSubroutine(s)
            }
            Subroutine.Sniff -> {
                val (first, second) = felt(motor.played.last())
                val bigger = if (first > second) SubroutineModel.SNIFF_FIRST else SubroutineModel.SNIFF_SECOND
                a.tapSubroutine(s, if (right) bigger else 1 - bigger)
                a.handOverSubroutine(s)
            }
            Subroutine.Deallocate -> {
                val columns = m.columns
                val level = columns.min()
                columns.forEachIndexed { at, height -> repeat(height - level) { a.tapSubroutine(s, at) } }
                if (!right) a.tapSubroutine(s, columns.indexOf(level))
                a.handOverSubroutine(s)
            }
            Subroutine.Interrupt -> {
                val sweep = m.sweep
                val outside = if (sweep.bandTo + 3 <= InterruptSweep.SPAN) sweep.bandTo + 3 else sweep.bandFrom - 3
                a.tapSubroutine(s, if (right) sweep.bandAt else outside)
            }
            Subroutine.Drift -> {
                val path = m.path
                // The motor's *now* is the screen's *now*: one wait, off one instance.
                assertEquals(path.script, motor.played.last(), "the motor buzzed a different Drift from the one drawn")
                val at = path.at(path.askAtMillis.toLong()) ?: error("the drawn dot has left the lane at the buzz")
                val far = path.hitRadius + 5
                val outside = if (at + far <= DriftPath.SPAN) at + far else at - far
                a.tapSubroutine(s, if (right) at else outside)
                a.handOverSubroutine(s)
            }
        }
        return true
    }

    /** One graded attempt, as the record the sweep keeps. */
    private data class Attempt(val kind: SubroutineKind, val role: Role, val right: Boolean)

    /**
     * **Scan, BEGIN, work it, and read the house's verdict off this phone's own socket.**
     *
     * Null where the house had nothing for this seat at [marker] (a lawful answer, D-124), or where
     * the screen offers no wrong answer to give.
     */
    private suspend fun Room.attempt(seat: Seat, marker: MarkerId, right: Boolean): Attempt? {
        val phone = phoneAt(seat)
        val role = house.state.roleOf(seat)
        val scans = phone.pushed.count { it is RoundMessage.ScanAnswered }
        phone.round.scanning(marker)
        phone.round.readCard(marker)
        until("${phone.name}'s scan was answered") { phone.pushed.count { it is RoundMessage.ScanAnswered } > scans }
        val opened: SubroutineInstance = (phone.pushed.last { it is RoundMessage.ScanAnswered } as RoundMessage.ScanAnswered)
            .opened ?: return null
        until("${phone.name} reached the caught scan") { phone.flow.state.screen == ScreenId.ScanCaught }

        // The house drew it; this is the one read of the authority, and it is not the answer.
        val drawn = house.state.openSubroutineFor(seat) ?: error("the house answered a scan it holds no work for")
        assertEquals(drawn.parameters, opened.parameters, "the phone decoded parameters the house never drew")
        assertEquals(drawn.subroutine, opened.subroutine)

        val subroutine = Subroutine.of(opened.subroutine)
        phone.flow.actions().beginSubroutine()
        assertEquals(subroutine.screen, phone.flow.state.screen, "BEGIN did not open the ${subroutine.label} the house dealt")
        assertEquals(
            asked(opened.subroutine, opened.parameters), drawnFrom(phone.flow.subroutines, opened.subroutine),
            "${subroutine.label}: the phone holds a question the house did not deal",
        )

        val integrity = house.state.systemIntegrity
        val progressed = house.emitted.count { it is Effect.SubroutineProgressed }
        val graded = phone.pushed.count { it is RoundMessage.SubroutineGraded }
        if (!phone.work(subroutine, right)) {
            phone.flow.actions().stopSubroutine()
            return null
        }
        until(
            "${phone.name}'s ${subroutine.label} was graded (right=$right)",
            { "refusals=${house.refusals.takeLast(3)} heard=${house.heard.takeLast(3)} entry=${phone.flow.subroutines.entry(subroutine)?.entered} log=${phone.log.takeLast(6)}" },
        ) {
            phone.pushed.count { it is RoundMessage.SubroutineGraded } > graded
        }
        val verdict = phone.pushed.last { it is RoundMessage.SubroutineGraded } as RoundMessage.SubroutineGraded
        assertEquals(
            right, verdict.accepted,
            "${subroutine.label} ($role) answered ${if (right) "rightly" else "wrongly"} from what the phone " +
                "holds and the house graded it ${verdict.accepted}: parameters=${opened.parameters}",
        )

        val banks = right && role == Role.Resident
        assertEquals(
            if (banks) integrity - 1 else integrity, house.state.systemIntegrity,
            "${subroutine.label} ($role, ${if (right) "right" else "wrong"}) moved System Integrity wrongly",
        )
        if (!banks) {
            assertEquals(
                progressed, house.emitted.count { it is Effect.SubroutineProgressed },
                "${subroutine.label} ($role) wrote progress — the fake must write nothing",
            )
        }
        return Attempt(opened.subroutine, role, right)
    }

    /**
     * Every card on every seat's own order, each answered wrongly and then — after a re-scan, which
     * re-draws the question (D-110) — rightly. The Insiders first, so a Residents' win cannot end the
     * round before the fakes have been played — and the Residents stop one bank short of clearing
     * System Integrity: the round ending is not what this file is about, and the entry that ends it
     * is answered by the ending rather than by a verdict.
     */
    private suspend fun Room.sweep(): List<Attempt> {
        val done = mutableListOf<Attempt>()
        val seats = house.state.seats.sortedBy { if (house.state.roleOf(it) == Role.Resident) 1 else 0 }
        for (seat in seats) {
            val tried = mutableSetOf<String>()
            while (!house.ended) {
                if (house.state.roleOf(seat) == Role.Resident && house.state.systemIntegrity <= 1) break
                val phone = phoneAt(seat)
                val marker = phone.flow.state.order.orEmpty().filterIsInstance<OrderLine.Known>()
                    .filter { !it.done }.flatMap { it.at }.map { it.marker }
                    .firstOrNull { it.value !in tried } ?: break
                tried += marker.value
                attempt(seat, marker, right = false)?.let { done += it }
                if (house.ended) break
                attempt(seat, marker, right = true)?.let { done += it }
            }
        }
        return done
    }

    /**
     * **GUARD (D-238): all ten Subroutines, for a Resident and for the Insider's fake: the phone
     * holds the house's question, a right answer to it is graded right, a wrong one wrong, and only
     * a Resident's right answer writes anything.**
     *
     * The seeds are pinned, and chosen so that between them every kind is dealt to an Insider and to
     * a Resident. Every kind is graded right and wrong for both, Handshake too since D-296.
     */
    @Test
    fun theHousesQuestionIsTheOneOnThePhoneAndTheOneItGrades() {
        val attempts = mutableListOf<Attempt>()
        for (seed in SEEDS) evening(seed) { room -> attempts += room.sweep() }
        println(
            "GRADED ${attempts.size} ENTRIES: " + attempts.groupingBy { "${it.role}/${if (it.right) "right" else "wrong"}" }
                .eachCount().entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" },
        )

        for (role in listOf(Role.Resident, Role.Insider)) {
            for (right in listOf(true, false)) {
                val reached = attempts.filter { it.role == role && it.right == right }.map { it.kind }.toSet()
                val owed = SubroutineKind.entries.toSet()
                assertEquals(
                    owed, reached,
                    "these were never graded ${if (right) "right" else "wrong"} for a $role: ${owed - reached}",
                )
            }
        }
    }

    private companion object {
        val SEEDS = listOf(20261150L, 7L, 19L)
    }
}
