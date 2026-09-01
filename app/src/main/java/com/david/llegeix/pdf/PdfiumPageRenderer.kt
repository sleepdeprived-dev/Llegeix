package com.david.llegeix.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * [PdfPageRenderer] backed by PDFium, the engine Chrome uses.
 *
 * Chosen over PDFBox because this app needs both halves of the job: PDFium
 * renders roughly an order of magnitude faster, and — decisively — its text API
 * answers "which character is at this coordinate", which is exactly the question
 * tap-and-hold has to ask. PDFBox has the nicer text extraction in isolation but
 * a renderer too slow for comfortable paging.
 *
 * The cost is that PDFium hands back *characters*, not words, so word
 * boundaries are worked out here.
 */
class PdfiumPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val document: PdfDocument,
) : PdfPageRenderer {

    /** PDFium is not safe for concurrent use on one document. */
    private val mutex = Mutex()

    override val pageCount: Int = document.getPageCount()

    override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                // PDFium returns null for a page it cannot parse.
                val page = document.openPage(index)
                    ?: throw IOException("Page $index of this PDF could not be opened")
                page.use { page ->
                    val widthPt = page.getPageWidthPoint()
                    val heightPt = page.getPageHeightPoint()
                    val width = targetWidthPx.coerceAtLeast(1)
                    val height = if (widthPt > 0) {
                        (width.toFloat() * heightPt / widthPt).roundToInt().coerceAtLeast(1)
                    } else {
                        width
                    }

                    val bitmap = createBitmap(width, height)
                    // PDFium composites only the page's own marks, so anything
                    // it does not paint would stay transparent and read as black.
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.renderPageBitmap(bitmap, 0, 0, width, height, renderAnnot = true)
                    bitmap
                }
            }
        }

    override suspend fun wordAt(
        pageIndex: Int,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): PdfWord? = withContext(Dispatchers.IO) {
        if (renderedWidthPx <= 0 || renderedHeightPx <= 0) return@withContext null
        mutex.withLock {
            // A page that will not open simply has no word at that point.
            document.openPage(pageIndex)?.use { page ->
                page.openTextPage().use { textPage ->
                    findWord(page, textPage, xPx, yPx, renderedWidthPx, renderedHeightPx)
                }
            }
        }
    }

    /**
     * Locate the word under a point.
     *
     * Extracted from [wordAt] rather than nested inline so the early exits read
     * as plain returns instead of a stack of ambiguous `return@use` labels.
     */
    private fun findWord(
        page: PdfPage,
        textPage: PdfTextPage,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): PdfWord? {
        val widthPt = page.getPageWidthPoint().toDouble()
        val heightPt = page.getPageHeightPoint().toDouble()
        if (widthPt <= 0.0 || heightPt <= 0.0) return null

        // Bitmap pixels run top-left down; PDF user space runs bottom-left up,
        // so the vertical axis has to be flipped, not merely scaled.
        val xPt = xPx * widthPt / renderedWidthPx
        val yPt = heightPt - (yPx * heightPt / renderedHeightPx)
        val tolerance = widthPt * TOUCH_TOLERANCE_FRACTION

        val hitIndex = textPage.textPageGetCharIndexAtPos(xPt, yPt, tolerance, tolerance)
        if (hitIndex < 0) return null

        val charCount = textPage.textPageCountChars()
        if (charCount <= 0 || hitIndex >= charCount) return null
        // The tolerance can snap to a nearby space or comma; only a real letter
        // counts as hitting a word.
        if (!isWordChar(textPage.textPageGetUnicode(hitIndex))) return null

        var start = hitIndex
        while (start > 0 && isWordChar(textPage.textPageGetUnicode(start - 1))) start--
        var end = hitIndex
        while (end + 1 < charCount && isWordChar(textPage.textPageGetUnicode(end + 1))) end++

        val text = textPage.textPageGetText(start, end - start + 1)?.trim().orEmpty()
        if (text.isEmpty()) return null

        val bounds = wordBoundsPx(
            textPage, start, end, widthPt, heightPt, renderedWidthPx, renderedHeightPx,
        ) ?: return null

        return PdfWord(text = text, boundsPx = bounds)
    }

    /** Union of the word's character boxes, converted to bitmap pixels. */
    private fun wordBoundsPx(
        textPage: PdfTextPage,
        start: Int,
        end: Int,
        widthPt: Double,
        heightPt: Double,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): RectF? {
        val scaleX = renderedWidthPx / widthPt
        val scaleY = renderedHeightPx / heightPt

        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        var any = false

        for (index in start..end) {
            val box = textPage.textPageGetCharBox(index) ?: continue
            any = true
            // Normalise with min/max rather than trusting which edge PDFium
            // calls "top": in a y-up space that is the larger value.
            val boxLeft = minOf(box.left, box.right).toDouble()
            val boxRight = maxOf(box.left, box.right).toDouble()
            val boxUpper = maxOf(box.top, box.bottom).toDouble()
            val boxLower = minOf(box.top, box.bottom).toDouble()

            left = minOf(left, boxLeft * scaleX)
            right = maxOf(right, boxRight * scaleX)
            top = minOf(top, (heightPt - boxUpper) * scaleY)
            bottom = maxOf(bottom, (heightPt - boxLower) * scaleY)
        }

        return if (any) {
            RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        } else {
            null
        }
    }

    override suspend fun findMatches(
        query: String,
        limit: Int,
        onBatch: suspend (List<PdfMatch>) -> Unit,
    ) {
        val term = query.trim()
        if (term.isEmpty()) return
        var found = 0

        for (pageIndex in 0 until pageCount) {
            // The lock is taken and released per page rather than held for the
            // whole sweep, so paging and rendering stay responsive while a long
            // document is being searched.
            val matches = withContext(Dispatchers.IO) {
                mutex.withLock {
                    document.openPage(pageIndex)?.use { page ->
                        page.openTextPage().use { textPage ->
                            matchesOnPage(textPage, pageIndex, term, limit - found)
                        }
                    }.orEmpty()
                }
            }
            // Cancellation is checked between pages: a reader who keeps typing
            // starts a new search and this one should stop promptly.
            currentCoroutineContext().ensureActive()

            if (matches.isNotEmpty()) {
                found += matches.size
                onBatch(matches)
            }
            if (found >= limit) return
        }
    }

    private fun matchesOnPage(
        textPage: PdfTextPage,
        pageIndex: Int,
        term: String,
        remaining: Int,
    ): List<PdfMatch> {
        if (remaining <= 0) return emptyList()
        // No flags: case-insensitive, substring. Searching a language you are
        // learning means often being unsure of the exact form, so the widest
        // match is the useful one.
        // close() alone. FindResult.close() and closeFind() both call the same
        // native closeFind on the same handle, so calling both is a double free
        // and aborts the process inside the allocator.
        return textPage.findStart(term, emptySet(), 0)?.use { find ->
            buildList {
                while (size < remaining && find.findNext()) {
                    val index = find.getSchResultIndex()
                    val count = find.getSchCount()
                    if (index >= 0 && count > 0) {
                        add(PdfMatch(pageIndex = pageIndex, charIndex = index, charCount = count))
                    }
                }
            }
        }.orEmpty()
    }

    override suspend fun matchBoundsPx(
        match: PdfMatch,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): List<RectF> = withContext(Dispatchers.IO) {
        if (renderedWidthPx <= 0 || renderedHeightPx <= 0) return@withContext emptyList()
        mutex.withLock {
            document.openPage(match.pageIndex)?.use { page ->
                val widthPt = page.getPageWidthPoint().toDouble()
                val heightPt = page.getPageHeightPoint().toDouble()
                if (widthPt <= 0.0 || heightPt <= 0.0) return@use emptyList()
                page.openTextPage().use { textPage ->
                    // FPDFText_GetRect only answers after the range has been
                    // counted; the count call is what populates it.
                    val rectCount = textPage.textPageCountRects(match.charIndex, match.charCount)
                    (0 until rectCount).mapNotNull { i ->
                        textPage.textPageGetRect(i)?.let { box ->
                            toBitmapRect(box, widthPt, heightPt, renderedWidthPx, renderedHeightPx)
                        }
                    }
                }
            }.orEmpty()
        }
    }

    /** PDF user space is y-up from the bottom left; bitmaps are y-down. */
    private fun toBitmapRect(
        box: RectF,
        widthPt: Double,
        heightPt: Double,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ): RectF {
        val scaleX = renderedWidthPx / widthPt
        val scaleY = renderedHeightPx / heightPt
        val left = minOf(box.left, box.right).toDouble() * scaleX
        val right = maxOf(box.left, box.right).toDouble() * scaleX
        val top = (heightPt - maxOf(box.top, box.bottom).toDouble()) * scaleY
        val bottom = (heightPt - minOf(box.top, box.bottom).toDouble()) * scaleY
        return RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    override fun close() {
        runCatching { document.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * How far from the touch point PDFium may look for a character, as a
         * fraction of page width. Fingers are imprecise and glyphs are small;
         * zero tolerance makes the feature feel broken.
         */
        private const val TOUCH_TOLERANCE_FRACTION = 0.012

        /**
         * Whether a character belongs to a word, for Catalan.
         *
         * [Char.isLetter] already covers the accented vowels and ç. The middle
         * dot is included deliberately: the geminate "l·l" is one word, and
         * splitting on it would send "intel" to the dictionary instead of
         * "intel·ligent". The apostrophe is deliberately excluded, so "l'aigua"
         * looks up "aigua" rather than the elided article.
         */
        internal fun isWordChar(character: Char): Boolean =
            character.isLetter() || character == '·'

        /**
         * Open [uri] for rendering. Throws [IOException] when the document
         * cannot be read — a revoked grant, a deleted file, or a PDF PDFium
         * rejects as malformed or password-protected.
         */
        suspend fun open(context: Context, uri: Uri): PdfiumPageRenderer =
            withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Could not open $uri")
                try {
                    val core = PdfiumCore(context)
                    PdfiumPageRenderer(descriptor, core.newDocument(descriptor))
                } catch (error: Throwable) {
                    runCatching { descriptor.close() }
                    throw error
                }
            }
    }
}
