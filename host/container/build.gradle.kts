plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":host:apk-inspector"))
}

val containerTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-04 AHDC v2 vectors, round trips, tamper matrix, and cleanup checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.ContainerSelfTest")
    systemProperty("ah.container.reportDir", layout.buildDirectory.dir("reports/m1-04").get().asFile.absolutePath)
}

tasks.named<Test>("test") {
    dependsOn(containerTest)
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
