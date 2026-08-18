plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(kotlin("test"))
}

// The guard rules are unit-tested. `./gradlew check` passing proves only that the guards did not
// fire; these prove the matching logic still recognises a violation.
tasks.test { useJUnitPlatform() }
