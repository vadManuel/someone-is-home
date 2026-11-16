package home.someoneshome.app

import home.someoneshome.core.ContactGate
import home.someoneshome.core.contactSeedFor
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
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
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.SilentHaptics
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

/**
 * **The bench, through the fake radio, over real sockets: a round from LIGHTS OUT to an ending with
 * the Revoke and the report made by phones touching** (D-217, unit 3).
 *
 * Five phones, each a real [TransportClient] with its own [RoundOverTransport] whose radio is a
 * [SimulatedRadio] in one shared [SimulatedAir]; a real [TransportHost] and [EffectWire]; the real
 * [RoundAuthority]. Every phone uploads up its own socket on the beat; the host's `onFrame` puts
 * each upload on one ordered queue exactly as `LobbyHouse` does (that class cannot be started in a
 * test, E-P2-2), and the queue's one consumer calls [RoundAuthority.sighted]. Nothing mints a
 * contact or a report: [ContactAct] walks phones together, and the resolver concludes.
 *
 * The meeting's timed transitions are minted here, as `tickHouse` would mint them; the presses in
 * it are requests through [RoundAuthority.heard], as the desk would put them.
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

    private inner class Rig(private val scope: CoroutineScope) {
        val air = SimulatedAir()
        val said = mutableListOf<String>()
        private val arrivals = Channel<Pair<Seat, RoundBody.Sighted>>(Channel.UNLIMITED)
        private val faults = Channel<Pair<Seat, RadioCause>>(Channel.UNLIMITED)

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
                    faults.trySend(seat to body.cause)
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
        private val jobs = mutableListOf<Job>()

        suspend fun start() {
            val port = host.start(0)
            jobs += scope.launch {
                for ((seat, body) in arrivals) {
                    uploads.getValue(seat.index) += body.slots
                    house.sighted(seat, body)
                    taken++
                }
            }
            jobs += scope.launch {
                for ((seat, cause) in faults) {
                    house.radioFaulted(seat, cause)
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
                round = RoundOverTransport(
                    scope, FlowModel(PanelState(screen = ScreenId.Home)),
                    buzzer = HouseBuzzer(SilentHaptics),
                    send = { frame -> client.send(frame) },
                    radio = air.radioFor(seat),
                    wake = home.someoneshome.platform.ScreenWake(home.someoneshome.platform.NoIdleTimer),
                    windowNow = { window },
                    onInbound = { body ->
                        (RoundWire.decodeOrNull(body) as? RoundBody.Push)?.let { pushed.getValue(seat.index) += it.message }
                    },
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
        }

        val act get() = ContactAct(air, beat = { beat() }, note = { said += it })

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

    private fun rig(block: suspend Rig.() -> Unit) = runBlocking {
        val rig = Rig(this)
        try {
            rig.start()
            rig.block()
        } finally {
            rig.close()
        }
    }

    // ---- 1 · The round, to an ending ------------------------------------------------------------

    @Test
    fun theBenchPlaysARoundToAnEndingThroughTheFakeRadio() = rig {
        armed()
        playToAnEnding()
    }

    /** A Revoke, its report, a meeting that Restrains the Insider: the round to an ending. */
    private suspend fun Rig.playToAnEnding() {
        val state = { house.state }
        val insider = state().livingInsiders.single()
        val target = state().livingPlainResidents.first()
        val reporter = state().livingPlainResidents.first { it.index != target.index }

        // Nobody touching: a few beats of filler from every phone, and the house concludes nothing.
        act.idle(beats = 2)
        assertTrue(phones.values.all { it.uploading }, "a phone was not uploading in the round")
        assertFalse(house.emitted.any { it is home.someoneshome.model.Effect.ContactAnswered }, "a contact was minted from nobody touching")

        // The Revoke: the Insider walks over and holds the phones together.
        armRevoke(insider)
        act.touch(insider, target)
        assertTrue(state().isRevoked(target), "the touch did not Revoke the target: ${house.refusals}")
        assertTrue(RoundMessage.AbilityFired(cooldownStarted = true) in pushed.getValue(insider.index), "the Insider's phone was never told its Revoke fired")
        assertTrue(RoundMessage.RevokeLanded.Padded in pushed.getValue(target.index), "the target's phone was never told")
        assertTrue(phones.getValue(target.index).outOfTheRound, "the Revoked phone does not know it is out")

        // The report: a living phone finds the Revoked one.
        act.touch(reporter, target)
        val meeting = assertNotNull(state().meeting, "the report called no meeting: ${house.refusals}")
        assertEquals(reporter, meeting.caller, "the meeting was called by somebody else")
        assertEquals(MeetingTrigger.RevokeReported(target), meeting.trigger)

        // The meeting, to the Restraint of the Insider: every seat checks in, the living vote for
        // the Insider, the house closes each phase.
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
        assertTrue(said.any { it.startsWith("THE RADIO — SEAT ${insider.index} WALKS TO") }, "the act never walked")
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
     * **S4 (D-219) on the sockets: a second report of a couch phone is refused, recorded, and
     * answered with nothing.** The report calls its meeting; the room votes Skip and the lights go
     * out; the same living phone is held against the same Revoked phone again.
     */
    @Test
    fun aSecondReportOfTheSameRevokedPhoneIsRefusedAndAnsweredWithNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        act.touch(insider, target)
        assertTrue(house.state.isRevoked(target), "the fixture revoked nobody")
        act.touch(reporter, target)
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

        val framesBefore = seats.associate { it.index to pushed.getValue(it.index).size }
        val refusedBefore = house.contactRefusals.size
        // Past D-213's debounce, so the resolver mints the report again rather than swallowing it.
        house.advanceTo(Tick(house.clock + ContactGate.DEBOUNCE_TICKS + 1))
        act.touch(reporter, target)
        val refused = house.contactRefusals.drop(refusedBefore)
        assertTrue(
            refused.any { (event, reason) ->
                event is Event.MeetingCalled && event.trigger == MeetingTrigger.RevokeReported(target) &&
                    reason == RefusalReason.AlreadyReported
            },
            "S4: the second report of seat ${target.index} was not refused as already reported: $refused",
        )
        assertEquals(null, house.state.meeting, "S4: the second report called a meeting")
        assertEquals(framesBefore, seats.associate { it.index to pushed.getValue(it.index).size }, "S4: a refused second report was answered")
    }

    // ---- 2 · R8: out, and still uploading on the beat --------------------------------------------

    @Test
    fun aRevokedPhoneKeepsUploadingOnTheBeatAtOneSizeAndHearingNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val other = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        act.touch(insider, target)
        assertTrue(house.state.isRevoked(target), "the fixture revoked nobody")

        val before = uploads.getValue(target.index).size
        val emittedBefore = house.emitted.size
        // Put a living phone right against the Revoked one: if it were still scanning, it would hear.
        air.touch(target, other)
        repeat(3) { beat() }
        val after = uploads.getValue(target.index).drop(before)
        assertEquals(3, after.size, "a Revoked phone stopped uploading on the beat: ${after.size} in 3 beats")
        assertTrue(after.all { it.length == SightingUpload.BODY_CHARS }, "a Revoked phone's upload changed size")
        val sizes = seats.associate { s -> s.index to uploads.getValue(s.index).map { it.length }.toSet() }
        assertTrue(sizes.values.all { it == setOf(SightingUpload.BODY_CHARS) }, "uploads differ in size by phone: $sizes")
        // What the Revoked phone uploaded resolved to nothing: the only events since are the living
        // phone's one-sided report of it, never a contact from the Revoked side.
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

        val framesBefore = seats.associate { it.index to pushed.getValue(it.index).size }
        val refusedBefore = house.contactRefusals.size
        act.touch(insider, target)

        assertFalse(house.state.isRevoked(target), "R5: an armed Revoke landed in the meeting room")
        val refused = house.contactRefusals.drop(refusedBefore)
        assertEquals(
            listOf(RefusalReason.MeetingAlreadyRunning, RefusalReason.MeetingAlreadyRunning),
            refused.map { it.second },
            "R5: both halves of the touch should be refused for the meeting: $refused",
        )
        assertTrue(refused.all { it.first is Event.ContactMade })
        val framesAfter = seats.associate { it.index to pushed.getValue(it.index).size }
        assertEquals(framesBefore, framesAfter, "R4: a refused contact put frames on a socket")
        assertTrue(house.state.cooldownArmed.any { it.index == insider.index }, "a refused touch spent the armed Revoke")
    }

    /** R4 for the report: a second meeting from the radio is refused and answered with nothing. */
    @Test
    fun aReportDuringAMeetingIsRefusedAndAnsweredWithNothing() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        armRevoke(insider)
        act.touch(insider, target)
        assertTrue(house.state.isRevoked(target))
        house.mint(Event.MeetingCalled(house.now(), insider, MeetingTrigger.MeetingCard))
        delay(SETTLE_MILLIS)

        val framesBefore = seats.associate { it.index to pushed.getValue(it.index).size }
        val refusedBefore = house.contactRefusals.size
        act.touch(reporter, target)
        val refused = house.contactRefusals.drop(refusedBefore)
        assertTrue(
            refused.any { it.first is Event.MeetingCalled && it.second == RefusalReason.MeetingAlreadyRunning },
            "the report during a meeting was not refused: $refused",
        )
        assertEquals(framesBefore, seats.associate { it.index to pushed.getValue(it.index).size }, "R4: a refused report was answered")
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
        val framesBefore = seats.associate { it.index to pushed.getValue(it.index).size }
        air.radioFor(insider).fail(cause)
        beat()
        withTimeout(30_000) { while (faultsTaken < 1) delay(5) }
        beat()
        delay(SETTLE_MILLIS)
        assertEquals(listOf(1), seats.map { faultBodies.getValue(it.index).size }.filter { it > 0 }, "not one fault body, from one phone")
        assertEquals(
            listOf(RoundWire.encode(RoundBody.RadioFaulted(cause))), faultBodies.getValue(insider.index),
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
        val expected = RoundWire.encode(RoundBody.Push(1, RoundMessage.NoticeIssued(listOf(insider))))
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
        val empty = RoundWire.encode(RoundBody.Push(1, RoundMessage.NoticeIssued(emptyList())))
        for (seat in seats) {
            assertEquals(listOf(empty), notices.getValue(seat.index), "a reset was named to seat ${seat.index} (D-187)")
        }
    }

    // ---- 4 · The cross-process stand-in, without sockets ----------------------------------------

    /**
     * [HouseAir], which [RoundCheat] uses because five processes share no air: the same act, the
     * uploads put to [RoundAuthority.sighted] directly, and the Revoke and the report still made by
     * the resolver from tokens rather than minted.
     */
    @Test
    fun theRehearsalsHouseAirLandsARevokeAndAReportThroughTheResolver() = rig {
        armed()
        val insider = house.state.livingInsiders.single()
        val target = house.state.livingPlainResidents.first()
        val reporter = house.state.livingPlainResidents.first { it.index != target.index }
        stop() // the phones' own uploads stop; only the house's stand-in uploads from here
        armRevoke(insider)
        val radios = HouseAir(house, window = { window }).also { it.armed() }
        val act = ContactAct(radios.air, beat = { radios.beat() }, note = { said += it })
        act.touch(insider, target)
        assertTrue(house.state.isRevoked(target), "HouseAir's touch revoked nobody: ${house.refusals}")
        act.touch(reporter, target)
        assertEquals(MeetingTrigger.RevokeReported(target), house.state.meeting?.trigger, "HouseAir's report called no meeting")
    }

    private companion object {
        const val SEED = RoundCheat.SEED
        const val SETTLE_MILLIS = 150L
    }
}
