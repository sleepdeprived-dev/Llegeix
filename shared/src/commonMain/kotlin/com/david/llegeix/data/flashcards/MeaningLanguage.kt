package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity

/**
 * The language a card's meaning is practised in: Romanian, the card's own
 * meaning and always there. Cards are Catalan and Romanian only.
 */
enum class MeaningLanguage(
    /** ML Kit's code for it, for suggesting a meaning. */
    val code: String,
) {
    ROMANIAN("ro"),
    ;

    /** The card's meaning in this language, or null if it has none. */
    fun meaningOf(card: FlashcardEntity): String? = when (this) {
        ROMANIAN -> card.romanian
    }.trim().takeIf { it.isNotEmpty() }

    companion object {
        val Default = ROMANIAN

        fun fromName(name: String?): MeaningLanguage =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
