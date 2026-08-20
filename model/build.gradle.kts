plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    iosArm64(); iosSimulatorArm64()

    // A JVM target so :ui's desktop render harness can resolve this module. `model` is pure
    // data -- no coroutines, no datetime, no platform types -- so there is nothing here that a
    // second target could make untrue. The redaction and vocabulary lints are source-based and
    // are unaffected by target count.
    jvm()
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
