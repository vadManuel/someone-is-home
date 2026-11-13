package home.someoneshome.core

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Tick

/**
 * **The request becoming the fact — one path, whichever source the request came from.**
 *
 * [Intent] is imperative and may be refused; [Event] is past tense and happened (rule 10). This is
 * the one place the first becomes the second, and it is a *translation* rather than a decision:
 * nothing here reads [GameState], nothing here refuses, and nothing here chooses between two
 * events. Whether the request is allowed is [admit]'s, one line below and unchanged — so a request
 * arriving over a socket and a request built in this process reach the same gate by the same route,
 * and there is no second gate to drift from the first.
 *
 * ### Why the tick is an argument
 *
 * *The house's clock, sampled at the edge like every other* ([Event.DiscussionClosed]). The rules
 * never read a clock, so the moment a request arrived is stamped where it arrived and travels in
 * with it. It is deliberately not read from [GameState]: two requests reduced in one tick would be
 * two events the recording could not order, and the recording is the only debugging instrument
 * this game has.
 *
 * ### Why it is exhaustive, and what the exhaustiveness is worth
 *
 * A new [Intent] does not compile until somebody names the [Event] it makes, which is
 * `EmitSchema.kindOf`'s discipline pointed up the wire instead of down it. It is worth something
 * because the reverse direction is where the real judgement sits: **three client-originated events
 * have no intent at all**, and each is a decision written down rather than a case nobody reached.
 * [Event.ContactMade] and [MeetingTrigger.RevokeReported] name a seat that only the authority can
 * resolve from a rotating token (D7); [Event.EgressFired] carries a draw the house makes against a
 * home's shape the phone has never held. All three would need this function to compose a fact from
 * more than the phone gave it, which is the loop's join and not this translation's.
 *
 * `IntentAdmissionTest.everyClientOriginatedEventIsEitherRequestableOrNamedHere` is the completeness
 * check, written out by hand against the table rather than derived from [factOf] — a list derived
 * from the thing it is checking agrees with itself, which is L2's injection-6 finding and the
 * reason the emit schema's own completeness list is written twice.
 */
fun factOf(intent: Intent, at: Tick): Event = when (intent) {
    is Intent.ArmRevoke -> Event.RevokeArmed(at, intent.actor)

    // **The two Tier-2 abilities that DO have a request**, and the contrast with `EgressFired` two
    // paragraphs up is the whole reason they are lawful here: the house makes no draw for either.
    // A Surge names a seat off a roster and an Isolate names a card off a list the house itself
    // published to that phone, so both events are fully determined by what the phone did, which
    // connection did it, and when it arrived. Nothing is composed from a second source.
    //
    // Static is the third and it qualifies more plainly than either: it names nothing at all. The
    // event is composed out of the actor and the tick, so there is no target to resolve, no roster
    // to look up and no second source anywhere -- which is the narrowest a fire can be (D-186).
    is Intent.FireSurge -> Event.SurgeFired(at, intent.actor, intent.target)
    is Intent.FireIsolate -> Event.IsolateFired(at, intent.actor, intent.target)
    is Intent.FireStatic -> Event.StaticFired(at, intent.actor)

    is Intent.BeginSubroutine -> Event.MarkerScanned(at, intent.actor, intent.marker)

    // **STOP NOW, and the Sync Pulse handed over.** Both pass this file's test more plainly than
    // anything above them: the first composes an event out of the actor alone, and the second out of
    // the actor and the taps the finger made. Neither names a target the house has to resolve and
    // neither needs a draw -- which is exactly why they could have travelled from the day the
    // channel existed, and the gap was a missing member rather than a missing argument (E-P2-5).
    is Intent.StopSubroutine -> Event.PerformanceEnded(at, intent.actor)
    // The live view closed. The actor alone, for STOP NOW's reason (D-203).
    is Intent.LeaveTerminal -> Event.TerminalLeft(at, intent.actor)
    is Intent.ReturnSyncPulse -> Event.SyncPulseReturned(at, intent.actor, intent.taps)

    // The trigger is which control was pressed, and the wire has a member per control for exactly
    // this reason: a trigger field would be a client choosing which admission rule it is judged by,
    // and the card is inert during an Egress while the report is not (D-133). The report has no
    // intent at all — see this file's header.
    is Intent.CallMeeting -> Event.MeetingCalled(at, intent.actor, MeetingTrigger.MeetingCard)

    is Intent.CheckIn -> Event.MeetingCheckedIn(at, intent.actor)
    is Intent.DeclareReadyToVote -> Event.ReadyToVoteDeclared(at, intent.actor)
    is Intent.SelectVote -> Event.VoteSelected(at, intent.actor, intent.target)
    is Intent.LockVote -> Event.VoteLocked(at, intent.actor)
    is Intent.HandOverEntry ->
        Event.SubroutineReturned(at, intent.actor, intent.marker, intent.entered)
}

/**
 * **One request, put to the house.**
 *
 * [factOf] then [admit], and deliberately nothing else. It exists so that the two sources of
 * requests — a control on this phone and a frame off a socket — are one call rather than two
 * similar sequences that have to be kept in step by review.
 *
 * A refusal is answered by re-assertion and never by an explanation (D-097): the caller re-pushes
 * that seat's authorized view through the emit boundary, and nothing about the refusal reaches the
 * player. That is the caller's to do because it is a delivery, and this module has never held a
 * socket — but it is why [Admission.Refused] carries a reason the *authority* reads and no message
 * kind exists that could carry one down.
 */
fun admit(state: GameState, intent: Intent, at: Tick): Admission =
    admit(state, factOf(intent, at))
