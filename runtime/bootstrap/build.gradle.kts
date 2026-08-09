plugins {
    alias(libs.plugins.android.library)
}

val m005ExpectedSignerSha256 =
    providers.gradleProperty("m005ExpectedSignerSha256").orElse("0".repeat(64)).get()
if (!m005ExpectedSignerSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
    throw GradleException("m005ExpectedSignerSha256 must be exactly 64 hexadecimal characters")
}

android {
    namespace = "ah.runtime.bootstrap"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "ah.runtime.bootstrap.BootstrapConnectedRunner"
        buildConfigField(
            "String",
            "M005_EXPECTED_SIGNER_SHA256_HEX",
            "\"${m005ExpectedSignerSha256.lowercase()}\"",
        )
    }

    buildFeatures {
        buildConfig = true
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
