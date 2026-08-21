package home.someoneshome.platform

/**
 * Where the host's homes live between evenings.
 *
 * Everything [saveHousePlan] says applies here word for word and is not repeated: text in and text
 * out because the format belongs to `model`; atomic writes so a host closing the app mid-save
 * leaves the old list or the new one and never half of either; and a failed save **throws**,
 * because a return value nobody reads is how every save silently did nothing once already (D-087).
 *
 * ### One file for the list, not one file per home
 *
 * The plan and the map are separate files because they are *lost differently* — a host repaints a
 * wall without re-registering forty cards. The homes are not: deleting one, renaming one and
 * adding one are all the same act on the same list, and a directory of files would need its own
 * index to say what order the host sees them in and its own answer for a file the index does not
 * name. One file, rewritten whole, atomically, is the version of this with no half-states.
 */
expect fun saveSavedHomes(text: String)

/** Every stored home, or null if this phone has never kept one. Never a partial read. */
expect fun loadSavedHomes(): String?

/** Forget every stored home. */
expect fun clearSavedHomes()

/**
 * A save that did not happen.
 *
 * Carries the path for the reason [HouseMapNotSaved] does: no space and a directory that is not
 * there are told apart by looking at it, and a host reporting "it didn't save" cannot.
 */
class SavedHomesNotSaved(val path: String) : IllegalStateException(
    "the saved homes were not written to $path. Do not report success to the host; the home " +
        "they just walked is not on this phone."
)
