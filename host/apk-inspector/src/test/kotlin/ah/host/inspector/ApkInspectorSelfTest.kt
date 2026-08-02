package ah.host.inspector

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.Random
import kotlin.io.path.name

object ApkInspectorSelfTest {
    private const val FUZZ_SEED = 0x4d312d3031L

    @JvmStatic
    fun main(args: Array<String>) {
        check(args.isEmpty())
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.inspector.reportDir")))
        val fuzzSamples = requireNotNull(System.getProperty("ah.inspector.fuzzSamples")).toInt()
        require(fuzzSamples == 10_000) { "formal self-test requires exactly 10,000 fuzz samples" }
        Files.createDirectories(reportDir)
        val corpusDir = Files.createTempDirectory(reportDir, "corpus-")
        val errorResults = ArrayList<ErrorResult>()
        var peakUsedBytes = usedMemory()
        try {
            val baselineBytes = SyntheticApkFixtures.apk(SyntheticApkFixtures.baselineEntries())
            val baselinePath = write(corpusDir, "valid-multidex-four-abi.apk", baselineBytes)
            val baselineBefore = sha256(baselineBytes)
            val beforeNames = listNames(corpusDir)
            val inspection = ApkInspector().inspect(baselinePath)
            val afterNames = listNames(corpusDir)
            check(beforeNames == afterNames) { "inspection created an extraction artifact" }
            check(MessageDigest.isEqual(baselineBefore, inspection.inputSha256))
            check(MessageDigest.isEqual(baselineBefore, sha256(Files.readAllBytes(baselinePath))))
            verifyBaselineModel(inspection)
            verifyImmutability(inspection)
            verifyHandleReleased(baselinePath)

            val singleDexPath = write(
                corpusDir,
                "valid-single-dex.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        manifest = SyntheticApkFixtures.manifest(applicationClass = null, factoryClass = null),
                        dexDescriptors = listOf(listOf("Lfixture/Single;")),
                        additional = listOf(
                            SyntheticZipEntry(
                                "assets/deflated.bin",
                                ByteArray(1_024) { (it % 251).toByte() },
                                method = 8,
                                dataDescriptor = true,
                            ),
                        ),
                    ),
                ),
            )
            val singleDex = ApkInspector().inspect(singleDexPath)
            check(singleDex.dexEntries.size == 1)
            check(singleDex.applicationClass == null && !singleDex.hasAppComponentFactory)
            val maxPath = "assets/" + "p".repeat(1_017)
            val maxPathInspection = ApkInspector().inspect(
                write(
                    corpusDir,
                    "valid-max-path.apk",
                    SyntheticApkFixtures.apk(
                        SyntheticApkFixtures.baselineEntries(
                            additional = listOf(SyntheticZipEntry(maxPath, byteArrayOf(1))),
                        ),
                    ),
                ),
            )
            check(maxPathInspection.zipEntries.any { it.name == maxPath })
            val maxDexInspection = ApkInspector().inspect(
                write(corpusDir, "valid-64-dex.apk", SyntheticApkFixtures.apk(manyDexEntries(64))),
            )
            check(maxDexInspection.dexEntries.size == 64)

            val malformedManifest = SyntheticApkFixtures.manifestWithDuplicateStringOffset()
            val invalidDex = SyntheticApkFixtures.dex("Lfixture/Main;").also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            val duplicateEntries = SyntheticApkFixtures.baselineEntries() +
                SyntheticZipEntry("AndroidManifest.xml", SyntheticApkFixtures.manifest())
            val nfcCollision = SyntheticApkFixtures.baselineEntries() + listOf(
                SyntheticZipEntry("assets/caf\u00e9.txt", byteArrayOf(1)),
                SyntheticZipEntry("assets/cafe\u0301.txt", byteArrayOf(2)),
            )
            val structural = linkedMapOf(
                "central-local-length-conflict.apk" to SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries().mapIndexed { index, entry ->
                        if (index == 0) entry.copy(localUncompressedSizeDelta = 1) else entry
                    },
                ),
                "offset-overflow.apk" to SyntheticApkFixtures.mutateCentralLocalOffset(baselineBytes, 0xffff_fff0L),
                "zip64.apk" to SyntheticApkFixtures.mutateZip64Eocd(baselineBytes),
                "encrypted-entry.apk" to SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries().mapIndexed { index, entry ->
                        if (index == 0) entry.copy(flags = entry.flags or 1) else entry
                    },
                ),
                "actual-crc-corruption.apk" to SyntheticApkFixtures.mutateFirstEntryData(baselineBytes),
            )
            for ((name, bytes) in structural) {
                expectCode(corpusDir, name, bytes, InspectionErrorCode.INPUT_ZIP_STRUCTURE, errorResults)
            }
            expectCode(
                corpusDir,
                "not-a-zip.apk",
                "not a zip".toByteArray(),
                InspectionErrorCode.INPUT_ZIP_STRUCTURE,
                errorResults,
            )
            expectCode(
                corpusDir,
                "compression-bomb.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries() +
                        SyntheticZipEntry("assets/bomb.bin", ByteArray(100_000), method = 8),
                ),
                InspectionErrorCode.INPUT_LIMIT_EXCEEDED,
                errorResults,
                expectedLimit = "compressionRatio",
            )
            expectCode(
                corpusDir,
                "duplicate-entry.apk",
                SyntheticApkFixtures.apk(duplicateEntries),
                InspectionErrorCode.INPUT_DUPLICATE_ENTRY,
                errorResults,
            )
            expectCode(
                corpusDir,
                "nfc-collision.apk",
                SyntheticApkFixtures.apk(nfcCollision),
                InspectionErrorCode.INPUT_DUPLICATE_ENTRY,
                errorResults,
            )
            expectCode(
                corpusDir,
                "path-traversal.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries() + SyntheticZipEntry("../escape", byteArrayOf(1)),
                ),
                InspectionErrorCode.INPUT_PATH_UNSAFE,
                errorResults,
            )
            expectCode(
                corpusDir,
                "path-too-long.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("assets/" + "p".repeat(1_018), byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.INPUT_LIMIT_EXCEEDED,
                errorResults,
                expectedLimit = "pathUtf8Bytes",
            )
            expectCode(
                corpusDir,
                "manifest-string-offset-conflict.apk",
                SyntheticApkFixtures.apk(SyntheticApkFixtures.baselineEntries(manifest = malformedManifest)),
                InspectionErrorCode.INPUT_MANIFEST_INVALID,
                errorResults,
            )
            expectCode(
                corpusDir,
                "manifest-resource-map-overflow.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        manifest = SyntheticApkFixtures.manifestWithOversizedResourceMap(),
                    ),
                ),
                InspectionErrorCode.INPUT_MANIFEST_INVALID,
                errorResults,
            )
            expectCode(
                corpusDir,
                "manifest-illegal-package.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        manifest = SyntheticApkFixtures.manifest(packageName = "9invalid"),
                    ),
                ),
                InspectionErrorCode.INPUT_MANIFEST_INVALID,
                errorResults,
            )
            val manifestFailures = linkedMapOf(
                "manifest-package-missing.apk" to SyntheticApkFixtures.manifest(packageName = null),
                "manifest-package-duplicate.apk" to SyntheticApkFixtures.manifest(duplicatePackage = true),
                "manifest-invalid-utf8.apk" to SyntheticApkFixtures.manifestWithInvalidUtf8(),
                "manifest-resource-id-mismatch.apk" to SyntheticApkFixtures.manifest(minSdkResourceId = 0x01010000),
                "manifest-namespace-out-of-scope.apk" to SyntheticApkFixtures.manifest(declareAndroidNamespace = false),
                "manifest-namespaced-uses-sdk.apk" to SyntheticApkFixtures.manifest(usesSdkElementNamespace = true),
                "manifest-namespaced-application.apk" to SyntheticApkFixtures.manifest(applicationElementNamespace = true),
                "manifest-raw-typed-conflict.apk" to SyntheticApkFixtures.manifest(conflictingApplicationRawValue = true),
                "manifest-factory-resource-id-mismatch.apk" to SyntheticApkFixtures.manifest(factoryResourceId = 0x01010000),
            )
            for ((name, manifest) in manifestFailures) {
                try {
                    expectCode(
                        corpusDir,
                        name,
                        SyntheticApkFixtures.apk(SyntheticApkFixtures.baselineEntries(manifest = manifest)),
                        InspectionErrorCode.INPUT_MANIFEST_INVALID,
                        errorResults,
                    )
                } catch (exception: IllegalStateException) {
                    error("$name did not fail closed: ${exception.message}")
                }
            }
            expectCode(
                corpusDir,
                "dex-checksum.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(dexDescriptors = emptyList())
                        .toMutableList()
                        .apply { add(1, SyntheticZipEntry("classes.dex", invalidDex)) },
                ),
                InspectionErrorCode.INPUT_DEX_INVALID,
                errorResults,
            )
            expectCode(
                corpusDir,
                "dex-huge-declared-string.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(dexDescriptors = emptyList())
                        .toMutableList()
                        .apply {
                            add(
                                1,
                                SyntheticZipEntry(
                                    "classes.dex",
                                    SyntheticApkFixtures.dexWithDeclaredUtf16Length(Int.MAX_VALUE),
                                ),
                            )
                        },
                ),
                InspectionErrorCode.INPUT_DEX_INVALID,
                errorResults,
            )
            val dexFailures = linkedMapOf(
                "dex-magic.apk" to SyntheticApkFixtures.dexWithVersion("034"),
                "dex-version-036.apk" to SyntheticApkFixtures.dexWithVersion("036"),
                "dex-file-size.apk" to SyntheticApkFixtures.dexWithFileSizeDelta(1),
                "dex-sha1.apk" to SyntheticApkFixtures.dexWithInvalidSha1(),
                "dex-table-offset.apk" to SyntheticApkFixtures.dexWithInvalidTableOffset(),
                "dex-map-missing.apk" to SyntheticApkFixtures.dexWithMissingMap(),
                "dex-data-range.apk" to SyntheticApkFixtures.dexWithInvalidDataRange(),
                "dex-descriptor-syntax.apk" to SyntheticApkFixtures.dex("not-a-descriptor"),
            )
            for ((name, dex) in dexFailures) {
                expectCode(
                    corpusDir,
                    name,
                    SyntheticApkFixtures.apk(entriesWithDex(dex)),
                    InspectionErrorCode.INPUT_DEX_INVALID,
                    errorResults,
                )
            }
            val repeatedOffsetStart = System.nanoTime()
            expectCode(
                corpusDir,
                "dex-repeated-string-data-offset.apk",
                SyntheticApkFixtures.apk(entriesWithDex(SyntheticApkFixtures.dexWithRepeatedStringDataOffsets(4_096))),
                InspectionErrorCode.INPUT_DEX_INVALID,
                errorResults,
            )
            check(System.nanoTime() - repeatedOffsetStart < 5_000_000_000L) {
                "repeated DEX string-data offsets exceeded the five-second bounded-work gate"
            }
            expectCode(
                corpusDir,
                "dex-noncanonical-asset.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(
                            SyntheticZipEntry("assets/payload.dex", SyntheticApkFixtures.dex("Lfixture/Payload;")),
                        ),
                    ),
                ),
                InspectionErrorCode.INPUT_DEX_INVALID,
                errorResults,
            )
            expectCode(
                corpusDir,
                "dex-gap.apk",
                SyntheticApkFixtures.apk(
                    listOf(
                        SyntheticZipEntry("AndroidManifest.xml", SyntheticApkFixtures.manifest()),
                        SyntheticZipEntry("classes.dex", SyntheticApkFixtures.dex("Lfixture/Main;")),
                        SyntheticZipEntry("classes3.dex", SyntheticApkFixtures.dex("Lfixture/Third;")),
                    ),
                ),
                InspectionErrorCode.INPUT_DEX_INVALID,
                errorResults,
            )
            expectCode(
                corpusDir,
                "dex-count-65.apk",
                SyntheticApkFixtures.apk(manyDexEntries(65)),
                InspectionErrorCode.INPUT_LIMIT_EXCEEDED,
                errorResults,
                expectedLimit = "dexEntries",
            )
            expectCode(
                corpusDir,
                "min-sdk-28.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(manifest = SyntheticApkFixtures.manifest(minSdk = 28)),
                ),
                InspectionErrorCode.COMPAT_MIN_SDK,
                errorResults,
                expectedMarkers = listOf("MIN_SDK_BELOW_29"),
            )
            expectCode(
                corpusDir,
                "split.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(manifest = SyntheticApkFixtures.manifest(splitName = "config.en")),
                ),
                InspectionErrorCode.COMPAT_SPLIT,
                errorResults,
                expectedMarkers = listOf("MANIFEST_SPLIT_ATTRIBUTE"),
            )
            expectCode(
                corpusDir,
                "aab.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("BundleConfig.pb", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_SPLIT,
                errorResults,
                expectedMarkers = listOf("AAB_BUNDLE_CONFIG"),
            )
            expectCode(
                corpusDir,
                "apks.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("toc.pb", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_SPLIT,
                errorResults,
                expectedMarkers = listOf("APKS_TABLE_OF_CONTENTS"),
            )
            expectCode(
                corpusDir,
                "flutter.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("assets/flutter_assets/kernel_blob.bin", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("FLUTTER_RUNTIME"),
            )
            expectCode(
                corpusDir,
                "unity.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("assets/bin/Data/globalgamemanagers", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("UNITY_RUNTIME"),
            )
            expectCode(
                corpusDir,
                "react-native.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("assets/index.android.bundle", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("REACT_NATIVE_RUNTIME"),
            )
            expectCode(
                corpusDir,
                "tinker.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        dexDescriptors = listOf(listOf("Lcom/tencent/tinker/Loader;")),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("TINKER_HOTFIX"),
            )
            expectCode(
                corpusDir,
                "sophix.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        dexDescriptors = listOf(listOf("Lcom/taobao/sophix/Entry;")),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("SOPHIX_HOTFIX"),
            )
            expectCode(
                corpusDir,
                "plugin-runtime.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        dexDescriptors = listOf(listOf("Lcom/qihoo360/replugin/Entry;")),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("REPLUGIN_RUNTIME"),
            )
            expectCode(
                corpusDir,
                "unsupported-abi.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("lib/mips/libfixture.so", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("NATIVE_ABI_UNSUPPORTED"),
            )
            expectCode(
                corpusDir,
                "native-elf-invalid.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries().map { entry ->
                        if (entry.name == "lib/arm64-v8a/libfixture.so") entry.copy(data = byteArrayOf(1)) else entry
                    },
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("NATIVE_ELF_INVALID"),
            )
            expectCode(
                corpusDir,
                "native-elf-truncated-header.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries().map { entry ->
                        if (entry.name == "lib/arm64-v8a/libfixture.so") {
                            entry.copy(data = SyntheticApkFixtures.elf("arm64-v8a").copyOf(20))
                        } else {
                            entry
                        }
                    },
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("NATIVE_ELF_INVALID"),
            )
            expectCode(
                corpusDir,
                "native-elf-abi-mismatch.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries().map { entry ->
                        if (entry.name == "lib/arm64-v8a/libfixture.so") {
                            entry.copy(data = SyntheticApkFixtures.elf("x86"))
                        } else {
                            entry
                        }
                    },
                ),
                InspectionErrorCode.COMPAT_FRAMEWORK,
                errorResults,
                expectedMarkers = listOf("NATIVE_ELF_ABI_MISMATCH"),
            )
            expectCode(
                corpusDir,
                "existing-shell.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(
                            SyntheticZipEntry("lib/arm64-v8a/libjiagu.so", SyntheticApkFixtures.elf("arm64-v8a")),
                        ),
                    ),
                ),
                InspectionErrorCode.COMPAT_EXISTING_SHELL,
                errorResults,
                expectedMarkers = listOf("QIHO0_JIAGU_SHELL"),
            )
            expectCode(
                corpusDir,
                "reserved-namespace.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(SyntheticZipEntry("assets/ah/runtime/config.bin", byteArrayOf(1))),
                    ),
                ),
                InspectionErrorCode.COMPAT_RESERVED_NAMESPACE,
                errorResults,
                expectedMarkers = listOf("AH_RUNTIME_ASSET_NAMESPACE"),
            )
            expectCode(
                corpusDir,
                "reserved-class.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        dexDescriptors = listOf(listOf("Lah/runtime/Injected;")),
                    ),
                ),
                InspectionErrorCode.COMPAT_RESERVED_NAMESPACE,
                errorResults,
                expectedMarkers = listOf("AH_RUNTIME_CLASS_NAMESPACE"),
            )
            expectCode(
                corpusDir,
                "reserved-native.apk",
                SyntheticApkFixtures.apk(
                    SyntheticApkFixtures.baselineEntries(
                        additional = listOf(
                            SyntheticZipEntry("lib/x86_64/libah_runtime.so", SyntheticApkFixtures.elf("x86_64")),
                        ),
                    ),
                ),
                InspectionErrorCode.COMPAT_RESERVED_NAMESPACE,
                errorResults,
                expectedMarkers = listOf("AH_RUNTIME_NATIVE_LIBRARY"),
            )

            val missing = corpusDir.resolve("missing.apk")
            val ioException = expectFailure { ApkInspector().inspect(missing) }
            check(ioException.code == InspectionErrorCode.INPUT_IO)
            errorResults += ErrorResult("missing.apk", "not_applicable", ioException.code, ioException.markerIds)

            val changedPath = write(corpusDir, "input-changed.apk", baselineBytes)
            val changedInspector = ApkInspector { path ->
                Files.write(path, byteArrayOf(0x41), StandardOpenOption.APPEND)
            }
            val changed = expectFailure { changedInspector.inspect(changedPath) }
            check(changed.code == InspectionErrorCode.INPUT_CHANGED)
            errorResults += ErrorResult("input-changed.apk", hex(sha256(Files.readAllBytes(changedPath))), changed.code, changed.markerIds)
            verifyHandleReleased(changedPath)

            val restoredChangePath = write(corpusDir, "input-changed-restored.apk", baselineBytes)
            val alternateBytes = SyntheticApkFixtures.apk(
                SyntheticApkFixtures.baselineEntries(
                    manifest = SyntheticApkFixtures.manifest(packageName = "ah.fixtures.alternate"),
                ),
            )
            val restoredChangeInspector = ApkInspector(
                beforeFinalHash = { path ->
                    Files.write(
                        path,
                        baselineBytes,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    )
                },
                afterInitialHash = { path ->
                    Files.write(
                        path,
                        alternateBytes,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    )
                },
            )
            val restoredChange = expectFailure { restoredChangeInspector.inspect(restoredChangePath) }
            check(restoredChange.code == InspectionErrorCode.INPUT_CHANGED)
            errorResults += ErrorResult(
                "input-changed-restored.apk",
                hex(sha256(Files.readAllBytes(restoredChangePath))),
                restoredChange.code,
                restoredChange.markerIds,
            )
            verifyHandleReleased(restoredChangePath)

            val cancelledPath = write(corpusDir, "cancelled.apk", baselineBytes)
            try {
                val cancelled = expectFailure {
                    ApkInspector { Thread.currentThread().interrupt() }.inspect(cancelledPath)
                }
                check(cancelled.code == InspectionErrorCode.INPUT_IO)
            } finally {
                Thread.interrupted()
            }
            verifyHandleReleased(cancelledPath)

            val requiredCodes = InspectionErrorCode.entries.toSet()
            check(errorResults.map { it.code }.toSet() == requiredCodes) {
                "public error coverage mismatch: ${errorResults.map { it.code }.toSet()}"
            }

            peakUsedBytes = maxOf(peakUsedBytes, runSeededFuzz(corpusDir, baselineBytes, fuzzSamples))
            val canonicalModel = canonicalModelJson(inspection)
            Files.writeString(reportDir.resolve("canonical-model.json"), canonicalModel)
            Files.writeString(reportDir.resolve("error-matrix.json"), errorMatrixJson(errorResults))
            Files.writeString(
                reportDir.resolve("fuzz-summary.json"),
                "{\"seed\":\"0x${FUZZ_SEED.toString(16)}\",\"samples\":$fuzzSamples,\"peakUsedBytes\":$peakUsedBytes}\n",
            )
            println("M1-01 SELF-TEST PASS errors=${errorResults.size} fuzz=$fuzzSamples seed=0x${FUZZ_SEED.toString(16)}")
            println(canonicalModel.trim())
        } finally {
            deleteTree(corpusDir)
        }
    }

    private fun verifyBaselineModel(inspection: ApkInspection) {
        check(inspection.packageName == "ah.fixtures.inspector")
        check(inspection.minSdk == 29)
        check(inspection.targetSdk == 36)
        check(inspection.applicationClass == "ah.fixtures.inspector.FixtureApplication")
        check(inspection.appComponentFactoryClass == "ah.fixtures.inspector.FixtureFactory")
        check(inspection.hasAppComponentFactory)
        check(inspection.dexEntries.map { it.entryName } == listOf("classes.dex", "classes2.dex"))
        check(inspection.dexEntries.map { it.ordinal } == listOf(1, 2))
        check(inspection.nativeAbis.abis == listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        check(
            inspection.findings.map { it.markerId } == listOf(
                "CUSTOM_APPLICATION",
                "CUSTOM_APP_COMPONENT_FACTORY",
                "NATIVE_ABI_ARMEABI_V7A",
                "NATIVE_ABI_ARM64_V8A",
                "NATIVE_ABI_X86",
                "NATIVE_ABI_X86_64",
            ),
        )
        val packageHash = MessageDigest.getInstance("SHA-256")
            .digest(inspection.packageName.toByteArray(Charsets.UTF_8))
        check(MessageDigest.isEqual(packageHash, inspection.packageNameSha256))
        check(inspection.limitsApplied.values["dexEntries"] == 64L)
        check(inspection.compatibilityRulesVersion == "compatibility-rules-v1")
    }

    private fun manyDexEntries(count: Int): List<SyntheticZipEntry> {
        val entries = ArrayList<SyntheticZipEntry>(count + 1)
        entries += SyntheticZipEntry("AndroidManifest.xml", SyntheticApkFixtures.manifest())
        repeat(count) { index ->
            val ordinal = index + 1
            val name = if (ordinal == 1) "classes.dex" else "classes$ordinal.dex"
            entries += SyntheticZipEntry(name, SyntheticApkFixtures.dex("Lfixture/C$ordinal;"))
        }
        return entries
    }

    private fun entriesWithDex(dex: ByteArray): List<SyntheticZipEntry> =
        SyntheticApkFixtures.baselineEntries(dexDescriptors = emptyList()).toMutableList().apply {
            add(1, SyntheticZipEntry("classes.dex", dex))
        }

    private fun verifyImmutability(inspection: ApkInspection) {
        val originalHash = inspection.inputSha256
        originalHash[0] = (originalHash[0].toInt() xor 0xff).toByte()
        check(!originalHash.contentEquals(inspection.inputSha256))
        val packageHash = inspection.packageNameSha256
        packageHash[0] = (packageHash[0].toInt() xor 0xff).toByte()
        check(!packageHash.contentEquals(inspection.packageNameSha256))
        var rejected = false
        try {
            (inspection.dexEntries as MutableList<DexSummary>).clear()
        } catch (_: UnsupportedOperationException) {
            rejected = true
        }
        check(rejected)
    }

    private fun expectCode(
        directory: Path,
        name: String,
        bytes: ByteArray,
        expectedCode: InspectionErrorCode,
        results: MutableList<ErrorResult>,
        expectedMarkers: List<String> = emptyList(),
        expectedLimit: String? = null,
    ) {
        val path = write(directory, name, bytes)
        val before = sha256(bytes)
        val exception = expectFailure { ApkInspector().inspect(path) }
        check(exception.code == expectedCode) { "$name expected $expectedCode, got ${exception.code}" }
        check(exception.message?.contains(directory.toString()) != true) { "$name leaked an absolute path" }
        check(exception.markerIds == expectedMarkers) { "$name markers ${exception.markerIds}" }
        check(expectedLimit == null || exception.limitName == expectedLimit)
        check(MessageDigest.isEqual(before, sha256(Files.readAllBytes(path))))
        verifyHandleReleased(path)
        results += ErrorResult(name, hex(before), exception.code, exception.markerIds)
    }

    private fun runSeededFuzz(directory: Path, baseline: ByteArray, samples: Int): Long {
        val random = Random(FUZZ_SEED)
        val fuzzPath = directory.resolve("seeded-fuzz.apk")
        var peak = usedMemory()
        repeat(samples) { index ->
            val bytes = if (index % 4 == 0) {
                baseline.copyOf().also { candidate ->
                    repeat(1 + random.nextInt(4)) {
                        val offset = random.nextInt(candidate.size)
                        candidate[offset] = random.nextInt(256).toByte()
                    }
                }
            } else {
                ByteArray(random.nextInt(512)).also(random::nextBytes)
            }
            Files.write(
                fuzzPath,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            val first = outcome(fuzzPath)
            if (index % 100 == 0) {
                val second = outcome(fuzzPath)
                check(first == second) { "non-deterministic fuzz outcome at sample $index" }
            }
            peak = maxOf(peak, usedMemory())
        }
        verifyHandleReleased(fuzzPath)
        return peak
    }

    private fun outcome(path: Path): String = try {
        val inspection = ApkInspector().inspect(path)
        "OK:${inspection.packageName}:${inspection.dexEntries.size}"
    } catch (exception: InspectionException) {
        "ERR:${exception.code}:${exception.markerIds.joinToString(",")}"
    }

    private fun expectFailure(block: () -> Unit): InspectionException {
        try {
            block()
        } catch (exception: InspectionException) {
            return exception
        }
        error("expected InspectionException")
    }

    private fun verifyHandleReleased(path: Path) {
        val moved = path.resolveSibling(path.name + ".moved")
        Files.move(path, moved, StandardCopyOption.REPLACE_EXISTING)
        Files.move(moved, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun canonicalModelJson(inspection: ApkInspection): String = buildString {
        append("{\"inputSha256\":\"").append(hex(inspection.inputSha256)).append("\",")
        append("\"packageName\":\"").append(inspection.packageName).append("\",")
        append("\"packageNameSha256\":\"").append(hex(inspection.packageNameSha256)).append("\",")
        append("\"minSdk\":").append(inspection.minSdk).append(',')
        append("\"targetSdk\":").append(inspection.targetSdk).append(',')
        append("\"applicationClass\":\"").append(inspection.applicationClass).append("\",")
        append("\"appComponentFactoryClass\":\"").append(inspection.appComponentFactoryClass).append("\",")
        append("\"compatibilityRulesVersion\":\"").append(inspection.compatibilityRulesVersion).append("\",")
        append("\"dexEntries\":[")
        append(inspection.dexEntries.joinToString(",") { "\"${it.entryName}\"" })
        append("],\"nativeAbis\":[")
        append(inspection.nativeAbis.abis.joinToString(",") { "\"$it\"" })
        append("],\"markerIds\":[")
        append(inspection.findings.joinToString(",") { "\"${it.markerId}\"" })
        append("]}\n")
    }

    private fun errorMatrixJson(results: List<ErrorResult>): String = buildString {
        append("{\"fixtures\":[")
        results.forEachIndexed { index, result ->
            if (index > 0) append(',')
            append("{\"name\":\"").append(result.name).append("\",")
            append("\"sha256\":\"").append(result.sha256).append("\",")
            append("\"code\":\"").append(result.code).append("\",")
            append("\"markerIds\":[")
            append(result.markerIds.joinToString(",") { "\"$it\"" })
            append("]}")
        }
        append("]}\n")
    }

    private fun write(directory: Path, name: String, bytes: ByteArray): Path =
        Files.write(directory.resolve(name), bytes)

    private fun listNames(directory: Path): List<String> = Files.list(directory).use { stream ->
        stream.map { it.fileName.toString() }.sorted().toList()
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun usedMemory(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private data class ErrorResult(
        val name: String,
        val sha256: String,
        val code: InspectionErrorCode,
        val markerIds: List<String>,
    )
}
