package ah.host.cli

import ah.host.axml.AxmlTransformException
import ah.host.axml.BinaryManifestTransformer
import ah.host.axml.ManifestTransformRequest
import ah.host.container.ContainerBuildResult
import ah.host.container.ContainerException
import ah.host.container.DexContainerBuilder
import ah.host.inspector.ApkInspection
import ah.host.inspector.ApkInspector
import ah.host.inspector.DexSummary
import ah.host.inspector.InspectionException
import ah.host.inspector.SignerPolicyException
import ah.host.inspector.SignerPolicyV1
import ah.host.inspector.SignerPolicyVerifier
import ah.host.repacker.ApkRepacker
import ah.host.repacker.OutputVerification
import ah.host.repacker.PackageErrorCode
import ah.host.repacker.PackageException
import ah.host.repacker.RepackRequest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.ZipFile

internal interface CliFaults {
    fun beforeStage(stage: PipelineStage) = Unit
    fun afterStage(stage: PipelineStage) = Unit
    fun beforeReportPublish(report: Path, success: Boolean) = Unit
}

internal object NO_CLI_FAULTS : CliFaults

internal class ProtectionPipeline(
    private val runtimeBundleProvider: RuntimeBundleProvider,
    private val millisClock: MillisClock = MillisClock(System::nanoTime),
    private val instantClock: InstantClock = InstantClock(Instant::now),
    private val cancellationProbe: CancellationProbe = SystemCancellationProbe,
    private val faults: CliFaults = NO_CLI_FAULTS,
    private val shutdownHooks: ShutdownHooks = SystemShutdownHooks,
) {
    fun execute(arguments: ProtectArguments): CliExecution {
        val startedAt = instantClock.now()
        var paths: InvocationPaths? = null
        var workspace: Path? = null
        var inputHash: ByteArray? = null
        var inputBytes: Long? = null
        var inspection: ApkInspection? = null
        var signer: SignerPolicyV1? = null
        var containerBuild: ContainerBuildResult? = null
        var verification: OutputVerification? = null
        val outputPublished = AtomicBoolean(false)
        val transactionCommitted = AtomicBoolean(false)
        var shutdownHook: Thread? = null
        val stages = ArrayList<StageRecord>()
        try {
            paths = PathPolicy.validate(arguments)
            inputHash = sha256(paths.input)
            inputBytes = Files.size(paths.input)
            workspace = PathPolicy.createWorkspace(paths.output.parent)
            val ownedWorkspace = workspace
            val ownedOutput = paths.output
            shutdownHook = Thread({
                if (!transactionCommitted.get()) {
                    if (outputPublished.get()) runCatching { Files.deleteIfExists(ownedOutput) }
                    PathPolicy.deleteOwnedTree(ownedWorkspace)
                }
            }, "ah-cli-shutdown-cleanup").also(shutdownHooks::add)

            inspection = stage(PipelineStage.INSPECT, stages) { ApkInspector().inspect(paths.input) }
            signer = stage(PipelineStage.SIGNER, stages) { SignerPolicyVerifier().verify(paths.input, inspection) }
            val ahdcInspection = containerInspection(inspection)
            val transformed = stage(PipelineStage.MANIFEST, stages) {
                val manifest = readManifest(paths.input, inspection)
                BinaryManifestTransformer.transform(manifest, ManifestTransformRequest(inspection.manifest))
            }
            val containerPath = workspace.resolve("payload.ahdc")
            containerBuild = stage(PipelineStage.CONTAINER, stages) {
                DexContainerBuilder(paths.input).build(ahdcInspection, signer, containerPath)
            }
            val bundle = try {
                runtimeBundleProvider.load()
            } catch (_: RuntimeBundleUnavailable) {
                throw CliFailure(70, "INTERNAL_RUNTIME_BUNDLE_UNAVAILABLE", PipelineStage.PACKAGE, "runtime.bundle", ResultStatus.FAILED)
            }
            verification = stage(PipelineStage.PACKAGE, stages) {
                ApkRepacker().repack(
                    RepackRequest(
                        paths.input,
                        paths.output,
                        ahdcInspection,
                        signer,
                        transformed,
                        containerPath,
                        containerBuild.descriptor,
                        bundle,
                        containerBuild.keyPackagingPlan,
                    ),
                )
            }
            outputPublished.set(true)
            completedCompositeStage(PipelineStage.VERIFY, stages)
            val finalHash = sha256(paths.input)
            if (!MessageDigest.isEqual(inputHash, finalHash)) {
                throw CliFailure(10, "INPUT_CHANGED", PipelineStage.PUBLISH, "input.changed", ResultStatus.REJECTED)
            }
            completedCompositeStage(PipelineStage.PUBLISH, stages)
            val successBytes = ReportV1Writer.write(
                snapshot(
                    ResultStatus.SUCCESS,
                    null,
                    startedAt,
                    paths,
                    inputHash,
                    inspection,
                    signer,
                    containerBuild,
                    verification,
                    stages,
                    emptyList(),
                    inputBytes,
                ),
            )
            faults.beforeReportPublish(paths.report, true)
            PathPolicy.publishReport(successBytes, paths.report)
            transactionCommitted.set(true)
            return CliExecution(0, ResultStatus.SUCCESS, null, paths.reportBasename)
        } catch (failure: Throwable) {
            val mapped = mapFailure(failure)
            if (outputPublished.get()) {
                runCatching { paths?.output?.let { output -> Files.deleteIfExists(output) } }
                outputPublished.set(false)
            }
            containerBuild?.keyPackagingPlan?.let { plan -> runCatching { plan.close() } }
            val validPaths = paths
            if (validPaths != null) {
                val completedIndex = stages.indexOfLast { it.id == mapped.stage && it.status == "success" }
                if (completedIndex >= 0) {
                    stages[completedIndex] = StageRecord(mapped.stage, "failed", stages[completedIndex].durationMillis)
                } else if (stages.none { it.id == mapped.stage && it.status == "failed" }) {
                    stages += StageRecord(mapped.stage, "failed", 0)
                }
                val reportBytes = ReportV1Writer.write(
                    snapshot(
                        mapped.status,
                        mapped.errorCode,
                        startedAt,
                        validPaths,
                        inputHash,
                        inspection,
                        signer,
                        containerBuild,
                        null,
                        stages,
                        listOf(ReportError(mapped.errorCode, mapped.stage, mapped.messageId)),
                        inputBytes,
                    ),
                )
                try {
                    faults.beforeReportPublish(validPaths.report, false)
                    PathPolicy.publishReport(reportBytes, validPaths.report)
                    transactionCommitted.set(true)
                } catch (reportFailure: Throwable) {
                    val reportMapped = mapFailure(reportFailure)
                    return CliExecution(reportMapped.exitCode, reportMapped.status, reportMapped.errorCode, validPaths.reportBasename)
                }
            }
            return CliExecution(mapped.exitCode, mapped.status, mapped.errorCode, validPaths?.reportBasename ?: "-")
        } finally {
            shutdownHook?.let { hook -> runCatching { shutdownHooks.remove(hook) } }
            PathPolicy.deleteOwnedTree(workspace)
            inputHash?.fill(0)
        }
    }

    private fun snapshot(
        status: ResultStatus,
        errorCode: String?,
        startedAt: Instant,
        paths: InvocationPaths,
        inputHash: ByteArray?,
        inspection: ApkInspection?,
        signer: SignerPolicyV1?,
        containerBuild: ContainerBuildResult?,
        verification: OutputVerification?,
        stages: List<StageRecord>,
        errors: List<ReportError>,
        inputBytes: Long?,
    ): ReportSnapshot = ReportSnapshot(
        status,
        errorCode,
        startedAt,
        instantClock.now(),
        paths,
        inputHash?.toHex(),
        inspection,
        signer,
        containerBuild?.descriptor,
        verification,
        ArrayList(stages),
        errors,
        inputBytes,
        if (verification != null) runCatching { Files.size(paths.output) }.getOrNull() else null,
    )

    private fun <T> stage(id: PipelineStage, stages: MutableList<StageRecord>, action: () -> T): T {
        checkCancellation(id)
        val start = millisClock.now()
        return try {
            faults.beforeStage(id)
            val result = action()
            faults.afterStage(id)
            checkCancellation(id)
            stages += StageRecord(id, "success", elapsed(start))
            result
        } catch (failure: Throwable) {
            stages += StageRecord(id, "failed", elapsed(start))
            throw failure
        }
    }

    private fun completedCompositeStage(id: PipelineStage, stages: MutableList<StageRecord>) {
        checkCancellation(id)
        faults.beforeStage(id)
        faults.afterStage(id)
        checkCancellation(id)
        stages += StageRecord(id, "success", 0)
    }

    private fun checkCancellation(stage: PipelineStage) {
        if (cancellationProbe.cancelled()) {
            throw CliFailure(70, "INTERNAL_CANCELLED", stage, "internal.cancelled", ResultStatus.FAILED)
        }
    }

    private fun elapsed(start: Long): Long = (millisClock.now() - start).coerceAtLeast(0) / 1_000_000L

    private fun readManifest(input: Path, inspection: ApkInspection): ByteArray = try {
        val expected = inspection.zipEntries.single { it.name == MANIFEST_NAME }
        if (expected.uncompressedSize !in 1..MAX_MANIFEST_BYTES) {
            throw CliFailure(12, "AXML_LIMIT_EXCEEDED", PipelineStage.MANIFEST, "manifest.limit", ResultStatus.REJECTED)
        }
        ZipFile(input.toFile()).use { zip ->
            val entry = zip.getEntry(MANIFEST_NAME) ?: manifestReadFailure()
            if (entry.size != expected.uncompressedSize || entry.crc != expected.crc32 || entry.method != expected.method) {
                manifestReadFailure()
            }
            val bytes = zip.getInputStream(entry).use { stream -> stream.readNBytes(expected.uncompressedSize.toInt() + 1) }
            if (bytes.size.toLong() != expected.uncompressedSize || CRC32().apply { update(bytes) }.value != expected.crc32) {
                manifestReadFailure()
            }
            bytes
        }
    } catch (failure: CliFailure) {
        throw failure
    } catch (_: IOException) {
        manifestReadFailure()
    }

    private fun manifestReadFailure(): Nothing =
        throw CliFailure(12, "AXML_INPUT_CHANGED", PipelineStage.MANIFEST, "manifest.input", ResultStatus.REJECTED)

    /** M1-01 exposes one-based APK DEX ordinals; AHDC v2 records are explicitly zero-based. */
    private fun containerInspection(inspection: ApkInspection): ApkInspection = ApkInspection(
        inspection.inputSha256,
        inspection.manifest,
        inspection.zipEntries,
        inspection.dexEntries.map { summary ->
            DexSummary(summary.entryName, summary.ordinal - 1, summary.fileSize, summary.classCount, summary.sha256)
        },
        inspection.nativeAbis,
        inspection.findings,
        inspection.compatibilityRulesVersion,
        inspection.limitsApplied,
    )

    private fun mapFailure(failure: Throwable): CliFailure = when (failure) {
        is CliFailure -> failure
        is InspectionException -> CliFailure(10, failure.code.name, PipelineStage.INSPECT, "inspection.${failure.code.name.lowercase()}", ResultStatus.REJECTED)
        is SignerPolicyException -> CliFailure(11, failure.code.name, PipelineStage.SIGNER, "signer.${failure.code.name.lowercase()}", ResultStatus.REJECTED)
        is AxmlTransformException -> CliFailure(12, failure.code.name, PipelineStage.MANIFEST, "manifest.${failure.code.name.lowercase()}", ResultStatus.REJECTED)
        is ContainerException -> CliFailure(13, failure.code.name, PipelineStage.CONTAINER, "container.${failure.code.name.lowercase()}", ResultStatus.FAILED)
        is PackageException -> mapPackageFailure(failure)
        is OutOfMemoryError -> CliFailure(70, "INTERNAL_RESOURCE_EXHAUSTED", PipelineStage.PUBLISH, "internal.resource", ResultStatus.FAILED)
        else -> CliFailure(70, "INTERNAL_UNEXPECTED", PipelineStage.PUBLISH, "internal.unexpected", ResultStatus.FAILED)
    }

    private fun mapPackageFailure(failure: PackageException): CliFailure {
        val outputFailure = failure.code.name.startsWith("OUTPUT_") || failure.code == PackageErrorCode.PACKAGE_ALIGNMENT
        val stage = when (failure.code) {
            PackageErrorCode.OUTPUT_VERIFICATION_FAILED, PackageErrorCode.PACKAGE_ALIGNMENT -> PipelineStage.VERIFY
            PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, PackageErrorCode.OUTPUT_ALREADY_EXISTS,
            PackageErrorCode.OUTPUT_PATH_ALIAS, PackageErrorCode.OUTPUT_INPUT_CHANGED -> PipelineStage.PUBLISH
            else -> PipelineStage.PACKAGE
        }
        return CliFailure(
            if (outputFailure) 15 else 14,
            failure.code.name,
            stage,
            "package.${failure.code.name.lowercase()}",
            ResultStatus.FAILED,
        )
    }

    private fun sha256(path: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            buffer.fill(0)
        }
        return digest.digest()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val MANIFEST_NAME = "AndroidManifest.xml"
        const val MAX_MANIFEST_BYTES = 4L * 1024 * 1024
    }
}
