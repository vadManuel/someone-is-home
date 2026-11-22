package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState

/**
 * A whole round, captured (story 0.3).
 *
 * Holds the events that entered the rules, the effects they emitted, **and the authority state
 * after each one**. The events are the input; the effects and the states are jointly the
 * expected output.
 *
 * **The state rows are not redundant with the effects.** Rule 1 requires the effect stream to be
 * identical whether a revoke landed or not, so for the transition that matters most, effects
 * carry no information about what actually happened. A recording that stored only effects
 * certified a build where the revoke ability did nothing as replaying correctly.
 *
 * **The initial state is recorded too**, so a recording is self-contained. Without it, replaying
 * from a different starting state produced a different outcome and still reported Identical.
 *
 * **Recordings hold complete authority state** — who is an Insider, real occupancy, real Egress
 * progress. Debugging artifacts, gitignored. Never attach one to an issue or hand one to a
 * player.
 */
class Recording(
    val initialState: String,
    val events: List<Event>,
    val effectTranscript: List<String>,
    val stateTranscript: List<String>,
    /**
     * Events the admission gate refused (D-066), in arrival order.
     *
     * **Recorded, because an unrecorded refusal is an invisible drop.** The gate exists to stop
     * the recording's effect rows and state rows disagreeing about a round that had not begun; a
     * gate that silently swallowed events would fix that disagreement by creating a different
     * one, where the recording no longer says what reached the rules at all.
     */
    val refusalTranscript: List<String>,
) {
    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("I $initialState")
        events.forEach { appendLine("E ${Transcript.render(it)}") }
        refusalTranscript.forEach { appendLine("X $it") }
        effectTranscript.forEach { appendLine("F $it") }
        stateTranscript.forEach { appendLine("S $it") }
    }

    companion object {
        // 53 (D-328): not every seat is chained (the owner's 2026-11-23 ruling). A third of the seats,
        // rounded to the nearest even number, are drawn role-blind off the same cycle and paired, and
        // a pair the draw gave an Insider is two fake holds, so the same seed holds different lines
        // and draws different links: a version 52 round would replay to different orders and
        // releases. And a held Memory Dump pattern now refuses every card but its target, so the same
        // scans answer differently. No event, effect, field or row changed shape. A version 52
        // recording is refused here as every older one is.
        //
        // 52 (D-325): Memory Dump is dealt (its screens are built), so the same seed draws a long into
        // every order again, and its pattern is four elements drawn without a length draw, so a
        // version 51 round replays to different work. One event a 51 build cannot construct,
        // `CarryBegun` (`actor=`), BEGIN on a caught scan that offered a carry: a Memory Dump's source
        // or an Array Wipe's first Spares, now briefed. `ScanAnswered` gains `circuit=`; the state
        // row's `carries=` gains `:offered:` entries, what a scan showed before BEGIN took it. A
        // version 51 recording is refused here as every older one is.
        //
        // 51 (D-324): chains link plain Residents only (the owner's 2026-11-22 ruling). The same seed
        // draws different links, the Residents chained in the cycle's order and every Insider's locked
        // line a fake hold timed off the Resident before it, so a version 50 round would replay to
        // different releases. One effect a 50 build names is gone, `UpstreamWaiting` (no chain notice
        // at lights out, so the arming burst is one run shorter); `WorkUnblocked` gains `haptic=`, the
        // house text's buzz; the state row's `links=` gains `:real` or `:fake` on every link. A version
        // 50 recording is refused here as every older one is.
        //
        // 50: three changes built in parallel and integrated together (D-319, D-320, D-322, with the
        // owner's 2026-11-22 rulings, D-323), each of which alone was 49. The combined tree carries
        // every row change below: D-319's two timelapse events and `timelapse=` state field; D-320's
        // `ScanAnswered` `haptic=` and `Surged` without `sound=`; D-322's draw, `ChainUnlocked`,
        // `UpstreamWaiting`, `WorkUnblocked`, `ScanAnswered` `carrying=`, `links=`, `carries=` and
        // the entries' `/held|free/<wiped>`. With D-323 the mix deals no Memory Dump while its
        // screens are unbuilt, so the same seed draws a different order from any one 49, and
        // `RoleRevealed` gains `insiders=`. A recording of any one 49 lacks the others' rows or
        // fields, and a version 49 or older recording is refused here as every older one is.
        //
        // 49 (D-322): the work order is the design's now (D-322). The same seed draws different orders: the
        // owner's mix scaled to `K` (circuit, long, medium, short), the Array Wipe circuit and Memory
        // Dump dealt, and one cross-player dependency per seat, so a version 48 round would replay to
        // different work. One event a version 48 build cannot construct, `ChainUnlocked` (`seat=`,
        // `entry=`), minted by the house's clock when a dependency's delay runs out. Two effects it
        // cannot name, `UpstreamWaiting` (one per seat at the end of the arming burst) and
        // `WorkUnblocked`; `ScanAnswered` gains `carrying=`, and every ordinary scan now answers with
        // `WorkOrderIssued` between it and `PresenceChanged`. The state row gains `links=` and
        // `carries=`, and each order entry gains `/held|free/<wiped>`. A version 48 recording is
        // refused here as every older one is.
        // 49 (D-320): two effect rows changed shape (the owner's 2026-11-21 rulings, D-320). `ScanAnswered`
        // gains `haptic=`, `Double` on NOTHING FOR YOU HERE and `none` at a node of the Egress and on
        // an opened scan: the rules choose the buzz from the facts that chose `opened`, and a
        // replay that could not see it would certify a build that buzzed a node scan *no*. `Surged`
        // loses `sound=`: the game makes no sound, and the effect no longer carries one. A version
        // 48 recording's rows cannot be compared with what this build emits, so it is refused here
        // as every older one is. No event or state row moved.
        // 49 (D-319): the Terminal's timelapse (the owner's 2026-11-21 rulings, D-319). Two events a version
        // 48 build cannot construct, `TimelapsePlayed` and `TimelapseStopped` (`actor=`), the
        // reader's TIMELAPSE and STOP THE PLAYBACK; and the state row gains `timelapse=`: the
        // station's history (a frame every two seconds of the shift, bare counts and the jam), the
        // playback running and the station's cooldown. The frames are taken by the rules from the
        // recorded state at each mark, so what the reader was shown at a tick is still `occupancyBeat`
        // of the recorded state at that tick, and the beats are still not recorded. A version 48
        // recording has no row for any of it and is refused here as every older one is.
        //
        // 48: two changes built in parallel and integrated together, each of which alone was 47.
        // The home's plan goes to every seat at arming (D-316): a new effect, `PlanIssued`, one per
        // seat at the end of the arming burst, which an older build cannot name. The Terminal's
        // counts and the couch's true occupancy ride the upload's answer (`UploadHeard.map`), are no
        // effect and add no row: what a phone was shown at a tick is `occupancyBeat` of the recorded
        // state at that tick. And the host's DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN rows lock
        // at arming (the owner's 2026-11-21 ruling, D-317): `RoundArmed` gains `discussion=`,
        // `egressTimer=` and `revokeCooldown=`, in seconds, and the state row gains the same three;
        // the parser defaults none of them. Effect rows change too: `MeetingPhaseOpened` gains
        // `discussion=` (the talk's length on the push that opens it, `none` on every other phase),
        // and `OpeningMessage` and `MeetingEnded` gain `revokeCooldown=`, the ticks every phone's
        // Revoke clock is half of. A recording of either 47 lacks the other's rows or fields, and a
        // version 47 or older recording is refused here as every older one is.
        //
        // 46: the notice names who switched something off apart from who only left the app (the
        // owner's 2026-11-21 rulings, D-311). The `NoticeIssued` effect row's `unreachable=` now lists
        // the leavers alone and its `switchedOff=` is a list of seats where it was `true`/`false`, so a
        // version 45 recording's notice rows cannot be compared with what this build emits: a 45 row
        // naming a switcher in `unreachable=` would read here as a leaver. Events and state rows are
        // unchanged (the Bluetooth radio turned off arrives as the cause `Permission` a 45 build
        // already parses). A version 45 recording is refused here as every older one is.
        //
        // 45: a permission switched off is told apart from leaving the app, and the second one closes
        // the house (the owner's 2026-11-20 rulings, D-310). One event a version 44 build cannot
        // construct, `HouseClosedOnPermission` (`seat=`); `RadioFaulted` may carry a cause a version 44
        // parser refuses, `Permission`; the `NoticeIssued` effect row gains `switchedOff=` (the
        // owner's warning); and the state row gains `switchedOff=`, `warned=` and `closed=`, without
        // which a replay could not say why a round ended with no winner. A version 44 recording is
        // refused here as every older one is.
        //
        // 44: the contact source is gone (D-308). Every round is the hybrid: Bluetooth ranks who is
        // near, and the rules read a touch, a report and the spectator map from UWB distance alone,
        // so `RoundArmed` loses `contacts=` and so does the state row. A version 43 recording may
        // hold a round whose events the Bluetooth rules minted (`contacts=rssi`), which this build
        // cannot describe, and its arming row carries a field this parser does not expect, so a
        // version 43 recording is refused here as every older one is. No effect row changed shape;
        // the same events reduce the same way.
        //
        // 43: the round's contact source (D-301). `RoundArmed` gains `contacts=` (`rssi` or `uwb`)
        // and the state row gains `contacts=`: the house chooses at arming, from whether every phone
        // can range, whether the rules read a touch, a report and the spectator map from Bluetooth
        // RSSI or from UWB distance, and a replay that read the other would mint different touches,
        // reports and placements. A version 42 arming row is missing the field, and the parser
        // defaults nothing, so a version 42 recording is refused here as every older one is. No
        // effect row changed shape; no phone is sent the source.
        //
        // 42: no row changed shape, and the same events reduce differently (the owner's E2 ruling,
        // D-297), version 36's case. A scan of a node of the running Egress, paused or not, opens no
        // Subroutine: the effect row's `ScanAnswered` says `opened=none` where a version 41 build
        // opened the seat's work there, and the state row's `open` holds the spent instance (or
        // nothing) instead of a fresh one. A version 41 recording with such a scan would replay to a
        // different round, so it is refused here.
        //
        // 41: the same events reduce differently (the owner's O3 and O4 rulings, D-298). A Sync
        // Pulse return is good only on four consecutive pulses, so a version 40 return on four
        // scattered pulses would replay to a miss where the evening held it; two runs pair when they
        // share a pulse of the grid, not when their uploads arrived within 6 s; `SyncPulseAnswered`'s
        // `held` is the run being good, so the containing seat's answer reads `true` where it read
        // `false`. The state row's held offers gain the run's first pulse (`#n`). A version 40
        // recording is refused here instead.
        //
        // 40: no row gained or lost a field, and a Handshake's two lists changed what they hold
        // (the owner's Q7 ruling, D-296), version 36's case. `SubroutineReturned`'s `entered` for a
        // Handshake was one ordinal per tap and is now `HandshakeTaps.GAPS` gaps between taps, and
        // the state row's `open` answer key for one is the dealt gaps in the same encoding. Graded
        // each gap within ±40 %, a version 39 Handshake return would replay to a refusal where the
        // evening accepted it, so a version 39 recording is refused here instead.
        //
        // 39: two effect rows gain a field (the owner's 2026-11-18 ruling, D-290). `AbilityFired`
        // gains `touch=`, what an armed touch did with the actor's Revoke (`Landed`, `Spent`, or
        // `None` on every other fire), and `RevokeLanded` gains `haptic=`, `Long` on the touch that
        // Revoked its seat and `none` on every other. A version 38 effect row is missing both. No
        // event or state row moved.
        //
        // 38: the host's own seat (the owner's 2026-11-18 ruling, D-289). `RoundArmed` gains `host=`
        // (a seat or `none`) and the state row gains `host=`: the notice names an out host who goes
        // dark and no other out seat (D-283), so a replay that lost the seat would name nobody where
        // the evening named the host. A version 37 arming row is missing the field, and the parser
        // defaults nothing, so a version 37 recording is refused here as every older one is. No
        // effect row changed shape; no phone is sent the seat.
        //
        // 37: the `RoundEnded` effect row gains `integrity=` (decision pack 9b option a, D-284): the
        // final System Integrity, one percentage to every seat, so an ending with no meeting behind
        // it no longer draws "–". A version 36 effect row is missing it. No event or state row moved.
        //
        // 36: no row changed shape, and the same events reduce differently (decision pack 9f option
        // a, D-282): when a paused Egress resumes, at a meeting's close or at the house's return,
        // `egressReadyAt` moves back by the length of the pause, so the state row's `egressReady=`
        // differs for every round with a meeting during an Egress.
        //
        // 35: one event a version 34 build cannot construct, `OffAirEnded` (the owner's 2026-11-18
        // ruling, D-281): the house came back on the air, with the tick it went off it. A running
        // Egress is paused across the gap and every phone is sent `EgressHeld`. No row changed shape.
        //
        // 34: the Revoke arm window (the owner's 2026-11-18 ruling, D-280). The state row's
        // `armedRevoke=` entries carry the tick each arm's window closes (`seat@tick`), and the same
        // events reduce differently twice over: a contact past the window is an unarmed touch, and a
        // meeting call clears every arm. No event or effect row changed shape.
        //
        // 33: one refusal reason a version 32 build cannot name -- `CallerOut` (review r4 F5, D-279):
        // a meeting card call from an out seat is refused at the gate -- and three rule changes that
        // reduce the same events differently (D-279). An `EgressFired` during a meeting is refused
        // `MeetingAlreadyRunning`, and a `SyncPulseReturned` or `EgressExpired` during one
        // `EgressNotRunning` (F1); a held Sync Pulse offer from a seat that has gone out no longer
        // pairs (F2); and a presence row from before the shift places nobody at a node (F3). No row
        // changed shape.
        //
        // 32: every meeting call emits one more effect (D-274) -- `SystemIntegrityLive`, the same
        // percentage as the living's batch, so a seat put out before the meter moved is told it.
        // No row changed shape and no kind is new; the same events replay to a longer effect
        // transcript, so it is a bump for version 15's reason. `RosterIssued|seats=` now counts the
        // roster's non-null slots, which is the same number for every round armed with seats
        // `0..n-1` and the true seat count for one armed around a hole.
        //
        // 31: the house holds the plan again and builds the spectator map's input (O2, O4). Rows
        // changed shape in three places, and none could have been a tolerant read:
        //
        // **`RoundArmed` gains `rooms=`, `storeys=` and `terminalRoom=`**: the home's plan,
        // narrowed to storeys, cells and room names with no card in it (`HomePlanView`), and the
        // house's copy of the T card's room. Version 14 dropped the plan; the spectator map's error
        // asks which rooms touch which, so it is back as an input, and a row without it is fatal.
        //
        // **One event a version 30 build cannot construct, `Sensed`** (D-136, D-212): a sensor's
        // upload, the room the house knows it is in, and every seat heard there. It emits nothing,
        // so the effect transcripts of the same rounds are unchanged; what it changes is the state.
        //
        // **The state row gains the plan, the station's room, the shift's start, the sightings,
        // the observations with their once-rolled error, and the capture counter**, and every
        // `presence=` entry gains its time (`Presence.since`), which `placementOf` measures expiry
        // from. A version 30 state row is missing all of them.
        //
        // 30: the Sync Pulse on the host's clock (C2). `EgressFired` carries `pulseFrom`, the host's
        // monotonic millisecond the first pulse lands at, read at the edge -- an input, and a row
        // without one is fatal. `SyncPulseReturned.taps` are milliseconds on that clock, not ticks.
        // `EgressOpened` carries `pulseFrom`, the state row's `egress` gains `pulse=`, and one effect
        // a version 29 build cannot name -- `SyncPulseBlanked`, built for every seat but the tapper
        // beside each `SyncPulseAnswered`.
        //
        // 29: one effect a version 28 build cannot name -- `AbilityBlanked` (D-263), built for every
        // seat but the actor beside each `AbilityFired` a Surge, an Isolate, a Static or an Egress
        // answers. No event and no state row changed; the same events replay to a longer effect
        // transcript, so it is a bump for version 15's reason.
        //
        // 28: one effect a version 27 build cannot name -- `IsolateTargetsBlanked` (D-258), built
        // beside every seat's `IsolateTargetsOffered` at arming, at an Egress's start and at its
        // containment. No event and no state row changed; the same events replay to a longer effect
        // transcript, so it is a bump for version 15's reason.
        //
        // 27: one refusal reason a version 26 build cannot name -- `VoterOut` (H8, D-252) -- and the
        // rule change behind it: a selection or a READY from an out seat is refused at the gate, so
        // it no longer answers `VoteHeld` and `VoteSelectionShown`. No row changed shape.
        //
        // 26: `RadioFaulted` carries `cause` (D-223), and a row without one is fatal; the notice
        // effect is `NoticeIssued|unreachable=` where it was `RadioFaultsAnnounced|seats=`, and the
        // state column `radioFaulted` is `unreachable`, which a reset never writes (D-187).
        //
        // 25: one event and one effect a version 24 build cannot name -- `RadioFaulted` and
        // `RadioFaultsAnnounced` (T2, D-222) -- and a new state column, the radios awaiting the next
        // meeting's notice. Every meeting's gate now emits the notice, so every meeting's effect
        // transcript changes. The state row gains three columns: `reported` (S4's list, which it had
        // left out), `touches` (T1, D-220) and `radioFaulted`.
        //
        // 24: no row changed shape, and every touch reduces differently (T1, D-220). The first half
        // of a touch answers both of its phones and lands any Revoke either held; the second half,
        // at the same tick, changes nothing. So the same events replay to a different effect
        // transcript for every touch, and a Revoke held by the higher seat now lands in the lower
        // seat's half.
        //
        // 23: one refusal reason a version 22 build cannot name -- `AlreadyReported` (S4, D-219) --
        // and the rule change behind it: a second report of a Revoked seat a meeting has already
        // reported is refused, so the same events reduce differently. No row changed shape.
        //
        // 22: one refusal reason a version 21 build cannot name -- `ReporterOut` (R3, D-217) -- and
        // two rule changes that reduce the same events differently: a Revoked or Restrained seat
        // is disarmed (R2), and a contact at a meeting is refused (R5). No row changed shape.
        //
        // 21: one effect a version 20 build cannot name -- `ContactAnswered` (R1, D-216). A contact
        // from an actor who holds no armed Revoke used to answer `AbilityFired` and now answers
        // this, so the same events replayed by a version 20 build produce a different effect
        // transcript for every unarmed touch. No event and no state row changed.
        //
        // 20: no row changed shape and every round re-draws (D-214). Every stream the house draws
        // from the round's seed -- the Insider count and seats, the stations, the active set, each
        // seat's anchors and kinds, each Subroutine's question, each Egress's nodes and type -- now
        // starts from an HMAC of the seed under its own label rather than from the seed times a
        // constant, and the jam's seed on `StaticOpened` is an HMAC too. A version 19 recording
        // replayed by this build draws different work orders, questions, Egress nodes and noise from
        // the same arming row, and would report every one of them as a divergence.
        //
        // 19: one effect a version 18 build cannot name -- `ContactSeedIssued`, one per seat in the
        // arming burst (D-212). No event and no state row changed: the seed is derived from the
        // round's seed, which the arming row already holds. It is still a bump for version 15's
        // reason: the events are the same and the effect transcripts are not.
        //
        // 18: the Terminal became a station (D-199, D-203). One event a version 17 build cannot
        // construct (`TerminalLeft`) and one effect it cannot name (`TerminalAnswered`), and two
        // rows changed shape: **`RoundArmed` gained the T card's id**, without which a replay
        // answers every scan of it as NOTHING FOR YOU HERE, and **the state row gained the
        // station's card and its reader**, which reach no client and are therefore held nowhere
        // else. A version 17 arming row is missing a field this build requires.
        //
        // 17: two rows changed shape, in opposite directions, and neither could have been a
        // tolerant read.
        //
        // **`MeetingResult` gained the participation pair** -- `voted=` and `living=` beside the
        // outcome, because the owner ruled the living's result count-only (2026-08-24) and the
        // screen that used to compute those numbers off a fixture now reads them off the message.
        // Every version 16 result row holds two fields where this build writes four.
        //
        // **The state row LOST `accessPaused=`.** `GameState.accessPausedAt` is deleted: D-183
        // refuses a Tier-2 fire at the gate for the whole of a meeting and D-184 resets every
        // per-Insider clock at lights out, so between them nothing could observe the pause the mark
        // recorded. A version 16 state row carries a column this build does not write, and a
        // differential run across the boundary would report every state row as divergent -- which
        // is the condition this format's own note states for a smaller change.
        //
        // **Nothing about the round changes.** The pool behaves exactly as version 16's did,
        // because the mark was already unobservable; what is gone is the record of it.
        //
        // 16: three effects a version 15 build cannot name and one row that changed shape.
        // `RoleRevealed` was version 15's own arrival and never got its bump (found by the routing
        // day's closing sweep); `RosterIssued` and `RevokeLanded` are this unit's. A version 15
        // build cannot render any of the three, so a differential run across the boundary would
        // report a divergence that is really a format gap -- which is the condition version 15's
        // own note states in as many words for a smaller change.
        //
        // **`EgressOpened|nodes=` changed shape**, and that is the half that could not have been a
        // tolerant read: the nodes were card ids and are DESTINATIONS now -- card, glyph and room --
        // because the widget names the two nodes and a card id is not a name. Every Egress row in
        // every version 15 recording holds two bare ids where this build writes six escaped fields.
        //
        // **What version 16 does NOT gain is the names**, exactly as versions 10 and 11 did not gain
        // the lines and the names (D-115, D-116). `RosterIssued` renders its seat and the list's
        // LENGTH; a replayed round rebuilds blanks of the same length and the row comes out
        // byte-identical, so the game is reproduced without the words a third time.
        //
        // 15: one effect a version 14 build cannot name -- `MeetingCalling`, the per-seat message
        // that tells the calling phone which of the ringing phones it is (E-R1-1, ruled
        // 2026-08-23). It is the smallest bump this format has had: one row, one field, no state
        // row change and nothing removed.
        //
        // **It is still a bump, and the reason is the effect row rather than the state.** A version
        // 14 recording replays into a round where every meeting rang six identical phones and
        // nobody's screen said they had called it; the events are the same and the effect
        // transcripts are not, so a differential run across the boundary would report a divergence
        // that is really a format gap.
        //
        // 14: STATIC replaced the third Tier-2 ability outright (D-186), and this is the first
        // version bump in this format that is mostly a SUBTRACTION. Version 13's `SpoofFired` is
        // gone and `StaticFired` -- an actor and a tick, and nothing else -- is in its place; two
        // effects a version 13 build could name are gone with it (`HomePlanOffered`,
        // `PhantomPlaced`) and one it cannot name is new (`StaticOpened`).
        //
        // **The arming row lost the home's plan**, which is the largest thing this format has ever
        // stopped carrying. It existed because a picker over rooms cannot be drawn without rooms;
        // Static has no picker, so the plan crosses to no client and the rules never see one.
        //
        // The state row swapped the phantoms for one nullable deadline. It is load-bearing for the
        // isolations' reason inverted: the jam DOES reach every living phone, but it reaches them as
        // a window that has already started, so a replay that did not hold the deadline would come
        // out of the jam at the wrong moment. It carries no seat, which is D-186's anonymity kept in
        // the one artifact of this game that outlives the evening -- a recording cannot say who
        // jammed the house, because the house never knew.
        //
        // 12: Tier 2 arrived. Two events a version 11 build cannot construct (`SurgeFired`,
        // `IsolateFired`) and two effects it cannot name (`Surged`, `IsolateTargetsOffered`), and
        // the state row gained the two fields those need: the Access pool's pause mark and the
        // isolated markers. Both are load-bearing rather than decorative — `accessReady` reads the
        // pause, so it decides whether a Tier-2 ability is available at all, and the isolations
        // reach NO client, so a state row is the only artifact in the game that holds them. A
        // version 11 recording replays into a round where nothing was ever dark and every pool
        // recharged through every meeting. The cooldown list also doubled in length: every seat now
        // carries a Revoke row and an Access row (D-132, `gdd.md:306`).
        //
        // 11: the wire learned to say where, how long, and who. `RoundArmed.markers` were bare card
        // ids and are DESTINATIONS now -- card, glyph and room -- because a work order's whole
        // destination column is computed from them, and a version 10 recording replays into orders
        // that point nowhere. Beside them the arming row gained the host's ballot length, which
        // locks at arming like every other balance value, and the seats that said what to call
        // them. Two effects a version 10 build cannot construct arrive with it (the live meter and
        // the batched one), `MeetingPhaseOpened` carries the ballot's window, and a work order line
        // carries its destinations and can be a circuit rather than only one of the ten.
        //
        // **The escape covers the colon now**, which changes the bytes of every row holding a
        // marker id or a room name and is the honest reason this could not have been a tolerant
        // read of version 10. It closes E-L4-4: `:` has always been a sub-separator inside `open=`
        // and `presence=`, a marker id may contain one, and a room name is text a host typed.
        //
        // **What version 11 does NOT gain is the names** (D-115), exactly as version 10 did not
        // gain the lines. Both round-scoped texts render as seats, the parser rebuilds blanks, and
        // a replayed reveal publishes a blank name beside a blank line.
        //
        // 10: the ending. `ended` was a boolean and is the OUTCOME now -- winner and route -- so a
        // version 9 recording can say a round finished and cannot say who took it or how, which is
        // the only question anybody asks afterwards. Three effects a version 9 build cannot
        // construct arrive with it, and `RoundArmed` gained the seats that handed a one line over.
        //
        // **What version 10 deliberately does NOT gain is the lines themselves** (D-116). Both new
        // rows carry seats and no text: a recording is the one artefact of this game that outlives
        // the evening, and the promise printed on the screen where a line is typed does not survive
        // being written into one. A replayed round therefore rebuilds blanks and publishes blanks
        // at its reveal, and every row it produces is byte-identical to the row it is compared
        // against -- the game is reproduced without the words.
        //
        // 9: the Egress. Three events a version 8 build cannot construct -- and the first of them,
        // `EgressFired`, carries the two nodes and the type as INPUTS, drawn above the rules
        // against a home the recording does not hold. The state row's `egress` was a boolean and
        // is now the whole lifecycle: deadline, pause mark, held Sync Pulse offers and lockouts,
        // beside the house-wide shared cooldown. A version 8 recording says only that a house was
        // on fire, which cannot tell a contained Egress from one that ran out.
        //
        // 8: the scan answers. `MarkerScanned` stopped being silent -- it emits ScanAnswered and
        // PresenceChanged now -- and `PerformanceEnded` is a new event a version 7 build cannot
        // construct. The answer key moved off the work order and onto the open instance, where a
        // re-scan re-draws it (D-139), so `orders` lost `expected` and `open` gained the
        // parameters beside it; the state row gained the whole presence plane. A version 7
        // recording replays into a round whose questions are drawn somewhere else.
        //
        // 7: arming. `RoundArmed` gained the host's public Insider SETTING beside the hidden draw,
        // and the home's markers -- without which the stations, the active set and every work
        // order are undrawable, so a version 6 recording replays into a round with no work in it.
        // The state row gained the round's whole draw (orders with their answer keys, stations,
        // lit markers) and every Insider cooldown, and `open` gained the order line it came from.
        //
        // 6: the meeting engine. `VoteCast(voter, target)` became `VoteSelected` plus `VoteLocked`
        // -- the *changeable until the clock ends* model D-117 superseded, split into the live tap
        // and the irrevocable READY -- and six more meeting events arrived with them. The state row
        // gained the whole meeting, both out lists and the Egress flag. A version 5 recording holds
        // an event this build cannot construct, and its state rows are missing the ballots a tally
        // is computed from.
        //
        // 5: the verdict spine. `SubroutineCompleted` became `SubroutineReturned` and carries the
        // entry; the state row gained the work order.
        const val HEADER = "someone-is-home/recording/53"
    }
}

/**
 * The one walk through the authority, shared by everything in this module that runs a round.
 *
 * **Goes through [admit], not [home.someoneshome.core.reduce].** The admission gate is part of
 * the authority, so a harness that called the rules directly would be exercising a path no real
 * client can reach — and would have gone on certifying pre-arm effects as correct.
 *
 * [onStep] receives the event's index, the state AFTER the event, and the effects it emitted.
 * [onRefusal] receives the index, the event, and why the gate refused it. **Both carry the index
 * into the original event list, not a count of how many got through** — a refused event still
 * occupies a position, and a snapshot that named a different one could not be regenerated. Every capture —
 * the recording, the effect list, the per-client transcripts — is a different observer of the
 * same walk, so no two of them can disagree about what the round did.
 */
internal inline fun drive(
    initial: GameState,
    events: List<Event>,
    // Before `onStep` so that `onStep` stays the trailing lambda: a caller that only cares about
    // effects reads as `drive(initial, events) { after, emitted -> ... }` and cannot accidentally
    // bind its lambda to the refusal hook instead.
    onRefusal: (Int, Event, home.someoneshome.model.RefusalReason) -> Unit = { _, _, _ -> },
    onStep: (Int, GameState, List<Effect>) -> Unit,
): GameState {
    var state = initial
    events.forEachIndexed { index, event ->
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                onStep(index, state, admission.reduction.effects)
            }
            // No state change and no effects. Refusing is not a quiet version of reducing.
            is Admission.Refused -> onRefusal(index, event, admission.reason)
        }
    }
    return state
}

/**
 * Runs a round and captures it.
 *
 * Nothing here decides anything: it drives [reduce] and writes down what happened. A rule in
 * this function would mean the recording was of the harness rather than of the game.
 */
fun record(initial: GameState, events: List<Event>): Pair<GameState, Recording> {
    val effects = mutableListOf<String>()
    val states = mutableListOf<String>()
    val refusals = mutableListOf<String>()
    val state = drive(
        initial, events,
        onRefusal = { index, event, reason -> refusals += Transcript.render(index, event, reason) },
    ) { _, after, emitted ->
        emitted.forEach { effects += Transcript.render(it) }
        states += Transcript.render(after)
    }
    return state to Recording(
        initialState = Transcript.render(initial),
        events = events,
        effectTranscript = effects,
        stateTranscript = states,
        refusalTranscript = refusals,
    )
}

/** Every effect a round produced, in emission order. Shares the driver, so it cannot drift. */
fun effectsOf(initial: GameState, events: List<Event>): List<Effect> {
    val out = mutableListOf<Effect>()
    drive(initial, events) { _, _, emitted -> out += emitted }
    return out
}
