package ah.host.inspector

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

class ApkInspector internal constructor(
    private val afterInitialHash: ((Path) -> Unit)? = null,
) {
    constructor() : this(null)

    fun inspect(input: Path): ApkInspection {
        val safeFileName = safeFileName(input)
        val initialHash = hashInput(input, safeFileName)
        afterInitialHash?.invoke(input)
        var result: ApkInspection? = null
        var failure: InspectionException? = null
        try {
            result = inspectOpen(input, safeFileName, initialHash)
        } catch (exception: InspectionException) {
            failure = exception
        } catch (exception: IOException) {
            failure = InspectionException(InspectionErrorCode.INPUT_IO, safeFileName, cause = exception)
        } catch (exception: SecurityException) {
            failure = InspectionException(InspectionErrorCode.INPUT_IO, safeFileName, cause = exception)
        } catch (exception: LimitFailure) {
            failure = InspectionException(
                InspectionErrorCode.INPUT_LIMIT_EXCEEDED,
                safeFileName,
                limitName = exception.limit,
                cause = exception,
            )
        } catch (exception: DuplicateFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_DUPLICATE_ENTRY, safeFileName, cause = exception)
        } catch (exception: PathFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_PATH_UNSAFE, safeFileName, cause = exception)
        } catch (exception: ManifestFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_MANIFEST_INVALID, safeFileName, cause = exception)
        } catch (exception: DexFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_DEX_INVALID, safeFileName, cause = exception)
        } catch (exception: InterruptedFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_IO, safeFileName, cause = exception)
        } catch (exception: ParserFailure) {
            failure = InspectionException(InspectionErrorCode.INPUT_ZIP_STRUCTURE, safeFileName, cause = exception)
        }

        val finalHash = try {
            hashInput(input, safeFileName)
        } catch (exception: InspectionException) {
            throw exception
        }
        if (!MessageDigest.isEqual(initialHash, finalHash)) {
            throw InspectionException(InspectionErrorCode.INPUT_CHANGED, safeFileName)
        }
        failure?.let { throw it }
        return result ?: throw InspectionException(InspectionErrorCode.INPUT_IO, safeFileName)
    }

    private fun inspectOpen(input: Path, safeFileName: String, inputHash: ByteArray): ApkInspection {
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            throw InspectionException(InspectionErrorCode.INPUT_IO, safeFileName)
        }
        FileChannel.open(input, StandardOpenOption.READ).use { channel ->
            val source = FileSource(channel)
            if (source.size > InspectionLimits.MAX_APK_BYTES) throw LimitFailure("apkBytes")
            val parser = ZipParser(source)
            val zip = parser.parse()
            return buildInspection(zip, parser, safeFileName, inputHash)
        }
    }

    private fun buildInspection(
        zip: ParsedZip,
        parser: ZipParser,
        safeFileName: String,
        inputHash: ByteArray,
    ): ApkInspection {
        val manifestEntries = zip.entries.filter { it.record.name == MANIFEST_NAME }
        if (manifestEntries.size != 1) throw ManifestFailure()
        val manifestEntry = manifestEntries.single()
        if (manifestEntry.record.uncompressedSize > InspectionLimits.MAX_MANIFEST_BYTES) {
            throw LimitFailure("manifestBytes")
        }
        val manifestPayload = parser.materialize(manifestEntry)
        val manifest = BinaryManifestParser(manifestPayload).parse()

        val dexCandidates = zip.entries.filter { it.record.name.lowercase(Locale.ROOT).endsWith(".dex") }
        if (dexCandidates.isEmpty() || dexCandidates.size > InspectionLimits.MAX_DEX_ENTRIES) {
            if (dexCandidates.size > InspectionLimits.MAX_DEX_ENTRIES) throw LimitFailure("dexEntries")
            throw DexFailure()
        }
        val ordinals = dexCandidates.map { canonicalDexOrdinal(it.record.name) ?: throw DexFailure() }
        if (ordinals != (1..ordinals.size).toList()) throw DexFailure()
        val parsedDex = ArrayList<ParsedDex>(dexCandidates.size)
        for ((index, entry) in dexCandidates.withIndex()) {
            if (entry.record.uncompressedSize > InspectionLimits.MAX_DEX_BYTES) throw LimitFailure("dexBytes")
            val payload = parser.materialize(entry)
            parsedDex += DexParser(entry.record.name, ordinals[index], payload).parse()
        }
        val descriptorMarkerIds = parsedDex.flatMap { it.descriptorMarkerIds }.distinct()
        val entryNames = zip.entries.map { it.record.name }
        CompatibilityRules.evaluate(
            safeFileName,
            manifest,
            entryNames,
            descriptorMarkerIds,
            NativeAbiSummary(emptyList()),
        )
        val nativeLibraries = zip.entries.filter { NATIVE_LIBRARY_PATH.matches(it.record.name) }.map { entry ->
            NativeLibraryHeader(entry.record.name, parser.prefix(entry, ELF_HEADER_PREFIX))
        }
        val nativeAbis = CompatibilityRules.nativeAbis(nativeLibraries, safeFileName)
        val findings = CompatibilityRules.evaluate(
            safeFileName,
            manifest,
            entryNames,
            descriptorMarkerIds,
            nativeAbis,
        )
        return ApkInspection(
            inputSha256 = inputHash,
            manifest = manifest.summary,
            zipEntries = zip.entries.map { it.record },
            dexEntries = parsedDex.map { it.summary },
            nativeAbis = nativeAbis,
            findings = findings,
            compatibilityRulesVersion = CompatibilityRules.VERSION,
            limitsApplied = InspectionLimits.snapshot(),
        )
    }

    private fun hashInput(input: Path, safeFileName: String): ByteArray {
        try {
            if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
                throw InspectionException(InspectionErrorCode.INPUT_IO, safeFileName)
            }
            val size = Files.size(input)
            if (size > InspectionLimits.MAX_APK_BYTES) {
                throw InspectionException(
                    InspectionErrorCode.INPUT_LIMIT_EXCEEDED,
                    safeFileName,
                    limitName = "apkBytes",
                )
            }
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(input, StandardOpenOption.READ).use { stream ->
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest()
        } catch (exception: InspectionException) {
            throw exception
        } catch (exception: IOException) {
            throw InspectionException(InspectionErrorCode.INPUT_IO, safeFileName, cause = exception)
        } catch (exception: SecurityException) {
            throw InspectionException(InspectionErrorCode.INPUT_IO, safeFileName, cause = exception)
        }
    }

    private fun safeFileName(input: Path): String {
        val original = input.fileName?.toString() ?: "input.apk"
        val sanitized = buildString {
            for (character in original.take(MAX_SAFE_FILE_NAME)) {
                append(if (character.isISOControl()) '_' else character)
            }
        }
        return sanitized.ifEmpty { "input.apk" }
    }

    companion object {
        private const val HASH_BUFFER_SIZE = 64 * 1024
        private const val MAX_SAFE_FILE_NAME = 128
        private const val MANIFEST_NAME = "AndroidManifest.xml"
        private const val ELF_HEADER_PREFIX = 20
        private val NATIVE_LIBRARY_PATH = Regex("lib/[^/]+/[^/]+\\.so")
    }
}
