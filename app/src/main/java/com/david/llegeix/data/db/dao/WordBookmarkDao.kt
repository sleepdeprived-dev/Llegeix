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
}
