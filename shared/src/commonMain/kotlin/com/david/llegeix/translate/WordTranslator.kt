package com.david.llegeix.translate

/**
 * On-device translation between Catalan, Romanian and English.
 *
 * The phone uses ML Kit; the Mac uses Bergamot, the offline translator behind
 * Firefox's translations. Both keep the models on the device after a one-time
 * download, so [ensureModel] is separate from [translate] and the UI can report
 * downloading as its own state.
 *
 * Language codes are two-letter ISO codes: "ca", "ro", "en".
 */
expect class WordTranslator(
    targetLanguage: String = "en",
    sourceLanguage: String = "ca",
) : AutoCloseable {

    val targetLanguage: String

    /** True once the models are present, so the UI can skip the download notice. */
    val isModelReady: Boolean

    /**
     * Fetch the language models if they are not already present.
     *
     * @param requireWifi avoids a large download over mobile data, on the phone.
     * @param onDownloading called only once the wait is long enough to be a real
     *   download, so confirming a model already present stays silent.
     */
    suspend fun ensureModel(requireWifi: Boolean = true, onDownloading: () -> Unit = {})

    /** Translate a word or a line. Callers should have awaited [ensureModel]. */
    suspend fun translate(text: String): String

    /**
     * What [word] means in [line], when that can be established; null whenever
     * the reading is not clearly better than the word on its own. See
     * [ContextualGloss].
     */
    suspend fun translateInContext(
        word: String,
        line: String,
        lineTranslation: String,
        plainTranslation: String,
    ): String?

    override fun close()
}

/**
 * Long enough that confirming a model already on the device stays silent,
 * short enough that a real download is announced before the reader concludes
 * nothing is happening.
 */
internal const val DOWNLOAD_NOTICE_DELAY_MS = 600L
