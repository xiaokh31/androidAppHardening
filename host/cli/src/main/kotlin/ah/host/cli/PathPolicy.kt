package ah.host.cli

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

internal object PathPolicy {
    fun validate(arguments: ProtectArguments): InvocationPaths {
        val input = arguments.input.toAbsolutePath().normalize()
        val output = arguments.output.toAbsolutePath().normalize()
        val report = arguments.report.toAbsolutePath().normalize()
        try {
            if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(input)) {
                inputFailure("INPUT_NOT_READABLE")
            }
            if (input == output || input == report || output == report) outputFailure("OUTPUT_PATH_ALIAS")
            val outputExists = Files.exists(output, LinkOption.NOFOLLOW_LINKS)
            val reportExists = Files.exists(report, LinkOption.NOFOLLOW_LINKS)
            if (outputExists && Files.isSameFile(input, output)) outputFailure("OUTPUT_PATH_ALIAS")
            if (reportExists && Files.isSameFile(input, report)) outputFailure("OUTPUT_PATH_ALIAS")
            if (outputExists && reportExists && Files.isSameFile(output, report)) outputFailure("OUTPUT_PATH_ALIAS")
            if (outputExists) outputFailure("OUTPUT_ALREADY_EXISTS")
            if (reportExists) outputFailure("REPORT_ALREADY_EXISTS")
            val inputReal = input.toRealPath(LinkOption.NOFOLLOW_LINKS)
            val outputResolved = resolveAbsent(output)
            val reportResolved = resolveAbsent(report)
            if (inputReal == outputResolved || inputReal == reportResolved || outputResolved == reportResolved) {
                outputFailure("OUTPUT_PATH_ALIAS")
            }
            return InvocationPaths(
                input,
                output,
                report,
                inputReal,
                outputResolved,
                reportResolved,
                safeBasename(input, "input.apk"),
                safeBasename(output, "output-unsigned.apk"),
                safeBasename(report, "report.json"),
                pathToken("input", safeBasename(input, "input.apk")),
                pathToken("output", safeBasename(output, "output-unsigned.apk")),
                pathToken("report", safeBasename(report, "report.json")),
            )
        } catch (failure: CliFailure) {
            throw failure
        } catch (_: IOException) {
            outputFailure("OUTPUT_PATH_INVALID")
        } catch (_: SecurityException) {
            outputFailure("OUTPUT_PATH_INVALID")
        }
    }

    fun createWorkspace(outputParent: Path): Path {
        val parent = outputParent.toRealPath(LinkOption.NOFOLLOW_LINKS)
        return try {
            val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            Files.createTempDirectory(parent, ".ah-cli-", permissions)
        } catch (_: UnsupportedOperationException) {
            Files.createTempDirectory(parent, ".ah-cli-")
        }
    }

    fun publishReport(
        bytes: ByteArray,
        target: Path,
        onTempChanged: (Path?) -> Unit = {},
        onPublished: () -> Unit = {},
    ): Path {
        val parent = target.parent ?: outputFailure("REPORT_PARENT_INVALID")
        var temp: Path? = null
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) outputFailure("REPORT_ALREADY_EXISTS")
            temp = Files.createTempFile(parent, ".ah-report-", ".json")
            onTempChanged(temp)
            java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) outputFailure("REPORT_ALREADY_EXISTS")
            // A same-directory hard link publishes the fully fsynced inode atomically and has
            // create-new/no-replace semantics on both supported hosts. Never fall back to an
            // ATOMIC_MOVE whose Windows provider may replace a raced destination.
            Files.createLink(target, temp)
            onPublished()
            try {
                Files.delete(temp)
                temp = null
                onTempChanged(null)
            } catch (failure: IOException) {
                runCatching { Files.deleteIfExists(target) }
                throw failure
            }
            return target
        } catch (_: FileAlreadyExistsException) {
            outputFailure("REPORT_ALREADY_EXISTS")
        } catch (_: IOException) {
            outputFailure("REPORT_PUBLISH_FAILED")
        } catch (_: UnsupportedOperationException) {
            outputFailure("REPORT_PUBLISH_FAILED")
        } catch (_: SecurityException) {
            outputFailure("REPORT_PUBLISH_FAILED")
        } finally {
            temp?.let { runCatching { Files.deleteIfExists(it) } }
            onTempChanged(null)
        }
    }

    fun deleteOwnedTree(path: Path?): Boolean {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        val deleted = runCatching {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { current -> Files.deleteIfExists(current) }
            }
        }.isSuccess
        return deleted && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
    }

    private fun resolveAbsent(path: Path): Path {
        val parent = path.parent ?: outputFailure("OUTPUT_PARENT_INVALID")
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(parent)) {
            outputFailure("OUTPUT_PARENT_INVALID")
        }
        return parent.toRealPath(LinkOption.NOFOLLOW_LINKS).resolve(path.fileName.toString()).normalize()
    }

    private fun safeBasename(path: Path, fallback: String): String {
        val value = path.fileName?.toString().orEmpty()
        val safe = buildString {
            value.take(128).forEach { character -> append(if (character.isISOControl()) '_' else character) }
        }
        return safe.ifEmpty { fallback }
    }

    private fun pathToken(role: String, basename: String): String =
        sha256("$role:$basename".toByteArray(Charsets.UTF_8)).toHex()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun inputFailure(code: String): Nothing = throw CliFailure(10, code, PipelineStage.INSPECT, "input.path", ResultStatus.REJECTED)

    private fun outputFailure(code: String): Nothing = throw CliFailure(15, code, PipelineStage.PUBLISH, "output.path", ResultStatus.FAILED)
}
