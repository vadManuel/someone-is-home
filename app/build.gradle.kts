plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The app root: the one place that is allowed to know about every layer at once.
//
// `ui` renders and `platform` talks to hardware, and neither may reach the other -- a screen that
// can read a sensor is a screen that can leak what the sensor saw. This module is where they are
// wired together, which is why it is the only module that depends on both, and why it stays as
// close to empty as it can. Logic here is logic no boundary check covers.
kotlin {
    // Device AND simulator, deliberately, with the same caveat `ui`'s desktop target carries:
    // the simulator can show that the screens LAY OUT, and can never show that the game works.
    // It has no BLE, torch, camera or haptics -- which is every input this game has. Nothing
    // that touches those may ever be signed off from here.
    iosArm64 {
        binaries.framework {
            baseName = "SomeoneIsHomeKit"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "SomeoneIsHomeKit"
            isStatic = true
        }
    }

    sourceSets.commonMain.dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.ui)
        implementation(project(":ui"))
        implementation(project(":platform"))
        implementation(project(":model"))
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
