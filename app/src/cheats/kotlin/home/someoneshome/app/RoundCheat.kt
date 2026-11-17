package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.launchValue
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.RevokeState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.revokeShown
import home.someoneshome.ui.Subroutine
import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay

/**
 * **A whole round, on five seats, over the real transport — and every place it is still faked.**
 *
 * P3 played the first one and its list of stand-ins was six long. R1 built the tick driver and the
 * four missing wire members; C3 routed what was left of the effects that move a screen. **This is
 * the same script with the parts those units made real taken out of it**, and the value of the unit
 * is the difference between the two lists rather than the fact that a round ran again.
 *
 * ### What was a stand-in in P3 and is real here
 *
 * | P3 | now |
 * |---|---|
 * | the round's five timed transitions were minted by this class | [tickHouse] mints all five off a monotonic clock |
 * | the Egress firing had no wire member | an Insider's phone holds the EGRESS tile on its page 2; the house admits it ([RoundBody.Fire], D-231) |
 * | the Revoke's arming was minted by this class | an Insider's phone holds the REVOKE tile on its page 2 ([IntentMessage.ArmRevoke], D-231) |
 * | the Sync Pulse had no wire member at all | two seats scan a node and send [IntentMessage.ReturnSyncPulse] up their own socket |
 * | STOP NOW had no wire member | one seat presses it on real work ([IntentMessage.StopSubroutine]) |
 * | the hand-over had no send seam | every entry in the round goes up as [IntentMessage.HandOverEntry] |
 *
 * ### No player's act is minted by this class (D-231)
 *
 * The Revoke's arm, the containment beats and the answer-key scans and hand-overs were all minted
 * here until D-231, so rehearsals showed a Revoke landing and an Egress contained while the phones
 * could do neither. All three are gone. What is still stood in is **below** the acts: the contact
 * and the report are concluded by the house's resolver from a [SimulatedAir] (D-217), and the hop
 * up the other phones' sockets is [HouseAir]'s, because five processes share no air.
 *
 * ### What the phones still cannot do, and the bench now shows it
 *
 * **Contain an Egress.** No screen draws a containment control, and `onTheBeat` grades taps against
 * `egress.firedAt` in absolute house ticks while nothing on the wire carries the house's clock. The
 * bench's own unanchored taps go up and miss; nothing contains the Egress after that. **Answer a
 * Subroutine correctly**: a phone is shown the question and never the answer (rule 3), so the
 * bench's entries are honestly rejected and the meter does not reach the bar.
 *
 * ### The clock is pushed forward and never turned by hand
 *
 * P3's script jumped [RoundAuthority.at] to the far side of every cooldown. With a real driver that
 * is no longer possible and no longer wanted: the authority's clock is the driver's, so the bench
 * moves **the driver's view of real time** instead ([skew]) and waits for the house to arrive on its
 * own. Every one of the five transitions is still minted by [HouseClock] off elapsed nanoseconds; a
 * skip is reported by the driver as abandoned steps, out loud, which a jumped counter never was.
 */
class RoundCheat(
    private val client: RoundClient,
    /**
     * **This seat's own screens, so the bench presses controls instead of composing frames.**
     *
     * ⚠️ **Its absence is why every rehearsal in this project's history missed the hand-over
     * outage.** On a caught scan this script used to publish `IntentMessage.HandOverEntry` straight
     * out of [RoundClient.act], with the marker off its own [RoundClient.standingAt] and the
     * house's own parameters as the answer — a perfect frame, assembled by the instrument, that no
     * finger could ever have produced. So the wire looked healthy on five seats all evening while
     * BEGIN opened the wrong Subroutine and posted nothing, and the bug reached a person's hands.
     *
     * A bench that builds the request it is checking for is a bench that agrees with itself. The
     * scan reaction now walks the controls a player has — BEGIN, the taps, the hand-over — through
     * [FlowModel.actions], and what goes up is whatever those produce. See [react].
     */
    private val flow: FlowModel,
    /** The rules, on this phone. */
    private val authority: RoundAuthority?,
    /**
     * **Whether this phone is running the house** — the lobby's own answer, asked fresh.
     *
     * Deliberately not *does this object have a [RoundAuthority]*, which is what the first version
     * asked and is a different question: the app root builds one on every seat and it is simply
     * inert on four of them. P3's first five-seat run read that as *everybody is the host*, so every
     * phone took the host's abstention and the whole room voted Skip — a unanimous ballot that
     * looked exactly like a room that had decided something.
     */
    private val isHost: () -> Boolean,
    /** This seat's screen, at every beat. Null on a seat with no ledger attached. */
    private val ledger: ScreenLedger? = null,
    /**
     * **True when this process was launched to play a round rather than to be tapped** — the
     * [ROUND_SWITCH], read once. A parameter only so a test can hold a bench seat beside a person's.
     *
     * ⚠️ **It gates [react]'s acts as well as [play]**, and until D-237 it did not: the app root
     * wires [react] to every phone's pushes, so every phone a person launched from the springboard
     * cast a vote for the meeting's caller by itself the instant the ballot opened (D-232).
     */
    val armed: Boolean = launchSwitch(ROUND_SWITCH),
    private val note: (String) -> Unit,
) {

    /**
     * **How far ahead of real time the house's clock is being run, in nanoseconds.**
     *
     * Read by the driver's `clockNanos` in the app root and written only by [skipTo]. It is the
     * bench's one remaining hand on the clock and it is a *push*, not a set: the driver still
     * measures, still converts to whole steps, still abandons a backlog it cannot chase, and still
     * says so. What it buys is that a rehearsal does not stand in the dark for the ninety seconds of
     * an Egress cooldown five times an evening.
     */
    @Volatile
    var skewNanos: Long = 0L
        private set

    /** What the house has told this phone so far, for the reactive half to act on. */
    @Volatile
    private var sawOpening = false
    private var meetingCaller: Seat? = null
    private var votedThisMeeting = false
    @Volatile
    private var scanning = false

    /** How far along its own order's destinations this phone has walked. See [shift]. */
    private var walked = 0

    /**
     * **The page-2 tiles this seat keeps its thumb on, and why they are flags rather than calls.**
     *
     * Each phone is told its own role (`RoleRevealed`), and only an Insider's page 2 takes a hold
     * (D-142), so the bench presses only where its own screen says Insider, through the actions the
     * tiles call (D-231). A press the house refuses (a clock still running) is silent and costs
     * nothing.
     */
    private var pressingEgress = false
    private var pressingStatic = false

    /**
     * **The REVOKE tile, held on this phone's own page 2** (D-231), from the work order until the
     * first meeting rings. Only an Insider's page 2 answers a hold (D-142), so only a phone whose
     * screen says it is an Insider presses it, through the same action the tile calls. It is held
     * once, when the tile reads READY (D-239): the tile's clock is the public one, counted on this
     * phone from LIGHTS OUT, so the hold comes after the house's clock too.
     */
    private var pressingRevoke = false
    private var heldRevoke = false
    private var egressRunning = false
    private var jammed = false
    private var over = false

    /**
     * **When this phone rang, so that walking in takes a few seconds.**
     *
     * The first complete run checked five phones in inside one second and the ring was gone before
     * anything could look at it: one `ON FOUND` across five logs and not a single `ON CALLING`, in a
     * round where the caller's own message reached the calling seat exactly as E-R1-1's ruling says
     * it should. That is the bench's fault and not the build's — in a house, five people walk to a
     * card. Held under the ring's own six-second presentation on purpose, so the gate still closes
     * inside it and R1's second stall is still the thing being avoided rather than re-created.
     */
    private var ringingSince: Long? = null

    /** When the talk opened on this phone, so a hand goes up after it rather than during it. */
    private var talkingSince: Long? = null

    /** When this phone made its selection, so READY lands seconds later rather than with it. */
    private var votingSince: Long? = null

    /** When the reveal landed, so this phone stands on the ending long enough to be read. */
    private var revealedAt: Long? = null
    private var handedBack = false

    /** The nodes the house published, if this seat has been asked to walk to one. */
    private var walkTo: MarkerId? = null

    /** Set once the pulse has been returned from this seat, so a retry cannot re-scan a node. */
    private var pulsed = false

    /**
     * **Which node this bench walks to when an Egress opens — the finger, as a switch.**
     *
     * A person in a dark house knows which end of the building they are nearer. A process does not,
     * and there is nothing on the wire that would tell it: [RoundMessage.EgressOpened] carries both
     * nodes to everybody, on purpose, because coordination is required and nobody may speak. So the
     * two seats that go are the two the launch named, exactly as [LobbyCheat] names who is hosting.
     */
    private val node: Int? = launchValue(NODE_SWITCH)?.toIntOrNull()

    /** Whether this bench presses STOP NOW on the first work a scan opens, instead of finishing it. */
    private val stops: Boolean = launchSwitch(STOP_SWITCH)
    private var stopped = false

    /**
     * **A card has been caught and the work has not been begun yet** — set by [react], cleared by
     * the thumb in [pressing].
     *
     * A flag rather than a direct call for the reason written at the reaction: the push that raises
     * it is read off the socket before the screen has processed it, so a BEGIN pressed in the same
     * breath would open whatever the *previous* scan left standing. Waiting for the phone to be on
     * `ScanCaught` is the bench doing what a person does — looking at the screen before pressing
     * the button on it.
     */
    private var caughtScan = false

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
            is RoundMessage.WorkOrderIssued -> {
                scanning = true
                pressingRevoke = true
            }

            // A scan that opened work. **STOP NOW first, and only once**: the control is on every
            // Subroutine screen, it had no wire member until R1, and the one thing that proves a
            // member is a phone pressing it on work that was really open.
            //
            // ⚠️ **Everything else is now pressed rather than composed, and that is N1's finding
            // about this bench.** See the [flow] parameter: the old line published a `HandOverEntry`
            // from here, so the rehearsal exercised the wire and never the button, and the button
            // was the broken thing. The presses cannot happen in this method though — `read` runs
            // before `RoundOverTransport` has queued anything, so at this instant the screen has
            // not caught up and `PanelState.current` is still the previous card's. The flag is
            // raised here and the thumb lands in [pressing], once the phone is really standing on
            // the caught scan. A bench that pressed BEGIN before the row arrived would be a bench
            // racing the thing it is measuring.
            is RoundMessage.ScanAnswered -> message.opened?.let { instance ->
                if (client.standingAt == null) return@let
                if (stops && !stopped) {
                    stopped = true
                    note("STOP NOW — WALKING AWAY FROM ${instance.subroutine} WITH IT OPEN")
                    client.act(IntentMessage.StopSubroutine)
                    return@let
                }
                note("CAUGHT A SCAN — ${instance.subroutine}, AND THE THUMB GOES TO BEGIN")
                caughtScan = true
            }

            // The ring. Everybody walks in, and the gate does not close a player short (D-104).
            is RoundMessage.MeetingOpened -> {
                pressingRevoke = false
                meetingCaller = message.caller
                votedThisMeeting = false
                ringingSince = monotonicNanos()
            }

            // **STAND AND WALK IN — and the gate cannot close without this seat.**
            //
            // D-134 gives a player outside the system no phone call: they get this instead, and
            // D-104's gate counts *every living player and every out player*. P3 found the gate
            // stuck at 4 of 5 because `StandAndWalkIn` was routeless and no screen offered this
            // seat a control. R1 closed it — the kind now puts the seat on `Ghost2`, which has
            // drawn the living's own CHECK IN since the out screens were ported.
            is RoundMessage.StandAndWalkIn -> {
                votedThisMeeting = false
                ringingSince = monotonicNanos()
            }

            is RoundMessage.MeetingPhaseOpened -> when (message.phase) {
                // **Unanimous readiness closes the discussion early (D-117), and this bench waits a
                // few seconds before putting its hand up.** Not decoration: a room that has just
                // been told somebody was Revoked talks, and a hand that goes up in the same
                // millisecond the phase opened closes the talk inside one poll interval — which
                // leaves no discussion for anything to observe and no discussion to photograph.
                // The wait is the finger's, so it lives in [pressing] with the other two thumbs.
                MeetingPhase.Discussion -> talkingSince = monotonicNanos()
                // **The caller is the target, and that is a real screen fact.** Who called the
                // meeting reaches every living phone on `MeetingOpened`; picking a row on the
                // ballot is the one thing here a finger would do that the bench can do honestly
                // from something the house actually said. The host abstains -- see below.
                // **The selection goes up at once and READY goes up seconds later**, which is the
                // ballot D-117 describes rather than a compression of it: selections are published
                // live to the couch and the lock is irrevocable, so a phone that did both in one
                // millisecond would make the live half unobservable — four seats locking together
                // closes the window early, and the first run of this rehearsal had a whole ballot
                // that opened and closed inside half a second with nothing able to see it.
                // **Only on a bench seat** (D-237). `react` hears every phone's pushes, a person's
                // included, and a phone a person holds never votes by itself (D-232).
                MeetingPhase.Vote -> if (armed && !votedThisMeeting) {
                    votedThisMeeting = true
                    val target = if (isHost()) null else meetingCaller
                    client.act(IntentMessage.SelectVote(target))
                    votingSince = monotonicNanos()
                }
                else -> Unit
            }

            // **The meeting is over and the thumbs go onto the Egress tile** (D-141's two-second
            // hold, once every few seconds). [pressing] holds them only on a phone whose own screen
            // says Insider, because only that page 2 takes a hold (D-142, D-231).
            is RoundMessage.MeetingEnded -> {
                pressingEgress = true
                // **Both tiles, from the same moment.** The first run set this one only on a
                // containment and the containment failed, so the jam never got pressed at all and
                // the round ended with a whole ability unexercised. Which of the two lands first is
                // then the COOLDOWNS' answer rather than the script's: D-184 brings every per-seat
                // clock out of a meeting at half, and half of Static's 120 is less than half of the
                // Egress's 180.
                pressingStatic = true
            }

            // The house is on fire. Two facts follow and they are different: this seat stops
            // pressing while one is running, and the seat the launch named walks to its node.
            is RoundMessage.EgressOpened -> {
                egressRunning = true
                // The card, off the destination the house named it with -- the bench walks to a
                // marker, and the room beside it is what a player would read.
                node?.let { walkTo = message.nodes.getOrNull(it)?.marker }
            }

            is RoundMessage.EgressContained -> {
                egressRunning = false
                pressingStatic = true
            }

            is RoundMessage.StaticOpened -> jammed = true

            // **The round is over, and everything this seat was doing in it stops being true**
            // (D-259). A second round reuses this object (`SOMEONES_HOME_LOBBY_AGAIN`), and its work
            // order lands BEFORE its opening message, so the slate is wiped here, at the end of
            // this round, never at the start of the next. What the ending itself still needs —
            // [over], [revealedAt] and [handedBack] — is reset by [play] for the round it plays.
            is RoundMessage.RoundEnded -> {
                over = true
                sawOpening = false
                scanning = false
                walked = 0
                pressingEgress = false
                pressingStatic = false
                pressingRevoke = false
                heldRevoke = false
                egressRunning = false
                jammed = false
                meetingCaller = null
                votedThisMeeting = false
                ringingSince = null
                talkingSince = null
                votingSince = null
                walkTo = null
                pulsed = false
                caughtScan = false
            }

            // **NEW ROUND, on every seat — and the fact that it has to be is still the finding.**
            //
            // Keyed on the **reveal** and not on `RoundEnded`: an ending is two pushes, and a bench
            // that acted on the first walked this phone back to the lobby a moment before the
            // second arrived, whereupon `Flow.endings` put it straight back on the ending screen,
            // correctly (E-P3-6). What is missing on the other four phones is a push, not a button.
            is RoundMessage.InsidersRevealed -> revealedAt = monotonicNanos()

            else -> Unit
        }
    }

    /** What to do when the house says the round is over. Set by the app root; see [react]. */
    var onEnding: () -> Unit = {}

    /**
     * **A beat, taken after the house's push has had time to reach five phones.**
     *
     * A beat is a snapshot of five screens and the host announces it the instant the *rules* reach a
     * moment — which is before any of the four sockets has carried the push that moves anybody. The
     * first run of this rehearsal took every beat at t+0 and the whole meeting's ring is missing
     * from its table: `MEETING-RINGING` and `CHECKIN` both photographed five phones still standing
     * on the Subroutine screens they had been on a moment earlier, which is true and useless.
     *
     * **It is a named delay and not a wait for anything to become true**, deliberately. A beat that
     * waited for the screens to agree with the script would be a beat that could never disagree with
     * it, and disagreeing is the entire job.
     */
    private suspend fun beat(name: String) {
        delay(BEAT_SETTLE_MILLIS)
        // A second round's beats say so, so a hunt's pictures of two rounds cannot be confused.
        ledger?.beat(if (plays > 1) "AGAIN-$name" else name)
    }

    /** How many rounds [play] has been started for on this seat. */
    @Volatile
    private var plays = 0

    /**
     * **What every seat does, host included: wait to be dealt work, walk the house, then keep a
     * thumb on the two tiles the round asks for.**
     *
     * Everything here is a reaction to a push (see [react]) except the walking, which is a loop
     * because walking to the next card is what a person does between being told things.
     */
    suspend fun play() {
        // **This round's ending is not the last one's** (D-259): the last round's reveal, its
        // over and its hand-back are what ended the [pressing] loop that came before this call.
        plays++
        over = false
        revealedAt = null
        handedBack = false
        note("IN THE ROUND — WAITING FOR THE LIGHTS TO GO OUT")
        if (!awaitTheWorkOrder(lightsOut = { sawOpening }, ordered = { scanning }, note = note)) return
        beat("WORK-ORDER")
        note("THE ORDER ARRIVED — ${client.order.size} LINE(S) · WALKING THE HOUSE")
        shift()
        note("WALKED EVERY CARD — ${client.verdict()}")
        pressing()
    }

    /**
     * **The walk: the cards this seat's own order sent it to.**
     *
     * ⚠️ **It used to walk `BenchHome`'s ten ids**, which was the bench knowing the house's
     * geography — a fact no phone in this game has. That was harmless while the round was armed in
     * the same fixture and became a lie the moment it was not: a bench walking a home the round is
     * not in scans ten cards that open nothing, forever, and reports a healthy wire the whole time.
     *
     * What a phone really holds is the destinations on its **own** order lines, which the house
     * sent it and narrowed to that seat (`WorkOrder.asLines`). So the walk is now what a player
     * with a work order in the dark actually does: go to the places the order names. Cards this
     * seat has no work at are still reachable and still open nothing — that is D-124 and the
     * `SCAN OPENED NOTHING` lines are still the shape of the real thing — but they are no longer
     * the only thing this bench can find.
     */
    suspend fun shift() {
        val mine = client.order.filterIsInstance<OrderLine.Known>()
            .flatMap { it.at }
            .map { it.marker }
            .distinctBy { it.value }
        while (scanning && walked < mine.size) {
            val marker = mine[walked++]
            client.scanning(marker)
            client.act(IntentMessage.BeginSubroutine(marker))
            delay(STEP_MILLIS)
        }
    }

    /**
     * **The thumb, for the rest of the round — on every seat, for the whole of it.**
     *
     * Every control here goes up this phone's own socket as a frame the house may refuse in silence,
     * and the page-2 ones go through [FlowModel.actions], the same lambdas the tiles hold (D-231).
     */
    private suspend fun pressing() {
        while (!handedBack) {
            walkTo?.let { destination ->
                walkTo = null
                if (!pulsed) {
                    pulsed = true
                    reachNodeAndPulse(destination)
                }
            }
            if (caughtScan && flow.state.screen == ScreenId.ScanCaught) {
                caughtScan = false
                workTheCaughtScan()
            }
            ringingSince?.let { rang ->
                if (monotonicNanos() - rang > RING_NANOS) {
                    ringingSince = null
                    client.act(IntentMessage.CheckIn)
                }
            }
            talkingSince?.let { opened ->
                if (monotonicNanos() - opened > TALK_NANOS) {
                    talkingSince = null
                    client.act(IntentMessage.DeclareReadyToVote)
                }
            }
            votingSince?.let { chose ->
                if (monotonicNanos() - chose > BALLOT_NANOS) {
                    votingSince = null
                    client.act(IntentMessage.LockVote)
                }
            }
            // Held down again every few seconds, which is what a person does with a tile that did
            // nothing. `Balance.EGRESS_COOLDOWN` is the house's and the phone is not told it.
            //
            // **Pressed on the panel, through the actions the tiles call, and only on a phone whose
            // own screen says Insider** (D-231): a Resident's page 2 takes no pointer input (D-142),
            // so a bench that pressed there would be doing what no thumb can. Until D-231 these went
            // straight to `RoundClient`, which is how the bench kept firing Egresses while the tile
            // a person holds sent nothing at all.
            if (flow.state.role == PanelRole.Insider) {
                val tiles = flow.actions()
                // REVOKE is held ONCE, when this phone's own tile says READY, which is what a person
                // does (D-239). Re-holding it every second hid that a hold into the opening clock was
                // refused while the tile said ARMED: the next hold landed and the bench looked fine.
                if (!over && pressingRevoke && !heldRevoke && flow.state.revokeShown == RevokeState.Ready) {
                    heldRevoke = true
                    tiles.armRevoke()
                }
                if (!over && pressingEgress && !egressRunning) tiles.armEgress()
                if (!over && pressingStatic && !jammed) tiles.fireStatic()
            }
            revealedAt?.let { landed ->
                if (monotonicNanos() - landed > ENDING_NANOS) {
                    handedBack = true
                    onEnding()
                }
            }
            delay(PRESS_MILLIS)
        }
    }

    /**
     * **BEGIN, the work, and the hand-over — pressed on the real screens** (N1).
     *
     * The three controls a player touches between catching a card and standing on
     * `RETURNED . WAITING`, driven through [FlowModel.actions] because that is where the outage
     * was: `beginSubroutine` is a lambda the button holds, and every test and every bench that
     * called the model method behind it stayed green while the button opened Handshake and posted
     * nothing.
     *
     * **Which Subroutine is not decided here.** It is read back off the screen after BEGIN, which
     * is the only honest way for an instrument to name it — a bench that picked the kind off the
     * house's push and then pressed the matching screen would be routing the round itself, and
     * would agree with itself about the one thing under test.
     *
     * **The taps are noise on purpose.** The entry a player makes is graded by the house and this
     * process has no idea what the answer is (D-042); what a rehearsal proves is that a finger's
     * input becomes an entry, reaches the wire against the right card, and is answered. A refusal
     * is the expected outcome and is not a failure of anything here.
     */
    private fun workTheCaughtScan() {
        val actions = flow.actions()
        actions.beginSubroutine()
        val opened = Subroutine.entries.firstOrNull { it.screen == flow.state.screen }
        if (opened == null) {
            // BEGIN moved this phone somewhere that is not a Subroutine at all. Reported rather
            // than retried: it is exactly the shape of the defect N1 closed, and a bench that
            // quietly pressed again would turn it back into a silence.
            note("BEGIN OPENED NO SUBROUTINE — STILL ON ${flow.state.screen}")
            return
        }
        note("BEGAN ${opened.label} — WORKING IT ON THE REAL SCREEN")
        repeat(entryTaps(opened)) { actions.tapSubroutine(opened, it) }
        actions.handOverSubroutine(opened)
        note("HANDED OVER ${opened.label} — AN ANSWER THIS PHONE CANNOT KNOW")
    }

    /**
     * **Only what the screen draws** (H3, D-252). Signal Trace's nodes are the house's wiring, and
     * D-238's graphs can hold seven: [ENTRY_TAPS] tapped an eighth node no screen draws, and the
     * 4-seat dry run at seed 20261150 failed in the canvas. A finger cannot tap a node that is not
     * there, so the bench taps the nodes dealt.
     */
    internal fun entryTaps(opened: Subroutine): Int = when (opened) {
        Subroutine.SignalTrace -> minOf(ENTRY_TAPS, flow.subroutines.wiring.nodes.size)
        else -> ENTRY_TAPS
    }

    /**
     * **Walk to a node, and answer the beat — the two halves that are now on the wire, and the one
     * that is not.**
     *
     * The scan is real and is what puts this seat at the node: the house reads presence off the
     * card a performer scanned and never off a claim a phone makes (D-136), which is why
     * [IntentMessage.ReturnSyncPulse] carries no node and could not be given one.
     *
     * ⚠️ **The four taps are the honest failure this unit found.** `onTheBeat` grades them against
     * `egress.firedAt` in absolute house ticks, and a phone has never been told what time it is —
     * `EgressOpened` carries `remaining`, which is a duration. So the best a phone can compose is
     * the schedule's own shape from `Balance` with **no anchor under it**, and that is what goes up.
     * It will miss, the house will answer `held = false`, and that answer is the evidence. Nothing
     * contains the Egress after it: the host's mint that used to is gone (D-231).
     */
    private suspend fun reachNodeAndPulse(destination: MarkerId) {
        note("WALKING TO ${destination.value} — THE HOUSE WILL READ THIS SEAT'S OWN SCAN")
        client.scanning(destination)
        client.act(IntentMessage.BeginSubroutine(destination))
        delay(STEP_MILLIS)
        val taps = List(Balance.SYNC_PULSE_BEATS) { b ->
            Balance.SYNC_PULSE_LEAD + b * Balance.SYNC_PULSE_INTERVAL
        }
        note("RETURNING THE SYNC PULSE — ${taps.size} TAP(S), UNANCHORED (NO CLOCK ON THE WIRE)")
        client.act(IntentMessage.ReturnSyncPulse(taps))
    }

    /**
     * **The host's script: the acts, in the order the round has them.**
     *
     * Nothing here reaches into another phone. What it does is mint the two events no client can
     * send, push the house's clock past the cooldowns a rehearsal should not stand and wait for, and
     * then wait for five phones to answer on their own.
     */
    /**
     * **The scripted evening, which no longer starts itself.**
     *
     * ⚠️ **Act 1 used to be `house.arm(seed)` and it is gone.** Arming is production's now
     * ([armAtLightsOut]) and is reached the way a person reaches it — a live LIGHTS OUT, held. A
     * bench that armed its own round would be the second thing in the app that can start one, and
     * the two would draw different rounds from different seeds on the same press.
     *
     * So this **waits for the house the host's own thumb armed** and then plays the acts against
     * it. That is the same demotion [react] took when it stopped composing its own hand-over
     * frames: the instrument stops doing the thing under test.
     */
    suspend fun theRound(seed: Long) {
        val house = authority ?: return
        note("=== THE ROUND — ACT 1, WAITING FOR THE HOST'S OWN LIGHTS OUT ===")
        if (!awaitArmed(house)) {
            note("NO ROUND WAS EVER ARMED ON THIS PHONE — THE SCRIPT HAS NOTHING TO PLAY")
            return
        }
        note("ARMED — ${house.state.seats.size} SEAT(S) IN THE ROUND")
        beat("ARMED")
        delay(SETTLE_MILLIS)

        theShift(house)
        theRevoke(house)
        theStatic(house)
        theEgress(house)
        theEnding(house)
    }

    /** Act 2 — a shift: five phones walk the house, scan, hand over, and are graded. */
    private suspend fun theShift(house: RoundAuthority) {
        note("=== ACT 2, A SHIFT ===")
        shift()
        // Long enough for four other phones to have walked their own cards and been graded.
        delay(SHIFT_MILLIS)
        beat("SHIFT-DONE")
        note("AFTER THE SHIFT — SYSTEM INTEGRITY ${house.state.systemIntegrity}")
    }

    /**
     * Act 3 — a Revoke, a report, and the whole meeting.
     *
     * **The contact and the report are no longer minted** (D-217). Both were stand-ins until the
     * radio existed: this act now walks the Insider's phone to the target's in a [SimulatedAir], the
     * radios hear each other, the uploads go to the house, and the resolver mints whatever it
     * concludes. The Revoke lands, or does not, by the rules; the report is a living phone hearing a
     * Revoked one. What is still stood in for is the hop up each phone's socket, because five
     * processes share no air: see [HouseAir]. The in-process tests carry that hop over real sockets.
     *
     * The meeting's four transitions are [tickHouse]'s, so the gate, the talk, the ballot and the
     * tally end when the house's own deadlines say they do and this method's job is to wait.
     */
    private suspend fun theRevoke(house: RoundAuthority) {
        note("=== ACT 3, A REVOKE AND A MEETING ===")
        val insider = house.state.livingInsiders.firstOrNull()
        if (insider == null) {
            note("NO LIVING INSIDER — THE DRAW GAVE THIS ROUND NONE TO REVOKE WITH")
            return
        }
        // The Insider's cooldown starts the round already running at half (D-132), so the house's
        // own clock has to reach the far side of it before an arm is anything but a silent refusal.
        skipTo(house, house.state.cooldownFor(insider, home.someoneshome.model.InsiderAbility.Revoke)
            ?.readyAt?.step ?: 0L)
        // **The arm is the Insider's phone's, never this script's** (D-231). It used to be minted
        // here, which is how a Revoke "landed" in every rehearsal while the tile on a real phone
        // sent nothing. Now the Insider's own bench holds REVOKE on its page 2 (see [pressing]),
        // the request goes up its own socket, and this waits for the house to have armed it.
        if (!awaitRevokeArmed(house, insider)) {
            note("NO PHONE ARMED A REVOKE — THE HOUSE HOLDS NO ARMED SEAT")
            return
        }
        note("THE HOUSE ARMED SEAT ${insider.index}'S REVOKE — FROM ITS OWN PHONE")

        val target = house.state.livingPlainResidents.firstOrNull { it.index != insider.index }
        if (target == null) {
            note("NOBODY TO REVOKE")
            return
        }
        val radios = HouseAir(house).also { it.armed() }
        val act = ContactAct(radios.air, beat = { radios.beat(); delay(STEP_MILLIS) }, note = note)
        note("THE RADIO IS SIMULATED — THE HOUSE UPLOADS FOR THE OTHER PHONES (NO AIR BETWEEN PROCESSES)")
        act.idle(beats = 2)
        act.touch(insider, target)
        delay(SETTLE_MILLIS)
        note(if (house.state.isRevoked(target)) "THE REVOKE LANDED ON SEAT ${target.index}" else "THE TOUCH REVOKED NOBODY")
        beat("REVOKED")

        val reporter = house.state.livingSeats.firstOrNull { it.index != insider.index }
        if (reporter == null) {
            note("NOBODY LEFT TO REPORT IT")
            return
        }
        // A living phone finding the Revoked one: one side hears, because a Revoked phone
        // advertises and never scans (D-008). The resolver mints the report; the rules call it.
        act.touch(reporter, target)
        note(
            if (house.state.meeting != null) "SEAT ${reporter.index} FOUND A REVOKED PLAYER — A MEETING WAS CALLED"
            else "NO MEETING CAME OF THE REPORT"
        )
        theMeeting(house)
    }

    /**
     * **The meeting, watched rather than driven — E-P3-3's other half.**
     *
     * P3 minted four transitions here because nothing drove them. All four are the driver's now, so
     * every line in this method is a wait and a beat: the gate closes when five phones have checked
     * in, the talk ends when the room is unanimous or ninety seconds have passed, the ballot closes
     * on its own window and the tally runs its fifteen. **The bench's only remaining part in a
     * meeting is that it holds five of the phones.**
     */
    private suspend fun theMeeting(house: RoundAuthority) {
        awaitPhase(house, MeetingPhase.CheckIn)
        beat("MEETING-RINGING")
        note("THE GATE — WAITING FOR EVERY PHONE TO CHECK IN")

        // **Every phase announced as the house opens it, rather than four waits in a row.**
        // A wait for a named phase is a wait that never ends when that phase has already closed,
        // and a unanimous room closes the talk in well under one poll interval (D-117). So this
        // watches the phase the house is in and beats on every change — which also means the beat
        // list is the meeting's own shape rather than the shape this script expected.
        var seen: MeetingPhase? = null
        var waited = 0L
        // **And the round can end at the tally** (the 4-seat hunt at seed 20261150, D-259). A round
        // that ends keeps its last meeting, so this loop used to wait out GATE_MILLIS on a finished
        // round, until NEW ROUND and the next arming replaced the state; by then `house.ended` was
        // the second round's, and the script played acts 4 and 5 against it and never said ENDING.
        while (waited < GATE_MILLIS && !house.ended) {
            val phase = house.state.meeting?.phase ?: break
            if (phase != seen) {
                if (seen == MeetingPhase.CheckIn) {
                    val gate = house.state.meeting
                    note("THE GATE STANDS AT — ${gate?.checkedIn?.size ?: 0} OF ${house.state.seats.size}")
                    beat("GATE-CLOSED")
                }
                seen = phase
                beat(phase.name.uppercase())
            }
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        note("RESTRAINED — ${house.state.restrained.size} SEAT(S) OUT BY THE ROOM'S DECISION")
        if (house.ended) {
            note("THE ROUND ENDED AT THE MEETING — ${house.state.outcome}")
            return
        }
        beat("LIGHTS-OUT-AGAIN")
    }

    /**
     * **Act 4 — a Static jam, fired from a phone, and the cooldowns decide it comes first.**
     *
     * Every living thumb went onto both tiles when the meeting ended. D-184 brings every per-seat
     * clock out of a meeting at half, and half of `STATIC_COOLDOWN` is sixty seconds against the
     * Egress's ninety — so pushing the house past the Egress's clock passes the jam's on the way,
     * and the jam is what lands first. **That ordering is the balance table's and not this
     * script's**, which is why the push below is written against the Egress's number.
     */
    private suspend fun theStatic(house: RoundAuthority) {
        note("=== ACT 4, A STATIC JAM ===")
        if (house.ended) return
        if (!house.state.egressRunning) skipTo(house, house.state.egressReadyAt.step)
        if (awaitStatic(house)) {
            note("THE HOUSE HAS GONE BLIND — ${Balance.STATIC_DURATION / Balance.TICKS_PER_SECOND}S")
            beat("STATIC")
        } else {
            note("NO JAM WAS FIRED FROM ANY PHONE")
        }
        delay(SETTLE_MILLIS)
    }

    /**
     * Act 5 — an Egress, fired from a phone, and contained at two nodes.
     *
     * **The host's part in this act is the clock and nothing else.** The finger, the walk to the
     * node and the returned pulse are all frames on five sockets; what this does is push the house
     * past the cooldown that a meeting has just reset to half (D-184) and then watch.
     */
    private suspend fun theEgress(house: RoundAuthority) {
        note("=== ACT 5, AN EGRESS ===")
        if (house.ended) {
            note("THE ROUND IS ALREADY OVER — NO EGRESS")
            return
        }
        // **The clock is never pushed while the house is on fire**, and the first run of this
        // rehearsal is why. Both thumbs go on at the end of the meeting, so the Egress had already
        // been fired during the jam's act — and this line pushed the house a hundred and seventy
        // seconds forward, straight through the countdown two seats were walking across the house to
        // answer. A skew during a crisis is a crisis nobody can contain, which is the one thing this
        // act exists to watch happen.
        if (!house.state.egressRunning) {
            skipTo(house, house.state.egressReadyAt.step)
            note("THE EGRESS CLOCK IS READY — THE INSIDER'S THUMB IS ON THE TILE")
        } else {
            note("THE HOUSE IS ALREADY ON FIRE — A PHONE FIRED ONE WHILE THE JAM WAS LANDING")
        }
        if (!awaitEgress(house)) {
            note("NO EGRESS WAS FIRED FROM ANY PHONE")
            return
        }
        beat("EGRESS-OPEN")
        val nodes = house.state.egress?.nodes.orEmpty()
        note("EGRESS RUNNING — NODES ${nodes.joinToString { it.value }}")

        // The two phones walk and return their pulses on their own. What is waited for here is the
        // house's answer to them, which is what the escalation is about.
        delay(CONTAIN_MILLIS)
        val pair = house.state.livingSeats.filter { house.state.presenceFor(it)?.at != null }
        note("AT A NODE — ${pair.size} SEAT(S) BY THE HOUSE'S OWN PRESENCE PLANE")

        if (house.state.egressRunning) {
            // **No stand-in any more** (D-231). The same two seats' beats used to be minted here off
            // the house's own `firedAt`, so every rehearsal contained its Egress while no phone
            // could: the UI draws no containment control, and nothing on the wire carries the
            // house's clock for a tap to be anchored to. Raised with the owner; until it is ruled,
            // an Egress in a rehearsal is not contained, which is the truth about the phones.
            note("NO PHONE CAN CONTAIN IT — NO CONTAINMENT CONTROL ON ANY SCREEN, NO HOUSE CLOCK ON THE WIRE")
        }
        delay(SETTLE_MILLIS)
        beat("EGRESS-RESOLVED")
        note(
            if (house.state.egressRunning) "THE EGRESS IS STILL RUNNING"
            else "THE EGRESS WAS CONTAINED — THE WIDGET REVERTS EVERYWHERE"
        )

    }

    /**
     * Act 5 — the meter, the ending, and a new lobby.
     *
     * **The answer-key stand-in is gone** (D-231). A phone is shown the question and never the
     * answer, so nothing on a client can produce a correct entry, and the meter used to be moved
     * here by minting scans and hand-overs no phone made. Now the meter moves only as far as the
     * phones' own entries take it, and a bar out of reach ends the other way ([theOtherEnding]).
     */
    private suspend fun theEnding(house: RoundAuthority) {
        note("=== ACT 6, AN ENDING ===")
        if (house.ended) {
            note("THE ROUND ALREADY ENDED — ${house.state.outcome}")
            delay(SETTLE_MILLIS)
            beat("ENDING")
            return
        }
        // **No answer key any more** (D-231). Correct entries were minted here off the house's own
        // answers: a scan and a hand-over no phone made. The phones' own entries still go up the
        // wire through the real screens (see [workTheCaughtScan]) and are graded; what the bench
        // cannot do is know the answers, so the meter moves only as far as they take it.
        note("SYSTEM INTEGRITY — ${house.state.systemIntegrity}")
        delay(SETTLE_MILLIS)

        // **The bar can be out of reach, and when it is, this says so with the arithmetic.**
        //
        // The meter counts plain Residents, and every seat that leaves the round takes its whole
        // work order with it. E-P3-4: two plain Residents left holding sixteen lines against a bar
        // of nineteen, and nothing any of them could do would move it again. That is F-005's orphan
        // half — E-L3-3 — and it is not fixed, so this path is the one a five-seat round takes.
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
        if (house.ended) {
            delay(SETTLE_MILLIS)
            beat("ENDING")
        }
    }

    /**
     * **The round ends the other way: an Egress nobody contains — and no part of it is minted.**
     *
     * D-131's fourth route, and in P3 both halves of it were stand-ins: the finger had no wire
     * member and the countdown had nothing counting. Both are real now. **The Insider's thumb is already
     * on the tile** — [pressing] never stops — so all this has to do is push the house past the
     * Egress's full cooldown and then past the countdown, and let [tickHouse] mint the expiry off
     * its own deadline.
     */
    private suspend fun theOtherEnding(house: RoundAuthority) {
        note("THE THUMBS ARE STILL ON THE TILE — PUSHING THE HOUSE PAST THE FULL COOLDOWN")
        skipTo(house, house.state.egressReadyAt.step)
        if (!awaitEgress(house)) {
            note("NO SECOND EGRESS — THIS ROUND HAS NO ENDING LEFT AT ALL")
            return
        }
        beat("EGRESS-UNCONTAINED")
        val deadline = house.state.egress?.deadline?.step ?: return
        note("NOBODY IS WALKING TO A NODE — PUSHING THE HOUSE PAST THE COUNTDOWN")
        skipTo(house, deadline)
        awaitEnded(house)
    }

    /**
     * **Push the driver's clock forward until the house is past [tick], and let it get there.**
     *
     * [RoundAuthority.at] is not used by this script any more and must not be: the authority's
     * clock belongs to [tickHouse] now, and a script that set it by hand would leave the driver's
     * own timestep behind it — every deadline would then be evaluated against a clock the driver
     * could never reach. What this does instead is tell the driver that more real time has passed
     * than really has, and then **wait for the house to arrive**, which is the difference between
     * moving a counter and moving a clock.
     */
    private suspend fun skipTo(house: RoundAuthority, tick: Long) {
        val ahead = tick - house.clock
        if (ahead <= 0) return
        val seconds = ahead / Balance.TICKS_PER_SECOND + 1
        note("PUSHING THE HOUSE CLOCK FORWARD ${seconds}S — THE DRIVER STILL MINTS EVERYTHING")
        skewNanos += seconds * 1_000_000_000L
        var waited = 0L
        while (house.clock < tick && waited < PATIENCE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        note("THE HOUSE IS AT T+${house.second}S")
    }

    private suspend fun awaitGate(house: RoundAuthority) {
        var waited = 0L
        while (house.state.meeting?.phase == MeetingPhase.CheckIn && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
    }

    private suspend fun awaitMeetingEnd(house: RoundAuthority) {
        var waited = 0L
        while (house.state.meeting != null && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
    }

    /** Until the house holds [insider]'s Revoke armed: the arm came from that seat's own phone. */
    private suspend fun awaitRevokeArmed(house: RoundAuthority, insider: Seat): Boolean {
        var waited = 0L
        while (house.state.cooldownArmed.none { it.index == insider.index } && !house.ended && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        return house.state.cooldownArmed.any { it.index == insider.index }
    }

    private suspend fun awaitEgress(house: RoundAuthority): Boolean {
        var waited = 0L
        while (!house.state.egressRunning && !house.ended && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        return house.state.egressRunning
    }

    private suspend fun awaitStatic(house: RoundAuthority): Boolean {
        var waited = 0L
        while (house.state.staticUntil == null && !house.ended && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        return house.state.staticUntil != null
    }

    /**
     * Wait for the host's own press to have armed the house — the script's new act 1.
     *
     * Bounded like every other wait in this class, and for the same reason: a bench that waits
     * forever on something a person has to do is a phone that reports nothing about why the
     * evening never started.
     */
    private suspend fun awaitArmed(house: RoundAuthority): Boolean {
        var waited = 0L
        while (!house.armed && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        return house.armed
    }

    private suspend fun awaitEnded(house: RoundAuthority) {
        var waited = 0L
        while (!house.ended && waited < GATE_MILLIS) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
    }

    companion object {

        /** Play a round after the lobby is ready. Set on every seat that is taking part. */
        const val ROUND_SWITCH = "SOMEONES_HOME_ROUND"

        /**
         * The arming seed, so a rehearsal can be replayed.
         *
         * ⚠️ **Read by [benchSeed] and by nothing else, and only on a bench launch** (R4, D-240). It
         * is honoured only beside [ROUND_SWITCH] or [BY_HAND_SWITCH]; alone it is never even read.
         * `round.sh` sets it by default and its README says which seat that seed makes the Insider,
         * so a phone that honoured it on its own would deal a published answer key to anyone
         * holding a phone a launcher had once touched. `benchSeedCordon` fails the build on any
         * other read of it in the cheats.
         */
        const val SEED_SWITCH = "SOMEONES_HOME_ROUND_SEED"

        /**
         * **A bench launch whose round the owner plays by hand** (`round.sh start --by-hand`): no
         * script runs on this phone, but it is still the bench's, so [benchSeed] honours the named
         * seed and the Mac's peer can derive every seat's token (D-240). A phone a person launches
         * from the springboard carries neither this nor [ROUND_SWITCH].
         */
        const val BY_HAND_SWITCH = "SOMEONES_HOME_ROUND_BY_HAND"

        /** Which published Egress node this bench walks to. Absent means this seat does not go. */
        const val NODE_SWITCH = "SOMEONES_HOME_ROUND_NODE"

        /** Press STOP NOW on the first work a scan opens, rather than handing an entry over. */
        const val STOP_SWITCH = "SOMEONES_HOME_ROUND_STOP"

        /** The default seed. Written down rather than drawn: a rehearsal that cannot be repeated. */
        const val SEED: Long = 20260822L

        /**
         * **The seed a rehearsal named, or a real draw** — and the default is the draw.
         *
         * It used to fall back to [SEED], which was right while the only thing that ever armed a
         * round was a script somebody launched with switches. Arming is production's now, so the
         * fallback is what an *evening* gets: a phone launched from the springboard with no
         * environment at all would otherwise deal the same Insider seat and the same ten orders
         * every night it was ever played on.
         *
         * Real randomness in the app root is where the seat ledger's own token mint already is,
         * and for the same reason: tokens and seeds never enter a recording as anything but the
         * value that was used, and the determinism rules are about what *does* — the draw from a
         * seed, which is `armingFor`'s and is unchanged.
         *
         * **The draw is [drawRoundSeed], the platform's secure source** (D-215). It was
         * `kotlin.random.Random.nextLong()`, which is a seeded generator with a published
         * algorithm, and every secret in the round is an HMAC under this value (D-214).
         *
         * **A named seed is a bench's only** (R4, D-240): see [benchSeed]. [read] is the launch
         * environment; a test hands a map.
         */
        fun seed(read: (String) -> String? = ::launchValue): Long = benchSeed(read) ?: drawRoundSeed()

        /**
         * **The seed the bench named, or null: the one read of [SEED_SWITCH]** (R4, D-240).
         *
         * Null unless this process is a bench launch, [ROUND_SWITCH] or [BY_HAND_SWITCH]; and on a
         * launch that is neither, [SEED_SWITCH] is **not read at all**, so no value it holds can
         * reach a round. Until D-240 a real LIGHTS OUT read it on its own: `round.sh` sets it by
         * default, and its README publishes which seat that seed makes the Insider.
         *
         * Also what decides whether the house's ability lines may name a seat: only a round whose
         * seed the Mac already holds may (D-237).
         */
        fun benchSeed(read: (String) -> String? = ::launchValue): Long? {
            val bench = read(ROUND_SWITCH) != null || read(BY_HAND_SWITCH) != null
            if (!bench) return null
            return read(SEED_SWITCH)?.toLongOrNull()
        }

        private const val STEP_MILLIS = 400L
        private const val SETTLE_MILLIS = 1_500L
        private const val SHIFT_MILLIS = 12_000L

        /** How often a thumb goes back onto a tile that did nothing. D-141's hold, and a pause. */
        private const val PRESS_MILLIS = 1_000L

        /** How long it takes to walk in. Under the ring's six-second presentation, deliberately. */
        private const val RING_NANOS = 4_000_000_000L

        /** How long the room talks before every hand goes up. A few seconds, not an instant. */
        private const val TALK_NANOS = 8_000_000_000L

        /** How long a ballot stands open before READY. Well inside `VOTE_WINDOW_SECONDS`. */
        private const val BALLOT_NANOS = 12_000_000_000L

        /** How long five phones stand on the reveal before anybody hands the evening back. */
        private const val ENDING_NANOS = 14_000_000_000L

        /** How long a push takes to reach four other processes on one Mac, generously. */
        private const val BEAT_SETTLE_MILLIS = 1_800L

        /** Long enough for two phones to walk to a node and answer four beats. */
        private const val CONTAIN_MILLIS = 12_000L

        /** A bound on every wait for something five other phones have to do. */
        private const val GATE_MILLIS = 180_000L

        /** How long the bench waits for the house clock to reach a pushed tick. */
        private const val PATIENCE_MILLIS = 60_000L

        /**
         * How many taps the bench puts into a Subroutine before handing it over.
         *
         * Comfortably more than the longest entry on the roster, so every one of the ten reaches
         * `SubroutineEntry.gone` — the three sequences hand themselves over on their last element
         * and the rest need something under the control. A number tuned to one Subroutine would
         * make nine of them post nothing, which is the outage this bench failed to see.
         */
        private const val ENTRY_TAPS = 8

    }
}

/**
 * **Wait for the lights to go out, however long the room takes, and only then for the work order.**
 *
 * ⚠️ **The first one-phone device session (2026-11-16) found this clock in the wrong place.** The
 * wait for a work order started when the app launched, because [RoundCheat.play] is launched with
 * the app, and gave up after sixty seconds. A room is not seated in sixty seconds: the launcher
 * starts the two Simulators one after the other once the phone is on the air, and then a person
 * presses LIGHTS OUT, or the lobby bench presses it once three are seated. Both came after the
 * bench had already printed `NO WORK ORDER ARRIVED IN 60S` and stopped, so it never walked its
 * shift, never checked in, and the round it was launched to play went on without it.
 *
 * So the wait has two halves. **Before the lights go out there is no timeout**: a seat waits as
 * long as the room does, and says so every [LIGHTS_OUT_REMINDER_MILLIS] so a silent phone is never
 * a mystery. **After they go out the work order is owed**, and [ORDER_PATIENCE_MILLIS] is how long
 * it may take before the bench says out loud that this phone is not in the round. [lightsOut] is
 * a push that every seat gets on arming; [ordered] is the work order itself, which also ends the
 * first half if it is the first thing to arrive.
 */
internal suspend fun awaitTheWorkOrder(
    lightsOut: () -> Boolean,
    ordered: () -> Boolean,
    note: (String) -> Unit,
    pause: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    var waited = 0L
    while (!lightsOut() && !ordered()) {
        pause(BENCH_STEP_MILLIS)
        waited += BENCH_STEP_MILLIS
        if (waited % LIGHTS_OUT_REMINDER_MILLIS == 0L) {
            note("STILL WAITING FOR THE LIGHTS TO GO OUT — ${waited / 1000}S · NO TIMEOUT BEFORE ARMING")
        }
    }
    waited = 0L
    while (!ordered() && waited < ORDER_PATIENCE_MILLIS) {
        pause(BENCH_STEP_MILLIS)
        waited += BENCH_STEP_MILLIS
    }
    if (!ordered()) {
        note("THE LIGHTS WENT OUT AND NO WORK ORDER ARRIVED IN ${ORDER_PATIENCE_MILLIS / 1000}S — THIS PHONE IS NOT IN THE ROUND")
    }
    return ordered()
}

/** How often the bench looks while it waits. */
internal const val BENCH_STEP_MILLIS = 400L

/** How often a seat still waiting for LIGHTS OUT says so. Never a timeout. */
internal const val LIGHTS_OUT_REMINDER_MILLIS = 60_000L

/** How long, once the lights are out, a seat waits for its work order before saying it has none. */
internal const val ORDER_PATIENCE_MILLIS = 60_000L

/** Wait until the meeting is at [phase], or give up. The bench's only synchronisation. */
/**
 * Wait until the meeting is at [phase] — **or has gone past it, or has ended.**
 *
 * The three exits matter equally. A meeting that reached and left a phase inside one poll interval
 * is the ordinary case for the talk, and a wait that only ever looked for equality would then hold
 * the whole script for its full timeout while the round carried on without it.
 */
private suspend fun awaitPhase(house: RoundAuthority, phase: MeetingPhase) {
    var waited = 0L
    while (waited < 180_000L) {
        val meeting = house.state.meeting ?: break
        if (meeting.phase.ordinal >= phase.ordinal) break
        delay(250)
        waited += 250
    }
}
