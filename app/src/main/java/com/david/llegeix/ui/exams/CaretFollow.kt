package com.david.llegeix.ui.exams

/**
 * Where a page has to be scrolled so the line being typed can be seen.
 *
 * Its own object, and tested, for the same reason [PageFit] is: getting it
 * wrong does not look like a bug. A sign the wrong way round is a page that
 * scrolls away from the cursor, a clamp forgotten is a page that scrolls into
 * half a screen of empty desk, and both read as "this app is fighting me"
 * rather than as arithmetic anybody could point at.
 *
 * ### Why the page is moved at all
 *
 * A text field in a list is kept in view by the list. Here the field is pinned
 * over part of a fixed sheet of paper, and nothing in that arrangement moves
 * the sheet when the writing reaches the bottom of the screen — so the essay
 * went on being typed underneath the keyboard, and the only way to see it was
 * to push the page up by hand every few lines.
 *
 * ### Why a band
 *
 * The cursor is left alone while it is anywhere reasonable and put back to
 * [REST] when it is not, so the page moves once every several lines instead of
 * sliding under every character, which is its own kind of seasickness.
 *
 * [BAND_BOTTOM] is well above the bottom of the screen on purpose. A docked
 * keyboard has already been taken out of the room the page is given by the time
 * this runs, but a *floating* keyboard is a window of its own and reports no
 * inset at all — so the app cannot be told where it is. A cursor kept in the
 * upper middle of what can be seen is clear of anything sitting along the foot
 * of the screen, whichever kind of keyboard is up.
 */
object CaretFollow {

    /** The highest up the visible area the cursor is left alone. */
    const val BAND_TOP = 0.05f

    /** And the lowest, as a share of what can be seen. */
    const val BAND_BOTTOM = 0.6f

    /** Where the cursor is put back to when it leaves the band. */
    const val REST = 0.35f

    /**
     * The scroll the page should be at, or null to leave it where it is.
     *
     * Everything is in pixels. [caretTopPx] and [caretBottomPx] are measured
     * down the page itself, unmagnified, which is what the editor can say;
     * [pageTopPx] is where the page's own top edge sits inside the scrolling
     * content, which is the slack above it plus its margin. [lowestPx] is the
     * scroll that puts the page's top edge at the top of the screen — there is
     * half a screen of desk above that and never a reason to look at it.
     *
     * [alwaysPlace] is the first look after the cursor appears, which places it
     * even if it could already be seen: somebody coming back to a half-written
     * page should be looking at the line they stopped on.
     */
    fun scrollFor(
        caretTopPx: Float,
        caretBottomPx: Float,
        pageTopPx: Float,
        zoom: Float,
        scrolledPx: Int,
        furthestPx: Int,
        viewportPx: Float,
        lowestPx: Int,
        alwaysPlace: Boolean,
    ): Int? {
        if (viewportPx <= 0f || caretTopPx.isNaN() || caretBottomPx.isNaN()) return null
        // Where the cursor is on the screen right now. The magnification is
        // anchored at the page's top edge, so a line halfway down a page drawn
        // at twice life size is twice as far from that edge.
        val seenTop = pageTopPx + caretTopPx * zoom - scrolledPx
        val seenBottom = pageTopPx + caretBottomPx * zoom - scrolledPx
        val comfortable = seenTop >= viewportPx * BAND_TOP &&
            seenBottom <= viewportPx * BAND_BOTTOM
        if (comfortable && !alwaysPlace) return null

        val furthest = furthestPx.toFloat()
        // A page shorter than the slack above it cannot be scrolled as far as
        // [lowestPx]; asking for a range that runs backwards would throw.
        val lowest = lowestPx.toFloat().coerceAtMost(furthest)
        val target = (pageTopPx + caretTopPx * zoom - viewportPx * REST)
            .coerceIn(lowest, furthest)
            .toInt()
        return if (target == scrolledPx) null else target
    }
}
