package com.david.llegeix.pdf

import android.graphics.Bitmap

/**
 * Where the ink actually is on a page, as fractions of its width and height.
 *
 * Fractions rather than pixels so the answer survives being asked at one
 * resolution and used at another: the margins are found on a cheap thumbnail
 * and applied to the full-size render.
 */
data class ContentBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /**
     * Whether cropping to this would actually change anything worth the work.
     *
     * A page already filled edge to edge — a full-bleed scan, a photograph, a
     * cover — comes back as very nearly the whole page, and cropping it by two
     * per cent is a re-render for no gain and a page that no longer lines up
     * with its neighbours.
     */
    val isWorthCropping: Boolean
        get() = width < WORTH_CROPPING && height < WORTH_CROPPING

    companion object {
        /** The whole page: what a blank or unreadable page falls back to. */
        val Whole = ContentBox(0f, 0f, 1f, 1f)

        private const val WORTH_CROPPING = 0.97f
    }
}

/**
 * Finds the margins of a rendered page by looking at it.
 *
 * Deliberately not done through the text layer, which knows exactly where the
 * characters are: half the documents this app is for are scans, and a scan has
 * no text layer at all until it has been through recognition. Pixels are the
 * one thing every page has.
 *
 * The test is per row and per column rather than per pixel, because a single
 * dark speck — a scanning artefact, a fold, dust on the glass — sits in the
 * margin of a great many scans and would otherwise pin the crop to the edge of
 * the page and quietly do nothing at all.
 *
 * What separates a speck from a line of type is not an absolute number of dark
 * pixels — that depends on the size the page was rendered at and on how much
 * text is on it — but how the row compares with the busiest row on the same
 * page. A line of type is within an order of magnitude of the densest line; a
 * speck is one or two pixels against dozens.
 */
fun contentBoxOf(bitmap: Bitmap): ContentBox {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return ContentBox.Whole
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return contentBoxOf(pixels, width, height)
}

/**
 * The same thing, over pixels rather than a bitmap.
 *
 * Split out so the decision itself can be tested on the JVM. Everything
 * interesting here — what counts as paper, what counts as ink, how much of a
 * row has to be ink before the row counts — is arithmetic over an array, and
 * needing an emulator to check it would mean nobody ever does.
 */
fun contentBoxOf(pixels: IntArray, width: Int, height: Int): ContentBox {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return ContentBox.Whole

    // Judged against the page's own paper rather than against white. A scan is
    // grey, an aged book is yellow, and a fixed threshold would call the whole
    // of either one content and crop nothing.
    val paper = paperLuminance(pixels)
    val darkEnough = paper - INK_MARGIN

    val rowInk = IntArray(height)
    val columnInk = IntArray(width)
    for (y in 0 until height) {
        val rowStart = y * width
        for (x in 0 until width) {
            if (luminanceOf(pixels[rowStart + x]) < darkEnough) {
                rowInk[y]++
                columnInk[x]++
            }
        }
    }

    // Measured against the busiest row and column on this page, with a floor of
    // two pixels so that a page holding nothing but a speck still comes back
    // blank rather than treating its one mark as the densest line on it.
    val minRow = ((rowInk.max()) * SIGNAL_FRACTION).toInt().coerceAtLeast(MIN_INK_PIXELS)
    val minColumn = ((columnInk.max()) * SIGNAL_FRACTION).toInt().coerceAtLeast(MIN_INK_PIXELS)

    val top = rowInk.indexOfFirst { it >= minRow }
    if (top < 0) return ContentBox.Whole
    val bottom = rowInk.indexOfLast { it >= minRow }
    val left = columnInk.indexOfFirst { it >= minColumn }
    if (left < 0) return ContentBox.Whole
    val right = columnInk.indexOfLast { it >= minColumn }

    // A sliver of margin is kept on every side. Type cropped flush to its own
    // ascenders reads as though the page has been cut badly, and descenders on
    // the last line genuinely are clipped by an exact box.
    val padX = width * BREATHING_ROOM
    val padY = height * BREATHING_ROOM
    return ContentBox(
        left = ((left - padX) / width).coerceIn(0f, 1f),
        top = ((top - padY) / height).coerceIn(0f, 1f),
        right = ((right + 1 + padX) / width).coerceIn(0f, 1f),
        bottom = ((bottom + 1 + padY) / height).coerceIn(0f, 1f),
    )
}

/**
 * What this page's blank paper looks like, taken as the brightest common tone.
 *
 * The 90th percentile rather than the maximum: one blown-out white pixel is
 * common and would set the reference for the whole page, while nine tenths of
 * a typical page genuinely is paper.
 */
private fun paperLuminance(pixels: IntArray): Int {
    val histogram = IntArray(256)
    var step = 1
    // Sampling, because this runs on every page and the answer is a broad one.
    if (pixels.size > SAMPLE_TARGET) step = pixels.size / SAMPLE_TARGET
    var counted = 0
    var index = 0
    while (index < pixels.size) {
        histogram[luminanceOf(pixels[index])]++
        counted++
        index += step
    }
    if (counted == 0) return 255
    val target = (counted * PAPER_PERCENTILE).toInt()
    var seen = 0
    for (level in 0..255) {
        seen += histogram[level]
        if (seen >= target) return level
    }
    return 255
}

/** Rec. 601 luma, integer, which is all this decision needs. */
private fun luminanceOf(color: Int): Int {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return (red * 77 + green * 151 + blue * 28) shr 8
}

/** How much darker than the paper a pixel must be to count as ink. */
private const val INK_MARGIN = 40

/**
 * How dark a row must be, against the darkest row on the page, to count.
 *
 * Low, because the first and last lines of a page are often short — a heading,
 * a page number, the last line of a paragraph — and cropping them off is far
 * worse than leaving a little margin on.
 */
private const val SIGNAL_FRACTION = 0.04f

/** Below this many dark pixels, a row is dirt whatever the rest of the page is. */
private const val MIN_INK_PIXELS = 2

/** Margin left around the content, as a fraction of the page. */
private const val BREATHING_ROOM = 0.012f

private const val PAPER_PERCENTILE = 0.9f

/** Pixels sampled when working out what the paper looks like. */
private const val SAMPLE_TARGET = 20_000
