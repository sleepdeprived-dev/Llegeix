package com.david.llegeix.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
     * @param onDownloading called only once the wait is long enough to be a real
     *   download. ML Kit has no cheap "is it there" question — the same call
     *   fetches the models and confirms models already on the device — and a
     *   fresh translator starts every screen not knowing which it will be. Said
     *   immediately, "one-time download over Wi-Fi" flashed up on the first
     *   lookup of every session for a model that had been on the phone for
     *   weeks, which is a sentence that teaches the reader to distrust the app.
     */
    suspend fun ensureModel(requireWifi: Boolean = true, onDownloading: () -> Unit = {}) {
        if (modelReady) return
        downloadLock.withLock {
            if (modelReady) return
            val conditions = DownloadConditions.Builder()
                .apply { if (requireWifi) requireWifi() }
                .build()
            coroutineScope {
                val notice = launch {
                    delay(DOWNLOAD_NOTICE_DELAY_MS)
                    onDownloading()
                }
                try {
                    translator.downloadModelIfNeeded(conditions).await()
                } finally {
                    notice.cancel()
                }
            }
            modelReady = true
        }
    }

    /**
     * Translate a single word. Callers should have awaited [ensureModel]; ML Kit
     * fails rather than downloading implicitly here.
     */
    suspend fun translate(text: String): String = translator.translate(text).await()

    /**
     * What [word] means in [line], when that can be established.
     *
     * Translates the line, then the line without the word, and keeps what the
     * second one lost — see [ContextualGloss] for why that works and when it
     * refuses to answer. [lineTranslation] is passed in because the sheet has
     * usually translated the line already for its own sake; only the ablated
     * line is an extra call, and it is offline like every other.
     *
     * Returns null whenever the reading is not clearly better than the word on
     * its own, including when it merely agrees with it: the sheet has no room
     * for a second line saying the same thing twice.
     */
    suspend fun translateInContext(
        word: String,
        line: String,
        lineTranslation: String,
        plainTranslation: String,
    ): String? {
        val ablated = ContextualGloss.withoutWord(line, word) ?: return null
        val withoutIt = translator.translate(ablated).await()
        val span = ContextualGloss.difference(lineTranslation, withoutIt, targetLanguage) ?: return null
        return span.takeIf { !it.equals(plainTranslation.trim(), ignoreCase = true) }
    }

    override fun close() {
        translator.close()
    }

    private companion object {
        /**
         * Long enough that confirming a model already on the device stays
         * silent, short enough that a real download is announced before the
         * reader concludes nothing is happening.
         */
        const val DOWNLOAD_NOTICE_DELAY_MS = 600L
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
