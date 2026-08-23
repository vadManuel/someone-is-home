package home.someoneshome.model

/**
 * **A noise a phone makes out loud — and there is exactly one of them** (`gdd.md:313`, the owner's
 * 2026-08-22 ruling).
 *
 * *Surge: target's lamp slams to full, then to black — **and** their phone emits sound. One event.*
 * The ruling is that this sound is **real**, and that it is **the one phone-emitted sound in the
 * game**, superseding the design's earlier reservation of that slot for the meeting ring
 * (`gdd.md:400`).
 *
 * ### One member, and the set is closed for [Haptic]'s reason
 *
 * D-135 closes the long haptic to five events because *in a silent house a long buzz is
 * world-observable through a pocket, and a signal that means five specific things is still a
 * signal*. Sound is that argument at full volume: it is the loudest channel in a game whose central
 * promise is that nothing ever confirms anyone's alignment, and gdd.md:399 is explicit that **a cue
 * firing for a subset leaks role through a channel every person in the house can hear**.
 *
 * Surge survives that rule the way the ring did — not by firing for everyone, but by being
 * **self-inflictable** (`gdd.md:391`, `:393`). Insiders can Surge themselves, at the cost of the
 * pool's longest cooldown, so being audibly attacked is not evidence of innocence. **That is the
 * entire licence, and it does not extend to a second member.** Adding one is a decision and not a
 * parameter: every `when` over this type is exhaustive, so a second sound is a compile error until
 * somebody writes down which subset hears it and why that is not a role oracle.
 *
 * ### There is no duration, no volume and no file name here
 *
 * The same shape [Haptic] has, and for the same reason: a call site that could ask for a sound of
 * its own length or its own loudness is a call site that can make one phone louder than another
 * without either of their effects differing. **What the Surge actually sounds like is the platform's
 * and it is a stub** — see `platform`'s `Speaker`, flagged for the device pass, because picking a
 * waveform is a decision to take with a phone in a dark room rather than in a source file.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class Sound {
    /** The Surge. The only one, and see this type's KDoc before adding a second. */
    Surge,
}
