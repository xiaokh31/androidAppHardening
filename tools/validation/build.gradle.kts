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

val m005ConfigParserTestClasses = layout.buildDirectory.dir("classes/m0-05-config-parser-test")

val compileM005ConfigParserTest by tasks.registering(JavaCompile::class) {
    source(
        rootProject.file("runtime/bootstrap/src/main/java/ah/runtime/bootstrap/PocFailure.java"),
        rootProject.file("runtime/bootstrap/src/main/java/ah/runtime/bootstrap/EarlySignerResult.java"),
        layout.projectDirectory.file(
            "src/m0_05_config_test/java/ah/runtime/bootstrap/EarlyConfigResult.java",
        ),
        layout.projectDirectory.file(
            "src/m0_05_config_test/java/ah/runtime/bootstrap/ConfigV2Parser.java",
        ),
        layout.projectDirectory.file(
            "src/m0_05_config_test/java/ah/runtime/bootstrap/ConfigV2ParserSelfTest.java",
        ),
    )
    classpath = files()
    destinationDirectory.set(m005ConfigParserTestClasses)
    options.release.set(17)
}

val testM005ConfigParser by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M0-05 ConfigV2 positive and tamper matrix."
    dependsOn(compileM005ConfigParserTest)
    classpath = files(m005ConfigParserTestClasses)
    mainClass.set("ah.runtime.bootstrap.ConfigV2ParserSelfTest")
}

tasks.named("check") {
    dependsOn(testM005ConfigParser)
}
