plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosArm64(); iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation(project(":model"))
        implementation(project(":core"))
        implementation(project(":platform"))
        implementation(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
