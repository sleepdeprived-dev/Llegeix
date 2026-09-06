package com.david.llegeix.data.exam

/**
 * How a stroke's shape is written into one database column.
 *
 * A freehand stroke is a few hundred points and there are potentially thousands
 * of strokes in a sitting, so this is one of the few places in the app where
 * the encoding is worth thinking about rather than reaching for the obvious.
 *
 * The obvious would be a row per point, and it is the wrong shape: a stroke is
 * only ever read and written whole — nobody edits the fourteenth point of a
 * pen stroke — so splitting it up buys nothing and costs a join, an index and
 * a few hundred rows per scribble.
 *
 * JSON is the other obvious answer, and this app does not have a JSON library
 * on its main classpath by choice (see the note in the build file: the real
 * org.json is a test dependency, and Android's own is a stub there). Pulling one
 * in to write pairs of numbers would be a dependency for a comma.
 *
 * So: pairs of fractions, `x,y` separated by spaces. Four decimal places, which
 * on a page rendered at any size a phone or tablet has is finer than a pixel and
 * cuts the stored size of a long stroke by more than half against full float
 * precision.
 *
 * Decoding is deliberately forgiving. These strings are read back months after
 * they were written, possibly by a later version of the app, and a stroke with
 * one unreadable point should lose that point rather than take the page down.
 */
object MarkGeometry {

    /** Points, as fractions of the page, flattened to `x1,y1 x2,y2 …`. */
    fun encode(points: List<Point>): String =
        points.joinToString(" ") { "${round(it.x)},${round(it.y)}" }

    fun decode(encoded: String?): List<Point> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(' ').mapNotNull { pair ->
            val comma = pair.indexOf(',')
            if (comma <= 0) return@mapNotNull null
            val x = pair.substring(0, comma).toFloatOrNull() ?: return@mapNotNull null
            val y = pair.substring(comma + 1).toFloatOrNull() ?: return@mapNotNull null
            Point(x, y)
        }
    }

    /**
     * Rectangles, for a highlight, as `left,top right,bottom` pairs in sequence.
     *
     * The same encoding as a path rather than one of its own: a rectangle is
     * two corners, so a highlight is a list of points read two at a time. One
     * format to read and one to write, and a highlight whose list has an odd
     * length simply drops its dangling corner.
     */
    fun encodeRects(rects: List<Rect>): String =
        encode(rects.flatMap { listOf(Point(it.left, it.top), Point(it.right, it.bottom)) })

    fun decodeRects(encoded: String?): List<Rect> =
        decode(encoded).chunked(2)
            .filter { it.size == 2 }
            .map { (topLeft, bottomRight) ->
                Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
            }

    /** The smallest box containing every point, for the eraser to test against. */
    fun boundsOf(points: List<Point>): Rect {
        if (points.isEmpty()) return Rect(0f, 0f, 0f, 0f)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (point in points) {
            if (point.x < left) left = point.x
            if (point.x > right) right = point.x
            if (point.y < top) top = point.y
            if (point.y > bottom) bottom = point.y
        }
        return Rect(left, top, right, bottom)
    }

    private fun round(value: Float): String {
        // Four places, without trailing zeros, and without String.format, which
        // would render "0.5" as "0,5" for a reader whose phone is in Catalan
        // and produce a string this object could not read back.
        val scaled = Math.round(value * SCALE)
        val whole = scaled / SCALE_INT
        val fraction = kotlin.math.abs(scaled % SCALE_INT)
        if (fraction == 0) return whole.toString()
        val digits = fraction.toString().padStart(PLACES, '0').trimEnd('0')
        val sign = if (scaled < 0 && whole == 0) "-" else ""
        return "$sign$whole.$digits"
    }

    private const val PLACES = 4
    private const val SCALE_INT = 10_000
    private const val SCALE = SCALE_INT.toFloat()

    /** A position on a page, as a fraction of its width and height. */
    data class Point(val x: Float, val y: Float)

    /** A box on a page, as fractions. Edges are not assumed to be ordered. */
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
