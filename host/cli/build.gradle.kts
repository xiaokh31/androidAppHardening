import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":host:apk-inspector"))
    implementation(project(":host:axml"))
    implementation(project(":host:container"))
    implementation(project(":host:repacker"))
}

application {
    mainClass.set("ah.host.cli.CliMain")
    applicationName = "android-app-hardening"
}

val cliTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs M1-06 parser, report schema, error mapping, path, cleanup, and capability tests."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.cli.CliSelfTest")
    systemProperty("ah.cli.reportDir", layout.buildDirectory.dir("reports/m1-06/unit").get().asFile.absolutePath)
}

val integrationTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-06 full-flow CLI against the repository-generated signed APK fixture."
    dependsOn(tasks.named("testClasses"), ":host:apk-inspector:signerPolicyTest")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.cli.CliIntegrationSelfTest")
    systemProperty("ah.cli.reportDir", layout.buildDirectory.dir("reports/m1-06/integration").get().asFile.absolutePath)
    systemProperty(
        "ah.cli.signedFixture",
        project(":host:apk-inspector").layout.buildDirectory.file("reports/m1-02/fixtures/combined.apk").get().asFile.absolutePath,
    )
}

tasks.named<Test>("test") {
    dependsOn(cliTest)
    failOnNoDiscoveredTests = false
}

tasks.named("check") {
    dependsOn(integrationTest)
}
