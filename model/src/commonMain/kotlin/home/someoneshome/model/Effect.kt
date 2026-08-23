package home.someoneshome.model

/**
 * What the rules emit. Authority-side, and deliberately **not** `@Serializable`.
 *
 * Nothing here goes on the wire as-is. Effects are redacted per client at the emit boundary,
 * by constructing a narrower client-facing type — never by nulling fields on this one. A nulled
 * field still exists, and someone makes it non-null later for an unrelated reason.
 *
 * The per-client transcript recorder and the schema allowlist that police that boundary are
 * stories 0.5 and 0.6b. Until they exist, this type simply must not acquire a wire encoding,
 * which `redactionLint` enforces: adding `@Serializable` here fails the build.
 */
sealed interface Effect {
    /**
     * Light is game state. No local animation, tween or easing the rules did not emit — a fade
     * nobody authored is a signal nobody authored.
     */
    data class LampSet(val seat: Seat, val luminance: Int) : Effect

    /**
     * Emitted whether or not the ability changed anything.
     *
     * This is the shape rule 1 demands: firing at an already-revoked target must produce the
     * same effect, with the same cooldown, as firing at a live one. An absent effect is a
     * revoke-detector.
     */
    data class AbilityFired(val actor: Seat, val cooldownStarted: Boolean) : Effect

    /**
     * **The house's answer to a returned entry — the same words on the same schedule for both
     * roles (D-109).**
     *
     * Two fields, and that is deliberately the whole of it. There is no `remaining`, no *which
     * elements were wrong*, no attempt count and no reason: a mismatch reports only that it was a
     * mismatch (`gdd.md:608`), and every extra field would be one more thing that could be
     * generated differently for a fake.
     *
     * ### This is rule 1's exact shape, and it is the reason the effect exists
     *
     * An Insider's Subroutines are fakes: real UI, real progress, real completion, **writing
     * nothing**. The tempting implementation is to say nothing back about a fake, or to roll a
     * plausible failure distribution for one. Both are role oracles — the first after one
     * completion, the second after enough of them — and D-109 refuses both: *run the real rule,
     * emit the real shape.* **The Insider's verdict is emitted, never omitted.**
     *
     * The single asymmetry lives where no player can stand: an Insider's [accepted] verdict does
     * not move `SystemIntegrity`. Nothing here carries that, and nothing on any screen can read it.
     */
    data class SubroutineGraded(val seat: Seat, val accepted: Boolean) : Effect

    /**
     * **The Surge, landing on the seat it was fired at — one event, and it is loud**
     * (`gdd.md:313`, `:342`).
     *
     * *Target's lamp slams to full, then to black — **and** their phone emits sound. One event.*
     * That sentence is this type: one effect carrying both halves, because the design says **one
     * event** and two effects would be two things a build could deliver separately. Either half
     * alone was a nudge; together it is the loudest, brightest thing that can happen in a silent
     * dark house.
     *
     * ### The lamp values are on the effect, and rule 5 is why
     *
     * *Light is game state. No local animation, tween or easing the rules did not emit — a fade
     * nobody authored is a signal nobody authored.* So the slam is not a device effect triggered by
     * a bare `Surged` token: [peak] and [black] are the two luminances the **rules** chose, and the
     * device performs those and nothing else. **What it returns to afterwards is the last
     * [LampSet] the house sent**, which the phone is already holding — so the gesture ends where
     * state says it should rather than at a value this effect invented.
     *
     * ⚠️ **How long the two halves last is unruled and is the platform's.** Nothing in the design
     * gives the slam a duration, and `core` has no clock to give it one. Flagged rather than
     * invented; see the worklog.
     *
     * ### It is addressed to the TARGET and it is not feedback
     *
     * *No actor-side feedback on firing any ability. The attacker's device stays inert. All
     * observable effect happens on the target* (`gdd.md:398`). What the actor's phone gets is
     * [AbilityFired] and nothing else — and when the actor **is** the target, this arrives on their
     * phone as the target's effect rather than as a report about their own finger. Self-surge is the
     * premium alibi play (`gdd.md:342`) and it has to look exactly like being surged, because that
     * is what it is.
     *
     * ### An already-Revoked target receives nothing, and no branch here decides that
     *
     * The rules construct this identically whichever seat it names (rule 1); the allowlist permits
     * the kind to the two **living** classes and to no out class, so a Revoked target's phone is
     * declined at the boundary. Two independent denials and neither is visible as an absence — the
     * actor's device is told the ability fired and the cooldown is spent either way, which is
     * `gdd.md:344`'s *targeting an already-revoked player must appear to succeed on the actor's
     * device* kept without the rules ever asking whether it landed.
     *
     * **The room is a different question and the design has already answered it.** *Surge is
     * world-observable* — surge someone you can see, watch nothing happen, and you have learned they
     * are revoked with no panel involved (`gdd.md:346`). Accepted, because a Insider usually does not
     * know where their target is.
     *
     * ### [sound] is the one real sound in the game and [haptic] is Short
     *
     * See [Sound], which carries the ruling and the licence. The haptic is a ruling rather than a
     * default, exactly as [OpeningMessage]'s is: D-135 closes the long buzz to five events and this
     * is not one of them, however loud the rest of it is.
     */
    data class Surged(
        val seat: Seat,
        /** Full. The lamp the rules chose, not a value the device picked. */
        val peak: Int,
        /** Black. Where the slam lands before the phone returns to its last authorised [LampSet]. */
        val black: Int,
        val sound: Sound,
        val haptic: Haptic,
    ) : Effect

    /**
     * **The markers Isolate may be fired at, to one Insider** (`gdd.md:315`, D-150, the owner's
     * 2026-08-22 targeting ruling).
     *
     * *Isolate picks from a LIST — markers, by shape and room — and the glow of reading the list is
     * the deliberate price.* A phone cannot draw a list it was never sent, so this is the list, and
     * it is the narrowest thing that can be: the round's **active** markers with their glyph and
     * room, and nothing about work, seats, occupancy or which of them are already dark.
     *
     * ### ⚠️ This is a real disclosure and it is the only one in the table shaped like a map
     *
     * `Effect.WorkOrderIssued`'s whole narrowing is that *knowing where your own next Subroutine is,
     * is the game; knowing where every card in the house is, is the map of the round the house has
     * never published* — and this publishes it, to Insiders, on purpose. **The ruling requires it**:
     * a list picker over markers is a list of markers. It is permitted to `Insider/Live` and to no
     * other class, which is [HouseSignedOff]'s narrowness for a live message, and it must never be
     * widened by one class — the row that served an Insider would hand every Resident the geography
     * their own order withholds.
     *
     * Flagged rather than defended: the disclosure follows from the ruling, and the *scope* of it —
     * active markers rather than every registered card — is this build's reading.
     *
     * ### The Egress nodes are absent for the duration of an Egress (D-150, F-002 amended)
     *
     * *The resolution is exclusion, not immunity: for the duration of an Egress the two nodes do not
     * appear in Isolate's target list.* Not a flag on a row and not a fake success — they are simply
     * not in [targets]. **It leaks exactly nothing**, because the house has already named both nodes
     * to every phone in the building on [EgressOpened]; a list that omits two rooms everybody was
     * just told about omits something public. And it is the *shared pool* that makes the alternative
     * unacceptable: a fake success would tax a teammate's budget in the dark for a shot the house
     * had already decided could not land.
     *
     * ### Re-sent whenever the answer changes, never as a diff
     *
     * The whole list, every time, exactly as [WorkOrderIssued] is re-sent on every return: a message
     * that only appeared when something had changed would make its **presence** the news, and what
     * it would be reporting is that an Egress had started or stopped — which every phone was told
     * outright.
     *
     * **Already-Isolated markers stay on the list.** Which cards another Insider has taken dark is
     * not this Insider's to know, and an omission would report it.
     */
    data class IsolateTargetsOffered(val seat: Seat, val targets: List<Destination>) : Effect

    /**
     * **STATIC: the house's monitoring is jammed, house-wide, for ten seconds** (D-186,
     * `gdd.md:447-495`).
     *
     * *A ten-second house-wide jam of every monitoring surface the living can read.* The living's
     * live view drowns in animated noise with the real counts faintly beneath it, a Timelapse
     * crossing the window renders those seconds as static, and **underneath, nothing changes** —
     * tracking records the truth throughout, check-ins land, the meter moves, and the view is
     * correct again the instant the window closes.
     *
     * ### ⚠️ It is LOUD and ANONYMOUS, and those two words are the whole ability
     *
     * *The house shows that it was jammed. It never shows who.* So this effect goes to the whole
     * living house — it is the announcement as well as the render — and **it carries no seat.** It
     * cannot be addressed to the firing Insider and widened later, because there is no field on it
     * that could name them: every living phone receives byte-identical bytes, which is what makes
     * *an Insider did something in the last ten seconds and I have no idea which* the sentence the
     * ability is built to produce.
     *
     * That also satisfies `gdd.md:398`'s no-actor-side-feedback rule by construction rather than by
     * suppression: the firing phone is told exactly what a bystander's phone is told, because they
     * are the same message. And `gdd.md:393`'s *every observable effect must be self-inflictable*
     * needs no special case — an Insider standing at the Terminal when their own Static lands sees
     * the same noise a Resident does.
     *
     * ### The GHOSTS ARE EXEMPT, and it is the liveness privilege stated once more
     *
     * M12's fiction is that the house erased the out from the registry and can neither see nor
     * control them; **a jam is the house's own monitoring degrading, and a ghost's true occupancy is
     * not the house's monitoring.** The out already get both bars, true counts, no injected error
     * and no staleness bands (D-134, D-145) — this is the same privilege, and it costs nothing
     * because a ghost with a clean map during a jam cannot tell anybody. The exemption is the
     * allowlist's: this kind is permitted to the two **living** classes and to neither out class.
     *
     * ### [seconds] is the window, and [seed] is the noise
     *
     * A phone has to draw ten seconds of animated noise, and rule 5 says light is game state — no
     * animation the core did not author. So the field the noise is generated from **rides the
     * effect**: the same seed on every phone draws the same field, and a replay of the round draws
     * it again. It is minted off the round's seed and the firing tick and from nothing else, so it
     * is a fact about *when* and never about *who* — see `staticFired` in the rules.
     *
     * ### It has no [Sound] and no [Haptic], and both are rulings rather than omissions
     *
     * D-176: **the one sound in the game is the Surge.** D-135 closes the long haptic to five
     * events and this is not one of them. The announcement is what the surfaces look like, and a
     * buzz in every pocket the moment an Insider fired would be a timer telling a dark house that
     * *now* was the moment somebody chose.
     */
    data class StaticOpened(val seconds: Int, val seed: Long) : Effect

    data class SubroutineProgressed(val remaining: Int) : Effect

    /**
     * **The meter, live, for the people outside the system** (D-134, D-145, `gdd.md:1014`).
     *
     * *A player outside the system sees the real bars live*, and that privilege is what being out
     * is made of. This is the bar as a **percentage** and never as a count against a total: an
     * absolute value hands back `seats × METER_PER_SEAT` and therefore the Insider count D-103
     * spent a revision hiding (D-130's argument, on the wire rather than on a screen). See
     * [Balance.integrityPercent], which is the only place that arithmetic exists.
     *
     * ### Why this is a second kind beside [SystemIntegrityBatched] and not one kind with two rows
     *
     * The two carry the same fact and differ in **when**, which is the one thing an allowlist
     * cannot express: a single kind permitted to both audiences would deliver every live movement
     * to the living the moment it happened, and the living are entitled to a number only at a
     * meeting (`gdd.md:192`, `:1002`). Timing is not a permission, so it has to be a kind — and
     * this is [EmitSchema]'s own rule arriving from the other direction: two audiences told
     * different amounts is two kinds, and *how often* is an amount.
     *
     * **It says nothing about who moved it.** A percentage of the house's work, to people who
     * cannot act on it — not whose entry banked, not how many anybody has done. [SubroutineGraded]
     * is addressed to the one seat that returned something for exactly that reason.
     */
    data class SystemIntegrityLive(val percent: Int) : Effect

    /**
     * **The meter as the living are told it: once, at a shift boundary, then frozen**
     * (`gdd.md:192`, `:1002`).
     *
     * A shift is the work interval between meetings, and a meeting ends the shift it interrupts —
     * so the boundary is the meeting being called, and that is the one moment this is emitted. In
     * between, the living see whatever number they were last given and it does not move: continuous
     * decrements are a rate signal nobody living is entitled to, and *the batch* is what stops the
     * meter reporting who is working and how fast.
     *
     * **The one exception is not here and must not come here.** SystemIntegrity reaching zero ends
     * the round immediately (D-131's first route, `gdd.md:203`) — that arrives as
     * [RoundEnded], on every phone at once, and a group that cleared the meter does not wait for a
     * meeting to be told so. See `Rules.outcomeOf`.
     *
     * Percentage only, for [SystemIntegrityLive]'s reason.
     */
    data class SystemIntegrityBatched(val percent: Int) : Effect

    data class MessageDelivered(val seat: Seat, val body: String) : Effect

    /**
     * **A seat's work order, as that seat may see it** (D-114, D-129, D-112).
     *
     * Carries [OrderLine]s and never [OrderEntry]s: the answer key and the dependency graph stay
     * on the authority side, and a blocked line is a different *type* rather than the same type
     * with its name blanked (rule 3).
     *
     * ### It carries where the work is, and only for this seat's own lines
     *
     * A known line carries its [Destination]s — the room and the glyph, which is what a player
     * navigates a dark house by. **The narrowing is which rows of the home's table come out**, and
     * it happens inside [WorkOrder.asLines] so that every producer of a client order narrows by the
     * same rule. What is *not* authorised is the home's full geography reaching a phone: knowing
     * where your own next Subroutine is, is the game; knowing where every card in the house is, is
     * the map of the round the house has never published.
     *
     * A blocked line carries no destination either, because a dependency is cross-player (D-146)
     * and a room without a name still names the person upstream of you.
     *
     * ### Emitted on every return, not only on the ones that changed something
     *
     * Completing an entry unblocks whatever was waiting behind it, so the order has to be re-sent
     * — but sending it only when something changed would make its **presence** the verdict, one
     * effect after [SubroutineGraded] has already delivered the real one. Two answers to the same
     * question is one answer too many, and the second would be the one a client could read without
     * being told. So it goes out on every return, constructed once outside the branch, exactly as
     * the cooldown is in the revoke path.
     *
     * ### Every order is the same length and that is load-bearing
     *
     * [Balance.orderSize] is computed from public lobby facts alone — seats and the host's visible
     * setting — so an Insider's fake order is exactly as long as everybody else's (D-129). Length
     * is not a channel, in either direction.
     */
    data class WorkOrderIssued(val seat: Seat, val lines: List<OrderLine>) : Effect

    /**
     * **The house's answer to a scan — and it is the same answer whether or not there was work
     * there** (D-124, D-110, rule 1).
     *
     * [opened] carries the instance the scan drew, or is null for **NOTHING FOR YOU HERE**.
     *
     * ### The null is the ruling, not a saving
     *
     * The tempting shapes are two: emit nothing when a card holds no work for this seat, or split
     * the answer into two kinds. Both are the same bug in different clothes.
     *
     * *Emit nothing* is rule 1's forbidden shape at its purest. A scan that opened work would
     * produce a message and a scan that did not would produce silence, so the **absence** of the
     * effect is the answer — and the player's phone is left holding a question the house has never
     * heard of, indistinguishable from a failed radio.
     *
     * *Two kinds* is the same leak one layer out. Two kinds mean two rows in the allowlist, two
     * shapes on the wire, and two different things for anything downstream to count — so a card
     * that holds nothing for you becomes separable from one that does by a reader who never sees
     * either payload. **One kind, one row, one message per scan**, and only the content differs.
     * `VoteHeld` carries a nullable selection for the same reason and it is the same decision.
     *
     * ### D-124's two vocabularies are not both here
     *
     * A **registered** card the house has nothing for you at is this effect with a null, and it is
     * deliberately unremarkable. **Unregistered paper never reaches the rules at all** — it is
     * refused at the scan's routing, reported to nobody (D-072), and answered by the client's own
     * alert. Collapsing the two would build the detector D-124 refuses: a Resident sweeping cards
     * could separate *registered but not mine* from *not registered at all*.
     *
     * **Addressed to the scanning seat and to nobody else.** Broadcast, it would publish who is
     * standing where and finding work, which is the whole read the meter is quantised into a
     * percentage to prevent, arriving one player at a time.
     */
    data class ScanAnswered(val seat: Seat, val opened: SubroutineInstance?) : Effect

    /**
     * **The presence plane's whole output: a window opened, or a window closed** (D-111, D-136).
     *
     * See [Presence]. The house **records, never recites** — this effect has no row in the emit
     * schema, so it ships to nobody, and it is addressed to the performing seat alone so that it
     * could not reach another player even if somebody wrote one.
     *
     * ### It carries no reason, and adding one would rebuild the leak D-111 closed
     *
     * A window closes when the entry is handed over, when the player presses STOP NOW, and when
     * they step away from the marker. **Those three are one fact here and must stay one fact.**
     * A `reason` field is an abandonment record, and an abandonment record is the behavioural
     * channel that separates a real Subroutine from a fake — somebody who walks away from work
     * that was never going to count, at a rate nobody designed and nobody can see.
     */
    data class PresenceChanged(val seat: Seat, val at: MarkerId?, val open: Boolean) : Effect

    /**
     * **The house's opening text — one of exactly two events that dim the house** (D-118).
     *
     * The other is the Egress. Every other notification is quiet: no dim, no brightness spike,
     * nothing world-observable at all. A separate kind from [MessageDelivered] rather than a flag
     * on it, and the reason is the one A2 gave for splitting the presentation: **a second dim on
     * the second text would spend the first one's meaning**, and a flag is a thing somebody can
     * pass `true` for by accident.
     *
     * **It carries no words**, for [WorkOrderIssued]'s reason and D-112's: the client holds the
     * copy, the house sends the fact. The words are identical for both roles anyway — what differs
     * by role is what is *behind* the message, on a thread that has to be opened to read — so
     * putting them on the wire would buy a drift risk and nothing else.
     *
     * **It reaches every seat.** The dim is world-observable, so a notification addressed to fewer
     * than everyone is a beacon (D-076).
     *
     * **[haptic] is Short, and that is a ruling rather than a default.** D-135 closes the long
     * haptic to five events — the Egress, an incoming phone call, STAND AND WALK IN, the Restrained
     * takeover, and the end of the LIGHTS OUT IN *n* countdown — and this is not one of them. In a
     * silent house a long buzz is world-observable through a pocket, and a signal that means five
     * specific things stops meaning them the moment a sixth is added.
     */
    data class OpeningMessage(val seat: Seat, val haptic: Haptic) : Effect

    // ---- The meeting -----------------------------------------------------------------------
    //
    // Twelve kinds for one lifecycle looks like a lot until you try to merge two of them. Every
    // split below is a permission set that differs -- EmitSchema's rule is that a kind is
    // permitted to a class in full or not at all, so **if two classes need different content,
    // that is two kinds**, each with its own row and its own reason. The ring and the walk-in,
    // the count and the selections, the result and the attribution: each pair is one fact told
    // to two audiences who may be told different amounts of it, and merging any pair would mean
    // redacting by nulling fields under another name.

    /**
     * **The ring** (D-121, D-134). Goes to the living; a player who is out gets [StandAndWalkIn].
     *
     * Carries who called it, because the caller is public by construction: they walked to the
     * meeting card, which is the whole reason D-121 refused a remote button — *the difference
     * between calling a meeting and being the person who called it.*
     */
    data class MeetingOpened(
        val caller: Seat,
        val trigger: MeetingTrigger,
        val haptic: Haptic,
    ) : Effect

    /**
     * **The caller's own phone, told that it is the one calling** (E-R1-1, ruled 2026-08-23).
     *
     * [MeetingOpened] carries `caller: Seat` to everybody, and a client cannot use it: **a phone
     * does not know which seat it is**, deliberately — *intents are attributed by connection, never
     * by a client naming itself* — so the comparison that would pick the caller's screen out has
     * one operand. The routing table asks for *the calling seat → `Calling`*, and until this existed
     * the caller stood on the ordinary ring screen with everybody else.
     *
     * **Two shapes could have closed it and the owner chose this one**: the house sends the caller a
     * distinct message rather than teaching a phone its own seat. A phone that knew its seat number
     * would be a phone that could name it, which is the one cheat in this game that is remote,
     * undetectable and requires no physical act — the whole reason the wire is built the way it is.
     * A per-seat message costs one row and teaches the client nothing.
     *
     * ### It is [RestrainedTakeover]'s shape exactly, and that is the argument for it
     *
     * Addressed to one seat, carrying nothing but the fact that it is addressed to that seat, and
     * **the seat comes off this type at the emit boundary** — the wire member has no field at all.
     * So a phone learns *you are the one calling* and still cannot say which seat it is, which is
     * the same trade [OpeningMessage] and [EgressOpened] already make.
     *
     * ### No haptic, and that is a ruling rather than an omission
     *
     * [MeetingOpened] rings, and the caller's phone receives that too — this is a second message to
     * a phone that is already ringing, not a second buzz. D-135 closes the long haptic to five
     * events and this is not a sixth; more sharply, **one phone in a silent house buzzing
     * differently from the other five is the caller announced through a pocket**, at the one moment
     * the design already made public by making them walk to the card. Public by walking is not the
     * same as broadcast by hardware.
     *
     * ### It is not a disclosure, because the caller already knows
     *
     * The one seat this reaches is the seat that scanned the meeting card or reported a Revoke
     * (D-121). It tells that phone something its owner did thirty seconds ago with their hands, and
     * it tells nobody else anything: two meetings called by two different people produce
     * byte-identical wire members, and the only difference is which socket it went down.
     */
    data class MeetingCalling(val seat: Seat) : Effect

    /**
     * **What a player outside the system gets instead of a call** (D-134).
     *
     * *The ringing call is for the living.* A newly Revoked player is told to stand and walk in,
     * with the long haptic D-135 reserves for it; the couch — previously Revoked and Restrained
     * players — is called in the same way with an ordinary buzz, because D-104's gate does not
     * close until they are standing there too.
     *
     * **The haptic differs by round-state and never by role.** Round-state is publicly observable
     * (D-068) and D-135 names this buzz specifically; a long buzz that meant *this player is an
     * Insider* would be the audible tell D-102 exists to prevent, and nothing here can carry that.
     */
    data class StandAndWalkIn(val seat: Seat, val haptic: Haptic) : Effect

    /**
     * **The gate, as a count** (D-104). Living and out alike, because they are one gate.
     *
     * A count and a total, never a list of who. *The ledger reports anonymous counts and nothing
     * finer* (`gdd.md:294`): an app that confirms who was standing where and when is an app
     * adjudicating alibis, which is considerably more than this design wants.
     */
    data class CheckInProgressed(val present: Int, val expected: Int) : Effect

    /**
     * The meeting moved on. One kind for every phase, and that is deliberate.
     *
     * The phases are not different disclosures — *the talk has started*, *the ballot is open*,
     * *the result is in* are each known to the whole room at the moment they happen, and all
     * three reach exactly the same audience. Splitting them would be three rows in the allowlist
     * that could only ever be edited together.
     */
    data class MeetingPhaseOpened(
        val phase: MeetingPhase,
        /**
         * **How long the ballot runs, on the push that opens it — and null on every other phase**
         * (D-117).
         *
         * The vote's window is the one meeting clock a host can move, it is locked at arming
         * ([Event.RoundArmed.voteWindowSeconds]), and it arrives *with the phase* because that is
         * the moment a phone needs it: the clock is drawn on the screen this push moves everybody
         * to. `Countdowns` has said since L2 that this is where the number would come from.
         *
         * **Named for the vote rather than for "this phase's window", and that is deliberate.** The
         * discussion's ninety seconds and the tally's fifteen are the design's own constants, the
         * client already holds them, and nothing in this unit gave them a sender — so a field called
         * `windowSeconds` would be null on two phases that *do* have windows, and the first client
         * to read null as *no clock* would take the discussion's countdown off the screen. This one
         * is null wherever it does not apply and cannot be mistaken for anything else.
         *
         * It is the same number for every class, so there is no narrowing hiding in it —
         * [EgressHeld]'s `running` is the same decision.
         */
        val voteWindowSeconds: Int?,
        val haptic: Haptic,
    ) : Effect

    /** Hands up for READY TO VOTE, counted. Named for the same reason as [CheckInProgressed]. */
    data class ReadyProgressed(val ready: Int, val expected: Int) : Effect

    /**
     * **The house telling one seat what its ballot actually says** (D-117, rule 1).
     *
     * Emitted on **every** selection tap and every READY from that seat, whatever the state of the
     * ballot. That is the whole point: after READY the vote cannot be changed, and a later tap is
     * **refused, not dropped** — so the house re-asserts, and what comes back is the locked
     * selection rather than the one the finger just landed on.
     *
     * Written the other way — say nothing to a seat whose tap changed nothing — the absence is
     * the message, and the player is left looking at a lit row the house has never heard of. The
     * shape is identical in both cases; only [selection] and [locked] differ, which is state.
     *
     * Addressed to the one seat. It is that player's own ballot and nobody else's business: the
     * living see a count and never a selection (D-117).
     */
    data class VoteHeld(val seat: Seat, val selection: Seat?, val locked: Boolean) : Effect

    /**
     * **A live selection, for the couch** (D-117, D-134).
     *
     * *Ghosts are the only readers who see selections rather than a count.* This is what a player
     * outside the system watches, and it is why being out is an information privilege rather than
     * a preview. It must never reach a living class in either role — the living get
     * [VoteProgressed], which is a number.
     */
    data class VoteSelectionShown(val voter: Seat, val selection: Seat?) : Effect

    /**
     * **`N OF 6 VOTED` — locked seats, not selections** (D-117, E8-1's first question).
     *
     * The living see the count and never the selections. Counting selections instead would leak
     * the shape of the room's thinking in real time to the people still in it.
     */
    data class VoteProgressed(val locked: Int, val expected: Int) : Effect

    /**
     * **The result, to everyone.** Most votes is Restrained; ties resolve to Skip
     * (`gdd.md:413`, `:1007`, D-075).
     *
     * Null means the room Restrained nobody. **No role, no confirmation, no reveal** — *a correct
     * restraint and a catastrophic one look identical* (`gdd.md:1007`), and nothing here has a
     * field that could tell them apart.
     */
    data class MeetingResult(val restrained: Seat?, val haptic: Haptic) : Effect

    /**
     * The ballot with names against it. **The out, and nobody else** (D-075).
     *
     * The living get [MeetingResult], which carries the outcome and no attribution. This carries
     * who cast what, which only a player outside the system ever learns — and it is safe only
     * because of *when*: the room already knows who is out, so there is never a window in which
     * someone outside knows something the living do not.
     */
    data class MeetingResolved(val restrained: Seat?, val attribution: List<Pair<Seat, Seat?>>) : Effect

    /**
     * **The takeover, at the halfway mark, to the losing seat only** (D-102, D-134's E1-1).
     *
     * A house push per seat, not a client-scheduled screen change. The rejected alternative is a
     * table in `ui` that could say *"…and if it was you, this other screen"*, which is the device
     * deciding a game answer — and the answer it would be deciding is the one the whole meeting
     * was about.
     *
     * Halfway rather than at zero *so they do not walk away when the countdown ends*, and the
     * seat leaves the round at this moment rather than at the buzzer: the group holds them, and
     * the house deauthorises them moments later (`gdd.md:1009`).
     */
    data class RestrainedTakeover(val seat: Seat, val haptic: Haptic) : Effect

    /** Lights out. The meeting is over, and D-135's fifth long haptic rides it. */
    data class MeetingEnded(val haptic: Haptic) : Effect

    // ---- The Egress ---------------------------------------------------------------------------
    //
    // Four kinds, and the audiences are the reason there are four rather than two. The house
    // catching fire, the timer stopping, the containment landing and the containment failing are
    // each one fact, but one of them -- SyncPulseAnswered -- is addressed to a single seat and
    // carries what that seat's own beat did, while the other three reach the whole building. A
    // kind is permitted to a class in full or not at all (EmitSchema's rule), so a merge would be
    // redaction by nulling fields under another name.

    /**
     * **The house is on fire, and this is where everyone finds out** (`gdd.md:349`, D-118, D-076).
     *
     * The heavy notification and the widget takeover both ride this one effect, which is what makes
     * *the alert and the widget cannot name different rooms* a property of the wire rather than a
     * discipline two screens have to keep. Two copies of that pair would send two people who may
     * not speak to two different places, and the mistake would look like a typo in a diff.
     *
     * **It reaches every seat, living and out.** This is one of exactly two events that **dim the
     * house** (D-118) — the other is [OpeningMessage] — and a dimming lamp is world-observable, so
     * a notification addressed to fewer than everyone is a beacon (D-076). It is also what a player
     * outside the system watches: their class already carries the liveness privilege that lets them
     * see the real Egress number while it runs (`gdd.md:1014`).
     *
     * **[haptic] is Long, and it is D-135's first.** *The Egress* is named at the head of that
     * closed set of five, and this is the effect it names.
     *
     * **The actor gets exactly this and nothing more** (`gdd.md:396`). No actor-side feedback on
     * firing any ability: the Insider who pressed the button receives the same dim, the same buzz
     * and the same two room names as everybody else. Their own phone knows what it pressed and what
     * its cooldown now says, and that is input echo rather than a game answer.
     */
    data class EgressOpened(
        val seat: Seat,
        val type: EgressType,
        /** Both nodes, in draw order. Named to everyone, because coordination is required and
         *  nobody may speak (`gdd.md:363`). */
        val nodes: List<MarkerId>,
        /** Ticks on the clock at the moment it started. The device holds no opinion of its own. */
        val remaining: Long,
        val haptic: Haptic,
    ) : Effect

    /**
     * **The countdown stopped, or started again** (D-133).
     *
     * A meeting called by reporting a Revoked player is the one meeting that can happen during an
     * Egress, and it **pauses** the timer — never resets it. Both halves are this one kind, because
     * they are one fact told to one audience: *the number on the widget is moving, or it is not.*
     * A separate kind for each would be two rows in the allowlist that could only ever be edited
     * together.
     *
     * **[running] is state, not a redaction flag.** It says which of the two happened, both
     * audiences get the same value, and there is no narrowing hiding inside it — [PresenceChanged]
     * carries `open` for the same reason and it is the same decision.
     *
     * **Everyone, living and out.** The whole party is standing in one room when it fires: a pause
     * that reached fewer than everybody would leave somebody's widget counting down through a
     * meeting the rest of the house had stopped for.
     */
    data class EgressHeld(val remaining: Long, val running: Boolean, val haptic: Haptic) : Effect

    /**
     * **The house's answer to one participant's beat — and it is the same answer either way**
     * (`gdd.md:355`, rule 1).
     *
     * [held] says whether the house is now holding this seat's beat, waiting for somebody at the
     * other node. It is `false` for a beat that missed the schedule, for a seat serving a lockout,
     * for a seat that is not standing at a node, and for a seat outside the system — **one shape,
     * one kind, one message per return**, with only the value differing.
     *
     * ### Written the tempting way it is a node-detector
     *
     * *Emit nothing unless the beat was good* leaves the absence as the answer, and the absence is
     * readable: a Insider standing anywhere in the house could tap, watch for silence, and learn
     * that the house does not think they are at a node — which is a claim about the presence plane,
     * delivered to a living phone, that D-111 split the two planes to prevent. `ScanAnswered`'s
     * null and `VoteHeld`'s selection are the same decision one system over.
     *
     * ### It says nothing about anybody else
     *
     * Not who is at the other node, not how many are waiting, not whether a partner is close.
     * Containment is a fact about the house and arrives as [EgressContained] to everybody at once;
     * anything finer here would be a live report of who is standing where, addressed to a living
     * player, which is the leak the whole presence plane is denied a schema row to close.
     */
    data class SyncPulseAnswered(val seat: Seat, val held: Boolean) : Effect

    /**
     * **Contained. The widget reverts and nobody learns anything about anybody** (`gdd.md:987`).
     *
     * **It carries no seats and no node, and that is the whole design of it.** Naming the pair that
     * contained it would publish two players who were demonstrably standing at known places at a
     * known moment — an alibi, minted by the app, at the one moment in the round when everybody is
     * moving and nobody can speak. Naming the node would publish where they were. The house says
     * *it stopped*, and the room works the rest out or does not.
     *
     * Everyone, living and out: the widget goes back to being System Integrity on every phone in
     * the building, and a reversion that reached fewer than everybody would leave somebody
     * counting down toward a loss that is not coming.
     */
    data class EgressContained(val haptic: Haptic) : Effect

    /**
     * **Uncontained. The terminal fact: the Insiders win outright** (`gdd.md:361`, D-131).
     *
     * *A running Egress outlives its Insiders and must still be stopped* — so this can arrive after
     * the room has Restrained every Insider it had, and it still ends the round in their favour.
     *
     * **It ends nothing by itself, and it is still the reason the round ended.** The fact and the
     * ending are two effects because they are two disclosures with two different jobs: this one
     * says the countdown reached zero, and [RoundEnded] says the evening is over.
     * `WinRoute.EgressUncontained` is where the two meet, and it is read off the event rather than
     * off state — see `Rules.outcomeOf`.
     *
     * **It reaches no client at all, and that is deliberate** — see [EmitSchema], where its row
     * used to be. Every emission of this sits in the same reduction that ends the round, so the
     * old row could not have delivered it to anybody; what the house tells the room is
     * [RoundEnded], once, with one buzz. This is what it tells the recording.
     */
    data class EgressSucceeded(val haptic: Haptic) : Effect

    // ---- The ending ---------------------------------------------------------------------------
    //
    // Three kinds, and the reason there are three is the reason there are twelve for the meeting:
    // a kind is permitted to a class in full or not at all, so two audiences told different
    // amounts is two kinds. Everybody is told the round is over and who won; everybody is told who
    // the Insiders were and what they were coerced with; and the house says one last thing to the
    // people it owned, which is nobody else's message.
    //
    // **All three are permitted to the ENDED classes and to nothing else**, which is a stronger
    // guarantee than any of their contents. `RoundState.Ended` is reachable only through
    // `GameState.outcome`, and the only writer of that is the transition that emits these — so a
    // reveal cannot be delivered before the round ends even if somebody constructs one, because
    // there is no class alive at that moment that the allowlist would hand it to.

    /**
     * **The round is over, and this is every phone learning it at once** (D-131, D-157).
     *
     * Addressed per seat and emitted once per seat, exactly as [OpeningMessage] and [EgressOpened]
     * are, and for their reason: the ending takes over every screen in the house, so the effect
     * must reach everyone — and a broadcast *shape* is a thing a quieter message could inherit
     * later.
     *
     * **[winner] and [by] are both public, without exception.** Every one of D-131's four routes is
     * something the whole house watched happen, and the reveal on the same screen states more than
     * either of them does. See [WinRoute], which carries the argument route by route.
     *
     * **[haptic] is Short, and that is a ruling rather than a default.** D-135 closes the long
     * haptic to five events and the round ending is not one of them. It is tempting — it is the
     * biggest moment in the round — and that is precisely the reason the set is closed: a signal
     * that means five specific things stops meaning them the moment a sixth is added, and this
     * one would arrive in a house where the lights are about to come on anyway.
     */
    data class RoundEnded(
        val seat: Seat,
        val winner: Winner,
        val by: WinRoute,
        val haptic: Haptic,
    ) : Effect

    /**
     * **THE INSIDERS WERE — the one moment this app ever states an alignment** (`gdd.md:1063`).
     *
     * *Never shown: any confirmation of alignment, at any point, by any path* (`gdd.md:213`) holds
     * everywhere before this and nowhere after it. Not on a Revoke, not at the tally, not on
     * finding a Revoked player — and then, once, when the round is over, all of it at once.
     *
     * ### The names are people's names now, and the transport is where they come from
     *
     * It carried seats and lines, and `PanelState.revealed` carried names and lines, and nothing
     * joined them — so the ending screens drew a fixture pair. [InsiderNamed] carries the name,
     * resolved from the desk and ridden into the round on the arming event (D-115). Nothing new is
     * disclosed by it: every phone in the lobby was already sent every name. What this pairs a name
     * with is the alignment, which is this effect's entire job.
     *
     * **The recording still holds seats and no names**, exactly as it holds seats and no lines, so
     * a replayed reveal publishes blanks and the round is reproduced without either.
     *
     * ### The blackmail publishes with the names, in one kind, on purpose
     *
     * *Everyone learns who **and why** — the petty, mundane thing each Insider was coerced with.*
     * The names and the lines are one disclosure and therefore one kind: split into two, the day
     * somebody narrowed one of them the app would name Insiders without publishing what was held
     * over them, which is the half that makes the reveal land as a person rather than as a result.
     *
     * ### It is the deletion, not something before it
     *
     * D-116 promises the line is deleted when the round ends, and **the publish is the round
     * ending**. `GameState.endRound` empties the desk in the same transition that builds this, so
     * the last read of a one line and its deletion are one instruction apart.
     *
     * ### Only Insiders' lines are in here
     *
     * The house never uses a Resident's (D-116, as swept in revision 33 — *all the one-lines are
     * real; the house never uses a Resident's*). A Resident's line is typed, handed over, held for
     * the round and then dropped without ever being read: that is what makes the promise worth
     * anything to the people who were never picked.
     *
     * Ordered by seat, like every collection the rules produce.
     */
    data class InsidersRevealed(val insiders: List<InsiderNamed>) : Effect

    /**
     * **The house speaks last, and only to the people it owned** (`gdd.md:1051`).
     *
     * *"Thank you for your cooperation"* on a Insider win; *"Unfortunate"* on a Resident win. Its
     * only direct address of the round, in the same customer-service register it used all evening,
     * arriving on the phones of the players it spent the evening being intimate with and on
     * nobody else's.
     *
     * **Addressed to a Insider and permitted only to a Insider who is out of a round that has
     * ended** — the narrowest row in the allowlist. Lawful for one reason and it is worth stating,
     * because on any other screen this would be the leak the whole app is built to prevent: by the
     * time this is delivered [InsidersRevealed] has already published who the Insiders were, to
     * everybody, on the screen this message appears on. There is nothing left for it to disclose.
     *
     * **It carries its words**, unlike [OpeningMessage], and for the reason [MessageDelivered]
     * does: the line depends on who won, and a client composing it would be a client deciding what
     * the house thinks of them.
     *
     * **No haptic.** The buzz for the ending is [RoundEnded]'s, once, for everybody. D-156: the
     * underlying message count never drives the buzz count — not at the reveal and not here, where
     * an extra buzz on two phones in a room that has just stood up would be the tell arriving
     * after the answer.
     */
    data class HouseSignedOff(val seat: Seat, val body: String) : Effect
}
