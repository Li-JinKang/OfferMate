package com.jk.offermate.data.resume

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 把用户选中的简历文件复制到应用内部存储，得到一个稳定路径用于预览渲染
 * （SAF 的 Uri 权限是临时的，重启后不可用，故需落地为本地文件）。
 */
class ResumeFileStore(private val context: Context) {

    suspend fun copyToInternal(uri: Uri): String = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, RESUME_FILE_NAME)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取所选文件")
        target.absolutePath
    }

    private companion object {
        const val RESUME_FILE_NAME = "resume.pdf"
    }
}
