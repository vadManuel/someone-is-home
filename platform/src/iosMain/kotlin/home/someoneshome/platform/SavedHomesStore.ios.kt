package home.someoneshome.platform

/**
 * The homes sit beside the map and the plan in the same backed-up directory, under their own name.
 *
 * [houseMapStorageDirectory] already names the one setup directory; a second accessor for the same
 * path is a second thing to keep in step.
 */
private const val FILE_NAME = "saved-homes.txt"

actual fun saveSavedHomes(text: String) {
    val path = documentPath(FILE_NAME)
    if (!writeDocument(path, text)) throw SavedHomesNotSaved(path)
}

actual fun loadSavedHomes(): String? = readDocument(documentPath(FILE_NAME))

actual fun clearSavedHomes() = deleteDocument(documentPath(FILE_NAME))
