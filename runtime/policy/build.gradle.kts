import org.gradle.api.tasks.JavaExec
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ah.runtime.policy"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "ah.runtime.guard.PolicyConnectedRunner"
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
    implementation(project(":runtime:native"))
    implementation(libs.android.apksig)
}

val policySelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-03 policy unit matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    val externalRuntime =
        configurations.named("debugRuntimeClasspath").map { configuration ->
            configuration.incoming.artifactView {
                componentFilter { identifier -> identifier is ModuleComponentIdentifier }
            }.files
        }
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        externalRuntime,
    )
    mainClass.set("ah.runtime.guard.PolicySelfTest")
}

val abiCompatibilitySelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-04 ABI compatibility matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    val externalRuntime =
        configurations.named("debugRuntimeClasspath").map { configuration ->
            configuration.incoming.artifactView {
                componentFilter { identifier -> identifier is ModuleComponentIdentifier }
            }.files
        }
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        externalRuntime,
    )
    mainClass.set("ah.runtime.AbiCompatibilitySelfTest")
    args(layout.buildDirectory.dir("reports/m2-04").get().asFile.absolutePath)
}

val environmentRiskSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-05 environment risk policy matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
    )
    mainClass.set("ah.runtime.risk.EnvironmentRiskEngineSelfTest")
    args(layout.buildDirectory.dir("reports/m2-05").get().asFile.absolutePath)
}

val memoryControlsSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-06 memory profile policy matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
    )
    mainClass.set("ah.runtime.MemoryControlsSelfTest")
}

afterEvaluate {
    tasks.named("test") {
        dependsOn(
            policySelfTest,
            abiCompatibilitySelfTest,
            environmentRiskSelfTest,
            memoryControlsSelfTest,
        )
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
