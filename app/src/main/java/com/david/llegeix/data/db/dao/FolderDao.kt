package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.david.llegeix.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** A folder plus how many documents are filed in it. */
data class FolderWithCount(
    val id: Long,
    val name: String,
    val isPinned: Boolean,
    val isBookmarked: Boolean,
    val colorArgb: Int?,
    val documentCount: Int,
)

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY isPinned DESC, isBookmarked DESC, name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    /**
     * LEFT JOIN so a folder with nothing in it still appears, with a count of
     * zero, rather than vanishing from the browser.
     */
    @Query(
        """
        SELECT f.id AS id, f.name AS name, f.isPinned AS isPinned,
               f.isBookmarked AS isBookmarked, f.colorArgb AS colorArgb,
               COUNT(d.uriString) AS documentCount
        FROM folders f
        LEFT JOIN documents d ON d.folderId = f.id
        GROUP BY f.id, f.name, f.isPinned, f.isBookmarked, f.colorArgb
        ORDER BY f.isPinned DESC, f.isBookmarked DESC, f.name COLLATE NOCASE
        """,
    )
    fun observeFoldersWithCounts(): Flow<List<FolderWithCount>>

    /** Returns -1 when a folder of that name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :folderId")
    suspend fun rename(folderId: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun delete(folderId: Long)

    @Query("UPDATE folders SET isPinned = :pinned WHERE id = :folderId")
    suspend fun setPinned(folderId: Long, pinned: Boolean)

    @Query("UPDATE folders SET isBookmarked = :bookmarked WHERE id = :folderId")
    suspend fun setBookmarked(folderId: Long, bookmarked: Boolean)

    @Query("UPDATE folders SET colorArgb = :color WHERE id = :folderId")
    suspend fun setColor(folderId: Long, color: Int?)
}
