package dev.snapledger.desktop

import dev.snapledger.ui.PlatformFiles
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native Windows open/save dialogs via AWT. */
class DesktopFiles : PlatformFiles {
    override suspend fun saveBackup(
        bytes: ByteArray,
        suggestedName: String,
    ): String? {
        val file = choose(FileDialog.SAVE, suggestedName) ?: return null
        val target = if (file.name.endsWith(".slbackup")) file else File(file.parentFile, file.name + ".slbackup")
        target.writeBytes(bytes)
        return target.absolutePath
    }

    override suspend fun openBackup(): ByteArray? = choose(FileDialog.LOAD, null)?.readBytes()

    private fun choose(
        mode: Int,
        suggestedName: String?,
    ): File? {
        val dialog = FileDialog(null as Frame?, if (mode == FileDialog.SAVE) "导出备份" else "选择备份文件", mode)
        dialog.file = suggestedName ?: "*.slbackup"
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
