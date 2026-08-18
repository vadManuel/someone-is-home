plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.coroutines.core)
    }
    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlin.native.runtime.NativeRuntimeApi",
        )
    }
}
