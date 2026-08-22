package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import home.someoneshome.model.SubroutineKind

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
 * | 2 | Interrupt | Short | Timing | A slow bar sweeps; tap inside a generous band | Medium |
 * | 3 | Parity Check | Short | Visual search | Grid of filled/empty cells; tap the one breaking the pattern | Bright |
 * | 4 | Sniff | Short | Magnitude | Two buzzed groups; say which was bigger (D-137) | Dark |
 * | 5 | Deallocate | Short | Counting | A tap removes a dot; level is the shortest column (D-138) | Bright |
 * | 6 | Drift | Medium | Tracking under occlusion | A dot drifts behind occluders; tap where it is now | Medium |
 * | 7 | Short | Short | Gross motor | Hold N fingers on the screen for two seconds | Dark |
 * | 8 | Signal Trace | Medium | Pathfinding | Tap node-to-node from source to sink | Medium |
 * | 9 | Jam | Medium | Convergence | Tap +/− until two shapes overlap | Medium |
 * | 10 | Handshake | Medium | Haptic echo | The phone buzzes a pattern; you tap it back | Dark |
 *
 * **Rows 4 and 5 are revision 31's, not the roster's.** `gdd.md:568` and `:569` are superseded by
 * D-137 and D-138 and the table above carries the rulings rather than the lines they replaced —
 * *"the phone buzzes N times; tap N"* was two Subroutines and both were wrong, and *"tap to even
 * them out"* did not say what a tap does. Rows 2 and 6 still quote the GDD because Interrupt and
 * Drift are unbuilt; D-139 and D-140 supersede those two lines as well.
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
) {
    Replay("REPLAY", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.Replay, ScreenId.SubReplay),
    Interrupt("INTERRUPT", SubroutineTier.Short, LightSignature.Medium, SubroutineKind.Interrupt),
    ParityCheck("PARITY CHECK", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.ParityCheck, ScreenId.SubParity),
    Sniff("SNIFF", SubroutineTier.Short, LightSignature.Dark, SubroutineKind.Sniff, ScreenId.SubSniff),
    Deallocate("DEALLOCATE", SubroutineTier.Short, LightSignature.Bright, SubroutineKind.Deallocate, ScreenId.SubDeallocate),
    Drift("DRIFT", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.Drift),
    Short("SHORT", SubroutineTier.Short, LightSignature.Dark, SubroutineKind.Short, ScreenId.SubShort),
    SignalTrace("SIGNAL TRACE", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.SignalTrace, ScreenId.SubTrace),
    Jam("JAM", SubroutineTier.Medium, LightSignature.Medium, SubroutineKind.Jam, ScreenId.SubJam),
    Handshake("HANDSHAKE", SubroutineTier.Medium, LightSignature.Dark, SubroutineKind.Handshake, ScreenId.SubHandshake);

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
class SequenceEntry(
    /**
     * How many elements the house asked for — **the one number any entry in this file is told,
     * and the only one that has to be.**
     *
     * A sequence has no other way to know it is over: the player stops when they have said as many
     * things as they were asked for, so the count is structure rather than answer. [HoldEntry],
     * [ScalarEntry] and [PathEntry] are told nothing at all, because a hold ends on a clock, a
     * converging shape has no finishing line, and a route ends when the player says it does.
     *
     * **A presentation fixture here, and the house's in play**, like every number in
     * [Flow.autoAdvance]. It is not a balance value and nothing locks it at arming: the design
     * gives Replay 3–5 dots and rates Handshake's rhythm nowhere, so a single number written here
     * would be an invention wearing a tuning value's clothes.
     */
    val length: Int,
) : SubroutineEntry {

    /**
     * Every element the player has entered, in the order they entered it.
     *
     * Kept in full rather than as a count, because the echo is per-element: the dot you touched
     * lights, and which dots you touched is the only thing on screen that is yours.
     */
    var entered: List<Int> by mutableStateOf(emptyList())
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
 * The design's Signal Trace is *tap node-to-node from source to sink through a small graph*. This
 * keeps the nodes tapped, in order, and **does not check that consecutive ones are joined by an
 * edge.** That is rule 1 stated as a type: *never early-return on invalid in a client-visible
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
class ColumnEntry(
    /** How many places a finger can land. Structure, not answer — see the note above. */
    val columns: Int,
) : SubroutineEntry {

    /** How many dots this phone has taken off each column, in column order. */
    var removed: List<Int> by mutableStateOf(List(columns) { 0 })
        private set

    var handedOver: List<Int>? by mutableStateOf(null)
        private set

    override val touched: Boolean get() = removed.any { it > 0 } || handedOver != null

    override val gone: Boolean get() = handedOver != null

    /**
     * One dot off column [at].
     *
     * A column index this entry does not have is ignored rather than throwing: nothing on a drawn
     * screen can produce one, and a `when` that threw would turn a routing mistake into a dead
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
 * **The parity grid: a regular pattern with exactly one cell breaking it, and no memory of which.**
 *
 * The design's Parity Check is *a grid of filled/empty cells; tap the one breaking the pattern* —
 * singular. The pattern is a checkerboard, which is the only regular arrangement that survives the
 * input vocabulary: at four luminance steps of amber, with no hue discrimination, what is left is
 * position, shape, count, size, order and presence/absence, and a checkerboard is presence and
 * position and nothing else.
 *
 * ### The generator forgets the answer, and that is the whole design of this object
 *
 * [of] returns the cells and nothing else. The index it flipped is a local, discarded on return,
 * so the screen drawing this grid **cannot** mark the odd cell however carelessly it is later
 * edited — the same discipline as `Observation` versus `ObservationView`: the client-facing type
 * is not the authority's type with a field hidden, it is a type that never had the field. A
 * comment saying "do not draw the answer" is not a boundary.
 *
 * The answer is not *secret* — it is on screen, in plain sight, and finding it is the work. What
 * must stay off the device is the **judgement**: whether the cell this player touched was that
 * one. See [SequenceEntry] for why that line is where it is.
 *
 * Deterministic from [seed] with no randomness at draw time (rule 4): the same seed is the same
 * grid on every render, on every replay, on every phone.
 */
object ParityGrid {

    const val COLUMNS = 6
    const val ROWS = 6
    const val SIZE = COLUMNS * ROWS

    /** The checkerboard this grid is a corruption of. True is a filled cell. */
    fun clean(at: Int): Boolean = (at / COLUMNS + at % COLUMNS) % 2 == 0

    /**
     * The grid as drawn: [clean], with the cell at `seed mod SIZE` inverted.
     *
     * The corner cells are as valid a hiding place as the middle ones and are not excluded. A
     * generator that avoided the edges would teach players where never to look, which is a
     * difficulty setting nobody chose.
     */
    fun of(seed: Int): List<Boolean> {
        val odd = seed.mod(SIZE)
        return List(SIZE) { at -> if (at == odd) !clean(at) else clean(at) }
    }
}

/**
 * **The Signal Trace graph: a lattice, a route that reaches across it, and some branches that do
 * not — and, like [ParityGrid], it forgets the route before it returns.**
 *
 * The design's Signal Trace *ships with BFS-generated graphs, so the optimum is computed rather
 * than authored, and difficulty tunes by decoy count*. [of] builds a walk from source to sink to
 * guarantee the graph is connected at all, hangs [DECOYS] branches off it, and then **returns only
 * the nodes and the edges.** The walk it used is a local; it is not the shortest route in general,
 * because a decoy touching two distant points of it makes a shorter one — which is exactly why the
 * optimum has to be computed. Nothing that computes it is on the phone.
 *
 * ### Why a lattice
 *
 * Node layout is not a detail the design left out by accident, it is the whole legibility question:
 * at four luminance steps of amber, with no hue, a graph is read by **position** and nothing else,
 * and edges that cross look like nodes that do not exist. A lattice with edges only between
 * orthogonally adjacent cells cannot produce a crossing, cannot produce two nodes close enough to
 * be one, and puts every node on a grid a thumb can find in the dark without looking. A prettier
 * force-directed layout would be a different Subroutine in a lit room.
 *
 * [source] and [sink] are returned because they are the *question* — both are drawn, and a route
 * with no visible ends is not a route. The answer is which way across.
 *
 * Deterministic from [seed] with no randomness at draw time (rule 4).
 */
object SignalGraph {

    /** How wide the lattice is, in columns. Four is the visual decision — see the note in [of]. */
    const val COLUMNS = 4

    /** How tall, in rows. */
    const val ROWS = 3

    /**
     * **A placeholder, and specifically not a tuning value.**
     *
     * The design says difficulty tunes by decoy count, which makes the real number balance, which
     * is out of scope here in the way every balance number is. Three is a neutral fixture that
     * fills a four-by-three lattice enough to make the route non-obvious and not so much that
     * every cell is occupied. Nothing locks it at arming and nobody has played with it.
     */
    const val DECOYS = 3

    /** The row the signal enters and leaves on: the middle one, so a route can bend either way. */
    const val ENTRY_ROW = 1

    /** Where a node sits on the lattice. Not a position on screen — the screen owns that. */
    data class Node(val column: Int, val row: Int)

    /**
     * A drawable graph: nodes, the pairs of node indices joined by an edge, and the two ends.
     *
     * **There is no route in here.** A screen holding this cannot draw the answer however
     * carelessly it is later edited, for the same reason [ParityGrid]'s cells cannot.
     */
    class Wiring(
        val nodes: List<Node>,
        val edges: List<Pair<Int, Int>>,
        val source: Int,
        val sink: Int,
    ) {
        /** The nodes joined to [at]. Adjacency is drawn, so asking for it reveals nothing. */
        fun joinedTo(at: Int): List<Int> =
            edges.mapNotNull { (a, b) -> if (a == at) b else if (b == at) a else null }
    }

    private fun adjacent(a: Node, b: Node): Boolean {
        val steps = kotlin.math.abs(a.column - b.column) + kotlin.math.abs(a.row - b.row)
        return steps == 1
    }

    fun of(seed: Int): Wiring {
        // A walk from source to sink, one lattice step at a time: up or down inside a column,
        // then across to the next. It exists to guarantee the sink is reachable at all, and it is
        // thrown away at the end of this function.
        // One base-ROWS digit of the seed per middle column, so consecutive seeds bend differently.
        var digits = seed
        val turns = List(COLUMNS) { column ->
            if (column == 0 || column == COLUMNS - 1) {
                ENTRY_ROW
            } else {
                val row = digits.mod(ROWS)
                digits /= ROWS
                row
            }
        }
        val walked = LinkedHashSet<Node>()
        walked += Node(0, turns[0])
        for (column in 0 until COLUMNS - 1) {
            val from = turns[column]
            val to = turns[column + 1]
            val step = if (to >= from) 1 else -1
            var row = from
            while (row != to) {
                row += step
                walked += Node(column, row)
            }
            walked += Node(column + 1, to)
        }

        // Decoys hang off the walk rather than floating: a node with no edge is a dot drawn in
        // the middle of nothing, which reads as a rendering fault rather than as a dead end.
        val candidates = buildList {
            for (column in 0 until COLUMNS) {
                for (row in 0 until ROWS) {
                    val cell = Node(column, row)
                    if (cell !in walked && walked.any { adjacent(it, cell) }) add(cell)
                }
            }
        }
        val chosen = LinkedHashSet(walked)
        if (candidates.isNotEmpty()) {
            var at = seed.mod(candidates.size)
            var tried = 0
            while (chosen.size < walked.size + DECOYS && tried < candidates.size) {
                chosen += candidates[at]
                at = (at + 1).mod(candidates.size)
                tried++
            }
        }

        val nodes = chosen.sortedWith(compareBy({ it.column }, { it.row }))
        val edges = buildList {
            for (a in nodes.indices) {
                for (b in a + 1 until nodes.size) {
                    if (adjacent(nodes[a], nodes[b])) add(a to b)
                }
            }
        }
        return Wiring(
            nodes = nodes,
            edges = edges,
            source = nodes.indexOf(Node(0, ENTRY_ROW)),
            sink = nodes.indexOf(Node(COLUMNS - 1, ENTRY_ROW)),
        )
    }
}

/**
 * **A scan parameter this client cannot read as the question it is supposed to be.**
 *
 * The house draws an instance's parameters at scan time and the client renders from them (D-139's
 * pattern, and the plumbing L4 built). This is what happens when what arrives is not a question:
 * two Sniff groups of the same size, a column of no dots, a list of the wrong length.
 *
 * ### Loud, and at the READ rather than at the draw
 *
 * The house map's format is the precedent and the reasoning is the same one (`HouseMapText` —
 * *strict, and loud*): a reader that quietly skipped what it did not understand hands back
 * something that looks complete and is not, and the fault surfaces as a player standing in the
 * dark being asked an impossible question. So nothing is defaulted, nothing is coerced, and every
 * failure names the fault.
 *
 * **It is thrown where the parameters are read, and never from a draw.** Rule 6 is that errors are
 * silent to the player, and rule 5 is that no error path may touch the lamp: a screen that threw
 * mid-composition would blank, and a phone that blanks in a dark house is indistinguishable from a
 * revocation. Today the read happens once, at class-load, against the port's own fixtures — so a
 * malformed one fails the build. **When the house's push exists this belongs at the effect
 * boundary, with the rest of the arrival**, and the worklog says so.
 */
class MalformedSubroutineParameters(val detail: String) :
    IllegalArgumentException("the house asked something this client cannot draw: $detail")

/**
 * **One step of a haptic pattern — data on this side of the boundary, a vibration on the other.**
 *
 * `ui` has no motor and this build has no phone attached, so a script here is a list, not a buzz.
 * It is shaped for the thing that will play it: alternating durations, no intensities, nothing to
 * interpret — the vocabulary every platform vibration API already has.
 *
 * **Every [Buzz] in a Sniff script is the same length** (D-135 reserves the long haptic for five
 * named events and this is not among them), which is not a detail: a script whose pulses varied
 * would be a rhythm, and D-137 makes Sniff a magnitude judgment precisely so that there is no
 * pattern to hold. What the player counts is *how many*, never *how they went*.
 */
sealed interface HapticStep {

    /** The motor is on for [millis]. */
    data class Buzz(val millis: Int) : HapticStep

    /** The motor is off for [millis]. */
    data class Rest(val millis: Int) : HapticStep
}

/**
 * **Sniff's question: two groups of buzzes with a pause between them, and one of them is bigger.**
 *
 * D-137 supersedes `gdd.md:568`. The phone buzzes **two haptic groups separated by a pause** and
 * the player answers **which group was bigger** — pure perception, no arithmetic to carry, no
 * numeral on screen, and nothing for the player to count *to*. It is the roster's only short dark
 * (`gdd.md:580`), the quick one a Resident can take without becoming a beacon.
 *
 * ### It holds the question and not the answer, and that is not the same sentence twice
 *
 * *Which group was bigger* is a fact about these two numbers, so this type **can** be asked it —
 * and must never be. It is the [ParityGrid] situation rather than the [SequenceEntry] one: the
 * answer is not secret (a player who felt both groups knows it) and it is the *judgement* that
 * stays on the house. What keeps the judgement off the phone is that [ChoiceEntry] holds the
 * player's answer, this holds the question, and **nothing in `ui` is given both** — no screen, no
 * model, no function takes a `SniffGroups` and a `ChoiceEntry` and compares them. There is no
 * `wasRight` here and there must never be one.
 *
 * ### Equal groups are refused, loudly (D-137)
 *
 * *The answer must exist and must be unique — a tie is a coin flip the house would then grade, and
 * D-109 grades entries on their merits or not at all.* So a tie is not clamped, nudged or answered
 * arbitrarily; it is a malformed question and it is said so. **The stand-in draw in `core` can
 * still produce one** — it draws three numbers out of one range with no idea what they mean
 * (`Scanning.kt`, E-L4-3) — which is exactly why this refusal has work to do rather than being a
 * defensive flourish.
 */
class SniffGroups private constructor(
    /** How many buzzes are in the first group. */
    val first: Int,
    /** How many are in the second. Never equal to [first]. */
    val second: Int,
    /** How long the phone is still between the two groups. The thing that makes them two. */
    val gapMillis: Int,
) {

    /**
     * The pattern, as the device unit will play it: the first group, the pause, the second group.
     *
     * Ends on a [HapticStep.Buzz] rather than on a trailing rest — a script that finished with
     * silence would be a script whose end the player cannot feel, and *when the question is over*
     * is the moment they are expected to answer.
     */
    val script: List<HapticStep> = buildList {
        repeat(first) {
            if (isNotEmpty()) add(HapticStep.Rest(PULSE_GAP_MILLIS))
            add(HapticStep.Buzz(PULSE_MILLIS))
        }
        add(HapticStep.Rest(gapMillis))
        repeat(second) {
            add(HapticStep.Buzz(PULSE_MILLIS))
            if (it < second - 1) add(HapticStep.Rest(PULSE_GAP_MILLIS))
        }
    }

    companion object {

        /**
         * **One buzz, and the same one every time.** A presentation fixture in the way
         * `HANDSHAKE_BEATS` is, and short: D-135 reserves the long haptic for five named events.
         */
        const val PULSE_MILLIS: Int = 90

        /** The silence between two buzzes of the same group. Even, so the group carries no rhythm. */
        const val PULSE_GAP_MILLIS: Int = 220

        /** How many numbers a Sniff instance is: two group sizes and the pause between them. */
        const val LENGTH: Int = 3

        /**
         * The pause arrives in **tenths of a second**, so the one duration on the wire is a small
         * integer like every other parameter. Nothing on the wire is ever milliseconds.
         */
        const val GAP_TENTHS: Int = 100

        /**
         * Read a scan's parameters as a Sniff question, or refuse them by name.
         *
         * Four ways to be malformed and only one of them is D-137's: a list of the wrong length, a
         * group with no buzzes in it, no pause between the groups — and **two groups of the same
         * size**, which is the one the ruling is about.
         */
        fun of(parameters: List<Int>): SniffGroups {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "SNIFF is $LENGTH numbers — two group sizes and the pause — and this is " +
                        "${parameters.size}: $parameters",
                )
            }
            val (first, second, gapTenths) = parameters
            if (first < 1 || second < 1) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given a group of $first and a group of $second, and a group with " +
                        "no buzzes in it is not a group",
                )
            }
            if (first == second) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given two groups of $first. D-137: equal groups never occur — the " +
                        "answer must exist and must be unique, and a tie is a coin flip the house " +
                        "would then grade",
                )
            }
            if (gapTenths < 1) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given a pause of $gapTenths tenths of a second, and two groups " +
                        "with no pause between them are one group",
                )
            }
            return SniffGroups(first, second, gapTenths * GAP_TENTHS)
        }
    }
}

/**
 * **Deallocate's question: the columns as they were dealt, and no opinion about what is owed.**
 *
 * D-138 supersedes `gdd.md:569`. A tap removes one dot from the tapped column and evening out
 * means bringing every column **down to the shortest** — so the answer is unique, the work is
 * countable, and *the verb is the fiction.* The columns carry the arithmetic and nothing on the
 * screen is a numeral (`gdd.md:588`).
 *
 * ### [of] returns the heights and computes nothing from them
 *
 * There is deliberately **no `level`, no `excess` and no `owed` in this file.** Every one of them
 * is one line and every one of them is the answer: the required entry is the sum of each column's
 * excess over the shortest, so a helper that returned the shortest column would put the answer key
 * on the phone, one `min()` from a screen that could then tell a player when to stop. The answer
 * is not secret — the columns are drawn and reading them is the work — but the *judgement* is the
 * house's, exactly as it is for [ParityGrid]'s odd cell.
 *
 * ### The refusals are the panel's capacity and nothing more
 *
 * A column that will not fit on the panel is not a harder Subroutine, it is a **different question
 * from the one the house asked**: dots clipped off the top of a column change the height a player
 * reads, and they would then answer the picture correctly and be graded wrong. So a distribution
 * that cannot be drawn is refused by name rather than truncated.
 *
 * **Equal columns are NOT refused.** A distribution that arrives already level asks for no
 * removals, which is a trivial instance rather than a malformed one — and refusing it would be
 * this client deciding how much work a piece of work has to contain, which is balance and is not
 * its to decide. `gdd.md:569` says *unequal*; D-138 does not, and the difference is left where the
 * ruling left it.
 */
object DotColumns {

    /**
     * **The panel's capacity, not a difficulty setting.**
     *
     * A column strip has to stay wider than [TAP_TARGET] — it is pressed one-handed, in the dark,
     * by somebody watching a doorway — and six strips with their gaps is where a 300-unit panel
     * runs out of room. [MOST_DOTS] is the same question vertically: seven dots and their gaps is
     * 130 units, against roughly 195 of body once the instruction, the return line, the motion row
     * and the two buttons have taken theirs.
     */
    const val LEAST_COLUMNS: Int = 2
    const val MOST_COLUMNS: Int = 6
    const val MOST_DOTS: Int = 7

    /** Read a scan's parameters as a column distribution, or refuse them by name. */
    fun of(parameters: List<Int>): List<Int> {
        if (parameters.size !in LEAST_COLUMNS..MOST_COLUMNS) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${parameters.size} column(s); this panel draws " +
                    "$LEAST_COLUMNS to $MOST_COLUMNS, and one column is already level",
            )
        }
        val short = parameters.withIndex().firstOrNull { it.value < 1 }
        if (short != null) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${short.value} dot(s) in column ${short.index}, and a " +
                    "column with no dots in it is a column that is not there: $parameters",
            )
        }
        val tall = parameters.withIndex().firstOrNull { it.value > MOST_DOTS }
        if (tall != null) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${tall.value} dots in column ${tall.index} and this panel " +
                    "draws $MOST_DOTS — a clipped column is a different question from the one " +
                    "the house asked: $parameters",
            )
        }
        return parameters
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
) {

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
        Subroutine.Interrupt, Subroutine.Drift -> null
    }

    /**
     * **A finger landing somewhere, and that is all it is.**
     *
     * What [at] means is the screen's own vocabulary, because the screens have nothing in common
     * to say: an element for a sequence, a cell for the parity grid, **how many fingers are now on
     * the glass** for Short, a signed step for Jam, a node for Signal Trace, **which of the two
     * groups was bigger** for Sniff, and a column for Deallocate. None of them is compared with
     * anything.
     *
     * A Subroutine with no entry behind it is ignored rather than crashing — a `when` that threw
     * would turn a routing mistake into a dead phone in a dark home, and rule 6 is that errors are
     * silent to the player.
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
            else -> Unit
        }
    }

    /**
     * The entry goes to the house.
     *
     * SUBMIT on the three whose answer can still change, and **the clock** on Short — a hold that
     * has run its two seconds has nothing further to say and no button to press, since both hands
     * are on the glass. The two sequences are absent because they hand themselves over on their
     * last element, which is what *an entry goes when it can no longer change* comes to when the
     * player has said everything they were asked for.
     */
    fun handOver(subroutine: Subroutine) {
        when (subroutine) {
            Subroutine.ParityCheck -> parity.handOver()
            Subroutine.Short -> short.handOver()
            Subroutine.SignalTrace -> trace.handOver()
            Subroutine.Jam -> jam.handOver()
            Subroutine.Sniff -> sniff.handOver()
            Subroutine.Deallocate -> deallocate.handOver()
            else -> Unit
        }
    }

    /** A marker scanned again opens the same Subroutine with nothing entered. */
    fun beganAgain() {
        Subroutine.entries.forEach { entry(it)?.restart() }
    }

    companion object {

        /**
         * Handshake's rhythm, in beats.
         *
         * The design rates it nowhere; five is the design fixture's own count, carried across so
         * that the ported screen keeps drawing what it drew. Presentation, not balance.
         */
        const val HANDSHAKE_BEATS = 5

        /**
         * Replay's dots. The roster says **3–5**; four is the middle of that and lays out evenly
         * in two columns. The house picks the real one.
         */
        const val REPLAY_DOTS = 4

        /** Which cell the parity grid corrupts. A fixture seed; in play the house sends the grid. */
        const val PARITY_SEED = 19

        /**
         * **How many fingers Short asks for.**
         *
         * The design says *hold N fingers* and never says what N is. Three is a presentation
         * fixture — enough that it is a deliberate arrangement of a hand rather than a press,
         * few enough to make one-handed on a phone held at an angle. In play the house sends it.
         *
         * It lives here, on the model, and **not on [HoldEntry]**: an entry that knew the asked-for
         * count could subtract it from the count it is holding, and then the phone would be one
         * careless edit away from grading the answer. See [HoldEntry].
         */
        const val SHORT_FINGERS = 3

        /**
         * How far Jam's shape may travel from where the house opened it, in steps.
         *
         * A stop so the shape cannot be driven off the screen, deliberately far outside anything
         * a player would aim at. Not a difficulty setting — see [ScalarEntry].
         */
        const val JAM_REACH = 16

        /** Which graph Signal Trace draws. A fixture seed; in play the house sends the wiring. */
        const val TRACE_SEED = 5

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
            val wiring = SignalGraph.of(TRACE_SEED)
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
