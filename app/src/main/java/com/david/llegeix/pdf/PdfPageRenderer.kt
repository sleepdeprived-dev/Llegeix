package com.david.llegeix.pdf

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * One line of a document's own table of contents.
 *
 * [depth] is how deeply nested the entry was, counted from zero, which is all
 * the list needs in order to indent it.
 */
data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
)

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
 * One occurrence of a search term, as a character range on a page.
 *
 * Stored as a range rather than as rectangles because the rectangles depend on
 * the size the page is currently rendered at, which changes with rotation and
 * zoom; the range does not.
 *
 * [snippet] is the range with the words either side of it, carried along
 * because the results list has to show what was found rather than only where.
 * It is filled in as the page is searched, while its text is already open;
 * fetching it later would mean opening every page a second time.
 */
data class PdfMatch(
    val pageIndex: Int,
    val charIndex: Int,
    val charCount: Int,
    /** The line around the match, tidied and cut to a scannable length. */
    val snippet: String = "",
    /** Where the term sits inside [snippet], so the row can mark it. */
    val snippetStart: Int = 0,
    /** One past the end of the term inside [snippet]. */
    val snippetEnd: Int = 0,
)

/**
 * A stretch of text the reader dragged across, with everything needed to make
 * sense of it later.
 *
 * [lineText] is the whole line the selection starts on. Saving a word without
 * the sentence it was working in throws away most of what makes it learnable,
 * so the line travels with it.
 */
data class PdfSelection(
    val text: String,
    val boundsPx: List<RectF>,
    val lineText: String,
    /** 1-based, counted from the page's text layer. */
    val lineNumber: Int,
    /**
     * The line with its neighbours, for weighing which sense of a word is in
     * play.
     *
     * A line of a printed book is often three or four words long, which is not
     * enough for the surrounding words to say anything about a meaning. This is
     * never shown; it only feeds the reference lookup.
     */
    val passage: String = "",
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
     *
     * With [crop] set the blank margins are trimmed off and the remaining
     * content is rendered at [targetWidthPx] instead, so the type comes out
     * bigger rather than the same size in a smaller frame. That is the whole
     * point of the option: on a phone the margins of an A4 page are a fifth of
     * the screen's width spent on nothing.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int, crop: Boolean = false): Bitmap

    /**
     * The document's own table of contents, flattened, or empty when it has
     * none.
     *
     * Most books built from a word processor or LaTeX carry one, and most
     * scans do not. Flattened rather than nested because the only thing the
     * list does with the structure is indent it.
     */
    suspend fun outline(): List<PdfOutlineEntry>

    /**
     * Whether [pageIndex] carries a text layer at all.
     *
     * The difference between "you pressed a margin" and "this page is a
     * photograph of writing", which are the same silence to a reader and want
     * completely different answers from the app.
     */
    suspend fun hasTextLayer(pageIndex: Int): Boolean

    /**
     * The word under a point, or null where the page has no selectable text
     * there — a scanned page, an image, or simply blank space.
     *
     * The point and the rendered size are both in bitmap pixels, so callers work
     * entirely in the coordinate space they already drew in.
     *
     * [crop] must be whatever was passed to [renderPage] for the bitmap being
     * touched. Cropping changes which part of the page a pixel stands for, and
     * a lookup that assumed otherwise answered with the word a line or two
     * further down.
     */
    suspend fun wordAt(
        pageIndex: Int,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean = false,
    ): PdfWord?

    /**
     * The text between two points on a page, snapped out to whole words.
     *
     * Both points are in rendered-bitmap pixels. Passing the same point twice
     * selects the single word under it, so the press-and-drag gesture and the
     * plain long press are the same call. [crop] must match the render, as
     * [wordAt] explains.
     */
    suspend fun selectionBetween(
        pageIndex: Int,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean = false,
    ): PdfSelection?

    /**
     * Search the whole document for [query], case-insensitively.
     *
     * Results are delivered page by page through [onBatch] rather than returned
     * at the end: a long document takes noticeable time to sweep, and a reader
     * who typed a word wants the first hit now, not after page 400 has been
     * checked. Stops once [limit] matches have been found.
     */
    suspend fun findMatches(
        query: String,
        limit: Int = MATCH_LIMIT,
        onBatch: suspend (List<PdfMatch>) -> Unit,
    )

    /**
     * Where the words in [words] appear on this page, in rendered-bitmap
     * pixels.
     *
     * What it is for: marking the words the reader has already saved, so the
     * book itself shows what has been worked on. [words] is expected lowercase;
     * matching is on whole words, so a saved phrase never marks one of the
     * words inside it.
     */
    suspend fun savedWordBounds(
        pageIndex: Int,
        words: Set<String>,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean = false,
    ): List<RectF>

    /**
     * Where [match] sits on its page, in rendered-bitmap pixels.
     *
     * A match can span more than one rectangle when it wraps across a line.
     */
    suspend fun matchBoundsPx(
        match: PdfMatch,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        crop: Boolean = false,
    ): List<RectF>
}

/**
 * How many matches one search keeps.
 *
 * A cap rather than a promise: a common word in a long book has thousands of
 * occurrences, and neither the list nor the reader has any use for the four
 * thousandth. Named here rather than left as a default argument so the results
 * list can say when it is showing all there is and when it is showing the
 * first of many.
 */
const val MATCH_LIMIT: Int = 500

/**
 * A4 portrait, used to size the placeholder before a page's real dimensions are
 * known. Only affects the brief moment before the first bitmap arrives.
 */
const val DEFAULT_PAGE_ASPECT_RATIO: Float = 1f / 1.414f
