plugins {
    alias(libs.plugins.android.library)
}

val stageM204RuntimeTemplates by tasks.registering(Sync::class) {
    group = "build"
    description = "Stages the four stripped Runtime templates at the M2-04 fixed path."
    dependsOn("stripReleaseDebugSymbols")
    from(layout.buildDirectory.dir(
        "intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib",
    ))
    into(layout.buildDirectory.dir("intermediates/stripped_native_libs/release/out/lib"))
}

afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy(stageM204RuntimeTemplates)
    }
}

val verifiedMbedTlsRoot =
    rootProject.layout.projectDirectory
        .dir(".toolchains/native-crypto/src/mbedtls-4.1.1")
        .asFile
        .absolutePath
        .replace('\\', '/')

val m204SupportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val m204TargetAbi = providers.gradleProperty("m204TargetAbi").orNull
if (m204TargetAbi != null && m204TargetAbi !in m204SupportedAbis) {
    throw GradleException("m204TargetAbi must be one of ${m204SupportedAbis.joinToString()}")
}

val archiveM204NativeDebugSymbols by tasks.registering(Zip::class) {
    group = "build"
    description = "Archives the four unstripped M2-04 Runtime ELFs separately from release templates."
    dependsOn("stripReleaseDebugSymbols")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
    archiveFileName.set("native-release-native-debug-symbols.zip")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    for (abi in m204SupportedAbis) {
        from(providers.provider {
            val matches = fileTree(layout.buildDirectory.dir("intermediates/cxx/RelWithDebInfo")) {
                include("**/obj/$abi/libah_runtime.so")
            }.files
            require(matches.size == 1) { "expected one unstripped Release $abi ELF, found ${matches.size}" }
            matches.single()
        }) {
            into("lib/$abi")
        }
    }
}

afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy(archiveM204NativeDebugSymbols)
    }
}

android {
    namespace = "ah.runtime.nativebridge"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "ah.runtime.nativebridge.NativeConnectedRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += if (m204TargetAbi == null) m204SupportedAbis else listOf(m204TargetAbi)
            debugSymbolLevel = "FULL"
        }
        if (m204TargetAbi != null) {
            testInstrumentationRunnerArguments["m204_expected_abi"] = m204TargetAbi
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
