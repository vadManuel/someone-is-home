plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// ---------------------------------------------------------------------------------------------
// Build variants (story 0.10b): release / playtest / debug.
//
// The variant is a SOURCE SET, not a flag. A release build does not carry cheats that are
// switched off; it carries no cheat code at all, so "the cheats leaked into release" is an
// unresolved reference at compile time rather than a boolean somebody has to audit. Each
// variant directory defines the same two names — `BuildVariant` and `VariantRoot` — and the
// property below picks which definition the framework is built from.
//
//   ./gradlew <anything>                 debug    (the development default)
//   ./gradlew <anything> -Pvariant=playtest
//   ./gradlew <anything> -Pvariant=release
//
// Xcode's three configurations (Debug / Playtest / Release) pass the matching property from
// the framework build phase, so the .app and the Kotlin inside it cannot disagree.
// ---------------------------------------------------------------------------------------------
val variantNames = listOf("release", "playtest", "debug")
val variant: String = providers.gradleProperty("variant").getOrElse("debug")
require(variant in variantNames) {
    "unknown variant '$variant' — one of: ${variantNames.joinToString()}"
}

kotlin {
    // Device AND simulator, deliberately, with the same caveat `ui`'s desktop target carries:
    // the simulator can show that the screens LAY OUT, and can never show that the game works.
    // It has no BLE, torch, camera or haptics -- which is every input this game has. Nothing
    // that touches those may ever be signed off from here.
    iosArm64 {
        binaries.framework {
            baseName = "SomeoneIsHomeKit"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "SomeoneIsHomeKit"
            isStatic = true
        }
    }

    sourceSets.commonMain {
        kotlin.srcDir("src/variant/$variant/kotlin")
        // The cheat surfaces exist for playtest and debug and are ABSENT from release — absent
        // as in not compiled, so no runtime check stands between them and a shipped round.
        if (variant != "release") kotlin.srcDir("src/cheats/kotlin")
        dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(project(":ui"))
            implementation(project(":platform"))
            implementation(project(":model"))
        }
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

// A plain `./gradlew check` builds ONE variant, so on its own it would certify a third of the
// matrix and report green — the exact shape of pass this project keeps writing down. These run
// the other two variants' tests in nested builds: release proves the cheats are unreachable
// (an injected reference fails compilation — see verify-guards.sh), and each variant's
// `BuildVariantTest` runs against its own constants rather than the default's.
val otherVariantSweeps = variantNames.filter { it != variant }.map { other ->
    tasks.register<GradleBuild>("variantSweep" + other.replaceFirstChar { it.uppercase() }) {
        group = "verification"
        description = "Compiles and tests the '$other' variant, which this build did not select."
        tasks = listOf(":app:iosSimulatorArm64Test")
        // Distinct names, or the two sweeps in one build collide on the default nested-build
        // name and the second fails before running anything.
        buildName = "variant-$other"
        startParameter = startParameter.newInstance().apply {
            projectProperties = mapOf("variant" to other)
        }
        // Serialised behind the selected variant's own test run: all three share :app's build
        // directory, and two variants compiling into it at once corrupt each other's outputs.
        mustRunAfter(project.tasks.matching { it.name == "iosSimulatorArm64Test" })
    }
}
otherVariantSweeps.zipWithNext().forEach { (a, b) -> b.configure { mustRunAfter(a) } }
tasks.matching { it.name == "check" }.configureEach { dependsOn(otherVariantSweeps) }
