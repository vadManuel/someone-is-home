package home.someoneshome.platform

private const val FILE_NAME = "house-map.txt"

actual fun houseMapStorageDirectory(): String = documentsDirectory()

actual fun saveHouseMap(text: String) {
    val path = documentPath(FILE_NAME)
    if (!writeDocument(path, text)) throw HouseMapNotSaved(path)
}

actual fun loadHouseMap(): String? = readDocument(documentPath(FILE_NAME))

actual fun clearHouseMap() = deleteDocument(documentPath(FILE_NAME))
