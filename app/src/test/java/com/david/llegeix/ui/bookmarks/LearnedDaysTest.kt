package com.david.llegeix.ui.bookmarks

import com.david.llegeix.data.db.entity.LearnedWordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LearnedDaysTest {

    private fun at(day: Int, hour: Int) =
        LocalDate.of(2026, 9, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `words are grouped by day, newest day first, and empty days are not there`() {
        val words = listOf(
            LearnedWordEntity(1, "paraula", "cuvânt", at(25, 9)),
            LearnedWordEntity(2, "poma", "măr", at(25, 18)),
            LearnedWordEntity(3, "pera", "pară", at(23, 12)),
        )
        val days = LearnedWordsViewModel.group(words, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 23)), days.map { it.date })
        assertEquals(listOf("poma", "paraula"), days.first().words.map { it.catalan })
    }
}
