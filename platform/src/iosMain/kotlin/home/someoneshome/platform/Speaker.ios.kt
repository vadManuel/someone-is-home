package home.someoneshome.platform

import platform.Foundation.NSLog

/**
 * ⚠️ **The stub, and it is the whole iOS implementation.** See [Speaker], which holds the three
 * questions the device pass owes before this becomes a real one — which audio route, whether it
 * plays through the silent switch, and what it does to the audio session.
 *
 * It is [SilentSpeaker] rather than a half-built `AVAudioPlayer`, deliberately: a sound that played
 * *sometimes* — through the speaker but not on silent, or with a quarter-second of latency behind
 * the lamp — would be worse than one that never plays, because it would look built. A Surge some
 * players can hear and others cannot is a channel nobody designed.
 *
 * The log line is [Haptics]' arrangement exactly: loud to the authority, silent to the player.
 */
actual fun deviceSpeaker(): Speaker = SilentSpeaker { NSLog("%@", it) }
