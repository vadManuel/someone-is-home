plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Device only. The Simulator renders through a different path on host hardware, has no
    // ProMotion panel, and does not exhibit Kotlin/Native-on-ARM GC behaviour. It cannot
    // answer this question, so it is not a target.
    iosArm64 {
        binaries.framework {
            baseName = "SpikeKit"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlin.native.runtime.NativeRuntimeApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            // No material3 on purpose. The gate is about the draw path, and every dependency
            // that can put work on a frame is one more thing the result must be defended
            // against.
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
