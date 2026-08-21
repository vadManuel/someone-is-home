package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.CellRect
import home.someoneshome.model.Floor
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.SavedHome
import home.someoneshome.model.SavedHomesText
import home.someoneshome.model.PlanRoom as PaintedRoom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **Where the homes are kept, as far as `ui` is allowed to know.**
 *
 * `ui` cannot see `platform` — `app` is the one module that sees both — so the phone's filesystem
 * arrives here as two functions and no detail. Text in and text out, because the format belongs to
 * `model` and this layer has no business holding a second opinion about it.
 *
 * [write] may throw, and callers must treat that as a save that did not happen (D-087). A store
 * that swallowed a failed write is how every save silently did nothing for a whole build once
 * already.
 */
interface HomeStore {
    fun read(): String?
    fun write(text: String)
}

/**
 * The store the screens get when nobody wired a real one: this session, and nothing after it.
 *
 * Every test, the desktop preview and every screen render use this. It is deliberately not a
 * no-op — a store that dropped writes would let a test claim a home was saved while proving
 * nothing at all about saving.
 */
class MemoryHomeStore(private var text: String? = null) : HomeStore {
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

/**
 * **The host's homes: the list, the one they are looking at, and the file underneath.**
 *
 * Sits beside [PanelState] for the reason [HomeEditorModel] does — `PanelState` is flat and inert
 * and every field of it is already decided at the effect boundary, and a list a host is adding to
 * and deleting from is neither. Like the editor it answers no game question and never will: a
 * saved home is host-side setup drawn in the light, weeks before anybody plays, and nothing in it
 * is read during a round.
 *
 * ### Every change is written before it is shown
 *
 * [homes] is only replaced once the store has taken the new text. A list that updated first and
 * wrote afterwards would show the host a home that is not on their phone — which is exactly the
 * failure D-087 was written about, arriving one layer up.
 *
 * ### A file that cannot be read is never overwritten
 *
 * If the stored text is refused — a format version this build does not know, a corrupted file —
 * the model comes up empty and [unreadable], and **every write is refused while it holds**.
 * Starting empty and saving over the top would delete every house on the phone to make room for
 * one, silently, which is the shape of failure this project is organised around refusing. The
 * host is told on the screen where their homes should have been.
 */
class SavedHomesModel(private val store: HomeStore = MemoryHomeStore()) {

    /** Most recently saved first — the order the host sees, and the order the file holds. */
    var homes: List<SavedHome> by mutableStateOf(emptyList())
        private set

    /** The home the host opened, or none while they are mapping a new one. */
    var openName: String? by mutableStateOf(null)
        private set

    /**
     * The last refusal, in the host's words.
     *
     * Cleared by the next thing they do. Rule 6 — errors silent to the player — is about a player
     * mid-round; a host saving a house in a lit room is owed the reason it did not save.
     */
    var refusal: String? by mutableStateOf(null)
        private set

    /** The stored homes could not be read, and nothing may be written over them. */
    var unreadable: Boolean by mutableStateOf(false)
        private set

    init {
        val text = store.read()
        if (text != null) {
            try {
                homes = SavedHomesText.read(text)
            } catch (e: IllegalArgumentException) {
                // Both the format's own refusal and the last-ditch `require`s behind it land here.
                unreadable = true
                refusal = e.message
            }
        }
    }

    val open: SavedHome? get() = homes.firstOrNull { it.name == openName }

    val isEmpty: Boolean get() = homes.isEmpty()

    fun openHome(name: String) {
        if (homes.none { it.name == name }) return
        openName = name
        refusal = null
    }

    /** MAP A NEW HOME: nothing is open, so a save adds rather than replaces. */
    fun closeHome() {
        openName = null
        refusal = null
    }

    /**
     * Save what the host has been painting, under this name.
     *
     * **One operation, because there is one act.** With a home open this replaces it — under a
     * different name, that is a rename, and the cards and the plan come with it. With none open it
     * adds. Returning whether it landed is what lets the caller stay put: a screen that navigated
     * away from a refused save would leave the host looking at a list that does not have their
     * house in it.
     */
    fun save(home: SavedHome): Boolean {
        refusal = null
        if (unreadable) {
            refusal = CANNOT_WRITE
            return false
        }
        val taken = homes.firstOrNull { it.name == home.name && it.name != openName }
        if (taken != null) {
            refusal = "A HOME IS ALREADY CALLED ${home.name}"
            return false
        }
        val rest = homes.filterNot { it.name == home.name || it.name == openName }
        if (!commit(listOf(home) + rest)) return false
        openName = home.name
        return true
    }

    /** Throw the open home away. Fifteen minutes of walking, gone, which is why it is held. */
    fun deleteOpen(): Boolean {
        refusal = null
        val name = openName ?: return false
        if (unreadable) {
            refusal = CANNOT_WRITE
            return false
        }
        if (!commit(homes.filterNot { it.name == name })) return false
        openName = null
        return true
    }

    /**
     * A second copy of the open home, opened.
     *
     * The same house on a different evening — a floor closed off, the terminal somewhere else —
     * without walking it again. The copy is what the host is now looking at, because duplicating
     * and staying on the original is a list you cannot see changing.
     */
    fun duplicateOpen(): Boolean {
        refusal = null
        val home = open ?: return false
        if (unreadable) {
            refusal = CANNOT_WRITE
            return false
        }
        val copy = home.renamedTo(freeName(baseOf(home.name)))
        if (!commit(listOf(copy) + homes)) return false
        openName = copy.name
        return true
    }

    /** Say why something did not happen, when the caller is the one that knows. */
    fun refuse(why: String) {
        refusal = why
    }

    fun clearRefusal() {
        refusal = null
    }

    /**
     * A name no stored home holds.
     *
     * `HOME 1` for a new one and `THE BUNGALOW 2` for a copy — obviously provisional, which is
     * what sends the host to the field to say what the place is really called. Same reasoning as
     * the editor's `ROOM 2`.
     */
    /**
     * The name without the number a copy was given.
     *
     * A copy of THE BUNGALOW 2 is THE BUNGALOW 3, not THE BUNGALOW 2 2. A host who genuinely
     * calls a place FLAT 2 gets FLAT 3, which is the same answer they would have typed.
     */
    private fun baseOf(name: String): String {
        val space = name.lastIndexOf(' ')
        val trailing = if (space > 0) name.substring(space + 1).toIntOrNull() else null
        return if (trailing == null) name else name.substring(0, space)
    }

    fun freeName(base: String = "HOME"): String =
        generateSequence(if (base == "HOME") 1 else 2) { it + 1 }
            .map { "$base $it" }
            .first { candidate -> homes.none { it.name == candidate } }

    /**
     * Write first, show second.
     *
     * A failed write leaves [homes] exactly as it was, so the list on screen is the list on the
     * phone. The refusal is the host's, not a stack trace: what they need to know is that the
     * house they just walked is not stored, while the cards are still in their hand.
     */
    private fun commit(next: List<SavedHome>): Boolean {
        if (runCatching { store.write(SavedHomesText.write(next)) }.isFailure) {
            refusal = "THIS PHONE DID NOT SAVE IT"
            return false
        }
        homes = next
        return true
    }

    companion object {

        private const val CANNOT_WRITE =
            "THE STORED HOMES COULD NOT BE READ. NOTHING WILL BE WRITTEN OVER THEM."

        /**
         * Three homes, the design's own, for every render that is not a real phone.
         *
         * THE BUNGALOW is the editor's fixture converted rather than restated — the same promise
         * [HomeEditorModel.bungalow] keeps about the live map, one storey down: a home that read
         * differently in the list from the way it draws in the editor would be a disagreement
         * nobody could see from inside the app. The other two exist because a list with one row
         * is not a list, and their counts are the ones the design wrote on that screen.
         */
        fun sample(): SavedHomesModel = SavedHomesModel(MemoryHomeStore(SAMPLE))
            // Open, because every host-setup screen the design drew was drawn with a home open —
            // the detail screen and the delete screen are about one, and a screen about nothing
            // is not the state they were designed in.
            .apply { openHome(HomeEditorModel.BUNGALOW) }

        /**
         * Written once, parsed per model.
         *
         * Every screen render in every test builds one of these, and building the bungalow to
         * throw it at a formatter each time is work with one answer. The parse stays, because the
         * parse is the thing every other caller does.
         */
        private val SAMPLE: String by lazy {
            SavedHomesText.write(
                listOf(
                    HomeEditorModel.bungalow().asSavedHome(),
                    sampleHome(
                        "MUM & DAD'S",
                        terminal = "UTILITY",
                        "GROUND" to listOf("KITCHEN", "LIVING", "HALL", "UTILITY", "GARAGE"),
                        "UPPER" to listOf("BED 1", "BED 2", "BATH 1", "LANDING"),
                    ),
                    sampleHome(
                        "THE LAKE PLACE",
                        terminal = "BOOT ROOM",
                        "GROUND" to
                            listOf("KITCHEN", "LIVING", "DECK", "BATH 1", "BED 1", "BOOT ROOM"),
                    ),
                )
            )
        }

        /**
         * A plausible house, laid out as blocks rather than drawn.
         *
         * These two are seen as rows and counts. The geometry only has to be a real plan — rooms
         * that hold cells, do not overlap and can be opened in the editor — so it is generated,
         * five to a row, instead of being hand-painted like the bungalow.
         */
        private fun sampleHome(
            name: String,
            terminal: String,
            vararg floors: Pair<String, List<String>>,
        ): SavedHome {
            val plan = HousePlan.of(
                floors.map { (storey, rooms) ->
                    Floor(
                        storey,
                        rooms.mapIndexed { i, room ->
                            PaintedRoom(
                                Room(room, RoomKind.Room),
                                listOf(CellRect(x = (i % 5) * 2, y = (i / 5) * 3, width = 2, height = 3)),
                            )
                        },
                    )
                }
            )
            val cards = plan.rooms.filter { it.name != terminal }.take(4)
            // The ids are the home's initial and a number, because a fixture whose card ids are
            // noise is a fixture nobody can follow on a screenshot. Seven characters, and out of
            // the alphabet a printed card is allowed to carry — no space.
            val stem = name.filter { it in MarkerShapes.ALPHABET }.take(4).padEnd(4, '-')
            return SavedHome(
                name = name,
                plan = plan,
                map = HouseMap.of(
                    cards.mapIndexed { i, room ->
                        Registration(sampleCard(MarkerShapes.registrable[i * 3], stem, i + 1), Room(room.name))
                    },
                    Registration(sampleCard(MarkerShapes.TERMINAL, stem, 0), Room(terminal)),
                ),
            )
        }

        private fun sampleCard(shape: MarkerShape, stem: String, number: Int) = MarkerCard(
            version = CardPayload.VERSION,
            shape = shape,
            id = MarkerId(stem + number.toString().padStart(3, '0')),
        )
    }
}

/**
 * The homes the host-setup screens list.
 *
 * Provided by [Screen] beside [LocalEditor] and for the same reason: a test that renders one
 * screen gets its own, so a test that deletes a home cannot leave the next one with a shorter
 * list than it expected.
 */
val LocalHomes: ProvidableCompositionLocal<SavedHomesModel> =
    staticCompositionLocalOf { SavedHomesModel.sample() }

/**
 * **What this home cost to walk, said in minutes.**
 *
 * The one number on the delete screen, and the only argument against deleting that is about the
 * host rather than about the data: fifteen minutes of walking a real house in the light, room by
 * room, scanning a card in each. The design wrote *"about fifteen minutes"* against a home of
 * eleven rooms and nine markers, so a room is a minute — walk in, paint it, name it — and a card
 * is half of one.
 *
 * **This is an estimate and it is presentation, not balance.** Nothing is locked at arming and
 * nothing reads it; it exists so the host is told the cost in the unit they actually spent.
 * Rounded to the nearest five so it reads as the estimate it is, and never below five, because a
 * one-room house still cost somebody an evening's decision to make it.
 */
fun walkMinutes(rooms: Int, markers: Int): Int {
    val raw = rooms + (markers + 1) / 2
    return maxOf(5, (raw + 2) / 5 * 5)
}

/**
 * The same span in words, unit included, because the sentence around it is prose and not a
 * readout.
 *
 * The unit comes back with the number so that the one span that is not counted in minutes — an
 * hour — does not have "minutes" appended to it by a caller that assumed.
 */
fun walkedInWords(rooms: Int, markers: Int): String = when (val minutes = walkMinutes(rooms, markers)) {
    5 -> "five minutes"
    10 -> "ten minutes"
    15 -> "fifteen minutes"
    20 -> "twenty minutes"
    25 -> "twenty-five minutes"
    30 -> "half an hour"
    35 -> "thirty-five minutes"
    40 -> "forty minutes"
    45 -> "three quarters of an hour"
    50 -> "fifty minutes"
    55 -> "fifty-five minutes"
    60 -> "an hour"
    else -> "$minutes minutes"
}
