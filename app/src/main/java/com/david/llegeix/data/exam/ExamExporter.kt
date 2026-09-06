package com.david.llegeix.data.exam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.net.toUri
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.pdf.PdfiumPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a finished sitting out as a PDF of its own.
 *
 * This is the reader's copy "with the current edits" — a real file they can
 * send to a teacher, print, or keep. The imported paper is not touched by it in
 * any way: the export renders that paper and draws over the rendering, and the
 * original is opened read-only throughout.
 *
 * [android.graphics.pdf.PdfDocument] rather than a PDF library. It has been in
 * Android since API 19, it is free, it adds no dependency, and it hands out an
 * ordinary [Canvas] — which means the marks can be drawn by the very same
 * [MarkPainter] that drew them on screen, so the export cannot disagree with
 * what the reader saw while writing.
 *
 * The cost is that each page becomes an image, so the exported file has no
 * selectable text and is larger than the original. That is the right trade for
 * what this is for: a record of what was written, whose layout is guaranteed to
 * match what was on screen. Anyone wanting the searchable original still has it
 * — untouched, which is the whole point.
 */
class ExamExporter(private val context: Context) {

    /**
     * Render [pages] of the paper with [marksByPage] over them into [target].
     *
     * @param onProgress called with each page as it is finished, so a long
     *   paper can say how far along it is rather than appearing to hang.
     */
    suspend fun export(
        paperFile: File,
        pageCount: Int,
        marksByPage: Map<Int, List<ExamMarkEntity>>,
        target: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        val renderer = runCatching {
            PdfiumPageRenderer.open(context, paperFile.toUri())
        }.getOrNull() ?: return@withContext false

        renderer.use { paper ->
            val document = PdfDocument()
            try {
                for (index in 0 until pageCount) {
                    // A long paper is a long job; a reader who backs out should
                    // not have the phone go on rendering pages for a file that
                    // is about to be thrown away.
                    currentCoroutineContext().ensureActive()

                    val bitmap = runCatching {
                        paper.renderPage(index, RENDER_WIDTH_PX, crop = false)
                    }.getOrNull() ?: continue

                    val info = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        index + 1,
                    ).create()
                    val page = document.startPage(info)
                    val canvas = page.canvas

                    canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(0, 0, bitmap.width, bitmap.height),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    MarkPainter.draw(
                        canvas = canvas,
                        marks = marksByPage[index].orEmpty(),
                        width = bitmap.width.toFloat(),
                        height = bitmap.height.toFloat(),
                    )

                    document.finishPage(page)
                    bitmap.recycle()
                    onProgress(index + 1, pageCount)
                }

                val written = runCatching {
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        document.writeTo(out)
                        true
                    } ?: false
                }.getOrDefault(false)
                written
            } finally {
                document.close()
            }
        }
    }

    /**
     * A filename the reader will recognise months later.
     *
     * The paper, then the sitting, then the date — because a folder of these is
     * sorted by name and that order puts every go at one paper together.
     */
    fun suggestedName(examTitle: String, attemptLabel: String): String {
        val safe = examTitle.map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else '_' }
            .joinToString("")
            .trim()
            .ifEmpty { "Exam" }
        val date = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis())
        return "$safe - $attemptLabel - $date.pdf"
    }

    private companion object {
        /**
         * How wide each page is rendered before the marks go on.
         *
         * About 150 dpi across an A4 sheet: sharp enough to print and to read a
         * printed question, without turning a twelve-page paper into a file too
         * large to email. The marks are drawn as vectors at this size rather
         * than scaled up from the screen, so handwriting comes out at the
         * resolution of the export and not of the phone.
         */
        const val RENDER_WIDTH_PX = 1240
    }
}
