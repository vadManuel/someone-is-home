package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **Whether this phone's owner has said NameDrop is off** (D-248, provisional: owner to confirm).
 *
 * ### Why there is a step for a setting the app cannot touch
 *
 * iOS NameDrop offers to swap contacts when two unlocked iPhones are held close, and its animation
 * **lights both screens**. In this game phones meet on purpose, in the dark (the Revoke and the
 * report are contact), so the OS would be authoring light the rules never sent: rule 5's spirit,
 * met in hardware. On two real iPhones (2026-11-16) it fired twice in about forty touches.
 *
 * An app cannot switch it off and cannot read whether it is on; there is no API for either. So the
 * player switches it off themselves, at setup, in the light (D-161's place), and says they did.
 *
 * ### Self-attested, once per install
 *
 * Nothing can check the answer, so asking again on every launch would only nag: the answer cannot
 * have changed in any way this phone could see. [confirmed] is stored the first time DONE is
 * pressed, and after that the step is reached only from the PERMISSIONS screen's NAMEDROP row.
 *
 * ### `ui` cannot see `platform`, so the file arrives as [NameDropStore]
 *
 * [NameStore]'s shape and its bargain: **a failed write is dropped**. A phone that could not
 * remember the answer plays perfectly; the whole consequence is that it asks once more next launch.
 */
interface NameDropStore {
    /** True once this install has been told NameDrop is off. */
    fun read(): Boolean

    /** Remember that it has been. May fail silently; see the class KDoc. */
    fun write()
}

/** The store every test and render gets: this session and nothing after it. Not a no-op. */
class MemoryNameDropStore(private var off: Boolean = false) : NameDropStore {
    override fun read(): Boolean = off
    override fun write() { off = true }
}

/**
 * **The NameDrop step's one fact, and the one decision it makes.**
 *
 * Read once, when the app builds it: this is a file, not an OS grant, so there is nothing a later
 * read could learn that the first did not.
 */
class NameDropModel(private val store: NameDropStore = MemoryNameDropStore()) {

    /** The player pressed DONE on this install. Never true because of anything the OS said. */
    var confirmed: Boolean by mutableStateOf(store.read())
        private set

    /** DONE: remembered here at once, and on the phone as far as the phone will take it. */
    fun confirm() {
        confirmed = true
        store.write()
    }

    /**
     * **Where a phone goes once its permissions are done** (D-248): the NameDrop step until it has
     * been confirmed, the join screen after.
     *
     * The single question both ways past the PERMISSIONS screen ask — its CONTINUE, and D-235's
     * skip when the OS already grants everything — so the skip cannot walk a fresh install past
     * the step, and a confirmed one is never stopped at it.
     */
    val pastPerms: ScreenId get() = if (confirmed) ScreenId.Join else ScreenId.NameDrop

    companion object {
        /**
         * **A phone whose setup is done**, for a render and a screenshot: [PermsModel.sample]'s
         * reason. Every screen after this one is reached through it.
         */
        fun sample(): NameDropModel = NameDropModel(MemoryNameDropStore(off = true))
    }
}

/** The NameDrop answer, for the two setup screens that draw it. */
val LocalNameDrop: ProvidableCompositionLocal<NameDropModel> = staticCompositionLocalOf { NameDropModel() }
