package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.JamRings
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MalformedSubroutineParameters
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.ReplayPattern
import home.someoneshome.model.ShortHold
import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage

/**
 * **The design's roster of ten, as data rather than as ten strings typed onto screens.**
 *
 * The roster (`gdd.md:565`) gives every Subroutine a structure, a mode and a light signature, and
 * three separate surfaces already draw pieces of it — the work order, the springboard widget, the
 * Subroutine screen. Before this existed those values were literals sitting next to one another
 * and agreeing by hand, which is the arrangement D-106 already lost once: a surface stops carrying
 * the value and the absence looks exactly like a Subroutine nobody rated.
 *
 * ### Modes, kept in the KDoc because nothing draws them
 *
 * | # | Subroutine | Structure | Mode | What you do | Light |
 * |---|---|---|---|---|---|
 * | 1 | Replay | Short | Sequence memory | 3–5 dots flash in order; tap them back | Bright |
 * | 2 | Interrupt | Short | Timing | A sweep bounces; catch it inside the band (D-139) | Medium |
 * | 3 | Parity Check | Short | Visual search | Grid of filled/empty cells; tap the one breaking the pattern | Bright |
 * | 4 | Sniff | Short | Magnitude | Two buzzed groups; say which was bigger (D-137) | Dark |
 * | 5 | Deallocate | Short | Counting | A tap removes a dot; level is the shortest column (D-138) | Bright |
 * | 6 | Drift | Medium | Tracking under occlusion | A buzz asks where the hidden dot is (D-140) | Medium |
 * | 7 | Short | Short | Gross motor | Hold N fingers on the screen for two seconds | Dark |
 * | 8 | Signal Trace | Medium | Pathfinding | Tap node-to-node from one end to the other (D-211) | Medium |
 * | 9 | Jam | Medium | Convergence | Tap +/− until two shapes overlap | Medium |
 * | 10 | Handshake | Medium | Haptic echo | The phone buzzes a pattern; you tap it back | Dark |
 *
 * **Rows 2, 4, 5 and 6 are revision 31's, not the roster's.** `gdd.md:566`, `:568`, `:569` and
 * `:570` are superseded by D-137 to D-140 and the table above carries the rulings rather than the
 * lines they replaced — *"the phone buzzes N times; tap N"* was two Subroutines and both were
 * wrong, *"tap to even them out"* did not say what a tap does, *"a slow bar sweeps"* left a static
 * bar with no moment to tap, and *"tap where it is now"* never said whose *now* it was.
 *
 * ### The ten, and not the two structured ones
 *
 * Memory Dump (long) and Array Wipe (circuit) are the design's *two structured pieces of work*,
 * described in their own section and rated for light nowhere. They are deliberately absent here:
 * a roster that quietly acquired two rows the design never rated would be inventing light values
 * under cover of being a transcription. Array Wipe still appears on the work order as its own row,
 * with the signature this port chose written where it was chosen and flagged for a ruling.
 *
 * [screen] is null for a Subroutine whose interaction has not been built. **Null is the honest
 * state, not a placeholder to be filled with the nearest screen** — pointing an unbuilt Subroutine
 * at a built one's screen would put a player through the wrong work and look like a routing bug
 * rather than a missing feature.
 *
 * ### [kind] is the only thing the house sends, and the rest of this table is the client's (D-112)
 *
 * In v1 the light level is **fixed per Subroutine kind** and the client holds the roster, so a
 * work order arrives as a list of kinds and every screen looks the label and the signature up
 * here. No field arrives on the wire, no effect grows a member and the redaction schema gains no
 * row — per-assignment variety is deferred to v2 rather than left ambiguous, because it is the
 * wire that costs something and nothing in the design needs that wire yet.
 */
enum class Subroutine(
    val label: String,
    val tier: SubroutineTier,
    val light: LightSignature,
    /** What the house calls this one. The one field of this table that crosses the boundary. */
    val kind: SubroutineKind,
    val screen: ScreenId? = null,
    /**
     * **What to do, read at the card, before the work opens — and it is the only place any of the
     * ten says it** (D-188).
     *
     * ### The pattern is inverted, and Sniff is what made the right one visible
     *
     * E-S1-1 closed this as a *placement* for Sniff alone: D-137 makes Sniff the roster's only
     * *short dark*, the treatment takes *fully dark* literally — every colour on the screen is
     * [Amber.Black] until the player has answered — and a sentence drawn there is black ink on
     * black glass, which is not an instruction, it is a layout. The other nine were left null with
     * the note that briefing them all at the card was a copy call nobody had made. **D-188 makes
     * it, the other way from the way it was framed.**
     *
     * **The argument is the light signature.** Every Subroutine is rated bright, medium or dark
     * ([light], D-112, D-113) and the rating is a promise about how much light you are standing in
     * while you work. An in-screen instruction line is lit pixels for the *whole duration* of the
     * task, on a screen whose entire design constraint is how little it may glow — it makes a dark
     * Subroutine less dark and a bright one longer. **Reading happens at the card, where the scan
     * has already lit you up and the cost is already paid**, and then the task is as dark as it was
     * rated to be. This is D-175's accounting about the Tier-2 pickers in the opposite direction:
     * there reading is a price worth charging; here it is a price already charged, and charging it
     * twice is the mistake.
     *
     * **A misread costs the attempt and a rescan**, which is the intended severity rather than an
     * accepted consequence. D-110 already rules one attempt per scan with the walk back as the
     * cost; tying comprehension to the same gate means the player who skims in the dark pays what
     * the player who fumbles pays, and there is one recovery path in the game rather than two.
     *
     * ### COPY: all ten are canon (the owner's 2026-08-24 ruling)
     *
     * **Sniff's always stood** and is the register the other nine were written against: second
     * person, imperative, **what the phone will do and then what you do about it**, two lines, no
     * nouns from the fiction. The nine were drafted here and carried a flag for two revisions; the
     * owner read them and **confirmed all nine as written**, so they are the game's words now and
     * changing one is a copy decision like any other rather than filling in a blank.
     *
     * **Non-null for all ten, and it must stay that way.** A nullable field here is how nine of
     * them came to say nothing at the card in the first place; total over the roster means a new
     * Subroutine cannot be added without somebody writing the sentence a player reads before a
     * screen they may not be able to read.
     */
    val briefing: String,
) {
    // The ten briefings, in roster order. Each is two lines: what the phone will do, then what you
    // do about it. All ten are canon (the owner's 2026-08-24 ruling); Sniff's is the one that was
    // already read at a card before the other nine were written.
    Replay(
        "REPLAY", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.Replay,
        ScreenId.SubReplay,
        briefing = "DOTS WILL FLASH IN ORDER.\nTAP THEM BACK IN THAT ORDER.",
    ),
    Interrupt(
        "INTERRUPT", SubroutineTier.Short, LightSignature.Medium, SubroutineKind.Interrupt,
        ScreenId.SubInterrupt,
        briefing = "A SWEEP WILL RUN BACK AND FORTH.\nTAP WHILE IT IS INSIDE THE BAND.",
    ),
    ParityCheck(
        "PARITY CHECK", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.ParityCheck,
        ScreenId.SubParity,
        briefing = "A GRID WILL FILL IN A PATTERN.\nTAP THE ONE BLOCK THAT BREAKS IT.",
    ),
    Sniff(
        "SNIFF", SubroutineTier.Short, LightSignature.Dark, SubroutineKind.Sniff, ScreenId.SubSniff,
        // The line the other nine are written against. Its screen emits nothing, which is how the
        // card became the place a briefing is read at all — see [briefing].
        briefing = "THE PHONE WILL BUZZ TWICE.\nTAP THE SIDE THAT BUZZED MORE.",
    ),
    Deallocate(
        "DEALLOCATE", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.Deallocate,
        ScreenId.SubDeallocate,
        briefing = "THE COLUMNS WILL STAND UNEVEN.\nTAP THEM DOWN TO THE SHORTEST.",
    ),
    Drift(
        "DRIFT", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.Drift,
        ScreenId.SubDrift,
        briefing = "A DOT WILL MOVE, THEN HIDE.\nWHEN THE PHONE BUZZES, TAP WHERE IT IS.",
    ),
    Short(
        "SHORT", SubroutineTier.Short, LightSignature.Dark, SubroutineKind.Short, ScreenId.SubShort,
        briefing = "THE PHONE WILL ASK FOR FINGERS.\nHOLD THAT MANY UNTIL IT RETURNS.",
    ),
    SignalTrace(
        "SIGNAL TRACE", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.SignalTrace,
        ScreenId.SubTrace,
        briefing = "TWO ENDS WILL BE MARKED.\nTAP NODE TO NODE FROM ONE TO THE OTHER.",
    ),
    Jam(
        "JAM", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.Jam, ScreenId.SubJam,
        briefing = "TWO RINGS WILL SIT APART.\nTAP UNTIL THEY SIT ON ONE ANOTHER.",
    ),
    Handshake(
        "HANDSHAKE", SubroutineTier.Medium, LightSignature.Dark, SubroutineKind.Handshake,
        ScreenId.SubHandshake,
        briefing = "THE PHONE WILL BUZZ A RHYTHM.\nTAP IT BACK BY FEEL.",
    );

    companion object {

        /** The Subroutine a screen belongs to, or null where the screen is not one of theirs. */
        fun on(screen: ScreenId): Subroutine? = entries.firstOrNull { it.screen == screen }

        /**
         * **The roster row the house named.** Total over [SubroutineKind] by construction — every
         * kind has exactly one row here, which `SubroutineRosterTest` holds in both directions.
         *
         * Total rather than nullable on purpose: a work order arriving with a kind this client
         * could not resolve would leave a blank line on the one screen a player navigates a dark
         * house by, and a blank line is indistinguishable from the blocked entry that is supposed
         * to be there (D-114).
         */
        fun of(kind: SubroutineKind): Subroutine = entries.first { it.kind == kind }

        /** The ones with an interaction behind them, in roster order. */
        val built: List<Subroutine> get() = entries.filter { it.screen != null }
    }
}

/**
 * How a Subroutine is scheduled and where it sits in space — the design's *structures*.
 *
 * The roster's ten are all Short or Medium. [Long] and [Circuit] are the two structured pieces of
 * work, and are here so that a tier is a closed set rather than the subset that happens to be on
 * the roster: a Subroutine assigned per the design's 1 circuit / 1 long / 3 medium / 2 short mix
 * has to be describable, and a tier list missing two of the four cannot describe one.
 */
enum class SubroutineTier { Short, Medium, Long, Circuit }

/**
 * **What one phone has put into one Subroutine — six shapes of input, one shape of promise.**
 *
 * Every entry in this file keeps the player's own taps, holds, presses or route, and **none of
 * them holds what the house asked for**, so no screen reading one can say whether the player was
 * right. That is rule 3's discipline turned on adjudication rather than on redaction: the way to
 * stop a type answering a question is to build a type the question cannot be asked of.
 *
 * The interface exists so [SubroutineModel] can restart them and so a guard can walk them. It
 * carries **no verdict, no target and no answer**, and it must never acquire one — the two
 * properties on it are both facts about this phone's own hand.
 */
sealed interface SubroutineEntry {

    /** True once this phone has put anything at all into it. */
    val touched: Boolean

    /** True once what this phone entered has gone to the house. Nothing here answers it. */
    val gone: Boolean

    /**
     * **What this phone entered, in the one shape the wire has** — `List<Int>`, in the order the
     * fingers landed (E-P2-1).
     *
     * Ten Subroutines have ten vocabularies — an element, a cell, how many fingers are on the
     * glass, a signed step, a node, which group was bigger, a column, where a sweep was — and the
     * house asks for one thing from all of them, because `Event.SubroutineReturned.entered` is one
     * field. **This is that projection, and it lives on the interface so that a Subroutine cannot
     * be built without one**: an entry with a screen and no way to reach the house is E-P2-1
     * exactly, and it was found by counting rather than by anybody noticing a control do nothing.
     *
     * **A List and not a Set.** Order is the answer in half of the ten, and this is recorded input.
     *
     * **Empty until there is something to send, and empty is not a hand-over.** What decides that a
     * hand-over happened is [gone]; this says what went. A screen that read an empty list as *the
     * player answered nothing* would be adjudicating, which is the one thing this whole file's
     * types are shaped to make impossible.
     */
    val entered: List<Int>

    /** The marker was scanned again: a fresh instance of the same Subroutine. */
    fun restart()
}

/**
 * **What the player has entered, and it is physically incapable of holding the answer.**
 *
 * A Subroutine's pattern arrives as an Effect, the screen displays it, captures taps and echoes
 * them locally, and the sequence returns as an Intent that the *server* verifies (D-042). The
 * verification is not a round trip this type is saving — it is a thing this type must not be able
 * to do at all, and the way to make that true is rule 3's: construct a narrower type rather than
 * add a comment. There is no pattern field here to compare against, so no screen reading this can
 * tell a player whether they were right, and no later change can make one without deleting this
 * paragraph first.
 *
 * ### Why it matters more here than anywhere else
 *
 * An Insider's Subroutines are fakes: real UI, real progress, real completion, writing nothing
 * (rule 8). The fake is only indistinguishable while *nothing on the device adjudicates* — the
 * moment a screen decides for itself that a sequence was correct, the two roles need two
 * behaviours, and the difference is on a lit screen in a dark room. Keeping the judgement on the
 * authority is what makes one screen serve both roles rather than two screens that have to be
 * kept in step.
 *
 * D-043 is the one documented exception to server authority — the motion budget, which accumulates
 * client-side because 100 Hz cannot round-trip. It is documented precisely so exceptions do not
 * breed, and this is not a second one.
 *
 * ### Handing over is automatic, because there is nothing left to change
 *
 * A sequence ends when its last element is entered: the player has said everything they have to
 * say and a confirmation step would be a second gesture in the dark for no decision. That is the
 * opposite of [ChoiceEntry], where the one answer can be changed right up until it is handed over
 * — the vote's shape, and for the vote's reason.
 */
class SequenceEntry(length: Int) : SubroutineEntry {

    /**
     * How many elements the house asked for — **the one number any entry in this file is told,
     * and the only one that has to be.**
     *
     * A sequence has no other way to know it is over: the player stops when they have said as many
     * things as they were asked for, so the count is structure rather than answer. [HoldEntry],
     * [ScalarEntry] and [PathEntry] are told nothing at all, because a hold ends on a clock, a
     * converging shape has no finishing line, and a route ends when the player says it does.
     *
     * **The house's on a housed phone, and the port's fixture on a bench** (D-238). Replay's dots
     * and Handshake's beats are drawn per scan, so the count arrives with the question and
     * [resize] is how it lands. It was a constructor `val` fixed at the port's four and five, and a
     * five-dot Replay then took four taps and handed the house a sequence one short of its own.
     */
    var length: Int by mutableStateOf(length)
        private set

    /** The house dealt a question of [to] elements. Cleared, because it is a different question. */
    internal fun resize(to: Int) {
        length = to
        restart()
    }

    /**
     * Every element the player has entered, in the order they entered it.
     *
     * Kept in full rather than as a count, because the echo is per-element: the dot you touched
     * lights, and which dots you touched is the only thing on screen that is yours.
     */
    override var entered: List<Int> by mutableStateOf(emptyList())
        private set

    /** True once the sequence has gone to the house. Nothing on this device answers it. */
    var handedOver: Boolean by mutableStateOf(false)
        private set

    override val touched: Boolean get() = entered.isNotEmpty()

    override val gone: Boolean get() = handedOver

    val complete: Boolean get() = entered.size >= length

    /** How many are still owed. Drawn as cells rather than as a numeral — see [ReplayScreen]. */
    val remaining: Int get() = (length - entered.size).coerceAtLeast(0)

    /**
     * A tap on element [at].
     *
     * Refused once the sequence has been handed over — not because a further tap would be wrong,
     * but because there is nothing left on this device for it to change, and a control that
     * appears to accept input it discards is worse than one that does not move.
     */
    fun enter(at: Int) {
        if (handedOver) return
        entered = entered + at
        if (complete) handedOver = true
    }

    /** Whether [at] is one of the elements this phone has entered. */
    fun holds(at: Int): Boolean = at in entered

    override fun restart() {
        entered = emptyList()
        handedOver = false
    }
}

/**
 * **One answer, changeable until it is handed over** — [SequenceEntry]'s sibling, and the same
 * refusal to hold the answer.
 *
 * The vote's shape exactly ([MeetingModel.choice] and [MeetingModel.handedOver]), because it is
 * the same situation: a single selection, made in the dark, by someone who may have to change it.
 * A tap that went straight to the house would make a mis-touch final in a game whose input
 * vocabulary is explicitly *no twitch timing, no precise dragging*, and whose players are standing
 * up, one-handed, unable to speak.
 *
 * [handedOver] is what this phone sent, not what the house received, and [locked] compares the two
 * rather than being a flag — a flag would read SUBMITTED over a cell the house has never heard of.
 */
class ChoiceEntry : SubroutineEntry {

    var choice: Int? by mutableStateOf(null)
        private set

    var handedOver: Int? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = choice != null

    override val gone: Boolean get() = handedOver != null

    /** The one cell, as a list of one. [handedOver] and not [choice]: what went, not what is lit. */
    override val entered: List<Int> get() = listOfNotNull(handedOver)

    /** A tap on a cell. Re-tapping the held one changes nothing; a different one moves the mark. */
    fun choose(at: Int) {
        if (handedOver != null) return
        choice = at
    }

    /** SUBMIT. Refused with nothing chosen, rather than quietly handing over an empty answer. */
    fun handOver() {
        if (choice != null) handedOver = choice
    }

    fun holds(at: Int): Boolean = choice == at

    val locked: Boolean get() = choice != null && choice == handedOver

    override fun restart() {
        choice = null
        handedOver = null
    }
}

/**
 * **A hold, and the count of fingers that made it — Short's entry, and the emptiest type here.**
 *
 * The design's Short is *hold N fingers on the screen for two seconds*, the roster's other short
 * dark, and the one Subroutine that commits both hands to the glass. This entry is told **nothing
 * about N.** It keeps how many fingers this phone currently has down, and — once the hold has run
 * its two seconds — how many were down when it went. Whether that was the right number is the
 * house's answer and there is nothing here that could form an opinion about it.
 *
 * ### The hold ends on the clock, never on the count
 *
 * A hold that completed *when the right number of fingers arrived* would be the phone grading the
 * answer, and it would grade it out loud: an Insider's fake would end at a different moment from a
 * Resident's real one, in the dark, on the screen. So the hold runs for [HOLD_MILLIS] and hands
 * over whatever was on the glass — one finger, four, the wrong four. Exactly the property
 * [SequenceEntry] has, which is that the entry progresses identically whatever was entered.
 *
 * ### After it has gone, the cells hold what went
 *
 * [press] is refused once the entry is handed over, so lifting your hand afterwards does not drain
 * the echo back to nothing. What is drawn is then *what this phone sent*, which is the honest
 * thing for it to be showing while the house has not answered — a count falling to zero would read
 * as the entry being withdrawn.
 */
class HoldEntry : SubroutineEntry {

    /** How many fingers are on the glass right now. The echo, and the whole of it. */
    var fingers: Int by mutableStateOf(0)
        private set

    /** How many were down when the hold completed, or null while nothing has gone. */
    var handedOver: Int? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = fingers > 0 || handedOver != null

    override val gone: Boolean get() = handedOver != null

    /** How many fingers were down when the hold completed. Never the live count — see [press]. */
    override val entered: List<Int> get() = listOfNotNull(handedOver)

    /** The pointer count changed. Negative counts are impossible and are floored rather than trusted. */
    fun press(count: Int) {
        if (handedOver != null) return
        fingers = count.coerceAtLeast(0)
    }

    /**
     * Two seconds of an unchanging hold have passed.
     *
     * Refused with an empty glass: a hold nobody was making did not happen, and a phone in a
     * pocket must not report one.
     */
    fun handOver() {
        if (handedOver == null && fingers > 0) handedOver = fingers
    }

    override fun restart() {
        fingers = 0
        handedOver = null
    }
}

/**
 * **One number the player walks up and down — Jam's entry, and it does not know where it started.**
 *
 * The design's Jam is *tap +/− until two shapes overlap; slow, forgiving, satisfying*. What this
 * keeps is [offset]: the net number of steps the player has pressed, signed, from wherever the
 * house opened the Subroutine. **Not the size of anything, and not the distance to anything.** The
 * shape on screen is drawn from an opening value the screen holds plus this offset; where the
 * *other* shape sits is a separate drawn value, and nothing in this type can be subtracted from
 * anything to find out how far apart they are.
 *
 * That is the same move [ParityGrid] makes. The answer is not secret — both shapes are on the
 * screen and closing the gap is the entire work — but the *judgement* is not the phone's.
 *
 * ### SUBMIT is live from the first frame, and that is a deliberate refusal
 *
 * A SUBMIT that stayed inert until the player had pressed something would be the phone saying *the
 * opening position is not the answer*. It is not the phone's place to know, so the control is live
 * with nothing pressed and an unmoved shape can be handed over.
 *
 * [reach] is how far the shape may travel before it stops, which exists so a shape cannot be
 * driven off the screen. It is deliberately far outside anything a player would aim at: a limit
 * that bit near the target would be the device fencing the answer.
 */
class ScalarEntry(val reach: Int) : SubroutineEntry {

    /** Net steps pressed, signed. Zero is where the house opened it, not where the answer is. */
    var offset: Int by mutableStateOf(0)
        private set

    /** How many presses have landed. The echo's own count, and the reason a return is *touched*. */
    var presses: Int by mutableStateOf(0)
        private set

    var handedOver: Int? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = presses > 0 || handedOver != null

    override val gone: Boolean get() = handedOver != null

    /** The offset that went, signed. **Not [presses]** — how hard somebody worked is not the answer. */
    override val entered: List<Int> get() = listOfNotNull(handedOver)

    /** One press of + or −. [by] is the step, signed, and is the whole vocabulary of this screen. */
    fun step(by: Int) {
        if (handedOver != null || by == 0) return
        presses++
        offset = (offset + by).coerceIn(-reach, reach)
    }

    /** SUBMIT. Accepted with nothing pressed — see the note above about what refusing would say. */
    fun handOver() {
        if (handedOver == null) handedOver = offset
    }

    val locked: Boolean get() = handedOver != null && handedOver == offset

    override fun restart() {
        offset = 0
        presses = 0
        handedOver = null
    }
}

/**
 * **The route a finger walked — Signal Trace's entry, and it accepts moves the graph forbids.**
 *
 * The design's Signal Trace is *tap node-to-node from one end to the other through a small graph*,
 * either end first (D-211). This keeps the nodes tapped, in order, and **does not check that
 * consecutive ones are joined by an edge.** That is rule 1 stated as a type: *never early-return on invalid in a client-visible
 * path — the absent effect is the leak.* A screen that refused a tap on an unconnected node would
 * be telling the player their move was illegal, which is the house's answer arriving from the
 * phone; and worse, it would be the only Subroutine in the game where the device says anything at
 * all about whether the work is going well.
 *
 * So every tap lands. The route goes to the house and the house decides whether it is a route.
 *
 * ### Tapping a node you already walked steps back to it
 *
 * The one editing gesture, and it is the physical one: a route retraced is a route shortened. It
 * is not a correction the phone is offering — the phone has no idea which end of the route is the
 * mistake — it is the player walking backwards. Tapping the node you are standing on truncates to
 * itself and therefore changes nothing, which is the right amount of nothing for a mis-touch.
 *
 * Handed over by SUBMIT rather than by arriving anywhere. A route can still change after it
 * reaches the sink — you may want a different one — so the rule *an entry goes when it can no
 * longer change* puts a button on it, exactly as it does on [ChoiceEntry].
 */
class PathEntry : SubroutineEntry {

    /** The nodes tapped, in order, with no claim that consecutive ones are joined. */
    var walked: List<Int> by mutableStateOf(emptyList())
        private set

    var handedOver: List<Int>? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = walked.isNotEmpty()

    override val gone: Boolean get() = handedOver != null

    /** The route that went, in order. [handedOver] and not [walked]: a route can change until SUBMIT. */
    override val entered: List<Int> get() = handedOver.orEmpty()

    /** A tap on node [to]: it extends the route, or steps back to it if the route already holds it. */
    fun walk(to: Int) {
        if (handedOver != null) return
        val already = walked.indexOf(to)
        walked = if (already >= 0) walked.take(already + 1) else walked + to
    }

    /** SUBMIT. Refused with an empty route, the same refusal LOCK IN makes on an empty ballot. */
    fun handOver() {
        if (handedOver == null && walked.isNotEmpty()) handedOver = walked
    }

    fun holds(at: Int): Boolean = at in walked

    val locked: Boolean get() = handedOver != null && handedOver == walked

    override fun restart() {
        walked = emptyList()
        handedOver = null
    }
}

/**
 * **What this phone took off each column — Deallocate's entry, and it has never seen the columns.**
 *
 * D-138: *a tap removes one dot from the tapped column, and evening out means bringing every column
 * down to the shortest.* What this keeps is [removed]: how many dots this phone has taken off each
 * column, in column order. **Not the heights, and not the distribution the house drew.**
 *
 * ### It is told the column COUNT and nothing else, and the difference is the whole answer
 *
 * The required entry is the sum of each column's excess over the shortest, so **the shortest column
 * is the answer key** — and an entry handed the heights could find it with one `min()`. That is
 * [HoldEntry]'s ruling one Subroutine along: `SHORT_FINGERS` lives on [SubroutineModel] and not on
 * the entry, because an entry that knew the ask could subtract it from what it holds. Here the ask
 * is a whole list and the subtraction is a one-liner, which makes the narrowing worth more, not
 * less.
 *
 * A count of columns is structure rather than answer, exactly as [SequenceEntry.length] is: there
 * have to be that many places a finger can land, and knowing how many says nothing about how tall
 * any of them was.
 *
 * ### Over-taps land, because a column that refused to go below level would be a verdict
 *
 * D-138 is explicit — *over-taps are the player's to make, columns can go below level, and the
 * house rejects a wrong final state on hand-over.* Nothing here compares a removal with anything,
 * so a column can be taken past level, and past it again. D-125's clamp rule is the reason: *clamp
 * only what players cannot perceive*, and a column's height is the one thing on this screen a
 * player can.
 *
 * What the *screen* will not do is take a dot off a column that has none — see [DeallocateScreen],
 * where that refusal is the picture's rather than the answer's.
 *
 * ### Sent by SUBMIT, and it is live from the first frame
 *
 * A removal cannot be undone, but another one can always be made, so the entry can still change and
 * the rule *an entry goes when it can no longer change* puts a button on it. The button is live
 * with nothing removed for [ScalarEntry]'s reason: an inert SUBMIT would be the phone saying *the
 * distribution you were dealt is not already level*, which is a thing it cannot know.
 */
class ColumnEntry(columns: Int) : SubroutineEntry {

    /**
     * How many places a finger can land. Structure, not answer — see the note above.
     *
     * The house's on a housed phone (D-238): the column count is drawn per scan, and [resize] is
     * how it lands, for [SequenceEntry.length]'s reason.
     */
    var columns: Int by mutableStateOf(columns)
        private set

    /** The house dealt [to] columns. Cleared, because it is a different question. */
    internal fun resize(to: Int) {
        columns = to
        restart()
    }

    /** How many dots this phone has taken off each column, in column order. */
    var removed: List<Int> by mutableStateOf(List(columns) { 0 })
        private set

    var handedOver: List<Int>? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = removed.any { it > 0 } || handedOver != null

    override val gone: Boolean get() = handedOver != null

    /** What came off each column, in column order — the order IS the answer here. */
    override val entered: List<Int> get() = handedOver.orEmpty()

    /**
     * One dot off column [at].
     *
     * A column index this entry does not have is ignored rather than throwing: nothing on a drawn
     * screen can produce one, and a `when` that threw would turn a routing mistake into a defunct
     * phone in a dark home (rule 6).
     */
    fun remove(at: Int) {
        if (handedOver != null || at !in removed.indices) return
        removed = removed.mapIndexed { column, count -> if (column == at) count + 1 else count }
    }

    /** How many dots this phone has taken off column [at]. The echo, and the whole of it. */
    fun taken(at: Int): Int = removed.getOrElse(at) { 0 }

    /** SUBMIT. Accepted with nothing removed — see the note above about what refusing would say. */
    fun handOver() {
        if (handedOver == null) handedOver = removed
    }

    val locked: Boolean get() = handedOver != null && handedOver == removed

    override fun restart() {
        removed = List(columns) { 0 }
        handedOver = null
    }
}

/**
 * **What this phone has entered into the Subroutine it has open.**
 *
 * Sits beside [PanelState] with [HomeEditorModel], [SavedHomesModel], [LobbyModel] and
 * [MeetingModel], for the reason they all do: `PanelState` is flat, inert, and every field of it
 * arrives already decided at the effect boundary. A finger landing on a dot is none of those.
 *
 * **One field per built Subroutine, deliberately.** A map keyed on [Subroutine] would let a
 * Subroutine be built without anybody noticing that its entry was never given a length; a field
 * has to be written, and shows up in a diff next to the screen it belongs to. It is also the
 * shape rule 8 asks for — *every Subroutine ships with its fake, in the same change* — read as a
 * thing you can see: six screens, six entries, and no role anywhere in this file.
 *
 * ### The dispatch is here, and there is exactly one of it
 *
 * [tap] and [handOver] are the only places that turn a [Subroutine] into the entry behind it.
 * `FlowModel` delegates to them and so does every test, because the second copy of that `when` is
 * where a Subroutine gets built with a screen, a light and a roster row — and no wiring. It is
 * checked: `SubroutineTest` walks [Subroutine.built] and fails on any of them whose tap reaches
 * nothing.
 *
 * ### There is no `isFake` here, and there must never be one
 *
 * An Insider's Subroutine is a fake: real UI, real progress, real completion, writing nothing. The
 * only place that distinction exists is the authority's ledger. Nothing in `ui` is told which it
 * is holding, so there is no branch to get wrong, no second code path to keep in step, and no
 * screen state that can only be reached by one role. `SubroutineParityTest` drives both roles
 * through the identical input and compares the rendered pixels, which is the claim stated as
 * something that can fail.
 */
class SubroutineModel(
    val handshake: SequenceEntry = SequenceEntry(HANDSHAKE_BEATS),
    val replay: SequenceEntry = SequenceEntry(REPLAY_DOTS),
    val parity: ChoiceEntry = ChoiceEntry(),
    val short: HoldEntry = HoldEntry(),
    val trace: PathEntry = PathEntry(),
    val jam: ScalarEntry = ScalarEntry(JAM_REACH),
    /**
     * **Sniff's answer is a [ChoiceEntry], and reusing it is the point.**
     *
     * *Which group was bigger* is one selection out of two, made in the dark, by somebody who may
     * have to change it — which is the vote's situation and Parity Check's, and they already have
     * a type. An eighth entry class holding a boolean would be a second way to say the same thing
     * and a second place for a mistake to be made.
     */
    val sniff: ChoiceEntry = ChoiceEntry(),
    val deallocate: ColumnEntry = ColumnEntry(DEALLOCATE.size),
    /**
     * **Interrupt's entry is a sequence of one, and the one is what makes it right.**
     *
     * The entry is the sweep's position at the tap (D-139), and a sweep does not wait: by the time
     * a thumb has moved to a SUBMIT the moment being confirmed is seconds gone. [SequenceEntry] is
     * the type whose rule is exactly that — *an entry goes when it can no longer change* — and here
     * it goes on the first element because there is only one. **The hand-over is the type's and not
     * the screen's**, which is the point of reusing it: a screen that had to remember to send would
     * be a screen that could forget.
     */
    val interrupt: SequenceEntry = SequenceEntry(length = 1),
    /**
     * **Drift's answer is a [ChoiceEntry], for [sniff]'s reason and one of its own.**
     *
     * It is one position, held until it goes. What it needs that a sequence cannot give it is a
     * touched-but-unsent state: **the question is not asked until the phone buzzes**, and a tap
     * before that is a finger landing on glass rather than an answer. So the mark moves and nothing
     * goes, and the screen hands it over once the house has asked. See [DriftScreen].
     */
    val drift: ChoiceEntry = ChoiceEntry(),
    /**
     * **Interrupt's opening question, before the house has dealt one** — the port's fixture.
     *
     * A constructor parameter so a test can vary the band and watch it move; on a housed phone it
     * is replaced by [deal] before the screen can open. See [sweep].
     */
    sweep: InterruptSweep = INTERRUPT,
    /** Drift's, for [sweep]'s reason. */
    path: DriftPath = DRIFT,
    /** Jam's, for [sweep]'s reason. */
    rings: JamRings = JAM,
    /**
     * **Where an entry goes when it is handed over — and it does not carry a seat** (E-P2-1).
     *
     * `MeetingModel.send`'s arrangement, one system over, and every line of that KDoc applies. The
     * frame is an `IntentMessage`, which has no field an actor could ride in, so the seat is
     * stamped at the host's desk from the connection the bytes arrived on.
     *
     * **This was the gap E-P2-1 named**: `IntentMessage.HandOverEntry` existed and had no control
     * to send it, so every Subroutine in the game was a screen that took an answer and posted it
     * nowhere. Wiring it needed three things — a send seam, a projection from ten entry types to
     * one `List<Int>`, and **the marker**. All three are here now: the seam is this, the projection
     * is [SubroutineEntry.entered], and the marker is [standingAt].
     *
     * Defaulted to nothing, so every render and every screenshot gets a phone whose Subroutines are
     * real and whose entries reach nobody. That is the honest state of a bench.
     */
    val send: (IntentMessage) -> Unit = {},
) {

    // ---- The question, as this phone was dealt it (D-238) --------------------------------------
    //
    // **Every screen draws from these and from nothing else**, and every one of them is the house's
    // on a housed phone: [deal] writes them from `SubroutineInstance.parameters`, read through the
    // same `model` reader `core` drew against and grades with, so the picture on the glass and the
    // answer key are two readings of one list. Until D-238 the screens read the companion's
    // fixtures, every phone in a round drew the same sample puzzle, and the house graded the entry
    // against a question the player had never been shown — a correct answer to what was on the
    // screen was refused, and Parity, Deallocate, Signal Trace and Replay could not be answered
    // right at all.
    //
    // **The fixtures are the bench's.** They are what a phone with no house shows, which is what
    // the render harness draws; a housed phone opens no Subroutine without a dealt question
    // (`FlowModel.beginSubroutine`), so on a housed phone these are always the house's by the time a
    // screen can read them.
    //
    // **No role, and no answer.** Each is the question as drawn — the grid, the columns, the band —
    // and none of them is compared with an entry anywhere in `ui`. The Insider's fake is dealt by
    // the same draw, read by the same reader and drawn by the same screen (rule 8).

    /**
     * **Interrupt's band, speed and phase.** The band's width is graded (E-S2-1, closed): what is
     * drawn is the band the house will grade, because they are the same number off the same scan.
     */
    var sweep: InterruptSweep by mutableStateOf(sweep)
        private set

    /** **Drift's path, cover, speed and *now*.** Its tolerance is graded and deliberately not drawn. */
    var path: DriftPath by mutableStateOf(path)
        private set

    /** **Jam's opening gap.** The press that puts one ring on the other is the one the house grades. */
    var rings: JamRings by mutableStateOf(rings)
        private set

    /** **Replay's pattern**, flashed in order by the screen and tapped back (D-238). */
    var pattern: ReplayPattern by mutableStateOf(REPLAY)
        private set

    /** **Parity Check's grid**, one cell breaking the checkerboard and no memory of which. */
    var grid: List<Boolean> by mutableStateOf(PARITY)
        private set

    /**
     * **Short's ask.** On the model and never on [HoldEntry]: an entry that knew the count could
     * subtract it from the count it is holding. See [HoldEntry].
     */
    var hold: ShortHold by mutableStateOf(SHORT)
        private set

    /** **Signal Trace's wiring**, with its two ends. */
    var wiring: SignalGraph.Wiring by mutableStateOf(TRACE)
        private set

    /** **Deallocate's columns, as dealt.** What the screen subtracts this phone's removals from. */
    var columns: List<Int> by mutableStateOf(DEALLOCATE)
        private set

    /**
     * **The house dealt this phone [parameters] as a question of [kind]** (D-238).
     *
     * Read through the `model` reader for that kind and written to the slot that kind's screen
     * draws from; the two sequences and Deallocate also take their size from it, because a five-dot
     * Replay owes five taps and a three-column Deallocate has three places to land. **Read before
     * anything is written**, so a refusal leaves the phone holding exactly what it held.
     *
     * Two of the ten have nothing on the screen to draw: **Sniff's and Handshake's questions are
     * felt**, and `HouseBuzzer` plays them off the same instance. They are still read here, so a
     * malformed one opens nothing — and Handshake's beat count is the number of taps it owes.
     *
     * Throws [MalformedSubroutineParameters] for a question this client cannot draw. The house
     * cannot deal one (`SubroutineDrawTest` reads thousands of its draws through these readers), and
     * `FlowModel` reads at the arrival and opens nothing on a refusal, which is the fail-closed
     * direction: *it did not open* rather than *it opened a question nobody asked*.
     */
    fun deal(kind: SubroutineKind, parameters: List<Int>) {
        when (kind) {
            SubroutineKind.Replay -> {
                val read = ReplayPattern.of(parameters)
                pattern = read
                replay.resize(read.order.size)
            }
            SubroutineKind.Interrupt -> sweep = InterruptSweep.of(parameters)
            SubroutineKind.ParityCheck -> grid = ParityGrid.of(parameters)
            // Felt, not drawn: the two groups are `HouseBuzzer`'s to play off this same instance,
            // and the screen's two halves are the same two halves whatever was buzzed.
            SubroutineKind.Sniff -> SniffGroups.of(parameters)
            SubroutineKind.Deallocate -> {
                val read = DotColumns.of(parameters)
                columns = read
                deallocate.resize(read.size)
            }
            SubroutineKind.Drift -> path = DriftPath.of(parameters)
            SubroutineKind.Short -> hold = ShortHold.of(parameters)
            SubroutineKind.SignalTrace -> wiring = SignalGraph.of(parameters)
            SubroutineKind.Jam -> rings = JamRings.of(parameters)
            // Felt, not drawn, for Sniff's reason; the beat count is the number of taps owed.
            SubroutineKind.Handshake -> handshake.resize(HandshakeRhythm.of(parameters).beats)
        }
    }

    /**
     * **Which card opened the work this phone has in front of it.**
     *
     * It rides the hand-over because the house asks for it — *a player may have scanned a second
     * one since*, and `IntentMessage.HandOverEntry.marker` is which card opened the work rather
     * than a claim about where the player is standing now (D-136).
     *
     * **Null until a scan has said so, and a hand-over with no marker goes nowhere.** That is the
     * fail-closed direction and it is the one that matters here: a marker guessed from the roster,
     * or defaulted to whatever was scanned last, would attribute an answer to the wrong piece of
     * work — and the player would be graded on it without anything on any screen looking wrong.
     */
    var standingAt: MarkerId? by mutableStateOf(null)
        private set

    /**
     * A card was read and it opened this. Set by the scan flow, before the screen appears.
     *
     * **Nullable, and null leaves the phone standing nowhere**, which is what a bench is: the work
     * opens, the answer is taken, and [posted] refuses to send it because there is no card to
     * attribute it to. A default marker here would post every bench answer against one card.
     */
    fun opened(marker: MarkerId?) {
        standingAt = marker
    }

    /**
     * The entry behind a Subroutine, or null where the Subroutine has no interaction built.
     *
     * Null rather than an empty entry, for [Subroutine.screen]'s reason: an unbuilt Subroutine
     * handed a working entry would accept input on behalf of work that does not exist.
     */
    fun entry(of: Subroutine): SubroutineEntry? = when (of) {
        Subroutine.Handshake -> handshake
        Subroutine.Replay -> replay
        Subroutine.ParityCheck -> parity
        Subroutine.Short -> short
        Subroutine.SignalTrace -> trace
        Subroutine.Jam -> jam
        Subroutine.Sniff -> sniff
        Subroutine.Deallocate -> deallocate
        Subroutine.Interrupt -> interrupt
        Subroutine.Drift -> drift
    }

    /**
     * **A finger landing somewhere, and that is all it is.**
     *
     * What [at] means is the screen's own vocabulary, because the screens have nothing in common
     * to say: an element for a sequence, a cell for the parity grid, **how many fingers are now on
     * the glass** for Short, a signed step for Jam, a node for Signal Trace, **which of the two
     * groups was bigger** for Sniff, a column for Deallocate, **where the sweep was when the
     * finger landed** for Interrupt and **where the finger landed** for Drift. None of them is
     * compared with anything.
     *
     * **There is no `else` on this `when` any more, because the roster is complete.** It carried
     * one while Interrupt and Drift were unbuilt, and an `else` on a dispatch is exactly how rule 8
     * gets broken quietly: an eleventh Subroutine would fall through it, echo nothing, and look
     * like a screen that had not loaded. Exhaustive, it stops being a runtime silence and becomes
     * a compiler error in the same change that adds the row.
     */
    fun tap(subroutine: Subroutine, at: Int) {
        when (subroutine) {
            Subroutine.Handshake -> handshake.enter(at)
            Subroutine.Replay -> replay.enter(at)
            Subroutine.ParityCheck -> parity.choose(at)
            Subroutine.Short -> short.press(at)
            Subroutine.SignalTrace -> trace.walk(at)
            Subroutine.Jam -> jam.step(at)
            Subroutine.Sniff -> sniff.choose(at)
            Subroutine.Deallocate -> deallocate.remove(at)
            Subroutine.Interrupt -> interrupt.enter(at)
            Subroutine.Drift -> drift.choose(at)
        }
        // **The three sequences hand themselves over on their last element**, so for them the tap
        // IS the hand-over and there is no button to hang the send off. Called unconditionally
        // because `posted` refuses anything that has not gone — a check here would be the same
        // question asked in two places, and the second copy is the one that gets it wrong.
        posted(subroutine)
    }

    /**
     * The entry goes to the house.
     *
     * SUBMIT on the five whose answer can still change, **the clock** on Short — a hold that has
     * run its two seconds has nothing further to say and no button to press, since both hands are
     * on the glass — and **the house's own buzz** on Drift, which is the only one of the eight
     * whose entry goes on something that is neither a control nor this phone's clock. The three
     * sequences are absent because they hand themselves over on their last element, which is what
     * *an entry goes when it can no longer change* comes to when the player has said everything
     * they were asked for.
     */
    fun handOver(subroutine: Subroutine) {
        when (subroutine) {
            Subroutine.ParityCheck -> parity.handOver()
            Subroutine.Short -> short.handOver()
            Subroutine.SignalTrace -> trace.handOver()
            Subroutine.Jam -> jam.handOver()
            Subroutine.Sniff -> sniff.handOver()
            Subroutine.Deallocate -> deallocate.handOver()
            Subroutine.Drift -> drift.handOver()
            // The three sequences hand themselves over on their last element — see this method's
            // note — so by the time anything calls this they are already gone. [posted] below is
            // what sends them, and it is called from `tap` for exactly that reason.
            else -> Unit
        }
        posted(subroutine)
    }

    /**
     * **The entry, on its way to the house** (E-P2-1 closed).
     *
     * Called wherever an entry becomes final, which is two places rather than one: [handOver] for
     * the seven that go on a button or a clock, and [tap] for the three sequences that go on their
     * last element. Both funnel here so that *an entry that went is an entry that was sent* is one
     * fact rather than two call sites that agree until somebody adds an eleventh Subroutine.
     *
     * **Nothing is sent for an entry that has not gone**, which is [SubroutineEntry.gone]'s job and
     * not this method's guesswork — a sequence one element short is a player still answering, and a
     * phone that posted it would hand the house a wrong answer the player never finished giving.
     *
     * **Nothing is sent without a marker either.** See [standingAt]: an answer attributed to the
     * wrong card is graded, silently, against work the player never did.
     *
     * It reports nothing back and moves no screen. *Whether the work landed is the house's answer*
     * — `Effect.SubroutineGraded` — and a screen that walked away on the strength of its own last
     * tap would be this phone announcing a completion it cannot see.
     */
    private fun posted(subroutine: Subroutine) {
        val entry = entry(subroutine) ?: return
        if (!entry.gone) return
        val marker = standingAt ?: return
        send(IntentMessage.HandOverEntry(marker = marker, entered = entry.entered))
    }

    /**
     * **STOP NOW — the performance window closed with nothing handed over** (D-110, D-111).
     *
     * The one control on every Subroutine screen, and until this unit it had no wire member at all
     * (E-P2-5). What it reports is that a window this phone opened has shut, which the presence
     * plane needs in order to expire a placement — *the one consumer it has is an expiry* (D-136).
     *
     * **It carries no marker and no reason**, because the event does not: a reason field is an
     * abandonment record, and how often somebody walks away from work that was never going to count
     * is a statistic separating a real Subroutine from a fake. See `Event.PerformanceEnded`.
     *
     * **Sent even when nothing was entered, and that is the point of it.** A phone that only
     * reported abandoned work it had made progress on would make the report itself a signal.
     */
    fun stopped() {
        if (standingAt == null) return
        standingAt = null
        send(IntentMessage.StopSubroutine)
    }

    /**
     * **Which opening this is — the identity a screen keys its clock on** (E-S2-4).
     *
     * A number rather than anything descriptive, and it says exactly one thing: *this is not the
     * instance you were drawing a moment ago.* It changes on every scan that opens work and on
     * nothing else.
     *
     * ### Why a screen cannot use its own composition instead
     *
     * The two moving Subroutines run a clock from the moment their screen appears (D-139's sweep,
     * D-140's drift), and a clock held in a `remember` restarts when the composition is disposed —
     * which today looks correct, because the only route back to a marker walks through the work
     * order and disposes the screen on the way. **That is a coincidence of the current flow, not a
     * rule**: the day a re-scan can re-open a Subroutine the phone is already standing on, a
     * screen keyed on its own composition keeps counting through a fresh draw, and the band the
     * player is watching is one the house has already replaced. The clock has to restart because
     * *the instance changed*, and this is the thing that says it did.
     *
     * ### It is this phone's count, and not the instance's parameters
     *
     * The arriving `SubroutineInstance` is now read ([deal]), but the identity here is still *how
     * many times this phone has opened work*. It is sufficient for the claim — it changes exactly
     * when a new instance is dealt, because BEGIN deals and counts in one act — and it is deliberately not
     * derived from the parameters: two consecutive draws may legitimately be identical, and a
     * re-scan that happened to redraw the same band would then not restart the clock.
     */
    var instance: Int by mutableStateOf(0)
        private set

    /** A marker scanned again opens the same Subroutine with nothing entered. */
    fun beganAgain() {
        instance++
        Subroutine.entries.forEach { entry(it)?.restart() }
    }

    companion object {

        /*
         * **Ten fixtures, and every one of them is written as the parameter list the scan sends.**
         *
         * **They are the bench's, and only the bench's** (D-238): what a phone with no house draws,
         * and the slots [deal] overwrites with the house's own on a housed phone. They are lists
         * rather than named numbers so that **the reader exercised by the bench is the reader that
         * reads the house's** — and so that a fixture this client could not draw
         * fails the build rather than a player. That was already Sniff's, Deallocate's, Interrupt's
         * and Drift's arrangement; the other six had loose numbers, which is how a client fixture
         * and a house parameter drift apart in the first place (E-S2-1, the one that got caught).
         *
         * Every one of them is read once, at class-load. See `MalformedSubroutineParameters`.
         */

        /**
         * **Handshake's rhythm, as the gaps between its beats.**
         *
         * Four gaps is five beats, which is the design fixture's own count carried across so the
         * ported screen keeps drawing what it drew. The gaps are uneven — a rhythm whose silences
         * were all the same length is a count, and Sniff is already the count.
         */
        val HANDSHAKE_PARAMETERS: List<Int> = listOf(3, 3, 6, 2)

        /** The rhythm, read once. */
        val HANDSHAKE: HandshakeRhythm = HandshakeRhythm.of(HANDSHAKE_PARAMETERS)

        /** How many taps Handshake owes. The rhythm's, not this file's. */
        val HANDSHAKE_BEATS: Int = HANDSHAKE.beats

        /**
         * **Replay's pattern.** The roster says 3–5 dots; four lays out evenly in two columns, and
         * the order is not the reading order, which is the only property of it that was chosen.
         */
        val REPLAY_PARAMETERS: List<Int> = listOf(2, 0, 3, 1)

        /** The pattern, read once. */
        val REPLAY: ReplayPattern = ReplayPattern.of(REPLAY_PARAMETERS)

        /** How many dots Replay draws. One per element of the pattern. */
        val REPLAY_DOTS: Int = REPLAY.order.size

        /** Which cell the parity grid corrupts. A fixture seed; in play the house sends the grid. */
        val PARITY_PARAMETERS: List<Int> = listOf(19)

        /** The grid, read once. */
        val PARITY: List<Boolean> = ParityGrid.of(PARITY_PARAMETERS)

        /**
         * **How many fingers Short asks for.**
         *
         * The design says *hold N fingers* and never says what N is. Three is a presentation
         * fixture — enough that it is a deliberate arrangement of a hand rather than a press,
         * few enough to make one-handed on a phone held at an angle. In play the house sends it.
         *
         * It reaches the screen through the model and **never through [HoldEntry]**: an entry that
         * knew the asked-for count could subtract it from the count it is holding, and then the
         * phone would be one careless edit away from grading the answer. See [HoldEntry].
         */
        val SHORT_PARAMETERS: List<Int> = listOf(3)

        /** The ask, read once. */
        val SHORT: ShortHold = ShortHold.of(SHORT_PARAMETERS)

        /** How many fingers, as the screen draws it. */
        val SHORT_FINGERS: Int = SHORT.fingers

        /** **Jam's opening gap**, negative so the player's ring opens inside the fixed one. */
        val JAM_PARAMETERS: List<Int> = listOf(-9)

        /** The rings, read once. */
        val JAM: JamRings = JamRings.of(JAM_PARAMETERS)

        /**
         * How far Jam's shape may travel from where the house opened it, in steps.
         *
         * A stop so the shape cannot be driven off the screen, deliberately far outside anything
         * a player would aim at. Not a difficulty setting — see [ScalarEntry].
         */
        val JAM_REACH: Int = JamRings.REACH

        /** Which graph Signal Trace draws. A fixture seed; in play the house sends the wiring. */
        val TRACE_PARAMETERS: List<Int> = listOf(5)

        /** The wiring, read once. */
        val TRACE: SignalGraph.Wiring = SignalGraph.of(TRACE_PARAMETERS)

        /**
         * **Sniff's two groups and its pause, as the scan would send them.**
         *
         * Three buzzes, then five, with eight tenths of a second between — *playtest owns the gap
         * between the two group sizes; it is the difficulty knob and the only one* (D-137). Two
         * apart is a comfortable magnitude judgment and nothing here has been played with.
         *
         * Written as the parameter list the house sends rather than as three named numbers, so
         * the fixture is the same shape as the arrival that will replace it, and so the reader
         * that will read the real one is the reader being exercised now.
         */
        val SNIFF_PARAMETERS: List<Int> = listOf(3, 5, 8)

        /**
         * **The question, read once.**
         *
         * At class-load, from the fixture, so a malformed one fails the build rather than a
         * player — see [MalformedSubroutineParameters] for why the read is here and not in a
         * draw. In play this is the scan's answer, resolved at the effect boundary.
         */
        val SNIFF: SniffGroups = SniffGroups.of(SNIFF_PARAMETERS)

        /**
         * **Deallocate's columns, as the scan would deal them.**
         *
         * Four columns at four, two, five and three — *playtest owns the column count and the dot
         * distribution* (D-138). The shortest is not on an end and not in the middle, which is the
         * only property of this fixture that was chosen rather than picked: a level that always
         * sits at one edge is a Subroutine you can do without looking at the whole screen.
         */
        val DEALLOCATE_PARAMETERS: List<Int> = listOf(4, 2, 5, 3)

        /** The columns, read once — [SNIFF]'s arrangement, for [SNIFF]'s reason. */
        val DEALLOCATE: List<Int> = DotColumns.of(DEALLOCATE_PARAMETERS)

        /**
         * **Interrupt's band, speed and phase, as the scan would send them.**
         *
         * The band sits at 58 of 100 — off centre, because a band in the middle of the bar is one
         * the sweep meets at the same point of every pass and the Subroutine becomes *tap on the
         * beat*. Fourteen steps a second is seven seconds end to end, which is *slow* per
         * `gdd.md:595`, and a little over a second of the sweep being inside the band. The phase
         * starts it part-way through the first stroke so the opening frame is not the same picture
         * every time this screen is looked at.
         *
         * ***Playtest owns the speed and the band width*** (D-139), and **both are on this line
         * now**: the width is the second number, drawn by the house with the position, because the
         * band that is drawn has to be the band that is graded (E-S2-1, closed). Eight either side
         * is sixteen steps of a hundred — *generous* per `gdd.md:595`, and at this speed a little
         * over a second of the sweep being inside it.
         */
        val INTERRUPT_PARAMETERS: List<Int> = listOf(58, 8, 14, 30)

        /** The sweep, read once — [SNIFF]'s arrangement, for [SNIFF]'s reason. */
        val INTERRUPT: InterruptSweep = InterruptSweep.of(INTERRUPT_PARAMETERS)

        /**
         * **Drift's layout seed, speed and hidden wait, as the scan would send them.**
         *
         * Twelve steps a second is eight seconds of lane; six tenths is the dot spending a little
         * over half a second out of sight before the phone asks, which fits inside the cover this
         * seed lays down and would not fit inside every one of them — which is the refusal in
         * [DriftPath.of] having something to refuse rather than being a flourish.
         *
         * ***Playtest owns the speed, the hidden duration and the hit radius*** (D-140), and the
         * radius is the fourth number: the house draws it and grades with it, and this client reads
         * it and **does not draw it** — a radius on screen would be the answer's neighbourhood
         * painted around the answer. See [DriftPath.hitRadius].
         */
        val DRIFT_PARAMETERS: List<Int> = listOf(7, 12, 6, 6)

        /** The path, read once — [SNIFF]'s arrangement, for [SNIFF]'s reason. */
        val DRIFT: DriftPath = DriftPath.of(DRIFT_PARAMETERS)

        /**
         * **The bench's question of [kind], as the parameter list a scan carries.**
         *
         * Internal, for the module's own tests: a hand-made `SubroutineInstance` needs a question
         * the readers accept, because a housed phone now reads one at the arrival and opens nothing
         * over a question it cannot draw (D-238). Nothing in the app may reach for it — a housed
         * phone's question is the house's.
         */
        internal fun benchParameters(kind: SubroutineKind): List<Int> = when (kind) {
            SubroutineKind.Replay -> REPLAY_PARAMETERS
            SubroutineKind.Interrupt -> INTERRUPT_PARAMETERS
            SubroutineKind.ParityCheck -> PARITY_PARAMETERS
            SubroutineKind.Sniff -> SNIFF_PARAMETERS
            SubroutineKind.Deallocate -> DEALLOCATE_PARAMETERS
            SubroutineKind.Drift -> DRIFT_PARAMETERS
            SubroutineKind.Short -> SHORT_PARAMETERS
            SubroutineKind.SignalTrace -> TRACE_PARAMETERS
            SubroutineKind.Jam -> JAM_PARAMETERS
            SubroutineKind.Handshake -> HANDSHAKE_PARAMETERS
        }

        /**
         * **The fixture: a phone part-way through each of the six.**
         *
         * Every render and every rendering test gets this, for the reason [MeetingModel.sample]
         * exists — a Subroutine nobody has touched is a screen with no echo on it, and a test
         * looking at one proves nothing about the thing being built. The Handshake count is the
         * design fixture's own three-of-five; Replay is two dots in; the parity grid has a cell
         * chosen and **not** submitted, so both halves of that control are on screen at once.
         *
         * The three new ones follow the same rule — mid-gesture, nothing handed over. Short has a
         * hand on the glass and its two seconds still running, Jam has been walked part of the way
         * in, and Signal Trace has a route two nodes long that has not been sent.
         *
         * **Sniff is the one that had to be thought about.** Its screen is black until an answer
         * has been given, so a fixture with nothing chosen would render as a screen with nothing
         * on it — which is the honest thing for the *untouched* frame and useless as the default
         * every other render gets. It holds the second group, unsent, so the default frame is the
         * one with something to look at. Deallocate has one dot off one column, which is the same
         * mid-gesture rule as the rest.
         *
         * **Interrupt is the one that cannot be mid-gesture, and it is caught rather than left
         * alone.** Its entry goes on the tap that makes it, so there is no state between untouched
         * and sent — and untouched is not available here, because `openingASubroutineAgainClears
         * EveryEntry` requires every entry in this fixture to hold something (an entry the fixture
         * never touched is one whose clearing proves nothing). So the sweep is caught, a third of
         * the way along, **which is outside the band the fixture draws**: a default frame showing a
         * catch defunct in the middle of the band would read as this phone having marked it correct.
         *
         * Drift holds a mark it has not sent, which is the state only that screen can be in: a
         * finger that landed before the house asked.
         */
        fun sample(): SubroutineModel = SubroutineModel().apply {
            handshake.enter(0)
            handshake.enter(1)
            handshake.enter(2)
            replay.enter(2)
            replay.enter(0)
            parity.choose(14)
            short.press(2)
            repeat(4) { jam.step(-1) }
            sniff.choose(SNIFF_SECOND)
            deallocate.remove(0)
            interrupt.enter(InterruptSweep.SPAN / 3)
            drift.choose(DriftPath.SPAN / 2)
            val wiring = TRACE
            trace.walk(wiring.source)
            wiring.joinedTo(wiring.source).firstOrNull()?.let { trace.walk(it) }
        }

        /**
         * **Sniff's two answers, as the only two things a tap on that screen can mean.**
         *
         * The left half of the panel is the group that buzzed first and the right half is the one
         * that buzzed second — reading order, which is also the order they arrived in. They are
         * named here rather than written as 0 and 1 at four call sites, because *the first one*
         * and *the second one* is what they are, and an index is what they are stored as.
         *
         * **Neither of them is the answer.** Which one is bigger is a fact about [SNIFF] and this
         * is a fact about the screen; nothing in `ui` holds both.
         */
        const val SNIFF_FIRST = 0
        const val SNIFF_SECOND = 1
    }
}

/**
 * The Subroutine entries the six Subroutine screens draw.
 *
 * Provided by [Screen] beside [LocalMeeting] and for the same reason: a test that taps four dots
 * must not leave the next render looking at a phone that has already handed its sequence over.
 */
val LocalSubroutine: ProvidableCompositionLocal<SubroutineModel> =
    staticCompositionLocalOf { SubroutineModel.sample() }
