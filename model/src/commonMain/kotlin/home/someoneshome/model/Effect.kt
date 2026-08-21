package home.someoneshome.model

/**
 * What the rules emit. Authority-side, and deliberately **not** `@Serializable`.
 *
 * Nothing here goes on the wire as-is. Effects are redacted per client at the emit boundary,
 * by constructing a narrower client-facing type — never by nulling fields on this one. A nulled
 * field still exists, and someone makes it non-null later for an unrelated reason.
 *
 * The per-client transcript recorder and the schema allowlist that police that boundary are
 * stories 0.5 and 0.6b. Until they exist, this type simply must not acquire a wire encoding,
 * which `redactionLint` enforces: adding `@Serializable` here fails the build.
 */
sealed interface Effect {
    /**
     * Light is game state. No local animation, tween or easing the rules did not emit — a fade
     * nobody authored is a signal nobody authored.
     */
    data class LampSet(val seat: Seat, val luminance: Int) : Effect

    /**
     * Emitted whether or not the ability changed anything.
     *
     * This is the shape rule 1 demands: firing at an already-revoked target must produce the
     * same effect, with the same cooldown, as firing at a live one. An absent effect is a
     * revoke-detector.
     */
    data class AbilityFired(val actor: Seat, val cooldownStarted: Boolean) : Effect

    /**
     * **The house's answer to a returned entry — the same words on the same schedule for both
     * roles (D-109).**
     *
     * Two fields, and that is deliberately the whole of it. There is no `remaining`, no *which
     * elements were wrong*, no attempt count and no reason: a mismatch reports only that it was a
     * mismatch (`gdd.md:608`), and every extra field would be one more thing that could be
     * generated differently for a fake.
     *
     * ### This is rule 1's exact shape, and it is the reason the effect exists
     *
     * An Insider's Subroutines are fakes: real UI, real progress, real completion, **writing
     * nothing**. The tempting implementation is to say nothing back about a fake, or to roll a
     * plausible failure distribution for one. Both are role oracles — the first after one
     * completion, the second after enough of them — and D-109 refuses both: *run the real rule,
     * emit the real shape.* **The Insider's verdict is emitted, never omitted.**
     *
     * The single asymmetry lives where no player can stand: an Insider's [accepted] verdict does
     * not move `SystemIntegrity`. Nothing here carries that, and nothing on any screen can read it.
     */
    data class SubroutineGraded(val seat: Seat, val accepted: Boolean) : Effect

    data class SubroutineProgressed(val remaining: Int) : Effect
    data class MessageDelivered(val seat: Seat, val body: String) : Effect
    data class MeetingOpened(val caller: Seat) : Effect
    data class MeetingResolved(val restrained: Seat?, val attribution: List<Pair<Seat, Seat?>>) : Effect
}
