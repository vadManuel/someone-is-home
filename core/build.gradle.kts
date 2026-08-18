plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
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
