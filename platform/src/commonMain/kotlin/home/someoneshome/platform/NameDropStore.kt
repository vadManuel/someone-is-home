package home.someoneshome.platform

/**
 * **This install has been told NameDrop is off** (D-248): the player said so on the setup step.
 *
 * The smallest of this module's stores: one fact, with no text worth keeping, so the file's
 * existence is the answer. Nothing here knows what NameDrop is, and nothing here can: iOS has no
 * API that reads AirDrop's *Bringing Devices Together* switch, which is the whole reason the
 * player is asked.
 *
 * ### A failed save does NOT throw
 *
 * [saveResidentName]'s bargain exactly: a phone that could not remember this plays a perfect round,
 * and the one consequence is that the step is shown once more at the next launch.
 */
expect fun saveNameDropOff()

/** True once [saveNameDropOff] has landed on this install. */
expect fun loadNameDropOff(): Boolean

/** Forget it. Nothing in the app calls this; it is here so a test can start from a clean phone. */
expect fun clearNameDropOff()
