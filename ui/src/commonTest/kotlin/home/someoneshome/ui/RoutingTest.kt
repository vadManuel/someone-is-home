package home.someoneshome.ui

import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
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
            meeting = MeetingModel(names = roster),
        )
        model.chooseVote(VoteChoice.Named("MARCUS"))
        assertTrue(!model.meeting.locked, "the fixture started locked")

        model.houseSaid(RoundMessage.VoteHeld(selection = Seat(1), locked = true))
        assertTrue(model.meeting.locked, "the house cast this seat's vote and the phone did not lock")
        assertEquals(VoteChoice.Named("MARCUS"), model.meeting.choice, "the house's row is not the lit one")

        // A tap after the lock changes nothing, which is what the lock is for.
        model.chooseVote(VoteChoice.Named("ROSE"))
        assertEquals(
            VoteChoice.Named("MARCUS"), model.meeting.choice,
            "a locked ballot went on echoing taps the house refuses",
        )
    }

    // ---- 5 · The tally --------------------------------------------------------------------------

    /**
     * **The result reaches every phone; the ballot with names against it reaches the couch.**
     *
     * Two kinds, one screen, two permissions (D-075). What a living phone holds is a name and no
     * rows; the couch holds both — and the rows are counted here rather than carried, because a
     * tally is what you get by counting an attribution list.
     */
    @Test
    fun `the result lands for everybody and the attribution only for the couch`() {
        val everyone = living(ScreenId.Tally)
        everyone.houseSaid(RoundMessage.MeetingResult(restrained = Seat(1), haptic = Haptic.Short))
        assertEquals("MARCUS", everyone.state.tally?.restrained, "the result never reached the screen")
        assertEquals(
            emptyList(), everyone.state.tally?.rows,
            "a living phone was handed the attribution list",
        )

        val couch = living(ScreenId.Ghost3)
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
            listOf(TallyCount("MARCUS", 2), TallyCount("SKIP", 1), TallyCount("PRIYA", 1)),
            couch.state.tally?.rows,
            "the couch's rows were not counted in the order the house read the ballot",
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
        model.houseSaid(RoundMessage.MeetingResult(restrained = null, haptic = Haptic.Short))
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
}
