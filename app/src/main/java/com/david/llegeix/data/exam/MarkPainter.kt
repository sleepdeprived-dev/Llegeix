package com.david.llegeix.data.exam

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.MarkKind

/**
 * Draws the reader's marks onto a page, at whatever size that page is.
 *
 * There is one of these rather than two, and that is the point of the file. The
 * marks are drawn twice in this app — once on screen while they are being made,
 * and once into the exported PDF — and if those were two implementations they
 * would drift, which is the one bug this feature cannot afford: an export that
 * does not look like what the reader wrote is an export they cannot trust, and
 * they would only find out after the exam.
 *
 * So both go through here. The screen reaches it through Compose's
 * `drawIntoCanvas { it.nativeCanvas }`, and the exporter hands it the canvas
 * that [android.graphics.pdf.PdfDocument] gives out. Neither knows anything the
 * other does not.
 *
 * Everything is in fractions of the page on the way in and pixels on the way
 * out, which makes [width] and [height] the only things that change between the
 * two callers.
 */
object MarkPainter {

    /**
     * How solid a highlight is.
     *
     * Enough to read as a colour over white, transparent enough that the
     * printed question underneath is still the thing being read.
     */
    const val HIGHLIGHT_ALPHA = 0.38f

    /** How thick a tick's two lines are, relative to the tick's own box. */
    private const val TICK_STROKE_FRACTION = 0.14f

    fun draw(canvas: Canvas, marks: List<ExamMarkEntity>, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        marks.forEach { mark -> draw(canvas, mark, width, height, paint) }
    }

    fun draw(
        canvas: Canvas,
        mark: ExamMarkEntity,
        width: Float,
        height: Float,
        paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG),
    ) {
        when (mark.kind) {
            MarkKind.INK.name -> stroke(
                canvas,
                MarkGeometry.decode(mark.points),
                mark.colorArgb,
                mark.size,
                1f,
                width,
                height,
                paint,
            )

            MarkKind.HIGHLIGHT.name -> stroke(
                canvas,
                MarkGeometry.decode(mark.points),
                mark.colorArgb,
                mark.size,
                HIGHLIGHT_ALPHA,
                width,
                height,
                paint,
            )

            MarkKind.TICK.name -> tick(canvas, mark, width, height, paint)

            MarkKind.TEXT.name -> text(canvas, mark, width, height, paint)
        }
    }

    /**
     * One stroke of ink.
     *
     * The path curves through the midpoints between samples rather than joining
     * the samples with straight lines. A finger is sampled once a frame, so a
     * polyline through those samples is visibly faceted at any speed — it comes
     * out as handwriting with corners in it. Quadratic segments cost nothing and
     * look like ink.
     */
    fun stroke(
        canvas: Canvas,
        points: List<MarkGeometry.Point>,
        colorArgb: Int,
        widthFraction: Float,
        alpha: Float,
        width: Float,
        height: Float,
        paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG),
    ) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x * width, points[0].y * height)
        for (i in 1 until points.size - 1) {
            val cx = points[i].x * width
            val cy = points[i].y * height
            val nx = points[i + 1].x * width
            val ny = points[i + 1].y * height
            path.quadTo(cx, cy, (cx + nx) / 2f, (cy + ny) / 2f)
        }
        path.lineTo(points.last().x * width, points.last().y * height)

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = colorArgb
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        // Against the page's width, so a stroke keeps its weight whatever size
        // the page is drawn at — on screen, and in the exported PDF.
        paint.strokeWidth = (widthFraction * width).coerceAtLeast(1f)
        canvas.drawPath(path, paint)
    }

    /**
     * A tick, drawn as two lines rather than typed as a character.
     *
     * A check glyph comes from whatever font the device happens to have, at
     * whatever weight, and on some devices it is missing entirely and renders as
     * an empty box — which on an exam paper is precisely the wrong mark to make.
     * Two lines are always a tick, on every phone and in every PDF reader.
     */
    private fun tick(canvas: Canvas, mark: ExamMarkEntity, width: Float, height: Float, paint: Paint) {
        if (!mark.checked) return
        val left = mark.x * width
        val top = mark.y * height
        val boxWidth = mark.width * width
        val boxHeight = mark.height * height

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = mark.colorArgb
        paint.strokeWidth = (boxWidth * TICK_STROKE_FRACTION).coerceAtLeast(2f)

        canvas.drawLine(
            left + boxWidth * 0.16f, top + boxHeight * 0.52f,
            left + boxWidth * 0.40f, top + boxHeight * 0.78f,
            paint,
        )
        canvas.drawLine(
            left + boxWidth * 0.40f, top + boxHeight * 0.78f,
            left + boxWidth * 0.86f, top + boxHeight * 0.20f,
            paint,
        )
    }

    /**
     * A typed answer, in its own box.
     *
     * The box is the mark's own [ExamMarkEntity.width] rather than "everything
     * to the right edge of the page", which is what it used to be. That change
     * is what makes a text box an object: it can be narrowed to fit the gap on
     * a printed line, widened into a paragraph, moved without reflowing, and
     * turned — and it wraps to itself instead of to whatever is left of the
     * sheet.
     *
     * Wrapped by hand rather than with a StaticLayout, because the only thing
     * that has to be true of the wrap is that it is the same on screen as in
     * the export, and hand-wrapping on the measured width is the shortest way
     * to guarantee that from one implementation.
     */
    private fun text(canvas: Canvas, mark: ExamMarkEntity, width: Float, height: Float, paint: Paint) {
        val content = mark.text ?: return
        preparePaint(paint, mark, width)

        val left = mark.x * width
        val top = mark.y * height
        val boxWidth = (mark.width * width).coerceAtLeast(paint.textSize)
        val lineHeight = paint.textSize * LINE_SPACING

        // Turned about the box's own centre, so a rotated box stays where it
        // was put. Rotating about the top-left would swing the text away from
        // the finger that turned it.
        val turned = mark.rotation != 0f
        if (turned) {
            canvas.save()
            canvas.rotate(
                mark.rotation,
                left + boxWidth / 2f,
                top + (mark.height * height) / 2f,
            )
        }

        var y = top + paint.textSize
        for (line in layOut(content, paint, boxWidth)) {
            canvas.drawText(line, left, y, paint)
            y += lineHeight
        }

        if (turned) canvas.restore()
    }

    /**
     * How tall a text box's content actually is, as a fraction of the page.
     *
     * The canvas needs it to draw the frame round a selected box, and the
     * editor needs it to store a height that the eraser and the hit test can
     * believe. Both go through here rather than each estimating, because a
     * frame that does not fit the words inside it is the clearest possible
     * signal that an app is guessing.
     */
    fun textHeightFraction(mark: ExamMarkEntity, width: Float, height: Float): Float {
        if (height <= 0f) return mark.height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        preparePaint(paint, mark, width)
        val boxWidth = (mark.width * width).coerceAtLeast(paint.textSize)
        val lines = layOut(mark.text.orEmpty(), paint, boxWidth).size.coerceAtLeast(1)
        return (lines * paint.textSize * LINE_SPACING + paint.textSize * 0.4f) / height
    }

    private fun preparePaint(paint: Paint, mark: ExamMarkEntity, width: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = mark.colorArgb
        paint.textSize = (mark.size * width).coerceAtLeast(6f)
    }

    /**
     * The lines a text box breaks into: hard newlines first, then word wrap.
     *
     * Newlines matter now that the blank pages at the back of a paper are
     * written on like a document. Before this, pressing return in an answer
     * produced one long line with a space in it.
     */
    private fun layOut(text: String, paint: Paint, available: Float): List<String> =
        text.split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf("") else wrap(paragraph, paint, available)
        }

    /** Greedy word wrap against the width of the box. */
    private fun wrap(text: String, paint: Paint, available: Float): List<String> {
        val words = text.split(' ')
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= available || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    /**
     * Line spacing, shared by the painter and the on-screen editor.
     *
     * The two have to agree or the text jumps the moment the keyboard closes,
     * which is the most alarming thing a text box can do.
     */
    const val LINE_SPACING = 1.25f
}
