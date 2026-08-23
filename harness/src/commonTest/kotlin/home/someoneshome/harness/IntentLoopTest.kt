package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.IntentDesk
import home.someoneshome.platform.transport.IntentOutbox
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The whole loop, over a real socket: a control goes up, the rules run, the effects come down.**
 *
 * `EffectPushTest` in `app` proves the downward carriage and `IntentDeskTest` in `platform` proves
 * the upward attribution, and neither of them can run [admit] — `app` does not see `core`. This is
 * the module that sees both, so it is the only place the join can be asserted at all: **a request
 * off a wire and the same request in process leave the same round behind.**
 *
 * ### What a house is, here
 *
 * A ledger, a server, an [IntentDesk] reading up the sockets and an [EffectWire] writing down them,
 * with `admit` between the two. That is [LobbyHouse]'s arrangement with `onIntent` joined to the
 * rules — which is exactly the join E-D3-3's remaining half is about, built here as a bench so that
 * the carriage on either side of it can be proved before anything arms a round in production.
 */
class IntentLoopTest {

    /** The requests the identity test drives, in order. Deliberately a mix of ruled and refused. */
    private fun aShiftOfRequests(state: GameState): List<IntentMessage> {
        val marker = state.workOrderFor(Seat(0))?.entries?.firstOrNull()?.anchor
            ?: MARKERS.first()
        return listOf(
            // A meeting, and then the gate the whole house fills.
            IntentMessage.CallMeeting,
            IntentMessage.CheckIn,
            // Out of phase and therefore refused -- on the path on purpose, because a refusal that
            // travelled differently from an admission would be the drift this test exists to catch.
            IntentMessage.LockVote,
            IntentMessage.DeclareReadyToVote,
            // Work, at a card this seat's own order points at.
            IntentMessage.BeginSubroutine(marker),
            IntentMessage.ArmRevoke,
        )
    }

    // ---- 1 · One gate, two sources -----------------------------------------------------------------

    /**
     * **The same requests, in process and over loopback, leave the same round.**
     *
     * `EffectPushTest.inProcessAndOverLoopbackLeaveIdenticalState` mirrored up the wire, and the
     * one assertion this unit exists to be able to make. Both the **state** and the **effects** are
     * compared: a state that agreed while the effect lists differed would be a house that reached
     * the same place and told the phones something different on the way.
     *
     * The refusals are compared too, because a refusal is the authority's own record (D-066) and a
     * wire path that quietly admitted something the in-process path refused would be the worst
     * possible version of this drift — invisible on both screens.
     */
    @Test
    fun inProcessAndOverLoopbackLeaveIdenticalAuthorityState() = runBlocking<Unit> {
        val start = armed()
        val requests = aShiftOfRequests(start)
        val seat = Seat(0)

        // ---- In process: the desk's stamp done by hand, everything else identical --------------
        val inProcess = Authority(start)
        for (message in requests) inProcess.put(message.attributedTo(seat))

        // ---- Over loopback: a real server, a real socket, a real client ------------------------
        val overTheWire = Authority(start)
        val house = House(this, overTheWire)
        try {
            house.start()
            val phone = house.seat()
            for (message in requests) phone.act(message)
            house.awaitHandled(requests.size)
        } finally {
            house.stop()
        }

        // **Compared as the recording's own state row**, not with `==`. `GameState` has no
        // structural equality — it is a plain class — so `assertEquals` on two of them compares
        // identities and can only ever fail. `Transcript.render` is the row the replay guarantee
        // already compares two rounds by, and *every field of authority state, with no exceptions
        // for fields nothing writes yet*, which is a stronger check than a data class's equals
        // would have been: a field the row omits is a field that guarantee does not cover either.
        assertEquals(
            Transcript.render(inProcess.state), Transcript.render(overTheWire.state),
            "a request put in process and the same request off a socket left two different rounds",
        )
        assertEquals(
            inProcess.emitted, overTheWire.emitted,
            "the two sources reached the same state and said different things getting there",
        )
        assertEquals(
            inProcess.refusals, overTheWire.refusals,
            "a request was judged differently depending on which door it came in by",
        )

        // ---- And none of it is vacuous --------------------------------------------------------
        assertTrue(
            Transcript.render(inProcess.state) != Transcript.render(start),
            "the fixture requests changed nothing",
        )
        assertTrue(inProcess.emitted.isNotEmpty(), "no effect was produced, so nothing was compared")
        assertTrue(
            inProcess.refusals.isNotEmpty(),
            "no request was refused, so the refusal path was never on this comparison",
        )
    }

    /**
     * **A request is attributed to the connection it arrived on, over a real socket.**
     *
     * `IntentDeskTest` proves the desk stamps a seat; this proves the seat it stamps is the one the
     * *ledger* admitted the token to, through a real handshake. Two phones send byte-for-byte
     * identical frames and the house hears two different players.
     */
    @Test
    fun twoPhonesSendingTheSameBytesAreHeardAsTwoDifferentPlayers() = runBlocking<Unit> {
        val authority = Authority(armed())
        val house = House(this, authority)
        val bytes: String
        try {
            house.start()
            val first = house.seat()
            val second = house.seat()

            // Byte-for-byte identical: the same message, and the same number, on two connections.
            bytes = RoundWire.encode(RoundBody.Act(seq = 1, intent = IntentMessage.CheckIn))
            first.raw(bytes)
            second.raw(bytes)
            house.awaitHandled(2)
        } finally {
            house.stop()
        }

        assertEquals(
            listOf(Intent.CheckIn(Seat(0)), Intent.CheckIn(Seat(1))),
            authority.heard.sortedBy { (it as Intent.CheckIn).actor.index },
            "identical frames on two connections were attributed to the same player: $bytes",
        )
    }

    /**
     * **A retried frame is heard once, and the round moves once.**
     *
     * Over a real socket rather than against the desk alone, because the property that matters is
     * about the *round*: `ArmRevoke` twice is two cooldowns spent on one press, and nothing
     * downstream of the desk would notice.
     */
    @Test
    fun aRetriedFrameAppliesOnceAndTheSecondCopyChangesNothing() = runBlocking<Unit> {
        val authority = Authority(armed())
        val house = House(this, authority)
        val afterFirst: GameState
        try {
            house.start()
            val phone = house.seat()
            val frame = RoundWire.encode(RoundBody.Act(seq = 1, intent = IntentMessage.ArmRevoke))

            phone.raw(frame)
            house.awaitHandled(1)
            afterFirst = authority.state

            // The resend, byte for byte. A client that never got its push would send exactly this.
            phone.raw(frame)
            phone.raw(frame)
            // Then something new, so the wait below cannot pass by the resends being slow.
            phone.raw(RoundWire.encode(RoundBody.Act(seq = 2, intent = IntentMessage.CheckIn)))
            house.awaitHandled(2)
        } finally {
            house.stop()
        }

        assertEquals(1, authority.heard.count { it is Intent.ArmRevoke }, "one press, two Revokes")
        assertEquals(
            listOf(Intent.ArmRevoke(Seat(0)), Intent.CheckIn(Seat(0))), authority.heard,
            "the resend reached the rules",
        )
        assertTrue(afterFirst.armed, "the fixture was not a round, so nothing was proved about one")
    }

    // ---- 2 · The round trip ------------------------------------------------------------------------

    /**
     * **A check-in goes up, the gate moves, and the count comes back down to every phone** (D-104).
     *
     * The first of the three round trips, and the simplest shape the loop has: one control, one
     * event, one effect, everybody. What makes it worth running is that every leg is real — a
     * websocket up, `admit` and `reduce` in the middle, the emit schema and a websocket down.
     */
    @Test
    fun aCheckInRidesUpAndTheGateComesBackDownToEveryPhone() = runBlocking<Unit> {
        val ringing = ringing()
        val authority = Authority(ringing)
        val house = House(this, authority)
        try {
            house.start()
            val phone = house.seat()
            phone.act(IntentMessage.CheckIn)
            house.awaitHandled(1)
            house.settle()
        } finally {
            house.stop()
        }

        val gate = authority.delivered.filter { it.second is RoundMessage.CheckInProgressed }
        assertEquals(
            SEATS.map { it.index }.toSet(), gate.map { it.first.index }.toSet(),
            "the gate is one gate and both screens draw it — somebody was left off it",
        )
        // Two, not one: *the caller's scan is their check-in* and they were counted from the
        // instant the meeting existed (Event.MeetingCalled). So this is the gate standing at one
        // and moving by exactly the one phone that pressed.
        assertEquals(
            listOf(2), gate.map { (it.second as RoundMessage.CheckInProgressed).present }.distinct(),
            "the gate did not move by exactly the one phone that pressed",
        )
        assertEquals(
            listOf(SEATS.size),
            gate.map { (it.second as RoundMessage.CheckInProgressed).expected }.distinct(),
            "the gate is against the whole house, and the total drifted",
        )
    }

    /**
     * **A vote selection goes up and reaches a player outside the system — and nobody living**
     * (D-117, D-134).
     *
     * The second round trip, and the one where the redaction is the point: the couch watches the
     * vote happen and the living see a count of locked seats and nothing else. The voter's own
     * ballot comes back to the voter alone.
     *
     * The *screen* this lands on is E-P1-2's — `PanelState.ballots` holds names and the effect
     * carries seats — so what is asserted here is the leg the wire owns: which phone the message
     * reached, off a real socket, and which phones it did not.
     */
    @Test
    fun aVoteSelectionReachesTheCouchAndTheVoterAndNobodyElse() = runBlocking<Unit> {
        val voting = voting()
        val out = voting.revoked + voting.restrained
        assertTrue(out.isNotEmpty(), "the fixture has nobody outside the system to watch the vote")

        val authority = Authority(voting)
        val house = House(this, authority)
        val voter: Seat
        try {
            house.start()
            val phone = house.seat()
            voter = phone.seat
            assertTrue(voter !in out, "the voter is the one seat that must still be in the round")
            phone.act(IntentMessage.SelectVote(target = voting.livingSeats.last { it != voter }))
            house.awaitHandled(1)
            house.settle()
        } finally {
            house.stop()
        }

        val watched = authority.delivered
            .filter { it.second is RoundMessage.VoteSelectionShown }
            .map { it.first }
        assertEquals(
            out.toSet(), watched.toSet(),
            "the live selection reached somebody it should not have, or missed the couch",
        )

        val ownBallot = authority.delivered.filter { it.second is RoundMessage.VoteHeld }.map { it.first }
        assertEquals(
            listOf(voter), ownBallot,
            "this seat's own ballot went to somebody who is not this seat",
        )
    }

    /**
     * **An entry goes up, the house grades it, and the verdict comes back to the sender alone**
     * (D-109, D-042).
     *
     * The third round trip, and the one that carries the whole Subroutine treatment: the phone
     * holds what it entered and nothing to compare it against, so the verdict *has* to make this
     * trip, and it has to land on one phone. A `SubroutineGraded` reaching a second seat would tell
     * that seat somebody was working, and — over a round — who is producing and who is not.
     */
    @Test
    fun anEntryIsGradedAndTheVerdictComesBackToTheSenderOnly() = runBlocking<Unit> {
        val authority = Authority(armed())
        val house = House(this, authority)
        val worker: Seat
        try {
            house.start()
            val phone = house.seat()
            worker = phone.seat

            // The card this seat's own order points at, and then the answer the house asked for --
            // read off the state after the scan, exactly as the shared fixture reads it, because a
            // typed-in answer key would agree with somebody's model of the grading rather than
            // with the grading.
            val marker = authority.state.workOrderFor(worker)?.entries
                ?.firstOrNull { authority.state.workOrderFor(worker)!!.isActionable(it) }?.anchor
            assertTrue(marker != null, "this seat was issued no work, so nothing could be handed over")
            phone.act(IntentMessage.BeginSubroutine(marker))
            house.awaitHandled(1)

            val expected = authority.state.openSubroutineFor(worker)?.takeIf { it.armed }?.expected
            assertTrue(expected != null, "the scan opened nothing, so there was nothing to hand over")
            phone.act(IntentMessage.HandOverEntry(marker, expected))
            house.awaitHandled(2)
            house.settle()
        } finally {
            house.stop()
        }

        val verdicts = authority.delivered.filter { it.second is RoundMessage.SubroutineGraded }
        assertEquals(
            listOf(worker), verdicts.map { it.first },
            "a verdict reached a phone that did not hand anything over",
        )
        assertEquals(
            listOf(true), verdicts.map { (it.second as RoundMessage.SubroutineGraded).accepted },
            "the house asked for an answer, was given that answer, and refused it",
        )
    }

    // ---- The fixtures --------------------------------------------------------------------------------

    private fun armed(): GameState = Authority(GameState.EMPTY).also {
        it.put(
            Event.RoundArmed(
                Tick(0), seed = 20260822L, seats = SEATS, insiders = INSIDERS,
                markers = placed(MARKERS), names = NAMES, oneLines = LINES,
            )
        )
    }.state

    /** A round with a meeting ringing, so a check-in has a gate to move. */
    private fun ringing(): GameState = Authority(armed()).also {
        it.put(Event.MeetingCalled(Tick(500), Seat(7), MeetingTrigger.MeetingCard))
    }.state

    /**
     * A round at the ballot, with at least one player outside the system to watch it.
     *
     * The seat goes out by a Revoke rather than a Restrain because a Revoke is one event pair and
     * needs no meeting of its own — and what this fixture is for is somebody on the couch, not the
     * route they took to get there.
     */
    private fun voting(): GameState = Authority(armed()).also { authority ->
        val insider = INSIDERS.first()
        val ready = authority.state.cooldownFor(
            insider, home.someoneshome.model.InsiderAbility.Revoke,
        )?.readyAt?.step ?: 0L
        var t = ready + 1
        authority.put(Event.RevokeArmed(Tick(t++), insider))
        authority.put(Event.ContactMade(Tick(t++), insider, Seat(6)))
        authority.put(Event.MeetingCalled(Tick(t++), Seat(7), MeetingTrigger.MeetingCard))
        SEATS.forEach { authority.put(Event.MeetingCheckedIn(Tick(t++), it)) }
        authority.put(Event.DiscussionClosed(Tick(t++)))
    }.state

    // ---- The rig -------------------------------------------------------------------------------------

    /**
     * **The house's own half: state, the gate, and what it said to whom.**
     *
     * The in-process source and the wire's source are the same object; the only difference between
     * the two runs is where the [Intent] came from and who put the seat on it. That is what makes
     * the identity test mean anything — a bench with two authorities would be comparing two
     * implementations rather than two doors into one.
     */
    private class Authority(start: GameState) {

        var state: GameState = start
            private set

        /** Every effect the rules produced, in order. The authority's own record. */
        val emitted = mutableListOf<Effect>()

        /** Every refusal, in order (D-066: an unrecorded refusal is an invisible drop). */
        val refusals = mutableListOf<RefusalReason>()

        /** Every request that reached the rules, attributed. */
        val heard = mutableListOf<Intent>()

        /** What actually went down which socket, decoded. Only the wire run fills this. */
        val delivered = mutableListOf<Pair<Seat, RoundMessage>>()

        /**
         * The house's clock, sampled where the request arrives.
         *
         * Started high so a fixture's own events never collide with a request's, and advanced once
         * per *arriving* request — so the two runs stamp the same ticks as long as they see the
         * same requests, which is the thing under comparison.
         */
        private var tick = 10_000L

        fun put(intent: Intent) {
            heard += intent
            put(home.someoneshome.core.factOf(intent, Tick(tick++)))
        }

        fun put(event: Event) {
            when (val admission = admit(state, event)) {
                is Admission.Admitted -> {
                    state = admission.reduction.state
                    emitted += admission.reduction.effects
                }
                is Admission.Refused -> refusals += admission.reason
            }
        }
    }

    /** One house on loopback, with the rules joined to both wires. [LobbyHouse]'s arrangement. */
    private class House(private val scope: CoroutineScope, private val authority: Authority) {

        private val seated = Channel<Seat>(Channel.UNLIMITED)
        private val handled = Channel<Intent>(Channel.UNLIMITED)

        /** Where the house's own delivery goes, recorded before it reaches a socket. */
        private val effects: EffectWire = EffectWire(
            send = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
                if (body is RoundBody.Push) authority.delivered += seat to body.message
                host.send(seat, frame)
            },
        )

        private val intents = IntentDesk()

        private var minted = 0
        private val host: TransportHost = TransportHost(
            SeatLedger(SEATS, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = { 0L },
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(RoundWire::decodeOrNull)
                if (body is RoundBody.Act) arriving.trySend(seat to body)
            },
            onSeated = { seat -> seated.trySend(seat) },
        )

        /**
         * **One ordered queue, exactly as [LobbyHouse] has**, and for the reason it has one: the
         * desk and the emit boundary both suspend, so a request per `launch` reaches the rules in
         * the order the launches *finished*. That is what this test caught on its first run, and
         * it is why the production side queues rather than launches.
         */
        private val arriving = Channel<Pair<Seat, RoundBody.Act>>(Channel.UNLIMITED)
        private var attending: Job? = null

        private var port = 0
        private val phones = mutableListOf<Phone>()

        suspend fun start() {
            port = host.start(0)
            attending = scope.launch {
                for ((seat, act) in arriving) {
                    // The join: attribute by connection, put to the one gate, push what the rules
                    // produced through the emit boundary. E-D3-3's remaining half, as a bench.
                    val landing = intents.arrived(seat, act)
                    if (landing is IntentDesk.Landing.Put) {
                        val before = authority.emitted.size
                        authority.put(landing.intent)
                        for (effect in authority.emitted.drop(before)) {
                            effects.emit(effect, authority.state)
                        }
                        handled.trySend(landing.intent)
                    }
                }
            }
        }

        /** One phone, joining over loopback exactly as the host's own does. */
        suspend fun seat(): Phone {
            val client = TransportClient(
                ClientSession(), "127.0.0.1", port,
                nowMillis = { 0L },
                onFrame = { },
            )
            val job = scope.launch { client.run() }
            val seat = withTimeout(30_000) { seated.receive() }
            return Phone(seat, client, job).also { phones += it }
        }

        /** Wait until the rules have been put [n] requests. The rig's only synchronisation. */
        suspend fun awaitHandled(n: Int) {
            withTimeout(30_000) { repeat(n - drained) { handled.receive(); drained++ } }
        }

        private var drained = 0

        /** Give the pushes the last request produced time to reach every socket. */
        suspend fun settle() = delay(200)

        suspend fun stop() {
            phones.forEach { it.hangUp() }
            attending?.cancel()
            host.stop()
        }
    }

    private class Phone(val seat: Seat, private val client: TransportClient, private val job: Job) {

        private val outbox = IntentOutbox()

        /** A control pressed on this phone. The seat is not on it and cannot be. */
        suspend fun act(intent: IntentMessage) = raw(RoundWire.encode(outbox.ask(intent)))

        /** Bytes, exactly as given — how a resend and a forge are both produced. */
        suspend fun raw(body: String) = client.send(TransportFrame.Carry(body))

        fun hangUp() = job.cancel()
    }
}
