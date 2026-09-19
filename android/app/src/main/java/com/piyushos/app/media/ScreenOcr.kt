package com.piyushos.app.media

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions

/**
 * On-device OCR (ML Kit) - tab use hota hai jab accessibility tree me text kam ho
 * (jaise canvas-based UI: maps, games, kuch media apps).
 */
object ScreenOcr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Screenshot se text lines + boxes nikalta hai. Format: "text"@x1,y1-x2,y2; ... */
    fun ocr(bmp: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bmp, 0)
            val visionText = Tasks.await(recognizer.process(image), 5000, java.util.concurrent.TimeUnit.MILLISECONDS)
            visionText.textBlocks
                .mapNotNull { block ->
                    val t = block.text.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    val box = block.boundingBox
                    if (box == null || box.width() < 8) return@mapNotNull null
                    "\"${t.replace("\"", "'").take(80)}\"@${box.left},${box.top}-${box.right},${box.bottom}"
                }
                .take(60)
                .joinToString("; ")
        } catch (e: Exception) {
            ""
        }
    }
}
