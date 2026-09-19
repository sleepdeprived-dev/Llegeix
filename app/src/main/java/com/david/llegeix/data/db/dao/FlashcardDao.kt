package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import kotlinx.coroutines.flow.Flow

/** A deck with how many cards are in it, for the list of decks. */
data class DeckWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val cardCount: Int,
)

@Dao
interface FlashcardDao {

    /** Alphabetical, because a deck is found by its name. */
    @Query(
        """
        SELECT d.id AS id, d.name AS name, d.createdAt AS createdAt,
               COUNT(c.id) AS cardCount
        FROM flashcard_decks d
        LEFT JOIN flashcards c ON c.deckId = d.id
        GROUP BY d.id
        ORDER BY d.name COLLATE NOCASE
        """,
    )
    fun observeDecks(): Flow<List<DeckWithCount>>

    @Query("SELECT * FROM flashcard_decks")
    suspend fun decks(): List<FlashcardDeckEntity>

    @Insert
    suspend fun insertDeck(deck: FlashcardDeckEntity): Long

    @Query("UPDATE flashcard_decks SET name = :name WHERE id = :id")
    suspend fun renameDeck(id: Long, name: String)

    /** Its cards go with it, by cascade; their pictures do not, see [imagePathsInDeck]. */
    @Query("DELETE FROM flashcard_decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    /** The picture files a deck's cards point at, to delete alongside it. */
    @Query("SELECT imagePath FROM flashcards WHERE deckId = :deckId AND imagePath IS NOT NULL")
    suspend fun imagePathsInDeck(deckId: Long): List<String>

    /**
     * Empties both tables, for the "erase everything" action in Configuració.
     *
     * The cards are deleted in their own right rather than left to the cascade,
     * so the wipe does not depend on how the connection was opened.
     */
    @Query("DELETE FROM flashcards")
    suspend fun clearCards()

    @Query("DELETE FROM flashcard_decks")
    suspend fun clearDecks()
}
