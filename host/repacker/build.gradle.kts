import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":host:apk-inspector"))
    api(project(":host:axml"))
    api(project(":host:container"))
    implementation(libs.jna.platform)
}

val repackerTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-05 APK repacker, alignment, verifier, ABI, and failure matrix."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.repacker.RepackerSelfTest")
    systemProperty("ah.repacker.reportDir", layout.buildDirectory.dir("reports/m1-05").get().asFile.absolutePath)
    providers.gradleProperty("aapt2Executable").orNull?.let { systemProperty("ah.repacker.aapt2", it) }
    providers.gradleProperty("aapt2AndroidJar").orNull?.let { systemProperty("ah.repacker.androidJar", it) }
}

tasks.named<Test>("test") {
    dependsOn(repackerTest)
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
