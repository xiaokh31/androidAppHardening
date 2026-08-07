plugins {
    alias(libs.plugins.android.library)
}

val verifiedMbedTlsRoot =
    rootProject.layout.projectDirectory
        .dir(".toolchains/native-crypto/src/mbedtls-4.1.1")
        .asFile
        .absolutePath
        .replace('\\', '/')

android {
    namespace = "ah.runtime.nativebridge"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()

        ndk {
            abiFilters += listOf(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.android.cmake.get()
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += "-DAH_MBEDTLS_ROOT=$verifiedMbedTlsRoot"
            }
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += "GradleDependency" // M0-03 intentionally pins compileSdk 36.
        warningsAsErrors = true
    }
}
