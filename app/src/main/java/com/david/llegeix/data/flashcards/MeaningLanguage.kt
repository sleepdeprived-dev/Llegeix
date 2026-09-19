package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity

/**
 * The language a card's meaning is practised in.
 *
 * Romanian is the card's own meaning and always there; English is optional,
 * and a card without one is simply left out of an English session rather than
 * shown with a gap where its answer should be.
 */
enum class MeaningLanguage(
    /** ML Kit's code for it, for suggesting a meaning. */
    val code: String,
) {
    ROMANIAN("ro"),
    ENGLISH("en"),
    ;

    /** The card's meaning in this language, or null if it has none. */
    fun meaningOf(card: FlashcardEntity): String? = when (this) {
        ROMANIAN -> card.romanian
        ENGLISH -> card.english
    }?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        val Default = ROMANIAN

        fun fromName(name: String?): MeaningLanguage =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
