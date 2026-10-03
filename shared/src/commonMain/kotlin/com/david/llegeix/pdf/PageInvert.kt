package com.david.llegeix.pdf

import androidx.compose.ui.graphics.ImageBitmap
import com.david.llegeix.platform.imageFromPixels

/**
 * Turns a page light-on-dark without turning its photographs into negatives.
 *
 * A plain negative is the right answer for a page of type and the wrong one for
 * a page with a picture on it: black text on white paper becomes white text on
 * black paper, which is what somebody reading at night asked for, but a
 * photograph becomes an X-ray of itself and a colour diagram becomes a diagram
 * in the wrong colours — and a language textbook is mostly pictures of the thing
 * being described.
 *
 * PDFium hands over pixels and nothing else: there is no object model here to
 * ask "which of you is an image". So the picture regions are found by looking
 * at the pixels, on the one property that reliably separates print from
 * photograph — **printed text sits on paper, and paper is nearly white.**
 *
 * The page is divided into blocks. A block of text is mostly paper with a few
 * dark strokes crossing it, so a large majority of its pixels are near-white. A
 * block of photograph is not: its pixels are spread across the range, or they
 * are coloured, and very few of them are paper. Blocks that fail the paper test
 * are candidates, and candidates are then kept only if they belong to a
 * *contiguous run* big enough to be a picture — which is what stops a heading in
 * 40-point type, a black table header or a solid rule from being mistaken for
 * one. Whatever survives is left exactly as it was drawn; everything else is
 * inverted.
 *
 * The result is the page an e-reader gives you in dark mode: dark paper, light
 * type, and the illustrations still looking like illustrations, sitting in their
 * own light rectangles.
 *
 * It is a heuristic and it is meant to be. Getting it slightly wrong leaves a
 * small patch of a page uninverted, which is a blemish; the alternative — no
 * detection at all — makes every photograph unreadable, which is a bug.
 */
object PageInvert {

    /**
     * How wide a block is, in pixels, at the size pages are rendered.
     *
     * Small enough that a picture's edges land within a block or two of where
     * they really are, large enough that one block holds a meaningful sample —
     * a 16×16 block of a line of 10-point type is a few hundred pixels of paper
     * and a few dozen of ink, which is exactly the signal being tested for.
     */
    private const val BLOCK = 16

    /** Above this, on all three channels, a pixel counts as paper. */
    private const val PAPER_LEVEL = 210

    /**
     * How much of a block has to be paper for the block to be print.
     *
     * Deliberately low. A page of dense type is still about three-quarters
     * paper; a heading in large type can drop to half. Photographs sit far
     * below either, so the threshold has a lot of room underneath it and the
     * cost of being wrong falls on the safe side — a block called print gets
     * inverted, which is the behaviour that was there before this existed.
     */
    private const val PAPER_SHARE = 0.35f

    /**
     * A block this saturated is coloured, and coloured means picture.
     *
     * Coloured *text* exists, but coloured text is still text on paper and is
     * caught by the paper test long before this one is reached: this only
     * decides blocks that already failed it.
     */
    private const val COLOURFUL = 60

    /**
     * The smallest run of blocks that can be a picture, in blocks.
     *
     * Both dimensions, so a picture has to be about 64×64 pixels before it is
     * treated as one. Below that it is a bullet, a logo, a rule or a very large
     * letter, all of which look better inverted with the text around them.
     */
    private const val MIN_PICTURE_BLOCKS = 4

    /**
     * A negative of [source] with its pictures left alone.
     *
     * Returns a new bitmap; [source] is not touched, because it is the one in
     * the page cache and the reader may be looking at it un-inverted a moment
     * later.
     */
    fun invertKeepingPictures(source: ImageBitmap): ImageBitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        val pixels = IntArray(width * height)
        source.readPixels(pixels)

        val picture = pictureMask(pixels, width, height)
        val blocksAcross = (width + BLOCK - 1) / BLOCK

        for (y in 0 until height) {
            val blockRow = (y / BLOCK) * blocksAcross
            val rowStart = y * width
            for (x in 0 until width) {
                if (picture[blockRow + x / BLOCK]) continue
                val pixel = pixels[rowStart + x]
                // The alpha channel is carried through untouched: a page
                // rendered with transparency has transparent margins, and
                // inverting those would paint a black border round the sheet.
                pixels[rowStart + x] = (pixel and ALPHA) or (pixel.inv() and RGB)
            }
        }

        return imageFromPixels(pixels, width, height)
    }

    private const val ALPHA = 0xFF000000.toInt()
    private const val RGB = 0x00FFFFFF

    /**
     * Which blocks of the page are picture rather than print.
     *
     * Two passes: classify every block on its own pixels, then discard the
     * candidates that are not part of a big enough rectangle of candidates. The
     * second pass is the one doing the real work — a page of body text produces
     * scattered candidate blocks wherever a word happens to be dense, and
     * inverting around them would leave the page speckled.
     *
     * Exposed for the test, which is the only way to check this without
     * eyeballing a rendered page.
     */
    internal fun pictureMask(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val blocksAcross = (width + BLOCK - 1) / BLOCK
        val blocksDown = (height + BLOCK - 1) / BLOCK
        val candidate = BooleanArray(blocksAcross * blocksDown)

        for (by in 0 until blocksDown) {
            for (bx in 0 until blocksAcross) {
                val x0 = bx * BLOCK
                val y0 = by * BLOCK
                val x1 = minOf(x0 + BLOCK, width)
                val y1 = minOf(y0 + BLOCK, height)
                var paper = 0
                var counted = 0
                var colourful = 0
                for (y in y0 until y1) {
                    val rowStart = y * width
                    for (x in x0 until x1) {
                        val pixel = pixels[rowStart + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        counted++
                        if (r >= PAPER_LEVEL && g >= PAPER_LEVEL && b >= PAPER_LEVEL) paper++
                        val spread = maxOf(r, g, b) - minOf(r, g, b)
                        if (spread >= COLOURFUL) colourful++
                    }
                }
                if (counted == 0) continue
                val paperShare = paper.toFloat() / counted
                val colourShare = colourful.toFloat() / counted
                candidate[by * blocksAcross + bx] =
                    paperShare < PAPER_SHARE || colourShare > 0.5f
            }
        }

        return keepOnlyLargeRegions(candidate, blocksAcross, blocksDown)
    }

    /**
     * Drop candidate blocks that are not part of a picture-sized region.
     *
     * Flood fill over the candidates, then keep a region only if its bounding
     * box is at least [MIN_PICTURE_BLOCKS] blocks in both directions. A run of
     * heading text is wide and one block tall, so it fails on height; a black
     * rule fails the same way; a large initial capital fails on both.
     */
    private fun keepOnlyLargeRegions(
        candidate: BooleanArray,
        across: Int,
        down: Int,
    ): BooleanArray {
        val kept = BooleanArray(candidate.size)
        val seen = BooleanArray(candidate.size)
        val stack = ArrayDeque<Int>()
        val region = ArrayList<Int>()

        for (start in candidate.indices) {
            if (!candidate[start] || seen[start]) continue
            region.clear()
            stack.addLast(start)
            seen[start] = true
            var minX = across
            var maxX = -1
            var minY = down
            var maxY = -1

            while (stack.isNotEmpty()) {
                val index = stack.removeLast()
                region += index
                val x = index % across
                val y = index / across
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                // Four-connected. Diagonal joins would let two separate
                // pictures touching at a corner count as one big one.
                if (x > 0) push(stack, seen, candidate, index - 1)
                if (x < across - 1) push(stack, seen, candidate, index + 1)
                if (y > 0) push(stack, seen, candidate, index - across)
                if (y < down - 1) push(stack, seen, candidate, index + across)
            }

            val bigEnough = (maxX - minX + 1) >= MIN_PICTURE_BLOCKS &&
                (maxY - minY + 1) >= MIN_PICTURE_BLOCKS
            if (bigEnough) {
                // The bounding box rather than the blocks themselves, so a
                // photograph with a pale sky in it comes out whole instead of
                // with its light parts inverted into holes.
                for (y in minY..maxY) {
                    for (x in minX..maxX) kept[y * across + x] = true
                }
            }
        }
        return kept
    }

    private fun push(
        stack: ArrayDeque<Int>,
        seen: BooleanArray,
        candidate: BooleanArray,
        index: Int,
    ) {
        if (!seen[index] && candidate[index]) {
            seen[index] = true
            stack.addLast(index)
        }
    }
}
