package com.david.llegeix.pdf

/** A box found on a page, in fractions of that page. */
data class TickBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Finds the little squares on an exam paper that are meant to be ticked.
 *
 * ### What this is, and what it is not
 *
 * This is a **snapping aid**, not a source of truth, and the whole design
 * follows from that. It looks at the pixels of a rendered page for square
 * outlines of about the right size; some it will miss, and some it will invent
 * out of a table cell or a letter O. That is unavoidable — a PDF's checkboxes
 * are usually just drawn lines with nothing in the file to say what they mean —
 * so nothing in the app depends on it being right.
 *
 * What it does instead is make ticking accurate when it works and harmless when
 * it does not: a tap near a box it found snaps neatly into that box, and a tap
 * anywhere else places a tick exactly where the finger went. A reader whose
 * paper defeats the detector entirely loses nothing but the snapping.
 *
 * ### How it works
 *
 * Deliberately the simplest thing that could work, rather than edge detection
 * or contour finding: the page is thresholded to ink and not-ink, horizontal
 * runs of ink of a plausible length are taken as candidate top edges, and each
 * is kept only if there is a matching bottom edge below it and enough ink down
 * both sides. Four edges of similar length, roughly square, at the size a
 * checkbox is printed at.
 *
 * It is pure arithmetic over a pixel array with no Android types in it, which
 * is what lets it be tested against drawn-by-hand pages on an ordinary JVM
 * rather than only ever on a device.
 */
object TickBoxes {

    /**
     * @param pixels ARGB pixels, row-major, as [android.graphics.Bitmap.getPixels] gives them.
     */
    fun detect(pixels: IntArray, width: Int, height: Int): List<TickBox> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()

        val minSide = (width * MIN_SIDE_FRACTION).toInt().coerceAtLeast(4)
        val maxSide = (width * MAX_SIDE_FRACTION).toInt().coerceAtLeast(minSide + 1)

        val ink = BooleanArray(width * height)
        for (i in 0 until width * height) ink[i] = pixels[i].isInk()

        // Collected in pixels and converted only at the end: the overlap test
        // that removes duplicate detections is an integer comparison, and doing
        // it in fractions would compare floats for containment.
        val found = ArrayList<PixelBox>()
        var y = 0
        while (y < height - minSide) {
            for (run in horizontalRuns(ink, width, y, minSide, maxSide)) {
                val side = run.last - run.first + 1
                val box = boxUnder(ink, width, height, run.first, run.last, y, side, minSide, maxSide)
                if (box != null && found.none { it.overlaps(box) }) found += box
            }
            y++
        }
        return found.map { it.toFractions(width, height) }
    }

    /** Runs of ink on one row whose length could be the top of a checkbox. */
    private fun horizontalRuns(
        ink: BooleanArray,
        width: Int,
        y: Int,
        minSide: Int,
        maxSide: Int,
    ): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var x = 0
        val row = y * width
        while (x < width) {
            if (!ink[row + x]) {
                x++
                continue
            }
            val start = x
            while (x < width && ink[row + x]) x++
            val length = x - start
            if (length in minSide..maxSide) runs += start until x
        }
        return runs
    }

    /**
     * A closed box hanging below a candidate top edge, or null.
     *
     * The bottom edge is looked for only at the distances that would make the
     * shape roughly square, because a checkbox is square and a long thin
     * rectangle at this size is a table cell or an underline for writing on.
     */
    private fun boxUnder(
        ink: BooleanArray,
        width: Int,
        height: Int,
        left: Int,
        right: Int,
        top: Int,
        side: Int,
        minSide: Int,
        maxSide: Int,
    ): PixelBox? {
        val from = (side * MIN_ASPECT).toInt().coerceAtLeast(minSide)
        val to = (side * MAX_ASPECT).toInt().coerceAtMost(maxSide)
        for (depth in from..to) {
            val bottom = top + depth
            if (bottom >= height) break
            if (!isEdge(ink, width, left, right, bottom)) continue
            if (!hasSide(ink, width, left, top, bottom)) continue
            if (!hasSide(ink, width, right, top, bottom)) continue
            return PixelBox(left, top, right, bottom)
        }
        return null
    }

    /** Enough ink along a horizontal span to count as a drawn edge. */
    private fun isEdge(ink: BooleanArray, width: Int, left: Int, right: Int, y: Int): Boolean {
        val row = y * width
        var hits = 0
        for (x in left..right) if (ink[row + x]) hits++
        return hits >= (right - left + 1) * EDGE_COVERAGE
    }

    /**
     * Enough ink down one side to count as a drawn edge.
     *
     * The column is allowed to wander by a pixel either way. A box drawn at an
     * angle of a fraction of a degree — which is what a scanned page is — has
     * sides that do not sit in a single column, and demanding one would find
     * nothing on exactly the papers this is most wanted for.
     */
    private fun hasSide(ink: BooleanArray, width: Int, x: Int, top: Int, bottom: Int): Boolean {
        var hits = 0
        for (y in top..bottom) {
            val row = y * width
            val wandered = (x - 1..x + 1).any { it in 0 until width && ink[row + it] }
            if (wandered) hits++
        }
        return hits >= (bottom - top + 1) * EDGE_COVERAGE
    }

    private fun Int.isInk(): Boolean {
        // Luminance, weighted the way an eye weights it. A printed box is
        // usually black but is grey once it has been through a scanner, so the
        // threshold is well above zero.
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 < INK_THRESHOLD
    }

    private data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {

        /**
         * Whether two detections are the same box found twice.
         *
         * A drawn line is two or three pixels thick, so the same square is found
         * again from each row of its top edge. Any overlap at all means the same
         * box: real checkboxes on a paper do not sit inside one another.
         */
        fun overlaps(other: PixelBox): Boolean =
            left <= other.right && other.left <= right &&
                top <= other.bottom && other.top <= bottom

        fun toFractions(width: Int, height: Int) = TickBox(
            left = left.toFloat() / width,
            top = top.toFloat() / height,
            right = (right + 1).toFloat() / width,
            bottom = (bottom + 1).toFloat() / height,
        )
    }

    /** Below this luminance a pixel counts as ink. */
    private const val INK_THRESHOLD = 140

    /**
     * How large a checkbox is, as a fraction of the page's width.
     *
     * A tick box on a printed A4 paper is between about three and eight
     * millimetres square. The upper bound is what keeps table cells, answer
     * frames and page borders out of the results.
     */
    private const val MIN_SIDE_FRACTION = 0.012f
    private const val MAX_SIDE_FRACTION = 0.055f

    /** How far from square a shape may be and still count as a checkbox. */
    private const val MIN_ASPECT = 0.65f
    private const val MAX_ASPECT = 1.55f

    /**
     * How much of an edge has to be inked.
     *
     * Not all of it: a printed box often has a hairline gap where the tick
     * crosses it, and a scan loses a pixel here and there.
     */
    private const val EDGE_COVERAGE = 0.8f
}
