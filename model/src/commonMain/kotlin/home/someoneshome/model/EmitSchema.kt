package home.someoneshome.model

import kotlin.jvm.JvmInline

/**
 * The stable name of a message shape on the wire.
 *
 * A String rather than an enum, because the allowlist has to be able to *not contain* a kind. An
 * enum-keyed map with an exhaustive `when` cannot express "this message has no entry", which is
 * precisely the state that must ship to nobody.
 */
@JvmInline
value class MessageKind(val name: String) {
    override fun toString(): String = name
}

/**
 * One delivery the emit boundary permitted: this seat, in this class, may receive this kind.
 *
 * Never carries the payload. The payload is whatever the transport renders for that kind, and the
 * rule below is what keeps that safe.
 */
data class Delivery(val seat: Seat, val clientClass: ClientClass, val kind: MessageKind)

/**
 * **Story 0.6b — the per-message schema allowlist at the emit boundary.**
 *
 * An explicit statement of what each client class may receive, keyed on [MessageKind], answering
 * *"was this ever permitted"* rather than *"did this differ"*. The differential harness of story
 * 0.6 is structurally blind to a leak that reaches everyone symmetrically — both its runs come
 * out of the same redaction code — so this table is independently required, not a second opinion.
 *
 * ### It fails closed, and that is the whole design
 *
 * [permits] reads `ALLOWED[kind]` and treats a missing entry as the empty set. A new [Effect]
 * whose kind was never added here therefore **ships to nobody**. The failure mode is *"my thing
 * didn't appear"*, noticed in thirty seconds; never *"my thing appeared to everyone"*, noticed
 * never. Nothing in this file may ever acquire a default branch, an `orEmpty()`-style permit, or
 * a wildcard class.
 *
 * ### One kind, one payload, one permission set
 *
 * A kind is permitted to a class in full or not at all — there is no per-class narrowing of a
 * single kind, because that would be redaction by nulling fields under another name. **If two
 * classes need different content, that is two kinds**, each with its own row here and its own
 * narrower client-facing type. That is what makes an entry in this table a decision somebody made
 * rather than a shape somebody serialised.
 *
 * ### Keyed on the class, which has two axes
 *
 * [ClientClass] is role *and* round-state. Two rows below would be wrong keyed on role alone —
 * [Effect.SubroutineProgressed] and [Effect.MeetingResolved] both go to players who are out and to
 * no living player of either role. Keyed on role, the entry that serves an out Resident serves a
 * living Resident too.
 */
object EmitSchema {

    /**
     * The name of every effect shape. Exhaustive over the sealed type **on purpose**: a new
     * [Effect] does not compile until someone names it.
     *
     * That compile error is not the fail-closed guard — it is the prompt. The guard is that naming
     * a kind here grants it nothing, and the person mechanically fixing compile errors after
     * adding an effect will not be stopped from forgetting [ALLOWED].
     */
    fun kindOf(effect: Effect): MessageKind = when (effect) {
        is Effect.LampSet -> LAMP_SET
        is Effect.AbilityFired -> ABILITY_FIRED
        is Effect.SubroutineGraded -> SUBROUTINE_GRADED
        is Effect.SubroutineProgressed -> SUBROUTINE_PROGRESSED
        is Effect.MessageDelivered -> MESSAGE_DELIVERED
        is Effect.MeetingOpened -> MEETING_OPENED
        is Effect.MeetingResolved -> MEETING_RESOLVED
    }

    val LAMP_SET = MessageKind("LampSet")
    val ABILITY_FIRED = MessageKind("AbilityFired")
    val SUBROUTINE_GRADED = MessageKind("SubroutineGraded")
    val SUBROUTINE_PROGRESSED = MessageKind("SubroutineProgressed")
    val MESSAGE_DELIVERED = MessageKind("MessageDelivered")
    val MEETING_OPENED = MessageKind("MeetingOpened")
    val MEETING_RESOLVED = MessageKind("MeetingResolved")

    private val LIVING = setOf(
        ClientClass(Role.Resident, RoundState.Live),
        ClientClass(Role.Insider, RoundState.Live),
    )

    /** Revoked or restrained: in the building, outside the system. */
    private val OUTSIDE = setOf(
        ClientClass(Role.Resident, RoundState.Out),
        ClientClass(Role.Insider, RoundState.Out),
    )

    private val AFTER = setOf(
        ClientClass(Role.Resident, RoundState.Ended),
        ClientClass(Role.Insider, RoundState.Ended),
    )

    /**
     * **The list.** Every row is a decision with a citation; a row with no reason is a row that
     * should not be here.
     *
     * The pre-arm classes appear in no row at all. D-067: nothing in-game runs in the lobby, and
     * D-066 refuses pre-arm events above the rules — so a pre-arm client receiving anything is
     * already a bug by the time this table is consulted, and this table declines it a second time.
     */
    private val ALLOWED: Map<MessageKind, Set<ClientClass>> = mapOf(

        // The lamp is a pure function of state and the device never abandons a player: a revoked
        // player's screen is authored (D-077), and so is the disarmed perimeter after the round.
        LAMP_SET to (LIVING + OUTSIDE + AFTER),

        // Only an Insider has an ability that fires. Rule 1 makes the effect identical whether the
        // revoke landed or not, which is exactly why it must not be widened: shipped to a
        // Resident it would be an unexplained event on a screen with no ability behind it.
        ABILITY_FIRED to setOf(ClientClass(Role.Insider, RoundState.Live)),

        // D-109: the house grades every entry for real, for both roles, in identical words. So
        // this row names BOTH living classes and it is the row that must never be narrowed to one
        // of them -- an Insider whose fake never came back would be a role oracle after a single
        // Subroutine, delivered by the allowlist rather than by a screen. Rule 1's exact shape.
        //
        // Not the out classes. A player outside the system has no Subroutine open and never
        // returns an entry, so a verdict could only reach them addressed to a seat that is out --
        // which happens when somebody is revoked between their scan and their entry. The verdict
        // is then denied, and that denial carries no role information: it is the round-state axis
        // moving, and round-state is publicly observable (D-068). Their screen is D-134's, not a
        // Subroutine's.
        SUBROUTINE_GRADED to LIVING,

        // The live true remaining count. gdd.md:192 — the number is shown to living players ONLY
        // AT MEETINGS, batched and then frozen (gdd.md:1002). Continuous decrements are a rate
        // signal nobody living is entitled to. A player outside the system sees the real bars live
        // (gdd.md:1014), and that privilege is what being out is made of.
        //
        // The meeting-time batched number is a different message with a different shape and does
        // not exist yet, so living players currently receive no progress at all. That is the
        // fail-closed direction and the correct one to be wrong in.
        SUBROUTINE_PROGRESSED to OUTSIDE,

        // A banner buzzes and dims every panel in the house, and a dimming lamp is world-
        // observable, so a notification addressed to fewer than everyone is a beacon (D-076).
        MESSAGE_DELIVERED to (LIVING + OUTSIDE),

        // A meeting is called out loud. Everyone still in the round knows; the out watch it.
        MEETING_OPENED to (LIVING + OUTSIDE),

        // D-075: the vote does not publish attribution. The living see counts; only a player
        // outside the system sees who cast what, and sees it live. This carries the attribution
        // list, so it goes to the out and to nobody else. The counts the living see are a
        // different message and are not built.
        MEETING_RESOLVED to OUTSIDE,
    )

    /**
     * May this class receive this kind?
     *
     * The `?: emptySet()` is the fail-closed hinge of the whole story. It is not defensive
     * boilerplate and must never be softened into a permit.
     */
    fun permits(kind: MessageKind, clientClass: ClientClass): Boolean =
        (ALLOWED[kind] ?: emptySet()).contains(clientClass)

    /** Every class permitted this kind, ordered. Empty for a kind with no entry. */
    fun classesFor(kind: MessageKind): List<ClientClass> =
        ClientClass.ALL.filter { permits(kind, it) }

    /** Every kind that has an entry, ordered by name. For reporting, never for permitting. */
    fun knownKinds(): List<MessageKind> = ALLOWED.keys.sortedBy { it.name }

    /**
     * Which seats an effect is *addressed* to, before the allowlist is consulted.
     *
     * **A separate concern from the allowlist, and neither subsumes the other.** Addressing stops
     * seat 5 from learning seat 3's lamp — a leak the allowlist cannot see, because both are
     * living Residents in the same class. The allowlist stops a kind reaching a class that may
     * never have it — a leak addressing cannot see, because the seat really was a recipient.
     *
     * Filtered to seated players, so a malformed [Seat] addresses nobody rather than being
     * silently accepted as a recipient.
     *
     * **Known tension, recorded not resolved:** [Effect.MessageDelivered] carries a seat, so it is
     * addressed to one player, while D-076 requires every notification to reach everyone. The type
     * and the decision disagree. Addressing follows the type here rather than quietly overriding
     * it; closing the gap means changing the effect, which is loop work.
     */
    fun audienceOf(effect: Effect, state: GameState): List<Seat> {
        val addressed = when (effect) {
            is Effect.LampSet -> listOf(effect.seat)
            is Effect.AbilityFired -> listOf(effect.actor)
            // The one seat that handed the entry over, and nobody else. Addressed rather than
            // broadcast because a verdict reaching the house at large would publish who is
            // completing work and how often -- which is the whole read the meter is quantised
            // into a percentage to prevent (D-103), arriving per player instead of in aggregate.
            is Effect.SubroutineGraded -> listOf(effect.seat)
            is Effect.MessageDelivered -> listOf(effect.seat)
            is Effect.SubroutineProgressed -> state.seats
            is Effect.MeetingOpened -> state.seats
            is Effect.MeetingResolved -> state.seats
        }
        return state.seats.filter { seated -> addressed.any { it.index == seated.index } }
    }

    /**
     * The emit boundary. Addressed AND permitted, in seat order.
     *
     * Everything downstream of this function — the per-client transcript recorder, and one day the
     * socket — takes its recipients from here and nowhere else.
     */
    fun deliveries(effect: Effect, state: GameState): List<Delivery> {
        val kind = kindOf(effect)
        return audienceOf(effect, state).mapNotNull { seat ->
            val clientClass = state.clientClassOf(seat)
            if (permits(kind, clientClass)) Delivery(seat, clientClass, kind) else null
        }
    }
}
