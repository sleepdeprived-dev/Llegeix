package com.david.llegeix.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

/*
 * The PDFium calls [PdfiumPageRenderer] makes, and nothing else.
 *
 * Both platforms run the same engine. The phone reaches it through
 * pdfiumandroid, as it always has; the Mac calls the library itself, built by
 * pdfium-binaries (tools/macos/fetch-pdfium.sh). Everything above this line —
 * cropping, the word under a finger, selections, search — is written once and
 * behaves the same on both.
 *
 * Coordinates are PDFium's own: points, y-up from the bottom left of the page.
 * Boxes come back exactly as PDFium reports them, edges in whichever order it
 * chose; the renderer normalises them with min and max.
 *
 * None of these is safe for concurrent use. The renderer holds a lock around
 * every call.
 */

/** One open document. */
interface PdfiumDocument : AutoCloseable {
    val pageCount: Int

    /** The page at [index], or null for a page PDFium cannot parse. */
    fun openPage(index: Int): PdfiumPage?

    /** The document's bookmarks, nested as the document nests them. */
    fun tableOfContents(): List<PdfiumBookmark>
}

/** One entry of a document's own table of contents. */
data class PdfiumBookmark(
    val title: String?,
    val pageIndex: Int,
    val children: List<PdfiumBookmark>,
)

/** One open page. */
interface PdfiumPage : AutoCloseable {
    val widthPt: Double
    val heightPt: Double

    /**
     * A [width] × [height] picture of the page on white, with the whole page
     * drawn [drawWidth] × [drawHeight] and its top left corner at
     * ([startX], [startY]) — negative to leave part of it off the edges, which
     * is how a crop is drawn without rasterising what it trims. Annotations
     * are drawn too.
     */
    fun render(
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        drawWidth: Int,
        drawHeight: Int,
    ): ImageBitmap

    fun openTextPage(): PdfiumTextPage
}

/** The text layer of one page. */
interface PdfiumTextPage : AutoCloseable {
    val charCount: Int

    /** The character within the tolerances of a point, or -1. */
    fun charIndexAt(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int

    fun charAt(index: Int): Char

    /** [count] characters from [start], or null when PDFium has none to give. */
    fun text(start: Int, count: Int): String?

    fun charBox(index: Int): Rect?

    /**
     * How many rectangles the range covers, one per stretch of a line. It is
     * also what makes [rect] answer for that range.
     */
    fun countRects(start: Int, count: Int): Int

    fun rect(index: Int): Rect?

    /**
     * Where [term] occurs, case-insensitively and anywhere inside a word, as
     * (first character, length), at most [limit] of them.
     */
    fun find(term: String, limit: Int): List<Pair<Int, Int>>
}
