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

val prepareM202DeviceVector by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Builds an ignored M2-02 device vector from two synthetic DEX files."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M202DeviceVectorMain")
    doFirst {
        args(
            providers.gradleProperty("m202PrimaryDex").get(),
            providers.gradleProperty("m202SecondaryDex").get(),
            providers.gradleProperty("m202VectorOutput").get(),
            providers.gradleProperty("m202PackageName").get(),
            providers.gradleProperty("m202SignerSha256").get(),
        )
    }
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
