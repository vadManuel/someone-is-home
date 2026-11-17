package home.someoneshome.platform

/** The answer is that the file is there; what it says is for a person reading the directory. */
private const val FILE_NAME = "namedrop-off.txt"

/** Dropped on failure, here rather than at the caller: see [saveNameDropOff]. */
actual fun saveNameDropOff() {
    writeDocument(documentPath(FILE_NAME), "OFF")
}

actual fun loadNameDropOff(): Boolean = readDocument(documentPath(FILE_NAME)) != null

actual fun clearNameDropOff() = deleteDocument(documentPath(FILE_NAME))
