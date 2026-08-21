package home.someoneshome.platform

/**
 * **Where the host's homes live between evenings — the only setup file there is.**
 *
 * There were three. `house-map.txt` and `house-plan.txt` were written first, one per half of the
 * setup walk, and then a home turned out to be *both halves under a name*: `saved-homes.txt`
 * carries the plan and the registrations inside each home. The other two stores kept working and
 * nothing called them, which is two encoders for data with one writer — the drift hazard this
 * project keeps writing down — so they are gone, and this KDoc carries the doctrine they held.
 *
 * ### Text in, text out — this layer does not know what a home is
 *
 * The format belongs to `model`; this stores a string. That is not fastidiousness: the stored form
 * and the **export/share** form are deliberately the same text (game-architecture.md:419), so
 * handing a house to another host is handing them the file. A store that understood the format
 * would grow a second encoder, and the two would drift.
 *
 * ### What "surviving reinstall" actually means, stated precisely
 *
 * The architecture's answer is *platform backup plus an explicit export path*. The file is written
 * where the OS includes it in device backup, so a phone restored from backup keeps its houses.
 *
 * **A bare reinstall on a wiped device with no backup restore does not keep them, and nothing here
 * can change that.** That is what the export path is for, and it is why the export format being
 * human-transferable text is load-bearing rather than a convenience.
 *
 * ### Writes are atomic
 *
 * A host closing the app mid-save must not leave a truncated file. A half-written one would be
 * refused by the reader — which is the right behaviour and still costs fifteen minutes of walking
 * around a dark house.
 *
 * ### A failed save THROWS, and that is deliberate (D-087)
 *
 * The first version of this returned Unit and discarded the write's result. Every save failed
 * silently and every load came back null — caught only because a test looked at what came back. In
 * a host's hands that is fifteen minutes of walking a dark house, gone, with the app showing no
 * sign anything went wrong until the next evening.
 *
 * A Boolean return would be dropped the same way, so it throws. **This is host-side setup, not a
 * live round** — rule 6 keeps errors away from a *player* mid-game, and a host who cannot save
 * their house needs to be told immediately, while the cards are still in their hand.
 *
 * ### One file for the list, not one file per home
 *
 * Deleting one, renaming one and adding one are all the same act on the same list, and a directory
 * of files would need its own index to say what order the host sees them in and its own answer for
 * a file the index does not name. One file, rewritten whole, atomically, is the version of this
 * with no half-states.
 */
expect fun saveSavedHomes(text: String)

/** Every stored home, or null if this phone has never kept one. Never a partial read. */
expect fun loadSavedHomes(): String?

/** Forget every stored home. */
expect fun clearSavedHomes()

/**
 * The directory the homes live in.
 *
 * Public because the export/share path needs it, and because the first-launch case — the directory
 * not existing yet — is otherwise untestable: it is true exactly once per install, and a test that
 * has already run has destroyed the condition it wanted to check.
 */
expect fun savedHomesStorageDirectory(): String

/**
 * A save that did not happen.
 *
 * Carries the path, because the two realistic causes — no space, and a directory that is not there
 * — are told apart by looking at it, and a host reporting "it didn't save" from a dark house cannot
 * tell you which.
 */
class SavedHomesNotSaved(val path: String) : IllegalStateException(
    "the saved homes were not written to $path. Do not report success to the host; the home " +
        "they just walked is not on this phone."
)
