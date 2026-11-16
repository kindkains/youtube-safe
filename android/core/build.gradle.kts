plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Gemeinsame Testtabelle der Entscheidungslogik (wird auch von den Server-Tests benutzt).
val gateCases = rootDir.resolve("../shared/gate-cases.json")

tasks.test {
    inputs.file(gateCases)
    systemProperty("gateCases", gateCases.absolutePath)
    // Live-Tests gegen YouTube nur auf ausdrücklichen Wunsch: ./gradlew :core:test -Plive
    val live = project.hasProperty("live")
    inputs.property("live", live)
    if (!live) {
        exclude("**/live/**")
    }
}
