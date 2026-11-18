package home.someoneshome.harness

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OrderLine
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.StoreyView

/**
 * Canonical rendering of events and effects.
 *
 * **Explicit, not `toString()`.** A data class's generated `toString` is stable within a
 * compiler version and guaranteed by nothing across one. A recording is a debugging artifact
 * that has to outlive the build that produced it, so "byte-identical replay" cannot rest on a
 * format the language is free to change.
 *
 * **Both `when`s are exhaustive over sealed types, deliberately.** Adding an [Effect] without
 * adding its line here is a COMPILE ERROR, not an effect that silently records as nothing. Same
 * discipline as the redaction schema: the failure mode must be "my thing didn't build", never
 * "my thing vanished from the recording".
 */
object Transcript {

    fun render(event: Event): String = when (event) {
        is Event.RoundArmed ->
            row("RoundArmed", event.at, "seed" to num(event.seed),
                "seats" to event.seats.joinToString(",") { num(it.index) },
                // Both Insider numbers, because they are two different facts and a recording that
                // held only the draw could not tell a round the host set from one the house chose.
                // `chosen` is the public setting; `insiders` is what was drawn behind it (D-103).
                "insiders" to event.insiders.joinToString(",") { num(it.index) },
                "chosen" to (event.chosenInsiders?.let { num(it) } ?: "none"),
                // **The cards WITH their glyph and their room**, because the destination on a work
                // order is computed from them: a row that held bare ids would replay into an order
                // whose destination column was empty, and the replay would certify it.
                "markers" to event.markers.joinToString(",") { destination(it) },
                // The host's ballot length. Balance values lock at arming and stamp into the
                // recording, so a replay runs the ballot the evening actually ran rather than
                // whatever this build's default happens to be.
                "voteWindow" to num(event.voteWindowSeconds),
                // **The seats that said what to call them, and NOT the names** (D-115). The lines'
                // argument below, applied to the other round-scoped text on the desk: names are
                // scoped to the round on purpose, and a recording outlives the evening. The parser
                // rebuilds blanks and the reveal renders as seats alone, so replay is exact.
                "names" to event.names.joinToString(",") { num(it.seat.index) },
                // **The seats that handed a line over, and NOT what any of them said** (D-116).
                //
                // The only recorded input this file deliberately refuses to record. A one line is
                // typed on the strength of two promises -- seen by the house only, deleted when
                // the round ends -- and a recording is the one artefact of this game that outlives
                // the evening. Rendering the text here would keep every player's confession in a
                // debugging file, forever, on the host's phone, in a game whose whole premise is
                // that the house is the only thing that ever sees it.
                //
                // **Replay is exact anyway**, which is what makes this affordable rather than a
                // trade. The parser rebuilds blank lines for these seats; the only effect that
                // ever reads one renders as seats alone (see Effect.InsidersRevealed below); so
                // every row a replay produces is byte-identical to the row it is compared against,
                // and the round is reproduced without the words.
                // **The home's shape is NOT on this row and no longer can be** (D-186). Version 13
                // carried it because a picker over rooms cannot be drawn without rooms; Static has
                // no picker, the plan crosses to no client, and `Event.RoundArmed` no longer holds
                // one. This is the largest thing this format has ever stopped carrying.
                "lines" to event.oneLines.joinToString(",") { num(it.seat.index) },
                // **The T card's id, or none** (D-199). An id and no room: the rules need only
                // recognise the paper, and the station's room is the host's to say aloud.
                "terminal" to (event.terminal?.let { text(it.value) } ?: "none"),
                // **The home's plan is back on this row, narrowed** (O2). Version 14 dropped it
                // with the Spoof picker; the spectator map's error asks which rooms touch which,
                // so the rules hold the plan again -- as storeys, cells and room names, with no
                // card of any kind in it (HomePlanView). An input, so a replay knows the same
                // adjacency the evening did.
                "rooms" to rooms(event.plan),
                "storeys" to storeys(event.plan),
                // The house's copy of the T card's room (O2), `some:` before a name so that a room
                // a host called NONE cannot read as no room.
                "terminalRoom" to optionalText(event.terminalRoom))
        // **The spectator map's input** (O4): who sensed, in which room, and every seat heard
        // there. An input drawn above the rules from the radio, exactly as a contact is, so it is
        // on the row in full and a replay places the same seats without a radio.
        is Event.Sensed ->
            row("Sensed", event.at, "sensor" to num(event.sensor.index), "room" to text(event.room),
                "heard" to event.heard.joinToString(",") { num(it.index) })
        is Event.MarkerScanned ->
            row("MarkerScanned", event.at, "actor" to num(event.actor.index), "marker" to text(event.marker.value))
        is Event.SubroutineReturned ->
            row("SubroutineReturned", event.at, "actor" to num(event.actor.index),
                "marker" to text(event.marker.value),
                "entered" to event.entered.joinToString(",") { num(it) })
        // No reason and no marker, because the event carries neither -- see Event.PerformanceEnded.
        // A recording that invented either would be the first place an abandonment record could
        // grow, and it would grow in the artifact kept specifically to find out what happened.
        is Event.PerformanceEnded ->
            row("PerformanceEnded", event.at, "actor" to num(event.actor.index))
        // The actor alone, for PerformanceEnded's reason: a reason here would be a record of how
        // somebody left the station (D-203).
        is Event.TerminalLeft ->
            row("TerminalLeft", event.at, "actor" to num(event.actor.index))
        is Event.RevokeArmed ->
            row("RevokeArmed", event.at, "actor" to num(event.actor.index))
        // The two Tier-2 fires. Both carry their target, because the target is what was ASKED for
        // and a recording that held only *somebody fired a Surge* could not replay which phone
        // screamed. `SurgeFired`'s target may be its own actor -- self-targeting is required
        // (gdd.md:393) -- and the row renders it the same way either way, because the rules do.
        is Event.SurgeFired ->
            row("SurgeFired", event.at, "actor" to num(event.actor.index),
                "target" to num(event.target.index))
        is Event.IsolateFired ->
            row("IsolateFired", event.at, "actor" to num(event.actor.index),
                "target" to text(event.target.value))
        // **The third fire, and the row the CUT-RISK METRIC is counted off** (`gdd.md:1347`).
        // It carries the actor and the tick and there is nothing else to carry: Static has no
        // target of any kind (D-186). See [staticFires], which is what makes *fired at least once
        // per three rounds* a number somebody can read off an evening rather than a thing somebody
        // has to remember to watch.
        is Event.StaticFired ->
            row("StaticFired", event.at, "actor" to num(event.actor.index))
        is Event.ContactMade ->
            row("ContactMade", event.at, "actor" to num(event.actor.index), "target" to num(event.target.index))
        is Event.MeetingCalled ->
            row("MeetingCalled", event.at, "caller" to num(event.caller.index),
                "trigger" to trigger(event.trigger))
        // The seat, the tick and D-187's line (D-223): a replay that named a reset must not match.
        is Event.RadioFaulted ->
            row("RadioFaulted", event.at, "seat" to num(event.seat.index), "cause" to event.cause.toString())
        is Event.MeetingCheckedIn ->
            row("MeetingCheckedIn", event.at, "seat" to num(event.seat.index))
        is Event.ReadyToVoteDeclared ->
            row("ReadyToVoteDeclared", event.at, "seat" to num(event.seat.index))
        is Event.VoteSelected ->
            row("VoteSelected", event.at, "voter" to num(event.voter.index),
                "target" to seatOrNone(event.target))
        is Event.VoteLocked ->
            row("VoteLocked", event.at, "voter" to num(event.voter.index))
        is Event.DiscussionClosed ->
            row("DiscussionClosed", event.at)
        is Event.VoteWindowClosed ->
            row("VoteWindowClosed", event.at)
        is Event.TallyHalfwayReached ->
            row("TallyHalfwayReached", event.at)
        is Event.MeetingClosed ->
            row("MeetingClosed", event.at)
        // **The type and both nodes are ON the row, because they are inputs and not consequences.**
        // They are drawn above the rules by `egressFor`, against a home the recording does not
        // carry -- so a row that held only "an Egress fired" would replay into a different pair of
        // rooms, or into none at all, and the replay would certify it.
        is Event.EgressFired ->
            row("EgressFired", event.at, "actor" to num(event.actor.index),
                "type" to event.type.toString(),
                "nodes" to event.nodes.joinToString(",") { text(it.value) },
                // The pulse grid's anchor on the host's clock (C2): read at the edge, so an input.
                "pulseFrom" to num(event.pulseFrom))
        // The taps and NOT the node. The event carries no node -- where this player was standing is
        // what the house recorded when they scanned -- and a recording that invented one would be
        // the first place a phone's claim about its own position could grow.
        is Event.SyncPulseReturned ->
            row("SyncPulseReturned", event.at, "actor" to num(event.actor.index),
                "taps" to event.taps.joinToString(",") { num(it) })
        is Event.EgressExpired ->
            row("EgressExpired", event.at)
    }

    /**
     * A card, its glyph and its room, as one field.
     *
     * `:` between the three and every part escaped, so a room name a host typed with a colon in it
     * cannot forge a fourth part — the same fault the pipe and the comma each had, and the reason
     * [escape] now covers the colon as well.
     */
    private fun destination(at: Destination): String =
        "${text(at.marker.value)}:${text(at.shape)}:${text(at.room)}"

    /** The plan's rooms in plan order, `name:Room` or `name:Stairs`, the name escaped. */
    internal fun rooms(plan: HomePlanView): String =
        plan.rooms.joinToString(",") { "${text(it.name)}:${it.kind}" }

    /**
     * The plan's storeys in plan order, `name:cols:rows:cells`, the cells row-major and joined by
     * `.`, a room's index or `x` for a square nobody painted. The name is escaped, so it holds no
     * raw `:` or `,`, and the split back is unambiguous.
     */
    internal fun storeys(plan: HomePlanView): String =
        plan.storeys.joinToString(",") { storey ->
            "${text(storey.name)}:${num(storey.cols)}:${num(storey.rows)}:" +
                storey.cells.joinToString(".") { if (it == StoreyView.UNPAINTED) "x" else num(it) }
        }

    /** `none`, or `some:` and the escaped text: a room can be called NONE and must not vanish. */
    internal fun optionalText(value: String?): String = value?.let { "some:${text(it)}" } ?: "none"

    /**
     * What one line of a work order is, as one token.
     *
     * The circuit renders as its own word rather than as a Subroutine name, because it is not one
     * — a state row that spelled it as a kind would replay into a round with an eleventh Subroutine
     * in it.
     */
    private fun work(work: AssignedWork): String = when (work) {
        is AssignedWork.Subroutine -> work.kind.toString()
        AssignedWork.ArrayWipe -> "ArrayWipe"
    }

    /**
     * How a meeting was called. Two forms, and the reported seat is part of the fact.
     *
     * `card` and `report:3` rather than a bare seat or a bare word: the discriminator is on the
     * page, so a recording cannot be read as the wrong trigger by a parser that guessed from the
     * shape of the value.
     */
    private fun trigger(trigger: MeetingTrigger): String = when (trigger) {
        is MeetingTrigger.MeetingCard -> "card"
        is MeetingTrigger.RevokeReported -> "report:${num(trigger.reported.index)}"
    }

    /**
     * A refusal by the admission gate (D-066).
     *
     * Carries the event that was refused as well as the reason, because "something was refused at
     * tick 40" and "a ContactMade was refused at tick 40" lead to different places — and the
     * whole point of recording a refusal is that the recording must not disagree with reality
     * about what reached the rules.
     */
    fun render(index: Int, event: Event, reason: RefusalReason): String =
        "Refused|index=${num(index)}|at=${num(event.at.step)}|" +
            "event=${render(event).substringBefore('|')}|reason=$reason"

    fun render(effect: Effect): String = when (effect) {
        is Effect.LampSet ->
            "LampSet|seat=${effect.seat.index}|luminance=${effect.luminance}"
        is Effect.AbilityFired ->
            "AbilityFired|actor=${effect.actor.index}|cooldownStarted=${effect.cooldownStarted}"
        is Effect.ContactAnswered -> "ContactAnswered|seat=${effect.seat.index}"
        // Every seat but the actor's, beside its `AbilityFired` (D-263): who it was built for.
        is Effect.AbilityBlanked -> "AbilityBlanked|seat=${effect.seat.index}"
        // **The whole gesture, luminances included.** The lamp is a pure function of state (rule 5),
        // so the two numbers ARE the effect rather than a device's reading of it -- a row that held
        // only `Surged|seat=` could not tell a replayed slam from one that lit nothing.
        is Effect.Surged ->
            "Surged|seat=${effect.seat.index}|peak=${num(effect.peak)}" +
                "|black=${num(effect.black)}|sound=${effect.sound}|haptic=${effect.haptic}"
        // The picker, as the seat receives it -- the same destinations, in the same order. The
        // Egress-node exclusion is therefore VISIBLE in the recording as two fewer cards on the
        // rows during an Egress, which is the only artifact that can show D-150 holding.
        is Effect.IsolateTargetsOffered ->
            "IsolateTargetsOffered|seat=${effect.seat.index}|targets=" +
                effect.targets.joinToString(",") { destination(it) }
        // **The blank beside the offer, and only how many it pads for** (D-258). The list is the
        // offer's and is on the offer's row; this row says the blank was built, for whom, and over
        // how many cards, which is the width a Resident's phone was sent.
        is Effect.IsolateTargetsBlanked ->
            "IsolateTargetsBlanked|seat=${effect.seat.index}|over=${effect.targets.size}"
        // **The jam, and there is no seat on it to render** (D-186). Every other ability's rows in
        // this transcript name somebody -- an actor, a target, a seat the offer was drawn for --
        // and this one names nobody, because the house never knew. A reader of a recording can see
        // THAT the house was jammed and when, and cannot see who did it, which is the same thing
        // every player in the building could see.
        is Effect.StaticOpened ->
            "StaticOpened|seconds=${num(effect.seconds)}|seed=${num(effect.seed)}"
        is Effect.SubroutineGraded ->
            "SubroutineGraded|seat=${effect.seat.index}|accepted=${effect.accepted}"
        is Effect.SubroutineProgressed ->
            "SubroutineProgressed|remaining=${effect.remaining}"
        // The percentage and never a total, exactly as narrow here as the wire is -- a recording
        // that helpfully wrote the denominator beside it would be the one artefact of this game in
        // which the Insider count is divisible out of the meter.
        is Effect.SystemIntegrityLive ->
            "SystemIntegrityLive|percent=${num(effect.percent)}"
        is Effect.SystemIntegrityBatched ->
            "SystemIntegrityBatched|percent=${num(effect.percent)}"
        is Effect.MessageDelivered ->
            "MessageDelivered|seat=${effect.seat.index}|body=${text(effect.body)}"
        // The lines as the seat receives them, blocked ones as `?`. A blocked line renders with no
        // name because it HAS no name to render -- OrderLine.Blocked cannot carry one -- so the
        // transcript is as narrow here as the wire is, and a leak could not hide in the recording's
        // own formatting.
        is Effect.WorkOrderIssued ->
            "WorkOrderIssued|seat=${effect.seat.index}|lines=" +
                effect.lines.joinToString(",") { line ->
                    when (line) {
                        // The destinations too, because they are on the wire and this row is a
                        // picture of the wire. A blocked line renders as a bare `?` with no
                        // destination after it -- not because this formatting withholds one, but
                        // because `OrderLine.Blocked` has none to render.
                        is OrderLine.Known ->
                            "${line.index}:${work(line.work)}${if (line.done) "!" else ""}" +
                                "@" + line.at.joinToString(".") { destination(it) }
                        is OrderLine.Blocked -> "${line.index}:?"
                    }
                }
        // The answer to a scan, and the null renders as a token rather than as an absent field:
        // NOTHING FOR YOU HERE and a real opening are one kind and must stay one line shape, or
        // the recording becomes the one place the two are trivially separable (rule 1).
        //
        // `parameters` is on the row and the answer is NOT -- this renders the client-facing
        // SubroutineInstance, so the transcript is exactly as narrow here as the wire is.
        is Effect.ScanAnswered ->
            "ScanAnswered|seat=${effect.seat.index}|opened=" +
                (effect.opened?.let {
                    "${it.entry}:${it.subroutine}:${it.parameters.joinToString(".") { p -> num(p) }}"
                } ?: "none")
        // Open and held are one kind and one row shape, for ScanAnswered's reason (D-199).
        is Effect.TerminalAnswered ->
            "TerminalAnswered|seat=${effect.seat.index}|reading=${effect.reading}"
        is Effect.PresenceChanged ->
            "PresenceChanged|seat=${effect.seat.index}" +
                "|at=${effect.at?.let { text(it.value) } ?: "none"}|open=${effect.open}"
        is Effect.OpeningMessage ->
            "OpeningMessage|seat=${effect.seat.index}|haptic=${effect.haptic}"
        // **The seat and the role, both written out, and this row is why a recording is a secret.**
        // The transcript is the authority's own ledger and it holds ground truth everywhere -- who
        // was revoked, who was drawn -- so writing the role here adds no channel that
        // `Recording`'s state rows did not already carry. What it buys is that a replay compares
        // the reveal each phone was actually sent rather than re-deriving it, which is the whole
        // reason the transcript never renders anything it can look up.
        is Effect.RoleRevealed ->
            "RoleRevealed|seat=${effect.seat.index}|role=${effect.role}"
        // **The seat and the SIZE, and never one of the names** (D-115). The lines' rule and the
        // arming row's, applied to the third round-scoped text: names arrive for the round and are
        // gone when it ends, and a recording outlives the evening. What a replay needs is that the
        // list was this long and reached this seat -- a rebuilt roster of blanks of the same length
        // renders this row byte-identically, which is what keeps a differential run across a
        // recording honest without the transcript holding anybody's name. The seats are the
        // non-null slots: the list is indexed by seat, null where no seat sits (D-274).
        is Effect.RosterIssued ->
            "RosterIssued|seat=${effect.seat.index}|seats=${num(effect.roster.count { it != null })}"
        // **The seat and the seed itself** (D-212). The recording is the authority's artifact and
        // already holds the round's seed every contact seed is derived from, so the hex adds nothing
        // a replay could not re-derive -- and rendering it is what lets the differential harness see
        // a seed that moved with a role, which is the leak this row exists to be compared for.
        is Effect.ContactSeedIssued ->
            "ContactSeedIssued|seat=${effect.seat.index}|seed=${effect.seed.hex}"
        is Effect.MeetingOpened ->
            "MeetingOpened|caller=${effect.caller.index}|trigger=${trigger(effect.trigger)}" +
                "|haptic=${effect.haptic}"
        // The seat, because the recording is the authority's artifact and the authority is what
        // addressed it. The WIRE member has no field -- see Effect.MeetingCalling -- and that is the
        // one place these two deliberately differ: a client cannot be told which seat it is, and a
        // recording that could not say which seat called the meeting would be a recording that
        // could not replay one.
        is Effect.MeetingCalling ->
            "MeetingCalling|seat=${effect.seat.index}"
        is Effect.StandAndWalkIn ->
            "StandAndWalkIn|seat=${effect.seat.index}|haptic=${effect.haptic}"
        is Effect.CheckInProgressed ->
            "CheckInProgressed|present=${effect.present}|expected=${effect.expected}"
        // The ballot's window is on the row and renders as a token when it is absent, for the
        // reason a scan's null does: a phase that carries no window and a phase that carries one
        // must not be separable by the shape of the line rather than by its contents.
        is Effect.NoticeIssued ->
            "NoticeIssued|unreachable=" + effect.unreachable.joinToString(",") { num(it.index) }
        is Effect.MeetingPhaseOpened ->
            "MeetingPhaseOpened|phase=${effect.phase}" +
                "|voteWindow=${effect.voteWindowSeconds?.let { num(it) } ?: "none"}" +
                "|haptic=${effect.haptic}"
        is Effect.ReadyProgressed ->
            "ReadyProgressed|ready=${effect.ready}|expected=${effect.expected}"
        is Effect.VoteHeld ->
            "VoteHeld|seat=${effect.seat.index}|selection=${seatOrNone(effect.selection)}" +
                "|locked=${effect.locked}"
        is Effect.VoteSelectionShown ->
            "VoteSelectionShown|voter=${effect.voter.index}|selection=${seatOrNone(effect.selection)}"
        is Effect.VoteProgressed ->
            "VoteProgressed|locked=${effect.locked}|expected=${effect.expected}"
        is Effect.MeetingResult ->
            "MeetingResult|restrained=${seatOrNone(effect.restrained)}" +
                "|voted=${effect.voted}|living=${effect.living}|haptic=${effect.haptic}"
        is Effect.MeetingResolved ->
            "MeetingResolved|restrained=${seatOrNone(effect.restrained)}|attribution=" +
                effect.attribution.joinToString(",") { "${it.first.index}>${seatOrNone(it.second)}" }
        is Effect.RestrainedTakeover ->
            "RestrainedTakeover|seat=${effect.seat.index}|haptic=${effect.haptic}"
        // The seat, because the recording is the authority's artifact and the authority is what
        // addressed it — `MeetingCalling`'s row, and the same one place these two deliberately
        // differ from their wire members.
        is Effect.RevokeLanded ->
            "RevokeLanded|seat=${effect.seat.index}"
        is Effect.MeetingEnded ->
            "MeetingEnded|haptic=${effect.haptic}"
        is Effect.EgressOpened ->
            "EgressOpened|seat=${effect.seat.index}|type=${effect.type}" +
                // Card AND room, because the effect carries both now and the transcript renders
                // what crossed rather than what it can look up. The room is host-authored text and
                // goes through `text` for the escape the marker id already needed (E-L4-4).
                "|nodes=" + effect.nodes.joinToString(",") {
                    "${text(it.marker.value)}:${text(it.shape)}:${text(it.room)}"
                } +
                "|remaining=${num(effect.remaining)}|haptic=${effect.haptic}" +
                "|pulseFrom=${num(effect.pulseFrom)}"
        is Effect.EgressHeld ->
            "EgressHeld|remaining=${num(effect.remaining)}|running=${effect.running}" +
                "|haptic=${effect.haptic}"
        is Effect.SyncPulseAnswered ->
            "SyncPulseAnswered|seat=${effect.seat.index}|held=${effect.held}"
        // Every seat but the tapper's, beside its `SyncPulseAnswered` (C2): who it was built for.
        is Effect.SyncPulseBlanked -> "SyncPulseBlanked|seat=${effect.seat.index}"
        // No seats and no node, because the effect has none -- see Effect.EgressContained. The
        // transcript is exactly as narrow here as the wire is, so an attribution could not hide in
        // the recording's own formatting.
        is Effect.EgressContained ->
            "EgressContained|haptic=${effect.haptic}"
        is Effect.EgressSucceeded ->
            "EgressSucceeded|haptic=${effect.haptic}"
        is Effect.RoundEnded ->
            "RoundEnded|seat=${effect.seat.index}|winner=${effect.winner}|by=${effect.by}" +
                "|haptic=${effect.haptic}"
        // **The seats, and neither the names nor one word of what was held over any of them**
        // (D-115, D-116).
        //
        // Which SEATS were drawn belongs in a recording -- it is the single most useful thing an
        // authority-side artefact can tell you about a round that went wrong. Neither round-scoped
        // text does, for the reason the arming row above gives at length: a recording outlives the
        // evening, the promise printed on the screen where a line was typed does not survive being
        // written into one, and D-115 scoped the names to the round on exactly the same terms.
        //
        // This is also the hinge that keeps replay exact. Rendering seats alone means the row a
        // replayed round produces is identical whether the lines were rebuilt as blanks or never
        // supplied at all -- so the recording is narrower than the wire here, on purpose, and it
        // is the only effect in this file of which that is true.
        is Effect.InsidersRevealed ->
            "InsidersRevealed|seats=" + effect.insiders.joinToString(",") { num(it.seat.index) }
        is Effect.HouseSignedOff ->
            "HouseSignedOff|seat=${effect.seat.index}|body=${text(effect.body)}"
    }

    /**
     * Fields are `Pair<String, String>`, not `Pair<String, Any>`.
     *
     * Two reasons, both of which bit. `Any` meant values rendered through a generated
     * `toString()` — the exact thing this file's header says it refuses to rely on — so
     * `"seat" to seat` would have written `Seat(index=3)` into a recording meant to outlive the
     * build. And it let raw Strings through unescaped: a MarkerId is external input, read off a
     * house marker, and one containing a newline produced a genuine forged event row in
     * `toText()`. Typing the vararg forces every call site through [text] or [num], and [text]
     * escapes.
     */
    private fun row(name: String, at: home.someoneshome.model.Tick, vararg fields: Pair<String, String>): String =
        buildString {
            append(name).append("|at=").append(at.step)
            for ((k, v) in fields) append('|').append(k).append('=').append(v)
        }

    /** A string field. Always escaped — separators must not survive inside a value. */
    private fun text(s: String): String = escape(s)

    /** A numeric field. Cannot contain a separator, so it needs no escaping. */
    private fun num(n: Long): String = n.toString()
    private fun num(n: Int): String = n.toString()

    /**
     * Absence renders as a token, never as a sentinel integer.
     *
     * `?.index ?: -1` made a skipped vote and a vote for `Seat(-1)` render identically, so the
     * transcript stopped being injective at precisely the point where "who abstained" matters.
     */
    private fun seatOrNone(seat: Seat?): String = seat?.let { num(it.index) } ?: "none"

    /**
     * Field separators must not survive inside a value, or a body could forge a row.
     *
     * **The comma is in here because lists are comma-joined**, and a marker id is external input
     * read off a card in somebody's house. One containing a comma would have forged a second
     * element in the arming event's marker list — the same fault the pipe had, one separator down.
     *
     * **The colon is in here now, and it closes E-L4-4.** It has always been a sub-separator inside
     * `open=` and `presence=`, and a marker id can contain one — the payload alphabet is QR
     * alphanumeric minus SPACE, which includes `:` — so two different states could render
     * identically. This unit made it sharper rather than merely latent: a destination is
     * `card:glyph:room`, and a **room name is text a host typed**, so an unescaped colon there
     * would forge a fourth part out of a name like `KITCHEN: BACK`. Third member of the family the
     * pipe and the comma opened, and the last separator this format has.
     */
    private fun escape(s: String): String = s
        .replace("\\", "\\\\").replace("|", "\\p").replace(",", "\\c")
        .replace(":", "\\o").replace("\n", "\\n")

    /**
     * A canonical rendering of authority STATE.
     *
     * This exists because comparing effect streams is structurally insufficient, and rule 1 is
     * why. Rule 1 requires the effect stream to be identical whether a revoke landed or not —
     * so for the single most important transition in the game, "the transcript matched" is the
     * weakest possible evidence. A build where the revoke ability did nothing at all replayed
     * clean against a recording made before it was broken.
     *
     * **Every field of authority state, with no exceptions for fields nothing writes yet.** A
     * field this row omits is a field that guarantee does not cover, and `ended` is the worst
     * possible omission to make: it feeds `roundStateOf`, so it decides what every client is
     * permitted to receive. It was omitted for exactly one commit.
     *
     * State rows close that hole. They never go to a client — this is authority-side debugging
     * tooling, and `GameState` still has no wire encoding.
     */
    fun render(state: GameState): String = buildString {
        append("armed=").append(state.armed)
        // **`ended` and the reason, as one field.** It used to be a bare boolean with no writer,
        // and it was the worst possible omission when it was omitted -- it feeds `roundStateOf`,
        // so it decides what every client is permitted to receive. It is derived from `outcome`
        // now, so the row prints the outcome and a replay that ended a round the right way for the
        // wrong reason no longer matches.
        append("|ended=").append(state.outcome?.let { "${it.winner}:${it.by}" } ?: "none")
        append("|seats=").append(state.seats.joinToString(",") { num(it.index) })
        append("|insiders=").append(state.insiderSeats.joinToString(",") { num(it.index) })
        append("|revoked=").append(state.revoked.joinToString(",") { num(it.index) })
        // Two lists, never one. A Revoke is system power the house lent; a Restrain is a physical
        // act it could not prevent and then ratified (rule 9). A state row that folded them
        // together would replay a round in which the distinction had never existed.
        append("|newlyRevoked=").append(state.newlyRevoked.joinToString(",") { num(it.index) })
        append("|restrained=").append(state.restrained.joinToString(",") { num(it.index) })
        // Each arm with the tick its window closes (the owner's 2026-11-18 ruling), so a replay that
        // opened a different window does not match one that opened this one.
        append("|armedRevoke=").append(
            state.revokeArms.joinToString(",") { "${num(it.seat.index)}@${num(it.lapsesAt.step)}" }
        )
        // S4's list (D-219), which this row had left out: a replay that reported a seat twice must
        // not match one that refused the second report.
        append("|reported=").append(state.reported.joinToString(",") { num(it.index) })
        // The touches answered at the current tick (T1, D-220), so a replay that answered a second
        // half as a first does not match one that recognised it.
        append("|touches=").append(
            state.touches.joinToString(",") { "${num(it.at.step)}:${num(it.a.index)}:${num(it.b.index)}" }
        )
        // The seats the next meeting's notice names (D-187, D-223).
        append("|unreachable=").append(state.unreachable.joinToString(",") { num(it.index) })
        // Every Insider cooldown, as the tick it is ready at (D-132). A remaining count would be a
        // fact about the moment somebody asked; a tick is a fact about the round, and it replays.
        append("|cooldowns=").append(
            state.cooldowns.joinToString(",") {
                "${num(it.seat.index)}:${it.ability}:${num(it.readyAt.step)}"
            }
        )
        // **The pause mark for the Access pool used to be written here** and is gone with the state
        // (the owner's 2026-08-24 ruling). D-183 and D-184 between them made it unobservable, so
        // every recording since has carried a column that could not change a replay's answer. This
        // is the shape change version 17 exists for: a version 16 state row holds `accessPaused=`
        // where this build writes nothing, so the two cannot be compared row by row.
        // **Which cards are dark, and until when.** It reaches NO client -- there is no effect that
        // carries it and therefore no schema row -- so a state row is the only artifact that holds
        // it, exactly as the presence plane's is. A replay that stopped short of it would certify a
        // build in which Isolate did nothing at all, which is rule 1's own warning about the revoke.
        append("|isolated=").append(
            state.isolated.joinToString(",") { "${text(it.marker.value)}:${num(it.until.step)}" }
        )
        // **The Egress, in full — offers and lockouts included.** It was a boolean, and a boolean
        // could not tell a replayed Egress from a different one that happened to end the same way.
        // The offers are who is standing at which node with a live beat, which reaches no client
        // (they have no schema row and the presence plane they come from has none either), so a
        // state row is the ONLY artifact that can hold them — and a replay that stopped short of
        // them would certify a build whose pairs never formed.
        append("|egress=").append(state.egress?.let { egress ->
            "${egress.type}:${egress.nodes.joinToString(".") { text(it.value) }}" +
                ":fired=${num(egress.firedAt.step)}:deadline=${num(egress.deadline.step)}" +
                // The pulse grid on the host's clock (C2), which a pause never moves.
                ":pulse=${num(egress.pulseFrom)}" +
                // The pause mark, because D-133's arithmetic is the whole of the rule: a recording
                // that held only the deadline could not tell a paused timer from a moved one.
                ":paused=${egress.pausedAt?.let { p -> num(p.step) } ?: "none"}" +
                ":offers=${egress.offers.joinToString(".") {
                    "${num(it.seat.index)}>${text(it.node.value)}@${num(it.at.step)}"
                }}" +
                ":lockouts=${egress.lockouts.joinToString(".") {
                    "${num(it.seat.index)}>${num(it.until.step)}"
                }}"
        } ?: "none")
        // The house-wide shared clock (D-132), as the tick it is ready at. One value, never a row
        // per seat -- a state row that held N rows would let a build de-synchronise them and still
        // replay clean.
        append("|egressReady=").append(num(state.egressReadyAt.step))
        append("|integrity=").append(num(state.systemIntegrity))
        // The work order, ANSWER KEY INCLUDED. A state row is authority-side debugging and holds
        // complete ground truth by design — recordings are gitignored and never handed to a
        // player. Omitting `expected` would leave the one piece of state a verdict is computed
        // from outside the guarantee, which is the `ended` omission again in a new costume.
        append("|open=").append(
            state.openSubroutines.joinToString(",") { open ->
                "${num(open.seat.index)}:${num(open.entry)}" +
                    ":${open.armedAt?.let { text(it.value) } ?: "none"}" +
                    // The QUESTION and the answer, both. A row that held only the answer could not
                    // tell a round where the house asked something different from one where it
                    // graded differently -- and the question is re-drawn by every re-scan now, so
                    // it is the field that moves when D-139's re-draw breaks.
                    ":${open.parameters.joinToString(".") { num(it) }}" +
                    ":${open.expected.joinToString(".") { num(it) }}"
            }
        )
        // **The round's draw, in full — orders with their answer keys, the stations and the lit
        // markers.** Same argument the work order's `expected` was added under: a state row that
        // stopped short of what a verdict is computed from would leave the one thing that decides
        // an outcome outside the replay guarantee. Recordings hold complete authority state, are
        // gitignored, and are never handed to a player.
        append("|orders=").append(
            state.workOrders.joinToString(",") { order ->
                "${num(order.seat.index)}:" + order.entries.joinToString(".") { entry ->
                    // The work and EVERY card it is done at. A circuit is three stations on one
                    // entry, so a row that held a single anchor could not tell a replayed circuit
                    // from one drawn against a different pair of stations.
                    "${num(entry.index)}/${work(entry.work)}" +
                        "/${entry.markers.joinToString("+") { text(it.value) }}" +
                        "/${entry.blockedBy.joinToString("-") { num(it) }}" +
                        "/${if (entry.done) "done" else "open"}"
                }
            }
        )
        append("|stations=").append(state.stations.joinToString(",") { text(it.value) })
        append("|active=").append(state.activeMarkers.joinToString(",") { text(it.value) })
        // **The home, because a work order's destination column is computed from it.** Host-
        // authored room names, and the one piece of geography this file holds -- a state row that
        // stopped short of it would certify a replay whose orders pointed nowhere.
        append("|home=").append(state.destinations.joinToString(",") { destination(it) })
        // **The presence plane, in the one place it is allowed to appear.** It reaches no client
        // -- EmitSchema gives PresenceChanged no row at all -- so a state row is the only artifact
        // that holds it, and a replay that stopped short of it would certify a build whose windows
        // never closed.
        append("|presence=").append(
            state.presence.joinToString(",") {
                "${num(it.seat.index)}:${it.at?.let { m -> text(m.value) } ?: "none"}" +
                    ":${if (it.open) "open" else "shut"}:${num(it.since.step)}"
            }
        )
        // **The jam's deadline, and never who set it** (D-186). The presence rows above are here
        // because they reach no client; this is here for the opposite reason -- it reaches every
        // living phone, as a window that has already started, so a replay that did not hold the
        // deadline would come out of the jam at the wrong moment. **It carries no seat**, which is
        // the ability's anonymity kept in the one artifact of this game that outlives the evening.
        append("|static=").append(state.staticUntil?.let { num(it.step) } ?: "none")
        // **The station's card and who holds it** (D-199, D-203). Load-bearing for the presence
        // plane's reason: the lock reaches no client as itself, so a state row is the only
        // artifact that holds it, and a replay that stopped short of it would certify a build
        // whose station never came free.
        append("|terminal=").append(state.terminal?.let { text(it.value) } ?: "none")
        append("|reader=").append(seatOrNone(state.reader))
        // **The plan, the station's room and the spectator map** (O2, O4). None of it reaches a
        // client -- no effect carries a placement, an observation or the plan -- so a state row is
        // the only artifact that holds them, as it is the presence plane's. The observations carry
        // their true count beside the reported one and the stored jitter: a replay that rolled a
        // different error, or rolled it twice, stops matching here.
        append("|rooms=").append(rooms(state.plan))
        append("|storeys=").append(storeys(state.plan))
        append("|terminalRoom=").append(optionalText(state.terminalRoom))
        append("|shift=").append(num(state.shiftStartedAt.step))
        append("|sightings=").append(
            state.sightings.joinToString(",") { "${num(it.seat.index)}:${text(it.room)}:${num(it.at.step)}" }
        )
        append("|observations=").append(
            state.observations.joinToString(",") {
                "${num(it.id)}:${text(it.room)}:${num(it.seenAt.step)}:${num(it.trueCount)}" +
                    ":${num(it.reportedCount)}:${num(it.ageJitter)}"
            }
        )
        append("|nextObservation=").append(num(state.nextObservation))
        // **The meeting, ballots and all.** Live selections are the one thing at a meeting that
        // only a player outside the system may read (D-075), which is exactly why they belong in
        // an authority-side state row: a recording that stopped short of them could not tell a
        // replayed vote from a different one that happened to reach the same result.
        append("|meeting=").append(state.meeting?.let { meeting ->
            "${meeting.caller.index}:${trigger(meeting.trigger)}:${meeting.phase}" +
                ":in=${meeting.checkedIn.joinToString(".") { num(it.index) }}" +
                ":ready=${meeting.ready.joinToString(".") { num(it.index) }}" +
                ":ballots=${meeting.ballots.joinToString(".") {
                    "${num(it.voter.index)}>${seatOrNone(it.selection)}${if (it.locked) "!" else ""}"
                }}" +
                ":pending=${seatOrNone(meeting.restrainPending)}"
        } ?: "none")
        // **The seats whose line the house is holding, and never the text** (D-116). The arming
        // row's argument, applied to the state it produced — and this is the field that goes to
        // EMPTY the instant the round ends, so a replay that skipped the deletion stops matching
        // here rather than nowhere.
        append("|lines=").append(state.oneLines.joinToString(",") { num(it.seat.index) })
        // **The seats that said what to call them, and never the names** (D-115). The lines' field
        // above, one round-scoped text over — and like it, this is the field that goes to EMPTY the
        // instant the round ends, so a replay that skipped the deletion stops matching here.
        append("|names=").append(state.names.joinToString(",") { num(it.seat.index) })
        // The ballot's length, locked at arming. A state row that omitted it would replay a meeting
        // whose window came from this build's default rather than from the evening's host.
        append("|voteWindow=").append(num(state.voteWindowSeconds))
        append("|nextEntity=").append(num(state.nextEntity))
        append("|seed=").append(num(state.seed))
    }

    /**
     * **How many Statics were fired in this recording — the cut-risk metric, made measurable**
     * (`gdd.md:1347`).
     *
     * ### The metric survived the ability being replaced, and the question it asks has changed
     *
     * `gdd.md:1347` used to read *Spoof usage — fired at least once per three rounds. If it is never
     * used, cut it*, and D-186 is the owner **taking that risk rather than continuing to watch it**:
     * the mechanic was cut and the slot was kept. So this counter is not asking the same question
     * any more. Nobody expects Static to go unused — it costs nothing to understand and it is fired
     * because you are about to do something — and what is worth counting now is the other
     * direction: *how often does the house go dark, and does a shift start to feel like it.*
     *
     * The counting method is unchanged and is why the function is kept rather than rewritten: the
     * metric is answered off **the artifact every round already produces** rather than off somebody
     * with a clipboard in a dark house. A playtest measurement that depends on a human remembering
     * to look gets reported as "felt frequent", which is not a number.
     *
     * ### It counts FIRES, including the ones that did nothing
     *
     * A Static fired into a running pool leaves an `Event.StaticFired` and no jam. It still counts
     * here, because what is measured is whether anybody **reached for** the ability. Read off the
     * recorded events rather than off the effects for that reason, and because refused fires are
     * recorded too (D-066) — the invisible drop this project refuses.
     *
     * ⚠️ **Three rounds is the unit and one recording is one round**, so any threshold is a
     * judgement across a session's recordings rather than a property of any single one. That is
     * deliberately not encoded here: a function that graded the number would be this build taking a
     * design decision it does not own.
     */
    fun staticFires(events: List<Event>): Int = events.count { it is Event.StaticFired }
}
