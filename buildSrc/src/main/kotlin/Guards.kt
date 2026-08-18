import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Source-scanning guards. The matching logic lives in Rules.kt and is unit-tested; these classes
 * are the Gradle plumbing around it.
 *
 * They fail the build rather than warn. In a codebase whose bugs are silent leaks, a warning is
 * a rule nobody is enforcing.
 *
 * **Both refuse to pass on an empty source set.** A lint that scans zero files reports success
 * forever, so renaming or relocating `src` would quietly delete the rule with no visible
 * symptom. BoundaryCheckTask already guarded this case; its two siblings did not, which is
 * exactly how one guard ends up protecting less than the others without anyone noticing.
 */
abstract class SourceGuardTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    protected fun scan(name: String, rule: (String) -> List<Violation>, advice: String) {
        val files = sources.asFileTree.matching { include("**/*.kt") }.files
        if (files.isEmpty()) {
            throw GradleException(
                "$name scanned ZERO files. Failing rather than passing: a lint with nothing to " +
                    "read reports success forever, and the source layout has probably moved."
            )
        }

        val violations = files.sortedBy { it.path }.flatMap { file ->
            rule(file.readText()).map { "${file.path}:${it.line}  '${it.text}' — ${it.reason}" }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("$name failed. ${violations.size} violation(s):")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    append(advice)
                }
            )
        }
        logger.lifecycle("$name clean — ${files.size} file(s) scanned")
    }
}

/** Vocabulary lint (project-context rule 9). */
abstract class VocabularyLintTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "vocabularyLint",
        rule = VocabularyRules::violations,
        advice = buildString {
            appendLine("The vocabulary is closed: Resident, Insider, Revoke, Restrain,")
            appendLine("Subroutine, SystemIntegrity, Egress, Override.")
            appendLine("Revoke and Restrain are NOT synonyms and must never be collapsed —")
            appendLine("one is system power lent by the house, the other is a physical act")
            appendLine("the house cannot prevent.")
        },
    )
}

/**
 * Redaction marker lint (project-context rule 3).
 *
 * *"Client-facing types carry a marker, and only marked types may be wire-`@Serializable`.
 * Unmarked-but-serializable is a lint failure."*
 *
 * The guard that makes redaction fail closed. A type that goes on the wire without anyone having
 * decided it is safe to send is the exact shape of a leak that ships, works, and is noticed by
 * nobody.
 */
abstract class RedactionLintTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "redactionLint",
        rule = RedactionRules::violations,
        advice = buildString {
            appendLine("Only @ClientFacing types may go on the wire. If this type really is")
            appendLine("safe to send, mark it and say why in the KDoc. If it carries ground")
            appendLine("truth, it must NOT be @Serializable — construct a narrower view type")
            appendLine("instead. Never redact by nulling fields: a nulled field still exists,")
            appendLine("and someone makes it non-null later for an unrelated reason.")
        },
    )
}
