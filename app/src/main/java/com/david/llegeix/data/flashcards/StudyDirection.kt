package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.practice.Leitner

/**
 * Which way round a card is asked.
 *
 * Each direction reads and moves its own half of the card's schedule, and only
 * that half: knowing *pa* when you see it says nothing about whether you can
 * produce it from *pâine*.
 */
enum class StudyDirection {
    /** The Catalan is shown; the Romanian is the answer. Recognition. */
    CATALAN_TO_ROMANIAN,

    /** The Romanian is shown; the Catalan is the answer. Recall, the harder one. */
    ROMANIAN_TO_CATALAN,
    ;

    fun boxOf(card: FlashcardEntity): Int = when (this) {
        CATALAN_TO_ROMANIAN -> card.box
        ROMANIAN_TO_CATALAN -> card.reverseBox
    }

    fun dueAtOf(card: FlashcardEntity): Long = when (this) {
        CATALAN_TO_ROMANIAN -> card.dueAt
        ROMANIAN_TO_CATALAN -> card.reverseDueAt
    }

    fun isDue(card: FlashcardEntity, now: Long): Boolean = dueAtOf(card) <= now

    /** The same card, the other way round. */
    fun other(): StudyDirection = when (this) {
        CATALAN_TO_ROMANIAN -> ROMANIAN_TO_CATALAN
        ROMANIAN_TO_CATALAN -> CATALAN_TO_ROMANIAN
    }

    /** Where the card lands in this direction after an answer, by the one scheduler the app has. */
    fun answer(card: FlashcardEntity, correct: Boolean, now: Long): Leitner.Next =
        Leitner.answer(boxOf(card), correct, now)

    companion object {
        val Default = CATALAN_TO_ROMANIAN

        fun fromName(name: String?): StudyDirection =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
