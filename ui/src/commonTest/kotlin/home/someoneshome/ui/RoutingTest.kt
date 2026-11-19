package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The round becoming visible — E-P3-1 and E-P3-2.**
 *
 * A client took a hundred pushes across a complete, correct round — arming, a shift, a Revoke, a
 * whole meeting with a real Restrain, an Egress fired and contained, an ending and the reveal — and
 * **stood on the lobby from the first frame to the last.** Nothing below it was broken: the rules
 * ran, the schema redacted, two hundred and forty-two frames reached each phone. `houseSaid` simply
 * moved the screen for almost nothing.
 *
 * This is the owner's routing table as assertions. Each case is *this kind moves this screen or this
 * field, for this class, and no other* — spot-checked per class rather than exhaustively, because
 * what a client may receive is the allowlist's decision and this file is about what a client does
 * with what it got.
 */
class RoutingTest {

    private val roster = listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS")

    private fun living(screen: ScreenId = ScreenId.Home): FlowModel =
        FlowModel(PanelState(screen = screen, roster = roster))

    // ---- 1 · The ring ----------------------------------------------------------------------------

    /**
     * **A meeting is called and the living phone rings** — the push E-P3-1 was really about.
     *
     * Both triggers, because they are the whole of what this branch reads: *same layout, different
     * reason.* A build that routed both to `Call` would pass any test that only fired the card, and
     * the header a player reads at a report is the one thing the two screens differ in.
     */
    @Test
    fun `a meeting call rings this phone and the header is the trigger's`() {
        val card = living()
        card.houseSaid(RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals(ScreenId.Call, card.state.screen, "a meeting was called and this phone did nothing")

        val report = living()
        report.houseSaid(
            RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.RevokeReported(Seat(4)), Haptic.Long),
        )
        assertEquals(
            ScreenId.Found, report.state.screen,
            "a reported Revoke rang as an ordinary meeting — the two screens differ in the reason",
        )
    }

    /**
     * **The caller is named, off the roster the house sent** (D-121).
     *
     * *The meeting has an author, and the room can ask them why.* Both ring screens drew a fixture
     * name on every phone in every round until this landed.
     *
     * **A seat the roster does not hold leaves the name alone rather than picking a nearby one**,
     * which is the fail-closed direction: the port's own name stands in and nobody is accused of
     * calling a meeting they did not.
     */
    @Test
    fun `the ring names the caller the house sent`() {
        val model = living()
        model.houseSaid(RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals("DANI", model.state.caller, "the ring drew a fixture name over the house's")

        val short = FlowModel(PanelState(screen = ScreenId.Home, roster = listOf("PRIYA")))
        short.houseSaid(RoundMessage.MeetingOpened(Seat(4), MeetingTrigger.MeetingCard, Haptic.Long))
        assertNull(short.state.caller, "a seat the roster does not hold was resolved to somebody")
    }

    // ---- 2 · The walk-in, and the gate that could not close (E-P3-2) -----------------------------

    /**
     * **A seat outside the system is told to stand and walk in, and lands where the check-in
     * control is.**
     *
     * D-104's gate counts *every living player and every out player*. An out seat gets
     * `StandAndWalkIn` and no phone call (D-134) — and with no route it never reached a screen
     * offering a check-in, so the gate stopped at 4 of 5 and every transition after it was refused
     * `WrongMeetingPhase`, on five phones, in silence.
     *
     * **The control was already on that screen.** `Ghost2Screen` has drawn the living's own
     * `CheckIn()` since the out screens were ported. What was missing was any way to arrive.
     */
    @Test
    fun `an out seat told to walk in lands on the screen with the check-in on it`() {
        val model = living()
        model.houseSaid(RoundMessage.StandAndWalkIn(Haptic.Long))
        assertEquals(
            ScreenId.Ghost2, model.state.screen,
            "an out seat was told to walk in and its phone stayed where it was — the gate can " +
                "never close (E-P3-2)",
        )
        assertTrue(
            ScreenId.Ghost2 in MeetingModel.STARTS,
            "arriving there does not begin a meeting, so a check-in from the last one is still lit",
        )
    }

    /**
     * **The gate closes at five of five with an out seat among them, and the count is the house's.**
     *
     * The whole of E-P3-2 in one walk: the out seat arrives, the house counts it, and the phone
     * draws five of five rather than four. **Nothing on this phone moved the number** — pressing
     * I AM HERE lights this phone's own tick and leaves the count alone, which is asserted here
     * because it is the thing a helpful build would fix.
     */
    @Test
    fun `the gate closes at five of five with an out seat checking in`() {
        val model = living()
        model.houseSaid(RoundMessage.StandAndWalkIn(Haptic.Long))
        model.houseSaid(RoundMessage.CheckInProgressed(present = 4, expected = 5))
        assertEquals("4 OF 5", model.meeting.counts.ofSeats(model.meeting.counts.present))

        // This phone's own check-in: its tick lights, the house's number does not move.
        model.checkIn()
        assertTrue(model.meeting.checkedIn, "the control did not echo")
        assertEquals(
            4, model.meeting.counts.present,
            "the phone counted itself towards a gate that closes on a number the house holds",
        )

        model.houseSaid(RoundMessage.CheckInProgressed(present = 5, expected = 5))
        assertEquals("5 OF 5", model.meeting.counts.ofSeats(model.meeting.counts.present))

        // And the phase push that follows takes the couch to its own view, which is the row
        // `Flow.housePushes` already held and could never be reached before.
        model.houseSaid(
            RoundMessage.MeetingPhaseOpened(MeetingPhase.Discussion, voteWindowSeconds = null, haptic = Haptic.Short),
        )
        assertEquals(ScreenId.GhostMeeting, model.state.screen, "the couch never got its view")
    }

    // ---- 3 · The counts, and the two denominators ------------------------------------------------

    /**
     * **Presence counts everybody; readiness and the ballot count the living** (D-104, D-147).
     *
     * Three kinds, three numbers, **and two totals** — which is the mistake this test exists to
     * catch. Writing `ReadyProgressed.expected` into the same field the gate's total goes in would
     * draw `3 OF 6 READY` in a house where two people are out and four hands are all the hands
     * there are, which is a screen telling a room it is waiting on somebody sitting on the couch.
     *
     * Each kind moves its own number and leaves the others alone, asserted together because the
     * four are counted at four different moments and a push carrying all of them would make a kind
     * that stopped being emitted go on looking correct.
     */
    @Test
    fun `each count moves its own number against its own total`() {
        val model = living(ScreenId.Assemble)
        model.houseSaid(RoundMessage.CheckInProgressed(present = 6, expected = 6))
        model.houseSaid(RoundMessage.ReadyProgressed(ready = 3, expected = 4))
        model.houseSaid(RoundMessage.VoteProgressed(locked = 2, expected = 4))

        val counts = model.meeting.counts
        assertEquals("6 OF 6", counts.ofSeats(counts.present), "the gate's count is against every seat")
        assertEquals("3 OF 4", counts.ofLiving(counts.ready), "readiness is against the living")
        assertEquals("2 OF 4", counts.ofLiving(counts.voted), "the ballot is against the living")
        assertNotEquals(
            counts.seats, counts.living,
            "the two totals collapsed into one, which is the failure this case is for",
        )
    }

    // ---- 4 · The ballot the house holds ----------------------------------------------------------

    /**
     * **VOTE CAST: the house's lock is taken outright, because it is the half a phone cannot know
     * better** (D-117).
     *
     * The lit row is input echo and is already on the screen; what the house adds is the refusal
     * after READY. A phone that kept its own `false` after the buzzer auto-locked every ballot
     * would go on echoing taps the house refuses.
     */
    @Test
    fun `the house's ballot re-assertion locks this phone`() {
        val model = FlowModel(
            PanelState(screen = ScreenId.Vote, roster = roster),
            meeting = MeetingModel(initialNames = roster),
        )
        model.chooseVote(VoteChoice.Named("MARCUS"))
        assertTrue(!model.meeting.locked, "the fixture started locked")

        model.houseSaid(RoundMessage.VoteHeld(selection = Seat(1), locked = true))
        assertTrue(model.meeting.locked, "the house cast this seat's vote and the phone did not lock")
        // The house names a seat, so the row it lights carries that seat (D-278).
        assertEquals(VoteChoice.Named("MARCUS", 1), model.meeting.choice, "the house's row is not the lit one")

        // A tap after the lock changes nothing, which is what the lock is for.
        model.chooseVote(VoteChoice.Named("ROSE"))
        assertEquals(
            VoteChoice.Named("MARCUS", 1), model.meeting.choice,
            "a locked ballot went on echoing taps the house refuses",
        )
    }

    // ---- 5 · The tally --------------------------------------------------------------------------

    /**
     * **The result reaches every phone as a count; the ballot with names against it reaches the
     * couch** (D-075, and the owner's 2026-08-24 ruling that the living's result is count-only).
     *
     * Two kinds, two screens, two permissions. A living phone holds a name and a participation
     * pair, and **the type it holds them in has no room for a candidate's total** — so the half of
     * this test that used to assert *the rows are empty* is now asserting something the compiler
     * does: what is checked here instead is that the pair is the house's own and not a fixture's.
     *
     * The couch's half **completes the ballot rather than counting it**: the attribution arrives as
     * the finished list, including the seats whose silence was banked as a Skip and which therefore
     * never sent a selection of their own.
     */
    @Test
    fun `the result lands for everybody and the attribution only for the couch`() {
        val everyone = living(ScreenId.Tally)
        everyone.houseSaid(
            RoundMessage.MeetingResult(
                restrained = Seat(1), voted = 4, living = 5, haptic = Haptic.Short,
            ),
        )
        assertEquals("MARCUS", everyone.state.tally?.restrained, "the result never reached the screen")
        assertEquals(
            "4 OF 5 VOTED", PanelVals(everyone.state).votedLine,
            "the result screen is still counting the room off a fixture",
        )

        val couch = living(ScreenId.GhostMeeting)
        couch.houseSaid(
            RoundMessage.MeetingResolved(
                restrained = Seat(1),
                attribution = listOf(
                    RoundMessage.Cast(Seat(0), Seat(1)),
                    RoundMessage.Cast(Seat(2), Seat(1)),
                    RoundMessage.Cast(Seat(3), null),
                    RoundMessage.Cast(Seat(4), Seat(0)),
                ),
            ),
        )
        assertEquals(
            listOf(
                Ballot("PRIYA", "MARCUS"),
                Ballot("DANI", "MARCUS"),
                Ballot("ROSE", "SKIP"),
                Ballot("TOMAS", "PRIYA"),
            ),
            couch.state.ballots,
            "the couch's ballot was not completed off the house's own attribution, in seat order",
        )
    }

    /**
     * **Nobody restrained is an outcome, not an absence** (`gdd.md:413`, D-075).
     *
     * A Skip, a tie, or a vote nobody won all resolve to no name, and the screen has a sentence for
     * it. A null *field* here would put the fixture's MARCUS back on the screen at the exact moment
     * the room restrained nobody, which is the worst single frame this screen could draw.
     */
    @Test
    fun `a room that restrained nobody does not fall back to the fixture`() {
        val model = living(ScreenId.Tally)
        model.houseSaid(
            RoundMessage.MeetingResult(
                restrained = null, voted = 5, living = 5, haptic = Haptic.Short,
            ),
        )
        assertNull(model.state.tally?.restrained, "nobody was restrained and the screen named somebody")
        assertEquals(
            "NOBODY WAS RESTRAINED", PanelVals(model.state).restrainedLine,
            "the screen fell back to the drawn fixture on the outcome D-075 argues hardest for",
        )
    }

    // ---- 6 · The notifications -------------------------------------------------------------------

    /**
     * **The opening text dims the house; a later one does not** (D-118, D-119).
     *
     * Exactly two arrivals are heavy and the design named both. A build that routed
     * `MessageDelivered` to the heavy banner would spend the opening dim's meaning on every text
     * the house sends for the rest of the round.
     */
    @Test
    fun `the opening message is heavy and a later text is quiet`() {
        val opening = living()
        opening.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
        assertEquals(ScreenId.Notify, opening.state.screen, "the round started and nothing arrived")

        val later = living()
        later.houseSaid(RoundMessage.MessageDelivered("THE SOUTH DOOR IS UNLATCHED"))
        assertEquals(ScreenId.Quiet, later.state.screen, "a later text dimmed the house")
        assertEquals(
            listOf("THE SOUTH DOOR IS UNLATCHED"),
            later.notifications.standing.take(1).map { it.body },
            "a stored text did not reach the lock screen, newest first",
        )
    }

    /**
     * **A notification under the clock rather than over a springboard nobody is looking at**
     * (D-119), and **none at all over a screen the house is driving.**
     *
     * The second half is the fail-closed direction and the one worth the assertion: a banner raised
     * over the tally would take the room's result off the screen it is being read from, and one
     * over an ending would put a text message over the last thing the round has to say.
     */
    @Test
    fun `an arrival lands under the clock and never over a screen the house is driving`() {
        val locked = living(ScreenId.Lock)
        locked.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
        assertEquals(ScreenId.LockNotify, locked.state.screen, "it arrived over a phone in a pocket")

        for (driven in listOf(ScreenId.Tally, ScreenId.Vote, ScreenId.WinResidents, ScreenId.Revoked)) {
            val model = living(driven)
            model.houseSaid(RoundMessage.MessageDelivered("ANOTHER LINE"))
            assertEquals(
                driven, model.state.screen,
                "a text interrupted $driven, which is the house driving",
            )
            assertTrue(
                model.notifications.standing.any { it.body == "ANOTHER LINE" },
                "it did not interrupt, and it was lost instead of standing under the clock",
            )
        }
    }

    // ---- 7 · The house's last word ---------------------------------------------------------------

    /**
     * **The sign-off changes what an ending says and moves nothing** (E-P3-6).
     *
     * An ending is two pushes and anything that acts on an arrival rather than on the round being
     * over is wrong. This is a third push that lands on a phone already standing on an ending.
     */
    @Test
    fun `the house's last word lands on the ending without moving it`() {
        val model = living(ScreenId.WinResidents)
        model.houseSaid(RoundMessage.HouseSignedOff("THE PERIMETER IS DISARMED. GOODNIGHT."))
        assertEquals("THE PERIMETER IS DISARMED. GOODNIGHT.", model.state.signOff)
        assertEquals(ScreenId.WinResidents, model.state.screen, "the last word moved the phone")
    }

    /**
     * **STATIC raises the jam on every living phone, and a meeting call throws it away**
     * (D-186, D-185).
     *
     * Two branches, and the second is the one worth being careful about. `StaticOpened` sets the
     * field on **whichever** phone receives it — there is no seat on the message, so this branch
     * cannot tell the Insider who fired it from a Resident who did not, and that is the ability.
     *
     * **The meeting call drops it in the same assignment that names the caller**, which is not this
     * phone deciding a rule: the house cleared its own deadline at the call and no message reports
     * that, because there is no surface left showing noise for a message to clear — page 2 does not
     * exist during a meeting (D-183) and the Terminal is never readable at one. So the phone throws
     * its copy away in the frame it is told why.
     */
    @Test
    fun `a static jam is raised on any living phone and dropped at a meeting call`() {
        val model = living(ScreenId.TermLive)
        model.houseSaid(RoundMessage.StaticOpened(seconds = 10, seed = 4242L))
        assertEquals(
            StaticJam(seconds = 10, seed = 4242L), model.state.staticJam,
            "the house said the monitoring was jammed and the phone kept nothing",
        )
        assertEquals(
            ScreenId.TermLive, model.state.screen,
            "a jam moved the phone; it is a render over the surface, not a screen",
        )

        model.houseSaid(
            RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long),
        )
        assertNull(
            model.state.staticJam,
            "the jam outlived the meeting call that ended it (D-185)",
        )
    }

    // ---- 8 · What must still move nothing --------------------------------------------------------

    /**
     * **The kinds that stay routeless stay routeless, and each is a decision.**
     *
     * `PresenceChanged` is permitted to nobody (D-111). `EgressSucceeded` has no row at all
     * (D-131, D-156). `AbilityFired` is the only thing a Tier-2 fire sends back to the actor and
     * must not grow a screen — which is the rule the deleted fourth member of this list used to
     * carry: D-186 replaced the ability whose landing was forbidden feedback, and what took its
     * place is a kind that moves a panel field on **every** living phone rather than none.
     *
     * Asserted as *the panel did not move at all* rather than as *the screen did not change*,
     * because the failure worth catching is a field quietly appearing.
     *
     * **`LampSet` is still on this list and now does something**, which is the point of leaving it
     * here: it writes the lamp and it must never write [PanelState]. A level arriving has to cost a
     * draw invalidation and nothing above it (rule 7), so a field appearing here would be the
     * regression this row exists to catch. What it *does* do is asserted in `LampRoutingTest`.
     */
    @Test
    fun `the routeless kinds move neither screen nor panel`() {
        val quiet = listOf(
            RoundMessage.PresenceChanged(at = null, open = false),
            RoundMessage.EgressSucceeded(Haptic.Long),
            RoundMessage.AbilityFired(cooldownStarted = true),
            RoundMessage.LampSet(luminance = 0),
        )
        for (message in quiet) {
            val model = living(ScreenId.Page2)
            val before = model.state
            model.houseSaid(message)
            assertEquals(
                before, model.state,
                "${message::class.simpleName} moved a phone, and it is routeless on purpose",
            )
        }
    }

    // ---- 8b · What this phone is (E-R2-2) --------------------------------------------------------

    /**
     * **THE PUSH THAT TELLS A PHONE WHAT IT IS, and the whole of the Insider's game behind it**
     * (`gdd.md:923`, E-R2-2).
     *
     * Read off five ledgers of a five-seat rehearsal, every line of both rounds: `ROLE RESIDENT`,
     * including on the seat holding the round's only Insider. `PanelState.role` was written by the
     * cheat picker and by nothing else in the app, so every phone in a real round drew the
     * Resident's page 2 — and since the Isolate picker is reachable only from the Insider's, the one
     * Insider-only list the house really did send arrived on a phone with no way to open it. **In a
     * real evening no Insider could have fired anything at all.**
     *
     * ### Three claims, and the second is the one that closes the escalation
     *
     * The field follows the wire; the **capability** follows the field, so page 2 answers a thumb
     * on the phone the house told and on no other (D-142); and the labels follow it too, which is
     * the parity discipline reading its role from the house rather than from a fixture.
     *
     * ### It moves no screen, deliberately
     *
     * The reveal is text on a thread the player opens, and the opening banner is what says *now*.
     * A navigation here would be one phone in a lit room doing something the Resident's phone does
     * not, at the moment the whole party is still standing together — the tell the parity
     * discipline exists to prevent.
     */
    @Test
    fun `the house tells this phone what it is and page 2 answers only that phone's thumb`() {
        val insider = living(ScreenId.Page2)
        val resident = living(ScreenId.Page2)

        insider.houseSaid(RoundMessage.RoleRevealed(insider = true))
        resident.houseSaid(RoundMessage.RoleRevealed(insider = false))

        assertEquals(PanelRole.Insider, insider.state.role, "the house said Insider and the phone did not")
        assertEquals(PanelRole.Resident, resident.state.role, "a Resident's phone took an Insider's role")

        // **Capability, which is what the escalation was about.** `armable` on every page-2 tile is
        // this flag, so a phone whose role never arrived is a phone whose tiles take no input at all
        // (D-142) — which is exactly what an Insider's phone was doing all round.
        assertTrue(PanelVals(insider.state).insider, "the Insider's page 2 would take no input")
        assertFalse(PanelVals(resident.state).insider, "a Resident's page 2 became armable")

        // And the labels, from the same field: identical furniture, different words (D-142).
        assertEquals("REVOKE", PanelVals(insider.state).abilityName)
        assertEquals("POWER", PanelVals(resident.state).abilityName)

        // The screen does not move, on either phone.
        assertEquals(ScreenId.Page2, insider.state.screen, "the reveal moved a phone")
        assertEquals(ScreenId.Page2, resident.state.screen, "the reveal moved a phone")
    }

    /**
     * **The role is the house's and the phone never re-derives it.**
     *
     * The Isolate picker's list is the one message only an Insider may receive, so a client could
     * "helpfully" infer its role from the list arriving — and that inference is the leak with the
     * shortest fuse in the app: it makes a *delivery* into an *alignment*, on a phone, in a way no
     * schema row could see. `ui` cannot ask `core` anything; this asserts it does not ask itself
     * either. The list lands on the panel and the role stays where the house left it.
     */
    @Test
    fun `the picker's list arriving does not make a phone an Insider`() {
        val model = living(ScreenId.Page2)
        model.houseSaid(
            RoundMessage.IsolateTargetsOffered(
                listOf(Destination(MarkerId("m1"), shape = "ring", room = "KITCHEN")),
            ),
        )
        assertEquals(
            PanelRole.Resident, model.state.role,
            "a phone worked out its own role from a message it received, which is the rules " +
                "living on the device in their most plausible costume",
        )
        assertEquals(1, model.state.isolateTargets?.size, "the list did not land, so this proves nothing")
    }

    // ---- 9 · The caller, and the five phones that are not (E-R1-1) -------------------------------

    /**
     * **The one phone that called the meeting ends up somewhere different from the other five.**
     *
     * E-R1-1 exactly: the ring reaches everybody, `caller` is a `Seat`, and no phone knows which
     * seat it is — so until the house started sending a second, per-seat message, the person who
     * had walked to the card stood on the same CALL screen as the people it woke up.
     *
     * **Both phones take the ring first**, because that is the order the rules emit in and the ring
     * is what carries the caller's name. The caller is then moved off it; nothing else about the
     * meeting differs, which is the other half of the claim.
     */
    @Test
    fun `the caller lands on its own screen and everybody else stays on the ring`() {
        val ring = RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long)

        val caller = living()
        caller.houseSaid(ring)
        caller.houseSaid(RoundMessage.MeetingCalling)

        val bystander = living()
        bystander.houseSaid(ring)

        assertEquals(
            ScreenId.Calling, caller.state.screen,
            "the phone that called the meeting is standing on the ring with everybody else",
        )
        assertEquals(
            ScreenId.Call, bystander.state.screen,
            "a phone that called nothing was moved to the caller's own screen",
        )
        assertEquals(
            caller.state.caller, bystander.state.caller,
            "the two phones disagree about who called it — the name is the ring's and reaches all",
        )
    }

    /**
     * **The difference is the message and never the content**, which is what makes it safe.
     *
     * There is nothing on `MeetingCalling` to read, so two phones handed the identical object have
     * to end up on the identical screen. The only thing that separates the caller from anybody else
     * is that the house chose to send it down one socket — see `EmitSchema.deliveries`, and
     * `RoundWireTest` for the assertion that the seat cannot ride inside it.
     */
    @Test
    fun `the caller's screen is reached by the message arriving and by nothing on it`() {
        for (from in listOf(ScreenId.Home, ScreenId.Work, ScreenId.Lock)) {
            val model = living(from)
            model.houseSaid(RoundMessage.MeetingCalling)
            assertEquals(
                ScreenId.Calling, model.state.screen,
                "the caller's own message did not reach the caller's own screen from $from",
            )
        }
    }

    // ---- 10 · The routing tail (E-R1-4) ----------------------------------------------------------

    /**
     * **The ledger's raw count becomes a percentage or it becomes nothing** (D-103, D-153).
     *
     * The house sends `remaining` and the couch's meter is a percentage — the count itself may not
     * reach a screen, because `seats × METER_PER_SEAT` is public arithmetic and a meter drawn as a
     * fraction hands back the Insider count by division.
     *
     * **And the denominator has to be the house's.** A phone that has not been told how many seats
     * the round has leaves the bar exactly where it was rather than dividing by a guess: a meter
     * moving against an invented total is a wrong number that looks precisely like a right one, on
     * the one screen a player who is out watches for a whole round.
     */
    @Test
    fun `the ledger's count reaches the meter as a percentage and never without a denominator`() {
        val untold = living(ScreenId.Ghost3)
        untold.houseSaid(RoundMessage.SubroutineProgressed(remaining = 21))
        assertNull(
            untold.state.integrityPercent,
            "the meter moved on a phone that has never been told how many seats the round has",
        )

        val told = living(ScreenId.Ghost3)
        told.houseSaid(RoundMessage.CheckInProgressed(present = 1, expected = 6))
        told.houseSaid(RoundMessage.SubroutineProgressed(remaining = 21))
        assertEquals(
            Balance.integrityPercent(21, 6), told.state.integrityPercent,
            "the count was not converted through the one place that arithmetic lives",
        )
        assertEquals(
            6, told.state.seats,
            "the seat total the gate carried did not reach the field the conversion reads",
        )
    }

    /**
     * **The scan's answer opens the caught scan and nothing further** (D-124, D-188).
     *
     * One flow: scan → caught, with the briefing on it → BEGIN → the work. The push deliberately
     * stops at the card, because BEGIN is the last moment NOT THIS ONE is still a choice — and a
     * scan can catch the wrong card in the dark.
     *
     * **A null answer is the refusal and has its own screen.** A registered card the house has
     * nothing for *you* at is this message with nothing in it (D-124); unregistered paper never
     * reaches the rules at all.
     */
    @Test
    fun `a caught scan opens the card and a null answer opens the refusal`() {
        val caught = living(ScreenId.Scan)
        caught.houseSaid(RoundMessage.WorkOrderIssued(order))
        caught.houseSaid(
            RoundMessage.ScanAnswered(
                SubroutineInstance(entry = 1, subroutine = SubroutineKind.Sniff, parameters = listOf(3, 5, 8)),
            ),
        )
        assertEquals(ScreenId.ScanCaught, caught.state.screen, "a caught scan opened nothing")
        assertEquals(
            Subroutine.Sniff, caught.state.current?.subroutine,
            "the caught scan named a different Subroutine than the card the house answered for",
        )
        assertEquals("STUDY", caught.state.current?.room, "the room came off neither the order nor the house")
        assertEquals(2, caught.state.current?.index, "the position in the order is not the entry the house named")
        assertEquals(order.size, caught.state.current?.total, "the order's length changed on the way to the card")

        val refused = living(ScreenId.Scan)
        refused.houseSaid(RoundMessage.ScanAnswered(opened = null))
        assertEquals(
            ScreenId.ScanBad, refused.state.screen,
            "a card with nothing on it for this seat opened work anyway",
        )
    }

    /**
     * **An answer the order cannot place moves nothing**, which is the fail-closed direction.
     *
     * A phone that stayed on the scan is noticed in seconds. A caught scan drawn against an entry
     * this seat's own order does not hold would put a player through work with no room, no glyph and
     * no position in their own list — and a blocked line is deliberately unplaceable, because
     * `OrderLine.Blocked` carries an index and nothing else (D-114).
     */
    @Test
    fun `a scan answering for an entry the order does not hold opens nothing`() {
        for (entry in listOf(9, 2)) {
            val model = living(ScreenId.Scan)
            model.houseSaid(RoundMessage.WorkOrderIssued(order))
            model.houseSaid(
                RoundMessage.ScanAnswered(
                    SubroutineInstance(entry = entry, subroutine = SubroutineKind.Jam, parameters = listOf(-9)),
                ),
            )
            assertEquals(
                ScreenId.Scan, model.state.screen,
                "entry $entry is not a placeable line of this order and the phone opened a card anyway",
            )
            assertNull(model.state.current, "a row was built for work the order cannot place")
        }
    }

    /**
     * **The Egress clock stopping, and this phone's own beat** (D-133, `gdd.md:355`).
     *
     * Two facts on the containment screen and two fields, because they answer different questions:
     * *is the countdown moving* is the house's, and *did my tap land* is this seat's. `running` is
     * copied and inverted into a held flag; `held` is copied and never interpreted — false is the
     * answer for a missed beat, a lockout, a seat that is not at a node and a seat outside the
     * system alike, and a screen that split it finer would report where the house thinks you are.
     */
    @Test
    fun `the containment screen learns that the clock stopped and what this phone's beat did`() {
        val model = living(ScreenId.EgressWidget)
        assertEquals(false, model.state.egressHeld, "a countdown nobody stopped opened held")
        assertNull(model.state.beatHeld, "a phone that has tapped nothing was graded anyway")

        model.houseSaid(RoundMessage.EgressHeld(remaining = 400, running = false, haptic = Haptic.Short))
        assertTrue(model.state.egressHeld, "the house stopped the clock and the widget kept counting")

        model.houseSaid(RoundMessage.EgressHeld(remaining = 400, running = true, haptic = Haptic.Short))
        assertEquals(false, model.state.egressHeld, "the clock started again and the widget stayed held")

        model.houseSaid(RoundMessage.SyncPulseAnswered(held = true))
        assertEquals(true, model.state.beatHeld, "the house held this phone's beat and the screen did not say so")

        model.houseSaid(RoundMessage.SyncPulseAnswered(held = false))
        assertEquals(false, model.state.beatHeld, "an unheld beat was read as no answer at all")
    }

    // ---- 8 · The unit E1 routes ------------------------------------------------------------------

    /**
     * **THE HOUSE CATCHES FIRE AND EVERY PHONE FINDS OUT — E-R2-3** (`gdd.md:370-378`, D-076,
     * D-118).
     *
     * The effect reached every seat and moved nothing on any of them. Three separate claims here
     * and the middle one is the escalation's actual subject:
     *
     * 1. **A dismissable alert, not a navigation.** `Banner` is the springboard with the heavy
     *    notification over it, so the player keeps whatever they were doing underneath — *you
     *    cannot scan a marker through a modal.*
     * 2. **The two nodes are named as ROOMS**, which is what the widget takeover is for. This is
     *    what a nine-character card id could never do and is why the wire carries destinations.
     * 3. **The type stands and `egressRunning` comes up**, because the widget draws both.
     */
    @Test
    fun `the Egress alert reaches this phone and names two rooms`() {
        val model = living(ScreenId.Work)
        model.houseSaid(
            RoundMessage.EgressOpened(
                type = EgressType.Tether,
                nodes = listOf(
                    Destination(MarkerId("west-card"), "circle", "UTILITY"),
                    Destination(MarkerId("east-card"), "square", "LANDING"),
                ),
                remaining = 2_400,
                haptic = Haptic.Long,
                pulseFrom = 86_402_000L,
            ),
        )
        assertEquals(
            ScreenId.Banner, model.state.screen,
            "the house caught fire and this phone went on drawing the work screen",
        )
        assertEquals(
            listOf("UTILITY", "LANDING"), model.state.egressNodes,
            "the alert reached the phone and named nowhere to go",
        )
        assertEquals("TETHER", model.state.egressType)
        assertTrue(model.state.egressRunning, "the widget has no crisis to draw")
    }

    /**
     * **A locked phone gets it under the clock and a phone on a house screen keeps that screen.**
     *
     * The alert goes through `arrived`, so it inherits D-119's two cases rather than restating
     * them — which is the whole reason it is not a `push`. A banner raised over the tally would
     * take the room's result off the screen it is being read off.
     */
    @Test
    fun `the Egress alert respects the lock screen and never interrupts a house screen`() {
        val locked = living(ScreenId.Lock)
        locked.houseSaid(egressOpened())
        assertEquals(ScreenId.LockNotify, locked.state.screen, "a phone nobody picked up lost its clock")

        val tally = living(ScreenId.Tally)
        tally.houseSaid(egressOpened())
        assertEquals(ScreenId.Tally, tally.state.screen, "an alert took the room's result off the screen")
        // The pair still lands, because the widget behind it has to be right whenever it is reached.
        assertEquals(listOf("UTILITY", "LANDING"), tally.state.egressNodes)
    }

    /**
     * **THE ROSTER, AND IT IS THE FIELD FOUR SCREENS READ AND NOBODY WROTE** (D-115, E-R2-6, C3·4).
     *
     * `PanelState.roster` had no writer anywhere in the app, so `nameOf` returned null on every
     * lookup in every real round. This asserts all three consumers off one push, because they were
     * never three gaps — they were one missing carrier read three ways. **The seat total is the
     * fourth**: it is the roster's length, it is a public lobby fact, and until now it arrived only
     * at the first meeting (C3·4).
     */
    @Test
    fun `the roster arrives once and every surface that names anybody can`() {
        // **A list the fixtures cannot supply**, and that is the point rather than flavour: the
        // default `MeetingModel.sample()` carries the port's own five, so a roster equal to them
        // makes *the house wrote the ballot* and *nobody wrote the ballot* the same observation.
        // An injection that dropped `houseNamed` passed against the port's names.
        val tonight = listOf("ADA", "BRUNO", "", "DELPHINE", "EMEKA", "FINN")
        val model = FlowModel(PanelState(screen = ScreenId.Home))
        assertNull(model.state.roster, "the fixture started with a roster the house had not sent")
        assertNotEquals(tonight, model.meeting.names, "the fixture already held tonight's ballot")

        model.houseSaid(RoundMessage.RosterIssued(tonight))
        assertEquals(tonight, model.state.roster)
        assertEquals(tonight, model.meeting.names, "the ballot is drawn from a different list")
        assertEquals(
            6, model.state.seats,
            "the seat total still waits for the first meeting, so an out phone's meter cannot move",
        )

        // And the lookup it exists for: the ring's caller resolves to a name rather than to null.
        model.houseSaid(RoundMessage.MeetingOpened(Seat(3), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals("DELPHINE", model.state.caller, "the roster arrived and the ring still named nobody")

        // **The blank seat stays a slot.** A phone that never said what to call its owner holds a
        // position, or every seat after it resolves to somebody else — which the Surge picker fires
        // at and the ballot restrains.
        model.houseSaid(RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals("", model.state.caller, "an unnamed seat was skipped and the list shifted")
    }

    /**
     * **THE REVOKE LANDING — E-R2-5.** The two ways out of the round were asymmetric on the client:
     * the room holding you pushed a screen, and the house withdrawing your lamp pushed nothing.
     *
     * **Arriving is what sets `outBy`**, which is where the Revoke/Restrain distinction is kept
     * (rule 9) — so this asserts the cause and not only the screen. A build that routed this to
     * `Restrained` would put the room's word on a screen the room had nothing to do with.
     */
    @Test
    fun `the Revoke landing reaches the Revoked screen with the cause on it`() {
        val model = living(ScreenId.Page2)
        model.houseSaid(RoundMessage.RevokeLanded.Buzzing)
        assertEquals(ScreenId.Revoked, model.state.screen, "the house took this player out and said nothing")
        assertEquals(OutBy.Revoked, model.state.outBy, "the screen blamed the room for the house's act")

        val held = living(ScreenId.Page2)
        held.houseSaid(RoundMessage.RestrainedTakeover(Haptic.Long))
        assertEquals(OutBy.Restrained, held.state.outBy, "the two ways out stopped being distinguishable")
    }

    /**
     * **THE COUCH'S LIVE BALLOT — E-R2-6** (D-117, D-134, D-075). *Ghosts are the only readers who
     * see selections rather than a count*, and the branch was `Unit`.
     *
     * Four claims: the names resolve off the house's roster, a Skip says SKIP rather than STILL
     * DECIDING (D-075 — *not voting counts as a Skip*, so the two must stay distinguishable), a
     * seat that changes its mind moves its own row instead of growing a second, and the rows come
     * out in **seat order** however they arrived (rule 4 — two people on one couch must be reading
     * the same list).
     */
    @Test
    fun `the couch watches the ballot fill in and the rows come out in seat order`() {
        val couch = FlowModel(PanelState(screen = ScreenId.GhostMeeting))
        couch.houseSaid(RoundMessage.RosterIssued(roster))

        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(3), selection = Seat(1)))
        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(0), selection = null))
        assertEquals(
            listOf(Ballot("PRIYA", "SKIP"), Ballot("ROSE", "MARCUS")),
            couch.state.ballots,
            "the rows are in arrival order, so two phones on one couch disagree",
        )

        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(3), selection = Seat(4)))
        assertEquals(
            listOf(Ballot("PRIYA", "SKIP"), Ballot("ROSE", "TOMAS")),
            couch.state.ballots,
            "a voter who changed their mind was counted twice",
        )
    }

    /** A new meeting starts with nothing said, the couch's ballot included. */
    @Test
    fun `the couch's ballot does not survive into the next meeting`() {
        val couch = FlowModel(PanelState(screen = ScreenId.GhostMeeting))
        couch.houseSaid(RoundMessage.RosterIssued(roster))
        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(3), selection = Seat(1)))
        assertEquals(1, couch.state.ballots?.size)

        couch.houseSaid(RoundMessage.StandAndWalkIn(Haptic.Short))
        assertEquals(ScreenId.Ghost2, couch.state.screen, "the walk-in never arrived")
        assertNull(
            couch.state.ballots,
            "the couch opened the next meeting looking at the last one's completed vote",
        )
    }

    private fun egressOpened(): RoundMessage.EgressOpened = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(MarkerId("west-card"), "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    /** This seat's own order: six lines, one of them blocked, so an unplaceable entry really is one. */
    private val order: List<OrderLine> = listOf(
        OrderLine.Known(
            index = 0,
            work = AssignedWork.Subroutine(SubroutineKind.Replay),
            done = true,
            at = listOf(Destination(MarkerId("card-hall"), shape = "diamond", room = "HALL")),
        ),
        OrderLine.Known(
            index = 1,
            work = AssignedWork.Subroutine(SubroutineKind.Sniff),
            done = false,
            at = listOf(Destination(MarkerId("card-study"), shape = "square_frame", room = "STUDY")),
        ),
        OrderLine.Blocked(index = 2),
        OrderLine.Known(
            index = 3,
            work = AssignedWork.Subroutine(SubroutineKind.Jam),
            done = false,
            at = emptyList(),
        ),
    )
}
