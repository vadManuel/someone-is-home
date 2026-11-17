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
val vocabularyModules = setOf("model", "core", "ui", "app", "cards")
val redactionModules = setOf("model")
// The arming cordon covers `app`, which is the only module that holds the house. Its production
// source sets are what a release build compiles; the benches beside them are not.
val cordonModules = setOf("app")
// The seed-source cordon (D-215) covers the two modules that draw a round's seed and a seat's token:
// `app`, every source set that compiles into a phone and the cheats among them, and `platform`,
// where the secure source itself lives.
val seedSourceModules = setOf("app", "platform")
gradle.projectsEvaluated {
    val present = subprojects.map { it.name }.toSet()
    val missing = (vocabularyModules + redactionModules + cordonModules + seedSourceModules) - present
    require(missing.isEmpty()) {
        "Guards are registered by module name and these do not exist: $missing. " +
            "Renaming a module must not silently remove its guard."
    }
}

// **The human-mock, from Gradle — and deliberately NOT a dependency of `check`.**
//
// `check` is the suite that runs constantly and finishes in seconds; this one builds an app, boots
// a simulator, installs and drives it, and takes minutes. Wiring the slow suite into the fast one
// makes the fast one stop being run, which costs more than it buys. It is the release gate and it
// is invoked on purpose: `./gradlew e2e`, or `./e2e.sh --repeat 5` for the flakiness number.
//
// It shells out to the script rather than reimplementing it, so there is exactly one definition of
// how this app is driven and both doors open onto it.
tasks.register<Exec>("e2e") {
    group = "verification"
    description = "Drives the real app on a simulator with a mock of the human using it (slow; not in check)."
    workingDir = rootDir
    commandLine("./e2e.sh")
}

subprojects {
    // Vocabulary lint covers model, core, ui, app and cards — the modules where a wrong word
    // reaches a player. `app` holds no copy today and is meant to stay that way; it is listed
    // because the app root is the likeliest place for a stray string to appear later, and a lint
    // added after the fact is a lint added after the word shipped.
    // `cards` is listed for the harder version of that: its strings are PRINTED. A wrong word on a
    // screen is a wrong word until somebody fixes it; a wrong word on forty-four cards is a wrong
    // word in a house until somebody prints them again.
    // Not platform or harness, which talk to hardware and to tests.
    if (name in vocabularyModules) {
        val vocabulary = tasks.register<VocabularyLintTask>("vocabularyLint") {
            group = "verification"
            description = "Fails on role, mechanic or end-framing words outside the vocabulary."
            sources.from(layout.projectDirectory.dir("src"))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(vocabulary) }
    }

    // The arming cordon covers app's PRODUCTION source only — `src/commonMain`, `src/iosMain` and
    // the three variant roots. `src/cheats/` is deliberately outside it: the benches are where the
    // launch switches belong, and a release build does not compile that directory at all.
    //
    // The directories are named rather than excluded from `src`, because an exclusion that stopped
    // matching after a layout change would silently widen what is allowed, and `SourceGuardTask`
    // can only refuse an EMPTY scan — not a scan that quietly grew a hole in the middle.
    if (name in cordonModules) {
        val cordon = tasks.register<ArmingCordonTask>("armingCordon") {
            group = "verification"
            description = "Fails if anything that ships reads the launch environment."
            sources.from(
                layout.projectDirectory.dir("src/commonMain"),
                layout.projectDirectory.dir("src/iosMain"),
                layout.projectDirectory.dir("src/variant"),
            )
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(cordon) }

        // **And the one launch read the cheats may not make freely** (R4, D-240): the round seed's
        // switch, read only inside `RoundCheat.benchSeed` and only behind the bench's own switch.
        // The app root answers a real LIGHTS OUT from `src/cheats/`, so the cordon above cannot see
        // the press, and until D-240 the press read the seed on every launch.
        val benchSeed = tasks.register<BenchSeedCordonTask>("benchSeedCordon") {
            group = "verification"
            description = "Fails if the cheats read the round seed's switch outside the bench's gate."
            sources.from(layout.projectDirectory.dir("src/cheats"))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(benchSeed) }
    }

    // **The seed-source cordon (D-215).** Every secret in a round is an HMAC keyed by the round's
    // seed, so the seed's source is the whole of the secrecy. No `kotlin.random` in any source set
    // that compiles into a phone -- the cheats included, because the bench's seed draw is the one an
    // evening actually got. The directories are named, for the arming cordon's reason above.
    if (name in seedSourceModules) {
        val seedSource = tasks.register<SeedSourceCordonTask>("seedSourceCordon") {
            group = "verification"
            description = "Fails on any use of kotlin.random where a seed or a seat token is drawn."
            listOf("src/commonMain", "src/iosMain", "src/variant", "src/cheats")
                .map { layout.projectDirectory.dir(it) }
                .filter { it.asFile.exists() }
                .forEach { sources.from(it) }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(seedSource) }
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
