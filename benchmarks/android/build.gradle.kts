plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ah.benchmarks.android"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        applicationId = "ah.benchmarks.android"
        testApplicationId = "ah.benchmarks.android"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += "GradleDependency" // M0-03 intentionally pins compileSdk 36.
        disable += "OldTargetApi" // The fixed M3 reference contract targets API 36.
        disable += "DataExtractionRules" // Test-only harness has no user backup surface.
        disable += "MissingApplicationIcon" // Test-only harness is launched only by instrumentation.
        warningsAsErrors = true
    }
}

val selfInstrumentingStdlib by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies.add(
    selfInstrumentingStdlib.name,
    "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
)

val embeddedStdlibName = "kotlin-stdlib.jar"
val embeddedStdlibDirectory = layout.buildDirectory.dir("generated/self-instrumenting-dependencies")
val embeddedStdlib = embeddedStdlibDirectory.map { it.file(embeddedStdlibName) }
val prepareSelfInstrumentingStdlib by tasks.registering(Copy::class) {
    from(selfInstrumentingStdlib)
    into(embeddedStdlibDirectory)
    rename { "kotlin-stdlib.jar" }
    outputs.file(embeddedStdlib)
}

dependencies {
    // AGP normally removes dependencies also supplied by the tested APK. This
    // copied, pinned artifact keeps stdlib inside the independently installed
    // self-instrumenting test APK while the empty main APK stays uninstalled.
    androidTestImplementation(files(embeddedStdlib).builtBy(prepareSelfInstrumentingStdlib))
    androidTestImplementation(libs.androidx.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(project(":runtime:native"))
    androidTestImplementation(project(":runtime:policy"))
}

val androidHome = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val adb = androidHome.map { File(it, "platform-tools/${if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"}") }
val benchmarkRunner = rootProject.layout.projectDirectory.dir("tools/validation").file(
    listOf("run", "m3", "05", "android", "benchmark.mjs").joinToString("-"),
)

tasks.register<Exec>("connectedBenchmarkAndroidTest") {
    group = "verification"
    description = "Runs the bounded Macrobenchmark against the three prepared baseline/protected targets."
    dependsOn(
        "assembleDebug",
        "assembleDebugAndroidTest",
        ":integration-tests:prepareAndroidPerformanceBenchmark",
        ":runtime:policy:assembleRelease",
        ":host:cli:jar",
    )
    commandLine(
        "node",
        benchmarkRunner.asFile.absolutePath,
        "--adb", adb.get().absolutePath,
        "--benchmark-apk", layout.buildDirectory.file("outputs/apk/debug/android-debug.apk").get().asFile.absolutePath,
        "--test-apk", layout.buildDirectory.file("outputs/apk/androidTest/debug/android-debug-androidTest.apk").get().asFile.absolutePath,
        "--targets", project(":integration-tests").layout.buildDirectory.dir(listOf("m3", "05-device-targets/cases").joinToString("-")).get().asFile.absolutePath,
        "--output", layout.buildDirectory.dir("reports/performance").get().asFile.absolutePath,
    )
}
