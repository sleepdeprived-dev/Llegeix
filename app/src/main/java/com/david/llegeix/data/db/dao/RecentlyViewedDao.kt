package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

/** A recently-viewed entry joined with the document it points at. */
data class RecentDocument(
    val uriString: String,
    val displayName: String,
    val viewedAt: Long,
    val lastPageIndex: Int,
)

@Dao
interface RecentlyViewedDao {

    /** Upsert, so re-opening a document moves it up rather than adding a row. */
    @Upsert
    suspend fun record(entry: RecentlyViewedEntity)

    @Query(
        """
        SELECT d.uriString AS uriString,
               d.displayName AS displayName,
               r.viewedAt AS viewedAt,
               r.lastPageIndex AS lastPageIndex
        FROM recently_viewed r
        INNER JOIN documents d ON d.uriString = r.documentUri
        ORDER BY r.viewedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RecentDocument>>

    @Query("SELECT * FROM recently_viewed WHERE documentUri = :uriString")
    suspend fun find(uriString: String): RecentlyViewedEntity?

    @Query("DELETE FROM recently_viewed WHERE documentUri = :uriString")
    suspend fun remove(uriString: String)

    @Query("DELETE FROM recently_viewed")
    suspend fun clear()
}
