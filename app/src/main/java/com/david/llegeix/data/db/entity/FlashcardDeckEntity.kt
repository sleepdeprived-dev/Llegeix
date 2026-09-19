package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A deck of flashcards the reader made: "Food", "Verbs", "Travel".
 *
 * Nothing to do with the documents. Saved words are notes about something
 * read, and are tied to the page they came from; a deck is vocabulary written
 * by hand, and belongs to nobody's PDF. Keeping the two in separate tables is
 * what lets either change without the other noticing.
 *
 * Names are not uniquely indexed, although two decks may not share one: the
 * rule is "the same name ignoring case", and an index would only enforce the
 * exact spelling. [com.david.llegeix.data.flashcards.DeckNames] holds the rule.
 */
@Entity(tableName = "flashcard_decks")
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
