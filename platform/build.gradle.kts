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
