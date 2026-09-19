package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.practice.Leitner

/**
 * Which way round a card is asked.
 *
 * Each direction reads and moves its own half of the card's schedule, and only
 * that half: knowing *pa* when you see it says nothing about whether you can
 * produce it from *pâine*.
 *
 * The other side is "the meaning" rather than a language, because the schedule
 * is about the Catalan: recognising it, or producing it. That is the same skill
 * whether the meaning is shown in Romanian or in English, so the language is a
 * separate choice ([MeaningLanguage]) and does not split the schedule in two.
 */
enum class StudyDirection {
    /** The Catalan is shown; the meaning is the answer. Recognition. */
    CATALAN_TO_MEANING,

    /** The meaning is shown; the Catalan is the answer. Recall, the harder one. */
    MEANING_TO_CATALAN,
    ;

    fun boxOf(card: FlashcardEntity): Int = when (this) {
        CATALAN_TO_MEANING -> card.box
        MEANING_TO_CATALAN -> card.reverseBox
    }

    fun dueAtOf(card: FlashcardEntity): Long = when (this) {
        CATALAN_TO_MEANING -> card.dueAt
        MEANING_TO_CATALAN -> card.reverseDueAt
    }

    fun isDue(card: FlashcardEntity, now: Long): Boolean = dueAtOf(card) <= now

    /** The same card, the other way round. */
    fun other(): StudyDirection = when (this) {
        CATALAN_TO_MEANING -> MEANING_TO_CATALAN
        MEANING_TO_CATALAN -> CATALAN_TO_MEANING
    }

    /** Where the card lands in this direction after an answer, by the one scheduler the app has. */
    fun answer(card: FlashcardEntity, correct: Boolean, now: Long): Leitner.Next =
        Leitner.answer(boxOf(card), correct, now)

    companion object {
        val Default = CATALAN_TO_MEANING

        fun fromName(name: String?): StudyDirection =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
