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
    Short("SHORT", SubroutineTier.Short, LightSignature.Dark),
    SignalTrace("SIGNAL TRACE", SubroutineTier.Medium, LightSignature.Medium),
    Jam("JAM", SubroutineTier.Medium, LightSignature.Medium),
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
     * How many elements the house asked for.
     *
     * **A presentation fixture here, and the house's in play**, like every number in
     * [Flow.autoAdvance]. It is not a balance value and nothing locks it at arming: the design
     * gives Replay 3–5 dots and rates Handshake's rhythm nowhere, so a single number written here
     * would be an invention wearing a tuning value's clothes.
     */
    val length: Int,
) {

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

    /** A fresh instance of the same Subroutine: the marker was scanned again. */
    fun restart() {
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
class ChoiceEntry {

    var choice: Int? by mutableStateOf(null)
        private set

    var handedOver: Int? by mutableStateOf(null)
        private set

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

    fun restart() {
        choice = null
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
 * thing you can see: three screens, three entries, and no role anywhere in this file.
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
) {

    /** A marker scanned again opens the same Subroutine with nothing entered. */
    fun beganAgain() {
        handshake.restart()
        replay.restart()
        parity.restart()
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
         * **The fixture: a phone part-way through each of the three.**
         *
         * Every render and every rendering test gets this, for the reason [MeetingModel.sample]
         * exists — a Subroutine nobody has touched is a screen with no echo on it, and a test
         * looking at one proves nothing about the thing being built. The Handshake count is the
         * design fixture's own three-of-five; Replay is two dots in; the parity grid has a cell
         * chosen and **not** submitted, so both halves of that control are on screen at once.
         */
        fun sample(): SubroutineModel = SubroutineModel().apply {
            handshake.enter(0)
            handshake.enter(1)
            handshake.enter(2)
            replay.enter(2)
            replay.enter(0)
            parity.choose(14)
        }
    }
}

/**
 * The Subroutine entries the three Subroutine screens draw.
 *
 * Provided by [Screen] beside [LocalMeeting] and for the same reason: a test that taps four dots
 * must not leave the next render looking at a phone that has already handed its sequence over.
 */
val LocalSubroutine: ProvidableCompositionLocal<SubroutineModel> =
    staticCompositionLocalOf { SubroutineModel.sample() }
