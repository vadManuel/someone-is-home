package home.someoneshome.model

import home.someoneshome.model.protocol.RoundMessage
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **GUARD (D-320): the game makes no sound, and nothing is left that could ask for one** (the
 * owner's 2026-11-21 ruling: *we can't control sound on the phone*, reversing D-176).
 *
 * The Surge was the one effect that carried a sound, a sound type with one member, and a speaker
 * seam in `platform` whose every actual was a stub nothing called. All three are gone. On the
 * JVM because common Kotlin has no reflection and no files: the Surge's effect and message hold
 * their fields and no sound, the type cannot be loaded, the wire's Surge names none, and no Kotlin or
 * Swift source in the repository names the speaker, its stub or the type.
 */
class NoSoundTest {

    private fun fieldsOf(type: Class<*>): Set<String> = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .map { it.name }
        .toSet()

    @Test
    fun `the Surge carries light and a buzz and no sound`() {
        assertEquals(setOf("seat", "peak", "black", "haptic"), fieldsOf(Effect.Surged::class.java))
        assertEquals(setOf("peak", "black", "haptic"), fieldsOf(RoundMessage.Surged::class.java))
        val frame = Json.encodeToString(RoundMessage.serializer(), RoundMessage.Surged(peak = 100, black = 0, haptic = Haptic.Short))
        assertFalse("sound" in frame.lowercase(), "the Surge's frame still names a sound: $frame")
    }

    @Test
    fun `the sound type is gone`() {
        assertFailsWith<ClassNotFoundException> { Class.forName("home.someoneshome.model." + "Sound") }
    }

    /**
     * **Nothing in the repository calls the speaker.** Every `.kt` and `.swift` under a module's
     * `src`, by word, outside `build`. The words are assembled here so this file does not match itself.
     */
    @Test
    fun `no source names the speaker or the sound type`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val speaker = "Spea" + "ker"
        val words = listOf("device$speaker", "Silent$speaker", speaker, "model." + "Sou" + "nd", "Sou" + "nd.Surge")
        val sources = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" && it.name != "node_modules" && it.name != "worktrees" }
            .filter { it.isFile && ((it.extension == "kt" && "/src/" in it.invariantSeparatorsPath) || it.extension == "swift") }
            .toList()
        assertTrue(sources.size > 100, "the walk found ${sources.size} sources, so it looked in the wrong place: $root")
        val named = sources.flatMap { file ->
            val text = file.readText()
            words.filter { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }.map { "${file.relativeTo(root)}: $it" }
        }
        assertEquals(emptyList(), named, "a source still names the speaker or the sound type")
    }
}
