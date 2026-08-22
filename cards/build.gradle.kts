plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// ONE TARGET, AND IT IS THE MAC. This module makes paper. It reads `model` — the roster, the
// payload format, the two reserved shapes — and writes a PDF a host prints at home (story 4.11).
// There is no iOS target and there must never be one: a phone has no business generating the cards
// it later reads, and the absence of the target is what makes that structural rather than a rule.
kotlin {
    jvm()
    sourceSets.named("jvmMain").dependencies {
        implementation(project(":model"))
        implementation(libs.zxing.core)
    }
    sourceSets.named("jvmTest").dependencies {
        implementation(kotlin("test"))
        implementation(libs.zxing.core)
    }
}

// The command, and the whole of the operating instructions:
//
//   ./gradlew :cards:sheet                 a fresh run tag, so a fresh set of printed ids
//   ./gradlew :cards:sheet -Prun=QK7M      that exact run tag, reprinting the same deck
//
// A REPRINT MUST NOT REPEAT ITS IDS BY DEFAULT. The id exists because paper is lost (D-069): a
// card found behind a shelf a year later has to be recognisable as a card nobody registered, and it
// is only recognisable if the replacement carries a different id. So the tag is fresh unless
// somebody deliberately asks for an old one — and `-Prun` exists for the case where a card was
// spilled on and its OWN replacement is wanted, which is the one time repeating an id is right.
val jvmMain = kotlin.jvm().compilations.getByName("main")
tasks.register<JavaExec>("sheet") {
    group = "build"
    description = "Writes the printable marker deck to cards/build/deck."
    mainClass.set("home.someoneshome.cards.SheetKt")
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    args(layout.buildDirectory.dir("deck").get().asFile.absolutePath)
    providers.gradleProperty("run").orNull?.let { args(it) }
}

// The sheet is NOT built by `check`. It writes a file with a fresh run tag in its name every time,
// and a verification step that leaves a different artifact behind on every run is one nobody can
// diff. What `check` runs is the tests, which build the same deck in memory and read it back.
