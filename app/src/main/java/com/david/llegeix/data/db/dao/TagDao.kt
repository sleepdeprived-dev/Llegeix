package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.david.llegeix.data.db.entity.DocumentTagEntity
import com.david.llegeix.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** A tag together with the document it is on, for building the per-document map. */
data class DocumentTag(
    val documentUri: String,
    val id: Long,
    val name: String,
    val colorArgb: Int,
)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    /**
     * Every document-to-tag pairing in one query.
     *
     * One flow for the whole library rather than a query per row: the lists show
     * tags on every visible document, and a per-row query would mean a database
     * round trip for each item as it scrolls into view.
     */
    @Query(
        """
        SELECT dt.documentUri AS documentUri, t.id AS id, t.name AS name,
               t.colorArgb AS colorArgb
        FROM document_tags dt
        INNER JOIN tags t ON t.id = dt.tagId
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeDocumentTags(): Flow<List<DocumentTag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("UPDATE tags SET name = :name, colorArgb = :color WHERE id = :tagId")
    suspend fun update(tagId: Long, name: String, color: Int)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun attach(link: DocumentTagEntity)

    @Query("DELETE FROM document_tags WHERE documentUri = :uriString AND tagId = :tagId")
    suspend fun detach(uriString: String, tagId: Long)

    @Query("SELECT tagId FROM document_tags WHERE documentUri = :uriString")
    suspend fun tagIdsFor(uriString: String): List<Long>
}
