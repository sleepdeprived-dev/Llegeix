package com.david.llegeix.data.flashcards

/**
 * Taking the white paper out from behind a pictogram, and nothing else.
 *
 * A pictogram is drawn on white, and on a dark card that white is a lamp left on.
 * The drawing itself is not to be touched — no tint, no inversion, the reader's
 * own colours exactly as ARASAAC drew them — so only the paper goes: it becomes
 * transparent, and whatever the app draws behind the picture shows through.
 *
 * ### Which white is paper
 *
 * Not every white pixel. A pictogram has whites of its own — an eye, a plate, a
 * highlight on an apple — enclosed by its outlines. The paper is the white that
 * reaches the edge of the picture, so it is found by starting at the edge and
 * spreading only through near-white pixels: anything a line closes off is part
 * of the drawing and keeps its white.
 *
 * ### The edge of a line
 *
 * Where a line meets the paper, the pixels were smoothed half-way between ink
 * and white. Left as they are they would ring every outline with a pale halo on
 * a dark card. For the one pixel of edge next to the paper only, each is split
 * back into the ink and the white it was mixed from, and the white part made
 * see-through: a mid-grey beside the paper becomes black at half strength,
 * which on white looks exactly as it did before.
 */
object PictogramMatte {

    /** Brighter than this in every channel counts as paper, allowing for JPEG noise. */
    const val PAPER_THRESHOLD = 232

    /**
     * [pixels] as ARGB, row by row; a new array with the paper transparent.
     * The input is not changed.
     */
    fun cutPaper(pixels: IntArray, width: Int, height: Int): IntArray {
        val out = pixels.copyOf()
        if (width <= 0 || height <= 0) return out
        val paper = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!paper[i] && isPaperWhite(pixels[i])) {
                paper[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            val y = i / width
            if (x > 0) seed(i - 1)
            if (x < width - 1) seed(i + 1)
            if (y > 0) seed(i - width)
            if (y < height - 1) seed(i + width)
        }
        for (i in pixels.indices) {
            if (paper[i]) {
                out[i] = 0
                continue
            }
            val x = i % width
            val y = i / width
            val touchesPaper = (x > 0 && paper[i - 1]) || (x < width - 1 && paper[i + 1]) ||
                (y > 0 && paper[i - width]) || (y < height - 1 && paper[i + width])
            if (touchesPaper) out[i] = unmixFromWhite(pixels[i])
        }
        return out
    }

    private fun isPaperWhite(argb: Int): Boolean {
        if ((argb ushr 24) == 0) return true // already transparent: nothing to keep
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r >= PAPER_THRESHOLD && g >= PAPER_THRESHOLD && b >= PAPER_THRESHOLD
    }

    /**
     * The ink a pixel would be if it were that ink laid over white at some
     * strength: the strength is how far its lightest channel falls short of
     * white, and the ink is what remains once that much white is taken out.
     */
    fun unmixFromWhite(argb: Int): Int {
        val a0 = argb ushr 24
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val alpha = (255 - minOf(r, g, b)) / 255f
        if (alpha <= 0f) return 0
        fun ink(c: Int): Int = ((c - (1f - alpha) * 255f) / alpha).toInt().coerceIn(0, 255)
        val outAlpha = (alpha * a0).toInt().coerceIn(0, 255)
        return (outAlpha shl 24) or (ink(r) shl 16) or (ink(g) shl 8) or ink(b)
    }
}
