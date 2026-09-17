package com.david.llegeix.ui.bookmarks

import com.david.llegeix.data.db.entity.WordBookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The words the app says you learned today.
 *
 * It is a small list with a large claim attached — the card is headed with the
 * word *today* — so the two ways of getting on to it and the boundary of the day
 * are both worth pinning down. Getting the boundary wrong is the failure that
 * would never be noticed as a bug: the card would simply be quietly wrong every
 * evening.
 */
class LearnedTodayTest {

    private val day = TimeUnit.DAYS.toMillis(1)

    /** Half past ten in the morning, in whatever zone the test machine is in. */
    private val now = startOfToday(System.currentTimeMillis()) + TimeUnit.HOURS.toMillis(10) +
        TimeUnit.MINUTES.toMillis(30)

    private val midnight = startOfToday(now)

    private fun word(
        id: Long,
        savedAt: Long,
        reviewedAt: Long? = null,
    ) = WordBookmarkEntity(
        id = id,
        word = "enrenou",
        translation = "commotion",
        ipa = null,
        context = null,
        documentUri = null,
        displayName = null,
        pageIndex = 0,
        lineNumber = 1,
        createdAt = savedAt,
        lastReviewedAt = reviewedAt,
    )

    @Test
    fun `a word saved today is on the list`() {
        val words = listOf(word(1, savedAt = now - TimeUnit.HOURS.toMillis(1)))
        assertEquals(listOf(1L), learnedSince(words, midnight).map { it.id })
    }

    @Test
    fun `a word saved months ago but practised today is on the list`() {
        val words = listOf(word(1, savedAt = now - 90 * day, reviewedAt = now - 60_000))
        assertEquals(
            "revising is learning; a day spent practising is not an empty day",
            listOf(1L),
            learnedSince(words, midnight).map { it.id },
        )
    }

    @Test
    fun `a word saved months ago and never practised is not`() {
        val words = listOf(word(1, savedAt = now - 90 * day))
        assertTrue(learnedSince(words, midnight).isEmpty())
    }

    @Test
    fun `a word last practised yesterday is not`() {
        val words = listOf(word(1, savedAt = now - 90 * day, reviewedAt = midnight - 1))
        assertTrue(
            "one millisecond before midnight is yesterday",
            learnedSince(words, midnight).isEmpty(),
        )
    }

    @Test
    fun `a word saved at the stroke of midnight is`() {
        val words = listOf(word(1, savedAt = midnight))
        assertEquals(listOf(1L), learnedSince(words, midnight).map { it.id })
    }

    @Test
    fun `a word saved and practised today appears once`() {
        val words = listOf(
            word(1, savedAt = midnight + 1000, reviewedAt = now - 1000),
        )
        assertEquals(
            "this is a list of words, not of things that happened to them",
            1,
            learnedSince(words, midnight).size,
        )
    }

    @Test
    fun `the most recently touched word comes first`() {
        val words = listOf(
            word(1, savedAt = midnight + 1000),
            word(2, savedAt = midnight + 2000),
            // Saved long ago, but practised a minute ago, so it leads.
            word(3, savedAt = now - 90 * day, reviewedAt = now - 60_000),
        )
        assertEquals(listOf(3L, 2L, 1L), learnedSince(words, midnight).map { it.id })
    }

    @Test
    fun `the day starts at local midnight`() {
        val start = startOfToday(now)
        val local = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
        assertEquals(0, local.hour)
        assertEquals(0, local.minute)
        assertEquals(0, local.second)
        assertTrue("and it is not in the future", start <= now)
        assertTrue("nor more than a day behind", now - start < day)
    }
}
