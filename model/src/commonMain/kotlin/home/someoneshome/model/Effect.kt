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
     *
     * ### [touch]: what an armed touch did with this phone's Revoke (the owner's 2026-11-18 ruling, D-290)
     *
     * [ArmedTouch.None] on every Surge, Isolate, Static and Egress answer. An armed phone's answer
     * to a touch says the touch spent its Revoke ([ArmedTouch.Spent]) or that the Revoke landed
     * ([ArmedTouch.Landed]), so the phone takes ARMED down at once rather than holding it until 20 s
     * from the hold, and an answer to a touch can no longer be read as an Egress's. **It is still one
     * frame of one size**: the three values are one digit on the wire. The owner accepted what the
     * second value tells the armed phone: whether its own Revoke connected. It reads the touch's two
     * phones and nothing else, and only an armed phone is ever told it.
     */
    data class AbilityFired(
        val actor: Seat,
        val cooldownStarted: Boolean,
        val touch: ArmedTouch = ArmedTouch.None,
    ) : Effect {
        /** **Long when this phone's Revoke landed** (D-290), and nothing otherwise; [ArmedTouch.haptic]. */
        val haptic: Haptic? get() = touch.haptic
    }

    /**
     * **Somebody else's ability was answered at this moment, and this seat is sent a frame of its
     * answer's length** (D-263, provisional).
     *
     * [AbilityFired] reaches the actor alone, so the moment a Surge, Isolate, Static or Egress is
     * answered the actor's socket carried one frame more than every other living phone's: on a
     * Wi-Fi that can be measured, the one phone that got a frame was the one that asked. So the
     * rules build one of these for **every other seat** at the same place in the burst, seat by
     * seat, and the allowlist sends it to both living classes and to no out class. Every living
     * phone is sent exactly one frame of one length (`RoundMessage.AbilityBlanked`), the actor its
     * real answer and the rest this.
     *
     * Built without a read of any role: the rules address it to every seat that is not the actor,
     * and the row narrows it to the living. It carries the seat and nothing else, and the seat comes
     * off on the wire. Nothing reads it on a phone.
     */
    data class AbilityBlanked(val seat: Seat) : Effect

    /**
     * **A touch, answered: the house heard this phone touch another, and it changed nothing this
     * phone may be told about** (R1 of the owner's 2026-11-15 rulings, D-216).
     *
     * ### Every touch puts one frame of one size on both phones, whatever they are
     *
     * The resolver mints [Event.ContactMade] in both directions for every mutual sighting (D-213), so
     * each of the two phones is the actor of exactly one event per touch, and `Rules.contact` answers
     * every actor with exactly one effect. **Which effect is the actor's own state and nothing else:**
     * an armed Insider is told [AbilityFired], because their Revoke really was spent; everybody else,
     * an unarmed Insider and every Resident alike, is told this. The two project to frames of one
     * length (`RoundWire`'s padding, pinned by `ContactAnswerSizeTest`), so a reader of the Wi-Fi sees
     * the same frame go down both sockets on every touch in the house.
     *
     * Until this existed, an unarmed Insider's touch put [AbilityFired] on their socket and a
     * Resident's put nothing on theirs: a frame whose presence followed the role, on a transport
     * D-212 records as readable (D-213, open item 1).
     *
     * ### It is not a branch on success, and that is why rule 1 still holds
     *
     * The branch is on whether *the actor* holds an armed Revoke, which their own phone already knows:
     * they held the tile down. Nothing here asks what the touch did to the other phone. An armed
     * Insider touching a live player, a Revoked one, another Insider or themselves is told
     * [AbilityFired] identically; an unarmed phone touching any of them is told this identically.
     *
     * **It moves nothing.** No screen, no lamp, no buzz: a touch that did nothing must look like
     * nothing, and a phone that reacted to it would be a phone announcing that it had been touched.
     */
    data class ContactAnswered(val seat: Seat) : Effect

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
    data class SubroutineGraded(val seat: Seat, val accepted: Boolean) : Effect {
        /**
         * **D-160's *yes* on ACCEPTED, its *no* on REJECTED** (the owner's 2026-11-21 ruling,
         * D-320): one Short, or two vibrates ([Haptic.Double], NOT A MARKER's, D-318), on this
         * phone only. Read off [accepted] and nothing else, so an Insider's graded fake buzzes
         * exactly as a Resident's real entry does for the same verdict (D-102, D-109).
         */
        val haptic: Haptic get() = if (accepted) Haptic.Short else Haptic.Double
    }

    /**
     * **The Surge, landing on the seat it was fired at — one event, and it is light**
     * (`gdd.md:313`, `:342`).
     *
     * *Target's lamp slams to full, then to black. One event.* **There is no sound**: the owner
     * ruled on 2026-11-21 that the game makes none (*we can't control sound on the phone*), which
     * reverses D-176's *the one sound in the game is the Surge* (D-320). What was a light and a
     * sound is the light alone, the brightest thing that can happen in a silent dark house.
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
     * ### No sound, and [haptic] is Short
     *
     * The game makes no sound (D-320). The haptic is a ruling rather than a default, exactly as
     * [OpeningMessage]'s is: D-135 closes the long buzz to five events and this is not one of them,
     * however bright the rest of it is.
     */
    data class Surged(
        val seat: Seat,
        /** Full. The lamp the rules chose, not a value the device picked. */
        val peak: Int,
        /** Black. Where the slam lands before the phone returns to its last authorised [LampSet]. */
        val black: Int,
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
     * **The frame a Resident is sent where an Insider is sent [IsolateTargetsOffered], carrying
     * nothing** (D-258, the owner's R1 principle: no frame count, size or timing follows the role).
     *
     * [IsolateTargetsOffered] reaches `Insider/Live` and nobody else, and it is sent at arming, when
     * an Egress starts and when one is contained. So at each of those moments every living
     * Insider's socket carried one frame more than every living Resident's: a count anybody
     * watching the Wi-Fi could read the role from, with the content encrypted or not.
     *
     * `Rules.isolateOffers` builds this beside the offer for EVERY seat, in the same run, so the
     * rules still never read a role; the allowlist does the narrowing. This kind's row is
     * `Resident/Live` and nothing else, so each living seat is sent exactly one of the two, at the
     * same place in the same burst. **A new kind rather than the offer widened**: widened, the row
     * that served a Resident would hand them the geography (see [IsolateTargetsOffered]), and a
     * Resident's phone would be handed something it could read as targets.
     *
     * [targets] is the offer's own list, and it stays on this side of the boundary: the projection
     * reads only how wide the offer's frame is (`RoundMessage.IsolateTargetsBlanked`), so the two
     * frames are one length at every moment and the Resident's carries none of it.
     */
    data class IsolateTargetsBlanked(val seat: Seat, val targets: List<Destination>) : Effect

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
     * is a fact about *when* and never about *who* — see `staticFired` in the rules. **Through an
     * HMAC, never arithmetic** (D-214): every living phone holds this value, and one that could be
     * divided back to the round's seed would redraw the Insiders.
     *
     * ### It has no sound and no [Haptic], and both are rulings rather than omissions
     *
     * **The game makes no sound** (D-320, reversing D-176). D-135 closes the long haptic to five
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
     * refused at the scan's routing, reported to nobody (D-072), and answered by [CardUnregistered],
     * which the house builds above the rules and sends to the scanning phone alone (D-318).
     * Collapsing the two would build the detector D-124 refuses: a Resident sweeping cards could
     * separate *registered but not mine* from *not registered at all*.
     *
     * **Addressed to the scanning seat and to nobody else.** Broadcast, it would publish who is
     * standing where and finding work, which is the whole read the meter is quantised into a
     * percentage to prevent, arriving one player at a time.
     *
     * ### [haptic]: NOTHING FOR YOU HERE says *no*, and a node of the Egress says nothing (D-320)
     *
     * The owner's 2026-11-21 ruling: NOTHING FOR YOU HERE gives D-160's *no*, two vibrates
     * ([Haptic.Double]), the buzz NOT A MARKER already gives (D-318). The acquire's *yes* before it
     * is the scan window's and is unchanged. An opened scan stays quiet: the acquire said *yes* and
     * the caught screen is the answer.
     *
     * **A scan of a node of the Egress is null here too and is not NOTHING FOR YOU HERE**: every
     * phone routes it to the tap control (D-295, D-297), so a *no* there would be the house refusing
     * a scan the phone is showing as the containment's. So the rules set the buzz, beside [opened],
     * from the same facts that chose it: [Haptic.Double] for a null at any other card, nothing at a
     * node or on an opened scan. The node is public (`EgressOpened` names both cards to every
     * phone) and no role is read, so the same card buzzes the same on an Insider's phone and a
     * Resident's. Only `null` or [Haptic.Double] can be constructed. On the wire it is one digit
     * that is always sent, so a node's answer and an empty card's stay one length (D-297).
     */
    data class ScanAnswered(
        val seat: Seat,
        val opened: SubroutineInstance?,
        val haptic: Haptic?,
        /**
         * **The Array Wipe disk in this seat's hand after the scan, or null for empty hands**
         * (D-168, F-014, D-322).
         *
         * On **every** answer, constructed once beside [opened], so that a station step and a refusal
         * are the same kind, the same row and the same message: what differs is the content. A
         * station step is the one scan that opens nothing and moves the carry — Spares puts a good
         * disk here, the Rack swaps it, Disposal takes it away — and the phone reads the step off
         * this field changing, never off a second kind of answer.
         *
         * A Memory Dump pattern is never here (F-015, *only your head is off-network*).
         */
        val carrying: DiskInHand? = null,
    ) : Effect {
        init {
            require(haptic == null || haptic == Haptic.Double) { "a scan's answer says no or nothing, never $haptic" }
            require(opened == null || haptic == null) { "a scan that opened work does not say no" }
        }
    }

    /**
     * **Somebody is waiting on [entry] of this seat's order** (`gdd.md:282`, D-146, D-322).
     *
     * *The upstream player IS told someone is waiting on them — at assignment time only.* So it is
     * sent once, at arming, to the seat whose entry holds another seat's, and **never again**: not
     * when the entry is done, and never to confirm that the downstream unlocked. It names nobody;
     * *never name the dependency* (`gdd.md:278`).
     *
     * **Every seat is upstream of exactly one other** (the draw's single cycle, D-322), so every seat
     * receives exactly one of these at arming and the burst's shape does not move with the role or
     * with anything else the house drew.
     *
     * ⚠️ **How the phone shows it is STOPPED for the owner** (D-322): no ruling says what the notice
     * looks like or says. The house sends it; the phone holds it and draws nothing.
     */
    data class UpstreamWaiting(val seat: Seat, val entry: Int) : Effect

    /**
     * **Another seat's work released one of this seat's lines: `you're up`** (D-201, D-146, D-322).
     *
     * Sent with the order that now names the line, when the house's clock releases it — the random
     * 20–120 s after the upstream entry was completed (`gdd.md:284`). The same effect whoever
     * completed it (D-109, rule 1). It carries nothing: the phone draws D-201's quiet text from
     * NUMBER WITHHELD, because *the dependency is never named* and the words never say why.
     *
     * A self-chain's unlock sends none (D-123: *discovered as they complete rather than announced*).
     */
    data class WorkUnblocked(val seat: Seat) : Effect

    /**
     * **The house's answer to a scan of the T card: you are reading, or somebody else is** (D-199).
     *
     * [reading] true opens the live view. False is ONE READER AT A TIME, the one refusal on the
     * scan path that names something about another player -- and what it names is a person
     * standing lit at a fixed place everybody knows, which the scanner is looking at.
     *
     * ### One kind for both answers, for [ScanAnswered]'s reason
     *
     * An open station and a held one are this effect with a different boolean, so the allowlist
     * holds one row and the wire carries one shape. Split, a reader of the allowlist could count
     * refusals without seeing either payload.
     *
     * ### A second kind beside ScanAnswered, and why that is not the leak it looks like
     *
     * *Which kind answers a scan* is decided by the card and by nothing else: every phone that
     * scans the T card gets this, whatever its role, and every phone that scans an ordinary
     * marker gets [ScanAnswered]. The card is printed paper with a T on it, the phone has already
     * read its shape, and the host named the station aloud during setup, so the kind says nothing
     * the scanner did not hold before the answer arrived.
     *
     * **Addressed to the scanning seat and to nobody else.** A broadcast would tell the whole
     * house who is reading the map, which is the anonymity M11's check-in rule is careful about.
     */
    data class TerminalAnswered(val seat: Seat, val reading: Boolean) : Effect

    /**
     * **NOT A MARKER: the card this phone just read is not part of this home** (D-124, D-169,
     * D-072, the owner's 2026-11-21 ruling, D-318).
     *
     * D-124's second vocabulary. A card the home never registered — a mislaid one, a spare from the
     * deck, a meeting card the host did not put in a room, a card from another home — is answered
     * with this, and a registered card with nothing for you is answered with [ScanAnswered]'s null.
     * The two must sound different, and they do: two kinds, two screens, two sets of words.
     *
     * ### The one effect the rules never produce
     *
     * **Unregistered paper never reaches the rules** (D-072), so there is no event for this to be
     * the effect of. The house builds it above the rules, where `routeScan` has just found that the
     * card is not on the map, and hands it to the emit boundary directly. **It is a delivery
     * outside the recording, and that is the ruling rather than a gap:** the recording holds what
     * reached the rules, and D-072 says this does not. A count of unregistered cards, in a
     * recording or anywhere else, is the arbiter of a player's claim that D-072 refuses; leaving it
     * out keeps the paper *theirs to report, or to lie about*.
     *
     * ### Addressed to the scanning seat and to nobody else
     *
     * Broadcast, it would tell the house who had just read a card that is not part of it, and
     * where that card might be. **No other phone learns anything**: not the scan, not the answer,
     * and not that a card was read at all.
     *
     * ### No role in it, and nothing that could carry one
     *
     * It carries the seat it is addressed to and nothing else. It is built from the card and the
     * map, never from who read it, so an Insider and a Resident holding the same paper are answered
     * with the same bytes on the same schedule — and the seat comes off at the wire.
     */
    data class CardUnregistered(val seat: Seat) : Effect {
        /**
         * **D-160's *no*: two vibrates**, on this phone only. The card's acquire has already said
         * *yes* with one (the scan window's, D-159); this is the house saying the paper is not
         * part of the home. **Provisional** (D-318): the owner agreed to the proposal that
         * recommended it. The same on every phone, for both roles, because it is the kind's.
         */
        val haptic: Haptic get() = Haptic.Double
    }

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
    data class OpeningMessage(
        val seat: Seat,
        val haptic: Haptic,
        /**
         * **The Revoke cooldown this round was armed with, in ticks** (D-317). Page 2 restarts every
         * seat's Revoke bar at half of it here (D-132, D-239), and an Insider's own arm holds its
         * echo back for all of it (D-286), so a phone draws the host's setting rather than its own
         * default. **The same number to every seat**: it is the host's public lobby setting, so
         * there is no narrowing in it, and a field that differed by seat would be a beacon.
         */
        val revokeCooldown: Long,
    ) : Effect

    /**
     * **What this phone is, said once, at arming, to the one phone it is about** (`gdd.md:923`,
     * E-R2-2).
     *
     * The design's role reveal: *Insiders receive a petty, mundane blackmail text, then a house line
     * in a customer-service register; Residents receive a flat administrative work order.* That is
     * [OpeningMessage] on the surface — one dim, one buzz, the same fact to everybody — and this is
     * the thing that message is a doorway to. *What differs by role is what is behind the message*,
     * and this is the wire carrying it.
     *
     * ### It was missing and the Insider's whole half of the game was unreachable
     *
     * Read off five ledgers of a five-seat rehearsal, every line of both rounds: `ROLE RESIDENT`,
     * including on the seat holding the round's only Insider role. Nothing on the wire set a role,
     * so page 2 drew the Resident's tiles on every phone in the house — and the Isolate picker is
     * reachable only from the Insider's page 2, so the one list the house *did* send that seat was
     * drawn by nothing. **In a real evening no Insider could have fired any ability at all.**
     *
     * ### One seat's own role and nothing else — the shape is the guarantee
     *
     * [MeetingCalling]'s idiom, with the one field that cannot be dropped. This is emitted **once
     * per seat, for every seat**, carrying that seat's own [role]; it is addressed to that seat and
     * to no other phone. **There is no list here and there must never be one.** A field naming the
     * other Insiders would be the reveal the endgame's [InsidersRevealed] exists to make once, at
     * the end, to everybody — arriving instead at arming, on one phone, about somebody else.
     *
     * **Known mode is not widened here.** `gdd.md:923` gives an Insider *a message from each fellow
     * Insider* — which rides the opening text thread, diegetically, and is the reason no roster
     * screen needs to exist. This carries self-knowledge only.
     *
     * ### The burst stays role-independent in SHAPE, which is what the differential measures
     *
     * [IsolateTargetsOffered]'s finding, one step further. That one is constructed identically for
     * every seat and narrowed by the allowlist; this one cannot be — its whole content is the role —
     * so what is held constant is the **run**: one of these per seat, every round, grouped with the
     * other arming runs. Every phone receives exactly one, at the same point in the same burst, and
     * a role-swapped round moves the payload on the swapped seat's own wire and nowhere else. That
     * is the divergence the differential harness is built to expect and it is named as such
     * (`Differential.THE_ROLE_REVEAL`).
     *
     * ### Why the row is both living classes rather than one
     *
     * A row of `Insider/Live` alone would deliver this to Insiders and to nobody else, and the
     * absence would then *be* the answer — rule 1's forbidden shape moved into the allowlist. The
     * message reaches every living phone; what differs is what it says.
     *
     * ### [line] is the Insider's own one line, quoted back to the phone that typed it (D-116, D-247)
     *
     * *Insiders receive a petty, mundane blackmail text* — and D-101 makes that text a verbatim
     * template over the line the player handed over. Every phone drew the port's *Priya's spare key*
     * instead, because nothing carried the line back. It rides here rather than on a kind of its
     * own because the blackmail text **is** the Insider's role reveal (story 2.7): one message per
     * seat, at the same point in the same burst, so the burst's shape is unchanged.
     *
     * **A Resident's carries nothing**, and that is D-116 rather than redaction: every line is real
     * and the house simply never uses a Resident's, so it is never sent anywhere again. An
     * Insider's carries their own line and only that — it reaches the seat that wrote it and no
     * other (the row and `EmitSchema.audienceOf`), so it tells no phone anything it did not type.
     * Blank, too, for an Insider who never handed one over, and on a replay, which rebuilds blank
     * lines (`Event.RoundArmed.oneLines`); `Transcript` renders the seat and the role and never
     * the line, so the live and replayed transcripts stay byte-identical.
     *
     * **[toString] prints the line's length and never its words** (`OneLine`'s rule): an effect is
     * the thing a bench, a log line or a failure message stringifies, and a device console is a file
     * that outlives the evening. `RoundAuthorityTest` sweeps everything pushed for the typed text.
     *
     * ### [insiders]: an Insider's phone is told how many Insiders the round has (D-323 B)
     *
     * The owner's 2026-11-22 ruling: *just because the phone knows doesn't mean the player itself
     * does … we can share any info with player phones.* An Insider's reveal carries the round's
     * Insider count, so page 2 says SHARED WITH THE OTHER INSIDER only when there is one. **A
     * Resident's carries 0**, for [line]'s reason: the house never tells a Resident, and the message
     * pads it to the same width (D-249). No name and no seat: a count is not a roster, and Known
     * mode's fellow Insider still arrives as text on a thread (`gdd.md:923`).
     */
    data class RoleRevealed(val seat: Seat, val role: Role, val line: String = "", val insiders: Int = 0) : Effect {
        init {
            require(role == Role.Insider || insiders == 0) { "a Resident is never told the Insider count" }
            require(insiders >= 0) { "an Insider count of $insiders" }
        }

        override fun toString(): String = "RoleRevealed(seat=$seat, role=$role, line=${line.length} chars, insiders=$insiders)"
    }

    /**
     * **Who is in the round, in SEAT ORDER — the arming push that makes every name on every screen
     * possible** (D-115).
     *
     * *The host learns names, and clients receive them wherever the interaction needs them.* Four
     * surfaces need them and not one of them worked: the ring's caller, the tally's outcome, the
     * couch's live ballot and the Surge picker all resolve a `Seat` against `PanelState.roster`,
     * and **nothing anywhere wrote that field.** It was null in every real round, so `FlowModel.nameOf`
     * returned null on every lookup and four screens drew the port's fixture names at a meeting
     * where six real people were standing in a hall.
     *
     * ### It discloses nothing, and that is provable rather than argued
     *
     * **Every phone in the lobby was already sent every name** — `LobbyBody.Standing.names`, D-115's
     * own widening, in arrival order. What this adds is the **order**, which is the one thing a
     * client cannot derive and must never guess: `Standing.names` is the order seats were taken and
     * its own KDoc forbids pairing it positionally with anything. So the disclosure is a permutation
     * of a list the phone is already holding, and the alternative — a client sorting the lobby's
     * names into what it hopes is seat order — is `ui` deciding who a Surge is fired at.
     *
     * ### One entry per seat, blank where a phone never said
     *
     * [LobbyBody.Standing.names]' rule, and here it is load-bearing rather than tidy: the client
     * reads this list **by index**, `roster[seat.index]`, in both directions — a seat to a name for
     * the tally, and a tapped row back to a seat for the Surge picker and the ballot. A list that
     * held only the seats that happened to have said a name would shift every index after the gap
     * and **fire an ability at somebody else**. A blank is a seat that said nothing; there is no
     * shorter list.
     *
     * **One slot per seat INDEX, and null where no seat sits** (D-274). A round need not be armed
     * with seats `0..n-1`: a guest who was away at LIGHTS OUT leaves a hole in the numbering, and a
     * list packed over the hole moved every later seat onto the next person's name — the house
     * restrained HERON while every screen said FINCH. So the list runs to the highest seat in the
     * round, and an index no seat holds is null rather than blank: a blank is somebody who gave no
     * name, a null is nobody, and the count of seats in the round is the count of non-nulls.
     *
     * ### It says nothing about which seat is reading it
     *
     * Byte-identical on all six phones, which is the property that keeps it safe: a phone still
     * cannot say which seat it is, and there is nothing here it could work that out from. It is
     * [StaticOpened]'s anonymity arriving at the one message that names everybody — the list is the
     * same list, and who is holding it is the socket's business.
     *
     * **The recording holds seats and no names**, exactly as `Event.RoundArmed.names` and
     * [InsidersRevealed] do (D-115: names are round-scoped and do not survive into the one artefact
     * of this game that outlives the evening). A replayed round rebuilds blanks of the same length.
     */
    data class RosterIssued(val seat: Seat, val roster: List<String?>) : Effect

    /**
     * **The home's plan, said once, at arming, to every seat, and every copy is the same plan**
     * (D-316).
     *
     * The Terminal and the couch draw the home with its counts, and a phone cannot draw a plan it
     * was never sent. So the plan goes to **every seat in the round, in seat order, at arming**,
     * rather than to the reader when it claims the station or to a seat when it goes out: either of
     * those would put one frame on one phone's socket at the moment that phone became the reader or
     * left the round, and the frame would say so. Sent to everybody at the one moment everybody is
     * sent the roster, it says nothing about the phone holding it (the owner's threat model,
     * 2026-11-20: what sits only inside a player's own phone is not a leak).
     *
     * [HomePlanView] is the field list: storeys, cells, room names and kinds, and no card, no
     * station and no seat, so which card is in which room (`Destination.kt`, the answer key to every
     * work order) cannot ride it. The rules read nothing from it to emit this; it is
     * `GameState.plan`, recorded on `Event.RoundArmed` since D-271.
     */
    data class PlanIssued(val seat: Seat, val plan: HomePlanView) : Effect

    /**
     * **This seat's contact seed, said once, at arming, to the one phone it belongs to** (D-212).
     *
     * The radio's key. The phone advertises `ContactTokens.of(seed, window)` and nothing else, so
     * the house — which can derive every seat's seed — can invert any token it is told about, and
     * nobody else can link one window's token to the next (D-032/D7).
     *
     * ### One per seat, every seat, whatever its role
     *
     * [RoleRevealed]'s shape and for its reason: a run of one per seat, grouped with the other
     * arming runs. **The seed is drawn from the round's seed and the seat index alone**, never from
     * the role, so a role-swapped round puts byte-identical seeds on every wire — the differential
     * harness finds nothing here, and `DifferentialTest` checks that it really finds nothing rather
     * than that the run is missing.
     *
     * ### Addressed to its seat, and that is half the guarantee
     *
     * A phone holding another seat's seed could follow that player by radio all round, which is the
     * leak D7 exists to close. Two living phones are one class, so the allowlist cannot stop seat 5
     * receiving seat 3's seed; [EmitSchema.audienceOf] does, and the row stops it reaching anybody
     * who is not living. Neither subsumes the other.
     *
     * Silent: it rides the arming burst, where a second buzz is F-003's tell.
     */
    data class ContactSeedIssued(val seat: Seat, val seed: ContactSeed) : Effect

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
        /**
         * **How long the talk runs, on the push that opens it — and null on every other phase**
         * (D-317). [voteWindowSeconds]' shape and its reason, one phase earlier: the DISCUSSION row
         * is the host's now, locked at arming ([Event.RoundArmed.discussionSeconds]), and a phone
         * draws the talk's clock from this rather than from its own ninety. Its own field rather
         * than a shared *this phase's window*, for the reason [voteWindowSeconds] gives.
         */
        val discussionSeconds: Int?,
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
     * **The result, to everyone — a participation count and an outcome, and nothing finer**
     * (`gdd.md:413`, `:1007`, D-075, and the owner's 2026-08-24 ruling that the living's result is
     * count-only).
     *
     * [restrained] null means the room Restrained nobody. **No role, no confirmation, no reveal** —
     * *a correct restraint and a catastrophic one look identical* (`gdd.md:1007`), and nothing here
     * has a field that could tell them apart.
     *
     * ### [voted] and [living] are the count, and they are the ONLY numbers the living get
     *
     * `6 OF 6 VOTED . MARCUS WAS RESTRAINED` is the whole of a living player's result. **There is
     * no per-candidate field here and there must never be one**: a per-candidate tally is the shape
     * of the room's thinking, published to the people still in it, and it survives the evening in
     * everybody's memory the way a count does not. The out get the ballot itself
     * ([MeetingResolved]) because they cannot act on it.
     *
     * **The pair discloses nothing new, and that is provable rather than argued.** It is
     * byte-identical to the [VoteProgressed] emitted in the same reduction one effect earlier —
     * same numerator, same denominator, same permission — so the living's wire carried both figures
     * already and this carries them again on the message the result screen is drawn from. The
     * screen used to compute them from a fixture, which is the state two numbers for one fact
     * always start in.
     */
    data class MeetingResult(
        val restrained: Seat?,
        val voted: Int,
        val living: Int,
        val haptic: Haptic,
    ) : Effect

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

    /**
     * **The Revoke landing, on the phone it landed on** (D-134, E-R2-5).
     *
     * *Newly Revoked: a screen tells them they are Revoked and to sit silently where it happened.*
     * That screen exists — `ScreenId.Revoked`, ported, drawn, with `PanelState.outBy` set on arrival
     * so it says REVOKED rather than RESTRAINED (rule 9) — and **nothing reached it.** The two ways
     * out of the game were asymmetric on the client: the room holding you pushed
     * [RestrainedTakeover], and the house withdrawing your lamp pushed nothing at all, so the
     * revoked player's phone kept drawing the round it was no longer in until a meeting was called.
     *
     * ### It is [RestrainedTakeover]'s shape, and that symmetry is the argument
     *
     * A house push per seat, addressed to the one seat it is about, carrying nothing but the fact
     * that it is addressed to them. The rejected alternative is the same one D-134's E1-1 rejected:
     * a client that noticed its own lamp go dark and drew a conclusion would be *the device deciding
     * a game answer* — and the answer it would be deciding is whether the player is still playing.
     *
     * ### It reaches the target and nobody else, least of all the actor
     *
     * *No actor-side feedback on firing any ability* (`gdd.md:398`). The Insider who armed the
     * Revoke is told [AbilityFired], one frame whether the contact landed on a live player, an
     * already-Revoked one, a fellow Insider or themselves — `Rules.contact` constructs that one
     * effect outside every branch and this one changes nothing about it. Since D-290 its one digit
     * says whether the Revoke landed ([ArmedTouch]), by the owner's ruling, which narrows
     * `gdd.md:398` for the armed phone's own tile and motor.
     *
     * ### It is constructed on EVERY contact, and the allowlist is what decides who gets one
     *
     * **This is the one thing about it that had to be got right, and the first attempt got it
     * wrong.** Emitted only when the contact landed, the authority's stream differs between a
     * Revoke that took and one that hit an already-Revoked seat — which is precisely the difference
     * `AbsentEffectIsTheLeakTest` exists to refuse, and it refused it.
     *
     * So it is built outside every branch in `Rules.contact`, exactly as [AbilityFired] is, and
     * **the narrowing happens at the emit boundary instead**: the row permits the kind to the two
     * OUT classes and to neither living one, so a contact that revoked nobody constructs this for a
     * seat that is still Live and the delivery is declined. [Surged]'s arrangement, inverted — *the
     * rules construct this identically whichever seat it names; the allowlist declines it at the
     * boundary; two independent denials and neither is visible as an absence.*
     *
     * Four cases and one shape. A contact that lands reaches a seat that is now Out. A contact on
     * an already-Revoked seat reaches a phone already standing on this screen and changes nothing
     * on it. A self-contact and an unarmed one both name a seat that is still Live and reach
     * nobody. **No client's stream differs between any two of them in its frames**: the same
     * effects, the same deliveries, the same lengths. Since D-290 two contents differ, each on one
     * of the touch's own two phones and each by the owner's ruling: the phone just Revoked is sent
     * [haptic] Long, and the armed phone's [AbilityFired] says whether its Revoke landed.
     *
     * ### [haptic]: Long on the touch that Revoked this phone, and nothing on any other (D-290, D-156)
     *
     * *The underlying message count never drives the buzz count.* A buzz on every one of these
     * would fire again every time anybody touched a player who was already out — a phone going off
     * in the hand of somebody sitting silently on a couch, in a dark room. So the re-assertion
     * carries `null`, as it always did.
     *
     * **The touch that put this phone out carries [Haptic.Long]** (the owner's 2026-11-18 ruling:
     * *the Revoked phone and the Insider's phone both give the Long buzz*). Its lamp is already defunct
     * in the player's hand by rule 7; the buzz is the motor saying the same thing. The rules set it
     * on the one seat the touch Revoked, so the only phone it ever reaches is a phone that has just
     * gone out, and it says nothing that phone's own dark lamp has not. Nothing else is possible:
     * a value other than `null` or `Long` cannot be constructed, and the wire carries it as one digit
     * so the frame stays the touch's one length (D-219).
     */
    data class RevokeLanded(val seat: Seat, val haptic: Haptic?) : Effect {
        init {
            require(haptic == null || haptic == Haptic.Long) { "a Revoke landing buzzes Long or not at all" }
        }
    }

    /**
     * **The house notice, at the top of the meeting: who was unreachable on purpose since the last
     * one** (D-035, D-187, D-223).
     *
     * D-187's notice and nothing else: it reports detectable deliberate evasion (locking the phone,
     * backgrounding the app, disabling a permission mid-round) and never a radio that failed. All
     * of them reach it as `Event.RadioFaulted` with `RadioCause.Person` or, for a permission switched
     * off, `RadioCause.Permission` (D-310): a radio switched off or its permission withdrawn (D-223),
     * and a phone that left the foreground mid-round, reported when it is back (D-229), the host's own
     * seat as any other (D-230). There is no second notice.
     *
     * Sent to every phone, living and out, at the moment the check-in gate closes, and **sent at
     * every meeting, empty when nobody evaded**, so its arrival says nothing; only its content
     * does, and the content is the same on every phone. **An empty one draws nothing** (the owner,
     * 2026-11-15): no *data is complete* line. Both lists are in seat order, each seat once, and
     * name no role: every phone reports an outage identically, so being named says whose phone went
     * quiet and nothing about who holds it.
     *
     * **Two lists, and no seat on both** (the owner's 2026-11-21 rulings, D-311):
     * - [unreachable]: the seats named that **only left the app** this shift (the side button, a
     *   swipe home, another app). They keep D-223's sentence, *was unreachable this shift*, unchanged,
     *   and it comes first.
     * - [switchedOff]: the seats named that **switched off something this game needs** this shift
     *   (`RadioCause.Permission`): a permission withdrawn in Settings, or the Bluetooth radio itself
     *   turned off (Control Centre, airplane mode). They are named in the owner's permission sentence,
     *   which carries the warning. A seat that both left and switched off is here alone, once.
     *
     * The same on every phone, so the phones draw the same words. Issuing a notice with [switchedOff]
     * not empty gives the round's one warning ([GameState.warned]); after that, anything switched off
     * closes the house ([HouseClosedOnPermission]).
     */
    data class NoticeIssued(val unreachable: List<Seat>, val switchedOff: List<Seat>) : Effect

    /**
     * Lights out. The meeting is over, and D-135's fifth long haptic rides it.
     *
     * [revokeCooldown] is [OpeningMessage.revokeCooldown] again (D-317): a meeting's end puts every
     * Revoke clock back at half (D-184), so the push that says so carries what it is half of. The
     * same number to every seat.
     */
    data class MeetingEnded(val haptic: Haptic, val revokeCooldown: Long) : Effect

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
        /**
         * **Both nodes, in draw order, as places rather than as card ids** (`gdd.md:363`, `:374`).
         *
         * *The System Integrity widget becomes the Egress countdown, and names the two nodes* —
         * **names**, and a nine-character card id is not a name. It carried [MarkerId] and the client
         * had nothing to turn one into a room with, so the alert and the widget drew the port's
         * UTILITY and LANDING in a house where the house had drawn two other rooms.
         *
         * [Destination] is the type that already answers *where is this card* — card, glyph and the
         * host's own name for the room — and it is what a work order's lines and an Isolate list
         * already carry. **Nothing about these two markers is narrowed by sending less**: D-150's
         * amendment to F-002 turns on the fact that *the two nodes are already named to every phone
         * in the house*, which is why the exclusion from Isolate's list leaks nothing. The glyph
         * rides with the room for the reason it rides on a work order — two people who may not speak
         * have to find one specific card in a dark house — and it is a property of two cards
         * everybody has already been told about.
         *
         * **This is not the home's geography.** A client receives these two rows and its own order's
         * rows and nothing else; the whole map on every phone remains the thing [Destination]'s KDoc
         * refuses.
         */
        val nodes: List<Destination>,
        /** Ticks on the clock at the moment it started. The device holds no opinion of its own. */
        val remaining: Long,
        val haptic: Haptic,
        /**
         * **The Sync Pulse's grid: the first pulse, in the host's monotonic milliseconds** (C2).
         *
         * The same number on every phone, whoever fired and whatever their role, and a phone that has
         * measured its offset to the host's clock (C1) can put each pulse at one moment of real time
         * in every hand. Its width follows the host's uptime, never the seat.
         */
        val pulseFrom: Long,
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
     * [held] says whether this seat's run was good, four in a row (D-298): the house now holds it
     * for somebody at the other node, or it paired and contained. It is `false` for a beat that missed the schedule, for a seat serving a lockout,
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
    data class SyncPulseAnswered(val seat: Seat, val held: Boolean) : Effect {
        /**
         * **The lockout's vibration on a failed attempt, and nothing on a good run** (the owner's
         * O4, D-298). Read off [held] and nothing else, so every failure, a missed run, a locked
         * seat, a seat the house does not place at a node, feels the same in the hand, and the
         * frame is the one digit it always was.
         */
        val haptic: Haptic? get() = if (held) null else Haptic.Lockout
    }

    /**
     * **Somebody's Sync Pulse was answered at this moment, and this seat is sent a frame of the
     * answer's length** (C2, D-263's pattern).
     *
     * [SyncPulseAnswered] reaches the seat that tapped and nobody else, so each return put one
     * frame on that phone's socket and none on anybody else's: on a Wi-Fi that can be measured, the
     * one phone that got a frame was the one standing at a node with its taps in. The request
     * already rides the fixed-cadence upload (D-263), so the downward frame was the whole tell. The
     * rules now build one of these for **every other seat**, seat by seat at the same place, and the
     * row sends it to both living classes, exactly as [SyncPulseAnswered]'s is: every living phone
     * is sent one frame of one length, the tapper its answer and the rest this.
     *
     * Built without a read of any role. It carries the seat and nothing else, and the seat comes off
     * on the wire. Nothing reads it on a phone.
     */
    data class SyncPulseBlanked(val seat: Seat) : Effect

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
        /**
         * **The final System Integrity, as the percentage every screen draws** (the owner's
         * 2026-11-18 ruling, decision pack 9b option a).
         *
         * The living are told the meter only at meetings (`gdd.md:192`, `:1002`), so a round that
         * ended before any meeting (parity at the first Revoke, at three seats) put SYSTEM
         * INTEGRITY "–" on every ending. One value, the same to every phone and every seat, from
         * the same `Balance.integrityPercent` the batch and the couch's live reading use: a
         * percentage and never the count, for D-153's reason, which holds at the ending too.
         */
        val integrity: Int,
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

    /**
     * **THE HOUSE IS CLOSED: a permission was switched off again, and everyone starts over** (the
     * owner's 2026-11-20 rulings, D-310).
     *
     * The one effect `Event.HouseClosedOnPermission` emits, to every seat, once: the living, the
     * out, the Insiders and the host's own phone alike, in the same bytes. **It carries nothing**:
     * not the seat whose report closed the house (the room heard that seat named with the warning at
     * the last meeting, and whose second switch it was is the house's log's, never a screen's), not
     * a role, not a count. Its arrival is the whole message, and the words are the phone's, the
     * owner's two lines, the same on every screen.
     *
     * The round is over when it is sent ([GameState.closed]), so it is permitted to the two classes
     * of a round that has ended, as the ending's own rows are. Each phone then goes back to JOIN A
     * HOME, and the host's to its lobby: that difference is the ruling's, and it is drawn by whether
     * the phone is hosting, which every phone in the room can see, never by a role.
     *
     * **No haptic and no light.** The owner's words are shown in the light the screen each phone
     * lands on already has; nothing here buzzes or sets a lamp.
     */
    data object HouseClosedOnPermission : Effect
}
