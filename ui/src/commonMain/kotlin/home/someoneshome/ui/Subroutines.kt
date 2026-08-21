package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

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
 * | 4 | Sniff | Short | Haptic counting | The phone buzzes N times; tap N | Dark |
 * | 5 | Deallocate | Short | Counting | Unequal columns of dots; tap to even them out | Bright |
 * | 6 | Drift | Medium | Tracking under occlusion | A dot drifts behind occluders; tap where it is now | Medium |
 * | 7 | Short | Short | Gross motor | Hold N fingers on the screen for two seconds | Dark |
 * | 8 | Signal Trace | Medium | Pathfinding | Tap node-to-node from source to sink | Medium |
 * | 9 | Jam | Medium | Convergence | Tap +/− until two shapes overlap | Medium |
 * | 10 | Handshake | Medium | Haptic echo | The phone buzzes a pattern; you tap it back | Dark |
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
 */
enum class Subroutine(
    val label: String,
    val tier: SubroutineTier,
    val light: LightSignature,
    val screen: ScreenId? = null,
) {
    Replay("REPLAY", SubroutineTier.Short, LightSignature.Bright, ScreenId.SubReplay),
    Interrupt("INTERRUPT", SubroutineTier.Short, LightSignature.Medium),
    ParityCheck("PARITY CHECK", SubroutineTier.Short, LightSignature.Bright, ScreenId.SubParity),
    Sniff("SNIFF", SubroutineTier.Short, LightSignature.Dark),
    Deallocate("DEALLOCATE", SubroutineTier.Short, LightSignature.Bright),
    Drift("DRIFT", SubroutineTier.Medium, LightSignature.Medium),
    Short("SHORT", SubroutineTier.Short, LightSignature.Dark, ScreenId.SubShort),
    SignalTrace("SIGNAL TRACE", SubroutineTier.Medium, LightSignature.Medium, ScreenId.SubTrace),
    Jam("JAM", SubroutineTier.Medium, LightSignature.Medium, ScreenId.SubJam),
    Handshake("HANDSHAKE", SubroutineTier.Medium, LightSignature.Dark, ScreenId.SubHandshake);

    companion object {

        /** The Subroutine a screen belongs to, or null where the screen is not one of theirs. */
        fun on(screen: ScreenId): Subroutine? = entries.firstOrNull { it.screen == screen }

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
        Subroutine.Interrupt, Subroutine.Sniff, Subroutine.Deallocate, Subroutine.Drift -> null
    }

    /**
     * **A finger landing somewhere, and that is all it is.**
     *
     * What [at] means is the screen's own vocabulary, because the screens have nothing in common
     * to say: an element for a sequence, a cell for the parity grid, **how many fingers are now on
     * the glass** for Short, a signed step for Jam, a node for Signal Trace. None of them is
     * compared with anything.
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
            val wiring = SignalGraph.of(TRACE_SEED)
            trace.walk(wiring.source)
            wiring.joinedTo(wiring.source).firstOrNull()?.let { trace.walk(it) }
        }
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
