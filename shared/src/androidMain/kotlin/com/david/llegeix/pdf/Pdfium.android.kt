package com.david.llegeix.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Open [uri] for rendering. Throws [IOException] when the document cannot be
 * read — a revoked grant, a deleted file, or a PDF PDFium rejects as malformed
 * or password-protected.
 */
suspend fun PdfiumPageRenderer.Companion.open(context: Context, uri: Uri): PdfiumPageRenderer =
    withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Could not open $uri")
        try {
            val core = PdfiumCore(context)
            PdfiumPageRenderer(AndroidPdfiumDocument(core.newDocument(descriptor)), release = descriptor::close)
        } catch (error: Throwable) {
            runCatching { descriptor.close() }
            throw error
        }
    }

/** PDFium on the phone, through pdfiumandroid, as the reader has always used it. */
private class AndroidPdfiumDocument(private val document: PdfDocument) : PdfiumDocument {

    override val pageCount: Int = document.getPageCount()

    override fun openPage(index: Int): PdfiumPage? = document.openPage(index)?.let(::AndroidPdfiumPage)

    override fun tableOfContents(): List<PdfiumBookmark> = document.getTableOfContents().map { it.toBookmark() }

    private fun Bookmark.toBookmark(): PdfiumBookmark =
        PdfiumBookmark(title, pageIdx.toInt(), children.map { it.toBookmark() })

    override fun close() = document.close()
}

private class AndroidPdfiumPage(private val page: PdfPage) : PdfiumPage {

    override val widthPt: Double get() = page.getPageWidthPoint().toDouble()
    override val heightPt: Double get() = page.getPageHeightPoint().toDouble()

    override fun render(
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        drawWidth: Int,
        drawHeight: Int,
    ): ImageBitmap {
        val bitmap = createBitmap(width, height)
        // PDFium composites only the page's own marks, so anything it does not
        // paint would stay transparent and read as black.
        Canvas(bitmap).drawColor(Color.WHITE)
        page.renderPageBitmap(bitmap, startX, startY, drawWidth, drawHeight, renderAnnot = true)
        return bitmap.asImageBitmap()
    }

    override fun openTextPage(): PdfiumTextPage = AndroidPdfiumTextPage(page.openTextPage())

    override fun close() = page.close()
}

private class AndroidPdfiumTextPage(private val text: PdfTextPage) : PdfiumTextPage {

    override val charCount: Int get() = text.textPageCountChars()

    override fun charIndexAt(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int =
        text.textPageGetCharIndexAtPos(x, y, xTolerance, yTolerance)

    override fun charAt(index: Int): Char = text.textPageGetUnicode(index)

    override fun text(start: Int, count: Int): String? = text.textPageGetText(start, count)

    override fun charBox(index: Int): Rect? =
        text.textPageGetCharBox(index)?.let { Rect(it.left, it.top, it.right, it.bottom) }

    override fun countRects(start: Int, count: Int): Int = text.textPageCountRects(start, count)

    override fun rect(index: Int): Rect? =
        text.textPageGetRect(index)?.let { Rect(it.left, it.top, it.right, it.bottom) }

    override fun find(term: String, limit: Int): List<Pair<Int, Int>> =
        // No flags: case-insensitive, substring.
        // close() alone. FindResult.close() and closeFind() both call the same
        // native closeFind on the same handle, so calling both is a double free
        // and aborts the process inside the allocator.
        text.findStart(term, emptySet(), 0)?.use { find ->
            buildList {
                while (size < limit && find.findNext()) {
                    val index = find.getSchResultIndex()
                    val count = find.getSchCount()
                    if (index >= 0 && count > 0) add(index to count)
                }
            }
        }.orEmpty()

    override fun close() = text.close()
}
