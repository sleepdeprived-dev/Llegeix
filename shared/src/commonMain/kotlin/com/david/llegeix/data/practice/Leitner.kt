package com.david.llegeix.data.practice

import java.util.concurrent.TimeUnit

/**
 * When a saved word should be asked about next.
 *
 * The Leitner system, which is the oldest and smallest thing that works: a word
 * answered correctly moves up a box and is not asked again for longer; a word
 * answered wrongly goes back to the first box and returns almost immediately.
 * No half-life estimates, no per-word difficulty model — those need far more
 * data than a reader saving twenty words a week will ever produce, and the
 * schedule they buy is not measurably better than this one.
 *
 * Everything here is a pure function of the box and the clock, so the intervals
 * can be changed later without migrating a single row: the box is what is
 * stored, and the schedule is derived from it.
 */
object Leitner {

    /**
     * How long each box waits, in days.
     *
     * Roughly doubling, starting the day after. The last box is over a month,
     * at which point a word is either known or was never going to be learned by
     * being shown on a card.
     */
    private val INTERVAL_DAYS = intArrayOf(1, 3, 7, 16, 35, 90)

    /** The highest box a word can reach. */
    val LAST_BOX: Int = INTERVAL_DAYS.lastIndex

    /**
     * How soon a word answered wrongly comes back.
     *
     * Minutes rather than a day. The point of getting one wrong is that it
     * needs to be seen again, and a scheduler that says "tomorrow" turns a
     * ten-minute session into one where every mistake is simply deferred.
     */
    private val RETRY_MILLIS = TimeUnit.MINUTES.toMillis(10)

    /** Where a word lands after being answered. */
    data class Next(val box: Int, val dueAt: Long)

    fun answer(box: Int, correct: Boolean, now: Long): Next = if (correct) {
        val moved = (box + 1).coerceAtMost(LAST_BOX)
        Next(
            box = moved,
            dueAt = now + TimeUnit.DAYS.toMillis(INTERVAL_DAYS[moved].toLong()),
        )
    } else {
        Next(box = 0, dueAt = now + RETRY_MILLIS)
    }

    /**
     * How settled a word is, 0..1, for the small bar drawn against it.
     *
     * A word in the last box is not "finished" — nothing is — but it is as
     * learned as this deck has any way of saying, so the bar fills.
     */
    fun progressOf(box: Int): Float =
        (box.coerceIn(0, LAST_BOX).toFloat() / LAST_BOX).coerceIn(0f, 1f)
}
