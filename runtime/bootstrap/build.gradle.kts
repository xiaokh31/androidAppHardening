plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ah.runtime.bootstrap"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += "GradleDependency" // M0-03 intentionally pins compileSdk 36.
        warningsAsErrors = true
    }
}

dependencies {
    api(project(":runtime:policy"))
    implementation(libs.android.apksig)
}
