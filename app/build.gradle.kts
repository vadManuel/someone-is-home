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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(project(":ui"))
            implementation(project(":platform"))
            implementation(project(":model"))
            // **`core`, because the authority has to run somewhere and this is the only module
            // left that may hold it.** The boundary table has always said so — *app roots may
            // depend on everything* — and names this module as the home of the tick driver, for
            // the reason `core` cannot import coroutines. The edge was simply never drawn,
            // because until the effect push and the intent desk existed there was nothing on
            // this side to join to the rules.
            //
            // **It is not a widening of any guarded boundary.** The three edges that make the
            // modules worth having are `core ↛ coroutines`, `core ↛ platform` and `ui ↛ core`,
            // and all three are untouched: `:ui:boundaryCheck` and `:core:boundaryCheck` are
            // what enforce them and neither reads this line. What it does close is E-D3-3's
            // remaining half at its root — `armingFor` had no production caller because no
            // module that ships in the app could see it.
            implementation(project(":core"))
        }
    }
    // The viewfinder puts a `UIView` holding an `AVCaptureVideoPreviewLayer` inside the Compose
    // tree, which is C interop and Compose's own iOS interop — both opt-in. Declared here rather
    // than as file annotations because `platform` already declares its interop opt-in this way and
    // two conventions for the same thing is one convention nobody follows.
    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
    sourceSets.commonTest {
        // The cheats' own tests, compiled exactly where the cheats are.
        //
        // A test for a cheat CANNOT live in `commonTest`: release does not compile the cheat source
        // set, so the test is an unresolved reference in the one variant that has to build without
        // them. That is the fail-closed arrangement above working rather than a limitation, and it
        // is how this directory came to exist — `LineWatchTest` was written into `commonTest`, the
        // two variants that carry cheats went green, and `variantSweepRelease` refused the build.
        if (variant != "release") kotlin.srcDir("src/cheatsTest/kotlin")
        dependencies {
            implementation(kotlin("test"))
            // Tests only: `Unplug`, a loopback relay a test can take one phone's network away with
            // (V1, D-267). The app itself reaches sockets through `platform` alone.
            implementation(libs.ktor.network)
        }
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
