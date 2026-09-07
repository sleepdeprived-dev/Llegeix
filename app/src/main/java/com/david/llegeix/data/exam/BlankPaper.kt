package com.david.llegeix.data.exam

import android.graphics.pdf.PdfDocument
import java.io.File

/**
 * Blank paper, as a real PDF.
 *
 * A writing exam needs somewhere to write, and the reader asked for that
 * somewhere to be *inside the paper* — not a notes screen with its own menus
 * and its own way of getting back. Generating an actual PDF is what makes that
 * possible without a second kind of page existing anywhere in the app: the
 * blank sheets become an ordinary document of the paper, so they are turned to
 * by swiping, written on by the same pen, saved by the same row-per-stroke, and
 * carried into the export by the same code that draws the printed pages.
 *
 * The alternative — a page the renderer knows to draw white — would have meant
 * a null file, a special case in the cache key, a special case in the exporter
 * and a special case in every page-address lookup, for a feature whose entire
 * content is "no content".
 *
 * A4 at 72dpi, which is the unit [PdfDocument] measures in and the shape every
 * exam in Europe is printed on.
 */
object BlankPaper {

    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    /**
     * Write [pageCount] blank A4 pages to [destination], replacing whatever is
     * there.
     *
     * Rewriting the whole file rather than appending to it, because
     * [PdfDocument] cannot append and because it costs nothing: the pages carry
     * no content, so a twenty-page file is a few kilobytes of structure. The
     * reader's work is not in this file and never was — marks are rows in the
     * database, addressed by the document and the page within it — so growing
     * the paper leaves every essay already written exactly where it was.
     */
    fun write(destination: File, pageCount: Int): Boolean = runCatching {
        val document = PdfDocument()
        try {
            repeat(pageCount.coerceAtLeast(1)) { index ->
                val info = PdfDocument.PageInfo
                    .Builder(A4_WIDTH_PT, A4_HEIGHT_PT, index + 1)
                    .create()
                // Started and finished with nothing drawn on it. The page is
                // white because a PDF page with no content is white.
                document.finishPage(document.startPage(info))
            }
            destination.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        true
    }.getOrDefault(false)
}
