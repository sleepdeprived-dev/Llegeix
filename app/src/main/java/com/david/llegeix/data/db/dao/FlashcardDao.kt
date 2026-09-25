package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

/** A deck with how many cards are in it, for the list of decks. */
data class DeckWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isPinned: Boolean,
    /** The shelf it sits on, or null for a deck that sits on its own. */
    val collectionId: Long?,
    val cardCount: Int,
    /** How many of its cards carry a picture, which deleting the deck also deletes. */
    val imageCount: Int,
    /** How many of its cards have an English meaning, and so can be practised in English. */
    val englishCount: Int,
    /**
     * The picture that stands for the deck: the one the reader chose, or
     * failing that the picture of its first illustrated card.
     */
    val coverImage: String?,
    /** Only the chosen picture, so the menu knows whether there is one to remove. */
    val chosenCover: String?,
    /** Who made [coverImage], which also says whether it is a pictogram. */
    val coverCredit: String?,
    /** The Leitner boxes of its cards added up, one sum per direction, for how well it is known. */
    val boxTotal: Int,
    val reverseBoxTotal: Int,
)

@Dao
interface FlashcardDao {

    /** Pinned first, then alphabetical, because a deck is found by its name. */
    @Query(
        """
        SELECT d.id AS id, d.name AS name, d.createdAt AS createdAt, d.isPinned AS isPinned,
               d.collectionId AS collectionId,
               COUNT(c.id) AS cardCount, COUNT(c.imagePath) AS imageCount,
               COALESCE(SUM(CASE WHEN c.english IS NOT NULL AND TRIM(c.english) != ''
                   THEN 1 ELSE 0 END), 0) AS englishCount,
               COALESCE(d.coverPath, (SELECT imagePath FROM flashcards
                WHERE deckId = d.id AND imagePath IS NOT NULL
                ORDER BY createdAt, id LIMIT 1)) AS coverImage,
               d.coverPath AS chosenCover,
               CASE WHEN d.coverPath IS NOT NULL THEN d.coverCredit
                    ELSE (SELECT imageCredit FROM flashcards
                          WHERE deckId = d.id AND imagePath IS NOT NULL
                          ORDER BY createdAt, id LIMIT 1) END AS coverCredit,
               COALESCE(SUM(c.box), 0) AS boxTotal,
               COALESCE(SUM(c.reverseBox), 0) AS reverseBoxTotal
        FROM flashcard_decks d
        LEFT JOIN flashcards c ON c.deckId = d.id
        GROUP BY d.id
        ORDER BY d.isPinned DESC, d.name COLLATE NOCASE
        """,
    )
    fun observeDecks(): Flow<List<DeckWithCount>>

    @Query("SELECT * FROM flashcard_decks")
    suspend fun decks(): List<FlashcardDeckEntity>

    // ---- Collections -------------------------------------------------------

    /**
     * The shelves, pinned first and then alphabetical, exactly as the decks
     * are ordered: the two kinds of row sit in one list and cannot be sorted
     * by different rules without the list looking broken.
     *
     * Only the rows. What is on a shelf — how many decks, how many cards, which
     * picture stands for it — is worked out from the decks the screen has
     * already loaded, rather than asked for again in SQL that would have to
     * repeat the deck query's own cover logic word for word.
     */
    @Query("SELECT * FROM flashcard_collections ORDER BY isPinned DESC, name COLLATE NOCASE")
    fun observeCollections(): Flow<List<FlashcardCollectionEntity>>

    @Query("SELECT * FROM flashcard_collections")
    suspend fun collections(): List<FlashcardCollectionEntity>

    @Query("SELECT * FROM flashcard_collections WHERE id = :id")
    suspend fun collection(id: Long): FlashcardCollectionEntity?

    @Insert
    suspend fun insertCollection(collection: FlashcardCollectionEntity): Long

    @Query("UPDATE flashcard_collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("UPDATE flashcard_collections SET isPinned = :pinned WHERE id = :id")
    suspend fun setCollectionPinned(id: Long, pinned: Boolean)

    @Query("UPDATE flashcard_collections SET coverPath = :path, coverCredit = :credit WHERE id = :id")
    suspend fun setCollectionCover(id: Long, path: String?, credit: String?)

    /** See [com.david.llegeix.data.flashcards.FlashcardRepository.deleteCollection] for what happens to what was in it. */
    @Query("DELETE FROM flashcard_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    /** Put a shelf inside another, or at the top with null. */
    @Query("UPDATE flashcard_collections SET parentId = :parentId WHERE id = :id")
    suspend fun setCollectionParent(id: Long, parentId: Long?)

    /** Everything directly inside [from] — shelves and decks — moved into [to]. */
    @Query("UPDATE flashcard_collections SET parentId = :to WHERE parentId = :from")
    suspend fun moveChildCollections(from: Long, to: Long?)

    @Query("UPDATE flashcard_decks SET collectionId = :to WHERE collectionId = :from")
    suspend fun moveDecksBetween(from: Long, to: Long?)

    /** Put a deck on a shelf, or take it off one with null. */
    @Query("UPDATE flashcard_decks SET collectionId = :collectionId WHERE id = :id")
    suspend fun setDeckCollection(id: Long, collectionId: Long?)

    @Query("DELETE FROM flashcard_collections")
    suspend fun clearCollections()

    @Insert
    suspend fun insertDeck(deck: FlashcardDeckEntity): Long

    @Query("UPDATE flashcard_decks SET name = :name WHERE id = :id")
    suspend fun renameDeck(id: Long, name: String)

    @Query("UPDATE flashcard_decks SET isPinned = :pinned WHERE id = :id")
    suspend fun setDeckPinned(id: Long, pinned: Boolean)

    @Query("UPDATE flashcard_decks SET coverPath = :path, coverCredit = :credit WHERE id = :id")
    suspend fun setDeckCover(id: Long, path: String?, credit: String?)

    @Query("SELECT * FROM flashcard_decks WHERE id = :id")
    suspend fun deck(id: Long): FlashcardDeckEntity?

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
        SET catalan = :catalan, romanian = :romanian, english = :english, ipa = :ipa,
            ipaApproximate = :ipaApproximate, imagePath = :imagePath,
            imageCredit = :imageCredit
        WHERE id = :id
        """,
    )
    suspend fun updateCardContent(
        id: Long,
        catalan: String,
        romanian: String,
        english: String?,
        ipa: String?,
        ipaApproximate: Boolean,
        imagePath: String?,
        imageCredit: String?,
    )

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteCard(id: Long)

    /** Every card in a deck, for dealing a study session from. */
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    suspend fun cardsInDeck(deckId: Long): List<FlashcardEntity>

    /**
     * Every card on a shelf: all of food at once, rather than one deck of it.
     *
     * This is the whole point of collections. A reader with *Vegetables*,
     * *Fruit* and *At the market* can practise any one of them, or the subject
     * — and the subject is a real session over real cards, not three sessions
     * run back to back.
     *
     * Everything under the shelf, however deep: the shelves inside it and the
     * shelves inside those. `UNION` rather than `UNION ALL`, so a loop — which
     * the repository never lets be made — would still end.
     */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
            SELECT :collectionId
            UNION
            SELECT s.id FROM flashcard_collections s JOIN tree t ON s.parentId = t.id
        )
        SELECT c.* FROM flashcards c
        JOIN flashcard_decks d ON d.id = c.deckId
        WHERE d.collectionId IN (SELECT id FROM tree)
        """,
    )
    suspend fun cardsInCollection(collectionId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards")
    suspend fun allCards(): List<FlashcardEntity>

    /** A deck's cards still without an English meaning, for filling them in. */
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND (english IS NULL OR TRIM(english) = '')")
    suspend fun cardsWithoutEnglish(deckId: Long): List<FlashcardEntity>

    @Query("UPDATE flashcards SET english = :english WHERE id = :id")
    suspend fun setEnglish(id: Long, english: String)

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

    /**
     * Every picture anything points at — cards and deck covers alike — for the
     * sweep of stray files. A cover left out of this would be swept an hour
     * after it was chosen.
     */
    @Query(
        """
        SELECT imagePath FROM flashcards WHERE imagePath IS NOT NULL
        UNION
        SELECT coverPath FROM flashcard_decks WHERE coverPath IS NOT NULL
        UNION
        SELECT coverPath FROM flashcard_collections WHERE coverPath IS NOT NULL
        """,
    )
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
