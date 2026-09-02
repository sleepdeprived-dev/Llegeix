package com.david.llegeix.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Catalan lookup, via ML Kit Translate.
 *
 * Catalan is genuinely supported: `TranslateLanguage.CATALAN` ("ca") is present
 * in the shipped artifact, which is what this was checked against rather than
 * the documentation.
 *
 * Translation runs entirely offline once the models are on the device, but the
 * models are a tens-of-megabytes download on first use, so [ensureModel] is
 * separate from [translate] and the UI reports downloading as its own state
 * rather than leaving the user staring at a spinner.
 */
class WordTranslator(
    val targetLanguage: String = TranslateLanguage.ENGLISH,
    sourceLanguage: String = TranslateLanguage.CATALAN,
) : AutoCloseable {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build(),
    )

    /** Serialises download attempts so several taps cannot start several fetches. */
    private val downloadLock = Mutex()

    @Volatile
    private var modelReady = false

    /** True once the models are present, so the UI can skip the download notice. */
    val isModelReady: Boolean get() = modelReady

    /**
     * Fetch the language models if they are not already present.
     *
     * @param requireWifi avoids a large download over mobile data. Worth keeping
     *   on by default; the caller can retry unmetered if it fails.
     */
    suspend fun ensureModel(requireWifi: Boolean = true) {
        if (modelReady) return
        downloadLock.withLock {
            if (modelReady) return
            val conditions = DownloadConditions.Builder()
                .apply { if (requireWifi) requireWifi() }
                .build()
            translator.downloadModelIfNeeded(conditions).await()
            modelReady = true
        }
    }

    /**
     * Translate a single word. Callers should have awaited [ensureModel]; ML Kit
     * fails rather than downloading implicitly here.
     */
    suspend fun translate(text: String): String = translator.translate(text).await()

    override fun close() {
        translator.close()
    }
}

/**
 * Bridge a Play Services [Task] to a coroutine.
 *
 * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for the
 * two call sites above. Cancelling the coroutine detaches from the task —
 * ML Kit's download cannot itself be cancelled, so the work continues and will
 * simply have completed by the next attempt.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
