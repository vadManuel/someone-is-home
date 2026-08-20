plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
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
