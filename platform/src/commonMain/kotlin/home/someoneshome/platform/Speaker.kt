package home.someoneshome.platform

import home.someoneshome.model.Sound

/**
 * **The speaker, and it has exactly one thing to say** (`gdd.md:313`, the owner's 2026-08-22
 * ruling).
 *
 * [Haptics]' shape, one sense over, and every argument that file makes for the motor is sharper
 * here. **There is no volume parameter, no duration and no file name**: [play] takes a [Sound] and
 * nothing else, so a call site cannot ask for a noise of its own loudness or its own length. A phone
 * that was a little louder than the others would be telling a dark room something nobody wrote down,
 * and unlike a buzz there would be no need to be holding it to hear.
 *
 * ### Why an interface with one member and one caller exists at all
 *
 * Because the alternative is the surge screen reaching for an audio API directly, and the moment two
 * screens can do that the rule *nothing that fires for a subset ever makes a sound*
 * (`gdd.md:399`–`:400`) is a convention rather than a shape. There is one door, it is this, and what
 * goes through it is a member of a closed enum with one entry.
 *
 * ### ⚠️ THIS IS A STUB AND IT IS FLAGGED FOR THE DEVICE PASS
 *
 * **No actual plays anything.** What the Surge sounds like is not a decision to take in a source
 * file: it is the one deliberate noise in a game played in silence, it has to carry across a house
 * through a pocket and a closed door, and it has to be unmistakable at the moment everybody in the
 * building starts moving toward it. That is a decision to take with two phones in a dark room, and
 * it needs a build first.
 *
 * **The three questions the device pass owes an answer to**, recorded here so they are asked rather
 * than defaulted:
 *
 * 1. **Which route.** A system sound, a bundled asset through `AVAudioPlayer`, or a synthesised
 *    tone. They differ in latency, and the design's *slams to full, then to black* is a moment the
 *    light and the sound have to land in together.
 * 2. **Whether it plays through the silent switch.** A phone on silent in a pocket is the ordinary
 *    state of every phone at a party, and a Surge that a silent switch could cancel is an ability
 *    that works on some players and not others — which is a channel nobody designed and nobody in
 *    the room could see.
 * 3. **What it does to the audio session.** The app owns no audio otherwise, and grabbing a session
 *    for one noise may duck or stop whatever the host is playing through the house speakers.
 *
 * Until then this is silent, which is the fail-closed direction and is loud where it should be:
 * `noSpeaker()` reports to the log and to nobody else, exactly as a phone with no motor does
 * (rule 6 — errors are silent to the player, loud to the authority).
 */
interface Speaker {

    /**
     * The house makes this phone emit a sound out loud.
     *
     * The one call in the game, from the one effect that carries a [Sound]. See the type, which
     * holds the licence: Surge is audible because it is **self-inflictable**, not because sound is
     * available.
     */
    fun play(sound: Sound)
}

/**
 * This device's speaker.
 *
 * ⚠️ **Every actual is [SilentSpeaker] today.** See [Speaker]'s KDoc for the three questions the
 * device pass owes before one of them stops being.
 */
expect fun deviceSpeaker(): Speaker

/**
 * **A speaker that makes no sound and says so where only a developer reads.**
 *
 * The honest shape of *not built yet*: the call goes through, the effect is delivered, the schema
 * row is exercised, and nothing comes out. A player sees no dialog and no screen state, because rule
 * 6 forbids one — an error surfacing as a Insider fires an ability is an alignment tell delivered by
 * the crash handler, and this ability is fired by an Insider every time.
 *
 * [played] is what a test reads: *the house asked for the sound* is checkable without a speaker, and
 * it is the whole of what this layer can be checked for until there is one.
 */
class SilentSpeaker(private val note: (String) -> Unit = {}) : Speaker {

    /** Every sound this phone was asked for, in order. Test-facing; nothing in play reads it. */
    val played: MutableList<Sound> = mutableListOf()

    override fun play(sound: Sound) {
        played += sound
        note("SPEAKER — $sound REQUESTED, NOT BUILT (see Speaker's KDoc)")
    }
}
