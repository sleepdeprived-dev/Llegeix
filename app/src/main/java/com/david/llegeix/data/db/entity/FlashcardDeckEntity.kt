package com.david.llegeix.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
@Entity(
    tableName = "flashcard_decks",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            // Deleting a shelf does not delete what was on it. A collection is
            // a way of grouping decks that already existed, so losing one has
            // to mean losing the grouping and nothing else; a cascade here
            // would make a tidy-up into a catastrophe.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("collectionId")],
)
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Kept at the top of the list, above the alphabetical rest. */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    /**
     * A picture the reader chose to stand for the deck, relative to the files
     * directory like a card's. Null means the deck shows its first card's
     * picture, or its initial.
     */
    val coverPath: String? = null,
    /** Who made [coverPath], when it came from a search. */
    val coverCredit: String? = null,
    /**
     * The collection this deck sits on, or null for a deck that sits on its
     * own — which is what every deck is until somebody files it.
     */
    val collectionId: Long? = null,
)
