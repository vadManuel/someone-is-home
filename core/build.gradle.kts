plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()

    // A JVM target for one reason: the JVM counts the bytes a thread allocates, exactly, and
    // Kotlin/Native has nothing honest in its place (D-276). The house's per-upload paths are
    // measured there (`RangingAllocationTest`, D-300) as `model`'s codecs already are. core stays a
    // pure function of state on every target: the boundary below is checked on the iOS classpath,
    // and nothing here is JVM-only.
    jvm()
    sourceSets.commonMain.dependencies {
        implementation(project(":model"))
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

// core is a pure function of state. No coroutines (nondeterministic interleaving), no datetime
// (a clock read is an input, so it arrives as an event with an integer timestamp), no platform.
// This is what makes a 25-minute round replay byte-identically from its recording.
val boundary = tasks.register<BoundaryCheckTask>("boundaryCheck") {
    group = "verification"
    description = "Fails if coroutines, datetime or platform reach core's compile classpath."
    moduleName.set("core")
    classpath.from(configurations.named("iosArm64CompileKlibraries"))
    forbidden.set(listOf("kotlinx-coroutines", "kotlinx-datetime"))
    forbiddenProjects.set(listOf(":platform", ":ui", ":harness"))
    componentIds.set(
        configurations.named("iosArm64CompileKlibraries").map { conf ->
            conf.incoming.resolutionResult.allComponents.map { it.id.displayName }
        }
    )
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(boundary) }
