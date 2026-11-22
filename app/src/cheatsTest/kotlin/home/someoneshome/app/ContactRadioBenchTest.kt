package home.someoneshome.app

import home.someoneshome.core.ContactGate
import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.RadioCause
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.RequestSlot
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.UploadProbe
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.NoRanging
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **The bench, through the fake radio, over real sockets: a round from LIGHTS OUT to an ending with
 * the Revoke and the report made by phones touching** (D-217, unit 3).
 *
 * Five phones, each a real [TransportClient] with its own [RoundOverTransport] whose radio is a
 * [SimulatedRadio] in one shared [SimulatedAir]; a real [TransportHost] and [EffectWire]; the real
 * [RoundAuthority]. Every phone uploads up its own socket on the beat; the host's `onFrame` puts
 * each upload on one ordered queue exactly as `LobbyHouse` does (that class cannot be started in a
 * test, E-P2-2), and the queue's one consumer calls [RoundAuthority.sighted]. Nothing mints a
 * contact or a report: the rig walks phones together, and the resolver concludes.
 *
 * The meeting's timed transitions are minted here, as `tickHouse` would mint them; the presses in
 * it are requests through [RoundAuthority.heard], as the desk would put them.
 *
 * **On distance, every round** (D-301, D-308: there are no Bluetooth rounds). Every phone has a
 * [SimulatedRanging] in the same air at the phones' own three peers; Bluetooth only ranks who is
 * near, and the touch, the held report and its circle's slot are the distance rules', over the same
 * sockets. `rig(without = seat)` gives one phone no UWB radio (capacity 0), which nobody can touch
 * or be touched by; `rig(ranging = false)` gives none, as five simulators have, for the rehearsal's
 * stand-in ([HouseAir]). Every upload comes in by [StandInDoor], the app root's own door.
 */
class ContactRadioBenchTest {

    private val seats = List(5) { Seat(it) }

    private val inputs = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
    )

    /** One window for every clock in the rig: the house and five phones agree, as wall clocks do. */
    private val window = ContactWindow(117_000L)

    private inner class Rig(
        private val scope: CoroutineScope,
        /** A phone with no UWB radio (D-308): it says capacity 0 and is paired with nobody. */
        val without: Seat? = null,
        /** Whether the phones range at all: false is five simulators, for the rehearsal's stand-in. */
        val ranging: Boolean = true,
    ) {
        val air = SimulatedAir()

        /** Every answer to an upload each phone's socket delivered, by its length on the wire (D-301). */
        val answers = seats.associate { it.index to mutableListOf<Int>() }

        /** Every report slot each phone read off its answers, one per beat (D-301). */
        val slots = seats.associate { it.index to mutableListOf<ReportSlot>() }
        val said = mutableListOf<String>()
        private val arrivals = Channel<Pair<Seat, RoundBody.Sighted>>(Channel.UNLIMITED)
        private val faults = Channel<Pair<Seat, RoundBody.RadioFaulted>>(Channel.UNLIMITED)

        /** Every radio-fault body the host took, raw, per seat (T2, D-222). */
        val faultBodies = seats.associate { it.index to mutableListOf<String>() }
        var faultsTaken = 0
        private val seated = Channel<Seat>(Channel.UNLIMITED)

        /** Every upload the host took, per seat, as it arrived on the socket. */
        val uploads = seats.associate { it.index to mutableListOf<String>() }
        var taken = 0

        /** Every push each phone's socket delivered, decoded. */
        val pushed = seats.associate { it.index to mutableListOf<RoundMessage>() }

        /** Every seat token the ledger minted, from the secure source production uses (D-215). */
        val tokens = mutableListOf<SeatToken>()
        private val host = TransportHost(
            SeatLedger(seats, mint = { mintSeatToken().also { tokens += it } }),
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { seat, frame ->
                // `LobbyHouse.onFrame`'s round half for this one body: onto the ordered queue.
                val raw = (frame as? TransportFrame.Carry)?.body
                val body = raw?.let(RoundWire::decodeOrNull)
                if (body is RoundBody.Sighted) arrivals.trySend(seat to body)
                if (body is RoundBody.RadioFaulted) {
                    faultBodies.getValue(seat.index) += raw
                    faults.trySend(seat to body)
                }
            },
            onSeated = { seated.trySend(it) },
        )
        private val effects = EffectWire(send = { seat, frame -> host.send(seat, frame) })
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> effects.emit(effect, state) },
            note = { said += it },
            contactWindow = { window },
        )
        val phones = mutableMapOf<Int, RoundOverTransport>()

        /** The app root's upload door (D-308): the rules', with the rehearsal's stand-in when it is on the air. */
        val door = StandInDoor(HouseDoors { house })

        /** Each phone's panel (D-307): where its person is, which every upload tells the house. */
        val panels = mutableMapOf<Int, FlowModel>()
        private val jobs = mutableListOf<Job>()

        suspend fun start() {
            val port = host.start(0)
            jobs += scope.launch {
                for ((seat, body) in arrivals) {
                    uploads.getValue(seat.index) += body.slots
                    val plan = door.sighted(seat, body) ?: error("the door had no house to ask")
                    // **Answered as `LobbyHouse.answerUpload` answers** (D-263, D-300, D-301): one
                    // `UploadHeard` for every upload, its slot's id and the house's plan for the
                    // phone, report slot and all, down that phone's own socket.
                    RequestSlot.read(body.ask)?.let { slot ->
                        val answer = RoundBody.UploadHeard.of(slot.id, UploadProbe.NONE, 0L, 0L, plan, home.someoneshome.model.protocol.OccupancyWire.blank(ByteArray(home.someoneshome.model.protocol.OccupancyWire.FIELD_BYTES)))
                        host.send(seat, TransportFrame.Carry(RoundWire.encode(answer)))
                    }
                    taken++
                }
            }
            jobs += scope.launch {
                for ((seat, report) in faults) {
                    house.radioFaulted(seat, report.cause, report.onset)
                    faultsTaken++
                }
            }
            for (seat in seats) {
                lateinit var round: RoundOverTransport
                val client = TransportClient(
                    ClientSession(), "127.0.0.1", port,
                    nowMillis = { monotonicNanos() / 1_000_000 },
                    onFrame = { frame -> round.frameArrived(frame) },
                )
                val panel = FlowModel(PanelState(screen = ScreenId.Home))
                panels[seat.index] = panel
                round = RoundOverTransport(
                    scope, panel,
                    buzzer = HouseBuzzer(SilentHaptics),
                    send = { frame -> client.send(frame) },
                    radio = air.radioFor(seat),
                    wake = home.someoneshome.platform.ScreenWake(home.someoneshome.platform.NoIdleTimer),
                    windowNow = { window },
                    onInbound = { body ->
                        when (val decoded = RoundWire.decodeOrNull(body)) {
                            is RoundBody.Push -> pushed.getValue(seat.index) += decoded.message
                            is RoundBody.UploadHeard -> answers.getValue(seat.index) += body.length
                            else -> Unit
                        }
                    },
                    ranging = if (ranging && seat != without) air.rangingFor(seat, UWB_CAPACITY) else NoRanging,
                )
                phones[seat.index] = round
                jobs += scope.launch { client.run() }
                jobs += scope.launch { round.run() }
                assertEquals(seat, withTimeout(30_000) { seated.receive() })
            }
            air.spreadOut(seats)
        }

        /** Every phone that is uploading beats once; returns when the house has taken every upload. */
        suspend fun beat() {
            val expected = taken + phones.values.count { it.uploading }
            for (seat in seats) phones.getValue(seat.index).beat()
            withTimeout(30_000) { while (taken < expected) delay(5) }
            // Let the pushes the uploads produced reach the phones before anybody looks.
            delay(SETTLE_MILLIS)
            for (seat in seats) slots.getValue(seat.index) += phones.getValue(seat.index).report
        }

        /** **One second**: the house's clock a second on, then every phone beats (D-301's holds are on that clock). */
        suspend fun second() {
            house.advanceTo(Tick(house.clock + ContactGate.UPLOAD_TICKS))
            beat()
        }

        /**
         * **Seconds until the pairing settles** (D-300, D-302): every phone ranges exactly the pairs
         * the house keeps with it in them, and nothing changed over the last two seconds. Five phones
         * at three cannot range every pair; a touch or a hold between two phones the house is not
         * ranging displaces a kept pair, inside [HELD_SECONDS]. **Since D-306 a settled room may keep
         * no pair at all**: spread out a room apart, no two phones are close, and the house pairs
         * only close phones the game needs, so the touch or hold is what makes its pair.
         */
        suspend fun ranged() {
            val ranging = seats.filter { it != without }
            var last: List<RangingHandle>? = null
            var still = 0
            repeat(20) {
                val kept = house.ranging?.pairs().orEmpty()
                val held = ranging.all { seat ->
                    air.rangingFor(seat).ranging.toSet() == kept.filter { it.has(seat) }.map { it.handle }.toSet()
                }
                val handles = kept.map { it.handle }
                still = if (held && handles == last) still + 1 else 0
                if (still >= 2) return
                last = handles
                second()
            }
            fail("the phones never settled on the house's pairs: ${house.ranging?.pairs()?.size} kept")
        }

        /**
         * **[mover] held [metres] from [still], a second at a time, until [until] or [HELD_SECONDS]**
         * (D-308): the pair is formed once Bluetooth says close and a rule needs it, started on both
         * phones, and read; then [mover] walks back to where it stood.
         */
        suspend fun holdAt(mover: Seat, still: Seat, metres: Double = SimulatedAir.TOUCH_METRES, until: () -> Boolean = { false }) {
            val from = air.spotOf(mover)
            val at = air.spotOf(still)!!
            air.place(mover, SimulatedAir.Spot(at.x + metres, at.y))
            repeat(HELD_SECONDS) { if (!until()) second() }
            from?.let { air.place(mover, it) }
        }

        /** [insider]'s armed phone held against [target]'s until the house Revokes it. */
        suspend fun revokeByTouch(insider: Seat, target: Seat) {
            holdAt(insider, target) { house.state.isRevoked(target) }
            assertTrue(house.state.isRevoked(target), "the touch did not Revoke the target by distance: ${house.refusals}")
        }

        /**
         * **Every seat's push count, once nothing is still on its way.** A snapshot taken straight
         * after arming could catch one seat a frame behind the rest — that frame was already in
         * flight, and landing during the act made a clean run read as "a frame reached a phone"
         * under a loaded machine. So the count is read only after it has held still for three
         * settle windows running; what the act then adds is the act's alone.
         */
        suspend fun settledFrames(): Map<Int, Int> {
            fun counts() = seats.associate { it.index to pushed.getValue(it.index).size }
            var last = counts()
            var still = 0
            withTimeout(30_000) {
                while (still < 3) {
                    delay(SETTLE_MILLIS)
                    val now = counts()
                    still = if (now == last) still + 1 else 0
                    last = now
                }
            }
            return last
        }

        /** LIGHTS OUT through production's own arming path, its log lines into [said]. */
        suspend fun armed(seed: Long = SEED) {
            assertTrue(armAtLightsOut(house, hosting = true, seed = seed, note = { said += it }), "the bench never armed a round")
            withTimeout(30_000) { while (phones.values.any { !it.uploading }) delay(10) }
        }

        fun stop() {
            jobs.forEach { it.cancel() }
        }

        suspend fun close() {
            stop()
            host.stop()
        }

        /** Push the house's clock to [at] and arm [insider]'s Revoke, through the gate as a request. */
        suspend fun armRevoke(insider: Seat) {
            val ready = house.state.cooldownFor(insider, InsiderAbility.Revoke)?.readyAt?.step ?: 0L
            house.advanceTo(Tick(ready))
            house.heard(Intent.ArmRevoke(insider))
            assertTrue(house.state.cooldownArmed.any { it.index == insider.index }, "the Revoke never armed")
        }
    }

    private fun rig(without: Seat? = null, ranging: Boolean = true, block: suspend Rig.() -> Unit) = runBlocking {
        val rig = Rig(this, without, ranging)
        try {
            rig.start()
            rig.block()
        } finally {
            rig.close()
        }
    }

    // ---- 1 · The round, to an ending ------------------------------------------------------------

    /**
     * **D-301 on the bench, over real sockets: an armed Insider's phone held against the target's
     * Revokes it by distance, and a living phone held within 30 cm of the Revoked one for two seconds
     * calls the meeting, its circle's slot filling on its own phone alone; the round plays to an
     * ending.** Every round is this one (D-308).
     */
    @Test
    fun theBenchPlaysARoundToAnEndingOnDistance() = rig {
        armed()
        playToAnEnding()
    }

    /** A Revoke, its report, a meeting that Restrains the Insider: the round to an ending, on distance. */
    private suspend fun Rig.playToAnEnding() {
        val state = { house.state }
        val insider = state().livingInsiders.single()
        val target = state().livingPlainResidents.first()
        val reporter = state().livingPlainResidents.first { it.index != target.index }

        // Nobody touching: a few seconds of uploads from every phone, and the house concludes nothing.
        repeat(2) { second() }
        assertTrue(phones.values.all { it.uploading }, "a phone was not uploading in the round")
        armRevoke(insider)
        ranged()
        assertFalse(house.emitted.any { it is home.someoneshome.model.Effect.ContactAnswered }, "a contact was minted from nobody touching")

        // The Revoke: held together, a second at a time, until the house concludes.
        revokeByTouch(insider, target)
        assertTrue(RoundMessage.AbilityFired(RoundMessage.AbilityFired.LANDED_AT_A_TOUCH) in pushed.getValue(insider.index), "the Insider's phone was never told its Revoke landed")
        assertTrue(RoundMessage.RevokeLanded.Buzzing in pushed.getValue(target.index), "the target's phone was never told")
        assertTrue(phones.getValue(target.index).outOfTheRound, "the Revoked phone does not know it is out")
        air.spreadOut(seats)
        repeat(ContactGate.PAIR_GRACE_TICKS.toInt() / ContactGate.UPLOAD_TICKS.toInt()) { second() }
        assertEquals(null, state().meeting, "a meeting was called with nobody near the Revoked phone")

        // The report: the reporter stands 20 cm from the Revoked phone and holds there.
        val from = slots.getValue(reporter.index).size
        holdAt(reporter, target, metres = 0.20) { state().meeting != null }
        val meeting = assertNotNull(state().meeting, "two seconds held at 20 cm called no meeting: ${house.refusals}")
        assertEquals(reporter, meeting.caller, "the meeting was called by somebody else")
        assertEquals(MeetingTrigger.RevokeReported(target), meeting.trigger)
        val held = slots.getValue(reporter.index).drop(from)
        assertTrue(held.any { it is ReportSlot.Pending }, "the reporter's phone was never told its report was held: $held")
        for (seat in seats.filter { it != reporter }) {
            assertTrue(slots.getValue(seat.index).all { it == ReportSlot.Idle }, "seat ${seat.index}'s phone was told of a hold: ${slots.getValue(seat.index)}")
        }
        meetingToAnEnding(insider)
    }

    /** The meeting, to the Restraint of [insider]: every seat checks in, the living vote, the house closes each phase. */
    private suspend fun Rig.meetingToAnEnding(insider: Seat) {
        val state = { house.state }
        for (seat in seats) house.heard(Intent.CheckIn(seat))
        if (state().meeting?.phase == MeetingPhase.Discussion) house.mint(Event.DiscussionClosed(house.now()))
        assertEquals(MeetingPhase.Vote, state().meeting?.phase, "the ballot never opened")
        for (seat in state().livingSeats) {
            house.heard(Intent.SelectVote(seat, insider))
            house.heard(Intent.LockVote(seat))
        }
        if (state().meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
        house.mint(Event.TallyHalfwayReached(house.now()))
        if (!house.ended) house.mint(Event.MeetingClosed(house.now()))
        delay(SETTLE_MILLIS)

        assertTrue(house.ended, "the round did not end: ${state().outcome} · ${house.refusals}")
        assertEquals(Winner.Residents, state().outcome?.winner, "the only Insider was Restrained and the Residents did not win")
        for (seat in seats) {
            assertTrue(
                pushed.getValue(seat.index).any { it is RoundMessage.RoundEnded },
                "seat ${seat.index}'s phone never heard the round end",
            )
        }
    }

    // ---- D-301: the rules read UWB distance ------------------------------------------------------

    /**
     * **GUARD (D-217, D-301): every answer is one width on every phone, whatever its report slot
     * says**: the Insider's, the Revoked phone's, the reader's mid-hold and every Resident's, from
     * arming to the call. A frame only a reporting phone receives would be a find anybody on the
     * Wi-Fi could see; there is none, only a sealed slot in the answer everybody gets.
     */
    @Test
    fun everyAnswerIsOneWidthOnEveryPhoneWhileAReportIsHeld() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        air.touch(insider, target)
        repeat(HELD_SECONDS) { if (!house.state.isRevoked(target)) second() }
        assertTrue(house.state.isRevoked(target), "the touch revoked nobody: ${house.refusals}")
        air.spreadOut(seats)
        repeat(5) { second() }
        val near = air.spotOf(target)!!
        air.place(reporter, SimulatedAir.Spot(near.x + 0.10, near.y))
        repeat(HELD_SECONDS) { if (house.state.meeting == null) second() }
        assertNotNull(house.state.meeting, "the hold called no meeting")
        delay(SETTLE_MILLIS)
        val widths = answers.mapValues { (_, w) -> w.toSet() }
        assertEquals(1, widths.values.flatten().toSet().size, "answers differ in width by seat or by moment: $widths")
        for (seat in seats) {
            assertEquals(
                uploads.getValue(seat.index).size, answers.getValue(seat.index).size,
                "seat ${seat.index} was not sent exactly one answer for each upload",
            )
            assertTrue(answers.getValue(seat.index).size >= 10, "seat ${seat.index} was answered ${answers.getValue(seat.index).size} times")
        }
        assertTrue(slots.getValue(reporter.index).any { it is ReportSlot.Pending }, "nothing was held on the reader's phone, so this compared nothing")
    }

    /** **GUARD (D-301): leaving reach before two seconds cancels the hold, on the reader's phone, and calls nothing.** */
    @Test
    fun walkingAwayBeforeTwoSecondsCancelsTheHold() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        air.touch(insider, target)
        repeat(HELD_SECONDS) { if (!house.state.isRevoked(target)) second() }
        air.spreadOut(seats)
        repeat(5) { second() }
        val home = air.spotOf(reporter)!!
        val near = air.spotOf(target)!!
        air.place(reporter, SimulatedAir.Spot(near.x + 0.25, near.y))
        // The first second the pair is ranged and in reach, the hold begins (D-302: a pair the house
        // was not ranging is paired within a few seconds).
        repeat(HELD_SECONDS) { if (phones.getValue(reporter.index).report == ReportSlot.Idle) second() }
        assertEquals(ReportSlot.Pending(0), phones.getValue(reporter.index).report, "the reader's phone was not told its hold began")
        air.place(reporter, home)
        second()
        assertEquals(ReportSlot.Cancelled, phones.getValue(reporter.index).report, "the reader's phone was not told its hold was cancelled")
        repeat(3) { second() }
        assertEquals(null, house.state.meeting, "a cancelled hold called the house: ${house.state.meeting}")
        assertEquals(ReportSlot.Idle, phones.getValue(reporter.index).report)
    }

    /**
     * **GUARD (D-307): a report runs only from the main view, over real sockets.** The reader stands
     * at 25 cm from a Revoked seat with a Subroutine open (the panel on Jam): every upload says it is
     * off the main view, and the house starts no hold for as long as it stands there. Back on page 2,
     * the next answer starts the hold, the panel takes the report screen over, and two seconds later
     * the meeting's call takes it off again.
     */
    @Test
    fun aReportRunsOnlyFromTheMainViewOnDistance() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        air.touch(insider, target)
        repeat(HELD_SECONDS) { if (!house.state.isRevoked(target)) second() }
        air.spreadOut(seats)
        repeat(5) { second() }
        val panel = panels.getValue(reporter.index)
        panel.go(ScreenId.SubJam)
        val near = air.spotOf(target)!!
        air.place(reporter, SimulatedAir.Spot(near.x + 0.25, near.y))
        repeat(HELD_SECONDS + 3) {
            second()
            assertEquals(ReportSlot.Idle, phones.getValue(reporter.index).report, "a reader in a Subroutine was told it is holding a report")
            assertEquals(ScreenId.SubJam, panel.state.screen, "the report took a Subroutine screen over")
        }
        assertEquals(null, house.state.meeting, "a reader in a Subroutine reported: ${house.state.meeting}")
        panel.go(ScreenId.Page2)
        repeat(HELD_SECONDS) { if (phones.getValue(reporter.index).report == ReportSlot.Idle && house.state.meeting == null) second() }
        assertEquals(ReportSlot.Pending(0), phones.getValue(reporter.index).report, "back on page 2 the hold did not begin")
        assertEquals(ScreenId.Report, panel.state.screen, "the hold did not take page 2 over")
        assertEquals(ScreenId.Page2, panel.state.reportFrom)
        repeat(HELD_SECONDS) { if (house.state.meeting == null) second() }
        val meeting = assertNotNull(house.state.meeting, "a hold on the main view did not report: ${house.refusals}")
        assertEquals(reporter, meeting.caller)
        // The reader called it, so the house sends its phone to its own ring (`MeetingCalling`).
        assertEquals(ScreenId.Calling, panel.state.screen, "the meeting's call did not end the report screen")
    }

    /**
     * **GUARD (D-307): leaving the main view mid-hold cancels it, and the panel goes back to its page.**
     * The reader holds from page 1; one second in, the house is told it opened the Egress widget
     * (the panel leaves the report screen by a tap on the banner, say); the next answer is cancelled.
     */
    @Test
    fun leavingTheMainViewCancelsTheHoldOnDistance() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        air.touch(insider, target)
        repeat(HELD_SECONDS) { if (!house.state.isRevoked(target)) second() }
        air.spreadOut(seats)
        repeat(5) { second() }
        val panel = panels.getValue(reporter.index)
        val near = air.spotOf(target)!!
        air.place(reporter, SimulatedAir.Spot(near.x + 0.25, near.y))
        repeat(HELD_SECONDS) { if (phones.getValue(reporter.index).report == ReportSlot.Idle) second() }
        assertEquals(ReportSlot.Pending(0), phones.getValue(reporter.index).report, "the hold did not begin on page 1")
        assertEquals(ScreenId.Report, panel.state.screen)
        panel.go(ScreenId.EgressWidget)
        second()
        assertEquals(ReportSlot.Cancelled, phones.getValue(reporter.index).report, "leaving the main view did not cancel the hold")
        repeat(3) { second() }
        assertEquals(null, house.state.meeting, "a hold cancelled by leaving the main view called the house: ${house.state.meeting}")
        assertEquals(ScreenId.EgressWidget, panel.state.screen, "the cancel moved a phone that had already left the report screen")
    }

    /** **D-291 on distance: the Revoking Insider's phone, held where it Revoked, holds a report only once the grace is over.** */
    @Test
    fun theRevokingInsidersPhoneHoldsItsTargetOnlyAfterTheGraceOnDistance() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        armRevoke(insider)
        ranged()
        air.touch(insider, target)
        repeat(HELD_SECONDS) { if (!house.state.isRevoked(target)) second() }
        assertTrue(house.state.isRevoked(target), "the touch revoked nobody: ${house.refusals}")
        val hit = house.clock
        while (house.clock + ContactGate.UPLOAD_TICKS < hit + ContactGate.PAIR_GRACE_TICKS) {
            second()
            assertEquals(ReportSlot.Idle, phones.getValue(insider.index).report, "the Insider's circle started ${house.clock - hit} ticks after the hit, inside the grace")
            assertEquals(null, house.state.meeting, "the Insider's phone reported inside the grace")
        }
        repeat(HELD_SECONDS) { if (house.state.meeting == null) second() }
        val meeting = assertNotNull(house.state.meeting, "the Insider's phone did not report after the grace: ${house.refusals}")
        assertEquals(insider, meeting.caller)
        assertTrue(slots.getValue(insider.index).any { it is ReportSlot.Pending }, "the Insider's circle never warned them")
    }

    /**
     * **GUARD (D-301, D-308): only the distance decides.** A body between two phones held together
     * makes UWB read three to five metres long while Bluetooth still says touching: the pair is
     * ranged and the armed touch lands nothing. And a phone that cannot range (capacity 0: NEARBY
     * DEVICES refused, no support, the session limit) is paired with nobody, so an armed touch with it
     * Revokes nobody however long it is held, while the round plays on for the rest.
     */
    @Test
    fun onlyTheDistanceDecidesAndAPhoneThatCannotRangeIsTouchedByNobody() {
        rig {
            armed()
            val insider = house.state.livingInsiders.single()
            val target = house.state.livingPlainResidents.first()
            armRevoke(insider)
            ranged()
            air.bodyBetween(insider, target)
            air.touch(insider, target)
            repeat(HELD_SECONDS) { second() }
            // Bluetooth still says touching, so the pair is ranged (D-302): what refuses is the distance.
            assertTrue(
                house.ranging?.pairs().orEmpty().any { it.has(insider) && it.has(target) },
                "the two phones held together were never ranged, so the body was never read",
            )
            assertFalse(house.state.isRevoked(target), "a touch Revoked through a body that reads metres long")
        }
        val cannot = Seat(4)
        rig(without = cannot) {
            armed()
            val insider = house.state.livingInsiders.single()
            // The phone that cannot range is the target, or, if the draw made it the Insider, the actor.
            val target = if (cannot != insider) cannot else house.state.livingPlainResidents.first()
            armRevoke(insider)
            ranged()
            holdAt(insider, target)
            assertFalse(house.state.isRevoked(target), "a touch with a phone that cannot range Revoked somebody: ${house.refusals}")
            assertTrue(house.ranging?.pairs().orEmpty().none { it.has(cannot) }, "a phone that cannot range was paired")
            assertTrue(phones.values.all { it.uploading }, "a phone stopped uploading in a round with a phone that cannot range")
        }
    }

    // ---- D-290: the hit, on the sockets ----------------------------------------------------------

    /**
     * **D-290 on real sockets: a landed Revoke is one frame each, and both say Long.** The Insider's
     * phone is sent its answer saying the Revoke landed, the Revoked phone its landing with the Long,
     * each buzzed by the phone's own `HouseBuzzer` off that one frame; nobody else is sent anything.
     */
    @Test
    fun aLandedRevokeIsOneFrameOnEachPhoneAndBothSayLong() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        armRevoke(insider)
        ranged()
        val before = settledFrames()
        revokeByTouch(insider, target)
        val after = settledFrames()
        val got = seats.associate { it.index to pushed.getValue(it.index).drop(before.getValue(it.index)) }
        assertEquals(
            listOf<RoundMessage>(RoundMessage.AbilityFired(RoundMessage.AbilityFired.LANDED_AT_A_TOUCH)),
            got.getValue(insider.index),
            "the Insider's phone was not sent exactly one answer saying its Revoke landed",
        )
        assertEquals(listOf<RoundMessage>(RoundMessage.RevokeLanded.Buzzing), got.getValue(target.index))
        assertEquals(Haptic.Long, got.getValue(insider.index).single().haptic(), "the Insider's phone would not buzz Long")
        assertEquals(Haptic.Long, got.getValue(target.index).single().haptic(), "the Revoked phone would not buzz Long")
        for (seat in seats.filter { it != insider && it != target }) {
            assertEquals(before.getValue(seat.index), after.getValue(seat.index), "seat ${seat.index} did not touch and was sent a frame")
        }
    }

    // ---- S3: the host log carries no seed, token or key ------------------------------------------

    /**
     * **S3 of the owner's 2026-11-15 rulings (D-219): the host log never prints the round seed, or
     * anything a seed, a contact seed or a seat token can be read back out of.**
     *
     * Every secret in the round is an HMAC under the seed (D-214), so a log line carrying it is the
     * Insider draw and every token on the air, in a file. This plays the whole round above, armed
     * through production's `armAtLightsOut` with a seed from the secure source, and reads every line
     * the house, the arming and the act wrote for the seed in decimal (signed and unsigned), hex and
     * base64 (both byte orders, both alphabets, padded or not), for every seat's contact seed and
     * for every seat token the ledger minted, in hex and base64.
     */
    @Test
    fun theHostLogCarriesNoSeedTokenOrKeyThroughARound() = rig {
        val seed = drawRoundSeed()
        armed(seed)
        playToAnEnding()

        val secrets = buildMap {
            putAll(HostLogSecrets.ofSeed("the round seed", seed))
            for (seat in seats) putAll(HostLogSecrets.ofBytes("seat ${seat.index}'s contact seed", contactSeedFor(seed, seat).bytes()))
            for ((i, token) in tokens.withIndex()) putAll(HostLogSecrets.ofHex("seat token $i", token.value))
        }
        // The log read is the round's, from the arming line to the ending.
        assertTrue(said.any { it.startsWith("ARMING — ") }, "the house's arming line is not in the log read: $said")
        assertTrue(said.any { it.startsWith("THE ROUND ENDED — ") }, "the log read stops before the round ended: $said")
        assertEquals(seats.size, tokens.size, "the ledger minted ${tokens.size} tokens for ${seats.size} seats")
        // The detector, checked against the line this ruling removed: if it cannot see that, it sees nothing.
        assertTrue(
            HostLogSecrets.found(listOf("ARMING — SEED $seed · 5 SEAT(S)"), secrets).isNotEmpty(),
            "the detector missed the seed in the very line S3 removed",
        )
        val found = HostLogSecrets.found(said, secrets)
        assertTrue(found.isEmpty(), "S3: the host log carried a secret:\n" + found.joinToString("\n"))
    }

    // ---- S4: a Revoked player is reported once ---------------------------------------------------

    /**
     * **S4 (D-219) on the sockets: a couch phone is reported once.** The report calls its meeting;
     * the room votes Skip and the lights go out; the same living phone is held 20 cm from the same
     * Revoked phone again, well past the pair's grace. On distance no hold starts on a seat already
     * reported (D-301), so nothing reaches the gate, nothing is called and nothing is answered.
     */
    @Test
    fun aSecondReportOfTheSameRevokedPhoneStartsNothingAndIsAnsweredWithNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        revokeByTouch(insider, target)
        air.spreadOut(seats)
        repeat(ContactGate.PAIR_GRACE_TICKS.toInt() / ContactGate.UPLOAD_TICKS.toInt()) { second() }
        holdAt(reporter, target, metres = 0.20) { house.state.meeting != null }
        assertEquals(MeetingTrigger.RevokeReported(target), house.state.meeting?.trigger, "the first report called no meeting")

        for (seat in seats) house.heard(Intent.CheckIn(seat))
        if (house.state.meeting?.phase == MeetingPhase.Discussion) house.mint(Event.DiscussionClosed(house.now()))
        for (seat in house.state.livingSeats) {
            house.heard(Intent.SelectVote(seat, null))
            house.heard(Intent.LockVote(seat))
        }
        if (house.state.meeting?.phase == MeetingPhase.Vote) house.mint(Event.VoteWindowClosed(house.now()))
        house.mint(Event.TallyHalfwayReached(house.now()))
        house.mint(Event.MeetingClosed(house.now()))
        delay(SETTLE_MILLIS)
        assertEquals(null, house.state.meeting, "the meeting never closed")
        assertFalse(house.ended, "the round ended at the meeting, so every refusal below would be that one")

        val framesBefore = settledFrames()
        val refusedBefore = house.contactRefusals.size
        // Past the pair's grace (D-291), so only S4 stands between this hold and a second call.
        house.advanceTo(Tick(house.clock + ContactGate.PAIR_GRACE_TICKS + 1))
        val from = slots.getValue(reporter.index).size
        holdAt(reporter, target, metres = 0.20)
        assertEquals(null, house.state.meeting, "S4: the second hold called a meeting")
        assertTrue(slots.getValue(reporter.index).drop(from).all { it == ReportSlot.Idle }, "S4: a hold started on a seat already reported")
        assertTrue(house.contactRefusals.drop(refusedBefore).none { it.first is Event.MeetingCalled }, "S4: a second report reached the gate")
        assertEquals(framesBefore, seats.associate { it.index to pushed.getValue(it.index).size }, "S4: the second hold was answered")
    }

    // ---- 2 · R8: out, and still uploading on the beat --------------------------------------------

    @Test
    fun aRevokedPhoneKeepsUploadingOnTheBeatAtOneSizeAndHearingNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val other = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        revokeByTouch(insider, target)

        val before = uploads.getValue(target.index).size
        val emittedBefore = house.emitted.size
        // Put a living phone right against the Revoked one: the living phone's hold reports it, and
        // the Revoked phone's own distances are read for nothing (D-301).
        holdAt(other, target) { house.state.meeting != null }
        val after = uploads.getValue(target.index).drop(before)
        assertTrue(after.size >= 3, "a Revoked phone stopped uploading on the beat: ${after.size} uploads")
        assertTrue(after.all { it.length == SightingUpload.BODY_CHARS }, "a Revoked phone's upload changed size")
        val sizes = seats.associate { s -> s.index to uploads.getValue(s.index).map { it.length }.toSet() }
        assertTrue(sizes.values.all { it == setOf(SightingUpload.BODY_CHARS) }, "uploads differ in size by phone: $sizes")
        // What the Revoked phone uploaded resolved to nothing: the only events since are the living
        // phone's report of it, never a contact from the Revoked side.
        assertTrue(house.state.meeting != null, "the living phone pressed against the Revoked one reported nothing")
        assertFalse(
            house.emitted.drop(emittedBefore)
                .filterIsInstance<home.someoneshome.model.Effect.ContactAnswered>().any { it.seat.index == target.index },
            "the Revoked phone was answered as the actor of a touch: it was still scanning",
        )
    }

    // ---- 3 · R5 and R4: a touch at a meeting is refused, and the refusal sends nothing ------------

    @Test
    fun aTouchAtAMeetingIsRefusedAndNothingGoesDownAnySocketForIt() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        armRevoke(insider)
        house.mint(Event.MeetingCalled(house.now(), target, MeetingTrigger.MeetingCard))
        assertNotNull(house.state.meeting, "the fixture called no meeting")
        delay(SETTLE_MILLIS)

        val framesBefore = settledFrames()
        val refusedBefore = house.contactRefusals.size
        holdAt(insider, target)

        assertFalse(house.state.isRevoked(target), "R5: an armed Revoke landed in the meeting room")
        // On distance nothing is ranged at a meeting (D-306: no rule there reads one), so a touch
        // reaches the gate only from a pair kept a moment past the call; any that does is refused.
        val refused = house.contactRefusals.drop(refusedBefore)
        assertTrue(
            refused.all { it.first is Event.ContactMade && it.second == RefusalReason.MeetingAlreadyRunning },
            "R5: a touch at a meeting was refused for something else: $refused",
        )
        val framesAfter = seats.associate { it.index to pushed.getValue(it.index).size }
        assertEquals(framesBefore, framesAfter, "R4: a refused contact put frames on a socket")
        // The arm is gone, and not by the touch: a meeting call ends every armed Revoke (the owner's
        // 2026-11-18 ruling, D-280). Until then it survived the call and this line checked that the
        // refusal had not spent it; the refusals above are what R5 is.
        assertFalse(house.state.cooldownArmed.any { it.index == insider.index }, "a meeting call left an armed Revoke standing (2026-11-18)")
    }

    /**
     * R4 for the report: no hold runs during a meeting (D-301 decision 5), so a living phone held
     * beside the Revoked one at a meeting is told nothing, calls nothing and is answered with nothing.
     */
    @Test
    fun aReportDuringAMeetingStartsNothingAndIsAnsweredWithNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        ranged()
        revokeByTouch(insider, target)
        house.mint(Event.MeetingCalled(house.now(), insider, MeetingTrigger.MeetingCard))
        delay(SETTLE_MILLIS)

        val framesBefore = settledFrames()
        val from = slots.getValue(reporter.index).size
        holdAt(reporter, target, metres = 0.20)
        assertEquals(MeetingTrigger.MeetingCard, house.state.meeting?.trigger, "the meeting changed under a hold")
        assertTrue(slots.getValue(reporter.index).drop(from).all { it == ReportSlot.Idle }, "a hold ran at a meeting")
        assertEquals(framesBefore, seats.associate { it.index to pushed.getValue(it.index).size }, "R4: a hold at a meeting was answered")
    }

    // ---- T2 · A radio that went down: named if a person did it, recorded if not (D-222; D-223) ----

    /**
     * **One phone's radio goes down and the meeting is called: what every socket carried.** The
     * Insider's radio here, so the notice is shown to name an Insider exactly as it would a Resident
     * (the rules' differential is `RadioFaultTest`). Returns each seat's notices at that meeting,
     * re-encoded with `RoundWire`, which is deterministic and is what the host wrote.
     */
    private suspend fun Rig.outageThenMeeting(cause: RadioCause): Pair<Seat, Map<Int, List<String>>> {
        armed()
        val insider = house.state.livingInsiders.single()
        val framesBefore = settledFrames()
        air.radioFor(insider).fail(cause)
        beat()
        withTimeout(30_000) { while (faultsTaken < 1) delay(5) }
        beat()
        delay(SETTLE_MILLIS)
        assertEquals(listOf(1), seats.map { faultBodies.getValue(it.index).size }.filter { it > 0 }, "not one fault body, from one phone")
        // The one fixed body: its cause, and an onset's random name of fixed width (D-245), so the
        // same length from every phone. The bytes differ only in that name.
        val sent = faultBodies.getValue(insider.index).single()
        val report = RoundWire.decodeOrNull(sent) as? RoundBody.RadioFaulted
        assertEquals(cause, report?.cause, "the fault went up as something other than the one fixed body")
        assertEquals(
            RoundWire.encode(RoundBody.RadioFaulted(cause, "0".repeat(RoundBody.RadioFaulted.ONSET_CHARS))).length, sent.length,
            "the fault went up as something other than the one fixed body",
        )
        assertEquals(framesBefore, seats.associate { it.index to pushed.getValue(it.index).size }, "a radio fault reached a phone before the meeting")
        assertTrue(said.any { it.startsWith("RADIO FAULT — SEAT ${insider.index} · ${cause.name.uppercase()}") }, "the house's log never said so: $said")

        val caller = house.state.livingPlainResidents.first()
        house.mint(Event.MeetingCalled(house.now(), caller, MeetingTrigger.MeetingCard))
        for (seat in seats.filter { it.index != caller.index }) house.heard(Intent.CheckIn(seat))
        delay(SETTLE_MILLIS)
        return insider to seats.associate { seat ->
            seat.index to pushed.getValue(seat.index).filterIsInstance<RoundMessage.NoticeIssued>()
                .map { RoundWire.encode(RoundBody.Push(1, it)) }
        }
    }

    /**
     * **GUARD: Bluetooth switched off mid-round is named to every phone at the next meeting, in one
     * frame of one length on every socket**, living and out, Insider or not.
     */
    @Test
    fun aRadioSwitchedOffIsNamedToEveryPhoneAtTheNextMeeting() = rig {
        val (insider, notices) = outageThenMeeting(RadioCause.Person)
        val expected = RoundWire.encode(RoundBody.Push(1, RoundMessage.NoticeIssued(listOf(insider), switchedOff = emptyList())))
        for (seat in seats) {
            assertEquals(listOf(expected), notices.getValue(seat.index), "seat ${seat.index} was not told exactly once, at the meeting")
        }
        assertEquals(1, notices.values.flatten().map { it.length }.toSet().size, "the notice's frame differs in length between phones")
    }

    /**
     * **GUARD: a reset is named by no notice.** The house logs it (loud to the authority) and every
     * phone gets the same empty notice every meeting gets, which draws nothing.
     */
    @Test
    fun aRadioThatResetIsRecordedAndNamedToNobody() = rig {
        val (insider, notices) = outageThenMeeting(RadioCause.Device)
        assertTrue(said.any { it.startsWith("RADIO FAULT — SEAT ${insider.index} · DEVICE · RECORDED") }, "the reset was not recorded for the authority: $said")
        val empty = RoundWire.encode(RoundBody.Push(1, RoundMessage.NoticeIssued(emptyList(), switchedOff = emptyList())))
        for (seat in seats) {
            assertEquals(listOf(empty), notices.getValue(seat.index), "a reset was named to seat ${seat.index} (D-187)")
        }
    }

    // ---- 4 · The cross-process stand-in, without sockets ----------------------------------------

    /**
     * **[HouseAir], which [RoundCheat] uses because five processes share no air** (D-308): five
     * phones with no UWB radio, as five simulators are, keep uploading up their own sockets; the
     * stand-in's radios are spliced into each upload at the app root's door ([StandInDoor]), the
     * plan goes back to the stand-in, and the Revoke and the report are still made by the resolver
     * from distances rather than minted. The reporter's own phone says it is on the main view.
     */
    @Test
    fun theRehearsalsHouseAirLandsARevokeAndAReportOnDistanceThroughTheResolver() = rig(ranging = false) {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        val radios = HouseAir(house, window = { window }).also { it.armed() }
        door.air = radios
        val act = ContactAct(radios.air, beat = { second() }, note = { said += it })
        act.touch(insider, target, until = { house.state.isRevoked(target) })
        assertTrue(house.state.isRevoked(target), "HouseAir's touch revoked nobody: ${house.refusals}")
        act.touch(reporter, target, until = { house.state.meeting != null })
        assertEquals(MeetingTrigger.RevokeReported(target), house.state.meeting?.trigger, "HouseAir's report called no meeting")
        assertTrue(slots.getValue(reporter.index).any { it is ReportSlot.Pending }, "the reporter's own phone was never told its report was held")
    }

    private companion object {
        const val SEED = RoundCheat.SEED
        const val SETTLE_MILLIS = 150L

        /**
         * **Three peers a phone, the phones' own start** (U6, D-302). D-301 ranged four so that five
         * phones ranged every pair; since D-302 a touch or a hold between two phones the house is not
         * ranging displaces a kept pair.
         */
        const val UWB_CAPACITY = SimulatedAir.BENCH_CAPACITY

        /**
         * **How long a held touch or a held report is given, in seconds: ten** (D-302, D-308). Between two
         * phones the house is not ranging, three to pair (the stops, the second spare, the second
         * start), one for both phones' readings, and for a report two more of the hold: six, with
         * four to spare. A loop over it stops as soon as the touch or the call lands.
         */
        const val HELD_SECONDS = 10
    }
}
