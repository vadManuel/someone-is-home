package home.someoneshome.app

import home.someoneshome.platform.loadNameDropOff
import home.someoneshome.platform.saveNameDropOff
import home.someoneshome.ui.NameDropStore

/**
 * **The NameDrop answer, on this phone's own filesystem** (D-248): [ResidentNameDocument]'s shape,
 * for the same reason. `ui` cannot see `platform`, so this is where the setup step and the file
 * are introduced. A failed write is dropped below this line, by the same bargain.
 */
class NameDropDocument : NameDropStore {
    override fun read(): Boolean = loadNameDropOff()
    override fun write() = saveNameDropOff()
}
