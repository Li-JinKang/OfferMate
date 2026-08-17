package com.jk.offermate.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 端侧 ML Kit 文字识别（bundled 中文模型）。
 *
 * 选用中文识别模型：它在识别中文的同时也覆盖**拉丁字母与数字**，适配计算机面经里的
 * 英文术语/代码/数字混排。模型随 APK 打包，离线可用、无需 Google Play 服务、不消耗 LLM token。
 */
class MlKitTextRecognizer : OcrTextRecognizer {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(imageBytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }

    /** 释放底层识别器（不再使用时调用）。 */
    fun close() = recognizer.close()
}
