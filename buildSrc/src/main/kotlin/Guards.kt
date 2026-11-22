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

/**
 * Arming cordon (E-PAY-1).
 *
 * *"A round is started by a person's thumb and by nothing else."* Scans a module's **production**
 * source set for a launch-environment read and fails the build on one. The benches keep their
 * switches; what this protects is the source set a release build compiles.
 */
abstract class ArmingCordonTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "armingCordon",
        rule = ArmingCordonRules::violations,
        advice = buildString {
            appendLine("Nothing that ships may read `launchSwitch` or `launchValue`. A round that")
            appendLine("needs a launch environment is a round nobody can start from the")
            appendLine("springboard — and it fails silently, which is how an evening was lost:")
            appendLine("the host relaunched the app by hand, the switch went with the process,")
            appendLine("and LIGHTS OUT reached a build that could not arm.")
            appendLine()
            appendLine("If a bench needs the switch, the bench belongs in src/cheats/, which a")
            appendLine("release build does not compile.")
        },
    )
}

/**
 * Bench-seed cordon (R4, D-240).
 *
 * *"A phone a person plays gets a secure seed."* Scans `app`'s cheat source set, where the app root
 * answers LIGHTS OUT, and fails on any read of the round seed's launch switch outside the one gate.
 */
abstract class BenchSeedCordonTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "benchSeedCordon",
        rule = BenchSeedRules::violations,
        advice = buildString {
            appendLine("The round seed's switch is read by RoundCheat.benchSeed and by nothing else,")
            appendLine("and only on a bench launch (ROUND_SWITCH or BY_HAND_SWITCH). round.sh sets the")
            appendLine("seed by default and its README says which seat it makes the Insider, so a read")
            appendLine("anywhere else is a published answer key on a phone somebody is playing.")
            appendLine()
            appendLine("Ask RoundCheat.seed() or RoundCheat.benchSeed() instead.")
        },
    )
}

/**
 * Seed-source cordon (D-215).
 *
 * *"A round's seed comes from the platform's secure source."* Scans every source set of `app` and
 * `platform` that compiles into a phone, cheats included, and fails on any use of `kotlin.random`.
 */
abstract class SeedSourceCordonTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "seedSourceCordon",
        rule = SeedSourceRules::violations,
        advice = buildString {
            appendLine("Every secret in a round is an HMAC keyed by the round's seed (D-214). A")
            appendLine("seed from kotlin.random is a seed a phone can guess, and a guessed seed")
            appendLine("redraws the Insiders and every seat's radio key.")
            appendLine()
            appendLine("Draw seeds with drawRoundSeed() and seat tokens with mintSeatToken(); both")
            appendLine("read platform's secureRandomBytes (SecRandomCopyBytes on iOS).")
        },
    )
}

/**
 * Notification cordon (D-326).
 *
 * *"Remove the notification permission. We don't use it."* Scans every source set that compiles
 * into a phone, cheats included, and fails on the UserNotifications framework or its permission
 * calls.
 */
abstract class NotificationCordonTask : SourceGuardTask() {
    @TaskAction
    fun check() = scan(
        name = "notificationCordon",
        rule = NotificationCordonRules::violations,
        advice = buildString {
            appendLine("The app posts no iOS notification and has no background mode; every")
            appendLine("notification in the game is drawn in-app (D-326). The NOTIFICATIONS row and")
            appendLine("its prompt were removed on the owner's ruling. If a real need for an OS")
            appendLine("notification arises, raise it with the owner before asking iOS for one.")
        },
    )
}
