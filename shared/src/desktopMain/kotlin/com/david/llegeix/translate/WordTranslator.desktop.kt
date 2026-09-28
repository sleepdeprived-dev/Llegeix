package com.david.llegeix.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The Mac's translator: Bergamot, through English where it has to be (see [Bergamot]). */
actual class WordTranslator actual constructor(
    actual val targetLanguage: String,
    sourceLanguage: String,
) : AutoCloseable {

    private val route = Bergamot.route(sourceLanguage, targetLanguage)

    private val downloadLock = Mutex()

    actual val isModelReady: Boolean get() = route.all(Bergamot::isInstalled)

    /** Wi-Fi is the phone's concern; a Mac downloads over whatever it has. */
    actual suspend fun ensureModel(requireWifi: Boolean, onDownloading: () -> Unit) {
        if (isModelReady) return
        downloadLock.withLock {
            if (isModelReady) return
            coroutineScope {
                val notice = launch {
                    delay(DOWNLOAD_NOTICE_DELAY_MS)
                    onDownloading()
                }
                try {
                    withContext(Dispatchers.IO) { Bergamot.install(route) }
                } finally {
                    notice.cancel()
                }
            }
        }
    }

    actual suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        route.fold(text) { acc, pair -> Bergamot.run(pair, acc) }
    }

    actual suspend fun translateInContext(
        word: String,
        line: String,
        lineTranslation: String,
        plainTranslation: String,
    ): String? {
        val ablated = ContextualGloss.withoutWord(line, word) ?: return null
        val withoutIt = translate(ablated)
        val span = ContextualGloss.difference(lineTranslation, withoutIt, targetLanguage) ?: return null
        return span.takeIf { !it.equals(plainTranslation.trim(), ignoreCase = true) }
    }

    actual override fun close() = Unit
}
