package ah.host.repacker

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.WString
import java.nio.file.Path

internal object NativeAtomicPublisher {
    fun moveNoReplace(source: Path, destination: Path) {
        try {
            when {
                Platform.isWindows() -> moveWindows(source, destination)
                Platform.isLinux() -> moveLinux(source, destination)
                else -> packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "platform")
            }
        } catch (_: LinkageError) {
            packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "atomicMove")
        } catch (_: SecurityException) {
            packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "atomicMove")
        }
    }

    private fun moveWindows(source: Path, destination: Path) {
        val moved = WindowsKernel.INSTANCE.MoveFileExW(
            WString(source.toString()),
            WString(destination.toString()),
            MOVEFILE_WRITE_THROUGH,
        )
        if (moved) return
        when (Native.getLastError()) {
            ERROR_FILE_EXISTS, ERROR_ALREADY_EXISTS -> packageFailure(PackageErrorCode.OUTPUT_ALREADY_EXISTS, "outputRace")
            ERROR_NOT_SAME_DEVICE, ERROR_NOT_SUPPORTED ->
                packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "atomicMove")
            else -> packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "atomicMove")
        }
    }

    private fun moveLinux(source: Path, destination: Path) {
        val moved = LinuxLibC.INSTANCE.renameat2(
            AT_FDCWD,
            source.toString(),
            AT_FDCWD,
            destination.toString(),
            RENAME_NOREPLACE,
        )
        if (moved == 0) return
        when (Native.getLastError()) {
            EEXIST -> packageFailure(PackageErrorCode.OUTPUT_ALREADY_EXISTS, "outputRace")
            EXDEV, ENOSYS, EINVAL, EOPNOTSUPP ->
                packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "atomicMove")
            else -> packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "atomicMove")
        }
    }

    private interface WindowsKernel : Library {
        fun MoveFileExW(existing: WString, destination: WString, flags: Int): Boolean

        companion object {
            val INSTANCE: WindowsKernel = Native.load("kernel32", WindowsKernel::class.java)
        }
    }

    private interface LinuxLibC : Library {
        fun renameat2(oldDirectory: Int, oldPath: String, newDirectory: Int, newPath: String, flags: Int): Int

        companion object {
            val INSTANCE: LinuxLibC = Native.load(Platform.C_LIBRARY_NAME, LinuxLibC::class.java)
        }
    }

    private const val MOVEFILE_WRITE_THROUGH = 0x00000008
    private const val ERROR_NOT_SAME_DEVICE = 17
    private const val ERROR_NOT_SUPPORTED = 50
    private const val ERROR_FILE_EXISTS = 80
    private const val ERROR_ALREADY_EXISTS = 183
    private const val AT_FDCWD = -100
    private const val RENAME_NOREPLACE = 1
    private const val EEXIST = 17
    private const val EXDEV = 18
    private const val EINVAL = 22
    private const val ENOSYS = 38
    private const val EOPNOTSUPP = 95
}
