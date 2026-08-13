import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
}

val m204SupportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val m204TargetAbi = providers.gradleProperty("m204TargetAbi").orNull
if (m204TargetAbi != null && m204TargetAbi !in m204SupportedAbis) {
    throw GradleException("m204TargetAbi must be one of ${m204SupportedAbis.joinToString()}")
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
abstract class GenerateM202Placeholders : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val assetRoot = outputDirectory.get().asFile.resolve("ah/runtime")
        assetRoot.deleteRecursively()
        assetRoot.mkdirs()
        assetRoot.resolve("config.bin").writeBytes(ByteArray(768))
        assetRoot.resolve("payload.ahdc").writeBytes(ByteArray(160))
    }
}

@CacheableTask
abstract class GenerateM301SecondaryDex
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

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
            throw GradleException("M3-01 requires JDK 17.0.19+10, got ${jdkRuntimeVersion.get()}")
        }
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: throw GradleException("M3-01 requires the pinned JDK, not a JRE")
        val classes = temporaryDir.resolve("classes")
        val dex = outputDirectory.get().asFile
        classes.deleteRecursively()
        dex.deleteRecursively()
        classes.mkdirs()
        dex.mkdirs()
        compiler.getStandardFileManager(null, null, Charsets.UTF_8).use { manager ->
            val units = manager.getJavaFileObjects(source.get().asFile)
            val options = listOf(
                "-encoding", "UTF-8", "-source", "17", "-target", "17",
                "-classpath", androidJar.get().asFile.absolutePath,
                "-d", classes.absolutePath,
            )
            if (compiler.getTask(null, manager, null, options, null, units).call() != true) {
                throw GradleException("M3-01 secondary DEX javac failed")
            }
        }
        val classFiles = classes.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        if (classFiles.size != 1) throw GradleException("M3-01 expected one secondary class")
        val arguments = listOf(
            d8Executable.get().asFile.absolutePath,
            "--min-api", "29",
            "--lib", androidJar.get().asFile.absolutePath,
            "--output", dex.absolutePath,
            classFiles.single().absolutePath,
        )
        execOperations.exec {
            if (System.getProperty("os.name").startsWith("Windows")) {
                commandLine(listOf("cmd.exe", "/d", "/c") + arguments)
            } else {
                commandLine(arguments)
            }
        }.assertNormalExitValue()
        if (dex.resolve("classes.dex").length() < 112) {
            throw GradleException("M3-01 secondary DEX is missing or truncated")
        }
    }
}

@CacheableTask
abstract class AssembleM301Fixtures : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceApkDirectories: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val secondaryDex: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun assemble() {
        val outputRoot = outputDirectory.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()
        val specs = listOf(
            "m301JavaSingleDex" to "java-single-dex",
            "m301KotlinSingleDex" to "kotlin-single-dex",
            "m301KotlinMultidex" to "kotlin-multidex",
            "m301CustomApplication" to "custom-application",
            "m301CustomFactory" to "custom-factory",
            "m301StartupProvider" to "startup-provider",
            "m301MultiProcess" to "multi-process",
            "m301JniFourAbi" to "jni-four-abi",
            "m301JniArmOnly" to "jni-arm-only",
        )
        val sourceDirectories = sourceApkDirectories.files.associateBy { it.parentFile.name }
        specs.forEach { (flavor, id) ->
            val sourceDirectory = sourceDirectories[flavor]
                ?: throw GradleException("M3-01 source directory missing for $flavor")
            val candidates = sourceDirectory.listFiles { file -> file.isFile && file.extension == "apk" }?.toList().orEmpty()
            if (candidates.size != 1) {
                throw GradleException("M3-01 expected one $flavor Release APK, found ${candidates.size}")
            }
            val target = outputRoot.resolve("$id.apk")
            canonicalize(candidates.single(), target, id)
            verify(target, id)
            val rebuilt = temporaryDir.resolve("reproducibility/$id.apk")
            rebuilt.parentFile.mkdirs()
            canonicalize(candidates.single(), rebuilt, id)
            if (!target.readBytes().contentEquals(rebuilt.readBytes())) {
                throw GradleException("M3-01 $id unsigned fixture is not reproducible")
            }
        }
    }

    private fun canonicalize(source: File, target: File, id: String) {
        ZipFile(source).use { input ->
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { output ->
                output.setLevel(9)
                input.entries().asSequence()
                    .filterNot(ZipEntry::isDirectory)
                    .filter { entry -> !isSignatureEntry(entry.name) }
                    .filter { entry -> id != "kotlin-multidex" || entry.name != "classes2.dex" }
                    .filter { entry -> includeNativeEntry(id, entry.name) }
                    .sortedBy(ZipEntry::getName)
                    .forEach { entry ->
                        val content = input.getInputStream(entry).use { it.readBytes() }
                        val canonical = ZipEntry(entry.name)
                        canonical.time = 315_532_800_000L // 1980-01-01, representable without timestamp extras.
                        if (entry.name == "resources.arsc") {
                            val crc = CRC32().apply { update(content) }
                            canonical.method = ZipEntry.STORED
                            canonical.size = content.size.toLong()
                            canonical.compressedSize = content.size.toLong()
                            canonical.crc = crc.value
                            canonical.extra = zipAlignmentExtra(bytes.size(), entry.name)
                        }
                        output.putNextEntry(canonical)
                        output.write(content)
                        output.closeEntry()
                    }
                if (id == "kotlin-multidex") {
                    val secondary = ZipEntry("classes2.dex")
                    secondary.time = 315_532_800_000L
                    output.putNextEntry(secondary)
                    secondaryDex.get().asFile.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
            target.writeBytes(bytes.toByteArray())
        }
    }

    private fun zipAlignmentExtra(offset: Int, name: String): ByteArray? {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8).size
        val required = (4 - ((offset + 30 + nameBytes) and 3)) and 3
        if (required == 0) return null
        val extra = ByteArray(required + 4)
        extra[0] = 0xfe.toByte()
        extra[1] = 0xca.toByte()
        extra[2] = required.toByte()
        extra[3] = 0
        return extra
    }

    private fun isSignatureEntry(name: String): Boolean {
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && listOf(".SF", ".RSA", ".DSA", ".EC").any(upper::endsWith))
    }

    private fun includeNativeEntry(id: String, name: String): Boolean {
        if (!name.startsWith("lib/")) return true
        if (id != "jni-four-abi" && id != "jni-arm-only") return false
        if (!name.endsWith("/libfixture_jni.so")) return false
        return id != "jni-arm-only" || name.startsWith("lib/armeabi-v7a/") || name.startsWith("lib/arm64-v8a/")
    }

    private fun verify(apk: File, id: String) {
        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().filterNot(ZipEntry::isDirectory).map(ZipEntry::getName).toList()
            if (names.any(::isSignatureEntry)) throw GradleException("M3-01 $id retained signing metadata")
            val dexCount = names.count { it.matches(Regex("classes(?:[2-9][0-9]*)?\\.dex")) }
            if (id == "kotlin-multidex") {
                if (dexCount < 2) throw GradleException("M3-01 kotlin-multidex did not produce multiple DEX files")
            } else if (dexCount != 1) {
                throw GradleException("M3-01 $id expected one DEX, found $dexCount")
            }
            val abis = names.filter { it.startsWith("lib/") && it.endsWith("/libfixture_jni.so") }
                .map { it.split('/')[1] }.toSet()
            val expected = when (id) {
                "jni-four-abi" -> setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                "jni-arm-only" -> setOf("armeabi-v7a", "arm64-v8a")
                else -> emptySet()
            }
            if (abis != expected) throw GradleException("M3-01 $id ABI mismatch: $abis != $expected")
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

    @get:Input
    abstract val expectedSignerSha256Hex: Property<String>

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

        writeConfigV2(assetRoot)
    }

    private fun writeConfigV2(assetRoot: File) {
        val signerHex = expectedSignerSha256Hex.get().lowercase()
        if (!signerHex.matches(Regex("[0-9a-f]{64}"))) {
            throw GradleException(
                "M0-05 expected signer digest must be exactly 64 hexadecimal characters",
            )
        }
        val signer =
            ByteArray(32) { index ->
                signerHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        val factoryName =
            "ah.fixtures.android.payload.OriginalAppComponentFactory"
                .toByteArray(StandardCharsets.UTF_8)
        if (factoryName.isEmpty() || factoryName.size > 512) {
            throw GradleException("M0-05 original factory name exceeds ConfigV2 bounds")
        }

        val config = ByteBuffer.allocate(768).order(ByteOrder.LITTLE_ENDIAN)
        config.put("AHKC".toByteArray(StandardCharsets.US_ASCII))
        config.putShort(2.toShort())
        config.putShort(0.toShort())
        config.putShort(1.toShort()) // HAS_ORIGINAL_FACTORY
        config.putShort(0.toShort())
        config.putInt(768)
        config.putShort(1.toShort()) // AHDC major
        config.putShort(1.toShort()) // signer policy
        config.putShort(1.toShort()) // risk policy
        config.putShort(factoryName.size.toShort())
        repeat(16) { config.put((0x10 + it).toByte()) } // PoC build ID
        repeat(16) { config.put((0x30 + it).toByte()) } // PoC key slot ID
        config.put(signer)
        repeat(32) { config.put((0x50 + it).toByte()) } // Format-only PoC R_java
        repeat(12) { config.put((0x70 + it).toByte()) } // Format-only PoC nonce
        repeat(32) { config.put((0x80 + it).toByte()) } // Format-only wrapped bytes
        repeat(16) { config.put((0xa0 + it).toByte()) } // Format-only tag
        config.put(factoryName)
        config.position(768)

        val configAsset = assetRoot.resolve("ah/runtime/config.bin")
        configAsset.parentFile.mkdirs()
        configAsset.writeBytes(config.array())
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

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ah.fixtures.android"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "ah.fixtures.android.ClassLoaderPocRunner"
        val m005ExpectedSignerSha256 =
            providers.gradleProperty("m005ExpectedSignerSha256").orElse("0".repeat(64)).get()
        if (!m005ExpectedSignerSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw GradleException(
                "m005ExpectedSignerSha256 must be exactly 64 hexadecimal characters",
            )
        }
        buildConfigField(
            "String",
            "M005_EXPECTED_SIGNER_SHA256_HEX",
            "\"${m005ExpectedSignerSha256.lowercase()}\"",
        )

        ndk {
            abiFilters += m204TargetAbi?.let(::listOf) ?: listOf("arm64-v8a", "x86_64")
        }
    }

    val m005TestKeystore = providers.environmentVariable("M005_TEST_KEYSTORE").orNull
    val m005TestStorePassword = providers.environmentVariable("M005_TEST_STORE_PASSWORD").orNull
    val m005TestKeyAlias = providers.environmentVariable("M005_TEST_KEY_ALIAS").orNull
    val m005TestKeyPassword = providers.environmentVariable("M005_TEST_KEY_PASSWORD").orNull
    val m005SigningValues =
        listOf(
            m005TestKeystore,
            m005TestStorePassword,
            m005TestKeyAlias,
            m005TestKeyPassword,
        )
    if (m005SigningValues.any { it != null } && m005SigningValues.any { it == null }) {
        throw GradleException("all M005_TEST_* signing environment variables must be set together")
    }
    if (m005TestKeystore != null) {
        signingConfigs.getByName("debug") {
            storeFile = file(m005TestKeystore)
            storePassword = m005TestStorePassword
            keyAlias = m005TestKeyAlias
            keyPassword = m005TestKeyPassword
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
        create("m202Extracted") {
            dimension = "poc"
            applicationIdSuffix = ".m202.extracted"
            testInstrumentationRunner = "ah.runtime.loader.M202DeviceRunner"
        }
        create("m202Direct") {
            dimension = "poc"
            applicationIdSuffix = ".m202.direct"
            testInstrumentationRunner = "ah.runtime.loader.M202DeviceRunner"
        }
        create("m203Extracted") {
            dimension = "poc"
            applicationIdSuffix = ".m203.extracted"
            testInstrumentationRunner = "ah.runtime.guard.M203DeviceRunner"
        }
        create("m203Direct") {
            dimension = "poc"
            applicationIdSuffix = ".m203.direct"
            testInstrumentationRunner = "ah.runtime.guard.M203DeviceRunner"
        }
        create("m201Extracted") {
            dimension = "poc"
            applicationIdSuffix = ".m201.extracted"
            testInstrumentationRunner = "ah.fixtures.android.m201.M201DeviceRunner"
        }
        create("m201Direct") {
            dimension = "poc"
            applicationIdSuffix = ".m201.direct"
            testInstrumentationRunner = "ah.fixtures.android.m201.M201DeviceRunner"
        }
        val m301 = listOf(
            Triple("m301JavaSingleDex", "java-single-dex", ".m301.java_single"),
            Triple("m301KotlinSingleDex", "kotlin-single-dex", ".m301.kotlin_single"),
            Triple("m301KotlinMultidex", "kotlin-multidex", ".m301.kotlin_multidex"),
            Triple("m301CustomApplication", "custom-application", ".m301.custom_application"),
            Triple("m301CustomFactory", "custom-factory", ".m301.custom_factory"),
            Triple("m301StartupProvider", "startup-provider", ".m301.startup_provider"),
            Triple("m301MultiProcess", "multi-process", ".m301.multi_process"),
            Triple("m301JniFourAbi", "jni-four-abi", ".m301.jni_four"),
            Triple("m301JniArmOnly", "jni-arm-only", ".m301.jni_arm"),
        )
        m301.forEach { (name, id, suffix) ->
            create(name) {
                dimension = "poc"
                applicationIdSuffix = suffix
                ndk {
                    // Keep this literal so Android Lint can prove the synthetic fixture includes ChromeOS ABIs.
                    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                }
                buildConfigField("String", "FIXTURE_ID", "\"$id\"")
                buildConfigField("boolean", "M301_KOTLIN", (id.startsWith("kotlin-")).toString())
                buildConfigField("boolean", "M301_STARTUP_PROVIDER", (id == "startup-provider").toString())
                buildConfigField("boolean", "M301_MULTI_PROCESS", (id == "multi-process").toString())
                buildConfigField("boolean", "M301_JNI", (id.startsWith("jni-")).toString())
                manifestPlaceholders["fixtureApplication"] =
                    if (id == "custom-application") "ah.fixtures.android.m301.CustomFixtureApplication" else "android.app.Application"
                manifestPlaceholders["fixtureFactory"] =
                    if (id == "custom-factory") "ah.fixtures.android.m301.CustomFixtureFactory" else "android.app.AppComponentFactory"
                if (id == "kotlin-multidex") multiDexEnabled = true
            }
        }
    }

    androidResources {
        noCompress += setOf("dex", "ahdc", "bin")
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
        getByName("classloaderPoc") {
            java.srcDir("src/legacyBootstrap/java")
        }
        getByName("compatExtracted") {
            manifest.srcFile("src/compatFixture/AndroidManifest.xml")
            java.srcDir("src/compatFixture/java")
            java.srcDir("src/sharedCompatFixture/java")
            java.srcDir("src/legacyBootstrap/java")
        }
        getByName("compatDirect") {
            manifest.srcFile("src/compatFixture/AndroidManifest.xml")
            java.srcDir("src/compatFixture/java")
            java.srcDir("src/sharedCompatFixture/java")
            java.srcDir("src/legacyBootstrap/java")
        }
        getByName("androidTestCompatExtracted") {
            java.srcDir("src/androidTestCompatFixture/java")
        }
        getByName("androidTestCompatDirect") {
            java.srcDir("src/androidTestCompatFixture/java")
        }
        getByName("m202Extracted") {
            manifest.srcFile("src/m202Fixture/AndroidManifest.xml")
            java.srcDir("src/m202Fixture/java")
        }
        getByName("m202Direct") {
            manifest.srcFile("src/m202Fixture/AndroidManifest.xml")
            java.srcDir("src/m202Fixture/java")
        }
        getByName("androidTestM202Extracted") {
            java.srcDir("src/androidTestM202Fixture/java")
        }
        getByName("androidTestM202Direct") {
            java.srcDir("src/androidTestM202Fixture/java")
        }
        getByName("m203Extracted") {
            manifest.srcFile("src/m203Fixture/AndroidManifest.xml")
            java.srcDir("src/m203Fixture/java")
        }
        getByName("m203Direct") {
            manifest.srcFile("src/m203Fixture/AndroidManifest.xml")
            java.srcDir("src/m203Fixture/java")
        }
        getByName("m201Extracted") {
            manifest.srcFile("src/m201Fixture/AndroidManifest.xml")
            java.srcDir("src/sharedCompatFixture/java")
        }
        getByName("m201Direct") {
            manifest.srcFile("src/m201Fixture/AndroidManifest.xml")
            java.srcDir("src/sharedCompatFixture/java")
        }
        getByName("androidTestM201Extracted") {
            java.srcDir("src/androidTestM201Fixture/java")
        }
        getByName("androidTestM201Direct") {
            java.srcDir("src/androidTestM201Fixture/java")
        }
        getByName("androidTestM203Extracted") {
            java.srcDir("src/androidTestM203Fixture/java")
        }
        getByName("androidTestM203Direct") {
            java.srcDir("src/androidTestM203Fixture/java")
        }
        listOf(
            "m301JavaSingleDex",
            "m301KotlinSingleDex",
            "m301KotlinMultidex",
            "m301CustomApplication",
            "m301CustomFactory",
            "m301StartupProvider",
            "m301MultiProcess",
            "m301JniFourAbi",
            "m301JniArmOnly",
        ).forEach { flavor ->
            getByName(flavor) {
                manifest.srcFile("src/m301Common/AndroidManifest.xml")
                java.srcDir("src/m301Common/java")
            }
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
            "MissingClass", // Payload components are intentionally supplied by generated in-memory DEX fixtures.
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
    onVariants(selector().all()) { variant ->
        if (variant.productFlavors.any { (_, flavor) -> flavor.startsWith("m301") }) {
            variant.packaging.jniLibs.useLegacyPackaging.set(true)
        }
    }
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
    onVariants(selector().withFlavor("poc" to "m202Extracted")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        registerM202Placeholders(variant)
    }
    onVariants(selector().withFlavor("poc" to "m202Direct")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
        registerM202Placeholders(variant)
    }
    onVariants(selector().withFlavor("poc" to "m203Extracted")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        registerM202Placeholders(variant)
    }
    onVariants(selector().withFlavor("poc" to "m203Direct")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
        registerM202Placeholders(variant)
    }
    onVariants(selector().withFlavor("poc" to "m201Extracted")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        registerM202Placeholders(variant)
    }
    onVariants(selector().withFlavor("poc" to "m201Direct")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
        registerM202Placeholders(variant)
    }
}

val m301FixtureFlavors = listOf(
    "M301JavaSingleDex",
    "M301KotlinSingleDex",
    "M301KotlinMultidex",
    "M301CustomApplication",
    "M301CustomFactory",
    "M301StartupProvider",
    "M301MultiProcess",
    "M301JniFourAbi",
    "M301JniArmOnly",
)

val generateM301SecondaryDex = tasks.register<GenerateM301SecondaryDex>("generateM301SecondaryDex") {
    source.set(layout.projectDirectory.file("src/m301KotlinMultidex/secondary/SecondaryMarker.java"))
    androidJar.set(androidJarFile)
    d8Executable.set(d8ExecutableFile)
    jdkRuntimeVersion.set(providers.systemProperty("java.runtime.version"))
    outputDirectory.set(layout.buildDirectory.dir("generated/m3-01/secondary-dex"))
}

tasks.register<AssembleM301Fixtures>("assembleFixtures") {
    group = "verification"
    description = "Builds the nine deterministic unsigned M3-01 Android fixtures."
    dependsOn(m301FixtureFlavors.map { "assemble${it}Release" })
    sourceApkDirectories.from(m301FixtureFlavors.map { flavor ->
        layout.buildDirectory.dir("outputs/apk/${flavor.replaceFirstChar { it.lowercase() }}/release")
    })
    secondaryDex.set(generateM301SecondaryDex.flatMap { it.outputDirectory.file("classes.dex") })
    outputDirectory.set(layout.buildDirectory.dir("fixtures"))
}

fun registerM202Placeholders(variant: com.android.build.api.variant.ApplicationVariant) {
    val taskName =
        "generate${variant.name.replaceFirstChar { character -> character.uppercase() }}M202Placeholders"
    val generate =
        tasks.register<GenerateM202Placeholders>(taskName) {
            outputDirectory.set(
                layout.buildDirectory.dir("generated/m2-02/${variant.name}/assets"),
            )
        }
    variant.sources.assets?.addGeneratedSourceDirectory(
        generate,
        GenerateM202Placeholders::outputDirectory,
    )
}

fun registerCompatibilityPayload(variant: com.android.build.api.variant.ApplicationVariant) {
    val taskName =
        "generate${variant.name.replaceFirstChar { character -> character.uppercase() }}Payload"
    val generatePayload =
        tasks.register<GenerateCompatibilityPocPayload>(taskName) {
            sources.from(
                layout.projectDirectory.dir("src/compatPayload/java").asFileTree,
                layout.projectDirectory
                    .file("src/sharedCompatFixture/java/ah/fixtures/android/ProbeSignal.java"),
            )
            androidJar.set(androidJarFile)
            d8Executable.set(d8ExecutableFile)
            bootstrapClassesJar.set(
                rootProject.layout.projectDirectory.file(
                    "runtime/bootstrap/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
                ),
            )
            expectedSignerSha256Hex.set(
                providers.gradleProperty("m005ExpectedSignerSha256").orElse("0".repeat(64)),
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
    add("compatExtractedImplementation", libs.android.apksig)
    add("compatDirectImplementation", libs.android.apksig)
    add("m202ExtractedImplementation", project(":runtime:native"))
    add("m202DirectImplementation", project(":runtime:native"))
    add("m203ExtractedImplementation", project(":runtime:policy"))
    add("m203DirectImplementation", project(":runtime:policy"))
    add("m201ExtractedImplementation", project(":runtime:bootstrap"))
    add("m201DirectImplementation", project(":runtime:bootstrap"))
    add("androidTestCompileOnly", project(":runtime:native"))
    add("androidTestCompileOnly", project(":runtime:policy"))
    add("m301KotlinSingleDexImplementation", "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    add("m301KotlinMultidexImplementation", "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
}
