package com.david.llegeix.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.david.llegeix.data.db.dao.BookmarkDao
import com.david.llegeix.data.db.dao.DocumentDao
import com.david.llegeix.data.db.dao.ExamDao
import com.david.llegeix.data.db.dao.FolderDao
import com.david.llegeix.data.db.dao.RecentlyViewedDao
import com.david.llegeix.data.db.dao.FolderRuleDao
import com.david.llegeix.data.db.dao.TagDao
import com.david.llegeix.data.db.dao.WordBookmarkDao
import com.david.llegeix.data.db.entity.BookmarkEntity
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.ExamPartEntity
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
        ExamEntity::class,
        ExamAttemptEntity::class,
        ExamAudioEntity::class,
        ExamMarkEntity::class,
        ExamPartEntity::class,
    ],
    version = 11,
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
    abstract fun examDao(): ExamDao

    companion object {
        fun build(context: Context): LlegeixDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LlegeixDatabase::class.java,
                "llegeix.db",
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            )
                .build()
    }
}
