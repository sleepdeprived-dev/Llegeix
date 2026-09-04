package com.david.llegeix.ui.bookmarks

import com.david.llegeix.data.db.entity.WordBookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The fortnight of bars above the saved words.
 *
 * Small, but it is the only place in the app that makes a claim about the
 * reader's own habits, so it had better not be able to make a wrong one.
 */
class ActivityStripTest {

    private val now = 1_700_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun word(savedAt: Long) = WordBookmarkEntity(
        word = "enrenou",
        translation = "commotion",
        ipa = null,
        context = null,
        documentUri = null,
        displayName = null,
        pageIndex = 0,
        lineNumber = 1,
        createdAt = savedAt,
    )

    @Test
    fun `an empty list is a fortnight of nothing`() {
        val counts = countPerDay(emptyList(), now)
        assertEquals(ACTIVITY_DAYS, counts.size)
        assertEquals(0, counts.sum())
    }

    @Test
    fun `today is the last bar`() {
        val counts = countPerDay(listOf(word(now), word(now - 1000)), now)
        assertEquals(2, counts.last())
        assertEquals(2, counts.sum())
    }

    @Test
    fun `yesterday is the bar before it`() {
        val counts = countPerDay(listOf(word(now - day - 1)), now)
        assertEquals(1, counts[ACTIVITY_DAYS - 2])
        assertEquals(0, counts.last())
    }

    @Test
    fun `anything older than the fortnight is left out`() {
        val counts = countPerDay(listOf(word(now - day * ACTIVITY_DAYS - 1)), now)
        assertEquals(0, counts.sum())
    }

    @Test
    fun `a clock that has gone backwards does not crash the strip`() {
        // Devices do move their clocks, and a word saved "in the future" must
        // not index off the front of the array.
        val counts = countPerDay(listOf(word(now + day * 3)), now)
        assertEquals(ACTIVITY_DAYS, counts.size)
        assertEquals(0, counts.sum())
    }
}
