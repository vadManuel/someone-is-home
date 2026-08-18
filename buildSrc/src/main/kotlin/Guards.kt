import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Source-scanning guards.
 *
 * These fail the build rather than warn. A warning in a project whose bugs are silent leaks is
 * a rule nobody is enforcing — and the failure mode this codebase cannot afford is the one that
 * nobody notices.
 */

/**
 * Vocabulary lint (project-context rule 9).
 *
 * A denylist of the specific wrong words, not an allowlist of every identifier: the rule says
 * the game's vocabulary is closed, but ordinary English in ordinary code is not the target.
 * What is mechanically checkable — and what actually drifts — is the synonym that slips in from
 * habit or from a genre neighbour.
 */
abstract class VocabularyLintTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val extraDenied: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (file in sources.asFileTree.matching { include("**/*.kt") }) {
            val raw = file.readText()

            // Vocabulary rules apply to CODE, not to prose. A KDoc that names the wrong words in
            // order to forbid them is documentation doing its job, and the first version of this
            // lint failed on its own rule-explaining comment. Comments are blanked rather than
            // removed so line numbers still point at the right place.
            val code = raw
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                    m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
                }
                .lines().joinToString("\n") { it.substringBefore("//") }

            code.lines().forEachIndexed { index, line ->
                for ((pattern, reason) in DENIED) {
                    val match = pattern.find(line) ?: continue
                    violations += "${file.path}:${index + 1}  '${match.value}' — $reason"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Vocabulary lint failed. ${violations.size} violation(s):")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("The vocabulary is closed: Resident, Insider, Revoke, Restrain,")
                    appendLine("Subroutine, SystemIntegrity, Egress, Override.")
                    appendLine("Revoke and Restrain are NOT synonyms and must never be collapsed —")
                    appendLine("one is system power lent by the house, the other is a physical act")
                    appendLine("the house cannot prevent.")
                }
            )
        }
    }

    private companion object {
        /** `\b` on both sides so `Restrained` is fine but `evict` inside `evicted` is caught. */
        fun word(w: String) = Regex("""\b$w\w*\b""", RegexOption.IGNORE_CASE)

        /**
         * **Deliberately does NOT duplicate the pre-commit hook.**
         *
         * That hook guards a different risk — distinctive, protectable expression in a public
         * repo, the thing an IP complaint would cite. It owns those words, it runs on staged
         * files at commit time, and duplicating its patterns here only meant this lint's own
         * source and tests tripped it.
         *
         * This list is the *project's* vocabulary discipline instead: the words that have
         * actually drifted in this codebase. Death framing is here because it drifted about
         * forty times across the spec and needed a sweep (D-061) — the pillar is that access is
         * revoked and nobody dies.
         */
        val DENIED: List<Pair<Regex, String>> = listOf(
            word("traitor") to "role word — use Insider",
            word("sabotage") to "use Egress",
            word("evict") to "use Restrain",
            Regex("""\btask(s|ed|ing)?\b""", RegexOption.IGNORE_CASE) to "use Subroutine",

            // The pillar: access is revoked, nobody dies.
            word("murder") to "use Revoke",
            word("corpse") to "nobody dies — there is no body",
            word("victim") to "use target, or revoked player",
        )

    }
}

/**
 * Redaction marker lint (project-context rule 3).
 *
 * *"Client-facing types carry a marker, and only marked types may be wire-`@Serializable`.
 * Unmarked-but-serializable is a lint failure."*
 *
 * This is the guard that makes redaction fail closed. A new type that goes on the wire without
 * anyone having decided it is safe to send is the exact shape of a leak that ships, works, and
 * is noticed by nobody.
 */
abstract class RedactionLintTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (file in sources.asFileTree.matching { include("**/*.kt") }) {
            val text = file.readText()
            // Annotation blocks attached to a declaration, so `@Serializable` on the line above
            // the class still counts as attached to it.
            val declaration = Regex(
                """((?:@\w+(?:\([^)]*\))?\s+)+)(?:public\s+|internal\s+)?(?:data\s+|value\s+|sealed\s+)*(?:class|object|interface)\s+(\w+)"""
            )
            for (m in declaration.findAll(text)) {
                val annotations = m.groupValues[1]
                val name = m.groupValues[2]
                if (!annotations.contains("@Serializable")) continue
                if (annotations.contains("@ClientFacing")) continue
                val line = text.take(m.range.first).count { it == '\n' } + 1
                violations += "${file.path}:$line  $name is @Serializable but not @ClientFacing"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Redaction lint failed. ${violations.size} violation(s):")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Only @ClientFacing types may go on the wire. If this type really is")
                    appendLine("safe to send, mark it and say why in the KDoc. If it carries ground")
                    appendLine("truth, it must NOT be @Serializable — construct a narrower view type")
                    appendLine("instead. Never redact by nulling fields: a nulled field still exists,")
                    appendLine("and someone makes it non-null later for an unrelated reason.")
                }
            )
        }
    }
}
