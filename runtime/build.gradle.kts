plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.pocketdev.runtime"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core"))

    testImplementation(libs.junit)
}
