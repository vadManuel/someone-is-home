package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.launchValue
import kotlinx.coroutines.delay

/**
 * **A whole round, on five phones, over the real transport — and every place it had to be faked.**
 *
 * [LobbyCheat] takes the evening as far as LIGHTS OUT and stops, because until this unit nothing
 * could take it further: `app` could not see `core`, so no module that ships in this app could
 * reach `armingFor`. This is the rest of the evening, and the honest shape of it is two halves
 * that are very different in kind.
 *
 * ### The half that is real
 *
 * Every **request** in this script goes up a real websocket as an [IntentMessage] with no seat on
 * it, is attributed by [IntentDesk] to the connection it arrived on, and reaches `admit`. Every
 * **effect** the rules produce goes through `EmitSchema` and down the per-seat sockets. The
 * screens are moved by `FlowModel.houseSaid` and by nothing else. None of that is simulated:
 * a check-in pressed on the 13 Pro is a frame on the wire and a gate that moves on five phones.
 *
 * ### The half that is a stand-in, and every one of them is an escalation
 *
 * Four things a *player* does have **no way to reach the rules from a client at all**, and the
 * host mints the `Event` for them:
 *
 * 1. **The Revoke firing.** `Event.ContactMade` is phone-touched-to-phone — the BLE handshake is
 *    unbuilt hardware, and P2's `factOf` rules that it is *reported, never requested*, because a
 *    client that could send it would be naming the seat it revoked. The stand-in constructs the
 *    same `Event` the handshake will.
 * 2. **The Revoked report.** `MeetingTrigger.RevokeReported` is the same act by the same rule.
 * 3. **The Egress firing.** The finger is a client's; the type and the two nodes are the house's
 *    draw against a home's shape no phone holds.
 * 4. **The Sync Pulse.** `Event.SyncPulseReturned` *would* pass `factOf` and has **no wire member
 *    at all** — E-P2-5, named and not built. So containment cannot be reached from a phone today,
 *    which is the sharpest finding in this script.
 *
 * And two more that are not about hardware:
 *
 * 5. **The round's timed transitions.** There is no tick driver, so the gate closing, the talk
 *    ending, the window closing, T-half and the Egress expiring are all minted here. See
 *    [RoundAuthority].
 * 6. **The correct entry.** A phone is shown the *question* and never the answer (rule 3 —
 *    `SubroutineInstance` is physically incapable of carrying `expected`). A real player solves
 *    the puzzle on their screen; a bench cannot, because the inverse of the draw lives in `core`'s
 *    grading and in `ui`'s rendering and nowhere a bench can call. So the client's own entries go
 *    up the wire and are **honestly rejected**, and the meter is moved by the host reading its own
 *    answer key. Both halves are worth having: the rejection proves the verdict reaches the sender
 *    alone, and the acceleration proves the ending.
 */
class RoundCheat(
    private val client: RoundClient,
    /** The rules, on this phone. */
    private val authority: RoundAuthority?,
    /**
     * **Whether this phone is running the house** — the lobby's own answer, asked fresh.
     *
     * Deliberately not *does this object have a [RoundAuthority]*, which is what the first version
     * asked and is a different question: the app root builds one on every seat and it is simply
     * inert on four of them. The first five-seat run read that as *everybody is the host*, so every
     * phone took the host's abstention and the whole room voted Skip — a unanimous ballot that
     * looked exactly like a room that had decided something.
     */
    private val isHost: () -> Boolean,
    private val note: (String) -> Unit,
) {

    /** True when this process was launched to play a round rather than to be tapped. */
    val armed: Boolean = launchSwitch(ROUND_SWITCH)

    /** What the house has told this phone so far, for the reactive half to act on. */
    private var sawOpening = false
    private var meetingCaller: Seat? = null
    private var votedThisMeeting = false
    private var scanning = false

    /** Which of [BenchHome]'s cards this phone has tried. A player walks; this walks the list. */
    private var walked = 0

    /**
     * **The reactive half, on every seat including the host's own.**
     *
     * A phone in this game acts on what the house pushed it and on nothing else — there is no
     * out-of-band channel between five processes in a dark house, and inventing one for the bench
     * would make the rehearsal prove something about the bench. So every reaction below is keyed
     * on a push that really arrived on this phone's own socket.
     */
    fun react(message: RoundMessage) {
        when (message) {
            // The lights went out. The opening message is the one push every seat gets on arming,
            // and E-D3-3's closing proof is that it lands on all five.
            is RoundMessage.OpeningMessage -> sawOpening = true

            // The work order. A player walks to the nearest card and turns the lamp on it; the
            // order says WHAT, never WHERE (D-114), so this walks the house exactly as they do.
            is RoundMessage.WorkOrderIssued -> scanning = true

            // A scan that opened work: hand something over. It will be refused, and that is the
            // point — see the class KDoc, stand-in 6.
            is RoundMessage.ScanAnswered -> message.opened?.let { instance ->
                val marker = client.standingAt ?: return@let
                note("HANDING OVER — ${instance.subroutine}, AN ANSWER THIS PHONE CANNOT KNOW")
                client.act(IntentMessage.HandOverEntry(marker, instance.parameters))
            }

            // The ring. Everybody walks in, and the gate does not close a player short (D-104).
            is RoundMessage.MeetingOpened -> {
                meetingCaller = message.caller
                votedThisMeeting = false
                client.act(IntentMessage.CheckIn)
            }

            // **STAND AND WALK IN — and the gate cannot close without this seat.**
            //
            // D-134 gives a player outside the system no phone call: they get this instead, and
            // D-104's gate counts *every living player and every out player*. So the couch has to
            // check in like everybody else — and **on the first run of this rehearsal it could
            // not.** The gate stopped at 4 of 5 and every transition after it was refused
            // `WrongMeetingPhase`, on five phones, in silence.
            //
            // The fix here is one line and the finding is not: `StandAndWalkIn` is one of
            // E-P1-4's sixteen kinds with no route and no field, so **no screen in the built app
            // offers this seat a control**. The wire member exists and the rules accept it; what
            // is missing is the surface. See the escalation.
            is RoundMessage.StandAndWalkIn -> {
                votedThisMeeting = false
                client.act(IntentMessage.CheckIn)
            }

            is RoundMessage.MeetingPhaseOpened -> when (message.phase) {
                // Unanimous readiness closes the discussion early (D-117). Every living phone says
                // it, which is what makes the early close a fact about the room.
                MeetingPhase.Discussion -> client.act(IntentMessage.DeclareReadyToVote)
                // **The caller is the target, and that is a real screen fact.** Who called the
                // meeting reaches every living phone on `MeetingOpened`; picking a row on the
                // ballot is the one thing here a finger would do that the bench can do honestly
                // from something the house actually said. The host abstains -- see [theVote].
                MeetingPhase.Vote -> if (!votedThisMeeting) {
                    votedThisMeeting = true
                    val target = if (isHost()) null else meetingCaller
                    client.act(IntentMessage.SelectVote(target))
                    client.act(IntentMessage.LockVote)
                }
                else -> Unit
            }

            // **NEW ROUND, on every seat — and the fact that it has to be is the finding.**
            //
            // D-157 keeps the sockets up between rounds and L6 made NEW ROUND the actions layer's
            // control, drawn *only where this phone is the host* — which is right, because whether
            // a screen offers it depends on a fact no screen graph can hold. What follows from it
            // is that the host pressing NEW ROUND returns **the host** to the lobby and says
            // nothing to anybody else: `LobbyLink.roundEnded` is a call into the desk sitting
            // beside the host, and on a client the desk is not there. So four phones stay on the
            // ending until somebody hands them back.
            //
            // The bench presses it on all five so the wipe can be proved at all. See the
            // escalation: what is missing is a push, not a button.
            // Keyed on the **reveal** and not on `RoundEnded`, and the difference showed up on a
            // five-seat run: an ending is two pushes, and a bench that acted on the first walked
            // this phone back to the lobby a moment before the second arrived — whereupon
            // `Flow.endings` put it straight back on the ending screen, correctly. The reveal is
            // the last thing the house says and the moment the evening is actually about; nothing
            // should leave it before it lands.
            is RoundMessage.InsidersRevealed -> onEnding()

            else -> Unit
        }
    }

    /** What to do when the house says the round is over. Set by the app root; see [react]. */
    var onEnding: () -> Unit = {}

    /**
     * **What every seat does, host included: wait to be dealt work, then walk the house.**
     *
     * Everything else this phone does is a reaction to a push (see [react]). This is the one part
     * that is a player *moving*, and it is a loop rather than a reaction because walking to the
     * next card is what a person does between being told things.
     */
    suspend fun play() {
        note("IN THE ROUND — WAITING FOR THE LIGHTS TO GO OUT")
        var waited = 0L
        while (!scanning && waited < PATIENCE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        if (!scanning) {
            note("NO WORK ORDER ARRIVED IN ${PATIENCE_MILLIS / 1000}S — THIS PHONE IS NOT IN A ROUND")
            return
        }
        note("THE ORDER ARRIVED — ${client.order.size} LINE(S) · WALKING THE HOUSE")
        shift()
        note("WALKED EVERY CARD — ${client.verdict()}")
    }

    /**
     * **The walk: try cards until one of them is work this seat was given.**
     *
     * A work order names ten Subroutines and no rooms, and the active set is a subset of the
     * house's markers (D-123), so a player walking in the dark scans what they find and most of it
     * opens nothing. That is what this does, one card at a time, and the `SCAN OPENED NOTHING`
     * lines in the log are the shape of the real thing rather than a fault.
     */
    suspend fun shift() {
        while (scanning && walked < BenchHome.destinations.size) {
            val marker = BenchHome.destinations[walked++].marker
            client.scanning(marker)
            client.act(IntentMessage.BeginSubroutine(marker))
            delay(STEP_MILLIS)
        }
    }

    /**
     * **The host's script: the five acts, in the order the round has them.**
     *
     * Nothing here reaches into another phone. What it does is mint the events no client can send
     * and move the house's clock, and then wait for five phones to answer on their own.
     */
    suspend fun theRound(seed: Long) {
        val house = authority ?: return
        note("=== THE ROUND — ACT 1, THE LIGHTS GO OUT ===")
        if (!house.arm(seed)) return
        note("ARMED — ${house.state.seats.size} SEAT(S) IN THE ROUND")
        delay(SETTLE_MILLIS)

        theShift(house)
        theRevoke(house)
        theEgress(house)
        theEnding(house)
    }

    /** Act 2 — a shift: five phones walk the house, scan, hand over, and are graded. */
    private suspend fun theShift(house: RoundAuthority) {
        note("=== ACT 2, A SHIFT ===")
        shift()
        // Long enough for four other phones to have walked their own cards and been graded.
        delay(SHIFT_MILLIS)
        note("AFTER THE SHIFT — SYSTEM INTEGRITY ${house.state.systemIntegrity}")
    }

    /**
     * Act 3 — a Revoke, a report, and the whole meeting.
     *
     * **Three stand-ins in a row and they are marked one by one.** The contact is hardware that
     * does not exist; the report is the same act by the same rule; the meeting's four transitions
     * are the tick driver that does not exist.
     */
    private suspend fun theRevoke(house: RoundAuthority) {
        note("=== ACT 3, A REVOKE AND A MEETING ===")
        val insider = house.state.livingInsiders.firstOrNull()
        if (insider == null) {
            note("NO LIVING INSIDER — THE DRAW GAVE THIS ROUND NONE TO REVOKE WITH")
            return
        }
        // The Insider's cooldown starts the round already running at half (D-132), so the clock
        // has to reach the far side of it before an arm is anything but a silent refusal.
        val readyAt = house.state.cooldownFor(insider, home.someoneshome.model.InsiderAbility.Revoke)
            ?.readyAt?.step ?: 0L
        house.at(readyAt / Balance.TICKS_PER_SECOND + 1)
        house.mint(Event.RevokeArmed(house.now(), insider))

        val target = house.state.livingPlainResidents.firstOrNull { it.index != insider.index }
        if (target == null) {
            note("NOBODY TO REVOKE")
            return
        }
        // ** STAND-IN 1 — the contact handshake is unbuilt hardware.** This constructs exactly the
        // Event the BLE handshake will construct: phone touched to phone, reported by the house,
        // never requested by a client. See the class KDoc.
        note("STAND-IN — CONTACT MADE (THE BLE HANDSHAKE IS NOT BUILT)")
        house.mint(Event.ContactMade(house.now(), insider, target))
        delay(SETTLE_MILLIS)

        // ** STAND-IN 2 — the report, by the same rule as the contact.**
        val reporter = house.state.livingSeats.firstOrNull { it.index != insider.index }
        if (reporter == null) {
            note("NOBODY LEFT TO REPORT IT")
            return
        }
        note("STAND-IN — A REVOKED PLAYER WAS FOUND AND REPORTED BY SEAT ${reporter.index}")
        house.mint(
            Event.MeetingCalled(house.now(), reporter, MeetingTrigger.RevokeReported(target))
        )
        theMeeting(house)
    }

    /**
     * The meeting itself: the gate, the talk, the ballot, the tally, the takeover.
     *
     * The four transitions are minted here because nothing drives them — see [RoundAuthority]. The
     * *check-ins*, the *readiness* and the *votes* are not: those arrive from five phones on their
     * own, which is what makes the gate closing evidence about a room rather than about a script.
     */
    private suspend fun theMeeting(house: RoundAuthority) {
        // Everybody walks in. D-104's gate does not close a player short, so this waits for five.
        awaitPhase(house, MeetingPhase.CheckIn)
        note("THE GATE — WAITING FOR EVERY PHONE TO CHECK IN")
        delay(MEETING_MILLIS)
        val gate = house.state.meeting
        note("THE GATE STANDS AT — ${gate?.checkedIn?.size ?: 0} OF ${house.state.seats.size}")

        // ** STAND-IN 5 — the tick driver.** Each of these four is an authority push the loop must
        // carry on a schedule (D-134's E8-2), and there is nothing scheduling anything.
        note("STAND-IN — CLOSING THE DISCUSSION (NO TICK DRIVER)")
        house.mint(Event.DiscussionClosed(house.now()))
        delay(MEETING_MILLIS)

        note("STAND-IN — CLOSING THE VOTE WINDOW (NO TICK DRIVER)")
        house.mint(Event.VoteWindowClosed(house.now()))
        delay(SETTLE_MILLIS)

        note("STAND-IN — THE TALLY REACHES ITS HALFWAY MARK (NO TICK DRIVER)")
        house.mint(Event.TallyHalfwayReached(house.now()))
        delay(SETTLE_MILLIS)

        note("RESTRAINED — ${house.state.restrained.size} SEAT(S) OUT BY THE ROOM'S DECISION")
        house.mint(Event.MeetingClosed(house.now()))
        delay(SETTLE_MILLIS)
    }

    /** Act 4 — an Egress, fired, and contained by two seats at two nodes. */
    private suspend fun theEgress(house: RoundAuthority) {
        note("=== ACT 4, AN EGRESS ===")
        if (house.ended) {
            note("THE ROUND IS ALREADY OVER — NO EGRESS")
            return
        }
        val insider = house.state.livingInsiders.firstOrNull() ?: run {
            note("NO LIVING INSIDER LEFT TO FIRE ONE")
            return
        }
        // The shared clock starts the round running at half (D-132), like the Revoke's.
        house.at(house.state.egressReadyAt.step / Balance.TICKS_PER_SECOND + 1)
        // ** STAND-IN 3 — the finger. The draw itself is the house's and is real.**
        note("STAND-IN — THE EGRESS IS FIRED (NO WIRE MEMBER FOR THE FINGER)")
        if (!house.fireEgress(insider)) return
        delay(SETTLE_MILLIS)

        val nodes = house.state.egress?.nodes.orEmpty()
        val pair = house.state.livingSeats.take(2)
        if (nodes.size < 2 || pair.size < 2) {
            note("NOT ENOUGH NODES OR PEOPLE TO CONTAIN IT")
            return
        }
        // ** STAND-IN 4 — the Sync Pulse has NO WIRE MEMBER (E-P2-5).** Two people at two nodes
        // cannot reach the rules from a phone at all today. Both halves are minted: the scan that
        // puts each of them at a node (D-136 — a performer's own scan is how the house knows where
        // anybody is), and the four taps, timed off the scheduled beat.
        note("STAND-IN — TWO SEATS SCAN TWO NODES AND TAP FOUR BEATS (NO WIRE MEMBER)")
        pair.forEachIndexed { i, seat ->
            house.mint(Event.MarkerScanned(house.now(), seat, nodes[i]))
        }
        val fired = house.state.egress?.let { it.deadline.step - Balance.EGRESS_TIMER } ?: 0L
        val beats = List(Balance.SYNC_PULSE_BEATS) { b ->
            fired + Balance.SYNC_PULSE_LEAD + b * Balance.SYNC_PULSE_INTERVAL
        }
        pair.forEach { seat -> house.mint(Event.SyncPulseReturned(house.now(), seat, beats)) }
        delay(SETTLE_MILLIS)
        note(
            if (house.state.egressRunning) "THE EGRESS IS STILL RUNNING"
            else "THE EGRESS WAS CONTAINED — THE WIDGET REVERTS EVERYWHERE"
        )
    }

    /**
     * Act 5 — the meter to zero, the reveal, and a new lobby.
     *
     * ** STAND-IN 6 — the host reads its own answer key.** A phone is shown the question and never
     * the answer, so nothing on a client can produce a correct entry; the bench that could would
     * be a phone that can adjudicate, which is rule 8's whole objection. So the meter is moved
     * here, in process, one line of one order at a time, and it is flagged rather than dressed up
     * as five people finishing their work.
     */
    private suspend fun theEnding(house: RoundAuthority) {
        note("=== ACT 5, AN ENDING ===")
        if (house.ended) {
            note("THE ROUND ALREADY ENDED — ${house.state.outcome}")
            return
        }
        note("STAND-IN — BANKING CORRECT ENTRIES OFF THE HOUSE'S OWN ANSWER KEY")
        var guard = 0
        while (!house.ended && guard++ < ENTRY_CEILING) {
            val worked = house.state.livingPlainResidents.any { seat -> bank(house, seat) }
            if (!worked) break
        }
        note("SYSTEM INTEGRITY — ${house.state.systemIntegrity}")
        delay(SETTLE_MILLIS)

        // **The bar can be out of reach, and when it is, this says so with the arithmetic.**
        //
        // The meter counts plain Residents, and every seat that leaves the round takes its whole
        // work order with it. On the first five-seat run that reached a Restrain the bar stopped
        // at 5 with every remaining line completed: two plain Residents were left holding sixteen
        // lines against a bar of nineteen, and nothing any of them could do would move it again.
        // That is F-005's orphan half — E-L3-3 — arriving on hardware. See the escalation.
        if (!house.ended) {
            val left = house.state.livingPlainResidents
            val reachable = left.sumOf { seat ->
                house.state.workOrderFor(seat)?.entries?.count { it.anchor != null } ?: 0
            }
            note(
                "THE BAR IS OUT OF REACH — ${house.state.systemIntegrity} STILL TO CLEAR, " +
                    "${left.size} PLAIN RESIDENT(S) LEFT HOLDING $reachable LINE(S) IN TOTAL, " +
                    "EVERY ONE OF THEM ALREADY DONE"
            )
            theOtherEnding(house)
        }
        note("THE ROUND ENDED — ${house.state.outcome ?: "IT DID NOT"}")
        if (house.ended) delay(SETTLE_MILLIS)
    }

    /**
     * **The round ends the other way: an Egress nobody contains.**
     *
     * Reached only when the meter is out of reach, and it is not a consolation prize — D-131's
     * fourth route is a real ending and it is the one that stays available when the Residents'
     * own has closed. What it buys this rehearsal is the half the meter would have proved: the
     * reveal reaching five phones, and a lobby to go back to.
     */
    private suspend fun theOtherEnding(house: RoundAuthority) {
        val insider = house.state.livingInsiders.firstOrNull() ?: run {
            note("NO LIVING INSIDER — THIS ROUND HAS NO ENDING LEFT AT ALL")
            return
        }
        house.at(house.state.egressReadyAt.step / Balance.TICKS_PER_SECOND + 1)
        note("STAND-IN — A SECOND EGRESS IS FIRED, AND NOBODY WILL CONTAIN IT")
        if (!house.fireEgress(insider)) return
        // ** STAND-IN 5 again — the tick driver.** The countdown reaching zero is the house's own
        // fact and there is nothing counting.
        val deadline = house.state.egress?.deadline?.step ?: return
        house.at(deadline / Balance.TICKS_PER_SECOND + 1)
        note("STAND-IN — THE EGRESS COUNTDOWN REACHES ZERO (NO TICK DRIVER)")
        house.mint(Event.EgressExpired(house.now()))
    }

    /**
     * One line of one seat's order, scanned and banked correctly.
     *
     * The scan is what arms the instance (D-110) and the entry is what spends it, so both halves
     * have to happen — and the answer is read off `OpenSubroutine.expected`, which is the authority
     * state a client is physically incapable of holding.
     */
    private suspend fun bank(house: RoundAuthority, seat: Seat): Boolean {
        val order = house.state.workOrderFor(seat) ?: return false
        val entry = order.entries.firstOrNull { order.isActionable(it) } ?: return false
        // An entry with no anchor is one no card opens, so there is nothing to walk to and
        // nothing to bank. Skipped rather than guessed at.
        val marker: MarkerId = entry.anchor ?: return false
        house.mint(Event.MarkerScanned(house.now(), seat, marker))
        val open = house.state.openSubroutineFor(seat)?.takeIf { it.armed } ?: return false
        house.mint(Event.SubroutineReturned(house.now(), seat, marker, open.expected))
        return true
    }

    companion object {

        /** Play a round after the lobby is ready. Set on every seat that is taking part. */
        const val ROUND_SWITCH = "SOMEONES_HOME_ROUND"

        /** The arming seed, so a rehearsal can be replayed. Defaulted, never random. */
        const val SEED_SWITCH = "SOMEONES_HOME_ROUND_SEED"

        /** The default seed. Written down rather than drawn: a rehearsal that cannot be repeated. */
        const val SEED: Long = 20260822L

        fun seed(): Long = launchValue(SEED_SWITCH)?.toLongOrNull() ?: SEED

        private const val STEP_MILLIS = 400L
        private const val SETTLE_MILLIS = 1_500L
        private const val SHIFT_MILLIS = 12_000L
        private const val MEETING_MILLIS = 6_000L

        /** How long a seat waits to be dealt work before saying out loud that it never was. */
        private const val PATIENCE_MILLIS = 60_000L

        /** A bound on the banking loop, so a rule that never moves the meter cannot hang a phone. */
        private const val ENTRY_CEILING = 200
    }
}

/** Wait until the meeting is at [phase], or give up. The bench's only synchronisation. */
private suspend fun awaitPhase(house: RoundAuthority, phase: MeetingPhase) {
    var waited = 0L
    while (house.state.meeting?.phase != phase && waited < 10_000L) {
        delay(250)
        waited += 250
    }
}
