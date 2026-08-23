package home.someoneshome.model

/**
 * A request from a client. Imperative mood, and **may be refused**.
 *
 * Kept rigorously separate from [Event] (project-context rule 10). `ArmRevoke` is a request that
 * may be denied; `PlayerRevoked` is a fact that happened. Name them interchangeably and you will
 * eventually store a request in the recording and replay something that never occurred.
 *
 * Intents are attributed **by connection**, never by a client naming itself. A client that could
 * name its own seat could claim another player's — the only cheat in this game that is remote,
 * undetectable, and requires no physical act.
 *
 * **[actor] is therefore filled in exactly one place**, and it is not a screen: a request arrives
 * as a [home.someoneshome.model.protocol.IntentMessage], which has no field for a seat and no way
 * to acquire one, and the host's desk stamps the seat the connection was admitted to. This type is
 * deliberately **not** `@Serializable` — it is the authority's shape, and the wire's is constructed
 * from it rather than annotated onto it, which is the same arrangement [Effect] and
 * [home.someoneshome.model.protocol.RoundMessage] have on the way down.
 *
 * ### An Intent may exist only where the [Event] it becomes needs nothing but the phone
 *
 * `core`'s `factOf` turns each member below into exactly one [Event] from three ingredients: what
 * the phone did, which connection did it, and when it arrived. Three client-originated events have
 * no member here for failing that test — [Event.ContactMade] and the Revoked report both name a
 * seat only the authority can resolve from a rotating token (D7), and [Event.EgressFired] carries a
 * draw the house makes against a home's shape. See `IntentMessage`'s header, which holds the full
 * argument for all three.
 */
sealed interface Intent {
    data class ArmRevoke(val actor: Seat) : Intent

    /**
     * **Surge this seat** (`gdd.md:313`, [Event.SurgeFired]).
     *
     * [target] is content and never attribution — who the ability is aimed at, picked off a roster
     * the house drew, exactly as [SelectVote]'s target is. **It may be [actor]**, and the rules
     * special-case nothing: *Insiders can target themselves with every ability* (`gdd.md:393`).
     *
     * Unlike the Revoke's firing, this one qualifies as an Intent: the target is a seat off a list
     * the house sent rather than an identity resolved from a rotating token, so the event is fully
     * determined by what the phone did, which connection did it, and when — which is the whole test
     * this file's header sets.
     */
    data class FireSurge(val actor: Seat, val target: Seat) : Intent

    /**
     * **Isolate this marker** (`gdd.md:315`, [Event.IsolateFired]).
     *
     * [target] is a printed card off `Effect.IsolateTargetsOffered`, and it is a card id rather than
     * a claim about where anybody is standing — [BeginSubroutine]'s marker exactly, and the same
     * reason (D-136).
     */
    data class FireIsolate(val actor: Seat, val target: MarkerId) : Intent
    data class BeginSubroutine(val actor: Seat, val marker: MarkerId) : Intent
    data class CallMeeting(val actor: Seat) : Intent

    /**
     * **An entry handed over at a marker — a report of what was entered, never a claim that it was
     * right** (D-109, [Event.SubroutineReturned]).
     *
     * [marker] is which card opened the work rather than where the player is standing now: a phone
     * naming its own location would be a client asserting a placement, and *inference never
     * overrides knowledge* (D-136).
     */
    data class HandOverEntry(val actor: Seat, val marker: MarkerId, val entered: List<Int>) : Intent

    /** I'M HERE. Reports one phone; the gate that closes on it is the house's (D-104). */
    data class CheckIn(val actor: Seat) : Intent

    /** READY TO VOTE. One hand up. Only a unanimous one ends the talk early. */
    data class DeclareReadyToVote(val actor: Seat) : Intent

    /** A finger on a name, or on Skip. Transmitted live so the couch can watch (D-117). */
    data class SelectVote(val actor: Seat, val target: Seat?) : Intent

    /**
     * READY: turn the current selection into the vote, irrevocably (D-117).
     *
     * **Carries no target**, exactly as [Event.VoteLocked] does not — a target here would let a
     * client lock a vote it never transmitted. This replaced `CastVote(actor, target)`, which was
     * the *changeable until the clock ends* model the design has since superseded.
     */
    data class LockVote(val actor: Seat) : Intent
}
