rootProject.name = "someone-is-home"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// The module list IS the architecture. See _bmad-output/planning-artifacts/game-architecture.md.
include(":model")      // data + the redaction schema
include(":core")       // pure rules. No coroutines, no datetime, no platform.
include(":platform")   // expect/actual — BLE, torch, sensors, clock
include(":ui")         // Compose. The whole fake phone OS. Never sees :core.
include(":harness")    // recording, replay, differential leak tests
include(":app")        // the iOS app root. The one place that sees both :ui and :platform.

// Host-side, and it ships in nothing. The printable deck (story 4.11) is a JVM tool that reads
// `model` and writes a PDF; it is a module rather than a script because the sheet is the one
// artifact in this project that CANNOT BE PATCHED once it is on paper, and a thing that cannot be
// patched gets tests. No iOS target, so nothing in it can ever be linked into the app.
include(":cards")
