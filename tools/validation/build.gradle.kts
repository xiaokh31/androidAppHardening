plugins {
    `java-library`
}

dependencies {
    implementation(project(":host:apk-inspector"))
    implementation(project(":host:axml"))
    runtimeOnly(libs.jazzer)
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

val m302CorpusRoot = layout.projectDirectory.dir("src/fuzz/resources/corpus")
val m302RegressionRoot = layout.projectDirectory.dir("src/fuzz/resources/regressions")
val m302WorkRoot = layout.buildDirectory.dir("fuzz-work")
val m302Report = layout.buildDirectory.file("reports/security/fuzz-summary.json")
val m302ComponentReports = layout.buildDirectory.dir("reports/security/m3-02")
val m302FuzzSeconds = providers.gradleProperty("m302FuzzSeconds").map(String::toInt).orElse(600)
val m302NightlyFuzzSeconds = providers.gradleProperty("m302FuzzSeconds").map(String::toInt).orElse(3600)

val verifyM302FuzzToolchain by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies the immutable Jazzer, Clang, sanitizer, resource and runner lock."
    workingDir(rootProject.projectDir)
    commandLine("node", "tools/validation/verify-m3-02-fuzz-toolchain.mjs")
}

fun registerJazzerTarget(
    taskName: String,
    targetClass: String,
    corpusName: String,
    durationSeconds: Provider<Int>,
) : TaskProvider<JavaExec> {
    val work = m302WorkRoot.get().dir(taskName).asFile
    val isolatedCorpus = work.resolve("corpus")
    val crashes = work.resolve("crashes")
    val prepareCorpus = tasks.register<Sync>("prepare${taskName.replaceFirstChar(Char::uppercaseChar)}Corpus") {
        from(m302CorpusRoot.dir(corpusName))
        from(m302RegressionRoot.dir(corpusName))
        into(isolatedCorpus)
    }
    return tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Runs the pinned Jazzer target $targetClass with bounded resources."
        dependsOn(tasks.named("classes"), prepareCorpus, "regressionFuzz")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.code_intelligence.jazzer.Jazzer")
        // Jazzer's bytecode instrumentation uses substantial native memory, so bound
        // every major JVM pool below the fail-closed 2 GiB total-process RSS cap.
        maxHeapSize = "256m"
        jvmArgs(
            "-XX:MaxMetaspaceSize=384m",
            "-XX:ReservedCodeCacheSize=128m",
            "-XX:MaxDirectMemorySize=128m",
            "-Xss512k",
        )
        workingDir(work)
        args(
            "--target_class=$targetClass",
            "-max_total_time=${durationSeconds.get()}",
            "-timeout=5",
            "-rss_limit_mb=2048",
            "-max_len=4194304",
            "-artifact_prefix=${crashes.absolutePath}${File.separator}",
            isolatedCorpus.absolutePath,
        )
        systemProperty("ah.m302.workDir", work.absolutePath)
        doFirst {
            crashes.mkdirs()
        }
    }
}

val jazzerApkPr = registerJazzerTarget(
    "jazzerApkPr",
    "ah.tools.validation.fuzz.ApkInspectorFuzzTarget",
    "apk",
    m302FuzzSeconds,
)
val jazzerAxmlPr = registerJazzerTarget(
    "jazzerAxmlPr",
    "ah.tools.validation.fuzz.BinaryAxmlFuzzTarget",
    "axml",
    m302FuzzSeconds,
)
val jazzerApkNightly = registerJazzerTarget(
    "jazzerApkNightly",
    "ah.tools.validation.fuzz.ApkInspectorFuzzTarget",
    "apk",
    m302NightlyFuzzSeconds,
)
val jazzerAxmlNightly = registerJazzerTarget(
    "jazzerAxmlNightly",
    "ah.tools.validation.fuzz.BinaryAxmlFuzzTarget",
    "axml",
    m302NightlyFuzzSeconds,
)

val regressionFuzz by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs every synthetic JVM fuzz seed and regression twice with identical results."
    dependsOn(tasks.named("classes"), verifyM302FuzzToolchain)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.tools.validation.fuzz.RegressionFuzzRunner")
    args(
        m302CorpusRoot.asFile.absolutePath,
        m302RegressionRoot.asFile.absolutePath,
        m302WorkRoot.get().dir("regression").asFile.absolutePath,
        m302ComponentReports.get().file("regression.json").asFile.absolutePath,
    )
}

val tamperTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates and executes the versioned M3-02 deterministic tamper contract."
    dependsOn(
        tasks.named("classes"),
        regressionFuzz,
        ":host:apk-inspector:inspectorSelfTest",
        ":host:axml:axmlSelfTest",
        ":host:container:containerTest",
    )
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.tools.validation.tamper.TamperCatalogRunner")
    args(
        layout.projectDirectory.file("src/tamper/resources/catalog.yaml").asFile.absolutePath,
        m302WorkRoot.get().dir("tamper").asFile.absolutePath,
        m302ComponentReports.get().file("tamper.json").asFile.absolutePath,
        m302ComponentReports.get().file("regression.json").asFile.absolutePath,
        rootProject.layout.projectDirectory.file("host/apk-inspector/build/reports/m1-01/error-matrix.json").asFile.absolutePath,
        rootProject.layout.projectDirectory.file("host/axml/build/reports/m1-03/error-matrix.json").asFile.absolutePath,
        rootProject.layout.projectDirectory.file("host/container/build/reports/m1-04/container-self-test.json").asFile.absolutePath,
        m302Report.get().asFile.absolutePath,
    )
}

fun registerNativeFuzz(taskName: String, durationSeconds: Provider<Int>) = tasks.register<Exec>(taskName) {
    group = "verification"
    description = "Runs the Ubuntu-only Clang libFuzzer target with ASan/UBSan and bounded resources."
    enabled = System.getProperty("os.name").lowercase().contains("linux")
    val executable = providers.gradleProperty("m302NativeFuzzer").orElse("M3_02_NATIVE_FUZZER_REQUIRED").get()
    val work = m302WorkRoot.get().dir(taskName).asFile
    workingDir(rootProject.projectDir)
    commandLine(
        "node",
        "tools/validation/run-m3-02-native-fuzz.mjs",
        executable,
        durationSeconds.get().toString(),
        work.absolutePath,
    )
    doFirst {
        if (!File(executable).isFile) {
            throw GradleException("-Pm302NativeFuzzer must identify the built M3-02 libFuzzer executable")
        }
    }
}

val nativePrFuzz = registerNativeFuzz("nativePrFuzz", m302FuzzSeconds)
val nativeNightlyFuzz = registerNativeFuzz("nativeNightlyFuzz", m302NightlyFuzzSeconds)

val prFuzz by tasks.registering {
    group = "verification"
    description = "Runs each M3-02 JVM Jazzer target for 10 minutes; Native libFuzzer is CI-hosted on Ubuntu."
    dependsOn(regressionFuzz, tamperTest, jazzerApkPr, jazzerAxmlPr, nativePrFuzz)
}

val nightlyFuzz by tasks.registering {
    group = "verification"
    description = "Runs each M3-02 JVM Jazzer target for 60 minutes; Native libFuzzer is CI-hosted on Ubuntu."
    dependsOn(regressionFuzz, tamperTest, jazzerApkNightly, jazzerAxmlNightly, nativeNightlyFuzz)
}

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
    dependsOn(testM005ConfigParser, verifyM302FuzzToolchain, regressionFuzz, tamperTest)
}
