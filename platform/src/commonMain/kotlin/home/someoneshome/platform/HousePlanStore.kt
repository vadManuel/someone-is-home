package home.someoneshome.platform

/**
 * Where the painted house lives between evenings — the other half of what [saveHouseMap] stores.
 *
 * Everything the house map's store says applies here word for word, and is not repeated: text in
 * and text out because the format belongs to `model` and the stored form is also the export form;
 * atomic writes so a host closing the app mid-save leaves the old plan or the new one and never
 * half of either; and a failed save **throws**, because a return value nobody reads is how every
 * save silently did nothing once already.
 *
 * ### A separate file, not a section of the map file
 *
 * The plan and the map are written at different moments and lost differently. A host repaints a
 * wall without re-registering forty cards, and re-registers a torn card without repainting
 * anything. One file means every save rewrites both, so a plan can be destroyed by a save that
 * was only ever about a card — and two formats sharing one file means one version number
 * answering two questions.
 */
expect fun saveHousePlan(text: String)

/** The stored plan, or null if none was ever painted. Never a partial read. */
expect fun loadHousePlan(): String?

/** Forget the stored plan. The host is painting a different house. */
expect fun clearHousePlan()

/**
 * A save that did not happen.
 *
 * Carries the path for the same reason [HouseMapNotSaved] does: no space and a directory that is
 * not there are told apart by looking at it, and a host reporting "it didn't save" cannot.
 */
class HousePlanNotSaved(val path: String) : IllegalStateException(
    "the house plan was not written to $path. The setup walk is not stored; do not report " +
        "success to the host."
)
