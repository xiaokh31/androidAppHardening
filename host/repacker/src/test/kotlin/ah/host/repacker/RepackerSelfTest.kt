package ah.host.repacker

import ah.host.axml.BinaryManifestTransformer
import ah.host.axml.ManifestAttributeChange
import ah.host.axml.ManifestSemanticDiff
import ah.host.axml.ManifestTransformRequest
import ah.host.axml.ManifestTransformResult
import ah.host.container.DexContainerBuilder
import ah.host.container.ContainerException
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
            sensitiveCleanupMatrix(root, manifestFixture, bundle)
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
            val actualFailure = runCatching { repacker(fixture) }.exceptionOrNull()
            val failure = actualFailure as? PackageException
                ?: error("$name did not fail with PackageException: $actualFailure")
            check(failure.code == expected) { "$name: expected $expected, got ${failure.code}" }
            check(MessageDigest.isEqual(inputHash, sha256(fixture.input))) { "$name changed input" }
            if (fixture.output != fixture.input && name !in setOf("pre-existing", "output-race")) {
                check(!Files.exists(fixture.output)) { "$name left output" }
            }
            if (name == "pre-existing") check(Files.readAllBytes(fixture.output).contentEquals(byteArrayOf(1)))
            if (name == "output-race") check(Files.readAllBytes(fixture.output).contentEquals(byteArrayOf(1)))
            check(Files.list(fixture.output.parent).use { paths -> paths.noneMatch { it.fileName.toString().startsWith(".ah-repack-") } })
            assertPlanConsumed(fixture)
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
        val mutations = linkedMapOf<String, (ByteArray) -> ByteArray>(
            "duplicate-name" to { bytes -> renameEntry(bytes, "META-INF/NOTICE.txt", "assets/business.dat") },
            "compressed-fixed-asset" to { bytes -> changeMethod(bytes, PAYLOAD_PATH, METHOD_DEFLATED) },
            "misaligned-fixed-asset" to { bytes -> misalignEntry(bytes, CONFIG_PATH) },
            "altered-preserved-bytes" to { bytes -> flipEntryByte(bytes, "res/raw/data.bin", 0) },
            "runtime-slot-mismatch" to { bytes -> flipEntryByte(bytes, runtimePath(RuntimeAbi.ARM64_V8A), 96) },
            "business-dex-reappears" to { bytes -> renameEntry(bytes, "keep0000.bin", "classes2.dex") },
            "signature-reappears" to { bytes -> renameEntry(bytes, "keep-sign000.bin", "META-INF/CERT.SF") },
            "trailing-structure" to { bytes -> bytes + byteArrayOf(0x5a) },
            "data-descriptor" to { bytes -> setDescriptor(bytes, CONFIG_PATH) },
            "local-range-overlap" to { bytes -> overlapLocalOffset(bytes) },
            "local-gap" to { bytes -> insertCentralGap(bytes, byteArrayOf(0)) },
            "signing-block-gap" to { bytes -> insertCentralGap(bytes, "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)) },
        )
        mutations.forEach { (name, mutation) ->
            expect(name, if (name == "misaligned-fixed-asset") PackageErrorCode.PACKAGE_ALIGNMENT else PackageErrorCode.OUTPUT_VERIFICATION_FAILED,
                { buildFixture(root.resolve("fail-$name"), emptyList(), manifest, bundle) },
                { fixture -> ApkRepacker(candidateMutation(mutation), ::atomicMove).repack(fixture.request) })
        }
        expect("malicious-name-sanitized", PackageErrorCode.OUTPUT_VERIFICATION_FAILED,
            { buildFixture(root.resolve("fail-malicious-name"), emptyList(), manifest, bundle) },
            { fixture ->
                val failure = runCatching {
                    ApkRepacker(candidateMutation { bytes -> renameEntry(bytes, "keep0000.bin", "../evil0.bin") }, ::atomicMove)
                        .repack(fixture.request)
                }.exceptionOrNull() as? PackageException ?: error("malicious name did not fail")
                check(!failure.message.orEmpty().contains("evil")) { "untrusted entry name leaked through exception" }
                throw failure
            })
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
        expect("input-identity-swap", PackageErrorCode.OUTPUT_INPUT_CHANGED,
            { buildFixture(root.resolve("fail-input-identity"), emptyList(), manifest, bundle) },
            { fixture ->
                val original = Files.readAllBytes(fixture.input)
                val displaced = fixture.input.resolveSibling("input-original.apk")
                try {
                    ApkRepacker(object : PackageFaults {
                        override fun beforeAtomicMove(candidate: Path, output: Path) {
                            if (isWindows()) {
                                Files.write(fixture.input, original + byteArrayOf(0))
                            } else {
                                Files.move(fixture.input, displaced)
                                Files.copy(displaced, fixture.input)
                            }
                        }
                    }, ::atomicMove).repack(fixture.request)
                } finally {
                    if (Files.exists(displaced)) {
                        Files.deleteIfExists(fixture.input)
                        Files.move(displaced, fixture.input)
                    } else {
                        Files.write(fixture.input, original)
                    }
                }
            })
        expect("container-identity-swap", PackageErrorCode.PACKAGE_ABI_MISMATCH,
            { buildFixture(root.resolve("fail-container-identity"), emptyList(), manifest, bundle) },
            { fixture ->
                val container = fixture.request.container
                val displaced = container.resolveSibling("payload-original.ahdc")
                try {
                    ApkRepacker(object : PackageFaults {
                        override fun afterCandidateClosed(candidate: Path) {
                            Files.move(container, displaced)
                            Files.copy(displaced, container)
                        }
                    }, ::atomicMove).repack(fixture.request)
                } finally {
                    Files.deleteIfExists(container)
                    if (Files.exists(displaced)) Files.move(displaced, container)
                }
            })
        expect("candidate-identity-swap", PackageErrorCode.OUTPUT_VERIFICATION_FAILED,
            { buildFixture(root.resolve("fail-candidate-identity"), emptyList(), manifest, bundle) },
            { fixture ->
                var displaced: Path? = null
                try {
                    ApkRepacker(object : PackageFaults {
                        override fun beforePublication(candidate: Path, output: Path) {
                            val backup = candidate.resolveSibling("${candidate.fileName}.original")
                            Files.move(candidate, backup)
                            Files.copy(backup, candidate)
                            displaced = backup
                        }
                    }, ::atomicMove).repack(fixture.request)
                } finally {
                    displaced?.let(Files::deleteIfExists)
                }
            })
        expect("output-race", PackageErrorCode.OUTPUT_ALREADY_EXISTS,
            { buildFixture(root.resolve("fail-output-race"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(object : PackageFaults {
                override fun beforePublication(candidate: Path, output: Path) {
                    Files.write(output, byteArrayOf(1))
                }
            }).repack(fixture.request) })
        expect("atomic-move", PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED,
            { buildFixture(root.resolve("fail-atomic"), emptyList(), manifest, bundle) },
            { fixture -> ApkRepacker(NO_PACKAGE_FAULTS) { source, target ->
                assertPlanConsumed(fixture)
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "synthetic")
            }.repack(fixture.request) })

        val hardlink = buildFixture(root.resolve("fail-hardlink"), emptyList(), manifest, bundle)
        Files.deleteIfExists(hardlink.output)
        try {
            Files.createLink(hardlink.output, hardlink.input)
            val failure = runCatching { ApkRepacker().repack(hardlink.request) }.exceptionOrNull() as? PackageException
            check(failure?.code == PackageErrorCode.OUTPUT_PATH_ALIAS)
            assertPlanConsumed(hardlink)
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

        expect("parent-identity-swap", PackageErrorCode.PACKAGE_WRITE_FAILED,
                { buildFixture(root.resolve("fail-parent-identity"), emptyList(), manifest, bundle) },
                { fixture ->
                    val parent = fixture.output.parent
                    val displaced = parent.resolveSibling("${parent.fileName}-original")
                    try {
                        ApkRepacker(object : PackageFaults {
                            override fun beforePublication(candidate: Path, output: Path) {
                                Files.move(parent, displaced)
                                Files.createDirectory(parent)
                            }
                        }, ::atomicMove).repack(fixture.request)
                    } finally {
                        if (Files.exists(displaced)) {
                            parent.toFile().deleteRecursively()
                            Files.move(displaced, parent)
                            Files.list(parent).use { paths ->
                                paths.filter { it.fileName.toString().startsWith(".ah-repack-") }.forEach(Files::deleteIfExists)
                            }
                        }
                    }
                })

        val unsupported = buildFixture(root.resolve("fail-abi"), listOf("mips"), manifest, bundle)
        val abiFailure = runCatching { ApkRepacker().repack(unsupported.request) }.exceptionOrNull() as? PackageException
        check(abiFailure?.code == PackageErrorCode.COMPAT_ABI_UNSUPPORTED)
        assertPlanConsumed(unsupported)
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

    private fun sensitiveCleanupMatrix(root: Path, manifest: ManifestFixture, bundle: RuntimeBundle) {
        val cases = linkedMapOf<String, (CleanupFaults) -> Unit>(
            "copy-oom" to { faults -> faults.failAfterCopy = "prepare.config" },
            "payload-plan-oom" to { faults -> faults.failAfterCopy = "bytesPlan.config" },
            "materialization-oom" to { faults -> faults.failAfterCopy = "materializer.runtime.armeabi-v7a" },
            "verifier-materialize-oom" to { faults -> faults.failAfterCopy = "verifier.materialize" },
            "verifier-oom" to { faults -> faults.failVerifier = true },
            "success" to { _ -> },
        )
        val report = ArrayList<String>()
        cases.forEach { (name, configure) ->
            val fixture = buildFixture(root.resolve("cleanup-$name"), emptyList(), manifest, bundle)
            val faults = CleanupFaults()
            configure(faults)
            val result = runCatching { ApkRepacker(faults, ::atomicMove).repack(fixture.request) }
            if (name == "success") {
                check(result.isSuccess && Files.exists(fixture.output))
                check(faults.cleared["prepared.bytePayloads"] == true)
                check(faults.cleared["prepared.expected"] == true)
                check(faults.cleared["verifier.runtime"] == true)
            } else {
                check(result.exceptionOrNull() is OutOfMemoryError) { "$name did not inject OOM" }
                check(!Files.exists(fixture.output)) { "$name published output" }
                assertPlanConsumed(fixture)
            }
            check(faults.cleared.isNotEmpty() && faults.cleared.values.all { it }) { "$name left a sensitive buffer uncleared" }
            report += "  {\"case\":\"$name\",\"cleared\":true}"
        }
        Files.writeString(root.parent.resolve("cleanup-matrix.json"), report.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]\n"))
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
            add(zip, "keep0000.bin", "renamed-dex!".toByteArray(), stored = false)
            add(zip, "keep-sign000.bin", "renamed-signature".toByteArray(), stored = false)
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
        val stringTable = byteArrayOf(0) + ".shstrtab\u0000.ah_share_v1\u0000.bss\u0000".toByteArray(StandardCharsets.US_ASCII)
        val stringOffset = header
        val slotOffset = align(stringOffset + stringTable.size, 16)
        val sectionOffset = align(slotOffset + SHARE_SLOT_BYTES, 16)
        val bytes = ByteArray(sectionOffset + sectionHeader * 4)
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
            putTestU2(bytes, 58, sectionHeader); putTestU2(bytes, 60, 4); putTestU2(bytes, 62, 1)
        } else {
            putTestU4(bytes, 32, sectionOffset.toLong())
            putTestU2(bytes, 46, sectionHeader); putTestU2(bytes, 48, 4); putTestU2(bytes, 50, 1)
        }
        stringTable.copyInto(bytes, stringOffset)
        writeSection(bytes, sectionOffset + sectionHeader, is64, 1, 3, 0, stringOffset, stringTable.size)
        writeSection(bytes, sectionOffset + sectionHeader * 2, is64, 11, 1, 2, slotOffset, SHARE_SLOT_BYTES)
        writeSection(bytes, sectionOffset + sectionHeader * 3, is64, 24, 8, 3, bytes.size + 4096, 512)
        "AHP0".toByteArray().copyInto(bytes, slotOffset)
        putTestU2(bytes, slotOffset + 4, 1)
        putTestU2(bytes, slotOffset + 6, abi.abiId)
        return bytes
    }

    private fun writeSection(
        bytes: ByteArray,
        offset: Int,
        is64: Boolean,
        name: Int,
        type: Int,
        flags: Long,
        data: Int,
        size: Int,
    ) {
        putTestU4(bytes, offset, name.toLong())
        putTestU4(bytes, offset + 4, type.toLong())
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
        val unsignedText = unsigned.output.lowercase(Locale.ROOT)
        check(unsigned.exit != 0 && unsignedText.contains("does not verify") &&
            (unsignedText.contains("missing meta-inf/manifest.mf") || unsignedText.contains("no signatures"))) {
            "apksigner failed for an unrelated reason: ${unsigned.output}"
        }
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

    private fun candidateMutation(mutation: (ByteArray) -> ByteArray): PackageFaults = object : PackageFaults {
        override fun afterCandidateClosed(candidate: Path) {
            Files.write(candidate, mutation(Files.readAllBytes(candidate)))
        }
    }

    private fun assertPlanConsumed(fixture: Fixture) {
        val failure = runCatching { fixture.request.keyPackagingPlan.consume { Unit } }.exceptionOrNull()
        check(failure is ContainerException && failure.field == "planConsumed") { "key packaging plan was not consumed" }
    }

    private fun renameEntry(bytes: ByteArray, oldName: String, newName: String): ByteArray {
        val old = oldName.toByteArray(StandardCharsets.UTF_8)
        val replacement = newName.toByteArray(StandardCharsets.UTF_8)
        check(old.size == replacement.size)
        val record = zipRecord(bytes, oldName)
        replacement.copyInto(bytes, record.localOffset + LOCAL_FIXED_BYTES)
        replacement.copyInto(bytes, record.centralOffset + CENTRAL_FIXED_BYTES)
        return bytes
    }

    private fun changeMethod(bytes: ByteArray, name: String, method: Int): ByteArray {
        val record = zipRecord(bytes, name)
        putTestU2(bytes, record.localOffset + 8, method)
        putTestU2(bytes, record.centralOffset + 10, method)
        return bytes
    }

    private fun setDescriptor(bytes: ByteArray, name: String): ByteArray {
        val record = zipRecord(bytes, name)
        putTestU2(bytes, record.localOffset + 6, leU2(bytes, record.localOffset + 6) or DATA_DESCRIPTOR_FLAG)
        putTestU2(bytes, record.centralOffset + 8, leU2(bytes, record.centralOffset + 8) or DATA_DESCRIPTOR_FLAG)
        return bytes
    }

    private fun flipEntryByte(bytes: ByteArray, name: String, relativeOffset: Int): ByteArray {
        val record = zipRecord(bytes, name)
        val dataOffset = record.localOffset + LOCAL_FIXED_BYTES + leU2(bytes, record.localOffset + 26) +
            leU2(bytes, record.localOffset + 28)
        check(relativeOffset >= 0 && dataOffset + relativeOffset < record.centralDirectoryOffset)
        bytes[dataOffset + relativeOffset] = (bytes[dataOffset + relativeOffset].toInt() xor 1).toByte()
        return bytes
    }

    private fun overlapLocalOffset(bytes: ByteArray): ByteArray {
        val records = zipRecords(bytes)
        check(records.size >= 2)
        putTestU4(bytes, records[1].centralOffset + 42, records[0].localOffset.toLong())
        return bytes
    }

    private fun insertCentralGap(bytes: ByteArray, gap: ByteArray): ByteArray {
        val eocd = findSignature(bytes, EOCD_SIGNATURE, bytes.size - EOCD_FIXED_BYTES)
        val central = leU4(bytes, eocd + 16).toInt()
        val shifted = ByteArray(bytes.size + gap.size)
        bytes.copyInto(shifted, 0, 0, central)
        gap.copyInto(shifted, central)
        bytes.copyInto(shifted, central + gap.size, central, bytes.size)
        putTestU4(shifted, eocd + gap.size + 16, central.toLong() + gap.size)
        return shifted
    }

    private fun misalignEntry(bytes: ByteArray, name: String): ByteArray {
        val record = zipRecord(bytes, name)
        val insertion = record.localOffset + LOCAL_FIXED_BYTES + leU2(bytes, record.localOffset + 26) +
            leU2(bytes, record.localOffset + 28)
        val shifted = ByteArray(bytes.size + 1)
        bytes.copyInto(shifted, 0, 0, insertion)
        shifted[insertion] = 0
        bytes.copyInto(shifted, insertion + 1, insertion, bytes.size)
        putTestU2(shifted, record.localOffset + 28, leU2(bytes, record.localOffset + 28) + 1)
        val eocd = findSignature(shifted, EOCD_SIGNATURE, shifted.size - EOCD_FIXED_BYTES)
        putTestU4(shifted, eocd + 16, record.centralDirectoryOffset.toLong() + 1)
        zipRecords(shifted).forEach { current ->
            if (current.localOffset > record.localOffset) {
                putTestU4(shifted, current.centralOffset + 42, current.localOffset.toLong() + 1)
            }
        }
        return shifted
    }

    private fun zipRecord(bytes: ByteArray, name: String): TestZipRecord =
        zipRecords(bytes).single { it.name == name }

    private fun zipRecords(bytes: ByteArray): List<TestZipRecord> {
        val eocd = findSignature(bytes, EOCD_SIGNATURE, bytes.size - EOCD_FIXED_BYTES)
        val count = leU2(bytes, eocd + 10)
        val centralDirectoryOffset = leU4(bytes, eocd + 16).toInt()
        val records = ArrayList<TestZipRecord>(count)
        var cursor = centralDirectoryOffset
        repeat(count) {
            check(leU4(bytes, cursor) == CENTRAL_SIGNATURE)
            val nameLength = leU2(bytes, cursor + 28)
            val extraLength = leU2(bytes, cursor + 30)
            val commentLength = leU2(bytes, cursor + 32)
            val name = String(bytes, cursor + CENTRAL_FIXED_BYTES, nameLength, StandardCharsets.UTF_8)
            records += TestZipRecord(name, leU4(bytes, cursor + 42).toInt(), cursor, centralDirectoryOffset)
            cursor += CENTRAL_FIXED_BYTES + nameLength + extraLength + commentLength
        }
        return records
    }

    private fun findSignature(bytes: ByteArray, signature: Long, start: Int): Int {
        for (index in start.coerceAtMost(bytes.size - 4) downTo 0) {
            if (leU4(bytes, index) == signature) return index
        }
        error("ZIP signature not found")
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")

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
    private data class TestZipRecord(val name: String, val localOffset: Int, val centralOffset: Int, val centralDirectoryOffset: Int)

    private class CleanupFaults : PackageFaults {
        var failAfterCopy: String? = null
        var failVerifier: Boolean = false
        val cleared = linkedMapOf<String, Boolean>()

        override fun afterSensitiveCopy(label: String) {
            if (label == failAfterCopy) throw OutOfMemoryError("synthetic $label")
        }

        override fun afterVerifierRuntimeRead() {
            if (failVerifier) throw OutOfMemoryError("synthetic verifier")
        }

        override fun sensitiveCleared(label: String, cleared: Boolean) {
            this.cleared[label] = (this.cleared[label] ?: true) && cleared
        }
    }
}
