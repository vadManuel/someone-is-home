package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.model.GameState
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The house's ability line names no seat unless it was told it may** (D-237).
 *
 * Only an Insider's page 2 takes a hold, so a seat in the line names an Insider, and a device console
 * is a file. The default is `A SEAT`; the seat is named only where the caller says so.
 */
class AbilityLogTest {

    private val refused = Admission.Refused(RefusalReason.RoundNotArmed)

    @Test
    fun theLineNamesNoSeatByDefault() {
        assertEquals(
            "REVOKE ARM — A SEAT · REFUSED RoundNotArmed",
            abilityLine(AbilityRequest.Revoke, Seat(3), Tick(0), GameState.EMPTY, refused, namesSeat = false),
        )
    }

    @Test
    fun theLineNamesTheSeatOnlyWhenTold() {
        assertEquals(
            "SURGE FIRE — SEAT 3 · REFUSED RoundNotArmed",
            abilityLine(AbilityRequest.Surge, Seat(3), Tick(0), GameState.EMPTY, refused, namesSeat = true),
        )
    }
}
