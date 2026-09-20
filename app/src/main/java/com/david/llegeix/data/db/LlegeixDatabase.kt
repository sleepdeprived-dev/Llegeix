package com.david.llegeix.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.david.llegeix.data.db.dao.BookmarkDao
import com.david.llegeix.data.db.dao.DocumentDao
import com.david.llegeix.data.db.dao.FlashcardDao
import com.david.llegeix.data.db.dao.FolderDao
import com.david.llegeix.data.db.dao.RecentlyViewedDao
import com.david.llegeix.data.db.dao.FolderRuleDao
import com.david.llegeix.data.db.dao.TagDao
import com.david.llegeix.data.db.dao.WordBookmarkDao
import com.david.llegeix.data.db.entity.BookmarkEntity
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import com.david.llegeix.data.db.entity.DocumentTagEntity
import com.david.llegeix.data.db.entity.FolderRuleEntity
import com.david.llegeix.data.db.entity.TagEntity
import com.david.llegeix.data.db.entity.WordBookmarkEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        BookmarkEntity::class,
        RecentlyViewedEntity::class,
        WordBookmarkEntity::class,
        TagEntity::class,
        DocumentTagEntity::class,
        FolderRuleEntity::class,
        FlashcardCollectionEntity::class,
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
    ],
    version = 19,
    exportSchema = true,
)
abstract class LlegeixDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun wordBookmarkDao(): WordBookmarkDao
    abstract fun tagDao(): TagDao
    abstract fun folderRuleDao(): FolderRuleDao
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        fun build(context: Context): LlegeixDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LlegeixDatabase::class.java,
                "llegeix.db",
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                MIGRATION_18_19,
            )
                .build()
    }
}
