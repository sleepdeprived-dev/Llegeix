package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Finding a card in a deck by either of its words.
 *
 * Accents are ignored on both sides. Somebody searching their own cards is
 * looking for a word they half remember, very often on a keyboard set to the
 * other language: *cafe* should find *cafè*, *masina* should find *mașină*, and
 * *collegi* should find *col·legi*. A search that insisted on the exact accent
 * would fail at exactly the moment it is needed.
 */
object CardSearch {

    /** The cards in [cards] that [query] finds, in the order they were given. */
    fun filter(cards: List<FlashcardEntity>, query: String): List<FlashcardEntity> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return cards
        return cards.filter { card ->
            val haystack = fold(card.catalan) + " " + fold(card.romanian)
            words.all { it in haystack }
        }
    }

    /**
     * Lower case, without accents, and with the punctuation a word can carry
     * inside it taken out.
     *
     * NFD splits an accented letter into the letter and its mark, which is then
     * dropped — Catalan's grave and acute accents and diaeresis, and Romanian's
     * breve, circumflex and comma below, all alike. The *punt volat* of *l·l*
     * goes too, and so does the full stop people type in its place.
     */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(INNER_PUNCTUATION, "")
            .replace(WHITESPACE, " ")
            .trim()

    private val MARKS = Regex("\\p{Mn}+")
    private val INNER_PUNCTUATION = Regex("[·.’']")
    private val WHITESPACE = Regex("\\s+")
}
