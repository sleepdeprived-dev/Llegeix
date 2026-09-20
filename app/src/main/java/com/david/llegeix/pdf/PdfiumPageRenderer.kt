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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

    /**
     * Whether the native document has been freed, or is about to be.
     *
     * Read inside the lock by everything that touches PDFium. The flag exists
     * because [close] and a render are not started by the same thing: pages are
     * rendered from the composition, which is torn down when the reader leaves
     * the screen, and the document is closed by the ViewModel, which is cleared
     * at roughly the same moment. Cancelling a coroutine does not stop a native
     * call that is already running, so without this the document could be freed
     * out from under a page still being drawn — a use-after-free inside PDFium,
     * which is a process abort rather than an exception anything could catch.
     */
    @Volatile
    private var closed = false

    /**
     * Where the actual freeing happens.
     *
     * [close] is called from [ViewModel.onCleared][androidx.lifecycle.ViewModel]
     * and cannot suspend, but the free has to wait its turn at [mutex] like
     * every other native call. So it is handed to a coroutine: the caller
     * returns immediately, and PDFium is not touched until whatever is inside
     * it has come out.
     */
    private val closing = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val pageCount: Int = document.getPageCount()

    /**
     * Where the ink is on each page, once it has been looked for.
     *
     * Found by rendering the page small and looking at the pixels, which is not
     * free, so the answer is kept: a page is scrolled past, back to, and
     * re-rendered on every rotation, and the margins of a printed page do not
     * move.
     */
    private val contentBoxes = HashMap<Int, ContentBox>()

    override suspend fun renderPage(
        index: Int,
        targetWidthPx: Int,
        crop: Boolean,
    ): Bitmap = withContext(Dispatchers.IO) {
        val box = boxFor(index, crop)
        if (box == ContentBox.Whole) {
            renderWhole(index, targetWidthPx)
        } else {
            renderCropped(index, targetWidthPx, box)
        }
    }

    /**
     * Draws only the part of the page that has ink on it, at the width asked
     * for.
     *
     * The obvious implementation — render the whole page oversized, then take a
     * sub-bitmap — is what this replaced, and it was a memory trap: to get a
     * 1080px-wide crop out of a page whose text column is half the sheet, the
     * whole sheet has to be rendered at 2160px, which is a 26 MB bitmap held
     * alongside the 7 MB one being cut out of it, for every page turn.
     *
     * PDFium can be told where to put the page instead. The destination bitmap
     * is exactly the size of the crop, and the page is drawn into it at the
     * oversized dimensions with its origin pushed up and to the left, so
     * everything outside the content box falls off the edges and is never
     * rasterised at all.
     */
    private suspend fun renderCropped(
        index: Int,
        targetWidthPx: Int,
        box: ContentBox,
    ): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) throw IOException("This PDF has been closed")
            val page = document.openPage(index)
                ?: throw IOException("Page $index of this PDF could not be opened")
            page.use { page ->
                val widthPt = page.getPageWidthPoint()
                val heightPt = page.getPageHeightPoint()
                // How large the whole sheet would have to be for the content box
                // to come out at the requested width.
                val sheetWidth = (targetWidthPx / box.width)
                    .toInt()
                    .coerceIn(targetWidthPx.coerceAtLeast(1), MAX_CROP_RENDER_PX)
                val sheetHeight = if (widthPt > 0) {
                    (sheetWidth.toFloat() * heightPt / widthPt).roundToInt().coerceAtLeast(1)
                } else {
                    sheetWidth
                }

                val left = (box.left * sheetWidth).roundToInt()
                val top = (box.top * sheetHeight).roundToInt()
                val width = (box.width * sheetWidth).roundToInt()
                    .coerceIn(1, sheetWidth)
                val height = (box.height * sheetHeight).roundToInt()
                    .coerceIn(1, sheetHeight)

                val bitmap = createBitmap(width, height)
                // PDFium composites only the page's own marks, so anything it
                // does not paint would stay transparent and read as black.
                Canvas(bitmap).drawColor(Color.WHITE)
                page.renderPageBitmap(
                    bitmap,
                    -left,
                    -top,
                    sheetWidth,
                    sheetHeight,
                    renderAnnot = true,
                )
                bitmap
            }
        }
    }

    /**
     * The box the page is actually drawn inside at these settings.
     *
     * The single source of truth for "what part of the page is on screen", and
     * the reason this exists: every conversion between a touch on the bitmap
     * and a point in the PDF has to agree with what was rendered, and while
     * [renderPage] worked that out privately they did not. Cropping trims the
     * margins and redraws the rest at the same width, so with it on a bitmap
     * pixel is a different point of the page — and the text layer went on being
     * asked as though nothing had moved. A press landed one or two lines below
     * the word under the finger, because the top margin was the part that had
     * been taken away.
     *
     * Resolved before the lock is taken, never inside it: [marginsOf] takes the
     * same lock to read its cache, and this mutex is not reentrant.
     */
    private suspend fun boxFor(index: Int, crop: Boolean): ContentBox {
        if (!crop) return ContentBox.Whole
        val box = marginsOf(index)
        return if (box.isWorthCropping) box else ContentBox.Whole
    }

    /**
     * The page's margins, measured once on a cheap render of it.
     *
     * The probe is deliberately tiny. Margins are a coarse fact — where the
     * block of type starts, to within a millimetre or two — and a 220px render
     * answers it as well as a full-size one for a fraction of the work.
     */
    private suspend fun marginsOf(index: Int): ContentBox {
        mutex.withLock { contentBoxes[index] }?.let { return it }
        val probe = runCatching { renderWhole(index, CROP_PROBE_PX) }.getOrNull()
            ?: return ContentBox.Whole
        val box = contentBoxOf(probe)
        probe.recycle()
        mutex.withLock { contentBoxes[index] = box }
        return box
    }

    override suspend fun outline(): List<PdfOutlineEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock emptyList()
            runCatching { document.getTableOfContents() }
                .getOrDefault(emptyList())
                .let { entries -> flattenOutline(entries, depth = 0) }
        }
    }

    private fun flattenOutline(
        entries: List<io.legere.pdfiumandroid.api.Bookmark>,
        depth: Int,
    ): List<PdfOutlineEntry> = entries.flatMap { entry ->
        val title = entry.title?.trim().orEmpty()
        val here = if (title.isEmpty()) {
            emptyList()
        } else {
            listOf(
                PdfOutlineEntry(
                    title = title,
                    pageIndex = entry.pageIdx.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                    depth = depth,
                ),
            )
        }
        // Depth is not advanced past an entry with no title of its own, so a
        // structural node nobody named does not indent everything under it for
        // no visible reason.
        here + flattenOutline(entry.children, if (here.isEmpty()) depth else depth + 1)
    }

    private suspend fun renderWhole(index: Int, targetWidthPx: Int): Bitmap =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (closed) throw IOException("This PDF has been closed")
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

    override suspend fun hasTextLayer(pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock false
            document.openPage(pageIndex)?.use { page ->
                page.openTextPage().use { it.textPageCountChars() > 0 }
            } ?: false
        }
    }

    override suspend fun wordAt(
        pageIndex: Int,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean,
    ): PdfWord? = withContext(Dispatchers.IO) {
        if (renderedWidthPx <= 0 || renderedHeightPx <= 0) return@withContext null
        val shown = boxFor(pageIndex, crop)
        mutex.withLock {
            if (closed) return@withLock null
            // A page that will not open simply has no word at that point.
            document.openPage(pageIndex)?.use { page ->
                page.openTextPage().use { textPage ->
                    findWord(page, textPage, xPx, yPx, renderedWidthPx, renderedHeightPx, shown)
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
        shown: ContentBox,
    ): PdfWord? {
        val widthPt = page.getPageWidthPoint().toDouble()
        val heightPt = page.getPageHeightPoint().toDouble()
        if (widthPt <= 0.0 || heightPt <= 0.0) return null

        val xPt = PageGeometry.xPoint(xPx, renderedWidthPx, widthPt, shown)
        val yPt = PageGeometry.yPoint(yPx, renderedHeightPx, heightPt, shown)
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
            textPage, start, end, widthPt, heightPt, renderedWidthPx, renderedHeightPx, shown,
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
        shown: ContentBox,
    ): RectF? {
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

            left = minOf(left, PageGeometry.xPixel(boxLeft, renderedWidthPx, widthPt, shown))
            right = maxOf(right, PageGeometry.xPixel(boxRight, renderedWidthPx, widthPt, shown))
            top = minOf(top, PageGeometry.yPixel(boxUpper, renderedHeightPx, heightPt, shown))
            bottom = maxOf(bottom, PageGeometry.yPixel(boxLower, renderedHeightPx, heightPt, shown))
        }

        return if (any) {
            RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        } else {
            null
        }
    }

    override suspend fun selectionBetween(
        pageIndex: Int,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean,
    ): PdfSelection? = withContext(Dispatchers.IO) {
        if (renderedWidthPx <= 0 || renderedHeightPx <= 0) return@withContext null
        val shown = boxFor(pageIndex, crop)
        mutex.withLock {
            if (closed) return@withLock null
            document.openPage(pageIndex)?.use { page ->
                page.openTextPage().use { textPage ->
                    buildSelection(
                        page, textPage,
                        startXPx, startYPx, endXPx, endYPx,
                        renderedWidthPx, renderedHeightPx, shown,
                    )
                }
            }
        }
    }

    private fun buildSelection(
        page: PdfPage,
        textPage: PdfTextPage,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        shown: ContentBox,
    ): PdfSelection? {
        val widthPt = page.getPageWidthPoint().toDouble()
        val heightPt = page.getPageHeightPoint().toDouble()
        if (widthPt <= 0.0 || heightPt <= 0.0) return null

        val charCount = textPage.textPageCountChars()
        if (charCount <= 0) return null
        val tolerance = widthPt * TOUCH_TOLERANCE_FRACTION

        fun charAt(xPx: Float, yPx: Float): Int {
            val xPt = PageGeometry.xPoint(xPx, renderedWidthPx, widthPt, shown)
            val yPt = PageGeometry.yPoint(yPx, renderedHeightPx, heightPt, shown)
            return textPage.textPageGetCharIndexAtPos(xPt, yPt, tolerance, tolerance)
        }

        /**
         * The character under a point, or the nearest one back towards [towardXPx].
         *
         * A finger dragged to the end of a sentence usually stops just past the
         * full stop, in the margin, where there is no character at all. Taking
         * that as "no selection" collapsed the whole drag back to the word it
         * started on, which made selecting to the end of a line almost
         * impossible. Walking back towards the other end of the drag finds the
         * last word the reader actually crossed.
         */
        fun charNear(xPx: Float, yPx: Float, towardXPx: Float): Int {
            charAt(xPx, yPx).let { if (it >= 0) return it }
            for (step in 1..MARGIN_PROBE_STEPS) {
                val ratio = step.toFloat() / MARGIN_PROBE_STEPS
                charAt(xPx + (towardXPx - xPx) * ratio, yPx).let { if (it >= 0) return it }
            }
            return -1
        }

        val a = charNear(startXPx, startYPx, endXPx)
        val b = charNear(endXPx, endYPx, startXPx)
        // A drag that started or ended in the margin still has one good end.
        val anchor = if (a >= 0) a else b
        if (anchor < 0 || anchor >= charCount) return null
        var from = minOf(a.takeIf { it >= 0 } ?: anchor, b.takeIf { it >= 0 } ?: anchor)
        var to = maxOf(a.takeIf { it >= 0 } ?: anchor, b.takeIf { it >= 0 } ?: anchor)
        if (from !in 0 until charCount || to !in 0 until charCount) return null

        // Snap to whole words: half a word is never what was meant.
        while (from > 0 && isWordChar(textPage.textPageGetUnicode(from - 1))) from--
        while (to + 1 < charCount && isWordChar(textPage.textPageGetUnicode(to + 1))) to++

        val text = textPage.textPageGetText(from, to - from + 1)
            ?.replace(LINE_BREAKS, " ")
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return null

        val rectCount = textPage.textPageCountRects(from, to - from + 1)
        val bounds = (0 until rectCount).mapNotNull { i ->
            textPage.textPageGetRect(i)?.let { box ->
                toBitmapRect(box, widthPt, heightPt, renderedWidthPx, renderedHeightPx, shown)
            }
        }

        val located = lineAt(textPage, charCount, from)
        return PdfSelection(
            text = text,
            boundsPx = bounds,
            lineText = located.text,
            lineNumber = located.number,
            passage = located.passage,
        )
    }

    /** The line containing [charIndex], with the lines around it. */
    private fun lineAt(
        textPage: PdfTextPage,
        charCount: Int,
        charIndex: Int,
    ): LocatedLine = PageLines.locate(textPage.textPageGetText(0, charCount).orEmpty(), charIndex)

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
                    if (closed) return@withLock emptyList()
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
        val found = textPage.findStart(term, emptySet(), 0)?.use { find ->
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
        if (found.isEmpty()) return found

        // The page's text is pulled once, here, and only for a page that
        // actually matched. It is what the snippets are cut out of, and the
        // alternative — asking PDFium for each match's surroundings separately —
        // is one call per hit on a page that can hold dozens.
        val pageText = textPage.textPageGetText(0, textPage.textPageCountChars()).orEmpty()
        return found.map { match ->
            val snippet = MatchSnippet.around(pageText, match.charIndex, match.charCount)
            match.copy(
                snippet = snippet.text,
                snippetStart = snippet.start,
                snippetEnd = snippet.end,
            )
        }
    }

    override suspend fun savedWordBounds(
        pageIndex: Int,
        words: Set<String>,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean,
    ): List<RectF> = withContext(Dispatchers.IO) {
        if (words.isEmpty() || renderedWidthPx <= 0 || renderedHeightPx <= 0) {
            return@withContext emptyList()
        }
        val shown = boxFor(pageIndex, crop)
        mutex.withLock {
            if (closed) return@withLock emptyList()
            document.openPage(pageIndex)?.use { page ->
                val widthPt = page.getPageWidthPoint().toDouble()
                val heightPt = page.getPageHeightPoint().toDouble()
                if (widthPt <= 0.0 || heightPt <= 0.0) return@use emptyList()
                page.openTextPage().use { textPage ->
                    // The page's text is pulled once and walked here rather
                    // than asking PDFium to find each saved word in turn. A
                    // reader with two hundred saved words would otherwise cost
                    // two hundred searches per page, every page.
                    val text = textPage
                        .textPageGetText(0, textPage.textPageCountChars())
                        .orEmpty()
                    buildList {
                        var start = 0
                        while (start < text.length) {
                            if (!isWordChar(text[start])) {
                                start++
                                continue
                            }
                            var end = start
                            while (end < text.length && isWordChar(text[end])) end++
                            if (text.substring(start, end).lowercase() in words) {
                                val rects = textPage.textPageCountRects(start, end - start)
                                for (index in 0 until rects) {
                                    textPage.textPageGetRect(index)?.let { box ->
                                        add(
                                            toBitmapRect(
                                                box,
                                                widthPt,
                                                heightPt,
                                                renderedWidthPx,
                                                renderedHeightPx,
                                                shown,
                                            ),
                                        )
                                    }
                                }
                            }
                            start = end
                        }
                    }
                }
            }.orEmpty()
        }
    }

    override suspend fun matchBoundsPx(
        match: PdfMatch,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean,
    ): List<RectF> = withContext(Dispatchers.IO) {
        if (renderedWidthPx <= 0 || renderedHeightPx <= 0) return@withContext emptyList()
        val shown = boxFor(match.pageIndex, crop)
        mutex.withLock {
            if (closed) return@withLock emptyList()
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
                            toBitmapRect(
                                box,
                                widthPt,
                                heightPt,
                                renderedWidthPx,
                                renderedHeightPx,
                                shown,
                            )
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
        shown: ContentBox,
    ): RectF {
        val left = PageGeometry.xPixel(minOf(box.left, box.right).toDouble(), renderedWidthPx, widthPt, shown)
        val right = PageGeometry.xPixel(maxOf(box.left, box.right).toDouble(), renderedWidthPx, widthPt, shown)
        val top = PageGeometry.yPixel(maxOf(box.top, box.bottom).toDouble(), renderedHeightPx, heightPt, shown)
        val bottom =
            PageGeometry.yPixel(minOf(box.top, box.bottom).toDouble(), renderedHeightPx, heightPt, shown)
        return RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    /**
     * Give the document back, once nothing is inside it.
     *
     * Returns straight away. The flag stops any call that has not yet taken the
     * lock, and the free itself queues behind any that already has, so a page
     * being rendered as the reader leaves finishes into a bitmap nobody looks
     * at rather than into freed memory.
     */
    override fun close() {
        if (closed) return
        closed = true
        closing.launch {
            mutex.withLock {
                runCatching { document.close() }
                runCatching { descriptor.close() }
            }
        }
    }

    companion object {
        /**
         * How far from the touch point PDFium may look for a character, as a
         * fraction of page width. Fingers are imprecise and glyphs are small;
         * zero tolerance makes the feature feel broken.
         */
        private const val TOUCH_TOLERANCE_FRACTION = 0.012

        /**
         * Width of the throwaway render the margins are measured on.
         *
         * Margins are a coarse fact and this is answered once per page, so the
         * probe is sized for speed rather than for precision.
         */
        private const val CROP_PROBE_PX = 220

        /**
         * A ceiling on the sheet size a crop renders the page at.
         *
         * A page whose content is one narrow column would otherwise ask for a
         * sheet several times the width of the screen. Nothing outside the crop
         * is rasterised any more, so this is no longer about memory — it is
         * about the time PDFium spends scaling vector art it is then told to
         * draw off the edge of the bitmap.
         */
        private const val MAX_CROP_RENDER_PX = 4_000

        /**
         * Probes taken back along a drag when its end landed on no character.
         *
         * Enough to cross a wide margin in small steps without ever jumping
         * over a word, and cheap: each probe is one lookup in the text page.
         */
        private const val MARGIN_PROBE_STEPS = 16

        /** PDFium reports page line breaks as CR, LF or both. */
        private val LINE_BREAKS = Regex("[\\r\\n]+")

        /**
         * Whether a character belongs to a word, for Catalan.
         *
         * [Char.isLetter] already covers the accented vowels and ç. The middle
         * dot is included deliberately: the geminate "l·l" is one word, and
         * splitting on it would send "intel" to the dictionary instead of
         * "intel·ligent". The apostrophe is deliberately excluded, so "l'aigua"
         * looks up "aigua" rather than the elided article.
         *
         * Digits count too, which they did not used to. Translating "1975" is
         * of no use to anybody, but a press that selects nothing at all is
         * worse: on a page of dates, prices or numbered notes it looked exactly
         * like the app being broken. Selecting the number and getting the
         * number back at least answers the press, and it lets "3r" and "km2"
         * hold together instead of losing half of themselves.
         */
        internal fun isWordChar(character: Char): Boolean =
            character.isLetterOrDigit() || character == '·'

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
