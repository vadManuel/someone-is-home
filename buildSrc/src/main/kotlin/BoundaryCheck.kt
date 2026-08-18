import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Module boundary enforcement.
 *
 * From `project-context.md`: *"Gradle already enforces: `core` sees no coroutines, no datetime,
 * no platform. `ui` sees no `core`. If an import fails, that is the architecture, not a
 * misconfiguration."*
 *
 * Declaring the right dependencies is not enough, because the dangerous case is TRANSITIVE — a
 * module picking up coroutines through something it legitimately depends on. So this inspects
 * the resolved compile classpath rather than the declared dependency list.
 *
 * **It fails when it finds nothing to inspect.** A boundary check that silently checks an empty
 * classpath reports success forever, and everyone goes on believing the boundary is enforced.
 * That is the same failure shape as a redaction schema that default-permits.
 */
abstract class BoundaryCheckTask : DefaultTask() {

    /** Resolved compile artifacts for the module under inspection. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val classpath: ConfigurableFileCollection

    /**
     * Substrings that must not appear in any resolved artifact name. For external libraries
     * only — see [forbiddenProjects] for sibling modules.
     */
    @get:Input
    abstract val forbidden: ListProperty<String>

    /**
     * Sibling module paths that must not be reachable, e.g. `:core`.
     *
     * Matched against resolved component identity, NOT against artifact file names. The first
     * version matched names and forbidding `core` in `:ui` immediately tripped on
     * `animation-core` and `kotlinx-coroutines-core` — a guard that cries wolf gets switched
     * off, which is worse than not having it.
     */
    @get:Input
    abstract val forbiddenProjects: ListProperty<String>

    /** Resolved component identities, supplied lazily by the module's build script. */
    @get:Input
    abstract val componentIds: ListProperty<String>

    @get:Input
    abstract val moduleName: Property<String>

    @TaskAction
    fun check() {
        val artifacts = classpath.files.map { it.name }.distinct().sorted()

        if (artifacts.isEmpty()) {
            throw GradleException(
                "Boundary check for '${moduleName.get()}' resolved an EMPTY classpath.\n" +
                    "This is a failure, not a pass. A check that inspects nothing reports success\n" +
                    "forever while the boundary it is supposed to enforce quietly stops existing.\n" +
                    "The configuration name this task reads has probably changed — fix the wiring."
            )
        }

        val violations = buildList {
            for (artifact in artifacts) {
                for (bad in forbidden.get()) {
                    if (artifact.contains(bad, ignoreCase = true)) add(artifact to bad)
                }
            }
            // Project rules match identity: "project ':core'" is the module, "animation-core" is
            // not, and only one of them is an architecture violation.
            //
            // Gradle renders these as `project ':core'` WITH quotes. The first version compared
            // against `project :core` without them, so it matched nothing and reported ui clean
            // while ui depended on core — the precise failure this task's own doc warns about.
            // Normalise rather than string-build, so the format can drift without going quiet.
            for (id in componentIds.get()) {
                val path = id.removePrefix("project").trim().trim('\'', '"')
                for (bad in forbiddenProjects.get()) {
                    if (path == bad.trim()) add(id to bad)
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module boundary violated in '${moduleName.get()}'.")
                    violations.forEach { (artifact, bad) ->
                        appendLine("  $artifact  (matched forbidden '$bad')")
                    }
                    appendLine()
                    appendLine("This is the architecture, not a misconfiguration. `core` is a pure")
                    appendLine("function of state: no coroutines, no datetime, no platform types.")
                    appendLine("That constraint is what makes a round deterministically replayable")
                    appendLine("and headless-testable in milliseconds — eight phones in a dark house")
                    appendLine("cannot be debugged any other way.")
                    appendLine()
                    appendLine("Inspected ${artifacts.size} artifact(s).")
                }
            )
        }

        // POSITIVE CONTROL.
        //
        // Non-empty is not enough. If Gradle changes how it renders a component identity — today
        // it is exactly `project ':core'`, quotes included — the normaliser above silently stops
        // matching, the list stays full, and the check reports clean forever. That is not
        // hypothetical: comparing against `project :core` WITHOUT the quotes is the bug that
        // already shipped here once and passed green while ui depended on core.
        //
        // A module always appears in its own resolution, so there is a free known-present
        // identity to test the normaliser against. If it cannot recognise that one, it cannot be
        // trusted to recognise a forbidden one.
        if (forbiddenProjects.get().isNotEmpty()) {
            val self = ":" + moduleName.get()
            val normalised = componentIds.get().map { it.removePrefix("project").trim().trim('\'', '"') }
            if (normalised.none { it == self }) {
                throw GradleException(
                    "Boundary check for '${moduleName.get()}' cannot recognise its OWN identity " +
                        "($self) among ${componentIds.get().size} resolved component(s).\n" +
                        "The identity format has changed and the normaliser no longer matches, so " +
                        "every project rule here is silently passing. Failing instead.\n" +
                        "Saw: " + componentIds.get().take(5).joinToString()
                )
            }
        }

        // The rule counts are logged deliberately. An earlier version printed "0 rules" while
        // silently checking nothing, and that line was the only visible symptom.
        logger.lifecycle(
            "boundary: ${moduleName.get()} clean — ${artifacts.size} artifacts vs " +
                "${forbidden.get().size} library rule(s), ${componentIds.get().size} components " +
                "vs ${forbiddenProjects.get().size} project rule(s)"
        )
    }
}
