package com.david.llegeix.data.flashcards

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The arithmetic of shrinking a picture, kept apart from the decoding so it can
 * be tested without a phone.
 */
object ImageSizing {

    /**
     * The longest side a card picture is stored at.
     *
     * Enough to fill a phone's width sharply on a card, and small enough that a
     * few hundred cards cost tens of megabytes rather than gigabytes. A camera
     * photo straight off the phone is twelve times this across.
     */
    const val MAX_EDGE = 1024

    data class Size(val width: Int, val height: Int)

    /**
     * How far the decoder may shrink the picture while it reads it.
     *
     * The largest power of two that still leaves the longest side at least
     * [maxEdge]: the decoder only divides by powers of two, and shrinking below
     * the target here would mean scaling back up afterwards, which blurs. A
     * picture already small enough is read at full size.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /**
     * The size that fits inside [maxEdge] on its longest side, keeping the shape.
     *
     * Never enlarges: a small picture is kept at the size it came in.
     */
    fun fit(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Size {
        val longest = max(width, height)
        if (longest <= maxEdge) return Size(width, height)
        val scale = maxEdge.toDouble() / longest
        return Size(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
