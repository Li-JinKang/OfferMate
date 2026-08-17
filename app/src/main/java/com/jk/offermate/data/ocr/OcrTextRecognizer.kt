package com.jk.offermate.data.ocr

/**
 * 图片文字识别（OCR）抽象。用于把"图片面经"转为文本，再进入抽题流水线。
 *
 * 抽象成接口便于替换实现与测试：生产用端侧 ML Kit，测试可用 fake。
 */
interface OcrTextRecognizer {
    /**
     * 识别一张图片中的文字。
     * @param imageBytes 图片原始字节（jpg/png 等）。
     * @return 识别出的文本（按阅读顺序拼接）；无法解码或无文字时返回空串。
     */
    suspend fun recognize(imageBytes: ByteArray): String
}
