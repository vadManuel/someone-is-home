package home.someoneshome.app

import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody

/**
 * **The arrival queue's four doors into the rules, each one awaited** (R6, D-241).
 *
 * `LobbyHouse.attend` is the one consumer of every arrival, and it hands each to one of these. Until
 * D-241 the app root's doors were `scope.launch { rules.value?.sighted(…) }`, one new coroutine per
 * arrival, so the queue kept nothing in order: the next arrival was taken while the last was still
 * pushing, and `sighted` pushes between the two halves of a touch. A second touch reduced in that gap
 * replaced `GameState.touches`, the first touch's second half no longer recognised its first, and
 * both of its phones were answered twice (T1 broken), with the effects of two reductions interleaved
 * on the wire. **Now each door suspends until the rules are done with it**, so the queue's next
 * arrival waits for this one. [rules] is read at the arrival, because the authority is built after
 * the link that holds these doors.
 *
 * `CheatRoot` builds these, and every test that stands up a host builds the same class, so a test
 * cannot pass on doors the app does not have.
 */
class HouseDoors(private val rules: () -> RoundAuthority?) {

    /** A request, attributed by the desk. */
    suspend fun heard(intent: Intent) {
        rules()?.heard(intent)
    }

    /** The Egress tile, held down: the house draws, then mints. */
    suspend fun fired(actor: Seat) {
        rules()?.fireEgress(actor)
    }

    /**
     * What a phone's radio heard, to the resolver; and the house's ranging plan for that phone, to
     * ride the upload's answer (D-300). Null when there is no authority to ask.
     */
    suspend fun sighted(seat: Seat, upload: RoundBody.Sighted): String? = rules()?.sighted(seat, upload)

    /** What [seat] may draw of the map this beat, sealed for it (D-316). Null when there is no authority to ask. */
    suspend fun mapped(seat: Seat): String? = rules()?.mapped(seat)

    /** A phone's radio went down, and why; heard once per onset (D-245). */
    suspend fun faulted(seat: Seat, report: RoundBody.RadioFaulted) {
        rules()?.radioFaulted(seat, report.cause, report.onset)
    }
}
