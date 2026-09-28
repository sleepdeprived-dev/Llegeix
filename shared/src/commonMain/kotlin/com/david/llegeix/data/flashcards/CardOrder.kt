package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import java.text.Collator
import java.util.Locale

/**
 * The order a deck's cards are listed in: alphabetical, the way Catalan is.
 *
 * Sorted here rather than by SQLite, whose `NOCASE` knows only the unaccented
 * English letters and so files *àvia* after *zona*. A Catalan collator treats
 * the accent as a finer difference than the letter, which puts *àvia* next to
 * *avi*, where somebody scanning the list for it will look.
 */
object CardOrder {

    fun sorted(cards: List<FlashcardEntity>): List<FlashcardEntity> {
        val collator = Collator.getInstance(Locale.forLanguageTag("ca")).apply {
            strength = Collator.TERTIARY
        }
        return cards.sortedWith { a, b ->
            collator.compare(a.catalan.trim(), b.catalan.trim()).takeIf { it != 0 }
                ?: collator.compare(a.romanian.trim(), b.romanian.trim()).takeIf { it != 0 }
                ?: a.id.compareTo(b.id)
        }
    }
}
