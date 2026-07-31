plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ah.fixtures.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        applicationId = "ah.fixtures.android"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += setOf(
            "GradleDependency", // M0-03 intentionally pins compileSdk 36.
            "MissingApplicationIcon", // The empty M0-03 fixture has no UI assets.
            "OldTargetApi", // M0-03 intentionally pins fixture targetSdk 36.
        )
        warningsAsErrors = true
    }
}
