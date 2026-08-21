package home.someoneshome.app

import home.someoneshome.platform.loadSavedHomes
import home.someoneshome.platform.saveSavedHomes
import home.someoneshome.ui.HomeStore

/**
 * **The phone's own file, handed to `ui` as the two functions it is allowed to know about.**
 *
 * This is why `app` exists as a module: `ui` cannot see `platform` and `platform` cannot see
 * `ui`, so the one place the screens and the filesystem can be introduced to each other is here.
 * The interface is text in and text out because the format belongs to `model`, and neither side
 * holds a second opinion about it.
 *
 * A failed write **throws**, all the way from `saveSavedHomes`. The model above catches it and
 * says so on the screen rather than letting the app die with a house in its hand — that is D-087
 * kept where it matters: the host is told, and success is never reported for a save that did not
 * happen.
 */
class SavedHomesDocument : HomeStore {
    override fun read(): String? = loadSavedHomes()
    override fun write(text: String) = saveSavedHomes(text)
}
