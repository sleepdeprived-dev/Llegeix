package com.david.llegeix.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** One word read off a rendered page, in that bitmap's own pixels. */
data class OcrWord(
    val text: String,
    val box: RectF,
    /** Index into [OcrPage.lines], so a selection can quote the line it came from. */
    val lineIndex: Int,
)

/** One line of a read page: its text, and its 1-based number down the page. */
data class OcrLine(val number: Int, val text: String)

/**
 * What was read off one page, ordered the way it is read.
 *
 * Words are held in reading order — down the page, then across each line — so
 * a selection is a range in one list rather than a geometry problem.
 */
class OcrPage(val words: List<OcrWord>, val lines: List<OcrLine>) {

    val isEmpty: Boolean get() = words.isEmpty()

    /**
     * The word under a point, or null if the point is not on one.
     *
     * Null matters: pressing a margin has to be able to say "nothing there"
     * rather than dragging in the nearest word from an inch away.
     */
    fun wordAt(x: Float, y: Float, tolerance: Float): OcrWord? = words.firstOrNull { word ->
        x >= word.box.left - tolerance && x <= word.box.right + tolerance &&
            y >= word.box.top - tolerance && y <= word.box.bottom + tolerance
    }

    /**
     * The words between two points, snapped out to whole words.
     *
     * The start has to land on a word; the end does not, because a finger
     * dragged to the end of a sentence usually stops just past the last letter
     * and the reader plainly meant to include it.
     */
    fun selectionBetween(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        tolerance: Float,
    ): PdfSelection? {
        val first = wordAt(startX, startY, tolerance) ?: return null
        val last = wordAt(endX, endY, tolerance) ?: nearestWord(endX, endY) ?: first

        val from = words.indexOf(first)
        val to = words.indexOf(last)
        if (from < 0 || to < 0) return null
        val range = if (from <= to) words.subList(from, to + 1) else words.subList(to, from + 1)

        val lineIndex = range.first().lineIndex
        val line = lines.getOrNull(lineIndex)
        return PdfSelection(
            text = range.joinToString(" ") { it.text },
            boundsPx = range.map { RectF(it.box) },
            lineText = line?.text.orEmpty(),
            lineNumber = line?.number ?: 1,
            passage = PageLines.passageAround(lines.map { it.text }, lineIndex),
        )
    }

    /** The word whose centre is closest, for the loose end of a drag. */
    private fun nearestWord(x: Float, y: Float): OcrWord? = words.minByOrNull { word ->
        val dx = abs(word.box.centerX() - x)
        val dy = abs(word.box.centerY() - y)
        // Vertical distance counts for more: the word after the one you meant
        // is a smaller mistake than a word on another line entirely.
        dx + dy * VERTICAL_WEIGHT
    }

    private companion object {
        const val VERTICAL_WEIGHT = 3f
    }
}

/**
 * Reads the words off a page that has none of its own.
 *
 * A scanned book is a photograph of writing: there is no text in the file, so
 * PDFium has nothing to hand back and press-and-hold does nothing at all. This
 * puts the words back by looking at the rendered page, which is the only place
 * they exist.
 *
 * Recognition is asked for lazily — a page is only read when somebody actually
 * presses on it — and kept afterwards, so the wait happens once per page and
 * never during the drag that follows.
 */
class PageOcr {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Concurrent because the two callers are not the same call: [read] writes
     * under [mutex] from the coroutine doing the recognition, while [cached] is
     * a plain read from wherever the question is being asked. They happen to
     * share a dispatcher today, and a map that quietly depends on that is a map
     * that breaks the day one of them moves.
     */
    private val cache = ConcurrentHashMap<String, OcrPage>()
    private val mutex = Mutex()

    /** What has already been read for this page, without reading it again. */
    fun cached(pageIndex: Int, widthPx: Int): OcrPage? = cache[key(pageIndex, widthPx)]

    /** Read [bitmap], or return what was read before. */
    suspend fun read(pageIndex: Int, bitmap: Bitmap): OcrPage = mutex.withLock {
        cache[key(pageIndex, bitmap.width)]?.let { return it }
        val page = recognise(bitmap)
        cache[key(pageIndex, bitmap.width)] = page
        page
    }

    fun close() {
        cache.clear()
        runCatching { recognizer.close() }
    }

    private suspend fun recognise(bitmap: Bitmap): OcrPage {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        // ML Kit groups by block, and a block is a paragraph-ish region rather
        // than a reading order, so the lines are put back in the order a person
        // would take them: down the page, then across.
        val sorted = text.textBlocks
            .flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))

        val lines = ArrayList<OcrLine>(sorted.size)
        val words = ArrayList<OcrWord>()
        sorted.forEachIndexed { index, line ->
            lines += OcrLine(number = index + 1, text = line.text)
            words += line.elements
                .sortedBy { it.boundingBox?.left ?: 0 }
                .mapNotNull { element -> element.toWord(index) }
        }
        return OcrPage(words = words, lines = lines)
    }

    private fun Text.Element.toWord(lineIndex: Int): OcrWord? {
        val box = boundingBox ?: return null
        if (text.isBlank()) return null
        return OcrWord(text = text, box = RectF(box), lineIndex = lineIndex)
    }

    private fun key(pageIndex: Int, widthPx: Int) = "$pageIndex@$widthPx"
}

/** Bridges a Play Services task to a coroutine, cancellably. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) {
            continuation.resumeWithException(error)
        } else {
            @Suppress("UNCHECKED_CAST")
            continuation.resume(task.result as T)
        }
    }
}
