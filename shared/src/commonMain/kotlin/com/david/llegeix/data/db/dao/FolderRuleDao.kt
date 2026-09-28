package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.david.llegeix.data.db.entity.FolderRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderRuleDao {

    @Query("SELECT * FROM folder_rules")
    fun observeAll(): Flow<List<FolderRuleEntity>>

    @Upsert(entity = FolderRuleEntity::class)
    suspend fun upsert(rule: FolderRuleEntity)

    @Query("DELETE FROM folder_rules WHERE path = :path")
    suspend fun delete(path: String)

    /**
     * Forget a folder and everything under it.
     *
     * Used when a whole source is given back: leaving rules behind would apply
     * them again if the same folder were ever added a second time, which is a
     * decision the reader made about a source that no longer exists.
     */
    @Query("DELETE FROM folder_rules WHERE path = :prefix OR path LIKE :prefix || '/%'")
    suspend fun deleteTree(prefix: String)

    @Query("DELETE FROM folder_rules")
    suspend fun clear()
}
