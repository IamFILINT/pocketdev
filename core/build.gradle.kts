// Pure-Kotlin (JVM) core domain logic. No Android dependencies, so it can be
// unit-tested quickly on GitHub Actions without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}