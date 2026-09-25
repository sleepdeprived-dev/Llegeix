package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import java.text.Collator
import java.util.Locale

/**
 * How the Flashcards tab lists its collections and decks.
 *
 * Pinned ones stay at the top whatever is chosen — pinning is the reader
 * saying "this first" — and the order applies inside every collection too.
 */
enum class ListSort {
    NAME,
    NAME_DESCENDING,
    NEWEST,
    OLDEST,
    MOST_CARDS,
    ;

    /**
     * An ordering for anything with a name, a date, a pin and a card count —
     * collections and decks alike, so the two are sorted by one rule.
     */
    fun <T> comparator(
        name: (T) -> String,
        createdAt: (T) -> Long,
        pinned: (T) -> Boolean,
        cards: (T) -> Int,
    ): Comparator<T> {
        val byName = Comparator<T> { a, b -> CATALAN.compare(name(a).trim(), name(b).trim()) }
        val chosen: Comparator<T> = when (this) {
            NAME -> byName
            NAME_DESCENDING -> byName.reversed()
            NEWEST -> compareByDescending<T> { createdAt(it) }.then(byName)
            OLDEST -> compareBy<T> { createdAt(it) }.then(byName)
            MOST_CARDS -> compareByDescending<T> { cards(it) }.then(byName)
        }
        return compareByDescending<T> { pinned(it) }.then(chosen)
    }

    companion object {
        val Default = NAME

        fun fromName(name: String?): ListSort = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** How a deck lists its cards. */
enum class CardSort {
    /** The Catalan, alphabetically, the way [CardOrder] has always listed them. */
    ALPHABETICAL,
    NEWEST,
    OLDEST,

    /** The least known first: the lower Leitner boxes, both directions added together. */
    WEAKEST,
    ;

    fun sorted(cards: List<FlashcardEntity>): List<FlashcardEntity> {
        val alphabetical = CardOrder.sorted(cards)
        return when (this) {
            ALPHABETICAL -> alphabetical
            // Stable sorts over the alphabetical order, so ties read A to Z.
            NEWEST -> alphabetical.sortedByDescending { it.createdAt }
            OLDEST -> alphabetical.sortedBy { it.createdAt }
            WEAKEST -> alphabetical.sortedWith(
                compareByDescending<FlashcardEntity> { it.weakAt != null }.thenBy { it.box + it.reverseBox },
            )
        }
    }

    companion object {
        val Default = ALPHABETICAL

        fun fromName(name: String?): CardSort = entries.firstOrNull { it.name == name } ?: Default
    }
}

private val CATALAN: Collator = Collator.getInstance(Locale.forLanguageTag("ca")).apply {
    strength = Collator.TERTIARY
}
