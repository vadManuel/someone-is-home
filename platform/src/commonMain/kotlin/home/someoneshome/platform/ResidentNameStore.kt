package home.someoneshome.platform

/**
 * **What the owner of this phone calls themselves, between launches** (the owner's E-PAY-6 ruling:
 * *it should be saved*).
 *
 * The fourth of this module's small text stores and deliberately the smallest — one line, this
 * phone's own, about the person holding it. Text in, text out, exactly as [saveSavedHomes] and
 * [saveSeatToken] are: this layer stores a string and does not know what a resident is.
 *
 * ### It is not the names the house holds, and nothing about D-115 moves
 *
 * A host's `LobbyDesk` holds every seat's name for one round and drops them all when it ends, and a
 * client re-announces its own on every re-seating rather than the house remembering. Both stay
 * exactly as they were. What is here is the *typist's* copy of the *typist's* name, on the typist's
 * phone — never somebody else's, never a seat, never a line.
 *
 * ### A failed save does NOT throw, and that is the one way it differs from its neighbours
 *
 * [saveSeatToken] throws because a token that silently failed to persist is a phone that plays a
 * whole round normally and is unresumable at the moment resuming matters. [saveSavedHomes] throws
 * because a house nobody can find is fifteen minutes of walking gone. Neither argument applies here:
 * a phone that could not write this plays perfectly, and the entire consequence is that it asks for
 * a name again next time — which is what every launch before this file did anyway. Reporting it
 * would put a refusal on a screen where nothing was refused.
 *
 * That asymmetry is written down at the caller as well (`LobbyModel.nameResident`), because a rule
 * that only exists in one of the two places it applies is a rule somebody will copy the wrong way.
 *
 * ### The one line is not here and could not be
 *
 * `OneLineNeverPersistedTest` sweeps this module's storage for the text a player types on the
 * arming screen. This file writes a name and has no way to reach one — the promise on that screen
 * is *seen by the house only, deleted when the round ends*, and the name being storable does not
 * make anything beside it storable.
 */
expect fun saveResidentName(text: String)

/** The stored name, or null if this phone has never been given one. */
expect fun loadResidentName(): String?

/** Forget it. Nothing in the app calls this; it is here so a test can start from a clean phone. */
expect fun clearResidentName()
