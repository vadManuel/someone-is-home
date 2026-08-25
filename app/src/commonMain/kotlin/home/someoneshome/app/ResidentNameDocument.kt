package home.someoneshome.app

import home.someoneshome.platform.loadResidentName
import home.someoneshome.platform.saveResidentName
import home.someoneshome.ui.NameStore

/**
 * **This phone's own resident name, on this phone's own filesystem** — [SavedHomesDocument]'s
 * shape, one file smaller.
 *
 * Here for the reason that class is here: `ui` cannot see `platform` and `platform` cannot see
 * `ui`, so `app` is the one place the screens and the disk can be introduced. The interface is text
 * in and text out because neither side holds an opinion about the other's format, and here there is
 * barely a format to hold one about.
 *
 * **A failed write does not throw**, and that is deliberate rather than an oversight — the whole
 * argument is at `saveResidentName`, which drops it. This class does not add a second opinion on top
 * of it, and it must not: a phone that could not remember a name plays a perfect round, and the one
 * consequence is that it asks again.
 */
class ResidentNameDocument : NameStore {
    override fun read(): String? = loadResidentName()
    override fun write(text: String) = saveResidentName(text)
}
