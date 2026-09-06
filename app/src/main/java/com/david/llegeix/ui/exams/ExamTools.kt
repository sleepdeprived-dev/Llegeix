package com.david.llegeix.ui.exams

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.MarkKind

/**
 * What the finger does on the page.
 *
 * [VIEW] is first and is where the workspace opens, which is the whole reason
 * the list starts with a tool that does not write. A finger on a page can mean
 * "turn this" or "write here" and it cannot mean both, so the reader says which
 * before touching the page rather than discovering it afterwards. In every other
 * mode the pager stops taking swipes and the arrows in the toolbar turn pages
 * instead — otherwise the first downstroke of a letter would be read as a swipe
 * and the page would leave under the pen.
 */
enum class ExamTool(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    /** What this tool writes, or null when it does not write. */
    val kind: MarkKind? = null,
) {
    /** Read, swipe, pinch. Writes nothing. */
    VIEW(R.string.exam_tool_view, R.drawable.ic_hand),

    /** Handwriting, which is the tool that copes with any paper. */
    PEN(R.string.exam_tool_pen, R.drawable.ic_pen, MarkKind.INK),

    /** Typed answers, for anyone who would rather not write on glass. */
    TEXT(R.string.exam_tool_text, R.drawable.ic_text_field, MarkKind.TEXT),

    /** Ticking a box, whether the app found it or the reader placed it. */
    TICK(R.string.exam_tool_tick, R.drawable.ic_check_circle, MarkKind.TICK),

    /** Marking the question rather than answering it. */
    HIGHLIGHTER(R.string.exam_tool_highlighter, R.drawable.ic_highlighter, MarkKind.HIGHLIGHT),

    /**
     * Removes whole marks, not parts of them.
     *
     * A pixel eraser would mean storing strokes as images rather than as
     * points, which is what everything else here is built to avoid. Erasing a
     * whole stroke is also what is nearly always meant: a letter written wrong
     * is rewritten, not shaved.
     */
    ERASER(R.string.exam_tool_eraser, R.drawable.ic_eraser),
    ;

    val writes: Boolean get() = kind != null

    /** True when the pager should stop taking swipes and the arrows take over. */
    val takesTheFinger: Boolean get() = this != VIEW
}

/**
 * The ink the reader writes in.
 *
 * Not [com.david.llegeix.ui.common.HighlightColors]: that palette is six pale
 * washes chosen to be legible *behind* black text, and handwriting in pale
 * yellow on a white page cannot be read at all. These are pen colours — the
 * ones a person actually sits an exam in, plus red, which is what marking is
 * done in.
 */
object ExamInk {

    val Black = 0xFF1A1A1A.toInt()
    val Blue = 0xFF1A4FD6.toInt()
    val Red = 0xFFD32F2F.toInt()
    val Green = 0xFF2E7D32.toInt()

    val palette: List<Int> = listOf(Black, Blue, Red, Green)

    val Default: Int = Blue

    @StringRes
    fun nameOf(argb: Int): Int = when (argb) {
        Black -> R.string.exam_ink_black
        Blue -> R.string.exam_ink_blue
        Red -> R.string.exam_ink_red
        else -> R.string.exam_ink_green
    }
}

/**
 * How thick a stroke is, as a fraction of the page's width.
 *
 * Fractions for the same reason positions are: a stroke stored in pixels would
 * change weight with the zoom and stop looking like handwriting. These are
 * tuned so that at a typical phone width the fine nib is about two pixels — a
 * ballpoint — and the highlighter covers a line of type.
 */
object StrokeWidths {
    const val FINE = 0.0025f
    const val MEDIUM = 0.0045f
    const val THICK = 0.008f
    const val HIGHLIGHTER = 0.028f

    val pen: List<Float> = listOf(FINE, MEDIUM, THICK)
}

/**
 * How large typed text is, as a fraction of the page's width.
 *
 * One size rather than a choice. An answer written into a gap on a printed page
 * has a size the page already decided, and offering three would be offering a
 * decision with no good answer.
 */
const val TEXT_SIZE_FRACTION = 0.022f

/** How large a placed tick is, as a fraction of the page's width. */
const val TICK_SIZE_FRACTION = 0.032f
