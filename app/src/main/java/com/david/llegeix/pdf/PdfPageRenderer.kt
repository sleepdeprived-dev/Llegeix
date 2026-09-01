package com.david.llegeix.pdf

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * A word found in a page's text layer.
 *
 * [boundsPx] is in rendered-bitmap pixel space with the origin at the top left,
 * already converted from PDF user space, so the UI can draw a highlight over it
 * without knowing anything about PDF coordinates.
 */
data class PdfWord(
    val text: String,
    val boundsPx: RectF,
)

/**
 * Renders the pages of one open PDF and answers questions about their text.
 *
 * The text half is what forced the backend choice: Android's built-in
 * PdfRenderer produces bitmaps and exposes no text at all, so it cannot support
 * tap-to-look-up. [PdfiumPageRenderer] is the implementation.
 */
interface PdfPageRenderer : AutoCloseable {

    val pageCount: Int

    /**
     * Rasterise one page to [targetWidthPx], preserving the page's aspect ratio.
     * Implementations must be safe to call from any thread.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap

    /**
     * The word under a point, or null where the page has no selectable text
     * there — a scanned page, an image, or simply blank space.
     *
     * The point and the rendered size are both in bitmap pixels, so callers work
     * entirely in the coordinate space they already drew in.
     */
    suspend fun wordAt(
        pageIndex: Int,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): PdfWord?
}

/**
 * A4 portrait, used to size the placeholder before a page's real dimensions are
 * known. Only affects the brief moment before the first bitmap arrives.
 */
const val DEFAULT_PAGE_ASPECT_RATIO: Float = 1f / 1.414f
