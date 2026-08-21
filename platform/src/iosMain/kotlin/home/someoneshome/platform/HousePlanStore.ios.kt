package home.someoneshome.platform

/**
 * The plan sits beside the map in the same backed-up directory, under its own name.
 *
 * No `housePlanStorageDirectory()`: there is one setup directory and
 * [houseMapStorageDirectory] already names it. A second accessor for the same path is a second
 * thing to keep in step.
 */
private const val FILE_NAME = "house-plan.txt"

actual fun saveHousePlan(text: String) {
    val path = documentPath(FILE_NAME)
    if (!writeDocument(path, text)) throw HousePlanNotSaved(path)
}

actual fun loadHousePlan(): String? = readDocument(documentPath(FILE_NAME))

actual fun clearHousePlan() = deleteDocument(documentPath(FILE_NAME))
