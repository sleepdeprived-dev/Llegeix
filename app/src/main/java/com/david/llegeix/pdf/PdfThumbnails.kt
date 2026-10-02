package com.david.llegeix.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.net.toUri
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * First-page covers for the library, rendered on demand and kept in memory.
 *
 * A cover costs a full document open, so this is deliberately lazy: only the
 * rows actually on screen ask for one, and [Semaphore] caps how many PDFium
 * documents are open at once. Without that cap a fast scroll through a large
 * library would try to open every file at the same moment.
 *
 * Failures are cached as misses rather than retried. A PDF that will not open
 * will not open the second time either, and retrying on every recomposition
 * would turn one broken file into a permanent background load.
 */
class PdfThumbnails(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** URIs already known not to render, so they are not attempted again. */
    private val failed = mutableSetOf<String>()

    private val openLimit = Semaphore(MAX_CONCURRENT_OPENS)

    fun cached(uriString: String, widthPx: Int): Bitmap? = cache.get(key(uriString, widthPx))

    /**
     * The cover for [uriString], rendering it if this is the first request.
     * Null when the document cannot be opened or has no pages.
     */
    suspend fun load(uriString: String, widthPx: Int): Bitmap? {
        if (widthPx <= 0) return null
        val key = key(uriString, widthPx)
        cache.get(key)?.let { return it }
        synchronized(failed) { if (uriString in failed) return null }

        val bitmap = openLimit.withPermit {
            // Re-check inside the permit: several rows can queue on the same
            // document while the first one is still rendering it.
            cache.get(key)?.let { return@withPermit it }
            runCatchingCancellable {
                PdfiumPageRenderer.open(context, uriString.toUri()).use { renderer ->
                    if (renderer.pageCount <= 0) null else renderer.renderPage(0, widthPx).asAndroidBitmap()
                }
            }.getOrNull()
        }

        if (bitmap == null) {
            synchronized(failed) { failed += uriString }
        } else {
            cache.put(key, bitmap)
        }
        return bitmap
    }

    private fun key(uriString: String, widthPx: Int): String = "$uriString@$widthPx"

    private companion object {
        /**
         * Covers are small, so a few megabytes holds a long list of them. Sized
         * in bytes rather than entries because a grid cover and a list cover are
         * very different weights.
         */
        const val CACHE_BYTES = 12 * 1024 * 1024

        /** PDFium opens are heavy; four at once keeps scrolling responsive. */
        const val MAX_CONCURRENT_OPENS = 4
    }

    /** Drop every cached cover and every remembered failure. */
    fun clear() {
        cache.evictAll()
        synchronized(failed) { failed.clear() }
    }
}
