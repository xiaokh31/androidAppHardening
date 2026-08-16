plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val jmh by sourceSets.creating
jmh.compileClasspath += sourceSets.main.get().output
jmh.runtimeClasspath += sourceSets.main.get().output

configurations[jmh.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[jmh.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation(project(":integration-tests"))
    implementation(project(":host:cli"))
    implementation(libs.jna.platform)
    add(jmh.implementationConfigurationName, libs.jmh.core)
    add(jmh.annotationProcessorConfigurationName, libs.jmh.generator.annprocess)
}

val runtimeBundle = project(":integration-tests").layout.buildDirectory.dir("generated/m3-01/runtime-bundle")

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the fixed M3-05 three-fixture Host JMH and 100 MiB stress case."
    dependsOn(
        tasks.named(jmh.classesTaskName),
        ":fixtures:android:assembleFixtures",
        ":integration-tests:generateM301RuntimeBundle",
    )
    classpath = jmh.runtimeClasspath + files(runtimeBundle)
    mainClass.set("ah.benchmarks.host.HostBenchmarkMain")
    systemProperty("m305.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("m305.work", layout.buildDirectory.dir("m3-05").get().asFile.absolutePath)
    systemProperty("m305.environment", providers.gradleProperty("m305Environment").orElse("local").get())
    providers.gradleProperty("m305Quick").orNull?.let { systemProperty("m305.quick", it) }
}

tasks.register<JavaExec>("benchmarkStatisticsTest") {
    group = "verification"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("ah.benchmarks.host.BenchmarkStatisticsSelfTest")
}

tasks.named("check") {
    dependsOn("benchmarkStatisticsTest")
}
