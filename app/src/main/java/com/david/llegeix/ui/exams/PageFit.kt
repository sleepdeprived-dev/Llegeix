package com.david.llegeix.ui.exams

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How big a page is drawn in the room it has been given.
 *
 * Its own object, and tested, because getting it wrong does not look like a
 * bug. A page fitted only to the width is a page whose top and bottom are
 * quietly outside the screen the moment anything above it — the recording, a
 * banner — makes the middle of the screen shorter, and a reader who cannot see
 * the first line of a question has no way of telling that the app has cropped
 * it rather than that the paper starts there.
 */
object PageFit {

    /** The width and height a sheet is drawn at. */
    data class Size(val width: Dp, val height: Dp)

    /**
     * Fit the whole sheet inside the room available.
     *
     * [ratio] is width over height, as the rendered bitmap reports it.
     * [fillWidth] forces the width-filling answer even when that makes the page
     * taller than the room: that is what typing wants, because a page shrunk to
     * fit what a keyboard leaves would put the writing at a size nobody can
     * read, and the page is made movable instead.
     */
    fun of(roomWide: Dp, roomTall: Dp, ratio: Float, fillWidth: Boolean = false): Size {
        // Nothing sensible to divide by. A page of no size draws nothing, which
        // is what a screen with no room for one should show; the alternative is
        // an infinity going into a layout.
        if (ratio <= 0f || roomWide.value <= 0f || roomTall.value <= 0f) {
            return Size(roomWide.coerceAtLeast(0.dp), roomTall.coerceAtLeast(0.dp))
        }
        // The page is relatively wider than the room, so the width runs out
        // first and the height comes from it. Otherwise the height runs out.
        val widthLimited = fillWidth || roomWide <= roomTall * ratio
        return if (widthLimited) {
            Size(width = roomWide, height = roomWide / ratio)
        } else {
            Size(width = roomTall * ratio, height = roomTall)
        }
    }
}
