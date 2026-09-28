package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.david.llegeix.data.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/** A bookmarked page, joined with the document it sits in. */
data class PageBookmark(
    val id: Long,
    val documentUri: String,
    val displayName: String,
    val pageIndex: Int,
    val label: String?,
    val highlightColor: Int?,
    val createdAt: Long,
)

@Dao
interface BookmarkDao {

    /**
     * Every page bookmark across all documents, newest first. Kept separate
     * from whole-document bookmarks, which live on the document row.
     */
    @Query(
        """
        SELECT b.id AS id, b.documentUri AS documentUri, d.displayName AS displayName,
               b.pageIndex AS pageIndex, b.label AS label,
               b.highlightColor AS highlightColor, b.createdAt AS createdAt
        FROM bookmarks b
        INNER JOIN documents d ON d.uriString = b.documentUri
        ORDER BY b.createdAt DESC
        """,
    )
    fun observePageBookmarks(): Flow<List<PageBookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long)

    @Query("SELECT * FROM bookmarks WHERE documentUri = :uriString ORDER BY pageIndex")
    fun observeForDocument(uriString: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE documentUri = :uriString AND pageIndex = :pageIndex LIMIT 1",
    )
    suspend fun findAt(uriString: String, pageIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE documentUri = :uriString AND pageIndex = :pageIndex")
    suspend fun deleteAt(uriString: String, pageIndex: Int)

    @Query("UPDATE bookmarks SET highlightColor = :color WHERE id = :bookmarkId")
    suspend fun setHighlightColor(bookmarkId: Long, color: Int?)

    @Query("UPDATE bookmarks SET label = :label WHERE id = :bookmarkId")
    suspend fun setLabel(bookmarkId: Long, label: String?)

    /** Empties the table, for the "erase everything" action in Configuració. */
    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}
