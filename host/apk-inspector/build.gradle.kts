import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
}

val inspectorSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M1-01 malicious APK and deterministic fuzz matrix."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.inspector.ApkInspectorSelfTest")
    systemProperty("ah.inspector.fuzzSamples", providers.gradleProperty("inspectorFuzzSamples").orElse("10000").get())
    systemProperty("ah.inspector.reportDir", layout.buildDirectory.dir("reports/m1-01").get().asFile.absolutePath)
}

tasks.named<Test>("test") {
    dependsOn(inspectorSelfTest)
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
