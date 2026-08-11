plugins {
    alias(libs.plugins.android.library)
}

abstract class StageM204NativeDebugSymbols : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val supportedAbis: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val unstrippedRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val destination: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun stage() {
        val sourceRoot = unstrippedRoot.get().asFile
        val destinationRoot = destination.get().asFile
        destinationRoot.deleteRecursively()
        for (abi in supportedAbis.get()) {
            val suffix = "/obj/$abi/libah_runtime.so"
            val matches = sourceRoot.walkTopDown()
                .filter { it.isFile && it.invariantSeparatorsPath.endsWith(suffix) }
                .toList()
            require(matches.size == 1) { "expected one unstripped Release $abi ELF, found ${matches.size}" }
            val target = destinationRoot.resolve("lib/$abi/libah_runtime.so")
            target.parentFile.mkdirs()
            matches.single().copyTo(target, overwrite = false)
        }
    }
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

val stagedM204NativeDebugSymbols =
    layout.buildDirectory.dir("intermediates/m2-04-native-debug-symbols")

val stageM204NativeDebugSymbols by tasks.registering(StageM204NativeDebugSymbols::class) {
    group = "build"
    description = "Stages the four unstripped M2-04 Runtime ELFs after the Release native build."
    dependsOn("stripReleaseDebugSymbols")
    supportedAbis.set(m204SupportedAbis)
    unstrippedRoot.set(layout.buildDirectory.dir("intermediates/cxx/RelWithDebInfo"))
    destination.set(stagedM204NativeDebugSymbols)
}

val archiveM204NativeDebugSymbols by tasks.registering(Zip::class) {
    group = "build"
    description = "Archives the four unstripped M2-04 Runtime ELFs separately from release templates."
    dependsOn(stageM204NativeDebugSymbols)
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
    archiveFileName.set("native-release-native-debug-symbols.zip")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(stagedM204NativeDebugSymbols)
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
        // M2-04's test-only m204TargetAbi intentionally builds one process ABI;
        // the release default is locked to all four ABIs by the archive verifier.
        disable += "ChromeOsAbiSupport"
        warningsAsErrors = true
    }
}
