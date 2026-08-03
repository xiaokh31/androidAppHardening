import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":host:apk-inspector"))
}

val axmlSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-03 binary AXML transform, negative, and deterministic fuzz matrix."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.axml.AxmlSelfTest")
    systemProperty("ah.axml.fuzzSamples", providers.gradleProperty("axmlFuzzSamples").orElse("5000").get())
    systemProperty("ah.axml.reportDir", layout.buildDirectory.dir("reports/m1-03").get().asFile.absolutePath)
    providers.gradleProperty("aapt2Executable").orNull?.let { systemProperty("ah.axml.aapt2", it) }
    providers.gradleProperty("aapt2AndroidJar").orNull?.let { systemProperty("ah.axml.androidJar", it) }
}

val transformDeviceManifest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Transforms one ignored M1-03 device-fixture Manifest with the production API."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.axml.AxmlDeviceManifestCli")
    workingDir(rootProject.projectDir)
    val input = providers.gradleProperty("m103InputManifest")
    val output = providers.gradleProperty("m103OutputManifest")
    val packageName = providers.gradleProperty("m103PackageName")
    doFirst {
        args(input.get(), output.get(), packageName.get())
    }
}

tasks.named<Test>("test") {
    dependsOn(axmlSelfTest)
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
