package home.someoneshome.app

import home.someoneshome.model.DriftPath
import home.someoneshome.model.Effect
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.MalformedSubroutineParameters
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.Haptics
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.Subroutine

/**
 * **Every buzz in the game goes through here, and there is nowhere else to get one.**
 *
 * An effect arrives on this phone; if it carries a [Haptic], the motor plays that kind. That is
 * the whole class, and its shortness is the point — D-102's *identical for every player* survives
 * exactly as long as there is one place that turns an arrival into a vibration.
 *
 * ### It is built by [RoundOverTransport], which is what closes E-D1-1
 *
 * For weeks the only construction of this class was `HapticCheat`'s, in the cheat source set — so a
 * release build had **no haptic wiring at all** and a real round buzzed on nobody's phone. The
 * pushes named their haptic in the log and the motor never heard about it; the Handshake, whose
 * whole question is a rhythm, was the one Subroutine a person could reach and the one they could
 * not perform. The recorded prescription was *the day the push pipeline lands, `HouseBuzzer` moves
 * to the app root beside it*, and this is that: the round's client half **takes one and cannot be
 * built without one**, so a root that forgets to wire the motor is a root that does not compile.
 * The bench keeps its own, on the same session's motor.
 *
 * ### Two channels, and the second one is the house's questions
 *
 * [heard] is the house saying a thing: a [Haptic] off the push, or D-160's ring. [showing] is the
 * other half of what a motor is for in this game — three of the ten Subroutines put their entire
 * question in it, and the numbers are the house's, drawn per scan. See that method for why a screen
 * may settle *when* a script plays and may never settle *which buzz* an arrival makes.
 *
 * ### It is not `PanelVals.buzzes`, and the two must never be joined
 *
 * `ui` already holds a set of screens that buzz, and wiring *that* to the motor is the obvious
 * shortcut and the wrong one. D-134's rule: **the buzz rides the effect rather than living in a
 * table in `ui`** — a client-side lookup that could say *"…and if it was you, buzz differently"*
 * is the device deciding a game answer. `PanelVals.BUZZING` exists so the flow can derive which
 * screens the house drives (`Flow.houseDriving`) and so the render harness can draw a screen
 * knowing it arrived unasked. It is a description of the design, not a source of vibrations.
 *
 * ### It never asks whether the effect was *meant* for this phone
 *
 * There is no filter here and there must not be one. What reaches a client is `EmitSchema`'s
 * decision, made once, on the authority, and a second opinion on the device is the shape rule 1
 * spends its length arguing against: an effect this phone declined to buzz for is an effect whose
 * absence is a fact about this phone's role. Everything delivered buzzes; delivery is decided
 * upstream and fails closed.
 *
 * ### The `when` is exhaustive on purpose
 *
 * A new `Effect` does not compile until somebody has written down whether it buzzes. That is the
 * same fail-closed direction as the schema: the failure is *"my effect doesn't buzz"*, found the
 * first time anyone runs it, never *"my effect buzzes on one role's phone"*, found never.
 */
class HouseBuzzer(
    private val haptics: Haptics,
    /**
     * Where a refusal goes. **Rule 6**: errors are silent to the player and loud to the authority,
     * so the one thing that can go wrong down here — a Subroutine instance whose parameters are not
     * a question — is written to a console and to nothing a player can see.
     */
    private val note: (String) -> Unit = {},
) {

    /**
     * **The question this phone's last scan opened, or null.**
     *
     * Three of the ten Subroutines *are* a haptic pattern — Handshake's rhythm, Sniff's two groups,
     * Drift's *now* — and the pattern is the house's, drawn per scan and carried on the instance in
     * [SubroutineInstance.parameters]. It is remembered here because the arrival and the playing are
     * two different moments: the house answers the scan, the player reads the briefing on the caught
     * scan, and *then* presses BEGIN. A script played on arrival would buzz the question at somebody
     * reading the sentence that explains it.
     *
     * **Null is silence and never a fixture.** `SubroutineModel.HANDSHAKE` and its two neighbours are
     * the unhoused port's furniture; a phone that fell back to one would buzz a rhythm the house
     * never dealt and then grade the player's answer against the one it did.
     */
    private var open: SubroutineInstance? = null

    /** True from a ring until anything silences it. What [showing] needs to not cut a Long short. */
    private var ringing: Boolean = false

    /** The Subroutine screen whose script is playing, or null. Cleared by anything the house says. */
    private var scripting: ScreenId? = null

    /**
     * An effect landed on this phone. Buzz if it carries a kind, ring if it is the call, and stay
     * silent if it is neither.
     *
     * **No branch on role, seat or content.** Which *effect* it was is the only question asked,
     * and it is asked twice — once by [ringing] and once by [haptic] — both of them exhaustive
     * `when`s over the effect type and nothing else. Two phones receiving the same effect do the
     * same thing, which is the whole of D-102.
     */
    fun heard(effect: Effect) {
        if (effect is Effect.ScanAnswered) open = effect.opened
        // A scan of the T card spends whatever was armed (D-199), so nothing is open after it.
        if (effect is Effect.TerminalAnswered) open = null
        if (effect.ringing()) return ring()
        buzz(effect.haptic() ?: return)
    }

    /**
     * **The same arrival, off the wire** — what a phone that is not the host actually receives.
     *
     * A client never holds an [Effect]. The authority resolves each one through `EmitSchema`,
     * projects it into a [RoundMessage], and writes *that* down the socket — so the effect-shaped
     * overload above is the host's own view of what it said, and this is every phone's view of what
     * it was told. **The guarantee has to hold on this side**, because this is the side a player is
     * holding, and until this existed the buzzer had no production caller at all.
     *
     * It answers the same two questions in the same order and it decides nothing new: the haptic
     * was chosen by `Rules.kt`, carried on the effect, and copied onto the message by the
     * projection. `HouseBuzzTest.theWireBuzzesWhateverTheEffectBuzzed` walks every effect in the
     * game and asserts the two overloads agree, so the second list cannot drift from the first.
     */
    fun heard(message: RoundMessage) {
        if (message is RoundMessage.ScanAnswered) open = message.opened
        if (message is RoundMessage.TerminalAnswered) open = null
        if (message.ringing()) return ring()
        buzz(message.haptic() ?: return)
    }

    /**
     * **The phone was answered, so it stops ringing** — and this is the only thing that stops it.
     *
     * D-160 makes the call the one signal in the game that does not give up: it repeats
     * indefinitely, because a meeting has been called and the round stops until everybody has it.
     * Something has to end it, and the something is a person picking the phone up. The screen that
     * knows they did calls this, through [showing].
     *
     * It is [Haptics.stop] and nothing cleverer on purpose: the motor replaces rather than queues,
     * so anything the house says next silences the ring anyway, and a second mechanism for
     * silence would be a second thing to get wrong on the one signal a player must not miss.
     */
    fun answered() {
        ringing = false
        haptics.stop()
    }

    /**
     * **This phone moved to a screen** — and there are exactly two things the motor does about it.
     *
     * ### ⚠️ It is not `PanelVals.BUZZING` and it must never grow into it
     *
     * The class KDoc spends its length on why a screen may not decide a *buzz*, and nothing here
     * touches one: no [Haptic] is chosen on this path, no branch reads a role or a seat, and the
     * only kind-bearing arrival is still the house's own push through [heard]. What a screen is
     * allowed to settle is the two questions that are genuinely about *where the player's attention
     * is*, neither of which any push can answer:
     *
     * - **The ring stops when this phone stops ringing.** D-160's *until answered* needs something
     *   that answers, and answering a phone is walking off the ring — the six seconds running out,
     *   or `MeetingCalling` moving the caller to their own screen. It fires only while [ringing], so
     *   a screen change during one of D-135's four remaining Longs cannot cut it short.
     * - **A Subroutine's own question is buzzed when its work opens.** Handshake's rhythm, Sniff's
     *   two groups and Drift's *now* are the house's [open] instance played back, and the moment a
     *   player is owed them is the moment they are standing on the screen that takes the answer.
     *
     * ### It plays the instance and never a fixture, and the mismatch is silence
     *
     * The screen is resolved to a [SubroutineKind] through the roster and compared with the kind the
     * house actually dealt. **A phone standing on a Handshake with a Sniff open buzzes nothing** —
     * which is the fail-closed direction, because the other one is a player tapping back a rhythm
     * against an answer key for a different piece of work.
     */
    fun showing(screen: ScreenId) {
        if (ringing && screen !in RINGS) answered()
        val playing = scripting
        if (playing != null && playing != screen) {
            // The work went away and the phone is still talking about it. `stop` rather than
            // letting it run out: a Sniff left mid-question is a phone buzzing a question at a
            // player who is somewhere else in a dark house.
            scripting = null
            haptics.stop()
        }
        val instance = open ?: return
        if (Subroutine.on(screen)?.kind != instance.subroutine) return
        val script = instance.script(note) ?: return
        scripting = screen
        haptics.play(script)
    }

    private fun ring() {
        ringing = true
        scripting = null
        haptics.play(HouseBuzz.RING, repeating = true)
    }

    private fun buzz(haptic: Haptic) {
        ringing = false
        scripting = null
        haptics.buzz(haptic)
    }

    private companion object {

        /**
         * **The screens this phone is ringing on, and there are two.** `Call` and `Found` are the
         * same summons with a different header; `Calling` is deliberately absent, because the caller
         * arriving there is the caller having been answered (E-R1-1).
         */
        val RINGS: Set<ScreenId> = setOf(ScreenId.Call, ScreenId.Found)
    }
}

/**
 * **The pattern a Subroutine instance IS, or null for the seven that are looked at instead.**
 *
 * Three of the design's ten put their whole question in the motor, and the numbers that make each
 * one are the house's: drawn per scan in `core/Subroutines.kt`, carried on
 * [SubroutineInstance.parameters], read here by the same `of` the screens read them with. **Nothing
 * is composed on this side** — the app root plays a list `model` built.
 *
 * ### Exhaustive, in [Effect.haptic]'s direction
 *
 * An eleventh [SubroutineKind] does not compile until somebody has written down whether it buzzes,
 * and the answer they will write is `null`, which is the safe one: the failure is *my Subroutine is
 * silent*, found the first time anyone opens it, never *my Subroutine buzzes somebody else's
 * question*.
 *
 * ### A malformed instance is silence and a console line (rule 6)
 *
 * `of` refuses parameters that are not a question — equal Sniff groups, a Handshake gap the wrong
 * side of a second — by throwing, and it throws at the *read*, which is here. A throw that reached
 * the composition would blank the screen, and a blank phone in a dark house reads as a revocation
 * (rule 5). So it is caught, the phone stays quiet, and the authority's log says which instance.
 */
internal fun SubroutineInstance.script(note: (String) -> Unit = {}): List<HapticStep>? = try {
    when (subroutine) {
        // ---- The three the house asks with the motor ------------------------------------------
        SubroutineKind.Handshake -> HandshakeRhythm.of(parameters).script
        SubroutineKind.Sniff -> SniffGroups.of(parameters).script
        SubroutineKind.Drift -> DriftPath.of(parameters).script

        // ---- The seven asked with light. Their questions are on the glass ---------------------
        SubroutineKind.Replay, SubroutineKind.Interrupt, SubroutineKind.ParityCheck,
        SubroutineKind.Deallocate, SubroutineKind.Short, SubroutineKind.SignalTrace,
        SubroutineKind.Jam,
        -> null
    }
} catch (refused: MalformedSubroutineParameters) {
    note("A SUBROUTINE ARRIVED THAT IS NOT A QUESTION — $subroutine · ${refused.detail}")
    null
}

/**
 * **Does this effect ring — and exactly one of them does.**
 *
 * D-160: *a phone call vibrates LONG–pause–LONG, repeating indefinitely until answered.* The call
 * is [Effect.MeetingOpened], and the ruling amends D-135 rather than replacing it — the incoming
 * call leaves the set of five single Longs and becomes the repeating pattern; **the other four
 * stay single Longs** and are the four `false` rows that would otherwise look like oversights.
 *
 * ### The effect still carries `Haptic.Long`, and that is not a contradiction
 *
 * `Rules.kt` puts a `Long` on `MeetingOpened` because D-135 said so, and D-160 did not go back and
 * rewrite the reduction — the kind on the effect is the house saying *this is one of the ones you
 * must not miss*, and this is the client obeying the amendment about how a phone says it. **It is
 * a decision about the effect type and nothing else.** Every phone that receives a `MeetingOpened`
 * rings; none of them consults a seat, a role, or who called the meeting.
 *
 * ### Exhaustive, in the same fail-closed direction as [haptic]
 *
 * A new `Effect` does not compile until somebody has written down whether it rings, and the
 * answer they will write is `false`, which is the safe one: the failure mode is *"my effect does
 * not ring"*, found immediately, never *"a second thing in this game vibrates forever"* — which
 * in a dark silent house is a phone nobody can explain and everybody can hear.
 */
internal fun Effect.ringing(): Boolean = when (this) {
    // The one summons in the game. A meeting has been called and the round stops until everybody
    // has it, so this is the one signal that does not fire once and give up.
    is Effect.MeetingOpened -> true

    // Everything else says its thing once — including D-135's other four Longs, which are loud
    // but finite.
    is Effect.OpeningMessage, is Effect.StandAndWalkIn, is Effect.MeetingPhaseOpened,
    is Effect.MeetingResult, is Effect.RestrainedTakeover, is Effect.MeetingEnded,
    is Effect.EgressOpened, is Effect.EgressHeld, is Effect.EgressContained,
    is Effect.EgressSucceeded, is Effect.RoundEnded, is Effect.LampSet, is Effect.AbilityFired,
    is Effect.ContactAnswered, is Effect.AbilityBlanked, is Effect.NoticeIssued,
    is Effect.SubroutineGraded, is Effect.SubroutineProgressed, is Effect.SystemIntegrityLive,
    is Effect.SystemIntegrityBatched, is Effect.MessageDelivered, is Effect.HouseSignedOff,
    is Effect.HouseClosedOnPermission,
    // The Revoke landing says its thing once, like the takeover it mirrors: a single Long on the
    // touch that Revoked this phone (D-290), and the armed phone's answer says its single Long the
    // same way. The roster is silent entirely and is one of the `null` rows in [haptic] below.
    is Effect.RevokeLanded, is Effect.RosterIssued, is Effect.PlanIssued, is Effect.ContactSeedIssued,
    is Effect.WorkOrderIssued, is Effect.ScanAnswered, is Effect.TerminalAnswered,
    is Effect.PresenceChanged,
    // NOT A MARKER says *no* once, like every refusal (D-160, D-318).
    is Effect.CardUnregistered,
    is Effect.CheckInProgressed, is Effect.ReadyProgressed, is Effect.VoteProgressed,
    is Effect.VoteHeld, is Effect.VoteSelectionShown, is Effect.MeetingResolved,
    is Effect.SyncPulseAnswered, is Effect.SyncPulseBlanked, is Effect.InsidersRevealed, is Effect.RoleRevealed,
    // **The Surge is the loudest thing in the game and it does NOT ring**, which is worth stating
    // rather than lumping in. Ringing means *until answered* (D-160), and a Surge has nothing to
    // answer: it is one moment, and its noise is the speaker's rather than the motor's. A phone
    // that kept buzzing after one would be the second thing in the game that goes off forever, on
    // the one player the whole house is already walking towards.
    is Effect.Surged,
    // A list arriving on a phone. It buzzes as much as a work order does, which is not at all.
    // Its blank is a Resident's frame of the same size, and it buzzes exactly as much (D-258).
    is Effect.IsolateTargetsOffered, is Effect.IsolateTargetsBlanked,
    // **STATIC, and it is silent on every phone in the building** (D-186, D-135, D-176). The jam
    // reaches every living seat at once, so a buzz here would be the only thing in the game that
    // rings a whole house without the house having called a meeting -- and it would ring it at the
    // exact moment an Insider chose, which is a timer telling a dark room that *now* was when
    // somebody moved. D-135 closes the long haptic to five events and this is not one of them; the
    // announcement is what the surfaces look like.
    is Effect.StaticOpened,
    // **The caller's own screen change, and it must not ring on top of the ring** (E-R1-1). The
    // ring is `MeetingOpened`'s and this phone already received it; a phone that rang twice for one
    // meeting would be the caller, audible through a pocket, in a house where five other phones are
    // ringing once.
    is Effect.MeetingCalling,
    -> false
}

/**
 * **The kind of buzz an effect carries, or null for the ones that arrive quietly.**
 *
 * The value is read off the effect and never computed: the house constructed it in the same
 * reduction that constructed the push (D-134), so there is nothing to decide here. Twelve kinds
 * carry one; the rest are counts, progress and content that update a screen already on the phone.
 * (Thirteen, since the Surge — see its branch, and D-135, which the Surge does *not* join. Fifteen,
 * since D-290: the armed phone's answer and the Revoke landing each carry a Long on a landed Revoke
 * and nothing otherwise.)
 *
 * ### D-135's five, and where they are
 *
 * The long haptic is closed to the Egress ([Effect.EgressOpened]), an incoming phone call
 * ([Effect.MeetingOpened]), STAND AND WALK IN for a newly Revoked player
 * ([Effect.StandAndWalkIn]), the Restrained takeover ([Effect.RestrainedTakeover]), and the end of
 * the LIGHTS OUT countdown ([Effect.MeetingEnded]). **Not one of them is decided here** — each of
 * those effects carries `Haptic.Long` because `Rules.kt` put it there, and `StandAndWalkIn` is the
 * proof that this is the right layer for it: the same effect is Long for a newly Revoked player
 * and Short for the couch, which is a fact about round-state that only the authority holds.
 */
internal fun Effect.haptic(): Haptic? = when (this) {
    // ---- The thirteen that buzz --------------------------------------------------------------
    // The house's answer to this phone's run of the Sync Pulse: a failed attempt vibrates for the
    // whole 3 s lockout, a good run is silent (the owner's O4, D-298). It reaches the tapper alone.
    is Effect.SyncPulseAnswered -> haptic
    is Effect.OpeningMessage -> haptic
    is Effect.MeetingOpened -> haptic
    is Effect.StandAndWalkIn -> haptic
    is Effect.MeetingPhaseOpened -> haptic
    is Effect.MeetingResult -> haptic
    is Effect.RestrainedTakeover -> haptic
    is Effect.MeetingEnded -> haptic
    is Effect.EgressOpened -> haptic
    is Effect.EgressHeld -> haptic
    is Effect.EgressContained -> haptic
    // Reaches no client at all — its row is gone from EmitSchema. It is listed anyway because
    // this function answers *what does a delivered effect do*, never *is it delivered*. The two
    // questions have one owner each and it is not the same owner.
    is Effect.EgressSucceeded -> haptic
    is Effect.RoundEnded -> haptic
    // **The Surge, and its buzz is Short by ruling rather than by default.** D-135 closes the long
    // haptic to five events and this is not one of them, however loud the rest of it is — the
    // sound is what makes a Surge carry across a house, and a sixth long buzz would spend the
    // meaning of the five that already mean something. The value is `Rules.kt`'s, like every other
    // one on this list; nothing is decided here.
    is Effect.Surged -> haptic
    // **A landed Revoke buzzes Long on both phones of the touch** (the owner's 2026-11-18 ruling,
    // D-290, the sixth Long beside D-135's five). The armed phone's answer carries it when its
    // Revoke landed and nothing otherwise: a Surge, Isolate, Static or Egress answer is input echo,
    // the phone in the hand that pressed the control, and a touch that spent the arm on nobody
    // stays quiet by the same ruling (*on a landed Revoke only*).
    is Effect.AbilityFired -> haptic
    // The Revoked phone's half: Long on the touch that put it out, and nothing on every touch after
    // (D-156, see the effect). Both values are `Rules.kt`'s; nothing is decided here.
    is Effect.RevokeLanded -> haptic
    // **NOT A MARKER: D-160's *no*, two vibrates, on the scanning phone alone** (D-318, provisional).
    // The house's, off the kind; the acquire's *yes* before it was the scan window's. Read off the
    // effect like every value here, so the same paper buzzes the same on either role's phone.
    is Effect.CardUnregistered -> haptic

    // ---- The rest arrive quietly, each for its own reason -------------------------------------
    // The lamp is a pure function of state (rule 5) and a buzz on every luminance change would be
    // a phone that vibrates through a whole round.
    is Effect.LampSet -> null
    // **A touch that changed nothing, and it must feel like nothing** (R1, D-216). Every touch in
    // the house sends one of these or an armed Insider's `AbilityFired`; a buzz on it would be a phone
    // going off in a dark room every time somebody brushed it.
    is Effect.ContactAnswered -> null
    // **Somebody else's ability, answered, and it must feel like nothing** (D-263): every living phone
    // but the actor's is sent one at that moment, and a buzz on it would tell the room an ability fired.
    is Effect.AbilityBlanked -> null
    // **The house notice arrives with the gate closing, and the gate's own push is what buzzes**
    // (D-187, D-223). A second buzz on it would be a buzz that meant *somebody evaded*, at every
    // meeting, empty or not.
    is Effect.NoticeIssued -> null
    // **The close is silent** (D-310): the owner ruled its words and nothing else, and D-135's set
    // is closed. Every phone walks out of the round in the light it already has.
    is Effect.HouseClosedOnPermission -> null
    // A verdict on work the player is standing over, on a screen they are already looking at.
    // **SILENT, and F-003 is the whole reason** (`gdd.md:930`). The reveal leaking through the
    // haptic count is the gap the design named itself: in a silent dark house, standing in a
    // group, the NUMBER of buzzes is an audible tell, and it fires at the exact moment nobody
    // has dispersed. The role arrives on a silent message behind one banner that buzzed for
    // everybody -- D-156's arrangement, and the reason a `haptic` field was never put on it.
    is Effect.RoleRevealed -> null
    // **Silent, and for the reason immediately above rather than a weaker one.** It rides the same
    // arming burst, so a buzz here would put a second vibration on every phone in the house at the
    // one moment nobody has dispersed -- and a list of names is not an event, it is the thing four
    // later screens are drawn from.
    is Effect.RosterIssued -> null
    // **Silent, for the roster's reason** (D-316): the plan rides the same arming burst.
    is Effect.PlanIssued -> null
    // **Silent, for the roster's reason** (D-212). The radio's key rides the same arming burst, and
    // a buzz here is a second vibration on every phone at the one moment nobody has dispersed.
    is Effect.ContactSeedIssued -> null
    is Effect.SubroutineGraded -> null
    is Effect.SubroutineProgressed -> null
    // **The meter never buzzes, in either form.** The live one moves whenever anybody in the house
    // banks an entry, so a buzz on it would be a pocket vibrating at the rate the Residents are
    // working -- audible, in a silent house, to everyone standing near the person outside the
    // system. The batched one arrives inside a meeting the whole party has already been rung to.
    is Effect.SystemIntegrityLive -> null
    is Effect.SystemIntegrityBatched -> null
    // D-156: whatever the house has to say arrives as ONE delivery with ONE buzz, and the buzz is
    // OpeningMessage's. The underlying message count never drives the buzz count — that is the
    // exact leak F-003 found, where an Insider's phone went off four times and a Resident's once.
    is Effect.MessageDelivered -> null
    is Effect.HouseSignedOff -> null
    // The work order lands with the opening delivery, which has already buzzed.
    is Effect.WorkOrderIssued -> null
    // The answer to a scan the player is holding the phone up to do.
    is Effect.ScanAnswered -> null
    // The station's answer, for the same reason: the phone is held up to the T card already.
    is Effect.TerminalAnswered -> null
    // The map moving. Every phone in the house would buzz every time anybody walked anywhere.
    is Effect.PresenceChanged -> null
    // Counts ticking up on a screen the player is watching tick up.
    is Effect.CheckInProgressed -> null
    is Effect.ReadyProgressed -> null
    is Effect.VoteProgressed -> null
    // Their own selection coming back, and the couch's live view of everyone's.
    is Effect.VoteHeld -> null
    is Effect.VoteSelectionShown -> null
    // The attribution, on the screen MeetingResult already buzzed for.
    is Effect.MeetingResolved -> null
    // Somebody else's Sync Pulse, answered (C2): AbilityBlanked's reason, and it must feel like nothing.
    is Effect.SyncPulseBlanked -> null
    // A target list arriving. It buzzes exactly as much as the work order it sits beside, which is
    // not at all: a phone that went off when the Egress-node exclusion changed its picker would be
    // announcing an Insider's own tooling to a pocket in a silent house.
    is Effect.IsolateTargetsOffered -> null
    // The blank beside it on a Resident's phone (D-258). A buzz here would ring every Resident's
    // pocket at the moment every Insider's stayed still: the role, through the motor.
    is Effect.IsolateTargetsBlanked -> null
    // **The jam, silent, and this is the branch where a buzz would be worst.** It lands on every
    // living phone including the one that fired it, so a haptic here is `gdd.md:398`'s forbidden
    // actor-side feedback and a house-wide beacon in the same instant -- and in a silent dark house
    // the beacon is the more expensive half.
    is Effect.StaticOpened -> null
    // The reveal, on the ending screen RoundEnded already buzzed for. One ending, one buzz.
    is Effect.InsidersRevealed -> null
    // **Silent, and the effect has no haptic field to be silent with** (E-R1-1). The ring buzzes on
    // this phone like every other; one phone buzzing differently from the rest is the caller named
    // by hardware, at the one moment the design already made public by making them walk to the card.
    is Effect.MeetingCalling -> null
}

/**
 * **Does this arrival ring** — the wire's copy of [Effect.ringing], and it must never disagree.
 *
 * One `true`, for the same reason and by the same ruling: the call is the one summons in the game
 * and it repeats until a person answers it (D-160). Everything else says its thing once.
 *
 * The list is written out by hand rather than derived, which is the discipline that makes it worth
 * having — an exhaustive `when` is what stops a new message reaching a motor nobody decided about.
 * What stops the two lists drifting is not care, it is
 * `HouseBuzzTest.theWireBuzzesWhateverTheEffectBuzzed`, which projects every effect in the game and
 * compares the answers.
 */
internal fun RoundMessage.ringing(): Boolean = when (this) {
    is RoundMessage.MeetingOpened -> true

    is RoundMessage.OpeningMessage, is RoundMessage.StandAndWalkIn,
    is RoundMessage.MeetingPhaseOpened, is RoundMessage.MeetingResult,
    is RoundMessage.RestrainedTakeover, is RoundMessage.MeetingEnded,
    is RoundMessage.EgressOpened, is RoundMessage.EgressHeld, is RoundMessage.EgressContained,
    is RoundMessage.EgressSucceeded, is RoundMessage.RoundEnded, is RoundMessage.LampSet,
    is RoundMessage.AbilityFired, is RoundMessage.AbilityBlanked, is RoundMessage.ContactAnswered,
    is RoundMessage.NoticeIssued,
    is RoundMessage.SubroutineGraded,
    is RoundMessage.SubroutineProgressed, is RoundMessage.SystemIntegrityLive,
    is RoundMessage.SystemIntegrityBatched, is RoundMessage.MessageDelivered,
    is RoundMessage.HouseSignedOff, is RoundMessage.WorkOrderIssued,
    is RoundMessage.ScanAnswered, is RoundMessage.TerminalAnswered, is RoundMessage.PresenceChanged,
    is RoundMessage.CheckInProgressed, is RoundMessage.ReadyProgressed,
    is RoundMessage.VoteProgressed, is RoundMessage.VoteHeld,
    is RoundMessage.VoteSelectionShown, is RoundMessage.MeetingResolved,
    is RoundMessage.SyncPulseAnswered, is RoundMessage.SyncPulseBlanked, is RoundMessage.InsidersRevealed,
    is RoundMessage.RoleRevealed, is RoundMessage.RevokeLanded, is RoundMessage.RosterIssued, is RoundMessage.PlanIssued,
    is RoundMessage.ContactSeedIssued,
    is RoundMessage.Surged, is RoundMessage.IsolateTargetsOffered, is RoundMessage.IsolateTargetsBlanked,
    is RoundMessage.StaticOpened, is RoundMessage.MeetingCalling, is RoundMessage.HouseClosedOnPermission,
    is RoundMessage.CardUnregistered,
    -> false
}

/**
 * **The kind of buzz an arrival carries** — the wire's copy of [Effect.haptic].
 *
 * Every value here is read off the message, and every value on the message was copied from the
 * effect by the projection, which copied it from the reduction that constructed it. **Nothing on
 * this path chooses a haptic**; the chain from `Rules.kt` to the motor is one value passed along,
 * which is what makes D-102's *identical for every player* a structural property rather than a
 * convention four layers have to keep.
 *
 * ### D-156 lives in the `null` branches, and this is the side that matters
 *
 * The reveal is one delivery with one buzz: [RoundMessage.OpeningMessage] carries the haptic and
 * every [RoundMessage.MessageDelivered] behind it is silent, however many of them there are. That
 * is F-003 — an Insider's phone went off four times and a Resident's once, in a cluster, at the one
 * moment the whole party is standing together — and it is a claim about **what a phone does with
 * what it received**, so the effect-side branch alone never closed it.
 */
internal fun RoundMessage.haptic(): Haptic? = when (this) {
    // ---- The fourteen that buzz ------------------------------------------------------------
    // The owner's O4 (D-298): the lockout's 3 s on a failed attempt, off the answer's one digit.
    is RoundMessage.SyncPulseAnswered -> haptic
    is RoundMessage.OpeningMessage -> haptic
    is RoundMessage.MeetingOpened -> haptic
    is RoundMessage.StandAndWalkIn -> haptic
    is RoundMessage.MeetingPhaseOpened -> haptic
    is RoundMessage.MeetingResult -> haptic
    is RoundMessage.RestrainedTakeover -> haptic
    is RoundMessage.MeetingEnded -> haptic
    is RoundMessage.EgressOpened -> haptic
    is RoundMessage.EgressHeld -> haptic
    is RoundMessage.EgressContained -> haptic
    is RoundMessage.EgressSucceeded -> haptic
    is RoundMessage.RoundEnded -> haptic
    is RoundMessage.Surged -> haptic
    // D-290: both phones of a landed Revoke, one digit each on the wire.
    is RoundMessage.AbilityFired -> haptic
    is RoundMessage.RevokeLanded -> haptic
    // D-318: NOT A MARKER's *no*, the effect's own and copied onto the message.
    is RoundMessage.CardUnregistered -> haptic

    // ---- The rest arrive quietly. Each reason is on the effect-side branch it mirrors ---------
    is RoundMessage.LampSet -> null
    is RoundMessage.ContactAnswered -> null
    is RoundMessage.AbilityBlanked -> null
    is RoundMessage.NoticeIssued -> null
    // F-003, on the receiving side. See the effect-side branch.
    is RoundMessage.RoleRevealed -> null
    is RoundMessage.RosterIssued -> null
    is RoundMessage.PlanIssued -> null
    is RoundMessage.ContactSeedIssued -> null
    is RoundMessage.SubroutineGraded -> null
    is RoundMessage.SubroutineProgressed -> null
    is RoundMessage.SystemIntegrityLive -> null
    is RoundMessage.SystemIntegrityBatched -> null
    // **D-156, on the receiving side.** The count of things the house had to say never becomes a
    // count of vibrations. See this function's KDoc.
    is RoundMessage.MessageDelivered -> null
    is RoundMessage.HouseSignedOff -> null
    is RoundMessage.WorkOrderIssued -> null
    is RoundMessage.ScanAnswered -> null
    is RoundMessage.TerminalAnswered -> null
    is RoundMessage.PresenceChanged -> null
    is RoundMessage.CheckInProgressed -> null
    is RoundMessage.ReadyProgressed -> null
    is RoundMessage.VoteProgressed -> null
    is RoundMessage.VoteHeld -> null
    is RoundMessage.VoteSelectionShown -> null
    is RoundMessage.MeetingResolved -> null
    is RoundMessage.SyncPulseBlanked -> null
    is RoundMessage.IsolateTargetsOffered -> null
    is RoundMessage.IsolateTargetsBlanked -> null
    is RoundMessage.StaticOpened -> null
    is RoundMessage.InsidersRevealed -> null
    is RoundMessage.MeetingCalling -> null
    // Silent, as the effect-side branch says (D-310).
    is RoundMessage.HouseClosedOnPermission -> null
}
