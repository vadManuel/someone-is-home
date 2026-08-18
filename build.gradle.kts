plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// The guards below fail the build rather than warn. In a codebase whose bugs are silent leaks,
// a warning is a rule nobody is enforcing.
// A rename silently deletes a guard: `if (name == "model")` matching nothing is
// indistinguishable from passing, and CLAUDE.md already flags a pending rename at repo init.
// Assert the named modules exist so the failure is a build error, not a missing rule.
val vocabularyModules = setOf("model", "core", "ui")
val redactionModules = setOf("model")
gradle.projectsEvaluated {
    val present = subprojects.map { it.name }.toSet()
    val missing = (vocabularyModules + redactionModules) - present
    require(missing.isEmpty()) {
        "Guards are registered by module name and these do not exist: $missing. " +
            "Renaming a module must not silently remove its guard."
    }
}

subprojects {
    // Vocabulary lint covers model, core and ui — the modules where a wrong word reaches a
    // player. Not platform or harness, which talk to hardware and to tests.
    if (name in vocabularyModules) {
        val vocabulary = tasks.register<VocabularyLintTask>("vocabularyLint") {
            group = "verification"
            description = "Fails on role, mechanic or death-framing words outside the vocabulary."
            sources.from(layout.projectDirectory.dir("src"))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(vocabulary) }
    }

    // Redaction lint covers model, which is where the schema and every wire type live.
    if (name in redactionModules) {
        val redaction = tasks.register<RedactionLintTask>("redactionLint") {
            group = "verification"
            description = "Fails on @Serializable types that are not marked @ClientFacing."
            sources.from(layout.projectDirectory.dir("src"))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(redaction) }
    }
}
