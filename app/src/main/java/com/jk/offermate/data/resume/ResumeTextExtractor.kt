package com.jk.offermate.data.resume

import android.net.Uri

/** 从简历文件（PDF）中提取纯文本。 */
interface ResumeTextExtractor {
    suspend fun extractText(uri: Uri): String
}
