import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.tools.ToolProvider
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
}

@CacheableTask
abstract class GenerateClassloaderPocDex
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val d8Executable: RegularFileProperty

    @get:Input
    abstract val jdkRuntimeVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        if (jdkRuntimeVersion.get() != "17.0.19+10") {
            throw GradleException(
                "M0-04 requires JDK 17.0.19+10, got ${jdkRuntimeVersion.get()}",
            )
        }
        val compiler =
            ToolProvider.getSystemJavaCompiler()
                ?: throw GradleException("M0-04 requires the pinned JDK, not a JRE")
        val classDirectory = temporaryDir.resolve("classes")
        val assetRoot = outputDirectory.get().asFile
        val dexDirectory = assetRoot.resolve("ah/poc")
        classDirectory.deleteRecursively()
        assetRoot.deleteRecursively()
        classDirectory.mkdirs()
        dexDirectory.mkdirs()

        val sourceFiles = sources.files.sortedBy(File::getPath)
        if (sourceFiles.isEmpty()) {
            throw GradleException("M0-04 payload sources are missing")
        }

        compiler.getStandardFileManager(null, null, Charsets.UTF_8).use { fileManager ->
            val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
            val options =
                listOf(
                    "-encoding",
                    "UTF-8",
                    "-source",
                    "17",
                    "-target",
                    "17",
                    "-classpath",
                    androidJar.get().asFile.absolutePath,
                    "-d",
                    classDirectory.absolutePath,
                )
            if (compiler.getTask(null, fileManager, null, options, null, units).call() != true) {
                throw GradleException("M0-04 payload javac failed")
            }
        }

        val classFiles =
            classDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .sortedBy(File::getPath)
                .toList()
        if (classFiles.size != 3) {
            throw GradleException(
                "M0-04 expected exactly three payload classes, found ${classFiles.size}",
            )
        }

        val d8Arguments =
            listOf(
                d8Executable.get().asFile.absolutePath,
                "--min-api",
                "29",
                "--lib",
                androidJar.get().asFile.absolutePath,
                "--output",
                dexDirectory.absolutePath,
            ) + classFiles.map(File::getAbsolutePath)
        execOperations
            .exec {
                if (System.getProperty("os.name").startsWith("Windows")) {
                    commandLine(listOf("cmd.exe", "/d", "/c") + d8Arguments)
                } else {
                    commandLine(d8Arguments)
                }
            }.assertNormalExitValue()

        val payload = dexDirectory.resolve("classes.dex")
        if (!payload.isFile || payload.length() < 112) {
            throw GradleException("M0-04 D8 output is missing or truncated")
        }
    }
}

@CacheableTask
abstract class GenerateCompatibilityPocPayload
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val d8Executable: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bootstrapClassesJar: RegularFileProperty

    @get:Input
    abstract val jdkRuntimeVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        if (jdkRuntimeVersion.get() != "17.0.19+10") {
            throw GradleException(
                "M0-05 requires JDK 17.0.19+10, got ${jdkRuntimeVersion.get()}",
            )
        }
        val compiler =
            ToolProvider.getSystemJavaCompiler()
                ?: throw GradleException("M0-05 requires the pinned JDK, not a JRE")
        val classDirectory = temporaryDir.resolve("compat-classes")
        val primaryDexDirectory = temporaryDir.resolve("primary-dex")
        val secondaryDexDirectory = temporaryDir.resolve("secondary-dex")
        val linkClassDirectory = temporaryDir.resolve("link-classes")
        val assetRoot = outputDirectory.get().asFile
        classDirectory.deleteRecursively()
        primaryDexDirectory.deleteRecursively()
        secondaryDexDirectory.deleteRecursively()
        linkClassDirectory.deleteRecursively()
        assetRoot.deleteRecursively()
        classDirectory.mkdirs()
        primaryDexDirectory.mkdirs()
        secondaryDexDirectory.mkdirs()
        linkClassDirectory.mkdirs()

        val sourceFiles = sources.files.sortedBy(File::getPath)
        if (sourceFiles.size != 9) {
            throw GradleException(
                "M0-05 expected nine payload/support sources, found ${sourceFiles.size}",
            )
        }
        compiler.getStandardFileManager(null, null, Charsets.UTF_8).use { fileManager ->
            val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
            val options =
                listOf(
                    "-encoding",
                    "UTF-8",
                    "-source",
                    "17",
                    "-target",
                    "17",
                    "-classpath",
                    androidJar.get().asFile.absolutePath
                        + File.pathSeparator
                        + bootstrapClassesJar.get().asFile.absolutePath,
                    "-d",
                    classDirectory.absolutePath,
                )
            if (compiler.getTask(null, fileManager, null, options, null, units).call() != true) {
                throw GradleException("M0-05 payload javac failed")
            }
        }

        val classFiles =
            classDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .sortedBy(File::getPath)
                .toList()
        val supportClass = classFiles.singleOrNull { it.name == "ProbeSignal.class" }
            ?: throw GradleException("M0-05 parent support class is missing")
        val secondaryClass = classFiles.singleOrNull { it.name == "SecondaryApi.class" }
            ?: throw GradleException("M0-05 classes2-only class is missing")
        val primaryClasses = classFiles.filter { it != supportClass && it != secondaryClass }
        if (primaryClasses.size != 7) {
            throw GradleException(
                "M0-05 expected seven primary payload classes, found ${primaryClasses.size}",
            )
        }

        for (linkClass in listOf(supportClass, secondaryClass)) {
            val relative = linkClass.relativeTo(classDirectory)
            val destination = linkClassDirectory.resolve(relative.path)
            destination.parentFile.mkdirs()
            linkClass.copyTo(destination, overwrite = true)
        }
        runD8(primaryClasses, primaryDexDirectory, linkClassDirectory)
        runD8(listOf(secondaryClass), secondaryDexDirectory, null)
        val primaryDex = primaryDexDirectory.resolve("classes.dex")
        val secondaryDex = secondaryDexDirectory.resolve("classes.dex")
        if (!primaryDex.isFile || !secondaryDex.isFile
            || primaryDex.length() < 112 || secondaryDex.length() < 112) {
            throw GradleException("M0-05 D8 output is missing or truncated")
        }

        val asset = assetRoot.resolve("ah/runtime/payload.ahdc")
        asset.parentFile.mkdirs()
        FileOutputStream(asset).use { output ->
            output.write(byteArrayOf('A'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'C'.code.toByte()))
            output.write(1)
            output.write(0)
            writeShortLe(output, 2)
            writeIntLe(output, primaryDex.length())
            writeIntLe(output, secondaryDex.length())
            primaryDex.inputStream().use { it.copyTo(output) }
            secondaryDex.inputStream().use { it.copyTo(output) }
        }
    }

    private fun runD8(classFiles: List<File>, output: File, classpath: File?) {
        val arguments =
            mutableListOf(
                d8Executable.get().asFile.absolutePath,
                "--min-api",
                "29",
                "--lib",
                androidJar.get().asFile.absolutePath,
                "--output",
                output.absolutePath,
            )
        if (classpath != null) {
            arguments += listOf("--classpath", classpath.absolutePath)
        }
        arguments += classFiles.map(File::getAbsolutePath)
        execOperations.exec {
            if (System.getProperty("os.name").startsWith("Windows")) {
                commandLine(listOf("cmd.exe", "/d", "/c") + arguments)
            } else {
                commandLine(arguments)
            }
        }.assertNormalExitValue()
    }

    private fun writeShortLe(output: FileOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeIntLe(output: FileOutputStream, value: Long) {
        output.write((value and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 24) and 0xff).toInt())
    }
}

android {
    namespace = "ah.fixtures.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        applicationId = "ah.fixtures.android"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "ah.fixtures.android.ClassLoaderPocRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    flavorDimensions += "poc"
    productFlavors {
        create("classloaderPoc") {
            dimension = "poc"
            applicationIdSuffix = ".classloaderpoc"
        }
        create("compatExtracted") {
            dimension = "poc"
            applicationIdSuffix = ".m005.extracted"
            testInstrumentationRunner = "ah.fixtures.android.CompatibilityPocRunner"
        }
        create("compatDirect") {
            dimension = "poc"
            applicationIdSuffix = ".m005.direct"
            testInstrumentationRunner = "ah.fixtures.android.CompatibilityPocRunner"
        }
    }

    androidResources {
        noCompress += setOf("dex", "ahdc")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.android.cmake.get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Synthetic fixture only: enables on-device R8 verification with the test APK.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("compatExtracted") {
            manifest.srcFile("src/compatFixture/AndroidManifest.xml")
            java.srcDir("src/compatFixture/java")
        }
        getByName("compatDirect") {
            manifest.srcFile("src/compatFixture/AndroidManifest.xml")
            java.srcDir("src/compatFixture/java")
        }
        getByName("androidTestCompatExtracted") {
            java.srcDir("src/androidTestCompatFixture/java")
        }
        getByName("androidTestCompatDirect") {
            java.srcDir("src/androidTestCompatFixture/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        checkDependencies = true
        disable += setOf(
            "GradleDependency", // M0-03 intentionally pins compileSdk 36.
            "MissingApplicationIcon", // The empty M0-03 fixture has no UI assets.
            "OldTargetApi", // M0-03 intentionally pins fixture targetSdk 36.
        )
        warningsAsErrors = true
    }
}

val androidHome =
    providers
        .environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?: throw GradleException("ANDROID_HOME or ANDROID_SDK_ROOT must identify the pinned SDK")
val compileSdkVersion = libs.versions.android.compile.sdk.get()
val buildToolsVersion = libs.versions.android.build.tools.get()
val androidJarFile = File(androidHome, "platforms/android-$compileSdkVersion/android.jar")
val d8Command = if (System.getProperty("os.name").startsWith("Windows")) "d8.bat" else "d8"
val d8ExecutableFile = File(androidHome, "build-tools/$buildToolsVersion/$d8Command")

androidComponents {
    onVariants(selector().withFlavor("poc" to "classloaderPoc")) { variant ->
        val taskName =
            "generate${variant.name.replaceFirstChar { character -> character.uppercase() }}PayloadDex"
        val generatePayload =
            tasks.register<GenerateClassloaderPocDex>(taskName) {
                sources.from(
                    layout.projectDirectory
                        .dir("src/classloaderPocPayload/java")
                        .asFileTree,
                )
                androidJar.set(androidJarFile)
                d8Executable.set(d8ExecutableFile)
                jdkRuntimeVersion.set(providers.systemProperty("java.runtime.version"))
                outputDirectory.set(
                    layout.buildDirectory.dir("generated/m0-04/${variant.name}/assets"),
                )
            }
        variant.sources.assets?.addGeneratedSourceDirectory(
            generatePayload,
            GenerateClassloaderPocDex::outputDirectory,
        )
    }
    onVariants(selector().withFlavor("poc" to "compatExtracted")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        registerCompatibilityPayload(variant)
    }
    onVariants(selector().withFlavor("poc" to "compatDirect")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
        registerCompatibilityPayload(variant)
    }
}

fun registerCompatibilityPayload(variant: com.android.build.api.variant.ApplicationVariant) {
    val taskName =
        "generate${variant.name.replaceFirstChar { character -> character.uppercase() }}Payload"
    val generatePayload =
        tasks.register<GenerateCompatibilityPocPayload>(taskName) {
            sources.from(
                layout.projectDirectory.dir("src/compatPayload/java").asFileTree,
                layout.projectDirectory
                    .file("src/compatFixture/java/ah/fixtures/android/ProbeSignal.java"),
            )
            androidJar.set(androidJarFile)
            d8Executable.set(d8ExecutableFile)
            bootstrapClassesJar.set(
                rootProject.layout.projectDirectory.file(
                    "runtime/bootstrap/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
                ),
            )
            dependsOn(":runtime:bootstrap:bundleLibCompileToJarDebug")
            jdkRuntimeVersion.set(providers.systemProperty("java.runtime.version"))
            outputDirectory.set(
                layout.buildDirectory.dir("generated/m0-05/${variant.name}/assets"),
            )
        }
    variant.sources.assets?.addGeneratedSourceDirectory(
        generatePayload,
        GenerateCompatibilityPocPayload::outputDirectory,
    )
}

dependencies {
    add("classloaderPocImplementation", project(":runtime:bootstrap"))
    add("compatExtractedImplementation", project(":runtime:bootstrap"))
    add("compatDirectImplementation", project(":runtime:bootstrap"))
}
