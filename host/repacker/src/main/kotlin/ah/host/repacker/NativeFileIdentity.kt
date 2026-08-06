package ah.host.repacker

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinDef
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal object NativeFileIdentity {
    fun capture(path: Path, attributes: BasicFileAttributes): String? = when {
        Platform.isWindows() -> captureWindows(path)
        Platform.isLinux() -> attributes.fileKey()?.toString()
        else -> null
    }

    private fun captureWindows(path: Path): String? {
        val handle = Kernel32.INSTANCE.CreateFile(
            path.toString(),
            WinNT.GENERIC_READ,
            WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE,
            null,
            WinNT.OPEN_EXISTING,
            WinNT.FILE_FLAG_BACKUP_SEMANTICS,
            null,
        )
        if (WinBase.INVALID_HANDLE_VALUE == handle) return null
        var identity: String? = null
        try {
            val information = WinBase.FILE_ID_INFO()
            if (Kernel32.INSTANCE.GetFileInformationByHandleEx(
                    handle,
                    WinBase.FileIdInfo,
                    information.pointer,
                    WinDef.DWORD(information.size().toLong()),
                )
            ) {
                information.read()
                identity = "${information.VolumeSerialNumber}:" + information.FileId.Identifier.joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }
            }
        } finally {
            if (!Kernel32.INSTANCE.CloseHandle(handle)) identity = null
        }
        return identity
    }
}
