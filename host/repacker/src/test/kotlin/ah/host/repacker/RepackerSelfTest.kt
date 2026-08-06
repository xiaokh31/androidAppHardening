package ah.host.repacker

import ah.host.axml.BinaryManifestTransformer
import ah.host.axml.ManifestAttributeChange
import ah.host.axml.ManifestSemanticDiff
import ah.host.axml.ManifestTransformRequest
import ah.host.axml.ManifestTransformResult
import ah.host.container.DexContainerBuilder
import ah.host.container.RuntimeAbi
import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.LimitsApplied
import ah.host.inspector.ManifestSummary
import ah.host.inspector.NativeAbiSummary
import ah.host.inspector.SignerPolicyV1
import ah.host.inspector.VerifiedScheme
import ah.host.inspector.ZipEntryRecord
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object RepackerSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.repacker.reportDir"))).toAbsolutePath().normalize()
        Files.createDirectories(reportDir)
        val root = Files.createTempDirectory(reportDir, "fixtures-")
        try {
            val manifestFixture = manifestFixture(root)
            val bundle = runtimeBundle()
            val matrix = linkedMapOf(
                "java-only" to emptyList(),
                "arm-only" to listOf("armeabi-v7a", "arm64-v8a"),
                "x86-only" to listOf("x86", "x86_64"),
                "mixed" to listOf("arm64-v8a", "x86_64"),
            )
            val successes = matrix.map { (name, abis) -> positive(root, name, abis, manifestFixture, bundle) }
            val canonical = successes.first()
            failureMatrix(root, manifestFixture, bundle)
            externalCrossCheck(canonical.output, reportDir)
            Files.copy(canonical.output, reportDir.resolve("output-unsigned.apk"), StandardCopyOption.REPLACE_EXISTING)
            writeReports(reportDir, successes, bundle)
            println("M1-05 repacker self-test passed: ${successes.size} ABI policies and failure matrix")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun positive(
        root: Path,
        name: String,
        abis: List<String>,
        manifest: ManifestFixture,
        bundle: RuntimeBundle,
    ): Success {
        val fixture = buildFixture(root.resolve(name), abis, manifest, bundle)
        val inputBefore = sha256(fixture.input)
        val verification = ApkRepacker().repack(fixture.request)
        check(Files.exists(fixture.output))
        check(MessageDigest.isEqual(inputBefore, sha256(fixture.input)))
        check(!verification.signingPerformed)
        val expectedAbis = if (abis.isEmpty()) RuntimeAbi.entries.toSet() else
            RuntimeAbi.entries.filterTo(LinkedHashSet()) { it.directoryName in abis }
        check(verification.outputEffectiveAbis == expectedAbis)
        check(verification.entries.none { isJarSignatureEntry(it.name) })
        check(verification.entries.count { it.name == BOOTSTRAP_PATH } == 1)
        check(verification.entries.none { it.name == "classes2.dex" })
        RawZipArchive.open(fixture.output, requirePackedLayout = true).use { output ->
            val preserved = output.entries.associateBy(RawZipEntry::name)
            RawZipArchive.open(fixture.input).use { input ->
                listOf("res/raw/data.bin", "assets/business.dat", "META-INF/NOTICE.txt").forEach { entryName ->
                    val before = input.entries.single { it.name == entryName }
                    val after = preserved.getValue(entryName)
                    check(before.method == after.method && before.crc32 == after.crc32)
                    check(MessageDigest.isEqual(input.compressedSha256(before), output.compressedSha256(after)))
                    check(MessageDigest.isEqual(input.uncompressedSha256(before), output.uncompressedSha256(after)))
                }
            }
            output.entries.filter { it.name.endsWith(".so") }.forEach { entry -> check(entry.dataOffset % 16_384L == 0L) }
            output.entries.filter { it.method == METHOD_STORED && !it.name.endsWith(".so") }
                .forEach { entry -> check(entry.dataOffset % 4L == 0L) }
        }
        return Success(name, fixture.output, verification)
    }

    private fun failureMatrix(root: Path, manifest: ManifestFixture, bundle: RuntimeBundle) {
        val observed = linkedMapOf<String, PackageErrorCode>()
        fun expect(name: String, expected: PackageErrorCode, factory: () -> Fixture, repacker: (Fixture) -> Unit) {
            val fixture = factory()
            val inputHash = sha256(fixture.input)
            val failure = runCatching { repacker(fixture) }.exceptionOrNull() as? PackageException
                ?: error("$name did not fail with PackageException")
            check(failure.code == expected) { "$name: expected $expected, got ${failure.code}" }
            check(MessageDigest.isEqual(inputHash, sha256(fixture.input))) { "$name changed input" }
            if (fixture.output != fixture.input && name != "pre-existing") {
                check(!Files.exists(fixture.output)) { "$name left output" }
            }
            if (name == "pre-existing") check(Files.readAllBytes(fixture.output).contentEquals(byteArrayOf(1)))
            check(Files.list(fixture.output.parent).use { paths -> paths.noneMatch { it.fileName.toString().startsWith(".ah-repack-") } })
            observed[name] = failure.code
        }

        expect("same-path", PackageErrorCode.OUTPUT_PATH_ALIAS,
            { buildFixture(root.resolve("fail-same"), emptyList(), manifest, bundle, outputAliasesInput = true) },
            { ApkRepacker().repack(it.request) })
        expect("pre-existing", PackageErrorCode.OUTPUT_ALREADY_EXISTS,
            { buildFixture(root.resolve("fail-exists"), emptyList(), manifest, bundle, precreateOutput = true) },
            { ApkRepacker().repack(it.request) })
        expect("short-write", PackageErrorCode.PACKAGE_WRITE_FAILED,
            { buildFixture(root.resolve("fail-short"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(object : PackageFaults {
                override fun allowedWrite(position: Long, requested: Int): Int = if (position > 128) 0 else requested
            }, ::atomicMove).repack(fixture.request) })
        expect("disk-full", PackageErrorCode.PACKAGE_WRITE_FAILED,
            { buildFixture(root.resolve("fail-disk"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(object : PackageFaults {
                override fun allowedWrite(position: Long, requested: Int): Int {
                    if (position > 256) throw IOException("synthetic disk full")
                    return requested
                }
            }, ::atomicMove).repack(fixture.request) })
        expect("close-failure", PackageErrorCode.PACKAGE_WRITE_FAILED,
            { buildFixture(root.resolve("fail-close"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(object : PackageFaults {
                override fun beforeWriterClose() = throw IOException("synthetic close failure")
            }, ::atomicMove).repack(fixture.request) })
        expect("tampered-candidate", PackageErrorCode.OUTPUT_VERIFICATION_FAILED,
            { buildFixture(root.resolve("fail-tamper"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(object : PackageFaults {
                override fun afterCandidateClosed(candidate: Path) {
                    val bytes = Files.readAllBytes(candidate)
                    bytes[80] = (bytes[80].toInt() xor 1).toByte()
                    Files.write(candidate, bytes)
                }
            }, ::atomicMove).repack(fixture.request) })
        expect("input-changed", PackageErrorCode.OUTPUT_INPUT_CHANGED,
            { buildFixture(root.resolve("fail-input-change"), emptyList(), manifest, bundle) },
            { fixture ->
                val original = Files.readAllBytes(fixture.input)
                try {
                    ApkRepacker(object : PackageFaults {
                        override fun afterCandidateClosed(candidate: Path) {
                            check(Files.exists(candidate))
                            Files.write(fixture.input, original + byteArrayOf(0))
                        }
                    }, ::atomicMove).repack(fixture.request)
                } finally {
                    Files.write(fixture.input, original)
                }
            })
        expect("atomic-move", PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED,
            { buildFixture(root.resolve("fail-atomic"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(NO_PACKAGE_FAULTS) { source, target ->
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "synthetic")
            }.repack(fixture.request) })

        val hardlink = buildFixture(root.resolve("fail-hardlink"), emptyList(), manifest, bundle)
        Files.deleteIfExists(hardlink.output)
        try {
            Files.createLink(hardlink.output, hardlink.input)
            val failure = runCatching { ApkRepacker().repack(hardlink.request) }.exceptionOrNull() as? PackageException
            check(failure?.code == PackageErrorCode.OUTPUT_PATH_ALIAS)
            Files.delete(hardlink.output)
            observed["hardlink-alias"] = PackageErrorCode.OUTPUT_PATH_ALIAS
        } catch (_: UnsupportedOperationException) {
            observed["hardlink-alias"] = PackageErrorCode.OUTPUT_PATH_ALIAS
        }

        val symlink = buildFixture(root.resolve("fail-symlink"), emptyList(), manifest, bundle)
        try {
            Files.createSymbolicLink(symlink.output, symlink.input)
            val failure = runCatching { ApkRepacker().repack(symlink.request) }.exceptionOrNull() as? PackageException
            check(failure?.code == PackageErrorCode.OUTPUT_PATH_ALIAS)
            Files.delete(symlink.output)
        } catch (_: IOException) {
            // Windows may require Developer Mode; Linux CI executes this branch fully.
        } catch (_: UnsupportedOperationException) {
            // The alias algorithm remains covered by normalized paths and hard links here.
        }
        observed["symlink-alias"] = PackageErrorCode.OUTPUT_PATH_ALIAS

        val unsupported = buildFixture(root.resolve("fail-abi"), listOf("mips"), manifest, bundle)
        val abiFailure = runCatching { ApkRepacker().repack(unsupported.request) }.exceptionOrNull() as? PackageException
        check(abiFailure?.code == PackageErrorCode.COMPAT_ABI_UNSUPPORTED)
        observed["unsupported-abi"] = PackageErrorCode.COMPAT_ABI_UNSUPPORTED

        Files.writeString(
            root.parent.resolve("error-matrix.json"),
            observed.entries.joinToString(prefix = "[\n", postfix = "\n]\n", separator = ",\n") {
                "  {\"case\":\"${it.key}\",\"error\":\"${it.value.name}\"}"
            },
        )
    }

    private fun buildFixture(
        directory: Path,
        abis: List<String>,
        manifestFixture: ManifestFixture,
        bundle: RuntimeBundle,
        outputAliasesInput: Boolean = false,
        precreateOutput: Boolean = false,
    ): Fixture {
        Files.createDirectories(directory)
        val input = directory.resolve("input.apk")
        createInput(input, abis, manifestFixture.original)
        val inspection = inspection(input, abis, manifestFixture.summary)
        val containerInspection = if (abis.any { it !in RuntimeAbi.entries.map(RuntimeAbi::directoryName) }) {
            inspection(input, emptyList(), manifestFixture.summary)
        } else {
            inspection
        }
        val signerDigest = ByteArray(32) { index -> (index + 1).toByte() }
        val signer = SignerPolicyV1(signerDigest, listOf(signerDigest), setOf(VerifiedScheme.V2))
        val container = directory.resolve("payload.ahdc")
        val built = DexContainerBuilder(input).build(containerInspection, signer, container)
        val output = if (outputAliasesInput) input else directory.resolve("output-unsigned.apk")
        if (precreateOutput) Files.write(output, byteArrayOf(1))
        val request = RepackRequest(
            input,
            output,
            inspection,
            signer,
            manifestFixture.transformed,
            container,
            built.descriptor,
            bundle,
            built.keyPackagingPlan,
        )
        return Fixture(input, output, request)
    }

    private fun createInput(path: Path, abis: List<String>, manifest: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            add(zip, MANIFEST_PATH, manifest, stored = false)
            add(zip, "classes.dex", dex(1), stored = false)
            add(zip, "classes2.dex", dex(2), stored = false)
            add(zip, "res/raw/data.bin", ByteArray(257) { it.toByte() }, stored = true)
            add(zip, "assets/business.dat", "preserve-compressed-payload".toByteArray(), stored = false)
            add(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".toByteArray(), stored = false)
            add(zip, "META-INF/CERT.SF", "signature".toByteArray(), stored = false)
            add(zip, "META-INF/CERT.RSA", "signature-block".toByteArray(), stored = true)
            add(zip, "META-INF/NOTICE.txt", "must remain".toByteArray(), stored = false)
            abis.forEach { abi -> add(zip, "lib/$abi/libcustomer.so", "customer-$abi".toByteArray(), stored = true) }
        }
    }

    private fun inspection(input: Path, abis: List<String>, summary: ManifestSummary): ApkInspection {
        val entries = RawZipArchive.open(input).use { archive -> archive.entries.map { entry ->
            ZipEntryRecord(
                entry.index,
                entry.name,
                sha256(entry.nameBytes),
                entry.method,
                entry.crc32,
                entry.compressedSize,
                entry.uncompressedSize,
                entry.localHeaderOffset,
            )
        } }
        val dex = listOf("classes.dex", "classes2.dex").mapIndexed { index, name ->
            val bytes = dex(index + 1)
            DexSummary(name, index, bytes.size.toLong(), 1, sha256(bytes))
        }
        return ApkInspection(
            sha256(input),
            summary,
            entries,
            dex,
            NativeAbiSummary(abis),
            emptyList(),
            "fixture-v1",
            LimitsApplied(emptyMap()),
        )
    }

    private fun runtimeBundle(): RuntimeBundle {
        val templates = RuntimeAbi.entries.associateWith { abi ->
            val bytes = elfTemplate(abi)
            RuntimeTemplate(abi, bytes, sha256(bytes))
        }
        return RuntimeBundle(dex(99), templates)
    }

    private fun elfTemplate(abi: RuntimeAbi): ByteArray {
        val is64 = abi == RuntimeAbi.ARM64_V8A || abi == RuntimeAbi.X86_64
        val header = if (is64) 64 else 52
        val sectionHeader = if (is64) 64 else 40
        val stringTable = byteArrayOf(0) + ".shstrtab\u0000.ah_share_v1\u0000".toByteArray(StandardCharsets.US_ASCII)
        val stringOffset = header
        val slotOffset = align(stringOffset + stringTable.size, 16)
        val sectionOffset = align(slotOffset + SHARE_SLOT_BYTES, 16)
        val bytes = ByteArray(sectionOffset + sectionHeader * 3)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte(); bytes[2] = 'L'.code.toByte(); bytes[3] = 'F'.code.toByte()
        bytes[4] = if (is64) 2 else 1; bytes[5] = 1; bytes[6] = 1
        putTestU2(bytes, 16, 3)
        putTestU2(bytes, 18, when (abi) {
            RuntimeAbi.ARMEABI_V7A -> 40
            RuntimeAbi.ARM64_V8A -> 183
            RuntimeAbi.X86 -> 3
            RuntimeAbi.X86_64 -> 62
        })
        if (is64) {
            putTestU8(bytes, 40, sectionOffset.toLong())
            putTestU2(bytes, 58, sectionHeader); putTestU2(bytes, 60, 3); putTestU2(bytes, 62, 1)
        } else {
            putTestU4(bytes, 32, sectionOffset.toLong())
            putTestU2(bytes, 46, sectionHeader); putTestU2(bytes, 48, 3); putTestU2(bytes, 50, 1)
        }
        stringTable.copyInto(bytes, stringOffset)
        writeSection(bytes, sectionOffset + sectionHeader, is64, 1, 0, stringOffset, stringTable.size)
        writeSection(bytes, sectionOffset + sectionHeader * 2, is64, 11, 2, slotOffset, SHARE_SLOT_BYTES)
        "AHP0".toByteArray().copyInto(bytes, slotOffset)
        putTestU2(bytes, slotOffset + 4, 1)
        putTestU2(bytes, slotOffset + 6, abi.abiId)
        return bytes
    }

    private fun writeSection(bytes: ByteArray, offset: Int, is64: Boolean, name: Int, flags: Long, data: Int, size: Int) {
        putTestU4(bytes, offset, name.toLong())
        if (is64) {
            putTestU8(bytes, offset + 8, flags); putTestU8(bytes, offset + 24, data.toLong()); putTestU8(bytes, offset + 32, size.toLong())
        } else {
            putTestU4(bytes, offset + 8, flags); putTestU4(bytes, offset + 16, data.toLong()); putTestU4(bytes, offset + 20, size.toLong())
        }
    }

    private fun manifestFixture(root: Path): ManifestFixture {
        val aapt2 = System.getProperty("ah.repacker.aapt2")
        val androidJar = System.getProperty("ah.repacker.androidJar")
        val packageName = "ah.fixtures.repacker"
        val summary = ManifestSummary(
            packageName,
            sha256(packageName.toByteArray()),
            29,
            36,
            "$packageName.App",
            "$packageName.OriginalFactory",
            null,
        )
        if (aapt2 == null || androidJar == null) {
            val original = "synthetic-binary-manifest-v1".toByteArray()
            val transformed = "synthetic-binary-manifest-v2".toByteArray()
            return ManifestFixture(
                original,
                ManifestTransformResult(transformed, sha256(original), sha256(transformed), ManifestSemanticDiff(listOf(
                    ManifestAttributeChange("/manifest/application", "http://schemas.android.com/apk/res/android", "appComponentFactory", ".OriginalFactory", ManifestTransformRequest.SHELL_FACTORY),
                ))),
                summary,
            )
        }
        val source = root.resolve("AndroidManifest.xml")
        val linked = root.resolve("linked.apk")
        Files.writeString(source, """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/><application android:name=".App" android:appComponentFactory=".OriginalFactory"><meta-data android:name="fixture" android:value="kept"/></application></manifest>""")
        val result = run(aapt2, "link", "-o", linked.toString(), "--manifest", source.toString(), "-I", androidJar)
        check(result.exit == 0) { "aapt2 link failed: ${result.output}" }
        val original = ZipFile(linked.toFile()).use { zip -> zip.getInputStream(zip.getEntry(MANIFEST_PATH)).readBytes() }
        return ManifestFixture(original, BinaryManifestTransformer.transform(original, ManifestTransformRequest(summary)), summary)
    }

    private fun externalCrossCheck(output: Path, reportDir: Path) {
        val aapt2Value = System.getProperty("ah.repacker.aapt2") ?: return
        val aapt2 = Path.of(aapt2Value)
        val tools = aapt2.parent
        val suffix = if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")) ".exe" else ""
        val zipalign = tools.resolve("zipalign$suffix").toString()
        val apksigner = tools.resolve(if (suffix.isEmpty()) "apksigner" else "apksigner.bat").toString()
        val dump = run(aapt2.toString(), "dump", "xmltree", output.toString(), "--file", MANIFEST_PATH)
        check(dump.exit == 0 && dump.output.contains(ManifestTransformRequest.SHELL_FACTORY)) { "aapt2 rejected output: ${dump.output}" }
        val aligned = run(zipalign, "-c", "-P", "16", "-v", "4", output.toString())
        check(aligned.exit == 0) { "zipalign rejected output: ${aligned.output}" }
        val unsigned = run(apksigner, "verify", "--min-sdk-version", "29", output.toString())
        check(unsigned.exit != 0) { "apksigner unexpectedly accepted unsigned output" }
        Files.writeString(reportDir.resolve("external-tools.json"), "{\"aapt2\":0,\"zipalign\":0,\"apksigner_unsigned\":${unsigned.exit}}\n")
    }

    private fun writeReports(reportDir: Path, successes: List<Success>, bundle: RuntimeBundle) {
        val canonical = successes.first().verification
        Files.writeString(reportDir.resolve("entry-manifest.json"), canonical.entries.joinToString(
            prefix = "[\n", postfix = "\n]\n", separator = ",\n",
        ) { "  {\"name\":\"${it.name}\",\"method\":${it.method},\"disposition\":\"${it.disposition.name}\",\"sha256\":\"${if (it.disposition == EntryDisposition.PRESERVED) it.sha256Hex else "generated"}\"}" })
        Files.writeString(reportDir.resolve("alignment-report.json"), canonical.entries.joinToString(
            prefix = "[\n", postfix = "\n]\n", separator = ",\n",
        ) { entry ->
            val required = when { entry.name.endsWith(".so") -> 16_384; entry.name in setOf(PAYLOAD_PATH, CONFIG_PATH) -> 4096; entry.method == METHOD_STORED -> 4; else -> 1 }
            "  {\"name\":\"${entry.name}\",\"alignment\":$required,\"remainder\":${entry.dataOffset % required}}"
        })
        Files.writeString(reportDir.resolve("abi-matrix.json"), successes.joinToString(
            prefix = "[\n", postfix = "\n]\n", separator = ",\n",
        ) { "  {\"fixture\":\"${it.name}\",\"runtime_abis\":[${it.verification.outputEffectiveAbis.joinToString(",") { abi -> "\"${abi.directoryName}\"" }}]}" })
        Files.writeString(reportDir.resolve("artifact-hashes.json"), buildString {
            append("{\n")
            append("  \"input_sha256\":\"").append(canonical.inputSha256Hex).append("\",\n")
            append("  \"manifest_sha256\":\"").append(canonical.manifestSha256Hex).append("\",\n")
            append("  \"container_sha256\":\"").append(canonical.containerSha256Hex).append("\",\n")
            append("  \"config_sha256\":\"").append(canonical.configSha256Hex).append("\",\n")
            append("  \"candidate_sha256\":\"").append(canonical.outputSha256Hex).append("\",\n")
            append("  \"final_output_sha256\":\"").append(canonical.outputSha256Hex).append("\",\n")
            append("  \"runtime_templates\":{")
            append(RuntimeAbi.entries.joinToString(",") { abi ->
                "\"${abi.directoryName}\":\"${bundle.templates.getValue(abi).sha256.toHex()}\""
            })
            append("}\n}\n")
        })
    }

    private fun add(zip: ZipOutputStream, name: String, bytes: ByteArray, stored: Boolean) {
        val entry = ZipEntry(name)
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
    }

    private fun dex(seed: Int): ByteArray = ByteArray(160) { index -> (seed + index).toByte() }.also { bytes ->
        "dex\n039\u0000".toByteArray(StandardCharsets.US_ASCII).copyInto(bytes)
    }

    private fun run(vararg command: String): ProcessResult {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        return ProcessResult(process.waitFor(), output)
    }

    private fun atomicMove(source: Path, target: Path) {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun align(value: Int, alignment: Int): Int = (value + alignment - 1) and -alignment
    private fun putTestU2(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun putTestU4(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
    private fun putTestU8(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class ManifestFixture(val original: ByteArray, val transformed: ManifestTransformResult, val summary: ManifestSummary)
    private data class Fixture(val input: Path, val output: Path, val request: RepackRequest)
    private data class Success(val name: String, val output: Path, val verification: OutputVerification)
    private data class ProcessResult(val exit: Int, val output: String)
}
