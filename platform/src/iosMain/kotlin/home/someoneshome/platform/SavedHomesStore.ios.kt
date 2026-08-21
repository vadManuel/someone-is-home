package home.someoneshome.platform

/** The homes are the one setup file, in the one backed-up directory, under their own name. */
private const val FILE_NAME = "saved-homes.txt"

actual fun savedHomesStorageDirectory(): String = documentsDirectory()

actual fun saveSavedHomes(text: String) {
    val path = documentPath(FILE_NAME)
    if (!writeDocument(path, text)) throw SavedHomesNotSaved(path)
}

actual fun loadSavedHomes(): String? = readDocument(documentPath(FILE_NAME))

actual fun clearSavedHomes() = deleteDocument(documentPath(FILE_NAME))
