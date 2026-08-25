package home.someoneshome.platform

/** One line, in the one backed-up directory, under its own name. */
private const val FILE_NAME = "resident-name.txt"

/**
 * **The one store in this package whose failed write is dropped**, and the drop is here rather than
 * at the caller so that no second caller can be given a different bargain. See [saveResidentName],
 * where the asymmetry with [saveSavedHomes] and [saveSeatToken] is argued.
 */
actual fun saveResidentName(text: String) {
    writeDocument(documentPath(FILE_NAME), text)
}

actual fun loadResidentName(): String? = readDocument(documentPath(FILE_NAME))

actual fun clearResidentName() = deleteDocument(documentPath(FILE_NAME))
