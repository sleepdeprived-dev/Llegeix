package com.david.llegeix.ui.exams

/**
 * Where a new text box goes, and how wide it is.
 *
 * Pulled out of the ViewModel as plain arithmetic so it can be tested without a
 * device, and it earns that on its own: a text box that comes out too narrow
 * does not fail in any way anybody would call a failure. It draws, it accepts
 * the keyboard, and it wraps after two characters — which reads as the typing
 * being broken rather than as the box being three millimetres wide, and is
 * correspondingly hard to find by looking at it.
 *
 * The rule has two halves.
 *
 * **A box is never narrower than [MIN_WIDTH].** Tapping near the right margin
 * used to give a box the width of whatever was left, which near the edge is
 * nothing. It now slides left until it has room, which is what a person would
 * do with a piece of paper.
 *
 * **A box is never wider than [MAX_WIDTH].** An answer typed into a gap on a
 * printed page belongs in a column, not across the whole sheet; the box can
 * still be widened by hand afterwards, and blank pages do not come through here
 * at all — the whole page is the field there.
 */
object TextBoxGeometry {

    /**
     * Room left at the right edge, as a share of the page.
     *
     * Small. It is not a margin in the typographic sense — the printed page has
     * its own — it is only there so a box's frame and its handles are not drawn
     * half off the sheet.
     */
    const val RIGHT_MARGIN = 0.02f

    /**
     * The narrowest a new box may be, as a share of the page.
     *
     * About a fifth of the width, which at the default type size is a dozen
     * characters or so: enough that a short answer fits on one line and enough
     * that the box is obviously a box.
     */
    const val MIN_WIDTH = 0.18f

    /** The widest a new box may be. Roughly a column of a two-column layout. */
    const val MAX_WIDTH = 0.55f

    /** Where a box goes when the finger lands at [x], as (left, width). */
    fun placeAt(x: Float): Placement {
        val furthestLeft = 1f - RIGHT_MARGIN - MIN_WIDTH
        val left = x.coerceIn(0f, furthestLeft)
        val width = (1f - RIGHT_MARGIN - left).coerceIn(MIN_WIDTH, MAX_WIDTH)
        return Placement(left = left, width = width)
    }

    data class Placement(val left: Float, val width: Float)
}
