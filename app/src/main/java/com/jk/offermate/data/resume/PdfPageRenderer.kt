package com.jk.offermate.data.resume

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把 PDF 文件按页渲染为位图，用于简历预览。端侧、无第三方依赖（用系统 [PdfRenderer]）。
 */
object PdfPageRenderer {

    /** 渲染 [path] 指向的 PDF 的所有页；失败或文件不存在时返回空列表。 */
    suspend fun render(path: String, targetWidth: Int = 1080): List<Bitmap> = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            val scale = targetWidth.toFloat() / page.width
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
