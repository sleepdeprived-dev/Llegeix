package com.david.llegeix.pdf

/**
 * Between a rendered bitmap and the page it was drawn from.
 *
 * Four conversions, and all of them go through the [ContentBox] the bitmap
 * actually holds: the whole page normally, and the ink alone when the margins
 * are being trimmed. Bitmap pixels run top-left down; PDF user space runs
 * bottom-left up, so the vertical axis is flipped as well as scaled.
 *
 * Pulled out of the renderer and made pure so that it can be checked without a
 * PDF, a device or PDFium. It is worth checking: these were four
 * multiplications by a scale factor for as long as the app only ever drew whole
 * pages, cropping was added to the rendering side without them, and the result
 * was that pressing a word with the margins trimmed selected the one a line or
 * two below it — the top margin being exactly the part that had been taken
 * away.
 */
object PageGeometry {

    /** Where a bitmap column sits across the page, in PDF points. */
    fun xPoint(xPx: Float, renderedWidthPx: Int, widthPt: Double, shown: ContentBox): Double =
        (shown.left + (xPx / renderedWidthPx) * shown.width) * widthPt

    /** Where a bitmap row sits up the page, in PDF points from the bottom. */
    fun yPoint(yPx: Float, renderedHeightPx: Int, heightPt: Double, shown: ContentBox): Double =
        heightPt * (1.0 - (shown.top + (yPx / renderedHeightPx) * shown.height))

    /** Where a point across the page falls in the bitmap, in pixels. */
    fun xPixel(xPt: Double, renderedWidthPx: Int, widthPt: Double, shown: ContentBox): Double =
        ((xPt / widthPt) - shown.left) / shown.width * renderedWidthPx

    /** Where a point up the page falls in the bitmap, in pixels from the top. */
    fun yPixel(yPt: Double, renderedHeightPx: Int, heightPt: Double, shown: ContentBox): Double =
        ((1.0 - yPt / heightPt) - shown.top) / shown.height * renderedHeightPx
}
