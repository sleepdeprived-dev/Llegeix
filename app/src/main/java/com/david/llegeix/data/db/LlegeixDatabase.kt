package com.david.llegeix.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.david.llegeix.data.db.dao.BookmarkDao
import com.david.llegeix.data.db.dao.DocumentDao
import com.david.llegeix.data.db.dao.FolderDao
import com.david.llegeix.data.db.dao.RecentlyViewedDao
import com.david.llegeix.data.db.dao.TagDao
import com.david.llegeix.data.db.dao.WordBookmarkDao
import com.david.llegeix.data.db.entity.BookmarkEntity
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import com.david.llegeix.data.db.entity.DocumentTagEntity
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
    ],
    version = 4,
    exportSchema = true,
)
abstract class LlegeixDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun wordBookmarkDao(): WordBookmarkDao
    abstract fun tagDao(): TagDao

    companion object {
        fun build(context: Context): LlegeixDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LlegeixDatabase::class.java,
                "llegeix.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
