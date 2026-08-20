plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.coroutines.core)
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
