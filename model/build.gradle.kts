plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}
