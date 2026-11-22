plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64 {
        // **The motion budget's sampler, on a phone only** (M2, D-321). Core Motion's
        // `userAcceleration` is read in C and crosses as three doubles, so a sample at 100 Hz costs
        // the Kotlin heap nothing (rule 7). The simulator has no device motion and its source set
        // holds a stand-in instead, so the interop is compiled for the device target alone.
        compilations.getByName("main").cinterops.create("motion") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/motion.def"))
            packageName("home.someoneshome.platform.motion")
        }
    }
    iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.coroutines.core)
        // The transport machinery (0.8) names Seat and the wire frames. Allowed by the boundary
        // table -- platform may see model, never core or ui.
        implementation(project(":model"))
        // D1: embedded Ktor server on the host, websocket clients on the phones. CIO on both
        // sides -- the one engine that is plain multiplatform Kotlin on native targets.
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.cio)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlin.native.runtime.NativeRuntimeApi",
        )
    }
}
