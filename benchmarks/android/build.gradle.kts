plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ah.benchmarks.android"
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

dependencies {
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
