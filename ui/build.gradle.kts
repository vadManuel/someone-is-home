plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64(); iosSimulatorArm64()

    // A desktop target that exists ONLY so the screens can be looked at while they are built.
    // Writing several thousand lines of layout and inferring correctness from `compileKotlin`
    // is how you ship a springboard whose tiles are in the wrong grid: weights, aspect ratios
    // and the density-scaled canvas all fail at runtime, silently, never at compile time.
    //
    // This is NOT simulator verification, which project-context rules out. That rule is about
    // the game -- BLE, torch, camera, haptics -- none of which this touches. It renders layout
    // on a Mac and nothing else, and it ships in no build variant.
    jvm("desktop")
    sourceSets.commonMain.dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.ui)
        implementation(libs.compose.resources)
        implementation(project(":model"))
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
    sourceSets.named("desktopMain").dependencies {
        implementation(compose.desktop.currentOs)
    }
    sourceSets.named("desktopTest").dependencies {
        implementation(kotlin("test"))
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
    }
}

compose.desktop {
    application { mainClass = "home.someoneshome.ui.preview.PreviewKt" }
}

// The pixel fonts ARE the interface. Silkscreen and VT323 are bitmap-derived faces whose glyphs
// only land on whole pixels; substituting a system monospace does not degrade the look, it
// deletes it. Both are OFL — licences sit beside the files in composeResources/font.
compose.resources {
    publicResClass = false
    packageOfResClass = "home.someoneshome.ui.generated.resources"
    generateResClass = always
}

// ui renders Effects and emits Intents. It never reaches into the rules, because a screen that
// can ask the core a question is a screen that can leak the answer.
val uiKlibConfig = configurations.named("iosArm64CompileKlibraries")
val boundary = tasks.register<BoundaryCheckTask>("boundaryCheck") {
    group = "verification"
    description = "Fails if :core becomes reachable from ui."
    moduleName.set("ui")
    classpath.from(uiKlibConfig)
    forbidden.set(emptyList<String>())
    forbiddenProjects.set(listOf(":core"))
    componentIds.set(
        uiKlibConfig.map { conf ->
            conf.incoming.resolutionResult.allComponents.map { it.id.displayName }
        }
    )
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(boundary) }
