package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The name a phone remembers about its own owner** (E-PAY-6: *it should be saved*).
 *
 * The store, on the real filesystem, at the layer where "survives a relaunch" is a fact rather than
 * an argument: a process that reads back what a previous process wrote is what a relaunch *is*, and
 * `LobbyExitTest` can only test the seam above it.
 */
class ResidentNameStoreTest {

    @BeforeTest
    fun start() = clearResidentName()

    @AfterTest
    fun clean() = clearResidentName()

    /**
     * **A phone that has never been given a name says so**, rather than answering with a blank.
     *
     * The distinction is the gate: `LobbyModel` reads null as *ask for one* and would read `""` the
     * same way today, but a store that invented an empty string would make *never named* and *named
     * with nothing* the same observation on the one field that now refuses a seat.
     */
    @Test
    fun aPhoneThatHasNeverBeenNamedHoldsNothing() {
        assertNull(loadResidentName())
    }

    @Test
    fun aStoredNameComesBackVerbatim() {
        saveResidentName("MARCUS")
        assertEquals("MARCUS", loadResidentName())
    }

    /** Typing replaces rather than accumulates — the field is one name, not a history of them. */
    @Test
    fun theLastNameWrittenIsTheOneThatComesBack() {
        saveResidentName("MARCU")
        saveResidentName("MARCUS")
        assertEquals("MARCUS", loadResidentName())
    }

    /**
     * A name is a person's, so nothing about it is normalised here: no trim, no case, no substitution.
     * The bounds are the wire's and are applied where the wire is (`LobbyBody.Naming.LIMIT`).
     */
    @Test
    fun nothingIsNormalisedOnTheWayThrough() {
        saveResidentName("de la Cruz")
        assertEquals("de la Cruz", loadResidentName())
    }

    @Test
    fun aClearedNameIsGone() {
        saveResidentName("ROSE")
        clearResidentName()
        assertNull(loadResidentName())
    }
}
