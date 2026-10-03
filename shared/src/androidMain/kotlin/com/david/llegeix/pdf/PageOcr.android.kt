package com.david.llegeix.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toComposeRect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun pageTextRecognizer(): PageTextRecognizer = MlKitRecognizer()

/** ML Kit's Latin recogniser, on the device, as the reader has always used it. */
private class MlKitRecognizer : PageTextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognise(image: ImageBitmap): List<RecognisedLine> {
        val text = recognizer.process(InputImage.fromBitmap(image.asAndroidBitmap(), 0)).await()
        return text.textBlocks.flatMap { it.lines }.map { line ->
            RecognisedLine(
                text = line.text,
                box = line.boundingBox?.toComposeRect(),
                words = line.elements.mapNotNull { element ->
                    element.boundingBox?.let { RecognisedWord(element.text, it.toComposeRect()) }
                },
            )
        }
    }

    override fun close() = recognizer.close()
}

/** Bridges a Play Services task to a coroutine, cancellably. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) {
            continuation.resumeWithException(error)
        } else {
            @Suppress("UNCHECKED_CAST")
            continuation.resume(task.result as T)
        }
    }
}
