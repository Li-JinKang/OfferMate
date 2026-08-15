package com.jk.offermate.data.resume

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 PdfBox-Android 的 PDF 文本提取（端侧）。需在 Application 中调用 PDFBoxResourceLoader.init。
 */
class PdfBoxResumeTextExtractor(private val context: Context) : ResumeTextExtractor {

    override suspend fun extractText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                PDFTextStripper().getText(doc).trim()
            }
        } ?: ""
    }
}
