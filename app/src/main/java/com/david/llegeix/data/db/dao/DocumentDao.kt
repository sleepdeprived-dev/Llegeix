package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.david.llegeix.data.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    /**
     * Create the row only if it is missing.
     *
     * IGNORE rather than REPLACE on purpose: this runs every time a document is
     * opened, and REPLACE would reset the folder and colour the user had
     * already chosen back to the defaults carried by the fresh instance.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE uriString = :uriString")
    fun observe(uriString: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY displayName COLLATE NOCASE")
    fun observeInFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY displayName COLLATE NOCASE")
    fun observeUnfiled(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET folderId = :folderId WHERE uriString = :uriString")
    suspend fun setFolder(uriString: String, folderId: Long?)

    @Query("UPDATE documents SET highlightColor = :color WHERE uriString = :uriString")
    suspend fun setHighlightColor(uriString: String, color: Int?)

    /**
     * Record how many pages a document turned out to have.
     *
     * Written once, when the reader opens it and PDFium reports the count. It
     * is what lets every other screen say "page 34 of 210" and draw a bar
     * rather than a bare page number nobody can weigh.
     */
    @Query("UPDATE documents SET pageCount = :pageCount WHERE uriString = :uriString")
    suspend fun setPageCount(uriString: String, pageCount: Int)

    /**
     * Documents bookmarked as a whole. This is the query behind the automatic
     * "Bookmarked" collection, which is derived rather than stored: a document
     * belongs to at most one real folder, so filing bookmarked PDFs into a
     * folder row would pull them out of whatever folder the user had chosen.
     */
    @Query("SELECT * FROM documents WHERE isBookmarked = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeBookmarked(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents WHERE isBookmarked = 1")
    fun observeBookmarkedCount(): Flow<Int>

    @Query("SELECT * FROM documents WHERE isReadLater = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeReadLater(): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET isBookmarked = :bookmarked WHERE uriString = :uriString")
    suspend fun setBookmarked(uriString: String, bookmarked: Boolean)

    @Query("UPDATE documents SET isReadLater = :readLater WHERE uriString = :uriString")
    suspend fun setReadLater(uriString: String, readLater: Boolean)

    /** Empties the table, for the "erase everything" action in Configuració. */
    @Query("DELETE FROM documents")
    suspend fun clear()
}
