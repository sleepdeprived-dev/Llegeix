package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

/** A deck with how many cards are in it, for the list of decks. */
data class DeckWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val cardCount: Int,
)

/** How many of a deck's cards are due in each direction of study. */
data class DeckDue(
    val deckId: Long,
    val forwardDue: Int,
    val reverseDue: Int,
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

    @Query("SELECT * FROM flashcard_decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<FlashcardDeckEntity?>

    /** Unordered: the list is sorted as Catalan, which SQLite cannot do. */
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    fun observeCards(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun card(id: Long): FlashcardEntity?

    @Insert
    suspend fun insertCard(card: FlashcardEntity): Long

    /**
     * Change what a card says, and leave its schedule alone.
     *
     * Not an `@Update` of the whole row: the form only knows the words, and
     * writing back a copy it loaded earlier would undo any answer given in the
     * meantime. Correcting a typo is not a reason to forget how well the word
     * is known.
     */
    @Query(
        """
        UPDATE flashcards
        SET catalan = :catalan, romanian = :romanian, ipa = :ipa,
            ipaApproximate = :ipaApproximate, imagePath = :imagePath
        WHERE id = :id
        """,
    )
    suspend fun updateCardContent(
        id: Long,
        catalan: String,
        romanian: String,
        ipa: String?,
        ipaApproximate: Boolean,
        imagePath: String?,
    )

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteCard(id: Long)

    /** Every card in a deck, for dealing a study session from. */
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    suspend fun cardsInDeck(deckId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards")
    suspend fun allCards(): List<FlashcardEntity>

    /**
     * How many cards in each deck are due, in each direction.
     *
     * One row per deck that has cards; a deck missing from the answer has
     * nothing in it to be due.
     */
    @Query(
        """
        SELECT deckId AS deckId,
               SUM(CASE WHEN dueAt <= :now THEN 1 ELSE 0 END) AS forwardDue,
               SUM(CASE WHEN reverseDueAt <= :now THEN 1 ELSE 0 END) AS reverseDue
        FROM flashcards
        GROUP BY deckId
        """,
    )
    fun observeDueCounts(now: Long): Flow<List<DeckDue>>

    /** Record an answer Catalan → Romanian, leaving the other direction alone. */
    @Query(
        """
        UPDATE flashcards
        SET box = :box, dueAt = :dueAt, reviewCount = reviewCount + 1,
            lastReviewedAt = :reviewedAt
        WHERE id = :id
        """,
    )
    suspend fun recordForward(id: Long, box: Int, dueAt: Long, reviewedAt: Long)

    /** Record an answer Romanian → Catalan, leaving the other direction alone. */
    @Query(
        """
        UPDATE flashcards
        SET reverseBox = :box, reverseDueAt = :dueAt,
            reverseReviewCount = reverseReviewCount + 1, reverseLastReviewedAt = :reviewedAt
        WHERE id = :id
        """,
    )
    suspend fun recordReverse(id: Long, box: Int, dueAt: Long, reviewedAt: Long)

    /** Every picture any card points at, for the sweep of stray files. */
    @Query("SELECT imagePath FROM flashcards WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>

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
