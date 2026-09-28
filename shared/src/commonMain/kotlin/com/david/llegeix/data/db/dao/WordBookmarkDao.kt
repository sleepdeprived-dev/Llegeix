package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookmarkDao {

    @Query("SELECT * FROM word_bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WordBookmarkEntity>>

    /**
     * Whether this exact word is already saved from this exact spot.
     *
     * Matched on the position as well as the word: the same word met again on
     * another page is a different note worth keeping, but tapping the same
     * occurrence twice should toggle rather than duplicate.
     */
    @Query(
        """
        SELECT * FROM word_bookmarks
        WHERE word = :word AND documentUri IS :documentUri AND pageIndex = :pageIndex
        LIMIT 1
        """,
    )
    suspend fun find(word: String, documentUri: String?, pageIndex: Int): WordBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: WordBookmarkEntity): Long

    @Query("DELETE FROM word_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM word_bookmarks")
    fun observeCount(): Flow<Int>

    /**
     * The words due to be practised, oldest debt first.
     *
     * Ordered by when they fell due rather than at random so a session that is
     * cut short has still dealt with the words that have been waiting longest.
     */
    @Query(
        """
        SELECT * FROM word_bookmarks
        WHERE dueAt <= :now
        ORDER BY dueAt ASC, createdAt ASC
        LIMIT :limit
        """,
    )
    fun observeDue(now: Long, limit: Int): Flow<List<WordBookmarkEntity>>

    @Query("SELECT COUNT(*) FROM word_bookmarks WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    /** Record an answer: which box the word is in now, and when to come back. */
    @Query(
        """
        UPDATE word_bookmarks
        SET box = :box, dueAt = :dueAt, reviewCount = reviewCount + 1,
            lastReviewedAt = :reviewedAt
        WHERE id = :id
        """,
    )
    suspend fun recordReview(id: Long, box: Int, dueAt: Long, reviewedAt: Long)

    /** Just the words, for marking the ones already saved on a page. */
    @Query("SELECT word FROM word_bookmarks")
    fun observeWords(): Flow<List<String>>

    /** Empties the table, for the "erase everything" action in Configuració. */
    @Query("DELETE FROM word_bookmarks")
    suspend fun clear()
}
